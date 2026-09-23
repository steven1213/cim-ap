# MDS 测试资产（Test Asset：探针卡 / 负载板 / 测试程序）主数据设计

> 本文承接[光罩设计](reticle-design.md)（同范式参照）、[设备建模](equipment-design.md)（tester/prober）、[配方设计](recipe-design.md)（程序版本范式）、[参数定义](param-def-design.md)（测试项参数）、[原因码与处置](reason-defect-design.md)（Bin 与处置），
> 补全 MDS 的**测试侧资产主数据**——晶圆测试（CP/Wafer Sort）与成品测试（FT/Final Test）的物理与程序资产。
>
> **它是什么**：光刻有「光罩」这个稀缺可复用资产；**测试也有**——**探针卡（Probe Card）** 与 **负载板（Load Board）**，外加 **测试程序（Test Program）**。三者与光罩/配方**完全同构**（有寿命、需资格、需版本）。
>
> 设计原则延续全篇：**MDS 拥有测试资产的定义、资格与寿命策略；使用计数与实时位置归 MES/EAP**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么必须建（核心，含缺口实证）

| 已有实体 | 为什么不能替代 |
| --- | --- |
| 设备（tester/prober） | 探针卡**装在** prober 上、负载板**插在** tester 上，不是设备本身 |
| 配方 | 配方是"怎么跑"，**测试程序是"测什么、判什么"**，且带测试项与限值 |
| 载具 | 探针卡需专用存放盒与洁净存放，但**探针卡本身不是载具** |
| 光罩 | 结构同构但**对象不同**（光罩曝光图形，探针卡接触 pad） |

**缺口实证**：在补齐前，全库检索 `探针卡|probe card|Load Board|测试程序` **仅 1 处命中**（[reason-defect-design §3.3](reason-defect-design.md) 的 Bin 号注释里提到"测试程序里的数字码"）——即**测试资产完全没有建模**。

