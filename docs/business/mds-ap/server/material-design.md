# MDS 物料与耗材主数据设计（Material / Consumable）

> 本文承接[工艺路线](route-design.md)、[配方](recipe-design.md)、[设备](equipment-design.md)、[产品](product-design.md)，
> 补全 MDS 第八块业务主数据——**物料（Material）与耗材（Consumable）**，即晶圆厂里「**用来造东西的东西**」。
>
> 设计原则延续全篇：**MDS 拥有物料定义 + 规格 + 寿命策略 + 用料关系；库存数量与批次实时态归 ERP/WMS，线边消耗实绩归 MES/EAP**。

---

## 0. 定位与边界（拍板）

### 0.1 为什么必须把「物料」与「产品」分开（核心）

系统里最容易犯的错是「把投进去的东西和造出来的东西混在一张表」。两者语义完全不同：

| 维度 | **产品 Product**（[product-design.md](product-design.md)） | **物料 Material**（本文） |
| --- | --- | --- |
| 语义 | 造出来的**产出物**（晶圆 / 器件 / 料号） | 投进去的**消耗或投入品**（硅片、化学品、气体、靶材、光刻胶、备件、dummy wafer） |
| 生命周期 | 有 `tech_node` / 路线 / 良率 / 出货 | 有**保质期、使用次数、装载位置、替代料** |
| 状态语义 | 设计 → 量产 → EOL | 在库 → 开封 → 装载 → 耗尽 → 报废/回收 |
| 归属系统 | MDS 管定义，MES 管 lot | **MDS 管定义，ERP/WMS 管库存，MES 管线边消耗** |

> **结论（M1）**：物料是 MDS 的**独立主数据域**，与产品并列、互不复用。混表会导致「保质期/寿命/替代料」无处挂，也无法支撑投料齐套校验与耗材寿命停机。

### 0.2 MDS 拥有 vs 下游拥有

| 内容 | 拥有方 | 说明 |
| --- | --- | --- |
| 物料分类 / 物料主记录 / 规格 | **MDS** | 定义级，本文范围 |
| 来料检验指标（incoming spec） | **MDS** | `mds_material_spec` |
| 保质期 / 使用次数 / 寿命策略 | **MDS** | `mds_material_life_policy`（策略定义） |
| 用料 BOM（工序/配方用哪些料） | **MDS** | `mds_material_bom` |
| 替代料/等效料关系 | **MDS** | `mds_material_alternate` |
| **库存数量、批号、库位、出入库** | **ERP / WMS** | MDS 不存数量；只存"有哪些料、什么规格" |
| **线边消耗、开封时刻、累计使用次数** | **MES / EAP** | 实时态，驱动寿命到期判定 |
| **用量计数超限的实际停机动作** | **MES / EAP** | MDS 给策略与阈值，执行在下游 |

### 0.3 与既有主数据的闭环映射

| 关联关系 | 外键 / 引用 | 见 |
| --- | --- | --- |
| 物料 ↔ 物料大类 | `mds_material.class_id → mds_material_class.id` | 本文 §3.1 |
| 用料 ↔ 工序 | `mds_material_bom.route_operation_id → mds_route_operation.id` | [route-design §3.3](route-design.md) |
| 用料 ↔ 逻辑配方 | `mds_material_bom.logic_recipe_id → mds_logic_recipe.id` | [recipe-design §3.2](recipe-design.md) |
| 用料 ↔ 产品 | `mds_material_bom.product_code → mds_product.code` | [product-design §3.2](product-design.md) |
| 耗材 ↔ 设备类 | `mds_equipment_consumable.equipment_class_id → mds_equipment_class.id` | [equipment-design §3.2](equipment-design.md) |
| 耗材装载位置 ↔ 设备模块 | `mds_equipment_consumable.module_ref → mds_equipment_module.code`（逻辑引用） | [equipment-design §3.4](equipment-design.md) |
| 物料 ↔ 供应商 | `mds_material.vendor_id → mds_vendor.id` | [org-personnel-design](org-personnel-design.md) |
| 物料 ↔ 单位 | `mds_material.base_uom → mds_uom.code` | [uom-dict-design](uom-dict-design.md) |
| 物料批号编码规则 | `mds_naming_rule.entity_type=MATERIAL`（新增枚举） | [naming-rule-design §3.1](naming-rule-design.md) |
| 物料寿命 → 约束 | 约束类型 `MATERIAL_LIFE_LIMIT`（新增） | [constraint-design §4.2](constraint-design.md) |
| dummy wafer ↔ 载具 | dummy 用生产载具流转，`wafer_size` 一致 | [carrier-design §4.3](carrier-design.md) |

