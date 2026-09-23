# MDS 约束（Constraint）主数据设计

> 本文是 MDS 的**第二条横切治理层**——与[命名/编码规则](naming-rule-design.md)（编码治理层）并列的**关系规则治理层**。
> 前七块主数据（位置 / 设备 / 路线 / 产品 / 载具 / 配方 / 仓库）定义「**有什么**」；[Process Flow](process-flow-design.md) 定义「**怎么做**」；本文定义「**什么与什么之间必须/不得成立**」——即主数据之间、以及主数据与运行上下文之间的**约束规则**。
>
> 设计哲学延续全篇：**MDS 拥有约束的「定义 + 试算」，约束的「实时求值与执行」归 MES / EAP / AMHS**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么 MDS 需要约束层（核心拍板 CT1）

七块主数据彼此之间存在大量**跨实体关系规则**：一台设备能不能跑某个产品、某载具能不能进某个工艺区、清洗后到炉管之间最长能等多久、一批最多混几片、返工最多几次、库容上限多少……这些规则**不属于任何单一主数据**，而是**主数据之间的关系**。

如果任由下游各系统自行硬编码这些判断，会导致：
1. **规则漂移**：MES 一套、EAP 一套、AMHS 一套、报表再一套，口径不一致；
2. **改规则要改代码**：工艺改一个 Q-Time 上限，要四处改代码、四处发版；
3. **无法审计**：规则散落代码里，工艺评审看不到「当前生效的规则全集」；
4. **无法试算**：投料/派工前无法预演「这批按现规则能不能走」。

因此 MDS 需要一个**声明式的约束治理层**：

| 层 | 治理对象 | 载体 | 回答的问题 |
| --- | --- | --- | --- |
| 编码治理层 | 实体的 `code` 生成 | [naming-rule-design.md](naming-rule-design.md) | 「这个东西怎么命名」 |
| **约束治理层（本文）** | **实体之间的关系规则** | `mds_constraint` 等 | 「什么与什么之间必须/不得成立」 |

> **结论（CT1）**：约束是 MDS 的**横切关系规则层**，不是某一主数据的附属字段。约束是**声明式数据**（配置即规则），不是硬编码分支；**MDS 拥有约束的定义与试算能力，MES / EAP / AMHS 拥有约束的实时求值与执行**。

### 0.2 MDS 拥有 vs 下游拥有（边界重申）

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 约束定义 `mds_constraint` / 参数 / 约束集 / 绑定 | **MDS** | 声明式规则，带类型、作用域、条件、参数、严重度、优先级、版本 |
| 约束试算 / 冲突检测 / 覆盖分析（设计期） | **MDS** | §4.6 的 4 类能力，结果落 `mds_constraint_eval` 供评审 |
| 编译产出的「约束快照 / 规则包」 | **MDS** | 供下游订阅缓存（避免每请求回查），格式见 §4.7 |
| **运行上下文求值**（lot 投料 / 组批 / 派工 / 搬运 / move-in 时判定） | **MES / EAP / AMHS** | 按 MDS 下发规则，结合**实时态**求值 |
| **约束违反处置**（阻断 / 告警 / 越权放行 / 触发返工） | **MES / EAP / AMHS** | MDS 只给 severity 语义，动作由下游执行 |
| **实时态数据**（设备 E10、载具 E87、WIP、槽位占用） | **MES / EAP / AMHS** | MDS 不存，约束求值时由下游注入 |

### 0.3 与七块主数据 + Process Flow 的闭环映射

| 约束族 | 关联主数据 | 外键 / 引用 | 见 |
| --- | --- | --- | --- |
| 相容性 COMPATIBILITY | 设备 / 配方 / 载具 / 工艺区 | `mds_equipment_recipe_qual` / `mds_carrier_type_compat` / `mds_equipment_capability` | [设备 §3.7/§3.8](equipment-design.md)、[载具 §4.4](carrier-design.md) |
| 时序 TIMING（Q-Time） | 工序流（边） | `mds_constraint_binding.scope_ref → mds_route_flow.id` | [路线 §3.4](route-design.md) |
| 顺序 SEQUENCE | 工序 / 边 | `mds_route_operation` / `mds_route_flow`（FRONT/BACK/REWORK） | [路线 §3.3/§3.4](route-design.md) |
| 批量 BATCHING | 设备 / 工艺参数 | `mds_equipment.process_mode` / `mds_process_flow_step_param` | [设备 §3.11](equipment-design.md)、[工艺流 §3.4](process-flow-design.md) |
| 能力 CAPABILITY | 设备 / 产品 | `mds_equipment_tech_node` ∩ `mds_product.tech_node`；`wafer_size` | [设备 §3.6](equipment-design.md)、[产品 §4.4](product-design.md) |
| 容量 CAPACITY | 仓库 / 在制 | `mds_bank.capacity` | [仓库 §3.1](bank-design.md) |
| 窗口 WINDOW | 路线 / 产品 / 设备 | `mds_route.effective_from/to` | [路线 §4.2.4](route-design.md) |
| 质量 QUALITY | 工艺参数 | `mds_process_flow_step_param`（spec 上下限） | [工艺流 §3.4](process-flow-design.md) |

> 约束（本文）与 Process Flow（[process-flow-design.md](process-flow-design.md)）是**互补**：Process Flow 描述「工艺怎么做」（工艺实现），约束描述「过程中的红线」（如"清洗后必须在 6h 内进炉"——这条红线跨两个 step/operation，不属于任何一个 step，只有约束层能表达）。

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E10** | 设备状态与可用性（RAM）；状态迁移合法性 | 设备可用性的约束（EQUIP_CALENDAR / 状态门禁）与 [设备 §3.14](equipment-design.md) 状态机一致 |
| **SEMI E5 / E30（GEM）** | Control State（offline/online-remote）决定能否接受主机派工 | 派工约束的前置门禁（须 ONLINE-REMOTE），[设备 §3.12](equipment-design.md) |
| **SEMI E40** | Process Program（PPID）按设备实例存在，须资格认证 | 配方资格约束（EQUIP_RECIPE_QUAL），复用 [recipe-design §3.4](recipe-design.md) |
| **SEMI E87 / E15** | 载具与设备/工艺区的兼容（污染控制） | 载具相容约束（CARRIER_AREA_COMPAT），复用 [载具 §4.4](carrier-design.md) |
| **SEMI E94** | Process Flow 的 operation/step 结构 | 时序约束锚定 operation 之间的 flow 边（§4.2 Q_TIME） |
| **fab MES 实践（Camstar / Opcenter / 199）** | **Q-Time（Queue Time）**、组批规则、派工规则、日历、采样规则普遍以**配置化 Rule / Constraint** 形式维护，而非代码 | 本文统一声明式约束模型（§4.1）+ 约束集（§4.4） |
| **ISA-95 / IEC 62264** | 主数据（Master Data）与其**关系/规则**分层管理 | 约束作为横切层，独立于七块主数据 |
| **MDM 通用实践** | 规则可版本化、可废弃而不改存量；规则集可复用组合 | 约束生命周期（§4.7）+ 约束集（§4.4） |

