package com.cim.system.tool;

import com.cim.system.config.SysConfig;
import com.cim.system.config.SysConfigHist;
import com.cim.system.dict.SysDict;
import com.cim.system.dict.SysDictHist;
import com.cim.system.dict.SysDictItem;
import com.cim.system.dict.SysDictItemHist;
import com.cim.system.log.LoginLog;
import com.cim.system.log.OperationLog;
import com.cim.system.menu.SysMenu;
import com.cim.system.menu.SysMenuHist;
import com.cim.system.permission.SysPermission;
import com.cim.system.permission.SysPermissionHist;
import com.cim.system.role.SysRole;
import com.cim.system.role.SysRoleHist;
import com.cim.system.role.SysRoleMenu;
import com.cim.system.role.SysRolePerm;
import com.cim.system.role.SysUserRole;
import com.cim.system.user.SysUser;
import com.cim.system.user.SysUserHist;
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
 * 落到 {@code src/main/resources/db/migration/system/{vendor}/V1__init_system_tables.sql}。
 *
 * <p><b>为什么用生成而不是手写</b>：本模块 19 张表（含 7 张 {@code *Hist} 历史表与 3 张关联表）
 * × 4 种方言，手写必然与实体漂移；而「主/历成对」正是平台 §6.2 的 CI 守卫项。
 * 由实体（单一事实源）导出，保证 DDL 与实体、与历史表映射三者永不脱节；
 * 实体变更后重跑本工具即可比对增量。</p>
 *
 * <p><b>默认跳过</b>（{@code assumeTrue}），不污染常规 {@code mvn test}。重新生成：</p>
 * <pre>
 * /tmp/mvnx.sh -pl cim-system -am test -Dtest=DdlExportTest -Dcim.ddl.export=true \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>刻意<b>不设置</b>物理命名策略：所有 {@code @Table}/{@code @Column} 均已显式小写蛇形，
 * 策略在此为空操作；显式书写也让生成结果可预期、可 diff。</p>
 */
class DdlExportTest {

    /** 参与建表的实体（主实体、历史实体、关联实体、流水实体）。 */
    private static final List<Class<?>> ENTITIES = List.of(
            SysUser.class, SysUserHist.class,
            SysRole.class, SysRoleHist.class,
            SysUserRole.class, SysRolePerm.class, SysRoleMenu.class,
            SysPermission.class, SysPermissionHist.class,
            SysMenu.class, SysMenuHist.class,
            SysDict.class, SysDictHist.class, SysDictItem.class, SysDictItemHist.class,
            SysConfig.class, SysConfigHist.class,
            OperationLog.class, LoginLog.class);

    /** 方言 → 迁移子目录（与 {@code CimFlywayAutoConfiguration} 的 vendor 命名一致）。 */
    private static final Map<String, String> DIALECTS = new LinkedHashMap<>();

    static {
        DIALECTS.put("mysql", "org.hibernate.dialect.MySQLDialect");
        DIALECTS.put("postgresql", "org.hibernate.dialect.PostgreSQLDialect");
        DIALECTS.put("oracle", "org.hibernate.dialect.OracleDialect");
        // 达梦 DM：Hibernate 6 起内置 DmDialect；若当前版本缺失则回退 Oracle 方言（DM 与 Oracle 兼容）
        DIALECTS.put("dm", "org.hibernate.dialect.DmDialect");
        // H2 供集成测试用：让测试真跑一遍 Flyway，从而验证生成的 DDL 可执行
        DIALECTS.put("h2", "org.hibernate.dialect.H2Dialect");
    }

    @Test
    void exportModuleDdl() {
        assumeTrue(Boolean.getBoolean("cim.ddl.export"),
                "DDL 导出为工具动作，默认跳过；需 -Dcim.ddl.export=true 触发");

        DIALECTS.forEach(this::exportDialect);
    }

    private void exportDialect(String vendor, String dialectClass) {
        File target = new File("src/main/resources/db/migration/system/" + vendor
                + "/V1__init_system_tables.sql");
        File parent = target.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("cannot create dir: " + parent);
        }

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("hibernate.dialect", resolveDialect(vendor, dialectClass));
        // 同时给出 JPA 与 Hibernate 两套键，兼容不同版本对「脚本生成动作」的读取路径
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
            // 读 package-info 的 @FilterDef（缺失会让带 @Filter 的实体解析失败）
            sources.addPackage("com.cim.system");
            ENTITIES.forEach(sources::addAnnotatedClass);
            Metadata metadata = sources.buildMetadata();
            // 只产出 SCRIPT（未配置 hbm2ddl.auto，故不会尝试连库）
            // 末位 DelayedDropRegistry 传 null：本工具不涉及延迟删除注册
            SchemaManagementToolCoordinator.process(metadata, registry, settings, null);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
        System.out.println("[DDL] " + vendor + " -> " + target.getPath());
    }

    /** 方言类缺失时回退到 Oracle 方言（DM 与 Oracle 兼容）。 */
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
