# MDS 主数据蓝图（总纲）

> 本文是 **MDS-AP 后端设计文档的总入口与总纲**——提供全局视图、统一术语、编码唯一性策略、**MES 生产执行消费视角**与文档地图。
> 各域的详细设计见对应独立文档；本文只做**总纲与索引**，不重复细节。
>
> **阅读顺序建议**：本文 → [README.md](README.md)（域入口与决策摘要）→ 各域 design 文档。

---

## 1. 主数据域全景（分层）

MDS 的主数据不是平铺的，而是**五层**结构。分层决定了引用方向（上层引用下层，下层不反引用上层）与变更影响面：

| 层 | 内容 | 变更影响面 | 文档 |
| --- | --- | --- | --- |
| **L0 基础参考数据** | 单位（UOM）、码表/枚举字典、**工艺参数字典** | 全网（几乎所有域都引用） | [uom-dict-design](uom-dict-design.md)、[param-def-design](param-def-design.md) |
| **L1 空间与组织** | 位置（Site→SubBay）、组织单元、班次与日历、供应商 | 广（设备/文档/审批引用） | [location-design](location-design.md)、[org-personnel-design](org-personnel-design.md) |
| **L2 资产主数据** | 设备（含模块/端口/能力/接口点表）、**载具**、**光罩**、**测试资产**、**工装夹具**、仓库 | 广（工艺与执行都引用） | [equipment-design](equipment-design.md)、[carrier-design](carrier-design.md)、[reticle-design](reticle-design.md)、[test-asset-design](test-asset-design.md)、[tooling-design](tooling-design.md)、[bank-design](bank-design.md) |
| **L3 工艺定义** | 产品、工艺路线（Route/Operation/Flow）、**层 Layer**、工艺流（Process Flow + 参数窗口）、配方（Logic/Physical/PPID） | 极广（改一处影响全产线） | [product-design](product-design.md)、[route-design](route-design.md)、[layer-design](layer-design.md)、[process-flow-design](process-flow-design.md)、[recipe-design](recipe-design.md) |
| **L4 物料与产出** | 物料/耗材、**产品 BOM**、**批次类型与策略** | 中（投料与成本） | [material-design](material-design.md)、[product-bom-design](product-bom-design.md)、[lot-type-design](lot-type-design.md) |
| **L5 运维与质量字典** | PM/校准（含**计量标准器溯源**）、原因码/缺陷码/Bin/处置规则、采样与 SPC/OCAP、**洁净度与环境监控**、**厂务 Utility**、**APC/FDC 模型**、**EHS 与安全** | 中（判定与处置口径） | [pm-calibration-design](pm-calibration-design.md)、[reason-defect-design](reason-defect-design.md)、[sampling-spc-design](sampling-spc-design.md)、[cleanliness-env-design](cleanliness-env-design.md)、[facility-utility-design](facility-utility-design.md)、[apc-fdc-design](apc-fdc-design.md)、[ehs-safety-design](ehs-safety-design.md) |

**跨层治理（横切，贯穿 L0–L5）**：

| 横切层 | 管什么 | 文档 |
| --- | --- | --- |
| 编码治理 | `code` 怎么生成 | [naming-rule-design](naming-rule-design.md) |
| 约束治理 | 什么与什么之间必须/不得成立 | [constraint-design](constraint-design.md) |
| **表达式规范** | 跨域条件表达式怎么写 | [expression-dsl-design](expression-dsl-design.md) |
| 变更治理 | 改不改、谁批、何时生效 | [change-mgmt-design](change-mgmt-design.md) |
| 元治理 | 谁负责、按什么规则管、怎么证明 | [md-governance-design](md-governance-design.md) |
| **分发订阅** | 怎么可靠交付给下游 | [md-distribution-design](md-distribution-design.md) |
| **实时态契约** | 谁拥有实时态、怎么衔接 | [realtime-contract-design](realtime-contract-design.md) |
| **多工厂策略** | 哪些全局共享、哪些 fab 本地 | [multi-site-design](multi-site-design.md) |
| 接口 | 对外 API 契约 | [api-design](api-design.md) |

### 1.1 主数据域清单（权威，唯一来源）

