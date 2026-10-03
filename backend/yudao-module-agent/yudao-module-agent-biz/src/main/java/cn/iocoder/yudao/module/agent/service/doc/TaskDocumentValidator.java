package cn.iocoder.yudao.module.agent.service.doc;

import cn.iocoder.yudao.module.agent.framework.workspace.git.GitRefs;
import cn.iocoder.yudao.module.agent.service.project.AgentProjectRef;
import cn.iocoder.yudao.module.agent.service.project.AgentProjectRefValidator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_BASE_BRANCH_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_FIELD_REQUIRED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_REPO_MODE_CONFLICT;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_REPO_MODE_MISSING;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_TARGET_BRANCH_INVALID;

/**
 * 任务文档校验器
 *
 * <p>在投递（submit）与暂停恢复（resume）前，对解析后的 {@link TaskDocument} 执行业务校验：
 * 必填字段、单仓/多仓仓库配置、目标分支与基线分支，以及项目引用的存在性、
 * 启用状态、基线分支与子目录映射冲突（委托给 {@link AgentProjectRefValidator}）。
 *
 * @author TaskForge
 */
@Component
public class TaskDocumentValidator {

    @Resource
    private AgentProjectRefValidator projectRefValidator;

    /**
     * 校验任务文档，任一非法项都会抛出 {@link cn.iocoder.yudao.framework.common.exception.ServiceException}。
     *
     * @param document 解析后的任务文档
     */
    public void validate(TaskDocument document) {
        if (document == null || document.getFrontMatter() == null) {
            throw exception(DOCUMENT_FIELD_REQUIRED, "frontMatter");
        }
        TaskFrontMatter frontMatter = document.getFrontMatter();

        requireNotBlank(frontMatter.getTaskId(), "taskId");
        requireNotBlank(frontMatter.getTitle(), "title");
        requireNotBlank(frontMatter.getTargetBranch(), "targetBranch");
        if (frontMatter.getTimeoutMinutes() == null) {
            throw exception(DOCUMENT_FIELD_REQUIRED, "timeoutMinutes");
        }

        if (!GitRefs.isValidBranchName(frontMatter.getTargetBranch())) {
            throw exception(DOCUMENT_TARGET_BRANCH_INVALID, frontMatter.getTargetBranch());
        }

        boolean multiRepo = frontMatter.getProjects() != null && !frontMatter.getProjects().isEmpty();
        boolean singleRepo = isNotBlank(frontMatter.getRepoUrl()) || isNotBlank(frontMatter.getBaseBranch());
        if (multiRepo && singleRepo) {
            throw exception(DOCUMENT_REPO_MODE_CONFLICT);
        }
        if (!multiRepo && !singleRepo) {
            throw exception(DOCUMENT_REPO_MODE_MISSING);
        }

        if (multiRepo) {
            validateProjects(frontMatter.getProjects());
        } else {
            requireNotBlank(frontMatter.getRepoUrl(), "repoUrl");
            requireNotBlank(frontMatter.getBaseBranch(), "baseBranch");
            if (!GitRefs.isValidBranchName(frontMatter.getBaseBranch())) {
                throw exception(DOCUMENT_BASE_BRANCH_INVALID, frontMatter.getBaseBranch());
            }
        }
    }

    private void validateProjects(List<TaskProjectRef> projects) {
        List<AgentProjectRef> refs = new ArrayList<>(projects.size());
        for (int i = 0; i < projects.size(); i++) {
            TaskProjectRef project = projects.get(i);
            String path = "projects[" + i + "]";
            requireNotBlank(project.getCode(), path + ".code");
            requireNotBlank(project.getBaseBranch(), path + ".baseBranch");
            requireNotBlank(project.getSubDir(), path + ".subDir");
            refs.add(AgentProjectRef.builder()
                    .projectCode(project.getCode())
                    .baseBranch(project.getBaseBranch())
                    .subDir(project.getSubDir())
                    .build());
        }
        projectRefValidator.validate(refs);
    }

    private void requireNotBlank(String value, String field) {
        if (!isNotBlank(value)) {
            throw exception(DOCUMENT_FIELD_REQUIRED, field);
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