---

## 1. 行业依据

| 来源 | 要点 | 落点 |
| --- | --- | --- |
| **SEMI E10** | 设备停机分类中的「No Operator / Setup」常由**换料/补料**触发；耗材耗尽属计划/非计划停机 | 寿命策略 `on_exhaust_action` 与设备 `admin_status`/E10 状态联动（§4.4） |
| **SEMI E30 / E40** | 设备常量（EC）与配方参数可含**耗材标识**；换料后需重新确认 | `mds_equipment_consumable` 装载位置与设备接口点表呼应（[equipment-interface-design](equipment-interface-design.md)） |
| **ERP / WMS 集成（ISA-95）** | 库存与批次归 ERP/WMS；MES 需要**物料主数据**做投料校验与 BOM 展开 | MDS 只持定义，库存不落 MDS（§0.2） |
| **fab Material Management 实践** | 靶材（Target）、化学品（Chemical）、特气（Gas）、光刻胶（Photoresist）均有**寿命/使用量约束**，到期必须更换或再验证 | `mds_material_life_policy` 四类寿命（§4.4） |
| **批次追溯（Lot Genealogy）** | 客诉/良率分析需按**供应商批次**回溯到投入物料 | `is_lot_controlled` + 批号规则（§4.6） |
| **ISO 9001 / IATF 16949** | 来料检验（IQC）指标与记录受控 | `mds_material_spec` + [md-governance-design](md-governance-design.md) |

---

## 2. 术语澄清

| 术语 | 含义 | 易混淆点 |
| --- | --- | --- |
| **Material / 物料** | 投入品（消耗或转化为产出） | **不是** Product（产出物，见 §0.1） |
| **Consumable / 耗材** | 用量随时间/次数**递减**的物料（靶材、化学品、备件） | 是 Material 的子集（`material_type=CONSUMABLE`），非独立实体 |
| **Material Class / 物料大类** | 物料分类树（SUBSTRATE/CHEMICAL/GAS/TARGET/PHOTORESIST/SPARE_PART/DUMMY/CONSUMABLE/OTHER） | 不是设备 `equipment_class`（工艺分类） |
| **Material Spec / 来料规格** | 来料检验指标（纯度/颗粒/厚度），门禁在 IQC | **不是** `mds_process_flow_step_param`（那是**工艺参数窗口**，加工时用） |
| **Life Policy / 寿命策略** | 保质期 / 使用次数 / 使用时长 / 曝光次数上限与到期动作 | 策略与阈值在 MDS；**实际计数在 MES/EAP** |
| **Material BOM / 用料清单** | 某工序/配方/产品**用哪些料、用多少** | 不是产品 BOM（产品的结构组成）；本文 BOM 是**消耗清单** |
| **Alternate / 替代料** | 可互换的等效料（同规格不同厂商） | 与「备用料」不同：替代是**事前批准的等效关系** |

---

## 3. 实体总览与 ER

```
mds_material_class (大类树, parent_class_id 自引用)
        ▲
        │ class_id
mds_material ──< mds_material_spec        (来料检验指标)
        │    ──< mds_material_life_policy (寿命策略: 保质/次数/时长/曝光)
        │    ──< mds_material_alternate   (替代料: material ↔ material)
        │
        ├──> mds_vendor (供应商, org-personnel-design)
        └──> mds_uom    (基本单位, uom-dict-design)

mds_material_bom (用料清单, bom_type ∈ OPERATION/RECIPE/PRODUCT)
   ├── route_operation_id ──> mds_route_operation
   ├── logic_recipe_id    ──> mds_logic_recipe
   ├── product_code       ──> mds_product.code
   └──< mds_material_bom_line (行: material_id + qty_per_unit + uom)

mds_equipment_consumable (设备类 ↔ 耗材适用性 + 装载位置 + 标准用量)
   ├── equipment_class_id ──> mds_equipment_class
   └── material_id        ──> mds_material
```

