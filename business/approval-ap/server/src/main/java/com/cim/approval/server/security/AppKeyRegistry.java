package com.cim.approval.server.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 开放 API 密钥注册表（G9）。持有 appCode → 共享密钥映射与默认限流配额。
 * 由 {@link ApiSecurityProperties} 注入，单例共享。
 */
@Component
public class AppKeyRegistry {

    private final Map<String, String> appKeys;
    private final Map<String, Integer> rateLimit;
    private final int defaultLimitPerMinute = 60;

    public AppKeyRegistry(ApiSecurityProperties props) {
        this.appKeys = props.getAppKeys();
        this.rateLimit = props.getRateLimit();
    }

    /** 该 appCode 是否已注册（用作 {@code X-App-Key} 校验）。 */
    public boolean containsAppCode(String appCode) {
        return appCode != null && appKeys.containsKey(appCode);
    }

    /** 取 appCode 对应共享密钥；未注册返回 null。 */
    public String secretOf(String appCode) {
        return appCode == null ? null : appKeys.get(appCode);
    }

    /** appCode 的每分钟限流配额。 */
    public int limitFor(String appCode) {
        return rateLimit.getOrDefault(appCode, defaultLimitPerMinute);
    }

    /** 已注册的全部业务域（用于审计/监控）。 */
    public Set<String> registeredAppCodes() {
        return appKeys.keySet();
    }
}
