# MDS 运行体制剖面设计：Operating Profile（多尺寸 / 多自动化）

> 本文承接 [00-blueprint §8.1](00-blueprint.md)（T9 业务覆盖评审）与 [99-backlog T9](99-backlog.md)，把「6/8/12 寸 + 手动/半自动/全自动」从**散落字段**收敛为**一层显式剖面**。
>
> **一句话定位**：OperatingProfile 是 **L1 空间与组织层的剖面扩展**（不是新的业务域），回答「**这个厂 / 这个区 / 这台设备 / 这道工序，按什么体制运行**」，供 MES 解析**执行契约**。

---

## 0. 定位与边界

| 问题 | 答案 |
| --- | --- |
| 它是什么 | **策略型数据**：把「运行体制」显式化为可查询、可求值、可覆盖的剖面 |
| 它不是什么 | **不是**业务主数据域（不拥有资产/工艺）；**不是**实时态（不含"现在在干什么"）；**不是**新的多厂机制（复用 [multi-site](multi-site-design.md)） |
| 归属层 | **L1 空间与组织**（与位置/组织/日历同层），作用域沿位置层级展开 |
| 谁消费 | **MES**（执行契约解析）、**EAP**（决定是否下发/等人工）、**约束求值**（派工分支）、**AMHS**（搬运模式） |
| 谁拥有 | **MDS** 拥有定义与剖面；**MES/EAP/AMHS** 拥有实时执行 |

> **不推翻的硬约束（重申）**：L0–L5 分层；MDS 出定义 / MES 出实例；Route ≠ Process Flow；行政态 / E10 / GEM Control State / 资格态**正交**；约束声明化；热路径零回查。本文只补**运行体制**与**尺寸断链**。

---

## 1. 行业依据

| 标准 / 实践 | 要点 | 本文落点 |
| --- | --- | --- |
| **SEMI E30 / E5（GEM）** | Control State = `OFFLINE` / `ONLINE-LOCAL` / `ONLINE-REMOTE`（**通信控制态**）；COMM_ENABLED/DISABLED | `protocol` + 能力位；**明确 E30 不是自动化等级** |
| **SEMI E10** | 设备状态与可用性（RAM） | 与 `job_exec_mode` 正交，不合并 |
| **SEMI E87（载具）** | 载具管理；FOUP/FOSB 等容器 | `carrier_type` 枚举扩展（含 Boat，见 §6 OP3） |
| **SEMI E90（片级追踪）** | Substrate Tracking：片位于哪个槽 | `default_tracking_grain` + `slot_map`（定义归 MDS，实时归 MES） |
| **SEMI E40 / E94** | PPID 与配方下发 | `supports_ppid_download` 能力位 |
| **SEMI E5/E30 CJ/PJ** | Control Job / Process Job | `supports_job_create` + `required_txns` |
| **6/8 寸实务** | 大量设备**无 SECS**、人工上机、开盒/舟、无 OHT；混线是 8 寸常态 | `protocol=NONE`、`job_exec_mode=MANUAL`、`carrier_required`、Area 级 override |
| **化合物半导体（SiC/GaN）** | 主流 **150mm**，亦有 100mm；往 200mm 演进 | `WAFER_SIZE` **开放码表**（非 INT 枚举） |

---

## 2. 术语澄清与三维正交声明（**核心**）

### 2.1 三个易混维度的正交关系

设备建模里已有 `process_mode`；本文引入 `job_exec_mode`；GEM 另有 `Control State`。**三者正交，不可互相推导**：

| 维度 | 字段 | 归属 | 取值 | 回答 |
| --- | --- | --- | --- | --- |
| **处理架构** | `mds_equipment.process_mode` | MDS（设备） | `SINGLE_WAFER` / `BATCH` / `CLUSTER` / `HYBRID` | 物理上**怎么处理**（串行/并行） |
| **作业模式** | `OperatingProfile.job_exec_mode`（**派生**） | MDS（剖面） | `MANUAL` / `SEMI_AUTO` / `FULL_AUTO` | **人机怎么交接** |
| **通信控制态** | GEM `Control State`（**实时**） | **EAP 实时域** | `OFFLINE` / `ONLINE-LOCAL` / `ONLINE-REMOTE` | 设备与主机的**通信控制态** |

