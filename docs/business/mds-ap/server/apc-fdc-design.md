# MDS 先进过程控制与故障检测模型（APC / FDC）主数据设计

> 本文承接[参数定义](param-def-design.md)（控制/输入变量）、[设备建模](equipment-design.md)（设备与模块）、[工艺路线](route-design.md)（工序上下文）、[采样与 SPC](sampling-spc-design.md)（判异与 OCAP），
> 补全 MDS 的**APC 控制模型（R2R / VM / 先进算法）与 FDC 故障检测模型** 主数据。
>
> **补的缺口**：全库检索 `APC|FDC` **零命中**。现代 fab 的先进控制（Run-to-Run 反馈、Virtual Metrology）与故障检测（多变量异常监测）已成标配，但其**模型定义与适用上下文**无处建模——导致"哪个产品/哪道工序该用哪个模型"只能写在 APC 系统里。
>
> 设计原则延续全篇：**MDS 拥有模型定义与适用上下文；算法执行与实时计算归 APC/FDC 系统**。

---

## 0. 定位与边界（拍板）

### 0.1 MDS 拥有 vs 下游拥有

| 内容 | 拥有者 | 说明 |
| --- | --- | --- |
| APC 模型定义（目标/控制变量、算法、版本） | **MDS** | `mds_apc_model` |
| APC 模型的**适用上下文**（哪些产品/工序/设备用） | **MDS** | `mds_apc_context`（核心） |
| FDC 模型定义（方法、输入变量、健康指标） | **MDS** | `mds_fdc_model` / `_input` |
| 模型正文/系数（模型文件） | **MDS 引用** | `body_ref`（正文在 APC 系统或模型库，同 `body_ref` 范式） |
| **模型的实际计算/预测/反馈** | **APC/FDC 系统** | 实时执行 |
| 实时预测值与控制量下发 | APC 系统 | MDS 不存 |
| 模型性能监控（模型漂移） | APC 系统 | MDS 不存 |

### 0.2 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 模型 ↔ 控制/目标参数 | `control_target_param_def_id` / `control_variable_param_def_id → mds_param_def.id` | [param-def-design §3.1](param-def-design.md) |
| 模型上下文 ↔ 产品 | `mds_apc_context(scope_type=PRODUCT, scope_ref)` | [product-design §3.2](product-design.md) |
| 模型上下文 ↔ 工序 | `(ROUTE_OPERATION, mds_route_operation.id)` | [route-design §3.3](route-design.md) |
| 模型上下文 ↔ 设备类/设备 | `(EQUIPMENT_CLASS / EQUIPMENT, …)` | [equipment-design §3.2/§3.3](equipment-design.md) |
| 模型上下文 ↔ 层 | `(LAYER, mds_layer.code)` | [layer-design §3.1](layer-design.md) |
| FDC 输入 ↔ 设备变量 | `mds_fdc_model_input.variable_ref → mds_equip_if_variable.code` | [equipment-interface-design §3.2](equipment-interface-design.md) |
| FDC 报警 ↔ 原因码 | 健康指标超限 → 原因码 | [reason-defect-design §3.1](reason-defect-design.md) |
| SPC 与 APC | SPC 监控、APC 调整（互补） | [sampling-spc-design §4.4](sampling-spc-design.md) |
| 编码规则 | `entity_type=APC_MODEL`/`FDC_MODEL`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E133（APC 框架）** | APC 的体系结构：模型、上下文、控制器分离 | `mds_apc_model` + `mds_apc_context` 分离 |
| **Run-to-Run（R2R）控制** | 用前批结果反馈调整下批配方参数（如刻蚀时间补偿） | `model_type=R2R` + `control_variable` |
| **Virtual Metrology（VM）** | 用设备传感器数据预测量测结果，减少量测 | `model_type=VM` + 输入变量 |
| **FDC（Fault Detection & Classification）** | 多变量（PCA/PLS/单变量）监测设备健康 | `mds_fdc_model` + `method` |
| **半导体智能制造** | 模型需**版本化 + 上下文绑定**，且变更受控 | 复用版本范式 + [change-mgmt-design](change-mgmt-design.md) |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **APC** | 先进过程控制（反馈/前馈调整工艺参数） | 不是 SPC：**SPC 监控、APC 干预**（主动 vs 被动） |
| **R2R / Run-to-Run** | 按批反馈控制 | 不是实时闭环（那是 RTPC） |
| **VM / Virtual Metrology** | 用传感器预测量测值 | 不是"虚拟量测设备" |
| **FDC** | 故障检测与分类（设备健康监测） | 与设备 E10 状态不同：FDC 是**预测性**，E10 是**现状** |
| **Context / 上下文** | 模型生效的条件（产品/工序/设备） | 不是"模型参数"；是**选用依据** |
| **health_metric** | FDC 的健康指标（如 T²、Q 统计量） | 不是"参数值" |
| **body_ref / 模型正文** | 模型文件/系数所在位置 | MDS 不存模型体（同 recipe/document 范式） |

