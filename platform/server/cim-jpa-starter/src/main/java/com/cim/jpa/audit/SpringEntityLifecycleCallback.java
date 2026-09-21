package com.cim.jpa.audit;

import com.cim.core.event.EntityLifecycleCallback;
import com.cim.core.model.Auditable;
import com.cim.core.model.BaseHistoryData;
import com.cim.core.port.CurrentUserPort;
import com.cim.core.port.IdGenerator;
import com.cim.core.port.TenantPort;
import com.cim.jpa.support.BeanProperties;
import com.cim.spring.support.trace.TraceContext;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * 生命周期回调实现：在插入 / 更新前统一注入主键、填充审计字段与租户
 * （见 README §5 / §9 / §10）。
 *
 * <p>操作人取自 {@link CurrentUserPort}（认证 starter 提供，缺失时为匿名实现），
 * 租户取自 {@link TenantPort}，主键取自 {@link IdGenerator}。业务代码零侵入。</p>
 */
@RequiredArgsConstructor
public class SpringEntityLifecycleCallback implements EntityLifecycleCallback {

    private final IdGenerator idGenerator;
    private final CurrentUserPort currentUserPort;
    private final TenantPort tenantPort;

    @Override
    public void beforeInsert(Object entity) {
        LocalDateTime now = LocalDateTime.now();
        String user = safeUsername();
        String tenant = safeTenant();
        String traceId = TraceContext.currentTraceId();

        if (entity instanceof Auditable auditable) {
            if (auditable.getCreateTime() == null) {
                auditable.setCreateTime(now);
            }
            auditable.setEventTime(now);
            if (auditable.getCreateUser() == null) {
                auditable.setCreateUser(user);
            }
            auditable.setEventUser(user);
            if (auditable.getTrxId() == null) {
                auditable.setTrxId(traceId);
            }
            if (auditable.getTenantId() == null && tenant != null) {
                auditable.setTenantId(tenant);
            }
        }

        if (entity instanceof BaseHistoryData history) {
            if (history.getHistoryId() == null) {
                history.setHistoryId(idGenerator.nextId());
            }
            if (history.getOpTime() == null) {
                history.setOpTime(now);
            }
            if (history.getOperator() == null) {
                history.setOperator(user);
            }
            if (history.getTrxId() == null) {
                history.setTrxId(traceId);
            }
            if (history.getTenantId() == null && tenant != null) {
                history.setTenantId(tenant);
            }
            return;
        }

        // 其余基类族（BaseDefData / BaseStateData / BaseEventData / BaseRevisionData）注入主键
        BeanProperties.setIfNull(entity, "id", () -> idGenerator.nextId());
    }

    @Override
    public void beforeUpdate(Object entity) {
        if (entity instanceof Auditable auditable) {
            String user = safeUsername();
            String tenant = safeTenant();
            auditable.setEventTime(LocalDateTime.now());
            auditable.setEventUser(user);
            if (auditable.getTrxId() == null) {
                auditable.setTrxId(TraceContext.currentTraceId());
            }
            if (auditable.getTenantId() == null && tenant != null) {
                auditable.setTenantId(tenant);
            }
        }
    }

    private String safeUsername() {
        try {
            return currentUserPort.username();
        } catch (Exception e) {
            return null;
        }
    }

    private String safeTenant() {
        try {
            return tenantPort.tenantId();
        } catch (Exception e) {
            return null;
        }
    }
}
