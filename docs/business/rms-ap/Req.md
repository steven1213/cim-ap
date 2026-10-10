# RMS-AP 需求规格说明书（完善版）

> **文档定位**：本文件在客户《RMS 技术协议》（53 条原始需求，**原样保留于[附录 A](#附录-a原始技术协议-53-条)**）基础上，结合半导体行业 RMS 的通行做法（Applied E3 RMS 等主流产品能力基线）、SEMI 标准体系（E42 配方管理指南 / E30 GEM / E5 SECS-II / E37 HSMS，配方链路对应 SECS S7 系列与 S2F41）以及 cim-ap 现有框架能力（IAM 统一登录、平台 RBAC、cim-mq-starter、多租户），细化为一套可直接指导设计与验收的需求规格。
>
> **阅读指南**：正文每条需求带编号（`FR-*` 功能 / `IF-*` 接口 / `NFR-*` 非功能 / `UI-*` 前端），[§10 追溯矩阵](#10-追溯矩阵原始-53-条--细化需求)给出原始条目 → 细化需求的完整映射；**业界普遍存在的问题在 §1.2 列出，本系统的改善点即差异化需求来源**；待拍板决策集中在 §9。
>
> **约定**：RMS **不直接连接设备**，一切设备侧 SECS-II 事务由 EAP 终结（这与业界一致：RMS 是配方的大脑，EAP 是设备的手）。

---

## 1. 行业分析：业界怎么做 RMS，问题在哪，我们改善什么

### 1.1 业界能力基线

成熟晶圆厂/面板厂的 RMS（业界标杆如 Applied Materials E3 RMS、Siemens Opcenter 配方模块等）普遍具备八大能力，本需求全部覆盖并对齐：

| # | 业界基线能力 | 本系统落点 |
| --- | --- | --- |
| 1 | **配方中央仓库**：Recipe Body（设备可执行的原始体）+ Recipe Spec（参数级规格）双轨存储，Body 格式按机型适配 | §4.1（FR-F1 系）、§4.3 参数 Spec |
| 2 | **版本与状态受控**：Draft→签核→Active（同配方至多一个生效）→Obsolete，全量变更留痕 | §3.2 状态机（FR-L1 系） |
| 3 | **分发受控**：仓库→机台下载有审批依据、有传输凭证（回读比对/HASH）、有失败重试队列 | §5.3 IF-E2 |
| 4 | **上传采集**：从设备反向采集 Body 入库（首次建档、机台侧被改动后回同步） | §5.3 IF-E1 |
| 5 | **在线校验**：Run 货前/中比对「机台当前配方」与「生效版本」（Body HASH 级或参数级），失败 Hold + 报警 | §5.3 IF-E3、§4.5 |
| 6 | **Golden Recipe**：同型号共享的基准配方，新机台/新产品以此为比对基准 | §4.6 |
| 7 | **设备↔设备、设备↔仓库、版本↔版本**多维比对与差异报告 | §4.4 比对引擎 |
| 8 | **审计与电子签核**：谁/何时/改了什么参数/为什么（等价 21 CFR Part 11 的证据链要求） | §2、§4.2、§5.6 |

### 1.2 业界普遍存在的问题 → 本系统改善点（差异化需求来源）

| # | 业界现状/痛点 | 本系统改善（需求落点） |
| --- | --- | --- |
| P1 | **机台侧漂移不受控**：工程师绕过 RMS 直接在机台改配方，仓库与机台长期不一致，事后才发现 | 定期/事件触发的**漂移巡检**：HASH 快比对批量巡检（FR-C6），漂移即漂移报告 + 可选自动 Hold + Alarm（FR-C7）；上传采集带 `UPLOAD_REASON=DRIFT` 留痕 |
| P2 | **Body 格式碎片化**：各设备厂私有二进制格式，比对只能「比 HASH 是否一致」，说不出哪里不同 | **机型适配器（Format Adapter）**架构：Body 原样存档（不丢真），同时经适配器抽取参数快照做参数级/结构级比对（FR-C1~C4）；适配器按机型注册、可扩展（对齐平台可插拔风格） |
| P3 | **Spec 游离在 Excel**：参数规格表在 RMS 之外人工维护，版本错乱 | 参数 Spec **一等公民化**：target/range/list-in/tolerance 四种规格类型建模（FR-S1），**Spec Template** 复用（FR-S2），新版本可继承生效版 Spec（FR-S3，对应 Req 32） |
| P4 | **签核靠邮件/线下**，周期长无 SLA | 内置签核流（会签/或签/多级）+ **外部签核系统集成契约**（FR-L4、IF-W1），含超时催办与代理审批 |
| P5 | **审核中配方急用无解**：要么等签核、要么口头放行无记录 | **预设限时使用**（Req 20）：PENDING 状态配方可预设放行窗口，超时自动失效、签核通过自动取消预设，全程留痕（FR-L5） |
| P6 | **分发与生产节拍脱节**：下载时机台忙碌导致失败率高 | 下载策略：即时 / **Idle 触发** / 批量队列（Req 40），EAP 侧事务超时与重试预算可配（IF-E2） |
| P7 | **权限粗放**：只有「能不能登系统」，机台级/配方级授权靠自觉 | **设备可见/可操作授权**（Req 11–12）+ 配方按区域/机型/分组的作用域授权（FR-P4/P5），操作历史完整（FR-P6） |
| P8 | **审计碎片化**：操作日志、比对日志、报警各系统各自为政 | 统一操作历史 + 校验日志（Validate Log）汇总统计（FR-H1~H3），复用平台审计/历史基类（`{X}`↔`{X}Hist`） |

---

## 2. 角色、权限与统一登录（对应 Req 7–12）

### 2.1 登录与准入（对齐 iam-ap 铁律）

- **FR-P1**：RMS **不自建登录**。用户经 IAM 统一登录获取 JWT(RS256) 令牌；RMS 本地用 JWKS 公钥验签（不逐请求回查 IAM）。
- **FR-P2**：准入判定——令牌 `apps` claim 含 `rms-ap` 方可进入；`roles` claim 提供 ap 内粗角色组。RMS **不存用户主数据**（员工身份由身份目录 AD/HR 承载），只存**业务授权关系**。

### 2.2 内部权限（业务自管）

- **FR-P3（Req 7/9/10）**：菜单/按钮/数据权限复用平台 RBAC（`sys_role`/`sys_permission`/`sys_user_role` 体系），权限码三段式 `rms:<res>:<action>`（如 `rms:recipe:create`、`rms:recipe:approve`、`rms:bypass:config`、`rms:device:manage`）。角色维护支持按**区域/机型/职能**建角色（Req 7）。
- **FR-P4（Req 11）**：**设备级授权**——用户↔设备（可含区域/机型维度）授权表，未授权设备在列表/树/接口中均不可见、不可操作；授权变更即时生效。
- **FR-P5（Req 12）**：分级管控——同一操作在不同**配方级别**（普通/Golden/生效版）可配置不同权限要求（如 Golden 的修改与签核需更高角色）。
- **FR-P6（Req 12）**：权限与授权**全量历史**：授权变更、登录、关键操作均留痕可查（操作人/IP/时间/前后值）。

---

## 3. 核心领域模型与配方状态机

### 3.1 领域模型（细化方向，实体设计在 design 阶段展开）

- **Recipe（配方）**：`uid`、名称、机型（Tool Type）、分组/层级（主配方/子配方，父子不限层，Req 15）、产品关联、区域作用域、Golden 标记。
- **RecipeVersion（配方版本）**：版本号（单调递增）、状态、Body 引用、Spec 参数集、变更摘要、创建/签核/生效人及时间。
- **RecipeBody（配方体）**：原始字节（不可变存档）+ 格式标识 + SHA-256 + 经机型适配器抽取的**参数快照**（JSON 结构化）。
- **ParamSpec（参数规格）**：参数路径、规格类型（target/range/list-in/tolerance）、规格值、启用比对开关（Req 34）、容差（百分比与固定值双轨，Req 36）。
- **SpecTemplate（规格模板）**：可应用到配方的规格集合（Req 35）。
- **Device/DeviceType/DeviceArea**：设备台账三件套（Req 46–48）；EC/SV 常量规格（Req 43–44，按配方/产品维度设定）。
- **授权/签核/比对/Bypass/Hold/历史** 各自独立聚合。

### 3.2 配方版本状态机（对应 Req 13–14、20、42）

```
DRAFT（新建/编辑中）
   │ 发起签核（批量支持，Req 3）
   ▼
PENDING_SIGN（签核中）⇄ 预设限时使用 TEMP_ALLOWED（超时自动回落）
   │ 签核通过（批量支持）              │ 签核拒绝
   ▼                                  ▼
ACTIVE（生效；同 Recipe 至多一个）   DRAFT（退回修改）
   │ 被新版本顶替 / 手动禁用
   ▼
OBSOLETE（失效；仅可查/可复制为 DRAFT）
```

- **FR-L1（Req 13/14）**：同 Recipe 多版本共存，**至多一个 ACTIVE**；新版本生效即旧版本转 OBSOLETE。
- **FR-L2（Req 3）**：状态变更支持批量（批量改状态、批量签核、批量生效），批量操作按单配方粒度返回逐项结果（部分成功语义，逐项原因）。
- **FR-L3（Req 42）**：更新 Body 生成新版本；**回退 = 选定历史版本内容生成新版本走正常签核生效**（业界惯例：不直接激活旧版本，保证任何生效都经过签核）。【决策点 D2】
- **FR-L4（Req 41）**：内置签核流 + 外部签核系统集成（见 IF-W1）；签核对象携带参数级差异附件（改动可视）。
- **FR-L5（Req 20）**：**预设限时使用**——PENDING 配方可预设放行（起止时间/最长时限/使用范围），超时自动失效回落 PENDING；签核通过后预设自动取消；预设/超时/取消均留痕并通知。

---

## 4. 功能需求细化

### 4.1 配方基本管理（Req 1–6）

- **FR-F1（Req 1/2）**：Recipe List（参数列表）与 Recipe Body 分层建档；经 EAP 从设备上传 Body，支持**批量上传**与**设备间复制**（跨机台复制按机型校验兼容性，跨机型需显式确认并记录）。
- **FR-F2（Req 4）**：查询支持通配符（`*`/`?`/SQL 转义语义明确定义），检索维度：名称/UID/机型/区域/状态/版本/产品/Golden；结果按设备授权裁剪（FR-P4）。
- **FR-F3（Req 5）**：删除——仅 DRAFT/OBSOLETE 可删；ACTIVE 删除需先禁用；软删除留痕。
- **FR-F4（Req 6）**：复制/另存为——版本内容整拷为新 Recipe 的 DRAFT，血缘关系记录（源 Recipe/版本）。

### 4.2 参数 Spec 与模板（Req 29/32/34/35/36）

- **FR-S1**：四种规格类型一等建模：`target`（目标/绝对值）、`range`（数值范围）、`list-in`（枚举列表）、`tolerance`（比例容差）；**容差支持百分比与固定值双轨并存**（Req 36）。
- **FR-S2（Req 35）**：Spec Template 管理（建/改/删/版本化），可整体应用到配方，应用记录模板版本。
- **FR-S3（Req 32）**：机型新建配方版本时可**一键继承 Active 版本 Spec**，逐参数可覆写。
- **FR-S4（Req 34）**：参数级比对开关（是否参与比对）按参数设定。

### 4.3 比对引擎（Req 21/24/25/26/27/28/33/37/38/39）

- **FR-C1（Req 37）**：三种比对模式：**Full Body**（原始体 HASH/字节级）、**Parameter**（参数级，按 Spec）、**Structure**（结构级：配方层级/参数组成差异）。
- **FR-C2（Req 25/26/33）**：比对维度：设备↔RMS（单台/批量巡检）、设备↔设备（同型号真实机台）、RMS 版本↔版本。
- **FR-C3（Req 28）**：差异报告——逐参数列出：参数路径、规格期望、机台实际、差异类型（超 target/出 range/不在 list/超 tolerance/单侧缺失/结构差异）、差异量化；可导出。
- **FR-C4（Req 29）**：参数级比对按 FR-S1 四种规格类型判定；Golden 比对同规则（§4.6）。
- **FR-C5（Req 39）**：上传时**自动参数检查**（格式合法性、必填、越界告警），结果入校验日志（FR-H1）。
- **FR-C6（Req 21）**：**在线比对**：Online Run 时响应 EAP/MES 请求比对机台配方（接口见 IF-E3/IF-M1）；引擎异步化，超时预算与降级策略可配。
- **FR-C7（Req 21）**：比对失败按配置 **Hold Recipe** 并发 Alarm（IF-A1）；Hold/解除 Hold 留痕。
- **FR-C8（Req 24/25）**：记录比对失败的具体参数信息（不只有 PASS/FAIL）；设备本地配方比对（设备↔设备）结果同样入报告。

### 4.4 Bypass（Req 22/23）

- **FR-B1**：设备级 Bypass——跳过某设备所有配方的比对/参数检查（含生效期与原因，强制留痕）。
- **FR-B2**：配方级 Bypass——单个 Recipe 的比对/参数检查跳过。
- **FR-B3**：Bypass 变更实时生效并通知 EAP（缓存失效策略），到期自动恢复，操作入高敏感审计。

### 4.5 Golden Recipe（Req 16/30/31）

- **FR-G1**：Golden Recipe 标记与管理；**同型号同配方共享同一 Golden**（Req 30）。
- **FR-G2（Req 31）**：**机差兼容**——Golden 比对时允许按设备维护「机差覆盖层」（该设备允许偏离 Golden 的参数白名单及范围），比对时 Golden Spec + 设备覆盖层合并判定。【决策点 D3：覆盖层的审批是否走签核】
- **FR-G3**：Golden 变更走签核；使用 Golden 验证的比对记录引用 Golden 版本号。

### 4.6 设备管理与 EC/SV（Req 43–48）

- **FR-D1（Req 46/47/48）**：设备类型 / 设备区域 / 设备台账 基础 CRUD（查询、新建、修改、删除），删除有引用校验。
- **FR-D2（Req 43）**：EC（设备常量）/SV（设备状态）上传与比对，比对方式支持 target/range/tolerance。
- **FR-D3（Req 44）**：EC/SV Spec 按 **配方 / 产品** 双维度设定。
- **FR-D4（Req 45）**：Online Run 时 EAP 主动校验 EC/SV，失败明细记录（同 FR-C7 Hold/Alarm 语义可配）。

### 4.7 校验日志与历史（Req 17/18/19）

- **FR-H1（Req 17）**：校验日志表格化展现，失败参数逐条可溯（比对批次 → 参数明细两级下钻）。
- **FR-H2（Req 18）**：Validate Log 汇总统计（按设备/配方/时间段/结果/差异类型聚合）。
- **FR-H3（Req 19）**：配方对象历史查询——时间范围、最大返回笔数可设；操作/状态/参数三级历史；复用平台 `{X}`↔`{X}Hist` 同构约定。

### 4.8 配方-机台资格矩阵（业界核心，原始协议未显式提出——行业补充）

> 业界（Applied E3 RMS 等）把「哪个配方允许跑在哪台机台」作为 RMS 的**核心管控点**：它区别于 Req 11 的「用户↔设备」授权（人视角），是**配方↔设备**的运行资格（物视角）。缺失此能力的 RMS 只能做档案库，管不住生产行为。

- **FR-Q1**：资格矩阵维护——配方（UID/版本策略）×设备（含机型/区域分组）的授权运行关系，逐条含生效期与审批依据。
- **FR-Q2**：**默认拒绝**——未登记资格的配方在机台运行时，IF-M1 校验/IF-E3 在线比对链路直接返回 `NOT_QUALIFIED` 并可选报警。【决策点 D8】
- **FR-Q3**：资格与配方版本联动——新版本生效后默认继承原版本资格（需审批的机型可配置为重新认定）；资格吊销即时生效并通知 EAP。
- **FR-Q4**：资格矩阵变更全量审计（谁/何时/哪台机/哪个配方的资格被授予或吊销）。

### 4.9 配方安全与 IP 保护（行业补充）

> 配方即工厂核心 IP（工艺窗口泄露 = 竞争力损失），业界对 Body 的访问控制远严于普通数据。

- **FR-SEC1**：Body **静态加密存储**（按机型/分组可配开关），密钥管理走平台 KMS/配置中心，库内不落明文。【决策点 D9】
- **FR-SEC2**：Body **明文查看受控**——默认仅展示参数快照（结构化），查看/下载原始 Body 需专门权限（`rms:body:view`/`rms:body:export`）+ 导出审批，全量留痕。
- **FR-SEC3**：防篡改证据链——Body 存档 HASH + 关键操作历史不可变（复用 `{X}Hist`），支持审计期完整性校验。

### 4.10 报表与看板（行业补充）

- **FR-RPT1**：配方 KPI 看板——配方使用率、版本变更频率、比对通过率、漂移检出数、Hold/豁免存量，按机型/区域/时间聚合（概览页直接呈现）。
- **FR-RPT2**：明细报表可导出（资格矩阵现状、Bypass 清单、签核时效统计）。

---

## 5. 系统间接口设计（业界惯例对齐）

### 5.1 集成拓扑

```
MES ──(Run货校验/指派)──┐
                        ▼
报警系统 ◄──(Alarm事件)── RMS ──(签核发起/回调)── 外部签核系统(BPM)
                        ▲
                        │ (上传/下载/在线比对/选配方事件)
IAM ◄──(登录/准入/JWKS)── EAP ──(HSMS/E37: S7系列·S2F41·S5·S1)── 设备
                        │
                        └──(只读开放API·预留)── FDC/SPC/报表平台（消费配方元数据与Spec基线）
```

- 业界惯例：**RMS 不与设备直连**，SECS-II 事务（S7F1–F6、S2F41 等）全部由 EAP 终结；RMS↔EAP 走**异步消息 + 命令回执**，RMS↔MES 走**服务调用或事件**，RMS↔Alarm/签核走**事件/回调**。
- **FDC/SPC 联动（行业补充，本版只预留）**：业界 RMS 常向 FDC 提供参数 Spec 基线供建模。本版仅提供**版本化只读 REST 开放 API**（配方元数据/参数快照/资格矩阵查询，服务令牌接入），不做双向集成；FDC 深度联动留待后续版本。
- 传输与可靠性统一基于 **cim-mq-starter**：关键链路走 outbox 事务发件箱（平台 §12）、消费幂等（§16，`eventId` 去重）、同 key 有序（`sendOrdered`，按 `TOOL_ID`/`aggregateId` 分区保序）、重试/死信（§13）。

### 5.2 接口通用规范（IF-COM，所有接口必须遵守）

| 项 | 约定 | 业界依据/平台落点 |
| --- | --- | --- |
| 消息封套 | 复用平台 `IntegrationEvent` 信封：`id`（幂等键）、`topic`、`type`、`aggregateType/Id`、`version`、`payloadType`、`payload`（JSON）、`headers`（traceId/tenantId/userId）、`occurredAt` | 平台 §4.3 |
| 幂等 | 每消息 `id` 全局唯一；消费端 `eventId` 去重；状态类用 `(aggregateId, version)` 乐观去重 | 平台 §16 |
| 顺序 | 同设备/同配方的消息以 `TOOL_ID`/`RECIPE_UID` 为 key 走 `sendOrdered` | 平台 §4.2 |
| 可靠 | 业务落库与 outbox 同本地事务；broker ack 确认；失败指数退避 + 死信人工干预 | 平台 §12/§13 |
| 时间 | ISO 8601 带时区（UTC 存储，展示按用户时区）；全链路 NTP | 业界惯例 |
| 编码 | UTF-8；Body 二进制用 Base64 封装 | 业界惯例 |
| 完整性 | Body 级字段必带 `BODY_HASH`（SHA-256），接收端强校验 | 防传输损伤（对应 S7 上传后回读校验惯例） |
| 安全 | 服务间 mTLS 或网关签发的服务令牌；字段最小披露；回调验签 | 业界惯例 |
| 可观测 | `traceId` 贯穿（平台 OTel 注入消息头）；每接口定义超时/重试预算与 SLA | 平台 §14 |
| 版本化 | `type` 携带语义版本（如 `RecipeDownloadRequested@v1`），只增字段不破坏向后兼容 | 业界惯例 |
| 审计 | 每笔接口调用留痕（发起方/时间/关键业务键/结果），接入 FR-H3 查询 | Req 19/12 |

### 5.3 RMS ↔ EAP（核心接口，字段级）

> 事件命名按平台 `type` 语义；`payload` 字段表如下。SECS 映射列为 EAP 侧封装参考（RMS 不感知 SECS 细节）。

**IF-E1 配方上传（EAP → RMS）**：设备 Body 采集入库（Req 1/2）

| payload 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `toolId` / `toolType` | string | 是 | 设备标识/机型（决定格式适配器） |
| `ppid` | string | 是 | 机台侧配方名（Process Program ID） |
| `recipeUid` / `recipeVersion` | string | 否 | 指定入库目标（新建上传时为空） |
| `bodyFormat` | enum | 是 | `BINARY` / `TEXT` / `JSON_STRUCTURED` |
| `bodyBase64` | string | 是 | Body 内容（Base64） |
| `bodyHash` | string | 是 | SHA-256 |
| `uploadReason` | enum | 是 | `MANUAL` / `ONLINE_EVENT` / `DRIFT_AUDIT` / `FIRST_REGISTRATION` |
| `opId` | string | 是 | 发起人/系统身份 |
| `correlationId` | string | 是 | 关联请求（回执用） |

回执 `RecipeUploadResult`：`correlationId`、`result`（`ACCEPTED`/`PARSE_FAIL`/`HASH_MISMATCH`/`PARAM_CHECK_FAIL`）、`recipeUid`+`version`（新建建档返回）、`paramCheckReport`（FR-C5 失败参数列表）。
SECS 映射：S7F19/F20（PPID 目录）→ S7F5/F6（Body 上传）。

**IF-E2 配方下载（RMS → EAP → 设备）**（Req 40）

| payload 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `toolId` | string | 是 | 目标机台 |
| `ppidOnTool` | string | 是 | 机台侧目标配方名（与 RMS UID 的映射关系在 RMS 维护） |
| `recipeUid` / `version` | string | 是 | RMS 侧配方与版本 |
| `bodyFormat` / `bodyBase64` / `bodyHash` | 同上 | 是 | Body 及完整性 |
| `downloadMode` | enum | 是 | `IMMEDIATE` / `ON_IDLE` / `BATCH`（Req 40：Idle 态下载、批量下载） |
| `batchId` | string | 批量必填 | 批次号（批量逐台回执聚合） |
| `lotId` | string | 否 | Run 货联动场景 |
| `requestor` | string | 是 | 用户/EAP 请求上下文 |

进度/结果 `RecipeDownloadProgress`：`correlationId`、`stage`（`QUEUED`→`GRANTED`→`SENT_TO_TOOL`→`TOOL_ACKED`→`VERIFIED`/`FAILED`）、`toolAck`（S7F4 ACKC7 语义）、`result`、`errorDesc`。
SECS 映射：S7F1/F2（Load Inquire/Grant，容量许可）→ S7F3/F4（下载+ACK）→（可选）S7F5/F6 回读比对 HASH（VERIFIED 依据）。
可靠性：EAP 离线时消息在队列保留，重试/超时预算按 `downloadMode` 配置（FR 分发受控）。

**IF-E3 在线比对请求（EAP → RMS）**（Req 21）

| payload 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `toolId` / `lotId` | string | 是/否 | 设备与批次上下文 |
| `ppidOnTool` | string | 是 | 机台当前配方（GEM `PPExecName` 语义） |
| `bodyBase64` / `bodyHash` | string | 二选一 | 机台 Body（或仅 HASH 快比对） |
| `cmpMode` | enum | 是 | `FULL_BODY` / `PARAMETER` / `STRUCTURE` |
| `baseline` | object | 是 | 基准：`ACTIVE`（生效版）/ `GOLDEN` / 指定版本 |
| `bizCtx` | object | 否 | `productId`/`processId`（MES 请求时携带） |

回执 `OnlineCompareResult`：`correlationId`、`cmpResult`（`PASS`/`FAIL`/`BYPASSED`）、`diffSummary`（失败参数明细，FR-C3 结构）、`holdApplied`、`alarmRaised`、`durationMs`。
超时契约：EAP 侧等待回执超时可配（建议 3–10s），超时行为（放行/Hold）由 EAP 配置并审计——业界惯例是比对引擎异步化、设备侧不等长时。
SECS 来源：S2F41 `PP-SELECT` 选配方事件、GEM Process Program 事件（Selected/Loaded）、S1F3 查询 `PPExecName`。

**IF-E4 配方选择事件（EAP → RMS）**：`RecipeSelected { toolId, lotId, ppidOnTool, opId, occurredAt }`——审计与触发在线比对的钩子。

**IF-E5 EC/SV 上传与校验（EAP ↔ RMS）**（Req 43/45）：同 IF-E1/E3 模式，payload 增加 `ecsvType`（`EC`/`SV`）、`svValue`、基准 `specScope`（`RECIPE`/`PRODUCT`，对应 FR-D3）。

### 5.4 RMS ↔ MES（Req 21/40）

**IF-M1 Run 货配方校验（MES → RMS）**

| payload 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `lotId` / `productId` | string | 是 | 批次/产品 |
| `processOperation` | string | 是 | 工序/站别 |
| `toolId` | string | 是 | 指派机台 |
| `recipeRef` | object | 是 | `recipeUid`+`versionPolicy`（`LATEST_ACTIVE`/`EXACT_VERSION`）/`ppidOnTool` 三选 |
| `verifyRequired` | bool | 是 | 是否强制比对通过 |
| `requestor` | string | 是 | 用户/系统 |

应答 `RecipeCheckResult`：`checkResult`（`QUALIFIED`/`NOT_QUALIFIED`（资格矩阵拦截，FR-Q2）/`HOLD`/`NOT_FOUND`/`VERSION_MISMATCH`）、`activeVersion`、`diffHint`（失败摘要）、`resultAt`。
**IF-M2 配方指派审计**：MES 侧 LOT↔Recipe 绑定变更以事件同步 RMS 留痕（不反向主数据管理）。

### 5.5 RMS → 报警系统（Req 21/45）

**IF-A1**：`RmsAlarmRaised { alarmId, severity(CRITICAL/MAJOR/MINOR), alarmCode(如 RMS_COMPARE_FAIL/RMS_DRIFT/RMS_HOLD), toolId, lotId?, recipeUid/version?, textI18nCode + args, correlationId, occurredAt }`——`alarmCode` 段位集中登记，语义对齐设备报警体系（SECS S5F1 ALID 思路），支持报警系统去重/升级联动。

### 5.6 RMS ↔ 外部签核系统（Req 41）

**IF-W1 发起/回调契约**：发起 `SignRequestCreated { signRequestId, recipeUid, fromVersion, toVersion, changeSummary, paramDiffRef, signRoute(会签/或签/多级), initiator, callbackUrl, expireAt }`；回调 `SignDecision { signRequestId, decision(APPROVE/REJECT), approver, decisionAt, comment, eSignEvidence }`。
客户端要求：回调强制**验签**（签名头/证书指纹）、`signRequestId+decision` 幂等去重、超时催办与代理审批、决策不可篡改存档（电子签证据链，对齐 21 CFR Part 11 精神）。

### 5.7 RMS ↔ IAM（统一登录，对齐 iam-ap 现状）

- **IF-I1**：登录/刷新/登出走 IAM（`/api/v1/login` 等），RMS 前端持有令牌；本地 JWKS 验签，`apps` 准入 + `ver`/黑名单校验链复用 `cim-auth-starter`（验签→版本失效 401→黑名单 401→`apps` 准入 403→加载权限）。
- **IF-I2**：RMS 菜单/按钮权限码在 RMS 侧 RBAC 自管（复用 `cim-system` 的 `sys_*` 能力）；IAM 只管「能否进 rms-ap」。
- **IF-I3**：设备级授权数据（FR-P4）为 RMS 私有，不进 JWT（铁律：姓名/部门/业务授权不塞令牌）。

### 5.8 接口客户端要求（对接方约束，写入集成手册）

| 对接方 | 协议 | 认证 | 必备能力 |
| --- | --- | --- | --- |
| EAP 客户端 | MQ（cim-mq-starter 三选一）+ 网关 REST（同步查询类） | 服务令牌/mTLS | HSMS(E37) 链路管理（Select/心跳/重连）、S7/S2 事务超时可配、大 Body 传输预算（T3/T5 调整）、设备离线时消息暂存与重放、`correlationId` 全链路透传 |
| MES 客户端 | REST/事件 | 服务令牌 | 同步校验接口的重试与降级（RMS 不可用时的策略显式配置）、批量校验 |
| 报警系统 | 事件订阅 | 服务令牌 | `alarmCode` 分级路由、去重键（`correlationId`） |
| 签核系统 | REST 回调 | 验签 | 幂等、超时催办、证据存档 |
| 通用 | — | — | NTP 时钟同步、ID 全局唯一、UTF-8、ISO 8601、schema 版本协商（v1 起步） |

---

## 6. 非功能需求（Req 49–53 + 补充）

- **NFR-1（Req 49/52/53）**：无状态多实例 + LB 水平扩展；会话/状态外置（Redis），MQ 消费组多实例分区并行。
- **NFR-2（Req 50/51）**：不停机发布（滚动升级）；新机型/新设备上线为**配置与适配器注册**，不改核心代码（机型适配器 SPI）。
- **NFR-3（性能）**：在线比对 P95 ≤ 3s（Body≤5MB，参数级）；批量巡检 100 台×HASH 级 ≤ 5min；配方查询 P95 ≤ 500ms（通配符走索引+授权裁剪下推）。
- **NFR-4（容量）**：Body 存档不动域库主表（对象存储或大对象表），参数快照结构化入 PG；历史表分区策略设计阶段定。
- **NFR-5（安全/审计）**：口令/令牌体系全复用 IAM；敏感操作（Bypass/Hold/预设放行/设备授权）双留痕（业务表 + 审计历史）。
- **NFR-6（多租户）**：全业务实体挂 `cimTenantFilter`（`@Filter` 只标注，`@FilterDef` 用平台唯一声明点，ADR-12）。
- **NFR-7（备份与 DR，行业补充）**：RMS 属生产关键系统——Body 存档与库数据异地/离线备份，目标 **RPO ≤ 15min / RTO ≤ 1h**；每季度恢复演练；Body 为不可变存档（append-only），天然利于增量备份。
- **NFR-8（跨厂区预留，行业补充）**：业界成熟 RMS 均有 Multi-Fab 演进路径——数据模型自始携带厂区/站点维度（复用租户体系或独立 site 字段），本版不做跨站同步，但**实体设计禁止写死单厂假设**。

---

## 7. 前端与 UI（参考 iam-ap 现行实现）

- **UI-1 外壳**：复用 iam-ap 四段式外壳（header + 可收缩分组侧栏 + 内容区 + footer），设计令牌全部走 `styles.css` 顶部 CSS 变量（浅/深双主题，勿硬编码色值），页签导航（tags-view：聚合、右键菜单、滑动指示器）同款复用。
- **UI-2 信息架构**（分组 → 页面）：概览 / **配方管理**（配方库、版本与状态、签核待办、Golden、Spec 模板）/ **比对中心**（发起比对、差异报告、校验日志、Bypass 管理）/ **设备管理**（设备台账、类型、区域、EC/SV Spec）/ **权限管理**（角色、功能授权、设备授权、操作历史）/ 我的。
- **UI-3 权限组件**：`<Perms codes={[...]}>` + 页级 `useHasAllPermissions`，与 iam-ap 双闸门模式一致（数据面 `sys:*`、入口 `rms:*` 时 AND 组合）。
- **UI-4 i18n**：文案键命名空间 `rms.*`，四级兜底与整包合并策略复用 iam-ap 方案（`sys_locale`/`sys_i18n`，zh-CN 强制兜底，本地缓存存整包）。
- **UI-5 关键交互**：差异报告左右分栏对照（参数级高亮）；批量操作逐项结果反馈；配方状态机可视化（版本时间线 + 状态徽标）；大参数表虚拟滚动 + 行内校验。
- **UI-6 双前缀约定**：RMS 业务端点 `/api/v1/**`；若复用平台 `sys:*` 端点则 `/sys/**`，axios 双实例（`http`/`rootHttp`）沿用 iam-ap 模式。

---

## 8. 平台能力复用与工程约束（设计红线）

| 主题 | 约束 |
| --- | --- |
| 工程 | 独立工程 `business/rms-ap/{server,web}`，无聚合 pom，`cd` 构建；端口 server 8084 / web 5174 |
| 装配 | 域模块组件扫描 + 自带 `@EntityScan`/`@EnableJpaRepositories`；扫描根 `com.cim.rms.*` 不与平台重叠（ADR-11） |
| 数据 | 主键 `IdGenerator` 雪花；Flyway 段位先在 `cim-jpa-starter` 迁移 README 登记（宿主 V1–999 段） |
| 消息 | EAP/MES/Alarm/签核全走 `cim-mq-starter`（outbox/幂等/有序/死信），业务代码不出现具体 MQ SDK |
| 韧性 | 外呼 Resilience4j（平台 §13）；比对引擎异步化 + 超时降级 |
| 租户 | ADR-12：`@FilterDef` 只在 `com.cim.system.package-info` 声明，RMS 实体只标 `@Filter` |

---

## 9. 决策拍板项（待确认）

| # | 决策点 | 建议方案 |
| --- | --- | --- |
| D1 | 在线比对超时后默认行为（放行/Hold） | Hold（宁严勿漏），可按设备配置白名单 |
| D2 | 版本回退语义 | 回退=以历史内容生成新版本走签核（不直接激活旧版） |
| D3 | Golden 机差覆盖层的管控强度 | 覆盖层变更走签核（等同配方变更） |
| D4 | Body 大对象存储位置 | 对象存储（MinIO/云 OSS）+ 库内 HASH/元数据；备选：独立大对象表 |
| D5 | MES↔RMS 同步调用 or 纯事件 | 校验类同步 REST（P95 预算内）、审计类事件 |
| D6 | 设备间复制跨机型策略 | 默认拒绝，显式确认 + 记录强审计 |
| D7 | RMS 是否维护「机台 PPID ↔ RMS UID」映射 | 是（RMS 维护映射表，EAP 只认机台 PPID） |
| D8 | 资格矩阵默认策略（FR-Q2） | 未登记资格的配方在机台运行默认拒绝（NOT_QUALIFIED）；已有产线数据迁移期可配白名单过渡 |
| D9 | Body 静态加密策略（FR-SEC1） | 按机型分组开关；密钥走平台 KMS；优先对高敏机型启用 |

---

## 10. 追溯矩阵（原始 53 条 → 细化需求）

| 原始条目 | 细化落点 |
| --- | --- |
| 1–6 | FR-F1~F4、IF-E1/IF-E2、§5.3 |
| 7–12 | FR-P1~P6、§2、IF-I1~I3、UI-3 |
| 13–15 | FR-L1、§3.1（主/子配方层级） |
| 16/30/31 | FR-G1~G3 |
| 17/18/19 | FR-H1~H3 |
| 20 | FR-L5 |
| 21/24/26/28 | FR-C6~C8、FR-C3、IF-E3、IF-M1、IF-A1 |
| 22/23 | FR-B1~B3 |
| 25/33 | FR-C2、FR-C8 |
| 27 | FR-C2（版本↔版本） |
| 29/34/35/36 | FR-S1/S2/S4 |
| 32 | FR-S3 |
| 37/38/39 | FR-C1/C3/C5 |
| 40 | IF-E2（downloadMode） |
| 41 | FR-L4、IF-W1 |
| 42 | FR-L3 |
| 43/44/45 | FR-D2~D4、IF-E5 |
| 46/47/48 | FR-D1 |
| 49–53 | NFR-1/NFR-2、§8 |
| （行业补充，超出原始协议） | FR-Q1~Q4（资格矩阵）、FR-SEC1~3（IP 保护）、FR-RPT1~2（报表）、NFR-7（备份 DR）、NFR-8（跨厂区预留）、FDC 开放 API 预留 |

---

## 附录 A：原始技术协议（53 条，原样保留）

| 序号 | 项目 | 功能描述 |
| --- | --- | --- |
| 1 | 配方(Recipe) 管理基本功能 | 创建配方参数列表(Recipe List)，通过设备自动化(EAP) 整合，从设备上传 Recipe List |
| 2 | 配方(Recipe)管理基本功能 | 创建配方(Recipe)，通过 设备自动化(EAP) 整合，从设备上传 配方主体(Recipe Body)，支持批量上传，设备间复制 |
| 3 | 配方(Recipe)管理基本功能 | 变更配方Recipe 状态，支持批量修改，支持批量签核，批量生效 |
| 4 # | 配方(Recipe)管理基本功能 | 查询配方Recipe，支持用通配符查询 |
| 5 | 配方(Recipe)管理基本功能 | 删除配方Recipe, 在系统中删除 Recipe |
| 6 | 配方(Recipe)管理基本功能 | 复制配方Recipe，提供"另存为"的功能 |
| 7 | 用户(权限)管理 | 角色维护(按区域、类型等创建角色,并赋予相应的系统操作权限) |
| 8 | 用户(权限)管理 | 用户维护(创建可登录，操作系统的用户) |
| 9 | 用户(权限)管理 | 用户角色授权(给用户定义角色，让其有相应的操作权限) |
| 10 | 用户(权限)管理 | 支持对操作功能设置权限 |
| 11 | 用户(权限)管理 | 支持对设备设置权限(用户只能看到自己对应的设备，操作对应的设备) |
| 12 | 用户(权限)管理 | 完善权限管理机制和历史记录, 支持设备/Recipe 不同级别的权限管控 |
| 13 | 配方(Recipe) 生命周期管理 | 新创建(Draft)版本，中间版本(可以修改，等待签核)，生效版本 |
| 14 | 配方(Recipe) 生命周期管理 | 配方Recipe 在系统里可以存在多个版本，最多有一个生效版本 |
| 15 | 配方(Recipe) 生命周期管理 | 配方Recipe 类型可分为主 Recipe 和子 Recipe, 支持多层次的配方Recipe，不限 Recipe 层级 |
| 16 | 配方(Recipe) 生命周期管理 | 支持黄金配方Golden Recipe 功能 |
| 17 | 配方(Recipe) 生命周期管理 | 支持校验日志表格展现失败参数 |
| 18 | 配方(Recipe) 生命周期管理 | 支持验证纪录(Validate log)进行各类型校验结果汇总统计 |
| 19 | 配方(Recipe) 生命周期管理 | 支持配方 Recipe 对象历史查询 (可设定条件如时间范围, 最大查询笔数),可以查询历史变化 |
| 20 | 配方(Recipe) 生命周期管理 | 支持预设一个审核中未激活的 Recipe 可以使用，不过使用需设置时限条件，超时后该 Recipe 恢复不可使用状态；如果审核通过，则该 Recipe 的预设设置可自动取消(用于特殊场景下需要使用 未审核激活的 Recipe 的情况) |
| 21 | 配方(Recipe)管理 | 在线Online Run 货时，根据 设备自动化(EAP) 或制造执行系统(MES) 请求比对配方主体(Recipe Body)。如果比对失败，可以按配置Hold Recipe，发 Alarm 给Alarm 系统 |
| 22 | 配方(Recipe)管理 | 略过(Bypass) 支持：可以 Bypass 一台设备所有 Recipe 的对比或参数检查 |
| 23 | 配方(Recipe)管理 | 略过(Bypass) 支持：可以 Bypass 单个 Recipe 的对比或参数检查 |
| 24 | 配方(Recipe)管理 | 记录比对失败的具体参数的信息 |
| 25 | 配方(Recipe)管理 | 不同设备间相同 Recipe 的比对(包含设备在配方管理系统(RMS) 上的 Recipe 对比，以及设备本地的 Recipe 对比) |
| 26 | 配方(Recipe)管理 | 设备配方(Recipe) 与配方管理系统(RMS) 上的 Recipe 比对，支持一次性批量对比 |
| 27 | 配方(Recipe)管理 | 同一配方Recipe 的不同版本间的比对 |
| 28 | 配方(Recipe)管理 | 对比可以清楚显示比对结果，列出差异项目 |
| 29 | 配方(Recipe)管理 | 支持多种 Recipe 参数比对方式：1. target (目标值/绝对值) 2. range (数值范围) 3. list/in (数值列表) 4. tolerance (比例范围) |
| 30 | 配方(Recipe)管理 | 支持黄金配方(Golden Recipe)，同型号设备，同一配方(recipe)可共享同一Golden Recipe，使用Golden Recipe进行程序的验证比对 |
| 31 | 配方(Recipe)管理 | 黄金配方(Golden Recipe)需要能够兼容设备机差情况 |
| 32 | 配方(Recipe)管理 | 支持机型配置配方(Recipe)新版本可继承Active版本Spec |
| 33 | 配方(Recipe)管理 | 支持工厂(FAB)中两个真实设备中程序差异比较 |
| 34 | 配方(Recipe)管理 | 可以按参数设定是否进行某个参数的比对 |
| 35 | 配方(Recipe)管理 | 可以定义 Spec Template，应用到相应的配方(Recipe)，简化参数 Spec 的设定 |
| 36 | 配方(Recipe)管理 | 配方(Recipe) 参数修改的容忍范围要可以定义 percentage 和固定数值 |
| 37 | 配方(Recipe)管理 | 支持 完整内容(Full Body), 参数(Parameter) 比对方式 |
| 38 | 配方(Recipe)管理 | 可以支持对配方(Recipe) 结构的比对 |
| 39 | 配方(Recipe)管理 | 配方(Recipe) 上传时，对参数自动检查 |
| 40 | 配方(Recipe)管理 | 配方(Recipe) 下载：Run 货时收到 设备自动化(EAP) 请求，自动下载指定 Recipe，从配方管理系统(RMS) 到机台； 设备空闲(Idle) 状态下，下载 Recipe 到指定机台，支持批量下载 |
| 41 | 配方(Recipe)管理 | 具备签核功能，以及与外部签核系统集成的能力。在进行 Recipe parameter 设定后支持发起签核流程 |
| 42 | 配方(Recipe)管理 | 完善的版本控制, 更新配方主体(Recipe body) 生成新版本,可回退到历史版本 |
| 43 | 设备管理 | 支持设备常量(EC)，设备状态(SV)上传和比对，支持比对方式- target, range，tolerance |
| 44 | 设备管理 | 能够支持按配方(Recipe)，产品(Product) 进行(设备常数/设定值)EC/SV Spec 的设定 |
| 45 | 设备管理 | 在线Online Run货时，设备自动化(EAP)主动校验，记录比对失败的具体信息 |
| 46 | 设备管理 | 设备类型基础设置管理(查询、新建、修改、删除) |
| 47 | 设备管理 | 设备区域管理(查询、新建、修改、删除) |
| 48 | 设备管理 | 设备基础设置管理(查询、新建、修改、删除) |
| 49 | 高可用 | 要求系统高可用性，负载平衡 |
| 50 | 高可用 | 软件升级和新功能发布不影响系统使用 (不停机) |
| 51 | 高可用 | 新的设备类型或新的设备上线，不影响系统使用(不停机) |
| 52 | 高可用 | 工厂产能、设备增加，可以动态增加服务器，不影响系统使用(不停机) |
| 53 | 高可用 | 多台服务器间可以实现负载平衡，自动故障切换 |
