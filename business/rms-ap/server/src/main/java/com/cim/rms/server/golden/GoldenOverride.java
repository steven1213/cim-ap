package com.cim.rms.server.golden;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Golden 机差覆盖层（FR-G2，Req 31）：设备在 Golden 比对时允许偏离的参数白名单/范围。
 *
 * <p>比对判定 = Golden Spec <b>合并</b> 本覆盖层（后者放宽该设备的允许区间）。按已拍板 D3，
 * 覆盖层变更<b>走签核</b>：{@code status}=PENDING → APPROVED/REJECTED。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "golden_override")
public class GoldenOverride extends BaseDefData {

    @Column(name = "device_id", length = 64, nullable = false)
    private String deviceId;

    /** 可为空：为空表示该设备所有配方通用。 */
    @Column(name = "recipe_id", length = 64)
    private String recipeId;

    /** 偏离的参数键。 */
    @Column(name = "param_key", length = 128)
    private String paramKey;

    @Column(name = "allow_min", length = 64)
    private String allowMin;

    @Column(name = "allow_max", length = 64)
    private String allowMax;

    @Column(name = "reason", length = 512)
    private String reason;

    /** PENDING / APPROVED / REJECTED（D3：走签核）。 */
    @Column(name = "status", length = 16, nullable = false)
    private String status = "PENDING";

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;
}