> **结论（TA1）**：测试资产是 MDS 的**独立主数据域**，与光罩并列（同属「有寿命 + 需资格的可复用资产」）。若产线含 CP/FT 段，**不建此域则测试段无法派工**。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 测试程序元数据与测试项 | **MDS** | `mds_test_program` / `_item`（正文在外围测试开发系统，`body_ref`） |
| 探针卡 / 负载板注册 | **MDS** | `mds_probe_card` / `mds_load_board` |
| 接口资产 ↔ tester/prober **资格** | **MDS** | `mds_test_interface_qual`（同 recipe/reticle 资格范式） |
| 寿命策略（触点数 / 插拔次数） | **MDS** | `mds_test_asset_usage_policy` |
| 行政态（`ACTIVE`/`HOLD`/`REPAIR`） | **MDS** | 资产级 |
| **累计触点数 / 插拔次数** | **EAP/MES** | 实时计数 |
| **探针卡实时位置**（在库/在机） | **AMHS/MES** | MDS 不存 |
| **实际测试结果与 Bin 分布** | **MES/YMS** | MDS 不存 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 测试程序 ↔ 产品 | `mds_test_program.product_code → mds_product.code` | [product-design §3.2](product-design.md) |
| 测试程序项 ↔ 参数 | `mds_test_program_item.param_def_id → mds_param_def.id` | [param-def-design §3.1](param-def-design.md) |
| 测试程序项 ↔ Bin | `mds_test_program_item.fail_bin_ref → mds_bin_code.code` | [reason-defect-design §3.3](reason-defect-design.md) |
| 探针卡 ↔ tester/prober 资格 | `mds_test_interface_qual.equipment_id / equipment_model_id` | [equipment-design §3.2/§3.3](equipment-design.md) |
| 负载板 ↔ tester | 同上 | 同上 |
| 探针卡存放盒 ↔ 载具类型 | 复用 `mds_carrier_type.carrier_type=PROBE_CARD_BOX`（**枚举扩展**） | [carrier-design §3.1](carrier-design.md) |
| 探针卡库 ↔ 仓库 | 复用 `mds_bank.bank_type=PROBE_CARD_STOCKER`（**枚举扩展**） | [bank-design §3.1](bank-design.md) |
| 测试资产 ↔ 供应商 | `vendor_id → mds_vendor.id` | [org-personnel-design §3.2](org-personnel-design.md) |
| 测试资产寿命 ↔ 约束 | `TEST_ASSET_USAGE_LIMIT` / `TEST_ASSET_QUAL`（新增约束类型） | [constraint-design §4.2](constraint-design.md) |
| 编码规则 | `entity_type=TEST_PROGRAM/PROBE_CARD/LOAD_BOARD`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E30 / E87** | 载具/资产管理模型适用于探针卡盒（专用容器） | 复用 `mds_carrier_type`（`PROBE_CARD_BOX`）与既有 E87 状态机 |
| **SEMI E40 / E94** | 与 Process Program 同构：测试程序需按 tester 型号适配与版本管理 | `mds_test_program.program_rev` + `body_ref`；`mds_test_interface_qual` |
| **SEMI E142 / E10（测试段）** | 测试设备与接口板的匹配性影响良率与停机 | 资格表 + 寿命策略 |
| **CP/Wafer Sort 实践** | 探针卡有**触点数（Touchdown）寿命**，超限需**清洗/研磨/换针/报废**；针痕深度影响 pad 损伤 | `mds_test_asset_usage_policy`（`max_touchdowns` + `clean_interval`） |
| **FT / Final Test 实践** | 负载板（含 Socket）有**插拔次数**寿命；不同 tester 型号需**不同板型** | `mds_load_board` + 资格表 |
| **测试程序与测试项** | 程序含测试项（Test Item）、限值（Spec Low/High）、失败 Bin 映射；变更需版本控制 | `mds_test_program_item` + 版本范式 |
| **IATF 16949** | 测试覆盖与程序变更需受控 | 与 [change-mgmt-design](change-mgmt-design.md) 联动 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Test Program / 测试程序** | 在 tester 上运行的程序（含测试项与限值） | **不是**工艺配方；前者"测什么/判什么"，后者"怎么做" |
| **Test Item / 测试项** | 程序中的一项测量与判定（如 VT、IDDQ、Continuity） | 与 [param-def-design](param-def-design.md) 的 `param_def` 关联 |
| **Probe Card / 探针卡** | CP 时接触 wafer pad 的接口卡 | **不是**光罩（无图形）；不是载具 |
| **Load Board / 负载板** | FT 时承载 DUT 与 Socket 的接口板 | 与 Socket（插座）区分：Socket 是板上的可换件 |
| **Touchdown / 触点次数** | 探针卡压一次（一片或一组 DUT）的计数 | 与"测试片数"不同：一次 touchdown 可测多颗 |
| **Insertion / 插拔次数** | 负载板/Socket 的插拔计数 | 与 touchdown 不同概念 |
| **接口资格（Interface Qual）** | 「接口资产 × tester/prober」的合格关系 | 同 recipe/reticle qual 范式 |
| **admin_status / 行政态** | MDS 侧资产态：`ACTIVE`/`HOLD`/`REPAIR`/`RETIRED` | 与实时位置（在机/在库）正交 |

---

## 3. 实体总览与 ER

