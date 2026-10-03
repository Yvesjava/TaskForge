package cn.iocoder.yudao.module.agent.service.workspace;

import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandException;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandResult;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitCommandRunner;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitRefs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.BRANCH_BASELINE_CHECK_FAILED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.BRANCH_BASELINE_NOT_FOUND;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.BRANCH_CREATE_FAILED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.BRANCH_DELETE_FAILED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.BRANCH_NAME_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.BRANCH_PUSH_FAILED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.BRANCH_TASK_NO_INVALID;

/**
 * 任务特性分支管理器
 *
 * <p>负责任务特性分支的命名、基线校验、创建、推送与清理。所有操作均以
 * {@link BranchOperation} 返回，使命名、基线与远端动作可被上层追踪。
 * 分支操作作用在 {@link BareRepoManager} 维护的裸仓库缓存上，创建与推送
 * 不进行 force push。
 *
 * @author TaskForge
 */
@Component
@Slf4j
public class TaskBranchManager {

    /**
     * 基线校验通过
     */
    public static final String ACTION_VALIDATE_BASELINE = "VALIDATE_BASELINE";
    /**
     * 创建本地特性分支
     */
    public static final String ACTION_CREATE_BRANCH = "CREATE_BRANCH";
    /**
     * 推送特性分支到远端
     */
    public static final String ACTION_PUSH_BRANCH = "PUSH_BRANCH";
    /**
     * 删除本地特性分支
     */
    public static final String ACTION_DELETE_LOCAL_BRANCH = "DELETE_LOCAL_BRANCH";
    /**
     * 删除远端特性分支
     */
    public static final String ACTION_DELETE_REMOTE_BRANCH = "DELETE_REMOTE_BRANCH";

    /**
     * 任务特性分支前缀，所有项目共用同一任务分支名
     */
    private static final String FEATURE_BRANCH_PREFIX = "feature/";

    /**
     * 裸仓库克隆时配置的远端名
     */
    private static final String REMOTE_NAME = "origin";

    /**
     * git ls-remote --exit-code 找不到匹配引用时的退出码
     */
    private static final int LS_REMOTE_NOT_FOUND_EXIT_CODE = 2;

    private final BareRepoManager bareRepoManager;

    private final GitCommandRunner gitRunner;

    public TaskBranchManager(BareRepoManager bareRepoManager, GitCommandRunner gitRunner) {
        this.bareRepoManager = bareRepoManager;
        this.gitRunner = gitRunner;
    }

    /**
     * 根据任务编号生成任务特性分支名。
     *
     * @param taskNo 任务编号（如 TASK-20261001-088）
     * @return 特性分支名（如 feature/TASK-20261001-088）
     */
    public String featureBranchName(String taskNo) {
        if (taskNo == null) {
            throw exception(BRANCH_TASK_NO_INVALID, taskNo);
        }
        String value = taskNo.trim();
        String branch = FEATURE_BRANCH_PREFIX + value;
        if (value.isEmpty() || !GitRefs.isValidBranchName(branch)) {
            throw exception(BRANCH_TASK_NO_INVALID, taskNo);
        }
        return branch;
    }

    /**
     * 校验基线分支存在并返回其最新提交。
     *
     * <p>先确保裸仓库缓存存在，再通过 {@code ls-remote --exit-code} 判断远端
     * 是否确实存在该基线，随后拉取最新引用并解析本地提交哈希。
     *
     * @param projectCode 项目代号
     * @param gitUrl      仓库地址
     * @param baseBranch  基线分支
     * @return 可追踪的基线校验结果
     */
    public BranchOperation validateBaseline(String projectCode, String gitUrl, String baseBranch) {
        validateBranch(projectCode, baseBranch);
        bareRepoManager.ensureRepository(projectCode, gitUrl);
        Path repo = bareRepoManager.repositoryPath(projectCode);

        int remoteExitCode = remoteRefExitCode(repo, baseBranch);
        if (remoteExitCode == LS_REMOTE_NOT_FOUND_EXIT_CODE) {
            throw exception(BRANCH_BASELINE_NOT_FOUND, projectCode, baseBranch);
        }
        if (remoteExitCode != 0) {
            throw exception(BRANCH_BASELINE_CHECK_FAILED, projectCode, baseBranch, "远端基线检查失败");
        }

        bareRepoManager.fetch(projectCode, baseBranch);
        String commit = resolveRef(repo, "refs/heads/" + baseBranch)
                .orElseThrow(() -> exception(BRANCH_BASELINE_NOT_FOUND, projectCode, baseBranch));

        BranchOperation operation = new BranchOperation(
                ACTION_VALIDATE_BASELINE, projectCode, baseBranch, baseBranch, null, commit);
        log.info("[TaskBranch] 基线校验通过 project={}, baseline={}, commit={}", projectCode, baseBranch, commit);
        return operation;
    }

