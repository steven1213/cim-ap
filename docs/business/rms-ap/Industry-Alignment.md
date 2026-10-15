# RMS-AP 行业对齐分析（Industry Alignment Review）

> **本文目的**：回答一个问题——**RMS-AP 是否满足半导体行业的使用习惯？** 以 SEMI 标准体系与主流商用 RMS（Applied E3 RMS、camLine LineWorks RM、Critical Manufacturing MES 配方模块、RES/Recipe Editor Server 等）的通行做法为基准，逐条对照本系统实现，给出**差距登记册**与**补齐路线**，并记录本轮已闭环项。
>
> **配套文档**：需求权威源 `Req.md`（§1 行业基线 / §5 接口契约）；本文件聚焦「与行业习惯的差距」，不重复需求正文。
>
> **基准日期**：2026-10-12。

---

## 0. 评审结论（先说结论）

**总体判断：核心能力已达行业基线，设备侧「事件驱动」治理为最大缺口——本轮已补齐。**

| 维度 | 判断 | 说明 |
| --- | --- | --- |
| 配方中央仓库（Body + 参数 Spec 双轨） | ✅ 达基线 | 八大能力基线全覆盖 |
| 版本/状态/签核生命周期 | ✅ 达基线 | 状态机 + 批量 + 限时放行 |
| 分发受控（阶段机 + 回读校验 + 失败重试） | ✅ 达基线 | W5 补齐 IF-E2 阶段机 |
| 在线校验（比对 + Hold + Alarm） | ✅ 达基线 | 三种比对模式 + Golden 机差合并 |
| 资格矩阵（默认拒绝） | ✅ 超基线 | 业界常有、原始协议未提，本系统作为核心管控点 |
| **设备主动上报（GEM 事件）** | ⚠️→✅ | **原缺，本轮补齐**（PPChange / PPVerificationFailed） |
| **机台配方目录核对（未纳管配方识别）** | ⚠️→✅ | **原缺，本轮补齐**（S7F19 语义） |
| 接口幂等 / 完整性 / 审计留痕 | ✅ 达基线 | correlationId 去重 + SHA-256 + 审计 |
| 变更控制（ECO/CCB 结构化理由与效果追踪） | ⚠️ 部分 | 有 changeSummary + 签核；缺 reason/category/ticket 与效果验证 |
| 配方类型（Process/Control/Sequence、多腔体） | ❌ 待做 | 当前仅主/子配方层级，无类型维度 |
| 运行上下文解析（产品/机型/腔体/耗材优先级） | ⚠️ 部分 | 当前 PPID 映射为「机台级 > 机型级」两级 |
| Body IP 保护（加密 + 电子签证据链） | ⚠️ 部分 | 受控查看已做；静态加密（D9）未落地 |

**一句话概括本轮动作**：把 EAP 集成面从「请求/应答 + 定期轮询」升级为「**请求/应答 + 事件驱动 + 目录核对**」三轨，与 SEMI E30 (GEM) 的配方管理事件模型对齐。

---

## 1. SEMI 标准对照（E42 / E30 / E5 / E37）

### 1.1 SEMI E42（Recipe Management Standard：概念、行为、消息服务）

E42 定义配方管理的**概念模型**与**服务契约**。本系统对标如下：

| E42 概念 | 业界含义 | 本系统落点 | 状态 |
| --- | --- | --- | --- |
| Recipe（配方对象） | 含 Generic/ASDS/Body 分区的结构化对象 | `Recipe`（主档）+ `RecipeVersion`（body + paramSnapshot） | ✅ |
| Recipe Body | 设备可执行原始体，格式由设备商定义 | `RecipeVersion.bodyBase64` + `bodyFormat`（BINARY/TEXT/JSON_STRUCTURED） | ✅ |
| ATTRIBUTE / PARAMNAME / PARAMSETTING / PARAMRULE | 属性与参数行的标签化表达 | `ParamSpec`（target/range/list-in/tolerance）+ `paramSnapshot` JSON | ✅ |
| Recipe identifier（PPID / recipe ID） | 机台侧以 PPID 为唯一身份的 ASCII 名 | `ToolRecipeMap.ppidOnTool` ↔ `Recipe.code`（D7，RMS 维护映射） | ✅ |
| Recipe namespace（按设备/共享设备组） | 机台专属命名空间 vs 同型号共享 | 机台级映射（`device_id`）优先于机型级映射（`device_type_id`） | ✅ |
| RMS provider / user | 服务提供方与使用方 | RMS = provider；EAP/MES = user（IF-E* / IF-M*） | ✅ |
| 配方对比（Recipe comparison） | 属性与 Body 级差异 | `RecipeCompareService`（FULL_BODY / PARAMETER / STRUCTURE） | ✅ |
| 配方验证规则（PARAMRULE） | 参数的合法性规则 | `SpecEvaluator` + 「上传即校验」（FR-C5） | ⚠️ 仅单条参数级，缺跨配方/跨步骤规则引擎 |

