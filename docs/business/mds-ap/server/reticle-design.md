# MDS 掩模版（Reticle / Photomask）主数据设计

> 本文承接[层定义](layer-design.md)（layer）、[设备](equipment-design.md)（scanner）、[载具](carrier-design.md)（pod）、[仓库](bank-design.md)（stocker），
> 补全 MDS 的**掩模版（光罩）** 主数据——晶圆厂**最贵的可复用资产**，光刻工序的物理前提。
>
> 设计原则延续全篇：**MDS 拥有光罩注册 + 套件 + 资格 + 寿命策略；实时位置、在机状态、使用计数归 AMHS / MES / EAP**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么光罩必须独立建模（核心）

光罩与已有实体**都不能互相替代**：

| 已有实体 | 为什么不能替代光罩 |
| --- | --- |
| 设备 | 光罩**装在** scanner 上，不是设备本身 |
| 配方 | 配方是"怎么曝光"，光罩是"曝光什么图形"——一份配方可配多片光罩 |
| 载具 | reticle pod 是载具的**一个子类型**，但光罩本身不是载具 |
| 仓库 | reticle stocker 是仓库的**一个子类型**，但光罩本身不是仓库 |

光罩的独特属性：**单片价值极高、有使用次数寿命、必须在指定 scanner 上做过资格（alignment）、需恒温恒湿与洁净存储**。

