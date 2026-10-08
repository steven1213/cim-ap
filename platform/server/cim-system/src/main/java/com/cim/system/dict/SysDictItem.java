package com.cim.system.dict;

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
 * 字典明细（design.md §2.10）。
 *
 * <p>{@code itemKey} 为存库值、{@code i18nCode} 为展示文案键（§7）、{@code itemValue}
 * 为可选的附加值（如排序权重、颜色）。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@SQLRestriction("deleted = false")
@Table(name = "sys_dict_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_dict_item_key_tenant",
                columnNames = {"dict_id", "item_key", "tenant_id", "deleted"}))
@History(HistoryStrategy.SNAPSHOT)
public class SysDictItem extends BaseDefData {

    /** 所属字典 ID。 */
    @Column(name = "dict_id", length = 64, nullable = false)
    private String dictId;

    /** 存库值（如 {@code RUN}）。 */
    @Column(name = "item_key", length = 64, nullable = false)
    private String itemKey;

    /** 展示文案 i18n 键（§7）。 */
    @Column(name = "i18n_code", length = 128)
    private String i18nCode;

    /** 附加值（可选）。 */
    @Column(name = "item_value", length = 256)
    private String itemValue;

    /** 排序号。 */
    @Column(name = "sort_no")
    private Integer sortNo = 0;

    /** 状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EnableStatus status = EnableStatus.ENABLED;
}
