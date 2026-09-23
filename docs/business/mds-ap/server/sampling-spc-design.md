# MDS 采样与 SPC 规则（Sampling / SPC / OCAP）主数据设计

> 本文承接[工艺流](process-flow-design.md)（`mds_process_flow_step_param` 工艺窗口）、[设备接口点表](equipment-interface-design.md)（变量定义）、[原因码与处置](reason-defect-design.md)（处置动作），
> 补全 MDS 的**采样计划（Sampling Plan）/ SPC 判异规则（Control Rules）/ 失控响应计划（OCAP）**。
>
> **补的缺口**：工艺流已有 spec 上下限（`step_param`），但「**什么时候测、测多少、超了怎么判、判了怎么办**」四件事都没有定义。
>
> 设计原则延续全篇：**MDS 拥有采样计划与判异规则定义；实测值、控制图、失控事件归 MES / SPC 系统**。

---

## 0. 定位与边界（拍板）

### 0.1 缺口与补位（核心）

| 问题 | 现状 | 本文补位 |
| --- | --- | --- |
| 什么时候测？ | ❌ 无 | `mds_sampling_plan.trigger_type` + `frequency_n` |
| 测什么？ | 部分（`step_param` 有参数名） | `sampling_plan` 绑定 `equipment variable` 或 `step_param` |
| 测多少？ | ❌ 无 | `sample_size` |
| 怎么判异常？ | ❌ 无 | `mds_spc_rule`（Western Electric / Nelson 规则） |
| 判了怎么办？ | ❌ 无 | `mds_ocap` + `mds_ocap_action` |

> **结论（SP1）**：采样与 SPC 规则是 MDS 的独立主数据域，是「工艺参数规范」到「实际测量与判定」之间的**桥梁定义**。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 采样计划（频率/样本量/对象） | **MDS** | `mds_sampling_plan`，本文范围 |
| SPC 判异规则（规则集与参数） | **MDS** | `mds_spc_rule` + `_term` |
| 失控响应计划（OCAP 动作链） | **MDS** | `mds_ocap` + `_action` |
| **实测数据**（量测值、采集值） | **MES / SPC / EAP** | MDS 不存（[equipment-interface-design](equipment-interface-design.md) 采集） |
| **控制图与控制限计算（X̄±3σ 实际值）** | **SPC 系统** | MDS 提供规则与基线，不计算结果 |
| **失控事件的触发与处置执行** | **MES / SPC** | MDS 给 OCAP 定义 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 采样 ↔ 工序 | `mds_sampling_plan.route_operation_id → mds_route_operation.id` | [route-design §3.3](route-design.md) |
| 采样 ↔ 工艺参数 | `mds_sampling_plan.param_ref → mds_process_flow_step_param.id` | [process-flow-design §3.4](process-flow-design.md) |
| 采样 ↔ 设备变量 | `mds_sampling_plan.variable_ref → mds_equip_if_variable.code` | [equipment-interface-design §3.2](equipment-interface-design.md) |
| 失控 → 处置 | `mds_ocap_action` 引用 `mds_disposition_rule` / 动作枚举 | [reason-defect-design §3.4](reason-defect-design.md) |
| 失控 → 约束 | 新增约束类型 `SPC_OOC_HOLD` | [constraint-design §4.2](constraint-design.md) |
| 采样 ↔ 产品/设备类 | `scope_type` + `scope_ref` | [product-design](product-design.md) / [equipment-design](equipment-design.md) |
| 规则编码 | `entity_type=SAMPLING_PLAN`/`SPC_RULE`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **Shewhart 控制图** | X̄-R / X-MR / p / np / c / u 图，按数据类型选择 | `mds_spc_rule.chart_type` |
| **Western Electric Rules（1956）** | 4 条经典判异：1 点超 3σ / 连续 3 点中 2 点超 2σ / 连续 5 点中 4 点超 1σ / 连续 8 点同侧 | `mds_spc_rule_term` 术语化表达 |
| **Nelson Rules（1984）** | 8 条扩展判异（含趋势、周期、混批） | 同上，`rule_set=NELSON` |
| **OCAP / 失控响应计划** | 判异触发后按预定动作链处理（通知/停线/扣留/返工/调整） | `mds_ocap_action` |
| **SEMI E116 / E10** | 设备性能与良率依赖过程能力（Cpk/CPK）与过程监控 | 与 [equipment-interface-design](equipment-interface-design.md) 采集联动 |
| **ISO 9001 / IATF 16949** | 统计过程控制作为过程确认手段，须有受控的监控与反应计划 | 采样/规则/OCAP 全部受控（[md-governance-design](md-governance-design.md)） |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Sampling Plan / 采样计划** | 采什么、多久一次、采多少 | 不是"量测配方"（量测在设备/recipe 侧） |
| **Control Rule / 判异规则** | 判定过程"失控"的统计准则 | 不是 `mds_disposition_rule`（那是处置动作） |
| **Spec Limit / 规格限** | 客户/工艺要求（USL/LSL） | 与 **Control Limit**（统计控制限，X̄±3σ）不同：前者是"合格线"，后者是"失控线" |
| **OCAP / 失控响应计划** | 失控时按序执行的动作链 | 不是处置规则本身；OCAP 是**动作序列编排** |
| **OOC / Out of Control** | 判异命中 | 与 OOS（Out of Spec，超规格）不同：OOC 是统计异常，OOS 是超合格线 |
| **Rule Term / 规则项** | 规则的一个参数化条件 | 与 `constraint` 的 condition 区分：这是**统计判定** |

