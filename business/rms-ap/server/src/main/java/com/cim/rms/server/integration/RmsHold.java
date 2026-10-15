package com.cim.rms.server.integration;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Hold 记录（Req 21）：在线比对/EC-SV/Run 校验失败时按配置 Hold 配方（FR-C7）。
 * Hold / 解除 Hold 均留痕；{@code source} 记录触发来源。
 */
@Getter
@Setter
@Entity
@Table(name = "rms_hold")
public class RmsHold extends BaseDefData {

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "recipe_id", length = 64)
    private String recipeId;

    @Column(name = "recipe_code", length = 128)
    private String recipeCode;

    @Column(name = "reason", length = 512)
    private String reason;

    /** ONLINE_COMPARE / ECSV / RUN_CHECK / MANUAL。 */
    @Column(name = "source", length = 32, nullable = false)
    private String source;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "released_by", length = 64)
    private String releasedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "release_reason", length = 512)
    private String releaseReason;
}
