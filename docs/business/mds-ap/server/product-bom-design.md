# MDS 产品结构 BOM（Product BOM）设计

> 本文承接[产品主数据](product-design.md)（产品/族）、[物料设计](material-design.md)（用料 BOM）、[层定义](layer-design.md)、[载具设计](carrier-design.md)，
> 补全 MDS 的**产品结构 BOM（Product BOM）**——即「**一个产品由哪些子件构成**」。
>
> **与 [material-design](material-design.md) 的用料 BOM 严格区分（核心）**：
> | | 用料 BOM（material-design） | **产品结构 BOM（本文）** |
> | --- | --- | --- |
> | 语义 | **消耗清单**：这一步**用掉**什么料 | **结构清单**：这个产品**由**什么构成 |
> | 方向 | 工序/配方 → 物料 | **父产品 → 子产品/子件** |
> | 用途 | 投料齐套校验、成本 | **追溯血缘、组装/封装投料、Die Bank** |
> | 例 | 光刻工序用 0.5L 光刻胶 | 1 颗成品 = 1 颗 die + 1 个基板 + 1 个盖 |
>
> **补的缺口**：material-design 术语表明确写了「**不是产品 BOM**」，即产品结构清单确实未建（grep 全库确认）。
>
> 设计原则延续全篇：**MDS 拥有结构定义；实际投入/产出的实例关系（genealogy 实例）归 MES**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么必须建（核心）

| 场景 | 不建的后果 |
| --- | --- |
| 封装/组装段投料 | 不知道一个成品需要几个 die、几个基板，无法齐套校验 |
| Die Bank（裸片库存） | 无法表达「某产品从 die 到成品的层级关系」 |
| 客户追溯（尤其车规） | 无法回答「这颗芯片用的是哪个 wafer lot 的 die」——**只有结构 BOM 才能定义血缘方向** |
| 成本卷算 | 无法自底向上汇总成本（成本在 ERP，但**结构来自 MDS**） |
| 变更影响 | 换一颗 die 供应商/改一个基板，不知影响哪些成品 |

> **结论（PB1）**：产品结构 BOM 是 MDS 的独立主数据（**L4 物料与产出层**），与用料 BOM 互补：**一个"投入消耗"，一个"产出构成"**。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有者 | 说明 |
| --- | --- | --- |
| 产品结构 BOM 定义（父 → 子件关系与用量） | **MDS** | `mds_product_bom` / `_line` |
| 结构版本与生效窗口 | **MDS** | 同全篇版本范式 |
| **实际的父子血缘实例**（这批用了哪些具体 die lot） | **MES** | genealogy 实绩，MDS 不存 |
| 成本卷算结果 | **ERP** | MDS 只给结构 |
| Die Bank 库位/数量 | **WMS/ERP** | MDS 只给"结构上用得到 die bank" |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 结构行 ↔ 父产品 | `mds_product_bom.parent_product_code → mds_product.code` | [product-design §3.2](product-design.md) |
| 结构行 ↔ 子产品 | `mds_product_bom_line.child_ref`（`child_type=PRODUCT`） | [product-design §3.2](product-design.md) |
| 结构行 ↔ 子物料 | `mds_product_bom_line.child_ref`（`child_type=MATERIAL`） | [material-design §3.2](material-design.md) |
| 结构 ↔ 用料 BOM | 二者互补（结构 vs 消耗），可交叉核对 | [material-design §4.5](material-design.md) |
| 结构 ↔ 载具 | 成品装盒/装管（载具类型） | [carrier-design §3.1](carrier-design.md) |
| 结构 ↔ Bin | 子件来自哪个 Bin（die 分级） | [reason-defect-design §3.3](reason-defect-design.md) |
| 结构 ↔ 表达式 | `product.bom_children` 可入条件 | [expression-dsl-design §5](expression-dsl-design.md) |
| 编码规则 | `entity_type=PRODUCT_BOM`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **ISA-95 / ERP BOM** | BOM 是"产品结构"的标准表达，支持多层展开（MES 需要单层用于投料） | `mds_product_bom` 多层 + MES 按需展开 |
| **半导体后道（Assembly/Test）** | 封装 = die + 基板 + 键合线 + 塑封料/盖板 → **典型结构 BOM** | `mds_product_bom_line` 多子件 |
| **Die Bank / Known-Good-Die（KGD）** | 前道产出的 die 入 die bank，后道从 bank 取 die 组装 | 父子产品关系（die 是产品，成品也是产品） |
| **客户追溯（车规/医疗）** | 要求全程血缘可追（wafer lot → die → 封装 lot → 成品 lot） | 结构 BOM **定义血缘方向**，MES 落实例 |
| **SEMI E87 / E142** | 载具/批次与产品的关联 | 成品装载具（盒/管/托盘） |
| **IATF 16949 / PPAP** | 产品结构变更需受控 | 与 [change-mgmt-design](change-mgmt-design.md) 联动 |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Product BOM / 产品结构 BOM** | 产品由哪些子件构成 | **不是**用料 BOM（消耗清单，见 header 对比表） |
| **parent / child** | 父（产出）/ 子（投入构成） | 与"用料"区分：用量是**构成**而非**消耗** |
| **qty_per** | 单位父件需要多少子件 | 与 `qty_per_unit`（用料 BOM）类似但语义不同 |
| **scrap_rate / 损耗率** | 结构性损耗（如 100 颗 die 中 2 颗报废） | 与工艺良率不同：这是**结构定义里的损耗** |
| **BOM 层级 / level** | 多层结构（成品 → 模块 → 零件） | 展开深度由使用方决定 |
| **Genealogy / 血缘实例** | 实际"这批用了哪些子件" | 定义在 MDS，**实例在 MES** |
| **KGD** | Known Good Die（已知良好裸片） | die bank 里可用的 die |

