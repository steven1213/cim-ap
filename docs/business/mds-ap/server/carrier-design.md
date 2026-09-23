# MDS 载具主数据设计：FOUP / Carrier / Cassette

> 本文档承接 [位置主数据](location-design.md)、[设备建模](equipment-design.md)、[工艺路线](route-design.md)、[产品主数据](product-design.md)，
> 补全 MDS 第五块核心主数据——**载具（Carrier）/ FOUP / 晶圆盒**。
> 载具是晶圆在厂区内的"运输与暂存容器"，其 `wafer_size` 须与产品/设备一致、其 `carrier_type` 决定容量与可进入的工艺区域（污染控制），是排程与 AMHS（自动物料搬运）的枢纽。

---

## 0. 定位与边界（拍板）

沿用既有原则：**MDS 拥有「定义 / 资产登记」，下游（MES / AMHS / EAP）拥有「实时实例 / 实时态」**。

- **MDS 拥有载具的「资产主数据」**：载具类型（模型字典）、单个物理载具的登记、工艺兼容性、所有权、PM/清洗周期、行政态与洁净态。
- **MES / AMHS 拥有载具的「运行实例」**：当前物理位置（stocker / 设备 / bay）、当前承载的 lot/晶圆、实时传输态与访问态（SEMI E87 的 NOTINIT/READY/NOTREADY、TRANSFERBLOCKED…）——不进 MDS。
- 与设备/路线一致：**实时态（位置/承载/传输）归下游，定义与资产态归 MDS**；MDS 仅缓存/记录可查询的资产级状态（行政态、洁净态、到期日），不维护秒级位置。

### 0.1 与其他主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 载具类型 ↔ 工艺兼容 | `mds_carrier_type_compat(equipment_class_id / area_code / process_id)` | 本文 §3.3 |
| 载具 ↔ 载具类型 | `mds_carrier.type_id → mds_carrier_type.id`（FK） | 本文 §3.2 |
| 载具 ↔ 所有权 | `mds_carrier_owner(carrier_id, owner_type, role)` | 本文 §3.4 |
| 载具 ↔ 产品/设备尺寸 | `mds_carrier_type.wafer_size` 须 ∈ {`product.wafer_size`, `equipment` 对应尺寸} | [产品 §3.2](product-design.md) / [设备 §3.x](equipment-design.md) |
| 载具 ↔ 归属库位（主数据侧） | `home_stocker_code → mds_bank(code)`（归属库位，逻辑引用；经 `mds_bank.location_id` 落到位置） | [仓库设计 §3.1](bank-design.md) |
| 实时位置/承载/传输态 | MES/AMHS 运行域（MDS 不存） | 本文 §0 / §4.2 |

---

## 1. 行业依据（SEMI + fab AMHS / 载具 MDM 实践）

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E15** | 300mm 载具（FOUP）机械/接口规范；标准 25 片容量 | `mds_carrier_type` 容量/尺寸基准（§3.1） |
| **SEMI E28.1** | 300mm 晶圆传输用 Cassette/Pod 规范 | carrier_type 区分 FOUP / SMIF_POD / CASSETTE |
| **SEMI E87** | Carrier Management：carrier 对象含 ID / Status(NOTINIT/READY/NOTREADY) / Location / MaterialState；传输与访问态由 AMHS 维护 | 实时态归 MES/AMHS（§0、§4.2） |
| **SEMI E39** | Object Services：carrier 作为通用对象，与 lot/equipment 同构建模 | type-vs-instance 分层（§3） |
| **fab AMHS 实践** | 载具污染隔离：不同工艺区（Litho vs Implant vs Diffusion）用不同载具或经清洗后才能跨区；载具有 PM（机械维护）与清洗周期 | `mds_carrier_type_compat` + `clean_interval_days`/`pm_interval_days`（§3.3、§4.4） |
| **fab 资产 MDM** | 载具为可计量资产：序列号/条码、供应商、所有权（自有/寄售）、丢失/报废流程 | `mds_carrier` + `mds_carrier_owner`（§3.2、§3.4） |

---

