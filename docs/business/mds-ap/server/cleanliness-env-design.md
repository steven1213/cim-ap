# MDS 洁净度与环境监控（Cleanliness / Environmental Monitoring）设计

> 本文承接[位置主数据](location-design.md)（FAB/BAY 层级）、[参数定义](param-def-design.md)（监控项参数）、[约束设计](constraint-design.md)、[载具设计](carrier-design.md)（污染控制），
> 补全 MDS 的**洁净等级（Clean Class）与环境监控点（Environmental Monitoring Point）** 主数据。
>
> **补的缺口**：`location-design` 目前把「洁净等级 / 洁净室分区」只写成 **FAB/BAY 行的说明性文字**（非字段、非实体）。结果：无法用洁净等级做**区域准入筛选**、无法表达**环境监控点**、无法建立「环境超限 → 区域设备/在制品」的联动。
>
> 设计原则延续全篇：**MDS 拥有洁净等级定义与监控点定义；环境实时值归环境监控系统（EMS）**。

---

## 0. 定位与边界（拍板）

### 0.1 MDS 拥有 vs 下游拥有

| 内容 | 拥有者 | 说明 |
| --- | --- | --- |
| 洁净等级定义（等级码/粒子限值） | **MDS** | `mds_clean_class` |
| 位置 ↔ 洁净等级 | **MDS** | `mds_location.clean_class_code`（新增字段） |
| 环境监控点定义 | **MDS** | `mds_env_monitor_point` |
| 监控点的参数与限值 | **MDS** | 复用 [param-def-design](param-def-design.md) |
| **环境实时值** | **EMS** | 秒–分，MDS 不存（[realtime-contract-design §1](realtime-contract-design.md) 第 12 行） |
| **超限报警与处置** | **EMS/MES** | MDS 给定义与限值 |

### 0.2 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 位置 ↔ 洁净等级 | `mds_location.clean_class_code → mds_clean_class.code` | [location-design §3](location-design.md) |
| 监控点 ↔ 位置 | `mds_env_monitor_point.location_id → mds_location.id` | [location-design §3](location-design.md) |
| 监控点 ↔ 参数 | `mds_env_monitor_point.param_def_id → mds_param_def.id` | [param-def-design §3.1](param-def-design.md) |
| 洁净等级 ↔ 载具准入 | 载具 `clean_status` 与区域洁净等级协同 | [carrier-design §4.2](carrier-design.md) |
| 洁净等级 ↔ 约束 | 新增约束类型 `CLEAN_CLASS_MATCH`（载具/人员/设备与区域洁净等级匹配） | [constraint-design §4.2](constraint-design.md) |
| 环境 ↔ 表达式 | `env.*` 命名空间（`clean_class`/`particle_count`/`temp`/`humidity`） | [expression-dsl-design §5](expression-dsl-design.md) |
| 环境 ↔ 厂务 | 温湿度由 PCW/空调设施提供 | [facility-utility-design](facility-utility-design.md) |
| 编码规则 | `entity_type=CLEAN_CLASS`/`ENV_POINT`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **ISO 14644-1** | 洁净室分级（ISO Class 1–9）按 ≥0.1/0.2/0.3/0.5/1.0/5.0 μm 粒子浓度限值 | `mds_clean_class`（`iso_class` + `particle_limit_json`） |
| **US FED STD 209E（历史标准）** | Class 1/10/100/1000…（仍被部分老厂沿用） | `fed_std_class` 兼容字段 |
| **SEMI E10 / 良率实践** | 环境污染（颗粒/AMC）直接导致缺陷与良率损失 | 环境监控点与 [reason-defect-design](reason-defect-design.md) 的缺陷码关联 |
| **fab 环境监控（EMS）实践** | 监控点覆盖粒子、温湿度、AMC（气态分子污染物）、压差、振动、静电 | `monitor_type` 枚举 |
| **EHS / ISO 14001** | 环境与排放合规要求可追溯 | 与 [facility-utility-design](facility-utility-design.md) 的排放设施联动 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Clean Class / 洁净等级** | 洁净度分级（ISO Class N） | 不是"区域"；区域（BAY）**拥有**一个洁净等级 |
| **ISO Class N** | N 越小越洁净（ISO Class 1 最洁净，Class 9 最松） | 与 FED STD 209E 的数值方向**相反**（Class 100 相当于 ISO 5） |
| **Environment Monitor Point / 环境监控点** | 具体测点（某个位置的粒子计数器/温湿度探头） | 不是"设施"；监控点在某位置，测某参数 |
| **AMC** | 气态分子污染物（Acid/Base/Organic/Dopant） | 与颗粒污染不同机理 |
| **particle_limit_json** | 各粒径的浓度限值集合 | 不是单个数值；ISO 分级是多粒径多限值 |

---

## 3. 实体总览与 ER

