# MDS 单位与字典（UOM / Code Table）主数据设计

> 本文是 MDS 的**基础参考数据层**——为全部主数据域提供**单位（UOM）与枚举字典（Code Table）**的统一收口。
>
> **补的缺口**：各域字段大量使用单位（`mds_material.base_uom`、`step_param.unit`、`equip_if_variable.unit`）与枚举（`carrier_type`、`bank_type`、`flow_type`…），此前**只有内联字符串**，没有统一字典、没有换算、没有多语言。
>
> 设计原则延续全篇：**MDS 拥有单位与字典定义；各域引用；下游按 code 消费**。

---

## 0. 定位与边界（拍板）

### 0.1 现状问题（核心）

| 问题 | 后果 |
| --- | --- |
| 单位是自由字符串（`℃`/`C`/`degC` 混用） | 数据比对失败、换算出错 |
| 枚举值散落各域且无展示序/多语言 | 前端下拉各写各的、英文界面无法本地化 |
| 同一枚举在两个域定义不一致 | 语义漂移（如 `carrier_type` 在两处取值不同） |
| 新增枚举值要改代码 | 扩展需发版 |

> **结论（UD1）**：单位与字典是 MDS 的**基础参考数据层**（Reference Data Management），统一收口、统一发布。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 单位定义与换算因子 | **MDS** | `mds_uom` / `mds_uom_conversion` |
| 枚举字典（表 + 项 + 展示序 + 多语言） | **MDS** | `mds_code_table` / `mds_code_table_item` |
| 各域字段的**枚举取值** | **MDS 各域 + 本域字典** | 见 §4.4 的取舍原则 |
| **实际换算计算** | **下游 / 各域服务** | MDS 提供因子，不代算 |
| **UI 展示与本地化渲染** | **前端** | MDS 提供多语言标签 |

### 0.3 与既有主数据的闭环映射

| 引用方 | 字段 | 见 |
| --- | --- | --- |
| 物料基本单位 | `mds_material.base_uom → mds_uom.code` | [material-design §3.2](material-design.md) |
| 用料行单位 | `mds_material_bom_line.uom → mds_uom.code` | [material-design §3.5](material-design.md) |
| 设备变量单位 | `mds_equip_if_variable.unit → mds_uom.code` | [equipment-interface-design §3.2](equipment-interface-design.md) |
| 设备变量枚举 | `mds_equip_if_variable.enum_ref → mds_code_table.code` | [equipment-interface-design §3.2](equipment-interface-design.md) |
| 工艺参数单位 | `mds_process_flow_step_param.unit → mds_uom.code` | [process-flow-design §3.4](process-flow-design.md) |
| 校准公差单位 | `mds_calibration_item.unit → mds_uom.code` | [pm-calibration-design §3.5](pm-calibration-design.md) |
| 各域枚举 | `carrier_type`/`bank_type`/`flow_type`/`state category`… | 各域文档 |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **ISO 80000 / SI 单位制** | 标准单位与量纲体系；换算须可追溯 | `mds_uom`（`uom_type` 量纲）+ `mds_uom_conversion` |
| **ISA-95 / IEC 62264** | 参考数据（Reference Data）作为独立主数据层 | 本域整体定位 |
| **MDM / DAMA-DMBOK** | 参考数据管理（RDM）与码表治理是 MDM 的必备子域 | `mds_code_table` |
| **SEMI E5 / E30** | 数据项类型（如 `U4`/`F8`）与单位在接口中须一致 | `equip_if_variable.unit` 统一引用 |
| **i18n 需求** | 多语言界面需要枚举的多语言标签 | `mds_code_table_item.i18n_json` |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **UOM / 计量单位** | 度量单位（L/KG/PCS/mm/℃/Pa…） | 与"数据精度"无关 |
| **Base UOM / 基本单位** | 某量纲下的标准单位（换算锚点） | 不是各域"默认单位" |
| **UOM Conversion / 换算** | 两单位之间的因子（可含偏移） | 非线性换算（如 ℃↔℉）用 `offset` |
| **Code Table / 码表** | 一组受控枚举（表头） | 不是"数据库表"；是**枚举的治理容器** |
| **Code Table Item / 码表项** | 枚举的一个取值（含展示序/标签/启用） | 与 `enum` 常量区分：这是**可扩展数据** |
| **System Table / 系统码表** | 由平台/域强约束、不允许前端增删的码表 | `is_system=1` 时仅管理员可改 |
| **i18n_json** | 多语言标签（`{"zh-CN":"…","en-US":"…"}`） | 不是"翻译文件"；是码表项属性 |

---

## 3. 实体总览与 ER

