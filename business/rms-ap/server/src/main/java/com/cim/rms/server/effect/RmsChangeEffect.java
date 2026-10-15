package com.cim.rms.server.effect;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 配方变更效果登记（W11，Req 缺口 #7：变更效果跟踪挂钩）。
 *
 * <p>每次配方版本<b>生效</b>（签核批准自动激活 / 手动激活）落一行：从哪个版本切到哪个版本、
 * 谁在何时批准、参数级差异几项——供质量侧按「变更时点」回溯 SPC/Cpk 对比。
 * {@code spcRef} 为跨域查询引用占位（SPC 域接入后回填关联键，本期留空）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_change_effect")
public class RmsChangeEffect extends BaseDefData {

    @Column(name = "recipe_id", length = 64, nullable = false)
    private String recipeId;

    @Column(name = "recipe_code", length = 128)
    private String recipeCode;

    @Column(name = "from_version_id", length = 64)
    private String fromVersionId;

    @Column(name = "from_version_no")
    private Integer fromVersionNo;

    @Column(name = "to_version_id", length = 64, nullable = false)
    private String toVersionId;

    @Column(name = "to_version_no")
    private Integer toVersionNo;

    @Column(name = "activated_by", length = 64)
    private String activatedBy;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt = Instant.now();

    /** 相对上一生效版的参数级差异项数。 */
    @Column(name = "param_diff_count", nullable = false)
    private int paramDiffCount;

    @Column(name = "change_summary", length = 512)
    private String changeSummary;

    /** SPC/Cpk 查询引用占位（跨域接入后回填）。 */
    @Column(name = "spc_ref", length = 128)
    private String spcRef;
}