---

## 3. 实体总览与 ER

```
mds_product (父产品)
      ▲ parent_product_code
mds_product_bom (结构 BOM 头: bom_type, revision, status)
      └──< mds_product_bom_line (结构行)
                 child_type ∈ {PRODUCT, MATERIAL}
                 child_ref  → mds_product.code | mds_material.code
                 qty_per / uom / scrap_rate / bin_ref
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 结构 BOM | `mds_product_bom` | 头（版本/状态/生效） |
| 结构行 | `mds_product_bom_line` | 父 → 子件 |

> 继承 `BaseDefData`（§5）。

### 3.1 `mds_product_bom`（产品结构 BOM）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `bom_code` | VARCHAR(64) | NOT NULL | BOM 代码 |
| `name` | VARCHAR(128) | | 名称 |
| `bom_type` | VARCHAR(16) | NOT NULL | `ASSEMBLY`（组装）/`PACKAGING`（封装）/`TEST_FLOW`（测试流转）/`DIE_BANK`（裸片库） |
| `parent_product_code` | VARCHAR(64) | NOT NULL, FK→`mds_product.code` | 父产品 |
| `level_no` | INT | | 结构层级（1=直接子件层） |
| `revision` | VARCHAR(16) | NOT NULL | 版本 |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`IN_REVIEW`/`APPROVED`/`RELEASED`/`OBSOLETE`/`ARCHIVED` |
| `is_frozen` | BIT | DEFAULT 0 | 冻结（与 status 正交，同全篇范式） |
| `effective_from`/`effective_to` | DATETIME(3) | | 生效窗口 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(bom_code, revision, tenant_id, deleted)` / `(parent_product_code, bom_type, revision, tenant_id, deleted)`。

