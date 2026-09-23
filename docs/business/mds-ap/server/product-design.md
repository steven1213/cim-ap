# MDS 产品主数据设计：Product / Product Family / Route Link

> 本文档承接 [位置主数据](location-design.md)、[设备建模](equipment-design.md)、[工艺路线](route-design.md)，
> 补全 MDS 第四块核心主数据——**产品（Product）/ 器件 / 料号**及其与工艺路线的闭环。
> 产品是晶圆厂制造活动的"对象"，它把"要造什么（product）→ 怎么造（route）→ 在哪造（area/equipment）→ 用什么配方（recipe）"串成完整链路。

---

## 0. 定位与边界（拍板）

沿用既有原则：**MDS 拥有「定义」，下游（MES）拥有「实例 / 实时态」**。

- **MDS 拥有 `product_family / product / product_route` 的定义主数据**：产品族、产品规格、技术节点、绑定路线、生命周期。
- **MES 拥有 `product` 的运行实例**：某 lot 属于哪个 product、当前 WIP、产出、良率实绩——不进 MDS（MES 侧 `lot` 引用 `product_code`）。
- `product → route` 是**资格级默认绑定**；实际投料时 MES 结合 route 版本状态（[route-design §4.2](route-design.md)）选定具体 `route_version`。

### 0.1 与其他主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 产品 ↔ 工艺路线（默认） | `mds_product.default_route_code → mds_route.route_code`（逻辑引用，服务层校验） | [route-design §3.1](route-design.md) |
| 产品 ↔ 工艺路线（备选） | `mds_product_route(product_code, route_code, is_default)` | 本文 §3.3 |
| 路线 ↔ 产品 | `mds_route.product_code → mds_product.code`（逻辑引用） | [route-design §3.1](route-design.md) |
| 产品 ↔ 产品族 | `mds_product.family_id → mds_product_family.id`（FK） | 本文 §3.2 |
| 产品 ↔ 技术节点 | `mds_product.tech_node`（属性）∩ `mds_equipment_tech_node`（链接表） | [设备设计 §3.x](equipment-design.md) |
| 产品 ↔ 载具尺寸 | `mds_product.wafer_size` 须 = 载具 `mds_carrier_type.wafer_size`（三方一致方可搬运） | [载具设计](carrier-design.md) §4.3 |
| 产品 → 设备能力（间接） | 经 route operation（area/equipment_class/recipe） | [route-design §4.5](route-design.md) |

---

## 1. 行业依据（fab MES 主数据实践）

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **fab MES 主数据实践** | 产品分层：Product Family > Product > Product Variant；一族共享技术节点 / 工艺平台，单产品可有多条 route（default + alternate） | `mds_product_family` + `mds_product` + `mds_product_route`（§3） |
| **半导体器件分类** | 产品类型：Logic / Memory / Analog / Mixed-Signal / Foundry-Customer；wafer size（200/300/450mm）、mask layer count 决定光刻/刻蚀负荷 | `product_type` / `wafer_size` / `mask_layer_count`（§3.2） |
| **技术节点** | 28/14/7/5nm 等是设备能力筛选键；产品 tech_node 与设备 tech_node 取交集决定可造设备 | `tech_node` 属性（§3.2、§4.4） |
| **SEMI E94 / E87** | route 绑定 product（E94）；lot 归属 product（E87）；product 是 route 与 lot 之间的枢纽 | 闭环（§0.1） |
| **产品生命周期** | DESIGN→QUALIFICATION→PILOT→MASS_PRODUCTION→EOL；规格有版本（spec rev） | `lifecycle_status` + `revision`（§4.2、§4.3） |
| **fab 工程实践** | 产品属性高度异构（封装/引脚/可靠性…）；用扩展属性承载而非塞满主表 | `ext_attrs` JSON（§4.5） |

---

