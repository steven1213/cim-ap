package com.cim.mds.server.edc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cim.mds.server.flow.CimParamDef;
import com.cim.mds.server.flow.CimParamDefRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EDC 演示数据种子（V28）——数采管理三页一整套可操作闭环，幂等 ensure。
 *
 * <p>用 Java 种子而非 Flyway SQL：与 {@code ReasonTypeSeed}/{@code MdsDictSeedService}
 * 同理——ApplicationRunner 迁移后运行、按业务码查重「存在即跳过」（页面上的手工
 * 修改不被冲掉；删除后重启会按种子重建，与 V4 演示链口径一致）。</p>
 *
 * <p>数据链：10 个 DC 参数定义（带物理单位）→ 2 个参数集（刻蚀/光刻）→
 * 2 个报告模板（GEM 报告结构显式化，SVID→参数）→ 2 条设备接口绑定
 * （EQP-ETCH-01 / EQP-LITHO-01，复用 V7 种子机台）→
 * 3 条 EDC 方案覆盖三态（ACTIVE=刻蚀带规格限 / FROZEN=光刻待发布 /
 * UNFROZEN=CVD 草稿供演示 freeze→activate）。方案引用的流程族/工序是演示
 * 悬空码（FAM-×、OPR-×），将来建同族真实流程即可闭环。</p>
 */
public final class EdcDemoSeed {

    private EdcDemoSeed() {}

    @Slf4j
    @Service
    static class SeedService {

        private final CimParamDefRepository paramDefRepository;
        private final CimEdcParamSetRepository setRepository;
        private final CimEdcParamSetItemRepository setItemRepository;
        private final CimEdcReportTplRepository tplRepository;
        private final CimEdcReportTplItemRepository tplItemRepository;
        private final CimEdcEquipMapRepository mapRepository;
        private final CimEdcPlanRepository planRepository;
        private final CimEdcPlanItemRepository planItemRepository;

        record ParamDef(String attrKey, String attrName, String unit, int sortNo) {}

        static final List<ParamDef> PARAM_DEFS = List.of(
                new ParamDef("ETCH_TIME", "刻蚀时间", "s", 10),
                new ParamDef("ETCH_RATE", "刻蚀速率", "Å/min", 20),
                new ParamDef("ETCH_UNIFORMITY", "刻蚀均匀性", "%", 30),
                new ParamDef("LITHO_DOSE", "光刻剂量", "mJ/cm²", 40),
                new ParamDef("LITHO_FOCUS", "光刻焦距偏移", "µm", 50),
                new ParamDef("FILM_THICKNESS", "膜厚", "Å", 60),
                new ParamDef("CHAMBER_TEMP", "腔体温度", "℃", 70),
                new ParamDef("CHAMBER_PRESS", "腔体压力", "Pa", 80),
                new ParamDef("N2_FLOW", "N2 流量", "sccm", 90),
                new ParamDef("PUMP_SPEED", "真空泵速", "L/s", 100));

        record SetDef(String setCode, String setName, String opCode, List<String> attrKeys) {}

        static final List<SetDef> SET_DEFS = List.of(
                new SetDef("EDCSET-ETCH", "刻蚀工序采集集", "OPR-ETCH",
                        List.of("ETCH_TIME", "ETCH_RATE", "ETCH_UNIFORMITY", "CHAMBER_TEMP", "CHAMBER_PRESS")),
                new SetDef("EDCSET-LITHO", "光刻工序采集集", "OPR-LITHO",
                        List.of("LITHO_DOSE", "LITHO_FOCUS", "FILM_THICKNESS")));

        record TplItemDef(int svid, String attrKey) {}

        record TplDef(String tplCode, String tplName, List<TplItemDef> items) {}

        static final List<TplDef> TPL_DEFS = List.of(
                new TplDef("RPT-ETCH-PROC", "刻蚀过程报告", List.of(
                        new TplItemDef(1001, "ETCH_TIME"),
                        new TplItemDef(1002, "ETCH_RATE"),
                        new TplItemDef(1003, "ETCH_UNIFORMITY"),
                        new TplItemDef(1004, "CHAMBER_TEMP"),
                        new TplItemDef(1005, "CHAMBER_PRESS"))),
                new TplDef("RPT-LITHO-PROC", "光刻过程报告", List.of(
                        new TplItemDef(2001, "LITHO_DOSE"),
                        new TplItemDef(2002, "LITHO_FOCUS"),
                        new TplItemDef(2003, "FILM_THICKNESS"))));

        record MapDef(String equipmentCode, int ceid, String tplCode) {}

        static final List<MapDef> MAP_DEFS = List.of(
                new MapDef("EQP-ETCH-01", 200, "RPT-ETCH-PROC"),
                new MapDef("EQP-LITHO-01", 201, "RPT-LITHO-PROC"));

