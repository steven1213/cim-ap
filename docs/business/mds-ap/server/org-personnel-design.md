# MDS 组织、人员与日历主数据设计

> 本文承接[设备建模 §3.9](equipment-design.md)（`mds_equipment_owner.owner_ref` 目前**悬空、无 master 可引用**）、[约束设计](constraint-design.md)（`EQUIP_CALENDAR` 约束缺少引用目标）、[PM/校准](pm-calibration-design.md)（`requires_qualification`），
> 补全 MDS 的**组织单元 / 供应商 / 技能与认证矩阵 / 工作日历与班次**。
>
> **边界是本域的第一原则**：身份与登录归 **IAM**（员工由 AD/LDAP 托管，[README §1](README.md)），MDS **只拥有业务侧的组织引用与资质矩阵**，不重建账号体系。

---

## 0. 定位与边界（拍板）

### 0.1 与 IAM 的严格划界（核心，OP1）

| 内容 | 归属 | 说明 |
| --- | --- | --- |
| 用户账号、口令、登录、令牌 | **IAM（`iam-ap`）** | 员工身份由企业 AD/LDAP 托管，MDS 不建 |
| 用户能否进入 `mds-ap` | **IAM** | `apps` claim 准入 |
| **部门 / 成本中心 / 班组的业务层级** | **MDS（本域）** | `mds_org_unit`，供设备 owner、文档责任人、审批角色引用 |
| **供应商 / OEM / 服务商** | **MDS（本域）** | `mds_vendor`，供设备 owner、物料供应商、光罩制造商引用 |
| **技能定义与人员认证矩阵** | **MDS（本域）** | `mds_skill` / `mds_person_certification` |
| **工作日历与班次** | **MDS（本域）** | `mds_work_calendar` / `mds_shift` |
| 人员**当前在岗/排班实绩** | **MES / HR** | 实时态，MDS 不存 |

> **结论（OP1）**：`mds_person_certification.person_ref` **指向 IAM 的用户标识，不重建人员主数据**。MDS 只回答「**这个人有没有资格做这件事**」。

### 0.2 为什么资质矩阵必须建模

