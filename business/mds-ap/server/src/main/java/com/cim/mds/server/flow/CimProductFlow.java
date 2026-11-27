package com.cim.mds.server.flow;

import java.time.LocalDateTime;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 产品↔流程绑定——产品规格域补缺口（产品当前可投批到哪条流程版本）。
 *
 * <p>守门：同 (product_code, flow_family_code) 最多一条 ACTIVE；绑定版本须 ACTIVE
 * （或 FROZEN+VALID 允许预绑定，投批解析到 ACTIVE）；流程 disable 前 2004 查
 * ACTIVE 绑定（先解绑再淘汰，防悬挂绑定）。</p>
 *
 * <p>P3 预留：产品级上下文（scope=PRODUCT）复用 cim_equip_assign/cim_constraint_attr
 * 扩枚举；P1 只建绑定关系——先用「绑不同流程版本」表达产品差异。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_product_flow", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_product_flow", columnNames = {
                "product_code", "flow_family_code", "flow_version_no", "deleted"})
})
public class CimProductFlow extends BaseDefData {

    /** 绑定状态：ACTIVE 现行（投批解析目标）/ HISTORY 历史（解绑/被新版本替换后留痕）。 */
    public static final String BIND_ACTIVE = "ACTIVE";
    public static final String BIND_HISTORY = "HISTORY";

    /** 产品编码（逻辑引用 cim_product.product_code；守门须存在）。 */
    @Column(name = "product_code", length = 64, nullable = false)
    private String productCode;

    /** 流程版本族（逻辑引用 cim_flow.family_code）。 */
    @Column(name = "flow_family_code", length = 64, nullable = false)
    private String flowFamilyCode;

    /** 绑定的族内版本号。 */
    @Column(name = "flow_version_no", nullable = false)
    private Integer flowVersionNo;

    /** 绑定状态：ACTIVE / HISTORY。 */
    @Column(name = "bind_state", length = 16, nullable = false)
    private String bindState = BIND_ACTIVE;

    /** 激活时间（绑定/换绑时写）。 */
    @Column(name = "active_time")
    private LocalDateTime activeTime;

    /** 激活人。 */
    @Column(name = "active_user", length = 64)
    private String activeUser;

    /** 编辑锁（人 vs 人互斥；同 bay/bank 模式）。 */
    @Column(name = "is_occupied", nullable = false)
    private Boolean isOccupied = Boolean.FALSE;

    /** 占用人。 */
    @Column(name = "occupied_by", length = 64)
    private String occupiedBy;

    /** 占用时间。 */
    @Column(name = "occupied_at")
    private LocalDateTime occupiedAt;

    // ---------------- 列型覆盖（V25 L3；NULL = 继承流程/工序层） ----------------

    /** 覆盖光罩引用（CSV；NULL = 继承）。 */
    @Column(name = "reticle_ref", length = 256)
    private String reticleRef;

    /** 覆盖进站污染级（NULL = 继承）。 */
    @Column(name = "contam_level", length = 16)
    private String contamLevel;

    /** 覆盖禁止污染级 CSV（NULL = 继承；CONTAM_VALID 口径同 step 层）。 */
    @Column(name = "contam_forbidden", length = 128)
    private String contamForbidden;

    /** 覆盖 NPW 投片上限（片数；NULL = 不限，落 V23 step 列语义之上）。 */
    @Column(name = "npw_limit")
    private Integer npwLimit;
}
