package com.cim.spring.support.tenant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多租户配置（{@code cim.tenant.*}，见 design.md §5 配置键）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cim.tenant")
public class TenantProperties {

    /** 是否启用多租户隔离。 */
    private boolean enabled = false;

    /** 租户请求头名。 */
    private String headerName = "X-Tenant-Id";

    /** 缺省租户（请求头缺失时使用；为空则不启用过滤）。 */
    private String defaultTenant;

    /** 是否允许超管跨租户访问。 */
    private boolean allowSuperCrossTenant = false;
}
