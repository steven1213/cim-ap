# RMS 配方模型评估与行业做法研究

> 触发：用户提出「配方管理应与机台相关（区域树 → 机台 → 配方列表 → 配方详情），且当前配方只是一个 body、未做解析」。
> 本文档先**评估**该判断（对照真实代码），再**调研**应用材料等公司的做法，最后给出目标模型与分阶段路线。
> 评审日期：2026-10-13 ｜ 对照需求：`docs/business/rms-ap/Req.md` ｜ 关联：`Industry-Alignment.md`

---

## 0. 结论速览

**用户的判断成立，且比表面更严重。三条核心结论：**

1. **「配方与机台脱节」属实，但正确的解法不是「配方挂在机台下」，而是「双视角」** ——
   配方**主数据**必须仍按机型/工艺定义（一个配方部署到 N 台机台）；机台视角是**实例/部署视图**（PPID + 腔体 + 资格 + 清单状态）。这是 SEMI E139 与 AMAT SmartFactory 的一致做法。
2. **「Body 未解析」属实，且是最深的技术债** ——
   现状是 `bodyBase64` 不透明存档、`paramSnapshot` 长期为空、**配方详情页只显示「版本号 / 格式 / 状态」三个字段，连参数都没有**。业界（E139）要求配方 Body 结构化（Header + Parameters + SubRecipes + Checksum）并提供「良构 / 可执行性」校验。缺这一层，参数级比对、ECSV 校验、配方编辑、差异审阅全部悬空。
3. **「腔体（Chamber）」维度完全缺失** ——
   多腔体机台是半导体常态，配方是**按腔体**装载的（TSMC/国内专利的「机台主界面信息管理」正是处理这个）。我方 `grep chamber` 仅命中一个演示用的参数名 `CHAMBER.TEMP`，**没有任何腔体实体/模型**。

---

## 1. 现状事实（带代码证据）

| # | 事实 | 证据（文件:行 / 结论） |
|---|---|---|
| F1 | **区域树的数据结构存在**：单表自引用 `parentId` + `sortNo` | `device/DeviceArea.java:35` |
| F2 | **但区域只是管理页**：`DeviceAreaPage` 是平铺表格 + 「父区域下拉」，无可展开树；后端 `GET /device-areas` 返回**扁平列表**，不做树装配 | `web/src/pages/DeviceAreaPage.tsx:143,200`；`DeviceAdminController.java:64` |
| F3 | **设备确实挂区域与机型**：`Device.areaId` + `Device.deviceTypeId` | `device/Device.java:39,43` |
| F4 | **配方挂在「机型」而非「机台」**：`Recipe.deviceTypeId`（非空）+ `Recipe.areaId`（可空） | `recipe/Recipe.java:36,39` |
| F5 | 配方有主/子层级：`parentId` + `recipeLevel` | `recipe/Recipe.java:48,52` |
| F6 | **机台↔配方无直接关联**，只有三条间接链：`ToolRecipeMap`(PPID↔配方)、`Qualification`(配方×机台资格)、`ToolRecipeInventory`(机台实存清单) | `integration/ToolRecipeMap.java`、`qualification/`、`integration/ToolRecipeInventory.java` |
| F7 | **Body 不透明**：`bodyBase64` 原样存 LOB，`bodyHash` 只有 SHA-256 摘要，`bodyFormat ∈ {BINARY, TEXT, JSON_STRUCTURED}` | `recipe/RecipeVersion.java:45-55`；`recipe/BodyFormat.java` |
| F8 | **参数快照形同虚设**：`paramSnapshot` 可空，注释明写「**W1 允许为空**」；演示种子也不填 | `recipe/RecipeVersion.java:57-60` |
| F9 | **无任何 Body 解析器 / 机型格式适配器**：`grep -l "Parser\|Adapter"` 只命中 `BodyFormat` 枚举本身；`IntegrationService.parseParams` 仅做「扁平 JSON → Map」供在线比对，非结构化模型 | 全仓搜索结论 |
| F10 | **配方详情页极薄**：抽屉内只有一条版本表（版本号 / BodyFormat / 状态 / 激活按钮），**无参数、无步序、无 Body 内容** | `web/src/pages/RecipeLibraryPage.tsx:186-244` |
| F11 | **配方摘要不含「被哪些机台使用」**：`RecipeSummary` 无任何机台/PPID 字段 | `recipe/RecipeService.java` record |
| F12 | **腔体概念完全缺失**：仅演示种子里出现参数名 `CHAMBER.TEMP`，无 Chamber 实体 | `grep -ri chamber` 结论 |

