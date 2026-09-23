# MDS 表达式规范（Expression DSL）设计

> 本文是 MDS 的**最底层横切规范**——定义「条件表达式」怎么写、能引用哪些变量、能用哪些函数。
> 它被 **5 处**共同引用：[约束设计](constraint-design.md) 的 `condition_expr`/`CUSTOM_EXPR`、[路线设计](route-design.md) 的 `flow.condition_expr`（分支/返工）、[元治理](md-governance-design.md) 的数据质量规则、[原因码与处置](reason-defect-design.md) 的处置规则条件、[采样与 SPC](sampling-spc-design.md) 的 OCAP。
>
> **不做统一规范的代价**：五处各写一套语法 → 下游要写 5 个解释器；表达式无法跨域复用；无法静态校验。
>
> 设计原则：**MDS 拥有「语法 + 函数/变量注册表 + 静态校验」，下游拥有「运行期求值」**（或直接使用 MDS 提供的求值 SDK）。

---

## 0. 定位与边界（拍板）

### 0.1 为什么必须先定 DSL（核心）

| 若不统一 | 后果 |
| --- | --- |
| 5 处各写语法 | 下游实现 5 个求值器；语义不一致 |
| 变量无注册表 | 表达式里写 `equip.temp` 还是 `equipment.temperature` 无人知道，运行时才报错 |
| 函数无边界的自由调用 | 安全风险（IO/网络）、性能风险（死循环） |
| 无静态校验 | 配错一条约束要等运行时才发现，且可能直接卡住产线 |

> **结论（DSL1）**：表达式是**声明式数据**，必须有**统一语法 + 注册表 + 静态校验**。MDS 提供**语法规范与注册表**，并可提供**求值 SDK**（Java/JS）给下游复用，避免各写一套。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 语法规范（本文） | **MDS（文档约定）** | 全平台一致 |
| 函数注册表 `mds_expr_function` | **MDS** | 白名单，可扩展但受控 |
| 变量注册表 `mds_expr_variable` | **MDS** | 定义「可求值上下文契约」，供下游按此注入 |
| 表达式的**静态校验** | **MDS** | 语法/类型/路径/函数合法性 |
| 表达式的**运行期求值** | **下游**（MES/EAP/AMHS/SPC） | 或使用 MDS 求值 SDK |
| **求值上下文数据** | **下游** | 实时态由下游注入（[realtime-contract-design](realtime-contract-design.md)） |

---

## 1. 行业依据

| 来源 | 要点 | 本文采用 |
| --- | --- | --- |
| **CEL（Common Expression Language，Google）** | 无副作用、可静态类型检查、有求值超时与代价限制 | 整体设计取向（安全、可静态校验、可限额） |
| **JsonLogic / JMESPath** | 轻量 JSON 表达式，便于跨语言实现与前端预览 | 采用 JSON 可序列化的 AST 表达（§4.6） |
| **SEMI E5/E30 条件报告** | 设备侧条件由简单比较/逻辑组合构成 | 运算符集与之对齐 |
| **ISA-95 / fab MES 规则引擎** | 派工/处置/质量规则普遍采用「条件 + 动作」的规则语言 | 本文为其提供**语法基座** |
| **SPC（Western Electric / Nelson）** | 判异规则可参数化为「窗口 + 阈值 + 命中数」 | 规则项用 DSL 表达，见 [sampling-spc-design](sampling-spc-design.md) |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Expression / 表达式** | 求值后返回值的字符串 | 不是脚本：**无语句、无赋值、无循环、无副作用** |
| **Evaluation Context / 求值上下文** | 求值时注入的**变量集合**（对象树） | 由下游构造；变量名须在注册表中 |
| **Path / 路径** | 上下文中的取值路径，如 `equipment.class.code` | 不是数据库字段名；是**契约变量** |
| **Function Registry / 函数注册表** | 允许调用的函数白名单 | 不允许任意函数（安全） |
| **Statically Determinable / 静态可判定** | 不需要实时数据即可判定的部分（语法/类型/常量折叠） | MDS 负责；运行期求值归下游 |
| **Namespace / 命名空间** | 变量的一级前缀（`lot`/`equipment`/`param`…） | 决定变量的语义归属 |
| **DSL Version** | 语法版本 | 语法变更才升版；加函数/变量不算 |

---

## 3. 实体总览与 ER