---

## 2. 术语澄清（避免歧义）

| 术语 | 含义 | 易混淆点（明确区分） |
| --- | --- | --- |
| **Constraint / 约束** | 一条声明式规则：「在条件 C 下，主体 S 与客体 O 之间必须/不得满足 P」 | 不是硬编码的判断分支；是**数据** |
| **constraint_type / 约束类型** | 约束的语义类别（如 `Q_TIME`/`BATCH_MAX_SIZE`），决定求值语义与所需参数 | 区别于 `family`（更粗的语义族，9 类） |
| **family / 语义族** | 约束的大类归属：相容/时序/顺序/批量/能力/容量/窗口/质量/其他 | 用于分类浏览与覆盖分析 |
| **subject / 主体** | 约束**作用在谁身上**（`subject_type`：产品/路线/工序/边/设备/载具/仓库…） | 一元约束只有主体；二元约束有主体+客体 |
| **object / 客体** | 二元约束的**另一端**（`object_type`：设备类/配方/载具类型/工艺区…） | 如「产品 P 只能上设备类 E」：主体=产品，客体=设备类 |
| **assertion / 断言** | `MUST`（必须成立）/ `MUST_NOT`（必须不成立） | 正/反向断言，冲突检测的基础（§4.6.3） |
| **condition_expr / 条件** | WHEN 子句：约束仅在条件成立时适用（空=恒适用） | 由下游求值引擎解释（表达式 DSL） |
| **hard_soft + severity** | 硬约束（`BLOCK` 阻断）/ 软约束（`WARN` 告警、`INFO` 提示） | 硬约束不可越权放行；软约束可带审批放行并留痕 |
| **ConstraintSet / 约束集** | 一组约束的**可复用组合**（带集合级优先级/严重度覆盖） | 与「约束」是组合关系；绑定到主数据时用集比逐条更省 |
| **Binding / 绑定** | 把约束或约束集挂到**具体主数据实例**上（带作用域分层） | 作用域：全局 → Family → Product/Route/Area → Operation/Flow/Equipment 实例 |
| **EvaluationContext / 求值上下文** | 求值时注入的**运行上下文**（product/route/op/equipment/carrier/lot 属性 + 实时态） | 是下游求值的输入；MDS 试算时用一种「声明式静态上下文」 |
| **试算 / 预演（Simulation）** | MDS 在设计期**静态模拟**约束求值（不接触实时态），产出预测结论 | 区别于 MES 的**运行期求值**（含实时态） |

---

## 3. 实体总览与 ER

```
                      ┌───────────────────────────┐
                      │ mds_constraint (约束定义)  │
                      │  type / family / assertion │
                      │  subject/object / severity │
                      └───────┬─────────┬─────────┘
                              │         │
             ┌────────────────┘         └──────────────────┐
             ▼                                              ▼
   mds_constraint_param                        mds_constraint_set (约束集)
   (参数: max_minutes / max_size ...)          └──< mds_constraint_set_member
                                                        (集 ↔ 约束, override 优先级/严重度)

                      ┌───────────────────────────┐
                      │ mds_constraint_binding     │  ← 把「约束 / 集」挂到具体实例
                      │  scope_type / scope_ref    │     GLOBAL / PRODUCT_FAMILY / PRODUCT /
                      │  target_kind(constraint|set)│     ROUTE / ROUTE_OPERATION / ROUTE_FLOW /
                      │  priority_override / ...   │     EQUIPMENT_CLASS / EQUIPMENT / EQUIPMENT_GROUP /
                      └───────────────────────────┘     RECIPE / CARRIER_TYPE / BANK / LOCATION_AREA

                      ┌───────────────────────────┐
                      │ mds_constraint_eval (试算头)│  ← 4 类试算结果持久化 (§4.6)
                      │  eval_type / context_json  │
                      └───────┬───────────────────┘
                              ▼
                      mds_constraint_eval_item (逐条求值结果 PASS/FAIL/SKIP)
```

| 实体 | 表名 | 是否主数据 | 说明 |
| --- | --- | --- | --- |
| 约束定义 | `mds_constraint` | 是（配置） | 一条声明式规则：类型/族/主体客体/断言/条件/严重度/优先级/版本 |
| 约束参数 | `mds_constraint_param` | 是（配置） | 约束的类型相关参数（阈值/单位/引用） |
| 约束集 | `mds_constraint_set` | 是（配置） | 约束的可复用组合 |
| 约束集成员 | `mds_constraint_set_member` | 是（配置） | 集 ↔ 约束，可覆盖优先级/严重度 |
| 约束绑定 | `mds_constraint_binding` | 是（配置） | 约束/集 → 具体主数据实例的作用域绑定 |
| 试算快照 | `mds_constraint_eval` | 是 | 试算/冲突/覆盖分析结果头（评审留痕） |
| 试算明细 | `mds_constraint_eval_item` | 是 | 逐条约束的求值明细 |

> 所有实体继承 `BaseDefData`（见 §5），含 `id` / `tenant_id` / `@Version` / `deleted` / 审计列；历史表 `{entity}_hist` 由 `@History(SNAPSHOT)` 自动生成。

