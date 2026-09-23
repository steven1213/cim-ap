# MDS 配方主数据详细设计（Recipe / RecipeGroup / LogicRecipe / PPID）

> 本文是 MDS 第六块核心主数据——**配方（Recipe）及其分组、逻辑/物理分层、PPID**。
> 它与[位置设计](location-design.md)、[设备设计](equipment-design.md)、[工艺路线设计](route-design.md)、[产品主数据](product-design.md) 五向闭环，是「工艺路线 → 设备 → 配方」链路的最终落点。
>
> 设计原则延续前几篇：**MDS 拥有「定义 + 资格 + 映射」，实时态（当前 PPID 是否在跑、腔体活跃配方）归 MES/EAP**。

---

## 0. 边界与闭环映射

### 0.1 MDS 拥有 vs 下游拥有（边界重申）

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 逻辑配方定义 `mds_logic_recipe` | **MDS** | 工具无关的工艺配方（逻辑参数），route 工序直接引用 |
| 设备(物理)配方定义 `mds_recipe` | **MDS** | 某类/某型号设备上可执行的配方（工具相关参数），正文(body)归外围 Recipe 系统 |
| 配方分组 `mds_recipe_group` | **MDS** | 管理用层级分组 |
| 设备↔物理配方资格 + PPID `mds_equipment_recipe_qual` | **MDS** | 设备能在哪台工具上跑哪份物理配方、其 PPID 是什么 |
| **LogicRecipe → PhysicalRecipe 映射** | **MDS** | 经 `mds_recipe.logic_recipe_id` 表达（一份逻辑配方 → 每类设备一份物理配方） |
| **派工时的实时解析（选设备→选物理配方→取 PPID→下发 EAP）** | **MES/EAP** | MDS 只提供主数据映射，引擎执行 |
| **腔体当前活跃配方 / 设备当前 PPID 实例** | **MES/EAP** | 实时态，MDS 不存 |

### 0.2 闭环映射（ER 要点）

```
mds_recipe_group (层级分组)
   ├──parent_group_id──> mds_recipe_group           (自引用树)
   ├──group_type=LOGIC──> mds_logic_recipe.group_id  (逻辑配方归组)
   └──group_type=PHYSICAL─> mds_recipe.group_id      (物理配方归组)

mds_logic_recipe (逻辑配方, 工具无关)
   ├──logic_recipe_id?(反向)── mds_recipe.logic_recipe_id  (1:N, 每设备类一份物理配方)
   ├──process_id──> mds_process                       (实现的工艺规范, route-design §3.2)
   ├──tech_node / product_family_code──> 产品/设备能力筛选键
   └──(被引用)── mds_route_operation.recipe_id         (route 工序绑定逻辑配方)

mds_recipe (物理/设备配方, 工具相关)
   ├──class_id──> mds_equipment_class                 (适用设备大类)
   ├──model_id──> mds_equipment_model                 (可选: 具体型号)
   ├──logic_recipe_id──> mds_logic_recipe             (实现的逻辑配方)
   └──(被引用)── mds_equipment_recipe_qual.recipe_id

mds_equipment_recipe_qual (设备↔物理配方资格, 含 PPID)
   ├──equipment_id──> mds_equipment
   ├──recipe_id──> mds_recipe
   └──ppid── 该物理配方在此设备上的 Process Program ID (SEMI E40)
```

**闭环链**：`route operation → mds_logic_recipe → (按设备类) mds_recipe → qual(equipment,PPID)`。
即在 MDS 设计期，route 工序只需绑定「逻辑配方（做什么）」；派工时 MES 据所选设备类解析出「物理配方 + PPID」，下发给 EAP 在该设备上加载执行。

---

## 1. 行业依据（为什么这样建模）

