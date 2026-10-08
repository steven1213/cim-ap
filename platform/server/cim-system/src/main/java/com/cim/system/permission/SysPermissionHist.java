package com.cim.system.permission;

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

/** {@link SysPermission} 的整行快照历史表（{@code sys_permission_hist}）。 */
@Getter
@Setter
@Entity
@Table(name = "sys_permission_hist")
public class SysPermissionHist extends BaseHistoryData implements Historizable {

    @Column(name = "code", length = 128)
    private String code;

    @Column(name = "module", length = 64)
    private String module;

    @Column(name = "res", length = 64)
    private String res;

    @Column(name = "action", length = 32)
    private String action;

    @Column(name = "name", length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private EnableStatus status;
}
