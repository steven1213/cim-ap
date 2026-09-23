# MDS 变更管理（Change Management / ECR / ECN）设计

> 本文是 MDS 的**第三条横切治理层（流程层）**——与 [naming-rule-design](naming-rule-design.md)（编码治理）、[constraint-design](constraint-design.md)（关系规则治理）并列。
> 前两者管「**数据长什么样**」，本层管「**数据怎么被改、由谁批、何时生效**」。
>
> **补的缺口**：route / recipe / product / process-flow 都已有 `cloneAsNewVersion()` 与 `RELEASED 不可变`，但**「变更受控流程」本身没建**——谁提的、影响什么、谁批的、何时生效。ISO 9001 / IATF 16949 审核必查此流程。
>
> 设计原则延续全篇：**MDS 拥有变更单与审批状态；变更的「技术执行」由各域服务完成，执行实绩归 MES**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么版本能力不够，还需要变更管理（核心）

| 已有能力 | 缺什么 |
| --- | --- |
| `cloneAsNewVersion()` | **谁授权**这次克隆？克隆前有没有评估影响？ |
| `RELEASED 不可变` | 变更的**申请—评估—批准—实施—生效**链条在哪里？ |
| `@History(SNAPSHOT)` | 记录的是"改了什么"，**不是"为什么改、谁批的"** |
| 审批（无） | 工艺/质量/生产**会签**无处承载 |