| 实体 | 表名 | 说明 |
| --- | --- | --- |
| 物料大类 | `mds_material_class` | 分类树（8 类语义族 + 自定义子类） |
| 物料 | `mds_material` | 物料主记录 |
| 来料规格 | `mds_material_spec` | 来料检验指标（IQC） |
| 寿命策略 | `mds_material_life_policy` | 保质/使用次数/时长/曝光上限与到期动作 |
| 用料清单 | `mds_material_bom` | 工序/配方/产品的用料头 |
| 用料行 | `mds_material_bom_line` | 行明细（物料 + 标准用量 + 单位） |
| 替代料 | `mds_material_alternate` | 等效/优选/应急替代关系 |
| 设备耗材 | `mds_equipment_consumable` | 设备类↔耗材适用性与装载位置 |

> 全部继承 `BaseDefData`（§5）：`id` / `tenant_id` / `@Version` / `deleted` / 审计五件套；历史表 `{entity}_hist` 由 `@History(SNAPSHOT)` 生成。

### 3.1 `mds_material_class`（物料大类）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `class_code` | VARCHAR(64) | NOT NULL | 类代码（如 `CHEM-ACID`） |
| `class_name` | VARCHAR(128) | | 名称 |
| `parent_class_id` | VARCHAR(32) | FK→self | 父类（树形；顶层为空） |
| `material_category` | VARCHAR(24) | NOT NULL | `SUBSTRATE`/`CHEMICAL`/`GAS`/`TARGET`/`PHOTORESIST`/`SPARE_PART`/`DUMMY_WAFER`/`CONSUMABLE`/`OTHER` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(class_code, tenant_id, deleted)`。

### 3.2 `mds_material`（物料主记录）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 物料编码（由 naming-rule 生成，`entity_type=MATERIAL`） |
| `name` | VARCHAR(128) | | 物料名 |
| `class_id` | VARCHAR(32) | NOT NULL, FK→`mds_material_class.id` | 所属大类 |
| `material_type` | VARCHAR(16) | NOT NULL | `RAW`（原材料）/`CONSUMABLE`（耗材）/`SPARE`（备件）/`DUMMY`（假片/挡片） |
| `spec_grade` | VARCHAR(32) | | 规格等级（如 `SEMI-C12`、`VLSI`） |
| `vendor_id` | VARCHAR(32) | FK→`mds_vendor.id` | 主供应商（[org-personnel-design](org-personnel-design.md)） |
| `vendor_part_no` | VARCHAR(64) | | 供应商料号（对账用） |
| `base_uom` | VARCHAR(16) | NOT NULL | 基本单位（`L`/`KG`/`PCS`/`ML`/`SLM`，[uom-dict-design](uom-dict-design.md)） |
| `wafer_size` | VARCHAR(16) | | 尺寸（仅基板/假片有；`200`/`300`） |
| `shelf_life_days` | INT | | 保质期（天）；NULL=无 |
| `storage_condition` | VARCHAR(16) | | `ROOM`/`N2`/`VACUUM`/`FRIDGE`/`DARK`/`HAZMAT` |
| `hazard_class` | VARCHAR(32) | | 危险品粗分类（**快速标志**）；**危险品台账、GHS 分类、UN 号、储存限值、相容性**见 [EHS 与安全设计](ehs-safety-design.md) |
| `is_lot_controlled` | BIT | DEFAULT 0 | 是否按批次管控（追溯强度） |
| `is_serialized` | BIT | DEFAULT 0 | 是否单件序列管控（靶材/光刻胶桶） |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `revision` | VARCHAR(16) | NOT NULL | 规格版本（改规格升版本） |
| `effective_from`/`effective_to` | DATETIME(3) | | 生效窗口（SCD2-lite） |
| `ext_attrs` | JSON | | 异构扩展（分子式/密度/闪点…） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。

### 3.3 `mds_material_spec`（来料规格 / IQC 指标）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `material_id` | VARCHAR(32) | NOT NULL, FK→`mds_material.id` | 所属物料 |
| `spec_key` | VARCHAR(64) | NOT NULL | 指标名（`PURITY`/`PARTICLE`/`THICKNESS`/`PH`） |
| `data_type` | VARCHAR(16) | NOT NULL | `NUMERIC`/`STRING`/`ENUM` |
| `target`/`min_value`/`max_value` | VARCHAR(64) | | 目标/下限/上限 |
| `unit` | VARCHAR(16) | | 单位 |
| `spec_type` | VARCHAR(16) | | `INCOMING`（来料）/`SAFETY`（安全）/`ENV`（环保） |
| `test_method` | VARCHAR(64) | | 检验方法/标准 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(material_id, spec_key, tenant_id, deleted)`。

