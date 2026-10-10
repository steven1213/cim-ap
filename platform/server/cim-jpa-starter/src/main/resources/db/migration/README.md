# 数据库迁移目录约定（cim-jpa-starter）

本目录为 Flyway 迁移脚本的**统一根**，按数据库类型分子目录（见 README §3）：

```
db/migration/
├── common/       # 各库通用脚本（尽量少用，方言差异放各库目录）
├── mysql/        # MySQL / MariaDB
├── oracle/       # Oracle
├── postgresql/   # PostgreSQL
├── dm/           # 达梦 DM（Oracle 兼容，见 dm/README.md）
└── h2/           # H2（单测/本地）
```

## 选择规则

由 `CimFlywayAutoConfiguration` 在 `cim.jpa.flyway.enabled=true` 时按**当前数据源的产品名**
自动选择位置：

- `classpath:db/migration/common`
- `classpath:db/migration/{vendor}`，`vendor ∈ {mysql, oracle, postgresql, dm, h2}`

可用 `cim.jpa.flyway.locations` 显式覆盖。

## 版本空间分配（多模块 / 宿主同库**必守**）

Flyway 把所有 `locations` 合并为**同一套版本序列**——只要两处出现相同版本号（如两个模块各写
`V1__...`），启动即抛 `Found more than one migration with version 1`。因此**版本号必须在全库唯一**，
按下表分配（本表即注册表，新增模块须在此登记）：

| 版本段 | 归属 | 说明 |
| --- | --- | --- |
| `V1`–`V999` | **宿主 ap 自有迁移**（`db/migration/{vendor}/`） | 每个部署单元自用；平台 `common/` 亦落此段 |
| `V1000`–`V1999` | 平台模块 `cim-system` | `db/migration/system/{vendor}/` |
| `V2000`–`V2999` | 平台模块 `cim-i18n-starter` | `db/migration/i18n/{vendor}/` |
| `V3000`–`V3999` | 平台模块 `cim-mq-starter` | 预留（模块迁移上线时登记） |
| `V4000`–`V4999` | 平台模块 `cim-cache-starter` | 预留 |
| `V10000+` | 预留扩展 | 后续模块按 1000 递增段登记 |

> 规定：**平台模块只占用自己的 1000 段**，宿主 ap 只用 `V1–V999`。这样「宿主 + 任意模块组合」
> 都不会版本重号，模块可被任意 ap 无改造引入（`design.md` ADR-10）。

## 规范

1. **主 / 历成对**：对实体 `{X}` 的 `ALTER` 必须同步其历史表 `{X}Hist` / `{X}StateLog`
   （CI 守卫见 §6.2）。
2. 命名 `V{版本}__{描述}.sql`，版本**在所属版本段内**单调递增。
3. 生产环境配合 `spring.jpa.hibernate.ddl-auto=validate`：表结构由 Flyway 管理，
   Hibernate 仅校验，不自动建表。

