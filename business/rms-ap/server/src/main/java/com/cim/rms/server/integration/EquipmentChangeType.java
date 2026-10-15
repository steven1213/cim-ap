package com.cim.rms.server.integration;

/**
 * 机台侧配方变更类型（GEM {@code PPChangeStatus} 取值）。
 *
 * <p>对齐 SEMI E30：设备上报配方变更时必须带变更类型，主机据此决定处置——
 * {@code DELETED} 意味着机台上已无该配方（仓库与机台必然不一致），
 * {@code EDITED} 意味机台侧内容可能已被改动，二者都触发漂移处置；
 * {@code CREATED} 若 PPID 已纳管则属重建，未纳管则记为孤儿信号。</p>
 */
public enum EquipmentChangeType {
    /** 机台侧新建（GEM {@code Create}）。 */
    CREATED,
    /** 机台侧编辑（GEM {@code Edit}）。 */
    EDITED,
    /** 机台侧删除（GEM {@code Delete}）。 */
    DELETED
}
