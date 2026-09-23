# MDS 工艺路线主数据设计：Route / Step / Flow

> 本文档承接 [位置主数据](location-design.md) 与 [设备建模](equipment-design.md)，
> 补全 MDS 第三块核心主数据——**工艺路线（Route）**、**工序（Step / Operation）**、**工序流（Flow / 连接）**。
> 三者与位置（AREA）、设备（设备类 / 设备 / 配方）天然闭环：一道工序「在哪个 Area、由哪类设备、跑哪份配方」在设计期即被绑定。

---

## 0. 定位与边界（拍板）

沿用既有原则：**MDS 拥有「定义」，下游（MES）拥有「实例 / 实时态」**。

- **MDS 拥有 `route / process / operation / flow` 的定义主数据**：可复用的工艺知识、跨 lot 共享、带版本与生效日期。
- **MES 拥有 `route` 的运行实例**：某 lot 当前所处 step、走过的 flow 历史、WIP 停留——不进 MDS（MES 侧建 `lot_route_instance`，引用 `route_id + current_op_id`）。
- 设计期在 route operation 上绑定的「默认 Area / 设备类 / 配方」是**资格级默认**；实际派工时 MES 结合设备实时态（[设备设计 §3.10–§3.12](equipment-design.md)）与配方资格（[§3.8](equipment-design.md)）选定具体物理设备。

### 0.1 与位置 / 设备设计的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 工序 ↔ 工艺 Area | `mds_route_operation.area_code → mds_location(code, level=AREA)` | [位置设计 §2](location-design.md) |
| 工序 ↔ 设备大类 | `mds_route_operation.equipment_class_id → mds_equipment_class.id` | [设备设计 §3.2](equipment-design.md) |
| 工序 ↔ 设备类型 | `mds_route_operation.equipment_type_id → mds_equipment_type.id` | [设备设计 §3.2](equipment-design.md) |
| 工序 ↔ 逻辑配方 | `mds_route_operation.recipe_id → mds_logic_recipe.id`（设计期绑定「做什么」；物理配方+PPID 在派工时按设备类解析，见 [配方设计 §4.3](recipe-design.md)） | [配方设计](recipe-design.md) |
| 工序 ↔ 工艺规范 | `mds_route_operation.process_id → mds_process.id` | 本文 §3.2 |
| route ↔ 产品 | `mds_route.product_code → mds_product.code` | [产品主数据设计](product-design.md) |

---

## 1. 行业依据（SEMI + fab MES 实践）

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E94** | Process Flow / Route 表达；route 由有序 operation 组成；operation 绑定 process、area、recipe | 节点-边图模型（§3、§4.1） |
| **SEMI E87** | 载具 / 批次（lot）与 route 的关系；lot 在 route 上的位置（current operation） | 运行实例归 MES（§0） |
| **SEMI E40/E94** | 配方按工艺步骤（step）绑定、需资格认证 | `operation.recipe_id`（§3.3） |
| **fab MES 实践** | route = **节点（operation）+ 边（flow）** 的图模型，而非线性表；支持分支 / 并行 / 返工回路；route 版本化（RELEASED 才可被 lot 引用）；process definition 独立于 route 复用 | §4.1–§4.3 |
| **fab 工程实践** | 同一工艺规范（如 `LITHO_PHOTO`）被多条 route 复用；rework loop 是产线常态（量测失败回流重做） | `mds_process` 独立（§3.2）、`REWORK` 流（§4.3） |

---

## 2. 术语澄清（避免歧义）

| 术语 | 含义 | 易混淆点（明确区分） |
| --- | --- | --- |
| **Route / 工艺路线（逻辑层）** | 一个产品从投料到完工要经历的**工序顺序有向图**（operation 节点 + flow 边） | 只定义"做什么工序、先后如何"，**不含**物理配方/设备组/工艺参数；详见 [Process Flow 设计](process-flow-design.md)（Process Flow = Route 的工艺展开层，两者**不同层级**，见下） |
| **Process Flow / 工艺流（展开层）** | 在 Route 之上，把每个 operation 展开为 **Process Flow Step**（物理配方 + PPID + 设备组 + 工艺参数规范） | **不是** Route 的同义词；是 Route 的"具体工艺实现"。1 Route : N Process Flow（多方案/多 fab）。详见 [process-flow-design.md](process-flow-design.md) |
| **Step = Operation** | Route 图中的一个**节点**，定义「在哪、做什么、用什么、跑哪份配方」 | 不是 recipe 内部的小步骤（归 recipe 侧），也不是设备腔体内部流程（归设备侧） |
| **Flow / Link** | 节点间的**连接与流程控制**：顺序 / 分支 / 并行分叉 / 并行汇合 / 返工回流 | 本设计的「flow」是 **route 内部工序之间的脉络**；与设备 `process_mode`（单片/批次/集群的**物理并行**）是**两个正交维度**（见 §4.4），与 recipe 内部步骤也层级不同 |

