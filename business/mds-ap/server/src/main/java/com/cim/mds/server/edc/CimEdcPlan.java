package com.cim.mds.server.edc;

import java.time.LocalDateTime;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * EDC 方案（V28）——面向业务执行的聚合层：把参数/参数集、采集规则与量测规格
 * 组装成可被工艺路线引用的采集契约（行业 Data Collection Plan 口径）。
 *
 * <p>绑定粒度 = 产品（可空=工序级通用）+ 流程（family+version）+ 工序；
 * 版本化管理，四态生命周期镜像 CimFlow：UNFROZEN→FROZEN→ACTIVE→DISABLED，
 * activate 自动降级同族旧 ACTIVE。明细（采集项 + 量测规格）见 {@link CimEdcPlanItem}，
 * 仅 UNFROZEN 可编辑（发布数据只读——与流程覆盖同口径）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_edc_plan", uniqueConstraints = {
        @UniqueConstraint(name = "uk_edc_plan", columnNames = {"plan_code", "version_no", "deleted"})
})
public class CimEdcPlan extends EditLockDefData {

    // ---------------- 生命周期词表（镜像 CimFlow 四态） ----------------
    public static final String STATUS_UNFROZEN = "UNFROZEN";
    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 方案族编码（同族多版本，唯一 ACTIVE）。 */
    @Column(name = "plan_code", length = 64, nullable = false)
    private String planCode;

    /** 版本号（同族内递增）。 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** 生命周期状态：UNFROZEN / FROZEN / ACTIVE / DISABLED。 */
    @Column(name = "status", length = 16, nullable = false)
    private String status = STATUS_UNFROZEN;

    /** 产品编码（可空 = 工序级通用方案，对该工序全部产品生效）。 */
    @Column(name = "product_code", length = 64)
    private String productCode;

    /** 流程族编码（逻辑引用 cim_flow.family_code）。 */
    @Column(name = "flow_family_code", length = 64, nullable = false)
    private String flowFamilyCode;

    /** 流程版本号。 */
    @Column(name = "flow_version_no", nullable = false)
    private Integer flowVersionNo;

    /** 工序编码（逻辑引用 cim_operation.op_code）。 */
    @Column(name = "op_code", length = 64, nullable = false)
    private String opCode;

    /** 来源参数集编码（一键带入明细的出处标注，可空）。 */
    @Column(name = "set_code", length = 64)
    private String setCode;

    /** 激活时间。 */
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    /** 激活人。 */
    @Column(name = "activated_by", length = 64)
    private String activatedBy;
}