```
mds_test_program (测试程序: test_stage CP/FT/SLT, product_code, program_rev, body_ref)
   └──< mds_test_program_item (测试项: param_def + spec_low/high + fail_bin)

mds_probe_card (探针卡: card_type / pin_count / pitch / dut_count / wafer_size)
mds_load_board (负载板: board_type / socket_type / socket_count / tester_model)
        │
        └──< mds_test_interface_qual  (接口资产 ↔ tester/prober 资格)
                 equipment_id / equipment_model_id / qual_status / alignment_ref

mds_test_asset_usage_policy (寿命: max_touchdowns / max_insertions / clean_interval)

复用: mds_carrier_type(PROBE_CARD_BOX) / mds_bank(PROBE_CARD_STOCKER)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 测试程序 | `mds_test_program` | 程序头（含版本与正文引用） |
| 测试项 | `mds_test_program_item` | 逐项判定与 Bin 映射 |
| 探针卡 | `mds_probe_card` | CP 接口资产 |
| 负载板 | `mds_load_board` | FT 接口资产 |
| 接口资格 | `mds_test_interface_qual` | 资产 × 设备资格 |
| 寿命策略 | `mds_test_asset_usage_policy` | 触点数/插拔次数上限 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_test_program`（测试程序）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 程序代码 |
| `name` | VARCHAR(128) | | 名称 |
| `test_stage` | VARCHAR(16) | NOT NULL | `CP`（晶圆测试）/`FT`（成品测试）/`SLT`（系统级测试）/`BURN_IN` |
| `product_code` | VARCHAR(64) | FK→`mds_product.code` | 适用产品 |
| `test_program_rev` | VARCHAR(16) | NOT NULL | 程序版本（对应 tester 加载的程序标识） |
| `body_ref` | VARCHAR(512) | | **程序正文引用**（外围测试开发系统/DMS，MDS 不存正文） |
| `tester_model_ref` | VARCHAR(32) | FK→`mds_equipment_model.id` | 适配 tester 型号（可空=通用） |
| `temp_condition` | VARCHAR(32) | | 测试温度条件（`HOT`/`COLD`/`ROOM`） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`IN_REVIEW`/`APPROVED`/`RELEASED`/`OBSOLETE`/`ARCHIVED` |
| `revision` | VARCHAR(16) | NOT NULL | 记录版本 |
| `is_frozen` | BIT | DEFAULT 0 | 冻结（与 status 正交，同全篇范式） |
| `effective_from`/`effective_to` | DATETIME(3) | | 生效窗口 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, revision, tenant_id, deleted)`。

### 3.2 `mds_test_program_item`（测试项）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `test_program_id` | VARCHAR(32) | NOT NULL, FK→`mds_test_program.id` | 所属程序 |
| `seq` | INT | NOT NULL | 测试顺序 |
| `item_name` | VARCHAR(64) | NOT NULL | 测试项名（如 `VT_N`、`IDDQ`、`CONT`） |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | 关联参数定义（统一语义） |
| `test_condition` | VARCHAR(128) | | 测试条件（电压/频率/温度） |
| `spec_low`/`spec_high` | DECIMAL(18,6) | | 判定上下限 |
| `unit` | VARCHAR(16) | FK→`mds_uom.code` | 单位 |
| `is_critical` | BIT | DEFAULT 0 | 是否关键测试项 |
| `fail_bin_ref` | VARCHAR(32) | FK→`mds_bin_code.code` | 失败映射的 Bin |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(test_program_id, seq, tenant_id, deleted)`。

