# MDS 待补清单与验收标准（Backlog & DoD）

> 本文是 MDS 全部**待补事项的唯一汇总入口**与**每域验收标准（DoD）**。
>
> **为什么要有这篇**：39 篇文档各有一个「待补 / 后续」节，合计 **177 条**——散在 39 处等于**没有 backlog**，无法排期、无法追踪、必然遗漏。本文把它们**主题化汇总**，并定义**「什么算设计完成」**。
>
> **维护约定（强制）**：任何文档新增待补项，**必须同步在 §1 对应主题下登记**；任何域达到 §2 的 DoD 后，在 §4 的完成度表中标记 ✅。
>
> **2026-09-23 业务覆盖评审**（6/8/12 寸 + 手动/半自动/全自动 + MES 完整执行数据）新增 Epic **T9**，详见 [00-blueprint §8.1](00-blueprint.md)。T9 是**业务口径缺口**，与 T0 结构一致性、T1–T8 实现待办正交。

---

## 0. 使用方式

| 角色 | 怎么用 |
| --- | --- |
| 架构师 | 按 §1 主题排期；§3 是建议顺序 |
| 域负责人 | 按 §2 的 DoD 自检本域是否收口 |
| 迭代管理 | §4 完成度表作为进度看板；§1 主题作为 Epic 拆分依据 |

---

## 1. 待补事项主题化汇总（原 177 条 + T9 新增 18 + T10 新增 9 + T11 新增 4 → **12 个主题**）

> 「来源」列的「等」表示该文档 §8 中还有 1–3 条同类小项未单列；明细以各文档 §8 原文为准。

### T0 设计一致性收口（**阻塞建表，最高优先**，来源：[98-audit-report](98-audit-report.md)）

| # | 事项 | 级别 | 状态 |
| --- | --- | --- | --- |
| ~~T0-1~~ | ~~**业务版本字段 `version` → `revision`**；`version_col` → `version`；唯一约束与正文同步~~ —— **已按方案 B 完成**：25 处 DDL + 26 处字段表 + 12 处唯一约束 + 3 处 `version_col` + 正文/决策表 | 🔴 P0 | ✅ **已完成**（见 [00-blueprint §11.2](00-blueprint.md)） |
| T0-2 | 补孤儿域关联：`mds_product.default_lot_type_id → mds_lot_type(id)`，使 [lot-type-design](lot-type-design.md) 接入闭环。**与 T9-11 合并执行** | 🟠 P1 | ⏸ |
| T0-3 | 各域字段表对**可回写列**加标记（与 [realtime-contract §2](realtime-contract-design.md) 白名单双向对齐） | 🟡 P2 | ⏸ |
| T0-4 | 生命周期序列书写统一为 Unicode `→`（`route` 现用 ASCII 箭头） | 🟡 P2 | ⏸ |
| T0-5 | `mds_md_` 前缀双域共用 → multi-site 改 `mds_site_*` | 🟡 P2 | ⏸ |
| T0-6 | 各域补统一**文档元数据块**（版本/日期/状态）与 MES 消费视角 3–5 行（或在 [00-blueprint §5](00-blueprint.md) 建域→场景反查表） | 🟡 P2 | ⏸ |
| T0-7 | 术语表补「快照 / 增量 / 规则包」等条目；`mds_equipment_consumable`、`mds_equipment_group` 加归属注释 | 🟡 P2 | ⏸ |

### T1 实时态与集成契约（跨域，最高优先）

| # | 事项 | 来源 |
| --- | --- | --- |
| T1-1 | MES/AMHS/EAP 实时态衔接契约**逐域细化**（设备/载具/仓库/配方/工艺流各自的导出订阅格式） | equipment / carrier / bank / recipe / process-flow 等 §8 |
| T1-2 | 白名单回写的**限流与审计报表**、降级运行可观测性 | realtime-contract §12 |
| T1-3 | 集成测试契约：把 `mds_realtime_registry` 转为可执行断言 | 同上 |
| T1-4 | 各域 `resolve*` 解析接口的**返回结构与预热策略** | api-design §6 |

