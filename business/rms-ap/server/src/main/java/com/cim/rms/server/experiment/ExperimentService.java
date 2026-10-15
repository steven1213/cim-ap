package com.cim.rms.server.experiment;

import com.cim.core.port.IdGenerator;
import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.device.Device;
import com.cim.rms.server.device.DeviceRepository;
import com.cim.rms.server.recipe.Recipe;
import com.cim.rms.server.recipe.RecipeRepository;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 实验配方通道服务（W11）。
 *
 * <p>状态机：{@code REQUESTED → APPROVED / REJECTED}；APPROVED 可主动 {@code CLOSED}。
 * 放行判定 {@link #activeFor}：APPROVED 且当前时刻在窗口内（validFrom 缺省 = 建单时刻起）。
 * 窗口过期的 APPROVED 单在视图中派生 {@code expired=true}（无定时任务，读取时判定）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentService {

    private final RmsExperimentRepository experimentRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final DeviceRepository deviceRepository;
    private final IdGenerator idGenerator;
    private final RmsAuditService audit;

    /** 实验窗口默认上限（天）——防止忘关的无限期实验。 */
    private static final long MAX_WINDOW_DAYS = 30;

    // ==================== 查询 ====================

    public List<ExperimentView> list(String status) {
        List<RmsExperiment> rows = (status == null || status.isBlank())
                ? experimentRepository.findByDeletedFalseOrderByCreateTimeDesc()
                : experimentRepository.findByStatusAndDeletedFalse(status.toUpperCase());
        List<ExperimentView> out = new ArrayList<>();
        for (RmsExperiment e : rows) {
            out.add(toView(e));
        }
        return out;
    }

    /** 放行判定（IF-E2 闸门调用）：指定机台 × 指定版本存在窗口内的 APPROVED 实验单即放行。 */
    public boolean activeFor(String deviceId, String versionId) {
        if (deviceId == null || versionId == null) {
            return false;
        }
        Instant now = Instant.now();
        return experimentRepository.findByDeviceIdAndStatusAndDeletedFalse(deviceId, "APPROVED").stream()
                .anyMatch(e -> versionId.equals(e.getVersionId())
                        && (e.getValidFrom() == null || !now.isBefore(e.getValidFrom()))
                        && e.getValidUntil() != null && !now.isAfter(e.getValidUntil()));
    }

    // ==================== 生命周期 ====================

    @Transactional
    public ExperimentView create(String recipeId, String versionId, String deviceId,
                                 String reason, String validFrom, String validUntil) {
        Recipe r = requireRecipe(recipeId);
        RecipeVersion v = requireVersion(versionId);
        if (!v.getRecipeId().equals(recipeId)) {
            throw new BizException(BizCode.PARAM_INVALID, "版本不属于该配方: " + versionId);
        }
        Device d = requireDevice(deviceId);
        Instant start = validFrom == null || validFrom.isBlank() ? Instant.now() : parseInstant(validFrom);
        Instant end = validUntil == null || validUntil.isBlank() ? null : parseInstant(validUntil);
        if (end == null) {
            throw new BizException(BizCode.PARAM_INVALID, "必须指定实验截止时间");
        }
        if (!end.isAfter(start)) {
            throw new BizException(BizCode.PARAM_INVALID, "截止时间必须晚于起始时间");
        }
        if (end.isAfter(start.plus(java.time.Duration.ofDays(MAX_WINDOW_DAYS)))) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "实验窗口不得超过 " + MAX_WINDOW_DAYS + " 天（防忘关的无限期实验）");
        }
        RmsExperiment e = new RmsExperiment();
        e.setCode("EXP-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase());
        e.setRecipeId(r.getId());
        e.setRecipeCode(r.getCode());
        e.setVersionId(v.getId());
        e.setVersionNo(v.getVersionNo());
        e.setDeviceId(d.getId());
        e.setDeviceCode(d.getCode());
        e.setReason(reason);
        e.setStatus("REQUESTED");
        e.setRequestedBy(RmsAuditService.currentActor());
        e.setValidFrom(start);
        e.setValidUntil(end);
        RmsExperiment saved = experimentRepository.save(e);
        audit.success("EXPERIMENT_REQUESTED", saved.getCode(),
                r.getCode() + " v" + v.getVersionNo() + " @ " + d.getCode());
        return toView(saved);
    }

    @Transactional
    public ExperimentView approve(String id, String comment) {
        RmsExperiment e = requireByStatus(id, "REQUESTED");
        e.setStatus("APPROVED");
        e.setDecidedBy(RmsAuditService.currentActor());
        e.setDecidedAt(Instant.now());
        RmsExperiment saved = experimentRepository.save(e);
        audit.success("EXPERIMENT_APPROVED", saved.getCode(),
                saved.getRecipeCode() + " v" + saved.getVersionNo()
                        + (comment == null || comment.isBlank() ? "" : "：" + comment));
        return toView(saved);
    }

    @Transactional
    public ExperimentView reject(String id, String comment) {
        RmsExperiment e = requireByStatus(id, "REQUESTED");
        e.setStatus("REJECTED");
        e.setDecidedBy(RmsAuditService.currentActor());
        e.setDecidedAt(Instant.now());
        RmsExperiment saved = experimentRepository.save(e);
        audit.success("EXPERIMENT_REJECTED", saved.getCode(),
                saved.getRecipeCode() + (comment == null || comment.isBlank() ? "" : "：" + comment));
        return toView(saved);
    }

    @Transactional
    public ExperimentView close(String id, String reason) {
        RmsExperiment e = requireByStatus(id, "APPROVED");
        e.setStatus("CLOSED");
        e.setClosedAt(Instant.now());
        e.setCloseReason(reason == null || reason.isBlank() ? "实验结束" : reason);
        RmsExperiment saved = experimentRepository.save(e);
        audit.success("EXPERIMENT_CLOSED", saved.getCode(), saved.getRecipeCode() + "：" + saved.getCloseReason());
        return toView(saved);
    }

    // ==================== helpers ====================

    private RmsExperiment requireByStatus(String id, String expected) {
        RmsExperiment e = experimentRepository.findById(id).orElse(null);
        if (e == null || Boolean.TRUE.equals(e.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "实验单不存在: " + id);
        }
        if (!expected.equals(e.getStatus())) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "实验单状态须为 " + expected + "，当前: " + e.getStatus());
        }
        return e;
    }

    private Recipe requireRecipe(String id) {
        Recipe r = recipeRepository.findById(id).orElse(null);
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            r = id == null ? null : recipeRepository.findByCodeAndDeletedFalse(id).orElse(null);
        }
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方不存在: " + id);
        }
        return r;
    }

    private RecipeVersion requireVersion(String id) {
        RecipeVersion v = versionRepository.findById(id).orElse(null);
        if (v == null || Boolean.TRUE.equals(v.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方版本不存在: " + id);
        }
        return v;
    }

    private Device requireDevice(String id) {
        Device d = deviceRepository.findById(id).orElse(null);
        if (d == null || Boolean.TRUE.equals(d.getDeleted())) {
            d = id == null ? null : deviceRepository.findByCodeAndDeletedFalse(id).orElse(null);
        }
        if (d == null || Boolean.TRUE.equals(d.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + id);
        }
        return d;
    }

    private static ExperimentView toView(RmsExperiment e) {
        boolean expired = "APPROVED".equals(e.getStatus())
                && e.getValidUntil() != null && Instant.now().isAfter(e.getValidUntil());
        return new ExperimentView(e.getId(), e.getCode(), e.getRecipeId(), e.getRecipeCode(),
                e.getVersionId(), e.getVersionNo(), e.getDeviceId(), e.getDeviceCode(),
                e.getReason(), e.getStatus(), e.getRequestedBy(), e.getDecidedBy(),
                fmt(e.getDecidedAt()), fmt(e.getValidFrom()), fmt(e.getValidUntil()),
                fmt(e.getClosedAt()), e.getCloseReason(), expired);
    }

    private static Instant parseInstant(String s) {
        String t = s.trim();
        try {
            return Instant.parse(t);
        } catch (Exception ignore) {
            // try local
        }
        try {
            return LocalDateTime.parse(t).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            throw new BizException(BizCode.PARAM_INVALID, "时间格式无效: " + s);
        }
    }

    private static String fmt(Instant t) {
        return t == null ? null
                : LocalDateTime.ofInstant(t, ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 实验单视图（expired 为派生字段：APPROVED 但已过窗口）。 */
    public record ExperimentView(String id, String code, String recipeId, String recipeCode,
                                 String versionId, Integer versionNo, String deviceId, String deviceCode,
                                 String reason, String status, String requestedBy, String decidedBy,
                                 String decidedAt, String validFrom, String validUntil,
                                 String closedAt, String closeReason, boolean expired) {
    }
}
