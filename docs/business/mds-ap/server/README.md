# MDS-AP 后端设计文档（域入口与决策摘要）

> 本文件是 MDS 各主数据域的**入口索引与决策摘要**（§3.x 逐域列出拍板结论与链接）。
> **总纲（全局关系、术语表、编码唯一性、MES 消费视角）见 👉 [00-blueprint.md](00-blueprint.md)**。
> MDS（主数据 / 设备主数据）是业务层的一个 ap，与 `iam-ap`、`mes-ap` 同构。
>
> 文档规模：**39 篇 design 文档**。**权威域清单见 👉 [00-blueprint §1.1](00-blueprint.md)**（26 业务主数据域 + 2 基础参考数据域 + 4 横切治理层 + 2 横切规范 + 2 集成接口 = 36 个域，另加 3 篇交付管理 + 1 篇总纲）。
>
> 📋 **最近一次评审**：[98-audit-report.md](98-audit-report.md)（第 2 轮五轮次深度审核，P0×3 / P1×4 / P2×7）；**待办与 DoD**：[99-backlog.md](99-backlog.md)。

## 0. 定位

- **归属**：`docs/business/mds-ap/`，业务 ap，基于平台框架（`docs/platform/server`）实现。
- **身份与准入**：经 **IAM（`iam-ap`）** 统一登录，不直接对接 LDAP / AD。

## 1. 认证与准入对接（已确认方案）

访问 `mds-ap` 时的校验链路：

1. **本地验签**：用 IAM 的 JWKS 公钥验证令牌签名与有效期（不每请求回查 IAM）。
2. **准入校验（IAM 管）**：检查令牌 `apps` claim 是否含 `mds-ap`；不含则拒绝进入。
3. **内部权限（业务自管）**：准入通过后，`mds-ap` 用自己的 **RBAC**（复用平台 §21.1）控制菜单 / 按钮 / 数据行级权限。

> 关键点：IAM 的刀在「准入（步骤 2）」结束，**步骤 3 完全由 mds-ap 自行控制**。

## 2. 内部权限（业务自管）

- 菜单 / 按钮 / 数据权限由 `mds-ap` 的 RBAC 承载，不依赖 IAM。
- 员工在某 ap 内的**业务角色**由该 ap 自己分配（IAM 只管粗角色组与准入）。

## 3. 位置主数据设计（已拍板）

MDS 首项落地的业务域是**位置主数据（Site / Fab / Area / Bay / SubBay）**，详细设计见独立文档：

👉 **[位置主数据详细设计](location-design.md)**

基于半导体 CIM（SEMI E148/E10）行业经验，已对 6 项开放问题拍板（详见设计文档 §0）：

| # | 问题 | 拍板结论 |
| - | ---- | -------- |
| D1 | 层级是否固定 | 标准五层 + `level` 枚举可扩展；EQUIPMENT 不进位置层级 |
| D2 | Area 语义 | AREA = 工艺/功能域（Litho/Etch…），物理落位由 BAY/SUBBAY 表达 |
| D3 | 单父 / 多父 | 严格单父树；跨区服务用独立关联实体，不破坏树 |
| D4 | SCD2 生效日期 | 需要轻量 SCD2：`effective_from`/`effective_to` + `status` |
| D5 | code 唯一与编码 | 同租户全局唯一（遵循 T2.5 软删唯一）；推荐分层点分码 |
| D6 | 机台-位置关系 | 设备由 MDS 主数据管理；搬迁轨迹复用 `@History(SNAPSHOT)` |

## 3.1 工艺路线主数据（Route / Step / Flow）设计（已拍板）

MDS 第三块核心主数据——工艺路线（Route）、工序（Step/Operation）、工序流（Flow/连接），与位置、设备、配方四向闭环。详细设计见：

👉 **[工艺路线主数据详细设计](route-design.md)**

基于 SEMI E94/E87/E40 与 fab MES 实践，已拍板核心决策：

| # | 问题 | 拍板结论 |
| - | ---- | -------- |
| R1 | 边界 | MDS 拥有 route/step/flow **定义**（版本化、跨 lot 共享）；**运行实例（lot 当前 step/WIP）归 MES** |
| R2 | 模型 | **节点-边图模型**（operation=节点，flow=边），非线表——支持分支/并行/返工回路 |
| R3 | 版本与生命周期 | 完整生命周期 `DRAFT→IN_REVIEW→APPROVED→RELEASED→OBSOLETE→ARCHIVED`；**RELEASED 后版本不可变**（改须 `cloneAsNewVersion`）；`is_frozen` 叠加冻结标志（与 status 正交，量产前/客户/法规锁定） |
| R4 | Flow 类型 | 五型：`SEQUENCE`/`BRANCH`/`PARALLEL_SPLIT`/`PARALLEL_JOIN`/`REWORK`（含条件表达式） |
| R5 | 并行语义 | 工艺流「并行」与设备 `process_mode`「物理并行」**正交**，互不耦合 |
| R6 | 复用 | `mds_process` 独立于 route 复用；`sub_route_id` 支持子路线嵌套复用 |

