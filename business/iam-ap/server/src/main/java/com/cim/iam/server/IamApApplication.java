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
 * <p>实体/仓储扫描必须显式声明本 ap 包（{@code com.cim.iam.server}）：
 * 与 platform 域模块同理，{@code @SpringBootApplication(scanBasePackages=...)} 只管组件扫描，
 * 不覆盖实体/仓储扫描（实体包由启动类所在包决定）。</p>
 */
@SpringBootApplication
@EntityScan("com.cim.iam.server")
@EnableJpaRepositories("com.cim.iam.server")
public class IamApApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamApApplication.class, args);
    }
}

