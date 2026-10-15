package com.cim.rms.server.golden;

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
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Golden 机差覆盖层服务（FR-G1~G3，Req 16/30/31）。
 *
 * <p>覆盖层提交后进入 PENDING（D3：变更走签核），经 {@link #approve} 批准方生效；
 * {@link #effective} 返回某设备在指定配方下已批准的放宽区间，供 Golden 比对合并判定。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoldenService {

    private final GoldenOverrideRepository repository;
    private final DeviceRepository deviceRepository;
    private final RecipeRepository recipeRepository;
    private final RmsAuditService audit;

    public List<OverrideView> list(String deviceId, String status) {
        List<GoldenOverride> rows;
        if (deviceId != null && !deviceId.isBlank()) {
            rows = repository.findByDeviceIdAndStatusAndDeletedFalse(deviceId,
                    status == null || status.isBlank() ? "APPROVED" : status.toUpperCase());
        } else if (status != null && !status.isBlank()) {
            rows = repository.findByStatusAndDeletedFalse(status.toUpperCase());
        } else {
            rows = repository.findByDeletedFalseOrderByCreateTimeDesc();
        }
        return rows.stream().map(this::toView).toList();
    }

    /** 提交机差覆盖层（进入签核，D3）。 */
    @Transactional
    public OverrideView submit(String deviceId, String recipeId, String paramKey,
                               String allowMin, String allowMax, String reason) {
        Device d = deviceRepository.findById(deviceId).orElse(null);
        if (d == null || Boolean.TRUE.equals(d.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + deviceId);
        }
        if (paramKey == null || paramKey.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "参数键不能为空");
        }
        GoldenOverride o = new GoldenOverride();
        o.setDeviceId(deviceId);
        o.setRecipeId(blankToNull(recipeId));
        o.setParamKey(paramKey.trim());
        o.setAllowMin(allowMin);
        o.setAllowMax(allowMax);
        o.setReason(reason);
        o.setStatus("PENDING");
        GoldenOverride saved = repository.save(o);
        audit.success("GOLDEN_OVERRIDE_SUBMITTED", d.getCode(),
                "机差覆盖 " + paramKey + " [" + allowMin + "," + allowMax + "] 待签核");
        return toView(saved);
    }

    /** 批准（签核通过，D3）。 */
    @Transactional
    public OverrideView approve(String id, String comment) {
        GoldenOverride o = require(id);
        o.setStatus("APPROVED");
        o.setApprovedBy(RmsAuditService.currentActor());
        o.setApprovedAt(Instant.now());
        if (comment != null && !comment.isBlank()) {
            o.setDescription(comment);
        }
        GoldenOverride saved = repository.save(o);
        audit.success("GOLDEN_OVERRIDE_APPROVED", null, "机差覆盖批准 " + o.getParamKey());
        return toView(saved);
    }

    @Transactional
    public OverrideView reject(String id, String comment) {
        GoldenOverride o = require(id);
        o.setStatus("REJECTED");
        if (comment != null && !comment.isBlank()) {
            o.setDescription(comment);
        }
        GoldenOverride saved = repository.save(o);
        audit.success("GOLDEN_OVERRIDE_REJECTED", null, "机差覆盖拒绝 " + o.getParamKey());
        return toView(saved);
    }

    @Transactional
    public void delete(String id) {
        GoldenOverride o = require(id);
        o.setDeleted(true);
        repository.save(o);
        audit.success("GOLDEN_OVERRIDE_DELETED", null, "删除机差覆盖 " + o.getParamKey());
    }

    /**
     * 某设备在指定配方下的生效放宽区间（APPROVED 且 recipeId 匹配或为通用）。
     */
    public List<OverrideView> effective(String deviceId, String recipeId) {
        return repository.findByDeviceIdAndStatusAndDeletedFalse(deviceId, "APPROVED").stream()
                .filter(o -> o.getRecipeId() == null || o.getRecipeId().isBlank()
                        || o.getRecipeId().equals(recipeId))
                .map(this::toView).toList();
    }

    /** 是否处于放宽区间内（Golden 比对合并判定用）。 */
    public boolean withinOverride(String deviceId, String recipeId, String paramKey, String actual) {
        if (actual == null || actual.isBlank()) {
            return false;
        }
        double av;
        try {
            av = Double.parseDouble(actual.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        for (GoldenOverride o : repository.findByDeviceIdAndStatusAndDeletedFalse(deviceId, "APPROVED")) {
            if (!paramKey.equals(o.getParamKey())) {
                continue;
            }
            if (o.getRecipeId() != null && !o.getRecipeId().isBlank() && !o.getRecipeId().equals(recipeId)) {
                continue;
            }
            boolean lo = o.getAllowMin() == null || o.getAllowMin().isBlank() || av >= parse(o.getAllowMin(), Double.NEGATIVE_INFINITY);
            boolean hi = o.getAllowMax() == null || o.getAllowMax().isBlank() || av <= parse(o.getAllowMax(), Double.POSITIVE_INFINITY);
            if (lo && hi) {
                return true;
            }
        }
        return false;
    }

    private static double parse(String s, double dft) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return dft;
        }
    }

    private GoldenOverride require(String id) {
        GoldenOverride o = repository.findById(id).orElse(null);
        if (o == null || Boolean.TRUE.equals(o.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "机差覆盖不存在: " + id);
        }
        return o;
    }

    private OverrideView toView(GoldenOverride o) {
        String deviceCode = deviceRepository.findById(o.getDeviceId()).map(Device::getCode).orElse(null);
        String recipeCode = o.getRecipeId() == null ? null
                : recipeRepository.findById(o.getRecipeId()).map(Recipe::getCode).orElse(null);
        return new OverrideView(o.getId(), o.getDeviceId(), deviceCode, o.getRecipeId(), recipeCode,
                o.getParamKey(), o.getAllowMin(), o.getAllowMax(), o.getReason(), o.getStatus(),
                o.getApprovedBy(), fmt(o.getApprovedAt()), fmt(o.getCreateTime()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String fmt(Instant t) {
        return t == null ? null : LocalDateTime.ofInstant(t, java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 机差覆盖视图。 */
    public record OverrideView(String id, String deviceId, String deviceCode, String recipeId, String recipeCode,
                               String paramKey, String allowMin, String allowMax, String reason, String status,
                               String approvedBy, String approvedAt, String createTime) {
    }
}
