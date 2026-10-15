package com.cim.rms.server.device;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 机台腔体（Chamber）。
 *
 * <p>多腔体机台的配方是<b>按腔体</b>装载的：同一配方在 CH-A / CH-B 上可能装载不同版本或使用不同 PPID。
 * 对齐 SEMI E139（RecipeContext 以腔体为实例化上下文之一）与 TSMC US6968245「每机台每 Recipe 选可用 Chamber」。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "device_chamber", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_chamber_code", columnNames = {"device_id", "code"})
})
public class DeviceChamber extends BaseDefData {

    /** 归属机台（{@link Device} id）。 */
    @Column(name = "device_id", length = 64, nullable = false)
    private String deviceId;

    /** 腔体编码（机台内唯一，如 CH-A / PM1）。 */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    /** 腔体名称。 */
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /** 机台内排序。 */
    @Column(name = "sort_no")
    private int sortNo = 0;
}
