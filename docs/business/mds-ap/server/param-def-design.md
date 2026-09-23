# MDS 工艺参数字典（Parameter Dictionary）设计

> 本文补齐 MDS 的 **L0 基础参考数据**中缺失的一块——**参数定义（Parameter Dictionary）**。
>
> **补的缺口**：[工艺流](process-flow-design.md) 的 `mds_process_flow_step_param.param_name` 是**自由文本**（`VARCHAR(64)`，如 `TEMP`/`PRESSURE`）；[设备接口点表](equipment-interface-design.md) 的 `mds_equip_if_variable` 有**独立的编号体系**；[采样与 SPC](sampling-spc-design.md) 的 metric、[PM/校准](pm-calibration-design.md) 的校准项又各写一套。同一物理量在四处有四个名字 → **跨域聚合、SPC 归集、流程对比、良率分析全部对不上**。
>
> 设计原则延续全篇：**MDS 拥有参数定义（语义/类型/单位）；各域引用它；实测值归下游**。

---

## 0. 定位与边界（拍板）

### 0.1 缺口实证（核心）

| 位置 | 现状 | 问题 |
| --- | --- | --- |
| `mds_process_flow_step_param` | `param_name` 自由文本 | `TEMP` / `Temperature` / `CH_TEMP` 三种写法并存 |
| `mds_equip_if_variable` | `code` = SVID/DVID 数字编号 | 与工艺参数无映射关系 |
| `mds_calibration_spec.variable_ref` | 指向设备变量 code | 校准项与工艺参数不同源 |
| SPC 采样 metric | 引用 `param_ref` 或 `variable_ref` 二选一 | 语义不统一 |

> **结论（PD1）**：需要一张**参数定义主表 `mds_param_def`**，作为**全厂唯一参数语义来源**；各域保留自己的表，但**新增 `param_def_id` 外键**指向它。这样既统一语义、又不破坏各域结构。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 参数定义（编码/名称/语义/类型/单位/量纲） | **MDS** | `mds_param_def` |
| 参数别名与外部系统映射 | **MDS** | `mds_param_alias` |
| 枚举型参数的取值集 | **MDS** | `mds_param_enum` |
| 参数分组（成套参数） | **MDS** | `mds_param_group` |
| **参数的实测值 / 实时值** | **MES / EAP / SPC** | MDS 不存（[realtime-contract-design](realtime-contract-design.md)） |
| **参数的工艺窗口（target/min/max）** | **MDS** | 仍归 [process-flow-design §3.4](process-flow-design.md)（本域只定义"是什么"，不定义"要求多少"） |

> **边界**：本域回答「**这个参数是什么**」（语义/类型/单位）；[process-flow-design](process-flow-design.md) 回答「**要求它在什么窗口内**」（spec 限）。二者分离，避免一处改动两处。

### 0.3 与既有主数据的闭环映射

