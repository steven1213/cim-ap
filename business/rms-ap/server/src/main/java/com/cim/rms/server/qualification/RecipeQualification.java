package com.cim.rms.server.qualification;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 配方-机台资格（FR-Q1~Q4，行业核心：物视角「哪个配方允许跑在哪台机台」）。
 *
 * <p>与 FR-P4（人↔设备授权）互补。{@code versionPolicy}=LATEST_ACTIVE 表示随生效版本联动
 * （FR-Q3：新版本默认继承资格），=EXACT_VERSION 需绑定 {@code versionId} 重新认定。
 * 未登记资格默认 <b>NOT_QUALIFIED</b>（D8）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "recipe_qualification")
public class RecipeQualification extends BaseDefData {

    @Column(name = "recipe_id", length = 64, nullable = false)
    private String recipeId;

    /** LATEST_ACTIVE / EXACT_VERSION。 */
    @Column(name = "version_policy", length = 16, nullable = false)
    private String versionPolicy = "LATEST_ACTIVE";

    /** EXACT_VERSION 时的具体版本。 */
    @Column(name = "version_id", length = 64)
    private String versionId;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "device_type_id", length = 64)
    private String deviceTypeId;

    @Column(name = "area_id", length = 64)
    private String areaId;

    /** 审批依据（谁批准、依据单号等）。 */
    @Column(name = "basis", length = 512)
    private String basis;

    /** GRANTED / REVOKED（吊销即时生效）。 */
    @Column(name = "status", length = 16, nullable = false)
    private String status = "GRANTED";

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;
}