### 3.4 `mds_material_life_policy`（寿命策略）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `code` | VARCHAR(64) | NOT NULL | 策略代码 |
| `material_id` | VARCHAR(32) | FK→`mds_material.id` | 适用物料（与 `class_id` 二选一） |
| `class_id` | VARCHAR(32) | FK→`mds_material_class.id` | 适用大类（批量策略） |
| `life_type` | VARCHAR(16) | NOT NULL | `SHELF`（保质）/`USE_CYCLE`（使用次数）/`USE_DURATION`（使用时长）/`EXPOSURE`（曝光/光刻胶） |
| `limit_value` | DECIMAL(12,2) | NOT NULL | 上限值（天/次/小时） |
| `limit_unit` | VARCHAR(16) | | `DAY`/`CYCLE`/`HOUR` |
| `retest_days` | INT | | 到期后**再验证**周期（可延长寿命） |
| `on_exhaust_action` | VARCHAR(16) | NOT NULL | `REPLACE`（更换）/`RETEST`（再验证）/`SCRAP`（报废）/`HOLD`（扣留） |
| `warning_ratio` | DECIMAL(4,2) | | 预警比例（如 0.8 → 达 80% 预警） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(code, tenant_id, deleted)`。
- **实际计数在 MES/EAP**：MDS 只给阈值与动作定义；`USE_CYCLE` 的计数由设备侧累计（[equipment-interface-design](equipment-interface-design.md) 的 DV/EC 承载）。

### 3.5 `mds_material_bom` / `mds_material_bom_line`（用料清单）

**`mds_material_bom`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `bom_code` | VARCHAR(64) | NOT NULL | BOM 代码 |
| `name` | VARCHAR(128) | | 名称 |
| `bom_type` | VARCHAR(16) | NOT NULL | `OPERATION`/`RECIPE`/`PRODUCT` |
| `route_operation_id` | VARCHAR(32) | FK→`mds_route_operation.id` | `bom_type=OPERATION` 时非空 |
| `logic_recipe_id` | VARCHAR(32) | FK→`mds_logic_recipe.id` | `bom_type=RECIPE` 时非空 |
| `product_code` | VARCHAR(64) | FK→`mds_product.code` | `bom_type=PRODUCT` 时非空 |
| `revision` | VARCHAR(16) | NOT NULL | 版本 |
| `status` | VARCHAR(16) | NOT NULL | `DRAFT`/`ACTIVE`/`DEPRECATED` |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(bom_code, revision, tenant_id, deleted)`。

**`mds_material_bom_line`**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `bom_id` | VARCHAR(32) | NOT NULL, FK→`mds_material_bom.id` | 所属 BOM |
| `seq` | INT | NOT NULL | 行号 |
| `material_id` | VARCHAR(32) | NOT NULL, FK→`mds_material.id` | 物料 |
| `qty_per_unit` | DECIMAL(14,6) | NOT NULL | 标准用量（每片/每批/每次） |
| `uom` | VARCHAR(16) | NOT NULL | 用量单位 |
| `is_consumable` | BIT | DEFAULT 0 | 是否耗材 |
| `is_optional` | BIT | DEFAULT 0 | 是否可选（按工艺条件） |
| `substitute_group` | VARCHAR(32) | | 替代组（同组内可互换，见 §3.6） |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(bom_id, seq, tenant_id, deleted)`。

### 3.6 `mds_material_alternate`（替代料）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `material_id` | VARCHAR(32) | NOT NULL, FK→`mds_material.id` | 主物料 |
| `alternate_material_id` | VARCHAR(32) | NOT NULL, FK→`mds_material.id` | 替代物料 |
| `alt_type` | VARCHAR(16) | NOT NULL | `EQUIVALENT`（等效）/`PREFERRED`（优选）/`EMERGENCY`（应急） |
| `priority` | INT | DEFAULT 0 | 优先顺序 |
| `equivalence_note` | VARCHAR(256) | | 等效性说明（需工艺确认的差异） |
| `approved_by`/`approved_at` | — | | 工艺批准留痕 |
| `effective_from`/`effective_to` | DATETIME(3) | | 生效窗口 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(material_id, alternate_material_id, tenant_id, deleted)`。

### 3.7 `mds_equipment_consumable`（设备类 ↔ 耗材适用性）