| 引用方 | 新增字段 | 见 |
| --- | --- | --- |
| 工艺流步骤参数 | `mds_process_flow_step_param.param_def_id → mds_param_def.id` | [process-flow-design §3.4](process-flow-design.md) |
| 设备接口变量 | `mds_equip_if_variable.param_def_id → mds_param_def.id` | [equipment-interface-design §3.2](equipment-interface-design.md) |
| 采样计划 | `mds_sampling_plan.param_def_id` | [sampling-spc-design §3.1](sampling-spc-design.md) |
| SPC 规则项 | `mds_spc_rule_term.param_def_id` | [sampling-spc-design §3.2](sampling-spc-design.md) |
| 校准规格 | `mds_calibration_spec.param_def_id` | [pm-calibration-design §3.4](pm-calibration-design.md) |
| 表达式变量 | `param.*` 命名空间对齐本域 | [expression-dsl-design §5](expression-dsl-design.md) |
| 单位 | `base_uom → mds_uom.code` | [uom-dict-design §3.1](uom-dict-design.md) |
| 物料规格（来料指标） | `mds_material_spec` 可关联（`param_category=MATERIAL`） | [material-design §3.3](material-design.md) |
| APC/FDC 模型 | 控制/目标变量引用本域 | [apc-fdc-design](apc-fdc-design.md) |
| 环境监控 | 监控项引用本域 | [cleanliness-env-design](cleanliness-env-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E30 / E5** | SV/DV/EC 有 SVID/DVID/ECID 编号体系，但**编号≠语义**；跨设备需语义映射 | `mds_param_def` 作为语义层，`mds_param_alias` 承载各设备编号 |
| **SEMI E40 / E94** | 配方参数与工艺参数需可对齐（同参数在配方与流程中语义一致） | `param_def_id` 同时挂配方与流程步骤 |
| **SEMI E116（设备性能）** | 过程能力分析（Cpk）要求同一参数跨时间/设备可聚合 | 统一 `param_def` 是聚合前提 |
| **SPC / MSA（测量系统分析）** | 参数须有明确的量纲、单位、精度、测量方法 | `data_type`/`base_uom`/`dimension`/`measurement_method` |
| **MDM / 参考数据管理** | 参数/指标字典是 MDM 的必备参考数据集 | 本域整体定位 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Param Def / 参数定义** | 参数的**语义定义**（不是值，不是窗口） | 与 `step_param`（工艺窗口）区分：一个是"是什么"，一个是"要求多少" |
| **param_category** | 参数分类：`PROCESS`（工艺）/`EQUIPMENT`（设备）/`METROLOGY`（量测）/`ENV`（环境）/`MATERIAL`（物料） | 不是 `param_group`（分组是业务归类） |
| **dimension / 量纲** | 物理量纲（温度/压力/时间/长度…） | 与 `unit` 不同：量纲是"温度"，单位是"℃" |
| **alias / 别名** | 同一参数在不同系统的叫法（设备 SVID、旧系统名、供应商名） | 不是"同义词展示"，是**映射关系** |
| **measurement_method** | 测量方法（热电偶/光学/干涉） | 影响 MSA 与可比性 |
| **is_key_param** | 是否关键参数（KPIV/KPOV） | 决定变更严重度与 SPC 强制采样 |

---

## 3. 实体总览与 ER

```
mds_param_def (参数定义: code / category / data_type / base_uom / dimension)
     ├──< mds_param_alias  (别名: source_system + alias_name → param)
     ├──< mds_param_enum   (枚举型取值)
     └──< mds_param_group_member >── mds_param_group (参数分组)

引用方（各域新增 param_def_id）:
   mds_process_flow_step_param / mds_equip_if_variable /
   mds_sampling_plan / mds_spc_rule_term / mds_calibration_spec /
   mds_apc_model / mds_env_monitor_point
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 参数定义 | `mds_param_def` | 唯一参数语义来源 |
| 参数别名 | `mds_param_alias` | 多系统名称映射 |
| 参数枚举 | `mds_param_enum` | 枚举型取值 |
| 参数分组 | `mds_param_group` | 成套参数 |
| 分组成员 | `mds_param_group_member` | 组 ↔ 参数 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_param_def`（参数定义）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(32) | NOT NULL | 参数代码（如 `TEMP`/`PRESSURE`/`RF_POWER`）——**全厂唯一语义** |
| `name` | VARCHAR(128) | | 名称 |
| `param_category` | VARCHAR(16) | NOT NULL | `PROCESS`/`EQUIPMENT`/`METROLOGY`/`ENV`/`MATERIAL` |
| `data_type` | VARCHAR(16) | NOT NULL | `NUMERIC`/`INTEGER`/`STRING`/`ENUM`/`BOOLEAN`/`DATETIME` |
| `dimension` | VARCHAR(16) | | 量纲（`TEMPERATURE`/`PRESSURE`/`TIME`/`LENGTH`/`POWER`/`FLOW`/`CONCENTRATION`/`RATIO`/`NONE`） |
| `base_uom` | VARCHAR(16) | FK→`mds_uom.code` | 基本单位（全厂统一口径） |
| `precision` | INT | | 显示/比较精度（小数位） |
| `measurement_method` | VARCHAR(64) | | 测量方法（MSA 用） |
| `normal_range_low`/`normal_range_high` | DECIMAL(18,6) | | 正常范围（供合理性校验，**非 spec 限**） |
| `is_key_param` | BIT | DEFAULT 0 | 是否关键参数（KPIV/KPOV） |
| `semantic_note` | VARCHAR(512) | | 语义说明（防歧义） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `revision` | VARCHAR(16) | NOT NULL | 版本（改语义/单位升版） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.2 `mds_param_alias`（参数别名 / 外部映射）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `param_def_id` | VARCHAR(32) | NOT NULL, FK→`mds_param_def.id` | 参数 |
| `source_system` | VARCHAR(32) | NOT NULL | 来源系统/设备型号（`SECS_GEM`/`MODEL_XXX`/`LEGACY_MES`/`SUPPLIER_A`） |
| `alias_name` | VARCHAR(64) | NOT NULL | 该系统里的名称/编号（如 SVID `1001`、`CH1_TEMP`） |
| `alias_type` | VARCHAR(16) | | `CODE`/`LABEL`/`TAG` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(source_system, alias_name, tenant_id, deleted)`（同一系统内一个别名只映射一个参数）。

### 3.3 `mds_param_enum`（枚举型参数取值）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `param_def_id` | VARCHAR(32) | NOT NULL, FK→`mds_param_def.id` | 参数（`data_type=ENUM`） |
| `item_code` | VARCHAR(32) | NOT NULL | 取值代码 |
| `item_name` | VARCHAR(128) | | 名称 |
| `seq` | INT | | 展示序 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(param_def_id, item_code, tenant_id, deleted)`。

### 3.4 `mds_param_group` / `mds_param_group_member`（参数分组）

**`mds_param_group`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 组代码（如 `GRP_FURNACE_TEMP`） |
| `name` | VARCHAR(128) | | 名称 |
| `group_type` | VARCHAR(16) | | `EQUIPMENT_SENSOR_SET`/`PROCESS_RECIPE_SET`/`METROLOGY_SET`/`SPC_SET` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

**`mds_param_group_member`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `group_id` | VARCHAR(32) | NOT NULL, FK→`mds_param_group.id` | 组 |
| `param_def_id` | VARCHAR(32) | NOT NULL, FK→`mds_param_def.id` | 参数 |
| `seq` | INT | | 顺序 |
| `is_required` | BIT | DEFAULT 1 | 是否必采 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(group_id, param_def_id, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 PD1–PD6）

### 4.1 字典独立、各域外键引用（PD1）
不重构各域表，只在各域**新增 `param_def_id`** 指向本域。存量 `param_name` 保留（兼容与展示），但**新数据必须填 `param_def_id`**；迁移期允许 `param_def_id` 为空并由数据治理任务回填（[md-governance-design](md-governance-design.md) 质量规则）。

### 4.2 参数语义 vs 工艺窗口分离（PD2）
| 本域（是什么） | [process-flow-design](process-flow-design.md)（要求多少） |
| --- | --- |
| `param_def.code='TEMP'`, `dimension=TEMPERATURE`, `base_uom='CEL'` | `step_param(target=200, min=195, max=205, unit='CEL')` |
| 全厂唯一、跨步骤共享 | 每个 step 各自不同 |

> 这样「改参数单位」只动字典，「调工艺窗口」只动流程——**两个维度解耦**。

### 4.3 别名承载外部系统映射（PD3）
- 设备型号 A 的 SVID `1001` 与型号 B 的 `CH_TEMP` 都映射到 `param_def.code='TEMP'`；
- **EAP 采集时按别名反查**：拿到 SVID 后查 `(source_system='MODEL_A', alias_name='1001')` → `TEMP` → 统一上报；
- 这是**跨设备参数统一的关键一环**（无此表，各设备数据永远无法聚合）。

### 4.4 关键参数标记（PD4）
- `is_key_param=1` 用于：① [process-flow-design §4.7.1](process-flow-design.md) 的版本 diff 中作为 `KEY_PARAM_DELTA`（CRITICAL）；② SPC 强制采样；③ 变更影响升级（[change-mgmt-design](change-mgmt-design.md)）。
- 与 `step_param.spec_type=KEY` 呼应但**不同层次**：前者是**参数本身的属性**（全厂），后者是**该步骤里该参数的重要性**（局部）。

### 4.5 参数分组与成套采集（PD5）
- `param_group` 表达"必须一起看的参数集合"（如炉管 5 区温度）；
- 供 EAP 的 **Data Collection Plan** 与 SPC 的**多变量分析**使用；
- 与 [equipment-interface-design](equipment-interface-design.md) 的 `report`（RPTID）区别：RPTID 是**通信打包**，param_group 是**分析语义**。

### 4.6 生命周期与版本（PD6）
- `DRAFT → ACTIVE → DEPRECATED`；
- **改语义（单位/量纲/类型）必须升 version**，且须评估影响（历史数据可比性！）——这是**必须在变更单里评审**的一类高风险变更；
- 停用（`DEPRECATED`）的参数**不删**，历史数据仍可解读。

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 参数实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 参数定义变更落 `{entity}_hist`（**语义变更影响历史数据解读，必须留痕**） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + **核心参数种子**（温度/压力/功率/时间/流量/膜厚/线宽…） |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_param_def (
  id                 VARCHAR(32) NOT NULL,
  tenant_id          VARCHAR(32) NOT NULL,
  code               VARCHAR(32) NOT NULL,
  name               VARCHAR(128),
  param_category     VARCHAR(16) NOT NULL,
  data_type          VARCHAR(16) NOT NULL,
  dimension          VARCHAR(16),
  base_uom           VARCHAR(16),
  precision          INT,
  measurement_method VARCHAR(64),
  normal_range_low   DECIMAL(18,6),
  normal_range_high  DECIMAL(18,6),
  is_key_param       BIT DEFAULT 0,
  semantic_note      VARCHAR(512),
  status             VARCHAR(16) NOT NULL,
  revision            VARCHAR(16) NOT NULL,
  description        VARCHAR(512),
  version_           BIGINT NOT NULL DEFAULT 0,
  deleted            BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_param_def PRIMARY KEY (id),
  CONSTRAINT uq_mds_param_def UNIQUE (code, tenant_id, deleted)
  -- base_uom: 跨域逻辑引用（服务层校验），不建 DB 级 FK —— 见 00-blueprint §11
);
CREATE INDEX idx_paramdef_cat ON mds_param_def (param_category, is_key_param);

CREATE TABLE mds_param_alias (
  id            VARCHAR(32) NOT NULL,
  tenant_id     VARCHAR(32) NOT NULL,
  param_def_id  VARCHAR(32) NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  alias_name    VARCHAR(64) NOT NULL,
  alias_type    VARCHAR(16),
  description   VARCHAR(512),
  version_      BIGINT NOT NULL DEFAULT 0,
  deleted       BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_param_alias PRIMARY KEY (id),
  CONSTRAINT uq_mds_param_alias UNIQUE (source_system, alias_name, tenant_id, deleted),
  CONSTRAINT fk_paramalias_def FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id)
);
CREATE INDEX idx_paramalias_param ON mds_param_alias (param_def_id);

CREATE TABLE mds_param_enum (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  param_def_id VARCHAR(32) NOT NULL,
  item_code    VARCHAR(32) NOT NULL,
  item_name    VARCHAR(128),
  seq          INT,
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_param_enum PRIMARY KEY (id),
  CONSTRAINT uq_mds_param_enum UNIQUE (param_def_id, item_code, tenant_id, deleted),
  CONSTRAINT fk_paramenum_def FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id)
);