### 1.2 SEMI E30（GEM）配方管理消息服务

GEM 定义了配方管理的 SECS 消息集与**两个强制 Collection Event**。这是行业最硬的「使用习惯」——**设备必须主动通知主机**。

| GEM 能力 | SECS 消息 | 本系统落点 | 状态 |
| --- | --- | --- | --- |
| Load Inquire / Grant（容量许可） | S7F1 / S7F2 | `DownloadStage.GRANTED` | ✅ |
| Recipe Download + ACK | S7F3 / S7F4 | `SENT_TO_TOOL` → `TOOL_ACKED`（`toolAck` = ACKC7） | ✅ |
| Readback 校验（回读比对） | S7F5 / S7F6 | `VERIFIED`（以回读 HASH 为据，不一致判 `FAILED`） | ✅ |
| 配方上传（设备→主机） | S7F5/F6 请求、S7F3 上传 | IF-E1 上传（含 `uploadReason`） | ✅ |
| **请求机台配方目录** | **S7F19 / S7F20** | **`POST /integration/tool-inventory` + `rms_tool_recipe_inventory`** | ✅ **本轮** |
| 删除机台配方 | S7F17 / S7F18 | — | ❌ 未下发删除（仅旁路识别未纳管配方） |
| **配方变更事件** | **Collection Event `PPChange`**（`PPChangeName` + `PPChangeStatus`=Created/Edit/Delete） | **`POST /integration/equipment-events`（`PP_CHANGE`）** | ✅ **本轮** |
| **配方校验失败事件** | **Collection Event `PPVerificationFailed`**（`PPError`） | **`POST /integration/equipment-events`（`PP_VERIFICATION_FAILED`）** | ✅ **本轮** |
| 选配方事件 / 当前执行配方 | S2F41 `PP-SELECT`、S1F3 查 `PPExecName` | IF-E3（`ppidOnTool` = PPExecName 语义）、IF-E4 | ✅ |
| 设备常量 / 状态值（EC / SV） | S1F3 / S2F33 等 | FR-D2~D4、`EcSvService` + IF-E5 | ✅ |
| 配方持久化（GEM 接口内） | — | Body 不可变存档 + HASH（`{X}`↔`{X}Hist` 同构） | ✅ |

> **对齐要点**：GEM 的配方操作**以 PPID 为唯一身份**，主机与设备都用它对话。这正是 D7（RMS 维护 PPID↔UID 映射）的标准依据，也解释了为什么必须有机台配方目录核对——**不知道机台上有什么 PPID，就谈不上管控**。

### 1.3 SEMI E5（SECS-II）/ E37（HSMS）

| 项 | 约定 | 本系统 |
| --- | --- | --- |
| 传输终结 | SECS-II 事务由 **EAP 终结**，RMS 不直连设备 | ✅ 明确（Req §5.1） |
| 消息体编码 | 二进制/ASCII，单数组上限 16.7MB | Body Base64 封装 + `bodyFormat` 标注 |
| 大配方 | 分块（Large recipe set） | 未实现分块（单次传输；NFR-3 限 Body ≤5MB） |
| 链路管理 | HSMS Select / 心跳 / 重连 | EAP 侧职责（★注：EAP 项目已实现 T3–T8 定时器，见 cim EAP 侧工作） |