## 2. 术语澄清（避免歧义）

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Product / Device / Part** | 被制造的芯片/器件，由 `product_code`/`part_number` 唯一标识 | 不是 lot（lot 是产品的物理批次实例化） |
| **Product Family** | 共享技术平台/工艺的一组产品（如同一基带芯片系列） | 族级属性（tech_node）被产品继承，避免逐产品重复 |
| **Lot** | product 的物理批次实例（wafer 集合），含 WIP/良率实绩 | 属 MES 运行域，不进 MDS |
| **Route** | 产品从投料到完工的工序图 | 一产品可绑定多条 route（默认 + 备选），见 §3.3 |
| **Tech Node** | 工艺节点（14nm…），设备能力筛选键 | 产品侧属性 + 设备侧链接表，交集决定可造设备 |

---

## 3. 实体总览与 ER

```
mds_product_family ──< mds_product (产品族>产品, 一对多)
                          │
                          ├──default_route_code──> mds_route.route_code  (闭环: 路线, 逻辑引用)
                          │
                          └──< mds_product_route (product_code, route_code, is_default)  (备选路线, 多对多)
                                     └──route_code──> mds_route.route_code  (逻辑引用)

mds_route.product_code ──> mds_product.code   (路线反向指产品, 逻辑引用)
```

| 实体 | 表名 | 是否主数据 | 说明 |
| --- | --- | --- | --- |
| 产品族 | `mds_product_family` | 是 | 技术平台分组，承载继承属性 |
| 产品 | `mds_product` | 是 | 器件/料号：类型/节点/尺寸/生命周期/默认路线 |
| 产品-路线 | `mds_product_route` | 是 | 产品与路线的多对多（默认+备选） |

> 所有实体继承 `BaseDefData`（见 §5），含 `id` / `tenant_id` / `@Version` / `deleted` / 审计列；历史表 `{entity}_hist` 由 `@History(SNAPSHOT)` 自动生成。

### 3.1 `mds_product_family`（产品族）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | 雪花 / UUIDv7 |
| `family_code` | VARCHAR(64) | NOT NULL | 族代码 |
| `name` | VARCHAR(128) | | 族名 |
| `tech_node` | VARCHAR(16) | | 继承级技术节点（产品可覆盖） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(family_code, tenant_id, deleted)`。

### 3.2 `mds_product`（产品 / 器件 / 料号）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `product_code` | VARCHAR(64) | NOT NULL | 产品代码 / 料号（如 `PRD-14NM-LOGIC-A1`） |
| `name` | VARCHAR(128) | | 产品名 |
| `family_id` | VARCHAR(32) | FK→`mds_product_family.id` | 所属产品族 |
| `product_type` | VARCHAR(16) | NOT NULL | `LOGIC`/`MEMORY`/`ANALOG`/`MIXED`/`FOUNDRY_CUSTOM` |
| `tech_node` | VARCHAR(16) | | 技术节点（缺省继承 family；可覆盖） |
| `wafer_size` | INT | | 晶圆尺寸 mm（200/300/450） |
| `mask_layer_count` | INT | | 光罩层数（光刻/刻蚀负荷估算）；**逐层明细见 [layer-design.md](layer-design.md)**，二者一致性由 `LayerValidator` 校验（layer-design §4.4） |
| `default_route_code` | VARCHAR(64) | | **默认工艺路线**（→ `mds_route.route_code`，逻辑引用） |
| `customer_id` | VARCHAR(64) | | 客户/项目（代工场景） |
| `lifecycle_status` | VARCHAR(16) | NOT NULL | 业务阶段：`DESIGN`/`QUALIFICATION`/`PILOT`/`MASS_PRODUCTION`/`EOL` |
| `status` | VARCHAR(16) | NOT NULL | 记录管理态：`DRAFT`/`IN_REVIEW`/`APPROVED`/`RELEASED`/`OBSOLETE`/`ARCHIVED`（与 route **完全同源**，见 §4.2） |
| `revision` | VARCHAR(16) | NOT NULL | 规格版本（spec rev，如 `A`/`B`/`1.0`） |
| `effective_from` | DATETIME(3) | | 生效起始（SCD2-lite） |
| `effective_to` | DATETIME(3) | | 生效结束（NULL=当前有效） |
| `ext_attrs` | JSON | | 扩展属性（封装/引脚/可靠性…，见 §4.5） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(product_code, revision, tenant_id, deleted)`。
- `lifecycle_status`（业务阶段）与 `status`（记录管理态）**正交**，同设备/路线分层思路（见 §4.2）。

