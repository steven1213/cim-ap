package com.cim.system.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cim-system 配置键（前缀 {@code cim.system.*}）。
 *
 * <p>与 {@code cim.auth.*}（认证侧）、{@code cim.jpa.*}（持久化侧）并列，只承载
 * <b>本 ap 内部权限</b>相关的开关。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cim.system")
public class CimSystemProperties {

    /** 总开关；关闭后本模块不注册任何 Bean（切面/加载器一并退出）。 */
    private boolean enabled = true;

    /** RBAC 解析策略。 */
    private Rbac rbac = new Rbac();

    /** 菜单树构建策略。 */
    private Menu menu = new Menu();

    /** RBAC 解析策略。 */
    @Getter
    @Setter
    public static class Rbac {

        /**
         * 未在本 ap 生效授权时的回退策略（灰度迁移用）。
         *
         * <p>触发回退的两种情况：</p>
         * <ul>
         *   <li>{@code sys_user} 中<b>未建档</b>（该身份尚未被授权到本 ap）；</li>
         *   <li>已建档且启用，但<b>未授予任何权限</b>。</li>
         * </ul>
         *
         * <p>{@code true} → 回退到 IAM 令牌的 {@code authorities} claim（等价于 M3 的
         * {@code ClaimLocalAuthorityLoader} 行为，便于先把模块接进来再加授权）；
         * {@code false}（默认）→ 返回空权限集（拒绝）。<b>生产建议 false</b>：
         * 本 ap 的权限以本 ap 的授权表为准。</p>
         *
         * <p>注意：用户被<b>明确停用</b>（{@code status=DISABLED}）时<b>恒不回退</b>——
         * 停用是管理员的明确动作，不应被回退策略掩盖。</p>
         */
        private boolean fallbackToClaims = false;

        /**
         * 超管短路时下发/追加的权限码。
         *
         * <p>须与 {@code cim-auth-starter} 的 {@code CimPermissionEvaluator} 认知一致
         * （其对 {@code SUPER_ADMIN} / {@code ROLE_SUPER} 直接放通）。</p>
         */
        private String superAuthority = "SUPER_ADMIN";
    }

    /** 菜单树构建策略。 */
    @Getter
    @Setter
    public static class Menu {

        /** 菜单树是否包含 {@code BUTTON} 节点（按钮级权限码节点）。 */
        private boolean includeButtons = false;
    }
}
