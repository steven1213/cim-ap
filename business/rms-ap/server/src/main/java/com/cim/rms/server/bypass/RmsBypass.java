package com.cim.rms.server.bypass;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Bypass 豁免（FR-B1/B2/B3，Req 22/23）。
 *
 * <p>{@code scope}=DEVICE 表示豁免某设备所有配方的比对/参数检查；{@code scope}=RECIPE 表示豁免
 * 单个配方。{@code bypassType} 区分豁免范畴（比对 / 参数检查 / 两者）。含生效期与<b>必填原因</b>，
 * 到期自动失效（派生状态 ACTIVE/SCHEDULED/EXPIRED），变更入高敏感审计。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_bypass")
public class RmsBypass extends BaseDefData {

    /** DEVICE / RECIPE。 */
    @Column(name = "scope", length = 16, nullable = false)
    private String scope;

    /** 目标 id：scope=DEVICE 时为 deviceId，scope=RECIPE 时为 recipeId。 */
    @Column(name = "target_id", length = 64, nullable = false)
    private String targetId;

    /** 目标展示名（冗余，便于列表）。 */
    @Column(name = "target_label", length = 128)
    private String targetLabel;

    /** COMPARE / PARAM_CHECK / BOTH。 */
    @Column(name = "bypass_type", length = 16, nullable = false)
    private String bypassType = "BOTH";

    @Column(name = "reason", length = 512, nullable = false)
    private String reason;

    /** 手动开关（置 false 即撤销；配合生效期）。 */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "created_by", length = 64)
    private String createdBy;
}
