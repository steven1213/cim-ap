# Approval-AP 集成对接指南（审批内容契约）

> 面向外部业务系统（MES/RMS/QMS 等）对接审批流的开发人员。
> 2026-10-15 定稿，与 `FormSchemaField`（后端 DTO）与详情页 schema 渲染实现保持一致。

---

## 1. 总览：一次审批对接要传什么

| 事项 | 传递方式 | 必填 |
|---|---|---|
| 走哪个流程 | `processKey`（发起走同 key 最新版本） | ✅ |
| 业务单据标识 | `businessKey`（全局唯一，回调/附件/审计均以它关联） | ✅ |
| 审批标题 | `title`（列表与详情主标题） | ✅ |
| 租户/业务系统标识 | `tenantId`（即 appCode，决定回调路由与列表隔离） | ✅ |
| 发起人 | `initiator`（可见性判定依据） | ✅ |
| 审批人 | `assignee`（单级）或 `approvers`（会签）或 `variables`（自定义路由） | 三选一 |
| **审批内容** | `formData`（数据本体）+ `formSchema`（展示描述，推荐） | ✅ / 推荐 |
| 附件 | 发起后调用附件上传接口，按 `businessKey` 关联 | 可选 |

**核心原则：`formData` 只传数据，`formSchema` 告诉审批平台怎么展示。**
两者分离意味着：业务系统不必为展示格式做任何妥协，审批平台也不必理解业务字段含义。

---

## 2. 发起接口

```
POST /api/v1/approval/processes/start
Content-Type: application/json
```

### 2.1 最简对接（纯 Map，快速跑通）

只传 `formData`，详情页以「原始键值表」展示——键即字段名，值原样字符串化。

```json
{
  "processKey": "singleApproval",
  "businessKey": "MES-2026-0031",
  "tenantId": "mes-ap",
  "initiator": "zhang.san",
  "title": "工单 WO-88 的偏差放行申请",
  "assignee": "li.si",
  "formData": {
    "工单号": "WO-88",
    "偏差描述": "尺寸 12.02mm 超上差 0.01mm",
    "放行理由": "不影响装配功能"
  }
}
```

返回：

```json
{ "processInstanceId": "0ded55ab-c85c-..." }
```

> ⚠️ 该模式下键名直接展示给审批人。请传**业务可读的中文字段名**，不要传 `field_01` 这类内部标识。

### 2.2 标准对接（formData + formSchema，推荐）

`formSchema` 是字段描述数组，详情页据此渲染为**分组、中文标签、类型化格式**的审批内容视图。结构化展示是长期演进的唯一路径——新增字段只改数据不改页面。

```json
{
  "processKey": "singleApproval",
  "businessKey": "MES-2026-0031",
  "tenantId": "mes-ap",
  "initiator": "zhang.san",
  "title": "偏差放行申请",
  "assignee": "li.si",
  "formData": {
    "woNo": "WO-88",
    "deviation": "尺寸 12.02mm 超上差 0.01mm，涉及批次 B-20261015",
    "qty": 120,
    "estimatedLoss": 3500.5,
    "urgent": false,
    "planDate": "2026-11-05"
  },
  "formSchema": [
    { "key": "woNo",        "label": "工单号",   "type": "text",  "group": "偏差信息", "order": 1 },
    { "key": "deviation",   "label": "偏差描述", "type": "textarea", "group": "偏差信息", "order": 2 },
    { "key": "qty",         "label": "涉及数量", "type": "number", "unit": "件", "group": "偏差信息", "order": 3 },
    { "key": "urgent",      "label": "是否加急", "type": "boolean", "group": "偏差信息", "order": 4 },
    { "key": "estimatedLoss", "label": "预估损失", "type": "money", "group": "处置与损失", "order": 5 },
    { "key": "planDate",    "label": "计划处理日期", "type": "date", "group": "处置与损失", "order": 6 }
  ]
}
```

#### formSchema 字段说明

| 属性 | 必填 | 说明 |
|---|---|---|
| `key` | ✅ | 字段标识，必须与 `formData` 的键一致；建议用稳定英文标识符（`estimatedLoss`），不要用会变的中文 |
| `label` | 推荐 | 展示标签（中文业务名）；缺省回退展示 key |
| `type` | 推荐 | 渲染类型，见下表 |
| `unit` | 可选 | 单位后缀（`件`/`台`/`kg`），仅展示 |
| `group` | 可选 | 分组名，详情页按组分区展示；缺省归入「其他信息」 |
| `order` | 可选 | 组内展示顺序，小者靠前 |
| `required` | 可选 | 预留，仅语义说明 |

