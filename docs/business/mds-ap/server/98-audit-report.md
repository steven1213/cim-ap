# MDS 设计文档审核报告（第 2 轮 · 五轮次深度审核）

> **审核对象**：`docs/business/mds-ap/server/` 全部 39 篇 design 文档 + `README.md`
> **审核方法**：**脚本化核验**（链接可达性、DDL 字段 diff、外键引用集合运算、枚举/约束登记比对、跨文档一致性 grep），结论**全部附实测证据**，不使用印象判断。
> **上一轮**：本报告为**第 2 轮**审核。第 1 轮（2026-09-23）发现 P0×3 / P1×4 / P2×2 并已完成全面调整；本轮**复检第 1 轮修复成果**，并**新发现 3 类此前未覆盖的缺陷**。

---

## 0. 审核基线（实测）

| 指标 | 实测值 | 结论 |
| --- | --- | --- |
| 文档规模 | **40 个文件**（39 design + README） / **980 KB** / **155 张唯一 `mds_` 表** | ✅ |
| 链接可达性 | **100%**（0 处失效） | ✅ |
| 外键悬空 | **0**（`REFERENCES` 的目标表全部有 `CREATE TABLE` 定义） | ✅ |
| 跨文档重复定义表 | **0** | ✅ |
| 非 `mds_` 前缀表 | **0** | ✅ |
| 待补条目 | **177 条**，与 [99-backlog](99-backlog.md) 汇总数**一致** | ✅ |
| 平台底座对齐 | `@History(SNAPSHOT)` / `cimTenantFilter` / T2.5 在 **36/36 域文档**齐备 | ✅ |
| **外键引用形态** | `REFERENCES (id)` **187** / `REFERENCES (code)` **0**（本轮修复前为 182 / 11） | ✅ 已修 |
| **业务版本字段** | `revision VARCHAR(16)` **25** / `version BIGINT`（乐观锁，基类）**25** / `version_col` **0** | ✅ **已修**（原 27 / 22 / 3） |
| **`BaseRevisionData` 继承** | **0 篇**（72 处均继承 `BaseDefData`）——**方案 B 有意不启用**，见 [00-blueprint §11.2](00-blueprint.md) | ✅ 已定调 |
| **`frozenState` / `is_frozen`** | **0 / 33**——`status` + `is_frozen` 为**有意简化**，已声明 | ✅ 已声明 |
| **域数量口径** | 修复前 **4 处表述、2 个数值**（35 / 36） | ❌ 已修 |

---

## 1. 轮次一 · 结构一致性（DDL / 外键 / 命名）

### 1.1 通过项

- **外键引用完整性**：全集运算 `defined_tables ∖ ref_tables = ∅`，**无悬空引用**。
- **表命名规范**：`pk_*` / `uq_*` **100% 带 `mds_` 前缀**，无非规范命名。
- **表定义唯一性**：`CREATE TABLE` 语句按表名去重后无跨文档重复（每个表只在 1 篇定义）。
- **表前缀总览**：155 张表按域分布合理（equipment 14 / process-flow 8 / material 8 / equip-if 7 / constraint 7 …）。

### 1.2 缺陷

#### ✅ P0-1 · `version` 字段同名异型（**已按方案 B 修复**）

**实测证据**：

```
version VARCHAR(16)  → 27 处   （route/product/recipe/process-flow/constraint/naming-rule/test-asset 的业务版本）
version BIGINT       → 22 处   （重复定义基类乐观锁）
version_col          →  3 处   （自造字段名：naming-rule ×1、recipe ×2）
BaseRevisionData 继承 →  0 篇
frozenState          →  0 处 / is_frozen 33 处
```

**问题**：平台 `BaseDefData` 已含 `version`（`Long` 乐观锁），而 MDS 主表用 `version VARCHAR(16)` 表达**业务版本**——**同名异型**，DDL 无法与基类映射共存。平台另有 `BaseRevisionData`（`revision`/`activeState`/`frozenState`/`archiveState`/`originRevision`）专管业务版本，**MDS 完全未使用**。

**最典型症状**：[naming-rule-design.md](naming-rule-design.md) **同一文档内自相矛盾**——
- `mds_naming_rule`：`| version | VARCHAR(16) | 规则版本 |`（:73）
- `mds_naming_seq`：`| version | BIGINT | @Version 乐观锁 |`（:107）

