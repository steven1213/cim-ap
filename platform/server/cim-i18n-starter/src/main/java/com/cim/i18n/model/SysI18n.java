package com.cim.i18n.model;

import com.cim.core.model.BaseDefData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * 译文主体（README §7 的「翻译表」）。
 *
 * <p><b>唯一键 = {@code (locale_code, code)}</b>：同一语言下键唯一，跨语言同键多行。
 * 不另设键目录表——i18n 体量小，键级元数据随译文行冗余存储可忽略，换来零 join 与更易扩展语种。</p>
 *
 * <p>{@code content} 支持 {@code {name}} 占位符，由 {@code MessageFormat} 按语言语序渲染。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@SQLRestriction("deleted = false")
@Table(name = "sys_i18n",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_i18n_locale_code_tenant",
                columnNames = {"locale_code", "code", "tenant_id", "deleted"}),
        indexes = @Index(name = "idx_sys_i18n_code", columnList = "code"))
public class SysI18n extends BaseDefData {

    /** 语言标签（→ {@code sys_locale.code}）。 */
    @Column(name = "locale_code", length = 32, nullable = false)
    private String localeCode;

    /** i18n 键（如 {@code iam.menu.orgs}）。 */
    @Column(name = "code", length = 200, nullable = false)
    private String code;

    /** 级别：系统级 / 用户级。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 16, nullable = false)
    private I18nScope scope = I18nScope.USER;

    /** 归属模块（{@code system} / {@code iam}…），便于筛选与增量拉取。 */
    @Column(name = "module", length = 64)
    private String module;

    /** 译文（支持 {@code {name}} 占位符）。 */
    @Column(name = "content", length = 2000)
    private String content;
}