| 来源 | 关键概念 | 本文落地 |
| --- | --- | --- |
| **SEMI E40**（Generic Recipe & Equipment Control / Process Program Management） | 区分 **Logical Recipe（逻辑配方，工具无关参数）**、**Equipment Recipe（设备配方，工具相关参数）**、**Process Program（PP，设备内可执行程序）**；PP 以 **PPID** 标识 | 三层对应 `mds_logic_recipe` / `mds_recipe` / `mds_equipment_recipe_qual.ppid` |
| **SEMI E94**（Data Collection Management） | recipe 作为数据采集上下文；recipe 与 operation 绑定 | route operation 引用逻辑配方；recipe 作为 DCP 上下文键 |
| **fab Recipe 管理实践** | 逻辑配方与设备解耦，一份逻辑配方映射到多台不同类设备的物理配方；PPID 是设备侧程序名 | `logic_recipe_id` 1:N 映射；PPID 落在资格链接 |
| **前几篇统一范式** | 版本不可变 + 生命周期 + 冻结（route/product）；资格集合（equipment §3.8）；code 全局唯一（location D5/T2.5） | 逻辑/物理配方复用同一套生命周期与版本治理；资格表复用 |

---

## 2. 术语澄清（避免混淆）

| 术语 | 含义 | 易混点 |
| --- | --- | --- |
| **RecipeGroup / `mds_recipe_group`** | 配方**管理分组**（按工艺/产品族/设备类/模块组织），层级树 | 不是工艺路线，也不是配方本身 |
| **LogicRecipe / `mds_logic_recipe`** | **逻辑配方**：用工具无关的逻辑参数描述「要做什么工艺」；被 route 工序引用 | 与设备无关，一份逻辑配方可映射多台设备的物理配方 |
| **Physical/Equipment Recipe / `mds_recipe`** | **设备(物理)配方**：某类/型号设备上可执行的配方（工具相关参数），加载到设备后获得 PPID | 这是资格表引用的对象 |
| **PPID** | **Process Program ID**：配方加载到具体设备后在该设备上的**程序标识**（SEMI E40）。同一物理配方在不同设备上的 PPID 通常不同 | PPID 是**每设备每配方**维度的，故落在 `mds_equipment_recipe_qual` |
| **recipe body / 正文** | 真实参数集合（SEMI E40 程序体） | **归外围 Recipe 系统**，MDS 只存 `body_ref` 引用 |
| route operation 的 `recipe_id` | 此处指**逻辑配方**（设计期「跑哪份配方」） | 与 `mds_recipe`（物理）是两个层级 |

---

## 3. 实体与表结构

### 3.1 `mds_recipe_group`（配方分组，层级树）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` / `tenant_id` | — | PK | 基类 |
| `code` | VARCHAR(64) | 唯一 `(code,tenant_id,deleted)` | 分组编码 |
| `name` | VARCHAR(128) | | 分组名 |
| `group_type` | VARCHAR(16) | NOT NULL | `LOGIC` / `PHYSICAL`（分组的是逻辑还是物理配方） |
| `parent_group_id` | VARCHAR(32) | FK→`mds_recipe_group.id` | 父组（自引用，顶层 NULL） |
| `path` | VARCHAR(512) | | 物化路径（如 `/LITHO/ETCH`），加速子树查询 |
| `status` | VARCHAR(16) | NOT NULL | `ACTIVE`/`INACTIVE`（分组仅组织用，生命周期较轻） |
| `sort_no` | INT | | 展示排序 |
| `description` | VARCHAR(512) | | 基类 |

- `code` 同租户全局唯一（遵循 location D5 / T2.5 软删唯一）。
- `group_type` 与子表 `group_id` 保持语义一致（服务层校验：逻辑配方只能挂 `LOGIC` 组）；DB 不强约束。

