package com.cim.system.log;

import com.cim.core.model.BaseEventData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志（design.md §2.10 / plan.md T6.4）——<b>本身即流水</b>，故继承
 * {@link BaseEventData} 而非 {@code BaseDefData}，无独立历史表。
 *
 * <p>复用基类字段：{@code eventType} = 动作类型、{@code bizKey} = 目标业务键。
 * <b>不记录请求体与响应体</b>——这是本框架的默认脱敏策略：既避免口令/敏感数据落库，
 * 也避免日志表被大报文撑爆；需要还原现场时依赖 {@code trx_id} 关联业务历史表。</p>
 *
 * <p>写入入口见 {@code OperationLogService} 与 {@code @OperationLogged} 切面。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "sys_operation_log")
public class OperationLog extends BaseEventData {

    /** 模块（如 {@code sys}、{@code mes}）。 */
    @Column(name = "module", length = 64)
    private String module;

    /** 动作（如 {@code save}、{@code remove}）。 */
    @Column(name = "action", length = 64)
    private String action;

    /** 目标类型（如 {@code SysUser}）。 */
    @Column(name = "target_type", length = 128)
    private String targetType;

    /** 请求 URI。 */
    @Column(name = "request_uri", length = 512)
    private String requestUri;

    /** HTTP 方法。 */
    @Column(name = "http_method", length = 16)
    private String httpMethod;

    /** 是否成功。 */
    @Column(name = "success", nullable = false)
    private Boolean success = Boolean.TRUE;

    /** 失败原因（截断存储，避免异常堆栈撑爆列）。 */
    @Column(name = "error_msg", length = 512)
    private String errorMsg;

    /** 耗时（毫秒）。 */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** 客户端 IP。 */
    @Column(name = "client_ip", length = 64)
    private String clientIp;
}
