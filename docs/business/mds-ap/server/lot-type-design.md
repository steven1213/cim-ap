# MDS 批次类型与策略（Lot Type / Batch Policy）设计

> 本文承接[产品主数据](product-design.md)、[工艺路线](route-design.md)、[约束设计](constraint-design.md)、[采样与 SPC](sampling-spc-design.md)，
> 补全 MDS 的**批次类型（Lot Type）与优先等级（Priority Class）** 主数据。
>
> **边界**：**lot 实例归 MES**（运行时对象），**lot 类型定义与策略归 MDS**。这与全篇「定义 vs 实例」的分界一致。
>
> **补的缺口**：全库检索 `批次类型|lot_type|生产批|工程批` **零命中**——即 MES 投料时"这批是什么性质"无定义可依，导致**优先序、能否混批、是否计入良率/OEE** 只能写死在 MES 代码里。

---

## 0. 定位与边界（拍板）

### 0.1 为什么必须建（核心）

| 现象 | 不建的后果 |
| --- | --- |
| 工程批（ENG）与量产批（PROD）混在同一队列 | 优先序错乱：工程批抢占产能，或量产批被工程批拖慢 |
| 认证批（QUAL/CERT）与量产批混批 | **认证数据被污染**，客户稽核不通过 |
| 监测片（MONITOR）计入良率 | **良率失真** |
| 工程批计入 OEE 产出 | **设备效率失真** |

> **结论（LT1）**：批次类型是**影响全厂口径的基础语义**（良率 / OEE / WIP / 优先序 / 混批），属 MDS 主数据。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有者 | 说明 |
| --- | --- | --- |
| 批次类型定义与策略（计数口径/混批/拆分） | **MDS** | `mds_lot_type` |
| 优先等级定义 | **MDS** | `mds_priority_class` |
| **lot 实例**（某个具体批次） | **MES** | 运行时对象，MDS 不存 |
| **lot 当前优先级/当前工序** | **MES** | 实时态 |
| 混批的实际执行 | **MES** | MDS 给策略 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 批次类型 ↔ 约束 | `BATCHING` 族约束引用类型策略 | [constraint-design §4.2](constraint-design.md) |
| 批次类型 ↔ SPC | `is_yield_counted` 决定是否纳入 SPC 统计 | [sampling-spc-design](sampling-spc-design.md) |
| 批次类型 ↔ 产品 | 产品可声明默认批次类型（可选扩展） | [product-design §3.2](product-design.md) |
| 批次类型 ↔ 表达式 | `lot.type` / `lot.priority` 变量 | [expression-dsl-design §5](expression-dsl-design.md) |
| 批次类型 ↔ 路由分支 | 工程批可走不同分支（`condition_expr` 引用 `lot.type`） | [route-design §4.3](route-design.md) |
| 编码规则 | `entity_type=LOT_TYPE`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E10 / E79** | RAM/OEE 计算需区分「生产性产出」与非生产性（工程/监测） | `is_oee_counted` |
| **SEMI E87 / E94** | lot 与 carrier 关联；lot 属性影响流转 | `lot.type` 进入表达式变量 |
| **fab 良率实践** | 良率统计**排除**工程批与监测片；认证批单独统计 | `is_yield_counted` |
| **APS/排程实践** | 优先序（Hot Lot / Super Hot）驱动派工 | `mds_priority_class` |
| **混批规则** | 同批要求同产品/同配方/同工艺条件；工程批通常禁混 | `allow_mix` + 已有 `BATCH_MIX_ALLOWED` 约束 |
| **IATF 16949** | 认证批与量产批须可区分并可追溯 | `lot_category=CERTIFICATION` |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Lot Type / 批次类型** | 批次的**性质定义**（生产/工程/认证/监测…） | 不是"批次大小"；不含 lot id |
| **lot_category** | 类型的语义归类枚举 | 与 `lot_type.code` 区分：category 是粗分类，code 是可扩展的具体类型 |
| **Priority Class / 优先等级** | 优先序定义（含 Hot Lot） | 不是"lot 类型"；同一类型可有不同优先级 |
| **is_yield_counted** | 是否计入良率 | 不是"是否有产出" |
| **allow_mix / 混批** | 是否允许与他人合批 | 与"拆分（split）"不同 |
| **WIP 口径** | 是否计入在制 | 工程批通常计入 WIP（占产能）但不计入产出 |

---

## 3. 实体总览与 ER

