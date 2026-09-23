# MDS 层（Layer / Mask Layer）主数据设计

> 本文承接[产品主数据](product-design.md)（`mask_layer_count`）、[工艺路线](route-design.md)（operation）、[掩模版](reticle-design.md)（reticle），
> 补全 MDS 的**层（Layer）** 主数据——它不是「物理资产」，也不是「产出物」，而是连接**产品架构 / 工艺工序 / 光刻掩模**三者的**结构化坐标轴**。
>
> 设计原则延续全篇：**MDS 拥有层定义与映射关系；某片晶圆当前跑到哪一层属实时态，归 MES**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么层需要独立建模（核心）

「层」在晶圆厂同时是**三种东西**，缺一个统一实体就会三处各建一套：

| 视角 | 含义 | 谁在用 |
| --- | --- | --- |
| **产品架构视角** | 这颗芯片由哪些层堆叠而成（POLY / M1 / M2 / CONTACT / VIA…），共几层 | 产品定义、良率分析（按层切良率） |
| **工艺路线视角** | 哪个 operation 在加工哪一层（光刻 POLY、刻蚀 POLY） | 工艺路线、派工、追溯 |
| **光刻掩模视角** | 哪一层由哪套光罩曝光形成 | 光刻管理（[reticle-design](reticle-design.md)） |

> **结论（L1）**：`Layer` 是 MDS 的**独立主数据**，是产品、路线、光罩三者的**公共锚点**。不建 Layer，则：① reticle 无处挂载；② 按层追溯/按层良率无从下手；③ 产品`mask_layer_count` 无法与工艺对应。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 层定义（编码/名称/类型/堆叠序） | **MDS** | `mds_layer`，本文范围 |
| 层 ↔ 工序映射（哪道工序加工哪层、以什么角色） | **MDS** | `mds_layer_operation` |
| 层 ↔ 光罩映射 | **MDS** | `mds_reticle_layer`（定义在 [reticle-design](reticle-design.md)） |
| 层 ↔ 产品/技术节点归属 | **MDS** | `mds_layer.tech_node` + 逻辑引用 product |
| **某 lot 当前处于第几层 / 已完工层** | **MES** | 实时进度，MDS 不存 |
| **按层的实测良率/缺陷密度** | **MES / YMS** | 分析结果，MDS 不存 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 层 ↔ 工序 | `mds_layer_operation.route_operation_id → mds_route_operation.id` | [route-design §3.3](route-design.md) |
| 层 ↔ 工序 | `mds_layer_operation.layer_id → mds_layer.id` | 本文 §3.2 |
| 层 ↔ 光罩 | `mds_reticle_layer.layer_id → mds_layer.id` | [reticle-design §3.3](reticle-design.md) |
| 层 ↔ 技术节点 | `mds_layer.tech_node`（与 `mds_product.tech_node` 同域） | [product-design §4.4](product-design.md) |
| 层 ↔ 产品 | `mds_product.mask_layer_count` 表达"共几层"，逐层明细经本域 | [product-design §3.2](product-design.md) |
| 层 ↔ 约束 | 层可作为约束作用域/客体（如某层禁某载具） | [constraint-design §4.5](constraint-design.md) |
| 层编码规则 | `mds_naming_rule.entity_type=LAYER`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E94 / E10** | Process Flow 的 operation 与「加工目标层」强相关；工艺步骤按层组织 | `mds_layer_operation` 把 layering 与 operation 显式绑定 |
| **fab 工艺实践（Layer / Mask Layer）** | 工艺层（Process Layer，如 POLY/M1）与掩模层（Mask Layer）**不等价**：一层工艺可能需**多张掩模**（如 LELE/SADP 多重曝光）；一次掩模也可能跨多工序 | `layer_type` 区分 `PROCESS_LAYER` / `MASK_LAYER`；映射为多对多 |
| **先进工艺（Multi-Patterning）** | 14nm 以下大量使用 LELE/SADP/SAQP，一个工艺层对应 2–4 张掩模 | `mds_reticle_layer` 支持一层多片（[reticle-design](reticle-design.md)） |
| **良率分析（YMS）** | 按层（Layer-by-Layer）钻取缺陷与良率是标准分析维度 | Layer 作为分析维度主数据 |
| **产品定义（`mask_layer_count`）** | 产品需声明掩模层数（成本与工时估算） | `mds_layer` 提供逐层明细，二者一致性由校验保证 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Layer / 层** | 芯片结构上的一层（POLY / M1 / VIA1…），是**工艺与掩模的公共坐标** | **不是**设备层级/位置层级；也不是 recipe 内部步骤 |
| **PROCESS_LAYER / 工艺层** | 工艺意义上的层（一次完整的"光刻+刻蚀"形成） | 与 MASK_LAYER 可能 1:N |
| **MASK_LAYER / 掩模层** | 一张掩模对应的层 | 多图形化时一个 PROCESS_LAYER 有多张 MASK_LAYER |
| **layer_seq / 堆叠序** | 层在堆叠中的顺序（越小越下层） | 不等于工序号 `op_seq`（一层可跨多工序） |
| **role / 层的工序角色** | 该 operation 对本层做什么：`PATTERN`（成形）/`ETCH`/`IMPLANT`/`DEPOSIT`/`INSPECT`/`MEASURE` | 不是 `flow_type`（那是流程控制） |

