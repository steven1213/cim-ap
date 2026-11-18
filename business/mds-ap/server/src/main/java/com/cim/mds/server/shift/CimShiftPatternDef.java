package com.cim.mds.server.shift;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 班制·轮班模式（V17）——定义类主数据：一个周期 N 天内每天上哪个班次的轮转序列。
 *
 * <p>{@code pattern_detail} 存 CSV（第 1 天班次 code, 第 2 天 code, …），
 * 长度必须等于 {@code cycle_days}，引用的 {@code shift_code} 必须存在且启用
 * （服务端保存时二次校验）。典型样例：「四班三倒」不必建班组表——本模型
 * <b>排班排到人</b>（{@link CimShiftAssignment}），班制只是铺排的生成模板：
 * 在排班页选模式 + 起始日 + 勾人员，即可按 {@code detail[(day-1) % cycle_days]}
 * 一步落库，人工改过的格子（source=MANUAL）不被覆盖。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_shift_pattern_def", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_shift_pattern_def", columnNames = {"pattern_code", "deleted"})
})
public class CimShiftPatternDef extends BaseDefData {

    /** 定长编码（域内唯一、建后不可改）。 */
    @Column(name = "pattern_code", length = 32, nullable = false)
    private String patternCode;

    /** 班制名（铺排入口下拉文案）。 */
    @Column(name = "pattern_name", length = 64, nullable = false)
    private String patternName;

    /** 周期天数（detail 项数必须等于本值）。 */
    @Column(name = "cycle_days", nullable = false)
    private Integer cycleDays;

    /** 轮转序列 CSV（第 N 天 → shift_code；项数 == cycle_days）。 */
    @Column(name = "pattern_detail", length = 512, nullable = false)
    private String patternDetail;

    /** 是否启用（false = 不出现在铺排入口）。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /** 同级排序。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 100;
}
