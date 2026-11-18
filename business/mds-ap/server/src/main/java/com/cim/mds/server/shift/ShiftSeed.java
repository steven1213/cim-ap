package com.cim.mds.server.shift;

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
 * 班次/班制种子（V17）——三班示例幂等写入（按 code 查重）。
 *
 * <p>用 Java 种子而非 Flyway SQL：与 {@link MdsDictSeedService} 同理——种子统一走
 * ApplicationRunner（迁移之后运行、幂等不冲掉页面维护数据）。晚班 22:00-06:00
 * 演示跨天口径；「正倒班」演示周期模式铺排。</p>
 *
 * <p>⚠️ 测试上下文里 Runner 同样执行（见 MEMORY 2026-11-17）：断言别假设「表为空」，
 * 新增断言一律用本类的 ensure 语义而非硬编码库内总量。</p>
 */
public final class ShiftSeed {

    private ShiftSeed() {}

    @Slf4j
    @Service
    static class SeedService {

        private final CimShiftDefRepository shiftRepository;
        private final CimShiftPatternDefRepository patternRepository;

        SeedService(CimShiftDefRepository shiftRepository, CimShiftPatternDefRepository patternRepository) {
            this.shiftRepository = shiftRepository;
            this.patternRepository = patternRepository;
        }

        record ShiftDef(String code, String name, String start, String end, boolean crossDay,
                String color, int sortNo, String description) {}

        static final List<ShiftDef> SHIFTS = List.of(
                new ShiftDef("EARLY", "早班", "08:00", "16:00", false, "blue", 10, "白班主段"),
                new ShiftDef("MIDDLE", "中班", "16:00", "24:00", false, "teal", 20, "中班段"),
                new ShiftDef("LATE", "晚班", "22:00", "06:00", true, "purple", 30, "跨天班次（work_date 记开始那天）"));

        /** 三班两运转示例：奇数日早/中，偶数日晚/早。 */
        static final List<String> PATTERN_DETAIL = List.of("EARLY", "MIDDLE", "EARLY", "LATE");

        @Transactional
        public void ensure() {
            for (ShiftDef d : SHIFTS) {
                Optional<CimShiftDef> existing = shiftRepository.findByShiftCodeAndDeletedFalse(d.code());
                if (existing.isEmpty()) {
                    CimShiftDef e = new CimShiftDef();
                    e.setShiftCode(d.code());
                    e.setShiftName(d.name());
                    e.setStartTime(d.start());
                    e.setEndTime(d.end());
                    e.setCrossDay(d.crossDay());
                    e.setColor(d.color());
                    e.setEnabled(true);
                    e.setSortNo(d.sortNo());
                    e.setDescription(d.description());
                    shiftRepository.save(e);
                    log.info("[ShiftSeed] 班次种子写入: {}", d.code());
                }
            }
            if (patternRepository.findByPatternCodeAndDeletedFalse("REGULAR").isEmpty()) {
                CimShiftPatternDef p = new CimShiftPatternDef();
                p.setPatternCode("REGULAR");
                p.setPatternName("三班两运转（4 天周期）");
                p.setCycleDays(PATTERN_DETAIL.size());
                p.setPatternDetail(String.join(",", PATTERN_DETAIL));
                p.setEnabled(true);
                p.setSortNo(10);
                p.setDescription("示例班制：EARLY→MIDDLE→EARLY→LATE 循环");
                patternRepository.save(p);
                log.info("[ShiftSeed] 班制种子写入: REGULAR");
            }
        }
    }

    /** Runner 入口（晚于 console 种子执行，Order 与 reason 种子对齐）。 */
    @Slf4j
    @Component
    @Order(Ordered.LOWEST_PRECEDENCE)
    static class Runner implements ApplicationRunner {

        private final SeedService seedService;

        Runner(SeedService seedService) {
            this.seedService = seedService;
        }

        @Override
        public void run(ApplicationArguments args) {
            seedService.ensure();
        }
    }
}
