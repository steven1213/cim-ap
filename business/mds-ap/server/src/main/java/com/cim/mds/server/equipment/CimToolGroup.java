package com.cim.mds.server.equipment;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 机台组（ToolGroup）——派工候选池（ISA-95 Work Center 口径）。
 *
 * <p>组<b>绑定机型</b>（{@code class_code}）：成员机台必须同机型才能入组
 * （守门在 {@link ToolGroupAdminService}）——派工语义「先选组再选台」，组内机台
 * 天然可互换，入组校验从源头保证同质。</p>
 *
 * <p>组带编辑锁三列（与全库主数据统一「人 vs 人」互斥动线）；成员表不带锁，
 * 写操作守门组的编辑锁。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_tool_group", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_tool_group_code", columnNames = {"group_code", "deleted"})
})
public class CimToolGroup extends EditLockDefData {

    /** 组编码（全局唯一，例：TG-LITHO）。 */
    @Column(name = "group_code", length = 64, nullable = false)
    private String groupCode;

    /** 组名称（例：光刻机台组）。 */
    @Column(name = "group_name", length = 128, nullable = false)
    private String groupName;

    /** 绑定机型编码（逻辑引用 cim_equipment_class.class_code；成员守门同机型）。 */
    @Column(name = "class_code", length = 64, nullable = false)
    private String classCode;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用：停用组不参与派工候选。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
