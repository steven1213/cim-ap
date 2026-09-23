# MDS 厂务设施与设备接入（Facility / Utility）主数据设计

> 本文承接[设备建模](equipment-design.md)（设备/模块）、[位置主数据](location-design.md)（落点）、[参数定义](param-def-design.md)（设施参数）、[约束设计](constraint-design.md)（可用性约束），
> 补全 MDS 的**厂务设施（Facility）与设备接入（Utility Connection）** 主数据。
>
> **补的缺口**：全库检索 `厂务|utility|UPW|N2|CDA|废气` **零命中**。此前设备模型只描述"设备本身"，**未描述"设备靠什么活着"**——电力、超纯水（UPW）、氮气（N2）、洁净压缩空气（CDA）、真空、冷却水（PCW）、特气/化学品供给、废气废液处理。
>
> 这直接影响 MES：**厂务中断时，MES 无法回答"哪些设备受影响、哪些在制品要处置"**。
>
> 设计原则延续全篇：**MDS 拥有设施定义与接入关系；厂务实时的可用性归 FMCS（厂务监控系统）**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么必须建（核心）

| 场景 | 不建的后果 |
| --- | --- |
| N2 总管压力异常 | 无法快速列出"依赖该总管的设备"，停产范围靠人工经验猜 |
| UPW 检修计划 | 无法提前识别受影响工序，无法预排生产 |
| 设备上电/上机 | 无法校验"该设备所需 utility 是否齐备"，只能靠人眼确认 |
| 扩产/改造 | 无法评估"新设备接入是否超出设施容量" |
| 工艺红线 | 无法表达「该工序必须在 N2 可用时执行」这类约束 |

> **结论（FU1）**：厂务是 MDS 的**独立主数据域**，其价值在于**接入关系（Connection）**——正是这张关系表让"设施异常 → 设备影响面"可以被**自动推导**。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有者 | 说明 |
| --- | --- | --- |
| 厂务设施定义（类型/容量/落点/关键性） | **MDS** | `mds_facility` |
| **设备/模块 ↔ 设施接入关系** | **MDS** | `mds_facility_connection`（核心） |
| 设施级参数限值（压力/流量/纯度） | **MDS** | 复用 [param-def-design](param-def-design.md) |
| 设施行政态（`RUNNING`/`MAINT`/`DOWN`） | **MDS**（FMCS 回写） | 缓慢变化，属白名单（[realtime-contract-design §2](realtime-contract-design.md)） |
| **设施实时参数值** | **FMCS** | 秒–分，MDS 不存 |
| **设施实时可用性（是否在供）** | **FMCS** | MDS 不存 |
| 厂务检修计划 | 厂务系统 | MDS 只存行政态 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 设施 ↔ 位置 | `mds_facility.location_id → mds_location.id` | [location-design §3](location-design.md) |
| 接入 ↔ 设备 | `mds_facility_connection.equipment_id → mds_equipment.id` | [equipment-design §3.3](equipment-design.md) |
| 接入 ↔ 设备模块/腔体 | `mds_facility_connection.module_ref → mds_equipment_module.code` | [equipment-design §3.4](equipment-design.md) |
| 接入点 ↔ 设备点表变量 | `mds_facility_connection.point_ref → mds_equip_if_variable.code` | [equipment-interface-design §3.2](equipment-interface-design.md) |
| 设施参数 ↔ 参数定义 | `mds_facility_param.param_def_id → mds_param_def.id` | [param-def-design §3.1](param-def-design.md) |
| 设施 ↔ 约束 | 新增约束类型 `FACILITY_AVAILABLE`（变量 `facility.available`） | [constraint-design §4.2](constraint-design.md) |
| 设施 ↔ 表达式变量 | `facility.*` 命名空间 | [expression-dsl-design §5](expression-dsl-design.md) |
| 设施 ↔ 保养 | 设施 PM（如 HEPA 更换） | [pm-calibration-design](pm-calibration-design.md) |
| 编码规则 | `entity_type=FACILITY`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E30 / E10** | 设备停机原因常为「**设施相关**」（Facility Related）；E10 的 DOWN 分类含外部原因 | `mds_facility_connection.is_critical` 支撑自动归因 |
| **SEMI E49 / E6（设施接口）** | 设备与厂务的接口（电力/气体/水/排气）有标准连接点定义 | `mds_facility_connection`（含 `connection_type`） |
| **fab 厂务实践（Facility/Utility Matrix）** | 通常维护一张「**设备 × 厂务**」矩阵（Utility Matrix），用于风控与应急 | 本域的**核心表** |
| **EHS / 安全合规** | 特气、化学品、废气废液属高风险；中断影响安全 | `facility_type` 区分 `SPECIAL_GAS`/`CHEMICAL_DELIVERY`/`WASTE_TREATMENT` |
| **BCP / 应急预案** | 厂务中断需有"影响设备清单 → 停线范围 → 恢复顺序" | §4.4 影响面推导 + §8 MES 衔接 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Facility / 厂务设施** | 支撑生产的**基础设施与公用系统** | 不是"设备"；设备是加工者，设施是供养者 |
| **Utility / 公用工程** | 设施提供的具体介质（电/水/气/真空/化学品） | 与 Facility 常混用；本文：Facility 是系统，Utility 是介质 |
| **Connection / 接入点** | 某设备/模块从某设施**取什么、要多少** | **不是**"管道拓扑"；是**依赖关系**（拓扑归厂务/设施系统） |
| **is_critical / 关键性** | 该接入中断是否**直接导致设备不可用** | 不是"设施关键性"（那是设施自身属性） |
| **backup_facility / 备用设施** | 主设施故障时可切换的备用源 | 表达冗余（N+1 供电、备用 N2 罐） |
| **admin_status / 行政态** | MDS 侧设施态：`RUNNING`/`MAINT`/`DOWN`/`STANDBY` | 与实时参数值正交 |