## 3.2 产品主数据（Product / Product Family / Route Link）设计（已拍板）

MDS 第四块核心主数据——产品（Product）/ 器件 / 料号，经 route 与位置、设备、配方三向闭环。详细设计见：

👉 **[产品主数据详细设计](product-design.md)**

基于 fab MES 主数据实践，已拍板核心决策：

| # | 问题 | 拍板结论 |
| - | ---- | -------- |
| P1 | 分层 | **Family > Product** 继承（族承载 tech_node/工艺平台，产品可覆盖），避免逐产品重复 |
| P2 | 产品↔路线 | `default_route_code` 默认路线 + `mds_product_route` 多对多（默认+备选）；与 route 双向闭环 |
| P3 | 双维度状态 | `lifecycle_status`（业务阶段 DESIGN→…→EOL）**与** `status`（记录管理态 DRAFT→RELEASED→OBSOLETE→ARCHIVED）**正交** |
| P4 | 版本 | `product_code+version` 唯一；`RELEASED` 后规格版本**不可变**，改须 `cloneAsNewVersion`（与 route 对称） |
| P5 | tech_node | 产品 `tech_node` ∩ 设备 `mds_equipment_tech_node` → 可造设备池，全链路闭环筛选键 |
| P6 | 扩展属性 | `ext_attrs` JSON 承载异构属性（封装/引脚/可靠性…），核心字段留主表；EAV 为可选 |

## 3.3 载具主数据（FOUP / Carrier / Cassette）设计（已拍板）

MDS 第五块核心主数据——载具（Carrier）/ FOUP / 晶圆盒，经 wafer_size 与产品/设备一致、经 compat 与路线工艺区闭环。详细设计见：

👉 **[载具主数据详细设计](carrier-design.md)**

基于 SEMI E15/E28.1/E87/E39 + fab AMHS/载具 MDM 实践，已拍板核心决策：

| # | 问题 | 拍板结论 |
| - | ---- | -------- |
| C1 | FOUP vs Carrier | FOUP 是 **300mm carrier 的子类型**；用 `carrier_type ∈ {FOUP,SMIF_POD,CASSETTE}` + `wafer_size` 区分，不另建平行实体 |
| C2 | 类型 vs 实例 | `mds_carrier_type`（模型字典：容量/尺寸/厂商/兼容/周期）↔ `mds_carrier`（单个物理载具登记）分层，与设备 model/equipment 同构 |
| C3 | 三态分离 | 行政态 `admin_status` + 洁净态 `clean_status`（MDS 资产级）**与** 实时传输/访问态（E87，归 MES/AMHS）**正交** |
| C4 | wafer_size 一致 | 载具尺寸须同时匹配 产品 `wafer_size` 与 设备尺寸，三方一致方可组批搬运 |
| C5 | 工艺兼容/污染控制 | `mds_carrier_type_compat`（type↔area/class/process）决定可进工艺区，闭环 route operation |
| C6 | 所有权/PM | `mds_carrier_owner` 多 owner 多角色（自有/寄售）；`clean/pm_interval_days` + 到期驱动状态变更 |

## 3.4 配方主数据（Recipe / RecipeGroup / LogicRecipe / PPID）设计（已拍板）

MDS 第六块核心主数据——配方（逻辑/物理双层）、配方分组、PPID，与位置、设备、路线、产品五向闭环。详细设计见：

👉 **[配方主数据详细设计](recipe-design.md)**

基于 SEMI E40（Process Program Management）/E94 + fab Recipe 实践，已拍板核心决策：