### 3.1 `mds_constraint`（约束定义）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | 雪花 / UUIDv7 |
| `code` | VARCHAR(64) | NOT NULL | 约束代码（如 `CT-QTIME-POST-CLEAN`） |
| `name` | VARCHAR(128) | | 约束名称 |
| `constraint_type` | VARCHAR(32) | NOT NULL | 类型枚举（见 §4.2 类型目录）：`Q_TIME`/`BATCH_MAX_SIZE`/`EQUIP_RECIPE_QUAL`/… |
| `family` | VARCHAR(16) | NOT NULL | 语义族：`COMPATIBILITY`/`TIMING`/`SEQUENCE`/`BATCHING`/`CAPABILITY`/`CAPACITY`/`WINDOW`/`QUALITY`/`OTHER` |
| `subject_type` | VARCHAR(32) | NOT NULL | 主体类型：`PRODUCT`/`PRODUCT_FAMILY`/`ROUTE`/`ROUTE_OPERATION`/`ROUTE_FLOW`/`EQUIPMENT`/`EQUIPMENT_CLASS`/`EQUIPMENT_GROUP`/`RECIPE`/`CARRIER_TYPE`/`BANK`/`LOCATION_AREA`/`LOT` |
| `object_type` | VARCHAR(32) | | 客体类型（一元约束为空）：同上枚举 |
| `assertion` | VARCHAR(16) | NOT NULL DEFAULT 'MUST' | `MUST`/`MUST_NOT` |
| `condition_expr` | VARCHAR(512) | | WHEN 条件表达式（空=无条件适用）；DSL 由下游引擎解释 |
| `hard_soft` | VARCHAR(8) | NOT NULL DEFAULT 'HARD' | `HARD`/`SOFT` |
| `severity` | VARCHAR(16) | NOT NULL DEFAULT 'BLOCK' | `BLOCK`/`WARN`/`INFO`（HARD 通常 BLOCK；SOFT 为 WARN/INFO） |
| `priority` | INT | DEFAULT 0 | 同作用域求值优先级（大者先判，命中 BLOCK 即可短路） |
| `allow_override` | BIT | DEFAULT 0 | 是否允许越权放行（仅 SOFT 有效；放行须留痕） |
| `message_template` | VARCHAR(512) | | 违反时的提示模板（含占位符，如 `Q-Time 超限 {actual}/{max} min`） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED`（仅 ACTIVE 参与求值） |
| `revision` | VARCHAR(16) | NOT NULL | 规则版本（改语义须克隆升版本，存量绑定不受影响） |
| `is_frozen` | BIT | DEFAULT 0 | 冻结（与 status 正交，量产/法规锁定） |
| `effective_from`/`effective_to` | DATETIME(3) | | 生效窗口（SCD2-lite） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。
- 引用完整性：`constraint_type` 决定**必填参数集**（§4.2），保存时由 `ConstraintValidator` 校验参数齐备。

### 3.2 `mds_constraint_param`（约束参数）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `constraint_id` | VARCHAR(32) | NOT NULL, FK→`mds_constraint.id` | 所属约束 |
| `param_key` | VARCHAR(64) | NOT NULL | 参数键（如 `max_minutes`/`max_size`/`max_times`/`area_code`） |
| `param_value` | VARCHAR(128) | | 参数值（数值/字符串/枚举） |
| `data_type` | VARCHAR(16) | NOT NULL DEFAULT 'NUMERIC' | `NUMERIC`/`STRING`/`ENUM`/`REF` |
| `unit` | VARCHAR(16) | | 单位（`MIN`/`HOUR`/`PCS`/`WAFER`，无单位为空） |
| `ref_type` | VARCHAR(32) | | `data_type=REF` 时指向的实体类型（如 `LOCATION_AREA`） |
| `ref_value` | VARCHAR(128) | | `data_type=REF` 时指向的实例（code/id） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(constraint_id, param_key, tenant_id, deleted)`。
- 例：`Q_TIME` 的参数为 `max_minutes=360`（unit=MIN）；`BATCH_MAX_SIZE` 为 `max_size=25`（unit=WAFER）。

### 3.3 `mds_constraint_set` / `mds_constraint_set_member`（约束集与成员）

> 约束常成组复用（如「LITHO 区派工约束集」「300mm FOUP 搬运约束集」）。约束集把一组约束打包，绑定一次即可整体生效。

**`mds_constraint_set`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 集代码（如 `CS-LITHO-DISPATCH`） |
| `name` | VARCHAR(128) | | 集名称 |
| `set_category` | VARCHAR(32) | | 归类提示：`DISPATCH`/`BATCH`/`TRANSPORT`/`QUALITY`/`SAFETY`/`MIXED` |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `revision` | VARCHAR(16) | NOT NULL | 集版本 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

**`mds_constraint_set_member`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `set_id` | VARCHAR(32) | NOT NULL, FK→`mds_constraint_set.id` | 所属集 |
| `constraint_id` | VARCHAR(32) | NOT NULL, FK→`mds_constraint.id` | 成员约束 |
| `priority_override` | INT | | 集内覆盖优先级（空=用约束自身） |
| `severity_override` | VARCHAR(16) | | 集内覆盖严重度（如全局 WARN、某集内升为 BLOCK） |
| `is_enabled` | BIT | DEFAULT 1 | 是否启用 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(set_id, constraint_id, tenant_id, deleted)`。

### 3.4 `mds_constraint_binding`（约束/集 → 实例绑定，作用域分层）

> 约束是「规则」，绑定是「把规则挂到具体主数据实例上」。同一条 `Q_TIME` 约束可绑定到多条 flow 边；同一个约束集可绑定到整个 AREA。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `scope_type` | VARCHAR(32) | NOT NULL | 作用域层级：`GLOBAL`/`PRODUCT_FAMILY`/`PRODUCT`/`ROUTE`/`ROUTE_OPERATION`/`ROUTE_FLOW`/`EQUIPMENT_CLASS`/`EQUIPMENT`/`EQUIPMENT_GROUP`/`RECIPE`/`CARRIER_TYPE`/`BANK`/`LOCATION_AREA` |
| `scope_ref` | VARCHAR(128) | NOT NULL | 作用域目标（实例 id 或 code；`GLOBAL` 固定为 `*`） |
| `target_kind` | VARCHAR(16) | NOT NULL | `CONSTRAINT`/`SET` |
| `constraint_id` | VARCHAR(32) | FK→`mds_constraint.id` | `target_kind=CONSTRAINT` 时非空 |
| `set_id` | VARCHAR(32) | FK→`mds_constraint_set.id` | `target_kind=SET` 时非空 |
| `priority_override` | INT | | 覆盖优先级（作用域越深越具体，见 §4.5 解析规则） |
| `severity_override` | VARCHAR(16) | | 覆盖严重度 |
| `is_enabled` | BIT | DEFAULT 1 | 是否启用（可临时禁用而不删） |
| `effective_from`/`effective_to` | DATETIME(3) | | 绑定级生效窗口 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(scope_type, scope_ref, target_kind, constraint_id, set_id, tenant_id, deleted)`。
- **Q-Time 的落点**：`Q_TIME` 约束绑定到 `scope_type=ROUTE_FLOW`、`scope_ref=<mds_route_flow.id>`，天然表达「这条边上两个工序之间的最大等待」——与 [route-design §3.4](route-design.md) 的边模型严丝合缝。

### 3.5 `mds_constraint_eval` / `mds_constraint_eval_item`（试算快照）

> §4.6 的 4 类试算能力的结果**持久化**，供工艺评审签核与追溯（与 [process-flow-design §3.6](process-flow-design.md) 的对比快照同源范式）。

**`mds_constraint_eval`（试算头）**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `eval_type` | VARCHAR(16) | NOT NULL | `SINGLE`/`CONTEXT`/`CONFLICT`/`COVERAGE` |
| `name` | VARCHAR(128) | | 任务名（如 "投料预演 P-28N-A"） |
| `context_json` | JSON | | 求值上下文（product/route/op/equipment/carrier/lot 属性…） |
| `summary_json` | JSON | | 汇总（约束总数 / PASS / FAIL / SKIP / 最高严重度） |
| `status` | VARCHAR(16) | NOT NULL | `GENERATED`/`REVIEWED`/`APPROVED` |
| `reviewed_by`/`reviewed_at` | — | | 评审留痕 |
| `description` | VARCHAR(512) | | 基类 |

