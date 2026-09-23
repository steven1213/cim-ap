# MDS 环境健康安全（EHS）主数据设计

> 本文承接[物料与耗材](material-design.md)（`hazard_class` 字段）、[厂务设施](facility-utility-design.md)（特气/化学品/排放）、[洁净度与环境](cleanliness-env-design.md)（环境合规）、[组织人员](org-personnel-design.md)（安全资质），
> 补全 MDS 的 **EHS（环境健康安全）主数据**。
>
> **补的合规缺口**：审计确认 EHS 相关仅在 4 篇被**零散提及**（`hazard_class` 只是物料上的一个字段），**没有危险品台账、相容性矩阵、安全要求、应急预案**。对以化学品与特气为主的晶圆厂，这是**稽核硬缺口**（ISO 14001 / ISO 45001 / 危险化学品安全管理条例）。
>
> 设计原则延续全篇：**MDS 拥有 EHS 定义与台账；实时监测归 EMS/FMCS；事故与整改执行归 EHS 系统**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么必须独立建域（ES1）

| 现状 | 风险 |
| --- | --- |
| 危险品信息只挂在物料的一个 `hazard_class` 字段 | 无法做**相容性判定**（酸碱混存会放热/产气）、无法做**重大危险源**分级 |
| 无化学品相容性矩阵 | 库房与线边混放事故风险；稽核必查 |
| 无安全要求台账 | 「该设备需联锁 / 该工序需 PPE / 该区域需气体侦测」无处表达 |
| 无应急预案 | 应急响应靠文档而非主数据，无法与设施/区域联动 |

> **结论（ES1）**：EHS 是 MDS 的**独立主数据域**，其核心价值是**危险品台账 + 相容性矩阵 + 安全要求 + 应急预案**四件套，并可与物料/设施/位置/设备**四向关联**。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有者 | 说明 |
| --- | --- | --- |
| 危险品台账（GHS 分类、UN 号、储存限值） | **MDS** | `mds_hazardous_substance` |
| 化学品相容性矩阵（禁配关系） | **MDS** | `mds_chemical_compat` |
| 安全要求（PPE/联锁/通风/侦测/消防） | **MDS** | `mds_safety_requirement` |
| 应急预案定义 | **MDS** | `mds_emergency_plan` |
| **SDS 正文文件** | **MDS 引用** | `sds_ref`（正文在 DMS/EHS 系统，同 `doc_ref` 范式） |
| **实时气体侦测值 / 泄漏报警** | **EMS / FMCS** | 实时态 |
| **事故记录、整改、演练记录** | **EHS 系统** | MDS 只存"要求"与"预案"，不存"执行" |
| 人员安全资质 | **MDS**（复用认证矩阵） | [org-personnel-design §3.3](org-personnel-design.md) `skill_type=SAFETY` |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 危险品 ↔ 物料 | `mds_hazardous_substance.material_code → mds_material.code` | [material-design §3.2](material-design.md) |
| 危险品 ↔ 储存位置 | 经 `mds_material.storage_condition` + 库房（`mds_bank`） | [bank-design](bank-design.md) |
| 安全要求 ↔ 设备/工序/区域 | `mds_safety_requirement(scope_type, scope_ref)` | [equipment](equipment-design.md) / [route](route-design.md) / [location](location-design.md) |
| 安全要求 ↔ 参数 | `param_def_id → mds_param_def.id`（如气体侦测阈值） | [param-def-design §3.1](param-def-design.md) |
| 应急预案 ↔ 设施/区域 | `scope_ref` | [facility-utility-design §3.1](facility-utility-design.md) |
| 预案文档 ↔ 受控文档 | `doc_ref` / `mds_document_link` | [document-design §3.2](document-design.md) |
| 气体侦测点 ↔ 环境监控点 | 复用 `mds_env_monitor_point`（`monitor_type=AMC/GAS`） | [cleanliness-env-design §3.2](cleanliness-env-design.md) |
| 安全资质 ↔ 认证矩阵 | `mds_skill.skill_type=SAFETY` | [org-personnel-design §3.3](org-personnel-design.md) |
| 安全要求 → 约束 | 新增 `SAFETY_REQUIREMENT` / `CHEMICAL_COMPAT` 约束类型 | [constraint-design §4.2](constraint-design.md) |
| 编码规则 | `entity_type=HAZ_SUBSTANCE`/`EMERGENCY_PLAN`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **ISO 14001（环境管理体系）** | 环境因素识别、合规义务、应急准备与响应 | `mds_emergency_plan` |
| **ISO 45001（职业健康安全）** | 危险源辨识、风险控制层级（消除/替代/工程控制/管理控制/PPE） | `mds_safety_requirement.req_type` |
| **GHS / GB 30000 系列** | 化学品危险性分类与标签（27 类）；SDS（GB/T 16483） | `ghs_hazard_class` + `sds_ref` |
| **《危险化学品安全管理条例》** | 危险化学品目录、储存、使用许可；**重大危险源**分级 | `is_major_hazard_source` + `storage_limit` |
| **《安全生产法》** | 风险分级管控与隐患排查双预防机制 | 安全要求台账 |
| **SEMI S2 / S8 / S14** | 设备安全指南、人因工程、火灾防护 | `mds_safety_requirement`（设备级） |
| **相容性矩阵实践** | 化学物质禁配表（酸碱/氧化剂/易燃物等）是 fab 库房管理的基础 | `mds_chemical_compat` |
| **消防 / 特种设备** | 消防设施、压力容器等合规要求 | `mds_safety_requirement`（`FIRE_SUPPRESSION`） |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Hazardous Substance / 危险品** | 具有危险性、受法规管控的化学品/气体 | **不是**所有物料；是物料中受管控的子集（含气体） |
| **GHS 分类** | 全球化学品统一分类标签（27 类危险性） | 与 `hazard_class`（物料上的粗分类）不同：GHS 是**多维度**的 |
| **相容性 / Incompatibility** | 两种物质接触是否危险（放热/产气/燃烧） | **不是**工艺兼容（`mds_carrier_type_compat` 是工艺准入） |
| **重大危险源** | 达到临界量的危险源（分级 A/B/C） | 不是"重要设备"；是法规概念 |
| **Safety Requirement / 安全要求** | 对设备/工序/区域的安全措施要求 | 不是"安全操作规程"（那是文档）；本表是**结构化要求** |
| **Emergency Plan / 应急预案** | 特定事故的响应方案 | 不是演练记录（归 EHS 系统） |
| **SDS** | 安全数据表（16 节），正文文件 | MDS 只存 `sds_ref`，不存正文 |