> **双层关系重申**：本文档（Route）只负责"工序顺序"逻辑层；**工艺展开层（Process Flow）已独立成篇**——[process-flow-design.md](process-flow-design.md)。两者关系为 `process_flow.route_id → route.id`、每个 `process_flow_step.op_id → route_operation.id`。Route 的 `operation.recipe_id` 绑定**逻辑配方**（"做什么"），Process Flow Step 的 `recipe_id`/`ppid` 绑定**物理配方 + PPID**（"怎么做"），两级配方呼应 [recipe-design.md §3.2/§3.3](recipe-design.md)。

---

## 3. 实体总览与 ER

```
mds_route ──< mds_route_operation (Step/节点)
                │   ├──process_id──> mds_process            (工艺规范, 独立复用)
                │   ├──area_code──> mds_location(code,level=AREA)   (闭环: 位置)
                │   ├──equipment_class_id──> mds_equipment_class     (闭环: 设备类)
                │   ├──equipment_type_id──> mds_equipment_type       (闭环: 设备类二级)
                │   └──recipe_id──> mds_logic_recipe                  (闭环: 逻辑配方; 物理配方+PPID 见配方设计)
                │
                └──< mds_route_flow (边/连接)
                        from_op_id ──> mds_route_operation
                        to_op_id   ──> mds_route_operation
                        flow_type  ∈ {SEQUENCE, BRANCH, PARALLEL_SPLIT, PARALLEL_JOIN, REWORK}

mds_route.product_code ──> mds_product.code (产品主数据, 后续补)
```

| 实体 | 表名 | 是否主数据 | 说明 |
| --- | --- | --- | --- |
| 工艺路线 | `mds_route` | 是 | 路线头：版本、状态、绑定产品 |
| 工艺规范 | `mds_process` | 是 | 独立于 route 的「做什么」工艺知识，被多 route 复用 |
| 工序（节点） | `mds_route_operation` | 是 | Step：绑定 area / 设备类 / 配方 / 规范 |
| 工序流（边） | `mds_route_flow` | 是 | 节点间连接 + 流程控制 |

> 所有实体继承 `BaseDefData`（见 §5），含 `id` / `tenant_id` / `@Version` / `deleted` / 审计列；历史表 `{entity}_hist` 由 `@History(SNAPSHOT)` 自动生成。

### 3.1 `mds_route`（工艺路线头）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | 雪花 / UUIDv7 |
| `route_code` | VARCHAR(64) | NOT NULL | 路线代码（如 `RT-LITHO-28N`） |
| `name` | VARCHAR(128) | | 路线名称 |
| `product_code` | VARCHAR(64) | | 绑定产品（→ `mds_product.code`，[产品主数据设计](product-design.md)） |
| `revision` | VARCHAR(16) | NOT NULL | 版本号（如 `1.0` / `2.3`）；同 `route_code` 下唯一，且 **RELEASED 后不可变** |
| `status` | VARCHAR(16) | NOT NULL | 生命周期态：`DRAFT`/`IN_REVIEW`/`APPROVED`/`RELEASED`/`OBSOLETE`/`ARCHIVED`（见 §4.2） |
| `is_frozen` | BIT | DEFAULT 0 | 冻结标志（叠加在 RELEASED 上的组织级写锁，**与 status 正交**，见 §4.2.3） |
| `frozen_by` | VARCHAR(64) | | 冻结人 |
| `frozen_at` | DATETIME(3) | | 冻结时间 |
| `frozen_reason` | VARCHAR(256) | | 冻结原因（量产固化 / 客户冻结 / 法规 / 审计） |
| `unfreeze_at` | DATETIME(3) | | 计划解冻时间（可空，到点人工或定时解除） |
| `effective_from` | DATETIME(3) | | 生效起始（SCD2-lite） |
| `effective_to` | DATETIME(3) | | 生效结束（NULL=当前有效） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(route_code, revision, tenant_id, deleted)`。
- 版本策略：改 route 产生新 `revision`，旧版本 `status=OBSOLETE` 不破已投 lot；沿用 [位置设计 D4](location-design.md) 的轻量 SCD2 思路。

### 3.2 `mds_process`（工艺规范定义，独立主数据）

> **关键决策**：process（「这道工序要执行的工艺规范」）独立于 route 存在，可被多条 route 的 operation 复用。避免「同一光刻工艺」在每条 route 里重复定义。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `process_code` | VARCHAR(64) | NOT NULL | 工艺规范代码（如 `LITHO_PHOTO`、`ETCH_SIO2`） |
| `name` | VARCHAR(128) | | |
| `class_id` | VARCHAR(32) | FK→`mds_equipment_class.id` | 该规范所属工艺大类（决定可由哪类设备执行） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(process_code, tenant_id, deleted)`。
- 闭环：`class_id` 与 `mds_route_operation.equipment_class_id` 同源，保证「工序想要做的工艺」与「能做的设备类」一致。

