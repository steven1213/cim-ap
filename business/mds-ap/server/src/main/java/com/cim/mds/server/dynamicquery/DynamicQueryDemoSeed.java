package com.cim.mds.server.dynamicquery;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 动态查询演示种子（V39，幂等 ensure）——全链路演示「查询定义 + 视图模型」：
 * QRY-CONS-DEMO（cim_consumable 台账查询，DICT 参数按类别过滤）+
 * VM-CONS-DEMO（6 列：编码/名称/类别(字典)/单位/库存(数字右对齐)/生命周期）。
 *
 * <p>种子经由 QueryAdminService / ViewModelAdminService 创建——与页面保存走同一条
 * 三层验证链路（语法 + 参数一致 + dry-run），种子即验证用例。无工艺链依赖，
 * 独立 Runner。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicQueryDemoSeed {

    private final QueryAdminService queryAdminService;
    private final ViewModelAdminService viewModelAdminService;
    private final CimQueryDefRepository defRepository;
    private final CimViewModelRepository viewModelRepository;
    private final com.cim.mds.server.console.MdsDictSeedService dictSeedService;

    /** 方言中立演示 SQL：cim_consumable 直查，类别字典参数可选过滤。 */
    private static final String DEMO_SQL = """
            SELECT cons_code, cons_name, cons_type, unit, stock_qty, lifecycle
            FROM cim_consumable
            WHERE deleted = FALSE
              AND (:consType IS NULL OR cons_type = :consType)""";

    void seed() {
        // ⚠️ Runner 时序：字典种子同为 ApplicationRunner，先后不定——VM 列格式化字典
        // 校验依赖 mds.dq_format_type，必须显式先幂等跑字典种子（ConsBomDemoSeed 同款坑）
        dictSeedService.seed();
        if (defRepository.findByQueryCodeAndDeletedFalse("QRY-CONS-DEMO").isEmpty()) {
            queryAdminService.create(new QueryAdminService.QueryReq(
                    "QRY-CONS-DEMO", "耗材台账演示查询", DEMO_SQL, 500, true,
                    List.of(new QueryAdminService.ParamReq(
                            "consType", "耗材类别", "DICT", false, "mds.consumable_type", 10))));
            log.info("[mds-dq-seed] 演示查询 QRY-CONS-DEMO 已建");
        }
        if (viewModelRepository.findByViewCodeAndDeletedFalse("VM-CONS-DEMO").isEmpty()) {
            viewModelAdminService.create(new ViewModelAdminService.ViewModelReq(
                    "VM-CONS-DEMO", "耗材台账演示视图", "QRY-CONS-DEMO", null, true, 20, true,
                    List.of(
                            new ViewModelAdminService.ColReq("cons_code", "耗材编码", null, 140, "LEFT", true, "NONE", null, 10),
                            new ViewModelAdminService.ColReq("cons_name", "耗材名称", null, 220, "LEFT", true, "NONE", null, 20),
                            new ViewModelAdminService.ColReq("cons_type", "类别", null, 110, "LEFT", true, "DICT", "mds.consumable_type", 30),
                            new ViewModelAdminService.ColReq("unit", "单位", null, 80, "LEFT", true, "NONE", null, 40),
                            new ViewModelAdminService.ColReq("stock_qty", "当前库存", null, 110, "RIGHT", true, "NUMBER", null, 50),
                            new ViewModelAdminService.ColReq("lifecycle", "生命周期", null, 110, "LEFT", true, "NONE", null, 60))));
            log.info("[mds-dq-seed] 演示视图 VM-CONS-DEMO 已建");
        }
    }

    @Slf4j
    @Component
    @Order(Ordered.LOWEST_PRECEDENCE)
    static class SeedRunner implements ApplicationRunner {

        private final DynamicQueryDemoSeed seed;

        SeedRunner(DynamicQueryDemoSeed seed) {
            this.seed = seed;
        }

        @Override
        public void run(ApplicationArguments args) {
            try {
                seed.seed();
            } catch (Exception e) {
                log.warn("[mds-dq-seed] 动态查询演示种子失败（可在页面手工补建）：{}", e.getMessage(), e);
            }
        }
    }
}
