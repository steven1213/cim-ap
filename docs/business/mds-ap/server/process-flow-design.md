# MDS 工艺流（Process Flow）主数据设计

> 本文档承接 [工艺路线设计](route-design.md)、[配方设计](recipe-design.md)、[设备建模](equipment-design.md)、[产品主数据](product-design.md)，
> 补全 MDS 的**工艺流（Process Flow）** 主数据——即「工艺路线（Route）的工艺展开层」。
>
> 设计原则延续前几篇：**MDS 拥有「定义 + 资格 + 参数规范」，实时执行态（当前跑到哪一步、腔体实时配方/PPID、在制实绩）归 MES/EAP**。

---

## 0. 定位与边界（拍板）

### 0.1 Route 与 Process Flow 的关系——双层模型（核心拍板）

[route-design.md §2](route-design.md) 原先把 "Route / Process Flow / Product Flow" 视为同一物（节点-边图）。这在简化系统里成立，但在真实晶圆厂 MES 中二者是**两个正交层级**，必须分离：

| 层级 | 实体 | 回答的问题 | 绑定内容 | 复用粒度 |
| --- | --- | --- | --- | --- |
| **逻辑层（Logical）** | `mds_route` + `mds_route_operation` + `mds_route_flow` | **「工序顺序是什么」**（先光刻、后刻蚀、再量测；哪步返工） | operation 绑 `area_code` / `equipment_class_id`（**类**）/ `logic_recipe_id`（**逻辑**配方） | 跨产品/跨 fab 共享的"工序顺序骨架" |
| **工艺展开层（Process Expansion）** | `mds_process_flow` + `mds_process_flow_step` | **「这道工序具体怎么执行」**（用哪份**物理**配方、哪个 **PPID**、由哪**设备组**做、工艺窗口是多少） | step 绑 `physical_recipe_id` / `ppid` / `equipment_group_id` / 工艺参数规范 | 同一 Route 可有**多套工艺展开方案**（BASE / N+1 / HVM / 不同 fab） |

> **结论（PF1）**：`Route` = 逻辑工序图（顺序编排，已建）；`Process Flow` = 在 Route 之上的**工艺展开**，把每个 operation 实例化为具体的 Process Flow Step（含物理配方 + PPID + 设备组 + 工艺参数）。两者是 **1 个 Route : N 个 Process Flow** 的关系——一个逻辑路线可被多套工艺方案展开（研发方案 / 量产方案 / 某 fab 本地化方案）。

#### 为什么必须分离（行业依据）

1. **逻辑顺序与工艺参数解耦**：Route 改一道工序的先后（如把量测提前），不应触碰每套方案的物理配方/参数；反之工艺调参（换 PPID、收紧窗口）不应改工序顺序。分开后两边独立版本化、独立评审。
2. **多方案并存**：同一产品路线，研发用一套宽松参数（ENG），量产用另一套收紧参数（HVM）；或 A fab 与 B fab 设备不同、PPID 不同，却共用同一逻辑路线。这是 fab 常态，Route 单实体无法表达。
3. **派工真正依据**：MES 派工时真正要用的是 Process Flow Step（"这台设备组里挑一台、加载这个 PPID、按这个窗口跑"），而非 Route 的"逻辑配方"。Route 只给"做什么工序"，Process Flow 才给"怎么做"。

### 0.2 MDS 拥有 vs 下游拥有（边界重申）

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| Process Flow 定义 `mds_process_flow` / Step / 参数规范 | **MDS** | 工艺展开主数据，带版本与生效日期 |
| 设备组定义 `mds_equipment_group` / member | **MDS** | 派工设备集合（class 之上的精细集合），被 Process Flow Step 引用 |
| 物理配方 / PPID 映射 | **MDS**（见 [recipe-design.md](recipe-design.md)） | Process Flow Step 引用 `mds_recipe` / `mds_equipment_recipe_qual.ppid` |
| 工艺参数规范（窗口） | **MDS** | `mds_process_flow_step_param`，作为 SPC 上下限 / 派工校验基线 |
| **某 lot 当前跑到哪个 step / 实际 PPID / 实时参数实绩** | **MES/EAP** | 实时执行态，MDS 不存 |
| **派工引擎选择具体设备 + 加载 PPID + 采集实绩** | **MES/EAP** | MDS 提供主数据映射，引擎执行 |