### 3.3 `mds_route_operation`（工序节点 = Step）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `route_id` | VARCHAR(32) | NOT NULL, FK→`mds_route.id` | 所属路线 |
| `op_seq` | INT | NOT NULL | 逻辑工序号（如 100/200，留间隔便于插序） |
| `op_name` | VARCHAR(128) | | 工序名 |
| `process_id` | VARCHAR(32) | FK→`mds_process.id` | 引用工艺规范（§3.2） |
| `area_code` | VARCHAR(64) | FK→`mds_location.code (level=AREA)` | **在哪做**（闭环位置） |
| `equipment_class_id` | VARCHAR(32) | FK→`mds_equipment_class.id` | **哪类设备做**（闭环设备类） |
| `equipment_type_id` | VARCHAR(32) | FK→`mds_equipment_type.id` | 设备类型（可选，二级约束） |
| `recipe_id` | VARCHAR(32) | FK→`mds_logic_recipe.id` | **跑哪份逻辑配方**（设计期「做什么」；物理配方+PPID 派工时解析，[配方设计 §4.3](recipe-design.md)） |
| `default_time` | INT | | 标准驻留时间（分钟），产能/排程参考 |
| `yield_expect` | DECIMAL(5,2) | | 期望良率（%） |
| `is_rework` | BIT | DEFAULT 0 | 是否返工工序 |
| `sub_route_id` | VARCHAR(32) | FK→`mds_route.id`（可选） | 子路线复用（见 §4.6） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(route_id, op_seq, tenant_id, deleted)`。
- `op_seq` 仅作展示排序；**真实顺序由 `mds_route_flow` 边决定**（节点-边图模型，§4.1）。
- **工序缓冲库（可选）**：`mds_route_operation.out_bank_code → mds_bank.code`（见 [仓库设计 §0.2/§4.5](bank-design.md)），表达工序产出缓冲到指定 Bank；该列在路线校验服务阶段落地，本期预留绑定语义，不影响既有结构。

### 3.4 `mds_route_flow`（工序流 / 连接 / 分支 / 并行 / 返工）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `route_id` | VARCHAR(32) | NOT NULL, FK→`mds_route.id` | 所属路线 |
| `from_op_id` | VARCHAR(32) | NOT NULL, FK→`mds_route_operation.id` | 起点工序 |
| `to_op_id` | VARCHAR(32) | NOT NULL, FK→`mds_route_operation.id` | 终点工序 |
| `flow_type` | VARCHAR(16) | NOT NULL | `SEQUENCE`/`BRANCH`/`PARALLEL_SPLIT`/`PARALLEL_JOIN`/`REWORK` |
| `condition_expr` | VARCHAR(512) | | 分支/返工条件表达式（如 `metro_result=='FAIL'`），由 MES 解释执行 |
| `priority` | INT | DEFAULT 0 | 同起点多边的优先级 |

- 唯一：`(route_id, from_op_id, to_op_id, flow_type, tenant_id, deleted)`。
- `flow_type` 语义见 §4.3。
- **约束挂载点（Q-Time 等）**：边是**时序约束**的天然落点——「清洗后须在 N 小时内进炉」这类规则跨两个 operation，不属于任一节点，由 [约束设计](constraint-design.md) 的 `mds_constraint_binding`（`scope_type=ROUTE_FLOW`, `scope_ref=本边 id`）+ `constraint_type=Q_TIME` 表达。路由只提供结构，红线归约束层（§8）。

---

## 4. 关键设计点（拍板）

### 4.1 节点-边图模型 vs 线性序列表

| 方案 | 表达 | 支持分支/并行/返工 | 结论 |
| --- | --- | --- | --- |
| A 线性序列表（`op_seq` 自增） | 数组 | ❌ 仅顺序 | 简单但**不支持 rework loop / 工程分支**，而这两者是晶圆厂产线常态 |
| **B 节点-边图（operation 节点 + flow 边）** | 有向图 | ✅ | **采用**：SEMI E94 / fab MES 标准做法，能力完整 |

> 单一「start→end」线性产品流极少见；几乎所有真实 route 都有 rework 回路（量测失败回流）与工程分支（ENG 批次走不同路径）。图模型是唯一可扩展表达。

### 4.2 版本管理与生命周期状态机（SCD2-lite + 冻结）

#### 4.2.1 生命周期态（status）

工艺路线在工程与量产中有完整的**审批—发布—冻结—退役**生命周期，不是简单的草稿/发布两态：

| status | 含义 | 可编辑？ | 可被 lot 引用？ |
| --- | --- | --- | --- |
| `DRAFT` | **设计**/编辑中（工程师新建、增删工序与流向） | ✅ | ❌ |
| `IN_REVIEW` | 已提交评审（等待工艺 / 质量会签） | ❌（仅评审意见） | ❌ |
| `APPROVED` | 评审通过、待发布（技术冻结，尚未对产线生效） | ❌ | ❌ |
| `RELEASED` | 已发布生效 | ❌（**版本不可变**） | ✅ |
| `OBSOLETE` | 被新版本取代，不再用于新 lot（历史 lot 可走完） | ❌ | ❌（新投料禁止） |
| `ARCHIVED` | 完全停用、仅留档追溯 | ❌ | ❌ |

**状态机（合法迁移）**：

```
DRAFT ──submit──> IN_REVIEW ──approve──> APPROVED ──release──> RELEASED
  ▲                   │                       │                      │
  └──recall───────────┘                       │                      │
                              reject           │                      │
                                └──────────────┘                      │