**一句话概括现状**：我们建成了「配方主数据 + 版本状态机 + 规格/资格/金标/签核治理」的**骨架**，但（a）**操作者视角**（我管的那台机台现在装了什么配方）缺失，（b）**配方的内容**（Body 里到底有哪些步骤和参数）对外是黑盒。

---

## 2. 逐条评估用户诉求

| 用户诉求 | 评估 | 结论 |
|---|---|---|
| ① 配方管理应「与机台相关」 | **方向正确，但需精确化**。配方是**按机型/工艺定义的资产**，一台机台可装多个配方、一个配方可上多台机台 —— 属「多对多」。若把配方归属到机台，会立刻产生「同型号 5 台机台各存一份配方」的**重复与漂移灾难**（正是 E139 要消灭的问题）。 | ✅ 采纳「机台视角」，❌ 不采纳「配方归属机台」 |
| ② 区域树 → 挂机台 → 点机台看配方列表 | **完全对齐业界**（TSMC 专利「机台主界面」、AMAT 设备视图）。但现状区域仅是**管理页**（F2），且机台→配方**没有现成端点**（F6）。 | ✅ 采纳，需新建「设备视角」导航 + 聚合端点 |
| ③ 点配方看详情 | **现状详情太空**（F10）：点进去只有版本号/格式/状态。用户期望的是**配方内容**（步序、参数、单位、限值、子配方）。 | ✅ 采纳，须先补 Body 解析（F9） |
| ④ Body 未解析，要处理 | **完全正确，且是最深债**（F7–F9）。业界标准做法 = 结构化解析 + 「良构/可执行」校验。 | ✅ 最高优先级之一 |
| ⑤（隐含）按腔体管理 | 用户未提但属同一命题。多腔体机台的配方**按腔体装载**，缺此维度则「机台视角」不完整。 | ✅ 建议补（P1） |

**评估结论**：用户抓住了两个真问题（**操作者视角缺失** + **配方内容黑盒**），方向与业界一致；唯一需要在建模上修正的是「配方归属」的粒度 —— 应为**「配方（按机型定义）× 机台（按 PPID/腔体实例化）」** 的双层模型。

---

## 3. 行业做法：应用材料及同业

### 3.1 应用材料（Applied Materials）SmartFactory RMS 2.1.0 —— **最贴近本方诉求**

原厂文档要点（appliedsmartfactory.com，2022-04）：

- **配方身份 = 机台目录 + 配方短名**：「**Fully Qualified Recipe = Recipe Directory + Recipe Name**」。
  即同一配方的**完整身份是「在机台上的位置 + 名字」**，而不是一个全局唯一名字 —— 这正是我们 `ToolRecipeMap`(PPID) 想表达的东西。
- **运行时配方控制即「唯一真相源」**：RMS 在运行时同步 MES 与设备之间的「配方名 + 位置」，成为 runtime recipe control 的 point of truth。
- **配方解析模式（resolution modes）**：`External Short Form Mode` 与 `External Context Resolution` —— **按 `Operation + Equipment + Product` 组合在运行时解析出应使用哪个配方**。与 Critical Manufacturing 的「flexible context resolution」同源。
- **配方内容受控**：明确「tailored for **equipment recipe body and recipe parameter control**」—— body 与 parameter 都是受管对象。
- **审阅能力**：提供「review recipe **differences, duplicates, and consolidations**」——**重复/合并审查**是标配。
- **防错目标**：「reduce misprocessing by **controlling equipment constants and recipe parameter values**」「**validate runtime recipes against recipe specifications**」。