## 2. 术语澄清（FOUP vs Carrier vs Cassette）

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Carrier（载具 / 晶圆盒）** | 统称所有晶圆运输/暂存容器 | 是统称，FOUP/Cassette 都是它的子类 |
| **FOUP** | Front-Opening Unified Pod，300mm 标准载具，前开式，标准 25 片 | 是 **300mm carrier 的子类型**，非独立概念 |
| **Cassette** | 200mm 开放式晶圆盒（或泛指盒） | 200mm 用，容量/接口与 FOUP 不同 |
| **SMIF Pod** | 200mm 标准化机械接口 pod（前开式微环境） | 介于 cassette 与 FOUP 之间，按 wafer_size 区分 |
| **RSP / Reticle Pod** | 光罩盒 | 装 reticle 非 wafer，**不在本设计范围**（属光罩主数据） |
| **wafer_size** | 200 / 300 / 450mm | 载具与产品、设备必须尺寸一致，是核心筛选键 |

> 设计上用 `carrier_type ∈ {FOUP, SMIF_POD, CASSETTE}` + `wafer_size` 表达上述区分，不另建平行实体。
> **枚举扩展（权威清单，2026-09-23 全量补齐）**：`carrier_type` 除晶圆容器外，另含以下**非晶圆容器**类型，同一载具模型同时覆盖晶圆与资产容器，避免为每个资产域另建一套容器主数据：
>
> | carrier_type | 用途 | 登记来源 |
> | --- | --- | --- |
> | `RETICLE_POD` | 光罩盒（单次仅装 1 片光罩） | [reticle-design §0.2](reticle-design.md) |
> | `PROBE_CARD_BOX` | 探针卡存放盒 | [test-asset-design §3.3](test-asset-design.md) |
>
> 上述容器同样继承本域的三态分离（行政态 / 洁净态 / E87 实时态）与 `mds_carrier_type_compat` 兼容机制；其**存储位置**对应 [bank-design](bank-design.md) 的 `RETICLE_STOCKER` / `PROBE_CARD_STOCKER`。

---

## 3. 实体总览与 ER

```
mds_carrier_type ──< mds_carrier (类型>实例, 一对多)
                        │
                        ├──< mds_carrier_owner (多 owner 多角色)
                        │
                        └──< mds_carrier_type_compat (该类型可服务的 设备类/Area/工艺)
                                    ├──equipment_class_id──> mds_equipment_class
                                    ├──area_code────────> mds_location(code,level=AREA)
                                    └──process_id────────> mds_process

mds_carrier.home_stocker_code ──> mds_bank(code)   (归属库位, 逻辑引用; 经 mds_bank.location_id → mds_location)
```

| 实体 | 表名 | 是否主数据 | 说明 |
| --- | --- | --- | --- |
| 载具类型（模型字典） | `mds_carrier_type` | 是 | FOUP/Cassette 类型：容量/尺寸/厂商/兼容/周期 |
| 载具（实例登记） | `mds_carrier` | 是 | 单个物理载具：条码/行政态/洁净态/到期日 |
| 载具类型-工艺兼容 | `mds_carrier_type_compat` | 是 | 该类型可进入的工艺区/设备类（污染控制） |
| 载具所有权 | `mds_carrier_owner` | 是 | 多 owner 多角色（自有/寄售） |

> 所有实体继承 `BaseDefData`（见 §5），含 `id` / `tenant_id` / `@Version` / `deleted` / 审计列；历史表 `{entity}_hist` 由 `@History(SNAPSHOT)` 自动生成。

### 3.1 `mds_carrier_type`（载具类型 / 模型字典）

> 与 equipment 的 `mds_equipment_model` 同理：**可复用的"类型定义"**，单个物理载具引用之，避免逐盒重复录容量/厂商。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `type_code` | VARCHAR(64) | NOT NULL | 类型代码（如 `FOUP-300-25`） |
| `name` | VARCHAR(128) | | 类型名 |
| `carrier_type` | VARCHAR(16) | NOT NULL | `FOUP`/`SMIF_POD`/`CASSETTE` |
| `wafer_size` | INT | NOT NULL | 晶圆尺寸 mm（200/300/450） |
| `capacity` | INT | NOT NULL | 容量（片，FOUP=25） |
| `vendor` | VARCHAR(128) | | 供应商 |
| `clean_interval_days` | INT | | 清洗周期（天），到期须清洗 |
| `pm_interval_days` | INT | | PM 周期（天），到期须维护 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(type_code, tenant_id, deleted)`。

### 3.2 `mds_carrier`（单个物理载具登记）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `carrier_code` | VARCHAR(64) | NOT NULL | 载具条码/序列号（如 `FOUP000123`） |
| `type_id` | VARCHAR(32) | NOT NULL, FK→`mds_carrier_type.id` | 载具类型 |
| `admin_status` | VARCHAR(16) | NOT NULL | 行政态：`AVAILABLE`/`IN_USE`/`QUARANTINE`/`MAINTENANCE`/`SCRAPPED`/`LOST` |
| `clean_status` | VARCHAR(16) | NOT NULL | 洁净态：`CLEAN`/`DIRTY`/`NEEDS_CLEAN`（资产级，MDS 记录；实时由 AMHS 回写） |
| `owner_id` | VARCHAR(64) | | 主 owner（快速标志；详情见 `mds_carrier_owner`） |
| `home_stocker_code` | VARCHAR(64) | | 归属库位（→ `mds_bank.code`，逻辑引用；Bank 经 `location_id` 落到位置，见 [仓库设计 §3.1](bank-design.md)） |
| `clean_due_at` | DATETIME(3) | | 下次清洗到期 |
| `pm_due_at` | DATETIME(3) | | 下次 PM 到期 |
| `last_clean_at` | DATETIME(3) | | 上次清洗时间 |
| `last_pm_at` | DATETIME(3) | | 上次 PM 时间 |
| `commissioned_at` | DATETIME(3) | | 投用日期 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(carrier_code, tenant_id, deleted)`。
- `admin_status` 与 `clean_status` **正交**（见 §4.2）。

