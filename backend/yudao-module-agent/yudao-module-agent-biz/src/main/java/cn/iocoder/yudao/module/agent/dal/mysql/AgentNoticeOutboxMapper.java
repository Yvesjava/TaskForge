package cn.iocoder.yudao.module.agent.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.agent.dal.dataobject.AgentNoticeOutboxDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知投递 Outbox Mapper
 *
 * @author TaskForge
 */
@Mapper
public interface AgentNoticeOutboxMapper extends BaseMapperX<AgentNoticeOutboxDO> {

    /**
     * 扫描到期可重试的 Outbox 记录，命中索引 idx_outbox_dispatch。
     *
     * @param limit 单轮最多处理条数
     * @param now   当前时间
     * @return 到期且未达终态的记录，按重试时间升序
     */
    List<AgentNoticeOutboxDO> selectDueOutbox(@Param("limit") int limit,
                                              @Param("now") LocalDateTime now);

    /**
     * 回写一次投递结果：成功置为 SENT，失败置为 FAILED 并推进重试时间/错误信息。
     *
     * @param id            记录 ID
     * @param status        结果状态（SENT/FAILED）
     * @param retryCount    累计重试次数
     * @param nextRetryTime 下次重试时间；终态为 NULL
     * @param lastError     最近一次失败原因（已脱敏）
     * @return 影响行数
     */
    int updateOutboxResult(@Param("id") Long id,
                           @Param("status") String status,
                           @Param("retryCount") Integer retryCount,
                           @Param("nextRetryTime") LocalDateTime nextRetryTime,
                           @Param("lastError") String lastError);

}
