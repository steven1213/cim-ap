# MDS 原因码与不良/处置规则主数据设计

> 本文承接[设备建模 §3.14](equipment-design.md)（E10 状态机）、[工艺流](process-flow-design.md)（工艺参数）、[约束设计](constraint-design.md)、[设备接口点表](equipment-interface-design.md)（ALID），
> 补全 MDS 的**原因码（Reason Code）/ 缺陷码（Defect Code）/ 分选代码（Bin Code）/ 处置规则（Disposition Rule）**。
>
> 它是全厂的**统一语义字典**：任何「停机、报废、扣留、返工、放行」事件都必须挂一个受控代码，否则报表、SPC、OEE、追溯口径必然互相打架。
>
> 设计原则延续全篇：**MDS 拥有代码字典与处置规则；事件实例（哪台机何时停机、哪批被扣）归 MES**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么需要统一的代码字典（核心）

如果各系统自己维护原因码/不良码：

| 症状 | 后果 |
| --- | --- |
| MES 一套停机原因、EAP 一套、报表又一套 | 同一停机在三个系统是三种分类，OEE 无法对齐 |
| 缺陷码是自由文本 | 无法按缺陷类型聚合，良率分析失效 |
| 处置条件散在代码里 | "什么情况下扣留"不可查、不可审、改一次要发版 |

> **结论（RD1）**：原因码/缺陷码/处置规则是 MDS 的**横切语义字典域**，与 [naming-rule-design](naming-rule-design.md)（编码）、[constraint-design](constraint-design.md)（关系规则）并列的**第三条横切基础数据**（语义字典类）。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 停机/报废/扣留/返工原因码字典 | **MDS** | `mds_reason_code`，本文范围 |
| 缺陷码字典（分层） | **MDS** | `mds_defect_code` |
| 分选 Bin 代码 | **MDS** | `mds_bin_code` |
| 处置规则（条件 → 动作） | **MDS** | `mds_disposition_rule` |
| **事件实例**（哪台机、何时、什么原因） | **MES / EAP** | MDS 不存事件 |
| **缺陷实测**（哪片、几个、什么位置） | **MES / YMS** | MDS 不存实测 |
| **处置执行**（扣留/放行/报废的实际动作） | **MES** | MDS 给规则，执行在下游 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 原因码 ↔ E10 停机状态 | `mds_reason_code`（`reason_type=DOWNTIME`）用于 `SD*`/`UD*` 的原因 | [equipment-design §3.11](equipment-design.md) |
| 报警 ↔ 默认原因码 | `mds_equip_if_alarm.reason_code_ref → mds_reason_code.code` | [equipment-interface-design §3.4](equipment-interface-design.md) |
| 处置规则 ↔ 工序/产品 | `mds_disposition_rule` 的 scope 可绑 operation/product | [route-design §3.3](route-design.md) / [product-design §3.2](product-design.md) |
| 处置规则 ↔ 工艺参数 | 条件可引用 `mds_process_flow_step_param` 超差 | [process-flow-design §3.4](process-flow-design.md) |
| 处置规则 ↔ SPC 失控 | OCAP 动作引用处置（`HOLD`/`REWORK`） | [sampling-spc-design](sampling-spc-design.md) |
| 处置规则 ↔ 约束 | 可作为约束（新增 `DISPOSITION_TRIGGER` 类型） | [constraint-design §4.2](constraint-design.md) |
| 缺陷码 ↔ 返工/报废 | `is_rework`/`is_scrap` 影响工序流转 | [route-design §4.3](route-design.md) |
| 代码编码规则 | `entity_type=REASON`/`DEFECT`/`BIN`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E10** | 停机必须有**原因分类**（计划/非计划、批次/测试/无操作员）；RAM 与 OEE 的计算基于原因码一致性 | `mds_reason_code` 与 E10 `category` 对齐 |
| **SEMI E116 / E79** | 设备性能与效率分析需要标准化的停机原因 | 原因码树 + `e10_category` 字段 |
| **SEMI E30（GEM）** | ALID 报警与 CEID 事件是"原因"的自动化来源 | 报警带 `reason_code_ref` 默认原因 |
| **SEMI E5 / 分选（Sort/Bin）** | 晶圆测试后按 Bin 分类（Pass Bin / Fail Bin / Defect Bin） | `mds_bin_code`（`bin_type`） |
| **fab 良率与缺陷管理（YMS/DFM）** | 缺陷按**类型树**分类（颗粒/划伤/图形缺陷/膜厚异常…），支持按类聚合 | `mds_defect_code` 树形 |
| **ISO 9001 / 8D / 纠正措施** | 不合格品处置（让步接收/返工/报废）需受控规则与记录 | `mds_disposition_rule` + 签核（[md-governance-design](md-governance-design.md)） |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Reason Code / 原因码** | 事件原因的受控代码（停机/报废/扣留/返工） | 一个字段承载多类；用 `reason_type` 区分 |
| **Defect Code / 缺陷码** | 缺陷**类型**的受控代码（颗粒/划伤/图形异常…） | 不是原因码（原因=为什么发生；缺陷=发生了什么） |
| **Bin Code / 分选码** | 测试后晶圆/芯片的分类码 | 不是缺陷码（Bin 是**结果分类**，缺陷是**现象分类**） |
| **Disposition / 处置** | 对不合格品的处理决定（放行/扣留/返工/报废/送工程） | 不是流程分支（`flow_type`）；处置是**规则触发的动作** |
| **Disposition Rule / 处置规则** | 「满足条件 → 执行处置」的声明式规则 | 与约束（constraint）相似但语义不同：约束是**可否**，处置是**怎么处理**（§4.5） |
| **E10 category** | 停机原因的分类（计划/非计划/批次/测试/无操作员） | 用于 OEE 归类，不是原因码本身 |

