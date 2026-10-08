package com.cim.system.dict;

import com.cim.core.shared.PageResult;
import com.cim.jpa.history.HistoryRecorder;
import com.cim.jpa.support.AbstractJpaService;
import com.cim.jpa.tenant.TenantFilterApplier;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.PageQuery;
import com.cim.system.support.PermissionCodes;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 字典服务（字典本体 CRUD + 按编码读取明细）。 */
@Service
public class SysDictService extends AbstractJpaService<SysDict> {

    private final SysDictRepository dictRepository;
    private final SysDictItemRepository itemRepository;

    public SysDictService(SysDictRepository dictRepository,
                          SysDictItemRepository itemRepository,
                          EntityManager entityManager,
                          HistoryRecorder historyRecorder,
                          TenantFilterApplier tenantFilterApplier) {
        super(dictRepository, entityManager, historyRecorder, tenantFilterApplier);
        this.dictRepository = dictRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_LIST + "')")
    public PageResult<SysDict> page(PageQuery query) {
        return super.page(query);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_LIST + "')")
    public SysDict load(String id) {
        return super.load(id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_SAVE + "')")
    public SysDict save(SysDict entity) {
        if (entity.getCode() == null || entity.getCode().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "dict code is required");
        }
        if (dictRepository.findFirstByCodeOrderByCreateTimeAsc(entity.getCode()).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "dict code already exists: " + entity.getCode());
        }
        return super.save(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_SAVE + "')")
    public SysDict update(SysDict entity) {
        return super.update(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_REMOVE + "')")
    public void remove(String id) {
        // 明细随字典一并逻辑删除，避免留下孤立明细被其它租户/后续编码复用而误读
        itemRepository.deleteByDictId(id);
        super.remove(id);
    }

    /** 按字典编码读取启用明细（前端下拉直接用）。 */
    @Transactional(readOnly = true)
    public List<SysDictItem> items(String dictCode) {
        SysDict dict = dictRepository.findFirstByCodeOrderByCreateTimeAsc(dictCode)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "dict not found: " + dictCode));
        return itemRepository.findByDictIdOrderBySortNoAsc(dict.getId()).stream()
                .filter(item -> item.getStatus() == null || item.getStatus().enabled())
                .toList();
    }
}
