package cn.iocoder.yudao.module.agent.service.task;

/**
 * 重置外部资源清理委托
 *
 * <p>负责回收任务创建的工作区、分支等外部资源。清理必须幂等：资源已经
 * 不存在时直接返回，使重置流程仍能继续完成元数据清理，而不会因
 * “已删除”类错误中断。</p>
 *
 * @author TaskForge
 */
public interface ResetCleanupDelegate {

    /**
     * 幂等清理任务相关的外部资源。
     *
     * @param context 重置清理上下文
     */
    void cleanup(ResetCleanupContext context);

}