### 3.3 `mds_product_route`（产品-路线多对多）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `product_code` | VARCHAR(64) | NOT NULL | → `mds_product.code`（逻辑引用） |
| `route_code` | VARCHAR(64) | NOT NULL | → `mds_route.route_code`（逻辑引用） |
| `is_default` | BIT | DEFAULT 0 | 是否默认路线（与 `mds_product.default_route_code` 保持一致） |
| `priority` | INT | DEFAULT 0 | 备选路线优先级 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(product_code, route_code, tenant_id, deleted)`。
- 支持一产品多条 route（如主路线 + 工程备选 + 客户定制）；`is_default` 至多一条为 1。
- `product_code` / `route_code` 为**业务代码逻辑引用**（route 侧 `mds_route.product_code` 同理），参照完整性与版本有效性由 `ProductValidator` 在服务层校验（见 §4.6）。

---

## 4. 关键设计点（拍板）

### 4.1 分层：Family > Product（继承而非冗余）

- 产品族承载**共享工艺平台属性**（tech_node、基础工艺约束），产品继承并可覆盖。避免同一系列几十个产品重复录节点/工艺。
- 与 route 的 `mds_process` 独立复用、equipment 的 `type→class` 串行三级思路一致：**层级化、可继承、可扩展**。

### 4.2 双维度状态（与设备/路线同源）

产品有两个**正交**状态维度（沿用一贯"分层"原则）：

| 维度 | 字段 | 拥有方 | 取值 | 用途 |
| --- | --- | --- | --- | --- |
| 业务阶段 | `lifecycle_status` | MDS | `DESIGN`→`QUALIFICATION`→`PILOT`→`MASS_PRODUCTION`→`EOL` | 产品"商业/工程阶段" |
| 记录管理态 | `status` | MDS | `DRAFT`→`IN_REVIEW`→`APPROVED`→`RELEASED`→`OBSOLETE`→`ARCHIVED` | 记录"是否定稿可用" |

- `lifecycle_status`：产品"商业/工程阶段"（在研→认证→试产→量产→停产）。
- `status`：记录"是否定稿可用"（仅 `RELEASED` 可被 lot 引用），同 [route-design §4.2](route-design.md) 语义。
- 两者独立：一个 `MASS_PRODUCTION` 的产品其记录仍可能是 `DRAFT`（规格未定稿）或 `OBSOLETE`（被新规格取代但仍在产）。
- `status` 状态机迁移（`submitForReview`/`approve`/`release`/`obsolete`/`archive`）由 `ProductService` 显式方法触发，与 route 对称。

### 4.3 版本（spec rev）与不可变

- `product_code + version` 唯一；`RELEASED` 后规格版本**不可变**——改规格须 `cloneAsNewVersion()` 出新 `DRAFT`。
- 已投 lot 锁定投料时的 `product_code + version`（MES 侧 `lot.product_version`），新规格不影响历史 lot。
- 与 [route-design §4.2.2](route-design.md) 版本策略完全对称，形成主数据**版本治理统一范式**（产品/路线均"RELEASED 即锁死、改须开新版本"）。
- 配合 `effective_from/effective_to` 实现规格级生效窗口（SCD2-lite）。

### 4.4 技术节点：产品 ↔ 设备能力筛选键

- `mds_product.tech_node` 与 `mds_equipment_tech_node`（[设备设计 §3.x](equipment-design.md)）取**交集** → 可制造该产品的设备池。
- 例：产品 `14nm` + route operation 要求 `LITHO` 类 → 候选设备 = `tech_node` 含 `14nm` 且 `equipment_class=LITHO` 的 equipment。
- 全链路闭环：product（节点/路线）→ route operation（area/设备类/recipe）→ equipment（含 tech_node/控制模式）→ 派工。

### 4.5 扩展属性（ext_attrs JSON）

- fab 产品属性高度异构（封装形式、引脚数、可靠性等级、客户项目号…），全部塞主表会膨胀且多数稀疏。
- 采用 `ext_attrs` JSON 列（MySQL 8）承载可变扩展属性；核心搜索字段（product_type/tech_node/wafer_size）留在主表。
- 备选：若需强类型检索/索引，可改 `mds_product_attribute` EAV 链接表（key/value/type），本文以 JSON 为默认、EAV 为可选扩展。

### 4.6 与位置 / 设备 / 路线三向闭环

`product` 通过 `default_route_code` 接入 route；route operation 又挂 area/equipment_class/recipe（[route-design §4.5](route-design.md)）。由此：

1. **product → route**：确定制造工序图。
2. **route operation → area/equipment_class/recipe**：确定"在哪、哪类设备、哪份配方"。
3. **product.tech_node ∩ equipment.tech_node**：确定"哪些具体设备合格"。

→ MES 投料时仅凭 `product_code` 即可经 MDS 主数据推导出完整制造上下文，无需在 MES 重复维护。

- **跨实体引用完整性**：`default_route_code` / `mds_product_route.route_code` / `mds_product_route.product_code` 为业务代码逻辑引用（route 侧 `mds_route.product_code` 同理）。因 route 与 product 各自以 `code+version` 为自然键、`code` 非单列唯一，故不建物理 FK；由 `ProductValidator` 在服务层校验：
  - 引用的 `route_code` 存在且为 `RELEASED`；
  - `is_default=1` 的路线须与 `mds_product.default_route_code` 一致（至多一条）；
  - `default_route_code` 指向的 route 其 `product_code` 回指本产品（双向一致，可选强校验）。

### 4.7 软删 / 租户 / 主键（与平台一致）

- 所有实体继承 `BaseDefData`：`String id`（雪花/UUIDv7）、`tenant_id`、`@Version`、`deleted`、审计列。
- 唯一约束均含 `(..., tenant_id, deleted)`（T2.5）。
- 行级过滤：`@SQLRestriction("deleted=false")` + `@Filter(cimTenantFilter)`；`@FilterDef` 放实体包 `package-info.java`。

---

## 5. 与平台底座衔接（强制对齐）

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData`（零 Hibernate 基类） | 所有 product 实体继承，含 `@Version` 乐观锁、审计五件套、`tenant_id`、`deleted` |
| `@History(SNAPSHOT)` | product/family/product_route 变更落 `{entity}_hist`，含 `op_type`/`operator`/`op_time`/`trx_id`/`change_set_json`——规格修订自动留痕 |
| `cimTenantFilter` + `TenantContext` | 多租户行级过滤，MDS 主数据天然隔离 |
| T2.5 软删唯一约束 | 唯一键含 `deleted`，避免软删后唯一冲突 |
| `IdGenerator`（雪花/UUIDv7） | 主键生成 |
| 主历成对迁移 | DDL 与 `db/migration/{common,vendor}` 成对：结构迁移 + 历史表 + 种子数据 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
-- 产品族
CREATE TABLE mds_product_family (
  id          VARCHAR(32)  NOT NULL,
  tenant_id   VARCHAR(32)  NOT NULL,
  family_code VARCHAR(64)  NOT NULL,
  name        VARCHAR(128),
  tech_node   VARCHAR(16),
  description VARCHAR(512),
  version_    BIGINT       NOT NULL DEFAULT 0,
  deleted     BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pf PRIMARY KEY (id),
  CONSTRAINT uq_mds_pf_code UNIQUE (family_code, tenant_id, deleted)
);

