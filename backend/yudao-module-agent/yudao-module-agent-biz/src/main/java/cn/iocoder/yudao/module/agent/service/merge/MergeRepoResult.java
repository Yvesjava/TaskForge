package cn.iocoder.yudao.module.agent.service.merge;

/**
 * 单个仓库的合并结果。
 *
 * @param repoKey     仓库标识（项目代号）
 * @param merged      是否合并成功
 * @param mergeStatus 回写到 agent_task_project.merge_status 的值
 * @param commitSha   合并提交 SHA（未合并时为空）
 * @param webUrl      MR/PR Web 地址
 * @param message     平台返回的提示/错误信息
 * @author TaskForge
 */
public record MergeRepoResult(String repoKey, boolean merged, String mergeStatus,
                              String commitSha, String webUrl, String message) {

    public static final String MERGE_STATUS_MERGED = "MERGED";
    public static final String MERGE_STATUS_FAILED = "FAILED";

    public MergeRepoResult {
        if (repoKey == null || repoKey.isBlank()) {
            throw new IllegalArgumentException("repoKey 不能为空");
        }
        mergeStatus = mergeStatus == null ? "" : mergeStatus;
        commitSha = commitSha == null ? "" : commitSha;
        webUrl = webUrl == null ? "" : webUrl;
        message = message == null ? "" : message;
    }

    public static MergeRepoResult merged(String repoKey, String commitSha, String webUrl) {
        return new MergeRepoResult(repoKey, true, MERGE_STATUS_MERGED, commitSha, webUrl, "");
    }

    public static MergeRepoResult failed(String repoKey, String mergeStatus, String webUrl, String message) {
        return new MergeRepoResult(repoKey, false, mergeStatus, "", webUrl, message);
    }

}
