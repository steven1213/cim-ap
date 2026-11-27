# 参考项目深度梳理：站点建模 / 流程建模 / 产品规格 / 流程设计 / 流程对比 / StageDef / ModelFodCodeDef / ModelFodDef / ModelFodFsdDef / FsdModeler

> 源码实证级别：实体全字段（合并全部继承层为物理列）+ 前端逐视图按钮穷举（含显示/禁用条件）+ API 全清单。
> 参考项目：`chengdu_jt/MESDoc/A00. SourceCode`（前端 ameth.fmc Vue3+Element Plus+AntV X6；后端 Java/JPA，data.mes 218 实体 + industry.mes.data FodT*/Fsd* 家族）。
> 本文是[20-our-design.md](20-our-design.md)（我们的设计：ICM 定稿）的对照基线。

---

## 0. 全局机制（理解一切菜单的前提）

### 0.1 菜单与视图的参数化复用

菜单存 DB（MenuDef/MenuItemDef），菜单项带两个参数：

| 参数 | 作用 |
| --- | --- |
| `componentName` | 渲染哪个视图组件（`import.meta.glob('./views/*/*.vue')` 按文件名注册路由） |
| `tableName` | 该菜单操作哪张表——所有 API 按 `/{tableName}Ap/xxx` 动态拼装 |

因此：

- **StageDef / ModelFodCodeDef / ModelFodDef / ModelFodFsdDef / 站点建模** 五个菜单**没有专属页面**，全部复用 `Modeler.vue`（componentName='Modeler', tableName='XxxDef'）；
- 列定义也来自元数据 `TableColumnDef`（pkFlag / editMode: None|Create|Update|Always / editStyle / position），表格列与表单项全动态生成；
- `tableType ∈ {Spec, Revision, Duplicate, Relation}` 决定该菜单解锁哪些状态机按钮（Relation 型整体只读）。

### 0.2 实体基类（所有实体的公共物理列）

| 基类 | 字段 | 说明 |
| --- | --- | --- |
| EventData（最底层） | createTime / eventTime / createUser / eventUser / eventName / eventComment(250) / trxId | 事件审计七件套 |
| DefData | description(250) | 定义类 |
| SpecData | occupiedTime / occupiedUserId(30) / occupiedState(CheckIn\|CheckOut\|null) | **编辑占用锁**（人 vs 人） |
| RevisionData | **revision(PK)** / originRevision | 版本号 + 复制来源版本 |
| FrozenData | activeState(Active\|NotActive) / frozenState(Frozen\|Unfrozen) / archiveState(Archive\|Release) | 三态状态机字段 |
| *FactoryData | factoryName(40) | 多厂隔离 |
| FodData（FOD 锚点基类） | uuid(36) | 锚点 UUID |
| FsdData（FSD 参数基类） | validateState(Valid\|InValid) / fodUuid(36) | 参数校验态 + 挂靠锚点 |

每个实体配对 `XxxHist` 历史表：主键 = 原主键 + `timeKey`（事件时间），insert/update/delete 全量落历史（EventAp.addHistory()），提供 `findAllHist / findAllHistOnPage / exportHistExcel` 端点。**全项目无 seqno——版本体系 = revision + originRevision + Hist.timeKey。**

### 0.3 通用写 API 套路（所有 Ap 继承获得）

```
/{Entity}Ap/findAll | findAllWithEquals | findAllOnPage   查询（分页/条件）
/{Entity}Ap/insert | updateOnlyBinds | updateOnlyBindsForce | deleteAll
/{Entity}Ap/importData(multipart) | exportExcel(download)
/{Entity}Ap/findAllHist | findAllHistOnPage | exportHistExcel
/{Entity}Ap/duplicate | setOccupied | clearOccupied
/{Entity}Ap/frozenDesign | unfrozenDesign | frozen | unfrozen | active | notActive | archive | releaseArchive   （Revision 型）
```

---

## 1. 站点建模（StageDef 同构）

### 1.1 数据模型：STAGE_DEF

| 字段 | 类型 | PK | 说明 |
| --- | --- | --- | --- |
| stageName | String(40) | ✔ | 站点名 |
| stageOrder | Integer | | 展示/派工顺序 |
| factoryName | String(40) | | 工厂 |
| description | String(250) | | 描述 |

消费方：`ProcessFlowSpec.processFlowStage` 按名引用（流程按 Stage 归类）；`ProcessOperationSpec.stageName`（工序挂 Stage）。