#### type 类型约定与展示效果

| type | 业务系统应传的值 | 详情页展示 |
|---|---|---|
| `text` | 字符串 | 原样 |
| `textarea` | 长文本字符串（`<br/>` 换行写 `\n`） | 独占整行，保留换行 |
| `number` | 数值（不要带千分位/单位） | `toLocaleString` 千分位 + unit 后缀 |
| `money` | **数值，单位元**（860000，不是 "86万"） | `¥ 860,000.00` |
| `boolean` | `true` / `false` | 是 / 否 |
| `date` | `yyyy-MM-dd` 字符串 | `yyyy-MM-dd` |
| `datetime` | ISO 8601 字符串 | 本地化完整时间 |
| `list` | JSON 数组 | 顿号连接 |

> ⚠️ **金额只传数值、单位固定为元**。不要传 `"¥3,500"` 之类带符号的字符串，否则无法参与后续统计。
> ⚠️ `formData` 中存在但 `formSchema` 未声明的键不会丢失——详情页追加到「其他信息」组兜底展示。`formSchema` 中声明但 `formData` 缺失的键不展示。

### 2.3 回退与兼容

| 场景 | 展示行为 |
|---|---|
| 只传 formData（无 formSchema） | 原始键值表 |
| formData 为空 | 「发起时未填写结构化业务表单」 |
| formSchema JSON 解析失败 | 静默回退键值表，不阻塞发起 |

### 2.4 业务摘要 displayFields（可选，G5）

发起请求可附带 `displayFields`（key-label-value 数组），作为待办卡片/列表/审计的**摘要层**。详见 §8。

- 不传时：详情页仍由 `formSchema`（或键值表）完整渲染；卡片摘要按 §8.3 兜底规则从 `formSchema.display:true` 提取。
- 传了 `displayFields`：卡片/列表直接展示该数组，平台原样渲染 `value`。

---

## 3. 附件对接

审批内容需要携带附件（图纸/照片/PDF 报告）时，**发起后**用 `businessKey` 上传：

```
POST /api/v1/approval/attachments/upload   (multipart/form-data)
    file      : 文件本体
    businessKey : MES-2026-0031
    processInstanceId : （可选）发起接口返回值
    tenantId  : mes-ap
    uploader  : zhang.san
```

约束：单文件 ≤ 20MB；可执行扩展名（exe/bat/sh…）被拒绝；上传/删除均写入审计哈希链。

下载：`GET /api/v1/approval/attachments/{id}/download`，文件名经 RFC 5987 编码支持中文。
删除：`DELETE /api/v1/approval/attachments/{id}?actor=zhang.san`。

审批人「审批内容 → 附件」区可直接预览列表、下载、上传补充材料。

---

## 4. 审批结果回调

流程结束（批准 / 驳回 / 撤回）时，审批平台向回调注册表中该 `appCode`（= 发起时的 `tenantId`）登记的 URL 推送：

```json
{
  "eventType": "PROCESS_COMPLETED",
  "processInstanceId": "0ded55ab-...",
  "businessKey": "MES-2026-0031",
  "result": "APPROVED",
  "tenantId": "mes-ap",
  "completedAt": "2026-10-15T06:20:00Z"
}
```

- 可靠性：DB Outbox + 失败重试 + 死信；消费方需按 `businessKey` **幂等**处理。
- 安全：`X-Callback-Signature`（HMAC-SHA256），用 SDK 的 `CallbackVerifier` 验签。
- 回调只带结论不带表单：业务方如需表单快照，凭 `businessKey` 回查详情接口，或在自己侧于发起前留存。
- ⚠️ `result` 枚举命名见 §7.4 迁移说明：当前运行版实际回传 `APPROVE/REJECT/WITHDRAW`，集成方需兼容两套值。

**Java SDK**（`business/approval-ap/sdk/`）：`ApprovalClient` 封装发起/查询/完成任务；release 17，无 Spring 强依赖。

---

## 5. 最佳实践清单

1. **key 用英文稳定标识，label 用业务中文**——字段重命名只改 label，历史数据展示不受影响。
2. **一条流程一个 businessKey**：附件、回调、审计链都按它聚合；用业务单据号天然幂等。
3. **分组合理粒度**：3~6 组、每组 3~6 字段最易读；避免每组只有 1 个字段。
4. **顺序跟随业务阅读动线**：先单据标识，再事实描述，最后金额/日期。
5. **长文本用 textarea**：超过 40 字符的值详情页自动整行展示。
6. **审批人可读是唯一标准**：`formSchema` 是业务系统对审批人的「展示合同」，上线前用审批人视角过一遍详情页。
7. **不要把附件二进制塞进 formData**：走附件接口，formData 里只放文件名/编号等引用信息。

