# MDS 设备接口点表（Equipment Interface / GEM Data Model）主数据设计

> 本文承接[设备建模](equipment-design.md)（`mds_equipment_model` / `mds_equipment`），
> 补全 MDS 的**设备接口点表**——按设备**型号**定义的 SECS/GEM 通信数据字典（SV / DV / EC / CEID / ALID / RPTID）。
> 它是 **EAP 落地的前置条件**：EAP 不是给每台设备手写一套采集代码，而是按型号加载 MDS 的点表定义。
>
> 设计原则延续全篇：**MDS 拥有「型号 → 点表定义」；EAP 拥有「实例连接状态与实际值」**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么设备接口点表属于 MDS（核心）

fab 里 EAP 要为**同型号的几十台设备**做数据采集。如果点表散落在 EAP 代码或每台设备的配置文件里：

| 问题 | 后果 |
| --- | --- |
| 同型号设备重复配置 | 改一个变量要改 N 处；漏改导致数据缺失 |
| 点表定义不可查 | 工程师不知道"这台设备能报哪些变量" |
| 无法做数据治理 | 变量单位/缩放/数据类型各系统理解不一致 |
| 无法与工艺参数对齐 | SPC 不知道该采什么、不知道变量语义 |

> **结论（EI1）**：设备接口点表是 **MDS 的设备型号级主数据**（与设备型号 `mds_equipment_model` 同层），EAP 只负责**连接、读写与实时值持有**。这样「点表」成为可复用、可版本化、可治理的数据资产。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 点表定义（变量/事件/报警/报告） | **MDS** | `mds_equip_if_*`，本文范围 |
| 型号级模板 | **MDS** | `mds_equip_if_definition.equipment_model_id` |
| 实例级覆盖（某台设备特有变量） | **MDS** | `mds_equip_if_override` |
| 点表版本 | **MDS** | `definition.version`，EAP 按版本加载 |
| **设备连接状态（COMM_ENABLED/OFFLINE）** | **EAP** | 实时态，MDS 不存（[equipment-design §3.12](equipment-design.md)） |
| **变量实时值 / 事件发生 / 报警当前态** | **EAP / MES** | 实时数据，MDS 不存 |
| **报警限值判定与响应动作** | **EAP / MES** | MDS 给定义，执行在下游 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 点表 ↔ 设备型号 | `mds_equip_if_definition.equipment_model_id → mds_equipment_model.id` | [equipment-design §3.2](equipment-design.md) |
| 实例覆盖 ↔ 设备 | `mds_equip_if_override.equipment_id → mds_equipment.id` | [equipment-design §3.3](equipment-design.md) |
| 报警 ↔ 状态机迁移 | `mds_equip_if_alarm` 可关联 CEID，CEID 可关联状态迁移 | [equipment-design §3.14](equipment-design.md) |
| 变量 ↔ 工艺参数 | 采样计划把 `variable` 与 `mds_process_flow_step_param` 连起来 | [sampling-spc-design](sampling-spc-design.md) / [process-flow-design §3.4](process-flow-design.md) |
| 变量 ↔ 约束 | `EQUIP_ALARM_LIMIT` / `EC_LIMIT`（新增约束类型） | [constraint-design §4.2](constraint-design.md) |
| 变量 ↔ 耗材/光罩寿命计数 | 计数变量（如靶材用量）供寿命判定 | [material-design §4.4](material-design.md) / [reticle-design §4.5](reticle-design.md) |
| 变量 ↔ 原因码 | CEID → 停机原因码（人工/自动） | [reason-defect-design](reason-defect-design.md) |

---

## 1. 行业依据

