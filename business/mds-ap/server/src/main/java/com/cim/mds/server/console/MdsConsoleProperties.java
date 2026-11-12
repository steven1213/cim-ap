package com.cim.mds.server.console;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MDS 控制台种子配置（{@code cim.mds.console.*}）。
 *
 * <p>权限码 / 角色 / 角色授权由 {@link MdsConsoleSeedService} 在启动时<b>幂等</b>写入本 ap 的库
 * （{@code sys_permission} / {@code sys_role*}）。幂等口径：<b>已存在即跳过</b>——管理端在线改动
 * 不被重启冲掉。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cim.mds.console")
public class MdsConsoleProperties {

    /** 是否种子权限码 / 菜单 / 角色 / 授权。默认 true。 */
    private boolean seedEnabled = true;

    /** 是否同时种子首管理员的本地授权档案（{@code sys_user} + {@code MDS_ADMIN}）。默认 true。 */
    private boolean seedAdminProfile = true;

    /** 是否同时种子英文译文（{@code sys_i18n}）。默认 true；纯中文环境可关。 */
    private boolean seedEnglish = true;

    /** 控制台管理员的显示名（种子 {@code sys_user.display_name}）。 */
    private String adminDisplayName = "MDS 管理员";

    /** 首管理员用户名（与 IAM 引导管理员一致：externalId=username="admin"）。 */
    public String adminUsername() {
        return "admin";
    }
}
