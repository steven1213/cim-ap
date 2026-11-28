package com.cim.mds.server.flow;

import java.util.List;

import com.cim.mds.server.console.MdsDictSeedService;
import com.cim.mds.server.equipment.CapabilityAdminService;
import com.cim.mds.server.equipment.CimCapabilityRepository;
import com.cim.mds.server.equipment.CimEquipmentClassRepository;
import com.cim.mds.server.equipment.EquipmentClassAdminService;
import com.cim.mds.server.reticle.ReticleAdminService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工艺链演示种子——工序库 / 设备指派 / 制造流程三页一整套可操作闭环，幂等 ensure。
 *
 * <p><b>为什么需要</b>：技术（V5）/产品（V6）/机型·能力·机台（V7）都有 Flyway 演示种子，
 * 唯独工序库与流程域（V20/V12 起）一直空白——页面进全是空表，画布引用工序、
 * 配方解算这些核心动线无从演示。种子补齐「工序 ACTIVE → L0 默认指派 → 直线流程
 * 生成 → 校验 → 冻结 → 激活」全链，与 V4/V7 演示链同口径：
 * ApplicationRunner 迁移后运行、按业务码查重「存在即跳过」（页面手工修改不被冲掉）。</p>
 *
 * <p><b>数据链</b>：补一条 METRO 检测机型（V7 只种了 MATERIAL/PROCESS）→
 * 1 条光罩（光刻节点 RETICLE_RESOLVED 硬校验的引用锚点）→
 * 4 条工序（清洗/光刻/刻蚀/终检，全部 freeze→activate 到 ACTIVE——只有 ACTIVE
 * 工序可被画布引用）→ 每工序 1 条 L0 默认指派（CLASS 行，recipe_rule=GENERATE——
 * ASSIGN_REQUIRED 校验的候选池来源）→ 1 条直线流程（generateDesign 生成画布 →
 * validate 落 VALID → freeze → activate，生产线现行版本）。</p>
 *
 * <p>复用上游种子引用：技术 TECH-01 / 机型 ET-CLEAN·ET-LITHO·ET-ETCH /
 * 能力 CAP-CLEAN·CAP-6INCH（均 V7/V5 种子）。字典依赖显式化：光罩的尺寸/状态走
 * 字典值守门，{@code run()} 先幂等跑一遍字典种子（{@link MdsDictSeedService#seed()}
 * 已存在即 +0）再执行本链——不依赖 ApplicationRunner 的 @Order 排序
 * （LOWEST_PRECEDENCE+1 会整型溢出变最早，实测踩坑）。失败仅告警不阻断启动，
 * 管理员可在页面手工补建。</p>
 */
public final class ProcessDemoSeed {

    private ProcessDemoSeed() {}

    @Slf4j
    @Service
    public static class SeedService {

        // ------------------------------------------------------------------ 种子定义

        /** 检测机型种子定义（V7 未覆盖 METRO 大类，工序终检步骤的资格门）。 */
        private static final String METRO_CLASS_CODE = "ET-WIS";
        private static final String METRO_CLASS_NAME = "光学终检机";

        /** 光罩种子定义（光刻工序挂接；wafer/plate 尺寸走 mds.wafer_size / mds.reticle_plate_size 字典值）。 */
        private record ReticleDef(String code, String name, String layer, String techCode) {}
        private static final ReticleDef RETICLE =
                new ReticleDef("RET-AL-01", "铝金属层光罩", "AL", "TECH-01");

        /**
         * 工序种子定义（opCode 即版本族；equipType/capabilityCode 逻辑引用 V7 种子；
         * 光刻带 reticleRef——LITHO 类节点解析后光罩必填的硬校验）。
         */
        record OpDef(String opCode, String opName, String opType, String equipType,
                     String capabilityCode, String reticleRef, Integer qtimeMax,
                     boolean holdPoint, String holdReason, Integer samplingRate) {}

        static final List<OpDef> OPS = List.of(
                new OpDef("OPR-CLE-10", "硅片清洗", CimOperation.TYPE_PROCESS,
                        "ET-CLEAN", "CAP-CLEAN", null, 240, false, null, null),
                new OpDef("OPR-LIT-20", "光刻", CimOperation.TYPE_PROCESS,
                        "ET-LITHO", "CAP-6INCH", RETICLE.code(), 120, true,
                        "光刻关键尺寸卡控点，跳步须工艺工程师会签", null),
                new OpDef("OPR-ETS-30", "刻蚀", CimOperation.TYPE_PROCESS,
                        "ET-ETCH", "CAP-6INCH", null, 360, false, null, null),
                new OpDef("OPR-WIS-40", "终检", CimOperation.TYPE_INSPECT,
                        METRO_CLASS_CODE, "CAP-6INCH", null, 480, false, null, 100));

        /** 直线流程种子定义（operationCodes 顺序即画布顺序）。 */
        private static final String FLOW_CODE = "FLW-6IN-A";

        /**
         * 工艺长尾 KV 参数种子（ICM 三层演示数据；EDC 种子的 DC 参数是纯字典参数
         * 不进 KV 层——本批补齐真正可配在层上的参数）。scopes 决定出现在哪层候选：
         * PILOT/SPC 只配节点；SEASON 三层贯通（工序可变参数→流程覆盖→节点覆盖链路演示）。
         */
        record ParamDefSeed(String attrKey, String attrName, String groupCode, String valueType,
                            String scopes, Boolean required, Integer sortNo) {}
        static final List<ParamDefSeed> PARAM_DEFS = List.of(
                new ParamDefSeed("pilot_sample_ratio", "抽检比例(%)", "PILOT",
                        CimParamDef.VT_INT, "STEP", false, 10),
                new ParamDefSeed("season_warm_wafers", "暖机片数", "SEASON",
                        CimParamDef.VT_INT, "OPERATION,FLOW,STEP", false, 20),
                new ParamDefSeed("spc_skip_flag", "SPC 跳测标记", "SPC",
                        CimParamDef.VT_BOOLEAN, "STEP", false, 30));

        // ------------------------------------------------------------------ 依赖

        private final CimEquipmentClassRepository classRepository;
        private final CimCapabilityRepository capabilityRepository;
        private final com.cim.mds.server.reticle.CimReticleRepository reticleRepository;
        private final CimOperationRepository operationRepository;
        private final CimEquipAssignRepository assignRepository;
        private final CimFlowRepository flowRepository;
        private final CimParamDefRepository paramDefRepository;

        private final EquipmentClassAdminService classAdminService;
        private final ReticleAdminService reticleAdminService;
        private final OperationAdminService operationAdminService;
        private final EquipAssignAdminService assignAdminService;
        private final FlowAdminService flowAdminService;

        SeedService(CimEquipmentClassRepository classRepository,
                    CimCapabilityRepository capabilityRepository,
                    com.cim.mds.server.reticle.CimReticleRepository reticleRepository,
                    CimOperationRepository operationRepository,
                    CimEquipAssignRepository assignRepository,
                    CimFlowRepository flowRepository,
                    CimParamDefRepository paramDefRepository,
                    EquipmentClassAdminService classAdminService,
                    ReticleAdminService reticleAdminService,
                    OperationAdminService operationAdminService,
                    EquipAssignAdminService assignAdminService,
                    FlowAdminService flowAdminService) {
            this.classRepository = classRepository;
            this.capabilityRepository = capabilityRepository;
            this.reticleRepository = reticleRepository;
            this.operationRepository = operationRepository;
            this.assignRepository = assignRepository;
            this.flowRepository = flowRepository;
            this.paramDefRepository = paramDefRepository;
            this.classAdminService = classAdminService;
            this.reticleAdminService = reticleAdminService;
            this.operationAdminService = operationAdminService;
            this.assignAdminService = assignAdminService;
            this.flowAdminService = flowAdminService;
        }

        // ------------------------------------------------------------------ 种子链

        @Transactional
        public void seed() {
            ensureParamDefs();
            ensureMetroClass();
            ensureReticle();
            ensureOperations();
            ensureAssignments();
            ensureFlow();
        }

        /** 工艺长尾 KV 参数（幂等按 attrKey；直接落定义不走 admin 守门）。 */
        private void ensureParamDefs() {
            int created = 0;
            for (ParamDefSeed def : PARAM_DEFS) {
                if (paramDefRepository.findByAttrKeyAndDeletedFalse(def.attrKey()).isPresent()) {
                    continue;
                }
                CimParamDef entity = new CimParamDef();
                entity.setAttrKey(def.attrKey());
                entity.setAttrName(def.attrName());
                entity.setGroupCode(def.groupCode());
                entity.setValueType(def.valueType());
                entity.setScopes(def.scopes());
                entity.setRequired(def.required());
                entity.setSortNo(def.sortNo());
                paramDefRepository.save(entity);
                created++;
            }
            if (created > 0) {
                log.info("[mds-process-seed] 工艺长尾参数种子完成：+{} 条（PILOT/SEASON/SPC）", created);
            }
        }

        /** 检测机型（METRO/INSPECT；工序终检的资格门）。 */
        private void ensureMetroClass() {
            if (classRepository.findByClassCodeAndDeletedFalse(METRO_CLASS_CODE).isPresent()) {
                return;
            }
            classAdminService.create(new EquipmentClassAdminService.EquipmentClassReq(
                    METRO_CLASS_CODE, METRO_CLASS_NAME, "METRO", "INSPECT",
                    "150", "CASSETTE", 1, 4, null, null, true, null));
            log.info("[mds-process-seed] 检测机型种子完成：{} {}", METRO_CLASS_CODE, METRO_CLASS_NAME);
        }

        /** 光罩（IN_STOCK 在库；光刻工序与节点的引用锚点）。 */
        private void ensureReticle() {
            if (reticleRepository.existsByReticleCodeAndDeletedFalse(RETICLE.code())) {
                return;
            }
            reticleAdminService.create(new ReticleAdminService.ReticleReq(
                    RETICLE.code(), RETICLE.name(), RETICLE.layer(),
                    "6", "6", RETICLE.techCode(), "IN_STOCK",
                    null, null, null, null, true));
            log.info("[mds-process-seed] 光罩种子完成：{} {}", RETICLE.code(), RETICLE.name());
        }

        /** 工序（create → freeze → activate；只有 ACTIVE 可被画布引用）。 */
        private void ensureOperations() {
            for (OpDef def : OPS) {
                if (operationRepository.findByOpCodeAndDeletedFalse(def.opCode()).isPresent()) {
                    continue;
                }
                OperationAdminService.OpView created = operationAdminService.create(
                        new OperationAdminService.OpReq(
                                def.opCode(), def.opName(), def.opType(), "TECH-01",
                                null, def.equipType(), def.capabilityCode(), null,
                                null, def.qtimeMax(), null,
                                def.holdPoint(), def.holdReason(),
                                null, null,
                                null, def.samplingRate() == null ? null
                                        : java.math.BigDecimal.valueOf(def.samplingRate()),
                                def.reticleRef(), null, null, null, null,
                                null, null));
                operationAdminService.freeze(created.id());
                operationAdminService.activate(created.id());
                log.info("[mds-process-seed] 工序种子完成：{} {}（ACTIVE）", def.opCode(), def.opName());
            }
        }

        /** L0 工序默认指派（CLASS 行 + GENERATE：ASSIGN_REQUIRED 校验的候选池来源）。 */
        private void ensureAssignments() {
            for (OpDef def : OPS) {
                boolean hasClassRow = assignRepository
                        .findByScopeTypeAndOperationCodeAndDeletedFalse(
                                CimEquipAssign.SCOPE_OPERATION, def.opCode()).stream()
                        .anyMatch(a -> CimEquipAssign.TYPE_CLASS.equals(a.getAssignType()));
                if (hasClassRow) {
                    continue;
                }
                assignAdminService.createForOperation(def.opCode(), new EquipAssignAdminService.AssignReq(
                        CimEquipAssign.TYPE_CLASS, def.equipType(), null,
                        null, CimEquipAssign.RULE_GENERATE, null,
                        null, null, null, null));
                log.info("[mds-process-seed] 工序默认指派种子完成：{} → CLASS {}", def.opCode(), def.equipType());
            }
        }

        /** 直线流程（generateDesign → validate → freeze → activate；幂等：按 flowCode 查重）。 */
        private void ensureFlow() {
            if (flowRepository.findByFlowCodeAndDeletedFalse(FLOW_CODE).isPresent()) {
                return;
            }
            FlowAdminService.FlowView flow = flowAdminService.create(new FlowAdminService.FlowReq(
                    FLOW_CODE, "6英寸标准工艺流程", "TECH-01",
                    "工艺链演示种子生成的直线流程", null, null, null, null, null));
            String id = flow.id();
            flowAdminService.generateDesign(id, new FlowAdminService.GenerateDesignReq(
                    OPS.stream().map(OpDef::opCode).toList()));
            FlowAdminService.ValidationResult result = flowAdminService.validate(id);
            if (!result.valid()) {
                // 校验不通过不强行冻结——流程留在 UNFROZEN 供页面排查，种子链其余数据不受影响
                log.warn("[mds-process-seed] 流程 {} 校验未通过，保留草稿态：{}", FLOW_CODE, result.problems());
                return;
            }
            flowAdminService.freeze(id);
            flowAdminService.activate(id);
            log.info("[mds-process-seed] 流程种子完成：{} {}（ACTIVE，{} 个工序节点）",
                    FLOW_CODE, flow.flowName(), OPS.size());
        }
    }

    @Slf4j
    @Component
    static class SeedRunner implements ApplicationRunner {

        private final MdsDictSeedService dictSeedService;
        private final SeedService seedService;

        SeedRunner(MdsDictSeedService dictSeedService, SeedService seedService) {
            this.dictSeedService = dictSeedService;
            this.seedService = seedService;
        }

        @Override
        public void run(ApplicationArguments args) {
            try {
                // 光罩种子的尺寸/状态字典值守门依赖字典先行：显式幂等跑一遍（已存在即 +0），
                // 不依赖 Runner 排序——LOWEST_PRECEDENCE+1 整型溢出反而最早执行
                dictSeedService.seed();
                seedService.seed();
            } catch (Exception e) {
                log.warn("[mds-process-seed] 工艺链种子失败（可在页面手工补建）：{}", e.getMessage(), e);
            }
        }
    }
}