| 标准 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E5（SECS-II）** | 消息与数据项定义；`SVID` / `DVID` / `ECID` / `CEID` / `RPTID` / `ALID` 的编号体系 | `mds_equip_if_variable.code` / `_event.code` / `_alarm.code` / `_report.code` 承载编号 |
| **SEMI E30（GEM）** | 标准状态模型 + 必备变量/事件/报警集；`Status Variable`（SV）/ `Data Variable`（DV）/ `Equipment Constant`（EC）语义与访问权限（R/W） | `variable_kind` + `access_mode` |
| **SEMI E37（HSMS）** | 会话层（本项目 EAP 侧协议）；会话建立与设备在线判定 | 点表 `protocol` 声明（`SECS_I_GEM_HSMS`） |
| **SEMI E39（Object Services）** | 对象管理与事件报告机制（Report/Event linkage） | `mds_equip_if_report` + `_event.report_id` |
| **SEMI E10** | 设备状态与报警的关联（DOWN 原因的自动化来源） | 报警/CEID 与状态迁移、原因码联动 |
| **Data Collection Plan（DCP）** | 定义"采什么、多久采一次" | `mds_equip_if_variable` 被 [sampling-spc-design](sampling-spc-design.md) 的采样计划引用 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **点表（Interface Point List）** | 设备与主机通信的**全部数据点定义** | 不是数据库表结构；是通信契约 |
| **SV / Status Variable** | 状态变量（如 `ControlState`、`PPExecName`），只读 | 与 E10 状态（枚举语义）区分：SV 是其**通信载体** |
| **DV / Data Variable** | 数据变量（如温度读数），只读 | 与 EC 区别在**可否写** |
| **EC / Equipment Constant** | 设备常量（如 `MaxLoadPorts`），**可读可写** | 写 EC 属远程配置，需权限与留痕 |
| **CEID / Collection Event ID** | 事件 ID（如 `ProcessStart`、`WaferEnd`），触发上报 | 不是报警；事件是"发生了什么" |
| **ALID / Alarm ID** | 报警 ID，含 Set/Clear 对 | 与 CEID 常联动（报警发生即发 CEID） |
| **RPTID / Report ID** | 报告定义（一组变量的快照），事件触发时按报告上报 | 不是报表；是**数据打包契约** |
| **实例覆盖（Override）** | 同型号某台设备的差异（多一个传感器/少一个端口） | 不是"自定义新设备"；仅差异项 |

---

## 3. 实体总览与 ER

```
mds_equipment_model
        ▲ equipment_model_id
mds_equip_if_definition (点表模板: protocol / version / status)
        │
        ├──< mds_equip_if_variable  (SV / DV / EC; code + data_type + unit + access)
        ├──< mds_equip_if_event     (CEID; 触发条件 + 关联 report)
        ├──< mds_equip_if_alarm     (ALID; code + severity + category + ceid)
        └──< mds_equip_if_report    (RPTID)
                 └──< mds_equip_if_report_var (报告成员 → variable)

mds_equip_if_override (实例级差异: equipment_id + variable_code + override 值/启用)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 点表定义 | `mds_equip_if_definition` | 型号级模板头（协议/版本） |
| 变量 | `mds_equip_if_variable` | SV / DV / EC 统一表（`variable_kind` 区分） |
| 事件 | `mds_equip_if_event` | CEID 定义 |
| 报警 | `mds_equip_if_alarm` | ALID 定义 |
| 报告 | `mds_equip_if_report` | RPTID 定义 |
| 报告成员 | `mds_equip_if_report_var` | 报告 ↔ 变量 |
| 实例覆盖 | `mds_equip_if_override` | 实例级差异 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_equip_if_definition`（点表定义头）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 点表代码（如 `IF-SCN-28N-V1`） |
| `name` | VARCHAR(128) | | 名称 |
| `equipment_model_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment_model.id` | 所属设备型号 |
| `protocol` | VARCHAR(24) | NOT NULL | `SECS_I_GEM_HSMS`/`SECS_I_HSL`/`OPC_UA`/`MODBUS_TCP`/`CUSTOM` |
| `session_mode` | VARCHAR(16) | | `PASSIVE`（设备等主机）/`ACTIVE` |
| `revision` | VARCHAR(16) | NOT NULL | 点表版本（改点表升版本，EAP 按版本加载） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `mdln`/`softrev` | VARCHAR(32) | | 设备 MDLN / SOFTREV（GEM 识别） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(equipment_model_id, revision, tenant_id, deleted)`。

### 3.2 `mds_equip_if_variable`（SV / DV / EC）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `definition_id` | VARCHAR(32) | NOT NULL, FK→`mds_equip_if_definition.id` | 所属点表 |
| `variable_kind` | VARCHAR(8) | NOT NULL | `SV`（状态变量）/`DV`（数据变量）/`EC`（设备常量） |
| `code` | VARCHAR(32) | NOT NULL | 通信编号或名称（`SVID`/`DVID`/`ECID`，如 `1001` 或 `ControlState`） |
| `name` | VARCHAR(128) | | 变量名 |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | **参数定义（统一语义）**：把设备侧变量映射到全厂统一参数，是跨设备数据聚合的前提，见 [param-def-design](param-def-design.md)（亦可经 `mds_param_alias` 的 `source_system` 映射反查） |
| `data_type` | VARCHAR(16) | NOT NULL | `ASCII`/`U1`/`U2`/`U4`/`I1`/`I2`/`I4`/`F4`/`F8`/`BOOLEAN`/`LIST` |
| `unit` | VARCHAR(16) | | 单位（对齐 [uom-dict-design](uom-dict-design.md)） |
| `access_mode` | VARCHAR(8) | NOT NULL DEFAULT 'R' | `R`（读）/`RW`（读写，仅 EC 合理） |
| `default_value` | VARCHAR(128) | | 默认/初值 |
| `min_value`/`max_value` | VARCHAR(64) | | 有效范围（写 EC 时校验） |
| `scale_factor` | DECIMAL(12,6) | | 缩放系数（原始值 × scale = 工程值） |
| `enum_ref` | VARCHAR(64) | | 枚举字典引用（[uom-dict-design](uom-dict-design.md) `mds_code_table.code`） |
| `is_gem_required` | BIT | DEFAULT 0 | 是否 GEM 必备变量 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(definition_id, variable_kind, code, tenant_id, deleted)`。