        /** 方案明细行（lsl/target/usl；interval 仅 BY_TIME 用）。 */
        record ItemDef(String attrKey, String mode, String rule, Integer interval,
                double lsl, double target, double usl, String ooc) {}

        record PlanDef(String planCode, int versionNo, String status, String productCode,
                String family, String opCode, String setCode, List<ItemDef> items) {}

        static final List<PlanDef> PLAN_DEFS = List.of(
                new PlanDef("PLAN-ETCH", 1, CimEdcPlan.STATUS_ACTIVE, "PROD-01", "FAM-ETCH", "OPR-ETCH",
                        "EDCSET-ETCH", List.of(
                                new ItemDef("ETCH_RATE", CimEdcPlanItem.CM_AUTO, CimEdcPlanItem.SR_BY_LOT, null,
                                        1100, 1200, 1300, CimEdcPlanItem.OA_HOLD),
                                new ItemDef("ETCH_UNIFORMITY", CimEdcPlanItem.CM_AUTO, CimEdcPlanItem.SR_BY_WAFER, null,
                                        0, 3, 5, CimEdcPlanItem.OA_NOTIFY),
                                new ItemDef("CHAMBER_TEMP", CimEdcPlanItem.CM_AUTO, CimEdcPlanItem.SR_BY_TIME, 5,
                                        340, 350, 360, CimEdcPlanItem.OA_HOLD),
                                new ItemDef("CHAMBER_PRESS", CimEdcPlanItem.CM_AUTO, CimEdcPlanItem.SR_BY_TIME, 5,
                                        30, 40, 50, CimEdcPlanItem.OA_NONE),
                                new ItemDef("ETCH_TIME", CimEdcPlanItem.CM_SEMI, CimEdcPlanItem.SR_BY_LOT, null,
                                        80, 90, 100, CimEdcPlanItem.OA_NONE))),
                new PlanDef("PLAN-LITHO", 1, CimEdcPlan.STATUS_FROZEN, null, "FAM-LITHO", "OPR-LITHO",
                        "EDCSET-LITHO", List.of(
                                new ItemDef("LITHO_DOSE", CimEdcPlanItem.CM_AUTO, CimEdcPlanItem.SR_BY_WAFER, null,
                                        45, 50, 55, CimEdcPlanItem.OA_NOTIFY),
                                new ItemDef("LITHO_FOCUS", CimEdcPlanItem.CM_AUTO, CimEdcPlanItem.SR_BY_WAFER, null,
                                        -0.5, 0, 0.5, CimEdcPlanItem.OA_NONE),
                                new ItemDef("FILM_THICKNESS", CimEdcPlanItem.CM_MANUAL, CimEdcPlanItem.SR_BY_LOT, null,
                                        9800, 10000, 10200, CimEdcPlanItem.OA_NONE))),
                new PlanDef("PLAN-CVD", 1, CimEdcPlan.STATUS_UNFROZEN, null, "FAM-CVD", "OPR-CVD",
                        null, List.of(
                                new ItemDef("CHAMBER_TEMP", CimEdcPlanItem.CM_AUTO, CimEdcPlanItem.SR_BY_TIME, 10,
                                        395, 400, 405, CimEdcPlanItem.OA_HOLD),
                                new ItemDef("N2_FLOW", CimEdcPlanItem.CM_AUTO, CimEdcPlanItem.SR_BY_EVENT, null,
                                        180, 200, 220, CimEdcPlanItem.OA_NONE))));

        SeedService(CimParamDefRepository paramDefRepository, CimEdcParamSetRepository setRepository,
                CimEdcParamSetItemRepository setItemRepository, CimEdcReportTplRepository tplRepository,
                CimEdcReportTplItemRepository tplItemRepository, CimEdcEquipMapRepository mapRepository,
                CimEdcPlanRepository planRepository, CimEdcPlanItemRepository planItemRepository) {
            this.paramDefRepository = paramDefRepository;
            this.setRepository = setRepository;
            this.setItemRepository = setItemRepository;
            this.tplRepository = tplRepository;
            this.tplItemRepository = tplItemRepository;
            this.mapRepository = mapRepository;
            this.planRepository = planRepository;
            this.planItemRepository = planItemRepository;
        }

        @Transactional
        public void seed() {
            int pd = seedParamDefs();
            int sets = seedSets();
            int tpls = seedTpls();
            int maps = seedMaps();
            int plans = seedPlans();
            if (pd + sets + tpls + maps + plans > 0) {
                log.info("[mds-edc] 演示种子完成：+{} 参数定义 / +{} 参数集 / +{} 报告模板 / +{} 设备绑定 / +{} 方案",
                        pd, sets, tpls, maps, plans);
            }
        }

