package com.cim.rms.server.ecsv;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.device.Device;
import com.cim.rms.server.device.DeviceRepository;
import com.cim.rms.server.spec.SpecEvaluator;
import com.cim.rms.server.spec.SpecType;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * EC/SV 服务（FR-D2~D4，Req 43/44/45）。
 *
 * <p>规格按配方/产品双维度维护；{@link #upload} 接收 EAP 上报的 EC/SV 实际值，逐项按规格判定
 * （target/range/tolerance，复用 {@link SpecEvaluator}），落 {@link EcSvValue} 留痕并返回失败明细
 * （Req 45：记录比对失败具体信息）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EcSvService {

    private final EcSvSpecRepository specRepository;
    private final EcSvValueRepository valueRepository;
    private final DeviceRepository deviceRepository;
    private final RmsAuditService audit;

    // ---------- 规格 ----------

    public List<SpecView> listSpecs(String scope, String refId) {
        List<EcSvSpec> rows = (scope != null && !scope.isBlank() && refId != null && !refId.isBlank())
                ? specRepository.findByScopeAndRefIdAndDeletedFalseOrderByParamKeyAsc(scope.toUpperCase(), refId)
                : specRepository.findByDeletedFalseOrderByCreateTimeDesc();
        return rows.stream().map(EcSvService::toSpecView).toList();
    }

    @Transactional
    public SpecView saveSpec(String id, SpecInput in) {
        EcSvSpec s = id == null ? null : requireSpec(id);
        boolean created = s == null;
        if (created) {
            s = new EcSvSpec();
        }
        if (in.scope() == null || in.refId() == null || in.paramKey() == null) {
            throw new BizException(BizCode.PARAM_INVALID, "scope/refId/paramKey 不能为空");
        }
        s.setScope(in.scope().toUpperCase());
        s.setRefId(in.refId());
        s.setEcsvType(normalizeType(in.ecsvType()));
        s.setSvId(in.svId());
        s.setParamKey(in.paramKey());
        s.setSpecType(normalizeSpec(in.specType()));
        s.setTargetValue(in.targetValue());
        s.setRangeMin(in.rangeMin());
        s.setRangeMax(in.rangeMax());
        s.setTolerancePct(in.tolerancePct());
        s.setToleranceAbs(in.toleranceAbs());
        s.setUnit(in.unit());
        s.setEnabled(in.enabled() == null || in.enabled());
        EcSvSpec saved = specRepository.save(s);
        audit.success(created ? "ECSV_SPEC_CREATED" : "ECSV_SPEC_UPDATED", null,
                "EC/SV 规格 " + saved.getEcsvType() + " " + saved.getParamKey());
        return toSpecView(saved);
    }

    @Transactional
    public void deleteSpec(String id) {
        EcSvSpec s = requireSpec(id);
        s.setDeleted(true);
        specRepository.save(s);
        audit.success("ECSV_SPEC_DELETED", null, "EC/SV 规格 " + s.getParamKey());
    }

    // ---------- 上传与比对（IF-E5，Req 43/45） ----------

    /**
     * 上报并校验 EC/SV 实际值。
     *
     * @param scope RECIPE / PRODUCT
     * @param refId 配方/产品 id
     * @param values 参数键 → 实际值
     */
    @Transactional
    public UploadResult upload(String deviceId, String scope, String refId, String ecsvType,
                               Map<String, String> values) {
        Device d = deviceRepository.findById(deviceId).orElse(null);
        if (d == null || Boolean.TRUE.equals(d.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + deviceId);
        }
        if (values == null || values.isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "上报的 EC/SV 值为空");
        }
        String sc = scope == null ? "RECIPE" : scope.toUpperCase();
        String et = normalizeType(ecsvType);
        List<EcSvSpec> specs = specRepository.findByScopeAndRefIdAndDeletedFalseOrderByParamKeyAsc(sc, refId);
        List<ValueView> results = new ArrayList<>();
        int fail = 0;
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            String actual = e.getValue();
            EcSvSpec match = specs.stream()
                    .filter(s -> s.isEnabled() && s.getEcsvType().equals(et) && s.getParamKey().equals(key))
                    .findFirst().orElse(null);
            String result;
            String detail;
            if (match == null) {
                result = "NO_SPEC";
                detail = "无对应规格（视为告警）";
            } else {
                SpecEvaluator.Verdict v = SpecEvaluator.evaluate(SpecType.of(match.getSpecType()), "NUMBER",
                        match.getTargetValue(), match.getRangeMin(), match.getRangeMax(), null,
                        match.getTolerancePct(), match.getToleranceAbs(), actual);
                result = v.pass() ? "PASS" : "FAIL";
                detail = v.pass() ? ("符合 " + v.expected()) : (v.diffType() + "：期望 " + v.expected() + "，实际 " + actual);
            }
            if (!"PASS".equals(result)) {
                fail++;
            }
            EcSvValue ev = new EcSvValue();
            ev.setDeviceId(deviceId);
            ev.setEcsvType(et);
            ev.setParamKey(key);
            ev.setActualValue(actual);
            ev.setSpecId(match == null ? null : match.getId());
            ev.setResult(result);
            ev.setDetail(detail);
            ev.setCapturedAt(Instant.now());
            EcSvValue saved = valueRepository.save(ev);
            results.add(toValueView(saved));
        }
        audit.record("ECSV_UPLOAD", fail == 0 ? "SUCCESS" : "FAILURE", d.getCode(),
                "EC/SV 校验 " + et + " " + values.size() + " 项 · 失败 " + fail);
        return new UploadResult(deviceId, et, sc, refId, values.size(), fail, results);
    }

    public List<ValueView> listValues(String deviceId, int limit) {
        List<EcSvValue> rows = (deviceId != null && !deviceId.isBlank())
                ? valueRepository.findByDeviceIdAndDeletedFalseOrderByCreateTimeDesc(deviceId)
                : valueRepository.findByDeletedFalseOrderByCreateTimeDesc();
        int cap = limit <= 0 ? 200 : Math.min(limit, 1000);
        return rows.stream().limit(cap).map(EcSvService::toValueView).toList();
    }

    public Summary summary() {
        long pass = valueRepository.countByResultAndDeletedFalse("PASS");
        long fail = valueRepository.countByResultAndDeletedFalse("FAIL");
        long noSpec = valueRepository.countByResultAndDeletedFalse("NO_SPEC");
        return new Summary(specRepository.countByDeletedFalse(), pass, fail, noSpec);
    }

    // ---------- helpers ----------

    private EcSvSpec requireSpec(String id) {
        EcSvSpec s = specRepository.findById(id).orElse(null);
        if (s == null || Boolean.TRUE.equals(s.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "EC/SV 规格不存在: " + id);
        }
        return s;
    }

    private static String normalizeType(String t) {
        String v = t == null ? "EC" : t.trim().toUpperCase(Locale.ROOT);
        if (!"EC".equals(v) && !"SV".equals(v)) {
            throw new BizException(BizCode.PARAM_INVALID, "EC/SV 类型非法（EC/SV）: " + t);
        }
        return v;
    }

    private static String normalizeSpec(String t) {
        String v = t == null ? "TARGET" : t.trim().toUpperCase(Locale.ROOT);
        if (!"TARGET".equals(v) && !"RANGE".equals(v) && !"TOLERANCE".equals(v)) {
            throw new BizException(BizCode.PARAM_INVALID, "EC/SV 比对方式非法（TARGET/RANGE/TOLERANCE）: " + t);
        }
        return v;
    }

    private static String fmt(Instant t) {
        return t == null ? null : LocalDateTime.ofInstant(t, java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static SpecView toSpecView(EcSvSpec s) {
        return new SpecView(s.getId(), s.getScope(), s.getRefId(), s.getEcsvType(), s.getSvId(),
                s.getParamKey(), s.getSpecType(), s.getTargetValue(), s.getRangeMin(), s.getRangeMax(),
                s.getTolerancePct(), s.getToleranceAbs(), s.getUnit(), s.isEnabled());
    }

    private static ValueView toValueView(EcSvValue v) {
        return new ValueView(v.getId(), v.getDeviceId(), v.getEcsvType(), v.getParamKey(),
                v.getActualValue(), v.getSpecId(), v.getResult(), v.getDetail(), fmt(v.getCapturedAt()));
    }

    // ---------- 视图 / 输入 ----------

    public record SpecView(String id, String scope, String refId, String ecsvType, String svId,
                           String paramKey, String specType, String targetValue, String rangeMin, String rangeMax,
                           Double tolerancePct, Double toleranceAbs, String unit, boolean enabled) {
    }

    public record SpecInput(String scope, String refId, String ecsvType, String svId, String paramKey,
                            String specType, String targetValue, String rangeMin, String rangeMax,
                            Double tolerancePct, Double toleranceAbs, String unit, Boolean enabled) {
    }

    public record ValueView(String id, String deviceId, String ecsvType, String paramKey,
                            String actualValue, String specId, String result, String detail, String capturedAt) {
    }

    public record UploadResult(String deviceId, String ecsvType, String scope, String refId,
                               int total, int fail, List<ValueView> items) {
    }

    public record Summary(long specs, long pass, long fail, long noSpec) {
    }
}