> 表达「这类设备会用到哪些耗材、装在哪个位置、标准装多少」——供 MES 换料校验与 EAP 装载确认。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | VARCHAR(32) | PK | |
| `equipment_class_id` | VARCHAR(32) | NOT NULL, FK→`mds_equipment_class.id` | 设备类 |
| `material_id` | VARCHAR(32) | NOT NULL, FK→`mds_material.id` | 耗材 |
| `load_position` | VARCHAR(64) | | 装载位置/模块（逻辑引用 `mds_equipment_module.code`） |
| `standard_qty` | DECIMAL(12,4) | | 标准装载量 |
| `uom` | VARCHAR(16) | | 单位 |
| `life_policy_id` | VARCHAR(32) | FK→`mds_material_life_policy.id` | 该位置的寿命策略 |
| `description` | VARCHAR(512) | | 基类 |

- 唯一：`(equipment_class_id, material_id, load_position, tenant_id, deleted)`。

---

## 4. 关键设计点（拍板 M1–M6）

### 4.1 边界：产品 vs 物料（M1）
见 §0.1。要点：**物料不与产品复用**；MDS 持定义，库存归 ERP/WMS，线边实绩归 MES。

### 4.2 分类：大类树 + 语义族（M2）
- `material_category` 9 个语义族（SUBSTRATE/CHEMICAL/GAS/TARGET/PHOTORESIST/SPARE_PART/DUMMY_WAFER/CONSUMABLE/OTHER）用于**跨域分类统计**（EHS 报表、成本归集）；
- `parent_class_id` 树形用于**精细分类**（`CHEM-ACID` 隶属 `CHEMICAL`）；
- 与 [naming-rule-design §4.3](naming-rule-design.md) 呼应：物料 `code` 可用 `ATTR(material_category)+SEQ` 生成（如 `CHEM-0001`）。

### 4.3 来料规格 ≠ 工艺参数（M3）
| | `mds_material_spec`（本文） | `mds_process_flow_step_param`（[process-flow-design §3.4](process-flow-design.md)） |
| --- | --- | --- |
| 时机 | **加工前**（IQC 来料检验） | **加工中**（工艺窗口） |
| 对象 | 物料本身 | 工序/设备 |
| 超限动作 | 拒收/退货/让步接收 | 停线/调机/OOC（[sampling-spc-design](sampling-spc-design.md)） |

> 二者不可混表：一个是「料合格吗」，一个是「机台参数在窗口内吗」。

### 4.4 寿命策略与到期动作（M4）
- 四类寿命：`SHELF`（保质期，如光刻胶）、`USE_CYCLE`（使用次数，如靶材）、`USE_DURATION`（使用时长，如化学品槽液）、`EXPOSURE`（曝光量，如光刻胶/光源）。
- 到期动作：`REPLACE`/`RETEST`（再验证延长）/`SCRAP`/`HOLD`。
- **与设备状态机联动**：耗材耗尽 → 下游把设备模块置 `admin_status=MAINTENANCE` 或触发 E10 `SDT/UDT`（[equipment-design §3.14](equipment-design.md)）；MDS 提供策略，**执行在下游**。
- **与约束层联动**：`MATERIAL_LIFE_LIMIT` 约束类型（新增）可表达「该设备该耗材用量不得超 N」——见 [constraint-design §4.2](constraint-design.md)。

### 4.5 用料 BOM：三类挂载点（M5）
- `OPERATION`：挂工序（最常用）——"这步要投多少料"；
- `RECIPE`：挂逻辑配方——"这份配方耗哪些料"（与 recipe 解耦复用）；
- `PRODUCT`：挂产品——整片用料汇总（成本核算）。
- `substitute_group` 允许同组物料互换（MES 投料时按库存选组内可用项）。
- 与工艺流的关系：`mds_process_flow_step_param` 管"参数窗口"，本域管"用料清单"，同一 step 可两者都有。

### 4.6 替代料、供应商与批次（M6）
- **替代料**必须经工艺批准（`approved_by/at`）方可生效，且带 `effective_from/to`；
- **供应商**归 [org-personnel-design](org-personnel-design.md) 的 `mds_vendor`（不重复建）；
- **批号**：`is_lot_controlled=1` 的物料批次号规则交 [naming-rule-design](naming-rule-design.md)（`entity_type=MATERIAL_LOT`，新增枚举），生成后由 MES 落批次实例。

### 4.7 软删 / 租户 / 主键（与平台一致）
- 继承 `BaseDefData`；唯一键含 `(..., tenant_id, deleted)`（T2.5）；`@SQLRestriction("deleted=false")` + `@Filter(cimTenantFilter)`。

