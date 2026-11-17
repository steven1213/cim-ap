package com.cim.mds.server.reason;

import java.util.List;
import java.util.Optional;

import com.cim.mds.server.console.MdsDictSeedService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 原因码类型种子（V16）——8 类 WIP 事务语义域幂等写入（按 type_key 查重）。
 *
 * <p>用 Java 种子而非 Flyway SQL：与 {@link MdsDictSeedService} 同理——种子统一走
 * ApplicationRunner（迁移之后运行、幂等不冲掉页面维护数据）。测试上下文里 Runner
 * 同样执行，测试直接消费种子类型（见 {@code ReasonCodeTest}）。</p>
 */
public final class ReasonTypeSeed {

    private ReasonTypeSeed() {}

    /** 8 类种子（与 WIP 事务语义对齐；typeKey 与 {@link CimReasonCodeType} 常量一致）。 */
    @Slf4j
    @Service
    static class SeedService {

        record TypeDef(String typeKey, String typeName, int sortNo, String description) {}

        static final List<TypeDef> TYPES = List.of(
                new TypeDef(CimReasonCodeType.HOLD, "挂起（暂停等释放）", 10, "批次/载具管控挂起的归因"),
                new TypeDef(CimReasonCodeType.RELEASE, "释放", 20, "Hold 解除/放行归因"),
                new TypeDef(CimReasonCodeType.SCRAP, "报废", 30, "整批或晶圆报废归因"),
                new TypeDef(CimReasonCodeType.REWORK, "返工", 40, "重做工序归因"),
                new TypeDef(CimReasonCodeType.OFF_FLOW, "跳离流程", 50, "临时改道/搬出正常路线"),
                new TypeDef(CimReasonCodeType.PAUSE, "暂停", 60, "设备/批次级暂停（与管控 Hold 区分）"),
                new TypeDef(CimReasonCodeType.DEFECT, "缺陷", 70, "缺陷检测归因"),
                new TypeDef(CimReasonCodeType.LOSS, "损耗", 80, "数量损失归因（非整批处置）"));

        private final CimReasonCodeTypeRepository repository;

        SeedService(CimReasonCodeTypeRepository repository) {
            this.repository = repository;
        }

        @Transactional
        public void seed() {
            int created = 0;
            for (TypeDef def : TYPES) {
                Optional<CimReasonCodeType> existing =
                        repository.findByTypeKeyAndDeletedFalse(def.typeKey());
                if (existing.isPresent()) {
                    continue; // 幂等：已存在即跳过（页面维护过的名称不被冲掉）
                }
                CimReasonCodeType type = new CimReasonCodeType();
                type.setTypeKey(def.typeKey());
                type.setTypeName(def.typeName());
                type.setSortNo(def.sortNo());
                type.setDescription(def.description());
                repository.save(type);
                created++;
            }
            if (created > 0) {
                log.info("[mds-reason] 原因码类型种子完成：+{} / {} 类", created, TYPES.size());
            }
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
