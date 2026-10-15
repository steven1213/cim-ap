package com.cim.rms.server.integration;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Alarm 记录（IF-A1，Req 21/45）：向报警系统发出的报警事件留痕。
 *
 * <p>{@code alarmCode} 段位集中登记（如 RMS_COMPARE_FAIL / RMS_DRIFT / RMS_HOLD /
 * RMS_NOT_QUALIFIED / RMS_ECSV_FAIL），语义对齐设备报警体系（SECS S5F1 思路）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_alarm")
public class RmsAlarm extends BaseDefData {

    @Column(name = "alarm_code", length = 64, nullable = false)
    private String alarmCode;

    /** CRITICAL / MAJOR / MINOR。 */
    @Column(name = "severity", length = 16, nullable = false)
    private String severity;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "lot_id", length = 64)
    private String lotId;

    @Column(name = "recipe_code", length = 128)
    private String recipeCode;

    @Column(name = "message", length = 512)
    private String message;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged = false;
}
