package com.cim.mds.server.shift;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.cim.auth.principal.CimUserPrincipal;

/**
 * 当前登录人摘要（排班域用：交接记录的交班人/签收人、排班操作审计）。
 *
 * <p>dev 演示通道（auth 关闭）principal 为空——回 {@code dev} 占位，保证交接
 * 记录在开发态仍可成链（交班/签收同名占位），语义上等价于「本机操作」。</p>
 */
@Component
public class CurrentUser {

    /** 用户 id；dev 态回 {@code dev}。 */
    public String id() {
        CimUserPrincipal p = principal();
        return p == null ? "dev" : p.userId();
    }

    /** 用户名；dev 态回 {@code dev}。 */
    public String name() {
        CimUserPrincipal p = principal();
        return p == null ? "dev" : p.username();
    }

    private CimUserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CimUserPrincipal p ? p : null;
    }
}
