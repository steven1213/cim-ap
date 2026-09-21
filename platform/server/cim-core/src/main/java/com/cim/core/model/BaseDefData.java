package com.cim.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * 定义表 / 当前态基类（低频变更：定义、配置、配方、主数据）。
 *
 * <p>声明 {@code @History(SNAPSHOT)} 即按整行快照落该实体**专属**历史表 {@code {X}Hist}；
 * 变更检测闸门保证未变更不落历史（见 README §9）。</p>
 *
 * <p>主键 {@code id} 为**应用层时间有序 ID**（雪花 / UUIDv7），由 {@code IdGenerator}
 * 在 {@code @PrePersist} 注入，规避 Oracle IDENTITY 差异并支持分布式（见 README §3）。</p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseDefData extends Auditable {

    /** 主键（应用层时间有序 ID，见 README §3）。 */
    @Id
    @Column(name = "id", length = 64, updatable = false, nullable = false)
    private String id;

    /** 描述。 */
    @Column(name = "description", length = 512)
    private String description;

    /** 行级乐观锁（① 版本语义之一，不进历史，见 README §9.5）。 */
    @Version
    @Column(name = "version")
    private Long version;

    /** 逻辑删除标记（唯一约束需含本列，见 README §9.7）。 */
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = Boolean.FALSE;
}