> 对本文的意义：AMAT 同时持有**中心仓库**（secure central repository、version control）与**设备身份（directory+name）**两侧 —— 印证「双视角」而非「归属机台」。

### 3.2 SEMI E139（RaP：Recipe and Parameter Management）—— **配方结构化的事实标准**

- **PDE（Process Definition Element）**：配方被拆成「部件」，支持**多部件配方结构**，目的是**消灭「用错配方」**、减少下载、按需取参数、免于为 APC 反解配方。
- **配方对象模型**（Critical Manufacturing MES 按 E139 落地，可直接照抄）：
  ```
  Recipe{ uid, Name, Description, type, IsExecutable, userInfo, supplierInfo, CreatedBy,
          Body(PDEBody | PDEBodyReference), Checksum,
          {RecipeParameter}, {SubRecipeParameter} }
  RecipeInstance → {RecipeInstanceParameter}
  RecipeContext / RecipeParameterOverrideContext
  ```
  —— **Body + Checksum + Parameters + SubRecipes** 四件套，正是我方缺的（F7/F9）。
- **E139/RRI 标准服务**：列 PDE、删 PDE、取原生格式、下发 PDE、**「确认 checksum / 校验配方是否可执行且良构（executable and well-formed）」** ← 这就是用户说的「解析」要产出的能力。
- **配方头用 XML**：E139 建议 header 用 XML 描述、body 支持复杂结构（不再是「格式化」文本）。

### 3.3 CEID / GEM 层（SEMI E30 / E5）

- **E5 Stream 7**（Process Program Management）与 **Stream 15**（Recipe Management，按 E139 定义 recipe / recipe namespace / recipe executor）、**Stream 16**（Process Management）——配方相关消息不止 S7。
- **S7F19/F20**（请求机台配方目录）、**S7F17/F18**（删除机台配方）——目录核对与删除下发。
- **E30 事件**：`PPChange` / `PPVerificationFailed` ——「设备在配方变化时必须通知主机」（我方 W6 已补）。

### 3.4 TSMC 专利与国内专利 —— **机台主界面交互的权威描述**

- **US6968245 / CN115082253B（TSMC 及国内同业）** 描述的界面结构几乎就是用户的诉求：
  - 设备模块管理 / **设备类型管理** / 设备信息管理 / **机台主界面信息管理**；
  - **「对每个机台的每个 Recipe 编辑 Chamber，选择可用 Chamber」** ← **配方 × 机台 × 腔体** 三元关系；
  - **查询 Recipe 列表（RECIPELIST）/ 设备组类型（GroupType）** ← 机台视角的配方列表；
  - **EAP 配方校验**：取 MES 的 Recipe 与「工艺腔 Recipe 管理系统」编辑的 Recipe **求交集**，再与**机台实存 Recipe 列表**比对 —— **交集存在才允许生产，否则停机**。
    ← 这也正印证我方 `ToolRecipeInventory`(机台清单核对) + `ToolRecipeMap`(PPID 映射) 的方向正确。

### 3.5 行业能力对照（本系统 vs 业界）

| 能力 | 业界（AMAT / E139 / CM / 专利） | 本系统现状 |
|---|---|---|
| 配方中心仓库 + 版本控制 | ✅ 标配 | ✅ 已有（`recipe`/`recipe_version`） |
| 机台视角（目录+短名 = FQ Recipe） | ✅ 标配 | 🟡 有 PPID 映射表，**无导航/无视图** |
| 配方×机台×**腔体** | ✅ 标配 | ❌ 缺腔体维度 |
| **配方 Body 结构化**（Header+Params+SubRecipe） | ✅ E139 强制 | ❌ Body 不透明 |
| **参数模型**（类型/单位/限值） | ✅ 标配 | 🟡 仅 Spec 侧有参数规格，**配方侧无** |
| **良构 / 可执行性校验** | ✅ E139 服务 | ❌ 无 |
| **上下文解析**（Product+Operation+Equipment→配方） | ✅ AMAT/CM | ❌ 无 |
| 差异 / **重复 / 合并审查** | ✅ AMAT | 🟡 有版本比对，**无跨配方重复检测** |
| 变更控制（CCB/签核） | ✅ | ✅ 已有（W3 签核） |
| 事件驱动（PPChange 等） | ✅ E30 | ✅ 已有（W6） |