CREATE TABLE mds_param_group (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  code        VARCHAR(64) NOT NULL,
  name        VARCHAR(128),
  group_type  VARCHAR(16),
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_param_group PRIMARY KEY (id),
  CONSTRAINT uq_mds_param_group UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_param_group_member (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  group_id     VARCHAR(32) NOT NULL,
  param_def_id VARCHAR(32) NOT NULL,
  seq          INT,
  is_required  BIT DEFAULT 1,
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_param_gm PRIMARY KEY (id),
  CONSTRAINT uq_mds_param_gm UNIQUE (group_id, param_def_id, tenant_id, deleted),
  CONSTRAINT fk_paramgm_group FOREIGN KEY (group_id) REFERENCES mds_param_group (id),
  CONSTRAINT fk_paramgm_def FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id)
);

-- 各域新增外键（迁移成对执行）
-- ALTER TABLE mds_process_flow_step_param ADD COLUMN param_def_id VARCHAR(32) NULL;
-- ALTER TABLE mds_equip_if_variable        ADD COLUMN param_def_id VARCHAR(32) NULL;
-- ALTER TABLE mds_sampling_plan            ADD COLUMN param_def_id VARCHAR(32) NULL;
-- ALTER TABLE mds_spc_rule_term            ADD COLUMN param_def_id VARCHAR(32) NULL;
-- ALTER TABLE mds_calibration_spec         ADD COLUMN param_def_id VARCHAR(32) NULL;
```

> 历史表 `mds_param_def_hist` / `mds_param_alias_hist` / … 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.paramdef
  ├── entity
  │   ├── ParamDef.java              # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── ParamAlias.java             # @Entity 继承 BaseDefData
  │   ├── ParamEnum.java              # @Entity 继承 BaseDefData
  │   ├── ParamGroup.java             # @Entity 继承 BaseDefData
  │   ├── ParamGroupMember.java       # @Entity 继承 BaseDefData
  │   └── package-info.java           # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── ParamDefRepository.java
  │   └── ParamAliasRepository.java
  ├── service
  │   ├── ParamDefService.java          # 继承 AbstractJpaService
  │   ├── ParamAliasResolver.java        # (source_system, alias) → param_def（供 EAP 采集归一）
  │   └── ParamValidator.java             # 单位/量纲一致、枚举型必有取值、别名唯一
  └── web
      └── ParamController.java           # 参数查询/别名映射/分组
```