---

## 6. 自测路径

1. 发起带 formSchema 的实例（见 2.2）。
2. 打开前端「我发起的 → 详情」：应看到分组标题（蓝色分隔线）、中文标签、`¥ 860,000.00` 千分位、`2 台` 单位、`是/否` 布尔。
3. 对照脚本 `web/scripts/verify-form-schema.mjs`（9 项断言）可作回归。

---

## 7. 审批结果语义（G4 闸门出口）

审批流终态由**平台统一写入**，业务方不应自行解读流程变量推断结论，只消费下方契约。

### 7.1 两个字段的区分

| 字段 | 含义 | 取值 | 写入时机 |
|---|---|---|---|
| `status` | 流程实例**生命周期态** | `ACTIVE` / `COMPLETED` / `WITHDRAWN` | 详情/列表接口返回，反映实例是否还在跑 |
| `result` | 终态**业务结论** | `APPROVED` / `REJECTED` / `WITHDRAWN` | 末级审批或撤回发生时由平台落库 |

> 中间态（退回 `RETURNED`、重提 `RESUBMITTED`）**不进入 `result`**——它们发生在 `RUNNING` 周期内，不是终态，见 §9。

### 7.2 result 取值表

| result | 触发 | 业务语义 |
|---|---|---|
| `APPROVED` | 末级审批通过（`outcome=APPROVE`） | 审批通过，业务方据此放行 |
| `REJECTED` | 末级审批驳回（`outcome=REJECT`，意见必填） | 审批不通过，流程终止 |
| `WITHDRAWN` | 发起人撤回 | 发起人主动终止，流程已结束 |

### 7.3 回调 payload（终态统一出口）

流程到达任一终态时，回调注册表中该 `tenantId`（=appCode）的 URL 收到：

```json
{
  "eventType": "PROCESS_COMPLETED",
  "processInstanceId": "0ded55ab-...",
  "businessKey": "MES-2026-0031",
  "result": "APPROVED",
  "tenantId": "mes-ap",
  "completedAt": "2026-10-15T06:20:00Z"
}
```

- `eventType` 恒为 `PROCESS_COMPLETED`——批准、驳回、撤回**共用同一事件类型**，靠 `result` 区分。
- 撤回同样走此出口，`result=WITHDRAWN`（流程实例以 `deleteReason=WITHDRAW` 落历史）。
- 可靠性：DB Outbox + 失败重试（最多 6 次，5s 轮询）+ 死信；`messageId` 幂等，消费方按 `businessKey` 幂等处理。
- 安全：`X-Callback-Signature`（HMAC-SHA256），用 SDK `CallbackVerifier` 验签。

### 7.4 ⚠️ 枚举命名迁移说明（重要）

本文契约以**过去分词式** `APPROVED / REJECTED / WITHDRAWN` 为权威命名（对齐框架 G4）。

**当前运行版（v1.x）代码实际回写 `APPROVE` / `REJECT` / `WITHDRAW`**（BPMN 结束事件 `execution.setVariable('result','APPROVE')` 与撤回常量）。二者仅词尾差异，语义一致。

> **集成方须知**：M3 落地时将统一重命名（BPMN expression + 撤回常量 → 过去分词式）。过渡期内，消费端**应同时兼容两套值**，直至平台发布迁移完成公告。建议解析逻辑：`value in {APPROVED, APPROVE} → 通过`；`{REJECTED, REJECT} → 不通过`；`{WITHDRAWN, WITHDRAW} → 撤回`。

---

## 8. 业务摘要 displayFields（G5 闸门出口）

### 8.1 三层数据定位

| 层 | 字段 | 用途 | 渲染位置 |
|---|---|---|---|
| 数据本体 | `formData` | 全量业务数据（落库，审计/回查源） | 详情页完整表单 |
| 详情描述 | `formSchema` | 全量字段的中文标签/分组/类型化渲染 | 详情页 |
| **业务摘要** | **`displayFields`** | **卡片/列表/审计摘要**，审批人一眼看到的关键 3~5 项 | 待办卡片、我发起的列表、审计摘要 |

`displayFields` 是**展示合同**：业务系统挑出最该被一眼看到的字段，预渲染好展示值，平台原样渲染，不做二次解析。