> ⚠️ **本文最重要的澄清**：`ONLINE-LOCAL` / `ONLINE-REMOTE` **不是**工厂自动化等级。一台 6 寸炉管可以永远 `ONLINE-LOCAL`、甚至完全**没有 SECS**，MES 仍必须管它的批次、配方与工序——**"没有 GEM" 不等于 "不能生产"**。

```
一台设备 = (process_mode, job_exec_mode, control_state) 上的一个组合点
例：6 寸炉管 = BATCH + MANUAL + 无 SECS       —— 三者同时成立，互不矛盾
例：12 寸刻蚀 = SINGLE_WAFER + FULL_AUTO + ONLINE-REMOTE
```

### 2.2 其它术语

| 术语 | 定义 |
| --- | --- |
| **运行体制（Operating Regime）** | 厂/区/设备/工序四级作用域上的「尺寸集合 + 作业模式 + 追踪粒度 + 搬运模式 + 通信能力」综合声明 |
| **能力位（Capability Flags）** | 设备**客观能不能**做某事（能否主机启动、能否下发 PPID、能否建 CJ/PJ、有无载具读头）——**权威** |
| **作业模式（job_exec_mode）** | 由能力位**派生**的摘要标签，供人读与粗筛；**不得反向覆盖能力位** |
| **执行契约（Execution Contract）** | MES 执行一道工序所需的**事务集与前置条件**声明（MDS 出定义，MES 出实例） |
| **载体必需性（carrier_required）** | 该工序是否**必须有载具**；6 寸部分工序为「无载具直投」 |

---

## 3. 实体总览与 ER

```
             ┌──────────────────────────────────────────────┐
             │ 位置层级（L1）：SITE > FAB > AREA > BAY        │
             │                    │                          │
             │   scope_type=FAB ──┤                          │
             │   scope_type=AREA ─┘                          │
             └──────────▲───────────────────────────────────┘
                        │ scope_ref（逻辑引用 location.code）
   ┌────────────────────┴──────────────────────────────────────────┐
   │  mds_operating_profile        运行体制剖面（四作用域）          │
   │    ├── scope_type: GLOBAL / FAB / AREA / EQUIPMENT / ROUTE_OPERATION
   │    ├── 能力位（权威）: supports_host_start / ppid_download /    │
   │    │                 job_create / carrier_id_read              │
   │    ├── protocol: SECS_I / HSMS / NONE / BARCODE                │
   │    ├── job_exec_mode（派生摘要）                                │
   │    ├── default_tracking_grain / handling_modes[]               │
   │    ├── carrier_required / batch_capable                        │
   │    └── domain_profile（域启用与必填降级，见 §6 OP6）            │
   └────────────────────────┬───────────────────────────────────────┘
                            │ 引用（逻辑）
       ┌────────────────────┼────────────────────┬─────────────────┐
       ▼                    ▼                    ▼                 ▼
  mds_location       mds_equipment      mds_route_operation    mds_md_localization
  (FAB/AREA)         (EQUIPMENT)        (工序)                  (域级启用，multi-site)

   ┌──────────────────────────────────────────────┐
   │  mds_operating_profile_capability            │  （可选：能力位明细，见 §5.3）
   │  把 §5.2 的固定 bit 列扩展为「能力目录」行式   │
   └──────────────────────────────────────────────┘
```

---

## 4. 决策表（OP1–OP8）

