package com.cim.jpa.it;

import com.cim.core.history.Historizable;
import com.cim.core.model.BaseHistoryData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 示例历史实体：与主实体**同包同名 + {@code Hist} 后缀**、业务字段**同构**
 * （{@code code}/{@code name}/{@code capacity}），故可被 {@code HistoryMapper} 自动解析与拷贝。
 */
@Getter
@Setter
@Entity
@Table(name = "equipment_def_hist")
public class EquipmentDefHist extends BaseHistoryData implements Historizable {

    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "capacity")
    private Integer capacity;
}