**`mds_constraint_eval_item`（求值明细）**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `eval_id` | VARCHAR(32) | NOT NULL, FK→`mds_constraint_eval.id` | 所属试算 |
| `constraint_id` | VARCHAR(32) | FK→`mds_constraint.id` | 命中的约束 |
| `binding_id` | VARCHAR(32) | | 命中的绑定（可追溯来源作用域） |
| `result` | VARCHAR(16) | NOT NULL | `PASS`/`FAIL`/`SKIP`（条件不适用=SKIP） |
| `severity` | VARCHAR(16) | | 生效严重度（含 override 后） |
| `subject_ref` | VARCHAR(128) | | 主体实例引用 |
| `object_ref` | VARCHAR(128) | | 客体实例引用 |
| `message` | VARCHAR(512) | | 渲染后的提示（由 message_template 填充） |
| `detail_json` | JSON | | 求值细节（实际值/阈值/来源作用域链） |
| `description` | VARCHAR(512) | | 基类 |

---

## 4. 关键设计点（拍板）

### 4.1 统一声明式约束模型（CT2）

一条约束是**五元组**，全部为数据：

```
Constraint = ⟨ Type, Subject↔Object, Assertion, Condition, Params ⟩ + ⟨ severity, priority, scope(binding) ⟩
```

| 维度 | 字段 | 作用 |
| --- | --- | --- |
| 类型 | `constraint_type` / `family` | 决定**求值语义**与必填参数集 |
| 主体/客体 | `subject_type` / `object_type` | 决定约束**作用对象**（一元=仅主体；二元=主体↔客体） |
| 断言 | `assertion` (`MUST`/`MUST_NOT`) | 正向/反向要求（冲突检测基础） |
| 条件 | `condition_expr` | WHEN 子句，仅在条件成立时适用 |
| 参数 | `mds_constraint_param` | 阈值/单位/引用（Q-Time 上限、批上限…） |
| 严重度/优先级 | `hard_soft` / `severity` / `priority` | 违反处置强度与判定顺序 |
| 作用域 | `mds_constraint_binding` | 挂到哪些实例上 |

> **不做硬编码**：下游求值引擎只认「类型 + 参数 + 条件」这组数据，新增规则类型 = 扩枚举 + 加参数，不改调用方代码。

### 4.2 约束类型目录与 9 大语义族（CT3）

**语义族（family）总览：**

| family | 语义 | 典型类型 |
| --- | --- | --- |
| `COMPATIBILITY` | 相容性：谁能与谁搭配 | `EQUIP_PRODUCT_COMPAT` / `EQUIP_RECIPE_QUAL` / `CARRIER_AREA_COMPAT` / `EQUIP_CARRIER_COMPAT` |
| `TIMING` | 时序：时间窗口 | `Q_TIME` / `MIN_DWELL` / `MAX_DWELL` |
| `SEQUENCE` | 顺序：先后与次数 | `SEQ_BEFORE` / `PREDECESSOR` / `REWORK_LIMIT` |
| `BATCHING` | 批量：组批规则 | `BATCH_MAX_SIZE` / `BATCH_MIX_ALLOWED` / `BATCH_SAME_RECIPE` |
| `CAPABILITY` | 能力：能做/不能做 | `TECH_NODE_MATCH` / `WAFER_SIZE_MATCH` / `PROCESS_MODE_MATCH` |
| `CAPACITY` | 容量：数量上限 | `BANK_CAPACITY` / `WIP_LIMIT` / `PM_INTERVAL` |
| `WINDOW` | 窗口：日历/生效期 | `EQUIP_CALENDAR` / `ROUTE_EFFECTIVE_WINDOW` |
| `QUALITY` | 质量：规范与采样 | `SPC_SPEC` / `SAMPLING_RULE` |
| `OTHER` | 其他/自定义 | `CUSTOM_EXPR` |

**类型目录（`constraint_type`，含必填参数）：**

| constraint_type | family | 语义 | 主体/客体 | 必填参数 | 默认严重度 |
| --- | --- | --- | --- | --- | --- |
| `Q_TIME` | TIMING | 两工序间**最大等待**（超时须返工/报废） | ROUTE_FLOW / — | `max_minutes`；可选 `on_violation`(REWORK/SCRAP/HOLD) | BLOCK |
| `MIN_DWELL` | TIMING | 工序**最小停留**（如炉后冷却） | ROUTE_FLOW | `min_minutes` | WARN |
| `MAX_DWELL` | TIMING | 工序**最大停留**（含排队上限） | ROUTE_OPERATION | `max_minutes` | WARN |
| `SEQ_BEFORE` | SEQUENCE | 工序 A 必须先于 B | ROUTE_OPERATION / ROUTE_OPERATION | `before_op_ref` | BLOCK |
| `PREDECESSOR` | SEQUENCE | 工序须有已完成的指定前置 | ROUTE_OPERATION | `predecessor_op_ref` | BLOCK |
| `REWORK_LIMIT` | SEQUENCE | 返工回路**最大次数** | ROUTE | `max_times` | BLOCK |
| `BATCH_MAX_SIZE` | BATCHING | 单批**最大片数/载具数** | EQUIPMENT_CLASS | `max_size`,`unit` | BLOCK |
| `BATCH_MIX_ALLOWED` | BATCHING | 同批是否允许混产品/混配方 | EQUIPMENT_CLASS | `allow_mix`(ENUM) | BLOCK |
| `BATCH_SAME_RECIPE` | BATCHING | 同批须同配方 | EQUIPMENT_CLASS | `require_same_recipe` | WARN |
| `EQUIP_PRODUCT_COMPAT` | COMPATIBILITY | 设备（类）↔ 产品可制造 | PRODUCT / EQUIPMENT_CLASS | — | BLOCK |
| `EQUIP_RECIPE_QUAL` | COMPATIBILITY | 设备↔配方须有合格资格（含 PPID） | EQUIPMENT / RECIPE | `require_qual_status`(默认 QUALIFIED) | BLOCK |
| `CARRIER_AREA_COMPAT` | COMPATIBILITY | 载具类型可进工艺区 | CARRIER_TYPE / LOCATION_AREA | — | BLOCK |
| `EQUIP_CARRIER_COMPAT` | COMPATIBILITY | 设备端口可接载具类型 | EQUIPMENT / CARRIER_TYPE | — | BLOCK |
| `TECH_NODE_MATCH` | CAPABILITY | 产品 tech_node 须被设备覆盖 | PRODUCT / EQUIPMENT | — | BLOCK |
| `WAFER_SIZE_MATCH` | CAPABILITY | 产品/载具/设备尺寸须一致 | PRODUCT / EQUIPMENT | `wafer_size` | BLOCK |
| `PROCESS_MODE_MATCH` | CAPABILITY | 设备 `process_mode` 须匹配工序要求 | ROUTE_OPERATION / EQUIPMENT | `required_mode` | WARN |
| `BANK_CAPACITY` | CAPACITY | 仓库/储位容量上限 | BANK | `capacity` | WARN |
| `WIP_LIMIT` | CAPACITY | 在制上限（防 WIP 爆炸） | LOCATION_AREA | `max_wip` | WARN |
| `PM_INTERVAL` | CAPACITY | 保养间隔（到时须 PM） | EQUIPMENT | `pm_interval_days` | WARN |
| `EQUIP_CALENDAR` | WINDOW | 设备可用日历（班次/计划停机） | EQUIPMENT | `calendar_ref`(REF) | WARN |
| `ROUTE_EFFECTIVE_WINDOW` | WINDOW | 路线/配方生效窗口 | ROUTE | — (用 effective_from/to) | BLOCK |
| `SPC_SPEC` | QUALITY | 参数须落在 spec 上下限内 | ROUTE_OPERATION | `spec_key` | BLOCK |
| `SAMPLING_RULE` | QUALITY | 采样频率（每 N 批/片测一次） | ROUTE_OPERATION | `sample_freq` | INFO |
| `CUSTOM_EXPR` | OTHER | 自定义表达式约束 | 任意 | `expr` | WARN |

