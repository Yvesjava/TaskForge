package cn.iocoder.yudao.module.agent.service.workspace;

import cn.iocoder.yudao.module.agent.framework.workspace.config.AgentWorkspaceProperties;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandException;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandRunner;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitRefs;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_BRANCH_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_CREATE_FAILED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_GIT_URL_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_PROJECTS_EMPTY;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_PROJECT_CODE_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_RESIDUE_SCAN_FAILED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_ROOT_ESCAPE;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_SUB_DIR_CONFLICT;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_SUB_DIR_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_SYMLINK_NOT_ALLOWED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_TASK_NO_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.WORKTREE_WRITE_TASK_DOC_FAILED;

/**
 * 多仓聚合工作区管理器
 *
 * <p>为每个任务创建独立聚合根目录 {@code dirA-{taskNo}}，并把任务引用的
 * 多个代码项目以 Git Worktree 形式挂载到根目录下对应的子目录：
 *
 * <pre>
 * /data/agent-workspace/dirA-{taskNo}/
 * ├── .ai/task.md
 * ├── backend/          # backend-service 的 Git Worktree
 * └── frontend/         # frontend-portal 的 Git Worktree
 * </pre>
 *
 * <p>创建流程先复用 {@link BareRepoManager} 的裸仓库缓存并拉取基线，再执行
 * {@code git worktree add -b <targetBranch> <root>/<subDir> refs/heads/<baseBranch>}。
 * 任一项目挂载失败时，按已挂载项目倒序回滚，避免留下半成品目录。
 *
 * @author TaskForge
 */
@Component
public class WorktreeManager {

