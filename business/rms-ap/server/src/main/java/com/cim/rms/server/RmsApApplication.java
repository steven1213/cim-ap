package com.cim.rms.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * RMS 配方管理系统（业务层 ap）。
 *
 * <p>基于 platform 后端底座实现（Req.md §8 工程红线）；业务内部的 RBAC 权限由本 ap 自行控制
 * （权限码 {@code rms:*:*}），跨系统的「准入」由 IAM 统一管控（令牌 {@code apps} claim 含
 * {@code rms-ap} 方可进入，详见 docs/business/iam-ap 与 Req.md §2/§5.7）。</p>
 *
 * <p><b>组件/实体/仓储扫描</b>：W1 全部业务代码在宿主自身包 {@code com.cim.rms.server}
 * 下（暂无独立域模块），故三处扫描均只列本包；后续若拆域模块，须按 ADR-11 保证各单元
 * 扫描根互不重叠，并由域模块自带 {@code @EntityScan}/{@code @EnableJpaRepositories}。</p>
 */
@SpringBootApplication
@EntityScan("com.cim.rms.server")
@EnableJpaRepositories("com.cim.rms.server")
public class RmsApApplication {

    public static void main(String[] args) {
        SpringApplication.run(RmsApApplication.class, args);
    }
}
