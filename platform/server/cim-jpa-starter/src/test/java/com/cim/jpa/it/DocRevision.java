package com.cim.jpa.it;

import com.cim.core.history.History;
import com.cim.core.history.HistoryStrategy;
import com.cim.core.model.BaseRevisionData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * 示例「业务版本」实体（继承 {@code BaseRevisionData}）：其 {@code revision} 是**业务版本**
 * （发布 / 归档驱动，非每次写 +1），与 {@code @Version} 行级乐观锁、与历史表彼此独立。
 * 仅用于 T2.7 三层版本语义演示。
 *
 * <p>以 {@code @Filter} 引用本包 {@code package-info} 声明的租户过滤器，演示多实体共用同一
 * 过滤器定义。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "doc_revision")
@History(HistoryStrategy.SNAPSHOT)
public class DocRevision extends BaseRevisionData {

    @Column(name = "title", length = 128)
    private String title;
}