### 3.3 `mds_equip_if_event`（CEID）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `definition_id` | VARCHAR(32) | NOT NULL, FK→`mds_equip_if_definition.id` | 所属点表 |
| `code` | VARCHAR(32) | NOT NULL | CEID |
| `name` | VARCHAR(128) | | 事件名（如 `ProcessStart`/`WaferEnd`） |
| `report_id` | VARCHAR(32) | FK→`mds_equip_if_report.id` | 关联报告（事件触发时按报告上报） |
| `trigger_kind` | VARCHAR(16) | | `AUTO`（设备发）/`HOST_CMD`（主机请求） |
| `related_state_transition_id` | VARCHAR(32) | FK→`mds_equipment_state_transition.id` | 可选：该事件对应的状态迁移（[equipment-design §3.14](equipment-design.md)） |
| `is_gem_required` | BIT | DEFAULT 0 | 是否 GEM 必备事件 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(definition_id, code, tenant_id, deleted)`。

### 3.4 `mds_equip_if_alarm`（ALID）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `definition_id` | VARCHAR(32) | NOT NULL, FK→`mds_equip_if_definition.id` | 所属点表 |
| `code` | VARCHAR(32) | NOT NULL | ALID |
| `name` | VARCHAR(128) | | 报警名 |
| `severity` | VARCHAR(16) | NOT NULL | `CRITICAL`/`MAJOR`/`MINOR`/`WARNING`/`INFO` |
| `category` | VARCHAR(24) | | `EQUIPMENT`/`PROCESS`/`SAFETY`/`MATERIAL`/`ENV` |
| `set_ceid` | VARCHAR(32) | | 报警 Set 时的 CEID |
| `clear_ceid` | VARCHAR(32) | | 报警 Clear 时的 CEID |
| `reason_code_ref` | VARCHAR(64) | FK→`mds_reason_code.code` | 默认停机原因码（[reason-defect-design](reason-defect-design.md)） |
| `auto_down` | BIT | DEFAULT 0 | 是否自动置设备为 DOWN |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(definition_id, code, tenant_id, deleted)`。

### 3.5 `mds_equip_if_report` / `mds_equip_if_report_var`（RPTID）

