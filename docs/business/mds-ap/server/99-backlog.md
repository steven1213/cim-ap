# MDS 待补清单与验收标准（Backlog & DoD）

> 本文是 MDS 全部**待补事项的唯一汇总入口**与**每域验收标准（DoD）**。
>
> **为什么要有这篇**：39 篇文档各有一个「待补 / 后续」节，合计 **177 条**——散在 39 处等于**没有 backlog**，无法排期、无法追踪、必然遗漏。本文把它们**主题化汇总**，并定义**「什么算设计完成」**。
>
> **维护约定（强制）**：任何文档新增待补项，**必须同步在 §1 对应主题下登记**；任何域达到 §2 的 DoD 后，在 §4 的完成度表中标记 ✅。

---

## 0. 使用方式

| 角色 | 怎么用 |
| --- | --- |
| 架构师 | 按 §1 主题排期；§3 是建议顺序 |
| 域负责人 | 按 §2 的 DoD 自检本域是否收口 |
| 迭代管理 | §4 完成度表作为进度看板；§1 主题作为 Epic 拆分依据 |

---

## 1. 待补事项主题化汇总（177 条 → 8 个主题）

> 「来源」列的「等」表示该文档 §8 中还有 1–3 条同类小项未单列；明细以各文档 §8 原文为准。

### T0 设计一致性收口（**阻塞建表，最高优先**，来源：[98-audit-report](98-audit-report.md)）

| # | 事项 | 级别 | 状态 |
| --- | --- | --- | --- |
| ~~T0-1~~ | ~~**业务版本字段 `version` → `revision`**；`version_col` → `version`；唯一约束与正文同步~~ —— **已按方案 B 完成**：25 处 DDL + 26 处字段表 + 12 处唯一约束 + 3 处 `version_col` + 正文/决策表 | 🔴 P0 | ✅ **已完成**（见 [00-blueprint §11.2](00-blueprint.md)） |
| T0-2 | 补孤儿域关联：`mds_product.default_lot_type_id → mds_lot_type(id)`，使 [lot-type-design](lot-type-design.md) 接入闭环 | 🟠 P1 | ⏸ |
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

---

## 3. 优先级与建议排期

| 顺序 | 内容 | 理由 |
| --- | --- | --- |
| **第 1 轮** | **T4-1/T4-2/T4-3 数据迁移与种子** + **T1-1 实时态细化** | 是"能否上线"的前置；且新 FKs（`param_def_id` 等）必须先回填才能启用 |
| **第 2 轮** | **T3-1/T3-3/T3-4 校验与算法服务** | MES 依赖解析能力；也是约束/对比价值的兑现 |
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
| **待补事项** | **177 条** | 🟡 见 §1 主题 |
| **DoD 全量自检** | 36 篇 | 🟡 待逐域执行 §2 |

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
| 2026-09-23（第 2 轮审核） | **`version` 同名异型（27 处业务版本 vs 基类乐观锁）、`BaseRevisionData` 0 继承、`frozenState` 未用** | 建立 [00-blueprint §11 版状态字段规范](00-blueprint.md) + 两套方案 ⏸ **待决策** |