```
mds_clean_class (洁净等级: iso_class / fed_std_class / particle_limit_json)
      ▲ clean_class_code
mds_location (FAB / AREA / BAY 加 clean_class_code 字段)
      ▲ location_id
mds_env_monitor_point (监控点: monitor_type + param_def + alarm limits)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 洁净等级 | `mds_clean_class` | 分级定义与粒子限值 |
| 环境监控点 | `mds_env_monitor_point` | 测点定义 |
| *(扩展)* 位置 | `mds_location.clean_class_code` | 新增字段 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_clean_class`（洁净等级）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | 等级代码（如 `ISO5`/`ISO6`） |
| `name` | VARCHAR(64) | | 名称 |
| `iso_class` | INT | NOT NULL | ISO 14644-1 等级（1–9） |
| `fed_std_class` | VARCHAR(16) | | FED STD 209E 对应值（兼容老厂，如 `100`） |
| `particle_limit_json` | JSON | | 各粒径限值（`{"0.1":3520,"0.2":757,"0.3":309,"0.5":35.2,"5.0":0}`，单位：个/m³） |
| `temp_range` | VARCHAR(32) | | 典型温控范围（如 `22±0.5℃`） |
| `humidity_range` | VARCHAR(32) | | 典型湿度范围（如 `45±5%RH`） |
| `pressure_diff_pa` | DECIMAL(6,2) | | 相对外部的正压（Pa） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.2 `mds_env_monitor_point`（环境监控点）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 监控点编码（如 `EM-B03-P01`） |
| `name` | VARCHAR(128) | | 名称 |
| `location_id` | VARCHAR(32) | NOT NULL, FK→`mds_location.id` | 所在位置（BAY/SUBBAY 级） |
| `clean_class_code` | VARCHAR(32) | FK→`mds_clean_class.code` | 所在区域洁净等级（冗余便于筛选） |
| `monitor_type` | VARCHAR(16) | NOT NULL | `PARTICLE`/`TEMP`/`HUMIDITY`/`AMC`/`PRESSURE_DIFF`/`VIBRATION`/`ESD` |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | 监控参数（统一语义） |
| `alarm_low`/`alarm_high` | DECIMAL(18,6) | | 报警限值 |
| `unit` | VARCHAR(16) | FK→`mds_uom.code` | 单位 |
| `sampling_interval_sec` | INT | | 采样间隔（秒） |
| `linked_equipment_ids` | VARCHAR(512) | | 关联设备（可选：该测点异常直接影响哪些设备，逻辑引用） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 CL1–CL5）

### 4.1 洁净等级落为实体 + 位置字段（CL1）
- `mds_clean_class` 定义等级与粒子限值（ISO 14644-1 多粒径）；
- `mds_location.clean_class_code` 把等级**挂到 FAB/AREA/BAY**（BAY 最常用）；
- 使「**按洁净等级筛区域**」成为可查询条件（替代原先的文字描述）。

### 4.2 监控点建模（CL2）
- 监控点是**位置 × 参数**的组合；
- `monitor_type` 覆盖颗粒/温湿度/AMC/压差/振动/静电；
- `param_def_id` 统一语义，使环境数据与工艺参数**同源可比**。

### 4.3 环境超限的影响联动（CL3）
```
监控点报警（EMS）
   ↓ 经 location_id 上溯
受影响区域（BAY/AREA）→ 该区域内设备清单（equipment.location_id）
   ↓ 结合 MES 实时态
受影响在制品（该区域设备上的 lot）
   ↓
决策：暂停投料/转移 WIP/标记污染风险
```
- 与 [facility-utility-design §4.4](facility-utility-design.md) 的影响面推导**同构**；
- `linked_equipment_ids` 提供**快速直连**（不必遍历位置树）。

### 4.4 与约束/表达式联动（CL4）
- 新增 `CLEAN_CLASS_MATCH` 约束：表达「某类载具/人员不得进入某洁净等级区域」；
- `env.*` 变量可入表达式：`env['EM-B03-P01'].particle_count <= 352`；
- 载具 `clean_status`（[carrier-design §4.2](carrier-design.md)）与区域洁净等级协同准入。

### 4.5 与厂务、缺陷的关联（CL5）
- 温湿度/压差由 [facility-utility-design](facility-utility-design.md) 的 PCW/空调/排风支撑，二者**互相引用**（设施异常 → 环境劣化）；
- 环境污染事件可关联 [reason-defect-design](reason-defect-design.md) 的 `defect_class=PARTICLE`，形成「环境 → 缺陷 → 处置」链。

