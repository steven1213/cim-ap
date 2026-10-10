package com.cim.rms.server.device;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备区域（Req 47）。
 *
 * <p>单表自引用（{@code parentId} 可空）支撑区域分组；区域用于设备授权作用域
 * （Req 11：按区域/类型授权）与配方查询裁剪。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "device_area", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_area_code", columnNames = {"code"})
})
public class DeviceArea extends BaseDefData {

    /** 区域编码。 */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    /** 区域名称。 */
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /** 父区域 ID；顶层为 {@code null}。 */
    @Column(name = "parent_id", length = 64)
    private String parentId;

    /** 同级排序。 */
    @Column(name = "sort_no")
    private int sortNo = 0;
}
