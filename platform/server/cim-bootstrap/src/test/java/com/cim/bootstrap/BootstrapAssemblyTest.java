package com.cim.bootstrap;

import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.auth.token.TokenVersionChecker;
import com.cim.system.rbac.DbLocalAuthorityLoader;
import com.cim.system.user.SysUserController;
import com.cim.system.user.SysUserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装配冒烟测试：验证 {@code cim-bootstrap} 这条**唯一可执行模块**的链路真的能起来——
 * 即「加依赖即可用」在宿主侧的最终兑现。
 *
 * <p>覆盖三层：① 数据源 + Flyway（域模块自带 h2 脚本）跑通，且 {@code ddl-auto=validate}
 * 下实体与表结构一致；② 引入 cim-system 后，其 {@code DbLocalAuthorityLoader} 顶掉认证 starter
 * 的默认 {@code ClaimLocalAuthorityLoader}；③ 认证链（{@code SecurityFilterChain}）就位。</p>
 *
 * <p>上下文能加载本身就是强断言：若缺数据源、实体/仓储未被扫描、或表结构与实体不符，
 * {@code @SpringBootTest} 会直接失败。</p>
 */
@SpringBootTest(classes = CimApApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BootstrapAssemblyTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void assemblesStarterAndSystemDomainOverRealDatasource() {
        // ① 域模块的实体/仓储被扫描到（靠 cim-system 自带的 @EntityScan/@EnableJpaRepositories）
        assertThat(context.getBean(SysUserRepository.class))
                .as("cim-system 的仓储应已注册（未被扫描则会启动失败）")
                .isNotNull();
        // JPA 可实际访问库：证明确有数据源，且 validate 通过（表结构由 Flyway 建好）
        assertThat(context.getBean(SysUserRepository.class).count()).isNotNegative();

        // ② SPI 让位：域模块实现顶掉认证 starter 的默认实现
        assertThat(context.getBean(LocalAuthorityLoader.class))
                .isInstanceOf(DbLocalAuthorityLoader.class);

        // ③ 认证链与版本失效 SPI 均在位
        assertThat(context.getBean(SecurityFilterChain.class)).isNotNull();
        assertThat(context.getBean(TokenVersionChecker.class)).isNotNull();

        // ④ 域模块的控制器被扫描（业务端点可用）
        assertThat(context.getBean(SysUserController.class)).isNotNull();
    }

    @Test
    void actuatorHealthIsUp() {
        ResponseEntity<String> resp = rest.getForEntity("/actuator/health", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("健康端点应匿名可达（SecurityFilterChain 放行 + 无 @PreAuthorize）")
                .isTrue();
        assertThat(resp.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void unknownPathReturns404NotSystemError() {
        ResponseEntity<String> resp = rest.getForEntity("/no-such-endpoint", String.class);
        assertThat(resp.getStatusCode().value())
                .as("路径不存在应为 404，而非被全局兜底成 500")
                .isEqualTo(404);
        assertThat(resp.getBody()).contains("\"code\":2001");
    }

    @Test
    void protectedBusinessEndpointWithoutToken401() {
        ResponseEntity<String> resp = rest.getForEntity("/sys/users/page", String.class);
        assertThat(resp.getStatusCode().value())
                .as("方法级 @PreAuthorize 对匿名请求应返回 401")
                .isEqualTo(401);
    }

    @Test
    void pathVariableEndpointResolvesTo404NotSystemError() {
        // 回归守卫（两件事一起守）：
        // ① 父 POM 必须开启 -parameters——否则 @PathVariable 未显式命名会抛
        //    IllegalArgumentException("Name for argument ... not specified") → 500；
        // ② 字典不存在应映射为 404（BizException DATA_NOT_FOUND），而非被兜底成 500。
        ResponseEntity<String> resp =
                rest.getForEntity("/sys/dicts/code/not-a-dict/items", String.class);
        assertThat(resp.getStatusCode().value())
                .as("@PathVariable 应被正确解析且业务异常映射为 404")
                .isEqualTo(404);
        assertThat(resp.getBody()).contains("\"code\":2001");
    }
}
