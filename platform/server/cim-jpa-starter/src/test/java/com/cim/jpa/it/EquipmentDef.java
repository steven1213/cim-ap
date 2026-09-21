package com.cim.jpa.it;

import com.cim.core.history.History;
import com.cim.core.history.HistoryStrategy;
import com.cim.core.model.BaseDefData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 示例主实体（定义表）：继承 {@code BaseDefData}，声明 {@code @History(SNAPSHOT)}，
 * 即自动获得「整行快照 → {@code equipment_def_hist}」的历史能力。
 */
@Getter
@Setter
@Entity
@Table(name = "equipment_def")
@History(HistoryStrategy.SNAPSHOT)
public class EquipmentDef extends BaseDefData {

    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "capacity")
    private Integer capacity;
}
