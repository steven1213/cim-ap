package com.cim.mds.server.shift;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 班次定义（V17）——排班域的定义类主数据：一个班次 = 一天内的一个值守时间段。
 *
 * <p>跨天班次（如晚班 22:00-06:00）用 {@code cross_day} 标记，{@code start_time}
 * 允许大于 {@code end_time}；<b>排班与交接记录的 {@code work_date} 一律记班次开始那天</b>，
 * 库里不存冗余的结束时间戳。{@code color} 是前端日历色块的展示属性，取前端固定色板
 * 键（不进平台字典——纯视觉，8 色以内）。</p>
 *
 * <p>治理纪律：{@code shift_code} 域内唯一、建后不可改（班制/排班/交接记录都按
 * code 引用）；被引用禁删（服务端守门），只能停用——{@code enabled=false} 只影响
 * 未来排班下拉，历史排班照常解析。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_shift_def", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_shift_def", columnNames = {"shift_code", "deleted"})
})
public class CimShiftDef extends BaseDefData {

    /** 定长编码（如 EARLY/MIDDLE/LATE；域内唯一、建后不可改）。 */
    @Column(name = "shift_code", length = 32, nullable = false)
    private String shiftCode;

    /** 班次名（下拉与日历 tooltip 文案）。 */
    @Column(name = "shift_name", length = 64, nullable = false)
    private String shiftName;

    /** 开始时刻（HH:mm；跨天班次允许大于 end_time）。 */
    @Column(name = "start_time", length = 5, nullable = false)
    private String startTime;

    /** 结束时刻（HH:mm）。 */
    @Column(name = "end_time", length = 5, nullable = false)
    private String endTime;

    /** 跨天标志（true = 结束时刻在次日；work_date 仍记开始那天）。 */
    @Column(name = "cross_day", nullable = false)
    private Boolean crossDay = Boolean.FALSE;

    /** 日历色块色板键（前端固定色板；空 = 默认色）。 */
    @Column(name = "color", length = 16)
    private String color;

    /** 是否启用（false = 不再出现在排班选择；历史引用不受影响）。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /** 同级排序（下拉与日历图例顺序）。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 100;
}
