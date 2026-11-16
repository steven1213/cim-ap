package com.cim.mds.server.flow;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 制造流程（Flow）——有向图模型主档（SiView：Technology → Product → Route 口径的 Route 落点）。
 *
 * <p>行业对标（2026-11-16 设计定稿）：Camstar 把 Flow（主数据，版本化）与 Workflow
 * （运行时实例）分离；PROMIS Router 单一事实源 + 决策点 Go/No-Go；myCIM PRP 多路径
 * 条件选路 + Q-time + 返工限制。本域即「主数据侧 Flow」的 MVP：结构 = 步骤节点
 * （{@link CimFlowStep}）+ 语义边（{@link CimFlowEdge}），运行时选路/派工归 MES。</p>
 *
 * <p>版本生命周期（2026-11-16 用户定稿，Camstar Flow 版本口径）：
 * UNFROZEN（未冻结/草稿，可编辑）→ FROZEN（已冻结/定稿待激活）→ ACTIVE（生效，
 * 生产线现行版本）→ DISABLED（禁用/淘汰）。核心约束：<b>同一版本族（family_code）
 * 同一时刻最多 0/1 个 ACTIVE</b>——激活新版时旧 ACTIVE 自动降级 FROZEN，保证生产
 * 线永远只认一条现行有效工艺路线。</p>
 *
 * <p>flow_code 走 NamingRule FLOW 对象占号，全局唯一建后不可改；family_code +
 * version_no 标识「同一套 Flow 的第几个版本」（新建 = 自成一族 V1，复制 = 同族
 * 版本号递增）。改 UNFROZEN 版本直接编辑；改冻结后/生效版本 = 复制出新版本再改。</p>
 *
 * <p><b>XML 单一事实源（2026-11-16 用户定稿）</b>：{@code flow_xml} 是画布 XML 原文件
 * （节点+边+坐标的完整序列化）——渲染画布和修改画布都以它为源（前端解析渲染、
 * 保存时整份回传），每次保存后由后端重新解析，重建 {@link CimFlowStep}/
 * {@link CimFlowEdge} 解析产物表（结构化查询/MES 消费用）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_flow", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_flow_code", columnNames = {"flow_code", "deleted"})
})
public class CimFlow extends BaseDefData {

    /**
     * 版本生命周期（V14 定稿）：UNFROZEN 未冻结（草稿，可编辑不可用）/
     * FROZEN 已冻结（定稿待激活）/ ACTIVE 生效（生产线现行，同族唯一）/
     * DISABLED 禁用（淘汰预备删除）。
     */
    public static final String STATUS_UNFROZEN = "UNFROZEN";
    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    /** flow_kind：MAIN 主流程 / SUB 子流程（仅分类与列表筛选，不限制引用方向）。 */
    public static final String KIND_MAIN = "MAIN";
    public static final String KIND_SUB = "SUB";

    /** 流程编码（全局唯一主标识，走 NamingRule 占号；建后不可改）。 */
    @Column(name = "flow_code", length = 64, nullable = false)
    private String flowCode;

    /** 流程名称（例：CIS 前段标准流程）。 */
    @Column(name = "flow_name", length = 128, nullable = false)
    private String flowName;

    /** 流程种类：MAIN 主流程 / SUB 子流程（V13；仅分类，可被 SUBFLOW 节点引用）。 */
    @Column(name = "flow_kind", length = 8, nullable = false)
    private String flowKind = KIND_MAIN;

    /** 挂载制程技术编码（逻辑引用 cim_technology.tech_code，可空）。 */
    @Column(name = "tech_code", length = 64)
    private String techCode;

    /** 生命周期状态：UNFROZEN / FROZEN / ACTIVE / DISABLED（同族 ACTIVE 唯一）。 */
    @Column(name = "status", length = 16, nullable = false)
    private String status = STATUS_UNFROZEN;

    /** 版本族标识（同一套 Flow；新建 = 自成族（=首版 flow_code），复制 = 沿用源族）。 */
    @Column(name = "family_code", length = 64)
    private String familyCode;

    /** 族内版本号（1..n；复制时族内 max+1）。 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    /** 画布 XML 原文件（节点+边+坐标完整序列化；渲染与编辑的单一事实源）。 */
    @Lob
    @Column(name = "flow_xml")
    private String flowXml;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;
}
