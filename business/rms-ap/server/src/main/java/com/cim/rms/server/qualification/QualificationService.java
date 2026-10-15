package com.cim.rms.server.qualification;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.device.Device;
import com.cim.rms.server.device.DeviceType;
import com.cim.rms.server.device.DeviceTypeRepository;
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
 * 配方-机台资格矩阵服务（FR-Q1~Q4）。
 *
 * <p>维护「配方 × 设备（含机型/区域分组）」的运行资格；{@link #check} 供 Run 货校验（IF-M1）与
 * 在线比对（IF-E3）调用，<b>默认拒绝</b>（未登记即 NOT_QUALIFIED，D8）；资格吊销即时生效
 * （无缓存）；变更全量审计（FR-Q4）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualificationService {

    private final RecipeQualificationRepository repository;
    private final DeviceRepository deviceRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final RecipeRepository recipeRepository;
    private final RmsAuditService audit;

    public List<QualView> list(String recipeId, String deviceId) {
        List<RecipeQualification> rows;
        if (recipeId != null && !recipeId.isBlank()) {
            rows = repository.findByRecipeIdAndDeletedFalse(recipeId);
        } else if (deviceId != null && !deviceId.isBlank()) {
            rows = repository.findByDeviceIdAndDeletedFalse(deviceId);
        } else {
            rows = repository.findByDeletedFalseOrderByCreateTimeDesc();
        }
        return rows.stream().map(this::toView).toList();
    }

    @Transactional
    public QualView grant(String recipeId, String versionPolicy, String versionId,
                          String deviceId, String deviceTypeId, String areaId,
                          String basis, Instant from, Instant to) {
        Recipe r = recipeRepository.findById(recipeId).orElse(null);
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方不存在: " + recipeId);
        }
        if ((deviceId == null || deviceId.isBlank()) && (deviceTypeId == null || deviceTypeId.isBlank())
                && (areaId == null || areaId.isBlank())) {
            throw new BizException(BizCode.PARAM_INVALID, "设备/机型/区域至少指定一项");
        }
        RecipeQualification q = new RecipeQualification();
        q.setRecipeId(recipeId);
        q.setVersionPolicy(versionPolicy == null || versionPolicy.isBlank()
                ? "LATEST_ACTIVE" : versionPolicy.toUpperCase());
        q.setVersionId(blankToNull(versionId));
        q.setDeviceId(blankToNull(deviceId));
        q.setDeviceTypeId(blankToNull(deviceTypeId));
        q.setAreaId(blankToNull(areaId));
        q.setBasis(basis);
        q.setStatus("GRANTED");
        q.setEffectiveFrom(from);
        q.setEffectiveTo(to);
        RecipeQualification saved = repository.save(q);
        audit.success("QUALIFICATION_GRANTED", r.getCode(),
                "授予资格 device=" + deviceId + " type=" + deviceTypeId + " area=" + areaId + " · " + basis);
        return toView(saved);
    }

    /** 吊销（即时生效）。 */
    @Transactional
    public QualView revoke(String id) {
        RecipeQualification q = require(id);
        q.setStatus("REVOKED");
        RecipeQualification saved = repository.save(q);
        audit.success("QUALIFICATION_REVOKED", null, "吊销资格 id=" + id + " recipeId=" + q.getRecipeId());
        return toView(saved);
    }

    @Transactional
    public void delete(String id) {
        RecipeQualification q = require(id);
        q.setDeleted(true);
        repository.save(q);
        audit.success("QUALIFICATION_DELETED", null, "删除资格 id=" + id);
    }

    /**
     * 资格判定（IF-M1/IF-E3 复用）：未登记或已吊销或不在生效期 → NOT_QUALIFIED（默认拒绝）。
     * <p>不校验版本（兼容旧调用）。需要版本联动时用 {@link #check(String, String, String)}。</p>
     */
    public QualCheck check(String recipeId, String deviceId) {
        return check(recipeId, deviceId, null);
    }

    /**
     * 资格判定（含<b>版本策略</b>，FR-Q1/Q2）：
     * <ul>
     *   <li>{@code LATEST_ACTIVE}：对配方任意生效版本均成立；</li>
     *   <li>{@code EXACT_VERSION}：仅当 {@code versionId} 与资格登记的版本一致时成立。</li>
     * </ul>
     * {@code versionId} 传 {@code null} 时退化为不校验版本（兼容）。
     */
    public QualCheck check(String recipeId, String deviceId, String versionId) {
        Device d = deviceId == null ? null : deviceRepository.findById(deviceId).orElse(null);
        String typeId = d == null ? null : d.getDeviceTypeId();
        String areaId = d == null ? null : d.getAreaId();
        Instant now = Instant.now();
        for (RecipeQualification q : repository.findByRecipeIdAndDeletedFalse(recipeId)) {
            if (!"GRANTED".equalsIgnoreCase(q.getStatus()) || !inWindow(q, now)) {
                continue;
            }
            boolean match = (q.getDeviceId() != null && q.getDeviceId().equals(deviceId))
                    || (q.getDeviceTypeId() != null && q.getDeviceTypeId().equals(typeId))
                    || (q.getAreaId() != null && q.getAreaId().equals(areaId));
            if (!match) {
                continue;
            }
            if ("EXACT_VERSION".equalsIgnoreCase(q.getVersionPolicy()) && versionId != null) {
                if (q.getVersionId() == null || !q.getVersionId().equals(versionId)) {
                    continue; // 精确版本资格：版本不符则不成立
                }
            }
            return new QualCheck("QUALIFIED", q.getId(), q.getVersionPolicy(), null);
        }
        return new QualCheck("NOT_QUALIFIED", null, null,
                "该配方未登记在设备 " + (d == null ? deviceId : d.getCode()) + " 的运行资格（默认拒绝）");
    }

    /**
     * FR-Q3 资格随版本继承：新版本生效时，上一生效版本的 {@code EXACT_VERSION} 资格自动指向新版本。
     *
     * @return 继承（改写）的资格条数
     */
    @Transactional
    public int inheritOnActivate(String recipeId, String fromVersionId, String toVersionId) {
        if (fromVersionId == null || toVersionId == null || fromVersionId.equals(toVersionId)) {
            return 0;
        }
        int n = 0;
        for (RecipeQualification q : repository.findByRecipeIdAndDeletedFalse(recipeId)) {
            if (!"GRANTED".equalsIgnoreCase(q.getStatus()) || !fromVersionId.equals(q.getVersionId())) {
                continue;
            }
            q.setVersionId(toVersionId);
            repository.save(q);
            n++;
        }
        if (n > 0) {
            audit.success("QUALIFICATION_INHERITED", null,
                    "资格随新版本继承 · " + n + " 项 → version=" + toVersionId);
        }
        return n;
    }

    private static boolean inWindow(RecipeQualification q, Instant now) {
        if (q.getEffectiveFrom() != null && now.isBefore(q.getEffectiveFrom())) {
            return false;
        }
        return q.getEffectiveTo() == null || !now.isAfter(q.getEffectiveTo());
    }

    private RecipeQualification require(String id) {
        RecipeQualification q = repository.findById(id).orElse(null);
        if (q == null || Boolean.TRUE.equals(q.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "资格记录不存在: " + id);
        }
        return q;
    }

    private QualView toView(RecipeQualification q) {
        String recipeCode = recipeRepository.findById(q.getRecipeId()).map(Recipe::getCode).orElse(null);
        String deviceCode = q.getDeviceId() == null ? null
                : deviceRepository.findById(q.getDeviceId()).map(Device::getCode).orElse(null);
        String typeName = q.getDeviceTypeId() == null ? null
                : deviceTypeRepository.findById(q.getDeviceTypeId()).map(DeviceType::getName).orElse(null);
        return new QualView(q.getId(), q.getRecipeId(), recipeCode, q.getVersionPolicy(), q.getVersionId(),
                q.getDeviceId(), deviceCode, q.getDeviceTypeId(), typeName, q.getAreaId(),
                q.getBasis(), q.getStatus(), fmt(q.getEffectiveFrom()), fmt(q.getEffectiveTo()),
                fmt(q.getCreateTime()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String fmt(Instant t) {
        return t == null ? null : LocalDateTime.ofInstant(t, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 资格视图。 */
    public record QualView(String id, String recipeId, String recipeCode, String versionPolicy, String versionId,
                           String deviceId, String deviceCode, String deviceTypeId, String deviceTypeName,
                           String areaId, String basis, String status,
                           String effectiveFrom, String effectiveTo, String createTime) {
    }

    /** 资格判定结果。 */
    public record QualCheck(String result, String qualificationId, String versionPolicy, String reason) {
    }
}
