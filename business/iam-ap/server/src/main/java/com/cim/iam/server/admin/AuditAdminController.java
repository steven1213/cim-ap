package com.cim.iam.server.admin;

import com.cim.iam.server.audit.AuditEventDto;
import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.support.IamPermissionCodes;
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审计日志查询（管理面）。
 *
 * <p>方法级 {@code iam:audit:list}，类级 {@code iam:console:admin} 兜底。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + IamPermissionCodes.CONSOLE_ADMIN + "')")
public class AuditAdminController {

    private final AuditService auditService;

    /** 最近审计事件；可按类型过滤。 */
    @GetMapping
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.AUDIT_LIST + "')")
    public Result<List<AuditEventDto>> list(@RequestParam(required = false) Integer limit,
                                            @RequestParam(required = false) String type) {
        int n = limit == null ? 100 : limit;
        return Result.ok(auditService.recent(n, type).stream().map(AuditEventDto::of).toList());
    }
}