**`mds_equip_if_report`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `definition_id` | VARCHAR(32) | NOT NULL, FK→`mds_equip_if_definition.id` | 所属点表 |
| `code` | VARCHAR(32) | NOT NULL | RPTID |
| `name` | VARCHAR(128) | | 报告名 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(definition_id, code, tenant_id, deleted)`。

**`mds_equip_if_report_var`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `report_id` | VARCHAR(32) | NOT NULL, FK→`mds_equip_if_report.id` | 报告 |
| `variable_id` | VARCHAR(32) | NOT NULL, FK→`mds_equip_if_variable.id` | 成员变量 |
| `seq` | INT | NOT NULL | 上报顺序 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(report_id, variable_id, tenant_id, deleted)`。

### 3.6 `mds_equip_if_override`（实例级差异）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 设备实例 |
| `variable_code` | VARCHAR(32) | NOT NULL | 差异变量（引用 definition 内 `variable.code`） |
| `variable_kind` | VARCHAR(8) | NOT NULL | `SV`/`DV`/`EC` |
| `override_value` | VARCHAR(128) | | 覆盖默认值 |
| `is_enabled` | BIT | DEFAULT 1 | 该实例是否启用此变量 |
| `override_note` | VARCHAR(256) | | 差异原因（改机/加装传感器） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(equipment_id, variable_kind, variable_code, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 EI1–EI6）

### 4.1 型号级模板 + 实例级覆盖（EI2）
- **模板在型号**：同一型号几十台设备共用一份点表，改一处即全型号生效（EAP 按版本热加载）；
- **差异在实例**：改机/加装传感器导致的差异，用 `mds_equip_if_override` 表达，**不复制整份点表**；
- EAP 加载逻辑：`definition(model)` + `override(equipment)` 合并 → 实际生效点表。

### 4.2 变量三类与访问权限（EI3）
| kind | 语义 | 可写 | 落点 |
| --- | --- | --- | --- |
| `SV` | 状态变量（ControlState、PPExecName…） | ❌ | 派工与状态判定输入 |
| `DV` | 数据变量（温度、压力读数） | ❌ | 数据采集（SPC 输入） |
| `EC` | 设备常量（最大载具数、超时…） | ✅ | 远程配置（需权限 + 留痕，`@History` 覆盖） |

> `access_mode=RW` 仅对 `EC` 有意义；写 EC 属**远程配置变更**，须经 [change-mgmt-design](change-mgmt-design.md) 或至少留痕。

### 4.3 事件、报警与报告（EI4）
- **CEID**：设备"发生了什么"；可关联**状态迁移**（[equipment-design §3.14](equipment-design.md)），使状态变更可自动归因；
- **ALID**：报警 Set/Clear 成对；`auto_down=1` 时下游自动置设备 DOWN 并挂 `reason_code_ref`；
- **RPTID**：事件触发时的**数据打包契约**（一组变量的快照），是数据采集的实际载体。

### 4.4 与工艺参数 / 采样的关系（EI5）
- 点表回答「**设备能报什么**」；`mds_process_flow_step_param` 回答「**工艺要求什么**（窗口）」；[sampling-spc-design](sampling-spc-design.md) 的**采样计划**把二者连接（采哪个变量、多久一次、判定用哪条 SPC 规则）。
- 明确三方不重复：点表存**变量元数据**，工艺流存**参数窗口**，采样计划存**采集策略**。

### 4.5 与约束层的关系（EI6）
- 报警限值、EC 合法范围可作为约束（新增 `EQUIP_ALARM_LIMIT` / `EC_LIMIT`），交由 [constraint-design](constraint-design.md) 统一注册与求值编排；
- 避免在 EAP 内硬编码"超限就停"，而是由约束层声明、下游执行。

### 4.6 版本化（EI6 续）
- 点表改版（增删变量/改编号）必须**升 `revision`**，旧版本保留（老设备/未升级设备继续用）；
- 与 route/recipe 的"RELEASED 不可变"同源思路：**已发布点表版本不可改**，改须克隆新版。

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 点表实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 点表/变量/EC 默认值变更落 `{entity}_hist`（EC 变更留痕尤重要） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + GEM 必备 SV/CEID/ALID 种子数据 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_equip_if_definition (
  id                VARCHAR(32) NOT NULL,
  tenant_id         VARCHAR(32) NOT NULL,
  code              VARCHAR(64) NOT NULL,
  name              VARCHAR(128),
  equipment_model_id VARCHAR(32) NOT NULL,
  protocol          VARCHAR(24) NOT NULL,
  session_mode      VARCHAR(16),
  revision           VARCHAR(16) NOT NULL,
  status            VARCHAR(16) NOT NULL,
  mdln              VARCHAR(32),
  softrev           VARCHAR(32),
  description       VARCHAR(512),
  version_          BIGINT NOT NULL DEFAULT 0,
  deleted           BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqif_def PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqif_def UNIQUE (equipment_model_id, revision, tenant_id, deleted),
  CONSTRAINT fk_eqif_def_model FOREIGN KEY (equipment_model_id) REFERENCES mds_equipment_model (id)
);

CREATE TABLE mds_equip_if_variable (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  definition_id  VARCHAR(32) NOT NULL,
  variable_kind  VARCHAR(8) NOT NULL,
  code           VARCHAR(32) NOT NULL,
  name           VARCHAR(128),
  param_def_id   VARCHAR(32),
  data_type      VARCHAR(16) NOT NULL,
  unit           VARCHAR(16),
  access_mode    VARCHAR(8) NOT NULL DEFAULT 'R',
  default_value  VARCHAR(128),
  min_value      VARCHAR(64),
  max_value      VARCHAR(64),
  scale_factor   DECIMAL(12,6),
  enum_ref       VARCHAR(64),
  is_gem_required BIT DEFAULT 0,
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqif_var PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqif_var UNIQUE (definition_id, variable_kind, code, tenant_id, deleted),
  CONSTRAINT fk_eqif_var_def FOREIGN KEY (definition_id) REFERENCES mds_equip_if_definition (id),
  CONSTRAINT fk_eqif_var_param_def FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id)
);

