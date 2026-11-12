package com.cim.approval.server.controller;

import com.cim.auth.principal.CimUserPrincipal;
import com.cim.spring.support.web.Result;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 当前登录用户端点（对齐 IAM / RMS 的 {@code /me} 形态）。
 *
 * <p>前端启动时先打这一枪，据返回的 {@link MeDto#authEnabled()} 决定走哪条身份通道：</p>
 * <ul>
 *   <li>{@code true}：走统一登录（IAM）。没有令牌时本端点已被 {@code ApprovalSecurityConfig}
 *       的 {@code /api/**} 链拦成 401，前端据此中继到 IAM 登录页；有令牌时身份取自 JWT。</li>
 *   <li>{@code false}：dev 态无 IAM 联调。前端保留「身份切换」并按 {@code userId/tenantId}
 *       查询参数标识操作人（与 {@code CurrentUserResolver} 的 dev 分支同源）。</li>
 * </ul>
 *
 * <p><b>为什么把开关回传给前端而不是构建期常量</b>：同一份前端产物要同时能跑「带 IAM」与
 * 「无 IAM 演示」两种后端配置；写成 {@code import.meta.env} 就得为两种部署各打一次包。
 * 开关放在后端配置里，前端只按事实分支。</p>
 */
@RestController
@RequestMapping("/api/v1/approval")
public class ApprovalMeController {

    @Value("${cim.auth.enabled:false}")
    private boolean authEnabled;

    /** GET /api/v1/approval/me —— 当前用户档案 + 认证开关。 */
    @GetMapping("/me")
    public Result<MeDto> me() {
        CimUserPrincipal p = principal();
        if (p == null) {
            // dev 态（auth 关闭）没有 principal：身份仍由查询参数表达，这里只回开关。
            return Result.ok(new MeDto(null, null, null, List.of(), Set.of(), authEnabled));
        }
        return Result.ok(new MeDto(
                p.userId(),
                p.username(),
                p.tenantId(),
                p.roles() == null ? List.of() : List.copyOf(p.roles()),
                p.authoritiesSet() == null ? Set.of() : Set.copyOf(p.authoritiesSet()),
                authEnabled));
    }

    private CimUserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CimUserPrincipal p)) {
            return null;
        }
        return p;
    }

    /**
     * 当前用户档案传输对象。
     *
     * @param authEnabled 后端是否启用了 JWT 认证（{@code cim.auth.enabled}）
     */
    public record MeDto(
            String userId,
            String username,
            String tenantId,
            List<String> roles,
            Set<String> permissions,
            boolean authEnabled) {
    }
}
