package com.cim.system.dict;

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

/** {@link SysDict} 的整行快照历史表（{@code sys_dict_hist}）。 */
@Getter
@Setter
@Entity
@Table(name = "sys_dict_hist")
public class SysDictHist extends BaseHistoryData implements Historizable {

    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "name", length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private EnableStatus status;
}