> ⚠️ **本表是全域清单的单一来源**。任何新增 / 合并 / 拆分域，**必须回写本表**；其他文档引用域数量时应**指向本表**，不得各自表述（历史上曾出现"21 个域 / 26 个扩展域 / 26 个主数据域"三种口径，已统一至此）。

| 类别 | 数量 | 内容 |
| --- | --- | --- |
| **业务主数据域** | **26** | 位置、设备、工艺路线、产品、载具、配方、仓库、**工艺流**（工艺展开层）、物料耗材、层、光罩、**测试资产**、**工装夹具**、设备接口点表、PM/校准、原因码与处置、组织/人员/日历、受控文档、采样与 SPC、**批次类型策略**、**洁净度与环境**、**产品结构 BOM**、**厂务 Utility**、**APC/FDC 模型**、**多工厂策略**（策略型域）、**EHS 与安全** |
| 基础参考数据域 | 2 | 单位与字典（[uom-dict-design](uom-dict-design.md)）、工艺参数字典（[param-def-design](param-def-design.md)） |
| 横切治理层 | 4 | 编码治理（[naming-rule](naming-rule-design.md)）、约束治理（[constraint](constraint-design.md)）、变更治理（[change-mgmt](change-mgmt-design.md)）、元治理（[md-governance](md-governance-design.md)） |
| 横切规范与契约 | 2 | 表达式 DSL（[expression-dsl](expression-dsl-design.md)）、实时态契约（[realtime-contract](realtime-contract-design.md)） |
| 集成与接口 | 2 | 主数据分发与订阅（[md-distribution](md-distribution-design.md)）、接口总纲（[api-design](api-design.md)） |
| 交付管理 | 2 | 待补清单与验收标准（[99-backlog](99-backlog.md)）、非功能设计（[nonfunctional-design](nonfunctional-design.md)） |
| 总纲 | 1 | 主数据蓝图（本文） |
| **合计** | **39** | = 39 篇 design 文档（本目录 README 另计） |

> **域注册（[md-governance §3.1](md-governance-design.md) `mds_md_domain`）的种子以本表为准**：**36 个域**（26 + 2 + 4 + 2 + 2）＋交付管理与总纲不入域。四处口径（本表 / README / api-design / md-governance 种子）**已对齐**。

---

## 2. 全局关系总图

```
                      ┌───────────────── L0 基础参考数据 ─────────────────┐
                      │ mds_uom / mds_code_table / mds_param_def          │
                      └───────────▲──────────────▲───────────────▲──────┘
                                  │ 被全域引用    │               │
   ┌──── L1 空间与组织 ────┐      │              │               │
   │ mds_location(树)       │      │              │               │
   │ mds_org_unit / vendor  │      │              │               │
   │ mds_work_calendar/shift│      │              │               │
   └───────▲────────────────┘      │              │               │
           │ location_id           │              │               │
   ┌───────┴──── L2 资产主数据 ────┴──────────────┴───────────────┴────┐
   │ mds_equipment ─< module / port / capability / tech_node / owner     │
   │        │        └< mds_equip_if_definition(points)                  │
   │        ├── mds_equipment_group ─< member          (派工设备组)      │
   │        └── mds_equipment_recipe_qual (设备↔配方资格 + PPID)         │
   │ mds_carrier_type ─< mds_carrier / _compat / _owner                  │
   │ mds_reticle_set ─< mds_reticle ─< _qual / _layer / _usage_policy    │
   │ mds_probe_card / mds_load_board / mds_test_program ─< _qual         │
   │ mds_tooling                                                          │
   │ mds_bank ─< bank_port / bank_slot / carrier_type_compat             │
   │ mds_pm_plan / mds_calibration_spec          (运维触发源)            │
   │ mds_facility ─< mds_facility_connection     (厂务与设备接入)        │
   └───────▲──────────────────────────────────────────────▲────────────┘
           │ equipment_class_id / equipment_group_id       │ process/recipe
   ┌───────┴──────────── L3 工艺定义 ─────────────────────┴─────────────┐
   │ mds_product_family ─< mds_product ─< mds_product_route              │
   │ mds_layer ─< mds_layer_operation                                    │
   │ mds_route ─< mds_route_operation ─< mds_route_flow (节点-边图)      │
   │      └── mds_process (工艺规范)                                      │
   │ mds_process_flow ─< mds_process_flow_step ─< _recipe / _step_param  │
   │ mds_recipe_group ─< mds_logic_recipe ─< mds_recipe (物理配方)        │
   │ mds_sampling_plan ─< mds_spc_rule ─< mds_ocap ─< _action             │
   │ mds_apc_model / mds_fdc_model                                        │
   └───────▲─────────────────────────────────────────────────────────────┘
           │ 投料/产出定义
   ┌───────┴──────────── L4 物料与产出 ──────────────────────────────────┐
   │ mds_material ─< _spec / _life_policy / _alternate                   │
   │ mds_material_bom ─< _bom_line     (用料清单: 工序/配方/产品)         │
   │ mds_product_bom  ─< _bom_line     (产品结构: 封装/组装)              │
   │ mds_lot_type / mds_priority_class                                   │
   └───────▲─────────────────────────────────────────────────────────────┘
           │ 判定与处置
   ┌───────┴──── L5 运维与质量字典 ──────────────────────────────────────┐
   │ mds_reason_code / mds_defect_code / mds_bin_code                     │
   │ mds_disposition_rule ─< _item                                        │
   │ mds_clean_class ─< mds_env_monitor_point                             │
   └──────────────────────────────────────────────────────────────────────┘

   横切：mds_naming_rule* / mds_constraint* / mds_expr_function|variable /
         mds_ecr|ecn* / mds_md_domain|steward|workflow|quality_rule|signature /
         mds_md_consumer|subscription|release|delivery / mds_realtime_registry
```