CREATE TABLE mds_equip_if_report (
  id            VARCHAR(32) NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  definition_id VARCHAR(32) NOT NULL,
  code          VARCHAR(32) NOT NULL,
  name          VARCHAR(128),
  description   VARCHAR(512),
  version_      BIGINT NOT NULL DEFAULT 0,
  deleted       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqif_rpt PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqif_rpt UNIQUE (definition_id, code, tenant_id, deleted),
  CONSTRAINT fk_eqif_rpt_def FOREIGN KEY (definition_id) REFERENCES mds_equip_if_definition (id)
);

CREATE TABLE mds_equip_if_report_var (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  report_id   VARCHAR(32) NOT NULL,
  variable_id VARCHAR(32) NOT NULL,
  seq         INT NOT NULL,
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqif_rptvar PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqif_rptvar UNIQUE (report_id, variable_id, tenant_id, deleted),
  CONSTRAINT fk_eqif_rptvar_rpt FOREIGN KEY (report_id) REFERENCES mds_equip_if_report (id),
  CONSTRAINT fk_eqif_rptvar_var FOREIGN KEY (variable_id) REFERENCES mds_equip_if_variable (id)
);

CREATE TABLE mds_equip_if_event (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  definition_id  VARCHAR(32) NOT NULL,
  code           VARCHAR(32) NOT NULL,
  name           VARCHAR(128),
  report_id      VARCHAR(32),
  trigger_kind   VARCHAR(16),
  related_state_transition_id VARCHAR(32),
  is_gem_required BIT DEFAULT 0,
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqif_evt PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqif_evt UNIQUE (definition_id, code, tenant_id, deleted),
  CONSTRAINT fk_eqif_evt_def FOREIGN KEY (definition_id) REFERENCES mds_equip_if_definition (id),
  CONSTRAINT fk_eqif_evt_rpt FOREIGN KEY (report_id) REFERENCES mds_equip_if_report (id),
  CONSTRAINT fk_eqif_evt_tr FOREIGN KEY (related_state_transition_id) REFERENCES mds_equipment_state_transition (id)
);