---

## 3. 实体总览与 ER

```
mds_location
      ▲ location_id
mds_facility (设施: ELECTRICAL / UPW / N2 / CDA / VACUUM / PCW / SPECIAL_GAS / CHEMICAL / EXHAUST / WASTE)
      │
      ├──< mds_facility_param     (设施参数限值 → param_def)
      └──< mds_facility_connection (设备/模块 ↔ 设施接入点)
                 equipment_id ──> mds_equipment
                 module_ref   ──> mds_equipment_module.code
                 point_ref    ──> mds_equip_if_variable.code
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 厂务设施 | `mds_facility` | 设施定义与落点 |
| 设施参数 | `mds_facility_param` | 参数限值（复用 param_def） |
| **设备接入点** | `mds_facility_connection` | **核心：设备 × 设施矩阵** |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_facility`（厂务设施）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 设施编码（如 `UT-N2-MAIN-F1`） |
| `name` | VARCHAR(128) | | 名称 |
| `facility_type` | VARCHAR(24) | NOT NULL | `ELECTRICAL`/`UPW`/`N2`/`CDA`/`VACUUM`/`PCW`（工艺冷却水）/`CHILLED_WATER`/`SPECIAL_GAS`/`CHEMICAL_DELIVERY`/`EXHAUST`/`WASTE_TREATMENT`/`STEAM` |
| `location_id` | VARCHAR(32) | FK→`mds_location.id` | 落点（复用位置层级；中央设施常落 SITE/FAB 级） |
| `delivery_mode` | VARCHAR(16) | | `CENTRAL`（中央供应）/`LOCAL`（本地）/`VMB`（阀箱）/`GAS_CABINET`（气瓶柜）/`SUB_MAIN` |
| `capacity` | DECIMAL(18,4) | | 额定容量/产能力 |
| `capacity_uom` | VARCHAR(16) | FK→`mds_uom.code` | 容量单位（如 `M3H`/`KWH`/`LPM`） |
| `redundancy_level` | VARCHAR(16) | | 冗余等级：`N`（无冗余）/`N+1`/`2N` |
| `is_critical` | BIT | DEFAULT 0 | 设施自身是否关键（中断影响面大） |
| `admin_status` | VARCHAR(16) | NOT NULL DEFAULT 'RUNNING' | `RUNNING`/`MAINT`/`DOWN`/`STANDBY`（FMCS 回写，[realtime-contract-design §2](realtime-contract-design.md)） |
| `owner_org` | VARCHAR(64) | FK→`mds_org_unit.code` | 责任部门（厂务部） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.2 `mds_facility_param`（设施参数限值）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `facility_id` | VARCHAR(32) | NOT NULL, FK→`mds_facility.id` | 设施 |
| `param_def_id` | VARCHAR(32) | NOT NULL, FK→`mds_param_def.id` | 参数（如 `PRESSURE`/`PURITY`/`FLOW`/`VOLTAGE`） |
| `target` | DECIMAL(18,6) | | 目标值 |
| `min_value`/`max_value` | DECIMAL(18,6) | | 允许范围 |
| `unit` | VARCHAR(16) | FK→`mds_uom.code` | 单位 |
| `spec_type` | VARCHAR(16) | | `CONTROL`/`ALARM`/`MONITOR` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(facility_id, param_def_id, tenant_id, deleted)`。

### 3.3 `mds_facility_connection`（设备接入点，核心）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `facility_id` | VARCHAR(32) | NOT NULL, FK→`mds_facility.id` | 设施 |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 设备 |
| `module_ref` | VARCHAR(64) | | 模块/腔体（`mds_equipment_module.code`；空=设备级） |
| `connection_type` | VARCHAR(16) | NOT NULL | `SUPPLY`（供给）/`RETURN`（回流）/`EXHAUST`（排放） |
| `required_qty` | DECIMAL(18,4) | | 需求数量（如 200 SLM N2） |
| `qty_uom` | VARCHAR(16) | FK→`mds_uom.code` | 单位 |
| `is_critical` | BIT | DEFAULT 1 | 该接入中断是否**直接导致设备不可用** |
| `backup_facility_id` | VARCHAR(32) | FK→`mds_facility.id` | 备用源 |
| `point_ref` | VARCHAR(64) | | 关联设备点表变量（该接入的就绪/流量信号，[equipment-interface-design](equipment-interface-design.md)） |
| `is_enabled` | BIT | DEFAULT 1 | 是否启用 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(facility_id, equipment_id, module_ref, connection_type, tenant_id, deleted)`。
- **这张表就是「设备 × 厂务矩阵」**——所有影响面推导都基于它。

