package com.cim.rms.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * RMS 配方管理系统（业务层 ap）。
 *
 * <p>基于 platform 后端底座实现（Req.md §8 工程红线）；业务内部的 RBAC 权限由本 ap 自行控制
 * （权限码 {@code rms:*:*}，复用域模块 {@code cim-system} 的 {@code sys_role_perm}），
 * 跨系统的「准入」由 IAM 统一管控（令牌 {@code apps} claim 含
 * {@code rms-ap} 方可进入，详见 docs/business/iam-ap 与 Req.md §2/§5.7）。</p>
 *
 * <p><b>组件扫描范围（{@code scanBasePackages}）</b>：本 ap 自身的包 {@code com.cim.rms.server}
 * 与<b>域模块</b> {@code com.cim.system}。域模块刻意用组件扫描而非
 * {@code AutoConfiguration.imports} 生效（见 {@code CimSystemConfiguration} 说明）；漏扫
 * {@code com.cim.system} 会导致 {@code SysPermissionRepository} 等仓储 bean 缺失 → 启动即
 * {@code NoSuchBeanDefinitionException}。starter（cim-i18n 等）走自动装配，不列入。</p>
 *
 * <p><b>实体/仓储扫描</b>：本 ap 自身包 {@code com.cim.rms.server} 在此显式声明；
 * 域模块 {@code com.cim.system} 由 {@code CimSystemConfiguration} 自带
 * {@code @EntityScan}/{@code @EnableJpaRepositories} 声明，二者<b>互不重叠</b>
 * （重叠会让同一仓储 bean 被注册两次 → {@code BeanDefinitionOverrideException}）。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.cim.rms.server", "com.cim.system"})
@EntityScan("com.cim.rms.server")
@EnableJpaRepositories("com.cim.rms.server")
public class RmsApApplication {

    public static void main(String[] args) {
        SpringApplication.run(RmsApApplication.class, args);
    }
}