---

## 8. 与 MES 生产执行衔接

| 环节 | MDS 提供 | MES 使用 |
| --- | --- | --- |
| **采集归一** | `mds_param_alias` 映射表 | EAP 拿到设备 SVID/DVID 后**反查别名 → 统一 param code**，再上报（否则跨设备数据无法聚合） |
| **投料/工艺准备** | `param_def` 的类型/单位/量纲 | MES 校验工艺窗口（`step_param`）的单位与参数字典一致，避免"℃ vs ℉"错配 |
| **过程监控** | 参数定义 + 关键参数标记 | MES/SPC 按 `param_def` 聚合、按 `is_key_param` 决定是否强制采样 |
| **SPC 与良率** | 统一 `param code` | 按参数维度做跨设备/跨时间的能力分析（Cpk）与良率相关性分析 |
| **变更门禁** | 语义/单位变更需升版本 + 评审 | 收到参数定义变更通知后，MES 侧历史数据分段标记（**避免新旧单位混算**） |
| **追溯** | 参数定义版本 | 事后可回答"当时用的参数定义是哪一版" |

> **对 MES 的关键价值**：参数是 MES 数据模型的**公共维度**。没有统一 `param_def`，MES 的 SPC、良率、追溯都会退化为"按字符串名分组"，迟早出现同名不同义/同义不同名。

---

## 9. 待补 / 后续

- **存量数据回填**：把各域现有 `param_name` / `variable code` 批量映射到 `param_def`（脚本 + 人工确认），并用 [md-governance-design](md-governance-design.md) 的质量规则持续校验"未填 `param_def_id`"的记录。
- **参数与量测 recipe 的对应**：量测设备用哪份 recipe 测哪个参数（可与 [recipe-design](recipe-design.md) 联动）。
- **精度与舍入规则**：`precision` 的统一舍入策略（银行家舍入 vs 四舍五入）需在使用方约定。
- **与既有文档闭环**：本文引用 [process-flow-design §3.4](process-flow-design.md)、[equipment-interface-design §3.2](equipment-interface-design.md)、[sampling-spc-design](sampling-spc-design.md)、[pm-calibration-design §3.4](pm-calibration-design.md)、[uom-dict-design §3.1](uom-dict-design.md)、[expression-dsl-design §5](expression-dsl-design.md)、[apc-fdc-design](apc-fdc-design.md)。