### 3.2 `mds_logic_recipe`（逻辑配方，工具无关）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` / `tenant_id` | — | PK | 基类 |
| `code` | VARCHAR(64) | 唯一 `(code,version,tenant_id,deleted)` | 逻辑配方编码 |
| `name` | VARCHAR(128) | | 名称 |
| `revision` | VARCHAR(16) | NOT NULL | 版本（如 `1.0`） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`IN_REVIEW`/`APPROVED`/`RELEASED`/`OBSOLETE`/`ARCHIVED` |
| `is_frozen` | BIT | NOT NULL DEFAULT 0 | 冻结标志（与 status 正交，见 §4.2） |
| `frozen_by` / `frozen_at` / `frozen_reason` / `unfreeze_at` | — | | 冻结留痕 |
| `effective_from` / `effective_to` | DATETIME(3) | | 生效窗口（SCD2-lite） |
| `group_id` | VARCHAR(32) | FK→`mds_recipe_group.id`（应为 `LOGIC`） | 所属逻辑配方组 |
| `process_id` | VARCHAR(32) | FK→`mds_process.id` | 实现的工艺规范（route-design §3.2） |
| `recipe_class` | VARCHAR(64) | | 配方类别（与设备 `capability.recipe_class` 对齐） |
| `tech_node` | VARCHAR(32) | | 适用技术节点（与产品/设备能力筛选键闭环） |
| `product_family_code` | VARCHAR(64) | | 适用产品族（与产品主数据闭环） |
| `body_ref` | VARCHAR(512) | | 逻辑配方正文引用（外围 Recipe 系统，SEMI E40） |
| `ext_attrs` | JSON | | 异构扩展属性 |
| `description` | VARCHAR(512) | | 基类 |

- **`(code, version)` 唯一且 RELEASED 后不可变**：改逻辑配方须 `cloneAsNewVersion()` 出新版本（与 route/product 对称）。
- route 工序引用的即此表（见 §0.2）。

### 3.3 `mds_recipe`（物理/设备配方，工具相关）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` / `tenant_id` | — | PK | 基类 |
| `code` | VARCHAR(64) | 唯一 `(code,version,tenant_id,deleted)` | 物理配方编码 |
| `name` | VARCHAR(128) | | 名称 |
| `revision` | VARCHAR(16) | NOT NULL | 版本 |
| `status` | VARCHAR(16) | NOT NULL | 同逻辑配方生命周期 |
| `is_frozen` / 冻结留痕列 | — | | 同逻辑配方 |
| `effective_from` / `effective_to` | DATETIME(3) | | 生效窗口 |
| `group_id` | VARCHAR(32) | FK→`mds_recipe_group.id`（应为 `PHYSICAL`） | 所属物理配方组 |
| `class_id` | VARCHAR(32) | FK→`mds_equipment_class.id` | 适用设备大类 |
| `model_id` | VARCHAR(32) | FK→`mds_equipment_model.id` | 可选：具体型号 |
| `logic_recipe_id` | VARCHAR(32) | FK→`mds_logic_recipe.id` | **实现的逻辑配方**（映射键，可空——纯设备侧配方可不挂逻辑配方） |
| `recipe_class` | VARCHAR(64) | | 配方类别 |
| `body_ref` | VARCHAR(512) | | 物理配方正文/程序体引用（SEMI E40 PP，外围系统） |
| `ext_attrs` | JSON | | 异构扩展属性 |
| `description` | VARCHAR(512) | | 基类 |

- **LogicRecipe → PhysicalRecipe 映射**：一份 `mds_logic_recipe` 通过 `logic_recipe_id` 关联**多条** `mds_recipe`（每设备类一条），无需独立映射表。
- 查询「逻辑配方 L 在设备类 C 上的物理配方」：`SELECT * FROM mds_recipe WHERE logic_recipe_id=L AND class_id=C`。