```
mds_expr_function (函数白名单: code / signature / return_type / is_pure / category)
mds_expr_variable (变量注册表: namespace / path / data_type / unit / source_domain)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 函数注册表 | `mds_expr_function` | 允许使用的函数与签名 |
| 变量注册表 | `mds_expr_variable` | 可引用变量与类型/单位/来源域 |

> 继承 `BaseDefData`（§11）。

### 3.1 `mds_expr_function`（函数注册表）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | 函数名（如 `hours_between`） |
| `category` | VARCHAR(16) | NOT NULL | `MATH`/`TIME`/`STRING`/`COLLECTION`/`LOGIC`/`UNIT`/`DOMAIN` |
| `signature` | VARCHAR(256) | NOT NULL | 签名（如 `(datetime, datetime) -> number`） |
| `return_type` | VARCHAR(16) | NOT NULL | `NUMBER`/`STRING`/`BOOLEAN`/`LIST`/`DATETIME`/`ANY` |
| `is_pure` | BIT | DEFAULT 1 | 是否纯函数（无副作用；**必须为 1**） |
| `cost_weight` | INT | DEFAULT 1 | 求值代价权重（供限额） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.2 `mds_expr_variable`（变量注册表）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `namespace` | VARCHAR(24) | NOT NULL | `lot`/`product`/`route`/`op`/`flow`/`step`/`param`/`equipment`/`carrier`/`reticle`/`material`/`facility`/`env`/`org`/`user`/`ctx` |
| `path` | VARCHAR(128) | NOT NULL | 完整路径（如 `equipment.class.code`） |
| `data_type` | VARCHAR(16) | NOT NULL | `NUMBER`/`STRING`/`BOOLEAN`/`LIST`/`DATETIME`/`ENUM` |
| `unit` | VARCHAR(16) | | 单位（→ [uom-dict-design](uom-dict-design.md)） |
| `source_domain` | VARCHAR(32) | | 提供该变量的域（含 `RUNTIME`=由下游注入） |
| `is_nullable` | BIT | DEFAULT 1 | 是否可能为空（影响表达式写法） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(namespace, path, tenant_id, deleted)`。

---

## 4. 语法规范（DSL 1.0）

### 4.1 字面量

| 类型 | 写法 | 例 |
| --- | --- | --- |
| 数字 | `123`、`-4.5`、`1e3` | `min_minutes > 360` |
| 字符串 | `'...'` 或 `"..."`（**单引号优先**，避免与 JSON 冲突） | `product.tech_node == '28'` |
| 布尔 | `true` / `false` | `equipment.remote_capable == true` |
| 空 | `null` | `retest_days != null` |
| 时长 | 后缀 `s`/`m`/`h`/`d`（归一为分钟） | `360m`、`6h`、`2d` |
| 时刻 | ISO-8601 字符串 + `@` 前缀 | `@2026-09-23T08:00:00+08:00` |
| 枚举 | 大写标识符（须在码表中） | `lot.type == 'PILOT'` |

### 4.2 标识符与路径

```
<namespace>.<field>[.<field>…][ [<key>] ]
```
- 例：`equipment.class.code`、`lot.wafers[0].id`、`step.params['TEMP'].actual`；
- 路径**必须在变量注册表中存在**（否则静态校验报错）；
- 数组用下标或 `[*]` 通配（聚合函数见 §4.5）。

### 4.3 运算符（按优先级从低到高）

| 级别 | 运算符 | 说明 |
| --- | --- | --- |
| 1 | `or` / `\|\|` | 逻辑或（短路） |
| 2 | `and` / `&&` | 逻辑与（短路） |
| 3 | `not` / `!` | 逻辑非 |
| 4 | `==` `!=` `<` `<=` `>` `>=` | 比较（同类型才可比，见 §4.7） |
| 5 | `in` / `not in` | 集合成员 |
| 6 | `+` `-` | 加减（数字或时刻差） |
| 7 | `*` `/` `%` | 乘除模 |
| 8 | `()` | 分组（最高） |

**区间写法**：`a between b and c`（等价 `b <= a and a <= c`），或 `between(a, b, c)`。

### 4.4 逻辑组合的结果

表达式**必须**返回布尔值（用于条件场景）或标量（用于取值场景，如 `CUSTOM_EXPR` 参与比较）。语法上二者都合法，由**使用点**（如约束的 `condition_expr`）声明期望类型。