| # | 问题 | 拍板结论 |
| - | ---- | -------- |
| RC1 | 双层 | **LogicRecipe（工具无关）** 被 route 工序引用；**PhysicalRecipe（`mds_recipe`，工具相关）** 经 `logic_recipe_id` 按设备类 1:N 映射；PPID 落在设备资格链接（三层对应 SEMI E40 Logical→Equipment→ProcessProgram） |
| RC2 | RecipeGroup | `mds_recipe_group` 层级树（`group_type` ∈ LOGIC/PHYSICAL），管理分组，不参与强约束 |
| RC3 | PPID | `mds_equipment_recipe_qual.ppid` = 该物理配方在此设备上的 Process Program ID；设备内唯一 |
| RC4 | 资格/单多 | 设备↔物理配方资格多对多；资格集 1 条=专用机(单 recipe)，多条=柔性机(多 recipe)；运行时每腔体单活跃配方(归 MES) |
| RC5 | 生命周期 | 逻辑/物理配方均 `DRAFT→…→RELEASED→OBSOLETE→ARCHIVED`；`(code,version)` 不可变，改须 `cloneAsNewVersion`；`is_frozen` 叠加冻结（与 route/product 同源） |
| RC6 | 派工解析 | MDS 提供映射：设备 E + 逻辑配方 L → 物理配方 R(class=E.class) → qual(E,R).ppid 下发 EAP；当前 PPID 实时态归 MES/EAP |

## 3.5 仓库主数据（Bank / Stocker / 储位）设计（已拍板）

MDS 第七块核心主数据——仓库（Bank）/ 自动储位（Stocker）/ 缓冲库，经 `location_id` 落点位置层级、经 `home_stocker_code` 与载具闭环、经 `out_bank_code` 与工序缓冲闭环。详细设计见：

👉 **[仓库主数据详细设计](bank-design.md)**

基于 SEMI E87（Carrier/AMHS）/E15/E28.1 + fab 库存 MDM 实践，已拍板核心决策：

| # | 问题 | 拍板结论 |
| - | ---- | -------- |
| BK1 | Bank 与 location 关系 | Bank 是**独立存储设施实体**（非 `level` 枚举）；`location_id → mds_location` 物理落点，复用层级/闭包上溯 AREA；与 EQUIPMENT 处理同构 |
| BK2 | Bank 类型 | `bank_type ∈ {IN_PROCESS, BUFFER, FINISHED_GOODS, CENTRAL, BAY_STOCKER}`（IPB/缓冲/成品/中央/邻 bay） |
| BK3 | 容量与槽位 | `capacity` 规划额定值；`mds_bank_slot` 槽位目录（实时占用归 AMHS），MDS 不存实时态 |
| BK4 | AMHS 接口端口 | `mds_bank_port` 复用设备端口 `PORT_STATE` 状态机定义（设备设计 §3.14.5），不重复造轮子 |
| BK5 | 闭环 | Bank ↔ 载具：`home_stocker_code → mds_bank.code`（**修正原指向 mds_location**）；Bank ↔ 工序：`out_bank_code`（预留）；Bank ↔ 设备：共享 `location_id` |
| BK6 | 实时态 | Bank 定义/行政态/槽位目录归 MDS；实时占用、carrier 在槽位、端口访问态（E87）归 AMHS/MES |

## 3.6 命名/编码规则（NamingRule，横切配置）设计（已拍板）

MDS 的**编码治理层**——统一管理位置 / 设备 / 载具 / 配方 / 路线 / 产品 / 仓库七类实体 `code` 的自动生成与校验（含位置 D5 分层点分码）。详细设计见：

👉 **[命名/编码规则详细设计](naming-rule-design.md)**

基于 fab MES/MDS 编码规范与 MDM 通用实践，已拍板核心决策：

| # | 问题 | 拍板结论 |
| - | ---- | -------- |
| NR1 | 边界 | **横切配置实体**（非业务主数据），管理七类实体的 code/name 生成与校验；MDS 拥有定义+生成+校验，下游只消费 code |
| NR2 | 段模型 | 规则=有序 segment 列表；`segment_type ∈ {CONST,ATTR,SEQ,HIER,DATE,UUID,MAP,CHECKSUM}`，按 `delimiter` 拼接 |
| NR3 | 序列管理 | `mds_naming_seq` 自管计数器（scope_key 可选区域级流水），`@Version` 乐观锁保证并发安全 |
| NR4 | 层级码 | 位置用 `HIER` 段沿父链拼出 `/SZ/F1/LITH/B03`（落实 D5）；设备用 `ATTR(area_code)+ATTR(class)+SEQ` 拼 `F1-LITH-SCN-0001` |
| NR5 | 强制唯一 | `enforce_unique` 默认 true，生成后校验租户内唯一（契合 T2.5 软删唯一）；存量 code 不回溯破坏 |
| NR6 | 规则生命周期 | 规则本身 `DRAFT→ACTIVE→DEPRECATED`+`revision`；改 pattern 升版本，存量实体 code 不变（与 route/product 同源） |

## 3.7 工艺流（Process Flow，Route 的工艺展开层）设计（已拍板）

