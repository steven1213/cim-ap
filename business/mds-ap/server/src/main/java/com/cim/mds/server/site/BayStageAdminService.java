package com.cim.mds.server.site;

import com.cim.mds.server.namingrule.NamingRuleAdminService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 湾 / 设备位主数据服务（Bay / Stage，层级第四、五层）。
 *
 * <p>口径与 {@link SiteAdminService} 一致：对外走 {@code *View}；逻辑删除 + 下游引用保护；
 * 编辑锁「保存时校验 + 同事务清锁」（抢锁/释放在 {@link LocationLockController}）。</p>
 *
 * <p>归属校验（§8.1 / §8.3 守门）：<b>新父必须存在且启用</b>；Bay 换 Area、Stage 换 Bay 时
 * 若有下游子层则拒绝搬迁。Bay 的 {@code areaCode} 与 Stage 的 {@code (fabCode, bayCode)}
 * 必须与直接父层一致——Stage 不存 areaCode（经 Bay 推导，单一来源）。</p>
 */
@Service
@RequiredArgsConstructor
public class BayStageAdminService {

    private final CimFabRepository fabRepository;
    private final CimAreaRepository areaRepository;
    private final CimBayRepository bayRepository;
    private final CimStageRepository stageRepository;
    private final NamingRuleAdminService namingRuleService;

    // ------------------------------------------------------------------ 视图

    public record BayView(String id, String bayCode, String fabCode, String fabName,
                          String areaCode, String areaName, String bayName,
                          String remark, Boolean enabled, String description,
                          Boolean occupied, String occupiedBy) {
    }

    public record StageView(String id, String stageCode, String fabCode, String bayCode, String bayName,
                            String stageName, String remark, Boolean enabled, String description,
                            Boolean occupied, String occupiedBy) {
    }

    // ------------------------------------------------------------------ 请求

    public record BayReq(String bayCode, String fabCode, String areaCode, String bayName,
                         String remark, Boolean enabled, String description) {
    }