```
mds_lot_type (批次类型: lot_category / 计数口径 / 混批与拆分策略)
        └──> mds_priority_class (优先等级: level / weight / is_hot)

被引用: 约束(BATCHING) / 表达式变量(lot.type, lot.priority) / 路由分支条件
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 批次类型 | `mds_lot_type` | 类型定义与策略 |
| 优先等级 | `mds_priority_class` | 优先级定义 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_lot_type`（批次类型）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | 类型代码（如 `PROD`/`ENG`/`PILOT`/`QUAL`/`CERT`/`MONITOR`/`RECLAIM`） |
| `name` | VARCHAR(128) | | 名称 |
| `lot_category` | VARCHAR(16) | NOT NULL | `PRODUCTION`（量产）/`ENGINEERING`（工程）/`PILOT`（试产）/`QUALIFICATION`（认证/验机）/`CERTIFICATION`（客户认证）/`MONITOR`（监测片）/`RECLAIM`（回收片）/`DUMMY`（挡片） |
| `default_priority_id` | VARCHAR(32) | FK→`mds_priority_class.id` | 默认优先等级 |
| `is_yield_counted` | BIT | DEFAULT 1 | **是否计入良率**（工程/监测通常为 0） |
| `is_wip_counted` | BIT | DEFAULT 1 | 是否计入 WIP |
| `is_oee_counted` | BIT | DEFAULT 1 | **是否计入 OEE 产出**（工程/监测通常为 0） |
| `is_spc_counted` | BIT | DEFAULT 1 | 是否纳入 SPC 统计（避免工程数据污染基线） |
| `allow_mix` | BIT | DEFAULT 0 | **是否允许与他人混批**（工程/认证通常为 0） |
| `is_splittable` | BIT | DEFAULT 1 | 是否允许拆分 |
| `is_mergeable` | BIT | DEFAULT 1 | 是否允许合并 |
| `min_qty`/`max_qty` | INT | | 片数上下限（与设备 `process_mode` 协同） |
| `requires_approval` | BIT | DEFAULT 0 | 投料是否需审批 |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.2 `mds_priority_class`（优先等级）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | 等级代码（`SUPER_HOT`/`HOT`/`NORMAL`/`LOW`） |
| `name` | VARCHAR(128) | | 名称 |
| `level` | INT | NOT NULL | 等级数值（越大越优先） |
| `weight` | DECIMAL(8,3) | | 派工权重（供 APS 计算） |
| `is_hot` | BIT | DEFAULT 0 | 是否 Hot Lot（触发加急流程/通知） |
| `max_ratio` | DECIMAL(5,2) | | 产能占用上限比例（防 Hot Lot 泛滥） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 LT1–LT5）

### 4.1 定义归 MDS、实例归 MES（LT1）
MDS 只定义"有哪些类型、每类的策略"；`lot` 实例与其当前类型由 MES 持有。MES 投料时**引用 `lot_type.code`**，据此决定口径与行为。

### 4.2 计数口径是核心价值（LT2）
四个口径开关（`is_yield_counted` / `is_wip_counted` / `is_oee_counted` / `is_spc_counted`）解决**报表失真**问题——这是本域最难被替代的价值点。任一报表系统都需按此过滤。

### 4.3 混批与拆分（LT3）
- `allow_mix=0` 的类型（工程/认证）**禁止合批**；
- 与 [constraint-design](constraint-design.md) 的 `BATCH_MIX_ALLOWED`/`BATCH_SAME_RECIPE` 约束配合：设备类级规则 + 批次类型级规则**双保险**；
- `is_splittable`/`is_mergeable` 支持工程批的特殊流转（不可拆，保证实验完整性）。

### 4.4 优先等级与 Hot Lot（LT4）
- `level` 驱动派工排序（MES/APS 使用）；
- `is_hot` 触发加急流程（通知、专用通道）；
- `max_ratio` **防 Hot Lot 泛滥**（若所有批都是 Hot，等于没有 Hot）——这是行业里常见的管理手段。

### 4.5 与路由分支、SPC、约束的联动（LT5）
- **路由分支**：`condition_expr` 可引用 `lot.type`，使工程批走不同工艺分支（[route-design §4.3](route-design.md)）；
- **SPC**：`is_spc_counted=0` 的批次不进控制图基线；
- **约束**：`BATCHING` 族约束引用类型策略（[constraint-design](constraint-design.md)）。