| # | 议题 | 拍板 | 理由 |
| --- | --- | --- | --- |
| **OP1** | 运行体制是否独立成域？ | **不独立成域**，作为 **L1 剖面扩展**（本文），复用 multi-site 的 GLOBAL/Override 与 location 的层级 | 它是**策略数据**而非资产/工艺定义；另起一域会造成「位置 / 多厂 / 运行体制」三处描述同一件事 |
| **OP2** | 权威是能力位还是作业模式？ | **能力位为权威**，`job_exec_mode` 为**派生摘要** | MES 必须按「能不能做这件事」分支；等级标签会掩盖真实差异（同为 SEMI_AUTO，有的能下发 PPID、有的不能） |
| **OP3** | Boat 归 `carrier_type` 还是 `tooling`？ | **归 `carrier_type`（kind=`BOAT`）** | Boat 本质是**装片容器**：有槽位/管位、有容量、有清洗与寿命、参与搬运——与 carrier 模型同构；`tooling` 是「**不装片**的辅助加工件」（CMP 垫/修整器/模具） |
| **OP4** | `WAFER_SIZE` 用什么表达？ | **开放码表引用**（`mds_code_table(WAFER_SIZE)`，值域 `100/125/150/200/300` + `450` 预留 + 租户可扩展），字段统一为 `wafer_size_code VARCHAR(16)` | 它是**枚举码**而非物理量；写死 INT 枚举遇到 4/5 寸或化合物半导体需改码。与 [uom-dict](uom-dict-design.md) UD3「码表：系统可扩展」同机制 |
| **OP5** | 作用域与优先级 | `GLOBAL < FAB < AREA < EQUIPMENT < ROUTE_OPERATION`，**越具体越优先**；同层按 `priority`，仍冲突则报错 | 与 [constraint-design §4.5](constraint-design.md) 的 specificity 解析**同规则**，避免两套优先级 |
| **OP6** | 6 寸厂要关掉很多域，怎么关？ | **只做「域启用开关 + 必填降级」**（复用 multi-site `mds_md_localization`），**不得裁剪表结构**；开关变更留痕 | 裁表会让同一主数据**跨 fab 语义漂移**，破坏"一套模型多厂复用"；开关可审计、可回滚 |
| **OP7** | 混线怎么覆盖？ | 允许 **Area / Equipment / RouteOperation 覆盖** Fab 级剖面 | 8 寸混线是常态：光刻半自动、湿法手动、炉管舟、量测全自动 |
| **OP8** | 尺寸集合的**单一来源** | **`mds_operating_profile`（FAB/AREA 级）声明"本区支持哪些尺寸"**；`mds_location` **不冗余存**；设备/载具的尺寸是**各自事实**（用于一致性求值），不是"厂级声明"的副本 | 对齐 [location D3](location-design.md)「area 不冗余存储」原则，消除 T9-10 的双源风险 |

---

## 5. 字段设计

### 5.1 `mds_operating_profile`（运行体制剖面）

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` / `tenant_id` / `description` / `deleted` / 审计列 | — | — | 继承 `BaseDefData`；`@History(SNAPSHOT)`；乐观锁 `version` 由基类提供 |
| `code` | VARCHAR(64) | 唯一 `(code, tenant_id, deleted)` | 剖面编码 |
| `name` | VARCHAR(128) | — | 名称 |
| `profile_kind` | VARCHAR(24) | NOT NULL | `FAB_REGIME`（厂级）/ `AREA_REGIME`（区级）/ `EQUIPMENT_REGIME` / `OPERATION_REGIME` |
| `scope_type` | VARCHAR(24) | NOT NULL | `GLOBAL` / `FAB` / `AREA` / `EQUIPMENT` / `ROUTE_OPERATION` |
| `scope_ref` | VARCHAR(128) | — | 作用域引用（**逻辑引用**）：`location.code`（FAB/AREA）/ `equipment.code` / `route_operation.id`；`GLOBAL` 时空 |
| `priority` | INT | DEFAULT 0 | 同层作用域的解析优先级 |
| `job_exec_mode` | VARCHAR(16) | **派生**（见 §5.2.1） | `MANUAL` / `SEMI_AUTO` / `FULL_AUTO`；**冗余存储便于粗筛，权威是能力位** |
| `supports_host_start` | BIT | DEFAULT 0 | 能力位：支持**主机启动**（Host Start） |
| `supports_ppid_download` | BIT | DEFAULT 0 | 能力位：支持**配方/PPID 下发** |
| `supports_job_create` | BIT | DEFAULT 0 | 能力位：支持 **CJ/PJ**（Control Job / Process Job） |
| `supports_carrier_id_read` | BIT | DEFAULT 0 | 能力位：有**载具读头**（BCR / RFID） |
| `protocol` | VARCHAR(16) | DEFAULT `NONE` | `SECS_I` / `HSMS` / `NONE` / `BARCODE` |
| `default_tracking_grain` | VARCHAR(16) | NOT NULL DEFAULT `LOT` | 默认追踪粒度：`LOT` / `WAFER` / `DIE`（工序级可覆盖，见 §5.4） |
| `handling_modes` | JSON | — | 搬运模式**集合**：`WALK` / `CART` / `AGV` / `OHT`（可多值，如 `["WALK","OHT"]`） |
| `carrier_required` | BIT | DEFAULT 1 | 该作用域工序是否**必须有载具**；6 寸「无载具直投」置 0 |
| `batch_capable` | BIT | DEFAULT 0 | 是否支持**批量处理**（炉管一舟多片）；与 `process_mode=BATCH` 呼应 |
| `wafer_sizes` | JSON | — | 该作用域**支持**的尺寸码集合（如 `["150"]`）；**FAB/AREA 级为权威声明**；设备/载具各有事实值 |
| `required_txns` | JSON | — | 该体制必需的**事务集**：`["JOB_IN","JOB_OUT"]` / `+["OPERATOR_CONFIRM"]` / `["CONTROL_JOB","PROCESS_JOB"]` |
| `domain_profile` | JSON | — | **域启用与必填降级**（OP6）：如 `{"APC_FDC":"DISABLED","OHT_PORT":"DISABLED","RETICLE_QUAL":"REQUIRED"}` |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT` / `ACTIVE` / `DEPRECATED`（仅 ACTIVE 参与解析） |
| `effective_from` / `effective_to` | DATETIME(3) | — | 生效窗口（SCD2-lite） |

