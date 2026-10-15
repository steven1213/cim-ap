package com.cim.rms.server.compare;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.recipe.Recipe;
import com.cim.rms.server.recipe.RecipeRepository;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 配方比对服务（Req.md FR-C1/C3/C5/C8、Req 25/27/28/33/37/38/39）。
 *
 * <p>本版实现 <b>版本 ↔ 版本</b> 结构化比对，支持三种比对方式（{@link CompareMode}）：完整内容
 * （Body 格式/哈希/长度）、参数级（参数快照逐参数差异，快照缺失退化为变更摘要）、结构级
 * （版本号/状态/血缘）。支持<b>批量</b>（基线 vs 多版本），并把每次比对落 {@link RmsCompareLog}
 * 校验日志（Req 17 表格化 + 两级下钻、Req 18 汇总统计）。</p>
 *
 * <p>参数级细粒度差异的完整能力依赖机型格式适配器（Req.md §1.2 P2）产出参数快照；设备↔RMS /
 * 设备↔设备在线比对（IF-E3）待 EAP 链路接入，届时复用同一比对内核。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeCompareService {

    private final RecipeVersionRepository versionRepository;
    private final RecipeRepository recipeRepository;
    private final RmsCompareLogRepository logRepository;
    private final com.cim.rms.server.recipe.BodyStore bodyStore;
    private final RmsAuditService audit;
    private final ObjectMapper objectMapper;

    // ==================== 纯比对 ====================

    /** 全量差异（不过滤方式，供签核参数差异附件使用，FR-L4）。 */
    public CompareResult compare(String leftVersionId, String rightVersionId) {
        RecipeVersion left = requireVersion(leftVersionId);
        RecipeVersion right = requireVersion(rightVersionId);
        return buildResult(left, right, allItems(left, right));
    }

    /** 按比对方式过滤差异（前端比对中心使用，FR-C1）。 */
    public CompareResult compare(String leftVersionId, String rightVersionId, CompareMode mode) {
        RecipeVersion left = requireVersion(leftVersionId);
        RecipeVersion right = requireVersion(rightVersionId);
        List<DiffItem> items = allItems(left, right).stream()
                .filter(it -> modeOf(it).contains(mode))
                .toList();
        return buildResult(left, right, items);
    }

    // ==================== 落日志 + 批量 ====================

    /** 运行一次比对并落校验日志，返回该次比对结果。 */
    @Transactional
    public CompareRun run(String baselineVersionId, String targetVersionId, CompareMode mode) {
        CompareMode m = mode == null ? CompareMode.FULL_BODY : mode;
        CompareResult cr = compare(baselineVersionId, targetVersionId, m);
        RmsCompareLog saved = persist(cr, m, UUID.randomUUID().toString().substring(0, 16));
        audit.success("COMPARE_RUN", cr.right().recipeCode(),
                "比对 " + briefLabel(cr.left()) + " ↔ " + briefLabel(cr.right())
                        + " · " + m + " · " + (cr.identical() ? "一致" : "差异 " + diffCount(cr)));
        return new CompareRun(saved.getId(), saved.getBatchId(), m.name(), cr);
    }

    /** 批量比对：基线版本 vs 多个目标版本（Req 26 一次性批量对比），共享一个批次号。 */
    @Transactional
    public List<CompareRun> batch(String baselineVersionId, List<String> targetVersionIds, CompareMode mode) {
        if (targetVersionIds == null || targetVersionIds.isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "请选择至少一个目标版本");
        }
        CompareMode m = mode == null ? CompareMode.FULL_BODY : mode;
        String batchId = UUID.randomUUID().toString().substring(0, 16);
        RecipeVersion baseline = requireVersion(baselineVersionId);
        List<CompareRun> runs = new ArrayList<>();
        for (String tid : targetVersionIds) {
            if (tid.equals(baselineVersionId)) {
                continue;
            }
            RecipeVersion target = requireVersion(tid);
            List<DiffItem> items = allItems(baseline, target).stream()
                    .filter(it -> modeOf(it).contains(m)).toList();
            CompareResult cr = buildResult(baseline, target, items);
            RmsCompareLog saved = persist(cr, m, batchId);
            runs.add(new CompareRun(saved.getId(), batchId, m.name(), cr));
        }
        int fail = (int) runs.stream().filter(r -> !r.result().identical()).count();
        audit.success("COMPARE_BATCH", baseline.getRecipeId() == null ? null
                        : recipeRepository.findById(baseline.getRecipeId()).map(Recipe::getCode).orElse(null),
                "批量比对基线 " + briefLabel(runs.isEmpty() ? null : runs.get(0).result().left())
                        + " × " + runs.size() + " 台 · " + m + " · 差异 " + fail + " 项");
        return runs;
    }

    // ==================== 校验日志（Req 17/18） ====================

    /** 校验日志列表（可按结果过滤，时间倒序）。 */
    public List<LogView> logs(String result, int limit) {
        List<RmsCompareLog> rows = (result == null || result.isBlank())
                ? logRepository.findByDeletedFalseOrderByCreateTimeDesc()
                : logRepository.findByDeletedFalseAndResultOrderByCreateTimeDesc(result.toUpperCase());
        int cap = limit <= 0 ? 200 : Math.min(limit, 1000);
        return rows.stream().limit(cap).map(this::toLogView).toList();
    }

    /** 校验日志明细（差异项逐条，两级下钻的叶子）。 */
    public LogDetail logDetail(String id) {
        RmsCompareLog l = logRepository.findById(id).orElse(null);
        if (l == null || Boolean.TRUE.equals(l.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "比对记录不存在: " + id);
        }
        List<DiffItem> items = new ArrayList<>();
        if (l.getDiffItems() != null && !l.getDiffItems().isBlank()) {
            try {
                items = objectMapper.readValue(l.getDiffItems(), new TypeReference<List<DiffItem>>() { });
            } catch (Exception e) {
                log.warn("[compare] 解析差异明细失败 id={} err={}", id, e.toString());
            }
        }
        return new LogDetail(toLogView(l), items);
    }

    /** 校验日志汇总统计（Req 18）。 */
    public LogSummary logSummary() {
        List<RmsCompareLog> all = logRepository.findByDeletedFalseOrderByCreateTimeDesc();
        int pass = 0, fail = 0, diffTotal = 0;
        for (RmsCompareLog l : all) {
            if ("FAIL".equalsIgnoreCase(l.getResult())) {
                fail++;
            } else {
                pass++;
            }
            diffTotal += l.getDiffCount();
        }
        return new LogSummary(all.size(), pass, fail, diffTotal);
    }

    // ==================== 内部 ====================

    private RmsCompareLog persist(CompareResult cr, CompareMode mode, String batchId) {
        RmsCompareLog l = new RmsCompareLog();
        l.setBatchId(batchId);
        l.setMode(mode.name());
        l.setBaselineVersionId(cr.left() == null ? null : cr.left().id());
        l.setTargetVersionId(cr.right().id());
        l.setRecipeCode(cr.right().recipeCode());
        l.setIdentical(cr.identical());
        l.setDiffCount(diffCount(cr));
        l.setResult(cr.identical() ? "PASS" : "FAIL");
        try {
            l.setDiffItems(objectMapper.writeValueAsString(cr.items()));
        } catch (Exception e) {
            log.warn("[compare] 序列化差异明细失败 err={}", e.toString());
        }
        return logRepository.save(l);
    }

    private List<DiffItem> allItems(RecipeVersion left, RecipeVersion right) {
        List<DiffItem> items = new ArrayList<>();
        // 完整内容级（FR-C1 FULL_BODY）
        items.add(diff("bodyFormat", str(left.getBodyFormat()), str(right.getBodyFormat()), "BODY"));
        items.add(diff("bodyHash", left.getBodyHash(), right.getBodyHash(), "BODY"));
        items.add(diff("bodyLength", str(bodyLength(left)), str(bodyLength(right)), "BODY"));
        // 参数级（FR-C1 PARAMETER / FR-C4）
        addParamDiff(items, left.getParamSnapshot(), right.getParamSnapshot());
        // 结构级（FR-C1 STRUCTURE）
        // 变更摘要属版本元数据（非参数值），归结构级，避免「只改备注」被误判为参数差异。
        items.add(diff("changeSummary", left.getChangeSummary(), right.getChangeSummary(), "STRUCTURE"));
        items.add(diff("status", str(left.getStatus()), str(right.getStatus()), "STRUCTURE"));
        items.add(diff("sourceVersionId", left.getSourceVersionId(), right.getSourceVersionId(), "STRUCTURE"));
        // 层级结构差异（Req 15：主/子配方层级）
        Recipe lr = recipeRepository.findById(left.getRecipeId()).orElse(null);
        Recipe rr = recipeRepository.findById(right.getRecipeId()).orElse(null);
        items.add(diff("recipeLevel", str(lr == null ? null : lr.getRecipeLevel()),
                str(rr == null ? null : rr.getRecipeLevel()), "STRUCTURE"));
        items.add(diff("parentRecipe", lr == null ? null : lr.getParentId(),
                rr == null ? null : rr.getParentId(), "STRUCTURE"));
        return items;
    }

    /** 参数快照逐参数差异（快照缺失则跳过，退化由 changeSummary 承担）。 */
    private void addParamDiff(List<DiffItem> items, String leftSnap, String rightSnap) {
        Map<String, Object> lm = parseMap(leftSnap);
        Map<String, Object> rm = parseMap(rightSnap);
        if (lm == null && rm == null) {
            return;
        }
        Set<String> keys = new TreeSet<>();
        if (lm != null) {
            keys.addAll(lm.keySet());
        }
        if (rm != null) {
            keys.addAll(rm.keySet());
        }
        for (String k : keys) {
            String lv = str(lm == null ? null : lm.get(k));
            String rv = str(rm == null ? null : rm.get(k));
            items.add(new DiffItem("param:" + k, lv, rv, !Objects.equals(lv, rv), "PARAM"));
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<CompareMode> modeOf(DiffItem it) {
        Set<CompareMode> s = new LinkedHashSet<>();
        String c = it.category();
        if (c == null) {
            s.add(CompareMode.FULL_BODY);
            return s;
        }
        switch (c) {
            case "BODY" -> s.add(CompareMode.FULL_BODY);
            case "PARAM" -> s.add(CompareMode.PARAMETER);
            case "STRUCTURE" -> s.add(CompareMode.STRUCTURE);
            default -> s.add(CompareMode.FULL_BODY);
        }
        return s;
    }

    /**
     * 「一致」判定按<b>本次比对方式</b>的差异项集合计算（FR-C1）：
     * 传入的 {@code items} 已由调用方按 mode 过滤，故「该方式下无任何变更项」即为一致。
     * <p>此前按 bodyHash 单一判定，导致「参数级比对（Body 不同但参数相同）」被误判为不一致。</p>
     */
    private CompareResult buildResult(RecipeVersion left, RecipeVersion right, List<DiffItem> items) {
        boolean identical = items.stream().noneMatch(DiffItem::changed);
        return new CompareResult(identical, brief(left), brief(right), items);
    }

    private static int diffCount(CompareResult cr) {
        return (int) cr.items().stream().filter(DiffItem::changed).count();
    }

    private static String briefLabel(VersionBrief b) {
        return b == null ? "—" : (b.recipeCode() + " v" + b.versionNo());
    }

    private RecipeVersion requireVersion(String id) {
        RecipeVersion v = versionRepository.findById(id).orElse(null);
        if (v == null || Boolean.TRUE.equals(v.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方版本不存在: " + id);
        }
        return v;
    }

    private VersionBrief brief(RecipeVersion v) {
        Recipe r = recipeRepository.findById(v.getRecipeId()).orElse(null);
        return new VersionBrief(
                v.getId(), v.getRecipeId(),
                r == null ? null : r.getCode(), r == null ? null : r.getName(),
                v.getVersionNo(), str(v.getStatus()), str(v.getBodyFormat()),
                v.getBodyHash(), bodyLength(v), v.getChangeSummary());
    }

    /** Body 明文长度（D9：库内为密文时先解密再计）。 */
    private int bodyLength(RecipeVersion v) {
        return bodyLength(bodyStore.readPlain(v));
    }

    private static int bodyLength(String b64) {
        if (b64 == null || b64.isEmpty()) {
            return 0;
        }
        try {
            return Base64.getDecoder().decode(b64).length;
        } catch (IllegalArgumentException e) {
            return b64.length();
        }
    }

    private static DiffItem diff(String field, String left, String right, String category) {
        return new DiffItem(field, left, right, !Objects.equals(left, right), category);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ==================== 视图 ====================

    public record VersionBrief(String id, String recipeId, String recipeCode, String recipeName,
                               int versionNo, String status, String bodyFormat, String bodyHash,
                               int bodyLength, String changeSummary) {
    }

    /** 单个字段差异项（category 为 BODY/PARAM/STRUCTURE，决定所属比对方式）。 */
    public record DiffItem(String field, String left, String right, boolean changed, String category) {
    }

    public record CompareResult(boolean identical, VersionBrief left, VersionBrief right, List<DiffItem> items) {
    }

    /** 一次比对（含落库 id 与批次）。 */
    public record CompareRun(String logId, String batchId, String mode, CompareResult result) {
    }

    public record LogView(String id, String batchId, String mode, String recipeCode,
                          boolean identical, int diffCount, String result, String createTime) {
    }

    public record LogDetail(LogView view, List<DiffItem> items) {
    }

    public record LogSummary(int total, int pass, int fail, int diffTotal) {
    }

    private LogView toLogView(RmsCompareLog l) {
        return new LogView(l.getId(), l.getBatchId(), l.getMode(), l.getRecipeCode(),
                l.isIdentical(), l.getDiffCount(), l.getResult(), fmt(l.getCreateTime()));
    }
}
