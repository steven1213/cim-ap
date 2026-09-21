package com.cim.mes.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MES 制造执行系统服务（业务层 ap）。
 *
 * <p>基于 platform 后端底座实现；业务内部的 RBAC 权限由本 ap 自行控制，
 * 跨系统的「准入」由 IAM 统一管控（详见 docs/business/iam-ap）。</p>
 */
@SpringBootApplication
public class MesApApplication {

    public static void main(String[] args) {
        SpringApplication.run(MesApApplication.class, args);
    }
}
