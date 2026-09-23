# MDS 实时态衔接契约（Realtime Contract）设计

> 本文是**跨域统一的实时态衔接契约**——把此前散落在各域 §8 的「MES/AMHS/EAP 实时态衔接契约待补」收敛为一篇。
> 涉及：设备 E10/控制模式/端口态、载具传输态、光槽位、lot 运行态、环境与厂务实时值。
>
> **为什么必须统一**：至少 5 篇文档（设备 / 载具 / 仓库 / 配方 / 工艺流）各自写过一段"实时态归下游"，措辞与边界不完全一致 → 下游按不同理解实现，必然出现"两边都以为对方在维护"或"两边都不敢维护"的空洞。
>
> 设计原则：**MDS 只拥有定义、行政态与资产态；一切秒/分级实时态归 MES/EAP/AMHS/FMCS**。

---

## 0. 定位与边界（拍板）

### 0.1 本文解决什么

| 问题 | 本文给出的答案 |
| --- | --- |
| 哪些状态归 MDS、哪些归下游？ | §1 **实时态分类总表**（逐类明确拥有者与存储） |
| 下游能否回写 MDS？ | §2 **可回写白名单**（只有缓慢变化的资产级态可回写） |
| 下游怎么知道谁该维护什么？ | §3 **实时态注册表 `mds_realtime_registry`**（MDS 发布契约） |
| MES 在每个执行阶段要什么、给什么？ | §4 **七大执行阶段时序** |
| 数据不一致/系统不可用怎么办？ | §6 **一致性、降级与失败策略** |
| 性能怎么保证？ | §7 **SLA 与性能约定** |

### 0.2 唯一的铁律

> **MDS 永远不存秒/分级的实时态实例。** 唯一例外是 §2 白名单中的「**资产级缓慢变化态**」——它们由 MDS 拥有语义、下游只是观测回写。

---

## 1. 实时态分类总表

