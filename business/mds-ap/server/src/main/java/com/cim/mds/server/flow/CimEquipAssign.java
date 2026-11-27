package com.cim.mds.server.flow;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一指派表（ICM 核心）——机台候选池按 ContextChain 三 scope 复用一张表。
 *
 * <p><b>ICM 设计（2026-11-25 定稿，flow-modeling/20-our-design.md §2.2）</b>：
 * 工序默认指派（scope=OPERATION）/ 流程级覆盖指派（scope=FLOW）/ 节点覆盖指派
 * （scope=STEP）共用本表——同一个 Service/Controller/前端编辑组件/Excel 模板全复用，
 * 这是「便于配置」的落地，也是 myCIM 多层级 Context 的显式化实现（P3 加 PRODUCT
 * 只扩枚举）。</p>
 *
 * <p><b>资格门 vs 候选池</b>：{@link CimFlowStep#getEquipType()}（ET-*）= 资格门
 * （该节点「能由哪类设备做」的硬约束）；本表 = 候选池（派工用具体组/机/型清单）。
 * 解析取 {@code 候选池 ∩ 资格门}，交集为空报 ERROR（ASSIGN_MATCHES_EQUIP_TYPE）。</p>
 *
 * <p><b>守门</b>：scope=OPERATION → operation_code 非空串且存在；scope=FLOW →
 * flow_id 非空串且存在、step_id=''；scope=STEP → flow_id+step_id 非空串且存在。
 * assign_type=GROUP/EQUIP/CLASS → assign_ref 分别存在于
 * cim_tool_group/cim_equipment/cim_equipment_class 且 enabled。</p>
 *
 * <p><b>四态只读</b>：所属流程 status≠UNFROZEN 时 scope=FLOW/STEP 行禁改禁删（2004）；
 * 要改 = copy 新版本。scope=OPERATION 行随工序模板生命周期（OperationAdminService）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_equip_assign", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ea", columnNames = {
                "scope_type", "operation_code", "flow_id", "step_id",
                "assign_type", "assign_ref", "priority", "deleted"})
})
public class CimEquipAssign extends BaseDefData {

    // ---------------- scope_type：ContextChain 四层（V25 加 PRODUCT） ----------------
    /** L0 工序默认指派（operation_code 定位；改工序默认全体受益）。 */
    public static final String SCOPE_OPERATION = "OPERATION";
    /** L1 流程级覆盖指派（flow_id 定位；整条流程统一机台池，如 ENG 流程限定工程机组）。 */
    public static final String SCOPE_FLOW = "FLOW";
    /** L2 节点覆盖指派（flow_id+step_id 定位）。 */
    public static final String SCOPE_STEP = "STEP";
    /** L3 产品级覆盖指派（V25；product_code+family_code 定位，step_id 可空=整条流程生效）。
     *  仅按产品解析端点生效（validate 主数据闸不带产品轴，拍板项 C 方案 A）。 */
    public static final String SCOPE_PRODUCT = "PRODUCT";

    // ---------------- assign_type：候选池三种引用形态 ----------------
    /** 机台组（assign_ref = cim_tool_group.group_code）。 */
    public static final String TYPE_GROUP = "GROUP";
    /** 具体机台（assign_ref = cim_equipment.equipment_code）。 */
    public static final String TYPE_EQUIP = "EQUIP";
    /** 机型（assign_ref = cim_equipment_class.class_code）。 */
    public static final String TYPE_CLASS = "CLASS";

    // ---------------- recipe_rule：三级映射的「Actual Recipe」选择规则 ----------------
    /** 固定 PPID（recipe_ref 必填）。 */
    public static final String RULE_FIXED = "FIXED";
    /** 按规则生成 PPID（RMS 域实例化）。 */
    public static final String RULE_GENERATE = "GENERATE";
    /** 键值精确匹配（recipe_match JSON，如匹配前次量测）。 */
    public static final String RULE_EXACT_MATCH = "EXACT_MATCH";

    /**
     * 上下文 scope：OPERATION / FLOW / STEP。
     */
    @Column(name = "scope_type", length = 16, nullable = false)
    private String scopeType;

    /**
     * scope=OPERATION 时填工序编码（指向具体版本行 op_code）；其余<b>空串占位</b>——
     * 唯一键含 NULL 列在 MySQL/H2 不去重（uk NULL 陷阱），三上下文列统一
     * NOT NULL DEFAULT '' 保证 uk_ea 真实生效。
     */
    @Column(name = "operation_code", length = 64, nullable = false)
    private String operationCode = "";

    /** scope=FLOW/STEP 时填流程 id；其余空串占位（同上）。 */
    @Column(name = "flow_id", length = 64, nullable = false)
    private String flowId = "";

    /** 仅 scope=STEP 填节点 id（= cim_flow_step.node_id——saveXml 全量重建 step 表后
     *  主键 id 会换，node_id 保存画布后稳定，边/qtime/bin 同款引用锚点）；其余空串占位。 */
    @Column(name = "step_id", length = 64, nullable = false)
    private String stepId = "";

    /** 仅 scope=PRODUCT 填产品编码（product_code 定位）；其余空串占位（V25）。 */
    @Column(name = "product_code", length = 64, nullable = false)
    private String productCode = "";

    /** 仅 scope=PRODUCT 填流程版本族编码（family_code，对「该产品走该族流程」生效）；其余空串占位（V25）。 */
    @Column(name = "family_code", length = 64, nullable = false)
    private String familyCode = "";

    /** 指派类型：GROUP / EQUIP / CLASS。 */
    @Column(name = "assign_type", length = 16, nullable = false)
    private String assignType;

    /** 指派引用：组=group_code / 机=equipment_code / 型=class_code（按 assign_type 解读）。 */
    @Column(name = "assign_ref", length = 64, nullable = false)
    private String assignRef;

    /**
     * 槽位→腔位规则 JSON，如 {@code {"1":["CH-A","CH-B"]}}；空 = 任意腔
     * （12 寸多腔设备前瞻，6 寸厂通常留空）。
     */
    @Column(name = "chamber_rule", length = 512)
    private String chamberRule;

    /** FIXED PPID（recipe_rule=FIXED 时必填；Actual Recipe 层引用）。 */
    @Column(name = "recipe_ref", length = 64)
    private String recipeRef;

    /** 配方选择规则：FIXED / GENERATE / EXACT_MATCH。 */
    @Column(name = "recipe_rule", length = 16, nullable = false)
    private String recipeRule = RULE_FIXED;

    /** EXACT_MATCH 键值匹配 JSON（recipe_rule=EXACT_MATCH 时必填且须合法 JSON）。 */
    @Column(name = "recipe_match", length = 512)
    private String recipeMatch;

    /** 候选池顺序（小者优先；参与唯一键）。 */
    @Column(name = "priority", nullable = false)
    private Integer priority = 100;

    /** 是否启用（禁用行不参与解析）。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;
}