### 4.6 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 洁净等级/监控点实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 等级与监控点变更落 `{entity}_hist`（**等级变更影响历史环境合规判定，须留痕**） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移（含 `mds_location` 加列）+ 历史表 + ISO 等级种子（ISO 1–9） |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_clean_class (
  id                  VARCHAR(32) NOT NULL,
  tenant_id           VARCHAR(32) NOT NULL,
  code                VARCHAR(32) NOT NULL,
  name                VARCHAR(64),
  iso_class           INT NOT NULL,
  fed_std_class       VARCHAR(16),
  particle_limit_json JSON,
  temp_range          VARCHAR(32),
  humidity_range      VARCHAR(32),
  pressure_diff_pa    DECIMAL(6,2),
  status              VARCHAR(16) NOT NULL,
  description         VARCHAR(512),
  version_            BIGINT NOT NULL DEFAULT 0,
  deleted             BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_clean_class PRIMARY KEY (id),
  CONSTRAINT uq_mds_clean_class UNIQUE (code, tenant_id, deleted)
);

-- 位置扩展：洁净等级字段（迁移成对）
-- ALTER TABLE mds_location ADD COLUMN clean_class_code VARCHAR(32) NULL;

CREATE TABLE mds_env_monitor_point (
  id                  VARCHAR(32) NOT NULL,
  tenant_id           VARCHAR(32) NOT NULL,
  code                VARCHAR(64) NOT NULL,
  name                VARCHAR(128),
  location_id         VARCHAR(32) NOT NULL,
  clean_class_code    VARCHAR(32),
  monitor_type        VARCHAR(16) NOT NULL,
  param_def_id        VARCHAR(32),
  alarm_low           DECIMAL(18,6),
  alarm_high          DECIMAL(18,6),
  unit                VARCHAR(16),
  sampling_interval_sec INT,
  linked_equipment_ids VARCHAR(512),
  status              VARCHAR(16) NOT NULL,
  description         VARCHAR(512),
  version_            BIGINT NOT NULL DEFAULT 0,
  deleted             BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_env_point PRIMARY KEY (id),
  CONSTRAINT uq_mds_env_point UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_envpoint_loc FOREIGN KEY (location_id) REFERENCES mds_location (id),
  CONSTRAINT fk_envpoint_param FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id)
  -- clean_class_code: 跨域逻辑引用（服务层校验），不建 DB 级 FK —— 见 00-blueprint §11
);
CREATE INDEX idx_envpoint_loc ON mds_env_monitor_point (location_id, monitor_type);
```

> 历史表 `mds_clean_class_hist` / `mds_env_monitor_point_hist` 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.env
  ├── entity
  │   ├── CleanClass.java             # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── EnvMonitorPoint.java         # @Entity 继承 BaseDefData
  │   └── package-info.java            # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── CleanClassRepository.java
  │   └── EnvMonitorPointRepository.java
  ├── service
  │   ├── CleanClassService.java        # 继承 AbstractJpaService
  │   ├── EnvMonitorService.java         # 监控点维护 + 按区域/等级筛选
  │   └── EnvImpactService.java           # 环境超限影响面推导（同 facility）
  └── web
      └── EnvController.java            # 等级/监控点查询、影响面分析
```

---

## 8. 与 MES 生产执行衔接

| 场景 | MDS 提供 | MES/EMS 使用 |
| --- | --- | --- |
| **区域准入** | 位置洁净等级 + `CLEAN_CLASS_MATCH` 约束 + 载具 `clean_status` | MES 搬运/派工时校验「该载具/该批可否进入该区域」 |
| **环境超限联动** | 监控点 → 位置 → 设备清单（`linked_equipment_ids` / 位置上溯） | EMS 报警 → MES 暂停受影响区域投料、标记在制批风险 |
| **污染事件追溯** | 洁净等级 + 缺陷码关联 | 把环境污染事件与缺陷/良率关联分析 |
| **合规报表** | 等级定义 + 监控点限值 | 生成本区域洁净合规记录（ISO 14644 稽核） |
| **设备环境要求** | 洁净等级（设备所在区域的洁净要求） | 设备验收/移机时校验目标区域等级是否满足 |

> **对 MES 的关键价值**：把「洁净等级」从文字描述变成**可筛选、可校验、可联动**的数据，使污染控制从事后分析走向事前拦截。

---

## 9. 待补 / 后续

- **EMS 集成契约**：监控点实时值的读取方式（只读）；超限事件的订阅格式。
- **人员/物料准入**：是否需要「人员 × 洁净等级」的准入矩阵（可挂 [org-personnel-design](org-personnel-design.md) 的认证矩阵）。
- **AMC 细分**：AMC 需按酸/碱/有机/掺杂细分（可扩展 `monitor_type` 或参数）。
- **与既有文档闭环**：本文引用 [location-design](location-design.md)、[carrier-design §4.2](carrier-design.md)、[param-def-design](param-def-design.md)、[facility-utility-design](facility-utility-design.md)、[reason-defect-design](reason-defect-design.md)、[constraint-design](constraint-design.md)（新增 `CLEAN_CLASS_MATCH`）、[expression-dsl-design §5](expression-dsl-design.md)（`env.*`）、[realtime-contract-design §1](realtime-contract-design.md)、[naming-rule-design](naming-rule-design.md)。
