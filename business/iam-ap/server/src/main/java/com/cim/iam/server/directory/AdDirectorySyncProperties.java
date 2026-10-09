package com.cim.iam.server.directory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AD / LDAP 目录同步配置（{@code cim.iam.directory.ad.*}，identity-directory.md §6）。
 *
 * <p><b>未配置即跳过</b>：默认 {@code enabled=false} 且 {@code url}/{@code base-dn} 为空时，
 * 同步器不执行、不阻塞启动——纯 IAM 自建目录为默认可用态（已与用户确认）。</p>
 */
@Component
@ConfigurationProperties(prefix = "cim.iam.directory.ad")
public class AdDirectorySyncProperties {

    /** 总开关；默认关闭（本地无 AD 可连时保持 IAM 自建可用）。 */
    private boolean enabled = false;

    /** LDAP URL（生产用 {@code ldaps://}）。 */
    private String url = "";

    /** 同步专用绑定账号 DN（只读权限即可）。 */
    private String bindDn = "";

    /** 绑定账号口令。生产由 KMS/环境变量注入，严禁入库、严禁提交明文。 */
    private String bindPassword = "";

    /** 检索基址（如 {@code DC=corp,DC=com}）。 */
    private String baseDn = "";

    /** 人员过滤（默认取启用或禁用的全部 user，禁用位映射为 INACTIVE）。 */
    private String userFilter = "(sAMAccountName=*)";

    /** 组织（OU）过滤。 */
    private String orgFilter = "(objectClass=organizationalUnit)";

    /** 分页大小（PagedResultsControl）。 */
    private int pageSize = 500;

    /** 定时同步间隔（毫秒，fixedDelay）。 */
    private long syncIntervalMs = 300_000L;

    /** 是否已具备可连接配置（enabled 且 url/baseDn 均有值）。 */
    public boolean isConfigured() {
        return enabled && url != null && !url.isBlank() && baseDn != null && !baseDn.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBindDn() {
        return bindDn;
    }

    public void setBindDn(String bindDn) {
        this.bindDn = bindDn;
    }

    public String getBindPassword() {
        return bindPassword;
    }

    public void setBindPassword(String bindPassword) {
        this.bindPassword = bindPassword;
    }

    public String getBaseDn() {
        return baseDn;
    }

    public void setBaseDn(String baseDn) {
        this.baseDn = baseDn;
    }

    public String getUserFilter() {
        return userFilter;
    }

    public void setUserFilter(String userFilter) {
        this.userFilter = userFilter;
    }

    public String getOrgFilter() {
        return orgFilter;
    }

    public void setOrgFilter(String orgFilter) {
        this.orgFilter = orgFilter;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getSyncIntervalMs() {
        return syncIntervalMs;
    }

    public void setSyncIntervalMs(long syncIntervalMs) {
        this.syncIntervalMs = syncIntervalMs;
    }
}
