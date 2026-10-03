package cn.iocoder.yudao.module.agent.service.task;

import cn.iocoder.yudao.module.agent.controller.admin.task.vo.task.AgentTaskDiffRespVO;

/**
 * 任务结果读取 Service
 *
 * <p>负责聚合任务主表、任务-项目映射与项目资产，解析工作区内的
 * {@code .ai/workpad_summary.json}，并输出前端「查看结果」所需的完整响应。
 *
 * @author TaskForge
 */
public interface AgentTaskDiffService {

    /**
     * 读取任务结果（Diff/日志/测试报告/分支信息）。
     *
     * @param id 任务 ID
     * @return 任务结果，任务不存在时抛出业务异常
     */
    AgentTaskDiffRespVO getTaskDiff(Long id);

}