### 0.3 与其他主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| Process Flow ↔ Route（逻辑层） | `mds_process_flow.route_id → mds_route.id` | [route-design §3.1](route-design.md) |
| Process Flow Step ↔ Route Operation | `mds_process_flow_step.op_id → mds_route_operation.id` | [route-design §3.3](route-design.md) |
| Step ↔ 物理配方 | `mds_process_flow_step.recipe_id → mds_recipe.id` | [recipe-design §3.3](recipe-design.md) |
| Step ↔ PPID（经资格链接） | `mds_process_flow_step_recipe.ppid → mds_equipment_recipe_qual.ppid` | [recipe-design §3.4](recipe-design.md) |
| Step ↔ 设备组 | `mds_process_flow_step.equipment_group_id → mds_equipment_group.id` | 本文 §3.5 |
| Param ↔ 工艺规范 | `mds_process_flow_step_param.process_id → mds_process.id`（可选对齐） | [route-design §3.2](route-design.md) |
| Route ↔ Product（反向闭环） | `mds_route.product_code → mds_product.code` | [product-design §3.2](product-design.md) |
| Equipment Group ↔ Equipment | `mds_equipment_group_member.equipment_id → mds_equipment.id` | [equipment-design §3.3](equipment-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E94** | Process Flow = 一个产品的完整工艺路径；operation/step 绑定 process、recipe、equipment | 双层模型（Route 顺序 + Process Flow 展开），§0.1 / §3 |
| **SEMI E40** | Process Program（PPID）按设备实例存在；配方含参数（参数基线 = 工艺窗口） | Step 绑 `mds_recipe` + `ppid` + `mds_process_flow_step_param`（§3.3 / §3.4） |
| **Camstar / 西门子 Opcenter / 199 MES** | 普遍采用 **Route（逻辑路线）+ Process Flow（工艺展开，含 Process Flow Step / Process Parameter）** 双层；Equipment Group 作为派工设备集合 | 本文整体结构（§3） |
| **fab 工程实践** | 同一 Route 多工艺方案（研发/量产/本地化）；工艺变更需**版本对比评审**；平台化复用需**跨产品工艺对比** | 流程对比能力（§4.7） |
| **SPC / 工艺窗口** | 每道工序的参数有 target/上下限（spec limit），是派工校验与良率管控基线 | `mds_process_flow_step_param`（§3.4） |

---

## 2. 术语澄清（避免歧义）

| 术语 | 含义 | 易混淆点（明确区分） |
| --- | --- | --- |
| **Route / 工艺路线（逻辑层）** | operation 节点 + flow 边的**有向图**，定义工序顺序 | 只说"做什么工序、先后如何"，不含物理配方/设备组/参数 |
| **Process Flow / 工艺流（展开层）** | 在 Route 之上，把每个 operation 展开为 **Process Flow Step**（物理配方 + PPID + 设备组 + 参数窗口） | **不是** Route 的同义词；是 Route 的"工艺实现" |
| **Process Flow Step** | Process Flow 中的一个展开步骤，对应一个 Route Operation | 不是 recipe 内部小步骤，也不是设备腔体内部流程 |
| **Process Parameter / 工艺参数规范** | 某 step 的工艺窗口：`target` / `min` / `max` / `unit`（如温度 200±5℃） | 是"规范/基线"，实际实绩归 MES/SPC |
| **Equipment Group / 设备组** | 可派工执行某 step 的**具体设备集合**（class 之上的精细集合，如 "LITHO_SCANNER_GRP_A"） | 与 `equipment_class`（粗工艺分类）正交；group 由具体设备组成 |
| route.operation.recipe_id（逻辑） vs step.recipe_id（物理） | Route 工序绑**逻辑配方**（"做什么"）；Process Flow Step 绑**物理配方+PPID**（"怎么做"） | 两级配方，呼应 [recipe-design §3.2/§3.3](recipe-design.md) 逻辑/物理分层 |

---

## 3. 实体总览与 ER

```
mds_route ──< mds_process_flow (1 Route : N Process Flow, scenario_code 区分方案)
                │   route_id ──> mds_route.id
                │
                └──< mds_process_flow_step (展开 step, 对应一个 operation)
                        op_id ──> mds_route_operation.id
                        recipe_id ──> mds_recipe.id            (物理配方)
                        equipment_group_id ──> mds_equipment_group.id
                        │
                        ├──< mds_process_flow_step_recipe (step 可多物理配方/PPID 资格)
                        │       recipe_id ──> mds_recipe.id
                        │       ppid ──> mds_equipment_recipe_qual.ppid
                        │       equipment_id ──> mds_equipment.id (可选)
                        │
                        └──< mds_process_flow_step_param (工艺参数规范/窗口)
                                process_id ──> mds_process.id (可选对齐)

mds_equipment_group ──< mds_equipment_group_member (设备组成员)
        member.equipment_id ──> mds_equipment.id

mds_process_flow_compare (流程对比快照头) ──< mds_process_flow_compare_item (逐条差异)
```

| 实体 | 表名 | 是否主数据 | 说明 |
| --- | --- | --- | --- |
| 工艺流头 | `mds_process_flow` | 是 | 挂在 Route 上，含 scenario/version/status/frozen |
| 工艺流步骤 | `mds_process_flow_step` | 是 | 展开 step：物理配方 + 设备组 + 默认 PPID |
| 步骤配方资格 | `mds_process_flow_step_recipe` | 是 | step 可多物理配方/PPID（对应设备资格） |
| 步骤工艺参数 | `mds_process_flow_step_param` | 是 | 工艺窗口（target/min/max/unit） |
| 设备组 | `mds_equipment_group` | 是 | 派工设备集合（支撑实体） |
| 设备组成员 | `mds_equipment_group_member` | 是 | 组 ↔ 设备多对多 |
| 流程对比快照 | `mds_process_flow_compare` | 是 | 对比结果留痕（评审用） |
| 对比差异项 | `mds_process_flow_compare_item` | 是 | 逐条差异明细 |

> 所有实体继承 `BaseDefData`（见 §5），含 `id` / `tenant_id` / `@Version` / `deleted` / 审计列；历史表 `{entity}_hist` 由 `@History(SNAPSHOT)` 自动生成。

### 3.1 `mds_process_flow`（工艺流头）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | 雪花 / UUIDv7 |
| `flow_code` | VARCHAR(64) | NOT NULL | 工艺流代码（如 `PF-LITHO-28N-BASE`） |
| `name` | VARCHAR(128) | | 工艺流名称 |
| `route_id` | VARCHAR(32) | NOT NULL, FK→`mds_route.id` | **所属逻辑路线**（双层关系锚点，PF1） |
| `scenario_code` | VARCHAR(32) | NOT NULL | 方案标识：`BASE`/`ENG`/`HVM`/`N+1`/`LOCAL_<FAB>`（同一 Route 多方案区分） |
| `fab_code` | VARCHAR(32) | | 适用 fab（多 fab 本地化方案；空=通用） |
| `revision` | VARCHAR(16) | NOT NULL | 版本号（如 `1.0`）；同 `(route_id, scenario_code, fab_code)` 下唯一，RELEASED 后不可变 |
| `status` | VARCHAR(16) | NOT NULL | 生命周期态：`DRAFT`/`IN_REVIEW`/`APPROVED`/`RELEASED`/`OBSOLETE`/`ARCHIVED`（见 §4.2） |
| `is_frozen` | BIT | DEFAULT 0 | 冻结标志（与 status 正交，见 §4.2.3） |
| `frozen_by`/`frozen_at`/`frozen_reason`/`unfreeze_at` | — | | 冻结留痕 |
| `is_default` | BIT | DEFAULT 0 | 该 Route 下的默认工艺流（至多一套为 1） |
| `effective_from`/`effective_to` | DATETIME(3) | | 生效窗口（SCD2-lite） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(route_id, scenario_code, fab_code, revision, tenant_id, deleted)`。
- 版本策略与 Route 完全对称（§4.2）：RELEASED 后不可变，改须 `cloneAsNewVersion()`。

### 3.2 `mds_process_flow_step`（工艺流步骤 = 工序展开）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `process_flow_id` | VARCHAR(32) | NOT NULL, FK→`mds_process_flow.id` | 所属工艺流 |
| `op_id` | VARCHAR(32) | NOT NULL, FK→`mds_route_operation.id` | **对应的逻辑工序**（展开锚点） |
| `step_seq` | INT | NOT NULL | 展示序号（冗余 route_operation.op_seq，便于脱离 Route 直接浏览） |
| `step_name` | VARCHAR(128) | | 步骤名 |
| `recipe_id` | VARCHAR(32) | FK→`mds_recipe.id` | **物理配方**（"怎么做"，对应逻辑配方的物理落地） |
| `ppid` | VARCHAR(128) | | **默认 PPID**（该物理配方在默认设备上的 Program ID，见 §4.3） |
| `equipment_group_id` | VARCHAR(32) | FK→`mds_equipment_group.id` | **派工设备组**（精细集合，而非 class） |
| `default_time` | INT | | 标准驻留时间（分钟），产能估算 |
| `yield_expect` | DECIMAL(5,2) | | 期望良率（%） |
| `is_rework` | BIT | DEFAULT 0 | 是否返工步骤（与 route operation.is_rework 对齐） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(process_flow_id, op_id, tenant_id, deleted)`。
- `recipe_id` + `ppid` 是"默认/主"路径；完整的多设备多 PPID 资格见 `mds_process_flow_step_recipe`（§3.3）。
- **覆盖性闭环**：每个 Route Operation 原则上应有 ≥1 个 step（见 §4.7.3 覆盖校验）。

### 3.3 `mds_process_flow_step_recipe`（步骤 ↔ 多物理配方 / PPID 资格）

> 一个 step 可能对应**多台设备、每台设备不同 PPID**（同一物理配方在不同设备上的 Program ID 不同，[recipe-design §3.4](recipe-design.md)）。用链接实体表达资格集。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `step_id` | VARCHAR(32) | NOT NULL, FK→`mds_process_flow_step.id` | 所属 step |
| `recipe_id` | VARCHAR(32) | NOT NULL, FK→`mds_recipe.id` | 物理配方 |
| `equipment_id` | VARCHAR(32) | FK→`mds_equipment.id` | 具体设备（可选；不填表示"该组任意合格设备"） |
| `ppid` | VARCHAR(128) | NOT NULL | 该物理配方在此设备上的 Process Program ID（SEMI E40） |
| `is_default` | BIT | DEFAULT 0 | 该 step 默认 PPID（与 step.ppid 保持一致） |
| `qual_status` | VARCHAR(16) | NOT NULL DEFAULT 'QUALIFIED' | `QUALIFIED`/`PENDING`/`REVOKED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(step_id, equipment_id, ppid, tenant_id, deleted)`（设备可空时用占位值；服务层处理）。
- 闭环：`ppid` 直接来自 `mds_equipment_recipe_qual.ppid`，保证 MDS 内配方资格单一来源（[recipe-design §3.4](recipe-design.md)）。

### 3.4 `mds_process_flow_step_param`（工艺参数规范 / 窗口）

> 每 step 的关键工艺参数及其 **target / 下限 / 上限 / 单位**，作为 SPC 上下限基线、派工校验与变更对比的锚点。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `step_id` | VARCHAR(32) | NOT NULL, FK→`mds_process_flow_step.id` | 所属 step |
| `param_name` | VARCHAR(64) | NOT NULL | 参数名（如 `TEMP`、`PRESSURE`、`POWER`）——**展示用**；语义以 `param_def_id` 为准 |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | **参数定义（统一语义）**：类型/量纲/基本单位由字典决定，见 [param-def-design](param-def-design.md)。迁移期可空，回填后为必填 |
| `data_type` | VARCHAR(16) | NOT NULL | `NUMERIC`/`STRING`/`ENUM` |
| `target` | VARCHAR(64) | | 目标值 |
| `min_value` | VARCHAR(64) | | 下限（spec low） |
| `max_value` | VARCHAR(64) | | 上限（spec high） |
| `unit` | VARCHAR(16) | | 单位（℃/Pa/W/…） |
| `spec_type` | VARCHAR(16) | | `CONTROL`(管控)/`MONITOR`(仅监测)/`KEY`(关键参数) |
| `process_id` | VARCHAR(32) | FK→`mds_process.id` | 可选：对齐工艺规范 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(step_id, param_name, tenant_id, deleted)`。
- 工艺窗口是**流程对比的核心对比维度**（参数上下限变化常是工艺变更的本质，见 §4.7）。

### 3.5 `mds_equipment_group` / `mds_equipment_group_member`（设备组，派工支撑实体）

> **新增支撑主数据**：Process Flow Step 的派工目标应是"一组可互换的设备"，而非粗粒度 `equipment_class`。设备组是 class 之上的**精细派工集合**（如 "LITHO_SCANNER_GRP_A" 含 3 台 scanner），MES 据组挑具体设备。

**`mds_equipment_group`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | 唯一 `(code,tenant_id,deleted)` | 组代码 |
| `name` | VARCHAR(128) | | 组名 |
| `class_id` | VARCHAR(32) | FK→`mds_equipment_class.id` | 所属工艺大类（组须在 class 内） |
| `description` | VARCHAR(512) | | 基类 |

**`mds_equipment_group_member`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `group_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment_group.id` | 组 |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 成员设备 |
| `priority` | INT | DEFAULT 0 | 派工优先级 |
| `is_enabled` | BIT | DEFAULT 1 | 是否参与派工（与设备 `lifecycle_status`/`admin_status` 协同） |

- 唯一：`(group_id, equipment_id, tenant_id, deleted)`。
- 闭环：设备组向上经 `class_id` 与 `mds_equipment_class` 一致；向下经 member 与具体设备（含其 `lifecycle_status`/`qualification_state`）一致（[equipment-design §3.1/§3.10](equipment-design.md)）。
- 设备组是**跨域可复用主数据**：除被 Process Flow Step 引用外，MES 排程、AMHS 搬运、PM 分组均可复用，无需各自维护设备清单。

### 3.6 `mds_process_flow_compare` / `mds_process_flow_compare_item`（流程对比快照）

> **流程对比（§4.7）的结果持久化**：fab 工程评审经常要"存档某次流程对比结论"，故对比不是纯内存计算，而是可落库的快照，供追溯与签核。

**`mds_process_flow_compare`（对比头）**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `compare_type` | VARCHAR(16) | NOT NULL | `VERSION_DIFF`/`VARIANT`/`COVERAGE`/`CROSS_PRODUCT` |
| `name` | VARCHAR(128) | | 对比任务名（如 "PF v1.0 vs v2.0"） |
| `base_ref` | VARCHAR(512) | | 基准引用（JSON：pf_id+version 或 route_id 或 family_id） |
| `target_ref` | VARCHAR(512) | | 目标引用（JSON：可多个） |
| `summary_json` | JSON | | 汇总（差异计数/总时长/工序数等） |
| `status` | VARCHAR(16) | NOT NULL | `GENERATED`/`REVIEWED`/`APPROVED` |
| `reviewed_by`/`reviewed_at` | — | | 评审留痕 |
| `description` | VARCHAR(512) | | 基类 |

**`mds_process_flow_compare_item`（差异明细）**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `compare_id` | VARCHAR(32) | NOT NULL, FK→`mds_process_flow_compare.id` | 所属对比 |
| `category` | VARCHAR(32) | NOT NULL | `STEP_ADDED`/`STEP_REMOVED`/`RECIPE_CHANGED`/`PPID_CHANGED`/`EQUIP_GROUP_CHANGED`/`PARAM_CHANGED`/`COVERAGE_GAP`/`KEY_PARAM_DELTA` |
| `entity_type` | VARCHAR(32) | | 差异对象类型（step/param/recipe…） |
| `entity_ref` | VARCHAR(128) | | 对象引用（op_id/step_id/param_name） |
| `from_value` | VARCHAR(512) | | 旧值（基准） |
| `to_value` | VARCHAR(512) | | 新值（目标） |
| `severity` | VARCHAR(16) | | `INFO`/`WARN`/`CRITICAL`（关键参数变更=CRITICAL） |
| `description` | VARCHAR(512) | | 基类 |

---

## 4. 关键设计点（拍板）

### 4.1 双层模型：Route（逻辑） vs Process Flow（展开）（PF1）

- **Route** 管"工序顺序"（节点-边图，[route-design.md §3/§4.1](route-design.md)）。
- **Process Flow** 管"工序实现"（每个 operation → 一个 step，含物理配方/PPID/设备组/参数）。
- 两者经 `mds_process_flow.route_id` 与 `mds_process_flow_step.op_id` 锚定；一个 Route 可有 N 个 Process Flow（不同 `scenario_code`/fab）。
- **独立版本化**：Route 改顺序、Process Flow 改参数，互不影响、各自评审发布。

### 4.2 生命周期 / 版本 / 冻结（复用统一范式）

Process Flow 复用 Route/Product/Recipe 的**统一版本治理范式**：

| 维度 | 说明 | 与 Route 对称性 |
| --- | --- | --- |
| 生命周期态 `status` | `DRAFT→IN_REVIEW→APPROVED→RELEASED→OBSOLETE→ARCHIVED` | 完全同 [route-design §4.2.1](route-design.md) |
| 版本不可变 | RELEASED 后 `(route_id, scenario_code, fab_code, version)` 内容不可变；改须 `cloneAsNewVersion()` | 同 §4.2.2 |
| 冻结 `is_frozen` | 叠加在 RELEASED 上的组织级写锁，与 status 正交 | 同 §4.2.3 |
| 生效窗口 | `effective_from/effective_to` SCD2-lite | 同 §4.2.4 |

> 统一范式使「Route / Product / Recipe / Process Flow」四者版本语义一致，降低工程与系统复杂度（一处学会、处处适用）。

### 4.3 Step 展开：物理配方 + PPID + 设备组 + 参数（PF3）

- **逻辑配方**：仍由 `route_operation.recipe_id → mds_logic_recipe` 表达"做什么"（设计期）。
- **物理配方 + PPID**：由 `process_flow_step.recipe_id → mds_recipe` + `ppid` 表达"怎么做"（展开层）。派工时 MES 据所选设备解析到具体 `mds_equipment_recipe_qual.ppid`（[recipe-design §4.3](recipe-design.md)）。
- **设备组**：`step.equipment_group_id → mds_equipment_group`，MES 在组内挑具体设备（结合设备实时态）。
- **多资格**：`mds_process_flow_step_recipe` 承载"该 step 在哪些设备上有哪个 PPID"，是 step 级配方资格集（对齐设备级 `mds_equipment_recipe_qual`）。

### 4.4 工艺参数规范（Spec Window）（PF4）

- 每 step 的关键参数落 `mds_process_flow_step_param`，含 target/min/max/unit。
- 用途：① SPC 上下限基线；② 派工校验（设备能否满足窗口）；③ **流程对比的核心维度**（参数窗口变化 = 工艺变更本质）。
- 与 [recipe-design](recipe-design.md) 的 `body_ref`（配方正文在外围系统）解耦：本表只存"工艺窗口规范"，不存完整配方参数体。

### 4.5 多方案 / 多 fab（scenario_code）（PF2）

- `scenario_code` + `fab_code` 让同一 Route 承载多套工艺展开：
  - `BASE`（基准量产方案）、`ENG`（研发方案，参数宽松）、`HVM`（量产收紧）、`N+1`（下一代工艺）、`LOCAL_<FAB>`（某 fab 本地化）。
- `is_default` 标记该 Route 下默认采用哪套——MES 投料默认走 `is_default=1` 的 Process Flow，亦可按批指定方案。

### 4.6 与 Route / Recipe / Equipment / Product 闭环（PF6）

`Process Flow` 是串联前几块的**工艺实现层**：

1. **→ Route**：`process_flow.route_id` 确定逻辑顺序骨架。
2. **→ Recipe**：`step.recipe_id`/ppid 落在物理配方与资格（单一来源 [recipe-design](recipe-design.md)）。
3. **→ Equipment**：`step.equipment_group_id → group → member → equipment`（含 class/tech_node/qualification），全链路可派工。
4. **→ Product**：经 Route `product_code` 反推（[product-design §3.2](product-design.md)）——产品 → 路线 → 工艺流。

→ MES 投料时仅凭 `product_code` 即可经 MDS 主数据推导出「路线（顺序）+ 工艺流（物理配方/PPID/设备组/参数）」完整上下文，无需在 MES 重复维护。

### 4.7 流程对比能力（核心，PF5）

> "流程对比"是本文档最重要的工程能力。Process Flow 天然适合对比——它是结构化的工艺定义，diff 即工艺变更洞察。设计 4 类对比，结果可持久化（§3.6）供评审。

#### 4.7.1 版本间 diff（VERSION_DIFF）

- 输入：同一 `(route_id, scenario_code, fab_code)` 的两个 `revision`（如 v1.0 vs v2.0）。
- 算法：
  1. 取两版本的 step 集合，按 `op_id` 对齐。
  2. **结构差异**：`op_id` 仅在新版出现 = `STEP_ADDED`；仅旧版 = `STEP_REMOVED`。
  3. **内容差异**（同 `op_id`）：比较 `recipe_id`/`ppid`/`equipment_group_id` → `RECIPE_CHANGED`/`PPID_CHANGED`/`EQUIP_GROUP_CHANGED`。
  4. **参数差异**：逐 `param_name` 比较 target/min/max → `PARAM_CHANGED`；若 `spec_type=KEY` 则 `severity=CRITICAL`（关键参数变更）。
- 输出：逐条 `mds_process_flow_compare_item` + `summary_json`（差异计数、总时长变化 Δ、关键参数变更数）。

#### 4.7.2 多方案横评（VARIANT）

- 输入：同一 `route_id` 的多个 Process Flow（不同 `scenario_code`，如 BASE vs HVM vs N+1）。
- 算法：对每套 PF 聚合——step 数、Σ`default_time`（总标准周期）、Σ`yield_expect`、关键参数分布、设备组覆盖。
- 输出：横向对比矩阵（每套方案一行指标），供工艺/良率/产能工程师选方案。

#### 4.7.3 覆盖性 / 一致性校验（COVERAGE）

- 输入：单个 Process Flow。
- 校验规则：
  1. **正向覆盖**：Route 的每个 `mds_route_operation` 至少被 1 个 step 展开（`op_id` 全覆盖），否则 `COVERAGE_GAP`。
  2. **反向一致**：每个 step 的 `op_id` 必须属于其 `process_flow.route_id`（不能展开 Route 之外的工序）。
  3. **可执行性**：每个 step 须 `recipe_id` + `ppid`（或 step_recipe 资格集非空）+ `equipment_group_id` 齐备，且组内成员设备对该物理配方有合格资格（`mds_equipment_recipe_qual` 存在）。缺项报 `COVERAGE_GAP`/`CRITICAL`。
- 输出：覆盖报告（缺口清单），是 Process Flow 发布前（`RELEASED` 前）的强制门禁之一。

#### 4.7.4 跨产品 / 跨 Family 工艺对比（CROSS_PRODUCT）

- 输入：一个 `product_family_id`（或多 product）。
- 算法：沿 `family → product → default_route → process_flow` 拉出各产品的工艺流，diff 其 step 集合 / 关键参数 / 设备组，识别"同族产品工艺差异"与"可平台化合并的工艺"。
- 价值：支撑产品平台化（Platforming）——发现 A/B 产品工艺高度雷同，可合并路线降低维护成本。

#### 4.7.5 对比服务接口（建议）

```
ProcessFlowCompareService
  ├── compareVersions(pfId, v1, v2)        → 生成 VERSION_DIFF 快照
  ├── compareVariants(routeId, pfIds)      → 生成 VARIANT 横评矩阵
  ├── checkCoverage(pfId)                  → 生成 COVERAGE 报告（发布门禁）
  └── compareCrossProduct(familyId)        → 生成 CROSS_PRODUCT 对比
  （均落 mds_process_flow_compare + _item，供评审签核）
```

### 4.8 软删 / 租户 / 主键（与平台一致）

- 所有实体继承 `BaseDefData`：`String id`（雪花/UUIDv7）、`tenant_id`、`@Version`、`deleted`、审计列。
- 唯一约束均含 `(..., tenant_id, deleted)`（T2.5）。
- 行级过滤：`@SQLRestriction("deleted=false")` + `@Filter(cimTenantFilter)`；`@FilterDef` 放实体包 `package-info.java`。

---

## 5. 与平台底座衔接（强制对齐）

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData`（零 Hibernate 基类） | 所有 Process Flow / 设备组实体继承，含 `@Version`、审计五件套、`tenant_id`、`deleted` |
| `@History(SNAPSHOT)` | process_flow / step / param / 设备组变更落 `{entity}_hist`（含 op_type/operator/op_time/trx_id/change_set_json），工艺展开修改自动留痕 |
| `cimTenantFilter` + `TenantContext` | 多租户行级过滤 |
| T2.5 软删唯一约束 | 唯一键含 `deleted`，避免软删后唯一冲突 |
| `IdGenerator`（雪花/UUIDv7） | 主键生成 |
| 主历成对迁移 | DDL 与 `db/migration/{common,mysql}` 成对：结构迁移 + 历史表 + 种子数据 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
-- 工艺流头
CREATE TABLE mds_process_flow (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  flow_code     VARCHAR(64)  NOT NULL,
  name          VARCHAR(128),
  route_id      VARCHAR(32)  NOT NULL,
  scenario_code VARCHAR(32)  NOT NULL,
  fab_code      VARCHAR(32),
  revision       VARCHAR(16)  NOT NULL,
  status        VARCHAR(16)  NOT NULL,
  is_frozen     BIT          DEFAULT 0,
  frozen_by     VARCHAR(64),
  frozen_at     DATETIME(3),
  frozen_reason VARCHAR(256),
  unfreeze_at   DATETIME(3),
  is_default    BIT          DEFAULT 0,
  effective_from DATETIME(3),
  effective_to   DATETIME(3),
  description   VARCHAR(512),
  version_      BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pf PRIMARY KEY (id),
  CONSTRAINT uq_mds_pf UNIQUE (route_id, scenario_code, fab_code, revision, tenant_id, deleted),
  CONSTRAINT fk_pf_route FOREIGN KEY (route_id) REFERENCES mds_route (id)
);

-- 工艺流步骤（工序展开）
CREATE TABLE mds_process_flow_step (
  id                 VARCHAR(32)  NOT NULL,
  tenant_id          VARCHAR(32)  NOT NULL,
  process_flow_id    VARCHAR(32)  NOT NULL,
  op_id              VARCHAR(32)  NOT NULL,
  step_seq           INT          NOT NULL,
  step_name          VARCHAR(128),
  recipe_id          VARCHAR(32),
  ppid               VARCHAR(128),
  equipment_group_id VARCHAR(32),
  default_time       INT,
  yield_expect       DECIMAL(5,2),
  is_rework          BIT          DEFAULT 0,
  description        VARCHAR(512),
  version_           BIGINT       NOT NULL DEFAULT 0,
  deleted            BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pfs PRIMARY KEY (id),
  CONSTRAINT uq_mds_pfs UNIQUE (process_flow_id, op_id, tenant_id, deleted),
  CONSTRAINT fk_pfs_pf FOREIGN KEY (process_flow_id) REFERENCES mds_process_flow (id),
  CONSTRAINT fk_pfs_op FOREIGN KEY (op_id) REFERENCES mds_route_operation (id),
  CONSTRAINT fk_pfs_recipe FOREIGN KEY (recipe_id) REFERENCES mds_recipe (id),
  CONSTRAINT fk_pfs_grp FOREIGN KEY (equipment_group_id) REFERENCES mds_equipment_group (id)
);
CREATE INDEX idx_pfs_grp ON mds_process_flow_step (equipment_group_id);

-- 步骤 ↔ 多物理配方 / PPID 资格
CREATE TABLE mds_process_flow_step_recipe (
  id          VARCHAR(32)  NOT NULL,
  tenant_id   VARCHAR(32)  NOT NULL,
  step_id     VARCHAR(32)  NOT NULL,
  recipe_id   VARCHAR(32)  NOT NULL,
  equipment_id VARCHAR(32),
  ppid        VARCHAR(128) NOT NULL,
  is_default  BIT          DEFAULT 0,
  qual_status VARCHAR(16)  NOT NULL DEFAULT 'QUALIFIED',
  description VARCHAR(512),
  version_    BIGINT       NOT NULL DEFAULT 0,
  deleted     BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pfsr PRIMARY KEY (id),
  CONSTRAINT uq_mds_pfsr UNIQUE (step_id, equipment_id, ppid, tenant_id, deleted),
  CONSTRAINT fk_pfsr_step FOREIGN KEY (step_id) REFERENCES mds_process_flow_step (id),
  CONSTRAINT fk_pfsr_recipe FOREIGN KEY (recipe_id) REFERENCES mds_recipe (id),
  CONSTRAINT fk_pfsr_eqp FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id)
);

