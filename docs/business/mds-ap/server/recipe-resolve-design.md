# MDS 配方解算契约设计（V29，2026-11-27 定稿）

> 讨论定稿依据：用户提供的行业分层本体（Process Program → EC Recipe → Wafer Recipe →
> Logical Recipe → Recipe Group）对照本仓库现状逐层归属后收敛。**原则：不重建 RMS 已有的
> 配方真值层，MDS 只补「解算契约」缺口**。

## 1. 分层归属（引用既有，不新建）

| 行业层级 | 归属 | 既有实现（引用） | 本版动作 |
|---|---|---|---|
| Process Program（PPID + Checksum） | **RMS** | `rms_tool_recipe_map.ppid_on_tool`、`recipe_version.body_hash`（SHA-256 对明文算）、`rms_tool_recipe_inventory` 对账、`rms_recipe_download_job` 阶段机 | 不动 |
| EC Recipe（机台×腔体→PPID） | **RMS** | `device_chamber`（V9）+ `tool_recipe_map.chamber_id`（device_id 空=机型通用，即 Model 级默认+机台覆盖）+ `recipe_qualification` 资格矩阵 | 新增只读解析端点（见 §3） |
| Wafer Recipe（片级可变参数） | **MDS 定义 / MES 合并执行** | ICM `ContextChain` 四层（OP→FLOW→STEP→PRODUCT）继承机制；`cim_param_def` 参数字典 | 新增 `cim_operation_variable_param`（见 §4） |
| Logical Recipe（工艺意图） | **MDS** | `cim_operation.recipe_ref` + `ContextResolver`（SRC_PRODUCT/STEP/FLOW/OP 来源标注，`20-our-design.md` §Recipe 三级映射） | 不动，解算时引用 |
| Recipe Group（授权范围） | **组合已覆盖** | `cim_capability`（能力等价）+ `cim_tool_group`（机台组）+ RMS `recipe_qualification`（授权等价） | **不新建实体**；互斥对待 6 寸真实需求确认后排期 |
| 生命周期/审批 | **RMS** | `DRAFT→PENDING_SIGN→ACTIVE→OBSOLETE` + `rms_signoff`（SignRoute COUNTERSIGN 预留外部 BPM）+ EC-SV 限时使用 + `golden` | 不动；approval-AP 接入另议 |
| 自动化准入（AUTO3） | **MES（后续）** | MES-AP 为空骨架；MDS 侧静态资格（capability/equip_type/assign 池）已可用 | 本版只交付解算契约，准入记录随 MES 首批实体落地 |

排除项（与 EDC V28 一致的口径）：实时下发/在线比对归 RMS↔EAP（mq-starter）；RPTID/会话态
归 EAP；Wafer 覆盖值的运行时合并执行归 MES；版本化投影/本地缓存不做（见 §6 演进）。

## 2. 服务间身份与设备对齐（两项拍板约定）

- **服务间鉴权**：复用 IAM 目录 API 的「共享密钥 + 常量时间比较」模式（`DirectoryApiKeyFilter`）。
  RMS 侧新增 `IntegrationApiKeyFilter`：仅拦 `GET /api/v1/rms/integration/recipe-ppid`，
  校验 `X-Integration-Key`（配置 `cim.rms.integration.api-key`，未配置 → 503 安全默认）；
  安全链对该路径 `permitAll` 后由密钥过滤器守门。MDS 侧 `RmsRecipeClient` 携带同名密钥
  （配置 `cim.rms.integration-key`），基地址 `cim.rms.base-url`（默认 `http://localhost:8084`）。
- **设备身份对齐（方案①）**：约定 **`rms_device.code ≡ cim_equipment.equipment_code`**。
  RMS 解析端点按 code 查设备；不存在或非 ENABLED → `DEVICE_NOT_FOUND` / `DEVICE_DISABLED`。
  不做跨库外键、不迁移 RMS 设备域（11 个已应用迁移不动）。

## 3. RMS 解析端点（IF-M2：EC 层翻译）

`GET /api/v1/rms/integration/recipe-ppid?deviceCode=&recipeCode=` → `Result<PpidResolveView>`

```
PpidResolveView = { status, deviceCode, recipeCode, ppid, chamberCode,
                    versionNo, versionStatus, bodyHash, reason, checkedAt }
status ∈ RESOLVED | DEVICE_NOT_FOUND | DEVICE_DISABLED | RECIPE_NOT_FOUND
       | NO_ACTIVE_VERSION | MAPPING_INACTIVE | NOT_QUALIFIED
```

解析顺序：设备（code，须 ENABLED）→ 配方（code）→ 生效版本（`active_version_id` 且 ACTIVE，
取 `body_hash`）→ `tool_recipe_map`（按 recipe_id 查后筛本机：deviceId 精确匹配优先，
机型通用行兜底，取 `ppid_on_tool`+`chamber_id`，无命中 = MAPPING_INACTIVE）→
`QualificationService.check(recipeId, deviceId, versionId)` 非 QUALIFIED = NOT_QUALIFIED。
**只读、幂等、无副作用**（与 IF-M1 Run 校验互补：M2 解算给 PPID，M1 校验给定 PPID 的 Run 资格）。

## 4. MDS 可变参数（cim_operation_variable_param，V29）

挂在 **Logical Recipe 层**（`cim_operation` 版本行）——不是 `cim_equip_assign`（派工/资格层，
语义不合）。字段：

| 列 | 说明 |
|---|---|
| op_id | `cim_operation.id`（版本行 FK） |
| param_key | 引用 `cim_param_def`（存在且启用，服务端守门） |
| default_value | 默认值（VARCHAR，运行时数值语义由 param_def.valueType 决定） |
| min_value / max_value | 允许范围（可空；**范围真值锚点仍是 RMS `param_spec`**，本列为 MDS 侧防呆快照） |
| override_level | 允许覆盖层级：NONE / LOT / WAFER / SLOT（最深允许层） |