---

## 3. 实体总览与 ER

```
mds_reason_code (树: reason_type ∈ DOWNTIME/SCRAP/HOLD/REWORK/ENG/PAUSE)
        ▲ parent_id
        └── e10_category (计划/非计划分类, 供 OEE)

mds_defect_code (树: defect_class / severity / spec_ref)
        ▲ parent_id

mds_bin_code (bin_type ∈ PASS/FAIL/DEFECT/PARTIAL)

mds_disposition_rule (规则头: scope + trigger + action + priority)
        └──< mds_disposition_rule_item (条件项)

引用方: mds_equip_if_alarm.reason_code_ref → mds_reason_code.code
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 原因码 | `mds_reason_code` | 树形；多类事件原因 |
| 缺陷码 | `mds_defect_code` | 树形缺陷分类 |
| 分选码 | `mds_bin_code` | 测试 Bin 分类 |
| 处置规则 | `mds_disposition_rule` | 条件→动作 |
| 处置条件项 | `mds_disposition_rule_item` | 条件明细 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_reason_code`（原因码）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | 原因码（如 `DT-MECH`、`SC-PARTICLE`） |
| `name` | VARCHAR(128) | | 名称 |
| `reason_type` | VARCHAR(16) | NOT NULL | `DOWNTIME`/`SCRAP`/`HOLD`/`REWORK`/`ENG`/`PAUSE` |
| `parent_id` | VARCHAR(32) | FK→self | 父码（树形） |
| `e10_category` | VARCHAR(16) | | E10 分类：`SCHEDULED`/`UNSCHEDULED`/`ENGINEERING`/`NON_SCHEDULED` |
| `is_equipment_related` | BIT | DEFAULT 0 | 是否设备相关（供 MTBF/MTTR 归因） |
| `is_operator_error` | BIT | DEFAULT 0 | 是否人为（供培训归因） |
| `requires_comment` | BIT | DEFAULT 0 | 是否必须填备注 |
| `default_for_alarm` | BIT | DEFAULT 0 | 是否某报警的默认原因（冗余，便于查询） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, reason_type, tenant_id, deleted)`。

### 3.2 `mds_defect_code`（缺陷码）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | 缺陷码（如 `DEF-PART`、`DEF-SCR`） |
| `name` | VARCHAR(128) | | 名称 |
| `defect_class` | VARCHAR(24) | NOT NULL | `PARTICLE`/`SCRATCH`/`PATTERN`/`FILM`/`RESIDUE`/`CRACK`/`DISCOLOR`/`ELECTRICAL`/`OTHER` |
| `parent_id` | VARCHAR(32) | FK→self | 父码（树形） |
| `severity` | VARCHAR(16) | | `CRITICAL`/`MAJOR`/`MINOR` |
| `spec_ref` | VARCHAR(64) | | 判定标准引用（如"粒径>0.1μm 即判"） |
| `is_kill` | BIT | DEFAULT 0 | 是否致命缺陷（Kill Ratio 用） |
| `layer_scope` | VARCHAR(64) | | 常发层（[layer-design](layer-design.md) `code`，可空） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.3 `mds_bin_code`（分选码）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `bin_no` | INT | NOT NULL | Bin 号（测试程序里的数字码） |
| `code` | VARCHAR(32) | NOT NULL | Bin 代码 |
| `name` | VARCHAR(128) | | 名称 |
| `bin_type` | VARCHAR(16) | NOT NULL | `PASS`/`FAIL`/`DEFECT`/`PARTIAL`/`UNTESTED` |
| `defect_code_ref` | VARCHAR(32) | FK→`mds_defect_code.code` | 关联缺陷码（可选） |
| `is_scrap` | BIT | DEFAULT 0 | 是否直接报废 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(bin_no, tenant_id, deleted)` / `(code, tenant_id, deleted)`。

