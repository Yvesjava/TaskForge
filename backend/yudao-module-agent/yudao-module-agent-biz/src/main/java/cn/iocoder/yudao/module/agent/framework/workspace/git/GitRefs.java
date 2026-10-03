package cn.iocoder.yudao.module.agent.framework.workspace.git;

/**
 * Git 引用与路径安全校验工具
 *
 * <p>工作区编排把分支名拼入 Git 命令，因此必须统一拒绝
 * 非法字符，防止命令注入或路径逃逸。
 *
 * @author TaskForge
 */
public final class GitRefs {

    /**
     * Git 分支名中禁止出现的特殊字符（不含控制字符与空格，单独判断）
     */
    private static final String GIT_BRANCH_FORBIDDEN_CHARS = "~^:?*[\\";

    private GitRefs() {
    }

    /**
     * 判断是否为合法的 Git 分支名。
     *
     * @param branch 分支名
     * @return 是否合法
     */
    public static boolean isValidBranchName(String branch) {
        if (branch == null) {
            return false;
        }
        String value = branch.trim();
        if (value.isEmpty() || value.length() > 64) {
            return false;
        }
        // Git check-ref-format 约束：不能以 - 或 / 开头，不能以 /、.、.lock 结尾
        if (value.startsWith("-") || value.startsWith("/")
                || value.endsWith("/") || value.endsWith(".") || value.endsWith(".lock")) {
            return false;
        }
        if (value.equals("@") || value.contains("..") || value.contains("@{") || value.contains("//")) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // 禁止空格、ASCII 控制字符以及 Git 保留特殊字符
            if (c <= 0x20 || c == 0x7F || GIT_BRANCH_FORBIDDEN_CHARS.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

}
