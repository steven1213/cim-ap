package com.cim.mds.server.flow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import com.cim.core.model.BaseDefData;

/**
 * 工序可变参数（V29，recipe-resolve-design.md §4）——Wafer Recipe 层在 MDS 的落点。
 *
 * <p>挂 {@link CimOperation} 版本行（Logical Recipe 层）：声明该工序的配方中哪些参数
 * 允许运行时覆盖（Lot/Wafer/Slot 级）、默认值与防呆范围。范围真值锚点是 RMS
 * {@code param_spec}（target/range/list_in/tolerance），本表 min/max 只做 MDS 侧
 * 进站前防呆快照，避免双源漂移。param_key 逻辑引用 cim_param_def（服务层守门）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_operation_variable_param", uniqueConstraints = {
        @UniqueConstraint(name = "uk_operation_variable_param", columnNames = {"op_id", "param_key", "deleted"})
})
public class CimOperationVariableParam extends BaseDefData {

    /** 允许覆盖层级：NONE=不可覆盖（声明只读基准）/ LOT / WAFER / SLOT。 */
    public static final String LEVEL_NONE = "NONE";
    public static final String LEVEL_LOT = "LOT";
    public static final String LEVEL_WAFER = "WAFER";
    public static final String LEVEL_SLOT = "SLOT";

    /** 所属工序版本行 id（逻辑引用 cim_operation.id）。 */
    @Column(name = "op_id", nullable = false)
    private String opId;

    /** MDS 参数键（逻辑引用 cim_param_def.attr_key；同一工序内唯一）。 */
    @Column(name = "param_key", length = 64, nullable = false)
    private String paramKey;

    /** 默认值（未覆盖时的解算取值；数值语义由 param_def.valueType 决定）。 */
    @Column(name = "default_value", length = 256)
    private String defaultValue;

    /** 防呆下界（可空；两侧均可解析为数值时才做范围校验）。 */
    @Column(name = "min_value", length = 64)
    private String minValue;

    /** 防呆上界（可空）。 */
    @Column(name = "max_value", length = 64)
    private String maxValue;

    /** 允许覆盖的最深层级（NONE/LOT/WAFER/SLOT）。 */
    @Column(name = "override_level", length = 16, nullable = false)
    private String overrideLevel = LEVEL_NONE;

    /** 排序号。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