---

## 5. 与平台底座衔接（强制对齐）

| 平台能力 | 应用 |
| --- | --- |
| `BaseDefData` | 全部物料实体继承（`@Version`、审计五件套、`tenant_id`、`deleted`） |
| `@History(SNAPSHOT)` | 物料/规格/寿命策略/用料变更落 `{entity}_hist`，规格变更可追溯 |
| `cimTenantFilter` + `TenantContext` | 多租户过滤 |
| T2.5 软删唯一 | 唯一键含 `deleted` |
| `IdGenerator` | 主键生成 |
| 主历成对迁移 | DDL 与 `db/migration/{common,mysql}` 成对 + 分类/BOM 种子数据 |

---

## 6. DDL 示例（MySQL，主历成对）

```sql
CREATE TABLE mds_material_class (
  id                VARCHAR(32) NOT NULL,
  tenant_id         VARCHAR(32) NOT NULL,
  class_code        VARCHAR(64) NOT NULL,
  class_name        VARCHAR(128),
  parent_class_id   VARCHAR(32),
  material_category VARCHAR(24) NOT NULL,
  description       VARCHAR(512),
  version_          BIGINT NOT NULL DEFAULT 0,
  deleted           BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_mat_class PRIMARY KEY (id),
  CONSTRAINT uq_mds_mat_class UNIQUE (class_code, tenant_id, deleted),
  CONSTRAINT fk_mat_class_parent FOREIGN KEY (parent_class_id) REFERENCES mds_material_class (id)
);

CREATE TABLE mds_material (
  id                VARCHAR(32) NOT NULL,
  tenant_id         VARCHAR(32) NOT NULL,
  code              VARCHAR(64) NOT NULL,
  name              VARCHAR(128),
  class_id          VARCHAR(32) NOT NULL,
  material_type     VARCHAR(16) NOT NULL,
  spec_grade        VARCHAR(32),
  vendor_id         VARCHAR(32),
  vendor_part_no    VARCHAR(64),
  base_uom          VARCHAR(16) NOT NULL,
  wafer_size        VARCHAR(16),
  shelf_life_days   INT,
  storage_condition VARCHAR(16),
  hazard_class      VARCHAR(32),
  is_lot_controlled BIT DEFAULT 0,
  is_serialized     BIT DEFAULT 0,
  status            VARCHAR(16) NOT NULL,
  revision           VARCHAR(16) NOT NULL,
  effective_from    DATETIME(3),
  effective_to      DATETIME(3),
  ext_attrs         JSON,
  description       VARCHAR(512),
  version_          BIGINT NOT NULL DEFAULT 0,
  deleted           BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_material PRIMARY KEY (id),
  CONSTRAINT uq_mds_material_code UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_material_class FOREIGN KEY (class_id) REFERENCES mds_material_class (id),
  CONSTRAINT fk_material_vendor FOREIGN KEY (vendor_id) REFERENCES mds_vendor (id)
);
CREATE INDEX idx_material_class ON mds_material (class_id);
CREATE INDEX idx_material_status ON mds_material (status);

CREATE TABLE mds_material_spec (
  id          VARCHAR(32) NOT NULL,
  tenant_id   VARCHAR(32) NOT NULL,
  material_id VARCHAR(32) NOT NULL,
  spec_key    VARCHAR(64) NOT NULL,
  data_type   VARCHAR(16) NOT NULL,
  target      VARCHAR(64),
  min_value   VARCHAR(64),
  max_value   VARCHAR(64),
  unit        VARCHAR(16),
  spec_type   VARCHAR(16),
  test_method VARCHAR(64),
  description VARCHAR(512),
  version_    BIGINT NOT NULL DEFAULT 0,
  deleted     BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_mat_spec PRIMARY KEY (id),
  CONSTRAINT uq_mds_mat_spec UNIQUE (material_id, spec_key, tenant_id, deleted),
  CONSTRAINT fk_mat_spec_material FOREIGN KEY (material_id) REFERENCES mds_material (id)
);

CREATE TABLE mds_material_life_policy (
  id               VARCHAR(32) NOT NULL,
  tenant_id        VARCHAR(32) NOT NULL,
  code             VARCHAR(64) NOT NULL,
  material_id      VARCHAR(32),
  class_id         VARCHAR(32),
  life_type        VARCHAR(16) NOT NULL,
  limit_value      DECIMAL(12,2) NOT NULL,
  limit_unit       VARCHAR(16),
  retest_days      INT,
  on_exhaust_action VARCHAR(16) NOT NULL,
  warning_ratio    DECIMAL(4,2),
  description      VARCHAR(512),
  version_         BIGINT NOT NULL DEFAULT 0,
  deleted          BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_mat_life PRIMARY KEY (id),
  CONSTRAINT uq_mds_mat_life UNIQUE (code, tenant_id, deleted),
  CONSTRAINT fk_mat_life_material FOREIGN KEY (material_id) REFERENCES mds_material (id),
  CONSTRAINT fk_mat_life_class FOREIGN KEY (class_id) REFERENCES mds_material_class (id)
);

CREATE TABLE mds_material_bom (
  id                 VARCHAR(32) NOT NULL,
  tenant_id          VARCHAR(32) NOT NULL,
  bom_code           VARCHAR(64) NOT NULL,
  name               VARCHAR(128),
  bom_type           VARCHAR(16) NOT NULL,
  route_operation_id VARCHAR(32),
  logic_recipe_id    VARCHAR(32),
  product_code       VARCHAR(64),
  revision            VARCHAR(16) NOT NULL,
  status             VARCHAR(16) NOT NULL,
  description        VARCHAR(512),
  version_           BIGINT NOT NULL DEFAULT 0,
  deleted            BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_mat_bom PRIMARY KEY (id),
  CONSTRAINT uq_mds_mat_bom UNIQUE (bom_code, revision, tenant_id, deleted),
  CONSTRAINT fk_mat_bom_op FOREIGN KEY (route_operation_id) REFERENCES mds_route_operation (id),
  CONSTRAINT fk_mat_bom_recipe FOREIGN KEY (logic_recipe_id) REFERENCES mds_logic_recipe (id)
);

CREATE TABLE mds_material_bom_line (
  id                VARCHAR(32) NOT NULL,
  tenant_id         VARCHAR(32) NOT NULL,
  bom_id            VARCHAR(32) NOT NULL,
  seq               INT NOT NULL,
  material_id       VARCHAR(32) NOT NULL,
  qty_per_unit      DECIMAL(14,6) NOT NULL,
  uom               VARCHAR(16) NOT NULL,
  is_consumable     BIT DEFAULT 0,
  is_optional       BIT DEFAULT 0,
  substitute_group  VARCHAR(32),
  description       VARCHAR(512),
  version_          BIGINT NOT NULL DEFAULT 0,
  deleted           BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_mat_bom_line PRIMARY KEY (id),
  CONSTRAINT uq_mds_mat_bom_line UNIQUE (bom_id, seq, tenant_id, deleted),
  CONSTRAINT fk_mat_bomline_bom FOREIGN KEY (bom_id) REFERENCES mds_material_bom (id),
  CONSTRAINT fk_mat_bomline_material FOREIGN KEY (material_id) REFERENCES mds_material (id)
);

CREATE TABLE mds_material_alternate (
  id                    VARCHAR(32) NOT NULL,
  tenant_id             VARCHAR(32) NOT NULL,
  material_id           VARCHAR(32) NOT NULL,
  alternate_material_id VARCHAR(32) NOT NULL,
  alt_type              VARCHAR(16) NOT NULL,
  priority              INT DEFAULT 0,
  equivalence_note      VARCHAR(256),
  approved_by           VARCHAR(64),
  approved_at           DATETIME(3),
  effective_from        DATETIME(3),
  effective_to          DATETIME(3),
  description           VARCHAR(512),
  version_              BIGINT NOT NULL DEFAULT 0,
  deleted               BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_mat_alt PRIMARY KEY (id),
  CONSTRAINT uq_mds_mat_alt UNIQUE (material_id, alternate_material_id, tenant_id, deleted),
  CONSTRAINT fk_mat_alt_main FOREIGN KEY (material_id) REFERENCES mds_material (id),
  CONSTRAINT fk_mat_alt_sub  FOREIGN KEY (alternate_material_id) REFERENCES mds_material (id)
);

CREATE TABLE mds_equipment_consumable (
  id                 VARCHAR(32) NOT NULL,
  tenant_id          VARCHAR(32) NOT NULL,
  equipment_class_id VARCHAR(32) NOT NULL,
  material_id        VARCHAR(32) NOT NULL,
  load_position      VARCHAR(64),
  standard_qty       DECIMAL(12,4),
  uom                VARCHAR(16),
  life_policy_id     VARCHAR(32),
  description        VARCHAR(512),
  version_           BIGINT NOT NULL DEFAULT 0,
  deleted            BIT NOT NULL DEFAULT 0,
  create_time DATETIME(3), create_user VARCHAR(64),
  event_time  DATETIME(3), event_user  VARCHAR(64),
  event_name  VARCHAR(64), event_comment VARCHAR(256), trx_id VARCHAR(64),
  CONSTRAINT pk_mds_eq_consumable PRIMARY KEY (id),
  CONSTRAINT uq_mds_eq_consumable UNIQUE (equipment_class_id, material_id, load_position, tenant_id, deleted),
  CONSTRAINT fk_eqcons_class FOREIGN KEY (equipment_class_id) REFERENCES mds_equipment_class (id),
  CONSTRAINT fk_eqcons_material FOREIGN KEY (material_id) REFERENCES mds_material (id),
  CONSTRAINT fk_eqcons_life FOREIGN KEY (life_policy_id) REFERENCES mds_material_life_policy (id)
);
```