---

## 4. 关键设计点（拍板 FU1–FU6）

### 4.1 独立域 + 接入关系为核心（FU1）
见 §0.1。设施本身信息量不大，**价值集中在 `mds_facility_connection`**。

### 4.2 设施类型枚举（FU2）
覆盖 fab 常见公用工程，且区分「供给类」（`ELECTRICAL`/`UPW`/`N2`/`CDA`/`VACUUM`/`PCW`/`CHILLED_WATER`/`SPECIAL_GAS`/`CHEMICAL_DELIVERY`）与「排放类」（`EXHAUST`/`WASTE_TREATMENT`）——**排放类同样关键**（排风故障会导致设备停机）。

### 4.3 接入粒度：设备级与模块级（FU3）
- **设备级**（`module_ref` 为空）：整机依赖（如整机电力、CDA）；
- **模块级**：单腔体依赖（如某腔体专用特气）——**使影响面更精准**（不必因一个腔体的气体问题停整机）；
- `connection_type` 区分供/回/排，避免"只统计供给"而漏掉排放依赖。

### 4.4 影响面推导（FU4，核心能力）
```
输入：某设施（或某总管的某区段）
  ↓ 查 mds_facility_connection (facility_id = X AND is_critical = 1)
输出：受影响设备清单 → 受影响模块清单 → 受影响工序（经 operation.equipment_class）
  ↓ 结合 MES 实时态
输出：受影响在制品（当前在该设备/该工序的 lot）
  ↓ 结合 mds_route_flow / Q-TIME 约束
输出：是否触发 Q-Time 超时风险（如在制批等待该设备恢复）
```
- 与 [change-mgmt-design §4.3](change-mgmt-design.md) 的影响分析**同构**（都是依赖图遍历），共用 `ImpactAnalyzer` 思路。
- 备用源（`backup_facility_id`）存在时，标记为 `MITIGATED`（可切换），否则 `BLOCKED`。

