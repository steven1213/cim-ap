package com.cim.system.rbac;

import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.auth.token.TokenClaims;
import com.cim.system.autoconfigure.CimSystemProperties;
import com.cim.system.support.EnableStatus;
import com.cim.system.user.SysUser;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 基于本 ap RBAC 表的权限加载器——{@code LocalAuthorityLoader} SPI 的<b>实现端</b>
 * （接口定义在 {@code cim-auth-starter}，见 design.md §2.4 / §2.10）。
 *
 * <p><b>在认证链路中的位置</b>（{@code JwtAuthenticationFilter}）：
 * 取 Bearer → JWKS 本地验签 → {@code apps} 准入判定 → <b>本类加载本 ap 业务权限</b>
 * → 注入 {@code SecurityContext}。即：IAM 决定「能不能进」，本类决定「进来能干什么」。</p>
 *
 * <p><b>身份对齐</b>见 {@link LocalUserResolver}（{@code externalId} 优先，{@code username} 兜底）。
 * 未建档或未授权时按 {@code cim.system.rbac.fallback-to-claims} 决定回退令牌 claim 还是拒绝；
 * 用户被<b>明确停用</b>时恒拒绝，不受回退策略影响。</p>
 *
 * <p>本类刻意不吞异常：RBAC 表不可用属基础设施故障，应显式暴露而非静默降级为「无权限」
 * （那会让运维把 DB 故障误判为授权配置问题）。</p>
 *
 * <p><b>每请求解析、不缓存 → 变更即时生效</b>：本类不缓存权限结果，授权/停用变更在<b>下一次请求</b>
 * 即生效（同一枚令牌无需重新签发），这是 T6.5「变更即时生效」的即时性来源。相应地每请求有若干
 * 次库查询——若将来改为按令牌版本缓存以省查询，务必同时接入 {@code TokenVersionChecker}
 * 用版本号作缓存失效信号，二者需成对使用。</p>
 */
@Slf4j
public class DbLocalAuthorityLoader implements LocalAuthorityLoader {

    private final LocalUserResolver userResolver;
    private final SysRbacService rbacService;
    private final CimSystemProperties properties;

    public DbLocalAuthorityLoader(LocalUserResolver userResolver,
                                 SysRbacService rbacService,
                                 CimSystemProperties properties) {
        this.userResolver = userResolver;
        this.rbacService = rbacService;
        this.properties = properties;
    }

    @Override
    public Set<String> loadAuthorities(TokenClaims claims) {
        if (claims == null) {
            return Set.of();
        }
        Optional<SysUser> localUser = userResolver.resolve(claims);
        if (localUser.isEmpty()) {
            String identity = (claims.userId() != null && !claims.userId().isBlank())
                    ? claims.userId() : String.valueOf(claims.username());
            return fallback(claims, "identity not provisioned in this ap: " + identity);
        }
        SysUser user = localUser.get();
        if (user.getStatus() == EnableStatus.DISABLED) {
            log.info("[cim-system] user {} is disabled, authorities denied", user.getUsername());
            return Set.of();
        }
        ResolvedAuthorities resolved = rbacService.resolveAuthorities(user.getId());
        if (resolved.empty()) {
            return fallback(claims, "no role/permission granted for user " + user.getUsername());
        }
        Set<String> authorities = new LinkedHashSet<>(resolved.codes());
        if (resolved.superRole()) {
            authorities.add(properties.getRbac().getSuperAuthority());
            log.debug("[cim-system] user {} granted super by role flag", user.getUsername());
        }
        return authorities;
    }

    /** 未建档 / 未授权时的回退：按开关决定回退令牌 claim 还是拒绝。 */
    private Set<String> fallback(TokenClaims claims, String reason) {
        if (properties.getRbac().isFallbackToClaims()) {
            Set<String> claimAuthorities = claims.authorities();
            log.debug("[cim-system] {} -> fallback to token claims ({} codes)", reason,
                    claimAuthorities == null ? 0 : claimAuthorities.size());
            return claimAuthorities == null ? Set.of() : claimAuthorities;
        }
        log.debug("[cim-system] {} -> denied (fallback disabled)", reason);
        return Set.of();
    }
}
