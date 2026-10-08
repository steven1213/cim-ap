package com.cim.mq.outbox;

import java.util.List;

/**
 * 发件箱存储契约（供应商中立）。
 *
 * <p>默认 JDBC 实现（{@link JdbcOutboxStore}）；无数据库场景可换成内存/文件实现。
 * 关键是「与业务表同事务写入」——由调用方在 {@code @Transactional} 内 save。</p>
 */
public interface OutboxStore {

    /** 写入一条待投递记录（应在业务事务内调用）。 */
    void save(OutboxRecord record);

    /** 拉取一批可投递记录（PENDING 且 nextRetryAt 到期），按时间升序。 */
    List<OutboxRecord> claimBatch(int limit);

    /** 标记已成功投递（可删除或置 SENT）。 */
    void markSent(String id);

    /** 标记投递失败，更新重试时间与错误。 */
    void markFailed(String id, int attemptCount, String error, java.time.Instant nextRetryAt);

    /** 删除已完成的记录（可选）。 */
    void delete(String id);
}
