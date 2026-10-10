package com.cim.rms.server.device;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备台账（机台，Req 48）。
 *
 * <p>归属机型（{@code deviceTypeId}）与区域（{@code areaId}）；配方上传/下载、
 * 在线比对（IF-E1/E2/E3）都以 {@code deviceId} 为锚点。后续用户↔设备授权
 * （Req 11，W2+）挂本实体的 id 上。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "device", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_code", columnNames = {"code"})
})
public class Device extends BaseDefData {

    /** 设备编码（机台号，与 FAB 现场一致）。 */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    /** 设备名称。 */
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /** 归属机型（{@link DeviceType} id）。 */
    @Column(name = "device_type_id", length = 64, nullable = false)
    private String deviceTypeId;

    /** 归属区域（{@link DeviceArea} id），可空。 */
    @Column(name = "area_id", length = 64)
    private String areaId;

    /** 设备状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private DeviceStatus status = DeviceStatus.ENABLED;

    /** 设备 IP（SECS-II / HSMS 通信地址，可空）。 */
    @Column(name = "ip", length = 64)
    private String ip;
}