> **参数校验**：`ConstraintValidator` 依据本目录校验「类型 ↔ 必填参数」一致性（如 `Q_TIME` 缺 `max_minutes` 直接拒存）。
>
> **目录可扩展（由扩展域登记）**：本目录是**开放清单**——新增主数据域时会追加类型。扩展类型只需追加枚举与参数约定，**不改求值引擎**（§4.1 声明式模型的价值所在）。
>
> **已登记的扩展类型（权威登记表，2026-09-23 全量补齐）**：
>
> | constraint_type | family | 登记来源域 | 语义 | 关键参数 |
> | --- | --- | --- | --- | --- |
> | `MATERIAL_LIFE_LIMIT` | CAPACITY | [material-design §4.4](material-design.md) | 物料/耗材寿命超限 | `max_value`、`unit` |
> | `RETICLE_QUAL` | COMPATIBILITY | [reticle-design §4.4](reticle-design.md) | 光罩须在目标 scanner 有资格 | `require_qual_status` |
> | `RETICLE_USAGE_LIMIT` | CAPACITY | [reticle-design §4.5](reticle-design.md) | 光罩曝光次数/晶圆数超限 | `max_exposures`、`max_wafer_count` |
> | `TEST_ASSET_QUAL` | COMPATIBILITY | [test-asset-design §4.4](test-asset-design.md) | 探针卡/负载板须在目标 tester·prober 有资格 | `require_qual_status` |
> | `TEST_ASSET_USAGE_LIMIT` | CAPACITY | [test-asset-design §4.5](test-asset-design.md) | 触点数/插拔次数超限 | `max_touchdowns`、`max_insertions` |
> | `TOOLING_AVAILABLE` | COMPATIBILITY | [tooling-design §4.4](tooling-design.md) | 所需工装须到位且可用 | `tooling_type` |
> | `TOOLING_USAGE_LIMIT` | CAPACITY | [tooling-design §4.4](tooling-design.md) | 工装使用次数/时长超限 | `max_runs`、`max_hours` |
> | `EQUIP_ALARM_LIMIT` | QUALITY | [equipment-interface-design §4.5](equipment-interface-design.md) | 设备报警限值 | `alid`、`threshold` |
> | `EC_LIMIT` | CAPACITY | [equipment-interface-design §4.5](equipment-interface-design.md) | 设备常量（EC）合法范围 | `ecid`、`min_value`、`max_value` |
> | `FACILITY_AVAILABLE` | WINDOW | [facility-utility-design §4.5](facility-utility-design.md) | 厂务介质须可用 | `facility_code`、`is_critical` |
> | `CLEAN_CLASS_MATCH` | COMPATIBILITY | [cleanliness-env-design §4.4](cleanliness-env-design.md) | 载具/人员与区域洁净等级匹配 | `clean_class_code` |
> | `CAPABILITY_THRESHOLD` | CAPABILITY | [equipment-design 附录 A](equipment-design.md) | 设备量化能力须达阈值 | `param_def_id`、`threshold`、`direction` |
> | `DISPOSITION_TRIGGER` | OTHER | [reason-defect-design §4.5](reason-defect-design.md) | 命中条件触发处置动作 | `disposition_rule_ref` |
> | `SPC_OOC_HOLD` | QUALITY | [sampling-spc-design §4.6](sampling-spc-design.md) | 判异未闭环前不得放行 | `spc_rule_ref` |
> | `OPERATOR_QUALIFIED` | CAPABILITY | [org-personnel-design §4.4](org-personnel-design.md) | 操作人须持证上岗 | `skill_code`、`scope_ref` |
| `SAFETY_REQUIREMENT` | OTHER | [ehs-safety-design §4.3](ehs-safety-design.md) | 设备/工序/区域的安全要求须满足（PPE/联锁/通风/侦测/消防） | `scope_ref`、`req_type` |
| `CHEMICAL_COMPAT` | COMPATIBILITY | [ehs-safety-design §4.2](ehs-safety-design.md) | 化学品不得禁配混放/混用 | `substance_a`、`substance_b` |
>
> **维护约定**：任何域新增约束类型，**必须回写本表**（本表是该目录的单一来源）；新增实体/字段的同理见 [00-blueprint §4 与 §10](00-blueprint.md) 的回归同步约定。

### 4.3 硬约束 / 软约束与严重度（CT4）

| hard_soft | severity | 违反处置（下游） | 可否越权放行 |
| --- | --- | --- | --- |
| `HARD` | `BLOCK` | **阻断**（投料/派工/搬运被拒） | ❌（`allow_override` 无效） |
| `SOFT` | `WARN` | **告警放行** + 留痕（违反记录） | ✅（须 `allow_override=1` + 审批留痕） |
| `SOFT` | `INFO` | 仅提示（报表/审计） | ✅ |

- **BLOCK 短路**：同一次求值中，若命中 `BLOCK` 违反，即可短路返回（不再求值低优先级约束），提升派工热路径性能。
- **WARN 全量求值**：告警级约束需全部求值以便一次性暴露所有问题。
- **越权放行留痕**：`SOFT + allow_override` 的约束被放行时，下游须记录「谁、何时、以何理由、放行了哪条约束、实际值/阈值」——MDS 提供 `message_template` 与约束元数据，留痕落在下游业务事件。

### 4.4 约束集与覆盖（CT5）

- **约束集**把一组约束打包（§3.3），绑定一次整体生效；集内成员可**覆盖**约束自身的 `priority`/`severity`（如某集内把全局 WARN 升为 BLOCK）。
- **覆盖优先级解析**（作用域越深越具体，越具体越优先）：

```
求解某实例上生效的约束集合：
  1. 收集所有 scope 匹配该实例的 binding（含 GLOBAL * 兜底）
  2. 展开 SET → 其 enabled 成员约束
  3. 对同一 constraint 的多条 binding，按 specificity 排序取最具体者：
     GLOBAL < FAMILY < {PRODUCT/ROUTE/EQUIPMENT_CLASS/CARRIER_TYPE/BANK/AREA}
            < {ROUTE_OPERATION/ROUTE_FLOW/EQUIPMENT/EQUIPMENT_GROUP/RECIPE}
  4. 应用 binding.priority_override / severity_override（覆盖约束自身）
  5. 过滤 status=ACTIVE、is_enabled=1、effective 窗口内
  6. 按 priority 降序排列（BLOCK 先判）
```