### 3.3 `mds_carrier_type_compat`（载具类型 ↔ 工艺兼容，污染控制）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `type_id` | VARCHAR(32) | NOT NULL, FK→`mds_carrier_type.id` | 载具类型 |
| `equipment_class_id` | VARCHAR(32) | FK→`mds_equipment_class.id` | 可服务的设备大类（可选） |
| `area_code` | VARCHAR(64) | FK→`mds_location.code (level=AREA)` | 可进入的工艺区（可选，污染隔离） |
| `process_id` | VARCHAR(32) | FK→`mds_process.id` | 可服务的工艺（可选） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(type_id, equipment_class_id, area_code, process_id, tenant_id, deleted)`（三键至多一个非空）。
- 表达"此类型载具能进哪些工艺区/设备"——排程与 AMHS 派载具时据此外推可承载该 lot 的载具池。

### 3.4 `mds_carrier_owner`（载具所有权，多 owner 多角色）

> 与 [设备设计 §3.9](equipment-design.md) 的 `mds_equipment_owner` 同源：载具常由供应商寄售、厂内部门运营、外包清洗保养。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `carrier_id` | VARCHAR(32) | NOT NULL, FK→`mds_carrier.id` | 载具 |
| `owner_type` | VARCHAR(16) | NOT NULL | `VENDOR`/`BUSINESS_UNIT`/`DEPARTMENT`/`COST_CENTER`/`PROJECT` |
| `role` | VARCHAR(16) | NOT NULL | `ASSET_OWNER`/`OPERATIONAL_OWNER`/`MAINT_OWNER`/`CLEAN_OWNER` |
| `owner_ref` | VARCHAR(64) | | 主体引用：→ `mds_vendor.code` 或 `mds_org_unit.code`（见 [org-personnel-design §3.1/§3.2](org-personnel-design.md)） |
| `effective_from` | DATETIME(3) | | SCD2-lite 起始 |
| `effective_to` | DATETIME(3) | | SCD2-lite 结束 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(carrier_id, owner_type, role, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板）

### 4.1 类型字典 vs 实例登记（与设备同构）

- `mds_carrier_type` 承载**可复用属性**（容量/尺寸/厂商/兼容/周期），`mds_carrier` 是**单个物理载具**的登记行，引用类型。
- 与 `equipment_model`↔`equipment`、`product_family`↔`product` 思路一致：**字典-实例分层，避免逐对象冗余**。

### 4.2 三态分离（行政态 / 洁净态 / 实时传输态）

| 状态 | 拥有方 | 存储 | 取值 |
| --- | --- | --- | --- |
| ① 行政态 `admin_status` | **MDS** | `mds_carrier` | `AVAILABLE`/`IN_USE`/`QUARANTINE`/`MAINTENANCE`/`SCRAPPED`/`LOST` |
| ② 洁净态 `clean_status` | **MDS（资产级，AMHS 回写）** | `mds_carrier` | `CLEAN`/`DIRTY`/`NEEDS_CLEAN` |
| ③ 实时传输/访问态 | **MES/AMHS** | MDS 不存 | SEMI E87 `NOTINIT`/`READY`/`NOTREADY`/`TRANSFERBLOCKED`… |

- ① 与 ② **正交**：一个 `AVAILABLE` 载具可能 `DIRTY`（可用但待清洗）；一个 `MAINTENANCE` 载具也可能是 `CLEAN`。
- ③ 完全归下游：MDS 只定义/记录资产级①②，实时位置与承载由 AMHS 维护，避免重复维护秒级态（与设备 E10/控制模式一致）。
- **E87 状态机定义可复用设备状态机框架**：MDS 已建 `mds_equipment_state_model`（`model_kind` 区分），可加 `model_kind=CARRIER_STATE` 承载 E87 状态目录与合法迁移，供 AMHS 校验——与设备 §3.11 同源复用（可选扩展，本设计不新增表）。

### 4.3 wafer_size 三向一致性（核心筛选键）

- `mds_carrier_type.wafer_size` 须同时匹配 **产品 `wafer_size`**（[产品 §3.2](product-design.md)）与 **设备对应尺寸**（[设备 §3.x](equipment-design.md)）。
- 排程时：lot 的产品尺寸 → 候选载具（同尺寸）→ 候选设备（同尺寸），三者尺寸一致方可组批搬运。

### 4.4 工艺兼容性与污染控制（闭环关键）

- `mds_carrier_type_compat` 表达"该类型载具能进哪些 area/设备类/工艺"——这是 fab **污染隔离**的核心：
  - 例如 Implant 区载具不可直接进入 Litho 区，须经清洗；某些工艺要求专用载具。
- 与 route operation 的 `area_code` / `equipment_class_id` / `process_id`（[route-design §3.3](route-design.md)）对齐：lot 走某 step 时，所用载具类型必须在 step 对应 area/class/process 的兼容集合内。
- 由此形成闭环：product → route operation(area/class/process) → carrier_type_compat → carrier_type → carrier。

### 4.5 PM / 清洗周期与到期驱动

- `clean_interval_days` / `pm_interval_days` 在类型上定义；实例按 `commissioned_at`/`last_clean_at`/`last_pm_at` 推算 `clean_due_at`/`pm_due_at`。
- 到期可联动（可选，类似设备 PM）：`clean_due_at` 到期 → 置 `clean_status=NEEDS_CLEAN` 并禁止投料；`pm_due_at` 到期 → 置 `admin_status=MAINTENANCE`。

### 4.6 与位置 / 设备 / 路线 / 产品四向闭环

1. **载具 ↔ 归属库位**：`home_stocker_code`（MDS 侧归属库位）指向 `mds_bank`（见 [仓库设计 §3.1](bank-design.md)）；Bank 经 `location_id` 落到 `mds_location`；载具实时物理位置（在哪个 bank/槽位）归 AMHS。
2. **载具 ↔ 设备**：`wafer_size` 一致 + `carrier_type_compat.equipment_class_id` 决定可服务设备。
3. **载具 ↔ 路线**：route operation 的 area/class/process 经 compat 反推可用载具类型。
4. **载具 ↔ 产品**：`wafer_size` 一致（§4.3）。
- 排程/搬运时，MES 仅凭 lot 的 product + route 即可经 MDS 主数据推出"该用哪种载具、哪些设备可接"，无需在 MES 重复维护。

### 4.7 软删 / 租户 / 主键（与平台一致）

- 所有实体继承 `BaseDefData`：`String id`、`tenant_id`、`@Version`、`deleted`、审计列。
- 唯一约束均含 `(..., tenant_id, deleted)`（T2.5）。
- 行级过滤：`@SQLRestriction("deleted=false")` + `@Filter(cimTenantFilter)`；`@FilterDef` 放实体包 `package-info.java`。
- `home_stocker_code` / `area_code` 为**业务代码逻辑引用**（同产品↔路线），参照完整性由 `CarrierValidator` 服务层校验。

---

## 5. 与平台底座衔接（强制对齐）

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 所有 carrier 实体继承，含 `@Version`、审计五件套、`tenant_id`、`deleted` |
| `@History(SNAPSHOT)` | carrier/type/compat/owner 变更落 `{entity}_hist`，含 `op_type`/`operator`/`op_time`/`trx_id`/`change_set_json`——载具登记/状态变更自动留痕 |
| `cimTenantFilter` + `TenantContext` | 多租户行级过滤 |
| T2.5 软删唯一约束 | 唯一键含 `deleted` |
| `IdGenerator` | 主键生成 |
| 主历成对迁移 | DDL 与 `db/migration/{common,vendor}` 成对 |
| 复用设备状态机框架 | 可选：`model_kind=CARRIER_STATE` 承载 E87 状态目录（见 §4.2） |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
-- 载具类型（模型字典）
CREATE TABLE mds_carrier_type (
  id                 VARCHAR(32)  NOT NULL,
  tenant_id          VARCHAR(32)  NOT NULL,
  type_code          VARCHAR(64)  NOT NULL,
  name               VARCHAR(128),
  carrier_type       VARCHAR(16)  NOT NULL,
  wafer_size         INT          NOT NULL,
  capacity           INT          NOT NULL,
  vendor             VARCHAR(128),
  clean_interval_days INT,
  pm_interval_days    INT,
  description        VARCHAR(512),
  version_           BIGINT       NOT NULL DEFAULT 0,
  deleted            BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_ct PRIMARY KEY (id),
  CONSTRAINT uq_mds_ct_code UNIQUE (type_code, tenant_id, deleted)
);

-- 单个物理载具
CREATE TABLE mds_carrier (
  id                VARCHAR(32)  NOT NULL,
  tenant_id         VARCHAR(32)  NOT NULL,
  carrier_code      VARCHAR(64)  NOT NULL,
  type_id           VARCHAR(32)  NOT NULL,
  admin_status      VARCHAR(16)  NOT NULL,
  clean_status      VARCHAR(16)  NOT NULL,
  owner_id          VARCHAR(64),
  home_stocker_code VARCHAR(64),
  clean_due_at      DATETIME(3),
  pm_due_at         DATETIME(3),
  last_clean_at     DATETIME(3),
  last_pm_at        DATETIME(3),
  commissioned_at   DATETIME(3),
  description       VARCHAR(512),
  version_          BIGINT       NOT NULL DEFAULT 0,
  deleted           BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_carrier PRIMARY KEY (id),
  CONSTRAINT uq_mds_carrier_code UNIQUE (carrier_code, tenant_id, deleted),
  CONSTRAINT fk_carrier_type FOREIGN KEY (type_id) REFERENCES mds_carrier_type (id)
);
CREATE INDEX idx_carrier_admin ON mds_carrier (admin_status);
CREATE INDEX idx_carrier_clean ON mds_carrier (clean_status);

-- 载具类型-工艺兼容（污染控制）
CREATE TABLE mds_carrier_type_compat (
  id                 VARCHAR(32)  NOT NULL,
  tenant_id          VARCHAR(32)  NOT NULL,
  type_id            VARCHAR(32)  NOT NULL,
  equipment_class_id VARCHAR(32),
  area_code          VARCHAR(64),
  process_id         VARCHAR(32),
  description        VARCHAR(512),
  version_           BIGINT       NOT NULL DEFAULT 0,
  deleted            BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_ctc PRIMARY KEY (id),
  CONSTRAINT uq_mds_ctc UNIQUE (type_id, equipment_class_id, area_code, process_id, tenant_id, deleted),
  CONSTRAINT fk_ctc_type FOREIGN KEY (type_id) REFERENCES mds_carrier_type (id),
  CONSTRAINT fk_ctc_class FOREIGN KEY (equipment_class_id) REFERENCES mds_equipment_class (id),
  CONSTRAINT fk_ctc_proc FOREIGN KEY (process_id) REFERENCES mds_process (id)
  -- area_code: 跨域逻辑引用（业务代码，服务层校验），不建 DB 级 FK —— 见 00-blueprint §11
);

-- 载具所有权
CREATE TABLE mds_carrier_owner (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  carrier_id    VARCHAR(32)  NOT NULL,
  owner_type    VARCHAR(16)  NOT NULL,
  role          VARCHAR(16)  NOT NULL,
  owner_ref     VARCHAR(64),
  effective_from DATETIME(3),
  effective_to   DATETIME(3),
  description   VARCHAR(512),
  version_      BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_co PRIMARY KEY (id),
  CONSTRAINT uq_mds_co UNIQUE (carrier_id, owner_type, role, tenant_id, deleted),
  CONSTRAINT fk_co_carrier FOREIGN KEY (carrier_id) REFERENCES mds_carrier (id)
);
```

