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
 * 字典（design.md §2.10）：一类枚举值的容器，明细见 {@link SysDictItem}。
 *
 * <p>典型用途：设备状态、工单类型等前端下拉。明细的展示文案走 {@code i18n_code}（§7），
 * 使字典值与语言解耦。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@SQLRestriction("deleted = false")
@Table(name = "sys_dict",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_dict_code_tenant",
                columnNames = {"code", "tenant_id", "deleted"}))
@History(HistoryStrategy.SNAPSHOT)
public class SysDict extends BaseDefData {

    /** 字典编码（如 {@code equipment_status}）。 */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    /** 字典名称。 */
    @Column(name = "name", length = 128)
    private String name;

    /** 状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EnableStatus status = EnableStatus.ENABLED;
}
