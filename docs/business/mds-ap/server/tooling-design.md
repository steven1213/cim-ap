# MDS 工装与夹具（Tooling / Fixture）主数据设计

> 本文承接[设备建模](equipment-design.md)（设备/模块/设备类）、[物料设计](material-design.md)（耗材与寿命策略）、[测试资产](test-asset-design.md)（同寿命范式）、[PM/校准](pm-calibration-design.md)，
> 补全 MDS 的**工装与夹具（Tooling / Fixture）** 主数据——**装在设备上、参与加工、但可更换的工装件**。
>
> **与相邻域严格区分**：
> | | 耗材（[material-design](material-design.md)） | **工装（本文）** | 测试资产（[test-asset-design](test-asset-design.md)） |
> | --- | --- | --- | --- |
> | 性质 | **消耗掉**（化学品、靶材） | **可重复使用**、会磨损 | 测试接口（探针卡/负载板） |
> | 例 | 光刻胶、刻蚀气体、靶材 | **CMP 垫/修整器、模具、治具、卡盘、载具托盘** | 探针卡、负载板 |
> | 归属 | 物料主数据 | **本域** | 测试资产域 |
>
> 设计原则延续全篇：**MDS 拥有工装定义、适用关系与寿命策略；使用计数与实时位置归 MES/EAP**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么需要独立域

- **不是物料**：CMP 垫使用若干次后仍需更换，但它是**可复用件**而非一次性消耗品；用材料 BOM 的"用量"表达会失真；
- **不是设备**：工装**装在设备上**、可拆卸、跨设备流转；
- **寿命驱动停机**：CMP 垫磨损直接影响良率与设备可用性，需寿命计数与到期动作（与耗材/测试资产同一机制）。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有者 | 说明 |
| --- | --- | --- |
| 工装定义（类型/规格/适用设备） | **MDS** | `mds_tooling` |
| 寿命策略（使用次数/时长） | **MDS** | `mds_tooling_usage_policy` |
| 适用关系（可用在哪些设备类/型号/实例） | **MDS** | `mds_tooling` + 可选资格表 |
| 行政态（`ACTIVE`/`HOLD`/`RETIRED`） | **MDS** | 资产级 |
| **累计使用次数/时长** | **MES/EAP** | 实时计数 |
| **工装实时位置**（在机/在库） | **MES/AMHS** | MDS 不存 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 工装 ↔ 设备类 | `mds_tooling.applicable_equipment_class_id → mds_equipment_class.id` | [equipment-design §3.2](equipment-design.md) |
| 工装 ↔ 设备型号 | `applicable_model_id → mds_equipment_model.id` | [equipment-design §3.2](equipment-design.md) |
| 工装 ↔ 设备实例资格 | `mds_tooling_qual`（可选，同资格范式） | — |
| 工装 ↔ 仓库 | 复用 `mds_bank`（`bank_type=TOOLING_STOCKER`，**枚举扩展**） | [bank-design §3.1](bank-design.md) |
| 工装 ↔ 供应商 | `vendor_id → mds_vendor.id` | [org-personnel-design §3.2](org-personnel-design.md) |
| 工装 ↔ 约束 | `TOOLING_USAGE_LIMIT` / `TOOLING_AVAILABLE`（新增约束类型） | [constraint-design §4.2](constraint-design.md) |
| 工装 ↔ 表达式 | `tooling.*` 命名空间 | [expression-dsl-design §5](expression-dsl-design.md) |
| 编码规则 | `entity_type=TOOLING`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E10** | 工装磨损导致的停机属计划/非计划停机 | 到期动作触发维护（同 [pm-calibration-design §4.3](pm-calibration-design.md)） |
| **SEMI E30/E87** | 可复用件（如载具托盘）的管理范式 | 复用资格与寿命范式 |
| **CMP 工艺实践** | **CMP 垫（Pad）** 与 **修整器（Conditioner）** 有明确使用寿命（片数/时长），到期必须更换 | `mds_tooling_usage_policy` |
| **封装/模具实践** | **模具（Mold）** 有型腔寿命；**治具（Jig/Fixture）** 有精度衰减 | 同上 |
| **ISO 9001 / IATF** | 工装状态与更换记录需受控 | 与 [document-design](document-design.md) / 变更管理联动 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Tooling / 工装** | 装在设备上参与加工的可更换件 | **不是**耗材（不消耗）、不是设备部件（可拆） |
| **Fixture / 治具** | 定位/夹持用治具 | 是 Tooling 的子类型（`tooling_type=FIXTURE`） |
| **CMP Pad / Conditioner** | 抛光垫 / 修整器 | 典型高价值工装，寿命驱动更换 |
| **Mold / 模具** | 封装成型模具 | 型腔寿命 |
| **usage policy / 寿命策略** | 使用次数/时长上限与到期动作 | 与耗材寿命策略同范式，但对象工种装 |
| **applicable / 适用性** | 可用在哪些设备类/型号 | 与"资格"区别：适用性是定义级，资格是实例级（可选） |

