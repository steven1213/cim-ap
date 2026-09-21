package com.cim.core.port;

import java.util.Set;

/**
 * 当前操作人端口（实现由 {@code cim-auth-starter} 提供，读取 SecurityContext）。
 *
 * <p>供审计填充（{@code create_user}/{@code event_user}）与 {@code @DataPermission}
 * 获取操作人及其权限集，业务无需传参（见 README §5 / §9）。</p>
 */
public interface CurrentUserPort {

    /** 当前用户 ID（未登录返回 {@code null}）。 */
    String userId();

    /** 当前用户名（未登录返回 {@code null}）。 */
    String username();

    /** 当前用户权限集（权限码 {@code module:res:action}）。 */
    Set<String> authorities();

    /** 是否超级管理员（超管短路放通）。 */
    boolean isSuper();
}
