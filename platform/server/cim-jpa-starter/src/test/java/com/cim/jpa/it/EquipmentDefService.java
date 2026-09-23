package com.cim.jpa.it;

import com.cim.jpa.history.HistoryRecorder;
import com.cim.jpa.support.AbstractJpaService;
import com.cim.jpa.tenant.TenantFilterApplier;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

/**
 * 示例服务：继承 {@link AbstractJpaService} 即获得 CRUD + 自动历史 + 租户隔离，零业务样板。
 */
@Service
public class EquipmentDefService extends AbstractJpaService<EquipmentDef> {

    public EquipmentDefService(EquipmentDefRepository repository,
                               EntityManager entityManager,
                               HistoryRecorder historyRecorder,
                               TenantFilterApplier tenantFilterApplier) {
        super(repository, entityManager, historyRecorder, tenantFilterApplier);
    }
}
