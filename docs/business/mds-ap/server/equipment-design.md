# MDS-AP 设备建模详细设计（Equipment Modeling）

> 本文档为 `mds-ap` 业务域中「设备主数据」的**实现级详细设计**，是 [`location-design.md`](location-design.md)（位置主数据）的姊妹篇。
> 两者共同构成 MDS 的**位置-设备双主线**，并通过 `location_id` + 闭包表 + 工艺能力形成**闭环**。
> 平台底座对齐：`BaseDefData` / `@History(SNAPSHOT)` / `cimTenantFilter` / T2.5 软删唯一约束 / 主历成对迁移，详见 `docs/platform/server/design.md` §2.1–§2.3、§6。
>
> 行业依据：**SEMI E10**（设备可靠性/可用性状态模型与状态机）、**SEMI E148**（设备层级）、**SEMI E157**（Equipment Information Model，含 Module/Chamber/Port）、**SEMI E40/E94**（配方管理与数据采集）、**SEMI E87/E90**（载具/基板追踪，界定设备与 MES 边界）；以及 fab **MDM 主数据管理实践**（多轴分类、配方资格 qualification、**资产多角色所有权**、**状态分层：行政生命期态 / 实时 E10 运行态 / 设备级聚合态**）。

---

## 0. 边界与闭环总览

### 0.1 MDS 管什么、不管什么（职责边界）

| 范畴 | 归属 | 说明 |
| ---- | ---- | ---- |
| 设备定义与分类（class/type/kind、厂商/型号/序列、位置、能力、模块/端口） | **MDS 主数据** | 见 §3.1–§3.6 |
| 设备**分类维度**（串行三级 + 平行正交轴） | **MDS 主数据** | 见 §3.1 |
| 设备**所有权**（多 owner、多角色） | **MDS 主数据** | **见 §3.9（本次补全）** |
| 设备**行政生命期态**（PLANNED→…→RETIRED，含迁移约束） | **MDS 主数据（实例+强校验）** | **见 §3.10 / §3.14.1（统一转换模型）** |
| 设备**腔体/模块行政态**（ENABLED/DISABLED…，含迁移约束） | **MDS 主数据（实例+强校验）** | **见 §3.5 / §3.10 / §3.14.2** |
| **E10 状态机「定义」**（状态目录 + 合法迁移，按 class 定制） | **MDS 主数据（定义）** | **见 §3.11 / §3.14.3** |
| 设备**端口行政态**（ENABLED/DISABLED） | **MDS 主数据** | 见 §3.5 |
| 设备**端口 E157 实时态定义**（NOTINIT/INIT/READY/NOTREADY/TRANSFERRING） | **MDS 主数据（定义）** | **见 §3.14.5** |
| 配方**资格**（设备可跑的配方集合 = 多 recipe） | **MDS 主数据** | 见 §3.8；配方正文(body)归外围 Recipe 系统 |
| 设备搬迁轨迹（历史） | **MDS**（复用平台 `@History`） | 见 §3.3 / §6 |
| 设备**实时 E10 运行态**（PRD/IDL/SD/DOWN…）、**腔体实时 E10**、实时配方执行、活跃配方 | **MES / EAP 实时域** | MDS **只拥有 E10 状态机定义**与**行政态**；实时实例归 MES 维护，MDS 不存 |
| 设备**通信/控制模式**（offline / online / remote）定义 + 支持模式声明 | **MDS 主数据（定义+声明）** | 见 §3.12（本次补全） |
| 设备**实时通信/控制模式**实例 | **EAP 实时域** | MDS 拥有模式定义（`mds_equipment_state_model` model_kind=CONTROL_MODE）与「是否支持 remote」声明（`remote_capable`）；实时实例归 EAP，MDS 不存 |
| 设备级 E10 聚合态（= 各腔体 E10 的 rollup） | **MES 计算**（规则由 MDS 定义） | 见 §3.11 / §4.10 |
| 派工 / 在制 | **MES** | MDS 提供设备 / 能力 / 配方资格主数据供其引用 |

> 闭环要点：MDS 提供「设备是谁、归谁、在哪、属哪类、能干什么工艺、能跑哪些配方、处于什么行政态、遵循哪套 E10 状态机」的主数据；MES 据此派工并回报实时态。两者通过**设备 code / id**、**位置 code / path**、**配方 code** 衔接，不重复维护实时状态。

### 0.2 与位置主数据、平台底座的闭环关系

```
                        平台底座 (cim-core / cim-jpa-starter)
        BaseDefData / @History / cimTenantFilter / T2.5 / 主历成对
                            ▲                 ▲
        ┌───────────────────┴────────┐        │
        │     位置主数据 (location-design.md)  │
        │  mds_location ──closure──> mds_location_closure
        └───────────────┬────────────┘        │
              location_id (FK)                 │
                        │                      │
        ┌───────────────▼──────────────────────▼──────────────────────────┐
        │            设备主数据 (本文档)                                    │
        │  mds_equipment                                                  │
        │    ├──model_id──> mds_equipment_model ──type_id──> mds_equipment_type ──class_id──> mds_equipment_class  (串行三级分类)
        │    ├──equipment_id──> mds_equipment_module  (E157 设备内层级, 自引用, 含腔体行政态)        │
        │    ├──equipment_id──> mds_equipment_port     (E157 端口; admin_status + state_model_id→PORT_STATE)  │
        │    ├──equipment_id──> mds_equipment_capability ──area_code──> mds_location(AREA)  (闭环)
        │    ├──equipment_id──> mds_equipment_owner  (多 owner 多角色, 本次补全)               │
        │    ├──equipment_id──> mds_equipment_tech_node  (多值: 工艺节点)              │
        │    ├──equipment_id──> mds_equipment_recipe_qual ──recipe_id──> mds_recipe   (多 recipe 资格)
        │    ├──model_id──> mds_equipment_state_model(model_kind=E10_STATE) + mds_equipment_state + _state_transition (E10 状态机定义, 本次补全)
        │    ├──model_id──> mds_equipment_state_model(model_kind=CONTROL_MODE) + mds_equipment_state + _state_transition (通信/控制模式定义, 本次补全)
        │    ├──(equipment) remote_capable BIT  (是否支持 ONLINE-REMOTE 声明, 本次补全)
        │    └──@History(SNAPSHOT)──> mds_equipment_hist                                │
        └────────────────────────────────────────────────────────────────────────────┘
```

**闭环映射表：**

| 闭环链路 | 机制 | 落点 |
| -------- | ---- | ---- |
| 设备 ↔ 物理位置 | `mds_equipment.location_id → mds_location(BAY/SUBBAY)` | §3.3 |
| 设备 → 所属工艺 Area（向上推导） | 经 `mds_location_closure` 由 BAY/SUBBAY 向上找 `level=AREA` 祖先 | §4.2 |
| 设备 ↔ 工艺能力 ↔ Area | `mds_equipment_capability.area_code → mds_location(code, level=AREA)` | §3.7 |
| 设备分类（串行三级） | `equipment→model→type→class` 外键链 | §3.1 |
| 设备 ↔ 配方（多 recipe） | `mds_equipment_recipe_qual` 多对多资格 | §3.8 |
| 设备 ↔ 所有权（多 owner） | `mds_equipment_owner` 链接实体 | §3.9 |
| 设备 / 腔体 ↔ 行政态 | `mds_equipment.lifecycle_status` + `mds_equipment_module.admin_status` | §3.10 / §3.14.1–§3.14.2 |
| 设备 / 腔体 ↔ E10 状态机 | `mds_equipment_state_model/state/transition`（定义，model_kind=E10_STATE）；实时实例归 MES | §3.11 / §3.14.3 |
| 设备 ↔ 通信/控制模式 | `mds_equipment_state_model`(model_kind=CONTROL_MODE) + `mds_equipment.remote_capable`（定义+声明）；实时实例归 EAP | §3.12 / §3.14.4 |
| 端口 ↔ 状态机 | `mds_equipment_port.admin_status`（行政，MDS）+ `mds_equipment_port.state_model_id`（E157 实时态定义，model_kind=PORT_STATE） | §3.5 / §3.14.5 |
| 设备搬迁轨迹 | `@History(SNAPSHOT) → mds_equipment_hist` + 可选 `mds_equipment_move_event` | §3.3 / §3.15 |
| 设备内层级（E157） | `mds_equipment_module.parent_module_id` 自引用 | §3.5 |
| 全局底座 | 所有实体继承 `BaseDefData` + 租户过滤 + 软删唯一 | §5 |

---

## 1. 行业依据（SEMI + fab MDM 实践）

| 标准/实践 | 贡献 | 在本设计中的体现 |
| ---- | ---- | --------------- |
| **SEMI E148** | 设备层级 Enterprises>Site>Area>Bay>WorkArea>Equipment>Module | 位置层级（见 `location-design.md`）；设备作为 BAY/SUBBAY 上的叶子 |
| **SEMI E157** | Equipment Information Model：Equipment / SubEquipment / Module（Process/Transfer/Control）/ Port / Chamber | `mds_equipment` + `mds_equipment_module` + `mds_equipment_port`（§3.3–§3.6） |
| **SEMI E10** | 设备可靠性状态模型与状态机：PRD/IDL/SBY/ENG/NST/SDT/SDL/SDN/UDT/UDL/UDN/UPD/PRG/WUP 等状态 + 合法迁移 | **MDS 拥有「状态机定义」（`mds_equipment_state_model/_state/_state_transition`，§3.11）；实时运行态与设备级聚合态归 MES（§0.1 / §4.x）** |
| **SEMI E40/E94** | 配方管理 / 设备数据采集；配方按工艺步骤绑定、需资格认证 | `mds_recipe` + `mds_equipment_recipe_qual`（§3.8） |
| **SEMI E87/E90** | 载具/基板追踪、设备端口交互 | 设备端口 `mds_equipment_port`；实时追踪归 MES |
| **SEMI E5 / E30（GEM）** | 设备的通信状态与控制模式（Control State）：OFFLINE / ONLINE-LOCAL / ONLINE-REMOTE；通信使能 COMM_ENABLED/DISABLED | **MDS 拥有控制模式定义（`mds_equipment_state_model` model_kind=CONTROL_MODE，§3.12）+ 设备「是否支持 remote」声明（`remote_capable`）；实时控制模式实例归 EAP（§0.1）** |
| **fab MDM 实践** | 设备多轴分类（工艺/架构/等级/关键性/所有权彼此正交）；批次 vs 单片处理（并行/串行）；**资产多角色所有权（OEM 寄售硬件 / 部门管运营 / 外包管保养）**；**状态分层：行政生命期态与实时 E10 运行态分离** | 串行三级 + 平行正交轴（§3.1）；`process_mode`（§3.1.4）；`mds_equipment_owner`（§3.9）；行政态 vs E10 运行态（§3.10–§3.11）；控制模式（§3.12） |

