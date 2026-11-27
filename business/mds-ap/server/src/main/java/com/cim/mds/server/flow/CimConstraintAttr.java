package com.cim.mds.server.flow;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * KV 约束值表（ICM ContextChain 泛化）——长尾参数按上下文三层复用一张表。
 *
 * <p>替代上版 cim_flow_step_attr 单层表：context_type=OPERATION/FLOW/STEP 三层
 * 共用，继承规则与列型约束一致（取值沿链取最特异层，{@code ContextResolver}
 * 单一事实源）。上下文三列 NOT NULL DEFAULT '' 空串占位（uk NULL 陷阱规避，
 * 同 {@link CimEquipAssign}）。</p>
 *
 * <p>守门：attr_key 须在 {@link CimParamDef} 注册且 enabled；param_def.scopes
 * 未包含本 context_type → 2004（PARAM_SCOPE_NOT_ALLOWED）。四态只读：
 * 所属流程 status≠UNFROZEN 时 context=FLOW/STEP 行禁改禁删。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_constraint_attr", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cattr", columnNames = {
                "context_type", "operation_code", "flow_id", "step_id", "product_code", "family_code", "attr_key", "deleted"})
})
public class CimConstraintAttr extends BaseDefData {

    /** 上下文：OPERATION / FLOW / STEP（P3 加 PRODUCT）。 */
    @Column(name = "context_type", length = 16, nullable = false)
    private String contextType;

    /** context=OPERATION 时填 op_code；其余空串占位（uk NULL 陷阱规避）。 */
    @Column(name = "operation_code", length = 64, nullable = false)
    private String operationCode = "";

    /** context=FLOW/STEP 时填流程 id；其余空串占位。 */
    @Column(name = "flow_id", length = 64, nullable = false)
    private String flowId = "";

    /** 仅 context=STEP 填节点 id（= cim_flow_step.node_id，保存画布后稳定）；其余空串占位。 */
    @Column(name = "step_id", length = 64, nullable = false)
    private String stepId = "";

    /** 仅 context=PRODUCT 填产品编码；其余空串占位（V25 L3）。 */
    @Column(name = "product_code", length = 64, nullable = false)
    private String productCode = "";

    /** 仅 context=PRODUCT 填流程版本族编码；其余空串占位（V25 L3）。 */
    @Column(name = "family_code", length = 64, nullable = false)
    private String familyCode = "";

    /** 参数键（逻辑引用 cim_param_def.attr_key；守门须已注册且 enabled）。 */
    @Column(name = "attr_key", length = 64, nullable = false)
    private String attrKey;

    /** 参数值（按 value_type 校验形态；ENUM 校验字典项；JSON 须合法）。 */
    @Column(name = "attr_value", length = 1024)
    private String attrValue;
}
