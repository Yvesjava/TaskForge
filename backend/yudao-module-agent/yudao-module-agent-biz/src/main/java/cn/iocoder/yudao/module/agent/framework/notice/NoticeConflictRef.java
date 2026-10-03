package cn.iocoder.yudao.module.agent.framework.notice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 合并冲突卡片中的单仓冲突引用
 *
 * @author TaskForge
 */
public record NoticeConflictRef(String projectCode, String baseBranch, String mergeStatus) {

    /**
     * 转为稳定键名的结构化视图，供卡片 {@code conflicts} 字段使用。
     */
    public Map<String, String> toView() {
        Map<String, String> view = new LinkedHashMap<>();
        view.put("projectCode", projectCode);
        view.put("baseBranch", baseBranch);
        view.put("mergeStatus", mergeStatus);
        return view;
    }

}
