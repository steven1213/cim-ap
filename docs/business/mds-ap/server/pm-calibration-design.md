# MDS 保养与校准（PM / Calibration）主数据设计

> 本文承接[设备建模](equipment-design.md)（`mds_equipment` / `mds_equipment_module` / 状态机 §3.14）、[约束设计](constraint-design.md)（`PM_INTERVAL` 约束），
> 补全 MDS 的**预防性保养（PM）与校准（Calibration）** 主数据。
>
> 它同时**补上设备设计中一个悬空点**：`lifecycle_status` / `admin_status` 何时由 `ACTIVE`/`ENABLED` 变为 `MAINTENANCE`？**触发源就是本域**。
>
> 设计原则延续全篇：**MDS 拥有保养/校准的「计划定义 + 到期策略 + 标准项」；执行工单与实绩归 MES / 维护系统**。

---

## 0. 定位与边界（拍板）

### 0.1 当前缺口与本文补位（核心）

[equipment-design §3.10](equipment-design.md) 定义了状态分层，§3.14 定义了状态**迁移边**，但**没有人定义"什么时候该迁移"**：

| 悬空问题 | 本文补位 |
| --- | --- |
| 设备 `lifecycle_status` 何时进入 `MAINTENANCE`？ | `mds_pm_plan` 到期 → 触发 |
| 模块 `admin_status` 何时变 `MAINTENANCE`？ | 模块级 PM 计划到期 → 触发 |
| `qualification_state` 何时失效？ | 校准到期 / 再验证到期 → 触发 |
| 保养要做什么？做多久？ | `mds_pm_task` 标准项（时长/资质/顺序） |
| 校准的公差是多少？ | `mds_calibration_spec` + `_item` |