### 3.3 `mds_probe_card`（探针卡）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 探针卡编码 |
| `name` | VARCHAR(128) | | 名称 |
| `card_type` | VARCHAR(16) | NOT NULL | `CANTILEVER`（悬臂）/`VERTICAL`（垂直）/`MEMS`/`COBRA` |
| `pin_count` | INT | | 针数 |
| `pitch_um` | DECIMAL(8,2) | | 针间距（μm） |
| `dut_count` | INT | | 同测颗数（site count） |
| `wafer_size` | VARCHAR(16) | | 适用尺寸（`200`/`300`） |
| `pad_structure` | VARCHAR(32) | | pad 结构（`AL`/`CU`/`SOLDER`） |
| `prober_model_ref` | VARCHAR(32) | FK→`mds_equipment_model.id` | 适配 prober 型号 |
| `vendor_id` | VARCHAR(32) | FK→`mds_vendor.id` | 制造商 |
| `box_type_code` | VARCHAR(64) | FK→`mds_carrier_type.code` | 存放盒类型（`carrier_type=PROBE_CARD_BOX`） |
| `home_stocker_code` | VARCHAR(64) | FK→`mds_bank.code` | 归属库（`bank_type=PROBE_CARD_STOCKER`） |
| `usage_policy_id` | VARCHAR(32) | FK→`mds_test_asset_usage_policy.id` | 寿命策略 |
| `admin_status` | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE' | `ACTIVE`/`HOLD`/`REPAIR`/`RETIRED` |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED`/`ARCHIVED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.4 `mds_load_board`（负载板）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 负载板编码 |
| `name` | VARCHAR(128) | | 名称 |
| `board_type` | VARCHAR(24) | NOT NULL | `DIB`（Device Interface Board）/`LOAD_BOARD`/`PROBE_BOARD`/`BURN_IN_BOARD` |
| `board_rev` | VARCHAR(16) | | 板版本 |
| `socket_type` | VARCHAR(32) | | Socket 类型（`POGO`/`SPRING`/`ELASTOMER`） |
| `socket_count` | INT | | 插座数 |
| `tester_model_ref` | VARCHAR(32) | FK→`mds_equipment_model.id` | 适配 tester 型号 |
| `vendor_id` | VARCHAR(32) | FK→`mds_vendor.id` | 制造商 |
| `usage_policy_id` | VARCHAR(32) | FK→`mds_test_asset_usage_policy.id` | 寿命策略 |
| `admin_status` | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE' | 同探针卡 |
| `status` | VARCHAR(16) | NOT NULL | 同上 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.5 `mds_test_interface_qual`（接口资产 ↔ 设备资格）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `asset_type` | VARCHAR(16) | NOT NULL | `PROBE_CARD`/`LOAD_BOARD` |
| `asset_id` | VARCHAR(32) | NOT NULL | 资产 id（按 `asset_type` 指向对应表） |
| `equipment_id` | VARCHAR(32) | FK→`mds_equipment.id` | 具体 tester/prober（与 model 二选一） |
| `equipment_model_id` | VARCHAR(32) | FK→`mds_equipment_model.id` | 型号级资格 |
| `alignment_ref` | VARCHAR(64) | | 对准/校准程序标识 |
| `qual_status` | VARCHAR(16) | NOT NULL DEFAULT 'QUALIFIED' | `QUALIFIED`/`PENDING`/`REVOKED` |
| `qualified_by`/`qualified_at` | — | | 资格留痕 |
| `requal_interval_days` | INT | | 再验证周期 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(asset_type, asset_id, equipment_id, equipment_model_id, tenant_id, deleted)`。
- **同范式**：与 [recipe-design §3.4](recipe-design.md) `mds_equipment_recipe_qual`、[reticle-design §3.3](reticle-design.md) `mds_reticle_qual` 结构一致——「资产 × 设备 = 资格」是第三次复用，命名统一。

### 3.6 `mds_test_asset_usage_policy`（寿命策略）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 策略代码 |
| `asset_category` | VARCHAR(16) | NOT NULL | `PROBE_CARD`/`LOAD_BOARD` |
| `max_touchdowns` | BIGINT | | 最大触点数（探针卡） |
| `max_insertions` | BIGINT | | 最大插拔次数（负载板/Socket） |
| `max_age_days` | INT | | 最长使用期限 |
| `clean_interval_touchdowns` | BIGINT | | 每 N 次需清洗/研磨 |
| `on_exhaust_action` | VARCHAR(16) | NOT NULL | `CLEAN`/`REPAIR`/`RETIRE`/`HOLD` |
| `warning_ratio` | DECIMAL(4,2) | | 预警比例 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 TA1–TA6）

### 4.1 独立资产域、与光罩同构（TA1）
见 §0.1。**资格 + 寿命 + 行政态 + 正文引用**四件套与 [reticle-design](reticle-design.md)/[recipe-design](recipe-design.md) 完全一致，学习成本为零。**pod/stocker 复用**载具与仓库（枚举扩展），不建重复表。

### 4.2 测试程序与工艺配方分离（TA2）
| | 工艺配方（[recipe-design](recipe-design.md)） | 测试程序（本文） |
| --- | --- | --- |
| 语义 | 怎么做（工艺动作） | 测什么、判什么（判定条件） |
| 结构 | 步骤 + 工艺参数 | 测试项 + 限值 + Bin 映射 |
| 版本 | `program`/PPID | `test_program_rev` |
| 关联 | 设备↔配方资格 | 资产↔设备资格 + 程序↔产品 |

> 二者不可合并：一个是"制造条件"，一个是"验收条件"。

### 4.3 探针卡与负载板：同资格、分实体（TA3）
- 字段差异大（针数/pitch/dut_count vs socket/board_rev），故**分表**；
- 但**资格与寿命统一**（`mds_test_interface_qual.asset_type` + 统一策略表），避免两套机制。

### 4.4 资格（TA4，核心）
- **为什么必须**：同一张探针卡换到另一台 prober 上，**针痕位置与平面度不保证**；负载板换 tester 型号需不同板型 → 必须资格匹配；
- 支持 **实例级**（指定某台 tester/prober）与 **型号级**（同型号全部）；
- 派工时 MES 校验 `qual_status=QUALIFIED`，无资格 = BLOCK（`TEST_ASSET_QUAL` 约束）。

### 4.5 寿命与到期动作（TA5）
- 探针卡：`max_touchdowns` + `clean_interval_touchdowns`（超限先清洗/研磨，再超则换针/报废）；
- 负载板：`max_insertions`；
- 到期动作：`CLEAN` / `REPAIR`（`admin_status=REPAIR`）/ `RETIRE` / `HOLD`；
- **MDS 给策略，EAP 计数，MES 执行**（与 [material-design §4.4](material-design.md) / [reticle-design §4.5](reticle-design.md) 同一分工）。

### 4.6 测试项与参数/判定的闭环（TA6）
- 测试项 `param_def_id` → 统一参数语义（[param-def-design](param-def-design.md)）：使测试数据可跨产品/跨 tester **聚合分析**；
- `fail_bin_ref` → [reason-defect-design](reason-defect-design.md) 的 Bin，形成「测试失败 → Bin → 处置」链路；
- `spec_low/high` 可被 [sampling-spc-design](sampling-spc-design.md) 引用为 Spec 限（**Control 限仍由 SPC 计算**）。

### 4.7 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 测试资产实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 程序/资格/寿命策略变更落 `{entity}_hist`（资格吊销与程序改版必须留痕） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + 盒/库枚举扩展种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_test_program (
  id               VARCHAR(32) NOT NULL,
  tenant_id        VARCHAR(32) NOT NULL,
  code             VARCHAR(64) NOT NULL,
  name             VARCHAR(128),
  test_stage       VARCHAR(16) NOT NULL,
  product_code     VARCHAR(64),
  test_program_rev VARCHAR(16) NOT NULL,
  body_ref         VARCHAR(512),
  tester_model_ref VARCHAR(32),
  temp_condition   VARCHAR(32),
  status           VARCHAR(16) NOT NULL,
  revision          VARCHAR(16) NOT NULL,
  is_frozen        BIT DEFAULT 0,
  effective_from   DATETIME(3),
  effective_to     DATETIME(3),
  description      VARCHAR(512),
  version_         BIGINT NOT NULL DEFAULT 0,
  deleted          BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_tp PRIMARY KEY (id),
  CONSTRAINT uq_mds_tp UNIQUE (code, revision, tenant_id, deleted),
  CONSTRAINT fk_tp_model FOREIGN KEY (tester_model_ref) REFERENCES mds_equipment_model (id)
);

CREATE TABLE mds_test_program_item (
  id               VARCHAR(32) NOT NULL,
  tenant_id        VARCHAR(32) NOT NULL,
  test_program_id  VARCHAR(32) NOT NULL,
  seq              INT NOT NULL,
  item_name        VARCHAR(64) NOT NULL,
  param_def_id     VARCHAR(32),
  test_condition   VARCHAR(128),
  spec_low         DECIMAL(18,6),
  spec_high        DECIMAL(18,6),
  unit             VARCHAR(16),
  is_critical      BIT DEFAULT 0,
  fail_bin_ref     VARCHAR(32),
  description      VARCHAR(512),
  version_         BIGINT NOT NULL DEFAULT 0,
  deleted          BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_tpi PRIMARY KEY (id),
  CONSTRAINT uq_mds_tpi UNIQUE (test_program_id, seq, tenant_id, deleted),
  CONSTRAINT fk_tpi_tp FOREIGN KEY (test_program_id) REFERENCES mds_test_program (id),
  CONSTRAINT fk_tpi_param FOREIGN KEY (param_def_id) REFERENCES mds_param_def (id)
);

CREATE TABLE mds_test_asset_usage_policy (
  id                       VARCHAR(32) NOT NULL,
  tenant_id                VARCHAR(32) NOT NULL,
  code                     VARCHAR(64) NOT NULL,
  asset_category           VARCHAR(16) NOT NULL,
  max_touchdowns           BIGINT,
  max_insertions           BIGINT,
  max_age_days             INT,
  clean_interval_touchdowns BIGINT,
  on_exhaust_action        VARCHAR(16) NOT NULL,
  warning_ratio            DECIMAL(4,2),
  description              VARCHAR(512),
  version_                 BIGINT NOT NULL DEFAULT 0,
  deleted                  BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_taup PRIMARY KEY (id),
  CONSTRAINT uq_mds_taup UNIQUE (code, tenant_id, deleted)
);

CREATE TABLE mds_probe_card (
  id              VARCHAR(32) NOT NULL,
  tenant_id       VARCHAR(32) NOT NULL,
  code            VARCHAR(64) NOT NULL,
  name            VARCHAR(128),
  card_type       VARCHAR(16) NOT NULL,
  pin_count       INT,
  pitch_um        DECIMAL(8,2),
  dut_count       INT,
  wafer_size      VARCHAR(16),
  pad_structure   VARCHAR(32),
  prober_model_ref VARCHAR(32),
  vendor_id       VARCHAR(32),
  box_type_code   VARCHAR(64),
  home_stocker_code VARCHAR(64),
  usage_policy_id VARCHAR(32),
  admin_status    VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  status          VARCHAR(16) NOT NULL,
  description     VARCHAR(512),
  version_        BIGINT NOT NULL DEFAULT 0,
  deleted         BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pc PRIMARY KEY (id),
  CONSTRAINT uq_mds_pc UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_pc_policy FOREIGN KEY (usage_policy_id) REFERENCES mds_test_asset_usage_policy (id),
  CONSTRAINT fk_pc_model FOREIGN KEY (prober_model_ref) REFERENCES mds_equipment_model (id)
);
CREATE INDEX idx_pc_admin ON mds_probe_card (admin_status);

CREATE TABLE mds_load_board (
  id              VARCHAR(32) NOT NULL,
  tenant_id       VARCHAR(32) NOT NULL,
  code            VARCHAR(64) NOT NULL,
  name            VARCHAR(128),
  board_type      VARCHAR(24) NOT NULL,
  board_rev       VARCHAR(16),
  socket_type     VARCHAR(32),
  socket_count    INT,
  tester_model_ref VARCHAR(32),
  vendor_id       VARCHAR(32),
  usage_policy_id VARCHAR(32),
  admin_status    VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  status          VARCHAR(16) NOT NULL,
  description     VARCHAR(512),
  version_        BIGINT NOT NULL DEFAULT 0,
  deleted         BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_lb PRIMARY KEY (id),
  CONSTRAINT uq_mds_lb UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_lb_policy FOREIGN KEY (usage_policy_id) REFERENCES mds_test_asset_usage_policy (id),
  CONSTRAINT fk_lb_model FOREIGN KEY (tester_model_ref) REFERENCES mds_equipment_model (id)
);

CREATE TABLE mds_test_interface_qual (
  id                   VARCHAR(32) NOT NULL,
  tenant_id            VARCHAR(32) NOT NULL,
  asset_type           VARCHAR(16) NOT NULL,
  asset_id             VARCHAR(32) NOT NULL,
  equipment_id         VARCHAR(32),
  equipment_model_id   VARCHAR(32),
  alignment_ref        VARCHAR(64),
  qual_status          VARCHAR(16) NOT NULL DEFAULT 'QUALIFIED',
  qualified_by         VARCHAR(64),
  qualified_at         DATETIME(3),
  requal_interval_days INT,
  description          VARCHAR(512),
  version_             BIGINT NOT NULL DEFAULT 0,
  deleted              BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_tiq PRIMARY KEY (id),
  CONSTRAINT fk_tiq_eq FOREIGN KEY (equipment_id) REFERENCES mds_equipment (id),
  CONSTRAINT fk_tiq_model FOREIGN KEY (equipment_model_id) REFERENCES mds_equipment_model (id)
);
CREATE INDEX idx_tiq_asset ON mds_test_interface_qual (asset_type, asset_id);
CREATE INDEX idx_tiq_eq ON mds_test_interface_qual (equipment_id);
```

