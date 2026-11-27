package com.cim.mds.server.flow;

import java.util.List;
import java.util.Set;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参数定义字典服务（ICM 长尾 KV 的一等公民定义层）。
 *
 * <p>attr_key 建后不可改（值表引用锚点）；scopes 控可配层级 CSV；删除守门：
 * 被 cim_constraint_attr 任一行引用 → 2004（引用即依赖）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParamDefAdminService {

    private static final Set<String> VALID_VALUE_TYPES = Set.of(
            CimParamDef.VT_STRING, CimParamDef.VT_INT, CimParamDef.VT_DECIMAL,
            CimParamDef.VT_BOOLEAN, CimParamDef.VT_ENUM, CimParamDef.VT_JSON);
    private static final Set<String> VALID_SCOPES = Set.of(
            CimEquipAssign.SCOPE_OPERATION, CimEquipAssign.SCOPE_FLOW, CimEquipAssign.SCOPE_STEP,
            CimEquipAssign.SCOPE_PRODUCT);

    private final CimParamDefRepository defRepository;
    private final CimConstraintAttrRepository attrRepository;

    public record ParamDefReq(String attrKey, String attrName, String groupCode,
                              String valueType, String enumDict, String unit, Boolean required,
                              List<String> scopes, Integer sortNo, Boolean enabled) {
    }

    @Transactional(readOnly = true)
    public List<CimParamDef> list() {
        return defRepository.findByDeletedFalseOrderBySortNoAsc();
    }

    @Transactional
    public CimParamDef create(ParamDefReq req) {
        String key = req.attrKey() == null ? "" : req.attrKey().trim();
        if (key.isBlank()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", "attrKey");
        }
        if (defRepository.findByAttrKeyAndDeletedFalse(key).isPresent()) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.paramDef.duplicate", key);
        }
        CimParamDef entity = new CimParamDef();
        entity.setAttrKey(key); // 建后不可改
        applyFields(entity, req);
        return defRepository.save(entity);
    }

    @Transactional
    public CimParamDef update(String id, ParamDefReq req) {
        CimParamDef entity = defRepository.findById(id)
                .filter(d -> !Boolean.TRUE.equals(d.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.paramDef.notFound", id));
        applyFields(entity, req); // attrKey 不吃（建后不可改）
        return defRepository.save(entity);
    }

    @Transactional
    public void delete(String id) {
        CimParamDef entity = defRepository.findById(id)
                .filter(d -> !Boolean.TRUE.equals(d.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.paramDef.notFound", id));
        if (attrRepository.existsByAttrKeyAndDeletedFalse(entity.getAttrKey())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.paramDef.referencedNoDelete", entity.getAttrKey());
        }
        defRepository.delete(entity);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void applyFields(CimParamDef entity, ParamDefReq req) {
        if (req.attrName() == null || req.attrName().isBlank()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", "attrName");
        }
        if (req.groupCode() == null || req.groupCode().isBlank()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", "groupCode");
        }
        String vt = req.valueType() == null ? "" : req.valueType().trim();
        if (!VALID_VALUE_TYPES.contains(vt)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.paramDef.valueTypeInvalid", vt);
        }
        if (CimParamDef.VT_ENUM.equals(vt)
                && (req.enumDict() == null || req.enumDict().isBlank())) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.paramDef.enumDictRequired");
        }
        List<String> scopes = req.scopes() == null || req.scopes().isEmpty()
                ? List.of(CimEquipAssign.SCOPE_STEP) : req.scopes();
        for (String s : scopes) {
            if (!VALID_SCOPES.contains(s)) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.paramDef.scopeInvalid", s);
            }
        }
        entity.setAttrName(req.attrName().trim());
        entity.setGroupCode(req.groupCode().trim());
        entity.setValueType(vt);
        entity.setEnumDict(req.enumDict());
        entity.setUnit(ParamDefAdminService.blankToNull(req.unit()));
        entity.setRequired(Boolean.TRUE.equals(req.required()));
        entity.setScopes(String.join(",", scopes));
        entity.setSortNo(req.sortNo() == null ? 100 : req.sortNo());
        entity.setEnabled(req.enabled() == null ? Boolean.TRUE : req.enabled());
    }
}
