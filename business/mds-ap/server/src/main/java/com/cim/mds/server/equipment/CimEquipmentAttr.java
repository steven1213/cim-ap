package com.cim.mds.server.equipment;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备自定义属性（EquipmentAttr）——KV 扩展骨架（对标行业 CIM 的
 * Customer Defined Attribute）。
 *
 * <p>一期只建表 + 实体（V8 v2），API 与页面入口二期开放——目的：将来加属性
 * 不再频繁加列。attr_type 声明值类型（STRING/NUMBER/BOOLEAN），消费侧自行转换。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_equipment_attr", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_equipment_attr", columnNames = {"equipment_id", "attr_key", "deleted"})
})
public class CimEquipmentAttr extends BaseDefData {

    /** 所属机台主键（cim_equipment.id）。 */
    @Column(name = "equipment_id", length = 64, nullable = false)
    private String equipmentId;

    /** 属性键（设备内唯一，例：offset_x）。 */
    @Column(name = "attr_key", length = 64, nullable = false)
    private String attrKey;

    /** 属性值。 */
    @Column(name = "attr_value", length = 512)
    private String attrValue;

    /** 值类型：STRING / NUMBER / BOOLEAN。 */
    @Column(name = "attr_type", length = 16, nullable = false)
    private String attrType = "STRING";
}
