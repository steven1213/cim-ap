package com.cim.jpa.db;

/**
 * 数据库方言能力抽象：把跨库差异（NULL 排序、字符串拼接、JSON 函数、分页方言）收敛到
 * 一个接口，业务只调能力、不写方言分支（见 README §3）。
 */
public interface DbCapability {

    /** 数据库产品标识（小写）：{@code mysql} / {@code oracle} / {@code postgresql}。 */
    String product();

    /** NULL 值是否默认排在最后（Oracle 默认在最前，需显式 {@code NULLS LAST}）。 */
    boolean nullsLastByDefault();

    /** 是否支持原生 JSON 查询函数。 */
    boolean supportsJsonFunctions();

    /** 是否支持 {@code LIMIT} 语法（Oracle 12c 以下需 ROWNUM）。 */
    default boolean supportsLimitSyntax() {
        return !"oracle".equals(product());
    }
}
