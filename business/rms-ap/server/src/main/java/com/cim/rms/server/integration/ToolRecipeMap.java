package com.cim.rms.server.integration;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 机台 PPID ↔ RMS 配方映射（D7 / IF-E2）。
 *
 * <p>EAP/设备侧只认机台配方名（PPID，GEM Process Program），RMS 侧以 UID（{@code recipe_id}）管理；
 * 二者映射由 RMS 维护——{@code device_id} 非空时为机台级映射，为空时表示按 {@code device_type_id}
 * 建立的机型通用映射。解析优先级：机台级 &gt; 机型级。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_tool_recipe_map")
public class ToolRecipeMap extends BaseDefData {

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "device_type_id", length = 64)
    private String deviceTypeId;

    @Column(name = "ppid_on_tool", length = 128, nullable = false)
    private String ppidOnTool;

    /**
     * 腔体（{@link com.cim.rms.server.device.DeviceChamber} id），可空。
     *
     * <p>可空 = 机台级映射（该 PPID 不区分腔体）；非空 = 该 PPID 只对指定腔体生效。
     * 多腔体机台同一配方在不同腔体可能用不同 PPID（对齐 TSMC US6968245 / SEMI E139 RecipeContext）。</p>
     */
    @Column(name = "chamber_id", length = 64)
    private String chamberId;

    @Column(name = "recipe_id", length = 64, nullable = false)
    private String recipeId;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