RELEASED ──freeze──> RELEASED(is_frozen=T) ──unfreeze──> RELEASED(is_frozen=F)
RELEASED ──obsolete──> OBSOLETE ──archive──> ARCHIVED
```

- 迁移由 `RouteService` 显式方法触发（`submitForReview` / `approve` / `release` / `freeze` / `unfreeze` / `obsolete` / `archive`），并在应用层校验前后态合法性（非法迁移抛 `IllegalStateTransitionException`）。
- **设计（Design）/ 发布（Released）/ 冻结（Frozen）是时间递进 + 保护递增关系，并非互斥标签**：DRAFT→IN_REVIEW→APPROVED 为「设计态」，RELEASED 为「发布态」，is_frozen=T 在 RELEASED 之上叠加「冻结态」。

#### 4.2.2 版本不可变原则

- 一条 `route_code` 下可有多个 `revision`；**一旦 `RELEASED`，该版本的内容（operation / flow 集合）即不可变**——这是工艺路线追溯性的基石（出了问题能精确回放当时跑了什么）。
- 任何结构性修改（增删工序、改流向、换配方 / 设备类）必须 `cloneAsNewVersion()`：复制出 `version+1` 的新 `DRAFT`，旧版本保持 RELEASED 不变。
- 仅**最新 RELEASED 版本**可被新 lot 引用；已投 lot 继续走其投料时锁定的那个 `revision`（MES 侧 `lot_route_instance.route_version` 固化），不受新版本发布影响。

#### 4.2.3 冻结（FROZEN）——与 status 正交的保护态

- **冻结 ≠ 废弃**：`OBSOLETE` 表示「不再用于新 lot」，`FROZEN` 表示「此 RELEASED 版本被**组织级写锁**，任何人都不能改、也不能基于它 clone 覆盖，只能基于它开新版本」。常用于：
  - 量产前锁定（Production Freeze）
  - 客户指定冻结（Customer Lock）
  - 法规 / 审计冻结（需留痕溯源）
- 建模上 `is_frozen` 是**叠加标志，与 `status` 正交**：冻结态的 route 其 `status` 仍是 `RELEASED`（仍可投料，除非同时 `OBSOLETE`）。`frozen_by / frozen_at / frozen_reason / unfreeze_at` 记录冻结上下文（§3.1）。
- 解冻（`unfreeze`）后恢复可编辑、可 clone；`unfreeze_at` 提供计划解冻时间点（到点由定时任务或人工解除）。

#### 4.2.4 生效窗口（SCD2-lite）

- `effective_from/effective_to` 描述版本级生效时间窗（与 [位置设计 D4](location-design.md) 一致的轻量 SCD2）；`effective_to=NULL` 表示当前有效。
- 与 `status=RELEASED` 配合：`APPROVED` 但 `effective_from` 在未来 → 预约在未来某时刻生效。

### 4.3 Flow 类型与语义

| `flow_type` | 含义 | MES 行为 |
| --- | --- | --- |
| `SEQUENCE` | 顺序流转 | lot 顺序进入 `to_op` |
| `BRANCH` | 条件分支 | 按 `condition_expr` 选**一条** `to_op`（如正常→下一步 / 异常→量测） |
| `PARALLEL_SPLIT` | 并行分叉 | 同时进入多条 `to_op`（需配对的 `PARALLEL_JOIN`） |
| `PARALLEL_JOIN` | 并行汇合 | 等待所有上游 `PARALLEL_SPLIT` 分支完成再继续 |
| `REWORK` | 返工回流 | `to_op` 为上游工序，带 `condition_expr`（如量测失败），lot 回流重做 |

- `BRANCH` 与 `REWORK` 都依赖 `condition_expr`；表达式 DSL 由 MES 解释，MDS 只负责**存储**与**结构校验**（如 REWORK 的 `to_op` 必须早于 `from_op`）。

### 4.4 工艺流「并行」 vs 设备「物理并行」——正交维度

**极易混淆，明确区分**：

- **本设计的「并行」（flow `PARALLEL_SPLIT/JOIN`）**：是**工艺路线层级**的并行——同一片（或同批次）同时走多条工序（如双面镀膜、双量测）。属「生产流程编排」。
- **设备的「物理并行」（`process_mode`，[设备设计 §3.1.4](equipment-design.md)）**：是**单台设备层级**的并行架构——`BATCH`（一批次多片）/ `CLUSTER`（多腔体同时跑）/ `SINGLE_WAFER`（单片串行）。属「设备能力」。

两者正交：一个 `PARALLEL_SPLIT` 的工序节点，其目标设备可能是 `SINGLE_WAFER`（流程并行、设备串行）或 `CLUSTER`（流程并行、设备也并行）。建模时**互不耦合**，仅通过 `operation.equipment_class_id` 引用设备类间接关联。

### 4.5 与位置 / 设备 / 配方四向闭环

`operation` 一行同时挂四条外键（§0.1 映射），使「工艺路线」成为串联前三块主数据的**骨架**：

1. `area_code → mds_location(AREA)`：工序落点的工艺域（位置设计）。
2. `equipment_class_id/type_id → mds_equipment_class/type`：可执行该工序的设备分类（设备设计 §3.2）。
3. `recipe_id → mds_logic_recipe`：该工序绑定的**逻辑配方**（设计期「做什么」）；派工时由 MES 据所选设备类解析出物理配方与其 PPID（[配方设计 §4.3](recipe-design.md)）。
4. `process_id → mds_process`：工艺规范本体（本文 §3.2）。

→ 此外，route operation 的 `area_code`/`equipment_class_id`/`process_id` 也是**载具兼容性闭环**的反向入口：`mds_carrier_type_compat` 据此表达「该类型载具能进哪些工艺区/设备类」（[载具设计 §4.4](carrier-design.md)），排程时由 lot 的 route 反推可用载具类型。

→ 排程时 MES 可据此精确推断「某 lot 走到此 step，应去哪个 AREA、挑哪类设备、加载哪份配方」，全部来自 MDS 主数据，无需在 MES 重复录入。

### 4.6 子路线复用（sub_route，可选扩展）

- `mds_route_operation.sub_route_id` 指向另一条 `mds_route`，表达「主路线在此 step 展开为子流程」（如封装段作为子路线被多产品复用）。
- 与 `process_id` 二选一：引用 `process` 表示「执行一个工艺规范」，引用 `sub_route` 表示「展开一段子流程」。避免 route 爆炸式重复。

### 4.7 软删 / 租户 / 主键（与平台一致）

- 所有实体继承 `BaseDefData`：`String id`（雪花/UUIDv7）、`tenant_id`、`@Version`、`deleted`、审计列。
- 唯一约束均含 `(..., tenant_id, deleted)`（T2.5）。
- 行级过滤：`@SQLRestriction("deleted=false")` + `@Filter(cimTenantFilter)`；`@FilterDef` 放实体包 `package-info.java`。

---

## 5. 与平台底座衔接（强制对齐）

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData`（零 Hibernate 基类） | 所有 route 实体继承，含 `@Version` 乐观锁、审计五件套、`tenant_id`、`deleted` |
| `@History(SNAPSHOT)` | route / operation / flow 变更落 `{entity}_hist`，含 `op_type`/`operator`/`op_time`/`trx_id`/`change_set_json`——route 定义修改自动留痕 |
| `cimTenantFilter` + `TenantContext` | 多租户行级过滤，MDS 主数据天然隔离 |
| T2.5 软删唯一约束 | 唯一键含 `deleted`，避免软删后唯一冲突 |
| `IdGenerator`（雪花/UUIDv7） | 主键生成 |
| 主历成对迁移 | DDL 与 `db/migration/{common,vendor}` 成对：结构迁移 + 历史表 + 种子数据 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
-- 路线头
CREATE TABLE mds_route (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  route_code    VARCHAR(64)  NOT NULL,
  name          VARCHAR(128),
  product_code  VARCHAR(64),
  revision       VARCHAR(16)  NOT NULL,
  status        VARCHAR(16)  NOT NULL,
  is_frozen     BIT          DEFAULT 0,
  frozen_by     VARCHAR(64),
  frozen_at     DATETIME(3),
  frozen_reason VARCHAR(256),
  unfreeze_at   DATETIME(3),
  effective_from DATETIME(3),
  effective_to   DATETIME(3),
  description   VARCHAR(512),
  version_      BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time   DATETIME(3), create_user VARCHAR(64),
  event_time    DATETIME(3), event_user  VARCHAR(64),
  event_name    VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_route PRIMARY KEY (id),
  CONSTRAINT uq_mds_route_code UNIQUE (route_code, revision, tenant_id, deleted)
);

