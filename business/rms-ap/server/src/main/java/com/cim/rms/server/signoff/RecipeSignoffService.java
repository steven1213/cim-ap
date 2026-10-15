package com.cim.rms.server.signoff;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.compare.RecipeCompareService;
import com.cim.rms.server.recipe.Recipe;
import com.cim.rms.server.recipe.RecipeRepository;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionRepository;
import com.cim.rms.server.recipe.RecipeVersionStatus;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 签核流服务（Req.md FR-L2/L4/L5、§3.2 状态机、Req 3/20/41/42）。
 *
 * <p><b>状态迁移</b>：提交 → 版本 {@code DRAFT→PENDING_SIGN}（建 PENDING 签核单，自动计算相对
 * 上一生效版的<b>参数级差异附件</b>）；通过 → 版本 {@code PENDING_SIGN→ACTIVE}（同配方旧 ACTIVE
 * 自动转 OBSOLETE，Req 14）；拒绝 → 版本 {@code PENDING_SIGN→DRAFT}。全程写 {@link RmsSignoffEvent}
 * 时间线（证据链）与操作审计（FR-H3）。</p>
 *
 * <p><b>批量</b>（Req 3 / FR-L2）：{@link #batchDecision} 按单配方粒度返回逐项结果（部分成功语义），
 * 每项独立事务，互不回滚。</p>
 *
 * <p><b>预设限时使用</b>（Req 20 / FR-L5）：{@link #presetTempAllow} 为 PENDING 版本预设放行窗口，
 * 超时后由派生状态自动回落为不可用（无需定时任务，读取时按当前时刻判定）；签核通过/拒绝后预设自动取消。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeSignoffService {

    private final RecipeSignoffRepository signoffRepository;
    private final RmsSignoffEventRepository eventRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final RecipeCompareService compareService;
    private final com.cim.rms.server.qualification.QualificationService qualificationService;
    private final com.cim.rms.server.notification.NotificationService notificationService;
    private final com.cim.rms.server.effect.ChangeEffectService changeEffectService;
    private final RmsAuditService audit;
    private final PlatformTransactionManager txManager;
    private final ObjectMapper objectMapper;

    /** 限时放行「即将到期」阈值（小时）。 */
    private static final long EXPIRING_HOURS = 4;

    // ==================== 发起 ====================

    /** 发起签核：版本须为 DRAFT，且无进行中的签核单；自动生成相对上一生效版的参数差异附件。 */
    @Transactional
    public SignoffDetail submit(String recipeId, String versionId, String reason, SignRoute route) {
        Recipe r = requireRecipe(recipeId);
        RecipeVersion v = requireVersion(versionId);
        if (!v.getRecipeId().equals(recipeId)) {
            throw new BizException(BizCode.PARAM_INVALID, "版本不属于该配方: " + versionId);
        }
        if (v.getStatus() != RecipeVersionStatus.DRAFT) {
            throw new BizException(BizCode.PARAM_INVALID, "仅 DRAFT 版本可发起签核，当前: " + v.getStatus());
        }
        signoffRepository.findFirstByVersionIdAndStatusInAndDeletedFalse(versionId,
                        Set.of(SignoffStatus.PENDING))
                .ifPresent(s -> {
                    throw new BizException(BizCode.PARAM_INVALID, "该版本已有进行中的签核单: " + s.getId());
                });

        // 参数级差异附件：相对上一生效版（FR-L4「改动可视」）
        String paramDiffJson = null;
        int diffCount = 0;
        String prevActiveId = r.getActiveVersionId();
        if (prevActiveId != null && !prevActiveId.equals(versionId)) {
            try {
                RecipeCompareService.CompareResult cr = compareService.compare(prevActiveId, versionId);
                paramDiffJson = objectMapper.writeValueAsString(cr.items());
                diffCount = (int) cr.items().stream().filter(RecipeCompareService.DiffItem::changed).count();
            } catch (Exception e) {
                log.warn("[signoff] 参数差异计算失败 version={} err={}", versionId, e.toString());
            }
        }

        v.setStatus(RecipeVersionStatus.PENDING_SIGN);
        versionRepository.save(v);

        RecipeSignoff s = new RecipeSignoff();
        s.setRecipeId(recipeId);
        s.setVersionId(versionId);
        s.setStatus(SignoffStatus.PENDING);
        s.setInitiator(RmsAuditService.currentActor());
        s.setReason(reason);
        s.setSignRoute(route == null ? SignRoute.SINGLE : route);
        s.setParamDiff(paramDiffJson);
        RecipeSignoff saved = signoffRepository.save(s);

        event(saved.getId(), "SUBMITTED", RmsAuditService.currentActor(),
                reason, "发起签核 · v" + v.getVersionNo()
                        + (diffCount > 0 ? "（含 " + diffCount + " 项参数差异）" : ""));
        if (paramDiffJson != null) {
            event(saved.getId(), "PARAM_DIFF", null, null,
                    "参数差异附件已生成：相对上一生效版 " + diffCount + " 项变更");
        }
        audit.success("SIGNOFF_SUBMITTED", r.getCode(),
                "v" + v.getVersionNo() + " 发起签核（路由 " + s.getSignRoute() + "）");
        log.info("[signoff] 发起签核 recipe={} version=v{} diff={}", r.getCode(), v.getVersionNo(), diffCount);
        // W9 通知：提交 → 广播给有审批权的人（失败不影响主事务）
        notificationService.publish("SIGNOFF_SUBMITTED",
                "配方 " + r.getCode() + " v" + v.getVersionNo() + " 待审批",
                (RmsAuditService.currentActor() == null ? "" : RmsAuditService.currentActor() + " 发起签核")
                        + (diffCount > 0 ? "（含 " + diffCount + " 项参数差异）" : "")
                        + (reason == null || reason.isBlank() ? "" : " · 原因：" + reason),
                null, "APPROVER", "SIGNOFF", saved.getId());
        return detail(saved.getId());
    }

    // ==================== 审批 ====================

    /** 审批通过（版本生效，Req 14 单生效）。 */
    @Transactional
    public SignoffView approve(String signoffId, String comment) {
        return doApprove(signoffId, comment);
    }

    /** 审批拒绝（版本退回 DRAFT）。 */
    @Transactional
    public SignoffView reject(String signoffId, String comment) {
        return doReject(signoffId, comment);
    }

    private SignoffView doApprove(String signoffId, String comment) {
        RecipeSignoff s = requirePending(signoffId);
        Recipe r = requireRecipe(s.getRecipeId());
        RecipeVersion v = requireVersion(s.getVersionId());
        if (v.getStatus() != RecipeVersionStatus.PENDING_SIGN) {
            throw new BizException(BizCode.PARAM_INVALID, "版本非签核中，无法通过，当前: " + v.getStatus());
        }
        String oldNo = null;
        String prevActiveId = r.getActiveVersionId();
        if (prevActiveId != null && !prevActiveId.equals(v.getId())) {
            RecipeVersion old = versionRepository.findById(prevActiveId).orElse(null);
            if (old != null && old.getStatus() == RecipeVersionStatus.ACTIVE) {
                old.setStatus(RecipeVersionStatus.OBSOLETE);
                versionRepository.save(old);
                oldNo = "v" + old.getVersionNo();
            }
        }
        v.setStatus(RecipeVersionStatus.ACTIVE);
        v.setActivatedAt(Instant.now());
        v.setActivatedBy(RmsAuditService.currentActor());
        versionRepository.save(v);
        r.setActiveVersionId(v.getId());
        recipeRepository.save(r);
        // FR-Q3：资格随新版本继承（EXACT_VERSION 资格自动指向新版本）
        qualificationService.inheritOnActivate(r.getId(), prevActiveId, v.getId());

        Instant now = Instant.now();
        String actor = RmsAuditService.currentActor();
        s.setStatus(SignoffStatus.APPROVED);
        s.setApprover(actor);
        s.setDecisionComment(comment);
        s.setDecidedAt(now);
        s.setESignHash(eSign(signoffId, "APPROVE", actor, now, comment));
        cancelTempAllowFields(s);   // Req 20：签核通过后预设自动取消
        RecipeSignoff saved = signoffRepository.save(s);

        event(signoffId, "APPROVED", actor, comment,
                "签核通过并生效" + (oldNo != null ? "，旧 " + oldNo + " 转 OBSOLETE" : "")
                        + "（eSign " + shortHash(saved.getESignHash()) + "）");
        audit.success("SIGNOFF_APPROVED", r.getCode(),
                "v" + v.getVersionNo() + " 签核通过并生效" + (oldNo != null ? "，旧 " + oldNo + " 转 OBSOLETE" : ""));
        log.info("[signoff] 通过 signoff={} recipe={} version=v{}", signoffId, r.getCode(), v.getVersionNo());
        // W11 变更效果挂钩：版本生效即登记（相对上一生效版的参数差异项数）
        changeEffectService.record(r.getId(), r.getCode(), prevActiveId,
                oldNo == null ? null : Integer.valueOf(oldNo.replace("v", "")),
                v.getId(), v.getVersionNo(), countParamDiff(s.getParamDiff()), v.getChangeSummary());
        // W9 通知：决定 → 通知发起人
        notificationService.publish("SIGNOFF_APPROVED",
                "配方 " + r.getCode() + " v" + v.getVersionNo() + " 已生效",
                "签核通过" + (oldNo != null ? "，旧 " + oldNo + " 转 OBSOLETE" : "")
                        + (comment == null || comment.isBlank() ? "" : " · 意见：" + comment),
                s.getInitiator(), null, "SIGNOFF", signoffId);
        return toView(saved);
    }

    private SignoffView doReject(String signoffId, String comment) {
        RecipeSignoff s = requirePending(signoffId);
        Recipe r = requireRecipe(s.getRecipeId());
        RecipeVersion v = requireVersion(s.getVersionId());
        if (v.getStatus() != RecipeVersionStatus.PENDING_SIGN) {
            throw new BizException(BizCode.PARAM_INVALID, "版本非签核中，无法拒绝，当前: " + v.getStatus());
        }
        v.setStatus(RecipeVersionStatus.DRAFT);
        versionRepository.save(v);

        Instant now = Instant.now();
        String actor = RmsAuditService.currentActor();
        s.setStatus(SignoffStatus.REJECTED);
        s.setApprover(actor);
        s.setDecisionComment(comment);
        s.setDecidedAt(now);
        s.setESignHash(eSign(signoffId, "REJECT", actor, now, comment));
        cancelTempAllowFields(s);
        RecipeSignoff saved = signoffRepository.save(s);

        event(signoffId, "REJECTED", actor, comment,
                "签核被拒，退回 DRAFT" + (comment != null && !comment.isBlank() ? "：" + comment : "")
                        + "（eSign " + shortHash(saved.getESignHash()) + "）");
        audit.success("SIGNOFF_REJECTED", r.getCode(),
                "v" + v.getVersionNo() + " 签核被拒，退回 DRAFT"
                        + (comment != null && !comment.isBlank() ? "：" + comment : ""));
        log.info("[signoff] 拒绝 signoff={} recipe={} version=v{}", signoffId, r.getCode(), v.getVersionNo());
        // W9 通知：决定 → 通知发起人
        notificationService.publish("SIGNOFF_REJECTED",
                "配方 " + r.getCode() + " v" + v.getVersionNo() + " 签核被拒",
                "已退回 DRAFT" + (comment == null || comment.isBlank() ? "" : " · 意见：" + comment),
                s.getInitiator(), null, "SIGNOFF", signoffId);
        return toView(saved);
    }

    /**
     * 批量审批（Req 3 / FR-L2）：逐项独立事务，返回逐项结果（部分成功语义）。
     *
     * @param ids      签核单 id 列表
     * @param decision {@code APPROVE} / {@code REJECT}
     * @param comment  统一审批意见
     */
    public List<BatchResult> batchDecision(List<String> ids, String decision, String comment) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "请选择至少一条签核单");
        }
        String act = decision == null ? "APPROVE" : decision.trim().toUpperCase(Locale.ROOT);
        if (!act.equals("APPROVE") && !act.equals("REJECT")) {
            throw new BizException(BizCode.PARAM_INVALID, "decision 仅支持 APPROVE/REJECT");
        }
        TransactionTemplate tx = new TransactionTemplate(txManager);
        List<BatchResult> results = new ArrayList<>();
        for (String id : ids) {
            try {
                tx.executeWithoutResult(st -> {
                    if (act.equals("REJECT")) {
                        doReject(id, comment);
                    } else {
                        doApprove(id, comment);
                    }
                });
                results.add(new BatchResult(id, true, null));
            } catch (Exception e) {
                String msg = e instanceof BizException ? e.getMessage() : "处理失败: " + e.getMessage();
                results.add(new BatchResult(id, false, msg));
                log.warn("[signoff] 批量{}单项失败 id={} err={}", act, id, msg);
            }
        }
        return results;
    }

    // ==================== 预设限时使用（Req 20 / FR-L5） ====================

    /** 为 PENDING 版本预设限时放行窗口（超时自动回落，通过/拒绝自动取消）。 */
    @Transactional
    public SignoffView presetTempAllow(String signoffId, String from, String to, String scope, String reason) {
        RecipeSignoff s = requirePending(signoffId);
        Instant start = parseInstant(from);
        Instant end = parseInstant(to);
        if (end == null) {
            throw new BizException(BizCode.PARAM_INVALID, "必须指定放行截止时间");
        }
        if (start == null) {
            start = Instant.now();
        }
        if (!end.isAfter(start)) {
            throw new BizException(BizCode.PARAM_INVALID, "截止时间必须晚于起始时间");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "限时放行必须填写原因（强制留痕）");
        }
        s.setTempAllowFrom(start);
        s.setTempAllowTo(end);
        s.setTempAllowScope(scope);
        s.setTempAllowReason(reason);
        s.setTempAllowBy(RmsAuditService.currentActor());
        s.setTempAllowAt(Instant.now());
        RecipeSignoff saved = signoffRepository.save(s);

        event(signoffId, "TEMP_ALLOWED", RmsAuditService.currentActor(), reason,
                "预设限时放行：" + fmtInstant(start) + " ~ " + fmtInstant(end)
                        + (scope != null && !scope.isBlank() ? " · 范围 " + scope : ""));
        Recipe r = recipeRepository.findById(s.getRecipeId()).orElse(null);
        audit.success("SIGNOFF_TEMP_ALLOWED", r == null ? null : r.getCode(),
                fmtInstant(start) + " ~ " + fmtInstant(end) + " 限时放行（" + reason + "）");
        return toView(saved);
    }

    /** 取消预设限时放行。 */
    @Transactional
    public SignoffView cancelTempAllow(String signoffId, String reason) {
        RecipeSignoff s = requirePending(signoffId);
        if (s.getTempAllowTo() == null) {
            throw new BizException(BizCode.PARAM_INVALID, "该签核单未设置限时放行");
        }
        cancelTempAllowFields(s);
        RecipeSignoff saved = signoffRepository.save(s);

        event(signoffId, "TEMP_CANCELLED", RmsAuditService.currentActor(), reason, "取消预设限时放行");
        Recipe r = recipeRepository.findById(s.getRecipeId()).orElse(null);
        audit.success("SIGNOFF_TEMP_CANCELLED", r == null ? null : r.getCode(), "取消限时放行");
        return toView(saved);
    }

    private static void cancelTempAllowFields(RecipeSignoff s) {
        s.setTempAllowFrom(null);
        s.setTempAllowTo(null);
        s.setTempAllowScope(null);
        s.setTempAllowReason(null);
        s.setTempAllowBy(null);
        s.setTempAllowAt(null);
    }

    // ==================== 查询 ====================

    /** 签核单列表（可按状态 / 关键字过滤，时间倒序）。 */
    public List<SignoffView> list(SignoffStatus status, String keyword) {
        List<RecipeSignoff> rows = status == null
                ? signoffRepository.findByDeletedFalseOrderByCreateTimeDesc()
                : signoffRepository.findByDeletedFalseAndStatusOrderByCreateTimeDesc(status);
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<SignoffView> out = new ArrayList<>();
        for (RecipeSignoff s : rows) {
            SignoffView v = toView(s);
            if (kw != null) {
                String hay = ((v.recipeCode() == null ? "" : v.recipeCode()) + " "
                        + (v.recipeName() == null ? "" : v.recipeName()) + " "
                        + (v.changeSummary() == null ? "" : v.changeSummary()) + " "
                        + (v.reason() == null ? "" : v.reason()) + " "
                        + (v.initiator() == null ? "" : v.initiator())).toLowerCase(Locale.ROOT);
                if (!hay.contains(kw)) {
                    continue;
                }
            }
            out.add(v);
        }
        return out;
    }

    /** 待办/时效 KPI 汇总（供签核页顶部指标块）。 */
    public SignoffSummary summary() {
        List<RecipeSignoff> all = signoffRepository.findByDeletedFalseOrderByCreateTimeDesc();
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        Instant soon = Instant.now().plus(Duration.ofHours(EXPIRING_HOURS));
        int pending = 0, approvedToday = 0, rejectedToday = 0, tempActive = 0, expiringSoon = 0;
        for (RecipeSignoff s : all) {
            if (s.getStatus() == SignoffStatus.PENDING) {
                pending++;
                TempAllow ta = tempAllowStatus(s);
                if (ta == TempAllow.ACTIVE) {
                    tempActive++;
                    if (s.getTempAllowTo() != null && s.getTempAllowTo().isBefore(soon)) {
                        expiringSoon++;
                    }
                }
            } else if (s.getDecidedAt() != null) {
                LocalDate d = LocalDateTime.ofInstant(s.getDecidedAt(), zone).toLocalDate();
                if (d.equals(today)) {
                    if (s.getStatus() == SignoffStatus.APPROVED) {
                        approvedToday++;
                    } else if (s.getStatus() == SignoffStatus.REJECTED) {
                        rejectedToday++;
                    }
                }
            }
        }
        return new SignoffSummary(pending, approvedToday, rejectedToday, tempActive, expiringSoon);
    }

    /** 签核单详情（含参数差异附件与事件时间线）。 */
    public SignoffDetail detail(String signoffId) {
        RecipeSignoff s = signoffRepository.findById(signoffId).orElse(null);
        if (s == null || Boolean.TRUE.equals(s.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "签核单不存在: " + signoffId);
        }
        Recipe r = recipeRepository.findById(s.getRecipeId()).orElse(null);
        RecipeVersion v = versionRepository.findById(s.getVersionId()).orElse(null);

        List<RecipeCompareService.DiffItem> paramDiff = new ArrayList<>();
        if (s.getParamDiff() != null && !s.getParamDiff().isBlank()) {
            try {
                paramDiff = objectMapper.readValue(s.getParamDiff(),
                        new TypeReference<List<RecipeCompareService.DiffItem>>() { });
            } catch (Exception e) {
                log.warn("[signoff] 解析参数差异失败 id={} err={}", signoffId, e.toString());
            }
        }
        List<EventView> timeline = new ArrayList<>();
        for (RmsSignoffEvent e : eventRepository.findBySignoffIdAndDeletedFalseOrderByOccurredAtAsc(signoffId)) {
            timeline.add(new EventView(e.getEventType(), e.getActor(), e.getComment(), e.getDetail(),
                    fmtInstant(e.getOccurredAt())));
        }
        return new SignoffDetail(
                s.getId(), s.getRecipeId(),
                r == null ? null : r.getCode(), r == null ? null : r.getName(),
                s.getVersionId(), v == null ? null : v.getVersionNo(),
                s.getStatus().name(),
                v == null ? null : v.getChangeSummary(),
                s.getReason(),
                s.getSignRoute() == null ? SignRoute.SINGLE.name() : s.getSignRoute().name(),
                s.getInitiator(), s.getApprover(), s.getDecisionComment(),
                fmt(s.getCreateTime()),
                fmtInstant(s.getDecidedAt()),
                tempAllowStatus(s).name(),
                fmtInstant(s.getTempAllowFrom()), fmtInstant(s.getTempAllowTo()),
                s.getTempAllowScope(), s.getTempAllowReason(), s.getTempAllowBy(), fmtInstant(s.getTempAllowAt()),
                s.getESignHash(),
                paramDiff, timeline);
    }

    // ==================== helpers ====================

    private RecipeSignoff requirePending(String signoffId) {
        RecipeSignoff s = signoffRepository.findById(signoffId).orElse(null);
        if (s == null || Boolean.TRUE.equals(s.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "签核单不存在: " + signoffId);
        }
        if (s.getStatus() != SignoffStatus.PENDING) {
            throw new BizException(BizCode.PARAM_INVALID, "签核单已处理，当前: " + s.getStatus());
        }
        return s;
    }

    private Recipe requireRecipe(String id) {
        Recipe r = recipeRepository.findById(id).orElse(null);
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方不存在: " + id);
        }
        return r;
    }

    private RecipeVersion requireVersion(String versionId) {
        RecipeVersion v = versionRepository.findById(versionId).orElse(null);
        if (v == null || Boolean.TRUE.equals(v.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方版本不存在: " + versionId);
        }
        return v;
    }

    /** 派生限时放行状态（读取时按当前时刻判定，超时无需定时任务即自动回落，Req 20）。 */
    private static TempAllow tempAllowStatus(RecipeSignoff s) {
        if (s.getStatus() != SignoffStatus.PENDING || s.getTempAllowTo() == null) {
            return TempAllow.NONE;
        }
        Instant now = Instant.now();
        if (s.getTempAllowFrom() != null && now.isBefore(s.getTempAllowFrom())) {
            return TempAllow.SCHEDULED;
        }
        return now.isAfter(s.getTempAllowTo()) ? TempAllow.EXPIRED : TempAllow.ACTIVE;
    }

    /** 电子签证据链哈希（SHA-256(签核单|决策|操作人|时刻|意见)）。 */
    private static String eSign(String signoffId, String decision, String actor, Instant at, String comment) {
        String raw = signoffId + "|" + decision + "|" + (actor == null ? "" : actor) + "|" + at + "|"
                + (comment == null ? "" : comment);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private static String shortHash(String h) {
        return h == null ? "—" : h.substring(0, Math.min(12, h.length()));
    }

    private void event(String signoffId, String type, String actor, String comment, String detail) {
        RmsSignoffEvent e = new RmsSignoffEvent();
        e.setSignoffId(signoffId);
        e.setEventType(type);
        e.setActor(actor);
        e.setComment(comment);
        e.setDetail(detail);
        e.setOccurredAt(Instant.now());
        eventRepository.save(e);
    }

    /** 参数差异附件中的「变更项」计数（解析失败返回 0，不外抛）。 */
    private int countParamDiff(String paramDiffJson) {
        if (paramDiffJson == null || paramDiffJson.isBlank()) {
            return 0;
        }
        try {
            List<RecipeCompareService.DiffItem> items = objectMapper.readValue(paramDiffJson,
                    new TypeReference<List<RecipeCompareService.DiffItem>>() { });
            return (int) items.stream().filter(RecipeCompareService.DiffItem::changed).count();
        } catch (Exception e) {
            return 0;
        }
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        try {
            return Instant.parse(t);
        } catch (Exception ignore) {
            // 继续尝试本地时间
        }
        try {
            return LocalDateTime.parse(t).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception ignore) {
            // 继续尝试偏移时间
        }
        try {
            return java.time.OffsetDateTime.parse(t).toInstant();
        } catch (Exception e) {
            throw new BizException(BizCode.PARAM_INVALID, "时间格式无效: " + s);
        }
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String fmtInstant(Instant t) {
        return t == null ? null
                : LocalDateTime.ofInstant(t, ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ==================== 视图 ====================

    /** 限时放行派生状态。 */
    public enum TempAllow {
        /** 未设置。 */
        NONE,
        /** 已设置但尚未到起始时刻。 */
        SCHEDULED,
        /** 放行中。 */
        ACTIVE,
        /** 已超时回落。 */
        EXPIRED
    }

    /** 签核单列表行。 */
    public record SignoffView(
            String id,
            String recipeId,
            String recipeCode,
            String recipeName,
            String versionId,
            Integer versionNo,
            String status,
            String changeSummary,
            String reason,
            String signRoute,
            String initiator,
            String approver,
            String decisionComment,
            String createTime,
            String decidedTime,
            String tempAllowStatus,
            String tempAllowTo,
            int diffCount) {
    }

    /** 签核单详情（列表字段 + 差异附件 + 时间线 + 证据链）。 */
    public record SignoffDetail(
            String id,
            String recipeId,
            String recipeCode,
            String recipeName,
            String versionId,
            Integer versionNo,
            String status,
            String changeSummary,
            String reason,
            String signRoute,
            String initiator,
            String approver,
            String decisionComment,
            String createTime,
            String decidedTime,
            String tempAllowStatus,
            String tempAllowFrom,
            String tempAllowTo,
            String tempAllowScope,
            String tempAllowReason,
            String tempAllowBy,
            String tempAllowAt,
            String eSignHash,
            List<RecipeCompareService.DiffItem> paramDiff,
            List<EventView> timeline) {
    }

    /** 事件时间线项。 */
    public record EventView(String eventType, String actor, String comment, String detail, String occurredAt) {
    }

    /** 待办 KPI。 */
    public record SignoffSummary(int pending, int approvedToday, int rejectedToday,
                                 int tempActive, int expiringSoon) {
    }

    /** 批量决策逐项结果。 */
    public record BatchResult(String id, boolean ok, String message) {
    }

    private SignoffView toView(RecipeSignoff s) {
        Recipe r = recipeRepository.findById(s.getRecipeId()).orElse(null);
        RecipeVersion v = versionRepository.findById(s.getVersionId()).orElse(null);
        int diffCount = 0;
        if (s.getParamDiff() != null && !s.getParamDiff().isBlank()) {
            try {
                List<RecipeCompareService.DiffItem> items = objectMapper.readValue(s.getParamDiff(),
                        new TypeReference<List<RecipeCompareService.DiffItem>>() { });
                diffCount = (int) items.stream().filter(RecipeCompareService.DiffItem::changed).count();
            } catch (Exception ignore) {
                // 差异附件解析失败不影响列表
            }
        }
        return new SignoffView(
                s.getId(), s.getRecipeId(),
                r == null ? null : r.getCode(), r == null ? null : r.getName(),
                s.getVersionId(), v == null ? null : v.getVersionNo(),
                s.getStatus().name(),
                v == null ? null : v.getChangeSummary(),
                s.getReason(),
                s.getSignRoute() == null ? SignRoute.SINGLE.name() : s.getSignRoute().name(),
                s.getInitiator(), s.getApprover(), s.getDecisionComment(),
                fmt(s.getCreateTime()), fmtInstant(s.getDecidedAt()),
                tempAllowStatus(s).name(), fmtInstant(s.getTempAllowTo()), diffCount);
    }
}