        private int seedParamDefs() {
            int created = 0;
            for (ParamDef def : PARAM_DEFS) {
                if (paramDefRepository.findByAttrKeyAndDeletedFalse(def.attrKey()).isPresent()) {
                    continue;
                }
                CimParamDef entity = new CimParamDef();
                entity.setAttrKey(def.attrKey());
                entity.setAttrName(def.attrName());
                entity.setGroupCode("DC");
                entity.setValueType(CimParamDef.VT_DECIMAL);
                entity.setUnit(def.unit());
                entity.setSortNo(def.sortNo());
                paramDefRepository.save(entity);
                created++;
            }
            return created;
        }

        private int seedSets() {
            int created = 0;
            for (SetDef def : SET_DEFS) {
                if (setRepository.findBySetCodeAndDeletedFalse(def.setCode()).isPresent()) {
                    continue;
                }
                CimEdcParamSet set = new CimEdcParamSet();
                set.setSetCode(def.setCode());
                set.setSetName(def.setName());
                set.setOpCode(def.opCode());
                set = setRepository.save(set);
                int sort = 0;
                for (String attrKey : def.attrKeys()) {
                    CimEdcParamSetItem item = new CimEdcParamSetItem();
                    item.setSetId(set.getId());
                    item.setAttrKey(attrKey);
                    item.setSortNo(++sort);
                    setItemRepository.save(item);
                }
                created++;
            }
            return created;
        }

        private int seedTpls() {
            int created = 0;
            for (TplDef def : TPL_DEFS) {
                if (tplRepository.findByTplCodeAndDeletedFalse(def.tplCode()).isPresent()) {
                    continue;
                }
                CimEdcReportTpl tpl = new CimEdcReportTpl();
                tpl.setTplCode(def.tplCode());
                tpl.setTplName(def.tplName());
                tpl = tplRepository.save(tpl);
                int sort = 0;
                for (TplItemDef item : def.items()) {
                    CimEdcReportTplItem row = new CimEdcReportTplItem();
                    row.setTplId(tpl.getId());
                    row.setSvid(item.svid());
                    row.setAttrKey(item.attrKey());
                    row.setSortNo(++sort);
                    tplItemRepository.save(row);
                }
                created++;
            }
            return created;
        }

        private int seedMaps() {
            int created = 0;
            for (MapDef def : MAP_DEFS) {
                CimEdcReportTpl tpl = tplRepository.findByTplCodeAndDeletedFalse(def.tplCode()).orElse(null);
                if (tpl == null || mapRepository
                        .findByEquipmentCodeAndCeidAndTplIdAndDeletedFalse(
                                def.equipmentCode(), def.ceid(), tpl.getId()).isPresent()) {
                    continue;
                }
                CimEdcEquipMap map = new CimEdcEquipMap();
                map.setEquipmentCode(def.equipmentCode());
                map.setCeid(def.ceid());
                map.setTplId(tpl.getId());
                map.setSourceChannel(CimEdcEquipMap.CH_GEM);
                mapRepository.save(map);
                created++;
            }
            return created;
        }

        private int seedPlans() {
            int created = 0;
            for (PlanDef def : PLAN_DEFS) {
                if (planRepository.findByPlanCodeAndVersionNoAndDeletedFalse(def.planCode(), def.versionNo()).isPresent()) {
                    continue;
                }
                CimEdcPlan plan = new CimEdcPlan();
                plan.setPlanCode(def.planCode());
                plan.setVersionNo(def.versionNo());
                plan.setStatus(def.status());
                plan.setProductCode(def.productCode());
                plan.setFlowFamilyCode(def.family());
                plan.setFlowVersionNo(1);
                plan.setOpCode(def.opCode());
                plan.setSetCode(def.setCode());
                if (CimEdcPlan.STATUS_ACTIVE.equals(def.status())) {
                    plan.setActivatedAt(LocalDateTime.now().withSecond(0).withNano(0));
                    plan.setActivatedBy("seed");
                }
                plan = planRepository.save(plan);
                int sort = 0;
                for (ItemDef item : def.items()) {
                    CimEdcPlanItem row = new CimEdcPlanItem();
                    row.setPlanId(plan.getId());
                    row.setAttrKey(item.attrKey());
                    row.setCollectMode(item.mode());
                    row.setSampleRule(item.rule());
                    row.setIntervalMinutes(item.interval());
                    row.setLsl(BigDecimal.valueOf(item.lsl()));
                    row.setTarget(BigDecimal.valueOf(item.target()));
                    row.setUsl(BigDecimal.valueOf(item.usl()));
                    row.setOocAction(item.ooc());
                    row.setSortNo(++sort);
                    planItemRepository.save(row);
                }
                created++;
            }
            return created;
        }
    }

    @Slf4j
    @Component
    @Order(Ordered.LOWEST_PRECEDENCE)
    static class SeedRunner implements ApplicationRunner {

        private final SeedService seedService;

        SeedRunner(SeedService seedService) {
            this.seedService = seedService;
        }

        @Override
        public void run(ApplicationArguments args) {
            seedService.seed();
        }
    }
}
