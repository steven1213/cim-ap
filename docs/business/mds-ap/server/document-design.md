# MDS 受控文档（Document / SOP / Spec）主数据设计

> 本文承接[工艺路线](route-design.md)、[设备建模](equipment-design.md)、[配方](recipe-design.md)、[变更管理](change-mgmt-design.md)，
> 补全 MDS 的**受控文档主数据**——SOP（作业指导书）、Spec（规格书）、WI（作业指导）、MANUAL、POLICY、CHECKLIST。
>
> **边界是第一原则**：MDS **只拥有文档的元数据与绑定关系**（编码/版本/生效/受控状态/绑到哪个工序设备）；**文档正文与文件存储归外围 DMS**（内容管理系统），MDS 存 `doc_ref`。
> 该边界与 [recipe-design](recipe-design.md) 的 `body_ref`（配方正文在外围 Recipe 系统）**完全同构**。
>
> 设计原则延续全篇：**MDS 拥有文档元数据与受控状态；正文文件归 DMS；"谁读过/谁确认过"归下游**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么文档元数据属于 MDS（核心）

| 需求 | 只有元数据能回答 |
| --- | --- |
| 这道工序的作业指导书是哪一份？什么版本？ | `mds_document_link` + `revision` |
| 这台设备现在的操作规程是否已作废？ | `status`（`EFFECTIVE`/`OBSOLETE`） |
| 工艺评审/客户稽核要"当前受控文件清单" | 全量元数据查询 |
| 变更 ECN 要附"改了哪份文件" | 文档 ↔ [change-mgmt-design](change-mgmt-design.md) |

> **结论（DOC1）**：文档**元数据**是 MDS 主数据（可查、可绑、可受控）；文档**正文文件**归 DMS（版本管理、权限、预览），MDS 只持引用。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 文档元数据（编码/名称/类型/版本/状态/责任人） | **MDS** | 本文范围 |
| 文档 ↔ 工序/设备/配方/层的绑定 | **MDS** | `mds_document_link` |
| 受控状态（草稿/生效/作废）与生效窗口 | **MDS** | `status` + `effective_from/to` |
| **文档正文文件、版本内容、预览** | **DMS** | `doc_ref` 引用 |
| **阅读/培训/确认记录** | **HR / DMS / 培训系统** | MDS 不存 |
| **现场按文档执行的实绩** | **MES** | MDS 不存 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 文档 ↔ 工序 | `mds_document_link(target_type=ROUTE_OPERATION, target_ref)` | [route-design §3.3](route-design.md) |
| 文档 ↔ 设备型号 | `(EQUIPMENT_MODEL, model.id)` | [equipment-design §3.2](equipment-design.md) |
| 文档 ↔ 配方 | `(RECIPE, mds_recipe.id)` | [recipe-design §3.3](recipe-design.md) |
| 文档 ↔ 层 | `(LAYER, layer.code)` | [layer-design §3.1](layer-design.md) |
| 文档 ↔ PM 任务 | `(PM_TASK, mds_pm_task.code)` | [pm-calibration-design §3.2](pm-calibration-design.md) |
| 文档 ↔ 处置/原因码 | `(REASON_CODE, code)` | [reason-defect-design §3.1](reason-defect-design.md) |
| 文档责任人 ↔ 组织 | `mds_document.owner_org → mds_org_unit.code` | [org-personnel-design §3.1](org-personnel-design.md) |
| 文档变更 ↔ ECN | 变更项 `target_type=DOCUMENT` | [change-mgmt-design §3.3](change-mgmt-design.md) |
| 文档编码规则 | `entity_type=DOCUMENT`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **ISO 9001 §7.5（成文信息）** | 受控文件须有**唯一标识、版本、批准、发放、作废回收** | 元数据 + 状态 + 版本 |
| **IATF 16949 / 车规** | 作业指导书须在**工位可见且为当前版本** | 文档 ↔ 工序/设备绑定（现场按工位取最新生效版） |
| **21 CFR Part 11 / GMP（若适用）** | 受控记录与签核可追溯 | `@History` + 审批（[md-governance-design](md-governance-design.md)） |
| **SEMI E10 / fab 文档实践** | 设备操作规程（EOM）、工艺规格书（Spec）与设备/工序一一对应 | `mds_document_link` 多态绑定 |
| **DMS / ECMS 集成** | 文档正文与版本管理交给 DMS（如 SharePoint/专用 DMS） | `doc_ref` 引用（**同 `body_ref` 范式**） |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **SOP / 作业指导书** | 标准作业程序（怎么做） | 与 Spec 不同：SOP 管"动作"，Spec 管"要求/限值" |
| **Spec / 规格书** | 规格与限值（合格判据） | 与 `mds_process_flow_step_param`（工艺窗口）可互证但不同层：Spec 是文档，参数是数据 |
| **WI / 作业指导** | 更细的工位级指导 | 常与 SOP 混用；本文按 `doc_type` 区分 |
| **doc_ref / 正文引用** | 指向 DMS 中文档的唯一引用 | **不是文件内容**；MDS 不存正文 |
| **受控状态** | `DRAFT`/`REVIEW`/`EFFECTIVE`/`OBSOLETE` | 与"文档正文的草稿"不同：这是**受控生命周期** |
| **绑定 / Link** | 文档挂到哪个工序/设备/配方 | 一份文档可绑多个目标；一个目标可有多份文档 |
| **多态绑定** | `target_type` + `target_ref` 指向不同实体 | 不是外键约束（跨类型，由服务层校验） |

