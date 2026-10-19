package com.cim.approval.server.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放 API（服务间调用）安全配置（G9 / D2）。
 *
 * <p>仅当 {@link #enabled} = true 且请求携带 {@code X-App-Key} 时启用校验：
 * 服务方须以 AppKey + HMAC-SHA256(timestamp|method|path|body) 签名，并在重放窗口内调用；
 * 按 appCode 限流、IP 白名单可配；越权/签名失败一律 403 并告警。用户态（JWT）与 dev 放行不受影响。</p>
 */
@Component
@ConfigurationProperties(prefix = "approval.api.security")
public class ApiSecurityProperties {

    /** 总开关；默认关闭（兼容纯 dev 与 JWT 用户态）。 */
    private boolean enabled = false;

    /** appCode → 共享密钥。服务方以 appCode 作为 {@code X-App-Key} 值。 */
    private Map<String, String> appKeys = new LinkedHashMap<>();

    /** 重放窗口（毫秒），默认 5 分钟。超出窗口的时间戳视为重放/时钟异常 → 403。 */
    private long replayWindowMs = 300_000L;

    /** IP 白名单；为空表示不限制来源 IP。 */
    private List<String> ipWhitelist = new ArrayList<>();

    /** appCode → 每分钟调用上限；缺省 60。 */
    private Map<String, Integer> rateLimit = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getAppKeys() {
        return appKeys;
    }

    public void setAppKeys(Map<String, String> appKeys) {
        this.appKeys = appKeys == null ? new LinkedHashMap<>() : appKeys;
    }

    public long getReplayWindowMs() {
        return replayWindowMs;
    }

    public void setReplayWindowMs(long replayWindowMs) {
        this.replayWindowMs = replayWindowMs;
    }

    public List<String> getIpWhitelist() {
        return ipWhitelist;
    }

    public void setIpWhitelist(List<String> ipWhitelist) {
        this.ipWhitelist = ipWhitelist == null ? new ArrayList<>() : ipWhitelist;
    }

    public Map<String, Integer> getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Map<String, Integer> rateLimit) {
        this.rateLimit = rateLimit == null ? new LinkedHashMap<>() : rateLimit;
    }
}
