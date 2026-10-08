package com.cim.system.config;

import com.cim.core.history.History;
import com.cim.core.history.HistoryStrategy;
import com.cim.core.model.BaseDefData;
import com.cim.system.support.EnableStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * 参数配置（design.md §2.10 的 {@code config/} 域）——运行期可改的业务参数。
 *
 * <p>与 {@code application.yml} 的<b>分工</b>：部署期固定的（连接串、线程池）留在配置文件；
 * 运行期由管理员调整、需留痕的（阈值、开关、默认值）落在本表，并自动获得
 * {@code @History} 快照历史。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@SQLRestriction("deleted = false")
@Table(name = "sys_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_config_key_tenant",
                columnNames = {"config_key", "tenant_id", "deleted"}))
@History(HistoryStrategy.SNAPSHOT)
public class SysConfig extends BaseDefData {

    /** 参数键。 */
    @Column(name = "config_key", length = 128, nullable = false)
    private String configKey;

    /** 参数值（字符串存储，由使用方按需解析）。 */
    @Column(name = "config_value", length = 1024)
    private String configValue;

    /** 参数名称。 */
    @Column(name = "name", length = 128)
    private String name;

    /** 是否内置（内置参数不允许删除，仅允许改值）。 */
    @Column(name = "built_in", nullable = false)
    private Boolean builtIn = Boolean.FALSE;

    /** 状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EnableStatus status = EnableStatus.ENABLED;
}
