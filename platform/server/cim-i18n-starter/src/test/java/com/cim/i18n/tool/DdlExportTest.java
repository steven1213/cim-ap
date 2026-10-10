package com.cim.i18n.tool;

import com.cim.i18n.model.SysI18n;
import com.cim.i18n.model.SysLocale;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 模块 DDL 生成工具（<b>非业务测试</b>）：把实体元数据导出为各数据库方言的建表脚本，
 * 落到 {@code src/main/resources/db/migration/i18n/{vendor}/V2000__init_i18n_tables.sql}。
 *
 * <p><b>版本段</b>：本模块走保留版本段 {@code V2000–V2999}（见 {@code cim-jpa-starter} 的
 * {@code db/migration/README.md}「版本空间分配」），与 {@code cim-system}（V1000–V1999）
 * 及宿主 ap（V1–V999）互不重号。</p>
 *
 * <p>与 {@code cim-system} 的 {@code DdlExportTest} 同构：DDL 由实体（单一事实源）导出，
 * 保证脚本与实体、与 {@code ddl-auto: validate} 永不脱节。</p>
 *
 * <p>默认跳过（{@code assumeTrue}）。重新生成：</p>
 * <pre>
 * /tmp/mvnx.sh -pl cim-i18n-starter -am test -Dtest=DdlExportTest -Dcim.ddl.export=true \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
class DdlExportTest {

    private static final List<Class<?>> ENTITIES = List.of(SysLocale.class, SysI18n.class);

    private static final Map<String, String> DIALECTS = new LinkedHashMap<>();

    static {
        DIALECTS.put("mysql", "org.hibernate.dialect.MySQLDialect");
        DIALECTS.put("postgresql", "org.hibernate.dialect.PostgreSQLDialect");
        DIALECTS.put("oracle", "org.hibernate.dialect.OracleDialect");
        DIALECTS.put("dm", "org.hibernate.dialect.DmDialect");
        DIALECTS.put("h2", "org.hibernate.dialect.H2Dialect");
    }

    @Test
    void exportModuleDdl() {
        assumeTrue(Boolean.getBoolean("cim.ddl.export"),
                "DDL 导出为工具动作，默认跳过；需 -Dcim.ddl.export=true 触发");
        DIALECTS.forEach(this::exportDialect);
    }

    private void exportDialect(String vendor, String dialectClass) {
        File target = new File("src/main/resources/db/migration/i18n/" + vendor
                + "/V2000__init_i18n_tables.sql");
        File parent = target.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("cannot create dir: " + parent);
        }

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("hibernate.dialect", resolveDialect(vendor, dialectClass));
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", target.getAbsolutePath());
        settings.put("hibernate.hbm2ddl.script.action", "create");
        settings.put("hibernate.hbm2ddl.script.create-target", target.getAbsolutePath());
        settings.put("hibernate.hbm2ddl.scripts.action", "create");
        settings.put("hibernate.hbm2ddl.scripts.create-target", target.getAbsolutePath());

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            sources.addPackage("com.cim.i18n");
            ENTITIES.forEach(sources::addAnnotatedClass);
            Metadata metadata = sources.buildMetadata();
            SchemaManagementToolCoordinator.process(metadata, registry, settings, null);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
        System.out.println("[DDL] " + vendor + " -> " + target.getPath());
    }

    private String resolveDialect(String vendor, String dialectClass) {
        try {
            Class.forName(dialectClass);
            return dialectClass;
        } catch (ClassNotFoundException e) {
            System.out.println("[DDL] " + dialectClass + " not present, fallback OracleDialect for " + vendor);
            return "org.hibernate.dialect.OracleDialect";
        }
    }
}