### T2 接口契约实现

| # | 事项 | 来源 |
| --- | --- | --- |
| T2-1 | 各域 **OpenAPI 3.1 规格**并汇总为单一 spec | api-design §6 |
| T2-2 | **Java / TS SDK**（含 DSL 求值 SDK）发布 | api-design §6 / expression-dsl §14 |
| T2-3 | 灰度与旧版接口下线（基于 `_delivery` 的版本分布监控） | api-design §6 |
| T2-4 | 是否引入 GraphQL（管理端联查）的评估 | api-design §6 |

### T3 校验与算法服务

| # | 事项 | 来源 |
| --- | --- | --- |
| T3-1 | **route 图校验服务**（无环除 REWORK、SPLIT/JOIN 配对、起点唯一、可达性） | route §8 |
| T3-2 | **BOM 展开服务**（用料 BOM / 结构 BOM，含损耗与替代组） | material §8 / product-bom §9 |
| T3-3 | **影响面推导服务**（厂务/环境/变更共用依赖图遍历） | facility §4.4 / cleanliness §4.3 / change-mgmt §4.3 |
| T3-4 | 约束 **4 类试算**、Process Flow **4 类对比**的算法落地与用例 | constraint §4.6 / process-flow §4.7 |
| T3-5 | **DSL 求值引擎**与前端可视化条件构建器 | expression-dsl §14 |
| T3-6 | 生成时机编排（命名规则两阶段保存、HIER 需父先落库） | naming-rule §7 |
| T3-7 | 备件/资产 **EOL 与淘汰管理**预警 | material §8 等 |

### T4 数据迁移与种子

| # | 事项 | 来源 |
| --- | --- | --- |
| T4-1 | **存量 `param_name` → `param_def` 回填**（脚本 + 人工确认 + 质量规则持续校验） | param-def §9 |
| T4-2 | **存量 code 校验与回填**（历史手工 code 合规核对，不强制改号） | naming-rule §7 |
| T4-3 | **全量种子数据清单**（码表 / 单位 / 参数 / E10 停机原因 / 缺陷码 / 班次日历 / WE·Nelson 规则 / 域注册 / 审批工作流） | 各域 §5-6「种子」行 |
| T4-4 | 数据迁移与初始化方案（顺序依赖 / 校验 / 回滚 / 切换演练） | nonfunctional-design §4 |

### T5 外部系统集成

| # | 事项 | 来源 |
| --- | --- | --- |
| T5-1 | **DMS**：`doc_ref` 解析契约与版本对齐 | document §8 |
| T5-2 | **外围 Recipe 系统（E40）**：`body_ref` 正文格式与同步机制 | recipe §8 |
| T5-3 | **测试开发系统**：`mds_test_program.body_ref` 集成契约 | test-asset §9 |
| T5-4 | **EMS / FMCS**：环境与厂务实时值读取、超限事件订阅 | cleanliness §9 / facility §9 |
| T5-5 | **CMMS / ERP / WMS**：PM 工单、成本与库存、供应商配额分工 | pm-calibration §8 / material §8 |
| T5-6 | **APC/FDC 系统**：模型正文加载与换版下发 | apc-fdc §9 |
| T5-7 | **平台 M4 mq**：PUSH 通道（Kafka/MQTT/HTTP）落地 | md-distribution §8 |

### T6 管理端与可视化

| # | 事项 | 来源 |
| --- | --- | --- |
| T6-1 | 通用：列表/详情/版本对比/生效窗口管理 | 全域 |
| T6-2 | 规则可视化（DSL 构建器、约束矩阵、冲突/缺口高亮、规则包预览） | constraint §4.6 / expression-dsl §14 |
| T6-3 | 层堆叠剖面视图、光罩/测试资产资格矩阵、寿命台账 | layer §8 / reticle §8 / test-asset §8 |
| T6-4 | 厂务「设备 × 厂务」矩阵与影响面视图、环境监控点地图 | facility §9 / cleanliness §9 |
| T6-5 | 多站点生效视图对比、跨 fab 一致性巡检 | multi-site §9 |

