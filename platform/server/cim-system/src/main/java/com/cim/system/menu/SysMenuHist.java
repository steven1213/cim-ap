package com.cim.system.menu;

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

/** {@link SysMenu} 的整行快照历史表（{@code sys_menu_hist}）。 */
@Getter
@Setter
@Entity
@Table(name = "sys_menu_hist")
public class SysMenuHist extends BaseHistoryData implements Historizable {

    @Column(name = "parent_id", length = 64)
    private String parentId;

    @Column(name = "path", length = 256)
    private String path;

    @Column(name = "component", length = 256)
    private String component;

    @Column(name = "i18n_code", length = 128)
    private String i18nCode;

    @Column(name = "icon", length = 64)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 16)
    private MenuType type;

    @Column(name = "perm_code", length = 128)
    private String permCode;

    @Column(name = "sort_no")
    private Integer sortNo;

    @Column(name = "visible")
    private Boolean visible;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private EnableStatus status;
}
