package cn.iocoder.yudao.module.agent.service.merge;

import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiOperator;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitApiOperatorRegistry;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeRequestRef;
import cn.iocoder.yudao.module.agent.framework.git.platform.GitMergeStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 多仓合并前预检编排。
 *
 * <p>按任务涉及的全部仓库逐一检查目标分支可合并性，任一仓冲突、检查失败或平台错误
 * 都会导致整体未通过。预检阶段只创建/查询 MR/PR，绝不执行合并动作；真正合并由后续
 * 合并编排在整体通过后统一执行，从而保证“全有或全无”。</p>
 *
 * @author TaskForge
 */
@Service
public class MergePrecheckService {

    private final GitApiOperatorRegistry operatorRegistry;

    public MergePrecheckService(GitApiOperatorRegistry operatorRegistry) {
        this.operatorRegistry = Objects.requireNonNull(operatorRegistry, "operatorRegistry 不能为空");
    }

    public MergePrecheckResult precheck(List<MergePrecheckTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("合并预检目标不能为空");
        }
        List<MergePrecheckItemResult> items = new ArrayList<>(targets.size());
        for (MergePrecheckTarget target : targets) {
            items.add(precheckOne(target));
        }
        return new MergePrecheckResult(items);
    }

    private MergePrecheckItemResult precheckOne(MergePrecheckTarget target) {
        GitMergeRequestRef ref = target.ref();
        try {
            GitApiOperator operator = operatorRegistry.get(target.repository().platform());
            if (ref == null) {
                ref = operator.createMergeRequest(target.repository(), target.credentials(), target.request());
            }
            GitMergeStatus status = operator.queryMergeRequest(target.repository(), target.credentials(), ref);
            return evaluate(target.repoKey(), ref, status);
        } catch (RuntimeException ex) {
            return MergePrecheckItemResult.failed(target.repoKey(), ref, MergePrecheckFailure.ERROR,
                    null, null, reasonOf(ex));
        }
    }

    private MergePrecheckItemResult evaluate(String repoKey, GitMergeRequestRef ref, GitMergeStatus status) {
        if (status.isMerged()) {
            return MergePrecheckItemResult.passed(repoKey, ref, status, "已合并");
        }
        if (Boolean.TRUE.equals(status.mergeable())) {
            return MergePrecheckItemResult.passed(repoKey, ref, status, "");
        }
        if (Boolean.FALSE.equals(status.mergeable())) {
            return MergePrecheckItemResult.failed(repoKey, ref, MergePrecheckFailure.CONFLICT,
                    status.state(), Boolean.FALSE, "目标分支存在冲突，无法自动合并");
        }
        return MergePrecheckItemResult.failed(repoKey, ref, MergePrecheckFailure.CHECK_FAILED,
                status.state(), null, "平台未返回可合并性，无法确认可以安全合并");
    }

    private String reasonOf(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName() : ex.getMessage();
    }

}