### 8.2 契约（发起请求可选字段）

在 `StartProcessRequest` 增加可选 `displayFields`（数组）：

```json
{
  "processKey": "singleApproval",
  "businessKey": "MES-2026-0031",
  "tenantId": "mes-ap",
  "initiator": "zhang.san",
  "title": "偏差放行申请",
  "assignee": "li.si",
  "formData": { "woNo": "WO-88", "estimatedLoss": 3500.5, "applicant": "张三" },
  "displayFields": [
    { "key": "woNo",          "label": "工单号",   "value": "WO-88" },
    { "key": "estimatedLoss", "label": "预估损失", "value": "¥3,500.00" },
    { "key": "applicant",     "label": "申请人",   "value": "张三" }
  ]
}
```

字段说明：

| 属性 | 必填 | 说明 |
|---|---|---|
| `key` | ✅ | 与 `formData` 键一致，用于关联/回查 |
| `label` | 推荐 | 卡片展示中文标签；缺省回退 `key` |
| `value` | ✅ | 业务系统**预渲染的展示值**（已格式化：金额带符号、日期本地化、布尔转「是/否」）。平台原样渲染 |

### 8.3 兜底与截断规则

**兜底优先级**（平台自动选择摘要来源）：
1. 传入 `displayFields` → 直接使用；
2. 未传但传了 `formSchema` → 取 `formSchema` 中标记 `display:true` 的字段（按 `order`）作为摘要；
3. 二者皆无 → 卡片仅显示 `title`，无业务摘要。

**截断规则**：
- 单字段 `value` 展示上限 **24 字符**（中文计 1，英文/数字计 0.5），超出加 `…`；
- 卡片默认展示前 **4** 个摘要字段，其余折叠为「+N」展开。

### 8.4 渲染位置

- 待办卡片「业务信息」行；
- 「我发起的」列表副标题；
- 审计哈希链摘要列（如有）。

> 修改摘要只改 `displayFields` 数组，不影响 `formData` 落库与详情页完整渲染；历史实例的摘要按发起时快照保留。

---

## 9. 退回 / 重提状态机（G4 §2.2 契约出口）

退回是**唯一非终止的打回路径**（驳回=终止，见框架 D4）。其状态机作为 M3 契约出口，供业务方感知「卡在谁那、第几轮」。

### 9.1 状态流转

```
RUNNING(round=n)
  ├─ 末级通过 ──────────────▶ APPROVED    （终态，result=APPROVED）
  ├─ 末级驳回 ──────────────▶ REJECTED    （终态，result=REJECTED）
  ├─ 发起人撤回 ────────────▶ WITHDRAWN   （终态，result=WITHDRAWN）
  └─ 审批人「退回」(意见必填) ─▶ RETURNED(round=n, 中间态)
        └─ 发起人「修改并重新提交」 ─▶ RUNNING(round=n+1)  ──回到上方终态分支
```

- `round`：每次重新提交 +1，**流程定义版本不变**（BR-01）；旧轮次任务全部关闭。
- 轮次上限（默认可配，如 3）：达上限后前端禁用退回、后端 400 双保险（E3）。

### 9.2 中间事件（F20，默认关，按 appCode 开启）

| eventType | 触发 | 默认 |
|---|---|---|
| `TASK_RETURNED` | 审批人退回 | 关 |
| `TASK_RESUBMITTED` | 发起人重新提交 | 关 |

> 中间事件与 §7 终态回调（`PROCESS_COMPLETED`）相互独立：退回/重提若开启 F20 则经中间事件回调暴露；终态仍走 §7 统一出口。其余 `NEW_TASK`/`TASK_DONE`/`TASK_OVERDUE`/`URGE` 见 G7 通知章节。

### 9.3 审计与业务方感知

- 审计动作：`RETURN`（操作人/目标节点/原因/轮次）、`RESUBMIT`（发起人/轮次/表单变更摘要）入哈希链。
- 业务方感知「卡在谁那」：已退回实例 `status` 仍为 `RUNNING`；可通过 F20 中间事件、审计链、或详情接口 `activeTasks`（当前节点 + 轮次）感知。

### 9.4 业务方对接要点

- 不要依据 `result` 判断「是否被打回」——被打回时实例仍在 `RUNNING`，`result` 尚未写入。
- 判断「待修改」：实例 `status=RUNNING` 且最近审计动作含 `RETURN`、无后续 `RESUBMIT`/`APPROVED`。
- 轮次展示：从详情/审计读取 `round`，向发起人呈现「第 N 轮 · 已退回」。