MDS 在「工艺路线（Route，逻辑层）」之上补充**工艺流（Process Flow，工艺展开层）**——把每个 Route Operation 展开为具体的 Process Flow Step（物理配方 + PPID + 设备组 + 工艺参数规范）。同时提供**流程对比**能力（版本 diff / 多方案横评 / 覆盖校验 / 跨产品对比）。详细设计见：

👉 **[工艺流（Process Flow）详细设计](process-flow-design.md)**

基于 SEMI E94/E40 + Camstar/西门子 MES 双层模型 + fab 工程实践，已拍板核心决策：

| # | 问题 | 拍板结论 |
| --- | ---- | -------- |
| PF1 | Route 与 Process Flow 关系 | **双层分离**：Route = 逻辑工序图（顺序）；Process Flow = 工艺展开（每个 operation → step 含物理配方/PPID/设备组/参数）；**1 Route : N Process Flow**（多方案/多 fab）。修正原 route-design §2「Route=Process Flow」同义表述 |
| PF2 | 多方案/多 fab | `scenario_code`（`BASE`/`ENG`/`HVM`/`N+1`/`LOCAL_<FAB>`）+ `fab_code` 区分；`is_default` 标记默认工艺流 |
| PF3 | step 展开内容 | `recipe_id`(物理) + `ppid`（默认 PPID，经 `mds_process_flow_step_recipe` 表达多设备多 PPID 资格）；`equipment_group_id` 指向派工设备组；逻辑配方仍由 route.operation.recipe_id 承载「做什么」 |
| PF4 | 工艺参数规范 | `mds_process_flow_step_param` 独立表存工艺窗口（target/min/max/unit/spec_type），作为 SPC 基线 + 派工校验 + 对比核心维度 |
| PF5 | 流程对比（核心能力） | 4 类：**版本 diff**（同 route+scenario 两版本逐 step/param 对比）、**多方案横评**（同 route 各 scenario 指标矩阵）、**覆盖校验**（Process Flow 是否完整覆盖 Route 且可执行，RELEASED 前门禁）、**跨产品/跨 Family 对比**（平台化复用）；结果持久化 `mds_process_flow_compare` + `_item` 供评审留痕 |
| PF6 | 设备组 | 新增支撑主数据 `mds_equipment_group` + `mds_equipment_group_member`（class 之上的精细派工集合），被 Process Flow Step 引用，MES 排程/AMHS/PM 可复用 |

## 3.8 约束（Constraint，横切关系规则层）设计（已拍板）

MDS 的**第二条横切治理层**——与 [命名/编码规则](naming-rule-design.md)（编码治理层）并列的**关系规则治理层**：统一管理「什么与什么之间必须/不得成立」的跨实体规则（Q-Time、组批上限、配方资格、载具准入、返工上限、库容、日历、SPC…）。详细设计见：

👉 **[约束详细设计](constraint-design.md)**

基于 SEMI E10/E5·E30/E40/E87/E94 + Camstar/Opcenter/199 的 Rule/Constraint 实践 + ISA-95，已拍板核心决策：

| # | 问题 | 拍板结论 |
| --- | ---- | -------- |
| CT1 | 定位 | 约束是 MDS 的**横切关系规则层**（非某主数据的附属字段）；是**声明式数据**而非硬编码分支；**MDS 拥有约束定义 + 试算，MES/EAP/AMHS 拥有实时求值与执行** |
| CT2 | 模型 | 统一声明式约束模型 `⟨Type, Subject↔Object, Assertion(MUST/MUST_NOT), Condition, Params⟩ + ⟨severity, priority, scope⟩`；**已有专用兼容表保持权威，约束层只注册+编排，不复制数据**（§4.8） |
| CT3 | 分类 | 9 大语义族 `COMPATIBILITY/TIMING/SEQUENCE/BATCHING/CAPABILITY/CAPACITY/WINDOW/QUALITY/OTHER` + 类型目录（Q_TIME/BATCH_MAX_SIZE/REWORK_LIMIT/EQUIP_RECIPE_QUAL/CARRIER_AREA_COMPAT/TECH_NODE_MATCH/BANK_CAPACITY/EQUIP_CALENDAR/SPC_SPEC…，含必填参数） |
| CT4 | 强度 | `hard_soft`(HARD/SOFT) + `severity`(BLOCK/WARN/INFO)；HARD=BLOCK 不可越权放行并短路求值；SOFT 可带审批放行 + 留痕 |
| CT5 | 组合与作用域 | 约束集 `mds_constraint_set` 可复用组合 + `mds_constraint_binding` **作用域分层**（GLOBAL→FAMILY→产品/路线/区→工序/边/设备实例），按 specificity 解析生效集合并支持 override |
| CT6 | 试算能力（核心） | 4 类：**单对象校验 SINGLE**、**上下文求值预演 CONTEXT**（投料/派工试算）、**冲突检测 CONFLICT**、**覆盖分析 COVERAGE**（红线体检）；结果落 `mds_constraint_eval` + `_item`；另提供 `compileRulePack()` 供下游订阅 |
| CT7 | 生命周期/版本 | `DRAFT→ACTIVE→DEPRECATED` + `revision` + `is_frozen`（与 naming-rule/route 同源）；改语义须 `cloneAsNewVersion` |