### 3.4 `mds_disposition_rule` / `mds_disposition_rule_item`（处置规则）

**`mds_disposition_rule`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 规则代码 |
| `name` | VARCHAR(128) | | 名称 |
| `scope_type` | VARCHAR(24) | | 作用域：`GLOBAL`/`PRODUCT`/`ROUTE`/`ROUTE_OPERATION`/`EQUIPMENT_CLASS`/`LAYER`/`DEFECT_CLASS` |
| `scope_ref` | VARCHAR(128) | | 作用域目标（id/code；`GLOBAL` 用 `*`） |
| `action` | VARCHAR(16) | NOT NULL | `RELEASE`（放行）/`HOLD`（扣留）/`REWORK`（返工）/`SCRAP`（报废）/`SAMPLE`（送样）/`ENG_REVIEW`（送工程） |
| `reason_code_ref` | VARCHAR(32) | FK→`mds_reason_code.code` | 处置时挂的原因码 |
| `priority` | INT | DEFAULT 0 | 优先级（多规则命中取高） |
| `requires_approval` | BIT | DEFAULT 0 | 是否需审批（联动 [change-mgmt-design](change-mgmt-design.md) / [md-governance-design](md-governance-design.md)） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `revision` | VARCHAR(16) | NOT NULL | 版本 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

