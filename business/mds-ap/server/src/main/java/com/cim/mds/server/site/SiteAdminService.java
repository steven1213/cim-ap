package com.cim.mds.server.site;

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
 * 场地主数据服务（Site / Fab / Area）。
 *
 * <p><b>为什么不返回实体</b>：对外契约统一走 {@code *View}（含派生联表名 {@code siteName} /
 * {@code fabName}），前端表格直接消费；实体带 {@code version}/{@code trxId} 等内部列，
 * 暴露出去等于把持久化细节变成 API 契约。</p>
 *
 * <p><b>删除是逻辑删除 + 引用保护</b>：上层被下游子层引用时拒绝删除（{@code OPERATION_NOT_ALLOWED}），
 * 避免留下悬空的 {@code site_code}/{@code fab_code}/{@code area_code}——本项目跨域用逻辑引用而非 FK，
 * 数据库不会替我们挡这一刀。引用链：Site ← Fab ← Area ← Bay（{@link CimBay}）← Stage。</p>
 *
 * <p><b>编辑锁（location-design.md §8.1）</b>：改/删前判 {@code is_occupied}——被他人占用即拒绝；
 * 保存成功在同一事务清锁。抢锁/释放在 {@link LocationLockController}，此处只做「保存时校验 + 清锁」。</p>
 */
@Service
@RequiredArgsConstructor
public class SiteAdminService {

    private final CimSiteRepository siteRepository;
    private final CimFabRepository fabRepository;
    private final CimAreaRepository areaRepository;
    private final CimBayRepository bayRepository;

    // ------------------------------------------------------------------ 视图

    public record SiteView(String id, String siteCode, String siteName, String address,
                           String contactPerson, String contactPhone, String remark,
                           Boolean enabled, String description,
                           Boolean occupied, String occupiedBy) {
    }

    public record FabView(String id, String fabCode, String siteCode, String siteName, String fabName,
                          String fabType, String amhsType, String cleanGrade, String remark,
                          Boolean enabled, String description,
                          Boolean occupied, String occupiedBy) {
    }

    public record AreaView(String id, String areaCode, String fabCode, String fabName, String areaName,
                           String areaType, String remark, Boolean enabled, String description,
                           Boolean occupied, String occupiedBy) {
    }

    // ------------------------------------------------------------------ 请求

    public record SiteReq(String siteCode, String siteName, String address, String contactPerson,
                          String contactPhone, String remark, Boolean enabled, String description) {
    }

    public record FabReq(String fabCode, String siteCode, String fabName, String fabType, String amhsType,
                         String cleanGrade, String remark, Boolean enabled, String description) {
    }

    public record AreaReq(String areaCode, String fabCode, String areaName, String areaType,
                          String remark, Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ Site

    @Transactional(readOnly = true)
    public List<SiteView> listSites(String keyword) {
        return siteRepository.search(blankToNull(keyword)).stream().map(SiteAdminService::toView).toList();
    }

    @Transactional
    public SiteView createSite(SiteReq req) {
        requireText(req.siteCode(), "siteCode");
        requireText(req.siteName(), "siteName");
        if (siteRepository.findBySiteCodeAndDeletedFalse(req.siteCode().trim()).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "基地编码已存在: " + req.siteCode());
        }
        CimSite entity = new CimSite();
        applySite(entity, req);
        return toView(siteRepository.save(entity));
    }

    @Transactional
    public SiteView updateSite(String id, SiteReq req) {
        CimSite entity = requireSite(id);
        requireText(req.siteName(), "siteName");
        // 编码是主标识：不允许改（下游 site_code 引用会断），故忽略请求里的 siteCode
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        applySite(entity, req);
        clearLock(entity);
        return toView(siteRepository.save(entity));
    }