> **结论（RT1）**：光罩是 MDS 的**独立主数据**（含套件、资格、寿命），且**复用**载具与仓库两个既有域表达 pod 与 stocker（不重复造轮子）。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 光罩套件 / 单片光罩注册 | **MDS** | `mds_reticle_set` / `mds_reticle`，本文范围 |
| 光罩 ↔ 层映射 | **MDS** | `mds_reticle_layer` |
| 光罩 ↔ scanner **资格** | **MDS** | `mds_reticle_qual`（同 [recipe-design §3.4](recipe-design.md) 的资格范式） |
| 寿命策略（曝光次数/晶圆数上限） | **MDS** | `mds_reticle_usage_policy` |
| **光罩实时位置**（在 stocker / 在机 / 在送） | **AMHS / MES** | MDS 不存 |
| **实际曝光次数 / 累计晶圆数** | **EAP / MES** | 实时计数，驱动寿命预警 |
| **光罩送修 / 清洗 / 报废的执行** | **MES / 维护系统** | MDS 给策略与行政态 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 光罩 ↔ 套件 | `mds_reticle.set_id → mds_reticle_set.id` | 本文 §3.2 |
| 光罩 ↔ 层 | `mds_reticle_layer.layer_id → mds_layer.id` | [layer-design §3.1](layer-design.md) |
| 光罩 ↔ 工序 | 经 `layer → mds_layer_operation → mds_route_operation` | [layer-design](layer-design.md) |
| 光罩 ↔ scanner 资格 | `mds_reticle_qual.equipment_id → mds_equipment.id`（或 `equipment_model_id`） | [equipment-design §3.3](equipment-design.md) |
| 光罩 ↔ 载具（pod） | 复用 `mds_carrier_type.carrier_type = RETICLE_POD`（**新增枚举值**） | [carrier-design §3.1](carrier-design.md) |
| 光罩 ↔ 仓库（stocker） | 复用 `mds_bank.bank_type = RETICLE_STOCKER`（**新增枚举值**） | [bank-design §3.1](bank-design.md) |
| 光罩编码规则 | `mds_naming_rule.entity_type=RETICLE`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |
| 光罩寿命 → 约束 | `RETICLE_USAGE_LIMIT`（新增约束类型） | [constraint-design §4.2](constraint-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E30 / E87** | Carrier/载具管理模型适用于 reticle pod（SMIF 类容器） | 复用 `mds_carrier_type`（`RETICLE_POD`），pod 的 E87 状态机复用既有 `model_kind=CARRIER_STATE` |
| **SEMI E40 / E94** | 与 Process Program（PPID）类似，光罩在设备上需**资格/对准确认** | `mds_reticle_qual`（对齐参数 + 资格态），同 recipe qual 范式 |
| **fab Reticle Management（RTMS）实践** | 光罩按**套（reticle set）**管理（一个产品节点一套）；单片有**曝光次数上限**（超限需清洗/送修/报废）；需与 scanner 做**匹配资格** | 套件实体 + 寿命策略 + 资格表（§4.1–§4.4） |
| **先进工艺（Multi-Patterning）** | 一个工艺层对应多张掩模（LELE/SADP），光罩数量随节点指数上升 | 支撑 `mds_reticle_layer` 多对多（[layer-design §4.2](layer-design.md)） |
| **洁净与温控** | 光罩存储需恒温恒湿专用 stocker、专用 pod，污染控制严格 | 复用 `mds_bank` / `mds_carrier_type` 的兼容与洁净语义（[carrier-design §4.4](carrier-design.md)） |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Reticle / 光罩（掩模版）** | 单片掩模，光刻时投影图形的母版 | **不是**载具（pod）、不是配方、不是层 |
| **Reticle Set / 光罩套件** | 一套光罩（一个产品节点所需全部掩模） | 与「套件」不同：套件是集合，单片是成员 |
| **Reticle Pod** | 存放单片的密闭容器 | **复用** `mds_carrier_type`（`RETICLE_POD`），不建新表 |
| **Reticle Qualification / 光罩资格** | 该片光罩在某 scanner 上是否已对准验证合格 | 与设备↔配方资格同范式，但对象是光罩 |
| **Usage Policy / 寿命策略** | 曝光次数 / 累计晶圆数上限与到期动作 | 实际计数在 EAP/MES，MDS 只存阈值 |
| **admin_status / 光罩行政态** | MDS 侧资产态：`ACTIVE`/`HOLD`/`REPAIR`/`RETIRED` | 与 E87 实时态（在机/在库）正交 |

---

## 3. 实体总览与 ER

```
mds_reticle_set (套件: tech_node / product_family / layer 集合)
        ▲ set_id
mds_reticle ──< mds_reticle_qual        (光罩 ↔ scanner 资格)
        │    ──< mds_reticle_layer      (光罩 ↔ 层, 多对多)
        │            layer_id ──> mds_layer (layer-design)
        ├──> mds_reticle_usage_policy   (寿命策略; 也可挂在 set 级)
        └──> mds_carrier_type           (pod: carrier_type=RETICLE_POD, 复用)
                 
存储: mds_bank (bank_type=RETICLE_STOCKER, 复用) ──> mds_bank_slot (槽位目录)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 光罩套件 | `mds_reticle_set` | 一个产品/节点所需全套光罩 |
| 光罩 | `mds_reticle` | 单片光罩注册 |
| 光罩↔scanner 资格 | `mds_reticle_qual` | 对准验证合格关系 |
| 光罩↔层 | `mds_reticle_layer` | 多对多（先进工艺一层多片） |
| 光罩寿命策略 | `mds_reticle_usage_policy` | 曝光次数/晶圆数上限与动作 |
| *(复用)* 光罩盒 | `mds_carrier_type` | `carrier_type=RETICLE_POD`（枚举扩展） |
| *(复用)* 光罩库 | `mds_bank` | `bank_type=RETICLE_STOCKER`（枚举扩展） |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_reticle_set`（光罩套件）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `set_code` | VARCHAR(64) | NOT NULL | 套件代码（如 `RS-28N-LOGIC-A`） |
| `name` | VARCHAR(128) | | 名称 |
| `tech_node` | VARCHAR(16) | | 技术节点 |
| `product_family_id` | VARCHAR(32) | FK→`mds_product_family.id` | 适用产品族 |
| `layer_count` | INT | | 套内掩模层数（应与成员数一致，校验） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED`/`OBSOLETE` |
| `revision` | VARCHAR(16) | NOT NULL | 版本 |
| `footer_mask_info` | VARCHAR(128) | | 配套 frame/footer 掩模说明 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(set_code, revision, tenant_id, deleted)`。

### 3.2 `mds_reticle`（单片光罩）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 光罩编码（建议内嵌层码，如 `RET-M1-A`） |
| `set_id` | VARCHAR(32) | FK→`mds_reticle_set.id` | 所属套件（可空=独立片） |
| `reticle_no` | VARCHAR(32) | | 套内序号 |
| `mask_type` | VARCHAR(16) | NOT NULL | `BINARY`/`PSM`（相移）/`OPC`/`EUV`/`ALT_PSM` |
| `plate_size` | VARCHAR(16) | | 版尺寸（`5INCH`/`6INCH`） |
| `critical_dimension` | DECIMAL(10,3) | | 关键尺寸（nm） |
| `layer_id` | VARCHAR(32) | FK→`mds_layer.id` | 主对应层（多对多细节见 §3.4，此列为主层，便于查询） |
| `vendor_id` | VARCHAR(32) | FK→`mds_vendor.id` | 制造商（[org-personnel-design](org-personnel-design.md)） |
| `pod_type_code` | VARCHAR(64) | FK→`mds_carrier_type.code` | 配套 pod 类型（`carrier_type=RETICLE_POD`） |
| `home_stocker_code` | VARCHAR(64) | FK→`mds_bank.code` | 归属光罩库（`bank_type=RETICLE_STOCKER`） |
| `usage_policy_id` | VARCHAR(32) | FK→`mds_reticle_usage_policy.id` | 寿命策略 |
| `admin_status` | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE' | MDS 行政态：`ACTIVE`/`HOLD`/`REPAIR`/`RETIRED` |
| `qualified_date` | DATE | | 首次资格日期 |
| `status` | VARCHAR(16) | NOT NULL | 记录态：`DRAFT`/`ACTIVE`/`DEPRECATED`/`ARCHIVED` |
| `ext_attrs` | JSON | | 扩展（写场尺寸/透射率…） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。
- `admin_status` 与 E87 实时态正交（同 [carrier-design §3.2](carrier-design.md) 三态分离思路）。

### 3.3 `mds_reticle_qual`（光罩 ↔ scanner 资格）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `reticle_id` | VARCHAR(32) | NOT NULL, FK→`mds_reticle.id` | 光罩 |
| `equipment_id` | VARCHAR(32) | FK→`mds_equipment.id` | 具体 scanner（与 model 二选一） |
| `equipment_model_id` | VARCHAR(32) | FK→`mds_equipment_model.id` | 型号级资格（该型全部可用） |
| `alignment_recipe` | VARCHAR(64) | | 对准配方/作业程序标识 |
| `overlay_spec` | VARCHAR(64) | | 套刻精度要求 |
| `qual_status` | VARCHAR(16) | NOT NULL DEFAULT 'QUALIFIED' | `QUALIFIED`/`PENDING`/`REVOKED` |
| `qualified_by`/`qualified_at` | — | | 资格留痕 |
| `requal_interval_days` | INT | | 再验证周期 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(reticle_id, equipment_id, equipment_model_id, tenant_id, deleted)`（服务层保证二者非全空）。
- **同范式说明**：与 [recipe-design §3.4](recipe-design.md) 的 `mds_equipment_recipe_qual` 结构对齐——「资产 × 设备 = 资格」是重复出现的模式，命名为 `*_qual` 保持一致。

### 3.4 `mds_reticle_layer`（光罩 ↔ 层映射）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `reticle_id` | VARCHAR(32) | NOT NULL, FK→`mds_reticle.id` | 光罩 |
| `layer_id` | VARCHAR(32) | NOT NULL, FK→`mds_layer.id` | 层（`layer_type=MASK_LAYER` 优先） |
| `multi_pattern_seq` | INT | | 多重图形化序号（LELE 的 A/B；SADP 的 1/2/3） |
| `is_main` | BIT | DEFAULT 0 | 是否该层的主动光罩 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(reticle_id, layer_id, tenant_id, deleted)`。

### 3.5 `mds_reticle_usage_policy`（寿命策略）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 策略代码 |
| `max_exposures` | BIGINT | | 最大曝光次数 |
| `max_wafer_count` | BIGINT | | 最大累计晶圆数 |
| `max_age_days` | INT | | 最长使用期限 |
| `clean_interval_exposures` | BIGINT | | 每 N 次需清洗 |
| `on_exhaust_action` | VARCHAR(16) | NOT NULL | `CLEAN`/`REPAIR`/`RETIRE`/`HOLD` |
| `warning_ratio` | DECIMAL(4,2) | | 预警比例（0.8=达 80% 预警） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。
- 实际计数在 EAP/MES（设备接口点表承载计数变量，[equipment-interface-design](equipment-interface-design.md)）。

---

## 4. 关键设计点（拍板 RT1–RT6）

### 4.1 独立资产定位（RT1）
见 §0.1。光罩独立成域，但 **pod 复用 `mds_carrier_type`、stocker 复用 `mds_bank`**（枚举扩展，不建重复表）——这是全篇「不重复造轮子」原则的延续（同 [constraint-design §4.8](constraint-design.md)）。

### 4.2 套件 vs 单片（RT2）
- **套件**是业务单元（"这个产品节点需要这一套"），用于配齐检查（缺片则整套不可用）；
- **单片**是资产单元（有独立寿命、独立资格、独立行政态）；
- 与产品族关联：`mds_reticle_set.product_family_id`，与 [product-design §3.1](product-design.md) 的 Family 分层呼应。

### 4.3 光罩 ↔ 层映射（RT3）
- 多对多，支持先进工艺一层多片（`multi_pattern_seq`）；
- 与 [layer-design §4.2](layer-design.md) 的 PROCESS_LAYER : MASK_LAYER = 1:N 配合；
- 反向可查"这套光罩覆盖哪些层 → 哪些工序 → 哪条 route"。

### 4.4 光罩资格（RT4，核心）
- **为什么必须有**：同一片光罩换到另一台 scanner 上，套刻（overlay）精度不保证，必须重新对准验证——这与配方资格是**同一类问题**。
- 支持 **实例级**（指定某台 scanner）与 **型号级**（同型号全部）两种资格粒度；
- 派光罩时 MES 校验 `mds_reticle_qual.qual_status=QUALIFIED`；无资格 = BLOCK（可经 [constraint-design](constraint-design.md) 的 `EQUIP_RECIPE_QUAL` 同族约束表达，新增 `RETICLE_QUAL` 类型）。

### 4.5 寿命策略与到期动作（RT5）
- 三类上限：`max_exposures` / `max_wafer_count` / `max_age_days`；先到者触发；
- 清洗周期独立（`clean_interval_exposures`）——光罩用久会有沉积污染，需定期清洗复检；
- 到期动作：`CLEAN`（清洗后复检可用）/`REPAIR`（送修，`admin_status=REPAIR`）/`RETIRE`（报废）/`HOLD`。
- **MDS 给策略，EAP 计数，MES 执行**（同 [material-design §4.4](material-design.md) 的分工）。

### 4.6 编码与命名（RT6）
- 光罩编码建议**内嵌层码**：`RET-<layer>-<seq>`（如 `RET-M1-A`），实现"看码知层"；
- 由 [naming-rule-design](naming-rule-design.md) 的 `ATTR(layer.code)+CONST+SEQ` 段组合生成（`entity_type=RETICLE`）。

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 光罩实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 光罩/资格/寿命策略变更落 `{entity}_hist`（资产变更与资格吊销必须留痕） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + pod/stocker 枚举种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_reticle_set (
  id              VARCHAR(32) NOT NULL,
  tenant_id       VARCHAR(32) NOT NULL,
  set_code        VARCHAR(64) NOT NULL,
  name            VARCHAR(128),
  tech_node       VARCHAR(16),
  product_family_id VARCHAR(32),
  layer_count     INT,
  status          VARCHAR(16) NOT NULL,
  revision         VARCHAR(16) NOT NULL,
  footer_mask_info VARCHAR(128),
  description     VARCHAR(512),
  version_        BIGINT NOT NULL DEFAULT 0,
  deleted         BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_reticle_set PRIMARY KEY (id),
  CONSTRAINT uq_mds_reticle_set UNIQUE (set_code, revision, tenant_id, deleted),
  CONSTRAINT fk_retset_family FOREIGN KEY (product_family_id) REFERENCES mds_product_family (id)
);

CREATE TABLE mds_reticle (
  id                VARCHAR(32) NOT NULL,
  tenant_id         VARCHAR(32) NOT NULL,
  code              VARCHAR(64) NOT NULL,
  set_id            VARCHAR(32),
  reticle_no        VARCHAR(32),
  mask_type         VARCHAR(16) NOT NULL,
  plate_size        VARCHAR(16),
  critical_dimension DECIMAL(10,3),
  layer_id          VARCHAR(32),
  vendor_id         VARCHAR(32),
  pod_type_code     VARCHAR(64),
  home_stocker_code VARCHAR(64),
  usage_policy_id   VARCHAR(32),
  admin_status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  qualified_date    DATE,
  status            VARCHAR(16) NOT NULL,
  ext_attrs         JSON,
  description       VARCHAR(512),
  version_          BIGINT NOT NULL DEFAULT 0,
  deleted           BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_reticle PRIMARY KEY (id),
  CONSTRAINT uq_mds_reticle UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_reticle_set FOREIGN KEY (set_id) REFERENCES mds_reticle_set (id),
  CONSTRAINT fk_reticle_layer FOREIGN KEY (layer_id) REFERENCES mds_layer (id),
  CONSTRAINT fk_reticle_vendor FOREIGN KEY (vendor_id) REFERENCES mds_vendor (id),
  CONSTRAINT fk_reticle_policy FOREIGN KEY (usage_policy_id) REFERENCES mds_reticle_usage_policy (id)
);
CREATE INDEX idx_reticle_layer ON mds_reticle (layer_id);
CREATE INDEX idx_reticle_admin ON mds_reticle (admin_status);

CREATE TABLE mds_reticle_qual (
  id                  VARCHAR(32) NOT NULL,
  tenant_id           VARCHAR(32) NOT NULL,
  reticle_id          VARCHAR(32) NOT NULL,
  equipment_id        VARCHAR(32),
  equipment_model_id  VARCHAR(32),
  alignment_recipe    VARCHAR(64),
  overlay_spec        VARCHAR(64),
  qual_status         VARCHAR(16) NOT NULL DEFAULT 'QUALIFIED',
  qualified_by        VARCHAR(64),
  qualified_at        DATETIME(3),
  requal_interval_days INT,
  description         VARCHAR(512),
  version_            BIGINT NOT NULL DEFAULT 0,
  deleted             BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_reticle_qual PRIMARY KEY (id),
  CONSTRAINT fk_retqual_reticle FOREIGN KEY (reticle_id) REFERENCES mds_reticle (id),
  CONSTRAINT fk_retqual_eq FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id),
  CONSTRAINT fk_retqual_model FOREIGN KEY (equipment_model_id) REFERENCES mds_equipment_model (id)
);
CREATE INDEX idx_retqual_ret ON mds_reticle_qual (reticle_id);
CREATE INDEX idx_retqual_eq ON mds_reticle_qual (equipment_id);

