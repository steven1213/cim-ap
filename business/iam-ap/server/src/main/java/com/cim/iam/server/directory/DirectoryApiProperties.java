package com.cim.iam.server.directory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 目录只读 API 的服务身份配置（{@code cim.iam.directory.api.*}，identity-directory.md §5.3 方案 A）。
 *
 * <p>业务 ap 拉取档案/组织时不能用真人令牌（会串身份、真人离职即断），改由双方配置同一密钥、
 * 以请求头 {@code X-Directory-Key} 认证。<b>方案 A 起步</b>，后续可平滑替换为方案 B
 * （OAuth2 client_credentials 服务令牌 / NHI）——接口契约不变，只换认证头。</p>
 *
 * <p>未配置 {@code key} 时目录 API 视为<b>未启用</b>（安全默认：拒绝访问而非放行）。</p>
 */
@Component
@ConfigurationProperties(prefix = "cim.iam.directory.api")
public class DirectoryApiProperties {

    /** 共享密钥；为空表示未启用目录 API。生产由 KMS/环境变量注入并可轮换。 */
    private String key = "";

    /** 是否启用（由 key 是否配置决定，不可单独开关，避免误放行）。 */
    public boolean isEnabled() {
        return key != null && !key.isBlank();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