---

## 3. 实体总览与 ER

```
mds_material ──< mds_hazardous_substance   (危险品台账: GHS / UN / 储存限值 / 重大危险源)
                          │
                          ├──< mds_chemical_compat  (相容性矩阵: A × B → 判定)
                          └──> mds_document (SDS 正文引用: sds_ref)

mds_safety_requirement (安全要求: scope(设备/工序/区域/物质) × 类型(PPE/联锁/通风/侦测/消防))
        └──> param_def (阈值类要求，如气体侦测浓度)

mds_emergency_plan (应急预案: 类型 × 作用域 + doc_ref + 复评周期)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 危险品台账 | `mds_hazardous_substance` | GHS 分类与法规信息 |
| 相容性矩阵 | `mds_chemical_compat` | 禁配关系 |
| 安全要求 | `mds_safety_requirement` | 结构化安全措施要求 |
| 应急预案 | `mds_emergency_plan` | 预案定义与复评周期 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_hazardous_substance`（危险品台账）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | 危险品代码 |
| `name` | VARCHAR(128) | | 名称 |
| `material_code` | VARCHAR(64) | FK→`mds_material.code` | 关联物料（含特气） |
| `substance_category` | VARCHAR(16) | NOT NULL | `CHEMICAL`/`GAS`/`PYROPHORIC`（自燃）/`CORROSIVE`/`TOXIC`/`FLAMMABLE`/`OXIDIZER`/`OTHER` |
| `ghs_hazard_class` | VARCHAR(256) | | GHS 危险性分类（多值，逗号分隔或 JSON） |
| `un_no` | VARCHAR(16) | | UN 编号 |
| `packing_group` | VARCHAR(8) | | 包装类别（I/II/III） |
| `hazard_category` | VARCHAR(16) | | 中国危险货物类别 |
| `is_major_hazard_source` | BIT | DEFAULT 0 | 是否**重大危险源** |
| `mhs_level` | VARCHAR(8) | | 重大危险源分级（`A`/`B`/`C`） |
| `storage_limit_qty` | DECIMAL(18,4) | | 储存量上限（法规或内部限制） |
| `storage_limit_uom` | VARCHAR(16) | FK→`mds_uom.code` | 单位 |
| `storage_condition` | VARCHAR(32) | | 储存条件（`N2`/`FRIDGE`/`DARK`/`VENTILATED`/`SEPARATE`） |
| `sds_ref` | VARCHAR(512) | | **SDS 正文引用**（DMS 中的对象键/URL） |
| `regulatory_refs` | VARCHAR(512) | | 法规依据（如"危化品目录序号 xxx"） |
| `exposure_limit` | VARCHAR(64) | | 职业接触限值（OEL/TWA） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.2 `mds_chemical_compat`（化学品相容性矩阵）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `substance_a_id` | VARCHAR(32) | NOT NULL, FK→`mds_hazardous_substance.id` | 物质 A |
| `substance_b_id` | VARCHAR(32) | NOT NULL, FK→`mds_hazardous_substance.id` | 物质 B |
| `compat_result` | VARCHAR(16) | NOT NULL | `COMPATIBLE`/`CAUTION`（需条件）/`INCOMPATIBLE`（禁配） |
| `risk_note` | VARCHAR(256) | | 风险说明（放热/产气/燃烧/腐蚀） |
| `condition_note` | VARCHAR(256) | | 允许条件（温度/浓度/隔离距离） |
| `basis_ref` | VARCHAR(128) | | 判定依据（SDS 第 10 节 / 供应商建议 / 内部试验） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(substance_a_id, substance_b_id, tenant_id, deleted)`。
- **对称性**：A×B 与 B×A 语义相同，服务层保存时**自动补对称行**并做冲突检测。

### 3.3 `mds_safety_requirement`（安全要求）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 要求代码 |
| `scope_type` | VARCHAR(24) | NOT NULL | `EQUIPMENT`/`EQUIPMENT_CLASS`/`ROUTE_OPERATION`/`LOCATION`/`AREA`/`SUBSTANCE`/`FACILITY` |
| `scope_ref` | VARCHAR(128) | NOT NULL | 作用域目标 |
| `req_type` | VARCHAR(24) | NOT NULL | `PPE`/`INTERLOCK`（联锁）/`VENTILATION`（通风）/`EYEWASH`（洗眼器）/`GAS_DETECTOR`（气体侦测）/`FIRE_SUPPRESSION`（消防）/`EMERGENCY_STOP`/`LOTO`（上锁挂牌）/`GROUNDING`（接地）/`MONITORING` |
| `req_level` | VARCHAR(16) | | 控制层级：`ELIMINATE`/`SUBSTITUTE`/`ENGINEERING`/`ADMIN`/`PPE`（ISO 45001 层级） |
| `req_detail` | VARCHAR(512) | | 具体要求描述（如"需佩戴防酸手套与面罩"） |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | 阈值类要求的参数（如气体侦测浓度上限） |
| `threshold_value` | VARCHAR(64) | | 阈值 |
| `is_mandatory` | BIT | DEFAULT 1 | 是否强制 |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.4 `mds_emergency_plan`（应急预案）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 预案代码 |
| `name` | VARCHAR(128) | | 名称 |
| `plan_type` | VARCHAR(24) | NOT NULL | `FIRE`/`CHEM_SPILL`/`GAS_LEAK`/`POWER_FAIL`/`UPW_LOSS`/`EARTHQUAKE`/`MEDICAL`/`OTHER` |
| `scope_type` | VARCHAR(24) | | `SITE`/`FAB`/`AREA`/`EQUIPMENT`/`FACILITY`/`SUBSTANCE` |
| `scope_ref` | VARCHAR(128) | | 作用域目标 |
| `response_level` | VARCHAR(16) | | 响应分级：`LEVEL_1`/`LEVEL_2`/`LEVEL_3` |
| `trigger_note` | VARCHAR(512) | | 触发条件（可与 `mds_safety_requirement`/侦测点联动） |
| `doc_ref` | VARCHAR(512) | | 预案正文引用（受控文档） |
| `review_cycle_days` | INT | | 复评周期（法规通常要求定期复评） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 ES1–ES5）

### 4.1 独立域四件套（ES1）
台账 + 相容性 + 安全要求 + 应急预案。**最小可稽核集**，且全部与既有域外键相连（不是孤立台账）。

### 4.2 相容性矩阵的对称与传递（ES2）
- 保存 A×B 时**自动生成 B×A**，避免漏配；
- 冲突检测：同一对出现 `INCOMPATIBLE` 与 `COMPATIBLE` 时拒绝保存并告警（同 [constraint-design §4.6.3](constraint-design.md) 的冲突检测思路）；
- 用于：库房/线边**存放校验**、MES 投料前**混放检查**、灾后**处置指导**。

### 4.3 安全要求的控制层级（ES3）
- `req_level` 对应 ISO 45001 的**控制层级**（消除 > 替代 > 工程控制 > 管理控制 > PPE），便于风险评审时判断"是否过度依赖 PPE"；
- `GAS_DETECTOR`/`MONITORING` 类要求可绑定 `param_def_id` 与阈值，并与 [cleanliness-env-design](cleanliness-env-design.md) 的监控点联动。

### 4.4 应急预案与实时联动（ES4）
- 预案的 `trigger_note` 可引用实时事件（如 FMCS 的气体侦测报警、[facility-utility-design](facility-utility-design.md) 的设施 DOWN）；
- 事故发生时，MES/应急系统可按 `scope_ref` 反查**受影响的设备/在制品**（复用 [facility-utility-design §4.4](facility-utility-design.md) 的影响面推导）。

### 4.5 与人员资质、文档、合规的串联（ES5）
- 特种作业/危险品操作资质 → 复用 [org-personnel-design](org-personnel-design.md) 认证矩阵（`skill_type=SAFETY`），MES 派工前可校验；
- SDS 与预案正文 → 复用 [document-design](document-design.md)（受控文档 + `doc_ref`）；
- 台账变更 → 走 [change-mgmt-design](change-mgmt-design.md)（危险品新增属**高风险变更**）。

### 4.6 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | EHS 实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 台账/相容性/安全要求/预案变更落 `{entity}_hist`（**合规留痕刚性要求**） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + 常见危险品类别/安全要求类型种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_hazardous_substance (
  id                     VARCHAR(32) NOT NULL,
  tenant_id              VARCHAR(32) NOT NULL,
  code                   VARCHAR(32) NOT NULL,
  name                   VARCHAR(128),
  material_code          VARCHAR(64),
  substance_category     VARCHAR(16) NOT NULL,
  ghs_hazard_class       VARCHAR(256),
  un_no                  VARCHAR(16),
  packing_group          VARCHAR(8),
  hazard_category        VARCHAR(16),
  is_major_hazard_source BIT DEFAULT 0,
  mhs_level              VARCHAR(8),
  storage_limit_qty      DECIMAL(18,4),
  storage_limit_uom      VARCHAR(16),
  storage_condition      VARCHAR(32),
  sds_ref                VARCHAR(512),
  regulatory_refs        VARCHAR(512),
  exposure_limit         VARCHAR(64),
  status                 VARCHAR(16) NOT NULL,
  description            VARCHAR(512),
  version_               BIGINT NOT NULL DEFAULT 0,
  deleted                BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_haz_sub PRIMARY KEY (id),
  CONSTRAINT uq_mds_haz_sub UNIQUE (code, tenant_id, deleted)
);
CREATE INDEX idx_haz_sub_material ON mds_hazardous_substance (material_code);

CREATE TABLE mds_chemical_compat (
  id                VARCHAR(32) NOT NULL,
  tenant_id         VARCHAR(32) NOT NULL,
  substance_a_id  VARCHAR(32) NOT NULL,
  substance_b_id  VARCHAR(32) NOT NULL,
  compat_result     VARCHAR(16) NOT NULL,
  risk_note         VARCHAR(256),
  condition_note    VARCHAR(256),
  basis_ref         VARCHAR(128),
  description       VARCHAR(512),
  version_          BIGINT NOT NULL DEFAULT 0,
  deleted           BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_chem_compat PRIMARY KEY (id),
  CONSTRAINT uq_mds_chem_compat UNIQUE (substance_a_id, substance_b_id, tenant_id, deleted),
  CONSTRAINT fk_chemcompat_a FOREIGN KEY (substance_a_id) REFERENCES mds_hazardous_substance (id),
  CONSTRAINT fk_chemcompat_b FOREIGN KEY (substance_b_id) REFERENCES mds_hazardous_substance (id)
);

CREATE TABLE mds_safety_requirement (
  id              VARCHAR(32) NOT NULL,
  tenant_id       VARCHAR(32) NOT NULL,
  code            VARCHAR(64) NOT NULL,
  scope_type      VARCHAR(24) NOT NULL,
  scope_ref       VARCHAR(128) NOT NULL,
  req_type        VARCHAR(24) NOT NULL,
  req_level       VARCHAR(16),
  req_detail      VARCHAR(512),
  param_def_id    VARCHAR(32),
  threshold_value VARCHAR(64),
  is_mandatory    BIT DEFAULT 1,
  status          VARCHAR(16) NOT NULL,
  description     VARCHAR(512),
  version_        BIGINT NOT NULL DEFAULT 0,
  deleted         BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_safety_req PRIMARY KEY (id),
  CONSTRAINT uq_mds_safety_req UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_safetyreq_param FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id)
);
CREATE INDEX idx_safetyreq_scope ON mds_safety_requirement (scope_type, scope_ref);

CREATE TABLE mds_emergency_plan (
  id                VARCHAR(32) NOT NULL,
  tenant_id         VARCHAR(32) NOT NULL,
  code              VARCHAR(64) NOT NULL,
  name              VARCHAR(128),
  plan_type         VARCHAR(24) NOT NULL,
  scope_type        VARCHAR(24),
  scope_ref         VARCHAR(128),
  response_level    VARCHAR(16),
  trigger_note      VARCHAR(512),
  doc_ref           VARCHAR(512),
  review_cycle_days INT,
  status            VARCHAR(16) NOT NULL,
  description       VARCHAR(512),
  version_          BIGINT NOT NULL DEFAULT 0,
  deleted           BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_emerg_plan PRIMARY KEY (id),
  CONSTRAINT uq_mds_emerg_plan UNIQUE (code, tenant_id, deleted)
);
```

