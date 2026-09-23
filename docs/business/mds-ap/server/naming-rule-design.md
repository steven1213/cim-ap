# MDS 命名/编码规则设计（NamingRule，横切配置）

> 本文是 MDS 的**横切配置**——主数据编码引擎，统一管理位置 / 设备 / 载具 / 配方 / 路线 / 产品 / 仓库**七类核心实体**的 `code` / `name` 自动生成与校验；`entity_type` 枚举可**扩展**至后续新增的扩展主数据域（物料 / 层 / 光罩 / 原因码 / 缺陷 / Bin / 文档 / 采样计划 / SPC 规则…，见 [README §3.9](README.md)）。
> 它与[位置主数据](location-design.md)（D5 分层点分码 `/SZ/F1/LITH/B03`）、[设备主数据](equipment-design.md)（型号/类编码）、[载具](carrier-design.md)（FOUP 条码）等强相关，是「七块主数据」之上的**编码治理层**。
>
> 本层与[约束治理层](constraint-design.md)（定义实体之间的**关系规则**，如 Q-Time / 组批 / 资格 / 准入）并列，构成 MDS 的**两条横切治理层**：编码层管「怎么命名」，约束层管「什么与什么之间必须/不得成立」。两者共享同一套**规则生命周期范式**（`DRAFT→ACTIVE→DEPRECATED` + `revision`），见 constraint-design §4.9。

---

## 0. 决策摘要

### 0.1 边界与状态归属（MDS vs 下游）

| 维度 | 拥有方 | 存储位置 | 说明 |
| --- | --- | --- | --- |
| **命名规则定义**（段组成 / 分隔符 / 生成模式） | **MDS 配置** | `mds_naming_rule` 等 | 本文范围 |
| **序列计数器**（SEQ 段当前值） | **MDS** | `mds_naming_seq` | 生成时自增，行锁保证并发安全 |
| **实体 code 的生成动作** | **MDS**（落库前） | 由各主数据实体的 `code` 列承载 | code 是主数据属性，生成即写 MDS |
| **下游实时系统对 code 的使用** | MES / EAP / AMHS | 各自实时域 | 仅消费 code，不负责生成 |

> **设计哲学（与前七块一致）**：MDS 拥有「定义 + 生成 + 校验」；下游只消费生成的 code。命名规则本身不进入实时态链路。

### 0.2 闭环映射

| 关系 | 引用键 | 指向 | 见 |
| --- | --- | --- | --- |
| 规则 ↔ 实体类型 | `mds_naming_rule.entity_type` ∈ 核心七类 {LOCATION,EQUIPMENT,CARRIER,RECIPE,ROUTE,PRODUCT,BANK} + 扩展域 {MATERIAL,MATERIAL_LOT,LAYER,RETICLE,REASON,DEFECT,BIN,DOCUMENT,SAMPLING_PLAN,SPC_RULE} | 各主数据域（逻辑引用，枚举） | 各 domain 文档（见 [README §3.9](README.md) 扩展域总览） |
| 规则生成结果 → 实体 `code` | 生成的字符串写入各实体的 `code` 列 | 位置 / 设备 / 载具 / 配方 / 路线 / 产品 / 仓库 | 各 domain 文档 |
| ATTR 段 → 父/关联实体字段 | `segment.expr` 支持 `parent.location.code`、`equipment_class.code`、`location.area_code` | 位置层级 / 设备类 | [位置 §2](location-design.md) / [设备 §3.2](equipment-design.md) |
| SEQ 段 → 区域级流水 | `scope_attr=location.area_code` 时，流水按 AREA 独立编号 | 位置 AREA | [位置 §2.3](location-design.md) |
| 生成 code 的唯一性 | 由 `enforce_unique=1` + T2.5 `(code,tenant_id,deleted)` 唯一约束保证 | 平台软删唯一 | [platform design §2.3](../../../platform/server/design.md) |

---

## 1. 行业依据

