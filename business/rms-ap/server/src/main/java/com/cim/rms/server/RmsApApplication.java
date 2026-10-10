package com.cim.rms.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RMS 配方管理系统（业务层 ap）。
 *
 * <p>基于 platform 后端底座实现；业务内部的 RBAC 权限由本 ap 自行控制，
 * 跨系统的「准入」由 IAM 统一管控（详见 docs/business/iam-ap）。</p>
 */
@SpringBootApplication
public class RmsApApplication {

    public static void main(String[] args) {
        SpringApplication.run(RmsApApplication.class, args);
    }
}
