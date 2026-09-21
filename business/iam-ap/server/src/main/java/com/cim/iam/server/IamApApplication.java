package com.cim.iam.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IAM 统一登录与跨业务准入服务（业务层 ap）。
 *
 * <p>本服务基于 platform 后端底座（cim-spring-support 等 starter）实现；
 * 负责企业内统一身份认证（对接 LDAP/AD）与跨业务系统的「准入」权限控制。
 * 业务系统内部的菜单/按钮/数据权限由各业务 ap 自行控制（详见 docs/business/iam-ap）。</p>
 */
@SpringBootApplication
public class IamApApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamApApplication.class, args);
    }
}
