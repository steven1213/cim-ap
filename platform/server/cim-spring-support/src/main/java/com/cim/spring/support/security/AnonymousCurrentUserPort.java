package com.cim.spring.support.security;

import com.cim.core.port.CurrentUserPort;

import java.util.Set;

/**
 * 匿名操作人端口：未接入 {@code cim-auth-starter} 时的兜底实现（返回空身份）。
 *
 * <p>由 {@code SpringSupportAutoConfiguration} 以 {@code @ConditionalOnMissingBean} 注册；
 * 引入认证 starter 后会被其基于 SecurityContext 的实现覆盖（见 README §8 可插拔）。</p>
 */
public class AnonymousCurrentUserPort implements CurrentUserPort {

    @Override
    public String userId() {
        return null;
    }

    @Override
    public String username() {
        return null;
    }

    @Override
    public Set<String> authorities() {
        return Set.of();
    }

    @Override
    public boolean isSuper() {
        return false;
    }
}