- **唯一**：`(scope_type, scope_ref, profile_kind, tenant_id, deleted)`。
- **`@History(SNAPSHOT)`**：剖面变更需可追溯（6 寸/8 寸切换是**受控变更**）。

### 5.2 能力位 → `job_exec_mode` 派生规则

#### 5.2.1 派生逻辑（**单一算法，写入服务**）

```
if protocol ∈ {NONE, BARCODE} and not supports_host_start and not supports_ppid_download:
        job_exec_mode = MANUAL          -- 人搬盒、面板选配方、按启动；MES 只做 JobIn/JobOut
elif supports_host_start and supports_job_create and protocol ∈ {SECS_I, HSMS}:
        job_exec_mode = FULL_AUTO       -- AMHS 到位 + GEM REMOTE + CJ/PJ；才要求 ONLINE-REMOTE
else:
        job_exec_mode = SEMI_AUTO       -- MES 选设备+配方（可下发 PPID）→ 人确认上机
```

#### 5.2.2 三种模式的执行契约对照

| 模式 | 载具到位 | 配方下发 | 事务集 | 是否要求 GEM REMOTE |
| --- | --- | --- | --- | --- |
| `MANUAL` | 人搬 / 可无载具 | 人**在面板选** | `JOB_IN` / `JOB_OUT` | **不查 GEM**（无 SECS 亦合法） |
| `SEMI_AUTO` | 人搬或 AGV | MES 解析并**可下发 PPID** | `+ OPERATOR_CONFIRM` | 不强制；若 `ONLINE` 则校验本地态 |
| `FULL_AUTO` | AMHS/OHT | MES 下发 PPID | `CONTROL_JOB` / `PROCESS_JOB` | **强制 `ONLINE-REMOTE`** |

> **红线**：**禁止用「必须 ONLINE-REMOTE 才可派工」一刀切**（详见 [constraint-design](constraint-design.md) `DISPATCH_READY`）。

### 5.3 `mds_operating_profile_capability`（可选：能力目录）

当厂内设备能力差异大、且希望**不随能力新增而改表**时，用行式能力目录替代 §5.1 的固定 bit 列：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `profile_id` | VARCHAR(32) | FK→`mds_operating_profile.id` |
| `capability_code` | VARCHAR(32) | 能力码（如 `HOST_START` / `PPID_DOWNLOAD` / `JOB_CREATE` / `CARRIER_ID_READ` / `ALARM_REPORT` / `SAMPLING`） |
| `capability_value` | VARCHAR(64) | 取值（`1`/`0` 或具体能力等级） |

- **唯一**：`(profile_id, capability_code, tenant_id, deleted)`。
- **默认采用 §5.1 固定列**（快、可索引、热路径友好）；仅当能力项**超过 8 个且频繁变化**时切换到本表。