### 4.5 函数库（初始白名单）

| 类别 | 函数 |
| --- | --- |
| 数学 | `min` `max` `abs` `round(n, d)` `ceil` `floor` `sum(list)` `avg(list)` `count(list)` |
| 时间 | `now()` `date(dt)` `hours_between(a, b)` `minutes_between(a, b)` `days_between(a, b)` `add_hours(dt, n)` |
| 字符串 | `upper` `lower` `trim` `starts_with(s, p)` `ends_with` `contains(s, sub)` `matches(s, regex)` `len` |
| 集合 | `size(list)` `any(list, expr)` `all(list, expr)` `none(list, expr)` `in_set(v, list)` `distinct(list)` |
| 逻辑 | `if(cond, a, b)` `coalesce(a, b, …)` `exists(path)` `is_null(v)` |
| 单位 | `convert(v, from, to)`（→ [uom-dict-design](uom-dict-design.md)） |
| 域函数 | `param_value(stepCode, paramCode)` `ppid_of(equipmentCode, recipeCode)` `qualified(personRef, scope)`（均**只读**） |

> 函数集**宁缺毋滥**：新增须经 [change-mgmt-design](change-mgmt-design.md) 并在注册表登记；**禁止**任何有副作用或 IO 的函数。

### 4.6 规范化存储形式（AST）

表达式**存储为字符串**（人类可读），但服务端解析后**缓存规范化 AST（JSON）**，供：
- 静态校验与类型推导；
- 前端可视化编辑（条件构建器）；
- 下游按 AST 求值（避免各自实现解析器差异）。

```json
{ "op": "and",
  "args": [
    { "op": ">=", "args": [ {"path":"flow.max_minutes"}, {"lit":360} ] },
    { "op": "==", "args": [ {"path":"lot.priority"}, {"lit":"HOT"} ] }
  ] }
```

### 4.7 类型系统与转换

- **不做隐式跨类型转换**（`'28' == 28` 为**错误**，静态校验即报错）；
- 数字统一按 `decimal` 比较（避免浮点误差）；比较前按注册表 `unit` 归一（如 `h` → `min`）；
- `null` 参与比较结果恒为 `false`（除 `is_null`/`coalesce`/`exists`）；
- 枚举比较必须用**码表中的 code**（字符串），不使用展示名。

---

## 5. 求值上下文规范（变量命名空间）

| namespace | 语义 | 典型变量 | 来源 |
| --- | --- | --- | --- |
| `lot` | 运行中的批次 | `id` `type` `wafer_count` `priority` `current_op` `qtime_start_at` | 下游（运行期） |
| `product` | 产品 | `code` `tech_node` `wafer_size` `family_code` | [product-design](product-design.md) |
| `route` | 路线 | `code` `revision` `status` `effective_to` | [route-design](route-design.md) |
| `op` | 工序 | `code` `seq` `area_code` `is_rework` `default_time` | [route-design](route-design.md) |
| `flow` | 工序边 | `flow_type` `from_op` `to_op` `max_minutes` | [route-design](route-design.md) |
| `step` | 工艺流步骤 | `code` `recipe_code` `ppid` `equipment_group` | [process-flow-design](process-flow-design.md) |
| `param` | 工艺参数实测/规范 | `code` `spec_target` `spec_min` `spec_max` `actual` | [param-def-design](param-def-design.md) |
| `equipment` | 设备 | `code` `class.code` `model.code` `tech_nodes[]` `process_mode` `lifecycle_status` `remote_capable` `qualification_state` | [equipment-design](equipment-design.md) |
| `carrier` | 载具 | `code` `type` `wafer_size` `clean_status` `home_stocker` | [carrier-design](carrier-design.md) |
| `reticle` | 光罩 | `code` `layer_code` `admin_status` `exposures_used` `max_exposures` | [reticle-design](reticle-design.md) |
| `material` | 物料 | `code` `class` `shelf_used_days` `life_remaining` | [material-design](material-design.md) |
| `tooling` | 工装 | `code` `tooling_type` `admin_status` `runs_used` `max_runs` | [tooling-design](tooling-design.md) |
| `test_asset` | 测试资产 | `code` `asset_category` `qual_status` `touchdowns_used` `max_touchdowns` | [test-asset-design](test-asset-design.md) |
| `facility` | 厂务 | `code` `type` `admin_status` `available` | [facility-utility-design](facility-utility-design.md) |
| `env` | 环境 | `clean_class` `particle_count` `temp` `humidity` | [cleanliness-env-design](cleanliness-env-design.md) |
| `org` | 组织 | `unit_code` `cost_center` | [org-personnel-design](org-personnel-design.md) |
| `user` | 操作人 | `person_ref` `skills[]` | [org-personnel-design](org-personnel-design.md) |
| `ctx` | 执行上下文 | `site_code` `fab_code` `now` `stage` | 下游 |