- **覆盖（override）与禁用（disable）区别**：`is_enabled=0` 是临时停用绑定；`severity_override` 是改变强度。二者都留痕于 `_hist`。

### 4.5 作用域分层与语义（CT5 续）

| scope_type | 语义 | 典型用法 |
| --- | --- | --- |
| `GLOBAL` | 全租户兜底 | 通用安全规则（尺寸一致） |
| `PRODUCT_FAMILY` | 产品族级 | 同族产品共享工艺红线 |
| `PRODUCT` | 单产品 | 某产品专属限制 |
| `ROUTE` | 整条路线 | 返工上限、路线生效窗口 |
| `ROUTE_OPERATION` | 单工序 | max dwell、SPC spec |
| `ROUTE_FLOW` | **边**（两工序之间） | **Q-Time / min dwell**（核心落点） |
| `EQUIPMENT_CLASS` | 设备大类 | 组批上限、process_mode |
| `EQUIPMENT` | 单台设备 | 日历、PM、配方资格 |
| `EQUIPMENT_GROUP` | 派工设备组 | 组级规则（[工艺流 §3.5](process-flow-design.md)） |
| `RECIPE` | 配方 | 配方专属限制 |
| `CARRIER_TYPE` | 载具类型 | 搬运/兼容规则 |
| `BANK` | 仓库 | 库容 |
| `LOCATION_AREA` | 工艺区 | 载具准入、WIP 上限 |

### 4.6 约束试算能力（核心，CT6）

> 与 [Process Flow 的 4 类对比](process-flow-design.md) 同构：约束也有 4 类**设计期能力**，结果持久化（§3.5）供评审。

#### 4.6.1 单对象校验（SINGLE）

- 输入：一个主数据实例（如一条 `mds_route_flow` 边、一台设备、一个载具类型）。
- 算法：解析该实例上生效的约束集合（§4.4），逐条做**静态可判定**部分校验（类型/参数完整性、引用存在性、断言自洽）。
- 输出：`PASS`/`FAIL`/`SKIP` 明细 + 提示。
- 用途：设计期"这条边有没有 Q-Time 保护""这台设备配了哪些规则"。

#### 4.6.2 上下文求值预演（CONTEXT）

- 输入：一个**声明式求值上下文**（`context_json`）：product + route + op/flow + equipment + carrier + lot 属性（可用声明值代替实时态）。
- 算法：模拟下游在该时点的求值——对每个求值时机（§4.7）用上下文填充主体/客体，逐条求值适用的约束。
- 输出：该上下文下的完整 PASS/FAIL 清单（含最高严重度与首条 BLOCK 原因）。
- 用途：**投料前预演**（这批按现规则能不能投）、**派工试算**（这台设备接不接得住）、**新规则上线前的影响预演**。

#### 4.6.3 冲突检测（CONFLICT）

- 输入：一个作用域（如某 ROUTE）或一个约束集。
- 算法：在**同一 (subject, object, 类型/语义)** 维度上找矛盾：
  1. **断言矛盾**：`MUST` 与 `MUST_NOT` 对同一主体客体对（且 `condition_expr` 可同时成立）；
  2. **参数区间不相交**：两条同类数值约束的区间交集为空（如 `max_minutes=60` 与 `min_minutes=120` 对同一 flow）；
  3. **语义互斥**：`BATCH_MIX_ALLOWED=false` 与 `BATCH_SAME_RECIPE=false` 叠加某流程时派工不可能满足。
- 输出：冲突对清单（含双方来源 binding 的作用域链）。
- 用途：**约束上线前防错**（避免配出自相矛盾的规则导致派工永远失败）。

#### 4.6.4 覆盖分析（COVERAGE）

- 输入：一个 ROUTE 或 PRODUCT（或 PRODUCT_FAMILY）。
- 算法：沿 `route → operation → flow` 展开全部节点与边，标注每个节点/边**已被哪些约束族覆盖**，识别**关键缺口**：
  1. 有 `REWORK` 边的路线却无 `REWORK_LIMIT` → 缺口（返工可能无限循环）；
  2. 有跨区/洁净敏感转换的边却无 `Q_TIME`/`CARRIER_AREA_COMPAT` → 缺口；
  3. 涉及多 tech_node 产品的设备池却无 `TECH_NODE_MATCH` → 缺口。
- 输出：覆盖矩阵（节点/边 × 约束族）+ 缺口清单。
- 用途：**工艺红线体检**——发布新路线前确认"该保护的都保护了"。

#### 4.6.5 试算服务接口（建议）

```
ConstraintEvalService
  ├── evalSingle(scopeType, scopeRef)            → SINGLE 校验（设计期）
  ├── evalContext(contextJson)                   → CONTEXT 预演（投料/派工试算）
  ├── detectConflict(scopeType, scopeRef | setId)→ CONFLICT 冲突检测
  └── analyzeCoverage(routeId | productCode)     → COVERAGE 覆盖分析
  （均落 mds_constraint_eval + _item，供评审签核）

ConstraintResolveService
  ├── resolveEffective(scopeType, scopeRef)      → 解析生效约束集合（§4.4 算法）
  └── compileRulePack(scopeType, scopeRef)       → 编译「约束规则包」供下游订阅（§4.7）
```

### 4.7 求值时机契约与规则下发（CT6 续）

MDS 定义**在哪些时点**下游应求值哪些族（契约，非实现）：

| 求值时机 | 触发方 | 典型约束族 | 输入上下文要点 |
| --- | --- | --- | --- |
| **投料 lot release** | MES | CAPABILITY / COMPATIBILITY / WINDOW | product / route / 目标产线 |
| **选路线 route selection** | MES | WINDOW / SEQUENCE | product / 候选 route |
| **组批 batch formation** | MES | BATCHING / CAPABILITY | 候选 lot 集 / 设备类 |
| **派工 dispatch** | MES | COMPATIBILITY / CAPACITY / WINDOW（日历）/ CAPABILITY | op / 设备池 / 实时设备态 |
| **搬运 carrier assignment** | AMHS | CARRIER_AREA_COMPAT / BANK_CAPACITY | carrier 类型 / 目标区 / 库 |
| **move-in / move-out** | EAP | QUALITY（SPC）/ TIMING 起点 | 设备 / PPID / 实时参数 |
| **Q-Time 计时** | MES | TIMING（Q_TIME） | flow 边 / lot move-out 时间 |

**规则下发（避免每请求回查 MDS）**：
- MDS 提供 `compileRulePack()`，把某作用域的生效约束**编译成只读快照**（JSON/表），下游启动时拉取 + 后续按版本增量订阅（版本号变更即刷新）；
- 下游缓存规则包，在热路径内**本地求值**；MDS 不参与实时求值（与 IAM「本地验签 + claim 准入」的解耦思路一致）。

### 4.8 与既有专用兼容表的关系（CT2 续，重要）