- **合规刚性**：ISO 9001 / IATF 16949 / 车规要求「**持证上岗**」并留有记录；
- **现有悬空**：[equipment-design §3.9](equipment-design.md) 的 `owner_ref` 指向"部门编码/厂商编码"，但**没有 master**——本文补上；
- **约束前置**：`requires_qualification`（[pm-calibration-design §3.2](pm-calibration-design.md)）需要一个可解析的资质定义。

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 设备所有权 ↔ 组织/供应商 | `mds_equipment_owner.owner_ref → mds_org_unit.code` 或 `mds_vendor.code` | [equipment-design §3.9](equipment-design.md) |
| 物料 ↔ 供应商 | `mds_material.vendor_id → mds_vendor.id` | [material-design §3.2](material-design.md) |
| 光罩 ↔ 制造商 | `mds_reticle.vendor_id → mds_vendor.id` | [reticle-design §3.2](reticle-design.md) |
| 任务 ↔ 资质要求 | `mds_pm_task.requires_qualification → mds_skill.code` | [pm-calibration-design §3.2](pm-calibration-design.md) |
| 认证 ↔ 设备类/配方 | `mds_person_certification.scope_ref → mds_equipment_class.id` / `mds_recipe.id` | [equipment-design §3.2](equipment-design.md) / [recipe-design §3.3](recipe-design.md) |
| 日历 ↔ 约束 | `EQUIP_CALENDAR.calendar_ref → mds_work_calendar.code` | [constraint-design §4.2](constraint-design.md) |
| 审批角色 ↔ 组织 | 变更/治理审批的 `approver_role` 引用组织 | [change-mgmt-design](change-mgmt-design.md) / [md-governance-design](md-governance-design.md) |
| 人员 ↔ IAM 用户 | `person_ref` → IAM 用户标识（**逻辑引用，不重建**） | [README §1](README.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **ISO 9001 / IATF 16949** | 人员能力与培训记录受控；关键岗位须持证 | `mds_skill` + `mds_person_certification`（含到期与留痕） |
| **ISA-95 / IEC 62264** | 组织模型（Enterprise/Site/Area/Work Center/Work Unit）与人员模型分层 | `mds_org_unit`（`org_type` 覆盖 DEPARTMENT/COST_CENTER/TEAM/WORK_CENTER） |
| **fab 资质矩阵实践** | 操作员/工程师按**设备类、配方、工序**授权；资格有**有效期**与**再认证** | 认证矩阵 `scope_type` + `expire_at` |
| **EHS / 安全合规** | 高风险作业（特气、化学品）需专项资质 | `skill_type=SAFETY` |
| **排程与日历** | 设备可用性受**工作日历/班次**影响（非 24×7 的工序） | `mds_work_calendar` / `mds_shift`，落实 `EQUIP_CALENDAR` 约束 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Org Unit / 组织单元** | 业务组织节点（部门/成本中心/班组/工作中心） | **不是** IAM 的账号组；不承载登录权限 |
| **Vendor / 供应商** | 外部法人（设备 OEM、化学品、气体、服务、校准） | 与 `owner_type=VENDOR` 的设备所有权呼应，但 vendor 是**主数据** |
| **Skill / 技能** | 能力定义（操作某设备类、执行某作业、安全资质） | 不是"岗位"；岗位通常是一组技能组合 |
| **Certification / 认证** | 「某人 × 某技能 × 某范围」的**有效授权**，带有效期 | 不是培训记录（培训归 HR）；这是**授权判定依据** |
| **Work Calendar / 工作日历** | 工作日模式 + 节假日（决定可用时间） | 不是设备班表（班表是排产结果） |
| **Shift / 班次** | 每日时段划分（早/中/夜） | 与日历正交：日历定义"哪天开工"，班次定义"一天几段" |
| **person_ref** | 指向 IAM 用户标识的**逻辑引用** | MDS 不存姓名/工号等 PII（尽量）——展示名由 IAM 提供 |

---

## 3. 实体总览与 ER

```
mds_org_unit (树: DEPARTMENT / COST_CENTER / TEAM / WORK_CENTER)
        ▲ parent_id
        ├──< 被 mds_equipment_owner.owner_ref 引用
        └──< 被 文档责任人 / 审批角色 引用

mds_vendor (供应商/OEM/服务商)
        └──< 被 mds_material.vendor_id / mds_reticle.vendor_id / mds_equipment_owner 引用

mds_skill (技能定义: OPERATION / EQUIPMENT_CLASS / RECIPE / SAFETY / PROCESS)
        ▲ skill_id
mds_person_certification (person_ref × skill × scope, 含 expire_at)

mds_work_calendar (工作日历: 时区 + 工作日模式 + 节假日)
mds_shift (班次: 时段划分)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 组织单元 | `mds_org_unit` | 部门/成本中心/班组树 |
| 供应商 | `mds_vendor` | 外部法人 |
| 技能 | `mds_skill` | 能力定义 |
| 人员认证 | `mds_person_certification` | 授权矩阵（含有效期） |
| 工作日历 | `mds_work_calendar` | 日历与节假日 |
| 班次 | `mds_shift` | 每日时段 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_org_unit`（组织单元）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 组织编码（供 `owner_ref` 引用） |
| `name` | VARCHAR(128) | | 名称 |
| `org_type` | VARCHAR(16) | NOT NULL | `DEPARTMENT`/`COST_CENTER`/`TEAM`/`WORK_CENTER`/`SECTION` |
| `parent_id` | VARCHAR(32) | FK→self | 父组织（树形） |
| `cost_center_code` | VARCHAR(32) | | 财务成本中心编码（与 ERP 对齐） |
| `site_code` | VARCHAR(64) | | 所属厂区（引用 `mds_location.code`，`level=SITE/FAB`） |
| `manager_ref` | VARCHAR(64) | | 负责人（IAM 用户标识，逻辑引用） |
| `status` | VARCHAR(16) | NOT NULL | `ACTIVE`/`INACTIVE` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.2 `mds_vendor`（供应商 / OEM / 服务商）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 供应商编码（供 `owner_ref` 引用） |
| `name` | VARCHAR(128) | | 名称 |
| `vendor_type` | VARCHAR(24) | NOT NULL | `EQUIPMENT_OEM`/`CHEMICAL`/`GAS`/`TARGET`/`PHOTORESIST`/`RETICLE`/`SERVICE`/`CALIBRATION`/`CONSUMABLE`/`OTHER` |
| `contact_name` | VARCHAR(64) | | 联系人 |
| `contact_phone`/`contact_email` | VARCHAR(64) | | 联系方式 |
| `qual_status` | VARCHAR(16) | NOT NULL DEFAULT 'APPROVED' | `APPROVED`/`PENDING`/`SUSPENDED`/`BLACKLISTED` |
| `qual_expire_at` | DATETIME(3) | | 供应商资格有效期 |
| `service_scope` | VARCHAR(256) | | 服务范围（服务商/校准商） |
| `status` | VARCHAR(16) | NOT NULL | `ACTIVE`/`INACTIVE` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.3 `mds_skill`（技能定义）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 技能编码（供 `requires_qualification` 引用） |
| `name` | VARCHAR(128) | | 技能名 |
| `skill_type` | VARCHAR(24) | NOT NULL | `OPERATION`/`EQUIPMENT_CLASS`/`RECIPE`/`SAFETY`/`PROCESS`/`METROLOGY` |
| `level` | VARCHAR(16) | | 等级：`L1`（可操作）/`L2`（可调机）/`L3`（可培训）/`TRAINER` |
| `validity_days` | INT | | 默认有效期（天），用于生成认证到期 |
| `requires_retrain` | BIT | DEFAULT 1 | 到期是否须再培训 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.4 `mds_person_certification`（人员认证矩阵）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `person_ref` | VARCHAR(64) | NOT NULL | **IAM 用户标识**（逻辑引用，不重建人员主数据） |
| `person_name` | VARCHAR(64) | | 冗余展示名（由 IAM 同步，便于查询） |
| `skill_id` | VARCHAR(32) | NOT NULL, FK→`mds_skill.id` | 技能 |
| `scope_type` | VARCHAR(24) | | 授权范围类型：`EQUIPMENT_CLASS`/`EQUIPMENT`/`RECIPE`/`PROCESS`/`GLOBAL` |
| `scope_ref` | VARCHAR(128) | | 范围目标（id/code） |
| `cert_status` | VARCHAR(16) | NOT NULL | `CERTIFIED`/`PENDING`/`EXPIRED`/`REVOKED`/`SUSPENDED` |
| `certified_at` | DATETIME(3) | | 认证日期 |
| `expire_at` | DATETIME(3) | | 到期日（空=长期有效） |
| `certified_by` | VARCHAR(64) | | 认证人（逻辑引用 IAM） |
| `evidence_ref` | VARCHAR(256) | | 证据引用（培训记录/考核单，归 HR/DMS） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(person_ref, skill_id, scope_type, scope_ref, tenant_id, deleted)`。
- **合规提示**：本表只存**授权判定所需最小信息**；个人敏感信息（身份证明等）归 HR，不落 MDS。

### 3.5 `mds_work_calendar`（工作日历）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 日历编码（供 `EQUIP_CALENDAR.calendar_ref` 引用） |
| `name` | VARCHAR(128) | | 名称 |
| `timezone` | VARCHAR(32) | NOT NULL DEFAULT 'Asia/Shanghai' | 时区 |
| `work_day_pattern` | VARCHAR(32) | | 工作日模式：`24x7`/`24x5`/`12x5`/`8x5`/`CUSTOM` |
| `holiday_calendar_ref` | VARCHAR(64) | | 节假日表引用（可指向外部 HR 日历） |
| `hours_per_day` | DECIMAL(4,2) | | 每日可用小时（默认由 pattern 推导） |
| `status` | VARCHAR(16) | NOT NULL | `ACTIVE`/`INACTIVE` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.6 `mds_shift`（班次）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | 班次编码 |
| `name` | VARCHAR(64) | | 名称（早班/中班/夜班） |
| `calendar_ref` | VARCHAR(64) | FK→`mds_work_calendar.code` | 所属日历 |
| `start_time` | TIME | NOT NULL | 开始 |
| `end_time` | TIME | NOT NULL | 结束 |
| `cross_day` | BIT | DEFAULT 0 | 是否跨天（夜班） |
| `seq` | INT | | 展示序 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 OP1–OP6）

### 4.1 与 IAM 划界（OP1）
见 §0.1。**MDS 不建人员主数据**；`person_ref` 是逻辑引用；只存授权判定所需最小信息。

### 4.2 组织单元承接 owner_ref（OP2）
- [equipment-design §3.9](equipment-design.md) 的 `owner_ref` 现在有明确引用目标：`owner_type=DEPARTMENT/COST_CENTER` → `mds_org_unit.code`；`owner_type=VENDOR` → `mds_vendor.code`；
- 由 `OrgRefValidator` 在保存设备所有权时校验引用存在且 `status=ACTIVE`。

### 4.3 供应商统一管理（OP3）
- 原先供应商散落在设备 owner、物料 vendor、光罩 vendor，现统一到 `mds_vendor`；
- `vendor_type` 覆盖设备/化学品/气体/光罩/服务/校准等，**一处维护、多处引用**；
- `qual_status` + `qual_expire_at` 支撑「供应商资格到期不得采购/不得服务」的约束（可经 [constraint-design](constraint-design.md) 表达）。

### 4.4 认证矩阵与"持证上岗"（OP4）
- 三重维度：`person_ref` × `skill_id` × `scope`；
- **到期驱动**：`expire_at` 到期 → `cert_status=EXPIRED`（可配定时任务或约束判定）；过期人员不得派工；
- **与派工联动**：MES 派工时校验「操作员对该设备类/配方 CERTIFIED」（可经约束新增 `OPERATOR_QUALIFIED` 类型表达）；
- **与 PM 联动**：`mds_pm_task.requires_qualification` 解析到 `mds_skill`，校验执行人资质。

### 4.5 日历与班次（OP5，补约束引用目标）
- [constraint-design §4.2](constraint-design.md) 的 `EQUIP_CALENDAR` 约束此前**无引用目标**，本域补齐（`calendar_ref → mds_work_calendar.code`）；
- 日历定义"**哪天/哪些时段可用**"（如部分老旧设备 24×5）；班次定义"**一天几段**"；
- 排产与可用性计算在 MES，本域只提供**定义**。

### 4.6 变更留痕（OP6）
- 认证状态的变更（授予/吊销/到期）虽属"业务事件"，但**授予与吊销动作**须留痕（`@History(SNAPSHOT)` 已覆盖认证记录的每次变更）；
- 吊销原因可关联 [reason-defect-design](reason-defect-design.md) 的原因码（如 `ENG`/`HOLD` 类）。

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 组织/供应商/技能/认证/日历实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 认证授予/吊销、供应商资格变更落 `{entity}_hist`（**合规审计关键**） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + 班次/日历种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_org_unit (
  id               VARCHAR(32) NOT NULL,
  tenant_id        VARCHAR(32) NOT NULL,
  code             VARCHAR(64) NOT NULL,
  name             VARCHAR(128),
  org_type         VARCHAR(16) NOT NULL,
  parent_id        VARCHAR(32),
  cost_center_code VARCHAR(32),
  site_code        VARCHAR(64),
  manager_ref      VARCHAR(64),
  status           VARCHAR(16) NOT NULL,
  description      VARCHAR(512),
  version_         BIGINT NOT NULL DEFAULT 0,
  deleted          BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_org PRIMARY KEY (id),
  CONSTRAINT uq_mds_org UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_org_parent FOREIGN KEY (parent_id) REFERENCES mds_org_unit (id)
);

CREATE TABLE mds_vendor (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  code           VARCHAR(64) NOT NULL,
  name           VARCHAR(128),
  vendor_type    VARCHAR(24) NOT NULL,
  contact_name   VARCHAR(64),
  contact_phone  VARCHAR(64),
  contact_email  VARCHAR(64),
  qual_status    VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
  qual_expire_at DATETIME(3),
  service_scope  VARCHAR(256),
  status         VARCHAR(16) NOT NULL,
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_vendor PRIMARY KEY (id),
  CONSTRAINT uq_mds_vendor UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_skill (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  code           VARCHAR(64) NOT NULL,
  name           VARCHAR(128),
  skill_type     VARCHAR(24) NOT NULL,
  level          VARCHAR(16),
  validity_days  INT,
  requires_retrain BIT DEFAULT 1,
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_skill PRIMARY KEY (id),
  CONSTRAINT uq_mds_skill UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_person_certification (
  id            VARCHAR(32) NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  person_ref    VARCHAR(64) NOT NULL,
  person_name   VARCHAR(64),
  skill_id      VARCHAR(32) NOT NULL,
  scope_type    VARCHAR(24),
  scope_ref     VARCHAR(128),
  cert_status   VARCHAR(16) NOT NULL,
  certified_at  DATETIME(3),
  expire_at     DATETIME(3),
  certified_by  VARCHAR(64),
  evidence_ref  VARCHAR(256),
  description   VARCHAR(512),
  version_      BIGINT NOT NULL DEFAULT 0,
  deleted       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_cert PRIMARY KEY (id),
  CONSTRAINT fk_cert_skill FOREIGN KEY (skill_id) REFERENCES mds_skill (id)
);
CREATE INDEX idx_cert_person ON mds_person_certification (person_ref);
CREATE INDEX idx_cert_scope  ON mds_person_certification (scope_type, scope_ref);
CREATE INDEX idx_cert_expire ON mds_person_certification (expire_at);

CREATE TABLE mds_work_calendar (
  id                 VARCHAR(32) NOT NULL,
  tenant_id          VARCHAR(32) NOT NULL,
  code               VARCHAR(64) NOT NULL,
  name               VARCHAR(128),
  timezone           VARCHAR(32) NOT NULL DEFAULT 'Asia/Shanghai',
  work_day_pattern   VARCHAR(32),
  holiday_calendar_ref VARCHAR(64),
  hours_per_day      DECIMAL(4,2),
  status             VARCHAR(16) NOT NULL,
  description        VARCHAR(512),
  version_           BIGINT NOT NULL DEFAULT 0,
  deleted            BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_calendar PRIMARY KEY (id),
  CONSTRAINT uq_mds_calendar UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_shift (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  code         VARCHAR(32) NOT NULL,
  name         VARCHAR(64),
  calendar_ref VARCHAR(64),
  start_time   TIME NOT NULL,
  end_time     TIME NOT NULL,
  cross_day    BIT DEFAULT 0,
  seq          INT,
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_shift PRIMARY KEY (id),
  CONSTRAINT uq_mds_shift UNIQUE (code, tenant_id, deleted)
);
```

> 历史表 `mds_person_certification_hist` / `mds_vendor_hist` / … 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.org
  ├── entity
  │   ├── OrgUnit.java               # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── Vendor.java                 # @Entity 继承 BaseDefData（qual_status/expire）
  │   ├── Skill.java                  # @Entity 继承 BaseDefData
  │   ├── PersonCertification.java    # @Entity 继承 BaseDefData（person_ref→IAM）
  │   ├── WorkCalendar.java           # @Entity 继承 BaseDefData
  │   ├── Shift.java                  # @Entity 继承 BaseDefData
  │   └── package-info.java           # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── OrgUnitRepository.java
  │   ├── VendorRepository.java
  │   └── PersonCertificationRepository.java
  ├── service
  │   ├── OrgUnitService.java          # 继承 AbstractJpaService
  │   ├── VendorService.java            # 供应商资格状态机（APPROVED/PENDING/SUSPENDED/BLACKLISTED）
  │   ├── CertificationService.java      # 授予/吊销/到期扫描；isQualified(person, scope)
  │   └── CalendarService.java           # 可用时段计算（供 MES 查询）
  └── web
      └── OrgController.java             # 查询/组织树/供应商/认证矩阵/日历
```

---

## 8. 待补 / 后续

- **与 IAM 的同步**：`person_ref` / `person_name` 由 IAM 提供给 MDS（只读同步），MDS 不写人员属性。
- **认证到期扫描任务**：定时任务扫描 `expire_at` → 置 `EXPIRED` → 通知；或由约束 `OPERATOR_QUALIFIED` 在下游判定。
- **节假日数据源**：`holiday_calendar_ref` 指向 HR/ERP 的节假日表，MDS 不维护国定假日明细。
- **与既有文档闭环**：本文引用 `mds_equipment_owner`（[equipment-design §3.9](equipment-design.md)）、`mds_material` / `mds_reticle`（[material-design](material-design.md) / [reticle-design](reticle-design.md)）、`mds_pm_task`（[pm-calibration-design](pm-calibration-design.md)）、`EQUIP_CALENDAR` 约束（[constraint-design §4.2](constraint-design.md)）、IAM 边界（[README §1/§2](README.md)）、审批角色（[change-mgmt-design](change-mgmt-design.md) / [md-governance-design](md-governance-design.md)）。