## 3.9 扩展主数据域总览（P0–P2，13 篇）

在七块主数据 + 工艺流展开层 + 编码/约束两条横切层之上，补齐行业必需的 **13 个扩展域**。按「是否阻塞下游上线」分三档：

### P0 · 生产直接依赖（不建则 MES/EAP 上不了线）

| # | 域 | 文档 | 核心决策 | 关键表 |
| - | -- | ---- | -------- | ------ |
| 3.9.1 | 物料与耗材 | [material-design.md](material-design.md) | **M1 物料≠产品**（投入品 vs 产出物）；M4 四类寿命策略（保质/次数/时长/曝光）+ 到期动作；M5 三类用料 BOM（工序/配方/产品）；库存归 ERP/WMS | `mds_material` `_class` `_spec` `_life_policy` `_bom` `_bom_line` `_alternate`、`mds_equipment_consumable` |
| 3.9.2 | 层 Layer | [layer-design.md](layer-design.md) | **L2 工艺层 : 掩模层 = 1:N**（多重曝光 LELE/SADP）；L3 层↔工序映射带 `role`；L4 与 `product.mask_layer_count` 一致性校验 | `mds_layer`、`mds_layer_operation` |
| 3.9.3 | 光罩 Reticle | [reticle-design.md](reticle-design.md) | RT2 套件（业务单元）+ 单片（资产单元）；**RT4 光罩↔scanner 资格**（同 recipe qual 范式）；RT5 曝光次数寿命；**pod 复用 `carrier_type`、stocker 复用 `bank`**（枚举扩展） | `mds_reticle_set` `_reticle` `_qual` `_layer` `_usage_policy` |
| 3.9.4 | 设备接口点表 | [equipment-interface-design.md](equipment-interface-design.md) | EI2 **型号级模板 + 实例级覆盖**（不复制整表）；EI3 SV/DV/EC 访问权限；EI4 CEID/ALID/RPTID；EI5 点表=「能报什么」≠ 参数=「要求什么」 | `mds_equip_if_definition` `_variable` `_event` `_alarm` `_report` `_report_var` `_override` |
| 3.9.5 | PM 与校准 | [pm-calibration-design.md](pm-calibration-design.md) | PM2 三层作用域（MODULE>EQUIPMENT>MODEL）；PM3 四类触发取先到；**PM4 补齐设备状态机的触发源**；PM5 校准超期 → 资格失效 | `mds_pm_plan` `_task` `_plan_task`、`mds_calibration_spec` `_item` |
| 3.9.6 | 原因码与处置 | [reason-defect-design.md](reason-defect-design.md) | **RD2 三类代码不可合并**（原因/缺陷/Bin）；RD3 停机必挂原因码对齐 E10/OEE；RD5 声明式处置规则；RD6 处置「怎么办」≠ 约束「可否」 | `mds_reason_code` `_defect_code` `_bin_code`、`mds_disposition_rule` `_item` |

### P1 · 治理与质量闭环

| # | 域 | 文档 | 核心决策 | 关键表 |
| - | -- | ---- | -------- | ------ |
| 3.9.7 | 组织/人员/日历 | [org-personnel-design.md](org-personnel-design.md) | **OP1 与 IAM 严格划界**（不建人员主数据，只存授权判定）；OP2 承接 `owner_ref` 悬空；OP3 统一 `mds_vendor`；OP4 认证矩阵（持证上岗）；OP5 补齐 `EQUIP_CALENDAR` 引用目标 | `mds_org_unit` `_vendor` `_skill` `_person_certification` `_work_calendar` `_shift` |
| 3.9.8 | 变更管理 | [change-mgmt-design.md](change-mgmt-design.md) | **CM1 第三条横切治理层（流程层）**；CM2 ECR→ECN 两段式；CM3 依赖图反向可达做影响分析；CM5 变更单=版本升级的合规凭证 | `mds_ecr` `_ecn` `_change_item` `_change_impact` `_change_approval` |
| 3.9.9 | 受控文档 | [document-design.md](document-design.md) | **DOC1 元数据归 MDS、正文归 DMS**（同 `body_ref` 范式）；DOC3 多态绑定；DOC4 同时仅一个 `EFFECTIVE` 版本 | `mds_document`、`mds_document_link` |
| 3.9.10 | 采样与 SPC | [sampling-spc-design.md](sampling-spc-design.md) | SP2 三方分工（点表/参数/采样）；SP4 判异规则术语化（WE/Nelson）；SP5 **Spec 限 vs Control 限**；SP6 OCAP 动作链 | `mds_sampling_plan`、`mds_spc_rule` `_term`、`mds_ocap` `_action` |
| 3.9.11 | 单位与字典 | [uom-dict-design.md](uom-dict-design.md) | UD2 量纲+基本单位+换算（含 offset）；UD3 码表（系统/可扩展）；**UD4 不强推全量字典化**（核心枚举保留 Java enum）；UD5 一致性校验 | `mds_uom` `_uom_conversion` `_code_table` `_code_table_item` |