CREATE TABLE mds_reticle_layer (
  id               VARCHAR(32) NOT NULL,
  tenant_id        VARCHAR(32) NOT NULL,
  reticle_id       VARCHAR(32) NOT NULL,
  layer_id         VARCHAR(32) NOT NULL,
  multi_pattern_seq INT,
  is_main          BIT DEFAULT 0,
  description      VARCHAR(512),
  version_         BIGINT NOT NULL DEFAULT 0,
  deleted          BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_reticle_layer PRIMARY KEY (id),
  CONSTRAINT uq_mds_reticle_layer UNIQUE (reticle_id, layer_id, tenant_id, deleted),
  CONSTRAINT fk_retlayer_ret FOREIGN KEY (reticle_id) REFERENCES mds_reticle (id),
  CONSTRAINT fk_retlayer_layer FOREIGN KEY (layer_id) REFERENCES mds_layer (id)
);

CREATE TABLE mds_reticle_usage_policy (
  id                     VARCHAR(32) NOT NULL,
  tenant_id              VARCHAR(32) NOT NULL,
  code                   VARCHAR(64) NOT NULL,
  max_exposures          BIGINT,
  max_wafer_count        BIGINT,
  max_age_days           INT,
  clean_interval_exposures BIGINT,
  on_exhaust_action      VARCHAR(16) NOT NULL,
  warning_ratio          DECIMAL(4,2),
  description            VARCHAR(512),
  version_               BIGINT NOT NULL DEFAULT 0,
  deleted                BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_ret_policy PRIMARY KEY (id),
  CONSTRAINT uq_mds_ret_policy UNIQUE (code, tenant_id, deleted)
);
```

> 历史表 `mds_reticle_hist` / `mds_reticle_qual_hist` / … 由 `@History(SNAPSHOT)` 生成。
> **枚举扩展迁移**：`mds_carrier_type.carrier_type` 增加 `RETICLE_POD`；`mds_bank.bank_type` 增加 `RETICLE_STOCKER`（见 [carrier-design §3.1](carrier-design.md) / [bank-design §3.1](bank-design.md)）。

---

## 7. 代码结构（包路径）

```
com.cim.mds.reticle
  ├── entity
  │   ├── ReticleSet.java          # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── Reticle.java              # @Entity 继承 BaseDefData（mask_type/pod/stocker/policy）
  │   ├── ReticleQual.java          # @Entity 继承 BaseDefData（光罩↔scanner 资格）
  │   ├── ReticleLayer.java         # @Entity 继承 BaseDefData（光罩↔层）
  │   ├── ReticleUsagePolicy.java   # @Entity 继承 BaseDefData（寿命策略）
  │   └── package-info.java         # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── ReticleRepository.java
  │   └── ReticleQualRepository.java
  ├── service
  │   ├── ReticleService.java        # 继承 AbstractJpaService；套件配齐检查/行政态迁移
  │   └── ReticleValidator.java       # 套件 layer_count 一致、资格完备、寿命策略必填
  └── web
      └── ReticleController.java      # 查询/套件配齐视图/资格矩阵/寿命台账