---

## 3. 实体总览与 ER

```
mds_layer (层定义: layer_type / layer_seq / tech_node)
   │
   ├──< mds_layer_operation  (层 ↔ 工序, 带 role)
   │        route_operation_id ──> mds_route_operation
   │
   └──< mds_reticle_layer    (层 ↔ 光罩, 多对多; 定义在 reticle-design)
            reticle_id ──> mds_reticle
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 层 | `mds_layer` | 层定义（类型/堆叠序/技术节点） |
| 层↔工序 | `mds_layer_operation` | 哪道工序加工哪层、以何角色 |
| 层↔光罩 | `mds_reticle_layer` | 见 [reticle-design §3.3](reticle-design.md)（不在本表重复定义） |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_layer`（层定义）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 层代码（如 `POLY`/`M1`/`VIA1`/`CONT`） |
| `name` | VARCHAR(128) | | 层名称 |
| `layer_type` | VARCHAR(16) | NOT NULL | `PROCESS_LAYER`/`MASK_LAYER`/`IMPLANT_LAYER`/`TEST_LAYER` |
| `layer_seq` | INT | NOT NULL | 堆叠顺序（越小越靠下） |
| `purpose` | VARCHAR(32) | | 用途：`GATE`/`METAL`/`CONTACT`/`VIA`/`PASSIVATION`/`MARK` |
| `tech_node` | VARCHAR(16) | | 适用技术节点（如 `28`/`14`/`7`；空=通用） |
| `parent_layer_id` | VARCHAR(32) | FK→self | 可选：层分组（如 `M` 下属 `M1/M2/M3`） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `revision` | VARCHAR(16) | NOT NULL | 版本 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tech_node, tenant_id, deleted)`。

### 3.2 `mds_layer_operation`（层 ↔ 工序映射）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `layer_id` | VARCHAR(32) | NOT NULL, FK→`mds_layer.id` | 层 |
| `route_operation_id` | VARCHAR(32) | NOT NULL, FK→`mds_route_operation.id` | 工序 |
| `role` | VARCHAR(16) | NOT NULL | `PATTERN`/`ETCH`/`IMPLANT`/`DEPOSIT`/`CMP`/`CLEAN`/`INSPECT`/`MEASURE`/`OTHER` |
| `is_critical` | BIT | DEFAULT 0 | 是否关键层工序（CD 关键尺寸管控） |
| `seq_in_layer` | INT | | 该层内工序顺序（同一层多工序时） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(layer_id, route_operation_id, role, tenant_id, deleted)`。
- 闭环：与 [route-design §3.3](route-design.md) 的 operation 一一对应；一条 route 的工序集合可**完整落层**（覆盖分析可校验"有无工序未归属任何层"）。

---

## 4. 关键设计点（拍板 L1–L5）

### 4.1 层的独立定位（L1）
见 §0.1。Layer 是产品、路线、掩模三者的公共锚点，必须独立成域。

### 4.2 工艺层 vs 掩模层（L2，先进工艺关键）
- `PROCESS_LAYER`：工艺意义上的层（一次成形）；
- `MASK_LAYER`：一张掩模对应的层；
- 关系：**1 PROCESS_LAYER : N MASK_LAYER**（多重曝光 LELE/SADP/SAQP）。
- 例：`M1`（PROCESS_LAYER）在 14nm 下由 `M1_A`/`M1_B`（两张 MASK_LAYER + 两片 reticle）共同形成。

### 4.3 层 ↔ 工序映射与角色（L3）
- 通过 `role` 表达"这道工序对层做什么"：光刻 → `PATTERN`；刻蚀 → `ETCH`；离子注入 → `IMPLANT`。
- 让 MES/YMS 能做**按层的工序聚合**（"POLY 层共 7 道工序"）与**按层的缺陷归集**。