> 历史表 `mds_carrier_type_hist` / `mds_carrier_hist` / `mds_carrier_type_compat_hist` / `mds_carrier_owner_hist` 由 `@History(SNAPSHOT)` 在运行时自动生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.carrier
  ├── entity
  │   ├── CarrierType.java      # @Entity 继承 BaseDefData（容量/尺寸/周期）
  │   ├── Carrier.java          # @Entity 继承 BaseDefData（admin_status/clean_status/到期）
  │   ├── CarrierTypeCompat.java # @Entity 继承 BaseDefData（equipment_class/area/process）
  │   ├── CarrierOwner.java      # @Entity 继承 BaseDefData（多 owner 多角色）
  │   └── package-info.java      # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── CarrierTypeRepository.java
  │   ├── CarrierRepository.java
  │   ├── CarrierTypeCompatRepository.java
  │   └── CarrierOwnerRepository.java
  ├── service
  │   ├── CarrierService.java      # 继承 AbstractJpaService；admin_status 跃迁/到期联动
  │   └── CarrierValidator.java   # 跨实体校验（compat 指向须存在；wafer_size 一致；home_stocker 校验）
  └── web
      └── CarrierController.java   # 查询/登记/状态变更/兼容维护
```

---

## 8. 待补 / 后续

- **MES / AMHS 运行实例衔接契约**：`carrier` 实时位置/承载 lot/传输态（E87）的导出订阅格式；`carrier_hist` 与实时态的边界。
- **载具-载具关系**：同批绑定（multiple carriers per lot / lot split-merge）由 MES 管理，MDS 仅登记单体。
- **API 设计**：载具登记/查询、按 area/尺寸/洁净态列可用载具、compat 维护、E87 状态机定义查询（复用设备状态机）。

---

## 附录 A. 载具关系与 Dummy Wafer（第二轮补强）

> **补的缺口**：原设计只把载具当成**独立个体**，未表达「**载具之间**的关系」（外箱/内盒、母托盘/子载具），也未表达「**dummy wafer 用载具**」的复用规则。生产中这两类都真实存在。

### A.1 `mds_carrier_relation`（载具-载具关系）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `parent_carrier_id` | VARCHAR(32) | NOT NULL, FK→`mds_carrier.id` | 父载具（外箱/母托盘） |
| `child_carrier_id` | VARCHAR(32) | NOT NULL, FK→`mds_carrier.id` | 子载具（内盒/子载具） |
| `relation_type` | VARCHAR(16) | NOT NULL | `CONTAINS`（装于）/`ADAPTER`（适配转换）/`PARENT_CHILD`（母/子） |
| `qty` | INT | | 容纳数量 |
| `is_active` | BIT | DEFAULT 1 | 是否有效 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(parent_carrier_id, child_carrier_id, tenant_id, deleted)`。
- **环检测**：父子关系不得成环（`CarrierValidator`）。
- **实时对应**（"此刻哪个子载具在哪个父载具里"）**归 AMHS**；本表只定义**结构性关系**。