-- 步骤工艺参数规范 / 窗口
CREATE TABLE mds_process_flow_step_param (
  id          VARCHAR(32)  NOT NULL,
  tenant_id   VARCHAR(32)  NOT NULL,
  step_id       VARCHAR(32)  NOT NULL,
  param_name    VARCHAR(64)  NOT NULL,
  param_def_id  VARCHAR(32),
  data_type     VARCHAR(16)  NOT NULL,
  target      VARCHAR(64),
  min_value   VARCHAR(64),
  max_value   VARCHAR(64),
  unit        VARCHAR(16),
  spec_type   VARCHAR(16),
  process_id  VARCHAR(32),
  description VARCHAR(512),
  version_    BIGINT       NOT NULL DEFAULT 0,
  deleted     BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pfsp PRIMARY KEY (id),
  CONSTRAINT uq_mds_pfsp UNIQUE (step_id, param_name, tenant_id, deleted),
  CONSTRAINT fk_pfsp_step FOREIGN KEY (step_id) REFERENCES mds_process_flow_step (id),
  CONSTRAINT fk_pfsp_proc FOREIGN KEY (process_id) REFERENCES mds_process (id),
  CONSTRAINT fk_pfsp_param_def FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id)
);

-- 设备组（派工支撑实体）
CREATE TABLE mds_equipment_group (
  id          VARCHAR(32)  NOT NULL,
  tenant_id   VARCHAR(32)  NOT NULL,
  code        VARCHAR(64)  NOT NULL,
  name        VARCHAR(128),
  class_id    VARCHAR(32),
  description VARCHAR(512),
  version_    BIGINT       NOT NULL DEFAULT 0,
  deleted     BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqg PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqg_code UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_eqg_class FOREIGN KEY (class_id) REFERENCES mds_equipment_class (id)
);