---

## 3. 实体总览与 ER

```
mds_apc_model (控制模型: model_type / 目标与控制变量 / algorithm / body_ref / model_rev)
      └──< mds_apc_context (适用上下文: PRODUCT / ROUTE_OPERATION / EQUIPMENT_CLASS / EQUIPMENT / LAYER)

mds_fdc_model (故障检测模型: method / health_metric / thresholds / body_ref)
      └──< mds_fdc_model_input (输入变量: param_def / equipment variable / weight)
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| APC 模型 | `mds_apc_model` | 控制模型定义 |
| APC 上下文 | `mds_apc_context` | 模型适用条件（核心） |
| FDC 模型 | `mds_fdc_model` | 故障检测模型定义 |
| FDC 输入 | `mds_fdc_model_input` | 输入变量集 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_apc_model`（APC 控制模型）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 模型代码 |
| `name` | VARCHAR(128) | | 名称 |
| `model_type` | VARCHAR(16) | NOT NULL | `R2R`（批间反馈）/`FEEDFORWARD`（前馈）/`VM`（虚拟量测）/`NN`（神经/高级算法）/`OTHER` |
| `control_target_param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | **被控制的目标参数**（如刻蚀时间） |
| `feedback_param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | **反馈来源参数**（如测得的 CD） |
| `algorithm` | VARCHAR(64) | | 算法标识（`EWMA`/`MPC`/`PLS`…） |
| `body_ref` | VARCHAR(512) | | **模型正文引用**（模型文件/系数，MDS 不存） |
| `model_rev` | VARCHAR(16) | NOT NULL | 模型版本 |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`IN_REVIEW`/`APPROVED`/`RELEASED`/`OBSOLETE`/`ARCHIVED` |
| `is_frozen` | BIT | DEFAULT 0 | 冻结（与 status 正交） |
| `effective_from`/`effective_to` | DATETIME(3) | | 生效窗口 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, model_rev, tenant_id, deleted)`。

### 3.2 `mds_apc_context`（模型适用上下文）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `apc_model_id` | VARCHAR(32) | NOT NULL, FK→`mds_apc_model.id` | 模型 |
| `scope_type` | VARCHAR(24) | NOT NULL | `PRODUCT`/`PRODUCT_FAMILY`/`ROUTE`/`ROUTE_OPERATION`/`EQUIPMENT_CLASS`/`EQUIPMENT`/`LAYER`/`GLOBAL` |
| `scope_ref` | VARCHAR(128) | NOT NULL | 目标（id/code；`GLOBAL` 用 `*`） |
| `priority` | INT | DEFAULT 0 | 多模型命中时的优先级（越具体越优先，同全篇 specificity） |
| `is_enabled` | BIT | DEFAULT 1 | 是否启用 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(apc_model_id, scope_type, scope_ref, tenant_id, deleted)`。