> **状态分层是 fab 设备建模最容易出错之处**。本设计严格区分四种「状态」，避免把行政态、实时态、腔体态、资格态混为一谈（详见 §3.10 / §3.11 与 §4.x）。

---

## 2. 实体总览

| 实体 | 表名 | 是否历史 | 角色 |
| ---- | ---- | -------- | ---- |
| 设备大类 | `mds_equipment_class` | 否 | 分类串行第 1 级（工艺域） |
| 设备类型 | `mds_equipment_type` | 否 | 分类串行第 2 级（挂在 class 下） |
| 设备型号/Kind | `mds_equipment_model` | 否 | 分类串行第 3 级（挂在 type 下）；引用 E10 状态模型 |
| **设备主记录** | `mds_equipment` | **SNAPSHOT** | 核心，含 `location_id`、分类 FK、平行轴、行政生命期态、搬迁轨迹 |
| 设备模块/腔体 | `mds_equipment_module` | 否 | E157 设备内层级（自引用）+ **腔体行政态** |
| 设备端口 | `mds_equipment_port` | 否 | E157 装载口 |
| 设备工艺能力 | `mds_equipment_capability` | 否 | 连接设备与工艺 Area（闭环） |
| 设备工艺节点（多值） | `mds_equipment_tech_node` | 否 | 可做的工艺节点（28/14/7nm…） |
| **设备所有权（多 owner）** | `mds_equipment_owner` | 否 | **多角色所有权链接实体（本次补全）** |
| 物理配方主数据 | `mds_recipe` | 否 | 设备(物理)配方定义（逻辑配方/分组/PPID 见 [配方设计](recipe-design.md)） |
| 设备配方资格（多对多，含 PPID） | `mds_equipment_recipe_qual` | 否 | 设备可跑的配方集合（单/多 recipe）；PPID 设备内唯一 |
| **状态机/模式模型（定义）** | `mds_equipment_state_model` | 否 | **状态机/模式定义框架（本次补全）；`model_kind` 区分 E10_STATE 与 CONTROL_MODE（§3.11–§3.12）** |
| **E10 状态（定义）** | `mds_equipment_state` | 否 | 状态目录（PRD/IDL/…），归属某状态模型 |
| **E10 状态迁移（定义）** | `mds_equipment_state_transition` | 否 | 合法状态迁移边（class 级定制） |
| 设备搬迁事件 | `mds_equipment_move_event` | 自身即日志 | 业务上下文（可选） |
| 设备快照历史 | `mds_equipment_hist` | — | `@History` 自动落（含换 Bay 轨迹） |

---

## 3. 实体详细设计

### 3.1 分类维度：串行三级 + 平行正交轴

设备「是什么」的归类与「有哪些彼此正交的属性」必须分开建模。

#### 3.1.1 串行三级（工艺归类树）

```
EquipmentClass ──> EquipmentType ──> EquipmentModel(=Kind)
   (LITHO)          (SCANNER)          (ASML NXT2050i)
```

- **Class（工艺大类）**：`LITHO` / `ETCH` / `DIFF` / `CMP` / `TF`(薄膜) / `IMP`(注入) / `INSP`(检验) / `METL`(金属化) / `CLEAN` / `OTHER`。
- **Type（工具类型，挂在 Class 下）**：每类下的工具形态，如
  - `LITHO` → `SCANNER` / `TRACK`(涂胶显影) / `SCANNER_TRACK` / `IMMERSION` / `EUV`
  - `ETCH` → `SINGLE_CHAMBER` / `MULTI_CHAMBER` / `BATCH`
  - `CLEAN` → `SINGLE_WAFER` / `BATCH`
- **Kind（具体机型 = Model）**：厂商具体型号，如 `ASML NXT2050i`、`TEL CLEAN-TRACK ACT12`。即 `mds_equipment_model`。

> 三级用外键链表达（`equipment→model→type→class`），是**串行层级**；新增机型/类型只加行，不动结构。

#### 3.1.2 平行正交轴（与工艺树无关的独立维度）

这些维度彼此正交、不与上述树嵌套，直接作为 `mds_equipment` 的列（或链接表）：

| 轴 | 列 | 取值 | 说明 |
| -- | -- | ---- | ---- |
| **处理架构（串行/并行）** | `process_mode` | `SINGLE_WAFER`(串行/单片) / `BATCH`(并行/批次) / `CLUSTER`(并行多腔) / `HYBRID` | 回答「串行还是并行」：单片=串行，批次/集群=并行 |
| 等级 | `grade` | `PROD` / `ENGINEERING` / `PILOT` | 量产 vs 工程 |
| 关键性 | `criticality` | `CRITICAL` / `NON_CRITICAL` | 关键层 vs 非关键层 |
| 资格状态 | `qualification_state` | `QUALIFIED` / `QUALIFYING` / `NOT_QUALIFIED` | 量产资格（注意：这是**资格态**，非实时态，见 §3.10 状态分层） |
| 所有权性质（快速标志） | `ownership` | `OWNED` / `CONTRACTED` | 自有 vs 外包；**详细多角色所有权见 §3.9 `mds_equipment_owner`** |
| 工艺节点（多值） | `mds_equipment_tech_node` | `28`/`14`/`7`(nm) | 链接表，支持多节点（见 §3.6） |

> **结论**：分类 = **串行核心（Class→Type→Kind）+ 平行属性轴** 的混合模型。既保留工艺归类的层级，又用独立轴表达正交属性。各厂自定义扩展维度可后续以 EAV/标签表（`mds_equipment_attribute`）兜底（见 §8）。

### 3.2 `mds_equipment_class` / `mds_equipment_type` / `mds_equipment_model`（分类三级）

均继承 `BaseDefData`。

**`mds_equipment_class`**

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `code` | VARCHAR(32) | 唯一 `(code,tenant_id,deleted)` | 如 `LITHO` |
| `name` | VARCHAR(128) | | 显示名 |
| `description` | VARCHAR(512) | | 基类 |

**`mds_equipment_type`**

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `class_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment_class.id` | 串行上级 |
| `code` | VARCHAR(32) | 唯一 `(class_id,code,tenant_id,deleted)` | 如 `SCANNER` |
| `name` | VARCHAR(128) | | 显示名 |
| `description` | VARCHAR(512) | | 基类 |

**`mds_equipment_model`（Kind）**

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `type_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment_type.id` | 串行上级（Kind→Type→Class） |
| `code` | VARCHAR(64) | 唯一 `(type_id,code,tenant_id,deleted)` | 型号编码，如 `ASML-NXT2050I` |
| `name` | VARCHAR(128) | | 显示名 |
| `manufacturer` | VARCHAR(128) | | 厂商 |
| `model_no` | VARCHAR(128) | | 厂商型号 |
| `state_model_id` | VARCHAR(32) | FK→`mds_equipment_state_model.id` | **引用的 E10 状态机定义（§3.11，model_kind=E10_STATE）** |
| `control_mode_model_id` | VARCHAR(32) | FK→`mds_equipment_state_model.id` | **引用的通信/控制模式定义（§3.12，model_kind=CONTROL_MODE）** |
| `description` | VARCHAR(512) | | 基类 |

### 3.3 `mds_equipment`（设备主记录，核心）

继承 `BaseDefData` + **`@History(SNAPSHOT)` → `mds_equipment_hist`**（与位置设计 §2.4 一致的权威定义）。

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `code` | VARCHAR(64) | 唯一 `(code,tenant_id,deleted)` | 设备自然键，如 `TK0001` |
| `name` | VARCHAR(128) | | 设备名 |
| `model_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment_model.id` | 型号（Kind）；经 model→type→class 得分类 |
| `location_id` | VARCHAR(32) | NOT NULL, FK→`mds_location` | **当前物理位置（BAY/SUBBAY）**——闭环锚点（D6） |
| `lifecycle_status` | VARCHAR(16) | NOT NULL | **行政生命期态**：`PLANNED`/`INSTALLING`/`QUALIFYING`/`ACTIVE`/`STANDBY`/`MAINTENANCE`/`DECOMMISSIONED`/`RETIRED`（见 §3.10） |
| `process_mode` | VARCHAR(16) | NOT NULL | 处理架构：`SINGLE_WAFER`/`BATCH`/`CLUSTER`/`HYBRID`（§3.1.4） |
| `grade` | VARCHAR(16) | NOT NULL | `PROD`/`ENGINEERING`/`PILOT` |
| `criticality` | VARCHAR(16) | NOT NULL | `CRITICAL`/`NON_CRITICAL` |
| `qualification_state` | VARCHAR(16) | NOT NULL | `QUALIFIED`/`QUALIFYING`/`NOT_QUALIFIED`（资格态，非实时态） |
| `ownership` | VARCHAR(16) | NOT NULL | `OWNED`/`CONTRACTED`（快速标志；详情见 `mds_equipment_owner`） |
| `remote_capable` | BIT | NOT NULL DEFAULT 1 | 是否支持 `ONLINE-REMOTE`（远程/主机控制）；不支持则仅 `LOCAL`/`OFFLINE`（见 §3.12） |
| `serial_no` | VARCHAR(128) | | 出厂序列号 |
| `asset_no` | VARCHAR(128) | | 资产编号 |
| `commission_date` | DATE | | 投产日期 |
| `description` | VARCHAR(512) | | 基类 |

- **闭环**：`location_id` 经 `mds_location_closure` 向上推导 `level=AREA` 祖先，得到设备所属工艺区。
- **分类**：`model_id` 经 `model→type→class` 得完整分类链。
- **实时 E10 运行态 / 实时控制模式不在本表**：设备级实时 E10 由 MES 按 §3.11 规则聚合各腔体 E10 得出；实时控制模式（offline/online/remote）由 EAP 维护（§3.12）。MDS 仅存行政生命期态 `lifecycle_status` 与「是否支持 remote」声明 `remote_capable`。
- **搬迁轨迹**：经 `EquipmentService`（继承 `AbstractJpaService`）`update` 改 `location_id` / 分类 / 行政态时，`@History(SNAPSHOT)` 自动落 `mds_equipment_hist`，含 `op_type`/`operator`/`op_time`/`trx_id`/`change_set_json`——与位置设计 §2.4 完全一致。
- 可选业务上下文（原因/单号）入 `mds_equipment_move_event`（§3.15）。

### 3.4 `mds_equipment_module`（设备模块/腔体，E157 设备内层级 + 腔体行政态）