### P2 · 横切治理能力（决定 MDS 能否「管得住」）

| # | 域 | 文档 | 核心决策 | 关键表 |
| - | -- | ---- | -------- | ------ |
| 3.9.12 | 主数据治理元模型 | [md-governance-design.md](md-governance-design.md) | **MG1 元数据驱动**（域注册一处定义全域适用）；MG2 责任人角色化委派；MG3 审批工作流模板；**MG4 数据质量规则 ≠ 业务约束**；MG5 电子签名不可否认（禁删禁改） | `mds_md_domain` `_steward` `_workflow` `_workflow_step` `_quality_rule` `_signature` |
| 3.9.13 | 主数据分发与订阅 | [md-distribution-design.md](md-distribution-design.md) | MD2 发布—订阅；**MD3 快照+增量两段式（增量源 = `@History`）**；MD4 范围订阅 + 载荷外置；MD6 收敛规则包/点表/SPC 规则等特例 | `mds_md_consumer` `_subscription` `_release` `_release_item` `_delivery` |

> **四条横切层协同**：编码治理（怎么命名）→ 约束治理（什么与什么必须/不得成立）→ 变更治理（改不改、谁批、何时生效）→ 元治理（谁负责、按什么规则管、怎么证明）；分发订阅负责把它们**可靠交付**给下游。
>
> **不重复造轮子（全篇一致原则）**：光罩盒复用 `mds_carrier_type`、光罩库复用 `mds_bank`、文档正文复用 DMS（`doc_ref`）、配方正文复用 `recipe body_ref`、增量复用 `@History`、兼容关系复用既有 `*_compat`/`*_qual` 表。

## 3.10 第二轮补充（总纲 / 横切规范 / P0–P2 缺口，13 篇）

> 第二轮审计发现并补齐：**4 个真缺口域 + 5 处横切设计不全 + 若干既有域深化**。每篇均含 **「与 MES 生产执行衔接」** 专章。

### 总纲与横切规范

| # | 名称 | 文档 | 优先级 | 核心决策 |
| - | ---- | ---- | ------ | -------- |
| 3.10.1 | **主数据蓝图总纲** | [00-blueprint.md](00-blueprint.md) | 总纲 | 主数据域全景（**L0 基础参考 → L5 运维质量**五层）+ 全局关系总图 + **术语表 Glossary** + **编码唯一性策略总表** + **MES 生产执行消费视角（13 场景表）** + 文档地图 + 路线图 |
| 3.10.2 | **表达式 DSL 规范** | [expression-dsl-design.md](expression-dsl-design.md) | **P0** | 被 **5 处**共用（约束 / 路由条件 / 质量规则 / 处置规则 / OCAP）；语法 + 运算符 + **函数与变量注册表** + AST 规范化（供下游免实现解析器）+ 静态校验；**无副作用、求值限额、失败保守阻断** |
| 3.10.3 | **工艺参数字典** | [param-def-design.md](param-def-design.md) | **P0** | **统一 `param_name` 语义**，打通 `step_param` / 设备变量 / 配方参数 / SPC metric / 校准项五处；`mds_param_alias` 承载跨设备/跨系统映射（跨设备聚合的前提） |
| 3.10.4 | **实时态衔接契约** | [realtime-contract-design.md](realtime-contract-design.md) | **P0** | **20 类实时态归属总表**（收敛此前 5 篇各自的 §8 表述）+ **可回写白名单（7 表）** + `mds_realtime_registry` 契约表 + MES 七阶段时序 + 降级策略 + SLA（热路径 ≤50ms 零回查） |
| 3.10.13 | **接口总纲** | [api-design.md](api-design.md) | 贯穿 | **双通道**（订阅 = 生产主通道 / Query+Resolve = 低频）；**12 个解析接口**把跨表拼装收敛为一个调用；**MES 消费接口清单**（按七阶段给频率/可缓存/SLA）；版本与契约测试策略 |

