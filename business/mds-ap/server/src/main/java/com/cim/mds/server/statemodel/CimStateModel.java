package com.cim.mds.server.statemodel;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 状态模型（StateModel）——「一类对象的状态机」注册表。
 *
 * <p>行业口径（SEMI E30 GEM 状态模型方法论）：状态模型 = 状态集合 + 转换集合 +
 * 每条转换的触发条件；转换图是设备侧与 Host 侧的契约。MDS 里持有状态的对象不止
 * 设备（子单元按形态各有词表，后续 SeasonJob / Lot 等域落地时各配模型），故本表
 * 是<b>通用机制</b>：object_type 标识归属域，unit_form 仅子单元域模型有值（形态→
 * 模型映射的后端落点，替代前端硬编码）。</p>
 *
 * <p>dict_code 挂平台字典词表（值的合法性由字典守门）；state_codes 为本模型的
 * 有序状态集（CSV，须为词表子集——mds.unit_state 是 E87/E157/E116 合并词表，
 * 各形态模型取自己的子集）。模型由种子/域迁移注册，不开放页面增删。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_state_model", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_state_model", columnNames = {"model_code", "deleted"})
})
public class CimStateModel extends BaseDefData {

    /** 模型编码（如 EQUIPMENT / UNIT_LOAD_PORT，唯一主标识，建后不可改）。 */
    @Column(name = "model_code", length = 64, nullable = false)
    private String modelCode;

    /** 模型名称。 */
    @Column(name = "model_name", length = 128, nullable = false)
    private String modelName;

    /** 挂载的平台字典词表（如 mds.equip_state / mds.unit_state）。 */
    @Column(name = "dict_code", length = 128, nullable = false)
    private String dictCode;

    /** 归属对象域（EQUIPMENT / EQUIPMENT_UNIT，后续域追加 LOT / SEASON_JOB…）。 */
    @Column(name = "object_type", length = 32, nullable = false)
    private String objectType;

    /** 子单元形态（仅 EQUIPMENT_UNIT 域模型有值；NULL = 该域默认/兜底模型）。 */
    @Column(name = "unit_form", length = 32)
    private String unitForm;

    /** 本模型的有序状态集（CSV，须为 dict_code 词表的子集）。 */
    @Column(name = "state_codes", length = 512, nullable = false)
    private String stateCodes;
}