继承 `BaseDefData`，`parent_module_id` 自引用表达模块树（Process/Transfer/Control Module、Chamber）。

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 所属设备 |
| `parent_module_id` | VARCHAR(32) | NULL（顶层模块） | 自引用 FK→本表 |
| `code` | VARCHAR(64) | 唯一 `(equipment_id,code,tenant_id,deleted)` | 模块编码（设备内唯一） |
| `name` | VARCHAR(128) | | 模块名 |
| `module_type` | VARCHAR(16) | NOT NULL | `PROCESS`/`TRANSFER`/`CONTROL`/`CHAMBER` |
| `chamber_no` | VARCHAR(16) | | 腔体号（module_type=CHAMBER 时） |
| `admin_status` | VARCHAR(16) | NOT NULL DEFAULT 'ENABLED' | **腔体行政态**：`ENABLED`/`DISABLED`/`STANDBY`/`MAINTENANCE`（见 §3.10，与实时 E10 分离） |
| `description` | VARCHAR(512) | | 基类 |

> **腔体行政态 vs 腔体实时 E10**：`admin_status` 是 MDS 拥有的**行政可可用性**（如该腔体因维护被 DISABLED，不再参与派工）；腔体**实时 E10**（PRD/IDL/…）由 MES 维护，MDS 不存。二者维度不同，不可混用。
> 与位置层级**严格分离**：此处是「设备内部」结构（一台工具由哪些腔体组成），位置层级是「厂房地理」结构。
> **与配方闭环**：CLUSTER/BATCH（`process_mode`）设备有多 PROCESS module，运行时各 module 可并行跑不同配方——每 module 单活跃配方（实时态归 MES），对应 §3.8 的多 recipe 资格。

### 3.5 `mds_equipment_port`（设备端口，E157 Port）

继承 `BaseDefData`。

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 所属设备 |
| `code` | VARCHAR(64) | 唯一 `(equipment_id,code,tenant_id,deleted)` | 端口编码（设备内唯一），如 `P1` |
| `name` | VARCHAR(128) | | 端口名 |
| `port_type` | VARCHAR(16) | NOT NULL | `LOAD`/`UNLOAD`/`BUFFER` |
| `admin_status` | VARCHAR(16) | NOT NULL DEFAULT 'ENABLED' | **端口行政态**：`ENABLED`/`DISABLED`（MDS 拥有的「是否在役可装卸」；与 E157 实时端口态分离，见 §3.14.5） |
| `state_model_id` | VARCHAR(32) | FK→`mds_equipment_state_model.id` | **引用的 E157 端口状态机定义**（model_kind=PORT_STATE，见 §3.14.5）；实时端口态实例归 EAP/AMHS，MDS 不存 |
| `description` | VARCHAR(512) | | 基类 |

> **端口行政态 vs E157 实时端口态（正交）**：`admin_status=ENABLED/DISABLED` 是 MDS 拥有的「该端口是否在役、能否参与装卸派工」；E157 定义的端口实时态（`NOTINIT`/`INIT`/`READY`/`NOTREADY`/`TRANSFERRING`…，列于 §3.14.5）是 EAP/AMHS 拥有的「此刻端口与载具的物理交互态」，由 `state_model_id` 指向其状态机**定义**，实时实例不存 MDS。二者维度不同，不可混用。

### 3.6 `mds_equipment_tech_node`（工艺节点，多值）

继承 `BaseDefData`；表达设备可覆盖的工艺节点（多值，正交维度之一）。

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 所属设备 |
| `tech_node` | VARCHAR(16) | NOT NULL | 如 `28`/`14`/`7`(nm) |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(equipment_id, tech_node, tenant_id, deleted)`。
- **闭环**：`tech_node` 是「产品 ↔ 设备能力」的筛选键——与 `mds_product.tech_node`（[产品主数据设计](product-design.md) §4.4）取交集，得出可制造某产品的设备池，再经 route operation 的 area/equipment_class/recipe 完成全链路闭环。

### 3.7 `mds_equipment_capability`（工艺能力，闭环关键）

继承 `BaseDefData`。把「设备能干什么工艺」与「位置层级中的 AREA」显式连接，形成闭环。

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 所属设备 |
| `area_code` | VARCHAR(64) | NOT NULL | 关联 `mds_location.code`（且 `level=AREA`）——闭环到位置 |
| `process_code` | VARCHAR(64) | | 工艺代码（如 `PHOTO_LITHO`） |
| `recipe_class` | VARCHAR(64) | | 配方类别（关联 `mds_recipe.recipe_class`） |
| `capability_type` | VARCHAR(16) | NOT NULL | `PROCESS`/`METROLOGY`/`CLEAN`/`OTHER` |
| `priority` | INT | DEFAULT 0 | 同 Area 内优先级（派工用） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(equipment_id, area_code, process_code, tenant_id, deleted)`。
- 闭环意义：即使设备物理上在 BAY B03，其 `capability.area_code` 可声明服务于多个 AREA（共享/跨区设备），解决了 D3「严格单父树」下跨区服务的建模需求（用能力关联而非破坏位置树）。

### 3.8 配方资格：单 recipe 还是多 recipe（SEMI E40/E94）

> **完整配方管理模型（LogicRecipe / PhysicalRecipe / RecipeGroup / PPID）见 [配方设计](recipe-design.md)。** 本节聚焦「设备 ↔ 配方资格」这一闭环环节。

行业现实：**一台设备合格可跑一组配方（多 recipe）**——这是「配方资格(qualification)」，设备↔配方为**多对多**；而**运行时每个处理腔体同一时刻只跑一个配方**（活跃配方是实时态，归 MES/EAP，MDS 不存）。

> 本节 `mds_recipe` 指**物理/设备配方**（工具相关）；其完整定义、`mds_logic_recipe`（逻辑配方）、`mds_recipe_group`（分组）、PPID 建模以 [配方设计](recipe-design.md) 为权威定义，本文保持一致不重复展开。

`equipment` 与 `mds_recipe`（物理配方）经资格表连接：

**`mds_recipe`（配方主数据）**

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `class_id` | VARCHAR(32) | FK→`mds_equipment_class.id` | 配方所属工艺大类 |
| `code` | VARCHAR(64) | 唯一 `(code,tenant_id,deleted)` | 配方编码 |
| `name` | VARCHAR(128) | | 配方名 |
| `process_code` | VARCHAR(64) | | 工艺代码 |
| `recipe_class` | VARCHAR(64) | | 配方类别（与 capability.recipe_class 对齐） |
| `revision` | VARCHAR(16) | | 版本 |
| `description` | VARCHAR(512) | | 基类 |

> 本表为**物理配方**主数据（工具相关）。完整列（`group_id`/`logic_recipe_id`/`status`/`is_frozen`/`effective_from/to`/`body_ref`/`ext_attrs`）与生命周期见 [配方设计 §3.3](recipe-design.md)；`logic_recipe_id` 指向其实现的逻辑配方（1 份逻辑配方 → 每设备类 1 份物理配方）。

> **配方正文(body)/参数**通常在外围 Recipe 系统（SEMI E40），MDS 至少维护资格所需的配方主引用。

> **闭环下游**：工艺路线 `mds_route_operation.recipe_id` 引用的是 **`mds_logic_recipe`（逻辑配方）**（见 [route-design.md](route-design.md) §3.3），派工时再据设备类解析到本表（物理配方）及其 PPID（[配方设计 §4.3](recipe-design.md)）。

**`mds_equipment_recipe_qual`（设备↔物理配方 多对多资格，含 PPID）**

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 设备 |
| `recipe_id` | VARCHAR(32) | NOT NULL, FK→`mds_recipe.id` | **物理**配方 |
| `ppid` | VARCHAR(128) | NOT NULL | **Process Program ID**：该物理配方在此设备上的程序标识（SEMI E40）；PPID 设备内唯一 |
| `qual_status` | VARCHAR(16) | NOT NULL | `QUALIFIED`/`PENDING`/`REVOKED` |
| `is_default` | BIT | DEFAULT 0 | 该设备资格集中默认物理配方 |
| `area_code` | VARCHAR(64) | | 可选：资格作用域（所在 AREA） |
| `qualified_by` | VARCHAR(64) | | 认证人 |
| `qualified_at` | DATETIME(3) | | 认证时间 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(equipment_id, recipe_id, tenant_id, deleted)`；PPID 设备内唯一：`(equipment_id, ppid, tenant_id, deleted)`。
- **单/多 recipe 统一表达**：资格集只有 1 条 = 单 recipe 设备（专用机）；多条 = 多 recipe 设备（柔性机）。无需不同表结构。
- 与模块闭环：CLUSTER/BATCH 设备多 PROCESS module，可对不同 module 各跑资格集内不同配方（并行），每 module 单活跃配方（实时态归 MES）。
- **派工解析**：给定设备 E 与 route 绑定的逻辑配方 L → 取 `mds_recipe(logic_recipe_id=L, class_id=E.class)` → 其 `qual(E, recipe).ppid` 下发 EAP（完整路径见 [配方设计 §4.3](recipe-design.md)）。

### 3.9 设备所有权：多 owner、多角色（本次补全）

fab 中一台设备往往**不是单一 owner**：硬件可能由 OEM 寄售（consignment），运营由某工艺部门负责，保养可能外包给厂商服务团队，安全责任另有归属。因此所有权是**多对多链接 + 角色化**的，不能只用 `ownership` 一个标志列表达。

建模一个链接实体 `mds_equipment_owner`：

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 设备 |
| `owner_type` | VARCHAR(16) | NOT NULL | 所有者类别：`VENDOR`(厂商/OEM) / `BUSINESS_UNIT` / `DEPARTMENT` / `COST_CENTER` / `PROJECT` |
| `owner_ref` | VARCHAR(64) | NOT NULL | 所有者引用：`owner_type=VENDOR` → `mds_vendor.code`；`DEPARTMENT`/`COST_CENTER`/`PROJECT` → `mds_org_unit.code`。**组织与供应商主数据见 [org-personnel-design.md](org-personnel-design.md)**（本列原先「无 master 可引用」的悬空问题已由该域补齐） |
| `owner_name` | VARCHAR(128) | | 所有者显示名（冗余便于展示） |
| `ownership_role` | VARCHAR(16) | NOT NULL | 所有权角色：`ASSET_OWNER`(资产所有) / `OPERATIONAL_OWNER`(运营负责) / `MAINT_OWNER`(保养负责) / `SAFETY_OWNER`(安全负责) |
| `is_primary` | BIT | DEFAULT 0 | 是否主负责人（每角色至多一个主） |
| `effective_from` | DATE | | 生效日（SCD2-lite，支持ownership变更历史） |
| `effective_to` | DATE | NULL | 失效日（NULL=当前有效） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(equipment_id, owner_ref, ownership_role, tenant_id, deleted)`。
- 与 `mds_equipment.ownership` 的关系：`ownership` 仅作**快速分类标志**（自有/外包）；`mds_equipment_owner` 是**详尽的多角色所有权明细**。二者可并存：当 `ownership=CONTRACTED` 时，通常存在 `owner_type=VENDOR` 的 `ASSET_OWNER` 记录。

