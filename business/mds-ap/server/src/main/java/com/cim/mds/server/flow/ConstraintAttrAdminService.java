package com.cim.mds.server.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KV 约束值服务（ICM：context=OPERATION/FLOW/STEP 三层一套批量 upsert）。
 *
 * <p>PUT 语义 = 全量 upsert：请求携带本上下文的完整 KV 集，缺失的 key 删除、
 * 相同值跳过、新值插入/更新。守门：attr_key 须在 cim_param_def 注册且 enabled；
 * param_def.scopes 未含本 context → 2004；四态只读（FLOW/STEP 行所属流程须 UNFROZEN）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConstraintAttrAdminService {

    private final CimConstraintAttrRepository attrRepository;
    private final CimParamDefRepository paramDefRepository;
    private final CimFlowRepository flowRepository;
    private final FlowEventService flowEventService;

    /** PUT 请求：key → value 全量快照（value 可空串 = 显式清空？不——空串=无值不写）。 */
    public record AttrPutReq(Map<String, String> values) {
    }

    // ---------------------------------------------------------------- 查询

    @Transactional(readOnly = true)
    public Map<String, String> listByOperation(String opCode) {
        Map<String, String> out = new HashMap<>();
        attrRepository.findByContextTypeAndOperationCodeAndDeletedFalse(
                CimEquipAssign.SCOPE_OPERATION, opCode)
                .forEach(a -> out.put(a.getAttrKey(), a.getAttrValue()));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, String> listByFlow(String flowId) {
        Map<String, String> out = new HashMap<>();
        attrRepository.findByContextTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_FLOW, flowId)
                .forEach(a -> out.put(a.getAttrKey(), a.getAttrValue()));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, String> listByStep(String flowId, String nodeId) {
        Map<String, String> out = new HashMap<>();
        attrRepository.findByContextTypeAndFlowIdAndStepIdAndDeletedFalse(
                CimEquipAssign.SCOPE_STEP, flowId, nodeId)
                .forEach(a -> out.put(a.getAttrKey(), a.getAttrValue()));
        return out;
    }

    // ---------------------------------------------------------------- upsert

    @Transactional
    public Map<String, String> putForFlow(String flowId, AttrPutReq req) {
        requireEditableFlow(flowId);
        Map<String, String> out = put(CimEquipAssign.SCOPE_FLOW, "", flowId, "", req);
        recordAttr(flowId, FlowEventService.summary("scope", CimEquipAssign.SCOPE_FLOW, "keys", out.keySet()));
        return out;
    }

    @Transactional
    public Map<String, String> putForStep(String flowId, String nodeId, AttrPutReq req) {
        requireEditableFlow(flowId);
        Map<String, String> out = put(CimEquipAssign.SCOPE_STEP, "", flowId, nodeId, req);
        recordAttr(flowId, FlowEventService.summary("scope", CimEquipAssign.SCOPE_STEP,
                "stepId", nodeId, "keys", out.keySet()));
        return out;
    }

    @Transactional
    public Map<String, String> putForOperation(String opCode, Map<String, String> values) {
        return put(CimEquipAssign.SCOPE_OPERATION, opCode, "", "", new AttrPutReq(values));
    }

    // ---------------------------------------------------------------- L3 产品级 KV（V25）

    @Transactional(readOnly = true)
    public Map<String, String> listByProduct(String productCode, String familyCode) {
        Map<String, String> out = new HashMap<>();
        attrRepository.findByContextTypeAndProductCodeAndFamilyCodeAndDeletedFalse(
                CimEquipAssign.SCOPE_PRODUCT, productCode, familyCode)
                .forEach(a -> out.put(a.getAttrKey(), a.getAttrValue()));
        return out;
    }

    /** PRODUCT 层 KV 全量快照 PUT（product_code+family_code 定位；独立于流程四态）。 */
    @Transactional
    public Map<String, String> putForProduct(String productCode, String familyCode, AttrPutReq req) {
        Map<String, String> values = req == null || req.values() == null ? Map.of() : req.values();
        for (Map.Entry<String, String> e : values.entrySet()) {
            CimParamDef def = paramDefRepository.findByAttrKeyAndDeletedFalse(e.getKey())
                    .filter(d -> Boolean.TRUE.equals(d.getEnabled()))
                    .orElseThrow(() -> BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                            "mds.biz.attr.keyNotDefined", e.getKey()));
            if (def.getScopes() == null || !def.getScopes().contains(CimEquipAssign.SCOPE_PRODUCT)) {
                throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                        "mds.biz.attr.scopeNotAllowed", e.getKey(), CimEquipAssign.SCOPE_PRODUCT);
            }
        }
        List<CimConstraintAttr> existing = attrRepository
                .findByContextTypeAndProductCodeAndFamilyCodeAndDeletedFalse(
                        CimEquipAssign.SCOPE_PRODUCT, productCode, familyCode);
        for (CimConstraintAttr row : existing) {
            if (!values.containsKey(row.getAttrKey())) {
                attrRepository.delete(row);
            }
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            String v = e.getValue();
            if (v == null || v.isBlank()) {
                continue;
            }
            CimConstraintAttr row = existing.stream()
                    .filter(r -> r.getAttrKey().equals(e.getKey())).findFirst()
                    .orElseGet(() -> {
                        CimConstraintAttr n = new CimConstraintAttr();
                        n.setContextType(CimEquipAssign.SCOPE_PRODUCT);
                        n.setProductCode(productCode);
                        n.setFamilyCode(familyCode);
                        n.setAttrKey(e.getKey());
                        return n;
                    });
            row.setAttrValue(v);
            attrRepository.save(row);
        }
        return listByProduct(productCode, familyCode);
    }

    /** 通用 upsert（key 校验 → 删/跳/插）。 */
    private Map<String, String> put(String context, String opCode, String flowId,
                                    String nodeId, AttrPutReq req) {
        Map<String, String> values = req == null || req.values() == null ? Map.of() : req.values();
        // key 合法性：注册 + enabled + scopes 含本 context
        // 未注册/停用 = 不允许写（与 scopes 守门、四态只读同族 → OPERATION_NOT_ALLOWED）
        for (Map.Entry<String, String> e : values.entrySet()) {
            CimParamDef def = paramDefRepository.findByAttrKeyAndDeletedFalse(e.getKey())
                    .filter(d -> Boolean.TRUE.equals(d.getEnabled()))
                    .orElseThrow(() -> BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                            "mds.biz.attr.keyNotDefined", e.getKey()));
            if (def.getScopes() == null || !def.getScopes().contains(context)) {
                throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                        "mds.biz.attr.scopeNotAllowed", e.getKey(), context);
            }
        }
        List<CimConstraintAttr> existing = switch (context) {
            case CimEquipAssign.SCOPE_OPERATION -> attrRepository
                    .findByContextTypeAndOperationCodeAndDeletedFalse(context, opCode);
            case CimEquipAssign.SCOPE_FLOW -> attrRepository
                    .findByContextTypeAndFlowIdAndDeletedFalse(context, flowId);
            default -> attrRepository
                    .findByContextTypeAndFlowIdAndStepIdAndDeletedFalse(context, flowId, nodeId);
        };
        // 删除：请求中没有的 key
        for (CimConstraintAttr row : existing) {
            if (!values.containsKey(row.getAttrKey())) {
                attrRepository.delete(row);
            }
        }
        // 插入/更新
        for (Map.Entry<String, String> e : values.entrySet()) {
            String v = e.getValue();
            if (v == null || v.isBlank()) {
                continue; // 空值 = 不落行（沿链回落）
            }
            CimConstraintAttr row = existing.stream()
                    .filter(r -> r.getAttrKey().equals(e.getKey())).findFirst()
                    .orElseGet(() -> {
                        CimConstraintAttr n = new CimConstraintAttr();
                        n.setContextType(context);
                        n.setOperationCode(opCode == null ? "" : opCode);
                        n.setFlowId(flowId == null ? "" : flowId);
                        n.setStepId(nodeId == null ? "" : nodeId);
                        n.setAttrKey(e.getKey());
                        return n;
                    });
            row.setAttrValue(v);
            attrRepository.save(row);
        }
        // 回读
        Map<String, String> out = new HashMap<>();
        switch (context) {
            case CimEquipAssign.SCOPE_OPERATION -> attrRepository
                    .findByContextTypeAndOperationCodeAndDeletedFalse(context, opCode)
                    .forEach(a -> out.put(a.getAttrKey(), a.getAttrValue()));
            case CimEquipAssign.SCOPE_FLOW -> attrRepository
                    .findByContextTypeAndFlowIdAndDeletedFalse(context, flowId)
                    .forEach(a -> out.put(a.getAttrKey(), a.getAttrValue()));
            default -> attrRepository
                    .findByContextTypeAndFlowIdAndStepIdAndDeletedFalse(context, flowId, nodeId)
                    .forEach(a -> out.put(a.getAttrKey(), a.getAttrValue()));
        }
        return out;
    }

    /** 事件历史（V24）：FLOW/STEP 层 KV 快照变更落流程锚点事件（action=ATTR_REPLACE）。 */
    private void recordAttr(String flowId, Map<String, Object> detail) {
        String flowCode = flowRepository.findById(flowId)
                .map(CimFlow::getFlowCode).orElse(flowId);
        flowEventService.record(flowId, flowCode, FlowEventService.T_ATTR, "ATTR_REPLACE", detail);
    }

    private void requireEditableFlow(String flowId) {
        CimFlow flow = flowRepository.findById(flowId)
                .filter(f -> !Boolean.TRUE.equals(f.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.flow.notFound", flowId));
        EquipAssignAdminService.requireUnfrozen(flow);
    }
}
