# MDS 主数据治理元模型（MDM Governance）设计

> 本文是 MDS 的**元治理层**——它不管理某一类主数据，而是管理「**主数据本身怎么被管**」：
> 哪个域由谁负责（数据责任人）、变更走什么审批链、数据质量按什么规则校验、关键动作的电子签名如何留痕。
>
> **补的缺口**：MDS 已有 `@History(SNAPSHOT)`（**改了什么**可追溯），但**「谁负责这份数据、谁能批、什么算合格数据」完全没定义**——这是审计与合规的直接缺口。
>
> 设计原则延续全篇：**MDS 拥有治理定义（域/责任人/流程/规则）；治理动作的执行结果落在各域与审计记录中**。

---

## 0. 定位与边界（拍板）

### 0.1 与既有三层横切的关系（核心）

| 层 | 治理对象 | 回答 |
| --- | --- | --- |
| [编码治理层](naming-rule-design.md) | `code` 生成 | 怎么命名 |
| [约束治理层](constraint-design.md) | 实体关系规则 | 什么与什么之间必须/不得成立 |
| [变更治理层](change-mgmt-design.md) | 变更授权 | 改不改、谁批、何时生效 |
| **元治理层（本文）** | **治理本身** | **谁负责、按什么规则管、怎么证明** |

