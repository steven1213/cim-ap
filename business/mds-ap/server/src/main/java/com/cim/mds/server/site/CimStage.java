package com.cim.mds.server.site;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备位（Stage）—— 层级 {@code Site → Fab → Area → Bay → Stage} 的第五层（叶子）。
 *
 * <p>单 Bay 内的物理落位（工具簇 / 机台位），设备将来以 {@code location_id} 落 Stage 或无细分的 Bay
 * （location-design.md §1.1 / §8）。第五层 2026-11-13 由设计稿 SubBay 定名 Stage；
 * <b>Module 不是位置层</b>——SEMI E157 语境的 Module 是设备内部工艺腔，属设备域。</p>
 *
 * <p>归属落 {@code fab_code}（经 Bay 冗余，便于过滤与路径展示）+ {@code bay_code}（直接父层）；
 * {@code stage_code} 在 Bay 内唯一。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_stage", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_stage_code", columnNames = {"fab_code", "bay_code", "stage_code", "deleted"})
})
public class CimStage extends EditLockDefData {

    /** Stage 编码（Bay 内唯一，例：S01）。 */
    @Column(name = "stage_code", length = 64, nullable = false)
    private String stageCode;

    /** 归属 Fab 编码（经 Bay 冗余，与所属 Bay 的 fab_code 一致）。 */
    @Column(name = "fab_code", length = 64, nullable = false)
    private String fabCode;

    /** 归属 Bay 编码——直接父层（Fab 内唯一，与 fab_code 共同定位）。 */
    @Column(name = "bay_code", length = 64, nullable = false)
    private String bayCode;

    /** Stage 名称。 */
    @Column(name = "stage_name", length = 128, nullable = false)
    private String stageName;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
