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

/** {@link SysDictItem} 的整行快照历史表（{@code sys_dict_item_hist}）。 */
@Getter
@Setter
@Entity
@Table(name = "sys_dict_item_hist")
public class SysDictItemHist extends BaseHistoryData implements Historizable {

    @Column(name = "dict_id", length = 64)
    private String dictId;

    @Column(name = "item_key", length = 64)
    private String itemKey;

    @Column(name = "i18n_code", length = 128)
    private String i18nCode;

    @Column(name = "item_value", length = 256)
    private String itemValue;

    @Column(name = "sort_no")
    private Integer sortNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private EnableStatus status;
}