> **结论（MG1）**：本层是**元数据驱动**的——通过**域注册表**统一登记所有主数据域，使治理规则（责任人、质量规则、审批链）**一处定义、全域适用**，新增域只需注册。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 域注册表（有哪些主数据域） | **MDS** | `mds_md_domain` |
| 数据责任人分配（域 × 范围 → 人/角色） | **MDS** | `mds_md_steward` |
| 审批工作流定义 | **MDS** | `mds_md_workflow` + `_step` |
| 数据质量规则定义 | **MDS** | `mds_md_quality_rule` |
| 电子签名记录 | **MDS** | `mds_md_signature` |
| **实际审批动作** | **MDS**（流程）/ 人 | 结果落 [change-mgmt-design](change-mgmt-design.md) 的审批步 |
| **质量校验的执行** | **MDS 定时/触发任务** | 结果可落 `mds_md_quality_result` |
| **身份与登录** | **IAM** | 责任人/审批人只存 `person_ref` 逻辑引用 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 域 ↔ 各主数据域 | `mds_md_domain.domain_code` 覆盖 LOCATION/EQUIPMENT/ROUTE/PRODUCT/CARRIER/RECIPE/BANK/MATERIAL/RETICLE/LAYER/CONSTRAINT/… | 全篇 |
| 责任人 ↔ 组织 | `mds_md_steward.steward_org → mds_org_unit.code` | [org-personnel-design §3.1](org-personnel-design.md) |
| 责任人 ↔ 人员 | `steward_ref` → IAM 标识（逻辑引用） | [org-personnel-design §0.1](org-personnel-design.md) |
| 工作流 ↔ 变更审批 | `mds_md_workflow_step` 定义 [change-mgmt-design §3.5](change-mgmt-design.md) 的审批链模板 | [change-mgmt-design](change-mgmt-design.md) |
| 质量规则 ↔ 约束 | 质量规则关注**数据自身**（完整/唯一/格式）；约束关注**关系与业务红线**（§4.4 区分） | [constraint-design](constraint-design.md) |
| 签名 ↔ 审计 | `@History(SNAPSHOT)` 提供"改了什么"，本域提供"谁签的字" | 平台 design §2 |
| 域注册 ↔ 依赖图 | 影响分析需要"谁引用我"的登记 | [change-mgmt-design §4.3](change-mgmt-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **DAMA-DMBOK** | 数据治理（数据责任人 Data Steward、数据质量、元数据管理）是 MDM 的核心能力 | 本层整体定位 |
| **ISO 9001 §7.5 / §8.5** | 成文信息的**职责与权限**须被指定；更改须授权 | `mds_md_steward` + `mds_md_workflow` |
| **IATF 16949 / 车规** | 关键数据变更需**跨职能审批**与顾客通知判定 | 审批链模板 + [change-mgmt-design](change-mgmt-design.md) |
| **21 CFR Part 11 / GMP** | 电子记录与**电子签名**须可追溯、不可否认（签名含含义、时间、签署人） | `mds_md_signature`（含 `sign_meaning`） |
| **ISO 8000（数据质量）** | 数据质量维度：完整性/一致性/唯一性/准确性/时效性 | `mds_md_quality_rule.rule_type` |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **MD Domain / 主数据域** | 一类主数据的逻辑集合（如 EQUIPMENT 域） | 不是微服务；是**治理单元** |
| **Data Steward / 数据责任人** | 对某域数据质量与变更负责的人/角色 | 不是"所有者"（业务所有者另有）；Steward 偏日常治理 |
| **Workflow / 审批工作流** | 可复用的审批链模板 | 不是流程引擎实例；本域存**模板**，实例在变更单上 |
| **Quality Rule / 数据质量规则** | 校验数据自身质量的规则（完整/唯一/格式/范围/引用） | 与 [constraint-design](constraint-design.md) 的业务约束不同（§4.4） |
| **E-Signature / 电子签名** | 签署人对某动作的**法律意义**确认（含签名含义） | 不是"登录"；是**动作级签名** |
| **sign_meaning** | 签名的含义（Reviewed/Approved/Authored） | 21 CFR Part 11 要求签名必须声明含义 |

---

## 3. 实体总览与 ER

```
mds_md_domain (域注册: domain_code / entity_prefix / owner_org / change_type)
        ▲ domain_code
        ├──< mds_md_steward      (责任人: 域 × 范围 → 人/角色)
        └──< mds_md_quality_rule (数据质量规则)

mds_md_workflow (审批工作流模板)
        └──< mds_md_workflow_step (步骤: 角色 / 最少同意数 / 是否必填)

mds_md_signature (电子签名: target + signer + meaning + hash)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 域注册 | `mds_md_domain` | 主数据域清单与元信息 |
| 责任人 | `mds_md_steward` | 域 × 范围 → 责任人 |
| 工作流 | `mds_md_workflow` | 审批链模板头 |
| 工作流步 | `mds_md_workflow_step` | 审批步骤 |
| 质量规则 | `mds_md_quality_rule` | 数据质量校验规则 |
| 电子签名 | `mds_md_signature` | 动作级签名记录 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_md_domain`（域注册）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `domain_code` | VARCHAR(32) | NOT NULL | 域代码（`EQUIPMENT`/`ROUTE`/`MATERIAL`/…） |
| `domain_name` | VARCHAR(128) | | 域名称 |
| `entity_prefix` | VARCHAR(32) | | 表名前缀（如 `mds_equipment`） |
| `primary_key_field` | VARCHAR(64) | | 域内主数据编码字段（如 `code`） |
| `owner_org` | VARCHAR(64) | FK→`mds_org_unit.code` | 业务所有部门 |
| `default_change_type` | VARCHAR(24) | | 默认变更类型（[change-mgmt-design §3.1](change-mgmt-design.md) `change_type`） |
| `workflow_ref` | VARCHAR(64) | FK→`mds_md_workflow.code` | 默认审批工作流 |
| `depends_on_json` | JSON | | 依赖的域清单（供影响分析候选） |
| `is_active` | BIT | DEFAULT 1 | 是否启用 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(domain_code, tenant_id, deleted)`。
- **`depends_on_json` 是影响分析的基础**：[change-mgmt-design §4.3](change-mgmt-design.md) 需要"谁引用我"，这里登记**域级依赖**，实体级依赖由各域外键推导。

### 3.2 `mds_md_steward`（数据责任人）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `domain_code` | VARCHAR(32) | NOT NULL, FK→`mds_md_domain.domain_code` | 域 |
| `scope_type` | VARCHAR(24) | | 范围（`GLOBAL`/`SITE`/`AREA`/`PRODUCT_FAMILY`/`EQUIPMENT_CLASS`…，空=全域） |
| `scope_ref` | VARCHAR(128) | | 范围目标 |
| `role_type` | VARCHAR(16) | NOT NULL | `OWNER`（业务所有）/`STEWARD`（数据治理）/`APPROVER`（审批）/`REVIEWER`（复核） |
| `person_ref` | VARCHAR(64) | | 责任人（IAM 标识，与 `steward_org` 二选一） |
| `steward_org` | VARCHAR(64) | FK→`mds_org_unit.code` | 责任部门（角色化委派） |
| `effective_from`/`effective_to` | DATETIME(3) | | 生效窗口 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(domain_code, scope_type, scope_ref, role_type, person_ref, steward_org, tenant_id, deleted)`。

### 3.3 `mds_md_workflow` / `mds_md_workflow_step`（审批工作流模板）

**`mds_md_workflow`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 工作流代码（如 `WF-MAJOR-CHANGE`/`WF-MINOR-CHANGE`） |
| `name` | VARCHAR(128) | | 名称 |
| `target_type` | VARCHAR(24) | | 适用对象：`ECR`/`ECN`/`DOMAIN`/`GENERIC` |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `revision` | VARCHAR(16) | NOT NULL | 版本 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

**`mds_md_workflow_step`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `workflow_id` | VARCHAR(32) | NOT NULL, FK→`mds_md_workflow.id` | 工作流 |
| `step_seq` | INT | NOT NULL | 顺序 |
| `step_name` | VARCHAR(128) | | 步骤名 |
| `approver_role` | VARCHAR(64) | NOT NULL | 审批角色（映射岗位/技能，见 [org-personnel-design §3.3](org-personnel-design.md)） |
| `min_approvals` | INT | DEFAULT 1 | 最少同意人数（会签） |
| `is_required` | BIT | DEFAULT 1 | 是否必经 |
| `timeout_hours` | INT | | 超时升级 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(workflow_id, step_seq, tenant_id, deleted)`。

### 3.4 `mds_md_quality_rule`（数据质量规则）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 规则代码 |
| `name` | VARCHAR(128) | | 名称 |
| `domain_code` | VARCHAR(32) | FK→`mds_md_domain.domain_code` | 适用域 |
| `rule_type` | VARCHAR(16) | NOT NULL | `COMPLETENESS`（必填）/`UNIQUENESS`/`CONSISTENCY`（跨字段）/`REFERENTIAL`（引用完整）/`FORMAT`/`RANGE`/`TIMELINESS` |
| `target_entity` | VARCHAR(64) | | 目标表/实体 |
| `target_field` | VARCHAR(64) | | 目标字段（可空=整行） |
| `expression` | VARCHAR(512) | | 校验表达式（DSL，与 [constraint-design](constraint-design.md) 共享语法） |
| `severity` | VARCHAR(16) | NOT NULL | `ERROR`（阻断保存）/`WARN`（告警） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.5 `mds_md_signature`（电子签名）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `target_type` | VARCHAR(32) | NOT NULL | 签名对象类型（`ECR`/`ECN`/`DOCUMENT`/`ROUTE`/`RECIPE`/`CONSTRAINT`…） |
| `target_ref` | VARCHAR(128) | NOT NULL | 签名对象 |
| `action` | VARCHAR(32) | NOT NULL | 动作（`AUTHOR`/`REVIEW`/`APPROVE`/`RELEASE`/`OBSOLETE`/`FREEZE`/`ACKNOWLEDGE`） |
| `sign_meaning` | VARCHAR(64) | NOT NULL | **签名含义**（21 CFR Part 11 必填，如 "Approved for production"） |
| `signer_ref` | VARCHAR(64) | NOT NULL | 签署人（IAM 标识） |
| `signed_at` | DATETIME(3) | NOT NULL | 签署时间 |
| `content_hash` | VARCHAR(128) | | 被签内容摘要（防篡改） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(target_type, target_ref, action, signer_ref, signed_at, tenant_id, deleted)`。
- **不可修改/删除**（仅追加）：签名记录由服务层禁止 UPDATE/DELETE（软删亦禁止），保证不可否认性。

---

## 4. 关键设计点（拍板 MG1–MG6）

### 4.1 元数据驱动、一处定义全域适用（MG1）
新增一个主数据域时，只需在 `mds_md_domain` **注册**（域代码/表前缀/所有部门/默认工作流/依赖域），治理规则自动适用——避免每个域各写一套治理代码。

### 4.2 数据责任人与角色化委派（MG2）
- `role_type` 四类：`OWNER`（业务所有）/`STEWARD`（数据治理）/`APPROVER`/`REVIEWER`；
- 责任人可**按范围细化**（`scope_type` + `scope_ref`），如"28nm 产品族由张三负责"；
- 责任人解析优先级：**范围越具体越优先**（同 [constraint-design §4.4](constraint-design.md) 的 specificity 思路）。

### 4.3 审批工作流模板（MG3）
- 模板可复用（`WF-MAJOR-CHANGE` / `WF-MINOR-CHANGE`），[change-mgmt-design](change-mgmt-design.md) 的 ECR/ECN 按 `change_class` 选择模板生成实际审批步；
- 支持**会签**（`min_approvals>1`）与**超时升级**（`timeout_hours`）；
- 本域只存**模板**，实例（谁批了）在变更单的 `mds_change_approval`。

### 4.4 数据质量规则 vs 业务约束（MG4，重要区分）
| | 数据质量规则（本文） | 业务约束（[constraint-design](constraint-design.md)） |
| --- | --- | --- |
| 关注 | **数据自身**是否合格 | **业务关系**是否成立 |
| 例 | code 非空、必填字段完整、引用存在、格式合法 | Q-Time ≤ 6h、该设备不得跑该产品 |
| 触发 | **保存/定时校验**（写入拦截） | **业务动作时**（投料/派工/搬送） |
| 失败 | 拒绝保存 / 告警 | BLOCK / WARN（放行留痕） |
| 共享 | **表达式 DSL 与求值上下文共享**（避免两套语法） | 同左 |

> 二者互补：质量规则保"数据干净"，业务约束保"业务正确"。

### 4.5 电子签名与不可否认性（MG5）
- 签名**必须声明含义**（`sign_meaning`，21 CFR Part 11）；
- `content_hash` 防止"签了之后内容被改"；
- 签名记录**只追加**，服务层禁止更新与删除（含软删）；
- 与 `@History(SNAPSHOT)` 配合：`@History` 记录"改了什么"，签名记录"谁以何含义确认了"。

### 4.6 与变更/约束/编码三层横切的协同（MG6）
```
变更治理：ECR → 影响评估 → 审批(本域模板) → ECN → 触发各域版本升级
                          ▲                     │
                          │                     ▼
元治理（本文）：责任人 / 审批模板 / 质量规则 / 签名
                          ▲
约束治理：冲突检测/覆盖分析 提供评估证据
编码治理：改 code 规则亦须经变更流程
```
- 四条横切层**互不重叠、互相引用**：编码管命名、约束管关系、变更管授权、元治理管职责与证明。

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`（**签名表例外：禁止删除**）；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 治理实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 域/责任人/工作流/质量规则变更落 `{entity}_hist`；**签名表另加"禁删禁改"约束** |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + 域注册与工作流种子（**域清单以 [00-blueprint §1.1](00-blueprint.md) 为单一来源**：26 业务主数据域 + 2 基础参考数据域 + 4 横切治理层 + 2 横切规范 + 2 集成接口 = 36 个域） |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_md_domain (
  id                 VARCHAR(32) NOT NULL,
  tenant_id          VARCHAR(32) NOT NULL,
  domain_code        VARCHAR(32) NOT NULL,
  domain_name        VARCHAR(128),
  entity_prefix      VARCHAR(32),
  primary_key_field  VARCHAR(64),
  owner_org          VARCHAR(64),
  default_change_type VARCHAR(24),
  workflow_ref       VARCHAR(64),
  depends_on_json    JSON,
  is_active          BIT DEFAULT 1,
  description        VARCHAR(512),
  version_           BIGINT NOT NULL DEFAULT 0,
  deleted            BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_md_domain PRIMARY KEY (id),
  CONSTRAINT uq_mds_md_domain UNIQUE (domain_code, tenant_id, deleted)
);

CREATE TABLE mds_md_steward (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  domain_code    VARCHAR(32) NOT NULL,
  scope_type     VARCHAR(24),
  scope_ref      VARCHAR(128),
  role_type      VARCHAR(16) NOT NULL,
  person_ref     VARCHAR(64),
  steward_org    VARCHAR(64),
  effective_from DATETIME(3),
  effective_to   DATETIME(3),
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_md_steward PRIMARY KEY (id),
  CONSTRAINT fk_mdsteward_domain FOREIGN KEY (domain_code) REFERENCES mds_md_domain (domain_code)
);
CREATE INDEX idx_mdsteward_domain ON mds_md_steward (domain_code, role_type);

CREATE TABLE mds_md_workflow (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  code        VARCHAR(64) NOT NULL,
  name        VARCHAR(128),
  target_type VARCHAR(24),
  status      VARCHAR(16) NOT NULL,
  revision     VARCHAR(16) NOT NULL,
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_md_wf PRIMARY KEY (id),
  CONSTRAINT uq_mds_md_wf UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_md_workflow_step (
  id            VARCHAR(32) NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  workflow_id   VARCHAR(32) NOT NULL,
  step_seq      INT NOT NULL,
  step_name     VARCHAR(128),
  approver_role VARCHAR(64) NOT NULL,
  min_approvals INT DEFAULT 1,
  is_required   BIT DEFAULT 1,
  timeout_hours INT,
  description   VARCHAR(512),
  version_      BIGINT NOT NULL DEFAULT 0,
  deleted       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_md_wf_step PRIMARY KEY (id),
  CONSTRAINT uq_mds_md_wf_step UNIQUE (workflow_id, step_seq, tenant_id, deleted),
  CONSTRAINT fk_mdwfstep_wf FOREIGN KEY (workflow_id) REFERENCES mds_md_workflow (id)
);

CREATE TABLE mds_md_quality_rule (
  id            VARCHAR(32) NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  code          VARCHAR(64) NOT NULL,
  name          VARCHAR(128),
  domain_code   VARCHAR(32),
  rule_type     VARCHAR(16) NOT NULL,
  target_entity VARCHAR(64),
  target_field  VARCHAR(64),
  expression    VARCHAR(512),
  severity      VARCHAR(16) NOT NULL,
  status        VARCHAR(16) NOT NULL,
  description   VARCHAR(512),
  version_      BIGINT NOT NULL DEFAULT 0,
  deleted       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_md_qrule PRIMARY KEY (id),
  CONSTRAINT uq_mds_md_qrule UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_md_signature (
  id            VARCHAR(32) NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  target_type   VARCHAR(32) NOT NULL,
  target_ref    VARCHAR(128) NOT NULL,
  action        VARCHAR(32) NOT NULL,
  sign_meaning  VARCHAR(64) NOT NULL,
  signer_ref    VARCHAR(64) NOT NULL,
  signed_at     DATETIME(3) NOT NULL,
  content_hash  VARCHAR(128),
  description   VARCHAR(512),
  version_      BIGINT NOT NULL DEFAULT 0,
  deleted       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_md_sign PRIMARY KEY (id)
);
CREATE INDEX idx_mdsign_target ON mds_md_signature (target_type, target_ref);
```

> 历史表 `mds_md_domain_hist` / `mds_md_steward_hist` / … 由 `@History(SNAPSHOT)` 生成。
> **`mds_md_signature` 特例**：服务层禁止 UPDATE/DELETE（即使软删），保证签名不可否认。

---

## 7. 代码结构（包路径）

```
com.cim.mds.governance
  ├── entity
  │   ├── MdDomain.java               # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── MdSteward.java               # @Entity 继承 BaseDefData
  │   ├── MdWorkflow.java              # @Entity 继承 BaseDefData
  │   ├── MdWorkflowStep.java          # @Entity 继承 BaseDefData
  │   ├── MdQualityRule.java           # @Entity 继承 BaseDefData
  │   ├── MdSignature.java             # @Entity 继承 BaseDefData（禁删禁改）
  │   └── package-info.java            # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── MdDomainRepository.java
  │   └── MdSignatureRepository.java    # 仅允许 insert/select
  ├── service
  │   ├── MdDomainService.java          # 继承 AbstractJpaService；域注册/依赖图维护
  │   ├── StewardResolver.java          # 责任人解析（specificity 优先）
  │   ├── QualityRuleEngine.java         # 质量规则求值（与 constraint 引擎共享 DSL）
  │   ├── SignatureService.java          # 追加式签名；禁止更新/删除
  │   └── WorkflowTemplateService.java    # 工作流模板 → 变更单审批步实例化
  └── web
      └── GovernanceController.java       # 域/责任人/工作流/质量规则/签名查询
```

---

## 8. 待补 / 后续

- **域注册种子**：按 **[00-blueprint §1.1 权威域清单](00-blueprint.md)** 登记全部 36 个域为种子（26 业务主数据域 + 2 基础参考数据域 + 4 横切治理层 + 2 横切规范 + 2 集成接口）——**不再在本文档内列举域清单**，避免两处维护再次漂移。
- **依赖图服务**：`depends_on_json` + 各域外键 → 供 [change-mgmt-design §4.3](change-mgmt-design.md) 影响分析使用（两处共用，避免重复实现）。
- **质量规则调度**：定时全量扫描 vs 写入时实时校验的策略配置。
- **与既有文档闭环**：本文引用 `mds_org_unit`（[org-personnel-design](org-personnel-design.md)）、审批实例（[change-mgmt-design](change-mgmt-design.md)）、业务约束（[constraint-design](constraint-design.md)）、编码规则（[naming-rule-design](naming-rule-design.md)）、全篇各主数据域。

---

## 附录 A. 数据保留与归档策略（第三轮补强）

> **补的合规缺口**：主数据的历史版本、审计记录、电子签名、发布批次**保留多久、如何归档、能否销毁**，此前全库未定义。GDPR / 个保法与各类稽核（ISO / IATF / 客户）都要求明确的保留与销毁规则。

### A.1 保留期矩阵

| 数据类别 | 最短保留期 | 归档 | 可否销毁 | 依据 |
| --- | --- | --- | --- | --- |
| 主数据**当前版本** | 永久（至产品/资产生命周期结束） | 不归档 | ❌ | 生产必需 |
| 主数据**历史版本** `{entity}_hist` | ≥ 产品生命周期 + 法规期（建议 **≥ 10 年**；车规建议 **≥ 15 年**） | ≥ 2 年移冷存储 | ❌ | IATF / 客户追溯 |
| **电子签名** `mds_md_signature` | **永久** | ❌ | ❌（禁删禁改） | 21 CFR Part 11 / 不可否认性 |
| 变更单 ECR/ECN + 审批步 | ≥ 10 年（车规 15 年） | ≥ 3 年移冷 | ❌ | ISO 9001 / IATF |
| 发布批次 `_release` / `_release_item` | 头表**永久**；明细 ≥ 3 年 | 明细 ≥ 1 年归档 | 明细可销毁 | 版本可溯 |
| 试算/对比明细 `_eval_item` / `_compare_item` | ≥ 3 年；头表 ≥ 5 年 | ≥ 1 年归档 | 明细可销毁 | 评审留痕 |
| 人员认证 `mds_person_certification` | 在职 + 离职后 ≥ 5 年 | — | 到期销毁 | 劳动 / 合规 |
| 数据质量校验结果 | ≥ 3 年 | — | 可销毁 | 内部审计 |
| 分发回执 `mds_md_delivery` | ≥ 1 年 | ≥ 6 月归档 | 可销毁 | 运维追溯 |

### A.2 归档与销毁规则

1. **归档 ≠ 删除**：归档数据进入冷存储并**保持可查**（提供异步查询接口，[nonfunctional-design §1.3](nonfunctional-design.md)）；
2. **销毁需双人审批 + 留痕**（以 `mds_md_signature` 记录销毁行为），且**签名与审计类数据永不销毁**；
3. **软删（`deleted=1`）≠ 销毁**：软删只移出热表；正式销毁走 A.1 流程；
4. **隐私与分级**：MDS 仅存 `person_ref` 等**最小必要信息**（[org-personnel-design §0.1](org-personnel-design.md)），**不存个人敏感信息**；如需扩展须先标注数据分级与法律依据（个保法 / GDPR）；
5. **保留期归属**：由**数据责任人**（本域 §3.2 `mds_md_steward`）确认，并作为数据质量规则的一类（`rule_type=TIMELINESS` 的扩展语义）。

### A.3 与既有设计的闭环

| 关联 | 说明 |
| --- | --- |
| [nonfunctional-design §1.3](nonfunctional-design.md) | 分区与归档 Job 的技术实现 |
| [realtime-contract §2](realtime-contract-design.md) | 白名单回写产生的历史记录同样受本表约束 |
| [change-mgmt-design](change-mgmt-design.md) | 变更单与审批记录的保留期 |
| [document-design](document-design.md) | 受控文档的作废与归档（与本文一致的"归档可查"原则） |
