package com.cim.mds.server.equipment;

import java.util.List;
import java.util.Map;

import com.cim.mds.server.site.EditLockDefData;
import com.cim.mds.server.site.EditLockSupport;
import com.cim.mds.server.namingrule.NamingRuleAdminService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 机台组（ToolGroup）管理——派工候选池（ISA-95 Work Center 口径，v2 新增）。
 *
 * <p>动线口径：派工「先选组再选台」；组绑定机型（class_code），成员守门
 * <b>同机型才能入组</b>（派工语义下组内机台天然可互换，从源头保证同质）。</p>
 *
 * <p>组走编辑锁三列；成员写操作守门组的编辑锁；组删除物理级联成员；
 * 机台删除时由 {@link EquipmentAdminService} 级联清理成员关联。</p>
 */
@Service
@RequiredArgsConstructor
public class ToolGroupAdminService {

    private final CimToolGroupRepository groupRepository;
    private final CimToolGroupMemberRepository memberRepository;
    private final CimEquipmentClassRepository classRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final NamingRuleAdminService namingRuleService;

    // ------------------------------------------------------------------ 视图 / 请求

    /** 组视图（带成员机台编码清单，供主从页右侧成员块直显）。 */
    public record ToolGroupView(String id, String groupCode, String groupName, String classCode,
                                String className, List<String> memberEquipmentCodes,
                                String remark, Boolean enabled, String description,
                                Boolean occupied, String occupiedBy) {
    }

    public record ToolGroupReq(String groupCode, String groupName, String classCode,
                               String remark, Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<ToolGroupView> list(String keyword, String classCode) {
        List<CimToolGroup> groups = groupRepository.search(blankToNull(keyword), blankToNull(classCode));
        return groups.stream().map(this::toView).toList();
    }

    // ------------------------------------------------------------------ 组写

    @Transactional
    public ToolGroupView create(ToolGroupReq req) {
        requireText(req.groupName(), "groupName");
        CimEquipmentClass cls = requireClassByCode(req.classCode());
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_TOOL_GROUP, req.groupCode(),
                Map.of("kind", cls.getKindCode(), "type", cls.getClassCode()));
        if (groupRepository.findByGroupCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "机台组编码已存在: " + code);
        }
        CimToolGroup entity = new CimToolGroup();
        entity.setGroupCode(code);
        apply(entity, req);
        return toView(groupRepository.save(entity));
    }

    @Transactional
    public ToolGroupView update(String id, ToolGroupReq req) {
        CimToolGroup entity = requireGroup(id);
        requireText(req.groupName(), "groupName");
        assertNotLockedByOther(entity);
        CimEquipmentClass cls = requireClassByCode(req.classCode());
        // groupCode 建后不可改；换绑机型时若已有异机型成员则拒绝（先移出成员）
        if (!entity.getClassCode().equals(cls.getClassCode())
                && memberRepository.findByGroupIdAndDeletedFalse(id).stream()
                        .anyMatch(m -> equipmentRepository.findById(m.getEquipmentId())
                                .filter(e -> !Boolean.TRUE.equals(e.getDeleted()))
                                .map(e -> !cls.getClassCode().equals(e.getClassCode()))
                                .orElse(false))) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "组内存在异机型成员，先移出后再换绑机型: " + entity.getGroupCode());
        }
        entity.setGroupName(req.groupName().trim());
        entity.setClassCode(cls.getClassCode());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
        clearLock(entity);
        return toView(groupRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimToolGroup entity = requireGroup(id);
        assertNotLockedByOther(entity);
        // 成员随组物理级联（软删会撞 uk_cim_tool_group_member）
        memberRepository.deleteByGroupId(id);
        entity.setDeleted(Boolean.TRUE);
        groupRepository.save(entity);
    }

    // ------------------------------------------------------------------ 成员写

    @Transactional
    public ToolGroupView addMember(String groupId, String equipmentId) {
        CimToolGroup group = requireGroup(groupId);
        assertNotLockedByOther(group);
        CimEquipment equipment = equipmentRepository.findById(equipmentId)
                .filter(e -> !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "机台不存在: " + equipmentId));
        // 核心守门：同机型才能入组（派工候选池同质性）
        if (!group.getClassCode().equals(equipment.getClassCode())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "机台 " + equipment.getEquipmentCode() + "（" + equipment.getClassCode()
                            + "）与组机型 " + group.getClassCode() + " 不一致，不能入组");
        }
        if (!memberRepository.existsByGroupIdAndEquipmentIdAndDeletedFalse(groupId, equipmentId)) {
            CimToolGroupMember link = new CimToolGroupMember();
            link.setGroupId(groupId);
            link.setEquipmentId(equipmentId);
            memberRepository.save(link);
        }
        return toView(group);
    }

    @Transactional
    public ToolGroupView removeMember(String groupId, String equipmentId) {
        CimToolGroup group = requireGroup(groupId);
        assertNotLockedByOther(group);
        requireEquipment(equipmentId);
        memberRepository.findByGroupIdAndDeletedFalse(groupId).stream()
                .filter(m -> equipmentId.equals(m.getEquipmentId()))
                .forEach(memberRepository::delete);
        return toView(group);
    }

    // ------------------------------------------------------------------ 内部

    private CimToolGroup requireGroup(String id) {
        return groupRepository.findById(id)
                .filter(g -> !Boolean.TRUE.equals(g.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "机台组不存在: " + id));
    }

    private void requireEquipment(String id) {
        equipmentRepository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "机台不存在: " + id));
    }

    private CimEquipmentClass requireClassByCode(String classCode) {
        requireText(classCode, "classCode");
        CimEquipmentClass cls = classRepository.findByClassCodeAndDeletedFalse(classCode.trim())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "机型不存在: " + classCode));
        if (!Boolean.TRUE.equals(cls.getEnabled())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "机型已停用: " + classCode);
        }
        return cls;
    }

    private void apply(CimToolGroup entity, ToolGroupReq req) {
        entity.setGroupCode(entity.getGroupCode() == null ? req.groupCode().trim() : entity.getGroupCode());
        entity.setGroupName(req.groupName().trim());
        entity.setClassCode(req.classCode().trim());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private ToolGroupView toView(CimToolGroup g) {
        List<String> members = memberRepository.findByGroupIdAndDeletedFalse(g.getId()).stream()
                .map(m -> equipmentRepository.findById(m.getEquipmentId())
                        .filter(e -> !Boolean.TRUE.equals(e.getDeleted()))
                        .map(CimEquipment::getEquipmentCode)
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        String className = classRepository.findByClassCodeAndDeletedFalse(g.getClassCode())
                .map(CimEquipmentClass::getClassName).orElse(null);
        return new ToolGroupView(g.getId(), g.getGroupCode(), g.getGroupName(), g.getClassCode(),
                className, members,
                g.getRemark(), g.getEnabled(), g.getDescription(),
                g.getOccupied(), g.getOccupiedBy());
    }

    private static void assertNotLockedByOther(EditLockDefData entity) {
        if (Boolean.TRUE.equals(entity.getOccupied())
                && entity.getOccupiedBy() != null
                && !entity.getOccupiedBy().equals(EditLockSupport.currentUser())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "该记录正在被 " + entity.getOccupiedBy() + " 编辑，暂不能修改或删除");
        }
    }

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