> 历史表 `mds_material_hist` / `mds_material_spec_hist` / … 由 `@History(SNAPSHOT)` 运行时生成。

---

## 7. 代码结构（包路径）

```
com.cim.mds.material
  ├── entity
  │   ├── MaterialClass.java          # @Entity 继承 BaseDefData + @History(SNAPSHOT)
  │   ├── Material.java                # @Entity 继承 BaseDefData（code/type/class/shelf_life/ext_attrs）
  │   ├── MaterialSpec.java            # @Entity 继承 BaseDefData（IQC 指标）
  │   ├── MaterialLifePolicy.java      # @Entity 继承 BaseDefData（寿命策略）
  │   ├── MaterialBom.java             # @Entity 继承 BaseDefData（三类挂载点）
  │   ├── MaterialBomLine.java         # @Entity 继承 BaseDefData（行）
  │   ├── MaterialAlternate.java       # @Entity 继承 BaseDefData（替代料）
  │   ├── EquipmentConsumable.java     # @Entity 继承 BaseDefData（设备类↔耗材）
  │   └── package-info.java            # @FilterDef(cimTenantFilter)
  ├── repository
  │   ├── MaterialRepository.java
  │   ├── MaterialBomRepository.java
  │   └── MaterialLifePolicyRepository.java
  ├── service
  │   ├── MaterialService.java          # 继承 AbstractJpaService；cloneAsNewVersion/deprecate
  │   ├── MaterialBomService.java       # BOM 展开（供 MES 投料校验，接口在 §8）
  │   └── MaterialValidator.java        # 替代料环路检测、寿命策略完备性、BOM 用量>0 校验
  └── web
      └── MaterialController.java       # 查询/版本发布/克隆/BOM 展开
```

