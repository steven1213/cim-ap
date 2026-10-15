package com.cim.rms.server.security;

import com.cim.auth.principal.CimUserPrincipal;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 权限断言工具（FR-SEC2）：用于<b>接口级</b>人工鉴权（RMS 暂不使用方法级 {@code @PreAuthorize}）。
 *
 * <p>无认证上下文（如测试关认证）时放行，与既有控制器口径一致；有认证上下文时按精确权限码匹配
 * （超管在平台侧已短路展开为全量权限码，故此处无需特判）。</p>
 *
 * <p>注：生产环境下 {@code /api/**} 已由 {@code RmsSecurityConfig} 强制要求令牌，
 * 因此匿名请求到不了控制器；本类用于在<b>已认证</b>基础上再做「细粒度授权」。</p>
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
