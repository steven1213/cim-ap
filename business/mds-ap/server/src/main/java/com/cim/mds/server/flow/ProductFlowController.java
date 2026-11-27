package com.cim.mds.server.flow;

import java.util.List;

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
 * 产品↔流程绑定 API（mds:productFlow:*；POST=绑定/换版激活，DELETE=解绑留痕）。
 * V25 L3：绑定行上挂列型覆盖（PUT /{id}）与产品级指派/KV（/{id}/assigns、/{id}/attrs）。
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ProductFlowController {

    private final ProductFlowAdminService service;
    private final EquipAssignAdminService assignService;
    private final ConstraintAttrAdminService attrService;

    /** 绑定列表（productCode 过滤可空=全量；产品历史走本端点带过滤）。 */
    @PreAuthorize("hasAuthority('mds:productFlow:view')")
    @GetMapping("/product-flows")
    public Result<List<ProductFlowAdminService.BindingView>> list(
            @RequestParam(required = false) String productCode) {
        return Result.ok(productCode == null || productCode.isBlank()
                ? service.listAll() : service.listByProduct(productCode));
    }

    /** 绑定/换版激活（同族旧 ACTIVE 自动降 HISTORY）。 */
    @PreAuthorize("hasAuthority('mds:productFlow:create')")
    @PostMapping("/product-flows")
    public Result<ProductFlowAdminService.BindingView> bind(
            @RequestBody ProductFlowAdminService.BindReq req) {
        return Result.ok(service.bind(req));
    }

    /** 列型覆盖更新（V25 L3：光罩/污染级/禁入/NPW 落绑定行；空 = 清除回落继承）。 */
    @PreAuthorize("hasAuthority('mds:productFlow:update')")
    @PutMapping("/product-flows/{id}")
    public Result<ProductFlowAdminService.BindingView> updateOverrides(
            @PathVariable String id,
            @RequestBody ProductFlowAdminService.OverrideReq req) {
        return Result.ok(service.updateOverrides(id, req));
    }

    /** 解绑（ACTIVE → HISTORY，历史保留）。 */
    @PreAuthorize("hasAuthority('mds:productFlow:delete')")
    @DeleteMapping("/product-flows/{id}")
    public Result<Void> unbind(@PathVariable String id) {
        service.unbind(id);
        return Result.ok(null);
    }

    // ---------------- V25 L3：产品级覆盖指派 / KV（锚定绑定行 → product+family） ----------------

    /** 产品级指派列表（绑定行定位 product+family）。 */
    @PreAuthorize("hasAuthority('mds:productFlow:view')")
    @GetMapping("/product-flows/{id}/assigns")
    public Result<List<CimEquipAssign>> productAssigns(@PathVariable String id) {
        CimProductFlow b = service.require(id);
        return Result.ok(assignService.listByProduct(b.getProductCode(), b.getFlowFamilyCode()));
    }

    /** 产品级指派新建（nodeId 可空 = 整条流程生效）。 */
    @PreAuthorize("hasAuthority('mds:productFlow:update')")
    @PostMapping("/product-flows/{id}/assigns")
    public Result<CimEquipAssign> createProductAssign(
            @PathVariable String id,
            @RequestParam(required = false) String nodeId,
            @RequestBody EquipAssignAdminService.AssignReq req) {
        CimProductFlow b = service.require(id);
        return Result.ok(assignService.createForProduct(b.getProductCode(), b.getFlowFamilyCode(), nodeId, req));
    }

    /** 产品级 KV 快照（绑定行定位）。 */
    @PreAuthorize("hasAuthority('mds:productFlow:view')")
    @GetMapping("/product-flows/{id}/attrs")
    public Result<java.util.Map<String, String>> productAttrs(@PathVariable String id) {
        CimProductFlow b = service.require(id);
        return Result.ok(attrService.listByProduct(b.getProductCode(), b.getFlowFamilyCode()));
    }

    /** 产品级 KV 全量快照 PUT。 */
    @PreAuthorize("hasAuthority('mds:productFlow:update')")
    @PutMapping("/product-flows/{id}/attrs")
    public Result<java.util.Map<String, String>> putProductAttrs(
            @PathVariable String id,
            @RequestBody ConstraintAttrAdminService.AttrPutReq req) {
        CimProductFlow b = service.require(id);
        return Result.ok(attrService.putForProduct(b.getProductCode(), b.getFlowFamilyCode(), req));
    }
}