### T7 非功能（→ [nonfunctional-design.md](nonfunctional-design.md)）

| # | 事项 |
| --- | --- |
| T7-1 | 容量与数据量估算、归档策略 |
| T7-2 | 性能指标与索引策略、缓存层次 |
| T7-3 | 可用性与容灾（RPO/RTO）、降级运行 |
| T7-4 | 并发与一致性（乐观锁/序列/批量克隆） |
| T7-5 | 可观测性（指标/日志/追踪/告警） |

### T8 合规深化

| # | 事项 | 来源 |
| --- | --- | --- |
| T8-1 | **EHS/安全域**（危险品台账、化学品相容性、安全要求、应急预案） | [ehs-safety-design.md](ehs-safety-design.md) |
| T8-2 | **计量标准器台账与溯源链**（ISO 17025 / IATF） | pm-calibration 附录 B |
| T8-3 | **数据保留与归档策略**（保留期/归档/销毁/审计） | md-governance 附录 A |
| T8-4 | 多语言：实体 `name` 的 i18n（当前仅码表有 `i18n_json`） | uom-dict §8 / 全域 |
| T8-5 | 隐私与数据分级说明（人员信息最小化已做，需明确分级与留存） | org-personnel §8 / md-governance 附录 A |

### T9 多尺寸与多自动化覆盖（**业务口径缺口，阻塞 6/8 寸与手动厂上线**）

> **来源**：2026-09-23 架构评审。现有文档按 **300mm 全自动前道**写透，向 200mm 外推；**150mm（6 寸）未建模**；自动化被压成 GEM `remote_capable` 一维。T9 **不推翻** L0–L5 分层与「定义 vs 实时态」边界，只补运行体制与断链。
>
> **拍板结论（2026-09-23 已定）**：① `wafer_size` → **开放码表**（值域含 150 且可扩展）；② 运行体制 → **L1 剖面 `OperatingProfile`**（**不是**独立域；**权威是能力位**、作业模式为派生，见 [operating-profile-design.md](operating-profile-design.md)）；③ Boat → **`carrier_type`**（kind=`BOAT`）。
>
> 四项补强（三维正交声明 / 尺寸单一来源 / 域裁剪边界 / 无载具直投与批量工艺）与影响清单见 [00-blueprint §8.2](00-blueprint.md)。**T9 条目现已可改权威枚举与表结构。**

