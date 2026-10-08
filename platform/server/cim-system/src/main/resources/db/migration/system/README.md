# cim-system 模块迁移目录

本模块**自带** schema（见 design.md §2.10）：表结构属于模块，宿主 ap 不需要也不应该重复维护。

```
db/migration/system/
├── mysql/V1__init_system_tables.sql
├── postgresql/V1__init_system_tables.sql
├── oracle/V1__init_system_tables.sql
└── dm/V1__init_system_tables.sql        # 由 DdlExportTest 生成；缺 DmDialect 时回退 Oracle 方言
```

## 如何被加载

`CimSystemFlywayConfiguration` 在 `cim.jpa.flyway.enabled=true` 时注册一个
`FlywayConfigurationCustomizer`，把 `classpath:db/migration/system/{vendor}` **追加**到 Flyway
的 locations（`{vendor}` 由当前数据源产品名解析，与 `cim-jpa-starter` 同一套 `DbCapabilities`）。

因此最终 locations 通常是：

```
classpath:db/migration/common          # 平台通用（cim-bootstrap 侧）
classpath:db/migration/{vendor}        # 宿主 ap 自有迁移
classpath:db/migration/system/{vendor} # 本模块自带迁移（由模块追加）
```

> 顺序保证：`CimFlywayAutoConfiguration.cimFlywayLocationsCustomizer` 会**整体改写** locations，
> 故它声明了 `@Order(HIGHEST_PRECEDENCE)`；本模块的 customizer 用更高的 order 值，确保是「追加」。

## 脚本不是手写的

19 张表（含 7 张 `*Hist` 历史表、3 张关联表、2 张日志表）× 4 种方言，手写必然与实体漂移——
而「主/历 DDL 成对」正是平台 §6.2 的 CI 守卫项。故脚本由**实体元数据导出**：

```bash
/tmp/mvnx.sh -pl cim-system -am test -Dtest=DdlExportTest -Dcim.ddl.export=true \
    -Dsurefire.failIfNoSpecifiedTests=false
```

实体变更后重跑该命令，再对生成结果做 diff 审视（工具在 `src/test/java/com/cim/system/tool/`，
默认 `assumeTrue` 跳过，不污染常规 `mvn test`）。

### 达梦 DM

Hibernate 6.x 未内置 `DmDialect`，故导出工具**回退到 Oracle 方言**（DM 与 Oracle 兼容）。
引入达梦官方方言包（`DmDialect-for-hibernate6.x`）后，本目录建议重新生成一次，
以免 `ddl-auto=validate` 在类型名（`varchar2` vs `varchar`）上产生误报。

## 规范

1. **主 / 历成对**：对 `{X}` 的 `ALTER` 必须同步其历史表 `{X}Hist`（§6.2 守卫）。
2. 命名 `V{版本}__{描述}.sql`，版本单调递增；本模块后续脚本从 `V2` 起。
3. 日志表（`sys_operation_log` / `sys_login_log`）是流水表，**无**历史表——
   这是设计使然（`BaseEventData` 自身即流水），不是遗漏。