    @Transactional
    public void deleteSite(String id) {
        CimSite entity = requireSite(id);
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        long fabs = fabRepository.countBySiteCodeAndDeletedFalse(entity.getSiteCode());
        if (fabs > 0) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "基地下仍有 " + fabs + " 个 Fab，先删除或改归属后再删基地");
        }
        entity.setDeleted(Boolean.TRUE);
        siteRepository.save(entity);
    }

    // ------------------------------------------------------------------ Fab

    @Transactional(readOnly = true)
    public List<FabView> listFabs(String siteCode, String keyword) {
        Map<String, String> siteNames = siteNameMap();
        return fabRepository.search(blankToNull(siteCode), blankToNull(keyword)).stream()
                .map(f -> new FabView(f.getId(), f.getFabCode(), f.getSiteCode(),
                        siteNames.get(f.getSiteCode()), f.getFabName(), f.getFabType(), f.getAmhsType(),
                        f.getCleanGrade(), f.getRemark(), f.getEnabled(), f.getDescription(),
                        f.getOccupied(), f.getOccupiedBy()))
                .toList();
    }

    @Transactional
    public FabView createFab(FabReq req) {
        requireText(req.fabCode(), "fabCode");
        requireText(req.fabName(), "fabName");
        requireText(req.siteCode(), "siteCode");
        requireActiveSiteByCode(req.siteCode());
        if (fabRepository.findByFabCodeAndDeletedFalse(req.fabCode().trim()).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "Fab 编码已存在: " + req.fabCode());
        }
        CimFab entity = new CimFab();
        applyFab(entity, req);
        return toView(siteRepository, fabRepository.save(entity));
    }

    @Transactional
    public FabView updateFab(String id, FabReq req) {
        CimFab entity = requireFab(id);
        requireText(req.fabName(), "fabName");
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        // 编码不可改；归属可改，但①新基地必须存在且启用②Fab 下仍有 Area 时禁止搬迁（下游悬空）
        if (req.siteCode() != null && !req.siteCode().isBlank()
                && !req.siteCode().trim().equals(entity.getSiteCode())) {
            requireActiveSiteByCode(req.siteCode());
            long areas = areaRepository.countByFabCodeAndDeletedFalse(entity.getFabCode());
            if (areas > 0) {
                throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                        "Fab 下仍有 " + areas + " 个 Area，先清空下游再换归属基地");
            }
            entity.setSiteCode(req.siteCode().trim());
        }
        applyFab(entity, req);
        clearLock(entity);
        return toView(siteRepository, fabRepository.save(entity));
    }

    @Transactional
    public void deleteFab(String id) {
        CimFab entity = requireFab(id);
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        long areas = areaRepository.countByFabCodeAndDeletedFalse(entity.getFabCode());
        if (areas > 0) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "Fab 下仍有 " + areas + " 个 Area，先删除或改归属后再删 Fab");
        }
        entity.setDeleted(Boolean.TRUE);
        fabRepository.save(entity);
    }

    // ------------------------------------------------------------------ Area

    @Transactional(readOnly = true)
    public List<AreaView> listAreas(String fabCode, String keyword) {
        Map<String, String> fabNames = fabNameMap();
        return areaRepository.search(blankToNull(fabCode), blankToNull(keyword)).stream()
                .map(a -> new AreaView(a.getId(), a.getAreaCode(), a.getFabCode(),
                        fabNames.get(a.getFabCode()), a.getAreaName(), a.getAreaType(),
                        a.getRemark(), a.getEnabled(), a.getDescription(),
                        a.getOccupied(), a.getOccupiedBy()))
                .toList();
    }

    @Transactional
    public AreaView createArea(AreaReq req) {
        requireText(req.areaCode(), "areaCode");
        requireText(req.areaName(), "areaName");
        requireText(req.fabCode(), "fabCode");
        requireActiveFabByCode(req.fabCode());
        if (areaRepository.findByFabCodeAndAreaCodeAndDeletedFalse(req.fabCode().trim(), req.areaCode().trim())
                .isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "该 Fab 下 Area 编码已存在: " + req.areaCode());
        }
        CimArea entity = new CimArea();
        applyArea(entity, req);
        return toView(fabNameMap(), areaRepository.save(entity));
    }

    @Transactional
    public AreaView updateArea(String id, AreaReq req) {
        CimArea entity = requireArea(id);
        requireText(req.areaName(), "areaName");
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        // 归属可改，但①新 Fab 必须存在且启用②Area 下仍有 Bay 时禁止搬迁
        if (req.fabCode() != null && !req.fabCode().isBlank()
                && !req.fabCode().trim().equals(entity.getFabCode())) {
            requireActiveFabByCode(req.fabCode());
            long bays = bayRepository.countByFabCodeAndAreaCodeAndDeletedFalse(
                    entity.getFabCode(), entity.getAreaCode());
            if (bays > 0) {
                throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                        "Area 下仍有 " + bays + " 个 Bay，先清空下游再换归属 Fab");
            }
            entity.setFabCode(req.fabCode().trim());
        }
        applyArea(entity, req);
        clearLock(entity);
        return toView(fabNameMap(), areaRepository.save(entity));
    }

    @Transactional
    public void deleteArea(String id) {
        CimArea entity = requireArea(id);
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        // 引用保护（V1 版遗漏的最大洞）：Area 是 Bay 的直接父层，裸删会留下悬空 area_code
        long bays = bayRepository.countByFabCodeAndAreaCodeAndDeletedFalse(
                entity.getFabCode(), entity.getAreaCode());
        if (bays > 0) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "Area 下仍有 " + bays + " 个 Bay，先删除或改归属后再删区域");
        }
        entity.setDeleted(Boolean.TRUE);
        areaRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private CimSite requireSite(String id) {
        return siteRepository.findById(id)
                .filter(s -> !Boolean.TRUE.equals(s.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "基地不存在: " + id));
    }

    private CimFab requireFab(String id) {
        return fabRepository.findById(id)
                .filter(f -> !Boolean.TRUE.equals(f.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "Fab 不存在: " + id));
    }

    private CimArea requireArea(String id) {
        return areaRepository.findById(id)
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "Area 不存在: " + id));
    }

    /**
     * 归属校验必须验「存在 + 启用」：往已停用的基地/Fab 下挂新子层或搬迁，
     * 等于绕过停用语义（V1 只查存在，是第二个洞）。
     */
    private void requireActiveSiteByCode(String siteCode) {
        CimSite site = siteRepository.findBySiteCodeAndDeletedFalse(siteCode.trim())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "归属基地不存在: " + siteCode));
        if (!Boolean.TRUE.equals(site.getEnabled())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "归属基地已停用: " + siteCode);
        }
    }

    private void requireActiveFabByCode(String fabCode) {
        CimFab fab = fabRepository.findByFabCodeAndDeletedFalse(fabCode.trim())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "归属 Fab 不存在: " + fabCode));
        if (!Boolean.TRUE.equals(fab.getEnabled())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "归属 Fab 已停用: " + fabCode);
        }
    }

    /**
     * 编辑锁守门：被<b>他人</b>占用即拒绝（本人放行——抢锁/续锁在 LockController，
     * 这里只挡「锁被抢后旧窗口保存」的竞态）。{@code @Version} 乐观锁是第二道保险。
     */
    private void assertNotLockedByOther(boolean occupied, String occupiedBy) {
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

    private Map<String, String> siteNameMap() {
        return siteRepository.search(null).stream()
                .collect(Collectors.toMap(CimSite::getSiteCode, CimSite::getSiteName, (a, b) -> a, HashMap::new));
    }

    private Map<String, String> fabNameMap() {
        return fabRepository.search(null, null).stream()
                .collect(Collectors.toMap(CimFab::getFabCode, CimFab::getFabName, (a, b) -> a, HashMap::new));
    }

    private static void applySite(CimSite entity, SiteReq req) {
        entity.setSiteCode(entity.getSiteCode() == null ? req.siteCode().trim() : entity.getSiteCode());
        entity.setSiteName(req.siteName().trim());
        entity.setAddress(req.address());
        entity.setContactPerson(req.contactPerson());
        entity.setContactPhone(req.contactPhone());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private static void applyFab(CimFab entity, FabReq req) {
        entity.setFabCode(entity.getFabCode() == null ? req.fabCode().trim() : entity.getFabCode());
        entity.setFabName(req.fabName().trim());
        entity.setSiteCode(entity.getSiteCode() == null ? req.siteCode().trim() : entity.getSiteCode());
        entity.setFabType(req.fabType());
        entity.setAmhsType(req.amhsType());
        entity.setCleanGrade(req.cleanGrade());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private static void applyArea(CimArea entity, AreaReq req) {
        entity.setAreaCode(entity.getAreaCode() == null ? req.areaCode().trim() : entity.getAreaCode());
        entity.setFabCode(entity.getFabCode() == null ? req.fabCode().trim() : entity.getFabCode());
        entity.setAreaName(req.areaName().trim());
        entity.setAreaType(req.areaType());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private static SiteView toView(CimSite s) {
        return new SiteView(s.getId(), s.getSiteCode(), s.getSiteName(), s.getAddress(),
                s.getContactPerson(), s.getContactPhone(), s.getRemark(), s.getEnabled(), s.getDescription(),
                s.getOccupied(), s.getOccupiedBy());
    }

    private static FabView toView(CimSiteRepository siteRepository, CimFab f) {
        String siteName = siteRepository.findBySiteCodeAndDeletedFalse(f.getSiteCode())
                .map(CimSite::getSiteName).orElse(null);
        return new FabView(f.getId(), f.getFabCode(), f.getSiteCode(), siteName, f.getFabName(),
                f.getFabType(), f.getAmhsType(), f.getCleanGrade(), f.getRemark(), f.getEnabled(),
                f.getDescription(), f.getOccupied(), f.getOccupiedBy());
    }

    private static AreaView toView(Map<String, String> fabNames, CimArea a) {
        return new AreaView(a.getId(), a.getAreaCode(), a.getFabCode(), fabNames.get(a.getFabCode()),
                a.getAreaName(), a.getAreaType(), a.getRemark(), a.getEnabled(), a.getDescription(),
                a.getOccupied(), a.getOccupiedBy());
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