---

## 3. 实体总览与 ER

```
mds_document (元数据: doc_code / doc_type / version / status / doc_ref / owner_org)
        │   doc_ref ──> [DMS 外围系统]（正文文件，MDS 不存）
        └──< mds_document_link (多态绑定)
                 target_type ∈ ROUTE_OPERATION / EQUIPMENT_MODEL / EQUIPMENT /
                               RECIPE / LAYER / PM_TASK / REASON_CODE / PROCESS
                 target_ref  ∈ 对应实体 id/code
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 文档 | `mds_document` | 元数据 + 受控状态 |
| 文档绑定 | `mds_document_link` | 多态关联 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_document`（文档元数据）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `doc_code` | VARCHAR(64) | NOT NULL | 文档编码（如 `SOP-LITHO-001`） |
| `name` | VARCHAR(256) | NOT NULL | 文档名称 |
| `doc_type` | VARCHAR(16) | NOT NULL | `SOP`/`SPEC`/`WI`/`MANUAL`/`POLICY`/`CHECKLIST`/`FORM` |
| `revision` | VARCHAR(16) | NOT NULL | 文档版本 |
| `doc_ref` | VARCHAR(512) | NOT NULL | **正文引用**（DMS 中的文档 ID/URL/对象键） |
| `dms_system` | VARCHAR(32) | | 承载正文的 DMS 标识 |
| `owner_org` | VARCHAR(64) | FK→`mds_org_unit.code` | 责任部门 |
| `owner_ref` | VARCHAR(64) | | 责任人（IAM 标识） |
| `classification` | VARCHAR(16) | | `PUBLIC`/`INTERNAL`/`CONFIDENTIAL`/`RESTRICTED` |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`REVIEW`/`EFFECTIVE`/`OBSOLETE`/`ARCHIVED` |
| `effective_from`/`effective_to` | DATETIME(3) | | 生效窗口 |
| `approved_by`/`approved_at` | — | | 批准留痕（可关联 ECN） |
| `supersedes_id` | VARCHAR(32) | FK→self | 被本版本取代的旧文档 |
| `language` | VARCHAR(8) | | 语言（`zh-CN`/`en-US`） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(doc_code, revision, tenant_id, deleted)`。
- `supersedes_id` 形成版本链；`status=EFFECTIVE` 至多一个版本（服务层校验）。

### 3.2 `mds_document_link`（多态绑定）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `document_id` | VARCHAR(32) | NOT NULL, FK→`mds_document.id` | 文档 |
| `target_type` | VARCHAR(24) | NOT NULL | `ROUTE_OPERATION`/`EQUIPMENT_MODEL`/`EQUIPMENT`/`RECIPE`/`LAYER`/`PM_TASK`/`REASON_CODE`/`PROCESS`/`MATERIAL`/`RETICLE` |
| `target_ref` | VARCHAR(128) | NOT NULL | 目标实体（id/code） |
| `link_role` | VARCHAR(16) | | `REFERENCE`（参考）/`MANDATORY`（必读）/`TRAINING`（培训材料） |
| `seq` | INT | | 展示序 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(document_id, target_type, target_ref, tenant_id, deleted)`。
- **多态绑定的引用完整性由服务层校验**（`DocumentLinkValidator` 按 `target_type` 查对应域是否存在该实体）。

---

## 4. 关键设计点（拍板 DOC1–DOC5）

### 4.1 元数据归 MDS、正文归 DMS（DOC1）
见 §0.1。**同 `body_ref` 范式**（[recipe-design §3.3](recipe-design.md)）：MDS 存引用，不存内容，避免把 MDS 变成文件服务器。

### 4.2 文档类型（DOC2）
| doc_type | 内容 | 典型绑定 |
| --- | --- | --- |
| `SOP` | 标准作业程序 | 工序 / 设备型号 |
| `SPEC` | 规格与限值 | 工序 / 产品 |
| `WI` | 工位级作业指导 | 设备 / 工位 |
| `MANUAL` | 设备手册 | 设备型号 |
| `POLICY` | 政策/程序文件 | 全局 |
| `CHECKLIST` | 检查清单 | PM 任务 |
| `FORM` | 表单模板 | 工序 |