### 1.2 界面（Modeler.vue 通用三栏）

- 左：分页数据表 + 组合查询行（tableCombo）+ History 开关（开→`findAllHist` 载历史表）
- 右：属性表单（新增时 Add / 行点击时 Update；PK 列 editMode=Create 决定新增可改、修改禁改）
- 按钮：Add（复制当前行→表单）· Remove（勾选多删/当前行，二次确认）· OK（insert / updateOnlyBinds）· Refresh · Upload（Excel 导入）· EXIT
- Relation 型整表只读；密码列 sha256（dummyPassword 'FACTORYModeler!' 占位机制）

### 1.3 消费链（为何 Stage 重要）

流程列表按 Stage 分组筛选 → 流程建模页 UpdateFlowStage 弹窗改归属（可触发审批 isApprove='Y'）→ 运行期 RTD/派工按 Stage 聚合。**Stage 是「流程→工序→机台」三层都挂的横向归类轴。**

---

## 2. 流程建模（ProcessFlowSpec）

### 2.1 数据模型：PROCESS_FLOW_SPEC（版本化主表）

继承：RevisionData + FrozenData + SpecData + DefFactoryData + EventData（完整字段见 §0.2）。自有字段：

| 字段 | 类型 | PK | 值域 | 说明 |
| --- | --- | --- | --- | --- |
| processFlowName | String(40) | ✔ | | 流程名 |
| revision | Integer | ✔ | | 版本号 |
| processFlowStage | String(40) | | | 所属站点（引 StageDef） |
| designData | Lob | | | 流程设计器原始数据（X6 节点/边 JSON） |
| designFrozenState | String(30) | | Frozen/Unfrozen | 设计态冻结（图冻结，独立于规格冻结） |
| processFlowType | String(40) | | Main/Rework… | 流程大类 |
| detailProcessFlowType | String(40) | | Production/Monitoring/Dummy… | 细分类型（枚举级联：type 值作为 enumName 再查枚举） |
| preventRule | String(40) | | None/CreateLot/TrackIn/CreateNTrackIn | 投入/进站阻止规则 |
| validateState | String(40) | | Valid/NotValid | FSD 参数校验状态 |
| originProcessFlowName | String(40) | | | 复制来源流程名 |

### 2.2 界面按钮全量（含禁用条件——状态机的代码级表达）

| 按钮 | 显示条件 | 禁用条件 | API |
| --- | --- | --- | --- |
| History 开关 | 恒显 | — | findAllHist |
| DesignFrozen | tableName='ProcessFlowSpec' && writeAble | designFrozen==Unfrozen && occupied==CheckIn 才可用 | frozenDesign |
| DesignUnfrozen | 同上 | designFrozen==Frozen && CheckIn && activeState==NotActive | unfrozenDesign |
| Frozen | tableType='Revision' | CheckIn+NotActive+Release 且 designFrozen!=Unfrozen | frozen |
| Active | tableType='Revision' | CheckIn+NotActive+Release；PFS 特例：**需 designFrozen==Frozen** | active |
| NotActive | Revision 且 PFS/ProductSpec | CheckIn+Active+Release | notActive |
| **Validation** | PFS 恒显（其他 Revision 型需 writeAble） | **designFrozen==Frozen 才可用** | validation |
| Archive | Revision | CheckIn+Active+Release | archive |
| ReleaseArchive | Revision | CheckIn+NotActive+Archive | releaseArchive |
| UpdateFlowStage | writeAble；Active 行禁用 | — | ProcessFlowExt/updateProcessFlowStage（isApprove='Y' 提示进审批） |
| Duplicate | Revision 型 | **designFrozen==Frozen 才可用** | duplicateProcessFlow |
| Add / Remove / Refresh | !Relation | Remove 受占用规则 | insert / deleteAll |
| Occupied / Clear Occupied | 属性面板 | CheckOut 仅本人可 Clear | setOccupied / clearOccupied |
| OK | 属性面板开 | occupied==本人 CheckOut | insert / updateOnlyBinds |

弹窗：Duplicate（源名/版本只读 + 目标名输入）· UpdateFlowStage（Stage 下拉，字典 codeName=ProcessFlowStage）· Upload（Excel 导入 importData）· Prevent（preventRule 强改，updateOnlyBindsForce）。

### 2.3 状态机（代码级规则）