CREATE TABLE mds_equipment_group_member (
  id           VARCHAR(32)  NOT NULL,
  tenant_id    VARCHAR(32)  NOT NULL,
  group_id     VARCHAR(32)  NOT NULL,
  equipment_id VARCHAR(32)  NOT NULL,
  priority     INT          DEFAULT 0,
  is_enabled   BIT          DEFAULT 1,
  version_     BIGINT       NOT NULL DEFAULT 0,
  deleted      BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eqgm PRIMARY KEY (id),
  CONSTRAINT uq_mds_eqgm UNIQUE (group_id, equipment_id, tenant_id, deleted),
  CONSTRAINT fk_eqgm_group FOREIGN KEY (group_id) REFERENCES mds_equipment_group (id),
  CONSTRAINT fk_eqgm_eqp FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id)
);

-- 流程对比快照
CREATE TABLE mds_process_flow_compare (
  id           VARCHAR(32)  NOT NULL,
  tenant_id    VARCHAR(32)  NOT NULL,
  compare_type VARCHAR(16)  NOT NULL,
  name         VARCHAR(128),
  base_ref     VARCHAR(512),
  target_ref   VARCHAR(512),
  summary_json JSON,
  status       VARCHAR(16)  NOT NULL,
  reviewed_by  VARCHAR(64),
  reviewed_at  DATETIME(3),
  description  VARCHAR(512),
  version_     BIGINT       NOT NULL DEFAULT 0,
  deleted      BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pfc PRIMARY KEY (id)
);