| 来源 | 实践 | 本文采用 |
| --- | --- | --- |
| **fab MES 编码规范** | 不同实体有固定编码范式：设备 `FAB-AREA-CLASS-####`、载具 `FOUP-####`、位置 `/SITE/FAB/AREA/BAY`；编码需可追溯、可读、可排序 | 段模型表达范式，HIER 段落地位置层级码 |
| **SEMI（无强制标准）** | SEMI 不规定命名，但 fab 约定通常**嵌入工艺/区域/类**语义，便于人读与排障 | ATTR 段引用 area/class，code 自带语义 |
| **MDM 通用实践** | 编码规则作为独立配置模块（Code Rule / Auto-numbering），支持常量/属性/流水/层级/日期/映射；规则可版本化、可 deprecated 而不改存量 | 横切 `mds_naming_rule` + 规则生命周期（§4.6） |
| **与既有 MDS 一致** | 位置 D5 已定「同租户全局唯一 + 分层点分码」；T2.5 软删唯一 | `enforce_unique` 默认 true 契合 T2.5；HIER 段实现点分码 |

---

## 2. 术语澄清

- **NamingRule（编码规则）**：一条规则描述「某类实体的 code 怎么拼」，由有序 segment 组成。
- **Segment（段）**：code 的一个片段，类型决定取值方式（常量 / 属性 / 流水 / 层级 / 日期 / 映射 / 校验位）。
- **HIER（层级路径段）**：沿父链向上拼接祖先 code（如位置 `/SZ/F1/LITH/B03`），对应位置设计 D5 的点分码。
- **SEQ（流水段）**：按 `(rule, segment, scope_key)` 自增的整数；`scope_key` 可绑定区域实现「每区独立编号」。
- **MAP（值→码映射）**：把实体某属性值映射成短码（如 `process_mode=SINGLE_WAFER → 'S'`），保持 code 紧凑。
- **generation_mode**：`AUTO`（落库自动生成，禁止手填）/ `SUGGEST`（生成建议值，允许覆盖）/ `MANUAL`（仅做格式校验，不自动生成）。

---

## 3. 实体与表结构

### 3.1 `mds_naming_rule`（编码规则主记录）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `tenant_id` | VARCHAR(32) | NOT NULL | 租户 |
| `code` | VARCHAR(64) | NOT NULL | 规则代码（如 `RULE-EQUIPMENT`） |
| `name` | VARCHAR(128) | | 显示名 |
| `entity_type` | VARCHAR(16) | NOT NULL | 核心七类：`LOCATION`/`EQUIPMENT`/`CARRIER`/`RECIPE`/`ROUTE`/`PRODUCT`/`BANK`；**扩展域**：`MATERIAL`/`MATERIAL_LOT`/`LAYER`/`RETICLE`/`REASON`/`DEFECT`/`BIN`/`DOCUMENT`/`SAMPLING_PLAN`/`SPC_RULE`（随新增主数据域登记，见 [README §3.9](README.md)） |
| `delimiter` | VARCHAR(4) | NOT NULL DEFAULT '-' | 段间分隔符（`-` 或 `/` 等） |
| `generation_mode` | VARCHAR(16) | NOT NULL | `AUTO`/`SUGGEST`/`MANUAL` |
| `enforce_unique` | BIT | NOT NULL DEFAULT 1 | 生成后是否校验租户内唯一（默认 true，契合 T2.5） |
| `scope_attr` | VARCHAR(64) | | 可选：SEQ 段的 scope 键来源（如 `location.area_code`），空=全局流水 |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED`（仅 ACTIVE 可被引用） |
| `revision` | VARCHAR(16) | NOT NULL | 规则版本（改 pattern 升版本，存量实体不受影响） |
| `description` | VARCHAR(512) | | 基类 |
| `id`/`tenant_id`/`description`/`deleted`/审计列 | — | | 继承 `BaseDefData`；`@History(SNAPSHOT)`；**乐观锁 `version`(BIGINT) 亦由基类提供** |

> ✅ **已按方案 B 统一（2026-09-23）**：本表业务版本字段为 **`revision`**（原 `version`）；乐观锁 `version`(BIGINT) 由基类 `BaseDefData` 提供。规范见 [00-blueprint §11](00-blueprint.md)。

- 唯一：`(code, tenant_id, deleted)`。
- 一个 `entity_type` 可有多条规则（不同版本/不同场景），但同一时刻仅一条 `ACTIVE` 作为默认生成器。

### 3.2 `mds_naming_rule_segment`（段定义，有序）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `tenant_id` | VARCHAR(32) | NOT NULL | |
| `rule_id` | VARCHAR(32) | NOT NULL, FK→`mds_naming_rule.id` | 所属规则 |
| `seq` | INT | NOT NULL | 段顺序（生成时按 seq 拼接） |
| `segment_type` | VARCHAR(16) | NOT NULL | `CONST`/`ATTR`/`SEQ`/`HIER`/`DATE`/`UUID`/`MAP`/`CHECKSUM` |
| `expr` | VARCHAR(128) | | 取值表达式：`CONST`=字面量；`ATTR`=属性路径（`parent.location.code`/`equipment_class.code`）；`DATE`=格式（`yyyyMM`）；`UUID`=长度 |
| `pad_length` | INT | | `SEQ`/`UUID` 补齐长度（如 4 → `0001`） |
| `pad_char` | VARCHAR(1) | | 补齐字符（默认 `0`） |
| `map_id` | VARCHAR(64) | | `MAP` 段引用的映射组 id |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(rule_id, seq, tenant_id, deleted)`。