-- 产品
CREATE TABLE mds_product (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  product_code  VARCHAR(64)  NOT NULL,
  name          VARCHAR(128),
  family_id     VARCHAR(32),
  product_type  VARCHAR(16)  NOT NULL,
  tech_node     VARCHAR(16),
  wafer_size    INT,
  mask_layer_count INT,
  default_route_code VARCHAR(64),
  customer_id   VARCHAR(64),
  lifecycle_status VARCHAR(16) NOT NULL,
  status        VARCHAR(16)  NOT NULL,
  revision       VARCHAR(16)  NOT NULL,
  effective_from DATETIME(3),
  effective_to   DATETIME(3),
  ext_attrs     JSON,
  description   VARCHAR(512),
  version_      BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time   DATETIME(3), create_user VARCHAR(64),
  event_time    DATETIME(3), event_user  VARCHAR(64),
  event_name    VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_product PRIMARY KEY (id),
  CONSTRAINT uq_mds_product_code UNIQUE (product_code, revision, tenant_id, deleted),
  CONSTRAINT fk_prod_family FOREIGN KEY (family_id) REFERENCES mds_product_family (id)
);
CREATE INDEX idx_product_lc ON mds_product (lifecycle_status);
CREATE INDEX idx_product_tn ON mds_product (tech_node);

