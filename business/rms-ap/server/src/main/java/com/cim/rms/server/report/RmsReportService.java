package com.cim.rms.server.report;

import com.cim.rms.server.audit.RmsAuditLogRepository;
import com.cim.rms.server.bypass.RmsBypassRepository;
import com.cim.rms.server.compare.RmsCompareLogRepository;
import com.cim.rms.server.device.DeviceRepository;
import com.cim.rms.server.device.DeviceType;
import com.cim.rms.server.device.DeviceTypeRepository;
import com.cim.rms.server.ecsv.EcSvSpecRepository;
import com.cim.rms.server.ecsv.EcSvValueRepository;
import com.cim.rms.server.integration.RmsAlarmRepository;
import com.cim.rms.server.integration.RmsHoldRepository;
import com.cim.rms.server.qualification.RecipeQualificationRepository;
import com.cim.rms.server.recipe.RecipeRepository;
import com.cim.rms.server.recipe.RecipeVersionRepository;
import com.cim.rms.server.recipe.RecipeVersionStatus;
import com.cim.rms.server.signoff.RecipeSignoffRepository;
import com.cim.rms.server.signoff.SignoffStatus;
import com.cim.rms.server.spec.ParamSpecRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表 / KPI 看板服务（FR-RPT1/2）：聚合各域当前态，供概览页与导出。
 *
 * <p>指标：配方/版本/规格规模、签核待办、比对通过率、Hold/报警存量、资格数、Bypass 数、
 * EC-SV 通过率，以及按机型的分布。全部实时聚合（当前为内存库规模；生产可加缓存/物化）。</p>
 */
@Service
@RequiredArgsConstructor
public class RmsReportService {

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final ParamSpecRepository specRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final RecipeSignoffRepository signoffRepository;
    private final RmsCompareLogRepository compareLogRepository;
    private final RmsHoldRepository holdRepository;
    private final RmsAlarmRepository alarmRepository;
    private final RecipeQualificationRepository qualificationRepository;
    private final RmsBypassRepository bypassRepository;
    private final EcSvSpecRepository ecsvSpecRepository;
    private final EcSvValueRepository ecsvValueRepository;
    private final RmsAuditLogRepository auditLogRepository;

    /** 概览 KPI（FR-RPT1）。 */
    public Dashboard dashboard() {
        long recipes = recipeRepository.countByDeletedFalse();
        long golden = recipeRepository.countByGoldenAndDeletedFalse(true);
        long versions = versionRepository.countByDeletedFalse();
        long activeVersions = versionRepository.countByStatusAndDeletedFalse(RecipeVersionStatus.ACTIVE);
        long specs = specRepository.countByDeletedFalse();

        long devices = deviceRepository.findAll().stream()
                .filter(d -> !Boolean.TRUE.equals(d.getDeleted())).count();

        long pendingSignoff = signoffRepository.findByDeletedFalseAndStatusOrderByCreateTimeDesc(SignoffStatus.PENDING).size();

        List<com.cim.rms.server.compare.RmsCompareLog> comps = compareLogRepository.findByDeletedFalseOrderByCreateTimeDesc();
        long cmpPass = comps.stream().filter(c -> c.isIdentical()).count();
        long cmpFail = comps.size() - cmpPass;
        double passRate = comps.isEmpty() ? 100.0 : Math.round(cmpPass * 1000.0 / comps.size()) / 10.0;

        long holds = holdRepository.countByActiveTrueAndDeletedFalse();
        long alarms = alarmRepository.countByAcknowledgedFalseAndDeletedFalse();
        long qualified = qualificationRepository.countByStatusAndDeletedFalse("GRANTED");
        long bypasses = bypassRepository.countByActiveTrueAndDeletedFalse();

        long ecsvPass = ecsvValueRepository.countByResultAndDeletedFalse("PASS");
        long ecsvFail = ecsvValueRepository.countByResultAndDeletedFalse("FAIL");
        long ecsvSpecs = ecsvSpecRepository.countByDeletedFalse();

        // 按机型分布（设备数）
        Map<String, String> typeNames = new LinkedHashMap<>();
        for (DeviceType t : deviceTypeRepository.findByDeletedFalseOrderByCode()) {
            typeNames.put(t.getId(), t.getName());
        }
        Map<String, Long> byType = new LinkedHashMap<>();
        deviceRepository.findAll().stream()
                .filter(d -> !Boolean.TRUE.equals(d.getDeleted()))
                .forEach(d -> byType.merge(
                        typeNames.getOrDefault(d.getDeviceTypeId(), "未分类"), 1L, Long::sum));
        List<TypeDist> typeDist = byType.entrySet().stream()
                .map(e -> new TypeDist(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(TypeDist::count).reversed())
                .toList();

        long auditTotal = auditLogRepository.findByDeletedFalseOrderByCreateTimeDesc().size();

        return new Dashboard(recipes, golden, versions, activeVersions, specs, devices,
                pendingSignoff, comps.size(), cmpPass, cmpFail, passRate,
                holds, alarms, qualified, bypasses,
                ecsvSpecs, ecsvPass, ecsvFail, auditTotal, typeDist);
    }

    /** 概览 KPI 数据。 */
    public record Dashboard(long recipes, long goldenRecipes, long versions, long activeVersions, long specs,
                            long devices, long pendingSignoff, long comparisons, long comparePass,
                            long compareFail, double comparePassRate, long activeHolds, long unackAlarms,
                            long qualifications, long activeBypasses, long ecsvSpecs, long ecsvPass,
                            long ecsvFail, long auditTotal, List<TypeDist> deviceByType) {
    }

    public record TypeDist(String name, long count) {
    }
}