---

## 8. 待补 / 后续

- **BOM 展开契约**：`explodeBom(operationId | recipeId | productCode)` 的输出格式（物料 + 标准用量 + 替代组），供 MES 投料齐套校验；库存校验由 MES 调 ERP/WMS。
- **寿命计数回写接口**：MES/EAP 上报「累计使用次数/时长」，MDS 只在**超过 `warning_ratio`** 时产生预警（或由 `MATERIAL_LIFE_LIMIT` 约束在下游判定）。
- **与 EHS 集成**：`hazard_class` 与危险品管理系统对齐；存储条件与库房（`mds_bank`）匹配。
- **成本与供应商比价**：物料成本、多供应商配额（`VENDOR_QUOTA`）——通常归 ERP，MDS 只保留主供应商引用。
- **与既有文档闭环**：本文引用 `mds_route_operation`（[route-design](route-design.md)）、`mds_logic_recipe`（[recipe-design](recipe-design.md)）、`mds_equipment_class` / `mds_equipment_module`（[equipment-design](equipment-design.md)）、`mds_product`（[product-design](product-design.md)）、`mds_carrier`（[carrier-design](carrier-design.md)）、新增枚举 `entity_type=MATERIAL/MATERIAL_LOT`（[naming-rule-design](naming-rule-design.md)）、新增约束类型 `MATERIAL_LIFE_LIMIT`（[constraint-design](constraint-design.md)）、`mds_vendor`（[org-personnel-design](org-personnel-design.md)）、`mds_uom`（[uom-dict-design](uom-dict-design.md)）。
