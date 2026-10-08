package com.cim.system.autoconfigure;

import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.system.log.OperationLogAspect;
import com.cim.system.log.OperationLogService;
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
 * <p><b>扫描根</b>：平台包约定为 {@code com.cim.*}，故此处直接声明
 * {@code @EntityScan/@EnableJpaRepositories basePackages = "com.cim"}——一次性覆盖平台全部模块，
 * 宿主 ap 无需再配。若宿主的实体/仓储不在此包下，请自行补充扫描根（两者不冲突；
 * 但同类型注解重复声明会导致注册器重复，届时以宿主声明为准即可）。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CimSystemProperties.class)
@ConditionalOnProperty(prefix = "cim.system", name = "enabled", havingValue = "true", matchIfMissing = true)
@EntityScan(basePackages = "com.cim")
@EnableJpaRepositories(basePackages = "com.cim")
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
}