> **为什么是实体而非列**：① 一台设备可有多个 owner（OEM + 部门 + 外包商）；② 同一 owner 可承担多个角色；③ 所有权会随业务变更（寄售转自有、保养外包切换），需 `effective_from/to` 留痕。这些都不是单列能承载的。

### 3.10 状态分层：行政态 / 实时态 / 资格态（本次补全，核心）

设备「状态」必须按**语义与归属**拆成三层，混用会导致派工与报表逻辑错乱：

| 状态层 | 拥有方 | 变更频率 | 存储位置 | 取值 |
| ------ | ------ | -------- | -------- | ---- |
| **① 行政生命期态** `lifecycle_status`（设备级） | **MDS** | 低（月/年） | `mds_equipment.lifecycle_status` | `PLANNED`→`INSTALLING`→`QUALIFYING`→`ACTIVE`→`STANDBY`→`MAINTENANCE`→`DECOMMISSIONED`→`RETIRED` |
| **② 腔体行政态** `admin_status`（腔体级） | **MDS** | 低-中 | `mds_equipment_module.admin_status` | `ENABLED`/`DISABLED`/`STANDBY`/`MAINTENANCE` |
| **③ 资格态** `qualification_state` | **MDS** | 中 | `mds_equipment.qualification_state` + `mds_equipment_recipe_qual.qual_status` | 量产/配置资格（非实时） |
| **④ 实时 E10 运行态**（设备级 + 腔体级） | **MES/EAP** | 高（秒/分） | MES 侧（MDS 不存） | PRD/IDL/SBY/ENG/NST/SDT/SDL/SDN/UDT/UDL/UDN/UPD/PRG/WUP… |
| **⑤ 通信/控制模式**（设备级） | **EAP 实时域**（定义+MDS 声明） | 中-高（连接/控制态变化） | 定义：`mds_equipment_state_model`(model_kind=CONTROL_MODE)+`mds_equipment.remote_capable`；实时实例：EAP（MDS 不存） | OFFLINE / ONLINE-LOCAL / ONLINE-REMOTE（底层 COMM_ENABLED/DISABLED） |

**关键规则：**
1. **MDS 永远不存实时 E10 实例**。MDS 只拥有**行政态**（① ②）与**资格态**（③），以及**E10 状态机的定义**（见 §3.11）。
2. **腔体行政态 ≠ 腔体实时 E10**。例如某腔体 `admin_status=MAINTENANCE`（MDS 标记停修，不参与派工），但其实时 E10 由 MES 实时上报（在 MDS 视角的「维护中」对应 MES 的 `SDT`/`UDN`）。二者维度正交。
3. **腔体行政态影响设备可用性**：派工时若设备某 PROCESS 腔体 `admin_status=DISABLED`，该腔体产能不可用；但若设备仍有其他 ENABLED 腔体，`lifecycle_status` 仍为 `ACTIVE`（设备可用、减容）。
4. **行政态与实时态的映射（参考，非强制存储）**：MES 在做 rollup 时，可参考 MDS 行政态——例如 `lifecycle_status` 非 `ACTIVE`（如 `MAINTENANCE`/`DECOMMISSIONED`）时，设备级 E10 聚合结果应被忽略或标为非生产。

> **状态转换模型（迁移约束）汇总**：上述各状态层的「允许迁移边」由 §3.14 的**统一状态转换模型**集中定义——设备行政生命期态（§3.14.1）、腔体行政态（§3.14.2）、E10 运行态（§3.14.3 / §3.11）、控制模式（§3.14.4 / §3.12）、端口 E157 态（§3.14.5）。所有状态机共用同一张「状态目录 + 迁移边」表，按 `model_kind` 区分。
>
> **触发源补充（补悬空点）**：本节定义的是「**允许怎么迁**」，而「**何时该迁**」的触发源在 [pm-calibration-design.md](pm-calibration-design.md) §4.3/§4.4——**PM 到期**触发 `lifecycle_status=MAINTENANCE` / `module.admin_status=MAINTENANCE`；**校准超期或超差**触发 `qualification_state` 失效。两处配合，设备状态机才完整闭环。

### 3.11 状态机与模式定义框架：E10 状态 + 通信/控制模式（本次补全，核心）

SEMI E10 规定了一组标准状态与合法迁移，但**不同设备类可裁剪**（如量测设备无 PRD，仅有 IDLE/ENG/SD）。因此 MDS 拥有**状态机的定义（目录 + 迁移边）**，MES 拥有**实时实例**并据此校验迁移合法性。

**`mds_equipment_state_model`（状态机定义）**

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `code` | VARCHAR(32) | 唯一 `(code,tenant_id,deleted)` | 如 `E10_STD`、`LITHO_CUSTOM` |
| `name` | VARCHAR(128) | | 显示名 |
| `is_default` | BIT | DEFAULT 0 | 是否默认（model 未显式引用时套用） |
| `description` | VARCHAR(512) | | 基类 |

> **`model_kind` 复用框架**：本表用同一套「状态目录 + 迁移边」结构承载 MDS 域内所有状态机，仅以 `model_kind` 区分——`EQUIP_LIFECYCLE`（设备行政生命期态，§3.14.1）、`MODULE_ADMIN`（腔体行政态，§3.14.2）、`E10_STATE`（设备/腔体实时 E10 运行态，§3.11）、`CONTROL_MODE`（通信/控制模式，§3.12）、`PORT_STATE`（端口 E157 实时态，§3.14.5），并可扩展 `CARRIER_STATE` 承载**载具 E87 状态目录**（见 [载具设计 §4.2](carrier-design.md)）。`mds_equipment_model` 经 `state_model_id` + `control_mode_model_id` 引用 E10/控制两类；`mds_equipment_port` 经 `state_model_id` 引用 PORT_STATE。统一框架实现跨域状态机定义复用，避免重复造轮子（详见 §3.14 元表）。

**`mds_equipment_state`（状态目录）**

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `state_model_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment_state_model.id` | 所属状态机 |
| `code` | VARCHAR(16) | NOT NULL | 如 `PRD`/`IDL`/`SBY`/`ENG`/`NST`/`SDT`/`SDL`/`SDN`/`UDT`/`UDL`/`UDN`/`UPD`/`PRG`/`WUP` |
| `name` | VARCHAR(128) | | 显示名 |
| `category` | VARCHAR(16) | NOT NULL | E10 大类的语义归类：`PRODUCTIVE`/`IDLE`/`STANDBY`/`ENGINEERING`/`SCHED_DOWN`/`UNPLANN_DOWN`/`SETUP`/`OTHER` |
| `is_productive` | BIT | DEFAULT 0 | 是否计入产出/可用性（PRD=1，其余=0） |
| `state_order` | INT | DEFAULT 0 | 展示排序 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(state_model_id, code, tenant_id, deleted)`。

**`mds_equipment_state_transition`（合法迁移边）**

| 列 | 类型 | 约束 | 说明 |
| -- | ---- | ---- | ---- |
| `id` / `tenant_id` | — | PK | 基类 |
| `state_model_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment_state_model.id` | 所属状态机 |
| `from_state_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment_state.id` | 源状态 |
| `to_state_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment_state.id` | 目标状态 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(state_model_id, from_state_id, to_state_id, tenant_id, deleted)`。
- MES 在收到设备实时 E10 变更时，查本表校验 `from→to` 是否合法；非法迁移应告警。

**E10 标准状态目录（示例：`E10_STD` 模型，model_kind=E10_STATE）**

SEMI E10 规定了一组标准状态，但**不同设备类可裁剪**（如量测设备无 PRD，仅 IDLE/ENG/SD）。下表为典型 `E10_STD` 目录；各厂/各 class 可建 `LITHO_CUSTOM` 等模型裁剪：

| `code` | 含义 | `category` | `is_productive` |
| ------ | ---- | ---------- | --------------- |
| `PRD` | 生产中（Productive） | PRODUCTIVE | 1 |
| `IDL` | 空闲（Idle） | IDLE | 0 |
| `SBY` | 待机（Standby） | STANDBY | 0 |
| `ENG` | 工程模式（Engineering） | ENGINEERING | 0 |
| `NST` | 非计划停机时间（Non-Scheduled Time） | SCHED_DOWN | 0 |
| `SDT` | 计划停机-测试（Sched Down-Test） | SCHED_DOWN | 0 |
| `SDL` | 计划停机-批次（Sched Down-Lot） | SCHED_DOWN | 0 |
| `SDN` | 计划停机-无操作员（Sched Down-No Op） | SCHED_DOWN | 0 |
| `UDT` | 非计划停机-测试（Unplanned Down-Test） | UNPLANN_DOWN | 0 |
| `UDL` | 非计划停机-批次（Unplanned Down-Lot） | UNPLANN_DOWN | 0 |
| `UDN` | 非计划停机-无操作员（Unplanned Down-No Op） | UNPLANN_DOWN | 0 |
| `UPD` | 用户预定义停机（User Pre-Defined Down） | OTHER | 0 |
| `PRG` | 生产准备（Productive-Generic，如装载后到正式生产前） | PRODUCTIVE | 1 |
| `WUP` | 暖机（Warm Up） | SETUP | 0 |

> `category` 与 `is_productive` 是 MES 做可用性（RAM/可用性=PRD+PRG 时间占比）与产能计算的一致语义基础。

**E10 合法迁移表（节选 `E10_STD` 的关键边，完整 `from→to` 集由 `mds_equipment_state_transition` 配置）**

| 源态 from | 可迁移至 to（集合） | 触发/语义 |
| --------- | ------------------ | --------- |
| `IDL` | `PRD`, `PRG`, `ENG`, `SBY`, `WUP`, `NST`, `SDT/SDL/SDN`, `UDT/UDL/UDN` | 空闲可进入生产/工程/待机/暖机/各类停机 |
| `PRD` / `PRG` | `IDL`, `ENG`, `SBY`, `NST`, `SDT/SDL/SDN`, `UDT/UDL/UDN` | 生产中可结束回空闲，或转工程/待机/停机 |
| `SBY` | `IDL`, `PRD`, `ENG`, `NST`, `SDT/SDL/SDN`, `UDT/UDL/UDN` | 待机可恢复生产/空闲 |
| `WUP` | `IDL`, `PRD` | 暖机完成进入生产或空闲 |
| `ENG` | `IDL`, `PRD`, `NST`, `SDT/SDL/SDN`, `UDT/UDL/UDN` | 工程结束 |
| `SDT`/`SDL`/`SDN` | `IDL`, `UDT/UDL/UDN` | 计划停机结束回空闲；若中途恶化转非计划停机 |
| `UDT`/`UDL`/`UDN` | `IDL` | 非计划停机恢复（唯一出口是空闲） |
| `NST` | `IDL`, `SDT/SDL/SDN`, `UDT/UDL/UDN` | 非计划时段结束 |