> **结论（PM1）**：PM/校准是 MDS 的**独立主数据域**，是设备状态机的**触发源**，也是 [constraint-design](constraint-design.md) 中 `PM_INTERVAL` 约束类型的落地承载。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| PM 计划定义（周期/触发条件/到期动作） | **MDS** | `mds_pm_plan`，本文范围 |
| PM 标准任务项（检查/更换/清洁/校准） | **MDS** | `mds_pm_task` + `mds_pm_plan_task` |
| 校准规格与公差 | **MDS** | `mds_calibration_spec` / `mds_calibration_item` |
| 到期策略与预警比例 | **MDS** | 计划字段 |
| **PM 工单的创建、派工、执行、签核** | **MES / 维护系统（CMMS）** | MDS 不存工单 |
| **累计运行时长 / 晶圆数 / 循环数** | **EAP / MES** | 实时计数，驱动到期判定 |
| **保养实绩（谁做的、做了什么、换件清单）** | **CMMS / MES** | 记录归维护系统 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 计划 ↔ 设备型号 | `mds_pm_plan.equipment_model_id → mds_equipment_model.id` | [equipment-design §3.2](equipment-design.md) |
| 计划 ↔ 设备实例 | `mds_pm_plan.equipment_id → mds_equipment.id` | [equipment-design §3.3](equipment-design.md) |
| 计划 ↔ 模块/腔体 | `mds_pm_plan.module_ref → mds_equipment_module.code` | [equipment-design §3.4](equipment-design.md) |
| 任务项 ↔ 备件/耗材 | `mds_pm_task.material_ref → mds_material.code` | [material-design §3.2](material-design.md) |
| 校准 ↔ 设备参数点 | `mds_calibration_spec.variable_ref → mds_equip_if_variable.code` | [equipment-interface-design §3.2](equipment-interface-design.md) |
| 到期 → 状态迁移 | 触发 `mds_equipment_state_transition` 的维护相关边 | [equipment-design §3.14](equipment-design.md) |
| 到期 → 约束 | `PM_INTERVAL` 约束类型 | [constraint-design §4.2](constraint-design.md) |
| 保养原因码 | `reason_type=DOWNTIME` 的 `mds_reason_code` | [reason-defect-design](reason-defect-design.md) |
| 执行记录归档 | 受控记录/签核 | [md-governance-design](md-governance-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E10** | RAM 分析中 PM 归 **Scheduled Down**（`SDT`/`SDL`/`SDN`）；PM 时长与频次直接影响设备可用率 | 到期动作触发 E10 计划停机（§4.4） |
| **SEMI E79 / E116（设备性能）** | 设备效率与可用性指标依赖 PM 计划的规范化 | PM 计划与 `mds_equipment.lifecycle_status` 联动 |
| **fab PM 实践** | PM 按**多触发维度**（日历 / 运行小时 / 晶圆数 / 循环数）取先到；PM 有**预警提前量**（lead time）与**标准工时**（影响排产） | `interval_type` 四类 + `lead_time_days` + `duration_min` |
| **计量与校准（Metrology）** | 关键量测参数须定期校准，超差则设备/量测不可用于生产（`qualification_state` 失效） | `mds_calibration_spec` + 到期触发资格失效 |
| **CMMS / ERP 集成** | 保养工单在 CMMS 执行；MES 需要"设备是否可用/何时可 PM"的排程输入 | MDS 出计划与窗口，工单归 CMMS |
| **ISO 9001 / IATF 16949** | 设备维护与校准记录受控、可追溯 | 计划与标准项受 [md-governance-design](md-governance-design.md) 治理 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **PM / Preventive Maintenance** | **预防性**保养（按计划主动做） | 区别于 CM（纠错性维修，被动）、BM（预测性，按状态） |
| **PM Plan / 保养计划** | 「多久做一次、谁触发、到期做什么」 | 不是工单；工单是实例，计划是定义 |
| **PM Task / 保养任务项** | 标准作业项（检查/清洁/更换/校准），含标准工时与资质要求 | 不是"任务实例" |
| **Calibration / 校准** | 用标准器验证/修正设备参数，带**公差** | 与"检定"（合规性判定）略有差别；本文合并处理 |
| **Calibration Spec / 校准规格** | 校准对象、目标值、公差、标准器、周期 | 与 `mds_process_flow_step_param`（工艺窗口）不同：这是**计量精度**要求 |
| **到期动作 on_due_action** | 到期后自动做什么（置维护态 / 仅告警 / 阻断派工） | 实际执行在下游，MDS 只声明 |
| **lead_time / 提前量** | 提前多久预警（便于排 PM 窗口） | 不是"宽限期"；宽限是 `grace_period` |

---

## 3. 实体总览与 ER

```
mds_pm_plan (计划: scope=MODEL|EQUIPMENT|MODULE, interval_type/interval_value)
   ├── equipment_model_id ──> mds_equipment_model
   ├── equipment_id       ──> mds_equipment
   ├── module_ref         ──> mds_equipment_module.code
   └──< mds_pm_plan_task (计划 ↔ 任务, 有序)
             └── mds_pm_task (标准任务项: 类型/工时/资质/备件)

mds_calibration_spec (校准规格: 参数/公差/标准器/周期)
   └──< mds_calibration_item (校准点: value + tolerance)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 保养计划 | `mds_pm_plan` | 周期/触发/到期动作 |
| 保养任务项 | `mds_pm_task` | 标准作业项 |
| 计划↔任务 | `mds_pm_plan_task` | 有序组合 |
| 校准规格 | `mds_calibration_spec` | 校准对象/公差/周期 |
| 校准点 | `mds_calibration_item` | 逐点公差 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_pm_plan`（保养计划）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 计划代码（如 `PM-SCN-CLEAN-500H`） |
| `name` | VARCHAR(128) | | 计划名 |
| `scope_type` | VARCHAR(16) | NOT NULL | `MODEL`（型号级）/`EQUIPMENT`（实例级）/`MODULE`（腔体级） |
| `equipment_model_id` | VARCHAR(32) | FK→`mds_equipment_model.id` | `scope_type=MODEL` 时非空 |
| `equipment_id` | VARCHAR(32) | FK→`mds_equipment.id` | `scope_type=EQUIPMENT` 时非空 |
| `module_ref` | VARCHAR(64) | | 模块/腔体标识（`scope_type=MODULE`） |
| `pm_category` | VARCHAR(16) | NOT NULL | `PM1`（短周）/`PM2`（中周）/`PM3`（大修）/`CLEAN`/`INSPECT` |
| `interval_type` | VARCHAR(16) | NOT NULL | `CALENDAR`（日历天）/`RUN_HOURS`（运行小时）/`WAFER_COUNT`（晶圆数）/`CYCLE_COUNT`（循环数） |
| `interval_value` | DECIMAL(12,2) | NOT NULL | 周期值 |
| `lead_time_hours` | INT | | 提前预警小时（排 PM 窗口） |
| `warning_ratio` | DECIMAL(4,2) | | 预警比例（0.9=达 90% 预警） |
| `grace_period_hours` | INT | | 超期宽限（超过才强制） |
| `duration_min` | INT | | 标准工时（分钟），排产用 |
| `on_due_action` | VARCHAR(24) | NOT NULL | `HOLD`（置维护态）/`WARN`（仅告警）/`BLOCK_DISPATCH`（阻断派工）/`NOTIFY` |
| `requires_shutdown` | BIT | DEFAULT 1 | 是否须停机 |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `revision` | VARCHAR(16) | NOT NULL | 版本 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.2 `mds_pm_task`（保养任务项）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 任务代码 |
| `name` | VARCHAR(128) | | 任务名 |
| `task_type` | VARCHAR(16) | NOT NULL | `INSPECT`/`CLEAN`/`REPLACE`/`CALIBRATE`/`TEST`/`LUBRICATE`/`OTHER` |
| `standard_duration_min` | INT | | 标准工时 |
| `material_ref` | VARCHAR(64) | FK→`mds_material.code` | 需更换的备件/耗材（[material-design](material-design.md)） |
| `standard_qty` | DECIMAL(12,4) | | 用量 |
| `requires_qualification` | VARCHAR(64) | | 执行所需资质（[org-personnel-design](org-personnel-design.md)） |
| `requires_equipment_down` | BIT | DEFAULT 1 | 是否需设备停机 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.3 `mds_pm_plan_task`（计划 ↔ 任务）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `plan_id` | VARCHAR(32) | NOT NULL, FK→`mds_pm_plan.id` | 计划 |
| `task_id` | VARCHAR(32) | NOT NULL, FK→`mds_pm_task.id` | 任务项 |
| `seq` | INT | NOT NULL | 执行顺序 |
| `is_mandatory` | BIT | DEFAULT 1 | 是否必做 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(plan_id, task_id, tenant_id, deleted)`。

### 3.4 `mds_calibration_spec`（校准规格）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 规格代码 |
| `name` | VARCHAR(128) | | 名称 |
| `equipment_model_id` | VARCHAR(32) | FK→`mds_equipment_model.id` | 适用型号 |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | **被校准的参数（统一语义）**，见 [param-def-design](param-def-design.md) |
| `variable_ref` | VARCHAR(32) | FK→`mds_equip_if_variable.code` | 对应的设备变量（[equipment-interface-design](equipment-interface-design.md)） |
| `calibration_type` | VARCHAR(16) | NOT NULL | `VERIFY`（只验）/`ADJUST`（校验并调整） |
| `standard_instrument` | VARCHAR(128) | | 标准器（可追溯性） |
| `interval_days` | INT | NOT NULL | 校准周期 |
| `tolerance_type` | VARCHAR(16) | | `ABSOLUTE`/`RELATIVE`（相对误差%） |
| `on_fail_action` | VARCHAR(16) | NOT NULL | `HOLD`（停用）/`READJUST`/`NOTIFY` |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.5 `mds_calibration_item`（校准点）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `spec_id` | VARCHAR(32) | NOT NULL, FK→`mds_calibration_spec.id` | 所属规格 |
| `seq` | INT | NOT NULL | 校准点序号 |
| `set_value` | DECIMAL(14,6) | NOT NULL | 设定值（加给设备的激励） |
| `tolerance_low` | DECIMAL(14,6) | | 允许下限 |
| `tolerance_high` | DECIMAL(14,6) | | 允许上限 |
| `unit` | VARCHAR(16) | | 单位 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(spec_id, seq, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 PM1–PM6）

### 4.1 三层作用域（PM2）
- `MODEL`：同型号通用计划（复用性最高，改一处全型号生效）；
- `EQUIPMENT`：单台设备特有（老旧设备加频）；
- `MODULE`：腔体级（如某腔体单独清洁周期更短）。
- 解析优先级：**MODULE > EQUIPMENT > MODEL**（越具体越优先），完全类比 [constraint-design §4.4](constraint-design.md) 的 specificity 解析。

### 4.2 多触发维度取先到（PM3）
- 四类：`CALENDAR` / `RUN_HOURS` / `WAFER_COUNT` / `CYCLE_COUNT`；
- 同一设备可能有多个计划（PM1 按周、PM3 按晶圆数），**任一到期即触发**；
- 计数来源：`RUN_HOURS`/`WAFER_COUNT`/`CYCLE_COUNT` 由 [equipment-interface-design](equipment-interface-design.md) 的 DV/计数器变量提供（**实时值在 EAP**）。

### 4.3 到期动作与状态机联动（PM4，补悬空点）
```
[MES/EAP] 监测累计值
      │ 达到 (interval_value × warning_ratio)   → WARN 预警（提前 lead_time_hours 排窗口）
      │ 达到 interval_value                      → 触发 on_due_action
      │ 超过 interval_value + grace_period_hours → 强制动作
      ▼
[MDS 定义]  on_due_action:
      HOLD            → 设备 lifecycle_status=MAINTENANCE / 模块 admin_status=MAINTENANCE
      BLOCK_DISPATCH  → 约束层拦截派工（PM_INTERVAL 约束，BLOCK）
      WARN / NOTIFY    → 仅通知
```
- 状态迁移本身仍走 [equipment-design §3.14](equipment-design.md) 的迁移边校验（由 `EquipmentService.transitionLifecycle` 执行）；
- **本域只定义"何时该迁"，不执行迁移**——保持职责单一。

### 4.4 校准与资格失效（PM5）
- 校准周期到期未做 → 触发 `mds_equipment.qualification_state` 失效（或置 `HOLD`）；
- 校准**超差**（实测超出 `tolerance_low/high`）→ 按 `on_fail_action` 处理（停用/再调整/通知）；
- 校准结果与设备是否可用于生产直接挂钩（同 recipe/reticle 资格思路）。

### 4.5 与 CMMS / MES 的分工（PM6）
- MDS 出**计划与标准项**（what/when/how long/what parts）；
- CMMS 出**工单与执行记录**（who/when actually done/实绩）；
- MES 出**排程窗口**（设备何时可停）；
- MDS 提供 `nextDueAt(equipmentId)` 计算服务，供 MES/CMMS 查询。

### 4.6 与约束层的关系（PM6 续）
- `PM_INTERVAL` 约束类型（[constraint-design §4.2](constraint-design.md)）指向本域计划，表达「超期不得派工」；
- 避免 EAP 硬编码"超期就停"，由约束声明 + 下游执行。

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | PM/校准实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 计划/规格变更落 `{entity}_hist`（周期变更影响维护合规，必须留痕） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + PM1/PM2/PM3 标准模板种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_pm_plan (
  id                 VARCHAR(32) NOT NULL,
  tenant_id          VARCHAR(32) NOT NULL,
  code               VARCHAR(64) NOT NULL,
  name               VARCHAR(128),
  scope_type         VARCHAR(16) NOT NULL,
  equipment_model_id VARCHAR(32),
  equipment_id       VARCHAR(32),
  module_ref         VARCHAR(64),
  pm_category        VARCHAR(16) NOT NULL,
  interval_type      VARCHAR(16) NOT NULL,
  interval_value     DECIMAL(12,2) NOT NULL,
  lead_time_hours    INT,
  warning_ratio      DECIMAL(4,2),
  grace_period_hours INT,
  duration_min       INT,
  on_due_action      VARCHAR(24) NOT NULL,
  requires_shutdown  BIT DEFAULT 1,
  status             VARCHAR(16) NOT NULL,
  revision            VARCHAR(16) NOT NULL,
  description        VARCHAR(512),
  version_           BIGINT NOT NULL DEFAULT 0,
  deleted            BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pm_plan PRIMARY KEY (id),
  CONSTRAINT uq_mds_pm_plan UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_pmplan_model FOREIGN KEY (equipment_model_id) REFERENCES mds_equipment_model (id),
  CONSTRAINT fk_pmplan_eq FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id)
);
CREATE INDEX idx_pmplan_eq ON mds_pm_plan (equipment_id);

CREATE TABLE mds_pm_task (
  id                      VARCHAR(32) NOT NULL,
  tenant_id               VARCHAR(32) NOT NULL,
  code                    VARCHAR(64) NOT NULL,
  name                    VARCHAR(128),
  task_type               VARCHAR(16) NOT NULL,
  standard_duration_min   INT,
  material_ref            VARCHAR(64),
  standard_qty            DECIMAL(12,4),
  requires_qualification  VARCHAR(64),
  requires_equipment_down BIT DEFAULT 1,
  description             VARCHAR(512),
  version_                BIGINT NOT NULL DEFAULT 0,
  deleted                 BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pm_task PRIMARY KEY (id),
  CONSTRAINT uq_mds_pm_task UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_pm_plan_task (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  plan_id      VARCHAR(32) NOT NULL,
  task_id      VARCHAR(32) NOT NULL,
  seq          INT NOT NULL,
  is_mandatory BIT DEFAULT 1,
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pm_plan_task PRIMARY KEY (id),
  CONSTRAINT uq_mds_pm_plan_task UNIQUE (plan_id, task_id, tenant_id, deleted),
  CONSTRAINT fk_pmpt_plan FOREIGN KEY (plan_id) REFERENCES mds_pm_plan (id),
  CONSTRAINT fk_pmpt_task FOREIGN KEY (task_id) REFERENCES mds_pm_task (id)
);

CREATE TABLE mds_calibration_spec (
  id                 VARCHAR(32) NOT NULL,
  tenant_id          VARCHAR(32) NOT NULL,
  code               VARCHAR(64) NOT NULL,
  name               VARCHAR(128),
  equipment_model_id VARCHAR(32),
  param_def_id       VARCHAR(32),
  variable_ref       VARCHAR(32),
  calibration_type   VARCHAR(16) NOT NULL,
  standard_instrument VARCHAR(128),
  interval_days      INT NOT NULL,
  tolerance_type     VARCHAR(16),
  on_fail_action     VARCHAR(16) NOT NULL,
  status             VARCHAR(16) NOT NULL,
  description        VARCHAR(512),
  version_           BIGINT NOT NULL DEFAULT 0,
  deleted            BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_cal_spec PRIMARY KEY (id),
  CONSTRAINT uq_mds_cal_spec UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_calspec_model FOREIGN KEY (equipment_model_id) REFERENCES mds_equipment_model (id),
  CONSTRAINT fk_calspec_param_def FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id)
);

CREATE TABLE mds_calibration_item (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  spec_id        VARCHAR(32) NOT NULL,
  seq            INT NOT NULL,
  set_value      DECIMAL(14,6) NOT NULL,
  tolerance_low  DECIMAL(14,6),
  tolerance_high DECIMAL(14,6),
  unit           VARCHAR(16),
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_cal_item PRIMARY KEY (id),
  CONSTRAINT uq_mds_cal_item UNIQUE (spec_id, seq, tenant_id, deleted),
  CONSTRAINT fk_calitem_spec FOREIGN KEY (spec_id) REFERENCES mds_calibration_spec (id)
);
```

> 历史表 `mds_pm_plan_hist` / `mds_pm_task_hist` / `mds_calibration_spec_hist` / … 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.pm
  ├── entity
  │   ├── PmPlan.java              # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── PmTask.java               # @Entity 继承 BaseDefData
  │   ├── PmPlanTask.java           # @Entity 继承 BaseDefData
  │   ├── CalibrationSpec.java      # @Entity 继承 BaseDefData
  │   ├── CalibrationItem.java      # @Entity 继承 BaseDefData
  │   └── package-info.java         # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── PmPlanRepository.java
  │   └── CalibrationSpecRepository.java
  ├── service
  │   ├── PmPlanService.java         # 继承 AbstractJpaService；计划解析（MODULE>EQUIPMENT>MODEL）
  │   ├── PmDueService.java           # nextDueAt(equipmentId) / 到期评估（供 MES/CMMS 查询）
  │   └── PmValidator.java            # 周期>0、scope 字段一致性、校准公差上下限校验
  └── web
      └── PmController.java           # 查询/计划发布/到期台账/校准规格
```

---

## 8. 待补 / 后续

- **计数回写与到期评估**：EAP/MES 上报 `RUN_HOURS`/`WAFER_COUNT`；MDS 只提供 `nextDueAt()` 计算，不存计数。
- **PM 窗口与排程**：与 MES 排程联动（何时可停、停多久），供产能损失预测。
- **Calibration 与 qualification_state 的自动失效规则**：超期/超差 → 资格失效的具体策略（可配为约束）。
- **与既有文档闭环**：本文引用 `mds_equipment` / `mds_equipment_model` / `mds_equipment_module` / `mds_equipment_state_transition`（[equipment-design](equipment-design.md)）、`mds_material`（[material-design](material-design.md)）、`mds_equip_if_variable`（[equipment-interface-design](equipment-interface-design.md)）、`mds_reason_code`（[reason-defect-design](reason-defect-design.md)）、`PM_INTERVAL` 约束（[constraint-design](constraint-design.md)）、执行记录治理（[md-governance-design](md-governance-design.md)）。

---

## 附录 B. 计量标准器台账与溯源链（第三轮补强）

> **补的合规缺口**：§3.4 的 `standard_instrument` 只是**一个文本字段**，**没有标准器台账、没有校准溯源码**——无法回答"这台设备的校准量值追溯到哪一级标准、该标准是否在有效期内"。**ISO 17025 / IATF 16949 对量测溯源有明确要求**，当前不足以过审。

### B.1 `mds_standard_instrument`（标准器台账）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | 标准器编号（如 `STD-DMM-001`） |
| `name` | VARCHAR(128) | | 名称 |
| `instrument_type` | VARCHAR(24) | NOT NULL | `MULTIMETER`/`PRESSURE_GAUGE`/`THERMOMETER`/`MASS_FLOW`/`TEMPERATURE_STANDARD`/`REFERENCE_WAFER`/`TIMING_STANDARD`/`OTHER` |
| `serial_no` | VARCHAR(64) | | 序列号 |
| `manufacturer`/`model` | VARCHAR(128) | | 厂商 / 型号 |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | 所溯源的参数（统一语义，[param-def-design](param-def-design.md)） |
| `range_low`/`range_high` | DECIMAL(18,6) | | 量程 |
| `unit` | VARCHAR(16) | FK→`mds_uom.code` | 单位 |
| `uncertainty` | VARCHAR(64) | | 扩展测量不确定度（含 k 值说明，如 `±0.01% (k=2)`） |
| `accuracy_class` | VARCHAR(16) | | 准确度等级 |
| `calibration_org` | VARCHAR(64) | | 校准机构（内部或外部；外部引用 `mds_vendor`，`vendor_type=CALIBRATION`） |
| `traceability_chain` | VARCHAR(128) | | **溯源链**：上一级标准器 `code` 或外部机构**证书号** |
| `last_calibration_at` | DATETIME(3) | | 上次校准时间 |
| `next_calibration_due` | DATETIME(3) | | 下次校准到期 |
| `certificate_ref` | VARCHAR(256) | | 校准证书引用（DMS） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`HOLD`/`RETIRED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### B.2 溯源链校验（`MetrologyValidator`）

1. **不成环**：沿 `traceability_chain` 上溯不得形成环路；
2. **上级有效**：上级标准器的 `next_calibration_due` 未过期，否则判定**溯源中断**（下级量测不可信）；
3. **不确定度逐级递增**：下级标准器的 `uncertainty` 不得优于上级（物理上不可能）；
4. **顶链可终点**：允许终到外部机构证书号（无需在库内）。

> 形成完整证据链：**设备参数 ← 校准（`mds_calibration_spec`）← 标准器 ← 上级标准器 ← 国家/国际基准**。

### B.3 与既有的闭环

| 关联 | 说明 |
| --- | --- |
| `mds_calibration_spec` 新增 `standard_instrument_ref → mds_standard_instrument.code` | 原 `standard_instrument` 文本列**保留兼容**，逐步迁移为引用 |
| `param_def_id` | 与 [param-def-design](param-def-design.md) 统一语义 |
| 校准机构 | 复用 [org-personnel-design §3.2](org-personnel-design.md) 的 `mds_vendor`（`vendor_type=CALIBRATION`） |
| 到期驱动 | 复用本文 §4.3/§4.4 的到期与资格失效机制 |
| 证书正文 | 复用 [document-design](document-design.md)（`doc_ref` 范式） |

### B.4 与 MES 生产执行衔接

| 场景 | MDS 提供 | MES / 量测系统使用 |
| --- | --- | --- |
| **量测可信度门禁** | 标准器有效期 + 溯源链状态 | MES 在量测工序前校验「该量测设备的校准所用标准器在有效期内」；不足则**量测结果不得用于放行** |
| **到期联动** | 标准器 `next_calibration_due` | 标准器过期 → 其校准的下级设备 `qualification_state` 降级（复用 §4.4） |
| **稽核证据** | 溯源链 | 一键导出「设备→校准→标准器→上级→基准」链作为 ISO 17025 证据 |
| **不确定度** | `uncertainty` | 用于测量系统分析（MSA / Gage R&R）判定量测能力是否满足规格 |