**`mds_disposition_rule_item`（条件项）**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `rule_id` | VARCHAR(32) | NOT NULL, FK→`mds_disposition_rule.id` | 所属规则 |
| `seq` | INT | NOT NULL | 条件顺序 |
| `cond_key` | VARCHAR(64) | NOT NULL | 条件字段（`defect_count`/`bin_no`/`param_value`/`rework_times`…） |
| `operator` | VARCHAR(8) | NOT NULL | `GT`/`GE`/`LT`/`LE`/`EQ`/`NE`/`IN`/`BETWEEN` |
| `cond_value` | VARCHAR(256) | NOT NULL | 阈值（`BETWEEN` 用 `a|b`，`IN` 用逗号分隔） |
| `logic` | VARCHAR(4) | DEFAULT 'AND' | 与上一项的组合：`AND`/`OR` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(rule_id, seq, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 RD1–RD6）

### 4.1 三类代码各司其职（RD2）
| | 原因码 | 缺陷码 | 分选码 |
| --- | --- | --- | --- |
| 回答 | **为什么**（停机/报废/扣留的原因） | **是什么**（缺陷现象类型） | **判成什么**（测试结果分类） |
| 形态 | 树 + 多 `reason_type` | 树 + `defect_class` | 平铺 + `bin_no` |
| 使用者 | E10/OEE、维护、MES | YMS、DFM、MES | 测试（CP/FT）、MES |
| 三者关系 | 可共用（Bin=FAIL → 挂 Defect → 处置挂 Reason） | | |

> 三者**不可合并**：一个是原因、一个是现象、一个是结果。合并会导致分析维度塌陷。

### 4.2 原因码与 E10 状态机联动（RD3）
- 设备进入 `SD*`/`UD*`（[equipment-design §3.11](equipment-design.md)）**必须挂 `reason_type=DOWNTIME` 的原因码**；
- `e10_category` 让 OEE 自动归类（计划/非计划）；
- 报警 `auto_down=1` 时（[equipment-interface-design §3.4](equipment-interface-design.md)），默认原因由 `mds_equip_if_alarm.reason_code_ref` 提供，**避免人工漏填**。

### 4.3 缺陷码与层/工序归因（RD4）
- `layer_scope` 让缺陷快速归因到层（[layer-design](layer-design.md)）；
- `is_kill` 支持 Kill Ratio 与良率损失计算；
- 与工艺流参数超差（[process-flow-design §3.4](process-flow-design.md)）互为因果：参数超窗 → 缺陷 → 处置。

### 4.4 处置规则的声明式表达（RD5）
- 「条件（`_item` 组合）→ 动作（`action`）」完全声明式，条件字段可引用：
  - 缺陷数量/类别、Bin 号、工艺参数值、返工次数、层、设备类；
- 多规则命中按 `priority` 取高；`RELEASE`/`HOLD` 冲突时**取更保守**（HOLD 优先）——安全侧默认；
- `requires_approval=1` 时，处置动作须走审批留痕。

### 4.5 处置规则 vs 约束（RD6，重要区分）
| | 约束（[constraint-design](constraint-design.md)） | 处置规则（本文） |
| --- | --- | --- |
| 语义 | **可否**（允许/禁止某行为） | **怎么办**（不合格品如何处理） |
| 结果 | PASS / FAIL（+severity） | 一个**动作**（HOLD/REWORK/SCRAP…） |
| 时机 | 事前（派工/投料前拦截） | 事后（异常已发生，决定去向） |
| 关系 | 约束可引用处置规则作为**后果** | 处置规则可作为约束命中后的动作 |

> 二者互补：约束"拦住不该发生的"，处置规则"处理已经发生的"。实现上可共用表达式 DSL 与执行上下文。

### 4.6 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 四类实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 代码字典与处置规则变更落 `{entity}_hist`（代码变更影响历史报表口径，必须留痕） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + E10 停机原因/常见缺陷码种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_reason_code (
  id                  VARCHAR(32) NOT NULL,
  tenant_id           VARCHAR(32) NOT NULL,
  code                VARCHAR(32) NOT NULL,
  name                VARCHAR(128),
  reason_type         VARCHAR(16) NOT NULL,
  parent_id           VARCHAR(32),
  e10_category        VARCHAR(16),
  is_equipment_related BIT DEFAULT 0,
  is_operator_error   BIT DEFAULT 0,
  requires_comment    BIT DEFAULT 0,
  default_for_alarm   BIT DEFAULT 0,
  status              VARCHAR(16) NOT NULL,
  description         VARCHAR(512),
  version_            BIGINT NOT NULL DEFAULT 0,
  deleted             BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_reason PRIMARY KEY (id),
  CONSTRAINT uq_mds_reason UNIQUE (code, reason_type, tenant_id, deleted),
  CONSTRAINT fk_reason_parent FOREIGN KEY (parent_id) REFERENCES mds_reason_code (id)
);
CREATE INDEX idx_reason_type ON mds_reason_code (reason_type);

CREATE TABLE mds_defect_code (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  code         VARCHAR(32) NOT NULL,
  name         VARCHAR(128),
  defect_class VARCHAR(24) NOT NULL,
  parent_id    VARCHAR(32),
  severity     VARCHAR(16),
  spec_ref     VARCHAR(64),
  is_kill      BIT DEFAULT 0,
  layer_scope  VARCHAR(64),
  status       VARCHAR(16) NOT NULL,
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_defect PRIMARY KEY (id),
  CONSTRAINT uq_mds_defect UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_defect_parent FOREIGN KEY (parent_id) REFERENCES mds_defect_code (id)
);