### 3.4 `mds_equipment_recipe_qual`（设备↔物理配方 资格，含 PPID）— 扩展自设备设计 §3.8

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` / `tenant_id` | — | PK | 基类 |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 设备 |
| `recipe_id` | VARCHAR(32) | NOT NULL, FK→`mds_recipe.id` | **物理**配方 |
| `ppid` | VARCHAR(128) | NOT NULL | **Process Program ID**：该物理配方在此设备上的程序标识（SEMI E40） |
| `qual_status` | VARCHAR(16) | NOT NULL | `QUALIFIED`/`PENDING`/`REVOKED` |
| `is_default` | BIT | DEFAULT 0 | 该设备资格集中默认物理配方 |
| `area_code` | VARCHAR(64) | | 可选：资格作用域（所在 AREA，便于按区派工） |
| `qualified_by` / `qualified_at` | — | | 认证人/时间 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(equipment_id, recipe_id, tenant_id, deleted)`。
- **PPID 设备内唯一**：`(equipment_id, ppid, tenant_id, deleted)` 唯一（一台设备上 PPID 不能重复命名程序）。
- **单/多 recipe 统一表达**：资格集 1 条 = 专用机（单 recipe）；多条 = 柔性机（多 recipe），与设备设计 §3.8 一致。
- 运行时每腔体单活跃配方（EAP 实时态），但与「资格集合」正交。

---

## 4. 关键设计点

### 4.1 双层配方（Logic vs Physical）+ PPID 三层
- **逻辑层（工具无关）**：`mds_logic_recipe` 表述「工艺意图」，被 route 工序直接引用——这是跨设备、跨 fab 可复用的配方身份。
- **物理层（工具相关）**：`mds_recipe` 表述「在某类设备上怎么跑」，参数已落到具体设备单位。
- **PPID 层（设备实例）**：`mds_equipment_recipe_qual.ppid` 表述「这份物理配方加载到这台具体设备后叫什么程序名」。
- 三层逐级具体化，正好对应 SEMI E40 的 Logical Recipe → Equipment Recipe → Process Program。

### 4.2 生命周期 + 冻结 + 版本不可变（复用统一范式）
- 逻辑/物理配方均采用与 route/product 一致的：**`DRAFT→IN_REVIEW→APPROVED→RELEASED→OBSOLETE→ARCHIVED`**，仅 `RELEASED` 可被引用。
- **`(code, version)` 唯一且 RELEASED 后不可变**；结构性修改须 `cloneAsNewVersion()` 出新版本，旧版本保持可用（已投 lot 不受影响）。
- **`is_frozen` 叠加冻结**（与 status 正交）：量产前/客户/法规冻结时锁写、禁止被 clone 覆盖，只能基于它开新版本——与 route §4.2 同源。

### 4.3 派工解析路径（MDS 提供映射，引擎执行）
给定 chosen equipment `E` 与 route 工序绑定的逻辑配方 `L`：
1. 取 `E.class_id`（设备大类）；
2. `R = mds_recipe WHERE logic_recipe_id = L AND class_id = E.class_id`（该逻辑配方在同类设备上的物理配方）；
3. `Q = mds_equipment_recipe_qual WHERE equipment_id = E AND recipe_id = R AND qual_status = QUALIFIED`；
4. `PPID = Q.ppid` → 由 EAP 在设备 `E` 上 `select/load` 该 PPID 执行。
> MDS 不存「当前在跑的 PPID」；该实时态归 EAP/MES。MDS 只保证映射主数据被正确维护。

### 4.4 与 RecipeGroup 的归属
- 逻辑配方挂 `group_type=LOGIC` 的组；物理配方挂 `group_type=PHYSICAL` 的组。
- 分组用于权限/检索/批量操作（如「Litho 组全部冻结」），不参与实体关系强约束。

### 4.5 与既有主数据闭环
- **route**：`operation.recipe_id → mds_logic_recipe`（设计期绑定逻辑配方）。
- **equipment**：`qual.recipe_id → mds_recipe` + `class_id/model_id` 关联设备分类；多 recipe 资格表达柔性机。
- **product**：逻辑配方的 `tech_node`/`product_family_code` 与产品主数据（[product-design.md](product-design.md) §4.4）取交集，参与「产品可造性」筛选。
- **process**：`mds_logic_recipe.process_id → mds_process`（route-design §3.2）。
- **location**：`qual.area_code → mds_location(AREA)`，按区限定配方资格作用域。