### 4.6 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 批次类型实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 类型与口径变更落 `{entity}_hist`（**口径变更直接影响历史报表解读，必须留痕**） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + 标准类型种子（PROD/ENG/PILOT/QUAL/CERT/MONITOR/DUMMY） |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_lot_type (
  id                   VARCHAR(32) NOT NULL,
  tenant_id            VARCHAR(32) NOT NULL,
  code                 VARCHAR(32) NOT NULL,
  name                 VARCHAR(128),
  lot_category         VARCHAR(16) NOT NULL,
  default_priority_id VARCHAR(32),
  is_yield_counted     BIT DEFAULT 1,
  is_wip_counted       BIT DEFAULT 1,
  is_oee_counted       BIT DEFAULT 1,
  is_spc_counted       BIT DEFAULT 1,
  allow_mix            BIT DEFAULT 0,
  is_splittable        BIT DEFAULT 1,
  is_mergeable         BIT DEFAULT 1,
  min_qty              INT,
  max_qty              INT,
  requires_approval    BIT DEFAULT 0,
  status               VARCHAR(16) NOT NULL,
  description          VARCHAR(512),
  version_             BIGINT NOT NULL DEFAULT 0,
  deleted              BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_lot_type PRIMARY KEY (id),
  CONSTRAINT uq_mds_lot_type UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_lottype_prio FOREIGN KEY (default_priority_id) REFERENCES mds_priority_class (id)
);

CREATE TABLE mds_priority_class (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  code        VARCHAR(32) NOT NULL,
  name        VARCHAR(128),
  level       INT NOT NULL,
  weight      DECIMAL(8,3),
  is_hot      BIT DEFAULT 0,
  max_ratio   DECIMAL(5,2),
  status      VARCHAR(16) NOT NULL,
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_prio PRIMARY KEY (id),
  CONSTRAINT uq_mds_prio UNIQUE (code, tenant_id, deleted)
);
```

> 历史表由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.lottype
  ├── entity
  │   ├── LotType.java                # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── PriorityClass.java           # @Entity 继承 BaseDefData
  │   └── package-info.java            # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── LotTypeRepository.java
  │   └── PriorityClassRepository.java
  ├── service
  │   ├── LotTypeService.java           # 继承 AbstractJpaService
  │   └── LotTypeValidator.java          # 优先级存在性、口径组合合理性（如 MONITOR 不得 is_yield_counted）
  └── web
      └── LotTypeController.java         # 类型/优先级查询与维护
```

---

## 8. 与 MES 生产执行衔接

| 场景 | MDS 提供 | MES 使用 |
| --- | --- | --- |
| **投料** | `mds_lot_type` 全量定义 + 默认优先级 | MES 投料时**指定 `lot_type`**（未指定则用产品默认）→ 校验 `requires_approval` 是否需审批 |
| **排程/派工** | `mds_priority_class`（level/weight/is_hot/max_ratio） | APS/MES 据此排序；Hot Lot 走加急；`max_ratio` 限制 Hot 占比 |
| **组批** | `allow_mix` / `is_splittable` / `is_mergeable` / min、max_qty | MES 组批时校验；与设备类级 `BATCH_*` 约束**双重校验** |
| **路由分支** | 类型代码作为 `lot.type` 变量 | 工程批按 `condition_expr` 走不同分支 |
| **报表口径** | 四个计数开关 | 良率/OEE/WIP/SPC 报表**按开关过滤**（不在 MES 里硬编码类型名） |
| **追溯** | 类型定义版本 | 事后可回答"当时该类型的口径是什么" |

> **对 MES 的关键价值**：把「什么批算什么」从**代码里的 if-else** 变成**可配置数据**——改口径只需改 MDS 数据并经审批，MES 不发版。

---

## 9. 待补 / 后续

- **产品默认批次类型**：在 [product-design](product-design.md) 增加可选字段（按产品默认投什么类型的批）。
- **Hot Lot 流程模板**：加急批的通知对象/审批链（可挂 [md-governance-design](md-governance-design.md) 的工作流）。
- **与 die bank / 回收片**：`RECLAIM`/`DUMMY` 类型的循环使用规则（与 [material-design §4.4](material-design.md) 的 dummy wafer 联动）。
- **与既有文档闭环**：本文引用 [product-design](product-design.md)、[route-design §4.3](route-design.md)、[constraint-design](constraint-design.md)、[sampling-spc-design](sampling-spc-design.md)、[expression-dsl-design §5](expression-dsl-design.md)、[naming-rule-design](naming-rule-design.md)。