### 3.2 `mds_product_bom_line`（结构行）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `bom_id` | VARCHAR(32) | NOT NULL, FK→`mds_product_bom.id` | 所属 BOM |
| `seq` | INT | NOT NULL | 行号 |
| `child_type` | VARCHAR(16) | NOT NULL | `PRODUCT`（子产品/裸片/模块）/`MATERIAL`（子物料：基板/塑封料/载带…） |
| `child_ref` | VARCHAR(128) | NOT NULL | 子件（`mds_product.code` 或 `mds_material.code`） |
| `qty_per` | DECIMAL(14,6) | NOT NULL | 单位父件的子件用量 |
| `uom` | VARCHAR(16) | FK→`mds_uom.code` | 单位 |
| `scrap_rate` | DECIMAL(6,4) | | 结构性损耗率（0–1） |
| `bin_ref` | VARCHAR(32) | FK→`mds_bin_code.code` | 允许的 die 等级（如仅 `PASS` bin） |
| `is_optional` | BIT | DEFAULT 0 | 是否可选子件 |
| `substitute_group` | VARCHAR(32) | | 可替代组（同组内可互换） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(bom_id, seq, tenant_id, deleted)`。
- **自引用检查**：结构不得成环（`A→B→A`），由 `ProductBomValidator` 做环检测。

---

## 4. 关键设计点（拍板 PB1–PB5）

### 4.1 与用料 BOM 严格分离（PB1）
见 header 对比表。二者**并存且互补**：
- **结构 BOM** 回答"由什么组成" → 用于**组装投料、血缘定义、成本卷算**；
- **用料 BOM** 回答"用掉什么" → 用于**工序投料齐套、耗材成本**。
- 交叉核对：如结构里的 die 数量应与前道产出/用料语义**一致**（校验规则，非强约束）。

### 4.2 多层结构与展开（PB2）
- 支持多层（成品 → 模块 → 零件）；`level_no` 标记层级；
- MDS 提供 `explode(parentProductCode, level)` **只读展开**（递归，含用量与损耗），MES/ERP 按需调用；
- 多层展开是**低频操作**（非热路径），允许直连查询。

### 4.3 裸片与 Die Bank（PB3）
- `bom_type=DIE_BANK` 表达「从 die bank 取 die 组装」；
- `child_type=PRODUCT` 且子件是 **die 产品**（前道产出物，本身也是产品）；
- `bin_ref` 限定可用 die 等级（KGD 要求）。

### 4.4 血缘方向的定义（PB4，核心）
```
MDS 定义方向：  成品 ← 子产品(die) ← 子件(基板/塑封料)
MES 落实例：    成品 lot L1  ← die lot D1(来自 wafer lot W1) ← 基板批次 S1
```
- **MDS 提供"血缘骨架"，MES 填充"血缘血肉"**；
- 客户追溯时：从成品 lot 出发，按结构 BOM 定义的层级逐层展开到 wafer lot（[layer-design](layer-design.md) 与 [material-design](material-design.md) 提供中间维度）。

### 4.5 版本与变更（PB5）
- 复用统一版本范式（RELEASED 不可变 + `cloneAsNewVersion` + `is_frozen`）；
- 结构变更（换子件、改用量）影响面大 → 走 [change-mgmt-design](change-mgmt-design.md)，影响分析可覆盖**所有父产品**（反向遍历）。

### 4.6 软删 / 租户 / 主键
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）。

---

## 5. 与平台底座衔接

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 结构 BOM 实体继承（`@Version`/审计/`tenant_id`/`deleted`） |
| `@History(SNAPSHOT)` | 结构变更落 `{entity}_hist`（**产品结构变更必须留痕**，追溯与合规要求） |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键 |
| 主历成对迁移 | 结构迁移 + 历史表 + 结构类型种子 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_product_bom (
  id                  VARCHAR(32) NOT NULL,
  tenant_id           VARCHAR(32) NOT NULL,
  bom_code            VARCHAR(64) NOT NULL,
  name                VARCHAR(128),
  bom_type            VARCHAR(16) NOT NULL,
  parent_product_code VARCHAR(64) NOT NULL,
  level_no            INT,
  revision             VARCHAR(16) NOT NULL,
  status              VARCHAR(16) NOT NULL,
  is_frozen           BIT DEFAULT 0,
  effective_from      DATETIME(3),
  effective_to        DATETIME(3),
  description         VARCHAR(512),
  version_            BIGINT NOT NULL DEFAULT 0,
  deleted             BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pbom PRIMARY KEY (id),
  CONSTRAINT uq_mds_pbom UNIQUE (bom_code, revision, tenant_id, deleted)
);
CREATE INDEX idx_pbom_parent ON mds_product_bom (parent_product_code, bom_type, status);

CREATE TABLE mds_product_bom_line (
  id               VARCHAR(32) NOT NULL,
  tenant_id        VARCHAR(32) NOT NULL,
  bom_id           VARCHAR(32) NOT NULL,
  seq              INT NOT NULL,
  child_type       VARCHAR(16) NOT NULL,
  child_ref        VARCHAR(128) NOT NULL,
  qty_per          DECIMAL(14,6) NOT NULL,
  uom              VARCHAR(16),
  scrap_rate       DECIMAL(6,4),
  bin_ref          VARCHAR(32),
  is_optional      BIT DEFAULT 0,
  substitute_group VARCHAR(32),
  description      VARCHAR(512),
  version_         BIGINT NOT NULL DEFAULT 0,
  deleted          BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_pbom_line PRIMARY KEY (id),
  CONSTRAINT uq_mds_pbom_line UNIQUE (bom_id, seq, tenant_id, deleted),
  CONSTRAINT fk_pbomline_bom FOREIGN KEY (bom_id) REFERENCES mds_product_bom (id)
);
CREATE INDEX idx_pbomline_child ON mds_product_bom_line (child_type, child_ref);
```