```

---

## 8. 待补 / 后续

- **与 AMHS 的接口**：光罩实时位置（在库/在机/在送）、pod 流转、stocker 槽位占用——MDS 不存，需接口约定。
- **寿命计数回写**：EAP 上报曝光次数/晶圆数，MDS 在 `warning_ratio` 处预警，到期触发 `admin_status` 变更或约束判定。
- **光罩送修闭环**：`admin_status=REPAIR` → 清洗/修复记录 → 复检 → 恢复 `ACTIVE`（记录归维护系统，可经 [pm-calibration-design](pm-calibration-design.md) 统一）。
- **与既有文档闭环**：本文引用 `mds_layer`（[layer-design](layer-design.md)）、`mds_equipment` / `mds_equipment_model`（[equipment-design](equipment-design.md)）、`mds_carrier_type`（[carrier-design](carrier-design.md)）、`mds_bank`（[bank-design](bank-design.md)）、`mds_product_family`（[product-design](product-design.md)）、`mds_vendor`（[org-personnel-design](org-personnel-design.md)）、新增 `entity_type=RETICLE`（[naming-rule-design](naming-rule-design.md)）、新增约束类型 `RETICLE_USAGE_LIMIT`/`RETICLE_QUAL`（[constraint-design](constraint-design.md)）。
