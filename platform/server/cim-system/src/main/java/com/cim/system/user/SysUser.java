package com.cim.system.user;

import com.cim.core.history.History;
import com.cim.core.history.HistoryStrategy;
import com.cim.core.model.BaseDefData;
import com.cim.system.support.EnableStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * 用户——本 ap 的<b>授权档案</b>（design.md §2.10）。
 *
 * <p><b>刻意不含 {@code password}</b>：身份认证（口令校验、令牌签发）由
 * {@code business/iam-ap} + 企业 AD/LDAP 负责；本表只回答「这个已认证身份在本 ap 里
 * 有哪些角色」，即 README §21.1 的「把 AD 用户授权到本 ap」。</p>
 *
 * <p>身份对齐键：优先 {@code external_id}（IAM 令牌的 {@code sub}），回退 {@code username}
 * （AD 账号）。见 {@code com.cim.system.rbac.DbLocalAuthorityLoader}。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@SQLRestriction("deleted = false")
@Table(name = "sys_user",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_user_username_tenant",
                columnNames = {"username", "tenant_id", "deleted"}))
@History(HistoryStrategy.SNAPSHOT)
public class SysUser extends BaseDefData {

    /** 登录名（AD 账号），与 IAM 令牌 {@code username} 对应。 */
    @Column(name = "username", length = 64, nullable = false)
    private String username;

    /** 外部身份标识（IAM 令牌 {@code sub} / AD 对象标识），对齐优先级高于 username。 */
    @Column(name = "external_id", length = 128)
    private String externalId;

    /** 显示名。 */
    @Column(name = "display_name", length = 128)
    private String displayName;

    /** 邮箱。 */
    @Column(name = "email", length = 128)
    private String email;

    /** 手机号。 */
    @Column(name = "phone", length = 32)
    private String phone;

    /** 语言偏好（驱动 §7 数据库 i18n 的菜单/按钮文案）。 */
    @Column(name = "lang", length = 16)
    private String lang;

    /** 状态；停用后不参与权限计算。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EnableStatus status = EnableStatus.ENABLED;
}
