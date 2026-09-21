package com.cim.jpa.it;

import com.cim.core.port.CurrentUserPort;
import com.cim.core.port.TenantPort;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Set;

/**
 * 集成测试启动类：以 H2 内存库验证「主键注入 + 审计填充 + 自动历史」全链路。
 *
 * <p>包根 {@code com.cim.jpa.it} 即实体与仓储的扫描根，无需额外 {@code @EntityScan}。</p>
 *
 * <p>这里提供的 {@link CurrentUserPort} / {@link TenantPort} 属**用户配置**，必然优先于
 * {@code cim-spring-support} 的默认实现（{@code @ConditionalOnMissingBean}）。</p>
 */
@SpringBootApplication
public class TestApplication {

    @Bean
    CurrentUserPort currentUserPort() {
        return new FixedCurrentUserPort();
    }

    @Bean
    TenantPort tenantPort() {
        return () -> "T1";
    }

    /** 固定操作人，便于断言审计字段。 */
    static class FixedCurrentUserPort implements CurrentUserPort {
        @Override
        public String userId() {
            return "U1";
        }

        @Override
        public String username() {
            return "tester";
        }

        @Override
        public Set<String> authorities() {
            return Set.of("sys:equipment:list");
        }

        @Override
        public boolean isSuper() {
            return false;
        }
    }
}
