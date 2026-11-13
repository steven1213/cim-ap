package com.cim.mds.server.site;

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
 * 产品（Product）API——产品管理域。
 *
 * <p>路径 {@code /api/v1/mds/products}；编辑锁复用 {@code /locations/{type}/{id}/lock}
 * （type=product，见 {@link LocationLockController}）。对外契约统一走 {@code *View}。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ProductController {

    private final ProductAdminService service;

    @PreAuthorize("hasAuthority('mds:product:view')")
    @GetMapping("/products")
    public Result<List<ProductAdminService.ProductView>> products(
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    @PreAuthorize("hasAuthority('mds:product:create')")
    @PostMapping("/products")
    public Result<ProductAdminService.ProductView> createProduct(
            @RequestBody ProductAdminService.ProductReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('mds:product:update')")
    @PutMapping("/products/{id}")
    public Result<ProductAdminService.ProductView> updateProduct(
            @PathVariable String id, @RequestBody ProductAdminService.ProductReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('mds:product:delete')")
    @DeleteMapping("/products/{id}")
    public Result<Void> deleteProduct(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
