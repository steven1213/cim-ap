package com.cim.mds.server.site;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cim.mds.server.namingrule.NamingRuleAdminService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 区域组（AreaGroup）管理——Area 的统计口径分类（location-design.md §9.1）。
 *
 * <p>组的增删改是 Area 管理切面的一部分：<b>权限复用 {@code mds:area:*}</b>（控制器层把关），
 * 不新增权限码与菜单；编码不可改、归属 Fab 不可改（改了等于全体成员跨厂搬迁）。
 * 删除守门：组内仍有 Area 时拒绝。</p>
 */
@Service
@RequiredArgsConstructor
public class AreaGroupAdminService {

    private final CimAreaGroupRepository areaGroupRepository;
    private final CimAreaRepository areaRepository;
    private final CimFabRepository fabRepository;
    private final NamingRuleAdminService namingRuleService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record AreaGroupView(String id, String groupCode, String fabCode, String groupName,
                                String remark, Boolean enabled, String description,
                                Boolean occupied, String occupiedBy) {
    }

    public record AreaGroupReq(String groupCode, String fabCode, String groupName,
                               String remark, Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<AreaGroupView> list(String fabCode, String keyword) {
        return areaGroupRepository.search(blankToNull(fabCode), blankToNull(keyword)).stream()
                .map(AreaGroupAdminService::toView)
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public AreaGroupView create(AreaGroupReq req) {
        requireText(req.groupName(), "groupName");
        requireText(req.fabCode(), "fabCode");
        requireActiveFabByCode(req.fabCode());
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_AREA_GROUP, req.groupCode(),
                Map.of("fab", req.fabCode().trim()));
        if (areaGroupRepository.findByFabCodeAndGroupCodeAndDeletedFalse(
                req.fabCode().trim(), code).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "该 Fab 下区域组编码已存在: " + code);
        }
        CimAreaGroup entity = new CimAreaGroup();
        entity.setGroupCode(code);
        apply(entity, req);
        return toView(areaGroupRepository.save(entity));
    }

    @Transactional
    public AreaGroupView update(String id, AreaGroupReq req) {
        CimAreaGroup entity = requireGroup(id);
        requireText(req.groupName(), "groupName");
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        // groupCode / fabCode 是主标识与归属：不可改（成员按 id 引用，改名会造成两套口径）
        entity.setGroupName(req.groupName().trim());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
        clearLock(entity);
        return toView(areaGroupRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimAreaGroup entity = requireGroup(id);
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        long members = areaRepository.countByAreaGroupIdAndDeletedFalse(entity.getId());
        if (members > 0) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "区域组内仍有 " + members + " 个 Area，先移出或改组后再删");
        }
        entity.setDeleted(Boolean.TRUE);
        areaGroupRepository.save(entity);
    }

    // ------------------------------------------------------------------ 供 SiteAdminService 复用

    /** 校验 Area 携带的 {@code areaGroupId}：可空；存在、与 Area 同 Fab、启用。返回规范化的组 id（或 null）。 */
    public String resolveGroupIdForArea(String areaGroupId, String fabCode) {
        if (areaGroupId == null || areaGroupId.isBlank()) {
            return null;
        }
        CimAreaGroup group = requireGroup(areaGroupId.trim());
        if (!group.getFabCode().equals(fabCode)) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "区域组 " + group.getGroupCode() + " 与 Area 不在同一 Fab");
        }
        if (!Boolean.TRUE.equals(group.getEnabled())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "区域组已停用: " + group.getGroupCode());
        }
        return group.getId();
    }

    /** Fab → 组名映射（前端树分节用不到名字以外的信息时按需取）。 */
    @Transactional(readOnly = true)
    public Map<String, String> groupNameMap() {
        return areaGroupRepository.search(null, null).stream()
                .collect(Collectors.toMap(CimAreaGroup::getId, CimAreaGroup::getGroupName, (a, b) -> a));
    }

    // ------------------------------------------------------------------ 内部

    private CimAreaGroup requireGroup(String id) {
        return areaGroupRepository.findById(id)
                .filter(g -> !Boolean.TRUE.equals(g.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "区域组不存在: " + id));
    }

    private void requireActiveFabByCode(String fabCode) {
        CimFab fab = fabRepository.findByFabCodeAndDeletedFalse(fabCode.trim())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "归属 Fab 不存在: " + fabCode));
        if (!Boolean.TRUE.equals(fab.getEnabled())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "归属 Fab 已停用: " + fabCode);
        }
    }

    private static void apply(CimAreaGroup entity, AreaGroupReq req) {
        entity.setGroupCode(entity.getGroupCode() == null ? req.groupCode().trim() : entity.getGroupCode());
        entity.setFabCode(entity.getFabCode() == null ? req.fabCode().trim() : entity.getFabCode());
        entity.setGroupName(req.groupName().trim());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private static AreaGroupView toView(CimAreaGroup g) {
        return new AreaGroupView(g.getId(), g.getGroupCode(), g.getFabCode(), g.getGroupName(),
                g.getRemark(), g.getEnabled(), g.getDescription(), g.getOccupied(), g.getOccupiedBy());
    }

    /** 编辑锁守门：被他人占用即拒绝（与 SiteAdminService 同口径）。 */
    private static void assertNotLockedByOther(boolean occupied, String occupiedBy) {
        if (occupied && occupiedBy != null && !occupiedBy.equals(EditLockSupport.currentUser())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "该记录正在被 " + occupiedBy + " 编辑，暂不能修改或删除");
        }
    }

    /** 保存成功后清编辑锁（与数据保存同一事务）。 */
    private static void clearLock(EditLockDefData entity) {
        entity.setOccupied(Boolean.FALSE);
        entity.setOccupiedBy(null);
        entity.setOccupiedAt(null);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
