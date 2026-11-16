package com.cim.mds.server.flow;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程连接边（Flow Edge）——画布 XML 的<b>解析产物</b>（非独立事实源）。
 *
 * <p>每次保存画布（{@code saveXml}）后由后端重新解析 XML 重建本表；
 * from/to 引用 {@link CimFlowStep#getNodeId()}（XML 节点 id）。</p>
 *
 * <p>edge_type 四类（画布着色口径）：MAIN 主线（蓝）/ PASS 决策·合格（绿）/
 * FAIL 决策·不合格（琥珀）/ REWORK 返工回边（红）。守门：DECISION 节点出边必须是
 * PASS/FAIL，其余节点出边必须是 MAIN/REWORK；REWORK 回边目标须是起点上游（防死循环）。</p>
 *
 * <p>condition_expr 条件表达式（MVP 存字段不解析执行——运行时选路归 MES；
 * 例：{@code bin == 'GOOD'}，决策分档选路的落点）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_flow_edge", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_flow_edge",
                columnNames = {"flow_id", "from_step_id", "to_step_id", "edge_type", "deleted"})
})
public class CimFlowEdge extends BaseDefData {

    // ---------------- edge_type 常量（画布四色语义） ----------------
    public static final String TYPE_MAIN = "MAIN";
    public static final String TYPE_PASS = "PASS";
    public static final String TYPE_FAIL = "FAIL";
    public static final String TYPE_REWORK = "REWORK";

    /** 所属流程 id。 */
    @Column(name = "flow_id", length = 64, nullable = false)
    private String flowId;

    /** 起点步骤的 XML 节点 id（引用 cim_flow_step.node_id）。 */
    @Column(name = "from_step_id", length = 64, nullable = false)
    private String fromStepId;

    /** 终点步骤的 XML 节点 id（引用 cim_flow_step.node_id）。 */
    @Column(name = "to_step_id", length = 64, nullable = false)
    private String toStepId;

    /** 边语义：MAIN / PASS / FAIL / REWORK。 */
    @Column(name = "edge_type", length = 16, nullable = false)
    private String edgeType;

    /** 条件表达式（MVP 存字段不解析；DECISION 出边按 bin 等分档变量选路）。 */
    @Column(name = "condition_expr", length = 512)
    private String conditionExpr;
}
