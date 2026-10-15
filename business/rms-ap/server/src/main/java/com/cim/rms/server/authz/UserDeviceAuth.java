package com.cim.rms.server.authz;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户↔设备授权（FR-P4，Req 11/12）：RMS 私有数据（不进 JWT，铁律）。
 *
 * <p>授权维度三选一或叠加：具体设备（{@code deviceId}）、机型（{@code deviceTypeId}），
 * 或区域（{@code areaId}）；{@code canOperate} 区分「可见」与「可操作」。未授权设备在
 * 列表/接口中均不可见（见 {@link DeviceAuthService}）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "user_device_auth")
public class UserDeviceAuth extends BaseDefData {

    /** 授权主体（用户名；后续可扩展为角色/组，用 {@code subjectType} 区分）。 */
    @Column(name = "subject", length = 64, nullable = false)
    private String subject;

    @Column(name = "subject_type", length = 16, nullable = false)
    private String subjectType = "USER";

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "device_type_id", length = 64)
    private String deviceTypeId;

    @Column(name = "area_id", length = 64)
    private String areaId;

    /** 是否可操作（false = 仅可见）。 */
    @Column(name = "can_operate", nullable = false)
    private boolean canOperate = true;
}
