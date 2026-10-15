package com.cim.rms.server.integration;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 接口幂等去重记录（Req §5.2 IF-COM「幂等」）。
 *
 * <p>EAP/MES 侧因网络超时/重连会重发同一请求；若不做去重，重试会重复执行有副作用的业务动作
 * （重复建配方版本、重复 Hold、重复报警）。本表以 {@code (scope, idem_key)} 唯一键记录<b>首次</b>
 * 请求的结果快照（{@code response_json}）：再次收到同键请求直接回放该快照，不再执行业务。</p>
 *
 * <p>{@code scope} 取接口名（{@code UPLOAD_DOWNLOAD_ONLINE_COMPARE_RUN_CHECK}），
 * {@code idemKey} 取请求的 {@code correlationId}（缺省时退化为内容哈希）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_if_idempotency")
public class IntegrationIdempotency extends BaseDefData {

    /** 接口作用域（隔离不同接口的相同 correlationId）。 */
    @Column(name = "scope", length = 32, nullable = false)
    private String scope;

    /** 幂等键（EAP/MES 请求的 correlationId）。 */
    @Column(name = "idem_key", length = 160, nullable = false)
    private String idemKey;

    /** 首次执行的结果码（如 ACCEPTED / PASS / QUEUED / QUALIFIED）。 */
    @Column(name = "result_code", length = 32)
    private String resultCode;

    /** 首次执行的回执快照（JSON），重试时原样回放。 */
    @Lob
    @Column(name = "response_json")
    private String responseJson;
}
