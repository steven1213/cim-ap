# 达梦数据库迁移目录（dm）

达梦 DM 与 Oracle 语法高度兼容，绝大多数 DDL/DML 迁移脚本可直接复用 `../oracle/` 目录；
若存在 DM 特有差异（个别数据类型、系统函数、伪列行为），在本目录放置覆盖脚本。

由 `CimFlywayAutoConfiguration` 在 `cim.jpa.flyway.enabled=true` 时按数据源产品名 `dm` 自动选择：

- `classpath:db/migration/common`
- `classpath:db/migration/dm`

可用 `cim.jpa.flyway.locations` 显式覆盖（例如指定为 `oracle` 目录以直接复用 Oracle 脚本）。

## 连接与方言（由业务 app 提供，starter 不内置）

- JDBC 驱动：`com.dameng:DmJdbcDriver18`（驱动类 `dm.jdbc.driver.DmDriver`，URL `jdbc:dm://host:5236`）。
  DM 驱动不在 Maven Central，需业务 app 本地安装或经私仓引入。
- Hibernate 方言：`org.hibernate.dialect.DmDialect`（DM 随库提供，Hibernate 6 对应
  `com.dameng:DmDialect-for-hibernate6.x`）。**必须显式配置**，因 Hibernate 6 无内置 DmDialect：

  ```yaml
  spring:
    jpa:
      properties:
        hibernate:
          dialect: org.hibernate.dialect.DmDialect
  ```

- Flyway：Spring Boot 3.3.5 使用 Flyway 10.x，`flyway-database-dameng` 社区模块在该版本可能需
  Teams 许可；若不可用，直接复用 `oracle` 目录脚本（DM 与 Oracle 兼容）或经
  `cim.jpa.flyway.locations` 指向自有脚本即可。

## 能力约定（见 `DbCapabilities`）

- `nullsLastByDefault=false`：DM 默认 NULL 排最前，业务排序需显式 `NULLS LAST`。
- `supportsJsonFunctions=false`：无原生 JSON 查询函数，JSON 字段走 `@Convert` 存 `TEXT/CLOB` 字符串。
- `supportsLimitSyntax=true`：DM8 起支持 `LIMIT/OFFSET`。