> **迁移规则要点**：所有 DOWN 态（`SD*`/`UD*`/`NST`）的唯一正向出口是 `IDL`（恢复）；所有非 DOWN 态均可进入 `SD*`/`UD*`；`PRD`/`PRG` 不可直达另一 `DOWN` 之外的「更深」态。具体边集以 `mds_equipment_state_transition` 配置为准，MES 据此实时校验；非法迁移（如 `UDN→PRD` 跳过 `IDL`）应告警。

**设备级 E10 聚合（rollup）规则（由 MES 实现，MDS 以文档约定）**

```
设备级 E10 = f(各腔体实时 E10)
  - 任一 PROCESS 腔体 PRD              → 设备 PRD
  - 全部 PROCESS 腔体 IDLE/STANDBY      → 设备 IDLE（或 SBY，按策略）
  - 任一 PROCESS 腔体 UNPLANNED_DOWN    → 设备 UDN（降级）
  - 全部 PROCESS 腔体 SCHEDULED_DOWN    → 设备 SD（取最久的那条原因）
  - 无 PROCESS 腔体（如量测台）         → 按设备本身 E10（IDLE/ENG/SD…）
```
> MDS 提供 `state_model` 与 `state.category/is_productive`，使 MES 的聚合与可用性计算（RAM）有一致语义基础；但**聚合动作本身在 MES**，MDS 不存结果。

### 3.12 通信/控制模式（offline / online / remote，本次补全）

fab 设备除了 E10「在干什么」（运行态）和「处于什么行政期」（生命期态），还有一个独立维度：**与主机（MES/EAP）的通信连接状态与控制权归属**。这正是用户说的 offline / online / remote，对应 SEMI E5 / E30（GEM）的 **Control State（控制状态）**：

| 模式 | 含义 | 控制权 | 通信 |
| ---- | ---- | ---- | ---- |
| `OFFLINE` | 离线，不与主机通信 | 设备本地 | COMM_DISABLED |
| `ONLINE-LOCAL` | 在线但本地控制（操作员在手） | 设备本地 | COMM_ENABLED |
| `ONLINE-REMOTE` | 在线且远程/主机控制（fab 自动化期望态） | 主机(MES/EAP) | COMM_ENABLED |

> 底层还有一层**通信使能状态** `COMM_ENABLED` / `COMM_DISABLED`（链路是否起来）；`OFFLINE` 等价于通信未使能或人工脱离，`ONLINE-*` 等价于通信已使能，再区分控制权在本地还是主机。

**建模原则（与 §3.11 E10 状态机一致）：MDS 拥有「定义 + 能力声明」，EAP 拥有「实时实例」。** 实时控制模式秒级变化，MDS 不存。

1. **定义复用状态机框架**：在 `mds_equipment_state_model` 增加 `model_kind=CONTROL_MODE`，其下 `mds_equipment_state` 存放 `OFFLINE`/`ONLINE-LOCAL`/`ONLINE-REMOTE`（或按厂扩展），`mds_equipment_state_transition` 存放合法迁移（如 `OFFLINE → ONLINE-LOCAL → ONLINE-REMOTE`）。EAP 实时变更时校验迁移合法性。
2. **设备能力声明（MDS 拥有，静态）**：`mds_equipment.remote_capable` 标记该设备是否支持 `ONLINE-REMOTE`（部分老旧或手动设备仅支持 `LOCAL`/`OFFLINE`）。设备型号 `mds_equipment_model.control_mode_model_id` 引用其控制模式模型。
3. **实时实例（EAP 拥有）**：当前控制模式由 EAP 实时上报/维护，MDS 不存。派工与 E10 rollup 需结合控制模式——例如设备须 `ONLINE-REMOTE` 才接受主机自动派工；`OFFLINE`/`ONLINE-LOCAL` 时主机无法自动调度。

**控制模式状态目录与合法迁移表（model_kind=CONTROL_MODE）**

| `code` | 含义 | 通信链路 | 控制权 |
| ------ | ---- | -------- | ------ |
| `OFFLINE` | 离线 | COMM_DISABLED | 设备本地 |
| `ONLINE-LOCAL` | 在线本地 | COMM_ENABLED | 设备本地（操作员在手） |
| `ONLINE-REMOTE` | 在线远程 | COMM_ENABLED | 主机(MES/EAP) |

| 源态 from | 可迁移至 to | 触发/语义 | 约束 |
| --------- | ---------- | --------- | ---- |
| `OFFLINE` | `ONLINE-LOCAL` | 通信使能（Comm Enable） | 必经 LOCAL |
| `ONLINE-LOCAL` | `ONLINE-REMOTE` | 主机请求接管（Request Enable / Control Change） | 须经操作员/主机握手；若 `remote_capable=0` 则禁止 |
| `ONLINE-REMOTE` | `ONLINE-LOCAL` | 主机释放控制权（Control Change 回本地） | — |
| `ONLINE-LOCAL` | `OFFLINE` | 通信禁用（Comm Disable） | — |
| `ONLINE-REMOTE` | `OFFLINE` | 通信禁用 / 链路中断 | 链路中断时 EAP 应告警并回退 |
| `OFFLINE` | `ONLINE-REMOTE` | — | **禁止直达**：必须 `OFFLINE→LOCAL→REMOTE` |

> 关键约束：**`OFFLINE` 不可直达 `ONLINE-REMOTE`**（SEMI E30 要求控制权交接经 LOCAL 握手）；且 `remote_capable=0` 的设备永远无法进入 `ONLINE-REMOTE`。EAP 在变更实时控制模式时校验上表。

> 与 §3.10/§3.11 的关系：行政生命期态（①）、腔体行政态（②）、E10 运行态（④）、**控制模式（⑤）** 是四个彼此正交的维度。一台 `ACTIVE` 设备可能此刻 `ONLINE-REMOTE` 且 E10=`PRD`（正常生产），也可能 `ONLINE-LOCAL`（在线但人工控制，需操作员确认），或 `OFFLINE`（通信中断，需 EAP 告警）。
>
> （若需 MDS 侧缓存最近已知控制模式用于可用性看板，可作为扩展列 `last_known_control_mode`，但这属于冗余缓存、非权威，默认不引入。）

### 3.14 统一状态转换模型（State Transition Model，核心）

设备、模块、端口涉及**多套正交的状态机**，每一套都用 **§3.11 的同一框架**（`mds_equipment_state_model` + `mds_equipment_state` + `mds_equipment_state_transition`）描述「状态目录 + 合法迁移边」，仅以 `model_kind` 区分。区别只在**实例归属与校验执行点**：行政态由 MDS 持有实例并强校验，实时态由 MES/EAP 持有实例、MDS 仅提供定义供其订阅校验。

**`model_kind` 元表**

| `model_kind` | 状态机 | 实例存储 | 校验/执行点 | 定义归属 |
| ------------ | ------ | -------- | ----------- | -------- |
| `EQUIP_LIFECYCLE` | 设备行政生命期态 | `mds_equipment.lifecycle_status`（MDS） | **MDS 强校验**（`EquipmentService.transitionLifecycle`） | MDS |
| `MODULE_ADMIN` | 腔体/模块行政态 | `mds_equipment_module.admin_status`（MDS） | **MDS 强校验**（`EquipmentModuleService.transitionAdminStatus`） | MDS |
| `E10_STATE` | 设备/腔体实时 E10 运行态 | MES/EAP（MDS 不存） | MES 实时校验（订阅定义） | MDS（定义） |
| `CONTROL_MODE` | 通信/控制模式 | EAP（MDS 不存） | EAP 实时校验（订阅定义） | MDS（定义）+ 设备 `remote_capable` 声明 |
| `PORT_STATE` | 端口 E157 实时态 | EAP/AMHS（MDS 不存） | EAP/AMHS 实时校验（订阅定义） | MDS（定义） |
| `CARRIER_STATE` | 载具 E87 实时态 | AMHS（MDS 不存） | AMHS 实时校验 | MDS（定义，见 [载具设计 §4.2](carrier-design.md)） |

> **统一收益**：五套状态机的「定义（目录+迁移）」集中在同一张表里，MES/EAP 只需拉一份 `model_kind=X` 的模型即可获得状态集与合法迁移边；新增设备类定制状态机 = 新增一条 `mds_equipment_state_model` + 其状态与迁移，无需改表结构。

#### 3.14.1 设备行政生命期态（`EQUIP_LIFECYCLE`，MDS 强校验）

| `code` | 含义 |
| ------ | ---- |
| `PLANNED` | 规划中（已立项，未到场） |
| `INSTALLING` | 安装中 |
| `QUALIFYING` | 认证/试产中 |
| `ACTIVE` | 投产（可派工） |
| `STANDBY` | 热 standby（可用但暂不在产） |
| `MAINTENANCE` | 维护中（减容/停机保养） |
| `DECOMMISSIONED` | 退役下架（不再派工） |
| `RETIRED` | 报废（终态） |

| 源态 from | 可迁移至 to | 语义 |
| --------- | ---------- | ---- |
| `PLANNED` | `INSTALLING` | 开始安装 |
| `INSTALLING` | `QUALIFYING`, `PLANNED` | 安装完成转认证；或取消回规划 |
| `QUALIFYING` | `ACTIVE`, `STANDBY`, `MAINTENANCE`, `PLANNED` | 认证通过转投产/热备；认证失败回规划 |
| `ACTIVE` | `STANDBY`, `MAINTENANCE`, `DECOMMISSIONED` | 降备/维护/退役 |
| `STANDBY` | `ACTIVE`, `MAINTENANCE`, `DECOMMISSIONED` | 恢复生产/维护/退役 |
| `MAINTENANCE` | `ACTIVE`, `STANDBY`, `DECOMMISSIONED` | 维护完成恢复/退役 |
| `DECOMMISSIONED` | `RETIRED`, `MAINTENANCE` | 报废；或重新启用（recommission） |
| `RETIRED` | — | 终态，不可迁移 |

#### 3.14.2 腔体/模块行政态（`MODULE_ADMIN`，MDS 强校验）

| `code` | 含义 |
| ------ | ---- |
| `ENABLED` | 启用（可参与派工） |
| `DISABLED` | 禁用（人工摘出，不派工） |
| `STANDBY` | 热备（可用但留作缓冲） |
| `MAINTENANCE` | 维护中（保养/检修） |