```
occupied: CheckIn ⇄ CheckOut（编辑锁；冻结与占用互斥校验 COM_00084/00097）
designFrozen: Unfrozen → Frozen（Validation/Duplicate/Active 的前置）
frozenState:  Unfrozen → Frozen（规格冻结；active 前置）
activeState:  NotActive → Active（前置：Frozen + validateState=Valid；active(exclusive=true) 同名其他 revision 自动 NotActive）
archiveState: Release → Archive（归档前校验无 Release 状态 Lot 在线）→ ReleaseArchive（解归档）
```

---

## 3. 流程设计（ProcessFlowDesign）

### 3.1 数据模型（设计展开三表）

**NodePositionDef**（节点展开表）：

| 字段 | 类型 | PK | 说明 |
| --- | --- | --- | --- |
| nodePosition | String(250) | ✔ | 位置编码 `0000-000-0000`（4位段-3位段-4位段，DFS 生成；Switch 分支缩进） |
| processFlowName | String(40) | ✔ | 所属流程 |
| processFlowRevision | Integer | ✔ | 流程版本 |
| nodeId / nodeName | String(50) | | 设计器节点 ID/名 |
| nodeType | String(20) | | Start/End/Gateway/Operation/WorkFlow |
| processOperationName | String(40) | | 绑定工序（Operation 节点） |
| nodeCondition | String(250) | | 条件表达式 |
| fromNodePosition / toNodePosition | String(40) | | 前驱/默认后继 |
| fromProcessFlowName/Revision | | | 子流程引用（跨流程跳转） |
| returnNodePosition | String(40) | | 返工返回点 |
| branchActiveMode | String(40) | | Version/Last（分支激活模式） |
| stepCode | Integer | | 步序 |
| xcoordinate / ycoordinate | Integer | | 画布坐标 |

**NodeStepDef**（边表）：fromNodePosition + processFlowName/Revision + toNodePosition（复合 PK）+ stepAttribute(40)。

**MainProcessFlowOverAllDef**（主流程总览，校验通过后自动重建）：mainProcessFlowName/Revision + sequenceNumber(PK) + 子流程名/版本 + nodePosition/mainNodePosition + processOperationName + stepCode(String) + operationOccurFrequency。

### 3.2 界面

- 左 15%：三级过滤（Factory/ProcessFlowType/DetailProcessFlowType 级联）→ `makeItemTree` 载流程树（P=文件夹/C=流程）→ 关键字过滤
- 右 85%：多 Tab 画布容器（X6）+ 节点拖拽面板（Start/End/Operation/SubFlow/IfElse(IfTrue/IfFalse)/Switch(Case/Other/End)/Note）+ 属性编辑器 + minimap
- 画布工具：New/Open/Save/Save as/Delete/Open File/Save File + dagre 自动布局、undo、8 种对齐、缩放系列
- 按钮：Add（左上，新建流程抽屉→`insertByDesignForJson`）· Compare（90% 抽屉：左当前画布 + 右 With 树选版对比）· Fod（跳 FsdModeler，带 factoryName/processFlowName/revision）· ImportDesign（csv/xlsx 直传 `importProcessFlowSpecDesign`）· Spec（跳流程建模页）· Occupied/ClearOccupied · Save（画布有改动才可用）
- Save：写 designData → 后端 `insertByDesign/updateByDesignForJson` DFS 展开 NodePositionDef/NodeStepDef（先删后插）→ 版本联动（origin 复制→duplicateFsdAndFod；同版本编辑→syncFsdAndFodWhenSameVersion）→ 重建 MainProcessFlowOverAllDef

### 3.3 流程复制与参数搬运（FsdAndFodDuplicator，JdbcTemplate 动态扫 `FOD%`/`FSD%` 物理表）

- 复制：nodeKey=`nodeId|processOperationName` 建源→目标 nodePosition 映射 + 旧UUID→新UUID 映射 → 逐列复制 FOD 行（替换 flow/rev、UUID、nodePosition、referNodePosition，清 validateState/trxId）→ 按 fodUuid 映射复制 FSD 行 → 映射不上的节点（removed）跳过并记日志
- 同版本同步：删被移除节点 FSD/FOD（uuid 分块 900）；nodePosition 变更走 `TEMP_` 两段交换避唯一键冲突
- 删除：先删 FSD(by fodUuid) 再删 FOD(by flow+rev)

---

