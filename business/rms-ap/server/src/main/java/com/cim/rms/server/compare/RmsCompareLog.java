package com.cim.rms.server.compare;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 比对校验日志（Req.md FR-C3/C8、Req 17/18）。
 *
 * <p>每次比对运行落一条记录：批次（{@code batchId}，批量比对共享）、比对方式（{@code mode}）、
 * 基线/目标版本、整体结果（{@code PASS}/{@code FAIL}）与差异计数、逐字段差异明细（{@code diffItems}
 * JSON）。校验日志页按「批次 → 差异明细」两级下钻（Req 17），并做汇总统计（Req 18）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_compare_log")
public class RmsCompareLog extends BaseDefData {

    /** 批次号（单次比对为空或自生成；批量比对共享同一批次）。 */
    @Column(name = "batch_id", length = 64)
    private String batchId;

    /** 比对方式（FULL_BODY / PARAMETER / STRUCTURE）。 */
    @Column(name = "mode", length = 16, nullable = false)
    private String mode;

    /** 基线版本 id（左侧）。 */
    @Column(name = "baseline_version_id", length = 64)
    private String baselineVersionId;

    /** 目标版本 id（右侧）。 */
    @Column(name = "target_version_id", length = 64, nullable = false)
    private String targetVersionId;

    /** 目标配方编码（便于按对象检索）。 */
    @Column(name = "recipe_code", length = 128)
    private String recipeCode;

    /** 是否完全一致。 */
    @Column(name = "identical", nullable = false)
    private boolean identical = false;

    /** 差异项计数（changed=true 的条数）。 */
    @Column(name = "diff_count", nullable = false)
    private int diffCount = 0;

    /** 结果（PASS / FAIL）。 */
    @Column(name = "result", length = 16, nullable = false)
    private String result = "PASS";

    /** 逐字段差异明细（JSON 数组，两级下钻的叶子）。 */
    @Lob
    @Column(name = "diff_items")
    private String diffItems;
}