CREATE TABLE mds_bin_code (
  id              VARCHAR(32) NOT NULL,
  tenant_id       VARCHAR(32) NOT NULL,
  bin_no          INT NOT NULL,
  code            VARCHAR(32) NOT NULL,
  name            VARCHAR(128),
  bin_type        VARCHAR(16) NOT NULL,
  defect_code_ref VARCHAR(32),
  is_scrap        BIT DEFAULT 0,
  description     VARCHAR(512),
  version_        BIGINT NOT NULL DEFAULT 0,
  deleted         BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_bin PRIMARY KEY (id),
  CONSTRAINT uq_mds_bin_no UNIQUE (bin_no, tenant_id, deleted),
  CONSTRAINT uq_mds_bin_code UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_disposition_rule (
  id                VARCHAR(32) NOT NULL,
  tenant_id         VARCHAR(32) NOT NULL,
  code              VARCHAR(64) NOT NULL,
  name              VARCHAR(128),
  scope_type        VARCHAR(24),
  scope_ref         VARCHAR(128),
  action            VARCHAR(16) NOT NULL,
  reason_code_ref   VARCHAR(32),
  priority          INT DEFAULT 0,
  requires_approval BIT DEFAULT 0,
  status            VARCHAR(16) NOT NULL,
  revision           VARCHAR(16) NOT NULL,
  description       VARCHAR(512),
  version_          BIGINT NOT NULL DEFAULT 0,
  deleted           BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_disp PRIMARY KEY (id),
  CONSTRAINT uq_mds_disp UNIQUE (code, tenant_id, deleted)
);
CREATE INDEX idx_disp_scope ON mds_disposition_rule (scope_type, scope_ref);

CREATE TABLE mds_disposition_rule_item (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  rule_id     VARCHAR(32) NOT NULL,
  seq         INT NOT NULL,
  cond_key    VARCHAR(64) NOT NULL,
  operator    VARCHAR(8) NOT NULL,
  cond_value  VARCHAR(256) NOT NULL,
  logic       VARCHAR(4) DEFAULT 'AND',
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_disp_item PRIMARY KEY (id),
  CONSTRAINT uq_mds_disp_item UNIQUE (rule_id, seq, tenant_id, deleted),
  CONSTRAINT fk_dispitem_rule FOREIGN KEY (rule_id) REFERENCES mds_disposition_rule (id)
);
```

> 历史表 `mds_reason_code_hist` / `mds_defect_code_hist` / `mds_disposition_rule_hist` / … 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.quality
  ├── entity
  │   ├── ReasonCode.java            # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── DefectCode.java             # @Entity 继承 BaseDefData
  │   ├── BinCode.java                # @Entity 继承 BaseDefData
  │   ├── DispositionRule.java        # @Entity 继承 BaseDefData
  │   ├── DispositionRuleItem.java    # @Entity 继承 BaseDefData
  │   └── package-info.java           # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── ReasonCodeRepository.java
  │   └── DispositionRuleRepository.java
  ├── service
  │   ├── ReasonCodeService.java       # 继承 AbstractJpaService
  │   ├── DispositionResolver.java      # 条件求值 → 动作（多规则按 priority，冲突取保守）
  │   └── CodeDictValidator.java        # 树无环、Bin 号唯一、条件字段合法性
  └── web
      └── QualityDictController.java    # 查询/字典维护/处置规则试算
```

---

## 8. 待补 / 后续

- **与 SPC/OOC 的联动**：OCAP 动作引用本域处置（[sampling-spc-design](sampling-spc-design.md)）。
- **条件字段字典**：`cond_key` 的合法取值需与可求值上下文对齐（与 [constraint-design §4.7](constraint-design.md) 的求值上下文共享定义）。
- **缺陷-原因因果链**：缺陷 → 原因（供 8D / 根因分析），可作为分析域的关联表。
- **与既有文档闭环**：本文引用 `mds_equipment_state_transition`（[equipment-design](equipment-design.md)）、`mds_equip_if_alarm`（[equipment-interface-design](equipment-interface-design.md)）、`mds_layer`（[layer-design](layer-design.md)）、`mds_process_flow_step_param`（[process-flow-design](process-flow-design.md)）、新增 `entity_type=REASON/DEFECT/BIN`（[naming-rule-design](naming-rule-design.md)）、新增约束类型 `DISPOSITION_TRIGGER`（[constraint-design](constraint-design.md)）。