### 4.3 多态绑定（DOC3）
- 一份文档可绑多个目标（如"设备安全规程"绑多个设备型号）；
- 一个目标可有多份文档（如工序既有 SOP 又有 Spec）；
- `link_role` 区分"参考/必读/培训"，供现场按角色取文档（MES/门户消费）。

### 4.4 受控生命周期与版本（DOC4）
- `DRAFT → REVIEW → EFFECTIVE → OBSOLETE → ARCHIVED`；
- 改文档 → 新 `revision` + `supersedes_id` 指向旧版；旧版转 `OBSOLETE`；
- **同时仅一个 `EFFECTIVE` 版本**（服务层强校验），保证现场取的永远是当前受控版；
- 与 [naming-rule-design](naming-rule-design.md) 的规则生命周期同源（`DRAFT→ACTIVE→DEPRECATED`），此处用文档域词汇。

### 4.5 与变更管理的联动（DOC5）
- 文档变更走 [change-mgmt-design](change-mgmt-design.md)（`change_type=DOCUMENT` / 变更项 `target_type=DOCUMENT`）；
- ECN 批准 → 文档新版本发布 → 旧版 `OBSOLETE`；
- 文档批准记录可关联 `approved_by` 与 ECN 号，形成"**文档—变更**"双向凭证。

### 4.6 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 文档实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 文档元数据与绑定变更落 `{entity}_hist`（受控文件变更必须留痕） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + 文档类型种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_document (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  doc_code       VARCHAR(64) NOT NULL,
  name           VARCHAR(256) NOT NULL,
  doc_type       VARCHAR(16) NOT NULL,
  revision        VARCHAR(16) NOT NULL,
  doc_ref        VARCHAR(512) NOT NULL,
  dms_system     VARCHAR(32),
  owner_org      VARCHAR(64),
  owner_ref      VARCHAR(64),
  classification VARCHAR(16),
  status         VARCHAR(16) NOT NULL,
  effective_from DATETIME(3),
  effective_to   DATETIME(3),
  approved_by    VARCHAR(64),
  approved_at    DATETIME(3),
  supersedes_id  VARCHAR(32),
  language       VARCHAR(8),
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_document PRIMARY KEY (id),
  CONSTRAINT uq_mds_document UNIQUE (doc_code, revision, tenant_id, deleted),
  CONSTRAINT fk_document_supersedes FOREIGN KEY (supersedes_id) REFERENCES mds_document (id)
);
CREATE INDEX idx_document_type ON mds_document (doc_type, status);

CREATE TABLE mds_document_link (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  document_id VARCHAR(32) NOT NULL,
  target_type VARCHAR(24) NOT NULL,
  target_ref  VARCHAR(128) NOT NULL,
  link_role   VARCHAR(16),
  seq         INT,
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_doc_link PRIMARY KEY (id),
  CONSTRAINT uq_mds_doc_link UNIQUE (document_id, target_type, target_ref, tenant_id, deleted),
  CONSTRAINT fk_doclink_doc FOREIGN KEY (document_id) REFERENCES mds_document (id)
);
CREATE INDEX idx_doclink_target ON mds_document_link (target_type, target_ref);
```

> 历史表 `mds_document_hist` / `mds_document_link_hist` 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.document
  ├── entity
  │   ├── Document.java               # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── DocumentLink.java            # @Entity 继承 BaseDefData（多态绑定）
  │   └── package-info.java            # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── DocumentRepository.java
  │   └── DocumentLinkRepository.java
  ├── service
  │   ├── DocumentService.java          # 继承 AbstractJpaService；release 时校验唯一 EFFECTIVE
  │   ├── DocumentLinkValidator.java     # 按 target_type 校验目标实体存在
  │   └── DocumentResolver.java           # 按目标取当前生效文档（供 MES/门户消费）
  └── web
      └── DocumentController.java        # 查询/发布/作废/按目标取文档
```

---

## 8. 待补 / 后续

- **DMS 集成契约**：`doc_ref` 的解析方式（URL / 对象键 / DMS API），以及 DMS 侧版本与 MDS `revision` 的对应规则。
- **现场可见性**：MES/工位终端按 `target_type/target_ref` 拉取"当前生效 + `link_role=MANDATORY`"文档（本文只提供定义）。
- **培训与阅读确认**：归 HR/培训系统，可通过 `evidence_ref` 反向引用（可选）。
- **与既有文档闭环**：本文引用 `mds_route_operation`（[route-design](route-design.md)）、`mds_equipment_model`（[equipment-design](equipment-design.md)）、`mds_recipe`（[recipe-design](recipe-design.md)）、`mds_layer`（[layer-design](layer-design.md)）、`mds_pm_task`（[pm-calibration-design](pm-calibration-design.md)）、`mds_org_unit`（[org-personnel-design](org-personnel-design.md)）、ECN 联动（[change-mgmt-design](change-mgmt-design.md)）、新增 `entity_type=DOCUMENT`（[naming-rule-design](naming-rule-design.md)）。
