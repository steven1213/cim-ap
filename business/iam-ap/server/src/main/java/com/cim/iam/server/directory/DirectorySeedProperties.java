package com.cim.iam.server.directory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 身份目录种子配置（{@code cim.iam.directory.seed.*}）。
 *
 * <p>启动时幂等写入组织树 + 演示用户（{@link DirectorySeedRunner}），
 * 避免每次启动（尤其 H2 内存库重启清数据后）组织架构 / 用户为空——
 * 业务侧（如 MDS 的部门下拉、排班选人）直接无数据可选。</p>
 *
 * <p>幂等口径：<b>已存在即跳过</b>——管理端在线改动不被重启冲掉；
 * 生产可用 {@code seed-enabled=false} 关闭（生产组织应由 AD 同步 / 管理端维护）。</p>
 */
@Component
@ConfigurationProperties(prefix = "cim.iam.directory.seed")
public class DirectorySeedProperties {

    /** 是否在启动时种子组织树与演示用户。默认 true。 */
    private boolean enabled = true;

    /** 演示用户统一初始口令（创建时生效，已存在账号不覆盖）。生产务必注入强口令或关闭种子。 */
    private String userPassword = "CimUser#2026";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }
}