维护规则：仅 **UNFROZEN** 版本可编辑（冻结/生效版本的改动走复制新版，与工序主字段同口径）；
整单替换保存（delete→flush→insert，同 EDC tpl items）；uk(op_id, param_key)。

## 5. MDS 解算契约（POST /api/v1/mds/recipe/resolve）

入参 `{ productCode?, flowId, nodeId, equipmentCode, overrides?: [{paramKey, value}] }`

复用 `FlowAdminService.effectiveAll(flowId, productCode)`（ContextChain 整层优先
PRODUCT→STEP→FLOW→OP + assign 池 priority 排序 + 产品轴），取 nodeId 的 `NodeEffective`：

1. **流程闸**：flow 不存在/非 ACTIVE → `VERSION_NOT_ACTIVE`。
2. **节点闸**：nodeId 不在流程内 → `NODE_NOT_FOUND`；候选池空 → `NO_CANDIDATE_POOL`。
3. **机台闸**：池行按 assignType 覆盖判定（EQUIP=assignRef==code、CLASS=classCode==assignRef、
   GROUP=组员含该机台 id），无覆盖行 → `RECIPE_NOT_AUTHORIZED`。
4. **确定性闸**：取覆盖行中 priority 最小层；同层多行且配方口径（rule+ref+match）不一致 →
   `AMBIGUOUS_POOL`（全自动唯一解算红线；validate 侧同口径 WARN 提前暴露）。
5. **配方规则**：
   - `FIXED` → `ppid = assign.recipe_ref`（MDS 直钉，ppidSource=MDS_FIXED）；
   - `GENERATE` → logical recipeRef（`node.recipeRef`，缺 → `RECIPE_REF_MISSING`）交 RMS IF-M2，
     RMS 侧 status 非 RESOLVED 映射为对应 MDS 原因码；
   - `EXACT_MATCH` → `recipe_match` JSON 键值对 overrides 全部命中才放行（放行后同 GENERATE
     走 RMS），否则 `EXACT_MATCH_UNRESOLVED`（v1 简化：匹配域=本次 overrides）。
6. **可变参数闸**：operation 的可变参数逐条取值（override > default）：
   覆盖了未声明可变的参数 → `OVERRIDE_NOT_ALLOWED`；数值范围越界 → `OVERRIDE_OUT_OF_RANGE`。
7. **快照输出**（`resolved=true`）：

```
ResolveView = { resolved, reason?, reasonDetail?,
                productCode, flowId, flowCode, familyCode, flowVersionNo, flowStatus,
                nodeId, stepCode, operationCode, logicalRecipeRef, logicalSource,
                equipmentCode, assignRowId?, assignScope?, assignPriority?, recipeRule?,
                ppid?, ppidSource?(MDS_FIXED|RMS_GENERATED), chamberCode?, rmsVersionNo?,
                rmsVersionStatus?, bodyHash?,
                variableParams: [{paramKey, value, source(DEFAULT|OVERRIDE), minValue, maxValue}],
                resolvedAt }
```

快照是**可比较的执行上下文**：连续批免下载判定 = 相邻 Lot 的
`(logicalRecipeRef, recipeRule, ppid, chamber, variableParams)` 全等；Checksum 熔断锚点 =
`bodyHash`（EAP 读回设备 Body 比对）；追溯链 = 全字段入 MES 执行记录（MES 落库，MDS 不存）。

**候选池判重口径（V30 `uk_ea` 扩列 `recipe_ref`）**：同上下文/类型/引用/优先级但配方口径
（recipe_rule/recipe_ref/recipe_match）不同的行**允许并存**——这正是「歧义池」场景，入库层
不拦，由 validate WARN（`RECIPE_POOL_AMBIGUOUS`）+ 解算 `AMBIGUOUS_POOL` 硬拦兜底；
全等口径的重复行仍由应用层 `assertNotDuplicate` 拒收（GENERATE 行 recipe_ref 为 NULL，
唯一索引对 NULL 互异，判重由应用层负责）。

**原因码（BLOCKED 枚举，供 MES 稳定消费）**：`VERSION_NOT_ACTIVE / NODE_NOT_FOUND /
NO_CANDIDATE_POOL / RECIPE_NOT_AUTHORIZED / AMBIGUOUS_POOL / RECIPE_REF_MISSING /
PPID_MAPPING_INACTIVE / EXACT_MATCH_UNRESOLVED / OVERRIDE_NOT_ALLOWED /
OVERRIDE_OUT_OF_RANGE / RMS_UNAVAILABLE`。HTTP 200 + `resolved=false`（解算本身成功，
答案是"阻断"）；入参缺失走常规 2000 错误。

## 6. 权限 / 前端 / 演进

- 权限：`mds:recipe:resolve`（解算执行）；可变参数维护挂既有 `mds:operation:update`。
- 前端：OperationsPage 详情加「可变参数」成员块（view 表格 / edit 行编辑，仅 UNFROZEN）；
  新页 `/recipe-resolve`（GuideBar 配方链：工序库 → 配方解算）。
- 演进（本期不做）：qualification 变更事件驱动的快照失效；MES 侧自动化准入记录与
  Job/Track-in 实体；EXACT_MATCH 匹配域扩展（前次量测）；Recipe Group 互斥对；
  RMS 签核接 approval-AP；E139 `JSON_STRUCTURED` 参数级比对激活。
- MySQL 持久库注意：新增权限码 seedGrants 只在角色无授权时写入，升级时需手动授权（H2 重建无此问题）。