### P0 / P1 / P2 新域

| # | 域 | 文档 | 优先级 | 核心决策 | 关键表 |
| - | -- | ---- | ------ | -------- | ------ |
| 3.10.5 | 测试资产 | [test-asset-design.md](test-asset-design.md) | **P0** | CP/FT 的「**测试侧光罩**」：探针卡 / 负载板 / 测试程序；资格 + 寿命**同 reticle 范式**；盒与库复用 carrier/bank | `mds_probe_card` `mds_load_board` `mds_test_program(_item)` `mds_test_interface_qual` `mds_test_asset_usage_policy` |
| 3.10.6 | 厂务 Utility | [facility-utility-design.md](facility-utility-design.md) | P1 | 设施 + **「设备 × 厂务」接入矩阵**（核心价值）；**影响面自动推导**；`FACILITY_AVAILABLE` 约束；`facility.*` 表达式变量 | `mds_facility` `_param` `mds_facility_connection` |
| 3.10.7 | 批次类型策略 | [lot-type-design.md](lot-type-design.md) | P1 | **四个计数口径**（良率/WIP/OEE/SPC）解决报表失真；混批/拆分/优先级 + `max_ratio` 防 Hot Lot 泛滥 | `mds_lot_type` `mds_priority_class` |
| 3.10.8 | 洁净度与环境 | [cleanliness-env-design.md](cleanliness-env-design.md) | P1 | ISO 14644 洁净等级**落为实体 + 位置字段**（替代原文字描述）；环境监控点；超限影响联动 | `mds_clean_class` `mds_env_monitor_point`（+ `mds_location` 加列） |
| 3.10.9 | 产品结构 BOM | [product-bom-design.md](product-bom-design.md) | P1 | 与**用料 BOM 严格区分**（结构 vs 消耗）；**定义追溯血缘方向**（MES 追溯的前置条件） | `mds_product_bom` `_line` |
| 3.10.10 | APC/FDC 模型 | [apc-fdc-design.md](apc-fdc-design.md) | P2 | 模型与**适用上下文分离**（上下文是核心）；SPC 监控 vs APC 干预的分工 | `mds_apc_model` `_context` `mds_fdc_model` `_input` |
| 3.10.11 | 多工厂策略 | [multi-site-design.md](multi-site-design.md) | P2 | **GLOBAL / FAB / Override 三层**；逐域策略表 + 覆盖字段白名单；`resolveForFab` 派生视图 | `mds_md_scope_policy` `mds_md_localization` |
| 3.10.12 | 工装与夹具 | [tooling-design.md](tooling-design.md) | P2 | CMP 垫 / 修整器 / 模具 / 治具；寿命 + **`REFURBISH` 翻新**；与耗材/测试资产**三分** | `mds_tooling` `mds_tooling_usage_policy` `mds_tooling_qual` |

### 既有域深化（本轮补强）

| 域 | 补强内容 |
| --- | --- |
| [recipe-design](recipe-design.md) | 补 **E40 正文与参数级（DCP）** 建模：`recipe_step` / `recipe_step_param`，使配方从"引用"变为"可展开" |
| [equipment-design](equipment-design.md) | 补 **量化能力**（`mds_equipment_capability_limit`：UPH / 最小线宽 / 精度），支撑工艺可行性筛选与产能估算 |
| [carrier-design](carrier-design.md) | 补 **载具-载具关系**（母/子载具、dummy wafer 使用规则） |

> **本轮闭环的关键悬空点**：① 参数语义五处不统一（参数字典）；② 实时态口径分散（实时态契约）；③ 表达式 5 处各写一套（DSL 规范）；④ 测试段完全缺失（测试资产）；⑤ 厂务影响面靠人工（厂务域）。

## 3.11 第三轮补充（评审驱动：合规、非功能、交付管理）

> 本节来自一次**以 MES/MDS 架构师 + BA + 资深产品**视角的正式评审（核验方式：链接可达性、DDL 字段 diff、跨文档枚举/外键回写、待补统计）。评审发现 **4 项 P0 + 4 项 P1 结构闭环缺陷**与 **3 类合规/完善性缺口**，本批全部修复并补齐。

