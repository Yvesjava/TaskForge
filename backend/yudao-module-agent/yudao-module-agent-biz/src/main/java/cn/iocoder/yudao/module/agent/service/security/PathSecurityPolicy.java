package cn.iocoder.yudao.module.agent.service.security;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.module.agent.framework.workspace.git.GitRefs;

import java.nio.file.Files;
import java.nio.file.Path;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.SECURITY_BRANCH_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.SECURITY_PATH_ESCAPE;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.SECURITY_PROJECT_CODE_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.SECURITY_SUB_DIR_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.SECURITY_SYMLINK_NOT_ALLOWED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.SECURITY_TASK_NO_INVALID;

/**
 * 路径安全策略
 *
 * <p>把任务编号、项目代号、分支与子目录映射的合法性规则集中到一处，统一拒绝
 * 路径穿越、绝对路径、分隔符混用、控制字符、符号链接与工作区越界。该工具被
 * {@link SecurityPolicy} 调用，规则与工作区编排保持一致。</p>
 *
 * @author TaskForge
 */
public final class PathSecurityPolicy {

    /**
     * 任务编号/项目代号允许的目录名模式：字母数字开头，只含字母、数字、点、下划线、连字符
     */
    private static final String IDENTIFIER_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]*";

    private static final int MAX_SEGMENT_LENGTH = 64;

    private PathSecurityPolicy() {
    }

    /**
     * 校验任务编号，非法时抛出 {@link cn.iocoder.yudao.framework.common.exception.ServiceException}。
     */
    public static void requireSafeTaskNo(String taskNo) {
        requireSafeIdentifier(taskNo, SECURITY_TASK_NO_INVALID);
    }

    /**
     * 校验项目代号，非法时抛出 {@link cn.iocoder.yudao.framework.common.exception.ServiceException}。
     */
    public static void requireSafeProjectCode(String projectCode) {
        requireSafeIdentifier(projectCode, SECURITY_PROJECT_CODE_INVALID);
    }

    /**
     * 校验 Git 分支名，非法时抛出 {@link cn.iocoder.yudao.framework.common.exception.ServiceException}。
     */
    public static void requireSafeBranch(String branch) {
        if (!GitRefs.isValidBranchName(branch)) {
            throw exception(SECURITY_BRANCH_INVALID, branch);
        }
    }

    /**
     * 校验工作区子目录映射，非法时抛出 {@link cn.iocoder.yudao.framework.common.exception.ServiceException}。
     */
    public static void requireSafeSubDir(String subDir) {
        if (subDir == null) {
            throw exception(SECURITY_SUB_DIR_INVALID, subDir);
        }
        String value = subDir.trim();
        if (value.isEmpty() || value.length() > MAX_SEGMENT_LENGTH
                || value.startsWith("/") || value.endsWith("/") || value.contains("\\")) {
            throw exception(SECURITY_SUB_DIR_INVALID, subDir);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x1F || c == 0x7F) {
                throw exception(SECURITY_SUB_DIR_INVALID, subDir);
            }
        }
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw exception(SECURITY_SUB_DIR_INVALID, subDir);
            }
        }
    }

    /**
     * 在根目录下解析子目录，并校验结果仍在根目录内且不含符号链接组件。
     */
    public static Path resolveSubDir(Path root, String subDir) {
        requireSafeSubDir(subDir);
        Path target = root.resolve(subDir).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw exception(SECURITY_PATH_ESCAPE, target);
        }
        rejectSymlinkComponents(root, target);
        return target;
    }

    private static void requireSafeIdentifier(String value, ErrorCode code) {
        if (value == null) {
            throw exception(code, value);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_SEGMENT_LENGTH
                || !trimmed.matches(IDENTIFIER_PATTERN)
                || trimmed.contains("..") || trimmed.endsWith(".")) {
            throw exception(code, value);
        }
    }

    private static void rejectSymlinkComponents(Path root, Path target) {
        Path baseAbs = root.toAbsolutePath().normalize();
        Path current = target.toAbsolutePath().normalize();
        while (current != null && !current.equals(baseAbs)) {
            if (Files.isSymbolicLink(current)) {
                throw exception(SECURITY_SYMLINK_NOT_ALLOWED, current);
            }
            current = current.getParent();
        }
    }

}