---

## 6. 作用域解析与优先级（OP5/OP7）

```
解析 resolve-exec-contract(输入：equipment | route_operation)
  1. 收集候选剖面：
       GLOBAL → 该设备所属 FAB → 所属 AREA → EQUIPMENT 级 → ROUTE_OPERATION 级
  2. 按 specificity 从粗到细**逐层覆盖**（细粒度字段覆盖粗粒度，未覆盖的继承）
  3. 同层多条命中 → 按 priority 降序；仍相同 → **报错**（不允许静默取任一条）
  4. 派生 job_exec_mode（§5.2.1），校验 required_txns 与 carry_required 组合合法
  5. 输出执行契约（见 §8）
```

**典型覆盖示例（8 寸混线）**：

| 作用域 | 剖面 | 说明 |
| --- | --- | --- |
| FAB `F2` | `MANUAL` + `protocol=NONE` + `handling=[WALK,CART]` + `wafer_sizes=["150"]` | 厂级默认：人工为主 |
| AREA `F2.LITHO` | `SEMI_AUTO` + `supports_ppid_download=1` + `protocol=SECS_I` | 光刻半自动（例外抬高） |
| AREA `F2.MEAS` | `FULL_AUTO` + `supports_job_create=1` + `handling+=[OHT]` | 量测全自动 |
| EQUIPMENT `F2.WET-03` | `MANUAL` + `carrier_required=0` | 湿法某台为无载具直投 |

---

## 7. 与既有文档闭环

| 关系 | 内容 | 对方文档 |
| --- | --- | --- |
| 作用域 ↔ 位置 | `scope_ref = mds_location.code`（`level=FAB/AREA`），**逻辑引用**；`mds_location` **不存** `wafer_sizes`/自动化字段 | [location-design](location-design.md)（D3、T9-10） |
| 剖面 ↔ 多厂策略 | 复用 GLOBAL/FAB/Override 词汇；**域启用开关**复用 `mds_md_localization`（`local_action` + `is_enabled`），本文不重复造 | [multi-site-design](multi-site-design.md)（MS1） |
| 剖面 ↔ 设备 | `process_mode` 是**物理架构**，与本表 `job_exec_mode`（作业模式）**正交**（§2.1）；设备尺寸事实值经 `mds_equipment_wafer_size` 关联表表达 | [equipment-design](equipment-design.md) §3.1.4 / 附录 A |
| 剖面 ↔ 工序 | `scope_type=ROUTE_OPERATION` 可覆盖 `job_exec_mode` / `tracking_grain` | [route-design](route-design.md) §3.3 |
| 剖面 ↔ 约束 | `DISPATCH_READY` 按 `job_exec_mode` 分支；`WAFER_SIZE_MATCH` 依赖设备尺寸事实值 | [constraint-design](constraint-design.md) §4.2 |
| 剖面 ↔ 载具 | `carrier_required` / `handling_modes` 决定是否强制载具与载具类型兼容；Boat 归 `carrier_type` | [carrier-design](carrier-design.md) §4.3 |
| 剖面 ↔ 设备接口 | `protocol=NONE/BARCODE` 时点表非必填 | [equipment-interface-design](equipment-interface-design.md) |
| 剖面 ↔ 实时态契约 | 剖面是**定义**；`control_state` 等实时态仍归 EAP，MDS 不存 | [realtime-contract-design](realtime-contract-design.md) |
| 剖面 ↔ 码表 | `wafer_sizes` 取值来自 `mds_code_table(WAFER_SIZE)` | [uom-dict-design](uom-dict-design.md) §3.3 |

---

## 8. 与 MES 生产执行衔接

### 8.1 解析接口

```
POST /api/v1/mds/operating/resolve-exec-contract
  body: { "scope": "EQUIPMENT", "ref": "F2.LITHO-01" }
    或  { "scope": "ROUTE_OPERATION", "ref": "<op_id>" }
  resp: {
    "job_exec_mode": "SEMI_AUTO",
    "protocol": "SECS_I",
    "capabilities": { "host_start": true, "ppid_download": true,
                      "job_create": false, "carrier_id_read": true },
    "default_tracking_grain": "LOT",
    "handling_modes": ["WALK", "CART"],
    "carrier_required": true,
    "required_txns": ["JOB_IN", "JOB_OUT", "OPERATOR_CONFIRM"],
    "domain_profile": { "APC_FDC": "DISABLED" },
    "source_profile_code": "F2.LITHO",
    "resolved_chain": ["GLOBAL", "F2", "F2.LITHO"]
  }
```