---

## 2. 业界能力基线（14 项）逐条核对

在 `Req.md §1.1` 的 8 项基线之上，按行业调研扩充至 14 项（★为本轮新增对照项）。

| # | 业界能力 | 业界依据 | 本系统落点 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 配方中央仓库（Body + Spec 双轨） | E3 RMS / E42 | FR-F1、FR-S1 | ✅ |
| 2 | 版本与状态受控（Draft→签核→Active→Obsolete） | 全部商用 RMS | §3.2 状态机 | ✅ |
| 3 | 分发受控（审批依据 + 传输凭证 + 失败重试） | camLine RM 集成指南 | IF-E2 阶段机（W5） | ✅ |
| 4 | 上传采集（设备→仓库反向同步） | E30 S7 | IF-E1 | ✅ |
| 5 | 在线校验（Run 前/中比对 + Hold + Alarm） | 23 项业界论文/产品共识 | IF-E3/IF-M1/IF-A1 | ✅ |
| 6 | Golden Recipe + 机差兼容 | Baseline Recipe 定义 | FR-G1~G3 | ✅ |
| 7 | 多维比对（设备↔仓库/设备↔设备/版本↔版本） | E42 比对服务 | §4.4 | ✅ |
| 8 | 审计 + 电子签核（21 CFR Part 11 精神） | GxP 惯例 | §2、§4.2、§5.6 | ✅（证据链待补） |
| 9 ★ | **设备主动上报配方变更（PPChange）** | **E30 强制** | **本轮补齐** | ✅ |
| 10 ★ | **机台校验失败上报（PPVerificationFailed）** | **E30 强制** | **本轮补齐** | ✅ |
| 11 ★ | **机台配方目录核对 / 未纳管配方识别（S7F19）** | 成熟 RMS 合规控制点 | **本轮补齐** | ✅ |
| 12 | 配方↔机台资格矩阵（默认拒绝） | Applied E3 核心管控 | FR-Q1~Q4 | ✅（超原始协议） |
| 13 ★ | 配方类型（Process / Control / Sequence；多腔体） | camLine / ISA-88 类比 | 仅主/子层级 | ❌ |
| 14 ★ | 变更控制（ECO/CCB：结构化理由 + 变更后效果追踪） | 基线配方管理惯例 | changeSummary + 签核 | ⚠️ 部分 |

**明确不在 RMS 范围**：Run-to-Run 控制（R2R）与 APC —— 属 SEMI **E133**（Automated Process Control Systems Interface）/ FDC 域。业界惯例是 RMS 向 APC/FDC 提供**版本化参数 Spec 基线**（本系统已预留只读开放 API，Req §5.1），不做控制器本身。

---

## 3. 差距登记册（Gap Register）

| ID | 差距 | 行业参考 | 优先级 | 状态 |
| --- | --- | --- | --- | --- |
| G1 | 缺 GEM `PPChange` 事件接收与漂移处置 | E30 强制 | P1 | ✅ 本轮闭环 |
| G2 | 缺 GEM `PPVerificationFailed` 事件与下发任务联动 | E30 强制 | P1 | ✅ 本轮闭环 |
| G3 | 缺机台配方目录核对（未纳管/漂移/缺失） | S7F19 + 合规 | P1 | ✅ 本轮闭环 |
| G4 | 未支持「删除机台配方」下发（S7F17/F18） | E30 | P2 | ⏳ 待做 |
| G5 | 未建模配方类型 Process/Control/Sequence 与多腔体 sequence+chamber | camLine / ISA-88 | P2 | ⏳ 待做 |
| G6 | 变更控制缺结构化理由/类别/工单号与变更后效果追踪 | 基线配方管理 | P2 | ⏳ 待做 |
| G7 | 运行上下文解析仅两级（机台 > 机型），无产品/腔体/耗材优先级与选择冲突处理 | Critical Mfg「flexible context resolution」 | P2 | ⏳ 待做 |
| G8 | 电子签证据链（`eSignEvidence`）未建模，外部签核回调仅有 decision | 21 CFR Part 11 | P2 | ⏳ 待做 |
| G9 | Body 静态加密未落地（D9 已决策） | IP 保护 | P1 | ⏳ 待做 |
| G10 | 下发超时预算/重试未按 `downloadMode` 配置化 | IF-E2 可靠性 | P2 | ⏳ 待做 |
| G11 | 配方老化/长期未使用告警缺失 | AI 增强惯例 | P3 | ⏳ 待做 |
| G12 | 机台清单 MISSING 只上报不报警 | 合规 | P3 | ⏳ 待做（本轮已在报告中返回） |

