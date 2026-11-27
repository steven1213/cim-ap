package com.cim.mds.server.equipment;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cim.mds.server.namingrule.NamingRuleAdminService;
import com.cim.mds.server.site.CimAreaRepository;
import com.cim.mds.server.site.CimBayRepository;
import com.cim.mds.server.site.CimFabRepository;
import com.cim.mds.server.site.CimStageRepository;
import com.cim.mds.server.site.EditLockDefData;
import com.cim.mds.server.site.EditLockSupport;
import com.cim.mds.server.statemodel.StateModelAdminService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.system.dict.SysDictService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 机台（Equipment）管理——实例主数据 CRUD + 能力关联维护（v2，2026-11-24 定稿）。
 *
 * <p>一张表按机型大类 kind 分页视图（设备页=kind≠MATERIAL、搬运设备页=MATERIAL——
 * 行业 Process Tool / Non-Process Tool 两分法）。位置校验：MATERIAL 绑 FAB/AREA/BAY
 * （OHT 挂线区、Stocker 挂 Bay）；其余 kind 绑 STAGE；位置记录必须真实存在。</p>
 *
 * <p>v2 新增：</p>
 * <ul>
 *   <li>owner_user（责任人）、control_mode（SEMI E30 通信控制模式）、
 *       production_mode（生产/工程模式）——三列正交，运行控制块并列维护。</li>
 *   <li>资产四件套 vendor/model_no/serial_no/asset_no + install_date（PhysicalAsset）。</li>
 *   <li>实例约束 wafer_sizes/carrier_types/batch/standard_wph——create 时为空的字段
 *       从机型模板默认值<b>回填</b>（req 值优先，空则模板），落库后与模板解耦。</li>
 *   <li>删除级联清理机台组成员关联（成员表物理删）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EquipmentAdminService {

    /** 设备可挂的位置层（组合唯一编码层用 id 引用，Fab 全局唯一也统一走 id）。 */
    private static final Set<String> LOCATION_TYPES = Set.of("FAB", "AREA", "BAY", "STAGE");

    /** 设备状态快照字典（E10 六态）。 */
    public static final String DICT_EQUIP_STATE = "mds.equip_state";

    /** 通信控制模式字典（SEMI E30 口径）。 */
    public static final String DICT_EQUIP_CONTROL_MODE = "mds.equip_control_mode";

    /** 生产/工程模式字典。 */
    public static final String DICT_EQUIP_PRODUCTION_MODE = "mds.equip_production_mode";
    /** 晶圆尺寸（mds.wafer_size，V35 混线资格口径）。 */
    public static final String DICT_WAFER_SIZE = "mds.wafer_size";

    private final CimEquipmentRepository equipmentRepository;
    private final CimEquipmentClassRepository classRepository;
    private final CimEquipmentCapabilityRepository eqpCapRepository;
    private final CimCapabilityRepository capabilityRepository;
    private final CimEquipmentUnitRepository unitRepository;
    private final CimToolGroupMemberRepository toolGroupMemberRepository;
    private final CimEquipmentAttrRepository attrRepository;
    private final SysDictService dictService;
    /** 归属部门引用守门（IAM 组织架构 DEPT 节点，V36 起部门真源=组织架构）。 */
    private final com.cim.mds.server.admin.DirectoryClient directoryClient;
    private final CimFabRepository fabRepository;
    private final CimAreaRepository areaRepository;
    private final CimBayRepository bayRepository;
    private final CimStageRepository stageRepository;
    private final StateModelAdminService stateModelService;
    private final NamingRuleAdminService namingRuleService;
    private final com.cim.mds.server.flow.CimEquipAssignRepository assignRepository;
    private final com.cim.mds.server.edc.CimEdcEquipMapRepository edcMapRepository;

    // ------------------------------------------------------------------ 视图 / 请求

    public record EquipmentView(String id, String equipmentCode, String equipmentName,
                                String classCode, String className, String kindCode,
                                String locationType, String locationId,
                                String ownerUser, String owningDept,
                                String controlMode, String productionMode,
                                String vendor, String modelNo, String serialNo, String assetNo,
                                java.time.LocalDate installDate,
                                String waferSizes, String carrierTypes,
                                Integer batchMin, Integer batchMax,
                                java.math.BigDecimal standardWph,
                                String stateCode,
                                String remark, Boolean enabled, String description,
                                Boolean occupied, String occupiedBy) {
    }

    public record EquipmentReq(String equipmentCode, String equipmentName, String classCode,
                               String locationType, String locationId,
                               String ownerUser, String owningDept,
                               String controlMode, String productionMode,
                               String vendor, String modelNo, String serialNo, String assetNo,
                               java.time.LocalDate installDate,
                               String waferSizes, String carrierTypes,
                               Integer batchMin, Integer batchMax,
                               java.math.BigDecimal standardWph,
                               String stateCode,
                               String remark, Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<EquipmentView> list(String keyword, String classCode, String ownerUser,
                                    boolean processOnly, boolean amhsOnly) {
        Map<String, CimEquipmentClass> classes = classRepository.findAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .collect(Collectors.toMap(CimEquipmentClass::getClassCode, Function.identity()));
        return equipmentRepository.search(blankToNull(keyword), blankToNull(classCode),
                        blankToNull(ownerUser), processOnly, amhsOnly).stream()
                .map(e -> toView(e, classes.get(e.getClassCode())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EquipmentView get(String id) {
        CimEquipment e = requireEquipment(id);
        CimEquipmentClass cls = classRepository.findByClassCodeAndDeletedFalse(e.getClassCode()).orElse(null);
        return toView(e, cls);
    }

    // ------------------------------------------------------------------ 写

    /**
     * 能力 → 可加工设备解析（V21，AMAT 口径）：经 cim_equipment_capability 反查
     * 挂有该能力码的机台清单（Equipment 级，2026-11-18 定稿）。
     * 路线画布/派工侧用 Step.capabilityCode 调用本端点得到「可用于加工的设备」。
     */
    @Transactional(readOnly = true)
    public List<EquipmentView> byCapability(String capabilityCode) {
        if (!StringUtils.hasText(capabilityCode)) {
            throw new BizException(BizCode.PARAM_INVALID, "capabilityCode 必填");
        }
        if (capabilityRepository.findByCapabilityCodeAndDeletedFalse(capabilityCode).isEmpty()) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "设备能力不存在: " + capabilityCode);
        }
        Map<String, CimEquipmentClass> classes = classRepository.findAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .collect(Collectors.toMap(CimEquipmentClass::getClassCode, Function.identity()));
        return eqpCapRepository.findByCapabilityCodeAndDeletedFalse(capabilityCode).stream()
                .map(rel -> equipmentRepository.findById(rel.getEquipmentId())
                        .filter(e -> !Boolean.TRUE.equals(e.getDeleted())))
                .flatMap(java.util.Optional::stream)
                .map(e -> toView(e, classes.get(e.getClassCode())))
                .toList();
    }

    /**
     * 新建机台：约束字段（片径/载具/batch/产能）req 为空时从机型模板默认值回填——
     * 「派生实例」动线的后端落地；req 显式给值则覆盖模板。
     */
    @Transactional
    public EquipmentView create(EquipmentReq req) {
        requireText(req.equipmentName(), "equipmentName");
        CimEquipmentClass cls = requireClassByCode(req.classCode());
        validateLocation(cls, req.locationType(), req.locationId());
        // 命名规则守门：ctx 带全位置链 + kind/class（EQUIPMENT 变量白名单最全）
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_EQUIPMENT,
                req.equipmentCode(), namingCtx(cls, req));
        if (equipmentRepository.existsByEquipmentCodeAndDeletedFalse(code)) {
            throw new BizException(BizCode.DATA_DUPLICATE, "设备编码已存在: " + code);
        }
        CimEquipment entity = new CimEquipment();
        entity.setEquipmentCode(code);
        apply(entity, req, cls);
        entity.setControlMode(requireDictItem(DICT_EQUIP_CONTROL_MODE, req.controlMode(), "controlMode"));
        entity.setProductionMode(requireProductionMode(req.productionMode()));
        entity.setStateCode(requireDictItem(DICT_EQUIP_STATE, req.stateCode(), "stateCode"));
        entity.setOwningDept(directoryClient.requireDeptCode(req.owningDept()));
        return toView(equipmentRepository.save(entity), cls);
    }

    @Transactional
    public EquipmentView update(String id, EquipmentReq req) {
        CimEquipment entity = requireEquipment(id);
        requireText(req.equipmentName(), "equipmentName");
        assertNotLockedByOther(entity);
        // equipmentCode 全局唯一主标识：建后不可改
        CimEquipmentClass cls = requireClassByCode(req.classCode());
        validateLocation(cls, req.locationType(), req.locationId());
        String newState = requireDictItem(DICT_EQUIP_STATE, req.stateCode(), "stateCode");
        // 状态转换守门（E30 口径）：仅在状态实际变化时校验（新建无旧态不校验）
        stateModelService.validateTransition(StateModelAdminService.MODEL_EQUIPMENT,
                entity.getStateCode(), newState);
        entity.setEquipmentName(req.equipmentName().trim());
        entity.setClassCode(req.classCode());
        entity.setLocationType(req.locationType());
        entity.setLocationId(req.locationId());
        entity.setOwnerUser(blankToNull(req.ownerUser()));
        entity.setOwningDept(directoryClient.requireDeptCode(req.owningDept()));
        entity.setControlMode(requireDictItem(DICT_EQUIP_CONTROL_MODE, req.controlMode(), "controlMode"));
        entity.setProductionMode(requireProductionMode(req.productionMode()));
        entity.setVendor(blankToNull(req.vendor()));
        entity.setModelNo(blankToNull(req.modelNo()));
        entity.setSerialNo(blankToNull(req.serialNo()));
        entity.setAssetNo(blankToNull(req.assetNo()));
        entity.setInstallDate(req.installDate());
        entity.setWaferSizes(normalizeCsvStatic(req.waferSizes(), 64));
        entity.setCarrierTypes(normalizeCsvStatic(req.carrierTypes(), 128));
        entity.setBatchMin(req.batchMin());
        entity.setBatchMax(req.batchMax());
        entity.setStandardWph(req.standardWph());
        entity.setStateCode(newState);
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
        clearLock(entity);
        return toView(equipmentRepository.save(entity), cls);
    }

    @Transactional
    public void delete(String id) {
        CimEquipment entity = requireEquipment(id);
        assertNotLockedByOther(entity);
        // 反向引用守门（与工序 isReferenced 同口径）：候选池 EQUIP 指派 / EDC 设备映射任一存活即拒删
        if (assignRepository.existsByAssignTypeAndAssignRefAndDeletedFalse(
                com.cim.mds.server.flow.CimEquipAssign.TYPE_EQUIP, entity.getEquipmentCode())
                || !edcMapRepository.findByEquipmentCodeAndDeletedFalse(entity.getEquipmentCode()).isEmpty()) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.equipment.referencedNoDelete", entity.getEquipmentCode());
        }
        // 子单元随设备物理级联（软删会撞 uk_cim_equipment_unit）；能力关联/组成员/自定义属性同口径
        unitRepository.deleteByEquipmentId(id);
        eqpCapRepository.deleteByEquipmentId(id);
        toolGroupMemberRepository.deleteByEquipmentId(id);
        attrRepository.findByEquipmentIdAndDeletedFalse(id).forEach(attrRepository::delete);
        entity.setDeleted(Boolean.TRUE);
        equipmentRepository.save(entity);
    }

    // ------------------------------------------------------------------ 能力关联

    /** 设备↔能力关联条目（V35：混线资格可按尺寸区分，空尺寸 = 全尺寸通用）。 */
    public record EqpCapItem(String capabilityCode, String waferSize) {
    }

    @Transactional
    public List<EqpCapItem> capabilities(String equipmentId) {
        requireEquipment(equipmentId);
        return eqpCapRepository.findByEquipmentIdAndDeletedFalse(equipmentId).stream()
                .map(l -> new EqpCapItem(l.getCapabilityCode(), l.getWaferSize()))
                .sorted(java.util.Comparator.comparing(EqpCapItem::capabilityCode)
                        .thenComparing(EqpCapItem::waferSize,
                                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .toList();
    }

    /** 全量替换设备能力关联（前端勾选提交，条目含可选适用尺寸）。 */
    @Transactional
    public List<EqpCapItem> setCapabilities(String equipmentId, List<EqpCapItem> items) {
        CimEquipment equipment = requireEquipment(equipmentId);
        List<EqpCapItem> normalized = items == null ? List.of() : items.stream()
                .filter(i -> i != null && i.capabilityCode() != null && !i.capabilityCode().isBlank())
                .map(i -> new EqpCapItem(i.capabilityCode().trim(),
                        requireDictItem(DICT_WAFER_SIZE, i.waferSize(), "waferSize")))
                .distinct()
                .toList();
        for (EqpCapItem item : normalized) {
            capabilityRepository.findByCapabilityCodeAndDeletedFalse(item.capabilityCode())
                    .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND,
                            "设备能力不存在: " + item.capabilityCode()));
        }
        // 全量替换 = 物理删旧 + 插新（软删会撞 uk_cim_eqp_cap：Hibernate 先 flush insert 后 update）
        eqpCapRepository.deleteByEquipmentId(equipment.getId());
        for (EqpCapItem item : normalized) {
            CimEquipmentCapability link = new CimEquipmentCapability();
            link.setEquipmentId(equipment.getId());
            link.setCapabilityCode(item.capabilityCode());
            link.setWaferSize(item.waferSize());
            eqpCapRepository.save(link);
        }
        return capabilities(equipment.getId());
    }

    // ------------------------------------------------------------------ 内部

    private CimEquipment requireEquipment(String id) {
        return equipmentRepository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + id));
    }

    /** 子单元域复用的包内入口（含存在性校验，不含锁守门——锁由子单元域按操作判）。 */
    CimEquipment requireForUnit(String id) {
        return requireEquipment(id);
    }

    private CimEquipmentClass requireClassByCode(String classCode) {
        requireText(classCode, "classCode");
        return classRepository.findByClassCodeAndDeletedFalse(classCode.trim())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "机型不存在: " + classCode));
    }

    /** 生产/工程模式：空 → 默认 PRODUCTION；非空须在字典内。 */
    private String requireProductionMode(String value) {
        if (value == null || value.isBlank()) {
            return "PRODUCTION";
        }
        return requireDictItem(DICT_EQUIP_PRODUCTION_MODE, value, "productionMode");
    }

    /**
     * 命名规则上下文：位置链码（按 locationId 上溯到 site）+ kind/class
     * （EQUIPMENT 变量白名单 {site}{fab}{area}{bay}{stage}{kind}{type}{seq}，type 段取机型编码）。
     */
    private Map<String, String> namingCtx(CimEquipmentClass cls, EquipmentReq req) {
        Map<String, String> ctx = new java.util.HashMap<>();
        ctx.put("kind", cls.getKindCode());
        ctx.put("type", cls.getClassCode());
        if (req.locationId() != null && !req.locationId().isBlank()) {
            switch (req.locationType()) {
                case "FAB" -> fabRepository.findById(req.locationId())
                        .filter(f -> !Boolean.TRUE.equals(f.getDeleted()))
                        .ifPresent(f -> ctx.put("fab", f.getFabCode()));
                case "AREA" -> areaRepository.findById(req.locationId())
                        .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                        .ifPresent(a -> {
                            ctx.put("area", a.getAreaCode());
                            ctx.put("fab", a.getFabCode());
                        });
                case "BAY" -> bayRepository.findById(req.locationId())
                        .filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                        .ifPresent(b -> {
                            ctx.put("bay", b.getBayCode());
                            ctx.put("area", b.getAreaCode());
                            ctx.put("fab", b.getFabCode());
                        });
                case "STAGE" -> stageRepository.findById(req.locationId())
                        .filter(s -> !Boolean.TRUE.equals(s.getDeleted()))
                        .ifPresent(s -> {
                            ctx.put("stage", s.getStageCode());
                            ctx.put("bay", s.getBayCode());
                            ctx.put("fab", s.getFabCode());
                        });
                default -> { }
            }
        }
        return ctx;
    }

    /** 位置校验：类型合法 + kind 规则（MATERIAL→FAB/AREA/BAY，其余→STAGE）+ 记录真实存在。 */
    private void validateLocation(CimEquipmentClass cls, String locationType, String locationId) {
        requireText(locationType, "locationType");
        requireText(locationId, "locationId");
        if (!LOCATION_TYPES.contains(locationType)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "locationType 必须是 " + LOCATION_TYPES + " 之一: " + locationType);
        }
        boolean material = "MATERIAL".equals(cls.getKindCode());
        if (material && "STAGE".equals(locationType)) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "搬运/仓储设备（" + cls.getClassCode() + "，MATERIAL 类）不能绑定位机台位(STAGE)，请选 FAB/AREA/BAY");
        }
        if (!material && !"STAGE".equals(locationType)) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "非搬运设备（" + cls.getKindCode() + " 类）必须绑定位机台位(STAGE)");
        }
        boolean exists = switch (locationType) {
            case "FAB" -> fabRepository.findById(locationId).filter(f -> !Boolean.TRUE.equals(f.getDeleted())).isPresent();
            case "AREA" -> areaRepository.findById(locationId).filter(a -> !Boolean.TRUE.equals(a.getDeleted())).isPresent();
            case "BAY" -> bayRepository.findById(locationId).filter(b -> !Boolean.TRUE.equals(b.getDeleted())).isPresent();
            case "STAGE" -> stageRepository.findById(locationId).filter(s -> !Boolean.TRUE.equals(s.getDeleted())).isPresent();
            default -> false;
        };
        if (!exists) {
            throw new BizException(BizCode.DATA_NOT_FOUND,
                    "位置记录不存在: " + locationType + "/" + locationId);
        }
    }

    /**
     * create 字段搬运：约束字段 req 为空从模板默认回填（派生语义）。
     * update 的字段覆盖在 update() 里显式展开（含 controlMode/productionMode 字典校验）。
     */
    private static void apply(CimEquipment entity, EquipmentReq req, CimEquipmentClass cls) {
        entity.setEquipmentCode(entity.getEquipmentCode() == null
                ? req.equipmentCode().trim() : entity.getEquipmentCode());
        entity.setEquipmentName(req.equipmentName().trim());
        entity.setClassCode(req.classCode());
        entity.setLocationType(req.locationType());
        entity.setLocationId(req.locationId());
        entity.setOwnerUser(blankToNull(req.ownerUser()));
        entity.setWaferSizes(firstNonBlank(normalizeCsvStatic(req.waferSizes(), 64),
                cls.getDefaultWaferSizes()));
        entity.setCarrierTypes(firstNonBlank(normalizeCsvStatic(req.carrierTypes(), 128),
                cls.getDefaultCarrierTypes()));
        entity.setBatchMin(req.batchMin() != null ? req.batchMin() : cls.getDefaultBatchMin());
        entity.setBatchMax(req.batchMax() != null ? req.batchMax() : cls.getDefaultBatchMax());
        entity.setStandardWph(req.standardWph() != null ? req.standardWph() : cls.getDefaultStandardWph());
        entity.setVendor(blankToNull(req.vendor()));
        entity.setModelNo(blankToNull(req.modelNo()));
        entity.setSerialNo(blankToNull(req.serialNo()));
        entity.setAssetNo(blankToNull(req.assetNo()));
        entity.setInstallDate(req.installDate());
        // stateCode/controlMode/productionMode 在 create 里走实例方法（字典校验）
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    /** 字典值守门：非空值必须是该字典的启用 itemKey（与 ProductAdminService 同口径）。 */
    private String requireDictItem(String dictCode, String value, String field) {
        String v = value == null || value.isBlank() ? null : value.trim();
        if (v == null) {
            return null;
        }
        boolean hit = dictService.items(dictCode).stream().anyMatch(i -> v.equals(i.getItemKey()));
        if (!hit) {
            throw new BizException(BizCode.PARAM_INVALID,
                    field + " 不在数据字典 " + dictCode + " 内: " + v);
        }
        return v;
    }

    private static EquipmentView toView(CimEquipment e, CimEquipmentClass cls) {
        return new EquipmentView(e.getId(), e.getEquipmentCode(), e.getEquipmentName(),
                e.getClassCode(),
                cls == null ? null : cls.getClassName(),
                cls == null ? null : cls.getKindCode(),
                e.getLocationType(), e.getLocationId(),
                e.getOwnerUser(), e.getOwningDept(), e.getControlMode(), e.getProductionMode(),
                e.getVendor(), e.getModelNo(), e.getSerialNo(), e.getAssetNo(),
                e.getInstallDate(),
                e.getWaferSizes(), e.getCarrierTypes(), e.getBatchMin(), e.getBatchMax(),
                e.getStandardWph(),
                e.getStateCode(),
                e.getRemark(), e.getEnabled(), e.getDescription(),
                e.getOccupied(), e.getOccupiedBy());
    }

    private static String normalizeCsvStatic(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String csv = String.join(",", java.util.Arrays.stream(raw.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        if (csv.length() > maxLen) {
            throw new BizException(BizCode.PARAM_INVALID, "字段过长（≤" + maxLen + " 字符）: " + csv);
        }
        return csv;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
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