-- 工艺规范（独立复用）
CREATE TABLE mds_process (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  process_code  VARCHAR(64)  NOT NULL,
  name          VARCHAR(128),
  class_id      VARCHAR(32),
  description   VARCHAR(512),
  version_      BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time   DATETIME(3), create_user VARCHAR(64),
  event_time    DATETIME(3), event_user  VARCHAR(64),
  event_name    VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_process PRIMARY KEY (id),
  CONSTRAINT uq_mds_process_code UNIQUE (process_code, tenant_id, deleted),
  CONSTRAINT fk_proc_class FOREIGN KEY (class_id) REFERENCES mds_equipment_class (id)
);

-- 工序（节点 = Step）
CREATE TABLE mds_route_operation (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  route_id      VARCHAR(32)  NOT NULL,
  op_seq        INT          NOT NULL,
  op_name       VARCHAR(128),
  process_id    VARCHAR(32),
  area_code     VARCHAR(64),
  equipment_class_id  VARCHAR(32),
  equipment_type_id   VARCHAR(32),
  recipe_id     VARCHAR(32),
  default_time  INT,
  yield_expect  DECIMAL(5,2),
  is_rework     BIT          DEFAULT 0,
  sub_route_id  VARCHAR(32),
  description   VARCHAR(512),
  version_      BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time   DATETIME(3), create_user VARCHAR(64),
  event_time    DATETIME(3), event_user  VARCHAR(64),
  event_name    VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_op PRIMARY KEY (id),
  CONSTRAINT uq_mds_op_seq UNIQUE (route_id, op_seq, tenant_id, deleted),
  CONSTRAINT fk_op_route FOREIGN KEY (route_id) REFERENCES mds_route (id),
  CONSTRAINT fk_op_proc FOREIGN KEY (process_id) REFERENCES mds_process (id),
  CONSTRAINT fk_op_class FOREIGN KEY (equipment_class_id) REFERENCES mds_equipment_class (id),
  -- area_code: 跨域逻辑引用（服务层校验），不建 DB 级 FK —— 见 00-blueprint §11
  CONSTRAINT fk_op_type FOREIGN KEY (equipment_type_id) REFERENCES mds_equipment_type (id),
  CONSTRAINT fk_op_recipe FOREIGN KEY (recipe_id) REFERENCES mds_logic_recipe (id),
  CONSTRAINT fk_op_subroute FOREIGN KEY (sub_route_id) REFERENCES mds_route (id)
);
CREATE INDEX idx_op_area   ON mds_route_operation (area_code);
CREATE INDEX idx_op_class  ON mds_route_operation (equipment_class_id);

