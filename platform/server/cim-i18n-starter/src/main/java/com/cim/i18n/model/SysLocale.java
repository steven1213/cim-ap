package com.cim.i18n.model;

import com.cim.core.model.BaseDefData;
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
 * 语言目录（README §7 的「多语言表」）。
 *
 * <p>{@code code} 采用 BCP-47 语言标签（{@code zh-CN} / {@code en-US} / {@code zh-TW}…），
 * 与 {@link SysI18n#getLocaleCode()} 对应。{@code isDefault=true} 的语言即全局兜底语言，
 * <b>约定为 {@code zh-CN}</b>（见 {@code DatabaseMessageSource}）。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@SQLRestriction("deleted = false")
@Table(name = "sys_locale",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_locale_code_tenant",
                columnNames = {"code", "tenant_id", "deleted"}))
public class SysLocale extends BaseDefData {

    /** 语言标签（如 {@code zh-CN}）。 */
    @Column(name = "code", length = 32, nullable = false)
    private String code;

    /** 语言显示名（如「简体中文」）。 */
    @Column(name = "name", length = 64)
    private String name;

    /** 是否默认兜底语言。 */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    /** 排序号。 */
    @Column(name = "sort_no")
    private Integer sortNo = 0;

    /** 状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private LocaleStatus status = LocaleStatus.ENABLED;
}
