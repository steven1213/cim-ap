package com.cim.mds.server.statemodel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.system.dict.SysDictService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 状态模型管理——「一类对象的状态机」注册表 + 状态转换 allowlist 的统一守门。
 *
 * <p>行业口径（SEMI E30 GEM 状态模型方法论 / State Transition Reporting）：
 * 状态模型 = 状态集合 + 转换集合 + 触发条件；改状态时按 {@code (旧态→新态)} 查
 * allowlist，白名单外拒绝（非法迁移不允许静默通过）。设备/子单元服务在更新
 * stateCode 前调 {@link #validateTransition}；新建不校验（无旧态即无转换）。</p>
 *
 * <p>模型支持页面 CRUD（V10 种子预置 EQUIPMENT / UNIT_LOAD_PORT / UNIT_CHAMBER /
 * UNIT_GENERIC 四个）：modelCode 建后不可改；dict_code/objectType 建后不可改（改了
 * 语义就断）；stateCodes 须为词表子集、收缩时涉及它的转换规则必须先清；删除守门：
 * 存在转换规则的模型不可删。</p>
 */
@Service
@RequiredArgsConstructor
public class StateModelAdminService {

    /** 设备域模型编码。 */
    public static final String MODEL_EQUIPMENT = "EQUIPMENT";

    /** 子单元域对象类型。 */
    public static final String OBJECT_EQUIPMENT_UNIT = "EQUIPMENT_UNIT";

    private final CimStateModelRepository modelRepository;
    private final CimStateTransitionRepository transitionRepository;
    private final SysDictService dictService;

    // ------------------------------------------------------------------ 视图 / 请求

    /** 模型视图（states 按 state_codes 声明顺序，前端矩阵行/列序）。 */
    public record StateModelView(String id, String modelCode, String modelName,
                                 String dictCode, String objectType, String unitForm,
                                 List<String> states) {
    }

    /** 模型请求：create 全字段生效；update 只吃 modelName/unitForm/stateCodes。 */
    public record StateModelReq(String modelCode, String modelName, String dictCode,
                                String objectType, String unitForm, List<String> stateCodes) {
    }

    /** 转换规则视图。 */
    public record TransitionView(String id, String fromState, String toState, String triggerNote) {
    }

    /** 矩阵勾选请求：allowed=true 建/更新行，false 物理删行。 */
    public record TransitionReq(String fromState, String toState, Boolean allowed, String triggerNote) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<StateModelView> listModels() {
        return modelRepository.findAll().stream()
                .filter(m -> !Boolean.TRUE.equals(m.getDeleted()))
                .map(StateModelAdminService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransitionView> transitions(String modelCode) {
        requireModel(modelCode);
        return transitionRepository.findByModelCodeAndDeletedFalse(modelCode).stream()
                .map(t -> new TransitionView(t.getId(), t.getFromState(), t.getToState(), t.getTriggerNote()))
                .toList();
    }

    /** 从某状态出发的合法目标列表（按模型状态声明序，供表单下拉只列可达项）。 */
    @Transactional(readOnly = true)
    public List<String> allowedTargets(String modelCode, String fromState) {
        requireModel(modelCode);
        List<String> targets = transitionRepository.findByModelCodeAndDeletedFalse(modelCode).stream()
                .filter(t -> t.getFromState().equals(fromState))
                .map(CimStateTransition::getToState)
                .toList();
        return statesOf(requireModel(modelCode)).stream().filter(targets::contains).toList();
    }

    /**
     * 按对象域 + 子单元形态解析模型：形态精确匹配优先，无则取该域 unit_form 为空的
     * 兜底模型（UNIT_GENERIC 之于 ALIGNER/ROBOT/…）。
     */
    @Transactional(readOnly = true)
    public StateModelView resolve(String objectType, String unitForm) {
        List<CimStateModel> models = modelRepository.findAll().stream()
                .filter(m -> !Boolean.TRUE.equals(m.getDeleted()))
                .filter(m -> m.getObjectType().equals(objectType))
                .toList();
        String form = blankToNull(unitForm);
        CimStateModel hit = models.stream()
                .filter(m -> form != null && form.equals(m.getUnitForm()))
                .findFirst()
                .orElseGet(() -> models.stream()
                        .filter(m -> m.getUnitForm() == null)
                        .findFirst()
                        .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND,
                                "无匹配的状态模型: objectType=" + objectType + ", unitForm=" + unitForm)));
        return toView(hit);
    }

    // ------------------------------------------------------------------ 模型 CRUD（页面维护）

    @Transactional
    public StateModelView create(StateModelReq req) {
        requireText(req.modelCode(), "modelCode");
        requireText(req.modelName(), "modelName");
        requireText(req.dictCode(), "dictCode");
        requireText(req.objectType(), "objectType");
        List<String> states = normalizeStates(req.stateCodes(), req.dictCode().trim());
        if (modelRepository.findByModelCodeAndDeletedFalse(req.modelCode().trim()).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "状态模型编码已存在: " + req.modelCode());
        }
        CimStateModel entity = new CimStateModel();
        entity.setModelCode(req.modelCode().trim());
        entity.setModelName(req.modelName().trim());
        entity.setDictCode(req.dictCode().trim());
        entity.setObjectType(req.objectType().trim());
        entity.setUnitForm(blankToNull(req.unitForm()));
        entity.setStateCodes(String.join(",", states));
        return toView(modelRepository.save(entity));
    }

    /**
     * 更新模型：只吃 modelName / unitForm / stateCodes（modelCode/dictCode/objectType
     * 建后不可改）。状态收缩时，被移除的状态若仍被转换规则引用则拒绝——先清矩阵再收。
     */
    @Transactional
    public StateModelView update(String modelCode, StateModelReq req) {
        CimStateModel entity = requireModel(modelCode);
        requireText(req.modelName(), "modelName");
        List<String> states = normalizeStates(req.stateCodes(), entity.getDictCode());
        Set<String> removed = new LinkedHashSet<>(statesOf(entity));
        states.forEach(removed::remove);
        if (!removed.isEmpty()) {
            List<String> blocking = transitionRepository.findByModelCodeAndDeletedFalse(modelCode)
                    .stream()
                    .filter(t -> removed.contains(t.getFromState()) || removed.contains(t.getToState()))
                    .map(t -> t.getFromState() + "→" + t.getToState())
                    .toList();
            if (!blocking.isEmpty()) {
                throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                        "状态仍被转换规则引用，先在矩阵中清除后再移除: " + blocking);
            }
        }
        entity.setModelName(req.modelName().trim());
        entity.setUnitForm(blankToNull(req.unitForm()));
        entity.setStateCodes(String.join(",", states));
        return toView(modelRepository.save(entity));
    }

    /** 删除模型（软删）：存在转换规则时拒绝（规则是模型语义的一部分，不允许悬空）。 */
    @Transactional
    public void delete(String modelCode) {
        CimStateModel entity = requireModel(modelCode);
        if (!transitionRepository.findByModelCodeAndDeletedFalse(modelCode).isEmpty()) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "状态模型仍有转换规则，先清空矩阵再删除: " + modelCode);
        }
        entity.setDeleted(Boolean.TRUE);
        modelRepository.save(entity);
    }

    // ------------------------------------------------------------------ 转换矩阵维护

    @Transactional
    public TransitionView setTransition(String modelCode, TransitionReq req) {
        CimStateModel model = requireModel(modelCode);
        String from = requireStateIn(model, req.fromState(), "fromState");
        String to = requireStateIn(model, req.toState(), "toState");
        if (from.equals(to)) {
            throw new BizException(BizCode.PARAM_INVALID, "fromState 与 toState 不能相同（自环无意义）");
        }
        boolean allowed = req.allowed() == null || req.allowed();
        if (!allowed) {
            transitionRepository.findByModelCodeAndFromStateAndToStateAndDeletedFalse(modelCode, from, to)
                    .ifPresent(t -> {
                        t.setDeleted(Boolean.TRUE);
                        transitionRepository.save(t);
                    });
            return null;
        }
        CimStateTransition entity = transitionRepository
                .findByModelCodeAndFromStateAndToStateAndDeletedFalse(modelCode, from, to)
                .orElseGet(() -> {
                    CimStateTransition t = new CimStateTransition();
                    t.setModelCode(modelCode);
                    t.setFromState(from);
                    t.setToState(to);
                    return t;
                });
        entity.setTriggerNote(blankToNull(req.triggerNote()));
        return toView(transitionRepository.save(entity));
    }

    // ------------------------------------------------------------------ 守门（供各业务域调用）

    /**
     * 状态转换守门：{@code (from→to)} 不在模型 allowlist 内即拒绝（OPERATION_NOT_ALLOWED）。
     * 调用约定：仅在状态<b>实际发生变化</b>时调用（新建无旧态不校验）。
     */
    @Transactional(readOnly = true)
    public void validateTransition(String modelCode, String fromState, String toState) {
        if (blankToNull(fromState) == null || blankToNull(toState) == null) {
            return;
        }
        if (fromState.equals(toState)) {
            return;
        }
        boolean hit = transitionRepository
                .findByModelCodeAndFromStateAndToStateAndDeletedFalse(modelCode, fromState, toState)
                .isPresent();
        if (!hit) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "状态转换不允许: " + modelCode + " " + fromState + " → " + toState
                            + "（不在状态模型 allowlist 内，请在状态模型页维护转换规则）");
        }
    }

    // ------------------------------------------------------------------ 内部

    private CimStateModel requireModel(String modelCode) {
        return modelRepository.findByModelCodeAndDeletedFalse(modelCode)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "状态模型不存在: " + modelCode));
    }

    private static List<String> statesOf(CimStateModel model) {
        return List.of(model.getStateCodes().split(","));
    }

    private String requireStateIn(CimStateModel model, String value, String field) {
        String v = blankToNull(value);
        if (v == null) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
        if (!statesOf(model).contains(v)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    field + " 不在模型 " + model.getModelCode() + " 的状态集内: " + v);
        }
        return v;
    }

    private static StateModelView toView(CimStateModel m) {
        return new StateModelView(m.getId(), m.getModelCode(), m.getModelName(), m.getDictCode(),
                m.getObjectType(), m.getUnitForm(), List.of(m.getStateCodes().split(",")));
    }

    private static TransitionView toView(CimStateTransition t) {
        return new TransitionView(t.getId(), t.getFromState(), t.getToState(), t.getTriggerNote());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
    }

    /**
     * 状态集归一：去空白、保序去重、非空；每个状态必须是挂载词表的字典项
     * （词表缺失/无项视为配置错误直接拒绝）；CSV 总长不超列宽 512。
     */
    private List<String> normalizeStates(List<String> stateCodes, String dictCode) {
        if (stateCodes == null || stateCodes.isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "stateCodes 不能为空（模型至少要有一个状态）");
        }
        var items = dictService.items(dictCode);
        if (items.isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "数据字典不存在或无字典项: " + dictCode);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : stateCodes) {
            String s = blankToNull(raw);
            if (s == null) {
                throw new BizException(BizCode.PARAM_INVALID, "stateCodes 含空白项");
            }
            if (items.stream().noneMatch(i -> s.equals(i.getItemKey()))) {
                throw new BizException(BizCode.PARAM_INVALID,
                        "stateCodes 不在数据字典 " + dictCode + " 内: " + s);
            }
            if (!normalized.add(s)) {
                throw new BizException(BizCode.PARAM_INVALID, "stateCodes 含重复项: " + s);
            }
        }
        String csv = String.join(",", normalized);
        if (csv.length() > 512) {
            throw new BizException(BizCode.PARAM_INVALID, "stateCodes 过长（CSV 上限 512 字符）");
        }
        return List.copyOf(normalized);
    }
}