---

## 3. 术语表（Glossary，跨域统一）

| 术语 | 统一定义 | 出现于 |
| --- | --- | --- |
| **定义（Definition）** | MDS 拥有的、跨 lot 共享的静态知识 | 全篇 |
| **实时态（Runtime State）** | 秒/分级变化的状态，MDS 一律不存 | [realtime-contract-design](realtime-contract-design.md) |
| **资产态（Asset Status）** | 缓慢变化的资产级状态（如载具洁净态），MDS 可存可回写 | [carrier-design](carrier-design.md) |
| **行政态（Admin Status）** | 由 MDS 拥有、人工/规则驱动的状态（设备生命期态、腔体态、光罩态） | [equipment-design](equipment-design.md) |
| **资格（Qualification）** | 「资产 × 设备」的合格关系，带状态与有效期 | 配方/光罩/**测试资产** 三处同范式 |
| **兼容（Compatibility）** | 「类型 × 区域/设备类」的可进入/可搭配关系 | 载具/仓库存点 |
| **绑定（Binding）** | 把规则/文档挂到具体实例上 | 约束/文档 |
| **版本不可变（Immutable on RELEASED）** | 发布后内容不可改，改须 `cloneAsNewVersion` | 路线/产品/配方/工艺流 |
| **冻结（Freeze）** | 叠加在已发布版本上的组织级写锁，与生命周期态正交 | 路线/产品/配方/工艺流/约束 |
| **生效窗口（Effective Window）** | SCD2-lite：`effective_from/to` | 全篇 |
| **绑定作用域（Scope）** | `scope_type` + `scope_ref`，越具体越优先 | 约束/文档/处置规则/采样 |
| **横切治理层** | 不成业务域、而是约束所有域的治理能力 | 编码/约束/变更/元治理 |
| **派工（Dispatch）** | MES 选具体设备并下发 | [realtime-contract-design](realtime-contract-design.md) |
| **组批（Batch Formation）** | 把多个 lot 合并成一批执行 | [lot-type-design](lot-type-design.md) |
| **Move-in / Move-out** | lot 上机 / 下机的执行事件 | [realtime-contract-design](realtime-contract-design.md) |
| **Q-Time** | 两工序之间的最大等待时间，超时返工/报废 | [constraint-design](constraint-design.md) |
| **Genealogy / 追溯** | 投入-产出-设备的血缘关系 | [product-bom-design](product-bom-design.md) / [material-design](material-design.md) |
| **PPID** | SEMI E40 的 Process Program ID | [recipe-design](recipe-design.md) |
| **点表（Point List）** | 设备通信数据点定义（SV/DV/EC/CEID/ALID/RPTID） | [equipment-interface-design](equipment-interface-design.md) |
| **OCAP** | 失控响应计划（动作链） | [sampling-spc-design](sampling-spc-design.md) |
| **Spec 限 / Control 限** | 合格线（USL/LSL） / 统计失控线（X̄±3σ） | [sampling-spc-design](sampling-spc-design.md) |
| **处置（Disposition）** | 不合格品去向（放行/扣留/返工/报废） | [reason-defect-design](reason-defect-design.md) |
| **约束 vs 处置** | 约束管「可否」（事前），处置管「怎么办」（事后） | 两篇 |
| **规则包（Rule Pack）** | 编译后的只读规则快照，供下游缓存 | [md-distribution-design](md-distribution-design.md) |

---

## 4. 编码（code）唯一性策略总表

统一原则：**唯一键一律含 `(..., tenant_id, deleted)`**（平台 T2.5 软删唯一）；**默认租户内唯一**；版本型实体按 `(code, revision)` 唯一。

| 域 | 唯一约束 | 唯一范围 | 备注 |
| --- | --- | --- | --- |
| 位置 | `(code, tenant_id, deleted)` | 租户内**全局** | 推荐分层点分码（location D5） |
| 设备 | `(code, tenant_id, deleted)` | 租户内**全局** | 建议含 FAB 前缀（naming-rule §4.3） |
| 载具类型 / 载具 | `(code, …)` | 租户内全局 | 载具条码通常由外部标签系统给定 |
| 配方（逻辑/物理） | `(code, revision, …)` | 租户内 + 版本 | RELEASED 后版本不可变 |
| 路线 | `(route_code, revision, …)` | 租户内 + 版本 | 同上 |
| 产品 | `(product_code, revision, …)` | 租户内 + 版本 | 同上 |
| 产品族 | `(code, …)` | 租户内全局 | |
| 仓库 / 槽位 / 端口 | `(code, …)` | 租户内全局；槽位在库内唯一 | |
| 工艺流 | `(route_id, scenario_code, fab_code, revision, …)` | 路线内 + 方案 + 版本 | |
| 物料 | `(code, …)` | 租户内全局 | 物料批次号另按 `entity_type=MATERIAL_LOT` |
| 光罩 / 套件 | `(code, …)` | 租户内全局 | 建议内嵌层码 |
| 测试资产（探针卡等） | `(code, …)` | 租户内全局 | 同光罩范式 |
| 层 | `(code, tech_node, …)` | 租户内 + 节点 | |
| 原因码 | `(code, reason_type, …)` | 租户内 + 类型 | |
| 缺陷码 / Bin | `(code, …)` / `(bin_no, …)` | 租户内全局 | |
| 参数定义 | `(code, …)` | 租户内全局 | 别名表另行约束 |
| 批次类型 | `(code, …)` | 租户内全局 | |
| 厂务设施 | `(code, …)` | 租户内全局 | |
| 洁净等级 | `(code, …)` | 租户内全局 | |
| 编码规则 | `(code, …)` | 租户内全局 | |
| 约束 | `(code, …)` | 租户内全局 | |
| ECR / ECN | `(code, …)` | 租户内全局 | 单号用 `mds_naming_seq` |
| 发布批次 | `(release_code, …)` / `(domain, scope, revision, …)` | 租户内全局 | |

> **多工厂补充**：跨 fab 共享的实体在同一租户内**只有一份**，fab 差异用「本地覆盖」表达（[multi-site-design](multi-site-design.md)）——不再为每个 fab 复制 code。

---

## 5. MES 生产执行消费视角（核心）

> 本节回答：**MES 在生产的每一个环节，究竟要从 MDS 拿什么、怎么拿、拿了干什么**。
> 原则：**热路径不回查 MDS**——MES 启动时拉**快照**、运行中接收**增量**（[md-distribution-design](md-distribution-design.md)），本地缓存内求值。

| # | MES 执行场景 | 需要的主数据 | 来源域 | 获取方式 | 触发/卡点 |
| --- | --- | --- | --- | --- | --- |
| 1 | **投料 lot release** | 产品、`tech_node`、可用路线、工艺流参数窗口、用料 BOM、**批次类型策略**、工艺规范、路线生效窗口 | product / route / process-flow / material / **lot-type** / constraint | 订阅快照 | `CAPABILITY` `WINDOW` `COMPATIBILITY` |
| 2 | **选路线 route selection** | 产品↔路线（默认+备选）、路线状态与版本 | product / route | 订阅 | `WINDOW`（生效期） |
| 3 | **组批 batch formation** | 设备 `process_mode`、组批上限、**混批策略**、批次类型优先级 | equipment / constraint / **lot-type** | 订阅 | `BATCHING` |
| 4 | **派工 dispatch** | 设备组与成员、设备能力/工艺节点/尺寸、配方资格与 **PPID**、**光罩资格与寿命**、**测试资产资格与寿命**、设备日历与**认证矩阵**、控制模式、工艺参数窗口、**厂务就绪** | equipment / recipe / reticle / **test-asset** / org / equipment-interface / param-def / **facility-utility** | 订阅（**热路径本地求值**） | `COMPATIBILITY` `CAPABILITY` `CAPACITY` `WINDOW` `PM_INTERVAL` `OPERATOR_QUALIFIED` |
| 5 | **搬运 carrier assignment** | 载具类型兼容（可进区域）、归属库、库容、载具行政/洁净态 | carrier / bank / constraint | 订阅 | `CARRIER_AREA_COMPAT` |
| 6 | **上机/下机 move-in / out** | 设备点表（变量与事件）、PPID 下发、工艺参数窗口、Q-Time 起点 | equipment-interface / recipe / process-flow / constraint | 订阅 | `TIMING` `QUALITY` |
| 7 | **过程监控与判异** | **参数定义**（统一语义）、采样计划、SPC 判异规则、OCAP 动作链 | **param-def** / sampling-spc | 订阅 | `SPC_OOC_HOLD` |
| 8 | **完工/放行/扣留** | 处置规则、原因码、缺陷码/Bin | reason-defect | 订阅 | `DISPOSITION_TRIGGER` |
| 9 | **Q-Time 计时与超时** | 边上 Q-Time 约束与动作 | constraint | 订阅 | `TIMING` |
| 10 | **保养/校准联动** | PM 计划与到期、校准规格、`nextDueAt` | pm-calibration | 订阅 + 按需查询 | `PM_INTERVAL` |
| 11 | **环境合规** | 洁净等级、环境监控点与限值 | **cleanliness-env** | 订阅 | 与区域联动 |
| 12 | **先进控制** | APC/FDC 模型与适用上下文 | **apc-fdc** | 订阅 | 下发 APC 系统 |
| 13 | **追溯 genealogy** | 层、光罩、投入物料批次、载具、产品结构 BOM | layer / reticle / material / carrier / **product-bom** | **按需查询**（低频） | — |

**结论**：场景 1–5 是**高频热路径**（必须本地缓存）；场景 6–12 是**事件驱动**；场景 13 是**低频查询**。详细时序、回写白名单与降级策略见 [realtime-contract-design](realtime-contract-design.md)。

---

## 6. 横切层的依赖关系

```
                    ┌──────────── 元治理（域注册/责任人/审批模板/质量规则/签名）────────────┐
                    │  ◄── 提供「职责边界」给下面全部                                        │
                    ▼                                                                       │
   编码治理 ──┐                                                                              │
   约束治理 ──┼──► 变更治理（ECR→影响分析→审批→ECN→触发各域 cloneAsNewVersion）◄─────────────┘
   表达式规范 ┘        │
   （供上三者 + 路由条件 + 质量规则 + 处置规则 + OCAP 共用）│
                       ▼
                  分发订阅（快照 + 增量 → MES/EAP/AMHS/SPC/门户）
                       ▲
                  实时态契约（定义谁拥有实时态、何时衔接、可否回写）
```

- **表达式规范**被 **5 处**引用，是最底层的横切规范；
- **变更治理**是唯一"写入触发方"，把治理意图落到各域版本；
- **分发订阅**是唯一"输出通道"，把主数据交给下游；
- **实时态契约**是"边界声明"，防止 MDS 被要求存实时态。

---

## 7. 文档地图

| 组 | 文档 |
| --- | --- |
| 总纲 | **00-blueprint.md（本文）**、[README.md](README.md) |
| L0 基础参考 | [uom-dict-design](uom-dict-design.md)、[param-def-design](param-def-design.md) |
| L1 空间与组织 | [location-design](location-design.md)、[org-personnel-design](org-personnel-design.md) |
| L2 资产 | [equipment-design](equipment-design.md)、[carrier-design](carrier-design.md)、[reticle-design](reticle-design.md)、[test-asset-design](test-asset-design.md)、[tooling-design](tooling-design.md)、[bank-design](bank-design.md) |
| L3 工艺定义 | [product-design](product-design.md)、[route-design](route-design.md)、[layer-design](layer-design.md)、[process-flow-design](process-flow-design.md)、[recipe-design](recipe-design.md) |
| L4 物料与产出 | [material-design](material-design.md)、[product-bom-design](product-bom-design.md)、[lot-type-design](lot-type-design.md) |
| L5 运维与质量 | [pm-calibration-design](pm-calibration-design.md)、[reason-defect-design](reason-defect-design.md)、[sampling-spc-design](sampling-spc-design.md)、[cleanliness-env-design](cleanliness-env-design.md)、[facility-utility-design](facility-utility-design.md)、[apc-fdc-design](apc-fdc-design.md)、[ehs-safety-design](ehs-safety-design.md) |
| 横切治理 | [naming-rule-design](naming-rule-design.md)、[constraint-design](constraint-design.md)、[expression-dsl-design](expression-dsl-design.md)、[change-mgmt-design](change-mgmt-design.md)、[md-governance-design](md-governance-design.md) |
| 集成与接口 | [equipment-interface-design](equipment-interface-design.md)、[md-distribution-design](md-distribution-design.md)、[realtime-contract-design](realtime-contract-design.md)、[multi-site-design](multi-site-design.md)、[api-design](api-design.md) |
| 辅助 | [document-design](document-design.md) |
| 交付管理 | [99-backlog](99-backlog.md)（待补与 DoD）、[nonfunctional-design](nonfunctional-design.md)（非功能设计）、[98-audit-report](98-audit-report.md)（**审核报告**：五轮次核验结论与缺陷总表） |

---

## 8. 缺口与路线图

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| 第一轮 | 7 核心主数据 + 工艺流 + 编码/约束治理 | ✅ |
| 第二轮 | 13 个扩展域（物料…分发订阅）+ 四条横切层 | ✅ |
| 第三轮 | 总纲 + DSL 规范 + 参数字典 + 实时态契约 + 测试资产 + 厂务 + 批次类型 + 洁净度 + 产品 BOM + APC/FDC + 多工厂 + 工装 + API | ✅（本文档集） |
| 第四轮（待办） | 各域 API 细化实现契约、route 图校验服务、MES 侧 `lot_route_instance` 等运行态模型、平台 M4 剩余（i18n/cache/限流幂等/mq） | 🟡 |

**仍明确未建（有意留待）**：
- 固定资产/折旧（归 ERP）；
- 成本与供应商配额（归 ERP）；
- 人员培训记录（归 HR/培训系统）；
- 包装出货规格明细（视是否含封测段）；
- 数据归档保留策略（归元治理的扩展）。

---

## 9. 与 MES 衔接的总体约定（重申）

1. **主数据向下、实时态向上分界清晰**：MDS 出定义与规则，MES 出实例与实绩（[realtime-contract-design](realtime-contract-design.md)）。
2. **热路径零回查**：派工/投料本地缓存求值，版本号比对触发刷新。
3. **规则即数据**：约束/处置/SPC/质量规则全部是 MDS 数据，MES 用统一求值引擎（[expression-dsl-design](expression-dsl-design.md)）解释，**改规则不发版**。
4. **变更可追溯**：任一版本可回答「哪张 ECN 授权了它」（[change-mgmt-design](change-mgmt-design.md)）。
5. **降级可控**：MDS 不可用时，MES 用最后一份快照继续生产并告警（[realtime-contract-design](realtime-contract-design.md) §6）。

---

## 10. 引用字段规范（域内 FK / 跨域逻辑引用）

**背景**：全库曾出现三种引用风格混用——`FOREIGN KEY … REFERENCES …(id)` 187 处、`REFERENCES …(code)` 11 处、以及「`*_code` 逻辑引用 + 服务层校验」30+ 处。其中 `REFERENCES …(code)` 与规范冲突，**已于本轮清零**（详见 [98-audit-report P0-2](98-audit-report.md)）。

**规范（强制）**：

| 场景 | 写法 | 参照完整性 | 示例 |
| --- | --- | --- | --- |
| **域内引用**（同域父子/主从表） | `xxx_id` + `FOREIGN KEY … REFERENCES mds_yyy (id)` | **DB 级 FK** | `mds_route_operation.route_id → mds_route(id)` |
| **跨域引用**（跨主数据域） | `xxx_code` **逻辑引用**，**不建 FK** | **服务层校验**（`XxxValidator`） | `mds_route_operation.area_code → mds_location.code` |
| **跨域引用（值语义为码）** | `xxx_code` / `xxx_no` 逻辑引用 | 服务层校验 | `bin_no`、`ppid`、`uom code` |

**禁止** `REFERENCES mds_yyy (code)`，原因有二：

1. 被引用表的唯一约束是 `(code, tenant_id, deleted)`（平台 T2.5），**单列 `code` 上没有唯一索引**，且 FK 不带租户过滤 → 多租户下存在**跨租户误关联**风险；
2. T2.5 软删唯一使「单列 code 唯一索引」不可用（软删后 code 仍被占用，无法重建同码），因此无法通过加索引规避。

**跨域为何用逻辑引用而非 FK**：MDS 各域按 AP 拆分、可独立分库与演进，DB 级 FK 会形成**跨域强耦合**，与「业务层可独立部署」的目标冲突（对齐[多工厂策略](multi-site-design.md)与[分发订阅](md-distribution-design.md)）。

---

## 11. 版本、状态与冻结字段规范

### 11.1 三套「版本」的区分（对齐平台 T2.7 三层版本语义）

| 概念 | 字段 | 类型 | 归属 | MDS 约定 |
| --- | --- | --- | --- | --- |
| 行级**乐观锁** | `version` | BIGINT | 基类 `BaseDefData` | **继承即得，不在业务表 DDL 中重复定义**；并发冲突重试 |
| **业务版本** | `revision` | VARCHAR(16) | 业务表 | 发布/归档驱动；`RELEASED` 后不可变，改须 `cloneAsNewVersion` |
| **生命周期态** | `status` | VARCHAR(16) | 业务表 | 域自定义取值域（如 route 六态）；语义对应平台 `activeState` |
| **冻结态** | `is_frozen` | BIT | 业务表 | 平台 `frozenState` 的布尔简化；与 `status` **正交** |
| **变更流水** | `{entity}_hist` | — | `@History(SNAPSHOT)` | 自动生成，含 `change_set_json` |

### 11.2 ✅ 已执行：`version` 同名异型改造（方案 B，2026-09-23）

**改造前实测缺陷**：25 处主表用 `version VARCHAR(16)` 表达**业务版本**，与基类 `BaseDefData.version`（BIGINT 乐观锁）**同名异型**；另有 3 处自造字段名 `version_col`；`naming-rule-design` 同文档内自相矛盾。

**已执行改造（方案 B —— 命名对齐、不动基类）**：

| 动作 | 范围 | 结果 |
| --- | --- | --- |
| 业务版本字段 `version VARCHAR(16)` → **`revision VARCHAR(16)`** | 25 处 DDL + 26 处字段表 | ✅ |
| 自造 `version_col` → **`version`**（归还给乐观锁） | 3 处（`mds_naming_seq` / `mds_logic_recipe` / `mds_recipe`） | ✅ |
| 唯一约束 `(…, version, …)` → `(…, revision, …)` | 12 处 | ✅ |
| ECN 的 `from_version`/`to_version` → `from_revision`/`to_revision` | 2 处 | ✅ |
| 正文引用与决策表同步 | route / document / product / process-flow / equipment-interface / expression-dsl / md-distribution / README | ✅ |

**改造后语义**：`version`（BIGINT）**唯一表示行级乐观锁**，由基类 `BaseDefData` 提供；`revision`（VARCHAR）**唯一表示业务版本**。两者在任一表中不再同名异型。

**方案 A（严格对齐平台，未采用）**：多版本主数据改继承 `BaseRevisionData`，用 `activeState`/`frozenState`/`archiveState` 替换 `status`/`is_frozen`。**未采用原因**：与 MDS 现有「单向六态生命周期」语义不完全吻合，且改造面更大；`status`/`is_frozen` 已在 §11.1 声明为**有意简化**。

**由于该改造会**改变对外接口字段名（下游 MES/EAP 消费方需同步），**属变更受控事项**——执行前须经 [change-mgmt-design](change-mgmt-design.md) 影响分析，清单见 [98-audit-report §P0-1](98-audit-report.md)。
