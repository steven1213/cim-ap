package com.cim.jpa.sqllog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SqlLogFormatter} 单元测试：渲染结果必须是「可直接执行」的 SQL。 */
class SqlLogFormatterTest {

    private static Map<Integer, Object> params(Object... values) {
        Map<Integer, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            map.put(i + 1, values[i]);
        }
        return map;
    }

    @Test
    @DisplayName("占位符按类型内联为字面量（含单引号转义）")
    void inlinesTypedLiterals() {
        String sql = "select * from t where id = ? and name = ? and amount = ? and flag = ? and memo is ?";
        String out = SqlLogFormatter.toExecutable(sql,
                params(7L, "O'Hara", new BigDecimal("12.50"), true, null), 4000);
        assertEquals("select * from t where id = 7 and name = 'O''Hara' "
                + "and amount = 12.50 and flag = true and memo is NULL", out);
    }

    @Test
    @DisplayName("字符串字面量内的 ? 与注释不参与替换")
    void ignoresPlaceholdersInLiteralsAndComments() {
        String sql = "select * from t /* c1 ? c2 */ where note = 'a?b' and id = ? -- tail ?\n and v = ?";
        String out = SqlLogFormatter.toExecutable(sql, params(1, 2), 4000);
        assertEquals("select * from t where note = 'a?b' and id = 1 and v = 2", out);
    }

    @Test
    @DisplayName("时间/UUID/字节数组按可执行字面量渲染")
    void rendersTemporalAndBinary() {
        assertEquals("'2026-11-09 15:00:00.0'",
                SqlLogFormatter.literal(Timestamp.valueOf("2026-11-09 15:00:00")));
        assertEquals("'2026-11-09'", SqlLogFormatter.literal(LocalDate.of(2026, 11, 9)));
        assertEquals("X'0a0b'", SqlLogFormatter.literal(new byte[]{0x0a, 0x0b}));
        assertEquals("'OK'", SqlLogFormatter.literal(SomeEnum.OK));
    }

    @Test
    @DisplayName("多行/多空白折叠为单行，换行参数压平为空格")
    void collapsesWhitespace() {
        String sql = "select\n    a,\n    b\n  from t\n where c = ?";
        assertEquals("select a, b from t where c = 'x y'",
                SqlLogFormatter.toExecutable(sql, params("x\ny"), 4000));
    }

    @Test
    @DisplayName("超长输出截断；未赋值占位符保留 ?")
    void truncatesAndKeepsUnboundPlaceholder() {
        String out = SqlLogFormatter.toExecutable("select ? from t", new LinkedHashMap<>(), 4000);
        assertEquals("select ? from t", out);

        Map<Integer, Object> p = params("0123456789".repeat(20));
        String truncated = SqlLogFormatter.toExecutable("select * from t where a = ?", p, 60);
        assertTrue(truncated.endsWith("<truncated>"), truncated);
        assertTrue(truncated.length() <= 80, "长度应受控：" + truncated.length());
    }

    @Test
    @DisplayName("单参数超长时按参数级截断，避免 CLOB 撑爆日志")
    void truncatesHugeParam() {
        String huge = "abcdefghij".repeat(200);
        String out = SqlLogFormatter.toExecutable("select * from t where a = ?", params(huge), 4000);
        assertTrue(out.contains("<truncated><len=2000>"), out);
        assertTrue(out.length() < 800, "参数应被截断：" + out.length());
    }

    private enum SomeEnum {OK}
}
