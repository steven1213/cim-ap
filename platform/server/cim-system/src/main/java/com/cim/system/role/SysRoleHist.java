package com.cim.system.role;

import com.cim.core.history.Historizable;
import com.cim.core.model.BaseHistoryData;
import com.cim.system.support.EnableStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** {@link SysRole} 的整行快照历史表（{@code sys_role_hist}）。 */
@Getter
@Setter
@Entity
@Table(name = "sys_role_hist")
public class SysRoleHist extends BaseHistoryData implements Historizable {

    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "is_super")
    private Boolean isSuper;

    @Column(name = "sort_no")
    private Integer sortNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private EnableStatus status;
}