---

## 3. 实体总览与 ER

```
mds_sampling_plan (采样计划: trigger/frequency/sample_size)
   ├── route_operation_id ──> mds_route_operation
   ├── param_ref          ──> mds_process_flow_step_param
   └── variable_ref       ──> mds_equip_if_variable.code

mds_spc_rule (判异规则集: chart_type / rule_set)
   └──< mds_spc_rule_term (规则项: term_type + params)

mds_ocap (失控响应计划)
   └──< mds_ocap_action (动作链: NOTIFY/HOLD/RECALL/RERUN/ADJUST/ESCALATE)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 采样计划 | `mds_sampling_plan` | 采什么/多久/多少 |
| SPC 规则 | `mds_spc_rule` | 规则集（图类型 + 规则族） |
| 规则项 | `mds_spc_rule_term` | 参数化判异条件 |
| 失控响应计划 | `mds_ocap` | 动作链头 |
| 响应动作 | `mds_ocap_action` | 有顺序的动作 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_sampling_plan`（采样计划）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 计划代码 |
| `name` | VARCHAR(128) | | 名称 |
| `scope_type` | VARCHAR(24) | NOT NULL | `ROUTE_OPERATION`/`PRODUCT`/`EQUIPMENT_CLASS`/`PROCESS` |
| `scope_ref` | VARCHAR(128) | | 作用域目标 |
| `route_operation_id` | VARCHAR(32) | FK→`mds_route_operation.id` | 采集所属工序（可空） |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | **被采集参数（统一语义）**；是所有报表/聚合的维度基准，见 [param-def-design](param-def-design.md) |
| `param_ref` | VARCHAR(32) | FK→`mds_process_flow_step_param.id` | 关联工艺参数窗口（可空，用于取 Spec 限） |
| `variable_ref` | VARCHAR(64) | | 关联设备变量（`mds_equip_if_variable.code`，可空） |
| `trigger_type` | VARCHAR(16) | NOT NULL | `LOT`（每 N 批）/`WAFER`（每 N 片）/`TIME`（每 N 分钟）/`BATCH`（每批次） |
| `frequency_n` | INT | NOT NULL DEFAULT 1 | 触发间隔（每 N 个触发单位采一次） |
| `sample_size` | INT | NOT NULL DEFAULT 1 | 每次采样样本量 |
| `spc_rule_id` | VARCHAR(32) | FK→`mds_spc_rule.id` | 判定所用 SPC 规则 |
| `ocap_id` | VARCHAR(32) | FK→`mds_ocap.id` | 失控时的响应计划 |
| `is_mandatory` | BIT | DEFAULT 1 | 是否强制采样 |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `revision` | VARCHAR(16) | NOT NULL | 版本 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.2 `mds_spc_rule` / `mds_spc_rule_term`（判异规则）