**修复（已执行，方案 B 由用户拍板）**：业务版本字段 `version` → **`revision`**（25 处 DDL + 26 处字段表）；自造 `version_col` 归还为 `version`（3 处）；唯一约束 12 处同步；ECN 的 `from_version`/`to_version` → `from_revision`/`to_revision`；正文与决策表同步。规范见 [00-blueprint §11.2](00-blueprint.md)。

**改造后语义**：`version`(BIGINT) **唯一表示乐观锁**（基类提供）；`revision`(VARCHAR) **唯一表示业务版本**。`status` + `is_frozen` 保留为**有意简化**（§11.1 已声明）。

#### 🔴 P0-2 · 跨域 FK 引用 `(code)`（11 处）—— ✅ **本轮已修复**

被引用列唯一约束均为 `(code, tenant_id, deleted)`，**单列 `code` 无唯一索引**；且 FK 不带租户过滤 → **多租户跨租户误关联**风险。另与 182 处 `REFERENCES (id)` 风格不一。

典型自相矛盾：[carrier-design.md](carrier-design.md) :212 已声明「`area_code` 为**业务代码逻辑引用**…服务层校验」，**但同文档 :301 却写了 `FOREIGN KEY … REFERENCES mds_location (code)`**。

**本轮修复**（11 处）：6 处跨域 → 去 FK 改逻辑引用；5 处域内 → 改 `REFERENCES (id)` + 字段名 `_code`→`_id`。规范已写入 [00-blueprint §10](00-blueprint.md)。

---

## 2. 轮次二 · 业务闭环与状态机一致性

### 2.1 通过项

- **主业务链路字段连通**：`product.default_route_code → route → route_operation(logic_recipe_id) → process_flow_step(recipe_id/ppid) → equipment_recipe_qual(ppid)` 全链闭合，无断点。
- **状态机框架**：`model_kind` 统一承载 `EQUIP_LIFECYCLE`/`MODULE_ADMIN`/`E10_STATE`/`CONTROL_MODE`/`PORT_STATE`/`CARRIER_STATE` 六类状态机，E10 状态目录（14 态）+ 合法迁移表齐备。

### 2.2 缺陷

#### 🟠 P1-1 · 孤儿主数据域（`mds_lot_type` / `mds_priority_class` 未接入闭环）

**实测证据**：两表**仅出现在自身文档**（`lot-type-design.md`）与总纲索引，**全库无任何实体表以字段关联**——`product` / `route` / `process_flow` 上**均无** `default_lot_type` / `priority_class` 字段。

**后果**：MDS 无法回答「**这个产品默认用哪类批次**」；[lot-type-design](lot-type-design.md) 声称「工程批走不同分支」只能靠**表达式层**（`lot.type`），缺少**实体级**闭环。

**建议**：`mds_product` 增 `default_lot_type_id`（域内 FK→`mds_lot_type.id`），并在 [00-blueprint §5](00-blueprint.md) 场景 1 标注。

#### 🟠 P1-2 · `product` 生命周期与「同源」声明不符 —— ✅ **本轮已修复**

**实测**：`route`/`recipe`/`process-flow` 均为 `DRAFT→IN_REVIEW→APPROVED→RELEASED→OBSOLETE→ARCHIVED`，而 `product` 写成 `DRAFT→RELEASED→OBSOLETE→ARCHIVED`（**缺 2 态**），却自称「与 route 同源」。且同文档 :150 明确列出 `submitForReview`/`approve` 方法——**证明本应有 IN_REVIEW/APPROVED**，属枚举漏写。

**本轮修复**：补全 `product-design.md` :104 与 :145 的枚举。

#### 🟠 P1-3 · 引用策略三套混用、无统一规范 —— ✅ **本轮已建立规范**

实测：`REFERENCES (id)` 187 / `REFERENCES (code)` 11 / 逻辑引用（`*_code` + 服务层）30+，三种并存且**无规范文档**。规范已写入 [00-blueprint §10](00-blueprint.md)。

#### 🟡 P2-1 · 生命周期序列书写格式不一

`route` 用 ASCII 箭头 `DRAFT ──submit──> IN_REVIEW`，`recipe`/`process-flow` 用 Unicode `→`。**建议统一为 Unicode `→` 并在正文补注触发方法名**。

---

## 3. 轮次三 · MES 生产执行落地可行性

### 3.1 通过项

- **热路径零回查**已定义：`api-design §2` + `realtime-contract §7`，组批/派工 ≤50 ms 本地缓存求值。
- **12 个解析接口**覆盖跨表拼装（`resolve-ppid`/`resolve-context`/`resolve-eligible`×4/`resolve-model`/`resolve-for-fab` …），每接口标注 SLA 与可缓存性。
- **降级可控**：MDS 不可用时 MES 用最后快照继续生产（L0–L3 分级，`nonfunctional-design`）。