| # | 实时态类别 | 典型取值 | 拥有者 | 存储 | 更新频率 | 依据 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 设备 E10 运行态（设备级） | `PRD`/`IDL`/`SBY`/`UDN`… | **MES/EAP** | MES | 秒–分 | [equipment-design §3.11](equipment-design.md) |
| 2 | 腔体 E10 运行态 | 同上（腔体粒度） | **MES/EAP** | MES | 秒–分 | 同上 |
| 3 | 通信/控制模式 | `OFFLINE`/`ONLINE-REMOTE` | **EAP** | EAP | 中–高 | [equipment-design §3.12](equipment-design.md) |
| 4 | 端口态（E157） | `NOTREADY`/`TRANSFERRING` | **EAP/MES** | EAP | 秒 | [equipment-design §3.14.5](equipment-design.md) |
| 5 | 载具传输态 / 位置 | 在机 / 在库 / 搬运中 | **AMHS** | AMHS | 秒 | [carrier-design §4.2](carrier-design.md) |
| 6 | 仓库槽位占用 | 某槽位的 carrier | **AMHS** | AMHS | 秒 | [bank-design §4.3](bank-design.md) |
| 7 | lot 运行态 | 当前 step / WIP / 已走 flow | **MES** | MES | 秒–分 | [route-design §0](route-design.md) |
| 8 | 配方当前活跃 PPID | 设备当前装载的 PPID | **EAP/MES** | EAP | 分 | [recipe-design §4.3](recipe-design.md) |
| 9 | 光罩在机/使用计数 | `exposures_used` | **EAP/MES** | EAP | 每次曝光 | [reticle-design §4.5](reticle-design.md) |
| 10 | 物料线边消耗 / 寿命计数 | 累计用量 | **MES/EAP** | MES | 每次使用 | [material-design §4.4](material-design.md) |
| 11 | 测试资产使用计数 | touchdowns / insertions | **EAP/MES** | MES | 每次测试 | [test-asset-design](test-asset-design.md) |
| 12 | 工装使用计数 | runs / hours / wafer_count | **MES/EAP** | MES | 每次使用 | [tooling-design §4.3](tooling-design.md) |
| 13 | 环境实时值 | 粒子数 / 温湿度 / AMC | **FMCS/Env** | 环境系统 | 秒–分 | [cleanliness-env-design](cleanliness-env-design.md) |
| 14 | 厂务可用性 | UPW / N2 / 真空 是否可用 | **FMCS** | 厂务系统 | 分 | [facility-utility-design](facility-utility-design.md) |
| 15 | 设备实时参数值 | 温度/压力读数 | **EAP** | EAP | 秒 | [equipment-interface-design §3.2](equipment-interface-design.md) |
| — | **（可回写，见 §2）** | | | | | |
| 16 | 载具资产态 | `admin_status` / `clean_status` | **MDS** | MDS | 天–周 | [carrier-design §4.2](carrier-design.md) |
| 17 | 光罩行政态 | `ACTIVE`/`HOLD`/`REPAIR` | **MDS** | MDS | 天 | [reticle-design §3.2](reticle-design.md) |
| 18 | 测试资产行政态 | `ACTIVE`/`HOLD`/`REPAIR` | **MDS** | MDS | 天 | [test-asset-design §3.3](test-asset-design.md) |
| 19 | 工装行政态 | `ACTIVE`/`HOLD`/`REPAIR` | **MDS** | MDS | 天 | [tooling-design §3.1](tooling-design.md) |
| 20 | 厂务行政态 | `RUNNING`/`MAINT`/`DOWN` | **MDS** | MDS | 天 | [facility-utility-design](facility-utility-design.md) |
| 21 | 设备/模块行政态 | `ACTIVE`/`MAINTENANCE` | **MDS** | MDS | 月 | [equipment-design §3.10](equipment-design.md) |
| 22 | 资格/认证到期派生态 | `EXPIRED` | **MDS**（定时派生） | MDS | 天 | [org-personnel-design §4.4](org-personnel-design.md) |
| 23 | PM/校准到期派生态 | `due` / `nextDueAt` | **MDS 计算 + MES 计数** | MDS（计算） | 天 | [pm-calibration-design §4.3](pm-calibration-design.md) |

> **可操作判据**：若某状态的**语义由 MDS 拥有**（不是"设备现在在干什么"，而是"这个资产在管理意义上的状态"），且**变化频率低**（≥ 天级），则可归 MDS（第 16–23 行）；否则一律归下游。

---

## 2. 可回写白名单（下游 → MDS）

**回写规则（强制）**：
1. **仅白名单内的字段可回写**（下表）；
2. 回写必须**幂等**且携带 `@Version`（乐观锁冲突需重试）；
3. 回写**必须留痕**（`@History(SNAPSHOT)` 已覆盖）；
4. 白名单外的字段，下游**只能读**。

| 表 | 可回写字段 | 回写方 | 触发 |
| --- | --- | --- | --- |
| `mds_carrier` | `admin_status`、`clean_status` | AMHS/MES | 清洗完成、报废、扣留 |
| `mds_reticle` | `admin_status` | MES | 清洗/送修复检完成、报废 |
| `mds_facility` | `admin_status` | FMCS/厂务系统 | 设施启停/检修 |
| `mds_equipment_module` | `admin_status` | MES（PM 执行完成） | PM 完成恢复 ENABLED |
| `mds_equipment` | `qualification_state` | MES（校准确认） | 校准通过/超差 |
| `mds_probe_card` / `mds_load_board` | `admin_status` | MES | 清洗/返修/报废 |
| `mds_tooling` | `admin_status` | MES | 更换/翻新（`REFURBISH`）完成、报废 |
| `mds_person_certification` | `cert_status` | 培训/HR 系统 | 认证授予/吊销 |

> **禁止回写**：一切 E10/控制模式/端口/lot/WIP/槽位/环境实时值 —— 这些是下游**自己域内**的数据，不要塞回 MDS。

---

## 3. 实时态注册表 `mds_realtime_registry`