> **结论（CM1）**：变更是 MDS 的**横切流程层**：以 **ECR（变更请求）→ 影响评估 → 审批 → ECN（变更执行单）→ 实施 → 生效 → 关闭** 的受控链条，串起所有域的版本升级动作。它**不替代**各域的版本机制，而是**给版本升级提供合规凭证与授权**。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| ECR / ECN 单据、状态、审批链 | **MDS** | 本文范围 |
| 影响分析（受影响实体清单） | **MDS** | 基于依赖图推导（§4.3） |
| 生效时间与生效范围 | **MDS** | `effective_from` + 目标实体 |
| **各域的技术变更动作** | **各域服务** | `RouteService.cloneAsNewVersion()` 等，由 ECN 触发 |
| **变更后的产线执行**（新版本实际投用） | **MES** | MDS 不存 |
| **变更导致的在线批次处置** | **MES** | 如"老版本 lot 继续走完还是切换" |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 变更项 ↔ 目标实体 | `mds_change_item.target_type/target_ref` | 覆盖 ROUTE/RECIPE/PRODUCT/PROCESS_FLOW/CONSTRAINT/NAMING_RULE/EQUIPMENT… |
| 变更 ↔ 版本 | 变更项记录 `from_revision` → `to_revision` | [route-design §4.2.2](route-design.md) 等各域版本机制 |
| 审批 ↔ 组织/角色 | `mds_change_approval.approver_role → mds_org_unit` | [org-personnel-design §3.1](org-personnel-design.md) |
| 影响 ↔ 约束 | 约束的冲突/覆盖分析常成为变更依据 | [constraint-design §4.6](constraint-design.md) |
| 影响 ↔ 工艺流对比 | Process Flow 版本 diff 作为变更附件证据 | [process-flow-design §4.7](process-flow-design.md) |
| 生效 ↔ 日历 | `effective_from` 与工作日历对齐 | [org-personnel-design §3.5](org-personnel-design.md) |
| 签核 ↔ 电子签名 | 审批结果与签名的合规性 | [md-governance-design](md-governance-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **ISO 9001 §8.5.6（设计和开发更改）** | 变更须经**评审、授权**，并识别对已交付产品的影响 | ECR/ECN + 影响分析 + 审批链 |
| **IATF 16949 / 车规** | 变更需**顾客通知**与**PPAP 再确认**触发判定 | `requires_customer_notice` / `requires_requalification` |
| **SEMI E10 / fab 工程变更（ECM）** | 工艺变更需跨部门会签（工艺/设备/质量/生产） | 多步审批链 `mds_change_approval` |
| **21 CFR Part 11（若适用）** | 电子记录与签名可追溯、不可否认 | 审批人/时间/意见留痕 + [md-governance-design](md-governance-design.md) |
| **APQP / PPAP** | 变更影响产品特性时须重新验证 | `requires_requalification` → 触发 [sampling-spc-design](sampling-spc-design.md) / [pm-calibration-design](pm-calibration-design.md) |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **ECR / Engineering Change Request** | 变更**请求**：有人提出"要改" | 提出≠批准；ECR 是意图 |
| **ECN / Engineering Change Notice** | 变更**执行单**：批准后的实施指令 | 不是通知邮件；是受控执行凭据 |
| **Change Item / 变更项** | ECN 里的一条具体改动（改哪条 route 的哪个版本） | 不是 ECR 的诉求描述 |
| **Impact Analysis / 影响分析** | 本次变更**波及哪些实体/产品/在制品** | 不是"风险评估"文本；是可推导的影响清单 |
| **Approval Step / 审批步** | 审批链中的一步（角色/顺序/结果） | 与"工作流引擎"区分：本域只存**审批定义与结果** |
| **Effective / 生效** | 变更正式对产线可用（新版本 `RELEASED`） | 与"实施完成"不同：实施可早于生效（预约生效） |
| **Change vs Constraint** | 变更决定"改不改"；约束决定"能不能" | 二者正交，可互相引用作证据 |

---

## 3. 实体总览与 ER

```
mds_ecr (变更请求)
   │ requested_by → IAM user
   └──< mds_ecn (变更执行单: erc_id, effective_from, status)
             ├──< mds_change_item   (变更项: target_type/target_ref/from_revision/to_revision)
             │         └──< mds_change_impact (影响分析: impacted_type/impacted_ref)
             └──< mds_change_approval (审批: step_seq/approver_role/result)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 变更请求 | `mds_ecr` | 诉求、类型、理由、优先级 |
| 变更执行单 | `mds_ecn` | 批准后的实施指令 + 生效时间 |
| 变更项 | `mds_change_item` | 具体改动（目标实体 + 版本迁移） |
| 影响分析 | `mds_change_impact` | 受影响实体清单 |
| 审批步 | `mds_change_approval` | 会签链与结果 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_ecr`（变更请求）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | ECR 编号（如 `ECR-2026-0001`） |
| `title` | VARCHAR(256) | NOT NULL | 标题 |
| `change_type` | VARCHAR(24) | NOT NULL | `ROUTE`/`RECIPE`/`PRODUCT`/`PROCESS_FLOW`/`SPEC`/`EQUIPMENT`/`MATERIAL`/`RETICLE`/`CONSTRAINT`/`NAMING_RULE`/`OTHER` |
| `change_reason` | VARCHAR(512) | | 变更理由（良率/成本/法规/客户） |
| `change_class` | VARCHAR(16) | | `MAJOR`/`MINOR`（决定审批链长度） |
| `requested_by` | VARCHAR(64) | NOT NULL | 申请人（IAM 标识） |
| `requested_org` | VARCHAR(64) | | 申请部门（→ `mds_org_unit.code`） |
| `priority` | VARCHAR(16) | | `URGENT`/`HIGH`/`NORMAL`/`LOW` |
| `requires_customer_notice` | BIT | DEFAULT 0 | 是否需通知客户 |
| `requires_requalification` | BIT | DEFAULT 0 | 是否需再验证/再认证 |
| `status` | VARCHAR(16) | NOT NULL | `SUBMITTED`/`ASSESSING`/`APPROVED`/`REJECTED`/`CANCELLED`/`CLOSED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.2 `mds_ecn`（变更执行单）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | ECN 编号（如 `ECN-2026-0001`） |
| `ecr_id` | VARCHAR(32) | NOT NULL, FK→`mds_ecr.id` | 来源请求 |
| `title` | VARCHAR(256) | | 标题 |
| `implementation_plan` | VARCHAR(1024) | | 实施计划说明 |
| `effective_from` | DATETIME(3) | | 计划生效时间（可预约未来） |
| `effective_to` | DATETIME(3) | | 失效时间（如临时变更） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`APPROVED`/`IMPLEMENTING`/`EFFECTIVE`/`CLOSED`/`CANCELLED` |
| `implemented_by`/`implemented_at` | — | | 实施留痕 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.3 `mds_change_item`（变更项）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `ecn_id` | VARCHAR(32) | NOT NULL, FK→`mds_ecn.id` | 所属执行单 |
| `target_type` | VARCHAR(24) | NOT NULL | 目标实体类型（同 `change_type` 枚举） |
| `target_ref` | VARCHAR(128) | NOT NULL | 目标实体（id/code） |
| `change_action` | VARCHAR(16) | NOT NULL | `CREATE`/`UPDATE`/`OBSOLETE`/`FREEZE`/`UNFREEZE` |
| `from_revision` | VARCHAR(16) | | 变更前版本 |
| `to_revision` | VARCHAR(16) | | 变更后版本（克隆新版本） |
| `changed_fields` | JSON | | 关键字段变更摘要（如参数窗口调整） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(ecn_id, target_type, target_ref, tenant_id, deleted)`。

### 3.4 `mds_change_impact`（影响分析）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `change_item_id` | VARCHAR(32) | NOT NULL, FK→`mds_change_item.id` | 所属变更项 |
| `impacted_type` | VARCHAR(24) | NOT NULL | 受影响实体类型（PRODUCT/ROUTE/PROCESS_FLOW/BOM/CONSTRAINT…） |
| `impacted_ref` | VARCHAR(128) | NOT NULL | 受影响实体 |
| `impact_kind` | VARCHAR(16) | NOT NULL | `DIRECT`（直接引用）/`INDIRECT`（经依赖链）/`POTENTIAL`（需人工确认） |
| `impact_note` | VARCHAR(512) | | 评估说明 |
| `assessment` | VARCHAR(16) | | `ACCEPT`（可接受）/`ACTION_REQUIRED`/`PENDING` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(change_item_id, impacted_type, impacted_ref, tenant_id, deleted)`。

### 3.5 `mds_change_approval`（审批步）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `ecn_id` | VARCHAR(32) | NOT NULL, FK→`mds_ecn.id` | 所属执行单（或挂 ecr，用 `owner_kind`） |
| `owner_kind` | VARCHAR(16) | NOT NULL DEFAULT 'ECN' | `ECR`/`ECN` |
| `step_seq` | INT | NOT NULL | 审批顺序 |
| `approver_role` | VARCHAR(64) | NOT NULL | 审批角色/岗位（映射 `mds_skill.code` 或组织角色，如 `PROCESS_ENG_MGR`） |
| `approver_ref` | VARCHAR(64) | | 实际审批人（IAM 标识） |
| `result` | VARCHAR(16) | | `PENDING`/`APPROVED`/`REJECTED`/`ABSTAINED` |
| `comment` | VARCHAR(512) | | 意见 |
| `acted_at` | DATETIME(3) | | 审批时间 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(ecn_id, owner_kind, step_seq, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 CM1–CM6）

### 4.1 三层横切治理层定位（CM1）
| 层 | 治理对象 | 回答 |
| --- | --- | --- |
| 编码治理层 | `code` 生成 | 「怎么命名」 |
| 约束治理层 | 实体关系规则 | 「什么与什么之间必须/不得成立」 |
| **变更治理层（本文）** | **变更的授权与流程** | 「**改不改、谁批、何时生效**」 |

> 三层共享同一套「DRAFT→ACTIVE→DEPRECATED」+ 版本 + 冻结语义。

### 4.2 ECR → ECN 两段式（CM2）
- **ECR** = 问"该不该改"（诉求 + 影响评估）；
- **ECN** = 说"怎么改、何时改"（实施计划 + 生效时间）；
- 一个 ECR 可产出**多个 ECN**（分阶段实施，如先改 A 厂再改 B 厂）；
- 状态机：`SUBMITTED → ASSESSING → APPROVED → (生成 ECN) → IMPLEMENTING → EFFECTIVE → CLOSED`；任一环节可 `REJECTED`/`CANCELLED`。

### 4.3 影响分析由依赖图推导（CM3，核心）
- 依赖关系来自各域既有外键：
  - `product → product_route → route → route_operation → process_flow_step → recipe/equipment_group`
  - `route_operation → material_bom`、`reticle_layer → reticle`、`constraint_binding → 目标实体`
- 算法：以变更目标为起点做**反向可达遍历**，标记 `DIRECT`（直接引用）与 `INDIRECT`（经链），无法自动判定的标 `POTENTIAL` 待人工确认；
- 输出落 `mds_change_impact`，**作为审批依据与证据附件**。
- 与 [constraint-design §4.6](constraint-design.md) 的冲突/覆盖分析、[process-flow-design §4.7](process-flow-design.md) 的版本 diff 互为佐证。

### 4.4 审批链与变更分级（CM4）
- `change_class=MAJOR` → 完整会签（工艺 → 质量 → 生产 → 管理层）；
- `change_class=MINOR` → 简链（工艺 → 质量）；
- 审批步由 `approver_role` 定义，实际审批人可代签（`approver_ref` + 意见留痕）；
- **审批通过才允许生成 ECN**，ECN 批准才允许触发各域 `cloneAsNewVersion()`。

### 4.5 与版本机制的联动（CM5）
```
ECR APPROVED
   → 建 ECN + change_item（记录 from_revision → to_revision）
ECN APPROVED
   → 调用各域服务：RouteService.cloneAsNewVersion(...) / RecipeService.cloneAsNewVersion(...)
   → 新版本进入 DRAFT → IN_REVIEW → APPROVED → 到 effective_from 时 release
ECN EFFECTIVE
   → 变更项标记已生效；旧版本 obsolete（按策略）
   → 通知 MES（经 [md-distribution-design](md-distribution-design.md)）
```
> **变更单是版本升级的合规凭证**：翻查任何一个版本，都能回答"哪张 ECN 授权了它"。

### 4.6 再验证触发（CM6）
- `requires_requalification=1` 时，ECN 生效后自动要求：
  - 相关设备/配方/光罩**重新资格**（[reticle-design §4.4](reticle-design.md) / [recipe-design](recipe-design.md)）；
  - 相关**校准/PM 计划复核**（[pm-calibration-design](pm-calibration-design.md)）；
  - 相关**采样/SPC 规则复核**（[sampling-spc-design](sampling-spc-design.md)）。
- `requires_customer_notice=1` 时，审批链增加"客户通知"步。

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 变更实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | ECR/ECN/审批步变更落 `{entity}_hist`（**审批与签名不可否认性的基础**） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 + ECR/ECN 单号序列（`mds_naming_seq`） |
| 主历成对迁移 | 结构迁移 + 历史表 + 审批角色种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_ecr (
  id                     VARCHAR(32) NOT NULL,
  tenant_id              VARCHAR(32) NOT NULL,
  code                   VARCHAR(32) NOT NULL,
  title                  VARCHAR(256) NOT NULL,
  change_type            VARCHAR(24) NOT NULL,
  change_reason          VARCHAR(512),
  change_class           VARCHAR(16),
  requested_by           VARCHAR(64) NOT NULL,
  requested_org          VARCHAR(64),
  priority               VARCHAR(16),
  requires_customer_notice BIT DEFAULT 0,
  requires_requalification BIT DEFAULT 0,
  status                 VARCHAR(16) NOT NULL,
  description            VARCHAR(512),
  version_               BIGINT NOT NULL DEFAULT 0,
  deleted                BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_ecr PRIMARY KEY (id),
  CONSTRAINT uq_mds_ecr UNIQUE (code, tenant_id, deleted)
);
CREATE INDEX idx_ecr_status ON mds_ecr (status);

CREATE TABLE mds_ecn (
  id                VARCHAR(32) NOT NULL,
  tenant_id         VARCHAR(32) NOT NULL,
  code              VARCHAR(32) NOT NULL,
  ecr_id            VARCHAR(32) NOT NULL,
  title             VARCHAR(256),
  implementation_plan VARCHAR(1024),
  effective_from    DATETIME(3),
  effective_to      DATETIME(3),
  status            VARCHAR(16) NOT NULL,
  implemented_by    VARCHAR(64),
  implemented_at    DATETIME(3),
  description       VARCHAR(512),
  version_          BIGINT NOT NULL DEFAULT 0,
  deleted           BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_ecn PRIMARY KEY (id),
  CONSTRAINT uq_mds_ecn UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_ecn_ecr FOREIGN KEY (ecr_id) REFERENCES mds_ecr (id)
);

CREATE TABLE mds_change_item (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  ecn_id         VARCHAR(32) NOT NULL,
  target_type    VARCHAR(24) NOT NULL,
  target_ref     VARCHAR(128) NOT NULL,
  change_action  VARCHAR(16) NOT NULL,
  from_revision   VARCHAR(16),
  to_revision     VARCHAR(16),
  changed_fields JSON,
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_change_item PRIMARY KEY (id),
  CONSTRAINT uq_mds_change_item UNIQUE (ecn_id, target_type, target_ref, tenant_id, deleted),
  CONSTRAINT fk_chgitem_ecn FOREIGN KEY (ecn_id) REFERENCES mds_ecn (id)
);

CREATE TABLE mds_change_impact (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  change_item_id VARCHAR(32) NOT NULL,
  impacted_type  VARCHAR(24) NOT NULL,
  impacted_ref   VARCHAR(128) NOT NULL,
  impact_kind    VARCHAR(16) NOT NULL,
  impact_note    VARCHAR(512),
  assessment     VARCHAR(16),
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_change_impact PRIMARY KEY (id),
  CONSTRAINT uq_mds_change_impact UNIQUE (change_item_id, impacted_type, impacted_ref, tenant_id, deleted),
  CONSTRAINT fk_chgimp_item FOREIGN KEY (change_item_id) REFERENCES mds_change_item (id)
);

CREATE TABLE mds_change_approval (
  id            VARCHAR(32) NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  ecn_id        VARCHAR(32) NOT NULL,
  owner_kind    VARCHAR(16) NOT NULL DEFAULT 'ECN',
  step_seq      INT NOT NULL,
  approver_role VARCHAR(64) NOT NULL,
  approver_ref  VARCHAR(64),
  result        VARCHAR(16),
  comment       VARCHAR(512),
  acted_at      DATETIME(3),
  description   VARCHAR(512),
  version_      BIGINT NOT NULL DEFAULT 0,
  deleted       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_change_appr PRIMARY KEY (id),
  CONSTRAINT uq_mds_change_appr UNIQUE (ecn_id, owner_kind, step_seq, tenant_id, deleted),
  CONSTRAINT fk_chgappr_ecn FOREIGN KEY (ecn_id) REFERENCES mds_ecn (id)
);
```

> 历史表 `mds_ecr_hist` / `mds_ecn_hist` / `mds_change_approval_hist` / … 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.change
  ├── entity
  │   ├── Ecr.java                   # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── Ecn.java                    # @Entity 继承 BaseDefData
  │   ├── ChangeItem.java             # @Entity 继承 BaseDefData
  │   ├── ChangeImpact.java           # @Entity 继承 BaseDefData
  │   ├── ChangeApproval.java         # @Entity 继承 BaseDefData
  │   └── package-info.java           # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── EcrRepository.java
  │   └── EcnRepository.java
  ├── service
  │   ├── EcrService.java              # 继承 AbstractJpaService；submit/assess/approve/reject
  │   ├── EcnService.java               # 生成/批准/实施/生效；触发各域 cloneAsNewVersion
  │   ├── ImpactAnalyzer.java            # 依赖图反向可达遍历 → mds_change_impact
  │   └── ChangeValidator.java            # 审批链完整性、版本号合法性、生效时间
  └── web
      └── ChangeController.java          # ECR/ECN 查询、影响分析、审批、生效
```

---

## 8. 待补 / 后续

- **依赖图注册表**：`ImpactAnalyzer` 需要各域登记"谁引用我"——与 [md-governance-design](md-governance-design.md) 的域注册表共用（一并解决）。
- **审批角色字典**：`approver_role` 的合法取值需与组织/岗位对齐（[org-personnel-design](org-personnel-design.md)）。
- **工作流引擎**：当前只存审批定义与结果（轻量）；若需并行会签/条件分支/超时升级，可对接平台的工作流能力（platform design §M6 之后的扩展）。
- **与既有文档闭环**：本文引用各域版本机制（[route-design §4.2](route-design.md) / [recipe-design](recipe-design.md) / [product-design](product-design.md) / [process-flow-design §4.2](process-flow-design.md)）、`mds_org_unit`（[org-personnel-design](org-personnel-design.md)）、影响分析输入（[constraint-design §4.6](constraint-design.md) / [process-flow-design §4.7](process-flow-design.md)）、再验证触发（[reticle-design](reticle-design.md) / [pm-calibration-design](pm-calibration-design.md) / [sampling-spc-design](sampling-spc-design.md)）、电子签名（[md-governance-design](md-governance-design.md)）。
