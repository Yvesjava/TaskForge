package cn.iocoder.yudao.module.agent.framework.notice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 待验收卡片中的单仓分支引用
 *
 * @author TaskForge
 */
public record NoticeBranchRef(String projectCode, String branch, String url) {

    /**
     * 转为稳定键名的结构化视图，供卡片 {@code branches} 字段使用。
     */
    public Map<String, String> toView() {
        Map<String, String> view = new LinkedHashMap<>();
        view.put("projectCode", projectCode);
        view.put("branch", branch);
        view.put("url", url);
        return view;
    }

}