CREATE TABLE mds_process_flow_compare_item (
  id           VARCHAR(32)  NOT NULL,
  tenant_id    VARCHAR(32)  NOT NULL,
  compare_id   VARCHAR(32)  NOT NULL,
  category     VARCHAR(32)  NOT NULL,
  entity_type  VARCHAR(32),
  entity_ref   VARCHAR(128),
  from_value   VARCHAR(512),
  to_value     VARCHAR(512),
  severity     VARCHAR(16),
  description  VARCHAR(512),
  version_     BIGINT       NOT NULL DEFAULT 0,
  deleted      BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pfci PRIMARY KEY (id),
  CONSTRAINT fk_pfci_compare FOREIGN KEY (compare_id) REFERENCES mds_process_flow_compare (id)
);
```

> 历史表 `mds_process_flow_hist` / `mds_process_flow_step_hist` / … 由 `@History(SNAPSHOT)` 运行时自动生成（结构为对应实体 + 历史元数据列）。`mds_equipment_group` / `mds_equipment_group_member` 同理。

---

## 7. 代码结构（包路径）

```
com.cim.mds.processflow
  ├── entity
  │   ├── ProcessFlow.java          # @Entity 继承 BaseDefData + @History(SNAPSHOT)；status/version/is_frozen/scenario_code
  │   ├── ProcessFlowStep.java       # @Entity 继承 BaseDefData（op_id/recipe_id/ppid/equipment_group_id）
  │   ├── ProcessFlowStepRecipe.java # @Entity 继承 BaseDefData（step↔多PPID资格）
  │   ├── ProcessFlowStepParam.java  # @Entity 继承 BaseDefData（工艺窗口 target/min/max/unit）
  │   ├── EquipmentGroup.java        # @Entity 继承 BaseDefData（class_id FK）
  │   ├── EquipmentGroupMember.java  # @Entity 继承 BaseDefData（group↔equipment）
  │   ├── ProcessFlowCompare.java    # @Entity 继承 BaseDefData（compare_type/summary_json/status）
  │   ├── ProcessFlowCompareItem.java# @Entity 继承 BaseDefData（逐条差异）
  │   └── package-info.java          # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── ProcessFlowRepository.java
  │   ├── ProcessFlowStepRepository.java
  │   ├── ProcessFlowStepRecipeRepository.java
  │   ├── ProcessFlowStepParamRepository.java
  │   ├── EquipmentGroupRepository.java
  │   └── ProcessFlowCompareRepository.java
  ├── service
  │   ├── ProcessFlowService.java          # 继承 AbstractJpaService；submitForReview/approve/release/freeze/unfreeze/obsolete/archive/cloneAsNewVersion
  │   ├── EquipmentGroupService.java        # 设备组维护（含 member 同步）
  │   ├── ProcessFlowValidator.java         # 覆盖性校验（§4.7.3，RELEASED 前门禁）
  │   └── ProcessFlowCompareService.java    # 4 类对比（§4.7.5），落 compare + compare_item
  └── web
      └── ProcessFlowController.java        # 查询/版本发布/克隆/对比/覆盖校验
