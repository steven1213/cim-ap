package com.cim.mds.server.equipment;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 机台组成员（ToolGroupMember）——组↔机台关联。
 *
 * <p>不带编辑锁：写操作守门所属组的编辑锁（与设备↔能力关联同口径）；
 * 随组删除物理级联（软删会撞 (group_id, equipment_id, deleted) 唯一索引）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_tool_group_member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_tool_group_member", columnNames = {"group_id", "equipment_id", "deleted"})
})
public class CimToolGroupMember extends BaseDefData {

    /** 所属组主键（cim_tool_group.id）。 */
    @Column(name = "group_id", length = 64, nullable = false)
    private String groupId;

    /** 成员机台主键（cim_equipment.id）。 */
    @Column(name = "equipment_id", length = 64, nullable = false)
    private String equipmentId;

    /** 派工优先级（候选池权重，小者优先；空 = 无偏好）。 */
    @Column(name = "priority")
    private Integer priority;
}