-- 产品-路线（多对多，业务代码逻辑引用）
CREATE TABLE mds_product_route (
  id           VARCHAR(32)  NOT NULL,
  tenant_id    VARCHAR(32)  NOT NULL,
  product_code VARCHAR(64)  NOT NULL,
  route_code   VARCHAR(64)  NOT NULL,
  is_default   BIT          DEFAULT 0,
  priority     INT          DEFAULT 0,
  description  VARCHAR(512),
  version_     BIGINT       NOT NULL DEFAULT 0,
  deleted      BIT          NOT NULL DEFAULT 0,
  create_time  DATETIME(3), create_user VARCHAR(64),
  event_time   DATETIME(3), event_user  VARCHAR(64),
  event_name   VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pr PRIMARY KEY (id),
  CONSTRAINT uq_mds_pr UNIQUE (product_code, route_code, tenant_id, deleted)
);
CREATE INDEX idx_pr_route ON mds_product_route (route_code);
```

> 历史表 `mds_product_family_hist` / `mds_product_hist` / `mds_product_route_hist` 由 `@History(SNAPSHOT)` 在运行时自动生成，结构为对应实体 + 历史元数据列（`hist_id` / `rev` / `rev_type` / `rev_at`）。
> `default_route_code` / `mds_product_route.route_code` / `product_code` 为逻辑引用，参照完整性由 `ProductValidator` 校验，不建物理 FK（因 `code` 非单列唯一）。

---

## 7. 代码结构（包路径）

```
com.cim.mds.product
  ├── entity
  │   ├── ProductFamily.java   # @Entity 继承 BaseDefData
  │   ├── Product.java         # @Entity 继承 BaseDefData；含 lifecycle_status/status/version
  │   ├── ProductRoute.java    # @Entity 继承 BaseDefData（product_code/route_code/is_default）
  │   └── package-info.java    # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── ProductFamilyRepository.java
  │   ├── ProductRepository.java
  │   └── ProductRouteRepository.java
  ├── service
  │   ├── ProductService.java     # 继承 AbstractJpaService；状态跃迁/cloneAsNewVersion/默认路线一致性校验
  │   └── ProductValidator.java  # 跨实体引用校验（route 须 RELEASED、is_default 至多一条、双向一致）
  └── web
      └── ProductController.java  # 查询/版本发布/克隆/路线绑定
```

---

## 8. 待补 / 后续

- **MES 运行实例衔接契约**：`lot` 对 `product_code`/`product_version`/`route_code`/`route_version` 的导出订阅格式（见 [route-design §8](route-design.md)）。
- **route 图校验服务**：入参校验（无环/分叉配对/可达性），与 product 路线绑定校验（默认路线须 RELEASED）联动。
- **API 设计**：product/route 查询、版本发布、克隆、跨主数据联动校验。
- **与既有文档闭环**：本文引用的 `mds_location` / `mds_equipment_*` / `mds_logic_recipe`（逻辑配方） / `mds_route` 分别见 [位置设计](location-design.md) / [设备设计](equipment-design.md) / [配方设计](recipe-design.md) / [工艺路线](route-design.md)。
