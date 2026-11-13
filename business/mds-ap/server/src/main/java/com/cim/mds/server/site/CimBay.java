package com.cim.mds.server.site;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 湾（Bay）—— 层级 {@code Site → Fab → Area → Bay → Stage} 的第四层，物理作业区。
 *
 * <p>层级语义见 location-design.md §1.1 / §8：Area 管「工艺归类」，Bay 管「物理落位」。
 * 物理湾道编号全厂不重复，{@code bay_code} 在 Fab 内唯一；归属两层都落列
 * （{@code fab_code} 快捷过滤 + {@code area_code} 直接父层）。
 * 第五层 {@link CimStage} 挂在 Bay 下；设备将来以 {@code location_id} 落 Stage 或无细分的 Bay。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_bay", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_bay_code", columnNames = {"fab_code", "bay_code", "deleted"})
})
public class CimBay extends EditLockDefData {

    /** Bay 编码（Fab 内唯一，物理湾道全厂不重复，例：B01）。 */
    @Column(name = "bay_code", length = 64, nullable = false)
    private String bayCode;

    /** 归属 Fab 编码（逻辑引用 {@code cim_fab.fab_code}）。 */
    @Column(name = "fab_code", length = 64, nullable = false)
    private String fabCode;

    /** 归属 Area 编码——直接父层（逻辑引用 {@code cim_area.area_code}，Fab 内唯一）。 */
    @Column(name = "area_code", length = 64, nullable = false)
    private String areaCode;

    /** Bay 名称（例：B01 湾）。 */
    @Column(name = "bay_name", length = 128, nullable = false)
    private String bayName;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
