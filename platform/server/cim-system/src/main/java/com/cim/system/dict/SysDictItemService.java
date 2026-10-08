package com.cim.system.dict;

import com.cim.core.shared.PageResult;
import com.cim.jpa.history.HistoryRecorder;
import com.cim.jpa.support.AbstractJpaService;
import com.cim.jpa.tenant.TenantFilterApplier;
import com.cim.spring.support.web.PageQuery;
import com.cim.system.support.PermissionCodes;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典明细服务。
 *
 * <p>独立成服务（而非挂在 {@code SysDictService} 里直接落库）是为了复用
 * {@code AbstractJpaService} 的<b>自动历史</b>能力——{@code SysDictItem} 声明了
 * {@code @History(SNAPSHOT)}，绕过服务基类就会静默丢掉明细的历史轨迹。</p>
 */
@Service
public class SysDictItemService extends AbstractJpaService<SysDictItem> {

    public SysDictItemService(SysDictItemRepository itemRepository,
                              EntityManager entityManager,
                              HistoryRecorder historyRecorder,
                              TenantFilterApplier tenantFilterApplier) {
        super(itemRepository, entityManager, historyRecorder, tenantFilterApplier);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_LIST + "')")
    public PageResult<SysDictItem> page(PageQuery query) {
        return super.page(query);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_LIST + "')")
    public SysDictItem load(String id) {
        return super.load(id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_SAVE + "')")
    public SysDictItem save(SysDictItem entity) {
        return super.save(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_SAVE + "')")
    public SysDictItem update(SysDictItem entity) {
        return super.update(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_REMOVE + "')")
    public void remove(String id) {
        super.remove(id);
    }
}
