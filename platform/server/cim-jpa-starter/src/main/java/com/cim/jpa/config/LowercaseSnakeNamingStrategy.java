package com.cim.jpa.config;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * 物理命名策略：统一小写蛇形（跨 Oracle / MySQL / PostgreSQL 一致），并支持全局表名前缀。
 *
 * <p>替代各库默认大小写行为，避免 Oracle 默认大写、MySQL 小写、PG 保留大小写导致跨库
 * 表名 / 列名错乱（见 README §3）。</p>
 */
public class LowercaseSnakeNamingStrategy extends CamelCaseToUnderscoresNamingStrategy {

    private final String tablePrefix;

    public LowercaseSnakeNamingStrategy(String tablePrefix) {
        this.tablePrefix = (tablePrefix == null) ? "" : tablePrefix.trim();
    }

    @Override
    public Identifier toPhysicalTableName(Identifier logicalName, JdbcEnvironment context) {
        Identifier physical = super.toPhysicalTableName(logicalName, context);
        if (tablePrefix.isEmpty()) {
            return physical;
        }
        return new Identifier(tablePrefix + physical.getText(), physical.isQuoted());
    }
}
