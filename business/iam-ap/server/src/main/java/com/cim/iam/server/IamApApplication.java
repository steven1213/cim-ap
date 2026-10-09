package com.cim.iam.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * IAM 统一登录与跨业务准入服务（业务层 ap）。
 *
 * <p>本服务基于 platform 后端底座（cim-spring-support 等 starter）实现；
 * 负责企业内统一身份认证（对接 LDAP/AD）与跨业务系统的「准入」权限控制，
 * 包括 ap 注册/接入码下发（§8 边界表）与令牌版本存储/递增/下发（§8.1(g)）。
 * 业务系统内部的菜单/按钮/数据权限由各业务 ap 自行控制（详见 docs/business/iam-ap）。</p>
 *
 * <p><b>组件扫描范围（{@code scanBasePackages}）</b>：只列<b>需要被扫描的单元</b>——
 * 本 ap 自己的包 {@code com.cim.iam.server}，以及<b>域模块</b> {@code com.cim.system}
 * （域模块刻意用组件扫描而非 {@code AutoConfiguration.imports} 生效，见
 * {@code CimSystemConfiguration} 说明；漏扫会导致 {@code SysPermissionRepository} 等
 * 仓储 bean 缺失 → 启动即 {@code NoSuchBeanDefinitionException}）。
 * <b>starter 无需列入</b>：{@code cim-i18n-starter} 走自动装配
 * （{@code I18nAutoConfiguration} 已以 {@code @Bean} 注册 {@code I18nController} 等），
 * 不扫也能用；刻意不扫可避免与其 {@code @RestController}/{@code @Configuration}
 * 组件重复注册。</p>
 *
 * <p><b>实体/仓储扫描</b>：{@code scanBasePackages} 只管组件扫描，<b>不</b>覆盖实体/仓储扫描
 * （实体包由启动类所在包决定），故本类显式声明本 ap 自己的包 {@code com.cim.iam.server}；
 * 域模块与 starter 各自声明自己的包（{@code cim-system} → {@code com.cim.system}、
 * {@code cim-i18n-starter} → {@code com.cim.i18n}），三者<b>互不重叠</b>——
 * 重叠会导致同一仓储 bean 被注册两次而抛 {@code BeanDefinitionOverrideException}
 * （Spring Data 的仓储注册器不做重名去重）。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.cim.iam.server", "com.cim.system"})
@EntityScan("com.cim.iam.server")
@EnableJpaRepositories("com.cim.iam.server")
public class IamApApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamApApplication.class, args);
    }
}

