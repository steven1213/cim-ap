package com.cim.mds.server.namingrule;

import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RestController;

/**
 * 编码规则 API——配置化 Key Generation（模板段 + 分桶计数器）。
 *
 * <p>路径 {@code /api/v1/mds/naming-rules}；轻量规则表不走编辑锁。
 * object-types 端点供前端配置表单取「对象白名单 + 各对象可用变量」。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class NamingRuleController {

    private final NamingRuleAdminService service;

    /** 全部编码规则。 */
    @PreAuthorize("hasAuthority('mds:namingRule:view')")
    @GetMapping("/naming-rules")
    public Result<List<NamingRuleAdminService.NamingRuleView>> list() {
        return Result.ok(service.list());
    }

    /** 对象白名单（object_type → 中文名 / 父对象提示 / 模板可用变量）。 */
    @PreAuthorize("hasAuthority('mds:namingRule:view')")
    @GetMapping("/naming-rules/object-types")
    public Result<List<NamingRuleAdminService.ObjectSpec>> objectTypes() {
        return Result.ok(NamingRuleAdminService.objectSpecs());
    }

    /** 新建规则（objectType 建后不可改；同对象至多一条启用规则，重复启用 409）。 */
    @PreAuthorize("hasAuthority('mds:namingRule:create')")
    @PostMapping("/naming-rules")
    public Result<NamingRuleAdminService.NamingRuleView> create(
            @RequestBody NamingRuleAdminService.NamingRuleReq req) {
        return Result.ok(service.create(req));
    }

    /** 更新规则（只改段列表/名称/启停；objectType 建后不可改，计数器保留不回退）。 */
    @PreAuthorize("hasAuthority('mds:namingRule:update')")
    @PutMapping("/naming-rules/{id}")
    public Result<NamingRuleAdminService.NamingRuleView> update(
            @PathVariable String id,
            @RequestBody NamingRuleAdminService.NamingRuleReq req) {
        return Result.ok(service.update(id, req));
    }

    /** 删除规则（连同计数器物理删）。 */
    @PreAuthorize("hasAuthority('mds:namingRule:delete')")
    @DeleteMapping("/naming-rules/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    /** 复制规则：名称加「·副本」、默认停用、不带计数器。 */
    @PreAuthorize("hasAuthority('mds:namingRule:create')")
    @PostMapping("/naming-rules/{id}/copy")
    public Result<NamingRuleAdminService.NamingRuleView> copy(@PathVariable String id) {
        return Result.ok(service.copy(id));
    }

    /** 计数器只读查询（各 SEQ 段各桶当前值；号段只增不回收，不提供重置）。 */
    @PreAuthorize("hasAuthority('mds:namingRule:view')")
    @GetMapping("/naming-rules/{id}/counters")
    public Result<List<NamingRuleAdminService.NamingCounterView>> counters(@PathVariable String id) {
        return Result.ok(service.listCounters(id));
    }

    /** 试算：逐段渲染明细 + 示例编码 + 派生正则 + 缺失变量（不占号）。 */
    @PreAuthorize("hasAuthority('mds:namingRule:view')")
    @PostMapping("/naming-rules/preview")
    public Result<NamingRuleAdminService.PreviewRes> preview(
            @RequestBody NamingRuleAdminService.PreviewReq req) {
        return Result.ok(service.preview(req));
    }
}
