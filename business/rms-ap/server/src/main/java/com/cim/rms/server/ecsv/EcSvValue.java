package com.cim.rms.server.ecsv;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** EC/SV 上传值与比对结果（FR-D2/D4，Req 43/45）：在线 Run 货时 EAP 主动校验的留痕。 */
@Getter
@Setter
@Entity
@Table(name = "ecsv_value")
public class EcSvValue extends BaseDefData {

    @Column(name = "device_id", length = 64, nullable = false)
    private String deviceId;

    @Column(name = "ecsv_type", length = 8, nullable = false)
    private String ecsvType;

    @Column(name = "param_key", length = 128, nullable = false)
    private String paramKey;

    @Column(name = "actual_value", length = 128)
    private String actualValue;

    /** 命中的规格 id（可为空=无规格，视为不合规告警）。 */
    @Column(name = "spec_id", length = 64)
    private String specId;

    /** PASS / FAIL / NO_SPEC。 */
    @Column(name = "result", length = 16, nullable = false)
    private String result = "PASS";

    @Column(name = "detail", length = 512)
    private String detail;

    @Column(name = "captured_at")
    private Instant capturedAt;
}
