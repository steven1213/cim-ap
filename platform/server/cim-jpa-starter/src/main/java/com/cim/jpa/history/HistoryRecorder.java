package com.cim.jpa.history;

import com.cim.core.history.Historizable;
import com.cim.core.history.History;
import com.cim.core.history.HistoryStrategy;
import com.cim.core.model.BaseHistoryData;
import com.cim.core.shared.ChangeSet;
import com.cim.jpa.config.CimJpaProperties;
import com.cim.jpa.support.BeanProperties;
import com.cim.spring.support.util.JsonUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 历史写入器：把主实体的快照 / 变更集写入其**专属**历史表（见 README §9）。
 *
 * <p>由服务基类 {@code AbstractJpaService} 在写操作后调用（对应 README §21.3 的「经 DataAp
 * 触发历史」）。历史实体由 {@link HistoryMapper} 按约定解析并拷贝业务字段；其
 * {@code historyId}/{@code opTime}/{@code operator}/{@code tenantId} 由已注册的生命周期回调
 * 在 persist 时自动填充。</p>
 *
 * <p>策略：{@code SNAPSHOT} → {@code {X}Hist}（整行快照，仅变更时）；{@code STATE_LOG} →
 * {@code {X}StateLog}；{@code NONE} → 跳过。{@code UPDATE} 且变更集为空时**不落历史**。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class HistoryRecorder {

    /**
     * 延迟解析 EntityManager：使本 Bean 在「无 JPA/无数据源」的上下文（如自动装配单测）
     * 亦可安全创建；真正写入历史时才要求 EntityManager 存在。
     */
    private final ObjectProvider<EntityManager> entityManagerProvider;
    private final CimJpaProperties properties;

    /**
     * 记录一次写操作历史。
     *
     * @param entity    主实体（写入后的实例）
     * @param opType    操作类型
     * @param changeSet 变更集（仅 UPDATE 需要；INSERT/DELETE 传 {@code null}）
     */
    public void record(Object entity, OpType opType, ChangeSet changeSet) {
        if (entity == null || !properties.getHistory().isEnabled()) {
            return;
        }
        if (entity instanceof Historizable) {
            return; // 历史实体自身不再记历史，避免递归
        }
        Class<?> mappedClass = Hibernate.getClass(entity);
        History history = mappedClass.getAnnotation(History.class);
        if (history == null || history.value() == HistoryStrategy.NONE) {
            return;
        }
        if (opType == OpType.UPDATE && (changeSet == null || changeSet.isEmpty())) {
            return; // 未变更不落历史
        }

        Class<?> historyClass = HistoryMapper.resolveHistoryClass(mappedClass, history.value());
        Object historyEntity = HistoryMapper.newHistoryInstance(historyClass);
        HistoryMapper.copyBusinessFields(entity, historyEntity);

        if (historyEntity instanceof BaseHistoryData row) {
            row.setBizId(String.valueOf(BeanProperties.get(entity, "id")));
            row.setOpType(opType.code());
            if (changeSet != null && !changeSet.isEmpty()) {
                row.setChangeSetJson(JsonUtils.toJson(changeSet.getChanges()));
            }
            if (properties.getHistory().getWriteMode() == CimJpaProperties.HistoryWriteMode.OUTBOX) {
                // OUTBOX 模式在 M4（cim-mq-starter 发件箱中继）落地前，先退化为同事务写入
                log.debug("[cim-jpa] history OUTBOX mode not yet available, fallback to in-transaction");
            }
        }
        EntityManager entityManager = entityManagerProvider.getIfAvailable();
        if (entityManager == null) {
            log.warn("[cim-jpa] EntityManager unavailable, history not recorded for {}",
                    mappedClass.getSimpleName());
            return;
        }
        entityManager.persist(historyEntity);
        log.debug("[cim-jpa] history recorded {} for {}", opType, mappedClass.getSimpleName());
    }
}