| 源态 from | 可迁移至 to | 语义 |
| --------- | ---------- | ---- |
| `ENABLED` | `DISABLED`, `STANDBY`, `MAINTENANCE` | 摘出/转备/转维护 |
| `DISABLED` | `ENABLED`, `MAINTENANCE` | 重新启用/转维护 |
| `STANDBY` | `ENABLED`, `DISABLED`, `MAINTENANCE` | 恢复/摘出/维护 |
| `MAINTENANCE` | `ENABLED`, `DISABLED`, `STANDBY` | 维护完成恢复/摘出/转备 |

> **与设备级联动**：某 PROCESS 腔体 `DISABLED`/`MAINTENANCE` 仅减该腔体产能；设备 `lifecycle_status` 仍为 `ACTIVE`（减容不退役）。设备整体 `MAINTENANCE` 应由 `EquipmentService` 联动将其下所有 PROCESS 模块置 `MAINTENANCE`（可选策略，见 §8）。

#### 3.14.3 设备/腔体实时 E10 运行态（`E10_STATE`）→ 见 §3.11

状态目录（PRD/IDL/SBY/ENG/NST/SDT/SDL/SDN/UDT/UDL/UDN/UPD/PRG/WUP）+ 合法迁移表见 §3.11。MDS 仅持有定义；**MES 持有实时实例并实时校验迁移**，设备级 E10 = 各腔体 E10 的 rollup（规则见 §3.11）。

#### 3.14.4 通信/控制模式（`CONTROL_MODE`）→ 见 §3.12

状态目录（OFFLINE/ONLINE-LOCAL/ONLINE-REMOTE）+ 合法迁移表（含 `OFFLINE` 不可直达 `REMOTE`、`remote_capable=0` 禁止 REMOTE）见 §3.12。MDS 持有定义 + `remote_capable` 声明；**EAP 持有实时实例并实时校验**。

#### 3.14.5 端口 E157 实时态（`PORT_STATE`，MDS 定义，EAP/AMHS 校验）

> 端口另有 MDS 拥有的行政态 `mds_equipment_port.admin_status`（`ENABLED`/`DISABLED`，表 §3.5）管「是否在役可装卸」；下表是 E157 定义的**端口实时交互态**，实例归 EAP/AMHS，MDS 仅持有其**状态机定义**（经 `mds_equipment_port.state_model_id` 引用）。

| `code` | 含义 |
| ------ | ---- |
| `NOTINIT` | 未初始化（无载具或端口离线） |
| `INIT` | 已初始化 |
| `READY` | 载具在位且可装卸（access 允许） |
| `NOTREADY` | 载具在位但被 interlock/阻塞，不可装卸 |
| `TRANSFERRING` | 载具传送中（AMHS 搬入/搬出） |

| 源态 from | 可迁移至 to | 触发/语义 |
| --------- | ---------- | --------- |
| `NOTINIT` | `INIT` | 端口初始化 |
| `INIT` | `READY`, `NOTINIT` | 载具就位；或去初始化 |
| `READY` | `NOTREADY`, `TRANSFERRING`, `NOTINIT` | interlock 阻塞；AMHS 搬出；载具取走 |
| `NOTREADY` | `READY`, `TRANSFERRING` | 阻塞解除；强制搬移 |
| `TRANSFERRING` | `READY`, `NOTINIT` | 搬入完成（载具在位）；搬出完成（无载具） |

> 与设备/腔体 E10 正交：端口态描述「载具物理交互」，不描述「设备在做工艺」。

#### 3.14.6 迁移校验服务设计（统一入口）

```
StateTransitionValidator
  └── validate(modelKind, fromState, toState)  // 查 mds_equipment_state_transition，非法则抛 IllegalStateTransitionException

EquipmentService.transitionLifecycle(eqpId, toStatus)
  └── 校验 EQUIP_LIFECYCLE 边 → 写 lifecycle_status + @History(SNAPSHOT) 落 mds_equipment_hist

EquipmentModuleService.transitionAdminStatus(moduleId, toStatus)
  └── 校验 MODULE_ADMIN 边 → 写 admin_status + @History

EquipmentStateModelService  // 定义维护 + 导出
  └── exportModel(modelKind) → 供 MES/EAP 订阅 E10_STATE / CONTROL_MODE / PORT_STATE / CARRIER_STATE 的状态目录与迁移边
```

- MDS 侧（行政态）写操作走 `AbstractJpaService`，自动落 `@History`；非法迁移在入口被 `StateTransitionValidator` 拦截，不写库。
- MES/EAP 侧（实时态）拉取 MDS 导出的状态模型后自行校验；MDS 不感知其实时值。

### 3.15 `mds_equipment_move_event`（搬迁业务事件，可选）

继承 `BaseEventData`（`biz_key`/`event_type`/`payload` + 审计）。与位置设计 §2.5 一致。

| 列 | 说明 |
| -- | ---- |
| `biz_key` | 设备 id |
| `event_type` | `MOVE` |
| `payload` | JSON：`{from_loc, to_loc, reason, wo_no, approver}` |

> 主时间线以 `mds_equipment_hist` 为准；本表仅补业务语义。

---

## 4. 关键设计要点（闭环保障）

### 4.1 设备内层级 vs 位置层级分离
- 位置层级（`mds_location`）= 厂房地理/工艺域拓扑，跨设备共享。
- 设备内层级（`mds_equipment_module` 自引用）= 单台工具内部结构（腔体/模块）。
- 二者通过 `mds_equipment.location_id` 衔接，互不嵌套、互不混淆。

### 4.2 设备 → 所属 Area 推导（向上闭环）
- 由 `mds_equipment.location_id` 取所在 BAY/SUBBAY，再查 `mds_location_closure`（`descendant = location_id` 且祖先 `level=AREA`）得到所属工艺区。
- 此推导供派工、按 Area 聚设备、Area 维度报表使用，无需冗余存储 area。

### 4.3 跨区服务（D3 的闭环解法）
- 位置树严格单父（D3）；跨区服务设备用 `mds_equipment_capability.area_code` 写多条能力记录，而非多父位置树。既保树结构干净，又满足「一台工具服务多 Area」。

### 4.4 搬迁轨迹复用平台
- 设备换 Bay = 改 `location_id` 经 `AbstractJpaService.update` → `@History(SNAPSHOT)` 自动落 `mds_equipment_hist`；与位置设计 §2.4 完全同源，不另造事件流。

### 4.5 分类：串行三级 + 平行正交轴（回答「串行还是并行」）
- **工艺归类用串行三级**：`equipment→model(Kind)→type→class` 外键链，层级清晰、可扩展。
- **正交属性用平行轴**：`process_mode`（串行单片/并行批次集群）、`grade`、`criticality`、`qualification_state`、`ownership`、以及多值 `tech_node`，彼此独立、不与工艺树嵌套。
- 混合模型兼顾「层级归类」与「多维描述」，避免把正交维度硬塞进一棵树导致爆炸式组合。

### 4.6 配方：合格集合（多 recipe）+ 每腔体单活跃（运行态归 MES）
- MDS 只存**资格集合**（`mds_equipment_recipe_qual`，多对多），天然支持单/多 recipe 两类设备。
- 活跃配方是实时态，由 MES/EAP 维护，MDS 不重复存储；CLUSTER/BATCH 多腔并行跑不同配方时，腔体级单活跃配方在 MES 侧。

### 4.7 所有权：多 owner 多角色（回答「owner 怎么建」）
- 不只用 `ownership` 标志列，而是用 `mds_equipment_owner` 链接实体表达**多所有者 + 多角色**（资产/运营/保养/安全）+ `effective_from/to` 留痕。
- OEM 寄售、部门运营、外包保养等复合所有权场景均可建模，且支持所有权变更历史。

### 4.8 状态分层：四态分离（回答「设备/腔体状态欠缺」）
- **MDS 拥有**：①设备级行政生命期态 `lifecycle_status`、②腔体行政态 `admin_status`、③资格态、④E10 状态机**定义**。
- **MES 拥有**：④的实时**实例**（设备级 E10 + 腔体级 E10）+ 活跃配方。
- 腔体行政态与腔体实时 E10 正交：前者管「是否参与派工」，后者管「此刻在干嘛」。
- 设备级实时 E10 = 各腔体 E10 的 rollup（规则由 MDS 的 `state_model` 语义定义、MES 计算）。

### 4.9 软删/租户/主键（与平台一致）
- 所有实体继承 `BaseDefData`：`String id`（雪花/UUIDv7）、`tenant_id`、`@Version`、`deleted`、审计列。
- 唯一约束均含 `(..., tenant_id, deleted)`（T2.5）。
- 行级过滤：`@SQLRestriction("deleted=false")` + `@Filter(cimTenantFilter)`；`@FilterDef` 放实体包 `package-info.java`。

### 4.10 通信/控制模式：定义归 MDS、实时归 EAP（回答「offline/online/remote 怎么建」）
- fab 设备有独立维度：**与主机(MES/EAP)的通信连接与控制权归属**，即用户说的 offline/online/remote，对应 SEMI E5/E30 的 Control State（`OFFLINE` / `ONLINE-LOCAL` / `ONLINE-REMOTE`，底层 `COMM_ENABLED/DISABLED`）。
- **MDS 拥有定义与能力声明**：`mds_equipment_state_model`（model_kind=CONTROL_MODE）存放模式目录与合法迁移；`mds_equipment.remote_capable` 声明是否支持远程控制；型号 `control_mode_model_id` 引用模型。
- **EAP 拥有实时实例**：当前控制模式秒级变化，由 EAP 维护与上报，MDS 不存；派工需结合控制模式（仅 `ONLINE-REMOTE` 接受主机自动派工）。
- 与行政态/E10 运行态/腔体行政态**正交**，构成四态之外的第五个维度（§3.10）。

---

## 5. 与平台底座衔接（强制对齐）

| 能力 | 平台机制 | 本设计用法 |
| ---- | -------- | ---------- |
| 基类/审计 | `BaseDefData` / `BaseEventData` | 全部设备实体继承 |
| 历史快照 | `@History(SNAPSHOT)` → `{X}Hist` | `mds_equipment` 搬迁与属性变更轨迹 |
| 多租户 | `cimTenantFilter` + `TenantContext` | 设备/模块/端口/能力/分类/配方/owner/状态定义均标注 |
| 软删唯一 | `(..., tenant_id, deleted)` + `@SQLRestriction` | T2.5 兼容 |
| 主键 | `IdGenerator` | 所有实体 `String id` |
| 迁移 | 主/历成对 + Flyway `db/migration/{common,mysql,...}` | 见 §6 |