-- 工序流（边 / 连接 / 分支 / 并行 / 返工）
CREATE TABLE mds_route_flow (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  route_id      VARCHAR(32)  NOT NULL,
  from_op_id    VARCHAR(32)  NOT NULL,
  to_op_id      VARCHAR(32)  NOT NULL,
  flow_type     VARCHAR(16)  NOT NULL,
  condition_expr VARCHAR(512),
  priority      INT          DEFAULT 0,
  version_      BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time   DATETIME(3), create_user VARCHAR(64),
  event_time    DATETIME(3), event_user  VARCHAR(64),
  event_name    VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_flow PRIMARY KEY (id),
  CONSTRAINT uq_mds_flow_edge UNIQUE (route_id, from_op_id, to_op_id, flow_type, tenant_id, deleted),
  CONSTRAINT fk_flow_route FOREIGN KEY (route_id) REFERENCES mds_route (id),
  CONSTRAINT fk_flow_from FOREIGN KEY (from_op_id) REFERENCES mds_route_operation (id),
  CONSTRAINT fk_flow_to   FOREIGN KEY (to_op_id)   REFERENCES mds_route_operation (id)
);
```

> 历史表 `mds_route_hist` / `mds_process_hist` / `mds_route_operation_hist` / `mds_route_flow_hist` 由 `@History(SNAPSHOT)` 在运行时自动生成，结构为对应实体 + 历史元数据列（`hist_id` / `rev` / `rev_type` / `rev_at`）。

---

## 7. 代码结构（包路径）

```
com.cim.mds.route
  ├── entity
  │   ├── Route.java             # @Entity 继承 BaseDefData + @History(SNAPSHOT)；含 status/version
  │   ├── Process.java           # @Entity 继承 BaseDefData（class_id FK）
  │   ├── RouteOperation.java     # @Entity 继承 BaseDefData（process/area/设备类/recipe/sub_route FK）
  │   ├── RouteFlow.java          # @Entity 继承 BaseDefData（from/to/flow_type/condition_expr）
  │   └── package-info.java       # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── RouteRepository.java
  │   ├── ProcessRepository.java
  │   ├── RouteOperationRepository.java
  │   └── RouteFlowRepository.java
  ├── service
  │   ├── RouteService.java         # 继承 AbstractJpaService；含状态跃迁方法 submitForReview/approve/release/freeze/unfreeze/obsolete/archive/cloneAsNewVersion（均校验前后态合法性）
  │   └── RouteValidator.java       # 图校验：REWORK 的 to_op 须早于 from_op；BRANCH 须有 condition
  └── web
      └── RouteController.java       # 查询/版本发布/克隆新版本
