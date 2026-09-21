package com.cim.core.model;

import com.cim.core.event.EntityLifecycleCallbacks;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 历史表基类：为**每一张**实体历史表提供公共列，**不含任何业务字段**
 * （业务字段由各实体历史表自己持有，与主表同构）。
 *
 * <p>核心原则：一个需要历史记录的实体 = 一张专属历史表，{@code {X}Hist} / {@code {X}StateLog}
 * 与主表一一对应且结构同构，因此可按业务字段建索引、做字段级审计检索、按时间分区归档，
 * 故障域隔离在单个实体内（见 README §9）。</p>
 *
 * <p>主键刻意采用**独立应用层 {@code historyId}**，而非「业务 PK + timeKey」复合主键，
 * 以消除同毫秒并发写的主键冲突（见 README §9.1）。</p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseHistoryData implements Serializable {

    /** 历史行主键（应用层时间有序 ID，与主表同策略，见 README §3）。 */
    @Id
    @Column(name = "history_id", length = 64, updatable = false, nullable = false)
    private String historyId;

    /** 对应主表业务主键（字符串化），普通索引列。 */
    @Column(name = "biz_id", length = 64, nullable = false)
    private String bizId;

    /** 操作类型：I / U / D。 */
    @Column(name = "op_type", length = 8, nullable = false)
    private String opType;

    /** 操作时间（普通索引列，非主键组成部分）。 */
    @Column(name = "op_time", nullable = false)
    private LocalDateTime opTime;

    /** 操作人（自动取 SecurityContext，见 README §5 / §9）。 */
    @Column(name = "operator", length = 64)
    private String operator;

    /** 事务号，与审计基类同源。 */
    @Column(name = "trx_id", length = 64)
    private String trxId;

    /** 业务版本快照（对应主表 revision）。 */
    @Column(name = "revision")
    private Long revision;

    /** 字段级变更集 JSON（仅变更字段的 diff）。 */
    @Lob
    @Column(name = "change_set_json")
    private String changeSetJson;

    /** 租户 ID。 */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /** JPA 回调：插入前生成 {@code historyId} 并补默认 {@code opTime}。 */
    @PrePersist
    protected void cimOnPrePersist() {
        EntityLifecycleCallbacks.beforeInsert(this);
    }
}
