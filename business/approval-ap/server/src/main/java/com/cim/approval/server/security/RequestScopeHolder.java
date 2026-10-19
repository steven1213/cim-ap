package com.cim.approval.server.security;

/**
 * 请求级作用域持有器（ThreadLocal）。由 {@link ApiAuthFilter} 在开放 API 鉴权成功后写入，
 * 请求结束清理；{@link SecurityGuard} 读取以判定跨业务域访问。
 */
public final class RequestScopeHolder {

    private static final ThreadLocal<RequestScope> SCOPE = new ThreadLocal<>();

    private RequestScopeHolder() {
    }

    public static void set(RequestScope scope) {
        SCOPE.set(scope);
    }

    public static RequestScope get() {
        return SCOPE.get();
    }

    public static void clear() {
        SCOPE.remove();
    }
}