> **`RUNTIME` 来源的变量由下游注入**；其余来自 MDS 主数据。运行期求值前，下游须按注册表**校验变量齐备**，缺失即按 §7 降级处理。

---

## 6. 静态可判定 vs 运行期可判定

| 检查项 | 归属 | 说明 |
| --- | --- | --- |
| 语法合法性 | **MDS**（保存时） | 解析失败直接拒存 |
| 路径存在性 | **MDS** | 对照变量注册表校验 |
| 函数白名单与签名 | **MDS** | 对照函数注册表校验 |
| 类型一致性 | **MDS** | 静态类型推导（如 `'x' > 3` 报错） |
| 常量折叠 | **MDS** | 纯常量部分可提前求值并简化 |
| 引用实体存在性 | **MDS** | 如 `param_code` 是否在参数字典中 |
| **运行期求值** | **下游** | 注入上下文后求值 |
| **超时与代价限制** | **下游** | 见 §7 |

---

## 7. 安全与健壮性

| 约束 | 说明 |
| --- | --- |
| **无副作用** | 无赋值、无循环、无 IO、无网络、无随机 |
| **函数白名单** | 仅注册表中的纯函数；`is_pure=1` 强制 |
| **求值限额** | 下游须设置**表达式求值超时**（建议 ≤ 50ms）与**AST 深度/AST 节点数上限**；超限按「求值失败」处理并告警 |
| **代价权重** | `cost_weight` 供下游估算总代价（如约束集合批量求值时的预算控制） |
| **失败策略** | 求值失败**不得**默认放行：区别于 `BLOCK`（阻断）与 `SKIP`（条件不适用），求值失败应产生 `ERROR` 结果并告警，由配置决定是否保守阻断（建议**保守阻断**） |
| **注入防护** | 路径与函数均为白名单，不做字符串拼接求值（禁止 `eval`） |

---

## 8. 五处应用示例

### 8.1 约束：Q-Time（[constraint-design](constraint-design.md)）
```
flow.max_minutes != null
and hours_between(lot.qtime_start_at, now()) <= flow.max_minutes
```
不满足 → `FAIL`，动作由 `on_violation` 参数给出（REWORK/SCRAP/HOLD）。

### 8.2 约束：返工上限
```
lot.rework_count(op.code) >= 2
```

### 8.3 路由分支 / 返工（[route-design](route-design.md)）
```
param['METRO_RESULT'].actual == 'FAIL'
```

### 8.4 处置规则（[reason-defect-design](reason-defect-design.md)）
```
defect_count > 10 and in_set(defect_class, ['PARTICLE','SCRATCH'])
```
→ 动作 `HOLD`。

### 8.5 SPC 判异（[sampling-spc-design](sampling-spc-design.md)）
```
window(lot.measurements, 3).count_beyond(2, 'sigma') >= 2
```

### 8.6 数据质量规则（[md-governance-design](md-governance-design.md)）
```
exists(product.tech_node) and matches(product.code, '^P-[A-Z0-9]+$')
```

> 六例共用**同一套语法与注册表**——这正是统一的收益。

---

## 9. 版本化

| 变更类型 | 是否升版 | 说明 |
| --- | --- | --- |
| 新增函数 / 新增变量 | ❌ 不升 DSL 版本 | 只增注册表记录；旧表达式不受影响 |
| **新增/改运算符语义、改优先级** | ✅ 升 DSL 版本 | 破坏性变更 → 需迁移；建议**长期不做** |
| 函数签名变更 | ✅ 函数级版本 | 旧签名保留（新增 `code_v2`），存量表达式继续可用 |
| 变量类型变更 | ✅ 变量级标记 | 注册表记录 `data_type` 变更历史（`@History`） |

> 表达式本身**随宿主实体版本化**（约束/路由/规则升版本时一并克隆），不单独版本化。

---

## 10. 与 MES 生产执行衔接

