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

流程结束（批准/驳回）时，审批平台向回调注册表中该 `appCode`（= 发起时的 `tenantId`）登记的 URL 推送：

```json
{
  "eventType": "PROCESS_COMPLETED",
  "processInstanceId": "0ded55ab-...",
  "businessKey": "MES-2026-0031",
  "result": "APPROVE",
  "tenantId": "mes-ap",
  "completedAt": "2026-10-15T06:20:00Z"
}
```

- 可靠性：DB Outbox + 失败重试 + 死信；消费方需按 `businessKey` **幂等**处理。
- 安全：`X-Callback-Signature`（HMAC-SHA256），用 SDK 的 `CallbackVerifier` 验签。
- 回调只带结论不带表单：业务方如需表单快照，凭 `businessKey` 回查详情接口，或在自己侧于发起前留存。

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