### 4.4 堆叠序与产品一致性（L4）
- `layer_seq` 定义层在堆叠中的物理顺序，与 [route-design](route-design.md) 的 `op_seq` **正交**（一层可跨多工序，一个工序也可跨多层，如同时刻蚀 M2/VIA1）。
- 与 `mds_product.mask_layer_count` 的一致性：`count(layer where tech_node=product.tech_node and layer_type in (MASK_LAYER))` 应等于 `mask_layer_count` —— 由 `LayerValidator` 校验（允许显式豁免）。

### 4.5 与光罩命名/编码联动（L5）
- 层编码遵循 fab 通用命名（POLY/M1/CONT），交 [naming-rule-design](naming-rule-design.md) 可选配置（`entity_type=LAYER`）。
- 光罩编码通常**内嵌层码**（如 `RET-M1-A`），由 [reticle-design §4.5](reticle-design.md) 的命名规则生成，实现「看码知层」。

### 4.6 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 层实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 层与映射变更落 `{entity}_hist`（层定义影响面大，留痕必要） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + 层种子（POLY/M1/M2/CONT/VIA…） |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_layer (
  id              VARCHAR(32) NOT NULL,
  tenant_id       VARCHAR(32) NOT NULL,
  code            VARCHAR(64) NOT NULL,
  name            VARCHAR(128),
  layer_type      VARCHAR(16) NOT NULL,
  layer_seq       INT NOT NULL,
  purpose         VARCHAR(32),
  tech_node       VARCHAR(16),
  parent_layer_id VARCHAR(32),
  status          VARCHAR(16) NOT NULL,
  revision         VARCHAR(16) NOT NULL,
  description     VARCHAR(512),
  version_        BIGINT NOT NULL DEFAULT 0,
  deleted         BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_layer PRIMARY KEY (id),
  CONSTRAINT uq_mds_layer UNIQUE (code, tech_node, tenant_id, deleted),
  CONSTRAINT fk_layer_parent FOREIGN KEY (parent_layer_id) REFERENCES mds_layer (id)
);
CREATE INDEX idx_layer_seq ON mds_layer (layer_seq);

CREATE TABLE mds_layer_operation (
  id                 VARCHAR(32) NOT NULL,
  tenant_id          VARCHAR(32) NOT NULL,
  layer_id           VARCHAR(32) NOT NULL,
  route_operation_id VARCHAR(32) NOT NULL,
  role               VARCHAR(16) NOT NULL,
  is_critical        BIT DEFAULT 0,
  seq_in_layer       INT,
  description        VARCHAR(512),
  version_           BIGINT NOT NULL DEFAULT 0,
  deleted            BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_layer_op PRIMARY KEY (id),
  CONSTRAINT uq_mds_layer_op UNIQUE (layer_id, route_operation_id, role, tenant_id, deleted),
  CONSTRAINT fk_layer_op_layer FOREIGN KEY (layer_id) REFERENCES mds_layer (id),
  CONSTRAINT fk_layer_op_op FOREIGN KEY (route_operation_id) REFERENCES mds_route_operation (id)
);
CREATE INDEX idx_layer_op_op ON mds_layer_operation (route_operation_id);
```

> 历史表 `mds_layer_hist` / `mds_layer_operation_hist` 由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.layer
  ├── entity
  │   ├── Layer.java               # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── LayerOperation.java       # @Entity 继承 BaseDefData（layer↔operation + role）
  │   └── package-info.java         # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── LayerRepository.java
  │   └── LayerOperationRepository.java
  ├── service
  │   ├── LayerService.java          # 继承 AbstractJpaService
  │   └── LayerValidator.java         # 与 product.mask_layer_count 一致性、工序落层覆盖
  └── web
      └── LayerController.java        # 查询/层堆叠视图/层↔工序矩阵
```

---

## 8. 待补 / 后续

- **层堆叠可视化**：管理端按 `layer_seq` 渲染层剖面 + 关联工序/光罩，供工艺评审。
- **与 YMS 的接口**：按层输出良率/缺陷归集维度（MDS 提供层字典，分析在 YMS）。
- **多层工艺适配**：SADP/SAQP 下 PROCESS_LAYER : MASK_LAYER 比例建模与校验。
- **与既有文档闭环**：本文引用 `mds_route_operation`（[route-design](route-design.md)）、`mds_reticle`（[reticle-design](reticle-design.md)）、`mds_product.mask_layer_count`（[product-design](product-design.md)）、新增 `entity_type=LAYER`（[naming-rule-design](naming-rule-design.md)）。
