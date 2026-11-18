package com.cim.mds.server.flow;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.cim.mds.server.equipment.CimCapabilityRepository;
import com.cim.mds.server.equipment.CimEquipmentTypeRepository;
import com.cim.mds.server.site.CimTechnologyRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 工序库（Operation）服务——独立主数据 CRUD + 版本生命周期 + 引用守门。
 *
 * <p>生命周期与 {@link FlowAdminService} 同款四态（建模纪律全库统一）：
 * UNFROZEN → FROZEN → ACTIVE（同族唯一，激活旧版自动降级）→ DISABLED；
 * 改冻结后/生效版本 = copy 出同族新版本。与 Flow 的差异：工序库无画布/XML，
 * op_code 手填建后不可改（追溯锚点，不走 NamingRule）。</p>
 *
 * <p>引用守门：只有 ACTIVE 工序可被路线节点引用（保存画布时守门）；
 * 被引用的工序禁删禁禁用（2004）——引用即依赖，下线走复制新版替换。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationAdminService {

    /** 合法 op_type 全集（业务工序三类；结构工件不入库）。 */
    private static final Set<String> VALID_OP_TYPES = Set.of(
            CimOperation.TYPE_PROCESS, CimOperation.TYPE_INSPECT, CimOperation.TYPE_REWORK);

    /** 合法批处理模式（与 Flow 步骤同词表）。 */
    private static final Set<String> VALID_BATCH_MODES = Set.of(
            CimOperation.BATCH_SERIAL, CimOperation.BATCH_PARALLEL, CimOperation.BATCH_BATCH);

    /** 抽样率上限 100。 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CimOperationRepository operationRepository;
    private final CimFlowStepRepository stepRepository;
    private final CimTechnologyRepository technologyRepository;
    private final CimEquipmentTypeRepository equipmentTypeRepository;
    private final CimCapabilityRepository capabilityRepository;

    // ------------------------------------------------------------------ 视图 / 请求

    /** 工序视图（referenced = 是否被路线步骤引用，前端禁删/禁禁用按钮用）。 */
    public record OpView(String id, String opCode, String opName, String opType,
                         String techCode, String status, String opFamilyCode, Integer versionNo,
                         String recipeRef, String equipType, String capabilityCode, String edcPlanCode,
                         Integer qtimeMin, Integer qtimeMax, Integer reworkLimit,
                         Boolean holdPoint, String holdReason,
                         String batchMode, Integer batchSize,
                         String inspectPlan, BigDecimal samplingRate,
                         String reticleRef, String testProgram, String inhibitGroup,
                         String npwPlan, String chamberSelect,
                         String remark, String description, boolean referenced) {
    }

    /** 工序新建/更新请求。opCode 仅新建生效（建后不可改）。 */
    public record OpReq(String opCode, String opName, String opType, String techCode,
                        String recipeRef, String equipType, String capabilityCode, String edcPlanCode,
                        Integer qtimeMin, Integer qtimeMax, Integer reworkLimit,
                        Boolean holdPoint, String holdReason,
                        String batchMode, Integer batchSize,
                        String inspectPlan, BigDecimal samplingRate,
                        String reticleRef, String testProgram, String inhibitGroup,
                        String npwPlan, String chamberSelect,
                        String remark, String description) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<OpView> list(String keyword) {
        return operationRepository.search(blankToNull(keyword)).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public OpView detail(String id) {
        return toView(requireOperation(id));
    }

    /** 生效工序清单（路线画布节点「选工序」下拉用；仅 ACTIVE）。 */
    @Transactional(readOnly = true)
    public List<OpView> listActive() {
        return operationRepository.search(null).stream()
                .filter(o -> CimOperation.STATUS_ACTIVE.equals(o.getStatus()))
                .map(this::toView)
                .toList();
    }

    // ------------------------------------------------------------------ CRUD

    @Transactional
    public OpView create(OpReq req) {
        requireText(req.opCode(), "opCode");
        requireText(req.opName(), "opName");
        String opType = requireOpType(req.opType());
        String techCode = requireTech(req.techCode());
        String equipType = requireEquipType(req.equipType());
        String capabilityCode = requireCapability(req.capabilityCode());
        checkQtime(req.qtimeMin(), req.qtimeMax());
        checkInspectOnly(opType, req.inspectPlan(), req.samplingRate());
        checkBatch(req.batchMode(), req.batchSize());
        if (operationRepository.findByOpCodeAndDeletedFalse(req.opCode().trim()).isPresent()) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.operation.opCodeDuplicate", req.opCode().trim());
        }
        CimOperation entity = new CimOperation();
        entity.setOpCode(req.opCode().trim());
        entity.setOpName(req.opName().trim());
        entity.setOpType(opType);
        entity.setTechCode(techCode);
        entity.setEquipType(equipType);
        entity.setCapabilityCode(capabilityCode);
        entity.setEdcPlanCode(req.edcPlanCode());
        entity.setRecipeRef(req.recipeRef());
        entity.setQtimeMin(req.qtimeMin());
        entity.setQtimeMax(req.qtimeMax());
        entity.setReworkLimit(req.reworkLimit());
        entity.setHoldPoint(Boolean.TRUE.equals(req.holdPoint()));
        entity.setHoldReason(req.holdReason());
        entity.setBatchMode(req.batchMode());
        entity.setBatchSize(req.batchSize());
        entity.setInspectPlan(req.inspectPlan());
        entity.setSamplingRate(req.samplingRate());
        entity.setReticleRef(req.reticleRef());
        entity.setTestProgram(req.testProgram());
        entity.setInhibitGroup(req.inhibitGroup());
        entity.setNpwPlan(req.npwPlan());
        entity.setChamberSelect(req.chamberSelect());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setStatus(CimOperation.STATUS_UNFROZEN);
        entity.setOpFamilyCode(entity.getOpCode()); // 新建 = 自成版本族
        entity.setVersionNo(1);
        return toView(operationRepository.save(entity));
    }

    /** 更新（仅 UNFROZEN；opCode/opType 不可改——改类型会破坏已引用语义）。 */
    @Transactional
    public OpView update(String id, OpReq req) {
        CimOperation entity = requireOperation(id);
        requireDraft(entity);
        requireText(req.opName(), "opName");
        String techCode = requireTech(req.techCode());
        String equipType = requireEquipType(req.equipType());
        String capabilityCode = requireCapability(req.capabilityCode());
        checkQtime(req.qtimeMin(), req.qtimeMax());
        checkInspectOnly(entity.getOpType(), req.inspectPlan(), req.samplingRate());
        checkBatch(req.batchMode(), req.batchSize());
        entity.setOpName(req.opName().trim());
        entity.setTechCode(techCode);
        entity.setEquipType(equipType);
        entity.setCapabilityCode(capabilityCode);
        entity.setEdcPlanCode(req.edcPlanCode());
        entity.setRecipeRef(req.recipeRef());
        entity.setQtimeMin(req.qtimeMin());
        entity.setQtimeMax(req.qtimeMax());
        entity.setReworkLimit(req.reworkLimit());
        entity.setHoldPoint(Boolean.TRUE.equals(req.holdPoint()));
        entity.setHoldReason(req.holdReason());
        entity.setBatchMode(req.batchMode());
        entity.setBatchSize(req.batchSize());
        entity.setInspectPlan(req.inspectPlan());
        entity.setSamplingRate(req.samplingRate());
        entity.setReticleRef(req.reticleRef());
        entity.setTestProgram(req.testProgram());
        entity.setInhibitGroup(req.inhibitGroup());
        entity.setNpwPlan(req.npwPlan());
        entity.setChamberSelect(req.chamberSelect());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        return toView(operationRepository.save(entity));
    }

    /** 删除（UNFROZEN 草稿或 DISABLED 已淘汰；被路线步骤引用禁删）。 */
    @Transactional
    public void delete(String id) {
        CimOperation op = requireOperation(id);
        if (!CimOperation.STATUS_UNFROZEN.equals(op.getStatus())
                && !CimOperation.STATUS_DISABLED.equals(op.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.operation.onlyDraftOrDisabledDeletable", op.getStatus());
        }
        if (isReferenced(op.getOpCode())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.operation.referencedNoDelete", op.getOpCode());
        }
        operationRepository.delete(op);
    }

    // ------------------------------------------------------------------ 生命周期

    /** 冻结：UNFROZEN → FROZEN（内容定稿，等待激活）。 */
    @Transactional
    public OpView freeze(String id) {
        CimOperation op = requireOperation(id);
        if (!CimOperation.STATUS_UNFROZEN.equals(op.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.operation.onlyUnfrozenFreezable", op.getStatus());
        }
        op.setStatus(CimOperation.STATUS_FROZEN);
        return toView(operationRepository.save(op));
    }

    /** 解冻：FROZEN → UNFROZEN（定稿前退回草稿）。 */
    @Transactional
    public OpView unfreeze(String id) {
        CimOperation op = requireOperation(id);
        if (!CimOperation.STATUS_FROZEN.equals(op.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.operation.onlyFrozenUnfreezable", op.getStatus());
        }
        op.setStatus(CimOperation.STATUS_UNFROZEN);
        return toView(operationRepository.save(op));
    }

    /** 激活：FROZEN → ACTIVE（同族唯一；旧 ACTIVE 自动降级 FROZEN）。 */
    @Transactional
    public OpView activate(String id) {
        CimOperation op = requireOperation(id);
        if (!CimOperation.STATUS_FROZEN.equals(op.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.operation.onlyFrozenActivatable", op.getStatus());
        }
        String family = op.getOpFamilyCode() == null ? op.getOpCode() : op.getOpFamilyCode();
        operationRepository.findByOpFamilyCodeAndDeletedFalseAndStatus(family, CimOperation.STATUS_ACTIVE)
                .filter(active -> !active.getId().equals(id))
                .ifPresent(old -> {
                    old.setStatus(CimOperation.STATUS_FROZEN);
                    operationRepository.save(old);
                });
        op.setStatus(CimOperation.STATUS_ACTIVE);
        return toView(operationRepository.save(op));
    }

    /** 禁用：FROZEN / ACTIVE → DISABLED；被引用时禁止（引用即依赖）。 */
    @Transactional
    public OpView disable(String id) {
        CimOperation op = requireOperation(id);
        if (!CimOperation.STATUS_FROZEN.equals(op.getStatus())
                && !CimOperation.STATUS_ACTIVE.equals(op.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.operation.onlyFrozenOrActiveDisablable", op.getStatus());
        }
        if (isReferenced(op.getOpCode())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.operation.referencedNoDisable", op.getOpCode());
        }
        op.setStatus(CimOperation.STATUS_DISABLED);
        return toView(operationRepository.save(op));
    }

    /**
     * 复制为同族新版本：新 UNFROZEN 版本（opCode 手填必填、同族版本号 max+1、
     * 名称 = 源名 + V{n}），全字段原样克隆。冻结/生效版本的改动一律走复制。
     */
    @Transactional
    public OpView copy(String id, String newOpCode) {
        CimOperation source = requireOperation(id);
        requireText(newOpCode, "newOpCode");
        String code = newOpCode.trim();
        if (operationRepository.findByOpCodeAndDeletedFalse(code).isPresent()) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.operation.opCodeDuplicate", code);
        }
        String family = source.getOpFamilyCode() == null ? source.getOpCode() : source.getOpFamilyCode();
        CimOperation entity = new CimOperation();
        entity.setOpCode(code);
        entity.setOpName(source.getOpName() + " V" + nextVersionNo(family));
        entity.setOpType(source.getOpType());
        entity.setTechCode(source.getTechCode());
        entity.setStatus(CimOperation.STATUS_UNFROZEN);
        entity.setOpFamilyCode(family);
        entity.setVersionNo(nextVersionNo(family));
        entity.setRecipeRef(source.getRecipeRef());
        entity.setEquipType(source.getEquipType());
        entity.setCapabilityCode(source.getCapabilityCode());
        entity.setEdcPlanCode(source.getEdcPlanCode());
        entity.setQtimeMin(source.getQtimeMin());
        entity.setQtimeMax(source.getQtimeMax());
        entity.setReworkLimit(source.getReworkLimit());
        entity.setHoldPoint(Boolean.TRUE.equals(source.getHoldPoint()));
        entity.setHoldReason(source.getHoldReason());
        entity.setBatchMode(source.getBatchMode());
        entity.setBatchSize(source.getBatchSize());
        entity.setInspectPlan(source.getInspectPlan());
        entity.setSamplingRate(source.getSamplingRate());
        entity.setReticleRef(source.getReticleRef());
        entity.setTestProgram(source.getTestProgram());
        entity.setInhibitGroup(source.getInhibitGroup());
        entity.setNpwPlan(source.getNpwPlan());
        entity.setChamberSelect(source.getChamberSelect());
        entity.setRemark(source.getRemark());
        entity.setDescription(source.getDescription());
        return toView(operationRepository.save(entity));
    }

    // ------------------------------------------------------------------ 守门工具

    /** 是否被路线步骤引用（删/禁用守门查据）。 */
    private boolean isReferenced(String opCode) {
        return stepRepository.existsByOperationCodeAndDeletedFalse(opCode);
    }

    private String requireOpType(String opType) {
        if (opType == null || !VALID_OP_TYPES.contains(opType)) {
            throw BizException.of(BizCode.PARAM_INVALID,
                    "mds.biz.operation.opTypeInvalid", String.valueOf(opType));
        }
        return opType;
    }

    private String requireTech(String techCode) {
        if (!StringUtils.hasText(techCode)) {
            return null;
        }
        if (technologyRepository.findByTechCodeAndDeletedFalse(techCode).isEmpty()) {
            throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.operation.techNotFound", techCode);
        }
        return techCode;
    }

    private String requireEquipType(String equipType) {
        if (!StringUtils.hasText(equipType)) {
            return null;
        }
        if (equipmentTypeRepository.findByTypeCodeAndDeletedFalse(equipType).isEmpty()) {
            throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.operation.equipTypeNotFound", equipType);
        }
        return equipType;
    }

    /** 能力需求校验（非空才校验；逻辑引用 cim_capability——AMAT 口径 Step 挂 Capability 匹配可加工设备）。 */
    private String requireCapability(String capabilityCode) {
        if (!StringUtils.hasText(capabilityCode)) {
            return null;
        }
        if (capabilityRepository.findByCapabilityCodeAndDeletedFalse(capabilityCode).isEmpty()) {
            throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.operation.capabilityNotFound", capabilityCode);
        }
        return capabilityCode;
    }

    private void checkQtime(Integer min, Integer max) {
        if (min != null && min <= 0) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.operation.qtimeMinPositive", min);
        }
        if (max != null && max <= 0) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.operation.qtimeMaxPositive", max);
        }
        if (min != null && max != null && min > max) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.operation.qtimeRange");
        }
    }

    /** 检验计划/抽样率仅 INSPECT 可设；抽样率 (0, 100]。 */
    private void checkInspectOnly(String opType, String inspectPlan, BigDecimal samplingRate) {
        if (!CimOperation.TYPE_INSPECT.equals(opType)) {
            if (inspectPlan != null) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.operation.inspectPlanOnly");
            }
            if (samplingRate != null) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.operation.samplingRateOnly");
            }
        } else if (samplingRate != null
                && (samplingRate.compareTo(BigDecimal.ZERO) <= 0 || samplingRate.compareTo(HUNDRED) > 0)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.operation.samplingRateRange", samplingRate);
        }
    }

    private void checkBatch(String batchMode, Integer batchSize) {
        if (batchMode != null && !VALID_BATCH_MODES.contains(batchMode)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.operation.batchModeInvalid", batchMode);
        }
        if (batchSize != null && batchSize <= 0) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.operation.batchSizePositive", batchSize);
        }
    }

    private void requireDraft(CimOperation op) {
        if (!CimOperation.STATUS_UNFROZEN.equals(op.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.operation.onlyUnfrozenEditable", op.getStatus());
        }
    }

    private Integer nextVersionNo(String family) {
        return operationRepository.findFirstByOpFamilyCodeAndDeletedFalseOrderByVersionNoDesc(family)
                .map(o -> o.getVersionNo() + 1)
                .orElse(1);
    }

    private CimOperation requireOperation(String id) {
        return operationRepository.findById(id)
                .filter(o -> !Boolean.TRUE.equals(o.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.operation.notFound", id));
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", field);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private OpView toView(CimOperation o) {
        return new OpView(o.getId(), o.getOpCode(), o.getOpName(), o.getOpType(),
                o.getTechCode(), o.getStatus(), o.getOpFamilyCode(), o.getVersionNo(),
                o.getRecipeRef(), o.getEquipType(), o.getCapabilityCode(), o.getEdcPlanCode(),
                o.getQtimeMin(), o.getQtimeMax(), o.getReworkLimit(),
                o.getHoldPoint(), o.getHoldReason(),
                o.getBatchMode(), o.getBatchSize(),
                o.getInspectPlan(), o.getSamplingRate(),
                o.getReticleRef(), o.getTestProgram(), o.getInhibitGroup(),
                o.getNpwPlan(), o.getChamberSelect(),
                o.getRemark(), o.getDescription(),
                stepRepository.existsByOperationCodeAndDeletedFalse(o.getOpCode()));
    }
}