**`mds_spc_rule`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 规则集代码（如 `SPC-WE-STD`） |
| `name` | VARCHAR(128) | | 名称 |
| `chart_type` | VARCHAR(16) | NOT NULL | `XBAR_R`/`XBAR_S`/`X_MR`/`P`/`NP`/`C`/`U` |
| `rule_set` | VARCHAR(24) | NOT NULL | `WESTERN_ELECTRIC`/`NELSON`/`CUSTOM` |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `revision` | VARCHAR(16) | NOT NULL | 版本 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

**`mds_spc_rule_term`（规则项）**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `rule_id` | VARCHAR(32) | NOT NULL, FK→`mds_spc_rule.id` | 所属规则集 |
| `seq` | INT | NOT NULL | 顺序 |
| `term_type` | VARCHAR(32) | NOT NULL | `BEYOND_3SIGMA`/`2_OF_3_BEYOND_2SIGMA`/`4_OF_5_BEYOND_1SIGMA`/`8_RUN_SAME_SIDE`/`6_TREND`/`14_ALTERNATING`/`15_BELOW_1SIGMA`/`8_BEYOND_1SIGMA`/`CUSTOM` |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | **被判异的参数（统一语义）**，见 [param-def-design](param-def-design.md) |
| `window_n` | INT | | 观察窗口（点个数） |
| `threshold_k` | DECIMAL(4,2) | | 阈值（σ 倍数） |
| `min_hits` | INT | | 窗口内命中点数 |
| `params_json` | JSON | | 扩展参数（CUSTOM 用） |
| `severity` | VARCHAR(16) | | 命中严重度：`CRITICAL`/`WARN` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(rule_id, seq, tenant_id, deleted)`。

### 3.3 `mds_ocap` / `mds_ocap_action`（失控响应计划）

**`mds_ocap`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | OCAP 代码 |
| `name` | VARCHAR(128) | | 名称 |
| `trigger_rule_ref` | VARCHAR(32) | FK→`mds_spc_rule.id` | 触发规则（可空=任意失控） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `revision` | VARCHAR(16) | NOT NULL | 版本 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

**`mds_ocap_action`（动作链）**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `ocap_id` | VARCHAR(32) | NOT NULL, FK→`mds_ocap.id` | 所属计划 |
| `seq` | INT | NOT NULL | 执行顺序 |
| `action_type` | VARCHAR(16) | NOT NULL | `NOTIFY`（通知）/`HOLD`（扣留）/`RECALL`（召回）/`RERUN`（重测）/`ADJUST`（调机）/`ESCALATE`（升级）/`STOP_LINE`（停线） |
| `target_ref` | VARCHAR(128) | | 动作对象（角色/工序/设备/库） |
| `disposition_ref` | VARCHAR(32) | | 引用处置规则（[reason-defect-design §3.4](reason-defect-design.md)） |
| `is_blocking` | BIT | DEFAULT 0 | 是否阻断流转（须完成后才继续） |
| `timeout_min` | INT | | 超时升级（分钟） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(ocap_id, seq, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 SP1–SP6）

### 4.1 三方分工：点表 / 参数 / 采样（SP2）
| 域 | 回答 | 例 |
| --- | --- | --- |
| [equipment-interface-design](equipment-interface-design.md) | 设备**能报什么** | 变量 `ChamberTemp`（DV, F4, ℃） |
| [process-flow-design §3.4](process-flow-design.md) | 工艺**要求什么** | step 参数 `TEMP` target=200, min=195, max=205 |
| **本文** | **怎么测、怎么判** | 每 5 批采 1 片，用 WE 规则判异 |

> 三者不重复：**变量是能力、参数是要求、采样是策略**。

### 4.2 采样触发类型（SP3）
- `LOT`（每 N 批）/ `WAFER`（每 N 片）/ `TIME`（每 N 分钟）/ `BATCH`（每批次）；
- `sample_size` 支持一次采多片（如"每 25 片抽 3 片"）；
- 触发与执行在 MES：MDS 只给**计划的定义**。

### 4.3 判异规则术语化（SP4）
- 规则集 + 规则项（`term_type` + 参数）表达标准规则，例：
  - `BEYOND_3SIGMA`（1 点超 3σ，`threshold_k=3`）；
  - `2_OF_3_BEYOND_2SIGMA`（`window_n=3, threshold_k=2, min_hits=2`）；
  - `8_RUN_SAME_SIDE`（`window_n=8, min_hits=8`）；
- `CUSTOM` + `params_json` 承载厂内自定义规则；
- **控制限的计算（X̄±3σ 实际值）不在 MDS**——MDS 只定义规则，SPC 系统计算并判定。

### 4.4 规格限 vs 控制限（SP5）
- **Spec Limit（USL/LSL）** 来自 [process-flow-design §3.4](process-flow-design.md) 的 `step_param`：**合格线**；
- **Control Limit（X̄±3σ）** 由 SPC 系统统计计算：**失控线**；
- 二者关系：控制限通常位于规格限之内；过程可"合格但失控"（OOC 未 OOS），这正是 SPC 的预警价值。

### 4.5 OCAP 动作链（SP6）
- 判异命中 → 按 `mds_ocap_action.seq` 依次执行；
- `is_blocking=1` 的动作（如 `HOLD`）**必须完成才允许继续流转**；
- `timeout_min` 超时未处理 → `ESCALATE`（升级到上级）；
- 动作可引用 [reason-defect-design](reason-defect-design.md) 的处置规则，形成「**判异 → 处置**」闭环。

### 4.6 与约束层的关系（SP6 续）
- `SPC_OOC_HOLD`（新增约束类型）可表达「判异未闭环前不得放行」——由约束层统一编排、下游执行；
- 避免在 SPC 系统内硬编码"失控就停"。

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 采样/SPC/OCAP 实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 规则与计划变更落 `{entity}_hist`（**判异规则变更影响历史判定口径，必须留痕**） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + WE/Nelson 规则集种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_sampling_plan (
  id                 VARCHAR(32) NOT NULL,
  tenant_id          VARCHAR(32) NOT NULL,
  code               VARCHAR(64) NOT NULL,
  name               VARCHAR(128),
  scope_type         VARCHAR(24) NOT NULL,
  scope_ref          VARCHAR(128),
  route_operation_id VARCHAR(32),
  param_def_id       VARCHAR(32),
  param_ref          VARCHAR(32),
  variable_ref       VARCHAR(64),
  trigger_type       VARCHAR(16) NOT NULL,
  frequency_n        INT NOT NULL DEFAULT 1,
  sample_size        INT NOT NULL DEFAULT 1,
  spc_rule_id        VARCHAR(32),
  ocap_id            VARCHAR(32),
  is_mandatory       BIT DEFAULT 1,
  status             VARCHAR(16) NOT NULL,
  revision            VARCHAR(16) NOT NULL,
  description        VARCHAR(512),
  version_           BIGINT NOT NULL DEFAULT 0,
  deleted            BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_sampling PRIMARY KEY (id),
  CONSTRAINT uq_mds_sampling UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_sampling_op FOREIGN KEY (route_operation_id) REFERENCES mds_route_operation (id),
  CONSTRAINT fk_sampling_param_def FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id),
  CONSTRAINT fk_sampling_param FOREIGN KEY (param_ref) REFERENCES mds_process_flow_step_param (id),
  CONSTRAINT fk_sampling_rule FOREIGN KEY (spc_rule_id) REFERENCES mds_spc_rule (id),
  CONSTRAINT fk_sampling_ocap FOREIGN KEY (ocap_id) REFERENCES mds_ocap (id)
);

CREATE TABLE mds_spc_rule (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  code        VARCHAR(64) NOT NULL,
  name        VARCHAR(128),
  chart_type  VARCHAR(16) NOT NULL,
  rule_set    VARCHAR(24) NOT NULL,
  status      VARCHAR(16) NOT NULL,
  revision     VARCHAR(16) NOT NULL,
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_spc_rule PRIMARY KEY (id),
  CONSTRAINT uq_mds_spc_rule UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_spc_rule_term (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  rule_id      VARCHAR(32) NOT NULL,
  seq          INT NOT NULL,
  term_type    VARCHAR(32) NOT NULL,
  param_def_id VARCHAR(32),
  window_n     INT,
  threshold_k  DECIMAL(4,2),
  min_hits     INT,
  params_json  JSON,
  severity     VARCHAR(16),
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_spc_term PRIMARY KEY (id),
  CONSTRAINT uq_mds_spc_term UNIQUE (rule_id, seq, tenant_id, deleted),
  CONSTRAINT fk_spcterm_rule FOREIGN KEY (rule_id) REFERENCES mds_spc_rule (id),
  CONSTRAINT fk_spcterm_param_def FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id)
);

CREATE TABLE mds_ocap (
  id                VARCHAR(32) NOT NULL,
  tenant_id         VARCHAR(32) NOT NULL,
  code              VARCHAR(64) NOT NULL,
  name              VARCHAR(128),
  trigger_rule_ref  VARCHAR(32),
  status            VARCHAR(16) NOT NULL,
  revision           VARCHAR(16) NOT NULL,
  description       VARCHAR(512),
  version_          BIGINT NOT NULL DEFAULT 0,
  deleted           BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_ocap PRIMARY KEY (id),
  CONSTRAINT uq_mds_ocap UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_ocap_rule FOREIGN KEY (trigger_rule_ref) REFERENCES mds_spc_rule (id)
);

CREATE TABLE mds_ocap_action (
  id              VARCHAR(32) NOT NULL,
  tenant_id       VARCHAR(32) NOT NULL,
  ocap_id         VARCHAR(32) NOT NULL,
  seq             INT NOT NULL,
  action_type     VARCHAR(16) NOT NULL,
  target_ref      VARCHAR(128),
  disposition_ref VARCHAR(32),
  is_blocking     BIT DEFAULT 0,
  timeout_min     INT,
  description     VARCHAR(512),
  version_        BIGINT NOT NULL DEFAULT 0,
  deleted         BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_ocap_action PRIMARY KEY (id),
  CONSTRAINT uq_mds_ocap_action UNIQUE (ocap_id, seq, tenant_id, deleted),
  CONSTRAINT fk_ocapact_ocap FOREIGN KEY (ocap_id) REFERENCES mds_ocap (id)
);
```