### 3.2 缺陷

#### 🟡 P2-2 · 「MES 生产执行衔接」专章覆盖 22/40，与「每域都含」的声称不符

**实测**：含 `MES` 标题的文档 **22 篇**（`##` 级 **17 篇**）；缺章的包括 `process-flow` / `material` / `reticle` / `sampling-spc` / `reason-defect` / `bank` / `location` / `layer` / `equipment-interface` / `constraint` 等**核心域**。

**说明**：多数可通过 `realtime-contract` 的**集中时序表**间接覆盖，但**域内视角缺失**——建议在缺失域补 3–5 行「本域在 MES 哪一步被消费」，或在 `00-blueprint §5` 建立**域→场景反查表**替代。

#### 🟡 P2-3 · 回写白名单单向声明

`realtime-contract §2` 集中定义了可回写表与字段，但**各域未反向声明**自己哪些列可回写（仅 `00-blueprint` 一处提及「资产态」）。建议各域字段表对可回写列加 `🔁` 标记。

---

## 4. 轮次四 · 合规性与平台对齐

### 4.1 通过项（上一轮 P3 修复成果）

- **EHS/安全域**已建（危险品台账 / GHS / 重大危险源 / 化学品相容性矩阵 / 应急预案 / ISO 45001 控制层级）。
- **计量溯源链**已补（`pm-calibration` 附录 B：标准器台账 / 不确定度 / `traceability_chain`）。
- **数据保留与归档**已补（`md-governance` 附录 A：9 类保留期矩阵、销毁双人审批、签名永久）。
- **非功能设计**已补（容量五档 / RPO≤5min / RTO≤30min / L0–L3 降级 / 数据迁移七步）。

### 4.2 缺陷

#### 🟠 P1-4 · 域数量口径仍漂移（**上一轮修复遗漏**）—— ✅ **本轮已修复**

**实测**：上一轮声称「四处口径已对齐」，但复检发现 **3 处仍为旧值**：

| 位置 | 修复前 | 修复后 |
| --- | --- | --- |
| `api-design.md` :212 | 全部 **35** 个域 | 36 |
| `md-governance-design.md` :264 | **25** 业务主数据域…= **35** 个域 | 26 … = 36 |
| `md-governance-design.md` :431 | **25** 业务主数据域…= **35** 个域 | 26 … = 36 |

**教训**：口径类修复须**全库 grep 复核**，不能只改「发起方」。

#### 🟡 P2-4 · `mds_md_` 前缀被两个域共用

`md-governance`（`mds_md_domain/steward/workflow/quality_rule/signature`）与 `multi-site`（`mds_md_scope_policy/localization`）共用 `mds_md_` 前缀，语义一为「元治理」一为「多站点」。**建议** multi-site 改 `mds_site_*`。

---

## 5. 轮次五 · 交付质量与可维护性

| 项 | 实测 | 结论 |
| --- | --- | --- |
| 「与既有文档闭环」小节 | 38/40 篇 | ✅ |
| 文档头部元数据（版本/日期） | 无统一元数据块 | 🟡 **P2-5** 建议补 |
| 术语表 | 24 条；「快照/增量」等缺 | 🟡 **P2-6** 建议补 |
| 表命名归属 | `mds_equipment_consumable`（定义于 material）、`mds_equipment_group`（定义于 process-flow）——**表名像 equipment 域** | 🟡 **P2-7** 建议加注释或改名 |
| 待补最集中域 | `recipe`(9) > `equipment`(8) = `carrier`(8) > `process-flow`(7) = `constraint`(7) | 🟡 主干仍最「半成品」 |

---

## 6. 缺陷总表

