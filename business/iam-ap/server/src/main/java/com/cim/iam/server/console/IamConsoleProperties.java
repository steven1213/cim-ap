package com.cim.iam.server.console;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IAM 控制台种子配置（{@code cim.iam.console.*}）。
 *
 * <p>控制台的菜单 / 按钮权限 / 角色 / 译文由 {@link IamConsoleSeedService} 在启动时
 * <b>幂等</b>写入本 ap 的库（{@code sys_menu} / {@code sys_permission} / {@code sys_role*} /
 * {@code sys_i18n}）。幂等口径：<b>已存在即跳过</b>——管理端在线改动不被重启冲掉。</p>
 */
@Component
@ConfigurationProperties(prefix = "cim.iam.console")
public class IamConsoleProperties {

    /** 是否在启动时种子控制台菜单/权限/角色/译文。默认 true。 */
    private boolean seedEnabled = true;

    /** 是否同时种子英文（{@code en-US}）译文。默认 true。 */
    private boolean seedEnglish = true;

    /** 是否在启动时种子首管理员的本地授权档案（{@code sys_user} + {@code IAM_ADMIN}）。默认 true。 */
    private boolean seedAdminProfile = true;

    /** 控制台管理员的显示名（种子 {@code sys_user.display_name}）。 */
    private String adminDisplayName = "IAM 管理员";

    public boolean isSeedEnabled() {
        return seedEnabled;
    }

    public void setSeedEnabled(boolean seedEnabled) {
        this.seedEnabled = seedEnabled;
    }

    public boolean isSeedEnglish() {
        return seedEnglish;
    }

    public void setSeedEnglish(boolean seedEnglish) {
        this.seedEnglish = seedEnglish;
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
}
