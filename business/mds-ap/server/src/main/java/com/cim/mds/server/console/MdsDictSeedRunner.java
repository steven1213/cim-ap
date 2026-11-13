package com.cim.mds.server.console;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时执行 MDS 数据字典种子（{@link MdsDictSeedService}）。
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)}：与控制台种子同级，在平台迁移（含 V1000 建
 * sys_dict 表）全部完成后运行。<b>失败即失败</b>（不吞异常）：产品保存的字典校验
 * 依赖这两本字典，缺失＝产品域写入链路带病。</p>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MdsDictSeedRunner implements ApplicationRunner {

    private final MdsDictSeedService seedService;

    public MdsDictSeedRunner(MdsDictSeedService seedService) {
        this.seedService = seedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedService.seed();
    }
}