## 4. 流程对比（ProcessFlowCompare）

- 布局：左 Compare 树 25% + 中 With 树 25% + 右结果表 50%
- Compare 按钮：`compareTo {compareProcessFlowName/Revision, withProcessFlowName/Revision}` → 取两侧 NodePositionDef 按 nodeName 对齐 → 行 {compareOperationName, withOperationName, compareValue}；compareValue ∈ add(黄行)/remove(红行)/相同(无色)
- View Graph：全屏双画布对照（工具：Full Screen/Zoom/Actual Size/Mini Map/Find Compare Node/Find With Node）
- 树控件：Expand/Collapse/Refresh ×2、关键字过滤

---

## 5. FOD/FSD 体系（ModelFodCodeDef / ModelFodDef / ModelFodFsdDef / FsdModeler / FodValueModeler）

### 5.1 建模元数据五表（data.mes）

| 表 | PK | 字段 | 说明 |
| --- | --- | --- | --- |
| ModelFodDef | fodName(40) | fodType(Fod\|Fsd)、queryId(100)、position | FOD/FSD 表注册表（建模器 Tab 数据源；FodInitializer 启动时与实体注册表同步） |
| ModelFodCodeDef | modelCode(10) | entryName(30)、modelName(30) | 模型代码→条目映射 |
| ModelFodCodeCombineDef | fodCode+modelCode | position | 代码组合 |
| ModelFodEntryGroupDef | fodCode+groupName(30) | fodTableName(40)、queryId(100) | 代码→分组→物理表映射 |
| ModelFodEntryDef | entryName+fodCode+groupName | pkFlag(Y/N)、position | 分组下的条目（动态列）|
| ModelFodFsdDef | fodName+fodName | fsdName(40)、priority | **FOD↔FSD 绑定**（一个锚点可挂多类参数，按优先级） |
| UuidFodFsdDef | fodName+fodUuid+fsdName | — | **行级绑定**：FOD 具体行(uuid) ↔ FSD 表；AbstractFsdAp 在 insert/delete FSD 时自动维护 |

### 5.2 FOD 锚点表家族（industry.mes.data，全部继承 FodData：uuid + description + 审计7字段，全部配 Hist）

命名解码：**T**=Table、**F**=Flow、**P**=ProductSpec、**V**=Version(revision)、**N**=Node(nodePosition)、**O**=Operation、**M**=Machine、**L**=Layer。

| 表 | PK 维度（除 factoryName） | 说明 |
| --- | --- | --- |
| FodTFV | flowName, flowRevision | 流程级 |
| **FodTFVN** | flowName, flowRevision, nodePosition | **节点级（校验核心）**；非PK列 processOperationName |
| FodTFVN M | + machineName | 节点+机台 |
| FodTFVO | factory, nodePosition, flow, rev（+非PK operationName） | 节点+工序 |
| FodTFVOM | + machineName | 节点+工序+机台 |
| FodTFVM | flow, rev, machineName | 流程+机台 |
| FodTM | machineName | 机台级 |
| FodTPF | productSpecName, productSpecRevision, flow, rev | 产品×流程 |
| FodTPFN / FodTPFVN | + nodePosition | 产品×流程×节点 |
| FodTPFO | productSpec+rev, flow+rev, operationName | 产品×流程×工序 |
| FodTPFOM | productSpec, flow, operation, machine（**无 revision**——特例） | 产品×工序×机台 |
| FodTPM | productSpec+rev, machineName | 产品×机台 |
| FodTPO | productSpec+rev, operationName | 产品×工序 |
| **FodTLP** | layer, productSpecName（不含 revision/flow——按产品+Layer 通用配置） | 层级（Layer=子流程前2字符） |

> 15 张表的差异**只有 PK 维度组合，无额外业务列**——这就是「锚点层」的本质：同一框架按作用域维度实例化。

### 5.3 FSD 参数表家族（全部继承 FsdData：fodUuid(PK) + validateState + description + 审计7，全部配 Hist）