| # | 事项 | 级别 | 状态 | 来源 |
| --- | --- | --- | --- | --- |
| T9-1 | **晶圆尺寸权威码表**：在 [uom-dict](uom-dict-design.md) 登记 `mds_code_table(WAFER_SIZE)`，值域 `100/125/150/200/300` + `450` 预留 + **租户可扩展**；6 域字段统一为 **`wafer_size_code VARCHAR(16)` 码表引用**（**不采用** INT 枚举） | 🔴 P0 | ⏸ | uom-dict / product / carrier / bank / material / test-asset |
| T9-2 | **设备尺寸断链**：设备补尺寸**事实值**（型号级默认 + 设备级覆盖，`mds_equipment_wafer_size` 关联表），使 `WAFER_SIZE_MATCH` 有权威列可求值 | 🔴 P0 | ⏸ | equipment / constraint |
| T9-3 | **载具类型扩展 6/8 寸**：`carrier_type` 增 `OPEN_CASSETTE` / `BOAT` / `MAGAZINE` / `FOSB`（**Boat 已拍板归载体**）；`capacity` 不再默认 25（舟按管位）；增类型级 `slot_map` | 🔴 P0 | ⏸ | carrier |
| T9-4 | **载具槽位定义（非实时占用）**：类型级 `slot_map`（槽位数、编号规则、缺口/notch 方向、空槽策略），支撑 E90 片级与舟位 | 🟠 P1 | ⏸ | carrier / realtime-contract |
| T9-5 | **运行体制剖面 `OperatingProfile`**（[详细设计](operating-profile-design.md)）：挂 `GLOBAL/FAB/AREA/EQUIPMENT/ROUTE_OPERATION` 五作用域，越具体越优先。字段：**能力位**（`supports_host_start`/`ppid_download`/`job_create`/`carrier_id_read`）、`protocol`、`job_exec_mode`（**派生摘要**）、`default_tracking_grain`、`handling_modes[]`、`carrier_required`、`batch_capable`、`wafer_sizes`、`required_txns`、`domain_profile`。复用 multi-site GLOBAL/Override，**不是**平行多厂域 | 🔴 P0 | ⏸ | location / multi-site / equipment |
| T9-6 | **作业模式与派工约束分支**：约束「须 ONLINE-REMOTE 才派工」按 `job_exec_mode` 分支。FULL_AUTO→REMOTE+载具到位；SEMI_AUTO→配方解析+人确认；MANUAL→批次合法+行政态 ACTIVE，**不查 GEM** | 🔴 P0 | ⏸ | constraint / equipment / realtime-contract |
| T9-7 | **无通信设备一等路径**：`protocol` 允许 `NONE` / `BARCODE`；点表对手动机台非必填 | 🟠 P1 | ⏸ | equipment-interface |
| T9-8 | **MES 执行契约解析**：`resolve-exec-contract(equipment\|step)` → `{job_exec_mode, tracking_grain, required_txns[]}`（MANUAL=JobIn/Out；SEMI_AUTO=+operator_confirm；FULL_AUTO=CJ/PJ）。MDS 只声明事务集，**不存运行实例** | 🟠 P1 | ⏸ | api / route / process-flow / realtime-contract |
| T9-9 | **Bank 去 300mm 默认**：`wafer_size` 改为 `wafer_size_code` 且**允许多值或可空**；人工货架/推车（`amhs_enabled=0`）不强制 `bank_slot`/`bank_port` | 🟠 P1 | ⏸ | bank |
| T9-10 | **位置厂级轮廓**：FAB 落实 `fab_type`（前道/后道）；**`wafer_sizes` 不落 location**（单一来源归 [OperatingProfile](operating-profile-design.md)，见 §8.2 补强 ⑤） | 🟠 P1 | ⏸ | location |
| T9-11 | **产品默认批次类型**（与 T0-2 合并执行）：`mds_product.default_lot_type_id → mds_lot_type(id)` | 🟠 P1 | ⏸ | product / lot-type |
| T9-12 | **按剖面裁剪必填域**：APC/FDC、OHT 端口、FOUP E87、光罩资格等对 6 寸功率/模拟厂可关闭。**限定为「域启用开关 + 必填降级」**（复用 [multi-site](multi-site-design.md) `mds_md_localization`），**不得裁剪表结构**且变更留痕——防同一主数据跨 fab 语义漂移 | 🟡 P2 | ⏸ | md-governance / multi-site |
| T9-13 | **Future Hold / 预置动作模板**：原因码之上的动作模板（MDS 定义，MES 执行），避免 MES 硬编码 | 🟡 P2 | ⏸ | reason-defect / constraint |
| T9-14 | **混线 override**：同一 FAB 内 Area/设备/工序可覆盖 `job_exec_mode`（8 寸常态：光刻半自动、湿法手动、炉管舟、量测全自动）；优先级 `GLOBAL<FAB<AREA<EQUIPMENT<ROUTE_OPERATION` | 🟠 P1 | ⏸ | [operating-profile §6](operating-profile-design.md) |
| T9-15 | **`wafer_size` 类型统一**（与 T9-1 同批）：product/carrier/bank(INT) 与 material/test-asset(VARCHAR) **全部改为 `wafer_size_code` 码表引用**，消除 INT/VARCHAR 混用 | 🟡 P2 | ⏸ | 随 T9-1 |
| T9-16 | **能力位集合（权威）**：`supports_host_start` / `supports_ppid_download` / `supports_job_create`(CJ/PJ) / `supports_carrier_id_read`(BCR/RFID) + `protocol`（`SECS_I`/`HSMS`/**`NONE`**/`BARCODE`）。**`job_exec_mode` 由能力位派生**，不得反向覆盖；自洽校验（`protocol=NONE` 时不得置 `host_start=1`） | 🔴 P0 | ⏸ | [operating-profile-design](operating-profile-design.md) |
| T9-17 | **三维正交声明**：`process_mode`（物理架构）× `job_exec_mode`（作业模式）× GEM `Control State`（实时通信态）三者**正交、不可互推**；回写 equipment / operating-profile / realtime-contract 三处，防下游误读 | 🟠 P1 | ⏸ | equipment / operating-profile / realtime-contract |
| T9-18 | **无载具直投与批量工艺**：① `carrier_required=0` 的工序（6 寸湿法/显微检）允许无载具投料，**lot 仍存在**；② 6 寸炉管**一舟 100+ 片**为常态，`BATCHING` 需体现 `batch_capacity` 与混装规则 | 🟠 P1 | ⏸ | realtime-contract / constraint / carrier |