---

## 4. 目标模型建议（Recipe Model Target）

### 4.1 双视角（核心修正）

```
主数据视角（Recipe Master，已有，权威源）
   机型 DeviceType ──< 配方 Recipe（按工艺/机型定义）──< 版本 RecipeVersion
                                              └── Body 结构化 → Parameters / Steps / SubRecipes

设备视角（Equipment Recipe View，新增）
   区域 DeviceArea（树）
     └── 机台 Device
          ├── Chamber（腔体，新增）
          └── 配方实例（PPID）← 由 ToolRecipeMap 承载
                ├── 指向某个 Recipe（跨机台共享同一主数据）
                ├── 绑定 Chamber
                ├── 资格状态（Qualification）
                ├── 实存状态（ToolRecipeInventory：OK / DRIFT / ORPHAN）
                └── 下发历史（RecipeDownloadJob）
```

**「点机台 → 配方列表」的数据来源**（可组合、可解释）：
`适用该机型(H) ∪ 该机台已映射PPID(H) ∪ 该机台已登记资格(H) ∪ 机台实存清单(H)`，并标注**未纳管（ORPHAN）**与**未登记资格**的异常项。

### 4.2 Body 解析（格式适配器）

```
BodyFormat(JSON_STRUCTURED | TEXT | BINARY)
   ↓  RecipeBodyParser（可插拔，按机型/格式注册）
ParsedRecipe {
   meta: { toolModel?, revision?, checksum }
   steps: [ { stepNo, name, duration?, params:[paramKey] } ]
   params: [ { key, name, type, value, unit, min?, max? } ]
   subRecipes: [ { ref, params:[...] } ]
}
   ↓ 落库
RecipeVersion.paramSnapshot（JSON）  ← 从「可空占位」变为「解析产物」
```

**解析后可解锁的能力**（当前全部悬空）：
1. **配方详情页**：步序表 + 参数表（键/名称/值/单位/限值/是否超规）
2. **参数级比对**（FR-C4）：现在是「有 hook 无数据」，解析后有真参数可比
3. **ECSV 校验**：EC/SV 校验直接对接参数集
4. **良构 / 可执行性校验**（对齐 E139 服务）：JSON 可解析 + 必填参数齐 + 类型合法
5. **重复 / 合并审查**（对齐 AMAT）：跨配方按参数指纹比对
6. **配方编辑**：参数级编辑（而非整块替换）

### 4.3 腔体（Chamber）

新增 `device_chamber`（挂 `deviceId`，`code`/`name`/`sortNo`）；`ToolRecipeMap` 增加 `chamberId`（可空=机台级）；配方明细按腔体分组展示。对齐 TSMC/CN 专利的「每机台每 Recipe 选可用 Chamber」。

### 4.4 上下文解析（P2，对齐 AMAT/CM）

`Product × Operation × EquipmentType(×Chamber)` 按优先级规则 → 解析应使用配方；支持「默认配方」与「选择冲突」提示。**不引入 R2R/APC**（属 SEMI E133，明确在 RMS 范围外，见 Industry-Alignment.md §2）。

---

## 5. 差距登记册（本命题相关）