| 环节 | MDS 侧 | MES 侧 |
| --- | --- | --- |
| **设计期** | 保存表达式时做**静态校验**（语法/路径/函数/类型）；提供**预览求值**（用样例上下文试算） | — |
| **下发** | 表达式随宿主实体（约束/规则）经 [md-distribution-design](md-distribution-design.md) 下发**含 AST** | 缓存 AST，**不自行解析字符串**（避免实现差异） |
| **运行期** | 提供变量注册表 → MES 按此构造上下文 | 注入实时态变量 → 本地求值（热路径 ≤ 50ms） |
| **求值失败** | — | 产生 `ERROR` + 告警；按配置**保守阻断** |
| **改规则** | 走 [change-mgmt-design](change-mgmt-design.md)；新版本经分发下发 | 版本比对刷新，**无需发版** |
| **可观测** | — | 记录「命中哪条表达式、哪个变量、实际值/阈值」供排障（配合 `message_template`） |

> **对 MES 的核心价值**：把「业务判断」从代码里彻底移除——*改一条 Q-Time 或一条判异规则，只需在 MDS 改数据并经审批发布，MES 不发版*。

---

## 11. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 注册表实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 函数/变量注册表变更落 `{entity}_hist`（**语义变更须留痕**，否则历史表达式解读漂移） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤（不同租户可有各自扩展函数/变量） |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + **标准函数与核心变量种子** |

---

## 12. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_expr_function (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  code        VARCHAR(32) NOT NULL,
  category    VARCHAR(16) NOT NULL,
  signature   VARCHAR(256) NOT NULL,
  return_type VARCHAR(16) NOT NULL,
  is_pure     BIT DEFAULT 1,
  cost_weight INT DEFAULT 1,
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_expr_fn PRIMARY KEY (id),
  CONSTRAINT uq_mds_expr_fn UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_expr_variable (
  id            VARCHAR(32) NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  namespace     VARCHAR(24) NOT NULL,
  path          VARCHAR(128) NOT NULL,
  data_type     VARCHAR(16) NOT NULL,
  unit          VARCHAR(16),
  source_domain VARCHAR(32),
  is_nullable   BIT DEFAULT 1,
  description   VARCHAR(512),
  version_      BIGINT NOT NULL DEFAULT 0,
  deleted       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_expr_var PRIMARY KEY (id),
  CONSTRAINT uq_mds_expr_var UNIQUE (namespace, path, tenant_id, deleted)
);
CREATE INDEX idx_exprvar_ns ON mds_expr_variable (namespace);
```

> 历史表 `mds_expr_function_hist` / `mds_expr_variable_hist` 由 `@History(SNAPSHOT)` 生成。

---

## 13. 代码结构（包路径）

```
com.cim.mds.expr
  ├── entity
  │   ├── ExprFunction.java          # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── ExprVariable.java           # @Entity 继承 BaseDefData
  │   └── package-info.java           # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── ExprFunctionRepository.java
  │   └── ExprVariableRepository.java
  ├── parser
  │   ├── ExprLexer.java               # 词法
  │   ├── ExprParser.java               # 语法 → AST
  │   └── ExprAst.java                  # 规范化 AST（JSON 序列化）
  ├── service
  │   ├── ExprValidateService.java       # 静态校验：语法/路径/函数/类型/常量折叠
  │   ├── ExprPreviewService.java         # 样例上下文预览求值（设计期）
  │   └── ExprRegistryService.java         # 函数/变量注册表维护与导出（下发 MES）
  └── web
      └── ExprController.java             # 校验/预览/注册表查询
```

---

## 14. 待补 / 后续

- **求值 SDK**：提供 Java（MDS/MES 共用）与 JS（前端预览）两版，保证语义一致；SDK 版本与 DSL 版本绑定。
- **可视化条件构建器**：前端基于变量/函数注册表生成拖拽式编辑器，输出同一字符串语法。
- **AST 兼容层**：下游若已有表达式引擎，提供 AST → 目标语法的转换器。
- **与既有文档闭环**：本文被 [constraint-design](constraint-design.md)、[route-design](route-design.md)、[md-governance-design](md-governance-design.md)、[reason-defect-design](reason-defect-design.md)、[sampling-spc-design](sampling-spc-design.md) 引用；变量单位对齐 [uom-dict-design](uom-dict-design.md)；参数变量对齐 [param-def-design](param-def-design.md)。