```

---

## 8. 待补 / 后续

- **产品主数据（`mds_product`）**：`mds_route.product_code` 的引用目标，已独立设计（[product-design.md](product-design.md)），与路线双向闭环。
- **MES 运行实例衔接契约**：`lot_route_instance` 对 `route_id` / `operation.id` / `flow.id` 的导出订阅格式；lot 当前 step、走过的 flow 历史、条件分支求值结果回写。
- **route 图校验服务**：入参校验（无环除 REWORK、PARALLEL_SPLIT/JOIN 配对、起点唯一、可达性），建议在 `RouteValidator` 落地单元测试。
- **约束层衔接**：route 的结构校验（图合法性）之后，还需经 [约束设计](constraint-design.md) 做**工艺红线**校验——`REWORK` 边须配 `REWORK_LIMIT`、跨区/洁净敏感边须配 `Q_TIME`/`CARRIER_AREA_COMPAT`（即约束的**覆盖分析 COVERAGE**，见 constraint-design §4.6.4）。
- **API 设计**：route 查询 / 版本发布 / 克隆 / 图可视化导出。
- **与既有文档闭环**：本设计引用的 `mds_location` / `mds_equipment_class|type` / `mds_logic_recipe`（逻辑配方）分别见 [位置设计](location-design.md) / [设备设计](equipment-design.md) / [配方设计](recipe-design.md)。