---

## 6. 迁移与 DDL（示例：MySQL / common）

> 遵循平台 §6.2「主/历成对」：`mds_equipment` 与其 `mds_equipment_hist` 在同一迁移文件内成对。其余主数据表自带审计列即可。

```sql
-- V2__equipment.sql

-- 分类串行三级
CREATE TABLE mds_equipment_class (
  id          VARCHAR(32)  NOT NULL,
  tenant_id   VARCHAR(32)  NOT NULL,
  code        VARCHAR(32)  NOT NULL,
  name        VARCHAR(128),
  description VARCHAR(512),
  version     BIGINT       NOT NULL DEFAULT 0,
  deleted     BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqp_class PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqp_class_code UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_equipment_type (
  id          VARCHAR(32)  NOT NULL,
  tenant_id   VARCHAR(32)  NOT NULL,
  class_id    VARCHAR(32)  NOT NULL,
  code        VARCHAR(32)  NOT NULL,
  name        VARCHAR(128),
  description VARCHAR(512),
  version     BIGINT       NOT NULL DEFAULT 0,
  deleted     BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqp_type PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqp_type_code UNIQUE (class_id, code, tenant_id, deleted),
  CONSTRAINT fk_eqt_class FOREIGN KEY (class_id) REFERENCES mds_equipment_class (id)
);

CREATE TABLE mds_equipment_model (
  id                VARCHAR(32)  NOT NULL,
  tenant_id         VARCHAR(32)  NOT NULL,
  type_id           VARCHAR(32)  NOT NULL,
  code              VARCHAR(64)  NOT NULL,
  name              VARCHAR(128),
  manufacturer      VARCHAR(128),
  model_no          VARCHAR(128),
  state_model_id    VARCHAR(32),
  control_mode_model_id VARCHAR(32),
  description       VARCHAR(512),
  version           BIGINT       NOT NULL DEFAULT 0,
  deleted           BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_equipment_model PRIMARY KEY (id),
  CONSTRAINT uq_mds_equipment_model_code UNIQUE (type_id, code, tenant_id, deleted),
  CONSTRAINT fk_eqm_type FOREIGN KEY (type_id) REFERENCES mds_equipment_type (id),
  CONSTRAINT fk_eqm_state_model FOREIGN KEY (state_model_id) REFERENCES mds_equipment_state_model (id),
  CONSTRAINT fk_eqm_ctrl_model  FOREIGN KEY (control_mode_model_id) REFERENCES mds_equipment_state_model (id)
);

-- E10 状态机定义（MDS 拥有，MES 引用）
CREATE TABLE mds_equipment_state_model (
  id          VARCHAR(32)  NOT NULL,
  tenant_id   VARCHAR(32)  NOT NULL,
  code        VARCHAR(32)  NOT NULL,
  model_kind  VARCHAR(16)  NOT NULL DEFAULT 'E10_STATE',
  name        VARCHAR(128),
  is_default  BIT          DEFAULT 0,
  description VARCHAR(512),
  version     BIGINT       NOT NULL DEFAULT 0,
  deleted     BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqp_state_model PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqp_state_model_code UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_equipment_state (
  id              VARCHAR(32)  NOT NULL,
  tenant_id       VARCHAR(32)  NOT NULL,
  state_model_id  VARCHAR(32)  NOT NULL,
  code            VARCHAR(16)  NOT NULL,
  name            VARCHAR(128),
  category        VARCHAR(16)  NOT NULL,
  is_productive   BIT          DEFAULT 0,
  state_order     INT          DEFAULT 0,
  description     VARCHAR(512),
  version         BIGINT       NOT NULL DEFAULT 0,
  deleted         BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_equipment_state PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqp_state UNIQUE (state_model_id, code, tenant_id, deleted),
  CONSTRAINT fk_eqs_model FOREIGN KEY (state_model_id) REFERENCES mds_equipment_state_model (id)
);

CREATE TABLE mds_equipment_state_transition (
  id              VARCHAR(32)  NOT NULL,
  tenant_id       VARCHAR(32)  NOT NULL,
  state_model_id  VARCHAR(32)  NOT NULL,
  from_state_id   VARCHAR(32)  NOT NULL,
  to_state_id     VARCHAR(32)  NOT NULL,
  description     VARCHAR(512),
  version         BIGINT       NOT NULL DEFAULT 0,
  deleted         BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqp_state_trans PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqp_state_trans UNIQUE (state_model_id, from_state_id, to_state_id, tenant_id, deleted),
  CONSTRAINT fk_eqt_model FOREIGN KEY (state_model_id) REFERENCES mds_equipment_state_model (id),
  CONSTRAINT fk_eqt_from  FOREIGN KEY (from_state_id)  REFERENCES mds_equipment_state (id),
  CONSTRAINT fk_eqt_to    FOREIGN KEY (to_state_id)     REFERENCES mds_equipment_state (id)
);

-- 设备主记录
CREATE TABLE mds_equipment (
  id                  VARCHAR(32)  NOT NULL,
  tenant_id           VARCHAR(32)  NOT NULL,
  code                VARCHAR(64)  NOT NULL,
  name                VARCHAR(128),
  model_id            VARCHAR(32)  NOT NULL,
  location_id         VARCHAR(32)  NOT NULL,
  lifecycle_status    VARCHAR(16)  NOT NULL,
  process_mode        VARCHAR(16)  NOT NULL,
  grade               VARCHAR(16)  NOT NULL,
  criticality         VARCHAR(16)  NOT NULL,
  qualification_state VARCHAR(16)  NOT NULL,
  ownership           VARCHAR(16)  NOT NULL,
  remote_capable      BIT          NOT NULL DEFAULT 1,
  serial_no           VARCHAR(128),
  asset_no            VARCHAR(128),
  commission_date     DATE,
  description         VARCHAR(512),
  version             BIGINT       NOT NULL DEFAULT 0,
  deleted             BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_equipment PRIMARY KEY (id),
  CONSTRAINT uq_mds_equipment_code UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_equipment_model  FOREIGN KEY (model_id)    REFERENCES mds_equipment_model (id),
  CONSTRAINT fk_equipment_loc    FOREIGN KEY (location_id) REFERENCES mds_location (id)
);
CREATE INDEX idx_equipment_loc    ON mds_equipment (location_id);
CREATE INDEX idx_equipment_model  ON mds_equipment (model_id);
CREATE INDEX idx_equipment_mode   ON mds_equipment (process_mode);
CREATE INDEX idx_equipment_grade  ON mds_equipment (grade);
CREATE INDEX idx_equipment_lc     ON mds_equipment (lifecycle_status);
CREATE INDEX idx_equipment_remote ON mds_equipment (remote_capable);

-- 主/历成对：SNAPSHOT 历史表
CREATE TABLE mds_equipment_hist (
  history_id     VARCHAR(32) NOT NULL,
  biz_id         VARCHAR(32) NOT NULL,
  op_type        VARCHAR(8)  NOT NULL,
  op_time        DATETIME(3),
  operator       VARCHAR(64),
  trx_id         VARCHAR(64),
  revision       BIGINT,
  change_set_json TEXT,
  tenant_id      VARCHAR(32),
  -- 业务字段同构镜像
  id                  VARCHAR(32),
  code                VARCHAR(64),
  name                VARCHAR(128),
  model_id            VARCHAR(32),
  location_id         VARCHAR(32),
  lifecycle_status    VARCHAR(16),
  process_mode        VARCHAR(16),
  grade               VARCHAR(16),
  criticality         VARCHAR(16),
  qualification_state VARCHAR(16),
  ownership           VARCHAR(16),
  remote_capable      BIT,
  serial_no           VARCHAR(128),
  asset_no            VARCHAR(128),
  commission_date     DATE,
  CONSTRAINT pk_mds_equipment_hist PRIMARY KEY (history_id)
);

CREATE TABLE mds_equipment_module (
  id                VARCHAR(32)  NOT NULL,
  tenant_id         VARCHAR(32)  NOT NULL,
  equipment_id      VARCHAR(32)  NOT NULL,
  parent_module_id  VARCHAR(32),
  code              VARCHAR(64)  NOT NULL,
  name              VARCHAR(128),
  module_type       VARCHAR(16)  NOT NULL,
  chamber_no        VARCHAR(16),
  admin_status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
  description       VARCHAR(512),
  version           BIGINT       NOT NULL DEFAULT 0,
  deleted           BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_equipment_module PRIMARY KEY (id),
  CONSTRAINT uq_mds_equipment_module_code UNIQUE (equipment_id, code, tenant_id, deleted),
  CONSTRAINT fk_em_equipment FOREIGN KEY (equipment_id)     REFERENCES mds_equipment (id),
  CONSTRAINT fk_em_parent    FOREIGN KEY (parent_module_id) REFERENCES mds_equipment_module (id)
);
CREATE INDEX idx_em_equipment ON mds_equipment_module (equipment_id);
CREATE INDEX idx_em_admin     ON mds_equipment_module (admin_status);

CREATE TABLE mds_equipment_port (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  equipment_id  VARCHAR(32)  NOT NULL,
  code          VARCHAR(64)  NOT NULL,
  name          VARCHAR(128),
  port_type     VARCHAR(16)  NOT NULL,
  admin_status  VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
  state_model_id VARCHAR(32),
  description   VARCHAR(512),
  version       BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_equipment_port PRIMARY KEY (id),
  CONSTRAINT uq_mds_equipment_port_code UNIQUE (equipment_id, code, tenant_id, deleted),
  CONSTRAINT fk_ep_equipment FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id),
  CONSTRAINT fk_ep_state_model FOREIGN KEY (state_model_id) REFERENCES mds_equipment_state_model (id)
);
CREATE INDEX idx_ep_equipment ON mds_equipment_port (equipment_id);
CREATE INDEX idx_ep_admin     ON mds_equipment_port (admin_status);

CREATE TABLE mds_equipment_tech_node (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  equipment_id  VARCHAR(32)  NOT NULL,
  tech_node     VARCHAR(16)  NOT NULL,
  description   VARCHAR(512),
  version       BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_equipment_tech_node PRIMARY KEY (id),
  CONSTRAINT uq_mds_equipment_tn UNIQUE (equipment_id, tech_node, tenant_id, deleted),
  CONSTRAINT fk_etn_equipment FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id)
);
CREATE INDEX idx_etn_equipment ON mds_equipment_tech_node (equipment_id);

-- 设备所有权（多 owner 多角色）
CREATE TABLE mds_equipment_owner (
  id              VARCHAR(32)  NOT NULL,
  tenant_id       VARCHAR(32)  NOT NULL,
  equipment_id    VARCHAR(32)  NOT NULL,
  owner_type      VARCHAR(16)  NOT NULL,
  owner_ref       VARCHAR(64)  NOT NULL,
  owner_name      VARCHAR(128),
  ownership_role  VARCHAR(16)  NOT NULL,
  is_primary      BIT          DEFAULT 0,
  effective_from  DATE,
  effective_to    DATE,
  description     VARCHAR(512),
  version         BIGINT       NOT NULL DEFAULT 0,
  deleted         BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_equipment_owner PRIMARY KEY (id),
  CONSTRAINT uq_mds_equipment_owner UNIQUE (equipment_id, owner_ref, ownership_role, tenant_id, deleted),
  CONSTRAINT fk_eo_equipment FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id)
);
CREATE INDEX idx_eo_equipment ON mds_equipment_owner (equipment_id);
CREATE INDEX idx_eo_owner     ON mds_equipment_owner (owner_ref);

CREATE TABLE mds_equipment_capability (
  id              VARCHAR(32)  NOT NULL,
  tenant_id       VARCHAR(32)  NOT NULL,
  equipment_id    VARCHAR(32)  NOT NULL,
  area_code       VARCHAR(64)  NOT NULL,
  process_code    VARCHAR(64),
  recipe_class    VARCHAR(64),
  capability_type VARCHAR(16)  NOT NULL,
  priority        INT          DEFAULT 0,
  description     VARCHAR(512),
  version         BIGINT       NOT NULL DEFAULT 0,
  deleted         BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_equipment_cap PRIMARY KEY (id),
  CONSTRAINT uq_mds_equipment_cap UNIQUE (equipment_id, area_code, process_code, tenant_id, deleted),
  CONSTRAINT fk_ec_equipment FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id)
  -- area_code: 跨域逻辑引用（服务层校验），不建 DB 级 FK —— 见 00-blueprint §11
);
CREATE INDEX idx_ec_equipment ON mds_equipment_capability (equipment_id);
CREATE INDEX idx_ec_area      ON mds_equipment_capability (area_code);

-- ⚠️ 单一来源（2026-09-23 修订）
-- mds_recipe / mds_equipment_recipe_qual（及 mds_recipe_group / mds_logic_recipe）的权威 DDL 见
--   recipe-design.md §6 —— 本文不再重复定义。
-- 早前版本此处保留的一份简化版 mds_recipe 与 recipe-design 不一致（缺 unfreeze_at、多 process_code），
-- 且会让读者误按旧口径建表，已删除。设备与配方的闭环关系见本文 §3.8。

CREATE TABLE mds_equipment_move_event (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  biz_key       VARCHAR(32)  NOT NULL,
  event_type    VARCHAR(32)  NOT NULL,
  payload       TEXT,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_equipment_move_event PRIMARY KEY (id),
  CONSTRAINT fk_eme_equipment FOREIGN KEY (biz_key) REFERENCES mds_equipment (id)
);
CREATE INDEX idx_eme_biz ON mds_equipment_move_event (biz_key);
```

