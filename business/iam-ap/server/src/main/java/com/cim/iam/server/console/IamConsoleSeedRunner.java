package com.cim.iam.server.console;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时执行 IAM 控制台种子（{@link IamConsoleSeedService}）。
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)}：让首管理员引导（{@code BootstrapAdminRunner}）
 * 与平台种子（{@code cim-system} 的权限目录、{@code cim-i18n} 的内置文案）先跑完，
 * 再落控制台目录——本种子依赖「权限码已存在」来建角色授权。</p>
 *
 * <p><b>失败即失败</b>（不吞异常）：种子没跑成 = 控制台管理员没有任何权限，
 * 属「启动即故障」，让应用启动失败比带病运行更容易被发现与修复。
 * 幂等，故重启安全；如需临时跳过用 {@code cim.iam.console.seed-enabled=false}。</p>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class IamConsoleSeedRunner implements ApplicationRunner {

    private final IamConsoleSeedService seedService;
    private final IamConsoleProperties properties;

    public IamConsoleSeedRunner(IamConsoleSeedService seedService, IamConsoleProperties properties) {
        this.seedService = seedService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSeedEnabled()) {
            log.info("[iam-console] 控制台种子已禁用（cim.iam.console.seed-enabled=false）");
            return;
        }
        seedService.seed();
    }
}
