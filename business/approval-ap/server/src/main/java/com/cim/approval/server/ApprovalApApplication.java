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
 * <p><b>组件扫描范围</b>：本 ap 自身包 {@code com.cim.approval.server}。不引入
 * {@code cim-system} 域模块——审批平台自身维护「流程定义→权限码」映射，权限源由
 * cim-auth-starter 的默认 {@code ClaimLocalAuthorityLoader}（令牌 roles claim）提供，
 * 跨系统准入由 IAM 统一管控（令牌 {@code apps} claim 含 {@code approval-ap} 方可进入）。</p>
 *
 * <p><b>实体/仓储扫描</b>：仅本 ap 自身包，与平台 starter 不重叠。</p>
 */
@SpringBootApplication(scanBasePackages = "com.cim.approval.server")
@EnableScheduling
@EntityScan("com.cim.approval.server")
@EnableJpaRepositories("com.cim.approval.server")
public class ApprovalApApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalApApplication.class, args);
    }
}