---

### T10 场景走查补充（R1/R2 走查发现的**半导体特有主数据缺口**，9 项）

> **来源**：2026-09-23 R1 轮「业务场景端到端走查」——用**腔体匹配 / pellicle / 晶圆图 / 量测配方**等半导体特有场景反查主数据覆盖度。**这些缺口此前全库检索为 0 命中**，属"业务上真实存在但模型未表达"。

| # | 事项 | 级别 | 状态 | 落点 |
| --- | --- | --- | --- | --- |
| T10-1 | **腔体匹配（Chamber Matching）**：新增 `mds_equipment_chamber_match` + `_member`（匹配组 / 成员 / `chamber_offset` / `match_criteria` / `match_status`）；**失配组不得派工**（约束 `CHAMBER_MATCH_REQUIRED`） | 🔴 P0 | ⏸ | [equipment-design 附录 B](equipment-design.md) |
| T10-2 | **腔体级配方资格**：`mds_equipment_recipe_qual` 增 `module_id`（空 = 设备级；非空 = 腔体级），唯一键调整为 `(equipment_id, module_id, recipe_id, tenant_id, deleted)` | 🔴 P0 | ⏸ | [recipe-design §3.4](recipe-design.md) / equipment 附录 B |
| T10-3 | **Pellicle（光罩保护膜）**：`mds_reticle` 增 `pellicle_type` / `pellicle_transmittance` / `pellicle_install_date` / `pellicle_life_policy_id`；复用 `usage_policy` 增加 pellicle 阈值维度 | 🟠 P1 | ⏸ | [reticle-design 附录 B](reticle-design.md) |
| T10-4 | **晶圆图规格与 Zone**：新增 `mds_wafer_map_spec`（die grid / `edge_exclusion_mm` / notch 方向 / E142）+ `mds_wafer_zone`（RADIAL / RECT）；**实际 map 归 MES/YMS，MDS 只存口径** | 🟠 P1 | ⏸ | [reason-defect-design 附录 A](reason-defect-design.md) |
| T10-5 | **`recipe_class` 权威码表**：`RECIPE_CLASS`（`PROCESS`/`METROLOGY`/`CLEAN`/`CALIBRATION`/`SETUP`/`MAINTENANCE`/`OTHER`），三处 `recipe_class` 统一为码表引用（建议与 T9-1 同批） | 🟡 P2 | ⏸ | [recipe-design 附录 B](recipe-design.md) |
| T10-6 | **`map_spec_id` 挂载点**：产品默认 + 工序级覆盖 | 🟡 P2 | ⏸ | [product-design](product-design.md) / [route-design](route-design.md) |
| T10-7 | **匹配度量与 tool matching 分工**：匹配实测值（均值/方差/极差）归分析系统；跨设备匹配用 `mds_equipment_group`（派工组）而非 chamber match | 🟡 P2 | ⏸ | [equipment-design](equipment-design.md) / [process-flow-design](process-flow-design.md) |
| T10-8 | **封装形式（Package Type）**：新增 `mds_package_type`（`family`/`pin_count`/`ball_pitch`/尺寸/可靠性等级）+ `mds_product.package_type_code`（可空）；FT 派工按封装筛 handler / load board / socket | 🟠 P1 | ⏸ | [product-design 附录 A](product-design.md) |
| T10-9 | **批容量（Batch Size）**：`mds_equipment_capability` 增 `BATCH_SIZE` / `BATCH_SIZE_MIN`（与 `process_mode=BATCH` 配套）；组批校验「装载量 ≤ 载具容量 ≤ 设备批容量」 | 🟠 P1 | ⏸ | [equipment-design 附录 C](equipment-design.md) |

