package com.cim.rms.server.bypass;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.device.Device;
import com.cim.rms.server.device.DeviceRepository;
import com.cim.rms.server.recipe.Recipe;
import com.cim.rms.server.recipe.RecipeRepository;
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
import java.util.List;

/**
 * Bypass 服务（FR-B1~B3，Req 22/23）。
 *
 * <p>提供设备级/配方级豁免的维护与「是否被豁免」判定（供在线比对、参数检查、Run 校验复用）。
 * 判定口径：{@code active=true} 且（无起止或无 from ≤ now ≤ to）。状态为读时派生
 * （SCHEDULED/ACTIVE/EXPIRED），到期自动失效；变更一律落高敏感审计（NFR-5）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BypassService {

    private final RmsBypassRepository repository;
    private final DeviceRepository deviceRepository;
    private final RecipeRepository recipeRepository;
    private final RmsAuditService audit;

    public List<BypassView> list() {
        return repository.findByDeletedFalseOrderByCreateTimeDesc().stream().map(this::toView).toList();
    }

    @Transactional
    public BypassView create(String scope, String targetId, String bypassType, String reason,
                            Instant from, Instant to) {
        String sc = scope == null ? "" : scope.trim().toUpperCase();
        if (!"DEVICE".equals(sc) && !"RECIPE".equals(sc)) {
            throw new BizException(BizCode.PARAM_INVALID, "Bypass 范围非法（DEVICE/RECIPE）: " + scope);
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "Bypass 必须填写原因（强制留痕）");
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new BizException(BizCode.PARAM_INVALID, "失效时间早于生效时间");
        }
        String label;
        if ("DEVICE".equals(sc)) {
            label = deviceRepository.findById(targetId).map(Device::getCode)
                    .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + targetId));
        } else {
            label = recipeRepository.findById(targetId).map(Recipe::getCode)
                    .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "配方不存在: " + targetId));
        }
        RmsBypass b = new RmsBypass();
        b.setScope(sc);
        b.setTargetId(targetId);
        b.setTargetLabel(label);
        b.setBypassType(normalizeType(bypassType));
        b.setReason(reason);
        b.setActive(true);
        b.setEffectiveFrom(from);
        b.setEffectiveTo(to);
        b.setCreatedBy(com.cim.rms.server.audit.RmsAuditService.currentActor());
        RmsBypass saved = repository.save(b);
        audit.success("BYPASS_CREATED", label, sc + " 豁免 " + saved.getBypassType() + " · " + reason);
        return toView(saved);
    }

    /** 撤销/启用（active 开关）。 */
    @Transactional
    public BypassView setActive(String id, boolean active) {
        RmsBypass b = require(id);
        b.setActive(active);
        audit.success(active ? "BYPASS_ENABLED" : "BYPASS_REVOKED", b.getTargetLabel(), "Bypass id=" + id);
        return toView(repository.save(b));
    }

    @Transactional
    public void delete(String id) {
        RmsBypass b = require(id);
        b.setDeleted(true);
        repository.save(b);
        audit.success("BYPASS_DELETED", b.getTargetLabel(), "删除 Bypass id=" + id);
    }

    /**
     * 是否被豁免。
     *
     * @param kind COMPARE（比对）/ PARAM_CHECK（参数检查）
     */
    public boolean isBypassed(String deviceId, String recipeId, String kind) {
        Instant now = Instant.now();
        for (RmsBypass b : repository.findByActiveTrueAndDeletedFalse()) {
            if (!inWindow(b, now) || !coversKind(b.getBypassType(), kind)) {
                continue;
            }
            if ("DEVICE".equals(b.getScope()) && deviceId != null && deviceId.equals(b.getTargetId())) {
                return true;
            }
            if ("RECIPE".equals(b.getScope()) && recipeId != null && recipeId.equals(b.getTargetId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean coversKind(String type, String kind) {
        if ("BOTH".equals(type)) {
            return true;
        }
        return type != null && type.equalsIgnoreCase(kind);
    }

    private static boolean inWindow(RmsBypass b, Instant now) {
        if (b.getEffectiveFrom() != null && now.isBefore(b.getEffectiveFrom())) {
            return false;
        }
        return b.getEffectiveTo() == null || !now.isAfter(b.getEffectiveTo());
    }

    private static String normalizeType(String t) {
        String v = t == null ? "BOTH" : t.trim().toUpperCase();
        if (!"COMPARE".equals(v) && !"PARAM_CHECK".equals(v) && !"BOTH".equals(v)) {
            throw new BizException(BizCode.PARAM_INVALID, "Bypass 类型非法（COMPARE/PARAM_CHECK/BOTH）: " + t);
        }
        return v;
    }

    private RmsBypass require(String id) {
        RmsBypass b = repository.findById(id).orElse(null);
        if (b == null || Boolean.TRUE.equals(b.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "Bypass 不存在: " + id);
        }
        return b;
    }

    private BypassView toView(RmsBypass b) {
        return new BypassView(b.getId(), b.getScope(), b.getTargetId(), b.getTargetLabel(),
                b.getBypassType(), b.getReason(), b.isActive(), status(b),
                fmt(b.getEffectiveFrom()), fmt(b.getEffectiveTo()), b.getCreatedBy(), fmt(b.getCreateTime()));
    }

    /** 读时派生状态（到期自动失效）。 */
    private static String status(RmsBypass b) {
        if (!b.isActive() || Boolean.TRUE.equals(b.getDeleted())) {
            return "REVOKED";
        }
        Instant now = Instant.now();
        if (b.getEffectiveFrom() != null && now.isBefore(b.getEffectiveFrom())) {
            return "SCHEDULED";
        }
        if (b.getEffectiveTo() != null && now.isAfter(b.getEffectiveTo())) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    private static String fmt(Instant t) {
        return t == null ? null : LocalDateTime.ofInstant(t, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** Bypass 视图。 */
    public record BypassView(String id, String scope, String targetId, String targetLabel,
                             String bypassType, String reason, boolean active, String status,
                             String effectiveFrom, String effectiveTo, String createdBy, String createTime) {
    }
}
