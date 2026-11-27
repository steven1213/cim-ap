package com.cim.mds.server.flow;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 参数定义字典——ICM 长尾 KV 的显式化一等公民（对标 FodModeler 的显式化替代）。
 *
 * <p><b>定位（2026-11-25 定稿）</b>：列型约束（recipe/qtime/批处理…）走同名覆盖列；
 * 长尾参数（DC/PILOT/SEASON/SPC… 分组）走「定义字典 + KV 值表」两层。本表只维护
 * 定义：attr_key 建后不可改（同 product_code 口径——值表按 key 引用，改 key 等于
 * 断链）；{@code scopes} 控制该参数可出现在 ContextChain 哪些层（CSV：
 * OPERATION/FLOW/STEP，P3 加 PRODUCT），守门与解析都以它为准。</p>
 *
 * <p>value_type=ENUM 时 enum_dict 引平台字典码（useDict 译文口径）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_param_def", uniqueConstraints = {
        @UniqueConstraint(name = "uk_param_def", columnNames = {"attr_key", "deleted"})
})
public class CimParamDef extends BaseDefData {

    // ---------------- value_type 词表 ----------------
    public static final String VT_STRING = "STRING";
    public static final String VT_INT = "INT";
    public static final String VT_DECIMAL = "DECIMAL";
    public static final String VT_BOOLEAN = "BOOLEAN";
    public static final String VT_ENUM = "ENUM";
    public static final String VT_JSON = "JSON";

    /** 参数键（全局唯一、建后不可改；值表引用锚点）。 */
    @Column(name = "attr_key", length = 64, nullable = false)
    private String attrKey;

    /** 参数名称（例：TrackOut 抽检比例）。 */
    @Column(name = "attr_name", length = 128, nullable = false)
    private String attrName;

    /** 展示分组：DC / PILOT / SEASON / SPC…（页面分栏用）。 */
    @Column(name = "group_code", length = 64, nullable = false)
    private String groupCode;

    /** 值类型：STRING / INT / DECIMAL / BOOLEAN / ENUM / JSON（解析时校验值形态）。 */
    @Column(name = "value_type", length = 16, nullable = false)
    private String valueType;

    /** value_type=ENUM 时引平台字典码（可空）。 */
    @Column(name = "enum_dict", length = 64)
    private String enumDict;

    /** 物理单位（V28 EDC：参数语义锚点的一部分；可空。规格限不落本表，挂方案明细）。 */
    @Column(name = "unit", length = 32)
    private String unit;

    /** 必填：ContextResolver 解析后全链无值 → WARN（PARAM_REQUIRED_MISSING）。 */
    @Column(name = "required", nullable = false)
    private Boolean required = Boolean.FALSE;

    /**
     * 可配置上下文 CSV：OPERATION / FLOW / STEP（P3 加 PRODUCT）——决定 attr_key
     * 可出现在哪些层（KV 写入守门）；空缺层写入即 2004。
     */
    @Column(name = "scopes", length = 64, nullable = false)
    private String scopes = "STEP";

    /** 展示顺序（小者在前）。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 100;

    /** 是否启用（禁用定义不参与解析；已有值保留但标 WARN）。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