> 历史表由 `@History(SNAPSHOT)` 生成。
> **枚举扩展迁移**：`mds_carrier_type.carrier_type` 增 `PROBE_CARD_BOX`；`mds_bank.bank_type` 增 `PROBE_CARD_STOCKER`。

---

## 7. 代码结构（包路径）

```
com.cim.mds.testasset
  ├── entity
  │   ├── TestProgram.java             # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── TestProgramItem.java          # @Entity 继承 BaseDefData
  │   ├── ProbeCard.java                # @Entity 继承 BaseDefData
  │   ├── LoadBoard.java                # @Entity 继承 BaseDefData
  │   ├── TestInterfaceQual.java        # @Entity 继承 BaseDefData
  │   ├── TestAssetUsagePolicy.java     # @Entity 继承 BaseDefData
  │   └── package-info.java             # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── TestProgramRepository.java
  │   ├── ProbeCardRepository.java
  │   └── TestInterfaceQualRepository.java
  ├── service
  │   ├── TestProgramService.java        # 继承 AbstractJpaService；cloneAsNewVersion
  │   ├── TestAssetService.java           # 探针卡/负载板注册与行政态迁移
  │   └── TestAssetResolver.java           # 派工匹配：按 tester + 产品 + 尺寸选出合格可用资产
  └── web
      └── TestAssetController.java        # 程序发布/资产登记/资格矩阵/寿命台账
```

