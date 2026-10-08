package com.cim.jpa.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link DbCapabilities} 对各数据库产品名的解析，尤其是达梦 DM 的能力映射。
 */
class DbCapabilitiesTest {

    @Test
    void dmDetectedByProductName() {
        // DM JDBC 驱动返回的 DatabaseMetaData.getDatabaseProductName() 即 "DM DBMS"
        DbCapability dm = DbCapabilities.of("DM DBMS");
        assertEquals("dm", dm.product());
        // DM 与 Oracle 兼容：NULL 默认排最前
        assertFalse(dm.nullsLastByDefault());
        // 无原生 JSON 查询函数
        assertFalse(dm.supportsJsonFunctions());
        // DM8 起支持 LIMIT/OFFSET
        assertTrue(dm.supportsLimitSyntax());
    }

    @Test
    void damengAliasAlsoDetected() {
        DbCapability dm = DbCapabilities.of("Dameng");
        assertEquals("dm", dm.product());
    }

    @Test
    void existingVendorsUnchanged() {
        assertEquals("oracle", DbCapabilities.of("Oracle").product());
        assertEquals("mysql", DbCapabilities.of("MySQL").product());
        assertEquals("mysql", DbCapabilities.of("MariaDB").product());
        assertEquals("postgresql", DbCapabilities.of("PostgreSQL").product());
    }
}
