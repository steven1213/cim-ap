package com.cim.approval.server.controller;

import com.cim.approval.server.domain.ApprovalAudit;
import com.cim.approval.server.dto.AuditView;
import com.cim.approval.server.security.ApprovalPermissions;
import com.cim.approval.server.security.CurrentUserResolver;
import com.cim.approval.server.security.SecurityGuard;
import com.cim.approval.server.service.AuditService;
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 审计查询（G1 权限码 + G3 业务域隔离）。
 * <ul>
 *   <li>G1：审计查看要求 {@code approval:audit:view}；dev 放行态默认跳过、dev-enforce 可开启 deny 路径。</li>
 *   <li>G3：审计按 businessKey + appCode 过滤；开放 API 强制收敛为令牌绑定域，跨域访问 403。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/approval/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final SecurityGuard securityGuard;
    private final CurrentUserResolver currentUser;

    @GetMapping
    public Result<List<AuditView>> list(@RequestParam String businessKey,
                                        @RequestParam(required = false) String appCode,
                                        @RequestParam(required = false) String actor) {
        // G1：审计查看属管理端点，须持 approval:audit:view
        securityGuard.assertPermission(ApprovalPermissions.AUDIT_VIEW, currentUser.resolveNullable(actor));
        // G3：域集合只有一个来源。appCode 是可选参数，但它**不再意味着「不传就不限域」**——
        // 未指定时收敛为调用方的授权域集合（认证态 = 令牌域 ∪ 共享域），否则知道 businessKey
        // 就能读到跨域审计，列表过滤形同虚设。
        List<String> domains = securityGuard.resolveDomainFilter(appCode);
        List<ApprovalAudit> audits = domains.contains("*")
                ? auditService.listByBusinessKey(businessKey)
                : auditService.listByBusinessKeyAndAppCodes(businessKey, domains);
        List<AuditView> views = new ArrayList<>();
        for (ApprovalAudit a : audits) {
            views.add(new AuditView(a.getId(), a.getSeq(), a.getBusinessKey(), a.getProcessInstanceId(),
                    a.getTaskId(), a.getAction(), a.getActor(), a.getBeforeState(), a.getAfterState(),
                    a.getDetail(), a.getPrevHash(), a.getHash(), a.getCreatedAt()));
        }
        return Result.ok(views);
    }
}
