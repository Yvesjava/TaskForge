package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskBranchInfoVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskDiffRespVO;
import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskTestReportVO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentProjectDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskDO;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentTaskProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentProjectMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskMapper;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentTaskProjectMapper;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitRepository;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitRepositoryParser;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.TASK_NOT_FOUND;

/**
 * 任务结果读取 Service 实现
 *
 * <p>只读聚合，不改变任务状态；任一数据源缺失时返回明确的空值（空集合或 null），
 * 避免前端展示侧出现未定义字段。</p>
 *
 * @author TaskForge
 */
@Service
@Slf4j
public class AgentTaskDiffServiceImpl implements AgentTaskDiffService {

    private static final String WORKPAD_SUMMARY_RELATIVE_PATH = ".ai/workpad_summary.json";

    private final AgentTaskMapper taskMapper;

    private final AgentTaskProjectMapper taskProjectMapper;

    private final AgentProjectMapper projectMapper;

    public AgentTaskDiffServiceImpl(AgentTaskMapper taskMapper,
                                    AgentTaskProjectMapper taskProjectMapper,
                                    AgentProjectMapper projectMapper) {
        this.taskMapper = Objects.requireNonNull(taskMapper, "taskMapper 不能为空");
        this.taskProjectMapper = Objects.requireNonNull(taskProjectMapper, "taskProjectMapper 不能为空");
        this.projectMapper = Objects.requireNonNull(projectMapper, "projectMapper 不能为空");
    }

    @Override
    public AgentTaskDiffRespVO getTaskDiff(Long id) {
        AgentTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw exception(TASK_NOT_FOUND);
        }

        AgentTaskDiffRespVO response = new AgentTaskDiffRespVO();
        response.setTaskId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setTitle(task.getTitle());
        response.setStatus(task.getStatus());
        response.setTargetBranch(task.getTargetBranch());
        response.setRetryTimes(task.getRetryTimes());
        response.setCostMs(task.getCostMs());
        response.setStartedTime(task.getStartedTime());
        response.setFinishedTime(task.getFinishedTime());
        response.setExecutionGeneration(task.getExecutionGeneration());
        response.setDiffStat(task.getDiffStat());
        response.setExecutionLog(task.getExecutionLog());
        // 当前执行写回阶段不做日志截断，完整日志直接落库，因此标记与原始路径为空
        response.setLogTruncated(Boolean.FALSE);
        response.setOriginalLogPath(null);

        AgentTaskTestReportVO report = loadTestReport(task.getWorkspacePath());
        response.setTestReport(report);
        response.setBranches(buildBranches(task));
        response.setChangedFiles(resolveChangedFiles(task.getDiffStat(), report));
        return response;
    }

    private List<AgentTaskBranchInfoVO> buildBranches(AgentTaskDO task) {
        List<AgentTaskProjectDO> taskProjects = taskProjectMapper.selectListByTaskId(task.getId());
        if (taskProjects == null || taskProjects.isEmpty()) {
            return List.of();
        }
        List<AgentTaskBranchInfoVO> branches = new ArrayList<>(taskProjects.size());
        for (AgentTaskProjectDO taskProject : taskProjects) {
            AgentProjectDO project = projectMapper.selectByProjectCode(taskProject.getProjectCode());
            AgentTaskBranchInfoVO branch = new AgentTaskBranchInfoVO();
            branch.setProjectCode(taskProject.getProjectCode());
            branch.setProjectName(project == null ? null : project.getName());
            branch.setBaseBranch(taskProject.getBaseBranch());
            branch.setFeatureBranch(task.getTargetBranch());
            branch.setBranchUrl(project == null ? null : buildBranchUrl(project.getGitUrl(), task.getTargetBranch()));
            branch.setSubDir(taskProject.getSubDir());
            branch.setMergeStatus(taskProject.getMergeStatus());
            branch.setCommitHash(taskProject.getCommitHash());
            branches.add(branch);
        }
        return branches;
    }

    private AgentTaskTestReportVO loadTestReport(String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) {
            return null;
        }
        Path summaryPath;
        try {
            summaryPath = Path.of(workspacePath).resolve(WORKPAD_SUMMARY_RELATIVE_PATH);
        } catch (InvalidPathException ex) {
            log.debug("[TaskDiff] 工作区路径非法，跳过测试报告读取 workspacePath={}", workspacePath);
            return null;
        }
        if (!Files.isRegularFile(summaryPath)) {
            return null;
        }
        try {
            String json = Files.readString(summaryPath, StandardCharsets.UTF_8);
            return JsonUtils.parseObjectQuietly(json, AgentTaskTestReportVO.class);
        } catch (IOException ex) {
            log.warn("[TaskDiff] 读取测试报告失败 path={}", summaryPath, ex);
            return null;
        }
    }

    private List<String> resolveChangedFiles(String diffStat, AgentTaskTestReportVO report) {
        List<String> fromDiff = extractChangedFilesFromDiffStat(diffStat);
        if (!fromDiff.isEmpty()) {
            return fromDiff;
        }
        if (report != null && report.getModifiedFiles() != null && !report.getModifiedFiles().isEmpty()) {
            return new ArrayList<>(report.getModifiedFiles());
        }
        return List.of();
    }

    private List<String> extractChangedFilesFromDiffStat(String diffStat) {
        if (diffStat == null || diffStat.isBlank()) {
            return List.of();
        }
        // diffStat 可能是 git diff --stat 纯文本或尚未定义的结构，解析失败时回退到报告
        JsonNode root = JsonUtils.parseObjectQuietly(diffStat, JsonNode.class);
        if (root == null || root.isNull()) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                addFile(node, files);
            }
        } else if (root.isObject()) {
            JsonNode changedFiles = root.get("changedFiles");
            if (changedFiles == null || !changedFiles.isArray()) {
                changedFiles = root.get("files");
            }
            if (changedFiles != null && changedFiles.isArray()) {
                for (JsonNode node : changedFiles) {
                    addFile(node, files);
                }
            }
        }
        return files;
    }

    private void addFile(JsonNode node, List<String> files) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String value = node.asText();
            if (!value.isBlank()) {
                files.add(value);
            }
            return;
        }
        if (node.isObject()) {
            JsonNode path = node.get("path");
            if (path == null || !path.isTextual() || path.asText().isBlank()) {
                path = node.get("file");
            }
            if (path != null && path.isTextual() && !path.asText().isBlank()) {
                files.add(path.asText());
            }
        }
    }

    private String buildBranchUrl(String gitUrl, String branch) {
        if (gitUrl == null || gitUrl.isBlank() || branch == null || branch.isBlank()) {
            return null;
        }
        try {
            GitRepository repository = GitRepositoryParser.parse(gitUrl);
            String base = "https://" + repository.host() + "/" + repository.owner() + "/" + repository.name();
            return switch (repository.platform()) {
                case GITHUB -> base + "/tree/" + branch;
                case GITLAB -> base + "/-/tree/" + branch;
                case GITEA -> base + "/src/branch/" + branch;
            };
        } catch (RuntimeException ex) {
            log.debug("[TaskDiff] 仓库地址无法推导分支链接 gitUrl={}", gitUrl);
            return null;
        }
    }

}