### 4.6 软删/租户/主键（与平台一致）
- 所有实体继承 `BaseDefData`：`String id`（雪花/UUIDv7）、`tenant_id`、`@Version`、`deleted`、审计列。
- 唯一约束均含 `(..., tenant_id, deleted)`（T2.5）。
- 行级过滤：`@SQLRestriction("deleted=false")` + `@Filter(cimTenantFilter)`；`@FilterDef` 放实体包 `package-info.java`。

---

## 5. 与平台底座衔接（强制对齐）
- 全部实体继承 `BaseDefData` / `BaseEventData`（审计、`tenant_id`、乐观锁、`deleted`）。
- `@History(SNAPSHOT)`：配方增改经 `AbstractJpaService.update` 自动落 `{entity}_hist`，天然形成配方变更时间线（与设备搬迁轨迹同源）。
- 租户过滤：`TenantContext` + `cimTenantFilter`。
- 主键：`IdGenerator`（与位置/设备一致）。
- 迁移：主表 + `_hist` 成对，遵循平台「主历成对迁移」规范。

---

## 6. MySQL DDL（主历成对，节选主表）

```sql
CREATE TABLE mds_recipe_group (
  id              VARCHAR(32)  NOT NULL,
  tenant_id       VARCHAR(32)  NOT NULL,
  code            VARCHAR(64)  NOT NULL,
  name            VARCHAR(128),
  group_type      VARCHAR(16)  NOT NULL,
  parent_group_id VARCHAR(32),
  path            VARCHAR(512),
  status          VARCHAR(16)  NOT NULL,
  sort_no         INT,
  description     VARCHAR(512),
  version         BIGINT       NOT NULL DEFAULT 0,
  deleted         BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_recipe_group PRIMARY KEY (id),
  CONSTRAINT uq_mds_rg_code UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_rg_parent FOREIGN KEY (parent_group_id) REFERENCES mds_recipe_group (id)
);

CREATE TABLE mds_logic_recipe (
  id                   VARCHAR(32)  NOT NULL,
  tenant_id            VARCHAR(32)  NOT NULL,
  code                 VARCHAR(64)  NOT NULL,
  name                 VARCHAR(128),
  revision              VARCHAR(16)  NOT NULL,
  status               VARCHAR(16)  NOT NULL,
  is_frozen            BIT          NOT NULL DEFAULT 0,
  frozen_by            VARCHAR(64),
  frozen_at            DATETIME(3),
  frozen_reason        VARCHAR(256),
  unfreeze_at          DATETIME(3),
  effective_from       DATETIME(3),
  effective_to         DATETIME(3),
  group_id             VARCHAR(32),
  process_id           VARCHAR(32),
  recipe_class         VARCHAR(64),
  tech_node            VARCHAR(32),
  product_family_code  VARCHAR(64),
  body_ref             VARCHAR(512),
  ext_attrs            JSON,
  description          VARCHAR(512),
  version          BIGINT       NOT NULL DEFAULT 0,
  deleted              BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_logic_recipe PRIMARY KEY (id),
  CONSTRAINT uq_mds_lr_code_rev UNIQUE (code, revision, tenant_id, deleted),
  CONSTRAINT fk_lr_group   FOREIGN KEY (group_id)   REFERENCES mds_recipe_group (id),
  CONSTRAINT fk_lr_process FOREIGN KEY (process_id) REFERENCES mds_process (id)
);

CREATE TABLE mds_recipe (
  id              VARCHAR(32)  NOT NULL,
  tenant_id       VARCHAR(32)  NOT NULL,
  code            VARCHAR(64)  NOT NULL,
  name            VARCHAR(128),
  revision         VARCHAR(16)  NOT NULL,
  status          VARCHAR(16)  NOT NULL,
  is_frozen       BIT          NOT NULL DEFAULT 0,
  frozen_by       VARCHAR(64),
  frozen_at       DATETIME(3),
  frozen_reason   VARCHAR(256),
  unfreeze_at     DATETIME(3),
  effective_from  DATETIME(3),
  effective_to    DATETIME(3),
  group_id        VARCHAR(32),
  class_id        VARCHAR(32),
  model_id        VARCHAR(32),
  logic_recipe_id VARCHAR(32),
  recipe_class    VARCHAR(64),
  body_ref        VARCHAR(512),
  ext_attrs       JSON,
  description     VARCHAR(512),
  version     BIGINT       NOT NULL DEFAULT 0,
  deleted         BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_recipe PRIMARY KEY (id),
  CONSTRAINT uq_mds_recipe_code_rev UNIQUE (code, revision, tenant_id, deleted),
  CONSTRAINT fk_recipe_group   FOREIGN KEY (group_id)        REFERENCES mds_recipe_group (id),
  CONSTRAINT fk_recipe_class   FOREIGN KEY (class_id)        REFERENCES mds_equipment_class (id),
  CONSTRAINT fk_recipe_model   FOREIGN KEY (model_id)        REFERENCES mds_equipment_model (id),
  CONSTRAINT fk_recipe_logic   FOREIGN KEY (logic_recipe_id) REFERENCES mds_logic_recipe (id)
);

CREATE TABLE mds_equipment_recipe_qual (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  equipment_id  VARCHAR(32)  NOT NULL,
  recipe_id     VARCHAR(32)  NOT NULL,
  ppid          VARCHAR(128) NOT NULL,
  qual_status   VARCHAR(16)  NOT NULL,
  is_default    BIT          DEFAULT 0,
  area_code     VARCHAR(64),
  qualified_by  VARCHAR(64),
  qualified_at  DATETIME(3),
  description   VARCHAR(512),
  version       BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqp_recipe_qual PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqp_rq UNIQUE (equipment_id, recipe_id, tenant_id, deleted),
  CONSTRAINT uq_mds_eqp_ppid UNIQUE (equipment_id, ppid, tenant_id, deleted),
  CONSTRAINT fk_erq_equipment FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id),
  CONSTRAINT fk_erq_recipe    FOREIGN KEY (recipe_id)    REFERENCES mds_recipe (id)
);
CREATE INDEX idx_erq_equipment ON mds_equipment_recipe_qual (equipment_id);
CREATE INDEX idx_erq_recipe    ON mds_equipment_recipe_qual (recipe_id);
CREATE INDEX idx_erq_ppid      ON mds_equipment_recipe_qual (ppid);
```

