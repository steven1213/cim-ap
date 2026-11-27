package com.cim.mds.server.reason;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cim.mds.server.namingrule.NamingRuleAdminService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 原因码主数据（V16）服务——Type / Code CRUD + 树维护 + 治理守门。
 *
 * <p><b>治理纪律</b>：{@code reasonCode} 全局唯一、建后不可改（命名规则
 * REASON_CODE 守门，无规则时手填直通）；{@code typeKey} 建后不可改（语义归属
 * 唯一，跨域挪用应新建码而非改旧码）；不物理删除；{@code enabled=false} 只影响
 * 未来下拉可见性。删除守门：有子节点的码不可删。</p>
 *
 * <p><b>树口径</b>：树即码——{@code parent_id} 自关联 + {@code node_path} 物化路径
 * 长在码上，每码必挂树。挪树（改父节点）时整棵子树重算路径与层级；环（把自己挂到
 * 自己的后代下）在后端拒绝。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReasonCodeAdminService {

    private final CimReasonCodeTypeRepository typeRepository;
    private final CimReasonCodeRepository codeRepository;
    private final NamingRuleAdminService namingRuleService;

    // ---------------------------------------------------------------- 视图 / 请求

    /** 类型视图（codeCount 供删除守门前端提示）。 */
    public record TypeView(String id, String typeKey, String typeName, Integer sortNo,
                           String description, long codeCount) {
    }

    /** 类型新建/更新请求。typeKey 建后不可改。 */
    public record TypeReq(String typeKey, String typeName, Integer sortNo, String description) {
    }

    /** 原因码视图（parentCode 供树形展示）。 */
    public record ReasonView(String id, String reasonCode, String typeKey, String shortName,
                             String owningDept, String parentId, String parentCode,
                             String nodePath, Integer levelNo, Boolean enabled,
                             Integer sortNo, String description) {
    }

    /** 原因码新建请求（reasonCode 走命名规则守门；typeKey/parentId 建后不可改）。 */
    public record ReasonCreateReq(String reasonCode, String typeKey, String shortName,
                                  String owningDept, String parentId, Integer sortNo,
                                  String description, Boolean enabled) {
    }

    /** 原因码更新请求（只吃治理属性与树位；reasonCode/typeKey 不在请求里）。 */
    public record ReasonUpdateReq(String shortName, String owningDept, String parentId,
                                  Integer sortNo, String description, Boolean enabled) {
    }

    // ---------------------------------------------------------------- 类型

    @Transactional(readOnly = true)
    public List<TypeView> listTypes() {
        return typeRepository.findByDeletedFalseOrderBySortNoAscTypeKeyAsc().stream()
                .map(this::toTypeView)
                .toList();
    }

    @Transactional
    public TypeView createType(TypeReq req) {
        String typeKey = requireText(req.typeKey(), "typeKey");
        String typeName = requireText(req.typeName(), "typeName");
        if (typeRepository.existsByTypeKeyAndDeletedFalse(typeKey)) {
            throw new BizException(BizCode.DATA_DUPLICATE, "类型键已存在: " + typeKey);
        }
        CimReasonCodeType type = new CimReasonCodeType();
        type.setTypeKey(typeKey);
        type.setTypeName(typeName);
        type.setSortNo(req.sortNo() == null ? 100 : req.sortNo());
        type.setDescription(blankToNull(req.description()));
        return toTypeView(typeRepository.save(type));
    }

    @Transactional
    public TypeView updateType(String id, TypeReq req) {
        CimReasonCodeType type = requireType(id);
        String typeName = requireText(req.typeName(), "typeName");
        // typeKey 建后不可改：码表按 type_key 字符串引用，改键=历史码语义漂移
        if (req.typeKey() != null && !req.typeKey().isBlank()
                && !req.typeKey().trim().equals(type.getTypeKey())) {
            throw new BizException(BizCode.PARAM_INVALID, "类型键建后不可改: " + type.getTypeKey());
        }
        type.setTypeName(typeName);
        type.setSortNo(req.sortNo() == null ? type.getSortNo() : req.sortNo());
        type.setDescription(blankToNull(req.description()));
        return toTypeView(typeRepository.save(type));
    }

    @Transactional
    public void deleteType(String id) {
        CimReasonCodeType type = requireType(id);
        if (!codeRepository.findByTypeKeyAndDeletedFalseOrderByNodePathAsc(type.getTypeKey())
                .isEmpty()) {
            // 有任何码挂靠（含停用）即拒绝：停用码仍是历史事实的语义锚点
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "类型下仍有原因码，不可删除: " + type.getTypeKey());
        }
        type.setDeleted(true);
        typeRepository.save(type);
    }

    // ---------------------------------------------------------------- 原因码

    /** 全量清单（node_path 升序 = 树的深度优先展开序）。 */
    @Transactional(readOnly = true)
    public List<ReasonView> listCodes(String typeKey, String keyword) {
        List<CimReasonCode> codes = (typeKey == null || typeKey.isBlank())
                ? codeRepository.findByDeletedFalseOrderByNodePathAsc()
                : codeRepository.findByTypeKeyAndDeletedFalseOrderByNodePathAsc(typeKey);
        Map<String, String> codeById = new HashMap<>();
        for (CimReasonCode c : codes) {
            codeById.put(c.getId(), c.getReasonCode());
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<ReasonView> views = new ArrayList<>();
        for (CimReasonCode c : codes) {
            if (!kw.isEmpty()
                    && !c.getReasonCode().toLowerCase().contains(kw)
                    && !(c.getShortName() == null || c.getShortName().toLowerCase().contains(kw))
                    && !c.getNodePath().toLowerCase().contains(kw)) {
                continue;
            }
            views.add(toView(c, codeById));
        }
        return views;
    }

    @Transactional
    public ReasonView createCode(ReasonCreateReq req) {
        String typeKey = requireText(req.typeKey(), "typeKey");
        CimReasonCodeType type = typeRepository.findByTypeKeyAndDeletedFalse(typeKey)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "原因码类型不存在: " + typeKey));
        // 命名规则 REASON_CODE 守门（无规则/停用 = 手填直通）；ctx.type 供 ENUM/SEQ 段用
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_REASON_CODE,
                req.reasonCode(), Map.of("type", type.getTypeKey()));
        if (codeRepository.existsByReasonCodeAndDeletedFalse(code)) {
            throw new BizException(BizCode.DATA_DUPLICATE, "原因码编码已存在: " + code);
        }
        CimReasonCode reason = new CimReasonCode();
        reason.setReasonCode(code);
        reason.setTypeKey(type.getTypeKey());
        reason.setShortName(blankToNull(req.shortName()));
        reason.setOwningDept(blankToNull(req.owningDept()));
        reason.setSortNo(req.sortNo() == null ? 100 : req.sortNo());
        reason.setDescription(blankToNull(req.description()));
        reason.setEnabled(req.enabled() == null ? Boolean.TRUE : req.enabled());
        attachParent(reason, req.parentId(), null);
        // 命名规则可能自动生成编码，路径/层级在 code 定稿后统一计算
        reason.setNodePath(buildPath(reason.getParentId(), code));
        reason.setLevelNo(computeLevel(reason.getParentId()));
        return toView(codeRepository.save(reason), null);
    }

    @Transactional
    public ReasonView updateCode(String id, ReasonUpdateReq req) {
        CimReasonCode reason = requireCode(id);
        reason.setShortName(blankToNull(req.shortName()));
        reason.setOwningDept(blankToNull(req.owningDept()));
        reason.setSortNo(req.sortNo() == null ? reason.getSortNo() : req.sortNo());
        reason.setDescription(blankToNull(req.description()));
        reason.setEnabled(req.enabled() == null ? reason.getEnabled() : req.enabled());
        String newParentId = blankToNull(req.parentId());
        boolean parentChanged = !java.util.Objects.equals(newParentId, reason.getParentId());
        if (parentChanged) {
            String oldPath = reason.getNodePath();
            attachParent(reason, newParentId, oldPath);
            String newPath = buildPath(reason.getParentId(), reason.getReasonCode());
            int newLevel = computeLevel(reason.getParentId());
            int delta = newLevel - reason.getLevelNo();
            // 子树整体搬移：替换路径前缀 + 平移层级
            for (CimReasonCode descendant : codeRepository
                    .findByNodePathStartingWithAndDeletedFalse(oldPath)) {
                descendant.setNodePath(newPath
                        + descendant.getNodePath().substring(oldPath.length()));
                descendant.setLevelNo(descendant.getLevelNo() + delta);
                codeRepository.save(descendant);
            }
            reason.setNodePath(newPath);
            reason.setLevelNo(newLevel);
        }
        return toView(codeRepository.save(reason), null);
    }

    @Transactional
    public void deleteCode(String id) {
        CimReasonCode reason = requireCode(id);
        if (!codeRepository.findByParentIdAndDeletedFalse(reason.getId()).isEmpty()) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "存在子节点，请先移除或删除子级: " + reason.getReasonCode());
        }
        reason.setDeleted(true);
        codeRepository.save(reason);
    }

    // ---------------------------------------------------------------- 内部

    /** 挂父守门：父存在、同类型、非自身、非自身后代（防环）。 */
    private void attachParent(CimReasonCode reason, String parentId, String selfOldPath) {
        if (parentId == null) {
            reason.setParentId(null);
            return;
        }
        CimReasonCode parent = codeRepository.findByIdAndDeletedFalse(parentId)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "父节点不存在: " + parentId));
        if (!parent.getTypeKey().equals(reason.getTypeKey())) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "父节点必须同语义域（" + reason.getTypeKey() + "）: " + parent.getReasonCode());
        }
        if (reason.getId() != null && reason.getId().equals(parent.getId())) {
            throw new BizException(BizCode.PARAM_INVALID, "父节点不能是自身");
        }
        if (selfOldPath != null && parent.getNodePath().startsWith(selfOldPath)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "父节点不能是自身的后代（会成环）: " + parent.getReasonCode());
        }
        reason.setParentId(parent.getId());
    }

    /** 物化路径：/根码/…/本码/。 */
    private String buildPath(String parentId, String reasonCode) {
        if (parentId == null) {
            return "/" + reasonCode + "/";
        }
        CimReasonCode parent = codeRepository.findByIdAndDeletedFalse(parentId)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "父节点不存在: " + parentId));
        return parent.getNodePath() + reasonCode + "/";
    }

    /** 层级：根 = 1，其余 = 父层级 + 1。 */
    private int computeLevel(String parentId) {
        if (parentId == null) {
            return 1;
        }
        CimReasonCode parent = codeRepository.findByIdAndDeletedFalse(parentId)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "父节点不存在: " + parentId));
        return parent.getLevelNo() + 1;
    }

    private TypeView toTypeView(CimReasonCodeType type) {
        long count = codeRepository.findByTypeKeyAndDeletedFalseOrderByNodePathAsc(type.getTypeKey())
                .size();
        return new TypeView(type.getId(), type.getTypeKey(), type.getTypeName(),
                type.getSortNo(), type.getDescription(), count);
    }

    private ReasonView toView(CimReasonCode c, Map<String, String> codeById) {
        String parentCode = c.getParentId() == null ? null
                : (codeById != null ? codeById.get(c.getParentId()) : codeRepository
                        .findByIdAndDeletedFalse(c.getParentId())
                        .map(CimReasonCode::getReasonCode).orElse(null));
        return new ReasonView(c.getId(), c.getReasonCode(), c.getTypeKey(), c.getShortName(),
                c.getOwningDept(), c.getParentId(), parentCode, c.getNodePath(),
                c.getLevelNo(), c.getEnabled(), c.getSortNo(), c.getDescription());
    }

    private CimReasonCodeType requireType(String id) {
        return typeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "原因码类型不存在: " + id));
    }

    private CimReasonCodeType requireTypeByKey(String typeKey) {
        return typeRepository.findByTypeKeyAndDeletedFalse(typeKey)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "原因码类型不存在: " + typeKey));
    }

    private CimReasonCode requireCode(String id) {
        return codeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "原因码不存在: " + id));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
