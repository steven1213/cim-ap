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
        return new SimpleDbCapability(p, true, false);
    }

    /** 简单记录型实现。 */
    public record SimpleDbCapability(String product, boolean nullsLastByDefault,
                                     boolean supportsJsonFunctions) implements DbCapability {
    }
}
