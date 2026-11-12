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
 * <p><b>删除是逻辑删除 + 引用保护</b>：上层被引用时拒绝删除（{@code OPERATION_NOT_ALLOWED}），
 * 避免留下悬空的 {@code site_code}/{@code fab_code}——本项目跨域用逻辑引用而非 FK，
 * 数据库不会替我们挡这一刀。</p>
 */
@Service
@RequiredArgsConstructor
public class SiteAdminService {

    private final CimSiteRepository siteRepository;
    private final CimFabRepository fabRepository;
    private final CimAreaRepository areaRepository;

    // ------------------------------------------------------------------ 视图

    public record SiteView(String id, String siteCode, String siteName, String address,
                           String contactPerson, String contactPhone, String remark,
                           Boolean enabled, String description) {
    }

    public record FabView(String id, String fabCode, String siteCode, String siteName, String fabName,
                          String fabType, String amhsType, String cleanGrade, String remark,
                          Boolean enabled, String description) {
    }

    public record AreaView(String id, String areaCode, String fabCode, String fabName, String areaName,
                           String areaType, String remark, Boolean enabled, String description) {
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
        applySite(entity, req);
        return toView(siteRepository.save(entity));
    }

    @Transactional
    public void deleteSite(String id) {
        CimSite entity = requireSite(id);
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
                        f.getCleanGrade(), f.getRemark(), f.getEnabled(), f.getDescription()))
                .toList();
    }

    @Transactional
    public FabView createFab(FabReq req) {
        requireText(req.fabCode(), "fabCode");
        requireText(req.fabName(), "fabName");
        requireText(req.siteCode(), "siteCode");
        requireSiteByCode(req.siteCode());
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
        // 编码不可改；归属可改（但新基地必须存在）
        if (req.siteCode() != null && !req.siteCode().isBlank()
                && !req.siteCode().trim().equals(entity.getSiteCode())) {
            requireSiteByCode(req.siteCode());
            entity.setSiteCode(req.siteCode().trim());
        }
        applyFab(entity, req);
        return toView(siteRepository, fabRepository.save(entity));
    }

    @Transactional
    public void deleteFab(String id) {
        CimFab entity = requireFab(id);
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
                        a.getRemark(), a.getEnabled(), a.getDescription()))
                .toList();
    }

    @Transactional
    public AreaView createArea(AreaReq req) {
        requireText(req.areaCode(), "areaCode");
        requireText(req.areaName(), "areaName");
        requireText(req.fabCode(), "fabCode");
        requireFabByCode(req.fabCode());
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
        if (req.fabCode() != null && !req.fabCode().isBlank()
                && !req.fabCode().trim().equals(entity.getFabCode())) {
            requireFabByCode(req.fabCode());
            entity.setFabCode(req.fabCode().trim());
        }
        applyArea(entity, req);
        return toView(fabNameMap(), areaRepository.save(entity));
    }

    @Transactional
    public void deleteArea(String id) {
        CimArea entity = requireArea(id);
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

    private void requireSiteByCode(String siteCode) {
        siteRepository.findBySiteCodeAndDeletedFalse(siteCode.trim())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "归属基地不存在: " + siteCode));
    }

    private void requireFabByCode(String fabCode) {
        fabRepository.findByFabCodeAndDeletedFalse(fabCode.trim())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "归属 Fab 不存在: " + fabCode));
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
                s.getContactPerson(), s.getContactPhone(), s.getRemark(), s.getEnabled(), s.getDescription());
    }

    private static FabView toView(CimSiteRepository siteRepository, CimFab f) {
        String siteName = siteRepository.findBySiteCodeAndDeletedFalse(f.getSiteCode())
                .map(CimSite::getSiteName).orElse(null);
        return new FabView(f.getId(), f.getFabCode(), f.getSiteCode(), siteName, f.getFabName(),
                f.getFabType(), f.getAmhsType(), f.getCleanGrade(), f.getRemark(), f.getEnabled(),
                f.getDescription());
    }

    private static AreaView toView(Map<String, String> fabNames, CimArea a) {
        return new AreaView(a.getId(), a.getAreaCode(), a.getFabCode(), fabNames.get(a.getFabCode()),
                a.getAreaName(), a.getAreaType(), a.getRemark(), a.getEnabled(), a.getDescription());
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