---

## 3. 实体总览与 ER

```
mds_tooling (工装: tooling_type / 适用设备类或型号 / 规格 / 寿命策略)
      ├──> mds_tooling_usage_policy (寿命: max_runs / max_hours / max_wafer_count)
      └──< mds_tooling_qual (可选: 工装 ↔ 设备实例资格)

复用: mds_bank(bank_type=TOOLING_STOCKER)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 工装 | `mds_tooling` | 工装定义与资产登记 |
| 寿命策略 | `mds_tooling_usage_policy` | 使用次数/时长上限 |
| 工装资格 | `mds_tooling_qual` | 可选：实例级资格 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_tooling`（工装）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 工装编码 |
| `name` | VARCHAR(128) | | 名称 |
| `tooling_type` | VARCHAR(24) | NOT NULL | `CMP_PAD`/`CONDITIONER`/`MOLD`/`JIG`/`FIXTURE`/`CHUCK`/`TRAY`/`SPARE_TOOL`/`OTHER` |
| `spec` | VARCHAR(128) | | 规格（尺寸/材质/硬度/型腔数） |
| `applicable_equipment_class_id` | VARCHAR(32) | FK→`mds_equipment_class.id` | 适用设备类 |
| `applicable_model_id` | VARCHAR(32) | FK→`mds_equipment_model.id` | 适用型号（可选，更精确） |
| `load_position` | VARCHAR(64) | | 装载位置（逻辑引用 `mds_equipment_module.code`） |
| `vendor_id` | VARCHAR(32) | FK→`mds_vendor.id` | 制造商 |
| `home_stocker_code` | VARCHAR(64) | FK→`mds_bank.code` | 归属库（`bank_type=TOOLING_STOCKER`） |
| `usage_policy_id` | VARCHAR(32) | FK→`mds_tooling_usage_policy.id` | 寿命策略 |
| `admin_status` | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE' | `ACTIVE`/`HOLD`/`REPAIR`/`RETIRED` |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED`/`ARCHIVED` |
| `ext_attrs` | JSON | | 扩展属性 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.2 `mds_tooling_usage_policy`（寿命策略）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 策略代码 |
| `tooling_type` | VARCHAR(24) | | 适用工装类型 |
| `max_runs` | BIGINT | | 最大使用次数（run/lot） |
| `max_hours` | DECIMAL(12,2) | | 最大使用时长（小时） |
| `max_wafer_count` | BIGINT | | 最大累计片数 |
| `max_age_days` | INT | | 最长使用期限 |
| `on_exhaust_action` | VARCHAR(16) | NOT NULL | `REPLACE`/`REFURBISH`（翻新）/`RETIRE`/`HOLD` |
| `warning_ratio` | DECIMAL(4,2) | | 预警比例 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.3 `mds_tooling_qual`（工装 ↔ 设备实例资格，可选）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `tooling_id` | VARCHAR(32) | NOT NULL, FK→`mds_tooling.id` | 工装 |
| `equipment_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment.id` | 设备实例 |
| `qual_status` | VARCHAR(16) | NOT NULL DEFAULT 'QUALIFIED' | `QUALIFIED`/`PENDING`/`REVOKED` |
| `qualified_by`/`qualified_at` | — | | 资格留痕 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(tooling_id, equipment_id, tenant_id, deleted)`。
- **仅在需要实例级管控时使用**（多数场景用 `applicable_equipment_class_id` 即可）。

---

## 4. 关键设计点（拍板 TL1–TL4）

### 4.1 与耗材、测试资产三分（TL1）
见 header 对比表。三者**寿命机制相同**（策略 + 计数 + 到期动作），但**性质不同**（消耗 vs 可复用 vs 测试接口），故分域。

### 4.2 适用性两级（TL2）
- **设备类级**（`applicable_equipment_class_id`）：多数场景，如"CMP_PAD_X 适用于 CMP 设备类"；
- **型号级**（`applicable_model_id`）：更精确；
- **实例级资格**（`mds_tooling_qual`）：仅在需逐台认证时启用（如高精度治具）。

### 4.3 寿命与到期动作（TL3）
- 三类上限：`max_runs` / `max_hours` / `max_wafer_count`（外加 `max_age_days`），先到者触发；
- 到期动作区分 `REPLACE`（换新）与 `REFURBISH`（翻新后再用，如 CMP 垫修整）——**翻新场景必须显式建模**，否则会误判为报废；
- **MDS 给策略，EAP/MES 计数，MES 执行**（同 [material-design §4.4](material-design.md) 分工）。

### 4.4 与约束/表达式联动（TL4）
- `TOOLING_AVAILABLE`：派工前校验"所需工装是否到位且未超寿命"；
- `TOOLING_USAGE_LIMIT`：用量超限阻断；
- `tooling.*` 变量可入表达式（[expression-dsl-design §5](expression-dsl-design.md)）。

### 4.5 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 工装实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 工装与策略变更落 `{entity}_hist` |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + 工装类型种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_tooling_usage_policy (
  id               VARCHAR(32) NOT NULL,
  tenant_id        VARCHAR(32) NOT NULL,
  code             VARCHAR(64) NOT NULL,
  tooling_type     VARCHAR(24),
  max_runs         BIGINT,
  max_hours        DECIMAL(12,2),
  max_wafer_count  BIGINT,
  max_age_days     INT,
  on_exhaust_action VARCHAR(16) NOT NULL,
  warning_ratio    DECIMAL(4,2),
  description      VARCHAR(512),
  version_         BIGINT NOT NULL DEFAULT 0,
  deleted          BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_tup PRIMARY KEY (id),
  CONSTRAINT uq_mds_tup UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_tooling (
  id                            VARCHAR(32) NOT NULL,
  tenant_id                     VARCHAR(32) NOT NULL,
  code                          VARCHAR(64) NOT NULL,
  name                          VARCHAR(128),
  tooling_type                  VARCHAR(24) NOT NULL,
  spec                          VARCHAR(128),
  applicable_equipment_class_id VARCHAR(32),
  applicable_model_id           VARCHAR(32),
  load_position                 VARCHAR(64),
  vendor_id                     VARCHAR(32),
  home_stocker_code             VARCHAR(64),
  usage_policy_id               VARCHAR(32),
  admin_status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  status                        VARCHAR(16) NOT NULL,
  ext_attrs                     JSON,
  description                   VARCHAR(512),
  version_                      BIGINT NOT NULL DEFAULT 0,
  deleted                       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_tooling PRIMARY KEY (id),
  CONSTRAINT uq_mds_tooling UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_tooling_class FOREIGN KEY (applicable_equipment_class_id) REFERENCES mds_equipment_class (id),
  CONSTRAINT fk_tooling_model FOREIGN KEY (applicable_model_id) REFERENCES mds_equipment_model (id),
  CONSTRAINT fk_tooling_policy FOREIGN KEY (usage_policy_id) REFERENCES mds_tooling_usage_policy (id)
);
CREATE INDEX idx_tooling_type ON mds_tooling (tooling_type, admin_status);

CREATE TABLE mds_tooling_qual (
  id            VARCHAR(32) NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  tooling_id    VARCHAR(32) NOT NULL,
  equipment_id  VARCHAR(32) NOT NULL,
  qual_status   VARCHAR(16) NOT NULL DEFAULT 'QUALIFIED',
  qualified_by  VARCHAR(64),
  qualified_at  DATETIME(3),
  description   VARCHAR(512),
  version_      BIGINT NOT NULL DEFAULT 0,
  deleted       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_tooling_qual PRIMARY KEY (id),
  CONSTRAINT uq_mds_tooling_qual UNIQUE (tooling_id, equipment_id, tenant_id, deleted),
  CONSTRAINT fk_tq_tooling FOREIGN KEY (tooling_id) REFERENCES mds_tooling (id),
  CONSTRAINT fk_tq_eq FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id)
);
```

