package com.cim.mds.server.edc;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * EDC 报告模板（V28）——SECS/GEM 报告结构的显式主数据：一个模板 = 一组
 * 「SVID → 参数」行（成员见 {@link CimEdcReportTplItem}），等价于隐式 Report
 * 的显式化；EAP 落地 S2F33/S2F35 时按绑定关系推导报告结构（RPTID 是 GEM
 * 会话态标识，动态分配、不落 MDS）。
 *
 * <p>模板与机台/事件解耦：一个模板可被多机台、多 CEID 复用（bind 见
 * {@link CimEdcEquipMap}），一个 CEID 也可绑多个模板。编辑锁三列随基类。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_edc_report_tpl", uniqueConstraints = {
        @UniqueConstraint(name = "uk_edc_report_tpl", columnNames = {"tpl_code", "deleted"})
})
public class CimEdcReportTpl extends EditLockDefData {

    /** 模板编码（全局唯一、建后不可改——映射绑定引用锚点）。 */
    @Column(name = "tpl_code", length = 64, nullable = false)
    private String tplCode;

    /** 模板名称（例：刻蚀过程报告）。 */
    @Column(name = "tpl_name", length = 128, nullable = false)
    private String tplName;

    /** 是否启用（停用模板不进新建绑定）。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
