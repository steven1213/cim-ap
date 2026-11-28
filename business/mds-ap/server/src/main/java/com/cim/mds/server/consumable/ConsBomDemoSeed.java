package com.cim.mds.server.consumable;

import java.math.BigDecimal;
import java.util.List;

import com.cim.mds.server.flow.CimFlow;
import com.cim.mds.server.flow.CimFlowRepository;
import com.cim.mds.server.flow.CimOperation;
import com.cim.mds.server.flow.CimOperationRepository;
import com.cim.mds.server.flow.CimParamDef;
import com.cim.mds.server.flow.CimParamDefRepository;
import com.cim.mds.server.site.CimProduct;
import com.cim.mds.server.site.CimProductRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消耗品 BOM 演示种子（V38）——规格→BOM→激活状态机一整套可操作闭环，幂等 ensure。
 *
 * <p>与 {@code EdcDemoSeed}/{@code ProcessDemoSeed} 同理用 Java 种子而非 Flyway SQL：
 * ApplicationRunner 迁移后运行、按业务码查重「存在即跳过」。依赖演示链既有数据
 * （{@code ProcessDemoSeed} 的 PROD-01 / FLW-6IN-A / OPR-ETS-30，{@code V34} 的
 * CM-SL-001 / CM-PD-001），另补一条 GAS 规格演示保质期/批次管控属性。</p>
 *
 * <p>数据链：CM-GA-001 特气规格（shelf_life/ldu/lot_control 全属性演示）→
 * CBOM-6IN-ETS-01 ACTIVE（三行：固定用量×2 + 变量消耗×1，同上下文现行基准）+
 * CBOM-6IN-ETS-02 DRAFT（演示「复制→编辑→激活」动线：激活后旧 ACTIVE 自动
 * SUPERSEDED，版本号递增）。N2_FLOW 参数由 EDC 种子建；缺失时本种子幂等补建。</p>
 */
public final class ConsBomDemoSeed {

    private ConsBomDemoSeed() {}

    @Slf4j
    @Service
    static class SeedService {

        private final CimConsumableRepository specRepository;
        private final CimConsBomRepository bomRepository;
        private final CimConsBomLineRepository lineRepository;
        private final CimProductRepository productRepository;
        private final CimFlowRepository flowRepository;
        private final CimOperationRepository operationRepository;
        private final CimParamDefRepository paramDefRepository;

        SeedService(CimConsumableRepository specRepository, CimConsBomRepository bomRepository,
                CimConsBomLineRepository lineRepository, CimProductRepository productRepository,
                CimFlowRepository flowRepository, CimOperationRepository operationRepository,
                CimParamDefRepository paramDefRepository) {
            this.specRepository = specRepository;
            this.bomRepository = bomRepository;
            this.lineRepository = lineRepository;
            this.productRepository = productRepository;
            this.flowRepository = flowRepository;
            this.operationRepository = operationRepository;
            this.paramDefRepository = paramDefRepository;
        }

        /** 演示 BOM 行（specCode + 消耗规则；VARIABLE 行挂 N2_FLOW）。 */
        record LineDef(String specCode, String consumeType, String basis, double stdQty,
                Double tolerancePct, String paramKey, Double paramQtyPerUnit,
                String substituteGroup, String expiryPolicy) {}

        static final String DEMO_PRODUCT = "PROD-01";
        static final String DEMO_FLOW = "FLW-6IN-A";
        static final String DEMO_OP = "OPR-ETS-30";
        static final String DEMO_PARAM = "N2_FLOW";

        @Transactional
        public void seed() {
            int specs = ensureSpecs();
            int boms = ensureBoms();
            if (specs + boms > 0) {
                log.info("[mds-consbom-seed] 演示种子完成：+{} 规格 / +{} BOM", specs, boms);
            }
        }

        /** 演示特气规格（全过程控制属性：保质期/LDU/存储回温/密度/替代组/批次管控）。 */
        private int ensureSpecs() {
            if (specRepository.findByConsCodeAndDeletedFalse("CM-GA-001").isPresent()) {
                return 0;
            }
            CimConsumable s = new CimConsumable();
            s.setConsCode("CM-GA-001");
            s.setConsName("刻蚀用 CF4 特气");
            s.setSpecModel("CF4 99.999% 47L");
            s.setConsType("GAS");
            s.setUnit("KG");
            s.setLifeType("NONE");
            s.setStockQty(new BigDecimal("12.0"));
            s.setMinQty(new BigDecimal("4.0"));
            s.setEnabled(Boolean.TRUE);
            s.setMaterialKind(CimConsumable.KIND_CONSUMABLE);
            s.setLifecycle(CimConsumable.LC_ACTIVE);
            s.setShelfLifeDays(1095);
            s.setLduHours(0);
            s.setStorageTemp("常温避压");
            s.setTolerancePct(new BigDecimal("2.00"));
            s.setDensityFactor(new BigDecimal("1.000000"));
            s.setSubstituteGroup("GA-ETS");
            s.setLotControl(Boolean.TRUE);
            specRepository.save(s);
            return 1;
        }