> 历史表由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.ehs
  ├── entity
  │   ├── HazardousSubstance.java      # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── ChemicalCompat.java           # @Entity 继承 BaseDefData
  │   ├── SafetyRequirement.java         # @Entity 继承 BaseDefData
  │   ├── EmergencyPlan.java             # @Entity 继承 BaseDefData
  │   └── package-info.java              # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── HazardousSubstanceRepository.java
  │   └── SafetyRequirementRepository.java
  ├── service
  │   ├── HazardousSubstanceService.java   # 台账维护 + 储存限值校验
  │   ├── ChemicalCompatService.java        # 对称补全 + 冲突检测 + 相容性判定
  │   ├── SafetyRequirementResolver.java     # 按设备/工序/区域解析安全要求（供 MES/巡检）
  │   └── EhsImpactService.java               # 事故影响面（复用依赖图遍历）
  └── web
      └── EhsController.java                 # 台账/相容性/安全要求/预案查询
```

---

## 8. 与 MES 生产执行衔接

| 场景 | MDS 提供 | MES / EHS 使用 |
| --- | --- | --- |
| **投料前 EHS 校验** | 危险品台账 + 储存限值 | MES 校验「本批所需危险品是否超储存限值 / 是否需资质人员」 |
| **混放/混批检查** | 相容性矩阵 | 库房与线边**禁配检查**；违规可写成 `CHEMICAL_COMPAT` 约束 BLOCK |
| **派工安全门禁** | 安全要求（`PPE`/`INTERLOCK`/`GAS_DETECTOR`）+ 人员安全资质 | MES 派工前校验「设备联锁正常 / 区域侦测在线 / 操作人有资质」 |
| **上机前检查** | 设备级安全要求 | EAP/MES 上机检查清单由 MDS 生成（而非硬编码） |
| **事故应急** | 应急预案 + 作用域 | 报警触发后，按 `scope_ref` 反查受影响设备/在制品，启动对应响应级别 |
| **职业健康** | OEL / 接触限值 | 环境与人员暴露监测的判定基准 |
| **稽核合规** | 台账 + SDS 引用 + 预案复评周期 | 生成 ISO 14001 / 45001 稽核证据链 |
| **变更** | 危险品新增/变更走 ECN | 高风险管理，需 EHS + 工艺 + 生产会签 |

> **对 MES 的关键价值**：把 EHS 从"贴在墙上的规程"变成**可校验的规则数据**——让"不该投的料投不进去、不该上的机派不出去"。

---

## 9. 待补 / 后续

- **EHS 系统集成**：事故/整改/演练记录的接口（MDS 只存要求与预案）。
- **储存量实时核算**：库存归 ERP/WMS；MDS 提供限值，二者比对做超限预警。
- **气体侦测点建模**：当前复用 `mds_env_monitor_point`（`monitor_type=GAS`），若需更细（气体种类/双探头/逻辑表决）可扩展独立表。
- **消防与特种设备**：压力容器/起重设备/消防设施的年检要求是否单列（当前归 `req_type`）。
- **多法域适配**：中国（危化品条例/GB 30000）与出口地区（REACH/OSHA）的法规字段差异。
- **与既有文档闭环**：[material-design](material-design.md)（`hazard_class` 字段 → 升级为台账入口）、[facility-utility-design](facility-utility-design.md)（特气/化学品/排放）、[cleanliness-env-design](cleanliness-env-design.md)（侦测点）、[org-personnel-design](org-personnel-design.md)（安全资质）、[document-design](document-design.md)（SDS/预案正文）、[change-mgmt-design](change-mgmt-design.md)、[constraint-design](constraint-design.md)（新增 `SAFETY_REQUIREMENT`/`CHEMICAL_COMPAT`）。