---

## 4. 行业「使用习惯」清单（工程视角）

不是功能清单，而是**做事的方式**——这些是最容易「看起来做了、实际不合行规」的地方。

| 习惯 | 行业做法 | 本系统 |
| --- | --- | --- |
| 身份 | 机台侧只认 **PPID**（ASCII），仓库侧用 UID，**映射由主系统维护** | ✅ D7：`ToolRecipeMap` 机台级 > 机型级；上传时自动登记/校正 |
| 版本语义 | **任何生效都必须经过签核**；回退 = 用历史内容生成**新版本**走签核，不直接激活旧版 | ✅ D2 / FR-L3 |
| 唯一生效版 | 同配方**至多一个 ACTIVE** | ✅ FR-L1（服务层保证） |
| 变更留痕 | 谁 / 何时 / 改了什么参数 / **为什么** | ⚠️ 前三项齐；「为什么」仅自由文本 `changeSummary` |
| 机台侧改动 | 设备**主动上报**（PPChange），主机回读重同步 | ✅ 本轮（事件驱动）+ 轮询巡检双轨 |
| 校验失败处置 | **校验失败的配方不得使用** | ✅ 本轮：判下发任务 `FAILED` + `RMS_VERIF_FAIL` 报警 |
| 未纳管配方 | 视为合规/安全事件，须识别与处置 | ✅ 本轮：`ORPHAN` 判定 + `RMS_ORPHAN_PPID` 报警 |
| 默认拒绝 | 未登记资格的配方不得在机台运行 | ✅ FR-Q2 / D8 |
| 报警纪律 | 分级（CRITICAL/MAJOR/MINOR）+ 去重键 + 可确认 | ✅ IF-A1：`alarmCode` + `correlationId` 去重 + ack |
| 报警克制 | 老问题不重复刷屏 | ✅ 本轮：清单核对**仅在结论新出现时**报警 |
| 幂等 | 至少一次投递 + 消费端去重（超时重试不得产生重复数据） | ✅ Req §5.2：`correlationId` / `eventId` 去重 |
| 时间与编码 | ISO 8601 UTC；UTF-8；二进制 Base64 | ✅ |
| 完整性 | Body 必带 SHA-256，接收端强校验 | ✅ IF-COM + S7F5/F6 回读语义 |
| 拓扑 | RMS 不与设备直连，SECS 由 EAP 终结 | ✅ Req §5.1 |
| 验证预期 | 集成测试须覆盖「缺上下文 / 多匹配 / 无发布版 / 网络中断 / 机台拒绝 / 部分传输 / 机台侧改动 / 回滚」 | ⚠️ 覆盖机台拒绝、机台侧改动、回滚、校验失败；**多匹配（选择冲突）未覆盖** |

---

## 5. 本轮补齐（W6：设备侧配方治理）

### 5.1 新增能力

| 能力 | 端点 | 表 | 说明 |
| --- | --- | --- | --- |
| GEM 设备事件 | `POST /api/v1/rms/integration/equipment-events` | `rms_equipment_event` | `PP_CHANGE`（建/改/删）与 `PP_VERIFICATION_FAILED`（PPError） |
| 事件查询 | `GET /api/v1/rms/integration/equipment-events` | 同上 | 按机台/类型过滤 |
| 机台配方目录上报 | `POST /api/v1/rms/integration/tool-inventory` | `rms_tool_recipe_inventory` | S7F19 语义；返回核对报告 |
| 清单查询 / 汇总 | `GET /api/v1/rms/integration/tool-inventory{,/summary}` | 同上 | 按机台/结论过滤 |

### 5.2 处置语义（对齐行业）