```
mds_uom (单位: uom_type 量纲 / is_base)
   └──< mds_uom_conversion (from_uom_id → to_uom_id, factor, offset)

mds_code_table (码表: domain / is_system)
   └──< mds_code_table_item (item_code / item_name / seq / i18n_json / is_default / is_enabled)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 单位 | `mds_uom` | 单位定义 |
| 单位换算 | `mds_uom_conversion` | 换算因子 |
| 码表 | `mds_code_table` | 枚举容器 |
| 码表项 | `mds_code_table_item` | 枚举取值 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_uom`（单位）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(16) | NOT NULL | 单位代码（如 `L`/`KG`/`PCS`/`MM`/`CEL`/`PA`/`SCCM`） |
| `name` | VARCHAR(64) | | 名称 |
| `uom_type` | VARCHAR(16) | NOT NULL | 量纲：`LENGTH`/`WEIGHT`/`VOLUME`/`TIME`/`TEMPERATURE`/`PRESSURE`/`FLOW`/`CONCENTRATION`/`COUNT`/`RATIO`/`OTHER` |
| `is_base` | BIT | DEFAULT 0 | 是否该量纲的基本单位（换算锚点） |
| `symbol` | VARCHAR(16) | | 显示符号（`℃`/`mm`） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)` / 每量纲至多一个 `is_base=1`（服务层校验）。

### 3.2 `mds_uom_conversion`（单位换算）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `from_uom_id` | VARCHAR(16) | NOT NULL, FK→`mds_uom.id` | 源单位 |
| `to_uom_id` | VARCHAR(16) | NOT NULL, FK→`mds_uom.id` | 目标单位 |
| `factor` | DECIMAL(20,10) | NOT NULL | 换算因子 |
| `offset` | DECIMAL(20,10) | DEFAULT 0 | 偏移量（如 ℃→℉：×(9/5)+32） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(from_uom_id, to_uom_id, tenant_id, deleted)`。
- 计算：`value_to = value_from × factor + offset`；同量纲内定义的换算由服务层校验（`uom_type` 一致）。

### 3.3 `mds_code_table`（码表）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 码表代码（如 `CARRIER_TYPE`、`BANK_TYPE`、`E10_CATEGORY`） |
| `name` | VARCHAR(128) | | 名称 |
| `domain` | VARCHAR(24) | | 归属域（`EQUIPMENT`/`CARRIER`/`BANK`/`ROUTE`/`MATERIAL`/`GLOBAL`…） |
| `is_system` | BIT | DEFAULT 1 | 是否系统码表（前端不可增删，仅管理员可改） |
| `is_extensible` | BIT | DEFAULT 0 | 是否允许业务扩展取值 |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.4 `mds_code_table_item`（码表项）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `table_id` | VARCHAR(32) | NOT NULL, FK→`mds_code_table.id` | 所属码表 |
| `item_code` | VARCHAR(64) | NOT NULL | 取值代码（**与各域字段实际存储值一致**） |
| `item_name` | VARCHAR(128) | | 默认名称 |
| `item_value` | VARCHAR(128) | | 附加值（如排序键/权重） |
| `seq` | INT | | 展示顺序 |
| `i18n_json` | JSON | | 多语言标签 |
| `is_default` | BIT | DEFAULT 0 | 是否默认值 |
| `is_enabled` | BIT | DEFAULT 1 | 是否启用（停用不影响历史数据） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(table_id, item_code, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 UD1–UD5）

### 4.1 基础参考数据层定位（UD1）
本域是**被引用方**（各域引用它），自身**不引用业务域**，保证无循环依赖。

### 4.2 单位：量纲 + 基本单位 + 换算（UD2）
- 每个量纲有唯一 `is_base=1` 的基本单位（换算锚点）；
- 任意两单位的换算 = `factor` + `offset`；同量纲校验由服务层完成；
- 各域存**业务单位**（如物料用 `KG`、参数用 `CEL`），展示与换算按需。

### 4.3 码表：可扩展枚举的治理（UD3）
- `is_system=1` + `is_extensible=0`：**强约束枚举**（如 `E10_CATEGORY`），仅平台可改；
- `is_system=0` 或 `is_extensible=1`：**业务可扩展枚举**（如厂内自定义 `reason_type` 子类）；
- `is_enabled=0` 用于**停用**（历史数据仍可解读，但新数据不可选）；
- `seq` + `i18n_json` 支撑前端一致的下拉展示与本地化。

### 4.4 与 Java 枚举的取舍原则（UD4，重要且务实）
| 场景 | 方案 | 理由 |
| --- | --- | --- |
| 核心且稳定的枚举（`flow_type`、`life_type`、`cert_status`…） | **保留 Java enum** | 编译期安全、性能好、不查库 |
| 需**业务扩展**的枚举（停机原因子类、缺陷子类） | **码表（本域）** | 免发版扩展 |
| 需**多语言/展示序/权重**的枚举 | **码表（本域）** | 前端一致渲染 |
| 需**运行时可配**的枚举（如自定义 bin 类型） | **码表（本域）** | 配置即生效 |

> **原则**：**不强推"全量字典化"**——核心枚举保留 Java enum（性能与安全），只有需要扩展/多语言/运行时配置的才落码表。二者通过 `item_code` 与 enum 的 `name()` **约定一致**（由 `DictSyncValidator` 做启动期校验）。

### 4.5 引用与一致性保障（UD5）
- 各域字段的**注释中标注码表 code**（如 `carrier_type ∈ CARRIER_TYPE 码表`）；
- 新增/停用码表项时，`UomDictValidator` 校验：被引用的项不得删除（只能停用）；
- 码表项变更经 [md-distribution-design](md-distribution-design.md) 发布给下游（前端/下游服务刷新缓存）。

### 4.6 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 单位/码表实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 码表项变更落 `{entity}_hist`（**枚举语义变更影响历史数据解读，必须留痕**） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + SI 单位种子 + 各域系统码表种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_uom (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  code        VARCHAR(16) NOT NULL,
  name        VARCHAR(64),
  uom_type    VARCHAR(16) NOT NULL,
  is_base     BIT DEFAULT 0,
  symbol      VARCHAR(16),
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_uom PRIMARY KEY (id),
  CONSTRAINT uq_mds_uom UNIQUE (code, tenant_id, deleted)
);
CREATE INDEX idx_uom_type ON mds_uom (uom_type, is_base);