### 4.5 与约束/表达式联动（FU5）
- 新增约束类型 `FACILITY_AVAILABLE`：表达式可引用 `facility.available`、`facility.admin_status`；
- 例：`facility['UT-N2-MAIN-F1'].available == true` 作为派工前置条件（BLOCK 级）；
- 使"厂务不满足 → 不派工"成为**声明式规则**而非硬编码。

### 4.6 与保养/安全联动（FU6）
- 设施自身 PM（HEPA 更换、水质再生）归 [pm-calibration-design](pm-calibration-design.md)（`scope_type` 可扩展 `FACILITY`）；
- 特气/化学品设施关联 [material-design](material-design.md) 的 `hazard_class` 与 [document-design](document-design.md) 的应急预案文档。

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 厂务实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 设施与接入关系变更落 `{entity}_hist`（**接入关系变更影响影响面推导，必须留痕**） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + 常见设施类型种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_facility (
  id               VARCHAR(32) NOT NULL,
  tenant_id        VARCHAR(32) NOT NULL,
  code             VARCHAR(64) NOT NULL,
  name             VARCHAR(128),
  facility_type    VARCHAR(24) NOT NULL,
  location_id      VARCHAR(32),
  delivery_mode    VARCHAR(16),
  capacity         DECIMAL(18,4),
  capacity_uom     VARCHAR(16),
  redundancy_level VARCHAR(16),
  is_critical      BIT DEFAULT 0,
  admin_status     VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
  owner_org        VARCHAR(64),
  status           VARCHAR(16) NOT NULL,
  description      VARCHAR(512),
  version_         BIGINT NOT NULL DEFAULT 0,
  deleted          BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_facility PRIMARY KEY (id),
  CONSTRAINT uq_mds_facility UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_facility_loc FOREIGN KEY (location_id) REFERENCES mds_location (id)
);
CREATE INDEX idx_facility_type ON mds_facility (facility_type, admin_status);

CREATE TABLE mds_facility_param (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  facility_id  VARCHAR(32) NOT NULL,
  param_def_id VARCHAR(32) NOT NULL,
  target       DECIMAL(18,6),
  min_value    DECIMAL(18,6),
  max_value    DECIMAL(18,6),
  unit         VARCHAR(16),
  spec_type    VARCHAR(16),
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_fac_param PRIMARY KEY (id),
  CONSTRAINT uq_mds_fac_param UNIQUE (facility_id, param_def_id, tenant_id, deleted),
  CONSTRAINT fk_facparam_fac FOREIGN KEY (facility_id) REFERENCES mds_facility (id),
  CONSTRAINT fk_facparam_def FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id)
);

