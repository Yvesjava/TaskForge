package cn.iocoder.yudao.module.agent.service.project;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentProjectDO;
import cn.iocoder.yudao.module.agent.dal.mysql.AgentProjectMapper;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitRefs;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.PROJECT_REF_BASE_BRANCH_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.PROJECT_REF_DISABLED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.PROJECT_REF_NOT_FOUND;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.PROJECT_REF_SUB_DIR_CONFLICT;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.PROJECT_REF_SUB_DIR_INVALID;

/**
 * 任务引用项目校验器
 *
 * <p>任务通过项目编码引用代码仓，在投递（submit）与恢复排队（resume）前统一执行：
 * <ul>
 *   <li>项目代号必须存在且处于启用状态；</li>
 *   <li>基线分支必须是合法的 Git 分支名；</li>
 *   <li>子目录映射在同一任务内不得冲突。</li>
 * </ul>
 *
 * @author TaskForge
 */
@Component
public class AgentProjectRefValidator {

    @Resource
    private AgentProjectMapper projectMapper;

    /**
     * 校验任务引用的一组项目映射，任一非法项都会抛出 {@link cn.iocoder.yudao.framework.common.exception.ServiceException}。
     *
     * @param refs 任务引用的项目映射列表，允许为空
     */
    public void validate(List<AgentProjectRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }

        Set<String> seenSubDirs = new HashSet<>();
        for (AgentProjectRef ref : refs) {
            validateRef(ref, seenSubDirs);
        }
    }

    private void validateRef(AgentProjectRef ref, Set<String> seenSubDirs) {
        String projectCode = trimToNull(ref.getProjectCode());
        AgentProjectDO project = projectMapper.selectByProjectCode(projectCode);
        if (project == null) {
            throw exception(PROJECT_REF_NOT_FOUND, projectCode);
        }
        if (!CommonStatusEnum.ENABLE.getStatus().equals(project.getStatus())) {
            throw exception(PROJECT_REF_DISABLED, projectCode);
        }
        if (!GitRefs.isValidBranchName(ref.getBaseBranch())) {
            throw exception(PROJECT_REF_BASE_BRANCH_INVALID, projectCode, ref.getBaseBranch());
        }

        String subDir = normalizeSubDir(ref.getSubDir());
        if (subDir == null) {
            throw exception(PROJECT_REF_SUB_DIR_INVALID, projectCode, ref.getSubDir());
        }
        if (!seenSubDirs.add(subDir)) {
            throw exception(PROJECT_REF_SUB_DIR_CONFLICT, subDir);
        }
    }

    /**
     * 规范化子目录名，非法时返回 {@code null}。用于后续冲突判断。
     */
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

}
