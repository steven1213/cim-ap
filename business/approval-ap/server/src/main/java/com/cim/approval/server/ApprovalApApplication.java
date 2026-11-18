package com.cim.approval.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 通用审批流平台（业务层 ap）。
 *
 * <p>独立微服务，基于 Flowable 7 承载 BPMN 2.0 审批编排；复用 cim-ap 平台底座
 * （IAM 鉴权链、JPA/缓存 starter）。业务系统经 {@code businessKey} 发起流程、注册回调地址，
 * 审批结果由回调可靠回写，双方零流程知识耦合（详见 docs/business/approval-ap/approval-design-v2.md）。</p>
 *
 * <p><b>组件扫描范围</b>：本 ap 自身包 {@code com.cim.approval.server} + <b>域模块</b>
 * {@code com.cim.system}（RBAC/菜单/权限目录，系统配置六页的数据底座）。域模块刻意用
 * 组件扫描而非 AutoConfiguration.imports——{@code scanBasePackages} 漏掉
 * {@code com.cim.system} 会导致 {@code SysPermissionRepository} 等仓储 bean 缺失 → 启动即
 * NoSuchBeanDefinitionException（与 MDS 同款）。</p>
 *
 * <p><b>实体/仓储扫描</b>：本注解只声明本 ap 自身包；{@code com.cim.system} 由
 * {@code CimSystemConfiguration} 自带 {@code @EntityScan}/{@code @EnableJpaRepositories}
 * 声明，二者<b>互不重叠</b>（Spring Data 仓储注册器不做重名去重，重叠即炸）。</p>
 *
 * <p>权限源：cim-auth-starter 默认 {@code ClaimLocalAuthorityLoader}（令牌 roles claim），
 * 跨系统准入由 IAM 统一管控（令牌 {@code apps} claim 含 {@code approval-ap} 方可进入）。</p>
 */
@SpringBootApplication(scanBasePackages = { "com.cim.approval.server", "com.cim.system" })
@EnableScheduling
@EntityScan("com.cim.approval.server")
@EnableJpaRepositories("com.cim.approval.server")
public class ApprovalApApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalApApplication.class, args);
    }
}