---

## 8. 与 MES 生产执行衔接

| 场景 | MDS 提供 | MES/EAP 使用 |
| --- | --- | --- |
| **CP 派工** | 探针卡（针数/pitch/dut_count/wafer_size 匹配）、**接口资格**、寿命策略、存放库 | MES 按「prober 型号 + 产品 + 尺寸」筛出合格探针卡 → 结合**实时在库/在机状态**（AMHS）与**剩余寿命**（EAP 计数）选定 → 校验 `TEST_ASSET_QUAL` 约束 |
| **FT 派工** | 负载板（板型/socket/tester 型号）、资格、寿命 | 同上 |
| **程序下发** | `mds_test_program` + `test_program_rev` + `body_ref` | EAP/MES 按产品 + tester 型号 + 温度条件解析程序 → 加载到 tester；校验程序状态为 `RELEASED` 且在生效窗口内 |
| **判定与 Bin** | `test_program_item`（spec_low/high + `fail_bin_ref`） | 测试系统判定 → 结果映射 Bin → 交 [reason-defect-design](reason-defect-design.md) 处置 |
| **寿命管理** | 策略阈值与到期动作 | EAP 累计 touchdown/insertion → 达 `warning_ratio` 预警 → 达上限触发清洗/送修/报废并回写 `admin_status`（[realtime-contract-design §2](realtime-contract-design.md) 白名单） |
| **数据分析** | 测试项关联 `param_def` | 跨产品/跨 tester 聚合测试数据（Cpk、Bin 分布、良率相关性） |
| **追溯** | 程序版本 + 资产编号 | 回答"这片 wafer 当时用的哪版程序、哪张探针卡" |

> **对 MES 的关键价值**：测试段的派工与判定同样由**主数据 + 规则数据**驱动，不需要在 MES 里硬编码"哪张卡能用"。

---

## 9. 待补 / 后续

- **程序正文托管**：`body_ref` 指向的测试开发系统（如 Advantest/Teradyne 工具链）的集成契约。
- **针痕/接触质量数据**：针痕深度、接触电阻等质量指标是否纳入寿命判定（可关联 [sampling-spc-design](sampling-spc-design.md)）。
- **Socket 独立建模**：Socket 作为负载板上的**可更换子件**，是否需要独立台账（当前作为负载板属性）。
- **与既有文档闭环**：本文引用 [reticle-design](reticle-design.md)（同范式）、[recipe-design](recipe-design.md)（资格范式）、[equipment-design](equipment-design.md)（tester/prober）、[carrier-design](carrier-design.md)（盒）、[bank-design](bank-design.md)（库）、[param-def-design](param-def-design.md)（测试项）、[reason-defect-design](reason-defect-design.md)（Bin）、[constraint-design](constraint-design.md)（新增 `TEST_ASSET_QUAL`/`TEST_ASSET_USAGE_LIMIT`）、[naming-rule-design](naming-rule-design.md)（新增 `entity_type`）。