| 编号 | 缺陷 | 级别 | 状态 |
| --- | --- | --- | --- |
| **P0-1** | `version` 同名异型（25 处业务版本 vs 基类乐观锁）；`BaseRevisionData` 0 继承（有意） | 🔴 P0 | ✅ **已修**（方案 B，见 §11.2） |
| **P0-2** | 跨域 FK 引用 `(code)`（11 处，单列无唯一索引 + 跨租户风险） | 🔴 P0 | ✅ **本轮已修** |
| **P0-3** | 域数量口径 3 处漂移（35 vs 36） | 🔴 P0 | ✅ **本轮已修** |
| **P1-1** | `mds_lot_type`/`mds_priority_class` 孤儿域，无实体级关联 | 🟠 P1 | ⏸ 待处理 |
| **P1-2** | `product` 生命周期枚举漏 `IN_REVIEW`/`APPROVED` | 🟠 P1 | ✅ **本轮已修** |
| **P1-3** | 引用策略三套混用、无规范 | 🟠 P1 | ✅ **本轮已建规范** |
| **P1-4** | 域数量口径漂移（上轮遗漏 3 处） | 🟠 P1 | ✅ **本轮已修** |
| **P2-1** | 生命周期序列书写格式不一（ASCII vs Unicode 箭头） | 🟡 P2 | ⏸ |
| **P2-2** | MES 衔接专章实际 22/40 | 🟡 P2 | ⏸ |
| **P2-3** | 回写白名单单向声明 | 🟡 P2 | ⏸ |
| **P2-4** | `mds_md_` 前缀双域共用 | 🟡 P2 | ⏸ |
| **P2-5** | 缺统一文档元数据块 | 🟡 P2 | ⏸ |
| **P2-6** | 术语表缺「快照/增量」等条目 | 🟡 P2 | ⏸ |
| **P2-7** | 表命名归属与定义文档不一致 | 🟡 P2 | ⏸ |

---

## 7. 评分卡（第 1 轮 → 第 2 轮）

| 维度 | 第 1 轮评审 | 第 2 轮复检 | 变化说明 |
| --- | --- | --- | --- |
| **全面性** | A- | **A** | EHS/计量/留存/非功能已补；权威域清单已建 |
| **合规性** | B | **A** | 合规硬缺口已补；`version` 语义已对齐平台 T2.7 三层版本语义 |
| **完善性** | B- | **B+** | 177 条待补已收敛 + DoD + 非功能；仍有主干域待补集中 |
| **闭环性** | C+ | **A** | DDL 单一来源、外键/枚举/约束全部回写、引用与版本策略均已规范 |
| **一致性**（本轮新增维度） | — | **A-** | 引用策略统一、版本字段统一（`version`=乐观锁 / `revision`=业务版本）；余 P2 细节 |
| **可落地性**（本轮新增维度） | — | **A** | 外键可建表、DDL 可与平台基类映射、待补可排期 |

---

## 8. 本轮修复记录（脚本验证）

| 修复 | 动作 | 验证 |
| --- | --- | --- |
| P0-2 | 6 处跨域去 FK（carrier/cleanliness/equipment/location/param-def/route）+ 5 处域内改 `(id)` 并同步字段名（ehs×2 / lot-type / uom×2） | `REFERENCES (code)` = **0**；`(id)` = **187** ✅ |
| P0-3 / P1-4 | `api-design` :212、`md-governance` :264/:431 口径 35→36、25→26 | 四处口径一致 ✅ |
| P1-2 | `product-design` :104/:145 补 `IN_REVIEW`/`APPROVED` | 与 route 完全同源 ✅ |
| P1-3 / P0-2 规范 | 新增 [00-blueprint §10 引用字段规范](00-blueprint.md) | 已落文 ✅ |
| **P0-1** | **执行方案 B**：业务版本 `version` → `revision`（25 处 DDL + 26 处字段表）；`version_col` → `version`（3 处）；唯一约束 12 处；ECN `from/to_version` → `from/to_revision`；正文与决策表同步；规范写入 [00-blueprint §11.2](00-blueprint.md) | `revision VARCHAR(16)` = **25**；`version_col` = **0**；文档内**不再同名异型** ✅ |

---

## 9. 结论与建议

**结论**：MDS 文档集已从第 1 轮的「概念闭环好、结构层不一致」提升到**结构层自洽**——外键 0 悬空、引用策略统一、**版本字段语义已对齐平台三层版本（方案 B 已执行）**、枚举/约束全登记、待补可排期。**P0 三项全部关闭**，剩余为 P1×1 与 P2×7 的收口项。

**建议执行顺序**：

1. **P1-1 补孤儿域关联**：`mds_product.default_lot_type_id`（唯一剩余 P1）。
2. **P2-2/P2-3 补 MES 视角**：各域加 3–5 行消费说明，或在 `00-blueprint §5` 建域→场景反查表（成本更低，推荐）。
3. **P2-1/P2-4/P2-5/P2-6/P2-7** 批量收口（一轮可完成）。
4. 全部完成后 → 按 [99-backlog §3](99-backlog.md) 第 1 轮推进（数据迁移与种子 + 实时态细化）。
