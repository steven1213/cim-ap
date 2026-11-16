package com.cim.mds.server.namingrule;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 编码流水号计数器——(rule, SEQ 段, scope) 分桶的当前值（段级：每条规则可含多个 SEQ 段，
 * 各段独立计数）。
 *
 * <p>占号在业务 create 的<b>同一事务</b>内完成：{@code SELECT … FOR UPDATE} 锁桶行 →
 * current_value+1 → 拼进编码。current_value 只增不回收——编码是外部契约
 * （报表 / MES 下游 / 人员口口相传），删除对象后号段复用会造成两台设备一个码的历史歧义。
 * BY_DATE 口径的 scope_key 带日期（{@code D:yyyyMMdd}），跨天自然产生新桶、按天重置。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_naming_counter", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_naming_counter",
                columnNames = {"rule_id", "segment_index", "scope_key"})
})
public class CimNamingCounter extends BaseDefData {

    /** 归属规则 id。 */
    @Column(name = "rule_id", length = 64, nullable = false)
    private String ruleId;

    /** SEQ 段在段列表中的下标（0 起）——段级化后每段独立计数器。 */
    @Column(name = "segment_index", nullable = false)
    private Integer segmentIndex;

    /** 分桶键（派生自段 scope，带口径前缀：G=全局 / P=父码 / L=位置码 / T=类型 / D=yyyyMMdd）。 */
    @Column(name = "scope_key", length = 128, nullable = false)
    private String scopeKey;

    /** 当前已发号（含起始值；只增不回收）。 */
    @Column(name = "current_value", nullable = false)
    private Long currentValue;
}
