package com.cim.mds.server.shift;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 排班安排（V17）——事务事实：某人在某天上哪个班。
 *
 * <p><b>一人一天一个班</b>：{@code (work_date, user_id)} 唯一（软删含入唯一约束，
 * 删格=软删后可重排）。{@code work_date} 记班次开始那天（跨天班次不记结束日）。
 * {@code source} 表达来源：{@code MANUAL}（人工单格排）/{@code PATTERN}（班制铺排）/
 * {@code COPY}（复制上周）——批量生成跳过 MANUAL 格，人工安排是当事人确认过的事实，
 * 不被模板静默覆盖。</p>
 *
 * <p>{@code username} 是落库时刻的<b>快照</b>：人员主数据在 IAM（Directory API），
 * 离职/改名后目录查不到，历史排班仍可读。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_shift_assignment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_shift_assignment", columnNames = {"work_date", "user_id", "deleted"})
})
public class CimShiftAssignment extends BaseDefData {

    /** 值守日（班次开始那天；跨天班次的次日归属由班次定义推算，不落库）。 */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** 人员 id（= IAM 目录 userId；逻辑引用，不建外键）。 */
    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    /** 人员名快照（落库时刻；展示兜底，不随目录回写）。 */
    @Column(name = "username", length = 64, nullable = false)
    private String username;

    /** 班次编码（= cim_shift_def.shift_code）。 */
    @Column(name = "shift_code", length = 32, nullable = false)
    private String shiftCode;

    /** 来源（MANUAL/PATTERN/COPY——批量生成跳过 MANUAL）。 */
    @Column(name = "source", length = 16, nullable = false)
    private String source;
}