    /**
     * 从基线创建本地特性分支。
     *
     * @param projectCode  项目代号
     * @param gitUrl       仓库地址
     * @param baseBranch   基线分支
     * @param targetBranch 目标特性分支
     * @return 可追踪的分支创建结果
     */
    public BranchOperation createFeatureBranch(String projectCode, String gitUrl,
                                               String baseBranch, String targetBranch) {
        validateBranch(projectCode, targetBranch);
        BranchOperation baseline = validateBaseline(projectCode, gitUrl, baseBranch);
        Path repo = bareRepoManager.repositoryPath(projectCode);

        String baseCommit = baseline.commit();
        try {
            gitRunner.run(repo, List.of("update-ref", "refs/heads/" + targetBranch, baseCommit));
        } catch (GitCommandException e) {
            throw exception(BRANCH_CREATE_FAILED, projectCode, targetBranch, reasonOf(e));
        }

        BranchOperation operation = new BranchOperation(
                ACTION_CREATE_BRANCH, projectCode, targetBranch, baseBranch, null, baseCommit);
        log.info("[TaskBranch] 创建特性分支 project={}, baseline={}, branch={}, commit={}",
                projectCode, baseBranch, targetBranch, baseCommit);
        return operation;
    }

    /**
     * 推送本地特性分支到远端。
     *
     * @param projectCode  项目代号
     * @param targetBranch 目标特性分支
     * @return 可追踪的分支推送结果
     */
    public BranchOperation pushFeatureBranch(String projectCode, String targetBranch) {
        validateBranch(projectCode, targetBranch);
        Path repo = bareRepoManager.repositoryPath(projectCode);
        String commit = resolveRef(repo, "refs/heads/" + targetBranch)
                .orElseThrow(() -> exception(BRANCH_PUSH_FAILED, projectCode, targetBranch, "本地特性分支不存在"));

        try {
            gitRunner.run(repo, List.of("push", REMOTE_NAME,
                    "refs/heads/" + targetBranch + ":refs/heads/" + targetBranch));
        } catch (GitCommandException e) {
            throw exception(BRANCH_PUSH_FAILED, projectCode, targetBranch, reasonOf(e));
        }

        BranchOperation operation = new BranchOperation(
                ACTION_PUSH_BRANCH, projectCode, targetBranch, null, REMOTE_NAME, commit);
        log.info("[TaskBranch] 推送特性分支 project={}, branch={}, remote={}, commit={}",
                projectCode, targetBranch, REMOTE_NAME, commit);
        return operation;
    }

    /**
     * 幂等清理本地与远端特性分支。
     *
     * <p>本地分支不存在则跳过本地删除，远端分支不存在则跳过远端删除；
     * 重复调用不会抛“已删除”错误。
     *
     * @param projectCode  项目代号
     * @param targetBranch 目标特性分支
     * @return 实际发生的删除操作（幂等时可能为空列表）
     */
    public List<BranchOperation> deleteFeatureBranch(String projectCode, String targetBranch) {
        validateBranch(projectCode, targetBranch);
        Path repo = bareRepoManager.repositoryPath(projectCode);
        List<BranchOperation> operations = new ArrayList<>();

        Optional<String> localCommit = resolveRef(repo, "refs/heads/" + targetBranch);
        if (localCommit.isPresent()) {
            try {
                gitRunner.run(repo, List.of("branch", "-D", targetBranch));
            } catch (GitCommandException e) {
                throw exception(BRANCH_DELETE_FAILED, projectCode, targetBranch, reasonOf(e));
            }
            operations.add(new BranchOperation(
                    ACTION_DELETE_LOCAL_BRANCH, projectCode, targetBranch, null, null, localCommit.get()));
            log.info("[TaskBranch] 删除本地特性分支 project={}, branch={}, commit={}",
                    projectCode, targetBranch, localCommit.get());
        }

        if (remoteRefExitCode(repo, targetBranch) == 0) {
            try {
                gitRunner.run(repo, List.of("push", REMOTE_NAME, "--delete", targetBranch));
            } catch (GitCommandException e) {
                throw exception(BRANCH_DELETE_FAILED, projectCode, targetBranch, reasonOf(e));
            }
            operations.add(new BranchOperation(
                    ACTION_DELETE_REMOTE_BRANCH, projectCode, targetBranch, null, REMOTE_NAME, null));
            log.info("[TaskBranch] 删除远端特性分支 project={}, branch={}, remote={}",
                    projectCode, targetBranch, REMOTE_NAME);
        }

        return operations;
    }

    private void validateBranch(String projectCode, String branch) {
        if (!GitRefs.isValidBranchName(branch)) {
            throw exception(BRANCH_NAME_INVALID, projectCode, branch);
        }
    }

    /**
     * 解析本地引用，解析失败返回空。
     */
    private Optional<String> resolveRef(Path repo, String ref) {
        try {
            GitCommandResult result = gitRunner.runAllowFailure(repo, List.of("rev-parse", "--verify", ref));
            if (result.exitCode() != 0) {
                return Optional.empty();
            }
            return Optional.of(result.output().trim());
        } catch (GitCommandException e) {
            return Optional.empty();
        }
    }

    /**
     * 查询远端分支引用是否存在，返回 {@code ls-remote --exit-code} 的退出码。
     */
    private int remoteRefExitCode(Path repo, String branch) {
        try {
            GitCommandResult result = gitRunner.runAllowFailure(repo,
                    List.of("ls-remote", "--heads", "--exit-code", REMOTE_NAME, branch));
            return result.exitCode();
        } catch (GitCommandException e) {
            return e.getExitCode();
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
