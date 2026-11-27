package com.cim.mds.server.flow;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程步骤节点（Flow Step）——画布 XML 的<b>解析产物</b>（按 XML 原顺序，非独立事实源）。
 *
 * <p>每次保存画布（{@code saveXml}）后由后端重新解析 XML 重建本表：node_id = XML 里
 * 的节点 id（边引用锚点）、step_no = XML 原顺序（1..n）。渲染信息（坐标）不落表，
 * 只存在于 cim_flow.flow_xml。本表供结构化查询与 MES 消费（按类型找步骤、
 * 配方/设备类型约束检查、Q-time 查询等）。</p>
 *
 * <p>step_type 六类：START / END 为结构性类型（守门：唯一 START、至少一 END）；
 * PROCESS / INSPECT / REWORK / DECISION 为业务类型，词表挂平台字典
 * {@code mds.process_step_type}（V12 Java 种子）。</p>
 *
 * <p>业务面：recipe_ref 逻辑配方引用（RMS 域按 PPID/腔体实例化，MVP 不守门）、
 * equip_type 约束设备类型（守门须存在于 cim_equipment_type）、qtime_min/qtime_max
 * 队留时间上下限（分钟）、rework_limit 返工次数上限。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_flow_step", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_flow_step_code", columnNames = {"flow_id", "step_code", "deleted"})
})
public class CimFlowStep extends BaseDefData {

    // ---------------- step_type 常量（结构性两类 + 业务四类 + 子流程引用） ----------------
    public static final String TYPE_START = "START";
    public static final String TYPE_END = "END";
    public static final String TYPE_PROCESS = "PROCESS";
    public static final String TYPE_INSPECT = "INSPECT";
    public static final String TYPE_REWORK = "REWORK";
    public static final String TYPE_DECISION = "DECISION";
    /** 子流程引用节点（V13）：flow_ref 指向目标流程编码；目标须已发布且无引用环。 */
    public static final String TYPE_SUBFLOW = "SUBFLOW";
    /** 分批节点（V14）：一个批次拆分为多条并行支路；出边 ≥2 且仅 MAIN。 */
    public static final String TYPE_SPLIT = "SPLIT";
    /** 合批节点（V14）：多条支路汇合回一个批次；入边 ≥2 且仅 MAIN。 */
    public static final String TYPE_MERGE = "MERGE";

    /** 所属流程 id。 */
    @Column(name = "flow_id", length = 64, nullable = false)
    private String flowId;

    /**
     * 引用工序库编码（V20；逻辑引用 cim_operation.op_code，可空 = 手工步骤不引用工序库）。
     * 引用即从工序库带出标准卡控属性，本路线内可局部覆盖（覆盖只影响本步骤副本）；
     * 守门：引用须存在且 ACTIVE（{@code FlowAdminService.saveXml}）。
     */
    @Column(name = "operation_code", length = 64)
    private String operationCode;

    /** XML 节点 id（画布内唯一；边解析产物 from/to 引用它，保存画布后保持稳定）。 */
    @Column(name = "node_id", length = 64, nullable = false)
    private String nodeId;

    /** 步骤编码（flow 内唯一，例：STP010；START/END 由前端给固定码）。 */
    @Column(name = "step_code", length = 64, nullable = false)
    private String stepCode;

    /** 步骤名称（例：光刻显影）。 */
    @Column(name = "step_name", length = 128)
    private String stepName;

    /** 步骤类型：START/PROCESS/INSPECT/REWORK/DECISION/END。 */
    @Column(name = "step_type", length = 16, nullable = false)
    private String stepType;

    /** XML 原顺序号（1..n；解析时按 XML 节点出现顺序写入）。 */
    @Column(name = "step_no", nullable = false)
    private Integer stepNo = 0;

    /** 逻辑配方引用（RMS PPID，可空；MVP 不守门存在性）。 */
    @Column(name = "recipe_ref", length = 64)
    private String recipeRef;

    /** 约束设备类型编码（逻辑引用 cim_equipment_type，可空；守门须存在）。 */
    @Column(name = "equip_type", length = 64)
    private String equipType;

    /** 能力需求编码（V21，AMAT 口径；逻辑引用 cim_capability，可空 = 继承所引工序
     *  的默认能力）。经设备能力关联（cim_equipment_capability，Equipment 级）匹配
     *  可加工设备；非空才校验存在。 */
    @Column(name = "capability_code", length = 64)
    private String capabilityCode;

    /** EDC Plan 引用码（V21；可空 = 继承所引工序默认值；MDS 不做主数据，采数规则属 MES/EDA 域）。 */
    @Column(name = "edc_plan_code", length = 64)
    private String edcPlanCode;

    /** Q-time 队留下限（分钟，可空）。 */
    @Column(name = "qtime_min")
    private Integer qtimeMin;

    /** Q-time 队留上限（分钟，可空）。 */
    @Column(name = "qtime_max")
    private Integer qtimeMax;

    /** 返工次数上限（可空；null = 不限，0 = 不允许返工进本站）。 */
    @Column(name = "rework_limit")
    private Integer reworkLimit;

    /** 子流程引用目标（SUBFLOW 节点专用；逻辑引用 cim_flow.flow_code，守门须已发布且无环）。 */
    @Column(name = "flow_ref", length = 64)
    private String flowRef;

    /** 批处理模式：SERIAL 串行 / PARALLEL 并行 / BATCH 合批加工（可空 = 默认串行）。 */
    @Column(name = "batch_mode", length = 16)
    private String batchMode;

    /** 装载量（BATCH 模式下每批最大片数，可空）。 */
    @Column(name = "batch_size")
    private Integer batchSize;

    /** 是否 Hold 点（关键站后强制暂停等释放；V13 存字段，释放流程归 MES）。 */
    @Column(name = "hold_point", nullable = false)
    private Boolean holdPoint = Boolean.FALSE;

    /** Hold 点说明（暂停原因/释放条件描述）。 */
    @Column(name = "hold_reason", length = 256)
    private String holdReason;

    /** 检验计划引用（INSPECT 专用，V14；逻辑引用 EDC 检验计划编码，MVP 不守门存在性）。 */
    @Column(name = "inspect_plan", length = 64)
    private String inspectPlan;

    /** 抽样率（INSPECT 专用，V14；百分比 0-100，null = 100% 全检）。 */
    @Column(name = "sampling_rate", precision = 5, scale = 2)
    private java.math.BigDecimal samplingRate;

    // ---------------- 覆盖列（V22 ICM；NULL = 继承工序默认） ----------------

    /** 覆盖光罩引用（CSV，可空 = 继承 operation.reticle_ref 单值；V22 ICM 覆盖层）。 */
    @Column(name = "reticle_ref", length = 256)
    private String reticleRef;

    /** 覆盖进站污染级（可空 = 继承；对标 myCIM 污染等级控制；V22 ICM）。 */
    @Column(name = "contam_level", length = 16)
    private String contamLevel;

    /** CSV 禁止进入的污染级（可空 = 继承；CONTAM_VALID：forbidden 不含自身 level；V22 ICM）。 */
    @Column(name = "contam_forbidden", length = 128)
    private String contamForbidden;

    /** NPW 投片上限（片数，可空 = 不限；V23 产品域口径——工程批数量控制在工艺路线落点）。 */
    @Column(name = "npw_limit")
    private Integer npwLimit;
}
