package com.cim.iam.server.admin;

import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.audit.AuditType;
import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.iam.server.auth.RefreshToken;
import com.cim.iam.server.auth.RefreshTokenService;
import com.cim.iam.server.token.TokenVersionService;
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 在线会话管理（管理面，需 {@code iam-ap:ADMIN}）。
 *
 * <p>「在线会话」以**未撤销且未过期的刷新令牌**近似表示（一个刷新令牌 ≈ 一个登录会话）。
 * 强制下线 = bump 该用户令牌版本，验证端进程内即时判定，旧访问令牌立即 401。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/sessions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('iam-ap:ADMIN')")
public class SessionAdminController {

    private final RefreshTokenService refreshTokenService;
    private final LocalCredentialService credentialService;
    private final TokenVersionService tokenVersionService;
    private final AuditService auditService;

    public record SessionRow(String userId, String username, String accessTokenJti, Instant expiresAt) {
    }

    public record KickResult(String uid, long version) {
    }

    /** 活跃会话清单（按到期时间升序）。 */
    @GetMapping
    public Result<List<SessionRow>> list() {
        List<SessionRow> rows = refreshTokenService.listActiveSessions().stream()
                .map(this::toRow)
                .toList();
        return Result.ok(rows);
    }

    /** 强制下线：bump 该用户令牌版本，使其所有存量令牌失效。 */
    @DeleteMapping("/{userId}")
    public Result<KickResult> kick(@PathVariable String userId) {
        long version = tokenVersionService.bump(userId);
        auditService.success(AuditType.SESSION_REVOKED, null, userId,
                "强制下线（bump 令牌版本 → " + version + "）");
        return Result.ok(new KickResult(userId, version));
    }

    private SessionRow toRow(RefreshToken rt) {
        String uid = rt.getUserId();
        String uname = credentialService.findByUserId(uid)
                .map(c -> c.getUsername())
                .orElse(uid);
        return new SessionRow(uid, uname, rt.getAccessTokenJti(), rt.getExpiresAt());
    }
}
