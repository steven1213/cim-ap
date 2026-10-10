package com.cim.system.autoconfigure;

import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.system.log.OperationLogAspect;
import com.cim.system.log.OperationLogService;
import com.cim.system.permission.SysPermissionRepository;
import com.cim.system.permission.SysPermissionSeeder;
import com.cim.system.rbac.DbLocalAuthorityLoader;
import com.cim.system.rbac.LocalUserResolver;
import com.cim.system.rbac.SysRbacService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * cim-system 模块装配（design.md §2.10）。
 *
 * <p><b>刻意用「组件扫描」而非 {@code AutoConfiguration.imports}</b>：本模块是<b>业务域模块</b>，
 * 不是 starter——宿主 ap 通过「加依赖」获得一整套后台管理域（实体 + 仓储 + 服务 + 控制器 +
 * 迁移 + 权限加载器），而不是「加依赖获得一个可插拔的横切能力」。这个区别就是 README §1 与
 * design.md §2 中「域模块 vs starter」的分界，也是 IAM（另一层，见 §8）不会被误当作
 * "同类选项"的原因。</p>
 *
 * <p><b>扫描根 = 本模块自己的包（{@code com.cim.system}），不是 {@code com.cim}</b>：
 * Spring Data 的仓储注册器<b>不做重名去重</b>——若两个 {@code @EnableJpaRepositories} 的包范围<b>重叠</b>
 * （例如本模块声明 {@code com.cim}，而 {@code cim-i18n-starter} 声明 {@code com.cim.i18n}），
 * 同一个仓储 bean 会被注册两次，Spring Boot 2.1+ 默认禁止 bean 覆盖 → 启动即
 * {@code BeanDefinitionOverrideException}。<b>故各单元只声明自己的包</b>：
 * 本模块 {@code com.cim.system}、i18n {@code com.cim.i18n}、宿主 ap 自己的包——
 * 三者互不重叠，「加依赖即可用」依然成立（宿主只需补自己的包）。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CimSystemProperties.class)
@ConditionalOnProperty(prefix = "cim.system", name = "enabled", havingValue = "true", matchIfMissing = true)
@EntityScan(basePackages = "com.cim.system")
@EnableJpaRepositories(basePackages = "com.cim.system")
public class CimSystemConfiguration {

    /**
     * 用本 ap 的 RBAC 表替换 {@code cim-auth-starter} 的默认 {@code ClaimLocalAuthorityLoader}
     * （后者声明了 {@code @ConditionalOnMissingBean}，会被本 Bean 顶掉）。
     *
     * <p>这就是「业务内部权限各 ap 自管」的接缝落地：接口在 platform，实现在本模块；
     * 不引入本模块时自动退回读令牌 claim 的 M3 行为，<b>互不影响</b>。</p>
     */
    @Bean
    @ConditionalOnMissingBean(LocalAuthorityLoader.class)
    public LocalAuthorityLoader cimDbLocalAuthorityLoader(LocalUserResolver userResolver,
                                                         SysRbacService rbacService,
                                                         CimSystemProperties properties) {
        return new DbLocalAuthorityLoader(userResolver, rbacService, properties);
    }

    /**
     * 操作日志切面（{@code @OperationLogged}）。
     *
     * <p>以 Bean 形式在此声明而非给切面打 {@code @Component}：切面是否启用应受
     * {@code cim.system.enabled} 一并控制（本类已加 {@code @ConditionalOnProperty}），
     * 避免「模块关了但审计仍在写表」。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public OperationLogAspect operationLogAspect(OperationLogService operationLogService) {
        return new OperationLogAspect(operationLogService);
    }

    /**
     * 平台权限目录种子（幂等）。见 {@link SysPermissionSeeder} 说明——
     * 目录为空会导致超管被 {@code hasAuthority('sys:*')} 拒绝。
     */
    @Bean
    @ConditionalOnProperty(prefix = "cim.system", name = "seed-permissions",
            havingValue = "true", matchIfMissing = true)
    public SysPermissionSeeder sysPermissionSeeder(SysPermissionRepository permissionRepository) {
        return new SysPermissionSeeder(permissionRepository);
    }
}
