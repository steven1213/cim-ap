package com.cim.jpa.support;

import com.cim.core.history.History;
import com.cim.core.model.BaseDefData;
import com.cim.core.shared.ChangeSet;
import com.cim.core.shared.PageResult;
import com.cim.jpa.history.ChangeDetector;
import com.cim.jpa.history.HistoryRecorder;
import com.cim.jpa.history.OpType;
import com.cim.jpa.tenant.TenantFilterApplier;
import com.cim.spring.support.service.CrudService;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.PageQuery;
import com.cim.spring.support.web.PageResults;
import com.cim.spring.support.util.JsonUtils;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通用 CRUD 服务基类（见 README §21.3）：为 {@link BaseDefData} 子类提供分页、增删改查，
 * 并在写操作后按 {@code @History} 策略**自动落历史**（变更检测闸门：UPDATE 无变更则不落）。
 *
 * <p>业务服务只需继承本类并传入仓储：</p>
 * <pre>{@code
 * @Service
 * public class EquipmentDefService extends AbstractJpaService<EquipmentDef> {
 *     public EquipmentDefService(EquipmentDefRepository repo, EntityManager em, HistoryRecorder rec) {
 *         super(repo, em, rec);
 *     }
 * }
 * }</pre>
 *
 * <p>主键固定为 {@code String}（应用层时间有序 ID，见 README §3）。</p>
 *
 * @param <T> 实体类型（须继承 {@link BaseDefData}）
 */
public abstract class AbstractJpaService<T extends BaseDefData> implements CrudService<T, String> {

    protected final BaseRepository<T, String> repository;
    protected final EntityManager entityManager;
    protected final HistoryRecorder historyRecorder;
    protected final TenantFilterApplier tenantFilterApplier;

    protected AbstractJpaService(BaseRepository<T, String> repository,
                                 EntityManager entityManager,
                                 HistoryRecorder historyRecorder,
                                 TenantFilterApplier tenantFilterApplier) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.historyRecorder = historyRecorder;
        this.tenantFilterApplier = tenantFilterApplier;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<T> page(PageQuery query) {
        tenantFilterApplier.apply(entityManager);
        return PageResults.from(repository.findAll(query.toPageable()));
    }

    @Override
    @Transactional(readOnly = true)
    public T load(String id) {
        tenantFilterApplier.apply(entityManager);
        return repository.findById(id).orElseThrow(() -> BizException.notFound(id));
    }

    @Override
    @Transactional
    public T save(T entity) {
        if (entity.getDeleted() == null) {
            entity.setDeleted(Boolean.FALSE);
        }
        T saved = repository.save(entity);
        historyRecorder.record(saved, OpType.INSERT, null);
        return saved;
    }

    @Override
    @Transactional
    public T update(T entity) {
        tenantFilterApplier.apply(entityManager);
        T existing = repository.findById(entity.getId()).orElseThrow(() -> BizException.notFound(entity.getId()));
        ChangeSet changeSet = detectChanges(existing, entity);
        T saved = repository.save(entity);
        historyRecorder.record(saved, OpType.UPDATE, changeSet);
        return saved;
    }

    @Override
    @Transactional
    public void remove(String id) {
        tenantFilterApplier.apply(entityManager);
        T existing = repository.findById(id).orElseThrow(() -> BizException.notFound(id));
        existing.setDeleted(Boolean.TRUE);
        T saved = repository.save(existing);
        historyRecorder.record(saved, OpType.DELETE, null);
    }

    /** 变更检测：仅当实体声明了 {@link History} 时计算字段差异。 */
    protected ChangeSet detectChanges(T existing, T incoming) {
        Class<?> mappedClass = Hibernate.getClass(incoming);
        History history = mappedClass.getAnnotation(History.class);
        if (history == null) {
            return null;
        }
        return ChangeDetector.detect(history, existing, incoming);
    }

    /** 便于子类输出调试/日志。 */
    protected String toJson(Object value) {
        return JsonUtils.toJson(value);
    }
}