### 3.3 `mds_naming_seq`（流水计数器）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `tenant_id` | VARCHAR(32) | NOT NULL | |
| `rule_id` | VARCHAR(32) | NOT NULL, FK→`mds_naming_rule.id` | |
| `segment_id` | VARCHAR(32) | NOT NULL, FK→`mds_naming_rule_segment.id` | 对应 SEQ 段 |
| `scope_key` | VARCHAR(64) | | 流水作用域（如 `LITH`）；空=全局；与规则 `scope_attr` 配合 |
| `current_value` | BIGINT | NOT NULL DEFAULT 0 | 当前流水值 |
| `version` | BIGINT | NOT NULL DEFAULT 0 | `@Version` 乐观锁，并发递增安全 |
| `deleted`/审计列 | — | | 继承 `BaseDefData` |

- 唯一：`(rule_id, segment_id, scope_key, tenant_id, deleted)`。
- 生成时 `UPDATE ... SET current_value = current_value + 1 WHERE id=? AND version=?`，失败重试（乐观锁冲突）。

### 3.4 `mds_naming_rule_map`（值→码映射表，供 MAP 段）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `tenant_id` | VARCHAR(32) | NOT NULL | |
| `map_id` | VARCHAR(64) | NOT NULL | 映射组（同规则内多个 MAP 段可共用或分组） |
| `attr_value` | VARCHAR(64) | NOT NULL | 实体属性原值（如 `SINGLE_WAFER`） |
| `code_value` | VARCHAR(16) | NOT NULL | 映射出的短码（如 `S`） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(map_id, attr_value, tenant_id, deleted)`。

---

## 4. 关键设计点

### 4.1 段模型（核心）
一条规则 = 有序 segment 列表，按 `seq` 用 `delimiter` 拼接。各段类型语义：

| segment_type | 取值 | 典型用途 |
| --- | --- | --- |
| `CONST` | 字面量（`expr`） | 固定前缀，如 `FOUP` |
| `ATTR` | 实体字段或父/关联字段（`expr` 属性路径） | 嵌入 `area_code` / `equipment_class.code` |
| `SEQ` | `mds_naming_seq.current_value + 1` | 流水号（区域级或全局） |
| `HIER` | 沿父链向上拼接祖先 code | 位置点分码 `/SZ/F1/LITH/B03` |
| `DATE` | 当前日期 `expr` 格式 | 批次/创建日期片段 |
| `UUID` | 随机后缀（长度 `expr`） | 防冲突后缀 |
| `MAP` | 查 `mds_naming_rule_map` | 属性值→短码 |
| `CHECKSUM` | 前序段计算校验位 | 防手工录入错误 |

### 4.2 位置层级码（落实 D5）
位置规则示例（`delimiter='/'`）：
```
HIER(parent.location.code) + CONST('/') + ATTR(self.code_segment)
```
- 顶层 SITE 无父，`HIER` 退化为自身；逐层向下，子节点 code 自动继承父路径，生成 `/SZ/F1/LITH` 与 `/SZ/F1/LITH/B03`。
- 与位置设计 D5「分层点分码」完全对应，且由引擎保证父子路径一致（避免手填错位）。

### 4.3 设备编码（ATTR + SEQ 组合）
设备规则示例（`delimiter='-'`）：
```
ATTR(location.area_code) + ATTR(equipment_class.code) + SEQ(scope=area_code, pad=4)
→ F1-LITH-SCN-0001
```
- `scope_attr=location.area_code` 使流水按 AREA 独立（LITH 区从 0001 起，ETCH 区也从 0001 起）。
- code 自带「厂-区-类-序号」语义，人读可定位。

### 4.4 序列并发安全
`SEQ` 段每次生成即 `current_value + 1`，依赖 `mds_naming_seq.version` 乐观锁 + 重试；高并发下若冲突频繁，可在 `NamingRuleService` 退化为 `SELECT ... FOR UPDATE` 行锁。流水永不回退（即使实体后续删除，号码不回收，避免与历史记录冲突）。

### 4.5 唯一性保障
- `enforce_unique=1` 时，`NamingRuleService.generate()` 生成后调用唯一性校验（查该实体表 `(code, tenant_id, deleted=0)`）；冲突则抛 `BizCode` 业务异常（复用平台 `Result`/`BizCode`）。
- 生成的 code 始终满足 T2.5 软删唯一约束，与七类实体现有 `code` 列一致。

### 4.6 规则生命周期（与前序同源）
- `status`：`DRAFT`（编辑中）→ `ACTIVE`（生效，可引用）→ `DEPRECATED`（停用，新实体不再用，存量不变）。
- 改 pattern 必须 `cloneAsNewVersion()` 出 `version+1` 新 DRAFT，旧 `ACTIVE` 规则继续服务存量实体，**已生成的 code 不受影响**（与 route/product 版本不可变范式一致）。
- 同一 `entity_type` 仅一条 `ACTIVE` 作为默认生成器；特殊场景可在创建实体时显式指定规则。

### 4.7 与平台底座衔接（强制对齐）
- 所有实体继承 `BaseDefData`：`String id`（雪花/UUIDv7）、`tenant_id`、`@Version`、`deleted`、审计列。
- 唯一约束含 `(..., tenant_id, deleted)`（T2.5）。
- 行级过滤：`@SQLRestriction("deleted=false")` + `@Filter(cimTenantFilter)`；`@FilterDef` 放包 `package-info.java`。
- 历史：`@History(SNAPSHOT)` → `{entity}_hist`，规则变更亦留痕。

---

## 5. DDL（MySQL 示例，主历成对）

```sql
CREATE TABLE mds_naming_rule (
  id              VARCHAR(32)  NOT NULL,
  tenant_id       VARCHAR(32)  NOT NULL,
  code            VARCHAR(64)  NOT NULL,
  name            VARCHAR(128),
  entity_type     VARCHAR(16)  NOT NULL,
  delimiter       VARCHAR(4)   NOT NULL DEFAULT '-',
  generation_mode VARCHAR(16)  NOT NULL,
  enforce_unique  BIT          NOT NULL DEFAULT 1,
  scope_attr      VARCHAR(64),
  status          VARCHAR(16)  NOT NULL,
  revision         VARCHAR(16)  NOT NULL,
  description     VARCHAR(512),
  version     BIGINT       NOT NULL DEFAULT 0,
  deleted         BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_naming_rule PRIMARY KEY (id),
  CONSTRAINT uq_mds_naming_rule_code UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_naming_rule_segment (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  rule_id       VARCHAR(32)  NOT NULL,
  seq           INT          NOT NULL,
  segment_type  VARCHAR(16)  NOT NULL,
  expr          VARCHAR(128),
  pad_length    INT,
  pad_char      VARCHAR(1),
  map_id        VARCHAR(64),
  description   VARCHAR(512),
  version       BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_naming_segment PRIMARY KEY (id),
  CONSTRAINT uq_mds_naming_segment UNIQUE (rule_id, seq, tenant_id, deleted),
  CONSTRAINT fk_ns_rule FOREIGN KEY (rule_id) REFERENCES mds_naming_rule (id)
);

CREATE TABLE mds_naming_seq (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  rule_id       VARCHAR(32)  NOT NULL,
  segment_id    VARCHAR(32)  NOT NULL,
  scope_key     VARCHAR(64),
  current_value BIGINT       NOT NULL DEFAULT 0,
  version       BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_naming_seq PRIMARY KEY (id),
  CONSTRAINT uq_mds_naming_seq UNIQUE (rule_id, segment_id, scope_key, tenant_id, deleted),
  CONSTRAINT fk_nseq_rule FOREIGN KEY (rule_id) REFERENCES mds_naming_rule (id),
  CONSTRAINT fk_nseq_seg  FOREIGN KEY (segment_id) REFERENCES mds_naming_rule_segment (id)
);
CREATE INDEX idx_nseq_segment ON mds_naming_seq (segment_id);

CREATE TABLE mds_naming_rule_map (
  id          VARCHAR(32)  NOT NULL,
  tenant_id   VARCHAR(32)  NOT NULL,
  map_id      VARCHAR(64)  NOT NULL,
  attr_value  VARCHAR(64)  NOT NULL,
  code_value  VARCHAR(16)  NOT NULL,
  description VARCHAR(512),
  version     BIGINT       NOT NULL DEFAULT 0,
  deleted     BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_naming_map PRIMARY KEY (id),
  CONSTRAINT uq_mds_naming_map UNIQUE (map_id, attr_value, tenant_id, deleted)
);
```

---

## 6. 代码结构

```
business/mds-ap/server/src/main/java/com/cim/mds/server/domain/naming/
  ├── NamingRule.java           # @Entity 继承 BaseDefData + @History(SNAPSHOT)；entity_type/generation_mode/status
  ├── NamingRuleSegment.java    # @Entity 继承 BaseDefData；segment_type/expr/pad/map_id
  ├── NamingSeq.java            # @Entity 继承 BaseDefData；current_value + @Version（乐观锁）
  ├── NamingRuleMap.java        # @Entity 继承 BaseDefData；map_id/attr_value/code_value
  ├── NamingRuleService.java    # 继承 AbstractJpaService：generate(entityType, entity)→code；validate(code, rule)；resolveSegments()
  └── NamingRuleValidator.java  # enforce_unique 校验 + 段合法性校验（expr 路径存在性）
```

---

## 7. 待补 / 后续

- **生成时机编排**：`NamingRuleService.generate()` 需在实体 `save` 前调用；HIER/ATTR 引用父实体的，需父先落库（两阶段保存）——与位置/设备的级联创建流程对齐。
- **存量 code 回填**：历史手工 code 可经「校验模式（MANUAL）」批量核对是否符合新规则，但不强制改号。
- **规则可视化**：管理端展示规则拼接预览（给定样例实体算出 code），供工艺/IT 配置。
- **与七类实体的集成测试**：位置点分码、设备区域流水、载具前缀码的端到端生成验证。