### 8.2 三种模式下的 MES 行为差异（**执行契约**）

| 阶段 | MANUAL | SEMI_AUTO | FULL_AUTO |
| --- | --- | --- | --- |
| 派工 | 选设备 + 校验批次/行政态，**不查 GEM** | 选设备 + 解析配方/PPID | 选设备 + 校验 REMOTE + 载具到位 |
| 上机 | 人搬盒 → MES 记 `JOB_IN` | MES 下发 PPID → **等 `OPERATOR_CONFIRM`** | AMHS 到位 → MES 建 `CONTROL_JOB`/`PROCESS_JOB` |
| 过程中 | 人看面板；**无点表**（或仅条码） | 部分 SV 可采（有点表则采） | SV/CEID/Alarm 全采 |
| 下机 | 人取出 → MES 记 `JOB_OUT` | 同上 | 自动 `JOB_OUT` + 载具回收 |
| 追溯 | lot 级（默认 `LOT`） | lot 级 + **关键工序片级**（override） | lot 级 + 片级 + 可选 die 级 |

### 8.3 无载具直投（`carrier_required=0`）

6 寸部分工序（如单片湿法、手动显微镜检）**片盒直接上机或不使用载具**：

- 剖面置 `carrier_required=0` → MES **不强制** `CARRIER_AREA_COMPAT` 校验，允许"无载具直投"；
- 但 **`lot` 仍存在**（批次是管理单元，与载具无关）；
- 若该工序同时 `default_tracking_grain=WAFER`，则 MES 需片级 JobIn（人扫码/手工点选）。

---

## 9. DDL（MySQL 8）

```sql
-- V1__operating_profile.sql  (common / mysql)
CREATE TABLE mds_operating_profile (
  id                    VARCHAR(32)  NOT NULL,
  tenant_id             VARCHAR(32)  NOT NULL,
  code                  VARCHAR(64)  NOT NULL,
  name                  VARCHAR(128),
  profile_kind          VARCHAR(24)  NOT NULL,
  scope_type            VARCHAR(24)  NOT NULL,
  scope_ref             VARCHAR(128),
  priority              INT          NOT NULL DEFAULT 0,
  job_exec_mode         VARCHAR(16)  NOT NULL,
  supports_host_start   BIT          NOT NULL DEFAULT 0,
  supports_ppid_download BIT         NOT NULL DEFAULT 0,
  supports_job_create   BIT          NOT NULL DEFAULT 0,
  supports_carrier_id_read BIT       NOT NULL DEFAULT 0,
  protocol              VARCHAR(16)  NOT NULL DEFAULT 'NONE',
  default_tracking_grain VARCHAR(16) NOT NULL DEFAULT 'LOT',
  handling_modes        JSON,
  carrier_required      BIT          NOT NULL DEFAULT 1,
  batch_capable         BIT          NOT NULL DEFAULT 0,
  wafer_sizes           JSON,
  required_txns         JSON,
  domain_profile        JSON,
  status                VARCHAR(16)  NOT NULL,
  effective_from        DATETIME(3),
  effective_to          DATETIME(3),
  description           VARCHAR(512),
  version               BIGINT       NOT NULL DEFAULT 0,
  deleted               BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_operating_profile PRIMARY KEY (id),
  CONSTRAINT uq_mds_op_profile UNIQUE (scope_type, scope_ref, profile_kind, tenant_id, deleted)
  -- scope_ref: 跨域/层级逻辑引用（mds_location.code / mds_equipment.code / mds_route_operation.id）
  --            按 00-blueprint §10 引用规范：不建 DB 级 FK，由 OperatingProfileValidator 服务层校验
);
CREATE INDEX idx_op_scope ON mds_operating_profile (scope_type, scope_ref);
CREATE INDEX idx_op_mode  ON mds_operating_profile (job_exec_mode, status);

CREATE TABLE mds_operating_profile_capability (
  id               VARCHAR(32)  NOT NULL,
  tenant_id        VARCHAR(32)  NOT NULL,
  profile_id       VARCHAR(32)  NOT NULL,
  capability_code  VARCHAR(32)  NOT NULL,
  capability_value VARCHAR(64),
  description      VARCHAR(512),
  version          BIGINT       NOT NULL DEFAULT 0,
  deleted          BIT          NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_op_cap PRIMARY KEY (id),
  CONSTRAINT uq_mds_op_cap UNIQUE (profile_id, capability_code, tenant_id, deleted),
  CONSTRAINT fk_opc_profile FOREIGN KEY (profile_id) REFERENCES mds_operating_profile (id)
);
CREATE INDEX idx_opc_profile ON mds_operating_profile_capability (profile_id);
```