> 各主表对应 `{entity}_hist` 由 `@History(SNAPSHOT)` 自动生成（结构同源，增加 `op_type/operator/op_time/trx_id/change_set_json` 等审计列），遵循平台主历成对迁移规范。

---

## 7. 代码结构（建议）

```
com.cim.mds.recipe
  ├── entity
  │   ├── RecipeGroup.java          # @Entity 继承 BaseDefData + @History(SNAPSHOT)；group_type/path
  │   ├── LogicRecipe.java          # @Entity 继承 BaseDefData；status/is_frozen/version/process_id/tech_node
  │   ├── Recipe.java               # @Entity 继承 BaseDefData；class_id/model_id/logic_recipe_id（物理配方）
  │   └── EquipmentRecipeQual.java  # @Entity 继承 BaseDefData；ppid/qual_status/is_default/area_code
  ├── repository
  │   ├── LogicRecipeRepository.java
  │   ├── RecipeRepository.java
  │   └── EquipmentRecipeQualRepository.java
  └── service
      ├── RecipeGroupService.java        # 继承 AbstractJpaService（租户过滤 + @History 留痕）
      ├── LogicRecipeService.java         # + cloneAsNewVersion() / freeze() / release()
      ├── RecipeService.java
      ├── EquipmentRecipeQualService.java # + resolvePpid(equipmentId, logicRecipeId)
      └── RecipeValidator.java            # 校验: 引用合法性 / 版本不可变 / 冻结保护 / group_type 一致
```

---