| 表 | PK（除 fodUuid） | 非PK 字段 | 说明 |
| --- | --- | --- | --- |
| **FsdMachine** | machineGroupName(40), machineName(40), chamberName(40), fsdRecipeType(40) | recipeName(40), recipeGroupName(40), department(40), generateRecipeNameRule(40), exactMatchRule(500) | 节点机台/腔/Recipe 指派；fsdRecipeType∈{GenerateRecipe, ExactMatchRecipe,…} |
| **FsdQtime** | qtimeRule(40), toFlowName(40), toFlowRevision, toNodePosition(40), fromPoint(40), toPoint(40) | toProcessOperationName, duration(Int), systemName, alarmId, warningDuration, warningSystemName, warningAlarmId | Q-time 规则：from/toPoint∈{TrackIn, TrackOut}；**超时+预警双配置**（时长/系统/告警码） |
| **FsdReticle** | mainFlowName(40), mainFlowRevision, reticleName(40) | reticleGroupName(Transient) | 站点级光罩指派（主流程+节点维度经 fodUuid） |
| **FsdReticleCommon** | reticleVersion(40)（挂 FodTLP.uuid） | layer(40), productSpecName(40), activeState(ActiveState 字典) | 通用光罩（产品×Layer 级，允许多版本） |
| **FsdSampling** | samplingFlowName, samplingFlowRev, samplingNodePosition, position, samplingType | samplingRuleValue(1000), machineName, lotName, productPositionTarget, productSelectionCount, productSelectionRule, productSelectionSlot(500), sortOrder | 抽检规则（规则值+选片四要素） |
| **FsdDedication** | —（单行/锚点） | productSpecName, portType, durableTypeList(100), durableDetailTypeList(100), criticalLayer(1, Y/N) | 专属化（机台耐久件/端口/关键层） |
| **FsdContamination** | — | outFlag(40), clearFlag(40), forbiddenContaminationFlag(100) | 污染控制（TrackOut 标记/清除/禁止列表） |
| **FsdDcSpec** | — | dcSpecName(40), referNodePosition(40) | DC 规格引用（可跨节点参照） |
| **FsdDcStep** | dcStepName, dcItemName, materialType, productSpecName, mainFlow | dcSiteId, target/usl/lsl/ucl/lcl(Double) | DC 采集步骤规格限 |
| **FsdPilot** | pilotSpecName, startNodePosition, endNodePosition | startType(FIRST_IN_VALID_PERIOD\|AFTER_PM), productSpecName, validStart/EndTime, mandatory, pilotCountLimit, selectedMaxCount, confirmedSpec | 试投规则（生效期/强制/数量限） |
| **FsdSeason** | — | maxProduct, maxIdle, skipShiftSeason(10, M/E/ALL) | 换班/季节控制 |
| **FsdSpcControl** | itemName, controlChartName | lastMonitorDataCount, ruleNames, controlLimitMode, fixedUcl/fixedLcl, baseStart/EndTime | 节点 SPC 控制图配置 |
| **FsdBom** | — | processBomName, machineBomName | 节点 BOM 引用 |
| **FsdReworkCount** | reworkFlowName, reworkFlowRevision | reworkCountLimit | 返工流程次数上限 |
| **FsdNDefault** | **fsdUuid**(自身uuid) | fodUuid(非PK), nodePosition, processOperationName, bomName, dcSpecName, machineCapabilityName, machineGroupName, recipeGroup/Name, referNodePosition, reticleGroup/Name | **节点默认值汇总表**（各类默认值一列一字段） |

### 5.4 FSD 通用服务（AbstractFsdAp）

- `insertByFod / updateByFod / deleteByFod {fodName, fodBind, data}`：按 fodBind 构造 FOD 实体查 PK，不存在则**插入并生成 UUID**（getOrSaveFodData）；FSD 行 setFodUuid；insert/delete 同步维护 UuidFodFsdDef（删 FSD 后该 uuid 无 FSD 则连带删绑定）
- `findAllOnPageByFod`：先查 FOD 行取 uuid 集合，再分页查 FSD
- `importExcelWithFod`（Excel 导入核心）：列头对照 TableColumnDef（FOD 表 pkFlag 列必须存在→COR_00007；FSD 可编辑列必须存在→COR_00008）；按 FOD PK 分组（FSD 列全空的行=仅建锚点）；**先删后插**
- `exportExcelWithFod / exportExcelWithFsd`：FOD 列+FSD 列合并扁平 Excel（按 editMode≠None/Hidden、useFlag=Y 过滤）

### 5.5 FsdModeler 界面（3148 行，双 Tab 表格模型器）

