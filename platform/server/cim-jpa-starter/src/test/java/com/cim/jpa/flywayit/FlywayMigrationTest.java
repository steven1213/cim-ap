package com.cim.jpa.flywayit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1.5 Flyway 多目录迁移验收（H2）：开启 {@code cim.jpa.flyway.enabled} 后，按库类型选中
 * {@code db/migration/h2} 执行迁移建表，JPA 以 {@code ddl-auto=validate} 校验通过
 * （证明「迁移自动化 + validate 不报错」）。
 */
@SpringBootTest(classes = FlywayTestApplication.class, properties = {
        "spring.flyway.enabled=true",
        "cim.jpa.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationAppliedAndSchemaValidated() {
        // Flyway 已记录迁移 V1（Flyway 以小写带引号建历史表/列）
        var history = jdbcTemplate.queryForList(
                "select \"version\" from \"flyway_schema_history\"");
        assertThat(history).extracting(r -> String.valueOf(r.get("version"))).contains("1");

        // 迁移目录建出的表存在（若 validate 未通过，上下文根本不会启动）
        Integer tables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'PROBE_ENTITY'",
                Integer.class);
        assertThat(tables).isEqualTo(1);

        // 迁移建出的表可读写
        jdbcTemplate.update("insert into probe_entity(id, name) values (?, ?)", "P1", "探针");
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from probe_entity where id = 'P1'", Integer.class);
        assertThat(rows).isEqualTo(1);
    }
}