CREATE TABLE mds_uom_conversion (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  from_uom_id    VARCHAR(16) NOT NULL,
  to_uom_id      VARCHAR(16) NOT NULL,
  factor      DECIMAL(20,10) NOT NULL,
  offset      DECIMAL(20,10) DEFAULT 0,
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_uom_conv PRIMARY KEY (id),
  CONSTRAINT uq_mds_uom_conv UNIQUE (from_uom_id, to_uom_id, tenant_id, deleted),
  CONSTRAINT fk_uomconv_from FOREIGN KEY (from_uom_id) REFERENCES mds_uom (id),
  CONSTRAINT fk_uomconv_to   FOREIGN KEY (to_uom_id)   REFERENCES mds_uom (id)
);

CREATE TABLE mds_code_table (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  code         VARCHAR(64) NOT NULL,
  name         VARCHAR(128),
  domain       VARCHAR(24),
  is_system    BIT DEFAULT 1,
  is_extensible BIT DEFAULT 0,
  status       VARCHAR(16) NOT NULL,
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_code_table PRIMARY KEY (id),
  CONSTRAINT uq_mds_code_table UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_code_table_item (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  table_id    VARCHAR(32) NOT NULL,
  item_code   VARCHAR(64) NOT NULL,
  item_name   VARCHAR(128),
  item_value  VARCHAR(128),
  seq         INT,
  i18n_json   JSON,
  is_default  BIT DEFAULT 0,
  is_enabled  BIT DEFAULT 1,
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_code_item PRIMARY KEY (id),
  CONSTRAINT uq_mds_code_item UNIQUE (table_id, item_code, tenant_id, deleted),
  CONSTRAINT fk_codeitem_table FOREIGN KEY (table_id) REFERENCES mds_code_table (id)
);
CREATE INDEX idx_codeitem_table ON mds_code_table_item (table_id, seq);
```

> 历史表 `mds_uom_hist` / `mds_code_table_item_hist` / … 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.refdata
  ├── entity
  │   ├── Uom.java                   # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── UomConversion.java          # @Entity 继承 BaseDefData
  │   ├── CodeTable.java              # @Entity 继承 BaseDefData
  │   ├── CodeTableItem.java          # @Entity 继承 BaseDefData
  │   └── package-info.java           # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── UomRepository.java
  │   └── CodeTableRepository.java
  ├── service
  │   ├── UomService.java              # convert(value, from, to) 换算
  │   ├── CodeTableService.java        # 码表查询/缓存（供前端与下游）
  │   └── DictSyncValidator.java        # Java enum ↔ 码表 item_code 一致性启动校验
  └── web
      └── RefDataController.java        # 单位/码表查询与维护
```

---

## 8. 待补 / 后续

- **码表种子清单**：把各域已有枚举（`carrier_type`/`bank_type`/`flow_type`/`E10_CATEGORY`/`material_category`…）登记为系统码表种子，形成"枚举全集"。
- **缓存与发布**：码表变更经 [md-distribution-design](md-distribution-design.md) 推送给前端与下游服务，避免各自缓存不一致。
- **单位换算方向**：是否允许反向换算自动推导（`1/factor`），或显式定义双向（`offset` 场景必须显式）。
- **与既有文档闭环**：本文引用 `mds_material`（[material-design](material-design.md)）、`mds_equip_if_variable`（[equipment-interface-design](equipment-interface-design.md)）、`mds_process_flow_step_param`（[process-flow-design](process-flow-design.md)）、`mds_calibration_item`（[pm-calibration-design](pm-calibration-design.md)）及各域枚举字段。