| # | 名称 | 文档 | 核心内容 |
| - | ---- | ---- | -------- |
| 3.11.1 | **EHS 与安全域** | [ehs-safety-design.md](ehs-safety-design.md) | **补合规硬缺口**（此前 EHS 仅 4 篇零散提及）。危险品台账（GHS/UN/重大危险源/储存限值）+ **化学品相容性矩阵** + 结构化安全要求（PPE/联锁/通风/侦测/消防，含 ISO 45001 控制层级）+ 应急预案；与物料/设施/位置/设备/文档/认证矩阵六向闭环 |
| 3.11.2 | **非功能设计** | [nonfunctional-design.md](nonfunctional-design.md) | 容量估算（150 表分五档，**历史表与分发表是容量主体**）+ 性能指标与索引/缓存/N+1 策略 + 可用性与容灾（RPO≤5min / RTO≤30min、L0–L3 降级分级）+ **数据迁移与初始化**（顺序依赖 + 六步流程 + 种子清单）+ 并发与一致性 + 可观测性 |
| 3.11.3 | **待补清单与 DoD** | [99-backlog.md](99-backlog.md) | 把散在 39 处的 **177 条待补**主题化为 8 个 Epic（T1–T8）+ **每域验收标准 DoD**（通用 10 项 + 域差异）+ 排期建议 + **已修复缺陷回归记录**；并作为 backlog 的**唯一入口** |

**同期修复的结构闭环缺陷**（详见 [99-backlog §5](99-backlog.md)）：

| 级别 | 缺陷 | 修复 |
| --- | --- | --- |
| P0 | `mds_equipment`/`_hist` 在 location-design 与 equipment-design **重复定义且字段不一致**（15 vs 22 列，旧版缺五态分离与型号外键） | location-design 删除重复 DDL，改为指向 equipment-design |
| P0 | `mds_recipe`/`mds_equipment_recipe_qual` 在 equipment-design 与 recipe-design 重复定义且不一致 | equipment-design 删除重复 DDL，改为指向 recipe-design |
| P0 | `param_def_id`（5 张表）与 `clean_class_code`（location）**新增外键未回写被改文档** | 逐个回写字段表 + DDL + FK |
| P0 | 7 个新增**约束类型未登记** constraint-design | 升级为**权威登记表**（15 个扩展类型） |
| P1 | `PROBE_CARD_BOX`/`PROBE_CARD_STOCKER`/`TOOLING_STOCKER` 枚举未回写 | carrier/bank 改为**权威枚举清单** |
| P1 | 表达式命名空间漏 `tooling`/`test_asset`；实时态注册表漏工装 | 已补 |
| P1 | 域数量三种口径（21/26/26） | 建立 **[00-blueprint §1.1 权威域清单](00-blueprint.md)**，四处口径统一指向 |
| P2 | 表名前缀不统一（`equipment_move_event`） | 统一为 `mds_equipment_move_event` |

**同期补强的既有域**：[pm-calibration 附录 B](pm-calibration-design.md)（**计量标准器台账与溯源链**，补 ISO 17025）、[md-governance 附录 A](md-governance-design.md)（**数据保留与归档策略**）。

## 4. 待补章节（后续填充）

| 章节 | 内容 | 状态 |
| --- | --- | --- |
| 业务域与实体 | MDS 主数据模型、与平台通用数据模型（§9）的衔接 | ✅ **39 篇设计文档**，覆盖 **7 块核心主数据**（位置/设备/工艺路线/产品/载具/配方/仓库）+ **工艺流展开层与流程对比**（[process-flow-design.md](process-flow-design.md)）+ 全部扩展域（§3.9 / §3.10 逐域列出，**权威清单见 [00-blueprint §1.1](00-blueprint.md)**：共 26 个业务主数据域 + 2 个基础参考数据域）+ **横切治理**（[编码](naming-rule-design.md) / [约束](constraint-design.md) / [变更](change-mgmt-design.md) / [元治理](md-governance-design.md)）+ **横切规范**（[表达式 DSL](expression-dsl-design.md) / [参数字典](param-def-design.md) / [实时态契约](realtime-contract-design.md)）+ **集成**（[分发订阅](md-distribution-design.md) / [设备接口](equipment-interface-design.md) / [API](api-design.md)）；**总纲见 [00-blueprint.md](00-blueprint.md)** |
| 认证与准入对接 | 验签、JWKS 拉取、`apps` claim 校验 | ✅ 见 §1/§2 |
| 内部 RBAC | 菜单 / 按钮 / 数据权限（平台 §21.1） | 待 M6 |
| API 设计 | 模块接口、版本策略 | ✅ 见 [api-design.md](api-design.md)（双通道、12 个解析接口、MES 消费接口清单、版本与契约测试） |
| 快速开始与部署 | 基于 platform 的启动、部署形态 | 待补 |
