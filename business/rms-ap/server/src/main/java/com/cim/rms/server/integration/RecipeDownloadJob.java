package com.cim.rms.server.integration;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * IF-E2 配方下发任务（Req §5.3 {@code RecipeDownloadProgress} 阶段机的持久化载体）。
 *
 * <p>一次「RMS → EAP → 机台」的下发请求落一条任务，以 {@code correlation_id} 唯一标识并作为
 * 幂等键：后续每个阶段回执都按它推进一步，从而把下发从「一次性返回 QUEUED」变成<b>可跟踪、
 * 可回执、可回读校验</b>的闭环流程（Req 21「有传输凭证、有失败重试队列」）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_recipe_download_job")
public class RecipeDownloadJob extends BaseDefData {

    /** 幂等键 / 全链路关联 id（EAP 回执按它定位任务）。 */
    @Column(name = "correlation_id", length = 128, nullable = false)
    private String correlationId;

    @Column(name = "device_id", length = 64, nullable = false)
    private String deviceId;

    @Column(name = "recipe_id", length = 64, nullable = false)
    private String recipeId;

    @Column(name = "version_id", length = 64)
    private String versionId;

    /** 机台侧目标配方名（PPID）。 */
    @Column(name = "ppid_on_tool", length = 128)
    private String ppidOnTool;

    /** 批量下发批次号（{@code BATCH} 模式必填）。 */
    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "lot_id", length = 64)
    private String lotId;

    @Column(name = "requestor", length = 128)
    private String requestor;

    /** {@code IMMEDIATE} / {@code ON_IDLE} / {@code BATCH}（Req 40）。 */
    @Column(name = "download_mode", length = 16, nullable = false)
    private String downloadMode = "IMMEDIATE";

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 24, nullable = false)
    private DownloadStage stage = DownloadStage.QUEUED;

    /** 机台确认码（S7F4 ACKC7 语义，如 {@code ACK} / {@code NAK}）。 */
    @Column(name = "tool_ack", length = 32)
    private String toolAck;

    @Column(name = "error_desc", length = 512)
    private String errorDesc;

    /** 下发 Body 的 SHA-256（回读比对的依据）。 */
    @Column(name = "body_hash", length = 128)
    private String bodyHash;

    /** 是否已通过回读校验（{@code VERIFIED} 的证据位）。 */
    @Column(name = "verified", nullable = false)
    private boolean verified = false;
}
