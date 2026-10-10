# cim-i18n-starter 模块迁移目录

本模块**自带** schema：`sys_locale`（语言目录）+ `sys_i18n`（译文表，`UNIQUE(locale_code, code)`），
由 `I18nFlywayConfiguration` 追加为 Flyway location（**追加**语义依赖 `cim-jpa-starter` 的
`cimFlywayLocationsCustomizer` 声明 `@Order(HIGHEST_PRECEDENCE)`）。

```
db/migration/i18n/
├── mysql/V2000__init_i18n_tables.sql
├── postgresql/V2000__init_i18n_tables.sql
├── oracle/V2000__init_i18n_tables.sql
├── dm/V2000__init_i18n_tables.sql          # 由 DdlExportTest 生成；缺 DmDialect 时回退 Oracle 方言
└── h2/V2000__init_i18n_tables.sql          # 单测/本地真跑 Flyway
```

> **版本段 `V2000–V2999`**：本模块属平台模块，按 `cim-jpa-starter` 的
> `db/migration/README.md`「版本空间分配」占用 2000 段（`cim-system` 占 1000 段），
> 与宿主 ap（`V1–V999`）互不重号。本模块后续脚本从 `V2001` 起。

## 脚本不是手写的

两张表的 DDL 由实体元数据导出（`src/test/java/com/cim/i18n/tool/DdlExportTest`，
默认 `assumeTrue` 跳过，不污染常规 `mvn test`）：

```bash
/tmp/mvnx.sh -pl cim-i18n-starter -am test -Dtest=DdlExportTest -Dcim.ddl.export=true \
    -Dsurefire.failIfNoSpecifiedTests=false
```

实体变更后重跑并 diff 审视。规范同平台：命名 `V{版本}__{描述}.sql`，主/历成对（本模块两表无历史表）。
