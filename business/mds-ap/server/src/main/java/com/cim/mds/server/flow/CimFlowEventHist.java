package com.cim.mds.server.flow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 流程域事件历史行（V24，P3-2 拍板项 D）——动作级事件，一张通用表承载 flow 域全部动作。
 *
 * <p>不继承 {@code BaseDefData}：历史行是 append-only 事实，无乐观锁/逻辑删除/审计维护语义；
 * id 由 {@code IdGenerator} 在服务层显式生成（与平台时间有序 ID 同源）。字段口径吸收平台
 * Hist 约定：flow_id（biz_id 风格）/ action（op_type 风格）/ actor（operator 风格）/
 * event_time（op_time 风格）；{@code detail} 为变更摘要 JSON（计数/关键码，非全行快照）。</p>
 *
 * <p>写点清单见 {@link FlowEventService}；历史写入失败只告警不阻断主流程。</p>
 */
@Entity
@Table(name = "cim_flow_event_hist")
@Getter
@Setter
public class CimFlowEventHist {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    /** 流程行 id（查询锚点：GET /flows/{id}/events）。 */
    @Column(name = "flow_id", length = 64, nullable = false, updatable = false)
    private String flowId;

    /** 流程编码（冗余存码，行自解释；copy/import 事件里即新版本码）。 */
    @Column(name = "flow_code", length = 64, nullable = false, updatable = false)
    private String flowCode;

    /** 事件主体类型：FLOW / ASSIGN / ATTR / QTIME / STEP。 */
    @Column(name = "entity_type", length = 32, nullable = false, updatable = false)
    private String entityType;

    /** 动作码：CREATE/COPY/UPDATE/DELETE/FREEZE/UNFREEZE/ACTIVATE/DISABLE/SAVE_XML/REFRESH_OP/IMPORT/ASSIGN_REPLACE/ATTR_REPLACE。 */
    @Column(name = "action", length = 32, nullable = false, updatable = false)
    private String action;

    /** 操作人（EditLockSupport.currentUser()；系统动作为 system）。 */
    @Column(name = "actor", length = 64, updatable = false)
    private String actor;

    /** 变更摘要 JSON（动作级：计数/关键码/参数快照）。 */
    @Column(name = "detail", columnDefinition = "CLOB")
    private String detail;

    /** 事件时间。 */
    @Column(name = "event_time", nullable = false, updatable = false)
    private LocalDateTime eventTime;
}