---

## 7. 代码结构

```
com.cim.mds.server
├── location                # 见 location-design.md
└── equipment
    ├── domain
    │   ├── Equipment.java               # @Entity 继承 BaseDefData + @History(SNAPSHOT)；含 lifecycle_status、remote_capable
    │   ├── EquipmentClass.java           # @Entity 继承 BaseDefData
    │   ├── EquipmentType.java            # @Entity 继承 BaseDefData（class_id FK）
    │   ├── EquipmentModel.java           # @Entity 继承 BaseDefData（type_id FK，=Kind；state_model_id + control_mode_model_id FK）
    │   ├── EquipmentModule.java          # @Entity 继承 BaseDefData（parent_module_id 自引用；admin_status 腔体行政态）
    │   ├── EquipmentPort.java            # @Entity 继承 BaseDefData（admin_status + state_model_id→PORT_STATE）
    │   ├── EquipmentTechNode.java        # @Entity 继承 BaseDefData（多值工艺节点）
    │   ├── EquipmentCapability.java      # @Entity 继承 BaseDefData（area_code → mds_location）
    │   ├── EquipmentOwner.java           # @Entity 继承 BaseDefData（多 owner 多角色）
    │   ├── EquipmentStateModel.java       # @Entity 继承 BaseDefData（状态机/模式定义；model_kind 区分 EQUIP_LIFECYCLE/MODULE_ADMIN/E10_STATE/CONTROL_MODE/PORT_STATE/CARRIER_STATE）
    │   ├── EquipmentState.java            # @Entity 继承 BaseDefData（状态目录；category/is_productive）
    │   ├── EquipmentStateTransition.java  # @Entity 继承 BaseDefData（合法迁移边）
    │   ├── Recipe.java                   # @Entity 继承 BaseDefData
    │   ├── EquipmentRecipeQual.java      # @Entity 继承 BaseDefData（多对多资格）
    │   ├── EquipmentMoveEvent.java        # @Entity 继承 BaseEventData
    │   └── repo/*Repository.java          # extends BaseRepository
    └── service
        ├── EquipmentService.java          # extends AbstractJpaService<Equipment>（触发历史）
        ├── EquipmentClassService.java
        ├── EquipmentTypeService.java
        ├── EquipmentModelService.java
        ├── EquipmentModuleService.java     # 维护腔体行政态（transitionAdminStatus 强校验 MODULE_ADMIN）
        ├── EquipmentPortService.java        # 维护端口 admin_status
        ├── EquipmentTechNodeService.java
        ├── EquipmentCapabilityService.java
        ├── EquipmentOwnerService.java      # 多 owner 维护（含 effective_from/to）
        ├── EquipmentStateModelService.java  # 状态机定义维护 + exportModel(modelKind) 供 MES/EAP 订阅
        ├── StateTransitionValidator.java    # 统一迁移校验：validate(modelKind, from, to) 查 mds_equipment_state_transition
        ├── RecipeService.java
        └── EquipmentRecipeQualService.java
```

> `EquipmentService.transitionLifecycle()` 与 `EquipmentModuleService.transitionAdminStatus()` 在写库前调用 `StateTransitionValidator.validate(...)`；非法迁移抛 `IllegalStateTransitionException`，不落库。MES/EAP 侧对 E10/控制/端口实时态的校验，通过 `EquipmentStateModelService.exportModel(modelKind)` 拉取同名状态模型后自行执行（§3.14.6）。

---

## 8. 待补 / 后续

- **扩展分类维度（EAV）**：各厂自定义维度（如 `vendor_family`、`tool_grade_detail`）可用 `mds_equipment_attribute`（实体-键-值/标签）兜底，避免频繁加列。
- **校准/保养主数据**：`mds_equipment_pm_plan`（周期、最近/下次保养、PM 到期态）可独立成篇；其与「腔体行政态=MAINTENANCE」「设备 lifecycle_status=MAINTENANCE」的触发关系需约定。
- **MES / EAP 实时态衔接契约**：设备 code/id、E10 状态机 code、状态 code、控制模式模型 code 的导出/订阅格式（供 EAP 实时态回写、设备级 E10 rollup 与实时控制模式维护），以及 MES/EAP 聚合与迁移校验规则的可配置化（§3.11–§3.12）。
- **与位置设计联动**：设备停机（`lifecycle_status`/`admin_status`）是否反向影响位置可用性视图，需在位置设计 §7 补充。
- **配方正文(body)**：SEMI E40 配方参数在外围 Recipe 系统；MDS 仅存资格引用（§3.8）。
- **API 设计**（README §4）：设备树查询（含模块/端口/腔体行政态）、按 Area/Class/工艺节点/生命周期态列设备、所有权维护、E10 状态机定义查询（供 MES 拉取）、搬迁接口（触发 Hist + 可选 move_event）、配方资格维护。

---

## 附录 A. 量化能力（Capability Limit）（第二轮补强）

> **补的缺口**：§3.7 的 `mds_equipment_capability` 只有**分类**（area / process_code / recipe_class），**没有量化**——无法回答"这台设备**够不够好**"（如能否做到 28nm 以下线宽、套刻精度是否达标、UPH 多少）。结果是：工艺可行性筛选只能靠 `tech_node` 粗筛，产能估算无数据支撑。

### A.1 `mds_equipment_capability_limit`（量化能力）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 设备 |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | 能力参数（由 [param-def-design](param-def-design.md) 统一语义，如 `MIN_CD`/`OVERLAY`/`THROUGHPUT`/`UNIFORMITY`） |
| `capability_kind` | VARCHAR(20) | NOT NULL | `CAPABILITY_LIMIT`（能力极限）/`THROUGHPUT`（产能 UPH）/`PRECISION`（精度）/`RANGE`（工艺范围） |
| `best_value` | DECIMAL(18,6) | | 最佳可达值 |
| `limit_value` | DECIMAL(18,6) | | 极限可用值 |
| `unit` | VARCHAR(16) | FK→`mds_uom.code` | 单位 |
| `direction` | VARCHAR(16) | | `LOWER_BETTER`/`HIGHER_BETTER`（用于比较） |
| `condition_note` | VARCHAR(256) | | 达成条件（如"100μm pitch 下"、"单腔模式"） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(equipment_id, param_def_id, capability_kind, tenant_id, deleted)`。

### A.2 闭环

| 关联 | 说明 |
| --- | --- |
| → [product-design §4.4](product-design.md) | 产品 `tech_node` 粗筛 → 本表**定量筛选**（产品要求 CD ≤ 28nm，设备 `best_value`=20nm 则可用） |
| → [param-def-design](param-def-design.md) | 能力参数统一语义，使不同型号设备可横向比较 |
| → [apc-fdc-design](apc-fdc-design.md) | 能力相关参数常是 APC 的控制/目标变量 |
| → [pm-calibration-design](pm-calibration-design.md) | 校准结果影响精度类能力值（`PRECISION`） |
| → [constraint-design](constraint-design.md) | 可新增 `CAPABILITY_THRESHOLD` 约束（如"该工序要求 `OVERLAY < 3nm`"） |

### A.3 与 MES 生产执行衔接

| 场景 | 说明 |
| --- | --- |
| **投料/派工可行性** | 从"能不能做"（分类）升级为"**够不够好**"（定量）——MES 按产品要求与设备能力做阈值筛选 |
| **产能估算** | `capability_kind=THROUGHPUT` 提供 UPH，供排产与交期预测（与 `route_operation.default_time` 互补） |
| **新设备评估** | 设备验收时录入能力值，形成"设备能力台账"，支撑良率与工艺能力分析 |
| **约束表达** | 定量要求可写成约束（`CAPABILITY_THRESHOLD`），由统一求值引擎执行 |
- **数据权限**：`@DataPermission(Scope.FACTORY)` 按厂区行级权限接入平台 RBAC（M6）。
