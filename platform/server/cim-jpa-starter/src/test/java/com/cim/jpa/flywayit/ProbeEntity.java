package com.cim.jpa.flywayit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T1.5 迁移探针实体：其表结构由 Flyway 脚本 {@code db/migration/h2/V1__probe_entity.sql} 建出，
 * JPA 以 {@code ddl-auto=validate} 校验（证明「迁移自动化 + validate 不报错」）。
 */
@Getter
@Setter
@Entity
@Table(name = "probe_entity")
public class ProbeEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "name", length = 128)
    private String name;
}
