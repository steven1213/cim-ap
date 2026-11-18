package com.cim.mds.server.flow;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 工序库主数据（Operation）——四层工艺域建模的 O 层（2026-11-18 设计定稿）。
 *
 * <p>行业对标：赛美特 PlantU P-T-R-O 四层建模的 O 层、Camstar Operation（版本化、
 * 跨 Routing 复用）、通用 fab 三层口径的 Step 定义面。工序是<b>独立主数据</b>：
 * 标准工序定义一次，多条路线（{@link CimFlow}）的步骤节点引用并局部覆盖——
 * 与路线内实例（Step，{@link CimFlowStep}）的关系是「定义 vs 副本」。</p>
 *
 * <p>术语纪律（用户定稿「Operation/Step 双词」）：Operation = 工序（本表，
 * op_code 追溯锚点全局唯一建后不可改）；Step = 路线内工序实例。</p>
 *
 * <p>生命周期与 Flow 同款四态（建模纪律全库统一）：UNFROZEN（草稿）→
 * FROZEN（冻结待激活）→ ACTIVE（生效，同版本族 {@code op_family_code} 唯一，
 * 激活旧 ACTIVE 自动降级 FROZEN）→ DISABLED（禁用）。只有 ACTIVE 工序可被
 * 路线节点引用（守门在 {@code FlowAdminService.saveXml}）。</p>
 *
 * <p>业务面：op_type 三类业务工序（PROCESS/INSPECT/REWORK；DECISION/SPLIT/MERGE/
 * START/END 是路线结构工件不入库）；标准卡控属性与 {@link CimFlowStep} 字面面一致
 * （引用时带出、本路线内可覆盖）。12 寸预留列（建列不启用，2026-11-18 用户定稿）：
 * reticle_ref 光罩 / test_program CP 测试程序 / inhibit_group 污染互斥组 /
 * npw_plan 监测片计划 / chamber_select 腔体约束——启用属后续迭代，本期不守门。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_operation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_operation_code", columnNames = {"op_code", "deleted"})
})
public class CimOperation extends BaseDefData {

    // ---------------- 生命周期四态（与 CimFlow 同款，建模纪律全库统一） ----------------
    public static final String STATUS_UNFROZEN = "UNFROZEN";
    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    // ---------------- op_type 业务工序三类（结构工件不入库） ----------------
    public static final String TYPE_PROCESS = "PROCESS";
    public static final String TYPE_INSPECT = "INSPECT";
    public static final String TYPE_REWORK = "REWORK";

    /** 批处理模式白名单（与 Flow 步骤同词表；空 = 默认串行）。 */
    public static final String BATCH_SERIAL = "SERIAL";
    public static final String BATCH_PARALLEL = "PARALLEL";
    public static final String BATCH_BATCH = "BATCH";

    /** 工序编码（全局唯一主标识、追溯锚点；手填，建后不可改）。 */
    @Column(name = "op_code", length = 64, nullable = false)
    private String opCode;

    /** 工序名称（例：光刻显影）。 */
    @Column(name = "op_name", length = 128, nullable = false)
    private String opName;

    /** 工序类型：PROCESS 加工 / INSPECT 检验 / REWORK 返工（业务工序三类）。 */
    @Column(name = "op_type", length = 16, nullable = false)
    private String opType;

    /** 归属工艺平台编码（逻辑引用 cim_technology.tech_code，可空）。 */
    @Column(name = "tech_code", length = 64)
    private String techCode;

    /** 生命周期状态：UNFROZEN / FROZEN / ACTIVE / DISABLED（同族 ACTIVE 唯一）。 */
    @Column(name = "status", length = 16, nullable = false)
    private String status = STATUS_UNFROZEN;

    /** 版本族标识（同一套工序；新建 = 自成族（=首版 op_code），复制 = 沿用源族）。 */
    @Column(name = "op_family_code", length = 64)
    private String opFamilyCode;

    /** 族内版本号（1..n；复制时族内 max+1）。 */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    // ---------------- 标准卡控属性（与 cim_flow_step 字面面一致；引用带出可覆盖） ----------------

    /** 默认逻辑配方引用（RMS PPID，可空；MVP 不守门存在性）。 */
    @Column(name = "recipe_ref", length = 64)
    private String recipeRef;

    /** 默认约束设备类型编码（逻辑引用 cim_equipment_type，可空；守门须存在）。 */
    @Column(name = "equip_type", length = 64)
    private String equipType;

    /** 默认 Q-time 队留下限（分钟，可空）。 */
    @Column(name = "qtime_min")
    private Integer qtimeMin;

    /** 默认 Q-time 队留上限（分钟，可空）。 */
    @Column(name = "qtime_max")
    private Integer qtimeMax;

    /** 默认返工次数上限（可空；null = 不限，0 = 不允许返工进本站）。 */
    @Column(name = "rework_limit")
    private Integer reworkLimit;

    /** 默认 Hold 点（关键站后强制暂停等释放；释放流程归 MES）。 */
    @Column(name = "hold_point", nullable = false)
    private Boolean holdPoint = Boolean.FALSE;

    /** Hold 点说明。 */
    @Column(name = "hold_reason", length = 256)
    private String holdReason;

    /** 默认批处理模式：SERIAL / PARALLEL / BATCH（可空 = 串行）。 */
    @Column(name = "batch_mode", length = 16)
    private String batchMode;

    /** 默认装载量（BATCH 模式下每批最大片数，可空）。 */
    @Column(name = "batch_size")
    private Integer batchSize;

    /** 默认检验计划引用（INSPECT 专用；逻辑引用 EDC 检验计划编码，MVP 不守门）。 */
    @Column(name = "inspect_plan", length = 64)
    private String inspectPlan;

    /** 默认抽样率（INSPECT 专用；百分比 0-100，null = 100% 全检）。 */
    @Column(name = "sampling_rate", precision = 5, scale = 2)
    private java.math.BigDecimal samplingRate;

    // ---------------- 12 寸预留列（建列不启用，本期不守门） ----------------

    /** 光罩引用（12 寸光刻；预留）。 */
    @Column(name = "reticle_ref", length = 64)
    private String reticleRef;

    /** CP 测试程序引用（12 寸 CP 工序；预留）。 */
    @Column(name = "test_program", length = 64)
    private String testProgram;

    /** 污染互斥组（Inhibit；预留）。 */
    @Column(name = "inhibit_group", length = 64)
    private String inhibitGroup;

    /** 监测片计划（NPW；预留）。 */
    @Column(name = "npw_plan", length = 64)
    private String npwPlan;

    /** 腔体约束（chamber 级选择；预留）。 */
    @Column(name = "chamber_select", length = 64)
    private String chamberSelect;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;
}
