package com.cim.system.dict;

import com.cim.core.shared.PageResult;
import com.cim.jpa.history.HistoryRecorder;
import com.cim.jpa.support.AbstractJpaService;
import com.cim.jpa.tenant.TenantFilterApplier;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.PageQuery;
import com.cim.system.support.EnableStatus;
import com.cim.system.support.PermissionCodes;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字典明细服务。
 *
 * <p>独立成服务（而非挂在 {@code SysDictService} 里直接落库）是为了复用
 * {@code AbstractJpaService} 的<b>自动历史</b>能力——{@code SysDictItem} 声明了
 * {@code @History(SNAPSHOT)}，绕过服务基类就会静默丢掉明细的历史轨迹。</p>
 */
@Service
public class SysDictItemService extends AbstractJpaService<SysDictItem> {

    private final SysDictItemRepository itemRepository;

    public SysDictItemService(SysDictItemRepository itemRepository,
                              EntityManager entityManager,
                              HistoryRecorder historyRecorder,
                              TenantFilterApplier tenantFilterApplier) {
        super(itemRepository, entityManager, historyRecorder, tenantFilterApplier);
        this.itemRepository = itemRepository;
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

    // ------------------------------------------------------------ 明细管理端点用（挂在 /sys/dicts/{dictId}/items 下）

    /** 某字典的全量明细（含停用；管理页明细表用，与 {@link SysDictService#items} 的「仅启用」口径不同）。 */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_LIST + "')")
    public List<SysDictItem> listByDict(String dictId) {
        return itemRepository.findByDictIdOrderBySortNoAsc(dictId);
    }

    /** 在指定字典下新增明细（编码必填 + 字典内唯一）。 */
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_SAVE + "')")
    public SysDictItem createInDict(String dictId, SysDictItem item) {
        if (item.getItemKey() == null || item.getItemKey().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "dict item key is required");
        }
        itemRepository.findByDictIdOrderBySortNoAsc(dictId).stream()
                .filter(it -> it.getItemKey().equals(item.getItemKey().trim()))
                .findFirst()
                .ifPresent(it -> {
                    throw new BizException(BizCode.DATA_DUPLICATE,
                            "dict item key already exists: " + it.getItemKey());
                });
        item.setId(null);
        item.setDictId(dictId);
        item.setItemKey(item.getItemKey().trim());
        if (item.getStatus() == null) {
            item.setStatus(EnableStatus.ENABLED);
        }
        if (item.getSortNo() == null) {
            item.setSortNo(0);
        }
        return super.save(item);
    }

    /** 更新明细（校验归属 + 改 itemKey 时重查重；系统级标记随请求体设置）。 */
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_SAVE + "')")
    public SysDictItem updateInDict(String dictId, String itemId, SysDictItem patch) {
        SysDictItem existing = itemRepository.findById(itemId)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "dict item not found: " + itemId));
        if (!dictId.equals(existing.getDictId())) {
            throw new BizException(BizCode.PARAM_INVALID, "dict item does not belong to dict: " + dictId);
        }
        if (patch.getItemKey() != null && !patch.getItemKey().isBlank()) {
            String newKey = patch.getItemKey().trim();
            if (!newKey.equals(existing.getItemKey())) {
                itemRepository.findByDictIdOrderBySortNoAsc(dictId).stream()
                        .filter(it -> it.getItemKey().equals(newKey))
                        .findFirst()
                        .ifPresent(it -> {
                            throw new BizException(BizCode.DATA_DUPLICATE,
                                    "dict item key already exists: " + it.getItemKey());
                        });
            }
            existing.setItemKey(newKey);
        }
        existing.setItemValue(patch.getItemValue());
        existing.setI18nCode(patch.getI18nCode());
        existing.setSortNo(patch.getSortNo() != null ? patch.getSortNo() : existing.getSortNo());
        existing.setStatus(patch.getStatus() != null ? patch.getStatus() : existing.getStatus());
        if (patch.getIsSystem() != null) {
            existing.setIsSystem(patch.getIsSystem());
        }
        return super.update(existing);
    }

    /** 删除明细（系统级明细拒删 → 2004）。 */
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.DICT_REMOVE + "')")
    public void removeInDict(String dictId, String itemId) {
        SysDictItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "dict item not found: " + itemId));
        if (!dictId.equals(item.getDictId())) {
            throw new BizException(BizCode.PARAM_INVALID, "dict item does not belong to dict: " + dictId);
        }
        if (Boolean.TRUE.equals(item.getIsSystem())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "system dict item cannot be removed: " + item.getItemKey());
        }
        super.remove(itemId);
    }
}