| ID | 差距 | 影响 | 优先级 | 备注 |
|---|---|---|---|---|
| R1 | 配方详情页无内容（无参数/步序） | 操作者无法核对配方 | **P0** | 依赖 R3 |
| R2 | 无「机台 → 配方列表」视角与导航 | 与操作者心智不符；用户首要诉求 | **P0** | 前端为主 + 聚合端点 |
| R3 | Body 不解析（`paramSnapshot` 空、无 parser） | 参数级比对/ECSV/编辑/校验全悬空 | **P0** | 技术债根节点 |
| R4 | 区域树仅管理页，无树形导航 | 区域树价值未兑现 | **P1** | 后端返回树 + 前端树组件 |
| R5 | 无腔体（Chamber）维度 | 多腔体机台建模不完整 | **P1** | 对齐 TSMC/CN 专利 |
| R6 | 无「良构/可执行」校验 | 可能装载坏配方 | **P1** | 对齐 E139 服务 |
| R7 | 无跨配方重复/合并审查 | 配方膨胀、易用错 | **P2** | 对齐 AMAT |
| R8 | 无上下文解析（Product/Operation/Equipment） | 运行时选配方靠人 | **P2** | 对齐 AMAT/CM |
| R9 | `RecipeSummary` 无「被哪些机台使用」 | 无法评估变更影响面 | **P1** | 依赖 F6 三链聚合 |

---

## 6. 分阶段实施路线（建议）

**Phase A（P0，打通「看得见」）**
1. Body 解析适配器：先落 `JSON_STRUCTURED`（+ `TEXT` 的行式解析），产出 `ParsedRecipe` → 写 `paramSnapshot`；上传/建版时自动解析，解析失败不阻断但标 `PARSE_WARN`。
2. 配方详情页重构：`步序表 + 参数表 + 子配方 + Body 原文（折叠）`，参数标注是否超 Spec 限值。
3. 新增 `GET /api/v1/rms/devices/{id}/recipes` 聚合端点 + 前端「设备视角」页（区域树 → 机台 → 配方列表 → 明细抽屉）。

**Phase B（P1，补齐行业标配）**
4. 区域树接口返回树 + 前端树组件（复用 IAM `.tree*` 样式）。
5. 腔体实体 + `ToolRecipeMap.chamberId` + 按腔体展示。
6. 良构/可执行校验端点（对齐 E139 服务）+ 详情页「校验」按钮。
7. `RecipeSummary` 增「使用机台数 / PPID 数」+ 变更影响面提示。

**Phase C（P2，深水区）**
8. 上下文解析（Product+Operation+EquipmentType → 配方）。
9. 跨配方重复/合并审查。
10. （E139）Recipe Instance / Parameter Override 上下文（金标机差已部分覆盖其思想）。

---

## 7. 待决策（请确认后我再动手）

1. **配方归属粒度**：确认采用「**配方按机型定义 + 机台按 PPID/腔体实例化**」的双视角（而非把配方挂到机台）？
2. **Body 解析优先级与格式**：首版是否先支持 `JSON_STRUCTURED`（结构化 JSON）与 `TEXT`（行式 `KEY=VALUE` 或 CSV），`BINARY` 留待机型适配器？
3. **是否本轮就引入腔体（Chamber）**，还是先做「机台级」PPID、腔体放 P1？
4. **设备视角导航落点**：是**新增一个「设备配方」顶级菜单**（区域树 + 机台 + 配方），还是把现有「区域/设备」管理页升级为树形并内嵌配方面板？
5. **是否允许在 RMS 内编辑配方参数**（E139 主张），还是保持「只读解析 + 编辑仍在机台/离线工具」？

---

## 8. 结论

用户的批评切中要害：**当前 RMS 是「配方主数据的保管者」，还不是「配方操作者的工具」** —— 既没有「以机台为中心」的作业视角，也没有「配方内容」的可见性。
修正方向明确：**双视角 + Body 结构化 + 腔体维度**，三者皆与 SEMI E139 / AMAT SmartFactory / TSMC 专利一致。本文档仅作评估，未改动任何代码，待第 7 节决策后进入实现。