CREATE TABLE mds_facility_connection (
  id                 VARCHAR(32) NOT NULL,
  tenant_id          VARCHAR(32) NOT NULL,
  facility_id        VARCHAR(32) NOT NULL,
  equipment_id       VARCHAR(32) NOT NULL,
  module_ref         VARCHAR(64),
  connection_type    VARCHAR(16) NOT NULL,
  required_qty       DECIMAL(18,4),
  qty_uom            VARCHAR(16),
  is_critical        BIT DEFAULT 1,
  backup_facility_id VARCHAR(32),
  point_ref          VARCHAR(64),
  is_enabled         BIT DEFAULT 1,
  description        VARCHAR(512),
  version_           BIGINT NOT NULL DEFAULT 0,
  deleted            BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_fac_conn PRIMARY KEY (id),
  CONSTRAINT uq_mds_fac_conn UNIQUE (facility_id, equipment_id, module_ref, connection_type, tenant_id, deleted),
  CONSTRAINT fk_facconn_fac FOREIGN KEY (facility_id) REFERENCES mds_facility (id),
  CONSTRAINT fk_facconn_eq FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id),
  CONSTRAINT fk_facconn_backup FOREIGN KEY (backup_facility_id) REFERENCES mds_facility (id)
);
CREATE INDEX idx_facconn_fac  ON mds_facility_connection (facility_id, is_critical);
CREATE INDEX idx_facconn_eq   ON mds_facility_connection (equipment_id);
```

> 历史表 `mds_facility_hist` / `mds_facility_connection_hist` 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.facility
  ├── entity
  │   ├── Facility.java               # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── FacilityParam.java           # @Entity 继承 BaseDefData
  │   ├── FacilityConnection.java       # @Entity 继承 BaseDefData
  │   └── package-info.java            # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── FacilityRepository.java
  │   └── FacilityConnectionRepository.java
  ├── service
  │   ├── FacilityService.java           # 继承 AbstractJpaService；设施与接入维护
  │   ├── FacilityImpactService.java      # 影响面推导（设施 down → 设备/模块/工序清单）
  │   └── FacilityValidator.java           # 容量超配校验、备用源环路、类型与设施参数一致
  └── web
      └── FacilityController.java         # 设施/接入矩阵查询/影响面分析
```

---

## 8. 与 MES 生产执行衔接

| 场景 | MDS 提供 | MES / FMCS 使用 |
| --- | --- | --- |
| **设备上电/上机前校验** | `mds_facility_connection`（该设备必备 utility 清单） | MES 校验「必备 utility 是否就绪」（实时由 FMCS/点表提供）→ 未就绪则不派工/不投料 |
| **厂务异常（如 N2 中断）** | 影响面推导（设施 → `is_critical=1` 的设备/模块清单） | FMCS 报警 → MES 调用/订阅影响面 → **自动停派受影响设备**、标记在制批待处置 |
| **厂务计划检修** | 设施 `admin_status=MAINT` + 影响清单 | MES 提前排产避开、调整 WIP 流向 |
| **派工约束** | `FACILITY_AVAILABLE` 约束 + `facility.*` 表达式变量 | MES 本地求值：`facility.available == true` 不满足则 BLOCK |
| **在制品风险** | 受影响工序 + Q-Time 约束 | MES 判断「等设备恢复是否会让 Q-Time 超时」→ 提前决策（转机/返工/留批） |
| **停机归因** | 设施类型 + 接入关系 | 停机原因码可自动归到 `FACILITY` 类（[reason-defect-design](reason-defect-design.md) `e10_category`）→ **OEE 的"外部原因"可量化** |
| **扩产评估** | 设施容量 + 接入需求 | 新增设备前评估容量是否够（离线分析，非生产路径） |

> **对 MES 的关键价值**：把「厂务异常影响面」从**人工经验**变成**可查询的依赖关系**，并让"厂务不就绪不派工"成为**声明式约束**。

---

## 9. 待补 / 后续

- **与 FMCS 的接口**：设施实时可用性与参数的读取契约（只读）；`admin_status` 的回写通道。
- **管道拓扑**：本域只建**依赖关系**，不做管道物理拓扑（归厂务 P&ID 系统）；如需分段隔离（某支管检修），可扩展 `mds_facility` 为树形（`parent_facility_id`）。
- **容量核算服务**：按 `connection.required_qty` 汇总 vs `facility.capacity`，做超配预警。
- **与既有文档闭环**：本文引用 [location-design](location-design.md)、[equipment-design](equipment-design.md)、[equipment-interface-design](equipment-interface-design.md)、[param-def-design](param-def-design.md)、[constraint-design](constraint-design.md)（新增 `FACILITY_AVAILABLE`）、[expression-dsl-design §5](expression-dsl-design.md)（`facility.*`）、[realtime-contract-design §1](realtime-contract-design.md)（第 13/17 行）、[reason-defect-design](reason-defect-design.md)、[naming-rule-design](naming-rule-design.md)。