```
PP_CHANGE(EDITED | DELETED)  ──→ 已纳管配方 → RMS_DRIFT(MAJOR) + 默认挂 Hold（可 holdOnDrift=false 抑制）
PP_CHANGE(CREATED)           ──→ 已纳管配方 → 仅留痕（不报警，避免噪声）
PP_CHANGE(任意) 未纳管 PPID   ──→ RMS_ORPHAN_PPID(MINOR)，提示建立映射
PP_VERIFICATION_FAILED       ──→ RMS_VERIF_FAIL(MAJOR) + 在途下发任务判 FAILED
tool-inventory 核对          ──→ OK / DRIFT / ORPHAN（+ MISSING 列表）；仅「新出现」报警
```

### 5.3 与既有能力的配合

- **双轨漂移检测**：事件（及时）+ 轮询巡检（兜底）。事件链路健康的设备秒级感知；链路异常的设备仍能被巡检发现。
- **幂等**：事件按 `idemKey`（`eventId` 或内容哈希）唯一，EAP 断线重连重放不会重复报警/重复挂 Hold。
- **报警克制**：清单核对只有「结论由 OK 变为 DRIFT/ORPHAN」时才报警，避免每轮巡检重复刷同一老问题。

### 5.4 验证结果

- 后端 **W6 测试 10/10 绿**（新增 `RmsGemEventInventoryTest`）：PPChange 漂移处置与幂等、孤儿信号不误判漂移、CREATED 不报警、`holdOnDrift=false` 不挂 Hold、PPVerificationFailed 联动判失败、清单三类判定 + MISSING、查询与汇总端点。
- 前端 `tsc` 0 错 + `vite build` 绿；集成页新增「设备事件」「机台配方清单」两个面板。

---

## 6. 后续路线（按优先级）

**P1（安全/合规）**
1. **G9 Body 静态加密**（D9 已拍板）：按机型分组开关 + 平台 KMS；库内不落明文。
2. **G4 删除机台配方下发**（S7F17/F18）：在 IF-E2 下发动作上增加 `DELETE` 语义，配合清单核对清理孤儿配方。

**P2（能力深度）**
3. **G6 变更控制强化**：`changeReason`（枚举：良率/机差/新工艺/纠错）、`changeCategory`、`changeTicket`（ECO/CCB 单号），并在签核对象上暴露；配合变更后效果追踪（关联批次良率/量测）。
4. **G5 配方类型**：`recipeType`（PROCESS/CONTROL/SEQUENCE）+ 多腔体 sequence↔chamber recipe 关联，支持刻蚀/溅射簇状设备的「序列配方 + 腔体配方」结构。
5. **G7 运行上下文解析**：由两级映射升级为可配优先级的规则集（产品族 → 机型 → 腔体 → 耗材 → lot），并显式处理**多匹配（选择冲突）**与**无发布版**。
6. **G8 电子签证据链**：`eSignEvidence`（签名/证书指纹/时间戳）建模与不可篡改存档。
7. **G10 下发超时预算**：按 `downloadMode` 配置事务超时与重试上限（`ON_IDLE` 显著长于 `IMMEDIATE`）。

**P3（运营优化）**
8. **G11 配方老化告警**：长期未使用/未更新的配方提示复核。
9. **G12 MISSING 报警**：机台未上报应存在的配方时联动 `RMS_DRIFT`/独立码。
10. **FDC/SPC 开放 API 落地**：把版本化参数 Spec 基线以服务令牌只读方式开放（当前仅预留）。

---

## 7. 结论

- **满足行业使用习惯**：核心侧（仓库/版本/分发/校验/资格/审计/幂等）已对齐基线；**设备侧主动上报与目录核对**这一最硬的 GEM 习惯在 W6 已补齐。
- **尚存差距集中在三处**：变更控制的结构化理由与效果追踪（G6）、配方类型与多腔体结构（G5）、运行上下文解析与选择冲突（G7）——都是「成熟商用 RMS 才有、能显著加分」的深水区能力，非阻塞项。
- **安全项需前置**：Body 静态加密（G9）在有 IP 保护要求的环境下应先做。
