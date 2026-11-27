package com.cim.mds.server.edc;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * EDC 设备接口映射（V28）——「机台 × CEID → 报告模板」的静态绑定
 * （接口元数据；运行时解析归 EAP/设备集成层，MDS 不参与实时通信）。
 *
 * <p>报告结构（SVID→参数列表）由 {@link CimEdcReportTpl} 显式承载；本表只回答
 * 「哪台机台的哪个事件上报哪份报告」。一个 CEID 可绑多个模板（GEM 允许一个
 * 事件关联多报告），一个模板可被多机台/事件复用。source_channel 区分取数通道
 * （GEM 事件报告 / EDA Interface A / 结果文件解析）。型号级映射 + 机台覆盖
 * （SEMI 惯例）留后续版本，本版先机台粒度。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_edc_equip_map", uniqueConstraints = {
        @UniqueConstraint(name = "uk_edc_equip_map", columnNames = {"equipment_code", "ceid", "tpl_id", "deleted"})
})
public class CimEdcEquipMap extends BaseDefData {

    /** 数据来源通道词表。 */
    public static final String CH_GEM = "GEM";
    public static final String CH_EDA = "EDA";
    public static final String CH_FILE = "FILE";

    /** 机台编码（逻辑引用 cim_equipment.equipment_code）。 */
    @Column(name = "equipment_code", length = 64, nullable = false)
    private String equipmentCode;

    /** 设备收集事件 ID（采集触发窗口，SEMI E5 CEID）。 */
    @Column(name = "ceid", nullable = false)
    private Integer ceid;

    /** 报告模板 id（逻辑引用 cim_edc_report_tpl.id）。 */
    @Column(name = "tpl_id", nullable = false)
    private String tplId;

    /** 来源通道：GEM / EDA / FILE。 */
    @Column(name = "source_channel", length = 16, nullable = false)
    private String sourceChannel = CH_GEM;
}