> **注**：`mds_operating_profile_capability` 按 §5.3 是**可选**表——默认用主表固定 bit 列，能力项超 8 个且频繁变化时启用。

---

## 10. 代码结构

```
business/mds-ap/server/
└── mds-domain/
    └── src/main/java/com/cim/mds/operating/
        ├── OperatingProfile.java              # @Entity 继承 BaseDefData + @History(SNAPSHOT)
        ├── OperatingProfileCapability.java    # @Entity 继承 BaseDefData
        ├── OperatingProfileRepository.java    # 按 scope_type + scope_ref 查询
        ├── OperatingProfileService.java       # 继承 AbstractJpaService；resolveExecContract()
        ├── ExecContractResolver.java          # §6 作用域链解析 + §5.2.1 派生
        ├── ExecContract.java                  # 输出 DTO（§8.1 resp 结构）
        ├── OperatingProfileValidator.java     # scope_ref 逻辑引用校验；MODEL 组合合法性
        └── OperatingProfileController.java    # /api/v1/mds/operating/**
```

**关键服务**：

- `ExecContractResolver.resolve(scope, ref)`：按 §6 收集候选 → 逐层覆盖 → 派生 `job_exec_mode` → 校验 `required_txns` 与 `carrier_required` 组合 → 返回契约（**热路径可缓存**，按 `profile.version` 失效）；
- `OperatingProfileValidator`：`scope_type` ↔ `scope_ref` 形态匹配（FAB/AREA→location.code；EQUIPMENT→equipment.code；ROUTE_OPERATION→route_operation.id）；`protocol=NONE` 时不得置 `supports_host_start=1`（**能力位自洽校验**）。

---

## 11. 待补 / 后续

- **与 `mds_md_localization` 的分工收敛**：`domain_profile`（本文）与 `local_action`（multi-site）目前有语义重叠——需定稿"谁管域启用、谁管必填降级"，避免双源（建议：**启用开关归 multi-site，必填降级归本文**，见 OP6）。
- **`mds_equipment_wafer_size` 关联表**：设备尺寸事实值的载体（与 `mds_equipment_tech_node` 同范式），在 [equipment-design 附录 A](equipment-design.md) 落地；位置/型号级选择待定（建议**型号级默认 + 设备级覆盖**）。
- **`WAFER_SIZE` 码表种子**：在 [uom-dict-design](uom-dict-design.md) §3.3 登记 `WAFER_SIZE` 系统码表（`100/125/150/200/300` + `450`），并回写 product / carrier / bank / material / test-asset / equipment 六处尺寸字段为 `*_code` 引用。
- **`APC/FDC` 与 6 寸的取舍细则**：`domain_profile` 的取值字典（`DISABLED` / `OPTIONAL` / `REQUIRED`）需与 [md-governance](md-governance-design.md) 的域注册对齐。
- **与 EAP 的 `protocol=NONE` 分支**：EAP 需支持"无通信设备"的伪设备（手工 JobIn 终端），属 EAP 侧实现；MDS 只声明 `protocol`。
- **管理端可视化**：剖面树（沿位置层级）+ 覆盖来源链（`resolved_chain`）展示，便于工艺工程师排障。
- **`wafer_size` 与 `lot` 的一致性**：工程批使用不同尺寸衬底时的例外规则（建议经约束 `WAFER_SIZE_MATCH` 的 `condition` 表达，不写死）。
