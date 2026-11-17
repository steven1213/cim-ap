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
 * 流程独立规则（V15）API——{@code /api/v1/mds/flow-rules}。
 *
 * <p>规则锚定流程版本族（familyCode + versionNo），按 flowId 定位；维护权限
 * 复用流程域四码（规则是版本内容的一部分，随版本只读守门）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class FlowRuleController {

    private final FlowRuleService service;

    /** 某流程版本的规则清单（按 priority 升序）。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @GetMapping("/flow-rules")
    public Result<List<FlowRuleService.RuleView>> list(@RequestParam String flowId) {
        return Result.ok(service.listByFlow(flowId));
    }

    /** 新建规则（仅 UNFROZEN 版本；ruleCode 族内同版唯一）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PostMapping("/flow-rules")
    public Result<FlowRuleService.RuleView> create(
            @RequestParam String flowId, @RequestBody FlowRuleService.RuleReq req) {
        return Result.ok(service.create(flowId, req));
    }

    /** 更新规则（仅 UNFROZEN 版本）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PutMapping("/flow-rules/{id}")
    public Result<FlowRuleService.RuleView> update(
            @PathVariable String id, @RequestBody FlowRuleService.RuleReq req) {
        return Result.ok(service.update(id, req));
    }

    /** 删除规则（软删；仅 UNFROZEN 版本）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @DeleteMapping("/flow-rules/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    /** 套用内置模板（QTIME_OVER_HOLD / BIN_ROUTE，按当前版本解析产物生成）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PostMapping("/flow-rules/apply-template")
    public Result<List<FlowRuleService.RuleView>> applyTemplate(
            @RequestBody FlowRuleService.TemplateReq req) {
        return Result.ok(service.applyTemplate(req));
    }

    /** evaluate 契约（MES/MCS 运行期调用 + 页面调试面板）：按 join_point/锚点/条件评估。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @PostMapping("/flow-rules/evaluate")
    public Result<FlowRuleService.EvaluateResult> evaluate(
            @RequestBody FlowRuleService.EvaluateReq req) {
        return Result.ok(service.evaluate(req));
    }
}