CREATE TABLE mds_equip_if_alarm (
  id            VARCHAR(32) NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  definition_id VARCHAR(32) NOT NULL,
  code          VARCHAR(32) NOT NULL,
  name          VARCHAR(128),
  severity      VARCHAR(16) NOT NULL,
  category      VARCHAR(24),
  set_ceid      VARCHAR(32),
  clear_ceid    VARCHAR(32),
  reason_code_ref VARCHAR(64),
  auto_down     BIT DEFAULT 0,
  description   VARCHAR(512),
  version_      BIGINT NOT NULL DEFAULT 0,
  deleted       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqif_alarm PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqif_alarm UNIQUE (definition_id, code, tenant_id, deleted),
  CONSTRAINT fk_eqif_alarm_def FOREIGN KEY (definition_id) REFERENCES mds_equip_if_definition (id)
);

CREATE TABLE mds_equip_if_override (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  equipment_id   VARCHAR(32) NOT NULL,
  variable_code  VARCHAR(32) NOT NULL,
  variable_kind  VARCHAR(8) NOT NULL,
  override_value VARCHAR(128),
  is_enabled     BIT DEFAULT 1,
  override_note  VARCHAR(256),
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqif_ovr PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqif_ovr UNIQUE (equipment_id, variable_kind, variable_code, tenant_id, deleted),
  CONSTRAINT fk_eqif_ovr_eq FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id)
);
```

> 历史表 `mds_equip_if_definition_hist` / `mds_equip_if_variable_hist` / … 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.equipif
  ├── entity
  │   ├── EquipIfDefinition.java     # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── EquipIfVariable.java        # SV/DV/EC
  │   ├── EquipIfEvent.java           # CEID
  │   ├── EquipIfAlarm.java           # ALID
  │   ├── EquipIfReport.java           # RPTID
  │   ├── EquipIfReportVar.java        # report ↔ variable
  │   ├── EquipIfOverride.java         # 实例级差异
  │   └── package-info.java            # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── EquipIfDefinitionRepository.java
  │   └── EquipIfVariableRepository.java
  ├── service
  │   ├── EquipIfService.java           # 继承 AbstractJpaService；cloneAsNewVersion（点表改版）
  │   ├── EquipIfResolver.java           # definition + override 合并 → 实例生效点表（EAP 拉取）
  │   └── EquipIfValidator.java          # EC 取值范围/编号唯一/报告成员有效性
  └── web
      └── EquipIfController.java         # 查询/版本发布/克隆/实例点表导出
```

---

## 8. 待补 / 后续

- **EAP 拉取协议**：`resolvePointList(equipmentId)` 输出（合并后点表 JSON），EAP 启动/改版时拉取 + 版本比对（与 [md-distribution-design](md-distribution-design.md) 的发布订阅复用）。
- **GEM 必备集种子**：按 SEMI E30 提供标准 SV/CEID/ALID 必备集作为种子数据，各型号在其上裁剪。
- **EC 写入管控**：写 EC 的权限、审批与回滚（联动 [change-mgmt-design](change-mgmt-design.md) 与 [md-governance-design](md-governance-design.md) 签名）。
- **点表差异对比**：两个点表版本 diff（新增/删除/改编号变量），供 EAP 升级评审（可复用 [process-flow-design §4.7](process-flow-design.md) 的 diff 范式）。
- **与既有文档闭环**：本文引用 `mds_equipment_model` / `mds_equipment` / `mds_equipment_state_transition`（[equipment-design](equipment-design.md)）、`mds_process_flow_step_param`（[process-flow-design](process-flow-design.md)）、`mds_reason_code`（[reason-defect-design](reason-defect-design.md)）、`mds_code_table` / `mds_uom`（[uom-dict-design](uom-dict-design.md)）、新增约束类型 `EQUIP_ALARM_LIMIT`/`EC_LIMIT`（[constraint-design](constraint-design.md)）。