**不重复造数据**。MDS 已有若干**专用兼容表**已表达部分约束（权威来源）：

| 已有专用表 | 表达的约束 | 约束层的角色 |
| --- | --- | --- |
| `mds_equipment_recipe_qual` | 设备↔物理配方资格 + PPID | 约束层**不复制**；`EQUIP_RECIPE_QUAL` 类型指向该表，提供**统一注册 + 求值编排 + 缺失检测** |
| `mds_carrier_type_compat` | 载具类型↔（area/class/process） | 同上，供 `CARRIER_AREA_COMPAT` |
| `mds_equipment_capability` | 设备↔工艺区能力 | 同上，供能力族校验 |
| `mds_equipment_tech_node` / `mds_product.tech_node` | 工艺节点 | 供 `TECH_NODE_MATCH`（求交集） |
| `mds_process_flow_step_param` | 工艺窗口 spec | 供 `SPC_SPEC` |
| `mds_route_flow.condition_expr` | 分支/返工条件 | 供 `SEQ_BEFORE`/`REWORK_LIMIT` 的语义基础 |

> **边界原则**：**已有专用表表达的兼容关系 → 约束层做「统一视图 + 求值编排」，不建第二份数据**；**尚无专用表表达的规则（Q-Time、组批、返工上限、库容、日历、采样…）→ 才落 `mds_constraint`**。约束层是「关系的统一注册与求值编排层」，不是第二份主数据。

### 4.9 生命周期 / 版本 / 冻结（CT7，与前序同源）

约束复用 NamingRule / Route 的**统一治理范式**：

| 维度 | 说明 |
| --- | --- |
| 生命周期 `status` | `DRAFT`（编辑）→ `ACTIVE`（可被求值）→ `DEPRECATED`（停用，存量绑定不改） |
| 版本 | 改**语义**（类型/断言/条件/参数）须 `cloneAsNewVersion()` 出 `version+1` 新 `DRAFT`；旧 `ACTIVE` 服务存量 |
| 冻结 `is_frozen` | 叠加在 `ACTIVE` 上的组织级写锁（量产/法规锁定），与 `status` 正交 |
| 生效窗口 | `effective_from/effective_to`（SCD2-lite），支持"预约生效/到期失效" |
| 变更留痕 | `@History(SNAPSHOT)` → `{entity}_hist`，参数/严重度变更全部可追溯 |

> 与 [naming-rule-design §4.6](naming-rule-design.md) 完全同源——两条横切层用同一套规则生命周期语义，降低学习成本。

### 4.10 软删 / 租户 / 主键（与平台一致）

- 所有实体继承 `BaseDefData`：`String id`（雪花/UUIDv7）、`tenant_id`、`@Version`、`deleted`、审计列。
- 唯一约束均含 `(..., tenant_id, deleted)`（T2.5）。
- 行级过滤：`@SQLRestriction("deleted=false")` + `@Filter(cimTenantFilter)`；`@FilterDef` 放实体包 `package-info.java`。

---

## 5. 与平台底座衔接（强制对齐）

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData`（零 Hibernate 基类） | 所有约束实体继承，含 `@Version`、审计五件套、`tenant_id`、`deleted` |
| `@History(SNAPSHOT)` | constraint / param / set / binding 变更落 `{entity}_hist`，规则修改自动留痕（合规审计关键） |
| `cimTenantFilter` + `TenantContext` | 多租户行级过滤 |
| T2.5 软删唯一约束 | 唯一键含 `deleted`，避免软删后唯一冲突 |
| `IdGenerator`（雪花/UUIDv7） | 主键生成 |
| 主历成对迁移 | DDL 与 `db/migration/{common,mysql}` 成对：结构迁移 + 历史表 + 种子数据（内置约束类型目录种子） |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
-- 约束定义
CREATE TABLE mds_constraint (
  id               VARCHAR(32)  NOT NULL,
  tenant_id        VARCHAR(32)  NOT NULL,
  code             VARCHAR(64)  NOT NULL,
  name             VARCHAR(128),
  constraint_type  VARCHAR(32)  NOT NULL,
  family           VARCHAR(16)  NOT NULL,
  subject_type     VARCHAR(32)  NOT NULL,
  object_type      VARCHAR(32),
  assertion        VARCHAR(16)  NOT NULL DEFAULT 'MUST',
  condition_expr   VARCHAR(512),
  hard_soft        VARCHAR(8)   NOT NULL DEFAULT 'HARD',
  severity         VARCHAR(16)  NOT NULL DEFAULT 'BLOCK',
  priority         INT          DEFAULT 0,
  allow_override   BIT          DEFAULT 0,
  message_template VARCHAR(512),
  status           VARCHAR(16)  NOT NULL,
  revision          VARCHAR(16)  NOT NULL,
  is_frozen        BIT          DEFAULT 0,
  effective_from   DATETIME(3),
  effective_to     DATETIME(3),
  description      VARCHAR(512),
  version_         BIGINT       NOT NULL DEFAULT 0,
  deleted          BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_constraint PRIMARY KEY (id),
  CONSTRAINT uq_mds_constraint_code UNIQUE (code, tenant_id, deleted)
);
CREATE INDEX idx_constraint_type ON mds_constraint (constraint_type, family);

-- 约束参数
CREATE TABLE mds_constraint_param (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  constraint_id VARCHAR(32)  NOT NULL,
  param_key     VARCHAR(64)  NOT NULL,
  param_value   VARCHAR(128),
  data_type     VARCHAR(16)  NOT NULL DEFAULT 'NUMERIC',
  unit          VARCHAR(16),
  ref_type      VARCHAR(32),
  ref_value     VARCHAR(128),
  description   VARCHAR(512),
  version_      BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_constraint_param PRIMARY KEY (id),
  CONSTRAINT uq_mds_constraint_param UNIQUE (constraint_id, param_key, tenant_id, deleted),
  CONSTRAINT fk_cparam_constraint FOREIGN KEY (constraint_id) REFERENCES mds_constraint (id)
);

-- 约束集
CREATE TABLE mds_constraint_set (
  id           VARCHAR(32)  NOT NULL,
  tenant_id    VARCHAR(32)  NOT NULL,
  code         VARCHAR(64)  NOT NULL,
  name         VARCHAR(128),
  set_category VARCHAR(32),
  status       VARCHAR(16)  NOT NULL,
  revision      VARCHAR(16)  NOT NULL,
  description  VARCHAR(512),
  version_     BIGINT       NOT NULL DEFAULT 0,
  deleted      BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_constraint_set PRIMARY KEY (id),
  CONSTRAINT uq_mds_constraint_set_code UNIQUE (code, tenant_id, deleted)
);

-- 约束集成员
CREATE TABLE mds_constraint_set_member (
  id                VARCHAR(32)  NOT NULL,
  tenant_id         VARCHAR(32)  NOT NULL,
  set_id            VARCHAR(32)  NOT NULL,
  constraint_id     VARCHAR(32)  NOT NULL,
  priority_override INT,
  severity_override VARCHAR(16),
  is_enabled        BIT          DEFAULT 1,
  description       VARCHAR(512),
  version_          BIGINT       NOT NULL DEFAULT 0,
  deleted           BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_constraint_set_member PRIMARY KEY (id),
  CONSTRAINT uq_mds_csm UNIQUE (set_id, constraint_id, tenant_id, deleted),
  CONSTRAINT fk_csm_set FOREIGN KEY (set_id) REFERENCES mds_constraint_set (id),
  CONSTRAINT fk_csm_constraint FOREIGN KEY (constraint_id) REFERENCES mds_constraint (id)
);

-- 约束/集 → 实例绑定（作用域分层）
CREATE TABLE mds_constraint_binding (
  id                VARCHAR(32)  NOT NULL,
  tenant_id         VARCHAR(32)  NOT NULL,
  scope_type        VARCHAR(32)  NOT NULL,
  scope_ref         VARCHAR(128) NOT NULL,
  target_kind       VARCHAR(16)  NOT NULL,
  constraint_id     VARCHAR(32),
  set_id            VARCHAR(32),
  priority_override INT,
  severity_override VARCHAR(16),
  is_enabled        BIT          DEFAULT 1,
  effective_from    DATETIME(3),
  effective_to      DATETIME(3),
  description       VARCHAR(512),
  version_          BIGINT       NOT NULL DEFAULT 0,
  deleted           BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_constraint_binding PRIMARY KEY (id),
  CONSTRAINT fk_cbind_constraint FOREIGN KEY (constraint_id) REFERENCES mds_constraint (id),
  CONSTRAINT fk_cbind_set FOREIGN KEY (set_id) REFERENCES mds_constraint_set (id),
  CONSTRAINT ck_cbind_target CHECK (
    (target_kind = 'CONSTRAINT' AND constraint_id IS NOT NULL AND set_id IS NULL) OR
    (target_kind = 'SET'        AND set_id IS NOT NULL AND constraint_id IS NULL)
  )
);
CREATE INDEX idx_cbind_scope  ON mds_constraint_binding (scope_type, scope_ref);
CREATE INDEX idx_cbind_target ON mds_constraint_binding (constraint_id, set_id);

-- 试算快照头
CREATE TABLE mds_constraint_eval (
  id           VARCHAR(32)  NOT NULL,
  tenant_id    VARCHAR(32)  NOT NULL,
  eval_type    VARCHAR(16)  NOT NULL,
  name         VARCHAR(128),
  context_json JSON,
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
  CONSTRAINT pk_mds_constraint_eval PRIMARY KEY (id)
);

-- 试算明细
CREATE TABLE mds_constraint_eval_item (
  id            VARCHAR(32)  NOT NULL,
  tenant_id     VARCHAR(32)  NOT NULL,
  eval_id       VARCHAR(32)  NOT NULL,
  constraint_id VARCHAR(32),
  binding_id    VARCHAR(32),
  result        VARCHAR(16)  NOT NULL,
  severity      VARCHAR(16),
  subject_ref   VARCHAR(128),
  object_ref    VARCHAR(128),
  message       VARCHAR(512),
  detail_json   JSON,
  description   VARCHAR(512),
  version_      BIGINT       NOT NULL DEFAULT 0,
  deleted       BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_constraint_eval_item PRIMARY KEY (id),
  CONSTRAINT fk_cei_eval FOREIGN KEY (eval_id) REFERENCES mds_constraint_eval (id)
);
```