> 历史表由 `@History(SNAPSHOT)` 生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.productbom
  ├── entity
  │   ├── ProductBom.java             # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── ProductBomLine.java          # @Entity 继承 BaseDefData
  │   └── package-info.java            # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── ProductBomRepository.java
  │   └── ProductBomLineRepository.java
  ├── service
  │   ├── ProductBomService.java        # 继承 AbstractJpaService；cloneAsNewVersion
  │   ├── ProductBomExploder.java        # 多层展开（含用量/损耗），供 MES/ERP 调用
  │   └── ProductBomValidator.java        # 环检测（自引用）、用量>0、子件存在性
  └── web
      └── ProductBomController.java      # 结构查询/发布/克隆/展开
```

---

## 8. 与 MES 生产执行衔接

| 场景 | MDS 提供 | MES 使用 |
| --- | --- | --- |
| **后道组装/封装投料** | 结构 BOM 单层展开（子件 + 用量 + 损耗 + 允许 bin） | MES 齐套校验：投料前确认 die/基板/塑封料齐备；die 须满足 `bin_ref` |
| **Die Bank 取片** | `bom_type=DIE_BANK` 结构与 die 产品定义 | MES 从 die bank 取 KGD，按结构绑定到成品 |
| **血缘追溯** | 结构 **方向定义** | MES 落血缘实例；追溯时按结构逐层展开（成品 → die → wafer lot） |
| **拆解/返工** | 结构（反向：成品 → 子件） | 返工拆解时可反查构成 |
| **变更联动** | 结构变更经 ECN | 变更生效后 MES 刷新结构，**已有在制批按锁定版本继续**（[route-design §4.2.2](route-design.md) 同思路） |
| **成本卷算** | 结构 + 用量 | ERP 卷算（MDS 只给结构） |

> **对 MES 的关键价值**：血缘方向必须由**主数据**定义，否则 MES 无从知道"该往哪个方向追"。结构 BOM 是**追溯能力的前置条件**。

---

## 9. 待补 / 后续

- **与 ERP BOM 的分工**：若 ERP 已是 BOM 权威源，则 MDS 只存"MES 需要的结构视图"（子件 + 用量 + bin 约束），避免双份维护——**需与用户确认权威源**。
- **替代料与结构**：`substitute_group` 与 [material-design §3.6](material-design.md) 的替代料需统一口径。
- **多层展开性能**：深层结构的递归展开缓存策略。
- **与既有文档闭环**：本文引用 [product-design](product-design.md)、[material-design](material-design.md)（互补且严格区分）、[layer-design](layer-design.md)、[carrier-design](carrier-design.md)、[reason-defect-design §3.3](reason-defect-design.md)（bin）、[change-mgmt-design](change-mgmt-design.md)、[expression-dsl-design §5](expression-dsl-design.md)、[naming-rule-design](naming-rule-design.md)。