## 8. 待补 / 后续
- **Recipe 正文(body) 契约**：`body_ref` 指向的外围 Recipe 系统（SEMI E40）的数据格式与同步机制。
- **MES/EAP 实时态衔接契约**：派工时 `resolvePpid` 的导出格式、PPID 下发与回写（select/load）、当前活跃配方/PPID 的实时域归属。
- **recipe 参数级校验**：逻辑配方→物理配方参数映射的合法性检查（单位换算、范围）。
- **DCP 上下文**：SEMI E94 以 recipe 为采集上下文的键映射。
- 与[设备设计](equipment-design.md) §3.8 的 `mds_recipe` / `mds_equipment_recipe_qual` 保持一致（本文为权威定义，设备设计 §3.8 引用本文）。

---

## 附录 A. Recipe 正文与参数级（DCP）建模（第二轮补强）

> **补的缺口**：原设计只到 `body_ref`（正文引用），配方**不可展开**——无法回答"这份配方有哪几步、每步参数是什么"，导致参数级校验、配方对比、DCP 采集上下文都无从落地。

### A.1 `mds_recipe_step`（配方步骤）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `recipe_id` | VARCHAR(32) | NOT NULL, FK→`mds_recipe.id` | 所属**物理配方** |
| `step_seq` | INT | NOT NULL | 步骤序号 |
| `step_name` | VARCHAR(64) | NOT NULL | 步骤名（如 `PUMP_DOWN`/`RF_ON`/`STABILIZE`） |
| `step_type` | VARCHAR(16) | | `PROCESS`/`STABILIZE`/`RAMP`/`PURGE`/`TRANSFER`/`OTHER` |
| `duration_sec` | DECIMAL(10,2) | | 标准时长（秒） |
| `is_critical` | BIT | DEFAULT 0 | 是否关键步骤 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(recipe_id, step_seq, tenant_id, deleted)`。

### A.2 `mds_recipe_step_param`（步骤参数）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `recipe_step_id` | VARCHAR(32) | NOT NULL, FK→`mds_recipe_step.id` | 所属步骤 |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | **统一参数语义**（[param-def-design](param-def-design.md)） |
| `set_value` | VARCHAR(64) | | 设定值 |
| `min_value`/`max_value` | VARCHAR(64) | | 允许范围 |
| `unit` | VARCHAR(16) | FK→`mds_uom.code` | 单位 |
| `is_tunable` | BIT | DEFAULT 0 | 是否 R2R 可调（联动 [apc-fdc-design](apc-fdc-design.md)） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(recipe_step_id, param_def_id, tenant_id, deleted)`。

### A.3 三处闭环

1. **参数统一**：`param_def_id` → [param-def-design](param-def-design.md)，使配方参数、工艺窗口、设备变量同源可比；
2. **工艺窗口对照**：`mds_process_flow_step_param`（工艺要求）↔ `mds_recipe_step_param`（配方设定）可由 `RecipeValidator` **自动核对**（设定值是否落在工艺窗口内）；
3. **DCP 上下文**：采集上下文 = `(recipe, recipe_step)`，供 [equipment-interface-design](equipment-interface-design.md) 的 report 关联；`is_tunable=1` 的参数即 APC 控制变量（[apc-fdc-design §3.1](apc-fdc-design.md)）。

### A.4 与 MES 生产执行衔接

| 场景 | 说明 |
| --- | --- |
| **下发前校验** | MES 展开配方步骤，做**参数级预校验**（设定值 vs 工艺窗口），避免加载即超窗 |
| **EAP 执行** | 按 `step_seq` 顺序执行；`is_tunable` 参数可被 APC 在批间调整 |
| **配方版本对比** | 从"配方整体 diff"下沉到**步骤/参数粒度 diff**，支撑工艺评审 |
| **追溯** | 回答"这批实际跑了哪些步骤、每步设定值/实测值"（实测归 MES） |

### A.5 边界（重要）

- **正文仍在 `body_ref`**：外围 Recipe 系统（SEMI E40）是权威正文源；本表是 MDS 侧的**结构化镜像**，用于**校验/对比/DCP**；
- 同步方式：外围系统推送或受控导入，**不把 MDS 做成 Recipe 编辑器**（避免职责膨胀）。
