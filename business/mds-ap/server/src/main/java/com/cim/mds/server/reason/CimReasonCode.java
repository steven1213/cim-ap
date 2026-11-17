package com.cim.mds.server.reason;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 原因码（V16）——WIP 事件归因主数据。
 *
 * <p><b>树即码</b>：分类层级不另立表，{@code parent_id} 自关联 + {@code node_path}
 * 物化路径直接长在码上（每个码必须挂树，否则 Pareto 报表里查不到）。层级语义
 * 「责任类 → 现象类 → 具体原因」；跨层聚合与下钻都用 path 前缀，不递归。</p>
 *
 * <p><b>治理纪律</b>：{@code reason_code} 全局唯一、建后不可改（事件快照与命名规则
 * 都按 code 引用）；不物理删除（软删）；{@code enabled=false} 只影响未来下拉可见性，
 * 历史事件照常解析。归属部门（{@code owning_dept}，平台字典）只表达「归谁统计改善」，
 * <b>不表达处置权限</b>——「谁可释放」是操作上下文→角色映射的职责，不进码表。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_reason_code", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_reason_code", columnNames = {"reason_code", "deleted"})
})
public class CimReasonCode extends BaseDefData {

    /** 定长编码（如 HLD-0103；全局唯一、建后不可改；命名规则 REASON_CODE 守门）。 */
    @Column(name = "reason_code", length = 64, nullable = false)
    private String reasonCode;

    /** 语义域（= cim_reason_code_type.type_key；建后不可改）。 */
    @Column(name = "type_key", length = 32, nullable = false)
    private String typeKey;

    /** 短名（下拉与快照展示文案）。 */
    @Column(name = "short_name", length = 128)
    private String shortName;

    /** 归属部门（平台字典 mds.owning_dept；统计聚合与改善路由用，非处置权限）。 */
    @Column(name = "owning_dept", length = 64)
    private String owningDept;

    /** 父节点（= 同类型另一原因码 id；空 = 根）。 */
    @Column(name = "parent_id", length = 64)
    private String parentId;

    /** 物化路径（/父码/…/本码/，前缀匹配做父级聚合与下钻）。 */
    @Column(name = "node_path", length = 512, nullable = false)
    private String nodePath;

    /** 层级（根 = 1）。 */
    @Column(name = "level_no", nullable = false)
    private Integer levelNo = 1;

    /** 是否启用（false = 不再出现在新事务下拉；历史引用不受影响）。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /** 排序号（同级展示顺序）。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 100;
}
