package com.cim.rms.server.console;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RMS 控制台种子配置（{@code cim.rms.console.*}）。
 *
 * <p>权限码 / 角色 / 角色授权由 {@link RmsConsoleSeedService} 在启动时<b>幂等</b>写入本 ap 的库
 * （{@code sys_permission} / {@code sys_role*}）。幂等口径：<b>已存在即跳过</b>——管理端在线改动
 * 不被重启冲掉。</p>
 */
@Component
@ConfigurationProperties(prefix = "cim.rms.console")
public class RmsConsoleProperties {

    /** 是否种子权限码 / 角色 / 授权。默认 true。 */
    private boolean seedEnabled = true;

    /** 是否同时种子首管理员的本地授权档案（{@code sys_user} + {@code RMS_ADMIN}）。默认 true。 */
    private boolean seedAdminProfile = true;

    /** 控制台管理员的显示名（种子 {@code sys_user.display_name}）。 */
    private String adminDisplayName = "RMS 管理员";

    public boolean isSeedEnabled() {
        return seedEnabled;
    }

    public void setSeedEnabled(boolean seedEnabled) {
        this.seedEnabled = seedEnabled;
    }

    public boolean isSeedAdminProfile() {
        return seedAdminProfile;
    }

    public void setSeedAdminProfile(boolean seedAdminProfile) {
        this.seedAdminProfile = seedAdminProfile;
    }

    public String getAdminDisplayName() {
        return adminDisplayName;
    }

    public void setAdminDisplayName(String adminDisplayName) {
        this.adminDisplayName = adminDisplayName;
    }

    /** 首管理员用户名（与 IAM 引导管理员一致：externalId=username="admin"）。 */
    public String adminUsername() {
        return "admin";
    }
}