    /**
     * 项目代号允许的目录名模式：字母数字开头，只含字母、数字、点、下划线、连字符
     */
    private static final String PROJECT_CODE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]*";

    /**
     * 任务编号允许的目录名模式
     */
    private static final String TASK_NO_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]*";

    /**
     * 聚合目录名前缀
     */
    private static final String AGGREGATE_DIR_PREFIX = "dirA-";

    private final Path workspaceRoot;

    private final BareRepoManager bareRepoManager;

    private final GitCommandRunner gitRunner;

    /**
     * 按任务编号隔离的工作区创建锁，防止同一任务并发创建产生冲突挂载
     */
    private final ConcurrentMap<String, ReentrantLock> taskLocks = new ConcurrentHashMap<>();

    public WorktreeManager(AgentWorkspaceProperties properties, BareRepoManager bareRepoManager,
                           GitCommandRunner gitRunner) {
        this.workspaceRoot = Paths.get(properties.getWorkspaceRoot()).toAbsolutePath().normalize();
        this.bareRepoManager = bareRepoManager;
        this.gitRunner = gitRunner;
    }

    /**
     * 创建多仓聚合工作区。
     *
     * @param taskNo       任务唯一编号
     * @param targetBranch 本次任务创建的特性分支
     * @param taskDoc      原始任务文档，写入聚合根目录下的 {@code .ai/task.md}
     * @param projects     任务引用的代码项目映射
     * @return 聚合工作区创建结果
     */
    public CompositeWorkspace createCompositeWorkspace(String taskNo, String targetBranch, String taskDoc,
                                                       List<WorkspaceProject> projects) {
        String normalizedTaskNo = normalizeTaskNo(taskNo);
        validateBranch(targetBranch);
        List<WorkspaceProject> normalizedProjects = validateProjects(projects);

        ReentrantLock lock = lockForTask(normalizedTaskNo);
        lock.lock();
        try {
            return createCompositeWorkspaceLocked(normalizedTaskNo, targetBranch, taskDoc, normalizedProjects);
        } finally {
            lock.unlock();
        }
    }

    private CompositeWorkspace createCompositeWorkspaceLocked(String taskNo, String targetBranch, String taskDoc,
                                                              List<WorkspaceProject> projects) {
        Path root = aggregateRoot(taskNo);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw exception(WORKTREE_CREATE_FAILED, "-", "-", "创建聚合根目录失败：" + reasonOf(e));
        }
        rejectSymlinkEscape(root);

        List<MountedProject> mounted = new ArrayList<>();
        boolean taskDocWritten = false;
        try {
            for (WorkspaceProject project : projects) {
                mounted.add(mount(root, project, targetBranch));
            }
            writeTaskDoc(root, taskDoc);
            taskDocWritten = true;
            return CompositeWorkspace.builder()
                    .taskNo(taskNo)
                    .targetBranch(targetBranch)
                    .root(root)
                    .projects(List.copyOf(mounted))
                    .build();
        } catch (RuntimeException e) {
            rollback(mounted, root, taskDocWritten);
            throw e;
        }
    }

    /**
     * 返回指定任务对应的聚合根目录（不创建）。
     *
     * @param taskNo 任务唯一编号
     * @return 聚合根目录绝对路径
     */
    public Path workspacePath(String taskNo) {
        return aggregateRoot(normalizeTaskNo(taskNo));
    }

    /**
     * 检测工作区根目录下残留的聚合目录。
     *
     * <p>异常退出、回滚失败或进程被强杀时可能留下 {@code dirA-{taskNo}} 目录。
     * 该方法返回所有匹配前缀的目录或符号链接，交由上层补偿清理流程处理。
     *
     * @return 残留工作区绝对路径列表（按路径排序，不存在工作区根目录时为空）
     */
    public List<Path> detectResidue() {
        if (!Files.isDirectory(workspaceRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(workspaceRoot)) {
            return stream
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> path.getFileName().toString().startsWith(AGGREGATE_DIR_PREFIX))
                    .filter(path -> Files.isDirectory(path) || Files.isSymbolicLink(path))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw exception(WORKTREE_RESIDUE_SCAN_FAILED, reasonOf(e));
        }
    }

    private MountedProject mount(Path root, WorkspaceProject project, String targetBranch) {
        Path targetDir = resolveSubDir(root, project.getSubDir());
        if (Files.exists(targetDir)) {
            throw exception(WORKTREE_CREATE_FAILED, project.getProjectCode(), project.getSubDir(), "目标目录已存在");
        }

        Path bareRepo = bareRepoManager.prepare(project.getProjectCode(), project.getGitUrl(), project.getBaseBranch());
        try {
            gitRunner.run(bareRepo, List.of("worktree", "add", "-b", targetBranch,
                    targetDir.toString(), "refs/heads/" + project.getBaseBranch()));
        } catch (GitCommandException e) {
            throw exception(WORKTREE_CREATE_FAILED, project.getProjectCode(), project.getSubDir(), reasonOf(e));
        }
        return MountedProject.builder()
                .projectCode(project.getProjectCode())
                .subDir(project.getSubDir())
                .branch(targetBranch)
                .path(targetDir)
                .build();
    }

    private void writeTaskDoc(Path root, String taskDoc) {
        try {
            Path aiDir = root.resolve(".ai");
            Files.createDirectories(aiDir);
            Files.writeString(aiDir.resolve("task.md"), taskDoc == null ? "" : taskDoc, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw exception(WORKTREE_WRITE_TASK_DOC_FAILED, reasonOf(e));
        }
    }

    private void rollback(List<MountedProject> mounted, Path root, boolean taskDocWritten) {
        for (int i = mounted.size() - 1; i >= 0; i--) {
            removeWorktree(mounted.get(i));
        }
        try {
            if (taskDocWritten) {
                deleteRecursively(root.resolve(".ai"));
            }
            // 仅在聚合根目录为空时删除，避免误删先前已存在的目录
            Files.deleteIfExists(root);
        } catch (IOException ignored) {
            // 回滚为尽力而为：残留目录交由补偿清理
        }
    }

    private void removeWorktree(MountedProject project) {
        Path targetDir = project.getPath();
        if (!Files.exists(targetDir)) {
            return;
        }
        Path bareRepo = bareRepoManager.repositoryPath(project.getProjectCode());
        try {
            gitRunner.run(bareRepo, List.of("worktree", "remove", "--force", targetDir.toString()));
        } catch (GitCommandException ignored) {
            // 命令失败时继续执行物理删除，确保目录不残留
        }
        try {
            deleteRecursively(targetDir);
            gitRunner.run(bareRepo, List.of("worktree", "prune"));
        } catch (Exception ignored) {
            // 清理尽力而为
        }
    }

    private String normalizeTaskNo(String taskNo) {
        if (taskNo == null) {
            throw exception(WORKTREE_TASK_NO_INVALID, taskNo);
        }
        String value = taskNo.trim();
        if (value.isEmpty() || value.length() > 64
                || !value.matches(TASK_NO_PATTERN)
                || value.contains("..")
                || value.endsWith(".")) {
            throw exception(WORKTREE_TASK_NO_INVALID, taskNo);
        }
        return value;
    }

    private ReentrantLock lockForTask(String taskNo) {
        return taskLocks.computeIfAbsent(taskNo, key -> new ReentrantLock());
    }

    private void validateBranch(String branch) {
        if (!GitRefs.isValidBranchName(branch)) {
            throw exception(WORKTREE_BRANCH_INVALID, branch);
        }
    }

    private List<WorkspaceProject> validateProjects(List<WorkspaceProject> projects) {
        if (projects == null || projects.isEmpty()) {
            throw exception(WORKTREE_PROJECTS_EMPTY);
        }
        Set<String> seenSubDirs = new HashSet<>();
        List<WorkspaceProject> normalized = new ArrayList<>(projects.size());
        for (WorkspaceProject project : projects) {
            String projectCode = normalizeProjectCode(project.getProjectCode());
            if (projectCode == null) {
                throw exception(WORKTREE_PROJECT_CODE_INVALID, project.getProjectCode());
            }
            if (trimToNull(project.getGitUrl()) == null) {
                throw exception(WORKTREE_GIT_URL_INVALID, projectCode);
            }
            if (!GitRefs.isValidBranchName(project.getBaseBranch())) {
                throw exception(WORKTREE_BRANCH_INVALID, project.getBaseBranch());
            }
            String subDir = normalizeSubDir(project.getSubDir());
            if (subDir == null) {
                throw exception(WORKTREE_SUB_DIR_INVALID, projectCode, project.getSubDir());
            }
            if (!seenSubDirs.add(subDir)) {
                throw exception(WORKTREE_SUB_DIR_CONFLICT, subDir);
            }
            normalized.add(WorkspaceProject.builder()
                    .projectCode(projectCode)
                    .gitUrl(project.getGitUrl().trim())
                    .baseBranch(project.getBaseBranch().trim())
                    .subDir(subDir)
                    .build());
        }
        return normalized;
    }

    private Path aggregateRoot(String taskNo) {
        Path root = workspaceRoot.resolve(AGGREGATE_DIR_PREFIX + taskNo).toAbsolutePath().normalize();
        if (!root.startsWith(workspaceRoot)) {
            throw exception(WORKTREE_ROOT_ESCAPE, root);
        }
        rejectSymlinkComponents(workspaceRoot, root);
        return root;
    }

    private Path resolveSubDir(Path root, String subDir) {
        Path target = root.resolve(subDir).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw exception(WORKTREE_SUB_DIR_INVALID, "-", subDir);
        }
        rejectSymlinkComponents(root, target);
        return target;
    }

    /**
     * 在聚合根目录创建后校验真实路径仍位于工作区根目录内，
     * 防止根目录被替换为指向外部的符号链接。
     */
    private void rejectSymlinkEscape(Path root) {
        try {
            Path realWorkspaceRoot = workspaceRoot.toRealPath();
            Path realRoot = root.toRealPath();
            if (!realRoot.startsWith(realWorkspaceRoot)) {
                throw exception(WORKTREE_ROOT_ESCAPE, root);
            }
        } catch (IOException e) {
            throw exception(WORKTREE_CREATE_FAILED, "-", "-", "解析工作区真实路径失败：" + reasonOf(e));
        }
    }

    /**
     * 拒绝 {@code base} 与 {@code target} 之间（不含 {@code base}）任何
     * 已存在的符号链接组件，防止子目录通过符号链接逃逸工作根目录。
     */
    private void rejectSymlinkComponents(Path base, Path target) {
        Path baseAbs = base.toAbsolutePath().normalize();
        Path current = target.toAbsolutePath().normalize();
        while (current != null && !current.equals(baseAbs)) {
            if (Files.isSymbolicLink(current)) {
                throw exception(WORKTREE_SYMLINK_NOT_ALLOWED, current);
            }
            current = current.getParent();
        }
    }

    private String normalizeProjectCode(String projectCode) {
        if (projectCode == null) {
            return null;
        }
        String value = projectCode.trim();
        if (value.isEmpty() || value.length() > 64
                || !value.matches(PROJECT_CODE_PATTERN)
                || value.contains("..")
                || value.endsWith(".")) {
            return null;
        }
        return value;
    }

    private String normalizeSubDir(String subDir) {
        if (subDir == null) {
            return null;
        }
        String value = subDir.trim();
        if (value.isEmpty() || value.length() > 64) {
            return null;
        }
        if (value.startsWith("/") || value.endsWith("/") || value.contains("\\")) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x1F || c == 0x7F) {
                return null;
            }
        }
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (Files.isSymbolicLink(path)) {
            Files.deleteIfExists(path);
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 单个文件删除失败不影响整体回滚
                }
            });
        }
    }

    private String reasonOf(Exception e) {
        if (e instanceof GitCommandException git) {
            String output = git.getSanitizedOutput();
            if (output.isEmpty()) {
                return git.getMessage();
            }
            return git.getMessage() + "：" + output;
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

}