> 历史表由 `@History(SNAPSHOT)` 生成。
> **枚举扩展**：`mds_bank.bank_type` 增 `TOOLING_STOCKER`。

---

## 7. 代码结构（包路径）

```
com.cim.mds.tooling
  ├── entity
  │   ├── Tooling.java                 # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── ToolingUsagePolicy.java        # @Entity 继承 BaseDefData
  │   ├── ToolingQual.java                # @Entity 继承 BaseDefData
  │   └── package-info.java              # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── ToolingRepository.java
  │   └── ToolingQualRepository.java
  ├── service
  │   ├── ToolingService.java              # 继承 AbstractJpaService；行政态迁移
  │   └── ToolingResolver.java              # 按设备类/型号解析可用工装（供派工）
  └── web
      └── ToolingController.java           # 工装登记/适用矩阵/寿命台账
```

---

## 8. 与 MES 生产执行衔接

| 场景 | MDS 提供 | MES/EAP 使用 |
| --- | --- | --- |
| **派工前校验** | 适用设备类/型号、行政态、寿命策略 | MES 校验「该设备所需工装是否已装且未超寿命」；`TOOLING_AVAILABLE` 约束可 BLOCK |
| **换装指导** | `load_position` + 寿命策略 | MES 在到期时提示更换/翻新；`REFURBISH` 后可恢复 `ACTIVE` |
| **用量计数** | 策略阈值与 `warning_ratio` | EAP/MES 累计 `runs`/`hours`/`wafer_count` → 预警与到期动作 |
| **CMP 场景** | CMP_PAD/CONDITIONER 寿命 | MES 按片数更换 pad，按时长修整 conditioner |
| **追溯** | 工装编号 + 寿命策略版本 | 回答"这批加工时用的是哪个 pad、用了多少次" |
| **翻新闭环** | `on_exhaust_action=REFURBISH` | MES 走翻新流程 → 复检 → 回写 `admin_status=ACTIVE` |

> **对 MES 的关键价值**：工装寿命是**良率与设备可用性的隐性杀手**；建模后可从"凭经验换"变为"按数据换"，并可与良率波动做相关性分析。

---

## 9. 待补 / 后续

- **翻新与再资格流程**：`REFURBISH` 后的复检标准（可关联 [pm-calibration-design](pm-calibration-design.md)）。
- **工装库存**：是否需要独立的工装库存台账（当前复用 `mds_bank` 表达归属库）。
- **多工装组合**：一台设备同时装多个工装的关系（可扩展为连接表）。
- **与既有文档闭环**：本文引用 [equipment-design](equipment-design.md)、[material-design](material-design.md)（区分）、[test-asset-design](test-asset-design.md)（同范式）、[bank-design](bank-design.md)（库）、[org-personnel-design](org-personnel-design.md)（供应商）、[constraint-design](constraint-design.md)（新增 `TOOLING_*`）、[expression-dsl-design §5](expression-dsl-design.md)、[realtime-contract-design §2](realtime-contract-design.md)、[naming-rule-design](naming-rule-design.md)。