### A.2 `mds_carrier_dummy_policy`（Dummy Wafer 使用规则）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `carrier_type_id` | VARCHAR(32) | NOT NULL, FK→`mds_carrier_type.id` | 载具类型 |
| `dummy_material_code` | VARCHAR(64) | FK→`mds_material.code` | 专用 dummy 物料（`material_type=DUMMY`，见 [material-design §3.2](material-design.md)） |
| `is_dedicated` | BIT | DEFAULT 0 | 该载具类型是否**专用于 dummy**（不混装产品片） |
| `max_dummy_count` | INT | | 单载具最大 dummy 片数 |
| `max_reuse_cycles` | INT | | dummy 循环使用上限（配合 [material-design §4.4](material-design.md) 的 `USE_CYCLE` 寿命策略） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(carrier_type_id, dummy_material_code, tenant_id, deleted)`。

### A.3 与 MES 生产执行衔接

| 场景 | 说明 |
| --- | --- |
| **装载校验** | MES 装载时校验"子载具是否可装入该父载具/适配器"；违规 BLOCK |
| **搬运决策** | AMHS 搬运外箱时，MDS 提供"内含哪些子载具类型、数量"的结构信息（实时内容物归 AMHS） |
| **Dummy 管理** | 热机/挡片必须用**专用载具**（`is_dedicated=1`）时，MES 校验载具类型；dummy 循环次数超 `max_reuse_cycles` 必须更换（与物料寿命策略联动） |
| **防污染** | 专用 dummy 载具不得混装产品片（可写成 `CUSTOM_EXPR` 约束） |
- **与既有文档闭环**：本文引用的 `mds_location` / `mds_equipment_*` / `mds_process` / `mds_route` / `mds_product` / `mds_bank` 分别见 [位置设计](location-design.md) / [设备设计](equipment-design.md) / [工艺路线](route-design.md) / [产品主数据](product-design.md) / [仓库设计](bank-design.md)。
