package com.cim.mds.server.reason;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 原因码类型（V16）——WIP 事务语义域，回答「这个码用于哪一类 WIP 事件」。
 *
 * <p>Hold 原因码和 Scrap 原因码不混同一个下拉列表：WIP 侧打开原因选择器时
 * 先按 {@code type_key} 过滤，而不是靠分组或部门去「猜」。一个码只能属于一个
 * Type（语义归属唯一）；Type 集合小而稳定（8 类种子），页面开放维护但删除受守门
 * （有码挂靠时拒绝）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_reason_code_type", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_reason_code_type", columnNames = {"type_key", "deleted"})
})
public class CimReasonCodeType extends BaseDefData {

    // ---------------------------------------------------------------- 8 类种子（与 WIP 事务语义对齐）

    /** 挂起（暂停等释放）。 */
    public static final String HOLD = "HOLD";
    /** 释放（Hold 解除）。 */
    public static final String RELEASE = "RELEASE";
    /** 报废。 */
    public static final String SCRAP = "SCRAP";
    /** 返工。 */
    public static final String REWORK = "REWORK";
    /** 跳离流程（off-flow：临时改道/搬出正常路线）。 */
    public static final String OFF_FLOW = "OFF_FLOW";
    /** 暂停（设备/批次级暂停，与 Hold 的批次管控语义区分）。 */
    public static final String PAUSE = "PAUSE";
    /** 缺陷归因。 */
    public static final String DEFECT = "DEFECT";
    /** 损耗归因（数量损失，非整批处置）。 */
    public static final String LOSS = "LOSS";

    /** 语义键（HOLD / RELEASE / SCRAP / REWORK / OFF_FLOW / PAUSE / DEFECT / LOSS…）。 */
    @Column(name = "type_key", length = 32, nullable = false)
    private String typeKey;

    /** 类型名称（如「挂起」）。 */
    @Column(name = "type_name", length = 64, nullable = false)
    private String typeName;

    /** 排序号（下拉/列表展示顺序）。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 100;
}