把 §1 的契约**落为数据**，使 MDS 能向所有下游发布「**谁该维护什么**」的统一清单。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(32) | PK |
| `state_code` | VARCHAR(32) | 状态类别代码（如 `EQUIP_E10`） |
| `state_name` | VARCHAR(128) | 名称 |
| `entity_scope` | VARCHAR(32) | 作用对象（`EQUIPMENT`/`MODULE`/`PORT`/`CARRIER`/`RETICLE`/`LOT`/`BANK_SLOT`/`FACILITY`/`ENV`/`PERSON`） |
| `owner_system` | VARCHAR(16) | 拥有者：`MDS`/`MES`/`EAP`/`AMHS`/`FMCS`/`ENV`/`HR` |
| `persist_store` | VARCHAR(16) | 实际存储：`MDS`/`MES`/`EAP`/`AMHS`/`FMCS` |
| `update_frequency` | VARCHAR(16) | `SECOND`/`MINUTE`/`HOUR`/`DAY`/`WEEK`/`MONTH` |
| `writable_back` | BIT | 是否允许下游回写 MDS |
| `writable_fields` | VARCHAR(256) | 可回写字段清单（逗号分隔） |
| `is_critical_for_dispatch` | BIT | 派工热路径是否必须注入（提示下游） |
| `source_doc` | VARCHAR(128) | 依据文档（如 `equipment-design §3.11`） |
| `description` | VARCHAR(512) | 基类 |

- 唯一：`(state_code, tenant_id, deleted)`。
- **对外价值**：MDS 通过 [md-distribution-design](md-distribution-design.md) 下发本表 → 下游据此自检"我是否维护了该我维护的态"，集成测试也据此断言。

---

## 4. MES 生产执行七大阶段时序

> 每阶段列：**MDS 提供什么** → **MES 需注入什么实时态** → **是否回写**。

### 阶段 1：投料准备（Lot Release）
| | 内容 |
| --- | --- |
| MDS 提供 | 产品 + `tech_node`、可用路线（默认/备选）、工艺流参数窗口、用料 BOM、**批次类型策略**、路线/配方生效窗口 |
| MES 注入 | 无（此时尚未上机） |
| 回写 | ❌ |
| 卡点 | `CAPABILITY`/`WINDOW`/`COMPATIBILITY` 约束求值 |

### 阶段 2：组批（Batch Formation）
| | 内容 |
| --- | --- |
| MDS 提供 | 设备 `process_mode`、组批上限与混批规则、批次类型优先级、工艺参数兼容性 |
| MES 注入 | 候选 lot 集及其属性 |
| 回写 | ❌ |
| 卡点 | `BATCHING` 约束 |

### 阶段 3：派工（Dispatch）— **最热路径**
| | 内容 |
| --- | --- |
| MDS 提供 | 设备组与成员、设备能力/工艺节点/尺寸、配方资格 + **PPID**、**光罩资格**、**测试资产资格**、认证矩阵、日历、**厂务接入与就绪要求**、工艺窗口 |
| MES 注入 | **设备 E10 实时态、控制模式、端口态**、设备实时参数、在制 WIP、载具可用性、厂务实时可用性 |
| 回写 | ❌（派工不改主数据） |
| 卡点 | `COMPATIBILITY`/`CAPABILITY`/`CAPACITY`/`WINDOW`/`PM_INTERVAL`/`OPERATOR_QUALIFIED` |
| 性能 | **禁止回查 MDS**；本地缓存 + 版本比对（§7） |

### 阶段 4：搬运（Carrier Assignment）
| | 内容 |
| --- | --- |
| MDS 提供 | 载具类型兼容（可进区域）、归属库、库容、载具资产态（行政/洁净） |
| MES/AMHS 注入 | 载具实时位置、槽位占用、AMHS 运力 |
| 回写 | ⚠️ 清洗/报废时回写 `carrier.clean_status`/`admin_status` |

