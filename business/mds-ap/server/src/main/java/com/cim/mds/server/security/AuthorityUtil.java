package com.cim.mds.server.security;

import com.cim.auth.principal.CimUserPrincipal;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 权限断言工具：用于<b>接口级</b>人工鉴权（与 {@code @PreAuthorize} 并存的逃生口）。
 *
 * <p>常规鉴权走控制器上的 {@code @PreAuthorize}（{@code MethodSecurityConfig} 已开
 * {@code prePostEnabled}）；本类用于「同一个接口内按条件收紧」的场景，例如某字段
 * 只在持特定权限码时才返回。</p>
 *
 * <p>无认证上下文（测试关认证）时放行，与既有控制器口径一致；有认证上下文时按精确权限码匹配
 * （超管在平台侧已短路展开为全量权限码，故此处无需特判）。</p>
 */
public final class AuthorityUtil {

    private AuthorityUtil() {}

    /** 当前用户是否持有指定权限码。 */
    public static boolean has(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CimUserPrincipal)) {
            return true;
        }
        for (GrantedAuthority g : auth.getAuthorities()) {
            if (authority.equals(g.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** 断言持有权限码；不满足抛 {@code ACCESS_DENIED}(403)。无认证上下文（测试）放行。 */
    public static void require(String authority) {
        if (!has(authority)) {
            throw new BizException(BizCode.ACCESS_DENIED, "无操作权限: " + authority);
        }
    }
}