### 3.3 `mds_fdc_model`（故障检测模型）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 模型代码 |
| `name` | VARCHAR(128) | | 名称 |
| `equipment_model_id` | VARCHAR(32) | FK→`mds_equipment_model.id` | 适用设备型号 |
| `method` | VARCHAR(24) | NOT NULL | `UNIVARIATE`/`PCA`/`PLS`/`SVM`/`AUTOENCODER`/`CUSTOM` |
| `health_metric` | VARCHAR(32) | | 健康指标（`T2`/`SPE`/`Q`/`COMBINED`） |
| `warn_threshold` | DECIMAL(18,6) | | 预警阈值 |
| `alarm_threshold` | DECIMAL(18,6) | | 报警阈值 |
| `reason_code_ref` | VARCHAR(32) | FK→`mds_reason_code.code` | 报警默认原因码 |
| `body_ref` | VARCHAR(512) | | 模型正文引用 |
| `model_rev` | VARCHAR(16) | NOT NULL | 模型版本 |
| `status` | VARCHAR(16) | NOT NULL | 同 APC |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, model_rev, tenant_id, deleted)`。

### 3.4 `mds_fdc_model_input`（FDC 输入变量）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `fdc_model_id` | VARCHAR(32) | NOT NULL, FK→`mds_fdc_model.id` | 模型 |
| `param_def_id` | VARCHAR(32) | FK→`mds_param_def.id` | 输入参数（统一语义） |
| `variable_ref` | VARCHAR(64) | | 设备变量（`mds_equip_if_variable.code`） |
| `is_required` | BIT | DEFAULT 1 | 是否必需输入 |
| `weight` | DECIMAL(10,6) | | 权重 |
| `seq` | INT | | 顺序 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(fdc_model_id, param_def_id, variable_ref, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 AF1–AF5）

### 4.1 模型与上下文分离（AF1）
- **模型**定义"怎么算"；**上下文**定义"哪些情况用"；
- 一个模型可服务多个产品/工序/设备；一个上下文组合也可命中多个模型 → 按 `priority` 与 specificity 选一；
- **这是本域的核心价值**：把"哪个产品哪道工序用哪个模型"从 APC 系统配置里搬出来，成为受管控、可审计、可对比的主数据。

### 4.2 APC 与 SPC 的分工（AF2）
| | SPC（[sampling-spc-design](sampling-spc-design.md)） | APC（本文） |
| --- | --- | --- |
| 方向 | **监控**（发现异常） | **干预**（自动调整） |
| 时机 | 事后判异 | 批间/前馈调整 |
| 输出 | OOC → OCAP 动作 | 新的控制量（下发设备） |
| 关系 | SPC 发现趋势 → APC 上线纠正；APC 失效 → SPC 兜底 | |

### 4.3 FDC 与设备状态/报警的关系（AF3）
- FDC 输出的是**健康指标**（预测性），设备 E10 是**现状**（[equipment-design §3.11](equipment-design.md)）；
- FDC 报警可映射到 [equipment-interface-design](equipment-interface-design.md) 的 ALID 或独立通道，并挂 `reason_code_ref` 自动归因；
- FDC 的输入变量来自设备点表（[equipment-interface-design §3.2](equipment-interface-design.md)），经 `param_def` 统一语义。

### 4.4 版本与变更（AF4）
- 复用统一版本范式（模型版本不可变 + `cloneAsNewVersion` + `is_frozen`）；
- 模型变更（换算法/改阈值/换输入）影响良率风险高 → 必须走 [change-mgmt-design](change-mgmt-design.md)；
- `body_ref` 指向的模型文件与 `model_rev` 应**同步**（模型文件也受版本控制）。

### 4.5 与良率/参数的闭环（AF5）
- APC 的目标/反馈参数、FDC 的输入参数均经 `param_def` 统一 → 可与 SPC、良率分析**同维度**关联；
- 支撑"模型有效性分析"（用了 APC 之后该参数 Cpk 是否提升）。

### 4.6 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | APC/FDC 实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 模型与上下文变更落 `{entity}_hist`（**模型变更影响良率，必须留痕**） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + FDC 方法种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_apc_model (
  id                         VARCHAR(32) NOT NULL,
  tenant_id                  VARCHAR(32) NOT NULL,
  code                       VARCHAR(64) NOT NULL,
  name                       VARCHAR(128),
  model_type                 VARCHAR(16) NOT NULL,
  control_target_param_def_id VARCHAR(32),
  feedback_param_def_id      VARCHAR(32),
  algorithm                  VARCHAR(64),
  body_ref                   VARCHAR(512),
  model_rev                  VARCHAR(16) NOT NULL,
  status                     VARCHAR(16) NOT NULL,
  is_frozen                  BIT DEFAULT 0,
  effective_from             DATETIME(3),
  effective_to               DATETIME(3),
  description                VARCHAR(512),
  version_                   BIGINT NOT NULL DEFAULT 0,
  deleted                    BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_apc PRIMARY KEY (id),
  CONSTRAINT uq_mds_apc UNIQUE (code, model_rev, tenant_id, deleted),
  CONSTRAINT fk_apc_target FOREIGN KEY (control_target_param_def_id) REFERENCES mds_param_def (id),
  CONSTRAINT fk_apc_feedback FOREIGN KEY (feedback_param_def_id) REFERENCES mds_param_def (id)
);

CREATE TABLE mds_apc_context (
  id           VARCHAR(32) NOT NULL,
  tenant_id    VARCHAR(32) NOT NULL,
  apc_model_id VARCHAR(32) NOT NULL,
  scope_type   VARCHAR(24) NOT NULL,
  scope_ref    VARCHAR(128) NOT NULL,
  priority     INT DEFAULT 0,
  is_enabled   BIT DEFAULT 1,
  description  VARCHAR(512),
  version_     BIGINT NOT NULL DEFAULT 0,
  deleted      BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_apc_ctx PRIMARY KEY (id),
  CONSTRAINT uq_mds_apc_ctx UNIQUE (apc_model_id, scope_type, scope_ref, tenant_id, deleted),
  CONSTRAINT fk_apcctx_model FOREIGN KEY (apc_model_id) REFERENCES mds_apc_model (id)
);
CREATE INDEX idx_apcctx_scope ON mds_apc_context (scope_type, scope_ref);

CREATE TABLE mds_fdc_model (
  id                 VARCHAR(32) NOT NULL,
  tenant_id          VARCHAR(32) NOT NULL,
  code               VARCHAR(64) NOT NULL,
  name               VARCHAR(128),
  equipment_model_id VARCHAR(32),
  method             VARCHAR(24) NOT NULL,
  health_metric      VARCHAR(32),
  warn_threshold     DECIMAL(18,6),
  alarm_threshold    DECIMAL(18,6),
  reason_code_ref    VARCHAR(32),
  body_ref           VARCHAR(512),
  model_rev          VARCHAR(16) NOT NULL,
  status             VARCHAR(16) NOT NULL,
  description        VARCHAR(512),
  version_           BIGINT NOT NULL DEFAULT 0,
  deleted            BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_fdc PRIMARY KEY (id),
  CONSTRAINT uq_mds_fdc UNIQUE (code, model_rev, tenant_id, deleted),
  CONSTRAINT fk_fdc_model FOREIGN KEY (equipment_model_id) REFERENCES mds_equipment_model (id)
);

CREATE TABLE mds_fdc_model_input (
  id             VARCHAR(32) NOT NULL,
  tenant_id      VARCHAR(32) NOT NULL,
  fdc_model_id   VARCHAR(32) NOT NULL,
  param_def_id   VARCHAR(32),
  variable_ref   VARCHAR(64),
  is_required    BIT DEFAULT 1,
  weight         DECIMAL(10,6),
  seq            INT,
  description    VARCHAR(512),
  version_       BIGINT NOT NULL DEFAULT 0,
  deleted        BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_fdc_in PRIMARY KEY (id),
  CONSTRAINT uq_mds_fdc_in UNIQUE (fdc_model_id, param_def_id, variable_ref, tenant_id, deleted),
  CONSTRAINT fk_fdcin_model FOREIGN KEY (fdc_model_id) REFERENCES mds_fdc_model (id)
);
```