> 历史表 `mds_sampling_plan_hist` / `mds_spc_rule_hist` / `mds_ocap_hist` / … 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.spc
  ├── entity
  │   ├── SamplingPlan.java          # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── SpcRule.java                # @Entity 继承 BaseDefData
  │   ├── SpcRuleTerm.java            # @Entity 继承 BaseDefData
  │   ├── Ocap.java                   # @Entity 继承 BaseDefData
  │   ├── OcapAction.java             # @Entity 继承 BaseDefData
  │   └── package-info.java           # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── SamplingPlanRepository.java
  │   └── SpcRuleRepository.java
  ├── service
  │   ├── SamplingPlanService.java     # 继承 AbstractJpaService
  │   ├── SpcRuleService.java           # 导出规则集给 SPC 系统（rule pack 特例）
  │   └── SpcValidator.java              # 规则项参数自洽、SPEC/CONTROL 限关系校验
  └── web
      └── SpcController.java             # 查询/计划发布/规则导出/OCAP 维护
```

---

## 8. 待补 / 后续

- **规则集导出**：把 `spc_rule` + `_term` 编译为 SPC 系统可加载的规则包（复用 [md-distribution-design](md-distribution-design.md) 的发布订阅）。
- **Spec/Control 限来源**：Spec 来自 `step_param`；Control 限可由 MDS 建议（如"前 30 批统计得基线"）但**计算在 SPC**。
- **OOC 事件回流**：失控事件可回流为 [reason-defect-design](reason-defect-design.md) 的原因，供 8D 分析。
- **与既有文档闭环**：本文引用 `mds_route_operation`（[route-design](route-design.md)）、`mds_process_flow_step_param`（[process-flow-design](process-flow-design.md)）、`mds_equip_if_variable`（[equipment-interface-design](equipment-interface-design.md)）、`mds_disposition_rule`（[reason-defect-design](reason-defect-design.md)）、新增约束类型 `SPC_OOC_HOLD`（[constraint-design](constraint-design.md)）、新增 `entity_type=SAMPLING_PLAN/SPC_RULE`（[naming-rule-design](naming-rule-design.md)）。
