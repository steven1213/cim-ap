package com.cim.mds.server.flow;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 跨站 Q-time 约束（Flow Q-time，V14）——画布 XML 里 {@code <canvas>} 下
 * {@code <qtime>} 子元素的解析产物。
 *
 * <p>半导体 Route 口径：约束「从 from 站离开到 to 站到达」的最大允许间隔
 * （分钟），超限按 action 处置——HOLD（暂停等人工释放）/ SCRAP（直接报废）。
 * 与步骤内的 qtime_min/qtime_max（站内队留）互补：跨站口径约束的是
 * <b>两站之间</b>的运输/等待窗口，from 必须主线可达 to（发布守门）。</p>
 *
 * <p>from/to 引用本流程节点的 XML node_id；uk = flow+from+to（同对站间只允许
 * 一条约束）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_flow_qtime", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_flow_qtime", columnNames = {"flow_id", "from_node_id", "to_node_id", "deleted"})
})
public class CimFlowQtime extends BaseDefData {

    /** 超限动作：HOLD（暂停等释放）。 */
    public static final String ACTION_HOLD = "HOLD";
    /** 超限动作：SCRAP（报废）。 */
    public static final String ACTION_SCRAP = "SCRAP";

    /** 所属流程 id。 */
    @Column(name = "flow_id", length = 64, nullable = false)
    private String flowId;

    /** 起点站 XML 节点 id（离开站）。 */
    @Column(name = "from_node_id", length = 64, nullable = false)
    private String fromNodeId;

    /** 终点站 XML 节点 id（到达站）。 */
    @Column(name = "to_node_id", length = 64, nullable = false)
    private String toNodeId;

    /** 最大允许间隔（分钟，必填 >0）。 */
    @Column(name = "max_minutes", nullable = false)
    private Integer maxMinutes;

    /** 超限动作：HOLD / SCRAP。 */
    @Column(name = "action", length = 16, nullable = false)
    private String action;
}
