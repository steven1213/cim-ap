package com.cim.rms.server.device;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备类型（机型，Req 46）。
 *
 * <p>配方（{@code Recipe.deviceTypeId}）与机差适配按机型维度组织；
 * 编码唯一（软删行不参与唯一性——查询侧手工过滤 {@code deleted=false}）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "device_type", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_type_code", columnNames = {"code"})
})
public class DeviceType extends BaseDefData {

    /** 机型编码（如 ETFD-300）。 */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    /** 机型名称。 */
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /** 制造商（如 Applied Materials / Lam Research）。 */
    @Column(name = "manufacturer", length = 128)
    private String manufacturer;

    /** 型号（如 Producer GT）。 */
    @Column(name = "model", length = 128)
    private String model;
}