---

### T11 物理约束与污染控制（R3 标准 / 物理约束核验发现）

> **来源**：2026-09-23 R3 轮「行业标准与工艺物理约束」核验。**污染隔离机制已有**（`carrier_type_compat` / `bank_carrier_type_compat` 按区隔离 + 清洗），但**缺两个显式维度**：金属污染等级、湿敏等级。SEMI 标准覆盖良好（E5/E10/E15/E28/E30/E37/E39/E40/E49/E79/E87/E90/E94/E116/E133/E142/E148/E157，共 18 个）。

| # | 事项 | 级别 | 状态 | 落点 |
| --- | --- | --- | --- | --- |
| T11-1 | **金属污染等级（Metal Contamination）**：`mds_location` 增 `contam_class`（`CU` / `NON_CU` / `CLEAN`）；`mds_equipment` / `mds_carrier` 增 `metal_contam_level`；配套约束 `METAL_CONTAM_MATCH`（类型已登记） | 🔴 P0 | ⏸ | location / equipment / carrier / constraint |
| T11-2 | **交叉污染隔离约束**：`CROSS_CONTAMINATION`（不同污染分区不得混用设备与载具）；须与既有 `carrier_type_compat` / `bank_carrier_type_compat` 的"污染隔离"语义**对齐合并**，避免两套并行 | 🟠 P1 | ⏸ | constraint / carrier / bank |
| T11-3 | **湿敏等级（MSL）与开封时限**：`mds_material` 增 `msl_level`；约束 `FLOOR_LIFE`（开封后使用时限，超时须烘烤或报废，与 `Q_TIME` 同族时序约束） | 🟠 P1 | ⏸ | material / constraint |
| T11-4 | **热预算（Thermal Budget）**：多次热过程累积窗口（先进逻辑 GAA 关注）；用 `param_def` + 约束表达，不新建域 | 🟡 P2 | ⏸ | param-def / constraint |

---

## 2. 每域验收标准（DoD）

### 2.1 通用 DoD（所有域必须满足）