- 布局：左上下双区（上 FOD 多 Tab、下 FSD 多 Tab，Tab=实体表，可拖拽排序/右键关闭）+ 右 FSD 属性表单
- 动线：FOD 下拉（/ModelFodDefAp findAllWithEquals {fodType:'Fod'}）→ 开 FOD Tab → FSD 下拉（/ModelFodFsdDefAp findAllWithEquals {fodName}）→ 开 FSD Tab（按 fodUuid 过滤）→ Add（Copy-Add 或新建；FodTFVN+FsdMachine 预取 ProcessOperationSpec 预填机台组/Recipe/部门）→ 右表单 → OK（insertByFod/updateOnlyBindsByFod）→ 成功后 setExistFod() 刷新 FOD 表 **e 列绿圈**（该锚点已挂此参数）
- FSD Tab Add 按钮：FsdPilot 需流程∈Branch 白名单（viewId=Common_C_BranchFlowList，前端预载 + 后端 BIZ_00216 兜底）；FsdQtime 的 to 节点用 Select 抽屉（viewId=fsdQtime_OperList）；FsdMachine 的 exactMatchRule 用 Setting 抽屉（枚举 ExactMatchRecipeKey 动态生成输入项→JSON）
- FsdMachine 表单联动：fsdRecipeType 切换（GenerateRecipe→禁 recipe*、启命名规则多选 '-' 连接；ExactMatchRecipe→启 Setting、清 recipe*）；machineGroup 变更级联清 machine/chamber/recipe*
- Upload 抽屉 → importExcelWithFod；导出 FOD 侧 exportExcelWithFsd / FSD 侧 exportExcelWithFod

### 5.6 FodValueModeler（FOD 取值 Graph/Grid）

- 三栏：左 FodCode 行表（+flow/rev 过滤；fodFrozenState=Frozen 红行）+ 中 Graph/Grid 双视图 + 右 FodEntry 属性表单
- Graph：载 ProcessFlowSpec.designData 画只读 X6 图，已配置节点标 isTo；点节点→右表单填值→Grid/Graph 同步
- DefaultFind 勾选：Y=只显示已有取值行 / N=全量候选行（findAllForFodEntry + findAllWithEquals 两路查询）
- 行着色：Start/End/Note=灰、Switch=橙、reworkAllowedOnlyStandard/reticleAllowed/branchAllowed=Y 专属色
- 头部按钮：Frozen/UnFrozen/Validate（`/ProcessFlowSpecAp/frozenFod|unFrozenFod|validateFod`；Unfrozen 才可 Frozen，Frozen 才可 UnFrozen/编辑——**参数冻结独立于流程冻结**）
- OK 仅 nodeType∈{Operation, SubFlow} 且未冻结可用

### 5.7 FsdValidationProvider 校验规则清单（代码级）

| # | 规则 | 失败码 |
| --- | --- | --- |
| 1 | 流程有工序节点但查无 FodTFVN 锚点 | FOD_REQUIRED |
| 2 | 流程无节点 | NODE_POSITION_REQUIRED |
| 3 | 逐节点（含 `_` 的跳过）校验：FsdMachine→Contamination→BOM→DcSpec→Dedication | — |
| 4 | 每个激活节点必须有 FsdMachine 行 | FSD_MACHINE_REQUIRED |
| 5 | FsdMachine 行 machineName 空→InValid；machineName=`*` 时校验机台组存在、否则校验机台存在 | FSD_MACHINE_INVALID |
| 6 | FsdDedication：需 (productSpec+criticalLayer) 或 (portType+durableTypeList+durableDetailTypeList) | FSD_Dedication_REQUIRED |
| 7 | FsdDcSpec：dcSpecName 必填 | FSD_DcSpec_REQUIRED |
| 8 | FsdBom：**代码强制 Valid（FORCE_VALID，失败逻辑被注释禁用）** | — |
| 9 | FsdContamination：校验体已被注释，恒过 | — |
| 10 | 全部通过→自动重建 MainProcessFlowOverAllDef | — |

> 注意：参考项目的校验器本身就是半成品（Bom 强制过、Contamination 注释掉）——说明**校验规则是按厂情渐进启用的**，我们的设计要支持规则可配置/可灰度。

---

## 6. 产品规格（ProductSpec）

### 6.1 数据模型：PRODUCT_SPEC（版本化）