        private int ensureBoms() {
            CimProduct product = productRepository.findByProductCodeAndDeletedFalse(DEMO_PRODUCT).orElse(null);
            CimFlow flow = flowRepository.findByFlowCodeAndDeletedFalse(DEMO_FLOW).orElse(null);
            CimOperation op = operationRepository.findByOpCodeAndDeletedFalse(DEMO_OP).orElse(null);
            if (product == null || flow == null || op == null) {
                log.info("[mds-consbom-seed] 演示链（{}/{}/{}）未就绪，跳过 BOM 种子（工艺链种子先跑）",
                        DEMO_PRODUCT, DEMO_FLOW, DEMO_OP);
                return 0;
            }
            int created = 0;
            created += ensureBom("CBOM-6IN-ETS-01", "6寸刻蚀工序消耗基准 V1",
                    product, flow, op, CimConsBom.STATUS_ACTIVE, 1, List.of(
                            new LineDef("CM-GA-001", CimConsBomLine.CT_VARIABLE, "PER_WAFER",
                                    0, null, DEMO_PARAM, 0.02, null, null),
                            new LineDef("CM-SL-001", CimConsBomLine.CT_FIXED, "PER_LOT",
                                    2, 5.0, null, null, null, "FIFO"),
                            new LineDef("CM-PD-001", CimConsBomLine.CT_FIXED, "PER_RUN",
                                    1, null, null, null, null, null)));
            created += ensureBom("CBOM-6IN-ETS-02", "6寸刻蚀工序消耗基准 V2（演示稿）",
                    product, flow, op, CimConsBom.STATUS_DRAFT, 0, List.of(
                            new LineDef("CM-GA-001", CimConsBomLine.CT_VARIABLE, "PER_WAFER",
                                    0, null, DEMO_PARAM, 0.018, null, null),
                            new LineDef("CM-PD-001", CimConsBomLine.CT_FIXED, "PER_RUN",
                                    1, null, null, null, null, null)));
            return created;
        }

        private int ensureBom(String bomCode, String bomName, CimProduct product, CimFlow flow,
                CimOperation op, String status, int versionNo, List<LineDef> lines) {
            if (bomRepository.findByBomCodeAndDeletedFalse(bomCode).isPresent()) {
                return 0;
            }
            CimParamDef param = ensureParam();
            CimConsBom bom = new CimConsBom();
            bom.setBomCode(bomCode);
            bom.setBomName(bomName);
            bom.setProductId(product.getId());
            bom.setFlowId(flow.getId());
            bom.setOperationId(op.getId());
            bom.setStatus(status);
            bom.setVersionNo(versionNo);
            bom.setSourceOpVersion(op.getVersionNo());
            bom.setEnabled(Boolean.TRUE);
            bom = bomRepository.save(bom);
            int sort = 0;
            for (LineDef def : lines) {
                CimConsumable spec = specRepository.findByConsCodeAndDeletedFalse(def.specCode()).orElse(null);
                if (spec == null) {
                    continue;
                }
                CimConsBomLine line = new CimConsBomLine();
                line.setBomId(bom.getId());
                line.setSpecId(spec.getId());
                line.setConsumeType(def.consumeType());
                line.setBasis(def.basis());
                line.setStdQty(BigDecimal.valueOf(def.stdQty()));
                if (def.tolerancePct() != null) {
                    line.setTolerancePct(BigDecimal.valueOf(def.tolerancePct()));
                }
                if (def.paramKey() != null && param != null) {
                    line.setParamKey(def.paramKey());
                    if (def.paramQtyPerUnit() != null) {
                        line.setParamQtyPerUnit(BigDecimal.valueOf(def.paramQtyPerUnit()));
                    }
                }
                if (def.substituteGroup() != null) {
                    line.setSubstituteGroup(def.substituteGroup());
                }
                if (def.expiryPolicy() != null) {
                    line.setExpiryPolicy(def.expiryPolicy());
                }
                line.setSortNo(++sort * 10);
                lineRepository.save(line);
            }
            return 1;
        }

        /** N2_FLOW 由 EDC 种子建；缺失时幂等补建（纯引用参数，不进 KV scopes）。 */
        private CimParamDef ensureParam() {
            return paramDefRepository.findByAttrKeyAndDeletedFalse(DEMO_PARAM).orElseGet(() -> {
                CimParamDef def = new CimParamDef();
                def.setAttrKey(DEMO_PARAM);
                def.setAttrName("N2 流量");
                def.setGroupCode("DC");
                def.setValueType(CimParamDef.VT_DECIMAL);
                def.setUnit("sccm");
                def.setScopes("");
                def.setSortNo(90);
                return paramDefRepository.save(def);
            });
        }
    }

    @Slf4j
    @Component
    @Order(Ordered.LOWEST_PRECEDENCE)
    static class SeedRunner implements ApplicationRunner {

        private final com.cim.mds.server.flow.ProcessDemoSeed.SeedService processSeed;
        private final SeedService seedService;

        SeedRunner(com.cim.mds.server.flow.ProcessDemoSeed.SeedService processSeed, SeedService seedService) {
            this.processSeed = processSeed;
            this.seedService = seedService;
        }

        @Override
        public void run(ApplicationArguments args) {
            try {
                // BOM 演示链依赖工艺链种子（PROD-01/FLW-6IN-A/OPR-ETS-30）就绪：
                // 显式幂等先跑一遍（已存在即 +0），不依赖 Runner 排序——
                // H2 每次重启清数据，同 Order 的 Runner 顺序不定，靠排序会永远建不出 BOM
                processSeed.seed();
                seedService.seed();
            } catch (Exception e) {
                log.warn("[mds-consbom-seed] 消耗品 BOM 种子失败（可在页面手工补建）：{}", e.getMessage(), e);
            }
        }
    }
}