> 历史表 `mds_constraint_hist` / `mds_constraint_param_hist` / `mds_constraint_set_hist` / `mds_constraint_binding_hist` / … 由 `@History(SNAPSHOT)` 运行时自动生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.constraint
  ├── entity
  │   ├── Constraint.java            # @Entity 继承 BaseDefData + @History(SNAPSHOT)；type/family/assertion/severity/status
  │   ├── ConstraintParam.java        # @Entity 继承 BaseDefData（param_key/value/unit/ref）
  │   ├── ConstraintSet.java          # @Entity 继承 BaseDefData（约束集头）
  │   ├── ConstraintSetMember.java    # @Entity 继承 BaseDefData（集↔约束, override）
  │   ├── ConstraintBinding.java      # @Entity 继承 BaseDefData（scope_type/scope_ref/target_kind）
  │   ├── ConstraintEval.java         # @Entity 继承 BaseDefData（试算头）
  │   ├── ConstraintEvalItem.java     # @Entity 继承 BaseDefData（逐条求值明细）
  │   └── package-info.java           # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── ConstraintRepository.java
  │   ├── ConstraintBindingRepository.java
  │   ├── ConstraintSetRepository.java
  │   └── ConstraintEvalRepository.java
  ├── service
  │   ├── ConstraintService.java          # 继承 AbstractJpaService；cloneAsNewVersion/freeze/unfreeze/deprecate
  │   ├── ConstraintResolver.java         # 生效约束解析（§4.4 specificity 算法）
  │   ├── ConstraintValidator.java        # 类型↔必填参数校验（§4.2 目录）
  │   └── ConstraintEvalService.java      # 4 类试算（§4.6），落 eval + eval_item
  ├── engine
  │   ├── ConstraintEvaluator.java        # 单条约束求值（按 constraint_type 分派）
  │   └── RulePackCompiler.java           # 编译下游订阅的规则快照（§4.7）
  └── web
      └── ConstraintController.java       # 查询/版本发布/克隆/试算/冲突/覆盖
```

---

## 8. 待补 / 后续

- **表达式 DSL 规范**：`condition_expr` 与 `CUSTOM_EXPR` 的语法（变量、运算符、函数）、求值引擎归属（下游实现，MDS 只存与做**静态可判定**校验）。
- **约束类型目录种子数据**：把 §4.2 目录作为 `mds_constraint_type` **元数据种子**（或枚举 + 校验表），供管理端下拉与参数校验。
- **规则包增量订阅协议**：`compileRulePack()` 输出格式、版本号与下游刷新机制（对接 [platform](../../../platform/server/design.md) 的缓存/配置下发布）。
- **与实际实时态的接口**：CONTEXT 预演若需真实实时态（如当前 WIP、槽位占用），需 MES/AMHS 提供查询接口（MDS 不存）。
- **约束影响分析**：改一条约束会影响哪些 route/product/设备（反向依赖图），可作为试算能力的第 5 类。
- **管理端可视化**：约束矩阵（实例 × 约束族）、冲突/缺口高亮、规则包预览。
- **与既有文档闭环**：本文引用 `mds_route_flow`（[route-design](route-design.md)）、`mds_equipment_recipe_qual` / `mds_equipment_capability` / `mds_equipment_tech_node`（[equipment-design](equipment-design.md)）、`mds_carrier_type_compat`（[carrier-design](carrier-design.md)）、`mds_bank`（[bank-design](bank-design.md)）、`mds_process_flow_step_param`（[process-flow-design](process-flow-design.md)）、`mds_product.tech_node`（[product-design](product-design.md)）、`naming-rule-design`（姊妹横切层）。