### 阶段 5：上机执行（Move-in / 处理中）
| | 内容 |
| --- | --- |
| MDS 提供 | 设备点表（变量/事件/报告）、PPID 与配方、工艺窗口、Q-Time 起点规则、SPC 采样计划 |
| EAP/MES 注入 | 设备实时参数、事件（CEID）、报警（ALID） |
| 回写 | ⚠️ 光罩/测试资产/物料使用计数**汇总**回写（可选，见 §2 外的灰度区） |
| 卡点 | `TIMING`（Q-Time 起点）、`QUALITY` |

### 阶段 6：下机与判定（Move-out / Measure / Judge）
| | 内容 |
| --- | --- |
| MDS 提供 | 采样计划、SPC 判异规则、OCAP 动作链、处置规则、原因码、缺陷码/Bin、参数定义 |
| MES/SPC 注入 | 实测值、控制限统计结果、判异命中 |
| 回写 | ❌ |
| 卡点 | `SPC_OOC_HOLD`、`DISPOSITION_TRIGGER` |

### 阶段 7：放行与追溯（Release / Genealogy）
| | 内容 |
| --- | --- |
| MDS 提供 | 处置规则（放行/扣留）、层、产品结构 BOM、光罩/物料/载具血缘维度 |
| MES 注入 | 该批完整执行历史 |
| 回写 | ❌ |
| 说明 | 追溯多为**低频按需查询**（非订阅） |

---

## 5. 实时事件与状态机的关联

- 设备侧事件（CEID）可**绑定状态迁移**：`mds_equip_if_event.related_state_transition_id`（[equipment-interface-design §3.3](equipment-interface-design.md)）→ 使状态变更**自动归因**，而非人工填写。
- 报警（ALID）`auto_down=1` 时自动进入 DOWN 并挂 `reason_code_ref`（[reason-defect-design §4.2](reason-defect-design.md)）→ **避免人工漏填停机原因**。
- 状态迁移合法性仍由 `mds_equipment_state_transition` 校验（[equipment-design §3.14](equipment-design.md)）；**MDS 只提供迁移边，MES/EAP 执行并校验**。

---

## 6. 一致性、降级与失败策略

| 场景 | 策略 |
| --- | --- |
| **版本漂移**（下游缓存旧版本） | 每次上电/定时比对 `release.version`；不一致即拉增量（[md-distribution-design §4.2](md-distribution-design.md)） |
| **未同步告警** | `mds_md_delivery` 中 `ACK` 超时 → 告警；运维可查"哪台下游停在哪个版本" |
| **MDS 不可用** | 下游**用最后一份快照继续生产**，进入**降级运行**状态并告警；**不允许**因 MDS 不可用而停线 |
| **MDS 不可用期间的受限操作** | 需要"新主数据"的操作（如新产品的首投、新配方首用）**无法降级** → 挂起或走应急审批流程 |
| **表达式求值失败** | 产生 `ERROR` + 告警；按配置**保守阻断**（不默认放行），见 [expression-dsl-design §7](expression-dsl-design.md) |
| **主数据变更期间的在线批次** | 已投 lot 继续走其**投料时锁定的版本**（`lot_route_instance.route_version` 固化），不受新版本影响（[route-design §4.2.2](route-design.md)） |
| **回写冲突** | 乐观锁 `@Version` 冲突 → 重试（指数退避）；连续失败告警，**不覆盖** |
| **实时态缺失** | 热路径若必需实时态缺失（如控制模式未知），**不得假定为可用**：按"未知=不可派工"保守处理 |

---

## 7. SLA 与性能约定

| 约定 | 值（建议） | 说明 |
| --- | --- | --- |
| 派工约束批量求值 | ≤ 50 ms | 本地缓存内完成，**零 MDS 回查** |
| 单表达式求值 | ≤ 50 ms | 含超时保护（[expression-dsl-design §7](expression-dsl-design.md)） |
| 主数据增量下发时延 | 变更生效 → 下游可见 ≤ 1 min | 经 [md-distribution-design](md-distribution-design.md) |
| 快照拉取 | 按批次，支持分页/断点续传 | 首次接入或基线重建 |
| 低频查询（追溯类） | ≤ 2 s（含分页） | 允许直连 MDS |
| **热路径禁止项** | 任何同步 HTTP 回查 MDS、任何跨系统 RPC | 由架构评审把关 |