> 历史表由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.apc
  ├── entity
  │   ├── ApcModel.java                # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── ApcContext.java               # @Entity 继承 BaseDefData
  │   ├── FdcModel.java                  # @Entity 继承 BaseDefData
  │   ├── FdcModelInput.java              # @Entity 继承 BaseDefData
  │   └── package-info.java              # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── ApcModelRepository.java
  │   └── FdcModelRepository.java
  ├── service
  │   ├── ApcModelService.java            # 继承 AbstractJpaService；cloneAsNewVersion
  │   ├── ApcContextResolver.java          # 按上下文解析生效模型（specificity + priority）
  │   └── ApcValidator.java                 # 阈值合理性、输入变量存在性、上下文完整性
  └── web
      └── ApcController.java               # 模型/上下文/FDC 查询与发布
```

---

## 8. 与 MES 生产执行衔接

| 场景 | MDS 提供 | MES / APC 系统使用 |
| --- | --- | --- |
| **选择模型** | `mds_apc_context`（产品/工序/设备/层） | APC/MES 在批次开始前按上下文解析生效模型（specificity + priority） |
| **R2R 控制** | 模型的控制目标参数、反馈参数、算法、`body_ref` | APC 系统加载模型正文 → 计算控制量 → 下发设备（MDS 不参与计算） |
| **VM 预测** | VM 模型与输入变量 | APC 系统用设备传感器数据预测量测值 → 影响 SPC 采样决策 |
| **FDC 监控** | FDC 模型、输入变量集、健康指标与阈值 | FDC 系统实时计算健康指标 → 超限报警 → 挂 `reason_code_ref` → 触发处置 |
| **模型变更** | 版本与 ECN | 收到变更后 APC 系统换版加载；**已有在制批按锁定版本继续** |
| **有效性分析** | 参数统一语义 + 版本 | 与 SPC/良率数据关联，评估模型效果 |

> **对 MES 的关键价值**：MES 不必知道"哪个产品用哪个模型"，只需按**上下文查询接口**问 MDS；模型换版不发版 MES。

---

## 9. 待补 / 后续

- **RTPC（实时过程控制）**：秒级闭环控制是否纳入（与 R2R 分属不同时间尺度）。
- **模型性能与漂移监控**：模型有效性指标（预测偏差）是否需在 MDS 留档（建议归 APC 系统）。
- **与设备点表的采集配置联动**：VM/FDC 需要的变量须在点表中存在（校验规则）。
- **与既有文档闭环**：本文引用 [param-def-design](param-def-design.md)、[equipment-design](equipment-design.md)、[equipment-interface-design](equipment-interface-design.md)、[route-design](route-design.md)、[layer-design](layer-design.md)、[sampling-spc-design §4.2](sampling-spc-design.md)（SPC vs APC 分工）、[reason-defect-design](reason-defect-design.md)、[change-mgmt-design](change-mgmt-design.md)、[naming-rule-design](naming-rule-design.md)。