| 字段 | 类型 | PK | 值域 | 说明 |
| --- | --- | --- | --- | --- |
| productSpecName | String(40) | ✔ | | 产品规格名 |
| revision | Integer | ✔ | | 版本 |
| processFlowName | String(40) | | | **绑定主流程**（核心语义） |
| productionType | String(40) | | Production/Engineer/Dummy/Sample | 生产属性 |
| productType / productSpecType / productModelType | String(40) | | | 产品类型/规格类型/型号类型 |
| productSize / productThickness(38,2) / productWeight(38,2) | | | | 尺寸/厚度/重量 |
| productQuantity | Integer | | | 基础投片量 |
| customerName | String(40) | | | 客户 |
| npwRecycleCountLimit / npwUsedCountLimit | Integer | | | NPW 回收/使用次数上限 |
| shipBankName / startBankName | String(40) | | | 出货/投入 Bank |

状态机与流程规格同构（Frozen→Active 前置顺序相反：ProductSpec 是 Frozen 才能点 Active）。Duplicate 特殊：**必选目标主流程**（`/ProductSpecAPExt/duplicateProductSpec {productSpec, processFlowName}`）——产品规格复制即「换一条流程跑」。

### 6.2 界面按钮（与流程建模差异点）

- Frozen（CheckIn+NotActive+**Unfrozen**+Release）/ Active（需 **Frozen**）/ NotActive / Archive / ReleaseArchive——同套路
- Duplicate：frozenState==Frozen 才可用；弹窗必选 processFlowName（viewId=Common_C_MainProcessFlowList）
- **View Process Operation**（底部 success）：70% 抽屉看该产品流程的工序清单（viewId=ProductSpec_OperationListAndFsd，可导出）——「产品规格→工序+FSD」一屏透视
- NPW 联动：productionType=NPW 时 processFlowName 下拉切 npwFlow 字典
- 运行时消费：Lot.productSpecName+Revision 引用；Wafer(Product) 再挂 productSpecName

---

## 7. 运行时消费链（建模数据被谁用——完整性视角）

- **Lot**：mainProcessFlowName/Revision、processFlowName/Revision、nodePosition、processOperationName、productSpecName/Revision、reworkCount/State、samplingState、contaminationFlag、machineReticleName、skipAllowed、branchAllowed……
- **Product(Wafer)**：runningHoldStatus（Running Hold 状态）、markId、npRecycleCount/npUsedCount、reworkCount、productGrade、originalProductName/parentProductName（拆分溯源）
- 派工/执行读 FSD：FsdMachine（机台候选池）、FsdQtime（Future Hold）、FsdSampling（抽检）、FsdReticle（光罩指派）、FsdDedication（专属化）、FsdDcStep（DC 采集配置）

---

## 8. 菜单→实体→API 总对照表

| 菜单 | 视图/组件 | tableName | 核心实体 | 特色端点 |
| --- | --- | --- | --- | --- |
| 站点建模 | Modeler | Site/Stage 类 Def | StageDef | 通用套路 |
| 流程建模 | ProcessFlowSpec.vue | ProcessFlowSpec (Revision) | ProcessFlowSpec | duplicateProcessFlow / validation / frozenDesign / updateProcessFlowStage |
| 产品规格 | ProductSpec.vue | ProductSpec | ProductSpec | duplicateProductSpec / View Process Operation 抽屉 |
| 流程设计 | ProcessFlowDesign.vue | — | ProcessFlowSpec.designData + NodePositionDef + NodeStepDef | makeItemTree / findById / insertByDesignForJson / importProcessFlowSpecDesign |
| 流程对比 | ProcessFlowCompare.vue | — | NodePositionDef 对比 | compareTo |
| StageDef | Modeler | StageDef | StageDef | 通用套路 |
| ModelFodCodeDef | Modeler | ModelFodCodeDef | ModelFodCodeDef(+Combine) | 通用套路 |
| ModelFodDef | FodModeler.vue | ModelFodDef 族 | ModelFodDef/EntryGroup/Entry/Code | createFodDef / moveAll |
| ModelFodFsdDef | Modeler + FsdModeler 消费 | ModelFodFsdDef | ModelFodFsdDef / UuidFodFsdDef | 通用套路 |
| FsdModeler | FsdModeler.vue / FodValueModeler.vue | — | FodT* ×15 + Fsd* ×15 + UuidFodFsdDef | insertByFod 族 / importExcelWithFod / frozenFod / validateFod |

下一步：[20-our-design.md](20-our-design.md) —— 我们系统的完整设计（架构选型、ER/DDL、API、校验规则、版本复制规则）。
