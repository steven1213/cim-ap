package com.cim.jpa.db;

/**
 * 由数据库产品名解析 {@link DbCapability} 的工厂（见 README §3）。
 */
public final class DbCapabilities {

    private DbCapabilities() {
    }

    /** 按产品名返回能力实现。 */
    public static DbCapability of(String product) {
        String p = (product == null) ? "" : product.toLowerCase();
        if (p.contains("oracle")) {
            return new SimpleDbCapability("oracle", false, true);
        }
        if (p.contains("mysql") || p.contains("mariadb")) {
            return new SimpleDbCapability("mysql", true, true);
        }
        if (p.contains("postgre")) {
            return new SimpleDbCapability("postgresql", true, true);
        }
        if (p.contains("dm") || p.contains("dameng")) {
            // 达梦 DM：与 Oracle 高度兼容——NULL 默认排最前、无原生 JSON 函数；DM8 起支持 LIMIT/OFFSET。
            // 注意：Hibernate 6 无内置 DmDialect，方言须由业务 app 显式配置（org.hibernate.dialect.DmDialect）。
            return new SimpleDbCapability("dm", false, false);
        }
        return new SimpleDbCapability(p, true, false);
    }

    /** 简单记录型实现。 */
    public record SimpleDbCapability(String product, boolean nullsLastByDefault,
                                     boolean supportsJsonFunctions) implements DbCapability {
    }
}