    public record StageReq(String stageCode, String fabCode, String bayCode, String stageName,
                           String remark, Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ Bay

    @Transactional(readOnly = true)
    public List<BayView> listBays(String fabCode, String areaCode, String keyword) {
        Map<String, String> fabNames = fabNameMap();
        Map<String, String> areaNames = areaNameMap();
        return bayRepository.search(blankToNull(fabCode), blankToNull(areaCode), blankToNull(keyword)).stream()
                .map(b -> new BayView(b.getId(), b.getBayCode(), b.getFabCode(),
                        fabNames.get(b.getFabCode()), b.getAreaCode(),
                        areaNames.get(b.getFabCode() + "/" + b.getAreaCode()),
                        b.getBayName(), b.getRemark(), b.getEnabled(), b.getDescription(),
                        b.getOccupied(), b.getOccupiedBy()))
                .toList();
    }

    @Transactional
    public BayView createBay(BayReq req) {
        requireText(req.fabCode(), "fabCode");
        requireText(req.areaCode(), "areaCode");
        requireText(req.bayName(), "bayName");
        requireActiveFabByCode(req.fabCode());
        requireActiveAreaInFab(req.fabCode(), req.areaCode());
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_BAY, req.bayCode(),
                Map.of("fab", req.fabCode().trim(), "area", req.areaCode().trim()));
        if (bayRepository.findByFabCodeAndBayCodeAndDeletedFalse(req.fabCode().trim(), code)
                .isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "该 Fab 下 Bay 编码已存在: " + code);
        }
        CimBay entity = new CimBay();
        entity.setBayCode(code);
        applyBay(entity, req);
        return toView(bayRepository.save(entity));
    }

    @Transactional
    public BayView updateBay(String id, BayReq req) {
        CimBay entity = requireBay(id);
        requireText(req.bayName(), "bayName");
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        // 编码不可改；换 Area 属搬迁：①新 Area 必须启用且仍属本 Fab②Bay 下仍有 Stage 时禁止
        if (req.areaCode() != null && !req.areaCode().isBlank()
                && !req.areaCode().trim().equals(entity.getAreaCode())) {
            requireActiveAreaInFab(entity.getFabCode(), req.areaCode());
            long stages = stageRepository.countByFabCodeAndBayCodeAndDeletedFalse(
                    entity.getFabCode(), entity.getBayCode());
            if (stages > 0) {
                throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                        "Bay 下仍有 " + stages + " 个 Stage，先清空下游再换归属 Area");
            }
            entity.setAreaCode(req.areaCode().trim());
        }
        applyBay(entity, req);
        clearLock(entity);
        return toView(bayRepository.save(entity));
    }

    @Transactional
    public void deleteBay(String id) {
        CimBay entity = requireBay(id);
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        long stages = stageRepository.countByFabCodeAndBayCodeAndDeletedFalse(
                entity.getFabCode(), entity.getBayCode());
        if (stages > 0) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "Bay 下仍有 " + stages + " 个 Stage，先删除或改归属后再删湾");
        }
        entity.setDeleted(Boolean.TRUE);
        bayRepository.save(entity);
    }

    // ------------------------------------------------------------------ Stage

    @Transactional(readOnly = true)
    public List<StageView> listStages(String fabCode, String bayCode, String keyword) {
        Map<String, String> bayNames = bayNameMap();
        return stageRepository.search(blankToNull(fabCode), blankToNull(bayCode), blankToNull(keyword)).stream()
                .map(s -> new StageView(s.getId(), s.getStageCode(), s.getFabCode(), s.getBayCode(),
                        bayNames.get(s.getFabCode() + "/" + s.getBayCode()), s.getStageName(), s.getRemark(),
                        s.getEnabled(), s.getDescription(), s.getOccupied(), s.getOccupiedBy()))
                .toList();
    }

    @Transactional
    public StageView createStage(StageReq req) {
        requireText(req.fabCode(), "fabCode");
        requireText(req.bayCode(), "bayCode");
        requireText(req.stageName(), "stageName");
        requireActiveBayInFab(req.fabCode(), req.bayCode());
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_STAGE, req.stageCode(),
                Map.of("fab", req.fabCode().trim(), "bay", req.bayCode().trim()));
        if (stageRepository.findByFabCodeAndBayCodeAndStageCodeAndDeletedFalse(
                req.fabCode().trim(), req.bayCode().trim(), code).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "该 Bay 下 Stage 编码已存在: " + code);
        }
        CimStage entity = new CimStage();
        entity.setStageCode(code);
        applyStage(entity, req);
        return toView(stageRepository.save(entity));
    }

    @Transactional
    public StageView updateStage(String id, StageReq req) {
        CimStage entity = requireStage(id);
        requireText(req.stageName(), "stageName");
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        // 换 Bay 属搬迁：新 Bay 必须启用且属本 Fab。Stage 是位置树叶子——
        // 将来设备以 location_id 落 Stage 后，搬迁守门需叠加设备占用检查（设备域接入时补）。
        if (req.bayCode() != null && !req.bayCode().isBlank()
                && !req.bayCode().trim().equals(entity.getBayCode())) {
            CimBay bay = requireActiveBayInFab(entity.getFabCode(), req.bayCode());
            entity.setBayCode(bay.getBayCode());
        }
        applyStage(entity, req);
        clearLock(entity);
        return toView(stageRepository.save(entity));
    }

    @Transactional
    public void deleteStage(String id) {
        CimStage entity = requireStage(id);
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        // 位置树叶子：暂无下游；设备域接入后需先校验 location_id 占用
        entity.setDeleted(Boolean.TRUE);
        stageRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private CimBay requireBay(String id) {
        return bayRepository.findById(id)
                .filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "Bay 不存在: " + id));
    }

    private CimStage requireStage(String id) {
        return stageRepository.findById(id)
                .filter(s -> !Boolean.TRUE.equals(s.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "Stage 不存在: " + id));
    }

    private void requireActiveFabByCode(String fabCode) {
        CimFab fab = fabRepository.findByFabCodeAndDeletedFalse(fabCode.trim())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "归属 Fab 不存在: " + fabCode));
        if (!Boolean.TRUE.equals(fab.getEnabled())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "归属 Fab 已停用: " + fabCode);
        }
    }

    /** Area 必须存在、启用、且确实属于该 Fab（area_code 仅 Fab 内唯一，跨 Fab 同名合法）。 */
    private void requireActiveAreaInFab(String fabCode, String areaCode) {
        CimArea area = areaRepository
                .findByFabCodeAndAreaCodeAndDeletedFalse(fabCode.trim(), areaCode.trim())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND,
                        "该 Fab 下 Area 不存在: " + areaCode));
        if (!Boolean.TRUE.equals(area.getEnabled())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "归属 Area 已停用: " + areaCode);
        }
    }

    /** Bay 必须存在、启用、且确实属于该 Fab（bay_code 仅 Fab 内唯一）。 */
    private CimBay requireActiveBayInFab(String fabCode, String bayCode) {
        CimBay bay = bayRepository.findByFabCodeAndBayCodeAndDeletedFalse(fabCode.trim(), bayCode.trim())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND,
                        "该 Fab 下 Bay 不存在: " + bayCode));
        if (!Boolean.TRUE.equals(bay.getEnabled())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "归属 Bay 已停用: " + bayCode);
        }
        return bay;
    }

    /** 编辑锁守门（口径同 SiteAdminService）：被他人占用即拒绝。 */
    private static void assertNotLockedByOther(Boolean occupied, String occupiedBy) {
        if (Boolean.TRUE.equals(occupied) && occupiedBy != null
                && !occupiedBy.equals(EditLockSupport.currentUser())) {
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

    private Map<String, String> fabNameMap() {
        return fabRepository.search(null, null).stream()
                .collect(Collectors.toMap(CimFab::getFabCode, CimFab::getFabName, (a, b) -> a, HashMap::new));
    }

    private Map<String, String> areaNameMap() {
        return areaRepository.search(null, null).stream()
                .collect(Collectors.toMap(a -> a.getFabCode() + "/" + a.getAreaCode(),
                        CimArea::getAreaName, (a, b) -> a, HashMap::new));
    }

    private Map<String, String> bayNameMap() {
        return bayRepository.search(null, null, null).stream()
                .collect(Collectors.toMap(b -> b.getFabCode() + "/" + b.getBayCode(),
                        CimBay::getBayName, (a, b) -> a, HashMap::new));
    }

    private static void applyBay(CimBay entity, BayReq req) {
        entity.setBayCode(entity.getBayCode() == null ? req.bayCode().trim() : entity.getBayCode());
        entity.setFabCode(entity.getFabCode() == null ? req.fabCode().trim() : entity.getFabCode());
        entity.setAreaCode(entity.getAreaCode() == null ? req.areaCode().trim() : entity.getAreaCode());
        entity.setBayName(req.bayName().trim());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private static void applyStage(CimStage entity, StageReq req) {
        entity.setStageCode(entity.getStageCode() == null ? req.stageCode().trim() : entity.getStageCode());
        entity.setFabCode(entity.getFabCode() == null ? req.fabCode().trim() : entity.getFabCode());
        entity.setBayCode(entity.getBayCode() == null ? req.bayCode().trim() : entity.getBayCode());
        entity.setStageName(req.stageName().trim());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    /** 单条转视图：联表名按需单查（写入路径仅单条，避免为一条记录刷全表映射）。 */
    private BayView toView(CimBay b) {
        String fabName = fabRepository.findByFabCodeAndDeletedFalse(b.getFabCode())
                .map(CimFab::getFabName).orElse(null);
        String areaName = areaRepository
                .findByFabCodeAndAreaCodeAndDeletedFalse(b.getFabCode(), b.getAreaCode())
                .map(CimArea::getAreaName).orElse(null);
        return new BayView(b.getId(), b.getBayCode(), b.getFabCode(), fabName,
                b.getAreaCode(), areaName, b.getBayName(), b.getRemark(), b.getEnabled(),
                b.getDescription(), b.getOccupied(), b.getOccupiedBy());
    }

    private StageView toView(CimStage s) {
        String bayName = bayRepository.findByFabCodeAndBayCodeAndDeletedFalse(s.getFabCode(), s.getBayCode())
                .map(CimBay::getBayName).orElse(null);
        return new StageView(s.getId(), s.getStageCode(), s.getFabCode(), s.getBayCode(), bayName,
                s.getStageName(), s.getRemark(), s.getEnabled(), s.getDescription(),
                s.getOccupied(), s.getOccupiedBy());
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static void requireText(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
    }
}