```

---

## 8. 待补 / 后续

- **MES 运行实例衔接契约**：`lot` 对 `process_flow_id` / `step_id` / 实际 PPID / 实时参数实绩的导出订阅格式；step 当前执行态、条件分支求值回写（与 [route-design §8](route-design.md) 联动）。
- **route 图校验服务完善**：无环除 REWORK、PARALLEL_SPLIT/JOIN 配对、起点唯一、可达性（[route-design §8](route-design.md)）。
- **Route 级结构对比**：两条 Route 的 operation/flow 差异对比（工艺路线选型），可作为 RouteValidator/compare 的延伸能力。
- **设备组与实时态协同**：派工时设备组 member 的 `is_enabled` 须结合设备 `lifecycle_status`/`admin_status`/`qualification_state` 实时过滤（[equipment-design §3.10](equipment-design.md)）。
- **API 设计**：Process Flow 查询/版本发布/克隆、对比接口、覆盖校验接口、设备组维护。
- **约束层衔接（红线）**：Process Flow 描述"工艺怎么做"，其上的**工艺红线**（Q-Time、组批上限、SPC spec、返工上限等跨 step/边的规则）由 [约束设计](constraint-design.md) 承载——`SPC_SPEC` 引用 `mds_process_flow_step_param` 的 spec 上下限，`Q_TIME` 绑定到 `mds_route_flow` 边；发布前可经约束的**覆盖分析 COVERAGE**（constraint-design §4.6.4）做红线体检。
- **与既有文档闭环**：本文引用 `mds_route` / `mds_route_operation` / `mds_process`（[route-design](route-design.md)）、`mds_recipe` / `mds_equipment_recipe_qual` / `mds_logic_recipe`（[recipe-design](recipe-design.md)）、`mds_equipment` / `mds_equipment_class`（[equipment-design](equipment-design.md)）、`mds_product`（[product-design](product-design.md)）。
```
