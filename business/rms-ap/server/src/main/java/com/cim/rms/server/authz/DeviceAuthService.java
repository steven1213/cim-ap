package com.cim.rms.server.authz;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.device.Device;
import com.cim.rms.server.device.DeviceRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 设备级授权服务（FR-P4/P6，Req 11/12）。
 *
 * <p><b>默认口径</b>：用户<b>没有任何</b>授权记录 → 不受限（视为全局可见，兼容管理员）；一旦有记录，
 * 则仅「具体设备 ∪ 机型命中 ∪ 区域命中」的设备可见。可见性在设备列表/详情接口下推（{@link #visibleDeviceIds}），
 * 变更即时生效（无缓存，实时查库）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAuthService {

    private final UserDeviceAuthRepository repository;
    private final DeviceRepository deviceRepository;
    private final RmsAuditService audit;

    public List<AuthView> list(String subject) {
        List<UserDeviceAuth> rows = (subject == null || subject.isBlank())
                ? repository.findByDeletedFalseOrderByCreateTimeDesc()
                : repository.findBySubjectAndDeletedFalseOrderByCreateTimeDesc(subject);
        return rows.stream().map(this::toView).toList();
    }

    @Transactional
    public AuthView grant(String subject, String subjectType, String deviceId,
                          String deviceTypeId, String areaId, Boolean canOperate) {
        if (subject == null || subject.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "授权主体不能为空");
        }
        if ((deviceId == null || deviceId.isBlank())
                && (deviceTypeId == null || deviceTypeId.isBlank())
                && (areaId == null || areaId.isBlank())) {
            throw new BizException(BizCode.PARAM_INVALID, "设备/机型/区域至少指定一项");
        }
        UserDeviceAuth a = new UserDeviceAuth();
        a.setSubject(subject.trim());
        a.setSubjectType(subjectType == null || subjectType.isBlank() ? "USER" : subjectType.toUpperCase());
        a.setDeviceId(blankToNull(deviceId));
        a.setDeviceTypeId(blankToNull(deviceTypeId));
        a.setAreaId(blankToNull(areaId));
        a.setCanOperate(canOperate == null || canOperate);
        UserDeviceAuth saved = repository.save(a);
        audit.success("DEVICE_AUTH_GRANTED", saved.getSubject(),
                "设备授权 device=" + saved.getDeviceId() + " type=" + saved.getDeviceTypeId()
                        + " area=" + saved.getAreaId() + " operate=" + saved.isCanOperate());
        return toView(saved);
    }

    @Transactional
    public void revoke(String id) {
        UserDeviceAuth a = repository.findById(id).orElse(null);
        if (a == null || Boolean.TRUE.equals(a.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "授权记录不存在: " + id);
        }
        a.setDeleted(true);
        repository.save(a);
        audit.success("DEVICE_AUTH_REVOKED", a.getSubject(), "撤销设备授权 id=" + id);
    }

    /**
     * 解析用户可见设备 id 集；返回 {@code null} 表示<b>不受限</b>（无任何授权记录）。
     */
    public Set<String> visibleDeviceIds(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        List<UserDeviceAuth> rows = repository.findBySubjectAndDeletedFalseOrderByCreateTimeDesc(subject);
        if (rows.isEmpty()) {
            return null;
        }
        Set<String> explicit = new LinkedHashSet<>();
        Set<String> types = new LinkedHashSet<>();
        Set<String> areas = new LinkedHashSet<>();
        for (UserDeviceAuth a : rows) {
            if (a.getDeviceId() != null) {
                explicit.add(a.getDeviceId());
            }
            if (a.getDeviceTypeId() != null) {
                types.add(a.getDeviceTypeId());
            }
            if (a.getAreaId() != null) {
                areas.add(a.getAreaId());
            }
        }
        Set<String> result = new LinkedHashSet<>(explicit);
        for (Device d : deviceRepository.findAll()) {
            if (Boolean.TRUE.equals(d.getDeleted())) {
                continue;
            }
            if ((d.getDeviceTypeId() != null && types.contains(d.getDeviceTypeId()))
                    || (d.getAreaId() != null && areas.contains(d.getAreaId()))) {
                result.add(d.getId());
            }
        }
        return result;
    }

    /** 是否可操作某设备（无授权记录视为可操作）。 */
    public boolean canOperate(String subject, String deviceId) {
        Set<String> visible = visibleDeviceIds(subject);
        return visible == null || visible.contains(deviceId);
    }

    private AuthView toView(UserDeviceAuth a) {
        String deviceLabel = null;
        if (a.getDeviceId() != null) {
            deviceLabel = deviceRepository.findById(a.getDeviceId()).map(Device::getCode).orElse(a.getDeviceId());
        }
        return new AuthView(a.getId(), a.getSubject(), a.getSubjectType(), a.getDeviceId(), deviceLabel,
                a.getDeviceTypeId(), a.getAreaId(), a.isCanOperate(), fmt(a.getCreateTime()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 授权视图。 */
    public record AuthView(String id, String subject, String subjectType,
                           String deviceId, String deviceLabel, String deviceTypeId, String areaId,
                           boolean canOperate, String createTime) {
    }
}