| # | 检查项 | 判定 |
| --- | --- | --- |
| D-1 | **边界明确**：MDS 拥有 / 下游拥有的内容逐条列清 | §0 存在且无歧义 |
| D-2 | **闭环映射完整**：所有外键指向真实存在的表，且**被指向方文档已回写该引用** | 双向可查 |
| D-3 | **实体与字段完整**：关键字段类型/约束/枚举齐全 | 表结构可据以建表 |
| D-4 | **DDL 唯一**：本域表**不在其他文档重复定义**；本文不重复定义他人表 | 全域无重复 `CREATE TABLE` |
| D-5 | **平台对齐**：`BaseDefData` / `@History(SNAPSHOT)` / `cimTenantFilter` / T2.5 软删唯一 / `IdGenerator` 五项齐备 | 五项均提及 |
| D-6 | **生命周期与版本**：版本不可变 + `cloneAsNewVersion` + 冻结（适用时） | 语义与全篇一致 |
| D-7 | **MES 衔接**：「与 MES 生产执行衔接」专章存在，含场景/接口/频率/可否缓存 | 章节存在 |
| D-8 | **枚举登记**：本域新增或扩展的枚举/约束类型，**已回写至权威登记处**（`carrier_type`/`bank_type`/`entity_type`/约束类型目录） | 回写可查 |
| D-9 | **待补登记**：本域待补已在 [99-backlog §1](#1-待补事项主题化汇总165-条--8-个主题) 登记 | 有对应条目 |
| D-10 | **种子数据**：本域必需的初始数据已列明 | §5-6 有「种子」行 |

### 2.2 域差异 DoD（按域类型追加）

| 域类型 | 追加要求 |
| --- | --- |
| 有生命周期的域（route/product/recipe/process-flow/constraint/naming-rule/test-program/product-bom/apc-model） | 状态机迁移表 + 非法迁移错误码（`42xxx`）+ 冻结正交说明 |
| 资产类域（equipment/carrier/reticle/test-asset/tooling/bank） | 行政态 vs 实时态分离 + 寿命策略 + 到期动作 |
| 依赖外部资产的域（reticle/test-asset/tooling/recipe） | **资格表**（资产 × 设备）+ 派工解析接口 |
| 参考数据域（uom-dict/param-def） | 量纲/单位一致性校验 + 别名/映射机制 |
| 治理类域（naming/constraint/change/governance/distribution） | 单一来源声明 + 回写约定 + 与其余治理层的协同图 |
| 规范类文档（expression-dsl/realtime-contract/api） | 版本策略 + 兼容承诺 + 下游实现指引 |
| **多尺寸/多自动化（T9）** | `wafer_size` 含 150；设备有尺寸权威字段；`job_exec_mode` 与 GEM Control State **正交**；约束按作业模式分支；无 GEM 设备可生产 |

---

## 3. 优先级与建议排期

| 顺序 | 内容 | 理由 |
| --- | --- | --- |
| **第 0 轮（业务口径）** | **T9-1/T9-2/T9-3/T9-5/T9-6/T9-16**（尺寸码表、设备尺寸、载具扩展、运行体制、派工分支、能力位） | 三项口径**已于 2026-09-23 拍板**（[00-blueprint §8.2](00-blueprint.md)），可直接进入建表 |
| **第 1 轮** | **T4-1/T4-2/T4-3 数据迁移与种子** + **T1-1 实时态细化** + **T9-7/T9-8/T9-9/T9-10/T9-11** | 是"能否上线"的前置；且新 FKs（`param_def_id` 等）必须先回填才能启用 |
| **第 2 轮** | **T3-1/T3-3/T3-4 校验与算法服务** + **T9-4/T9-14** | MES 依赖解析能力；也是约束/对比价值的兑现 |
| **第 3 轮** | **T2-1/T2-2 OpenAPI 与 SDK** | 契约固化，释放并行开发 |
| **第 4 轮** | **T5 外部系统集成** + **T6 管理端** | 依赖前两轮的契约与数据 |
| **第 5 轮** | **T8 合规深化**（EHS / 计量溯源 / 数据留存） | 制度与稽核需要，非阻塞开发 |
| 贯穿 | **T7 非功能** | 见 [nonfunctional-design.md](nonfunctional-design.md) |

---

## 4. 完成度看板

| 阶段 | 域数 | 状态 |
| --- | --- | --- |
| 核心主数据 + 工艺流 + 编码/约束治理 | 10 | ✅ 文档完成，待 §2 DoD 复核 |
| 第一轮扩展域 | 13 | ✅ 同上 |
| 第二轮扩展域与横切规范 | 12 | ✅ 同上 |
| 总纲 / API | 2 | ✅ |
| **待补事项** | **177 条 + T9×15** | 🟡 见 §1 主题 |
| **DoD 全量自检** | 36 篇 | 🟡 待逐域执行 §2 |
| **6/8/12 + 三自动化覆盖** | T9 | 🟡 口径未拍板，设计未落表 |

> **下一步建议**：先对 **10 个核心域**执行 §2.1 的 D-4（DDL 唯一）与 D-8（枚举回写）自检——这两项是**已经发现过缺陷**的地方（见下节 §5），需回归确认。

---

## 5. 已修复缺陷回归记录

| 轮次 | 缺陷 | 修复 |
| --- | --- | --- |
| 2026-09-23 | `mds_equipment`/`_hist` 在 location-design 与 equipment-design 重复定义且字段不一致（15 vs 22 列） | location-design 删除重复 DDL，改为指向 equipment-design ✅ |
| 2026-09-23 | `mds_recipe`/`mds_equipment_recipe_qual` 在 equipment-design 与 recipe-design 重复定义且不一致 | equipment-design 删除重复 DDL，改为指向 recipe-design ✅ |
| 2026-09-23 | 新增外键 `param_def_id`（5 张表）、`clean_class_code`（location）未回写被改文档 | 逐个回写字段表 + DDL + FK 约束 ✅ |
| 2026-09-23 | 7 个新增约束类型未登记 constraint-design | 升级为**权威登记表**（15 个扩展类型）✅ |
| 2026-09-23 | `PROBE_CARD_BOX` / `PROBE_CARD_STOCKER` / `TOOLING_STOCKER` 枚举未回写 | carrier-design / bank-design 改为**权威枚举清单** ✅ |
| 2026-09-23 | 表达式命名空间漏 `tooling`、实时态注册表漏工装 | 已补 ✅ |
| 2026-09-23 | 域数量三种口径（21 / 26 / 26） | 建立 [00-blueprint §1.1](00-blueprint.md) **权威域清单**，全部改为指向 ✅ |
| 2026-09-23 | 表名前缀不统一（`equipment_move_event`） | 统一为 `mds_equipment_move_event` ✅ |
| **2026-09-23（第 2 轮审核）** | **跨域 FK 引用 `(code)` 11 处**——被引用列无单列唯一索引（唯一约束为 `(code,tenant_id,deleted)`），多租户下跨租户误关联风险 | 6 处跨域去 FK 改**逻辑引用**、5 处域内改 `REFERENCES (id)` 并同步字段名 `_code`→`_id`；规范写入 [00-blueprint §10](00-blueprint.md) ✅ |
| 2026-09-23（第 2 轮审核） | **域数量口径漂移 3 处**（`api-design` :212 与 `md-governance` :264/:431 仍为 35/25） | 统一为 36/26 ✅ |
| 2026-09-23（第 2 轮审核） | **`product` 生命周期枚举漏 `IN_REVIEW`/`APPROVED`**，与「与 route 同源」声明矛盾 | 补全为六态 ✅ |
| 2026-09-23（第 2 轮审核） | 引用策略三套混用、无规范 | 建立 [00-blueprint §10 引用字段规范](00-blueprint.md)（域内 FK / 跨域逻辑引用）✅ |
| 2026-09-23（第 2 轮审核） | **`version` 同名异型（27 处业务版本 vs 基类乐观锁）、`BaseRevisionData` 0 继承、`frozenState` 未用** | 建立 [00-blueprint §11 版状态字段规范](00-blueprint.md)；**方案 B 已执行**（见 [00-blueprint §11.2](00-blueprint.md)）✅ |
| **2026-09-23（业务覆盖评审）** | 文档按 300mm 全自动前道写透；**150mm 未入码表**；设备无 `wafer_size`；自动化被压成 `remote_capable`；MES 缺作业模式/片级槽位/无 GEM 路径 | 登记 Epic **T9** + [00-blueprint §8.1](00-blueprint.md) ✅；**三项口径已拍板**（§8.2）→ 新建 [operating-profile-design](operating-profile-design.md)，条目扩至 T9-1…T9-18 ✅ |
| **2026-09-23（T9 拍板落地）** | 三项口径拍板（`WAFER_SIZE` 开放码表 / `OperatingProfile` 剖面 / Boat 归载体）+ 四项补强（三维正交、尺寸单一来源、不裁表、无载具直投）；另发现**乐观锁列名 `version_` 与基类 `BaseDefData.version` 不符（127 处）** | [00-blueprint §8.2](00-blueprint.md) 决策记录；各域「待补」改为已拍板表述；`version_` → `version` 归一 ✅ |
