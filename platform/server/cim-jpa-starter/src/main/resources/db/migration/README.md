# 数据库迁移目录约定（cim-jpa-starter）

本目录为 Flyway 迁移脚本的**统一根**，按数据库类型分子目录（见 README §3）：

```
db/migration/
├── common/       # 各库通用脚本（尽量少用，方言差异放各库目录）
├── mysql/        # MySQL / MariaDB
├── oracle/       # Oracle
├── postgresql/   # PostgreSQL
└── h2/           # H2（单测/本地）
```

## 选择规则

由 `CimFlywayAutoConfiguration` 在 `cim.jpa.flyway.enabled=true` 时按**当前数据源的产品名**
自动选择位置：

- `classpath:db/migration/common`
- `classpath:db/migration/{vendor}`，`vendor ∈ {mysql, oracle, postgresql, h2}`

可用 `cim.jpa.flyway.locations` 显式覆盖。

## 规范

1. **主 / 历成对**：对实体 `{X}` 的 `ALTER` 必须同步其历史表 `{X}Hist` / `{X}StateLog`
   （CI 守卫见 §6.2）。
2. 命名 `V{版本}__{描述}.sql`，版本单调递增。
3. 生产环境配合 `spring.jpa.hibernate.ddl-auto=validate`：表结构由 Flyway 管理，
   Hibernate 仅校验，不自动建表。
