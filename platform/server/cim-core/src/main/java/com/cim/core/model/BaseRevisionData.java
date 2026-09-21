package com.cim.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * 版本基类（主数据多版本生命周期）。
 *
 * <p>区别于 {@code @Version} 行级乐观锁：{@code revision} 是**业务版本**，由业务动作
 * （发布 / 归档）驱动，**不是每次写都 +1**（见 README §9.5）。主键为 {@code String id}，
 * {@code revision} 降为普通业务版本列。</p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseRevisionData extends Auditable {

    /** 业务主键（应用层时间有序 ID）。 */
    @Id
    @Column(name = "id", length = 64, updatable = false, nullable = false)
    private String id;

    /** 业务版本号（非每次写 +1）。 */
    @Column(name = "revision")
    private Integer revision;

    /** 生效状态（Active / NotActive）。 */
    @Column(name = "active_state", length = 32)
    private String activeState;

    /** 冻结状态（Frozen / Unfrozen）。 */
    @Column(name = "frozen_state", length = 32)
    private String frozenState;

    /** 归档状态（Archive / Release）。 */
    @Column(name = "archive_state", length = 32)
    private String archiveState;

    /** 源版本号（衍生 / 复制关系）。 */
    @Column(name = "origin_revision")
    private Integer originRevision;
}
