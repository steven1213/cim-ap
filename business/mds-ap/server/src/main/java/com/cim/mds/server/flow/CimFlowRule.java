package com.cim.mds.server.flow;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程独立规则（V15）——与画布结构解耦的处置规则层，锚定
 * 「版本族 {@code family_code} + {@code version_no}」。
 *
 * <p>规则条目不进画布 XML：画布描述「结构」（步骤/边/Q-time 约束），规则层描述
 * 「运行期处置策略」（进站/出站/派工时刻按条件放行、挂起、报废、改派、插单）。
 * 规则随版本生命周期只读：仅 UNFROZEN 版本可维护，FROZEN/ACTIVE 版本的规则
 * 供 evaluate 只读消费。</p>
 *
 * <p><b>评估口径</b>：priority 小者先评估；条件空 = 恒真；命中 HOLD/SCRAP 终态
 * 短路，ROUTE_OVERRIDE/INSERT_SEQUENCE 取优先级最高的一条。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_flow_rule", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_flow_rule", columnNames = {"family_code", "version_no", "rule_code", "deleted"})
})
public class CimFlowRule extends BaseDefData {

    // ---------------------------------------------------------------- 挂接点

    /** 进站前（步骤进入前校验）。 */
    public static final String JOIN_BEFORE_STEP_ENTER = "BEFORE_STEP_ENTER";
    /** 步骤完成出站前。 */
    public static final String JOIN_AFTER_STEP_COMPLETE = "AFTER_STEP_COMPLETE";
    /** 派工下发前（调度/搬运下发前最后守门）。 */
    public static final String JOIN_BEFORE_DISPATCH = "BEFORE_DISPATCH";

    // ---------------------------------------------------------------- 处置

    /** 显式放行（与隐式无规则放行区分，用于白名单语义）。 */
    public static final String EFFECT_ALLOW = "ALLOW";
    /** 暂停等人工释放。 */
    public static final String EFFECT_HOLD = "HOLD";
    /** 报废。 */
    public static final String EFFECT_SCRAP = "SCRAP";
    /** 改派目标节点（effect_param = 目标 nodeId）。 */
    public static final String EFFECT_ROUTE_OVERRIDE = "ROUTE_OVERRIDE";
    /** 插单（effect_param = 插入步骤编码）。 */
    public static final String EFFECT_INSERT_SEQUENCE = "INSERT_SEQUENCE";

    // ---------------------------------------------------------------- 内置模板

    /** 内置模板：跨站 Q-time 超限挂起（按 cim_flow_qtime 定义逐条生成）。 */
    public static final String TEMPLATE_QTIME_OVER_HOLD = "QTIME_OVER_HOLD";
    /** 内置模板：Bin 条件选路（按画布条件边逐条生成 ROUTE_OVERRIDE）。 */
    public static final String TEMPLATE_BIN_ROUTE = "BIN_ROUTE";

    /** 版本族编码（= cim_flow.family_code）。 */
    @Column(name = "family_code", length = 64, nullable = false)
    private String familyCode;

    /** 版本号（= cim_flow.version_no）。 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    /** 规则编码（版本族 + 版本内唯一）。 */
    @Column(name = "rule_code", length = 64, nullable = false)
    private String ruleCode;

    /** 规则名称。 */
    @Column(name = "rule_name", length = 128)
    private String ruleName;

    /** 挂接点：BEFORE_STEP_ENTER / AFTER_STEP_COMPLETE / BEFORE_DISPATCH。 */
    @Column(name = "join_point", length = 32, nullable = false)
    private String joinPoint;

    /** 约束步骤编码（空 = 任意步骤）。 */
    @Column(name = "step_code", length = 64)
    private String stepCode;

    /** 约束节点 id（空 = 任意节点；与 step_code 互补的精确锚点）。 */
    @Column(name = "node_id", length = 64)
    private String nodeId;

    /** 适用边语义（空 = 不限；ROUTE_OVERRIDE 模板来源边语义）。 */
    @Column(name = "edge_type", length = 16)
    private String edgeType;

    /** 声明式条件表达式（空 = 恒真）。 */
    @Column(name = "condition_expr", length = 512)
    private String conditionExpr;

    /** 处置：ALLOW / HOLD / SCRAP / ROUTE_OVERRIDE / INSERT_SEQUENCE。 */
    @Column(name = "effect", length = 32, nullable = false)
    private String effect;

    /** 处置参数：HOLD 原因说明 / ROUTE 目标 nodeId / INSERT 步骤编码。 */
    @Column(name = "effect_param", length = 512)
    private String effectParam;

    /** 优先级（小者先评估）。 */
    @Column(name = "priority", nullable = false)
    private Integer priority = 100;

    /** 是否启用（禁用规则不参与评估）。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /** 内置模板来源标识（手工规则为空）。 */
    @Column(name = "template_key", length = 64)
    private String templateKey;
}