---

## 8. 与 MES 生产执行衔接（汇总）

| MES 需求 | 契约要点 |
| --- | --- |
| 我要知道**每个状态谁维护** | 订阅 `mds_realtime_registry`（§3） |
| 我要知道**哪些字段我能写回** | §2 白名单，其余只读 |
| 我要在**派工**上做到零回查 | §7 + [md-distribution-design](md-distribution-design.md) 快照/增量 |
| 我要知道**每个阶段该用哪些主数据** | §4 七大阶段表 |
| MDS 挂了**我不能停线** | §6 降级策略 |
| 出了质量问题我要**能追溯** | 阶段 7 + [product-bom-design](product-bom-design.md) / [layer-design](layer-design.md) |
| 我要**改规则不改代码** | [expression-dsl-design](expression-dsl-design.md) + [change-mgmt-design](change-mgmt-design.md) |

---

## 9. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 实时态注册表继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 白名单字段的回写自动留痕（谁在何时把载具置为 DIRTY） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| M4 mq（待办） | 增量与回写的消息通道 |
| 主历成对迁移 | 结构迁移 + 历史表 + 实时态注册表种子（覆盖 §1 全 20 类） |

---

## 10. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_realtime_registry (
  id                      VARCHAR(32) NOT NULL,
  tenant_id               VARCHAR(32) NOT NULL,
  state_code              VARCHAR(32) NOT NULL,
  state_name              VARCHAR(128),
  entity_scope            VARCHAR(32) NOT NULL,
  owner_system            VARCHAR(16) NOT NULL,
  persist_store           VARCHAR(16) NOT NULL,
  update_frequency        VARCHAR(16) NOT NULL,
  writable_back           BIT DEFAULT 0,
  writable_fields         VARCHAR(256),
  is_critical_for_dispatch BIT DEFAULT 0,
  source_doc              VARCHAR(128),
  description             VARCHAR(512),
  version_                BIGINT NOT NULL DEFAULT 0,
  deleted                 BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_rt_reg PRIMARY KEY (id),
  CONSTRAINT uq_mds_rt_reg UNIQUE (state_code, tenant_id, deleted)
);
CREATE INDEX idx_rt_reg_scope ON mds_realtime_registry (entity_scope, owner_system);
```

> 历史表 `mds_realtime_registry_hist` 由 `@History(SNAPSHOT)` 生成。

---

## 11. 代码结构（包路径）

```
com.cim.mds.realtime
  ├── entity
  │   ├── RealtimeRegistry.java      # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   └── package-info.java           # @FilterDef(cimTenantFilter)
  ├── repository
  │   └── RealtimeRegistryRepository.java
  ├── service
  │   ├── RealtimeRegistryService.java   # 继承 AbstractJpaService；契约查询/导出
  │   └── AssetStateWritebackService.java # 白名单回写唯一入口（校验字段+幂等+留痕）
  └── web
      └── RealtimeController.java         # 契约查询；白名单回写 API
```

---

## 12. 待补 / 后续

- **集成测试契约**：把 §1 表格转为可执行的集成断言（断言"下游确实维护了 owner_system=自己的那些态"）。
- **降级运行的可观测性**：下游需上报"当前是否处于降级运行"，供运维大盘。
- **回写限流与审计报表**：白名单回写的频次统计与异常检测（防"频繁抖动"）。
- **与既有文档闭环**：[equipment-design §3.10–§3.14](equipment-design.md)、[carrier-design §4.2](carrier-design.md)、[bank-design §4.6](bank-design.md)、[recipe-design §4.3](recipe-design.md)、[process-flow-design §0.2](process-flow-design.md)、[md-distribution-design](md-distribution-design.md)、[expression-dsl-design](expression-dsl-design.md)、[00-blueprint §5](00-blueprint.md)。
