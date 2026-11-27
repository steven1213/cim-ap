package com.cim.mds.server.edc;

import java.util.List;

import com.cim.mds.server.security.MdsPermissions;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EDC 工程数采 API（V28）——参数集 / 报告模板 / 设备接口映射 / 方案 / 模板与导入。
 *
 * <p>MDS 边界：只维护「采集契约」静态元数据；运行时采集执行与判定归 MES/SPC/FDC。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class EdcController {

    private final EdcAdminService adminService;
    private final EdcPlanService planService;
    private final EdcImportService importService;

    // ================================================================ 参数集

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_SET_VIEW + "')")
    @GetMapping("/edc/param-sets")
    public Result<List<CimEdcParamSet>> listSets() {
        return Result.ok(adminService.listSets());
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_SET_VIEW + "')")
    @GetMapping("/edc/param-sets/{id}")
    public Result<EdcAdminService.SetView> getSet(@PathVariable String id) {
        return Result.ok(adminService.getSet(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_SET_CREATE + "')")
    @PostMapping("/edc/param-sets")
    public Result<CimEdcParamSet> createSet(@RequestBody EdcAdminService.SetReq req) {
        return Result.ok(adminService.createSet(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_SET_UPDATE + "')")
    @PutMapping("/edc/param-sets/{id}")
    public Result<CimEdcParamSet> updateSet(
            @PathVariable String id, @RequestBody EdcAdminService.SetReq req) {
        return Result.ok(adminService.updateSet(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_SET_DELETE + "')")
    @DeleteMapping("/edc/param-sets/{id}")
    public Result<Void> deleteSet(@PathVariable String id) {
        adminService.deleteSet(id);
        return Result.ok(null);
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_SET_UPDATE + "')")
    @PutMapping("/edc/param-sets/{id}/items")
    public Result<EdcAdminService.SetView> saveSetItems(
            @PathVariable String id, @RequestBody List<EdcAdminService.SetItemReq> items) {
        return Result.ok(adminService.saveSetItems(id, items));
    }

    // ================================================================ 报告模板

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_TPL_VIEW + "')")
    @GetMapping("/edc/report-tpls")
    public Result<List<CimEdcReportTpl>> listTpls() {
        return Result.ok(adminService.listTpls());
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_TPL_VIEW + "')")
    @GetMapping("/edc/report-tpls/{id}")
    public Result<EdcAdminService.TplView> getTpl(@PathVariable String id) {
        return Result.ok(adminService.getTpl(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_TPL_CREATE + "')")
    @PostMapping("/edc/report-tpls")
    public Result<CimEdcReportTpl> createTpl(@RequestBody EdcAdminService.TplReq req) {
        return Result.ok(adminService.createTpl(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_TPL_UPDATE + "')")
    @PutMapping("/edc/report-tpls/{id}")
    public Result<CimEdcReportTpl> updateTpl(
            @PathVariable String id, @RequestBody EdcAdminService.TplReq req) {
        return Result.ok(adminService.updateTpl(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_TPL_DELETE + "')")
    @DeleteMapping("/edc/report-tpls/{id}")
    public Result<Void> deleteTpl(@PathVariable String id) {
        adminService.deleteTpl(id);
        return Result.ok(null);
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_TPL_UPDATE + "')")
    @PutMapping("/edc/report-tpls/{id}/items")
    public Result<EdcAdminService.TplView> saveTplItems(
            @PathVariable String id, @RequestBody List<EdcAdminService.TplItemReq> items) {
        return Result.ok(adminService.saveTplItems(id, items));
    }

    // ================================================================ 设备接口映射

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_MAP_VIEW + "')")
    @GetMapping("/edc/equip-maps")
    public Result<List<EdcAdminService.MapView>> listMaps(
            @RequestParam(required = false) String equipmentCode) {
        return Result.ok(adminService.listMaps(equipmentCode));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_MAP_CREATE + "')")
    @PostMapping("/edc/equip-maps")
    public Result<CimEdcEquipMap> createMap(@RequestBody EdcAdminService.MapReq req) {
        return Result.ok(adminService.createMap(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_MAP_UPDATE + "')")
    @PutMapping("/edc/equip-maps/{id}")
    public Result<CimEdcEquipMap> updateMap(
            @PathVariable String id, @RequestBody EdcAdminService.MapReq req) {
        return Result.ok(adminService.updateMap(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_MAP_DELETE + "')")
    @DeleteMapping("/edc/equip-maps/{id}")
    public Result<Void> deleteMap(@PathVariable String id) {
        adminService.deleteMap(id);
        return Result.ok(null);
    }

    // ================================================================ 方案

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_PLAN_VIEW + "')")
    @GetMapping("/edc/plans")
    public Result<List<CimEdcPlan>> listPlans() {
        return Result.ok(planService.list());
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_PLAN_VIEW + "')")
    @GetMapping("/edc/plans/{id}")
    public Result<EdcPlanService.PlanView> getPlan(@PathVariable String id) {
        return Result.ok(planService.get(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_PLAN_CREATE + "')")
    @PostMapping("/edc/plans")
    public Result<CimEdcPlan> createPlan(@RequestBody EdcPlanService.PlanReq req) {
        return Result.ok(planService.create(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_PLAN_UPDATE + "')")
    @PutMapping("/edc/plans/{id}")
    public Result<CimEdcPlan> updatePlan(
            @PathVariable String id, @RequestBody EdcPlanService.PlanReq req) {
        return Result.ok(planService.update(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_PLAN_DELETE + "')")
    @DeleteMapping("/edc/plans/{id}")
    public Result<Void> deletePlan(@PathVariable String id) {
        planService.delete(id);
        return Result.ok(null);
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_PLAN_UPDATE + "')")
    @PutMapping("/edc/plans/{id}/items")
    public Result<EdcPlanService.PlanView> savePlanItems(
            @PathVariable String id, @RequestBody List<EdcPlanService.PlanItemReq> items) {
        return Result.ok(planService.saveItems(id, items));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_PLAN_UPDATE + "')")
    @PostMapping("/edc/plans/{id}/apply-set")
    public Result<EdcPlanService.PlanView> applySet(
            @PathVariable String id, @RequestParam String setCode) {
        return Result.ok(planService.applySet(id, setCode));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_PLAN_UPDATE + "')")
    @PostMapping("/edc/plans/{id}/freeze")
    public Result<CimEdcPlan> freeze(@PathVariable String id) {
        return Result.ok(planService.freeze(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_PLAN_UPDATE + "')")
    @PostMapping("/edc/plans/{id}/unfreeze")
    public Result<CimEdcPlan> unfreeze(@PathVariable String id) {
        return Result.ok(planService.unfreeze(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_PLAN_UPDATE + "')")
    @PostMapping("/edc/plans/{id}/activate")
    public Result<CimEdcPlan> activate(@PathVariable String id) {
        return Result.ok(planService.activate(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_PLAN_UPDATE + "')")
    @PostMapping("/edc/plans/{id}/disable")
    public Result<CimEdcPlan> disable(@PathVariable String id) {
        return Result.ok(planService.disable(id));
    }

    // ================================================================ 模板与导入

    /** 下载模板（base64 xlsx；kind=PARAM_DEF/PARAM_SET_ITEM/PLAN_ITEM/EQUIP_MAP）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_SET_VIEW + "')"
            + " or hasAuthority('" + MdsPermissions.PARAM_DEF_VIEW + "')")
    @GetMapping("/edc/import-template")
    public Result<String> importTemplate(@RequestParam String kind) {
        return Result.ok(java.util.Base64.getEncoder().encodeToString(importService.template(kind)));
    }

    /** 导入请求体（xlsx 的 base64；targetId 为参数集/方案 id，其余类可空）。 */
    public record EdcImportReq(String kind, String dataBase64, String targetId) {
    }

    /** 导入：PARAM_DEF 用参数定义写权限，其余按各自资源 update 权限（页面侧已分控）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.EDC_SET_UPDATE + "')"
            + " or hasAuthority('" + MdsPermissions.PARAM_DEF_UPDATE + "')")
    @PostMapping("/edc/import")
    public Result<EdcImportService.ImportReport> importExcel(@RequestBody EdcImportReq req) {
        return Result.ok(importService.importExcel(req.kind(), req.dataBase64(), req.targetId()));
    }
}
