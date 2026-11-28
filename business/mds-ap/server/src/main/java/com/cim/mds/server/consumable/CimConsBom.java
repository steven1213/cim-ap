package com.cim.mds.server.consumable;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 消耗品 BOM（Consumable BOM）——「工艺路线 × 消耗品规格」的消耗契约（V38）。
 *
 * <p>MDS 交付的是「在哪里、消耗多少」的版本化基准：头表绑定上下文
 * 产品 + 流程 + 工序（+ 机台可选腔体级覆盖），行表定义消耗规则
 * （{@link CimConsBomLine}）。库存扣减、齐套运算、FEFO 拣配归 MES/ERP，
 * 本表只存基准。</p>
 *
 * <p><b>状态机（对齐行业制造执行流派三态 + Superseded 自动画）</b>：
 * DRAFT ──激活──▶ ACTIVE ──激活新版──▶ SUPERSEDED（自动置位，只读追溯）；
 * ACTIVE 手动停用 ──▶ DISABLED。铁律：ACTIVE 不回 DRAFT、SUPERSEDED/DISABLED
 * 不可删；同上下文（产品×流程×工序×机台）任意时刻最多一个 ACTIVE——
 * 激活新版时旧 ACTIVE 自动 SUPERSEDED。</p>
 *
 * <p>bom_code 全局唯一、建后不可改；version_no 为同上下文激活序号（激活时
 * max+1）；source_op_version 记录激活时工序 version_no——工序/Recipe 版本
 * 联动锚点（工序升版后页面展示版本漂移告警，ECN 治理归变更管理）。
 * 编辑锁三列与全库主数据同款。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_cons_bom", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_cons_bom_code", columnNames = {"bom_code", "deleted"})
})
public class CimConsBom extends EditLockDefData {

    /** 草稿（可编辑、可激活、可删）。 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 生效（同上下文唯一，生产线消耗校验现行基准）。 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 被新版取代（自动置位，只读追溯，不可删）。 */
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    /** 禁用（手动停用）。 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** BOM 编码（全局唯一，例：CBOM-6IN-ETCH-001）。建后不可改。 */
    @Column(name = "bom_code", length = 64, nullable = false)
    private String bomCode;

    /** BOM 名称（例：6 寸刻蚀工序气体消耗基准）。 */
    @Column(name = "bom_name", length = 128, nullable = false)
    private String bomName;

    /** 产品 id（消耗契约绑定上下文之一，必填）。 */
    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    /** 流程 id（绑定上下文之一，必填）。 */
    @Column(name = "flow_id", length = 64, nullable = false)
    private String flowId;

    /** 工序 id（绑定上下文之一，必填——消耗契约挂工序粒度）。 */
    @Column(name = "operation_id", length = 64, nullable = false)
    private String operationId;

    /** 机台 id（可选；腔体级覆盖行绑定，空 = 不限机台）。 */
    @Column(name = "equipment_id", length = 64)
    private String equipmentId;

    /** 状态（DRAFT/ACTIVE/SUPERSEDED/DISABLED，见常量说明）。 */
    @Column(name = "status", length = 16, nullable = false)
    private String status = STATUS_DRAFT;

    /** 同上下文激活序号（激活时计算 max+1，未激活 = 0）。 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 0;

    /** 激活时工序 version_no 快照（版本漂移告警锚点，激活时写入）。 */
    @Column(name = "source_op_version")
    private Integer sourceOpVersion;

    /** 启用（禁用的 BOM 不参与消耗校验基准选择）。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
