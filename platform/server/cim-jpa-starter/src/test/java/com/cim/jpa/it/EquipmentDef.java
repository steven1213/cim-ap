package com.cim.jpa.it;

import com.cim.core.history.History;
import com.cim.core.history.HistoryStrategy;
import com.cim.core.model.BaseDefData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * 示例主实体（定义表）：继承 {@code BaseDefData}，声明 {@code @History(SNAPSHOT)}，
 * 即自动获得「整行快照 → {@code equipment_def_hist}」的历史能力。
 *
 * <p>同时以 {@code @Filter} 引用本包 {@code package-info} 中声明的租户过滤器启用自动租户隔离；
 * 声明 {@code @SQLRestriction("deleted = false")} 让逻辑删除行对业务查询不可见；唯一约束含
 * {@code deleted} 列，以保证「一删一活可并存」（见 README §9.7 / §10）。</p>
 *
 * <p>注：Hibernate 不解析元注解（meta-annotation）形式的 {@code @Filter}/{@code @SQLRestriction}，
 * 且 {@code @FilterDef} 全局须唯一，故：{@code @FilterDef} 放在 {@code package-info}（每应用一次），
 * 各实体仅标注 {@code @Filter}（框架约定，见 design.md §2.3）。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@SQLRestriction("deleted = false")
@Table(name = "equipment_def",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_equipment_def_code_tenant",
                columnNames = {"code", "tenant_id", "deleted"}))
@History(HistoryStrategy.SNAPSHOT)
public class EquipmentDef extends BaseDefData {

    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "capacity")
    private Integer capacity;
}
