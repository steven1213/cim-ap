package com.cim.rms.server.security;

import com.cim.auth.principal.CimUserPrincipal;
import com.cim.spring.support.web.Result;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 当前登录用户与权限端点（对齐 IAM {@code /api/v1/me} 形态，前缀收敛到 {@code /api/v1/rms}）。
 *
 * <p>前端 {@code authStore} 启动后拉取：{@code /me} 拿用户档案（写入 UI），
 * {@code /permissions} 拿本 ap 权限码集（用于 {@code <Perms>} 门禁）。
 * 权限码严格来自 {@link CimUserPrincipal#authoritiesSet()}（即 {@code RmsAuthorityLoader} 解析后的结果），
 * 与后端 {@code @PreAuthorize} 同源，避免「有按钮却 403」。</p>
 */
@RestController
@RequestMapping("/api/v1/rms")
public class MeController {

    private CimUserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CimUserPrincipal p)) {
            return null;
        }
        return p;
    }

    /** GET /api/v1/rms/me —— 当前用户档案。 */
    @GetMapping("/me")
    public Result<MeDto> me() {
        CimUserPrincipal p = principal();
        if (p == null) return Result.ok(null);
        MeDto dto = new MeDto(
                p.userId(),
                p.username(),
                p.tenantId(),
                p.roles() == null ? List.of() : List.copyOf(p.roles()),
                p.authoritiesSet() == null ? List.of() : List.copyOf(p.authoritiesSet()));
        return Result.ok(dto);
    }

    /** GET /api/v1/rms/permissions —— 本 ap 权限码集（前端门禁用）。 */
    @GetMapping("/permissions")
    public Result<Set<String>> permissions() {
        CimUserPrincipal p = principal();
        Set<String> auths = p == null || p.authoritiesSet() == null ? Set.of() : p.authoritiesSet();
        return Result.ok(auths);
    }

    /** 当前用户档案传输对象。 */
    public record MeDto(
            String userId,
            String username,
            String tenantId,
            List<String> roles,
            List<String> permissions) {
    }
}
