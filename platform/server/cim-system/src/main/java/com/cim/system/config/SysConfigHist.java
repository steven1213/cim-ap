package com.cim.system.config;

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

/** {@link SysConfig} 的整行快照历史表（{@code sys_config_hist}）。 */
@Getter
@Setter
@Entity
@Table(name = "sys_config_hist")
public class SysConfigHist extends BaseHistoryData implements Historizable {

    @Column(name = "config_key", length = 128)
    private String configKey;

    @Column(name = "config_value", length = 1024)
    private String configValue;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "built_in")
    private Boolean builtIn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private EnableStatus status;
}
