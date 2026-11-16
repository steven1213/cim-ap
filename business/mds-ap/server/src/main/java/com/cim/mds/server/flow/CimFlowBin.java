package com.cim.mds.server.flow;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程决策 bin 词表（Flow Bin）——画布 XML 里 DECISION 节点 {@code <bin>} 子元素的解析产物。
 *
 * <p>半导体 Route 口径：决策站把晶圆分档（bin），例如 GOOD/FAIL1/FAIL2；
 * bin 分 PASS / FAIL 两类（PASS 类 = 合格流向，FAIL 类 = 不良/待判定流向）。
 * 从 DECISION 节点出发的边的 {@code condition} 引用 bin code——词表已定义时，
 * 保存/校验守门 condition 必须命中该节点的 bin 词表（防手写条件漂移）。</p>
 *
 * <p>node_id = 所属 DECISION 节点的 XML 节点 id；sort_no = XML 里出现顺序。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_flow_bin", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_flow_bin", columnNames = {"flow_id", "node_id", "bin_code", "deleted"})
})
public class CimFlowBin extends BaseDefData {

    /** bin 类型：PASS（合格分档）/ FAIL（不良分档）。 */
    public static final String TYPE_PASS = "PASS";
    public static final String TYPE_FAIL = "FAIL";

    /** 所属流程 id。 */
    @Column(name = "flow_id", length = 64, nullable = false)
    private String flowId;

    /** 所属 DECISION 节点的 XML 节点 id。 */
    @Column(name = "node_id", length = 64, nullable = false)
    private String nodeId;

    /** bin 编码（节点内唯一；边 condition 引用它，例 GOOD）。 */
    @Column(name = "bin_code", length = 32, nullable = false)
    private String binCode;

    /** bin 名称（例：良品）。 */
    @Column(name = "bin_name", length = 64)
    private String binName;

    /** bin 类型：PASS / FAIL。 */
    @Column(name = "bin_type", length = 8, nullable = false)
    private String binType;

    /** 排序号（XML 出现顺序，1..n）。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
