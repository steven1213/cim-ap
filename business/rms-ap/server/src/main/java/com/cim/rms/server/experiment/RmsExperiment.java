package com.cim.rms.server.experiment;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 实验配方通道（W11，Req 缺口 #6：DOE 临时配方受控通道）。
 *
 * <p>价值：工程师不再「绕过 RMS 直改机台」做工艺实验——实验版本经审批后，在
 * <b>指定机台 × 指定版本 × 有效窗口</b>内放行 IF-E2 下发（绕过「仅 ACTIVE 可下发」闸门），
 * 窗口关闭/实验关闭后自动失效；全程留痕审计。到期判定读取时派生（无定时任务）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_experiment")
public class RmsExperiment extends BaseDefData {

    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "recipe_id", length = 64, nullable = false)
    private String recipeId;

    @Column(name = "recipe_code", length = 128)
    private String recipeCode;

    @Column(name = "version_id", length = 64, nullable = false)
    private String versionId;

    @Column(name = "version_no")
    private Integer versionNo;

    @Column(name = "device_id", length = 64, nullable = false)
    private String deviceId;

    @Column(name = "device_code", length = 128)
    private String deviceCode;

    @Column(name = "reason", length = 512)
    private String reason;

    /** REQUESTED / APPROVED / REJECTED / CLOSED。 */
    @Column(name = "status", length = 32, nullable = false)
    private String status = "REQUESTED";

    @Column(name = "requested_by", length = 64)
    private String requestedBy;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "close_reason", length = 512)
    private String closeReason;
}
