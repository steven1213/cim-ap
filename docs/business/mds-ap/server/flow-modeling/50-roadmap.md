# 分期实施计划

## P1（评审通过后立即实施，目标：核心闭环可用）

| # | 工作项 | 产出 |
| --- | --- | --- |
| 1 | Flyway V22：cim_flow 增强（validate_state 三列一摘要 + cim_flow_validate_result 表 + stage_code + **编辑锁三列**）+ **cim_equip_assign 统一指派表（scope=OPERATION/FLOW/STEP 三用）** + cim_flow_step 补覆盖列（reticle_ref/contam_level/contam_forbidden）+ cim_param_def/cim_constraint_attr + cim_product_flow（h2+mysql 双方言） | 迁移+种子（工序默认指派种子若干，体现继承效果） |
| 2 | 后端：**ContextResolver（ContextChain 解析单一事实源：STEP>FLOW>OPERATION+来源标注）** + FlowValidateService（**扩展现有 FlowAdminService.validateGraph 为表驱动规则，消费解析值，落 validate_state/validate_result**） + FlowCopyService（**扩展现有 copy，搬运 assign/attr**） + EquipAssign/AttrService（OPERATION/FLOW/STEP 三 context 一套服务） + ProductFlowController + ParamDefController + FlowCompareService（按解析值 diff） | API §20-4 |
| 3 | 状态机补丁：activate 校验 VALID（2004）+ **disable 查 ACTIVE 产品绑定（2004）**；copy 联动（NULL 列保持继承；operation_code 原样带过；rule 不自动复制）；删节点级联删 assign/attr | — |
| 4 | 权限三处同步：MdsPermissions + MdsConsoleCatalog + 前端 permCodes（**七组四动作**：opAssign/flowAssign/stepAssign/opAttr/flowAttr/stepAttr/paramDef + productFlow + 特例码 flow:validate） | — |
| 5 | 前端：P01 列表增强（详情加**「流程参数」Tab**=L1 指派/KV 入口 + 「刷新工序引用」按钮 + **编辑锁占用/释放动作**）+ P02 画布参数面板（**来源四级标注：节点覆盖蓝/流程覆盖青/工序默认灰/缺失红 + 一键还原=回落上一层**）+ P03 节点参数维护页（顶部范围切换：本节点/整条流程）+ P04 参数定义页 + P05 对比页 + P06 产品绑定 + P08 校验报告 | 7 个页面 |
| 6 | i18n（mds.flowParam.* / mds.paramDef.* 中英）+ 菜单注册（工艺流程组下加 节点参数/参数定义/流程对比） | — |
| 7 | 测试：ContextChain 解析（STEP/FLOW/OPERATION 各层来源+回落）、三 scope 守门（FLOW 级 step_id 必空、uk 空串占位去重）、资格门交集（ASSIGN_MATCHES_EQUIP_TYPE）、OP_REF_STALE/刷新工序引用、四态只读守门、**编辑锁互斥（画布保存判锁）**、validate 规则逐条、copy 搬运+NULL 保持+operation_code 原样+rule 不复制、**disable 绑定守门**、绑定唯一 ACTIVE、对比 diff——域测试+控制器测试（测试上下文防种子 Runner 干扰，幂等 ensure） | — |

验收标准：动线 A/B/C/D 全程可走通（pw 冒烟截图）；**新建流程拖工序节点零覆盖即可 VALID（继承生效）**；校验 INVALID 能定位节点；activate 被 INVALID 拦截。

## P2（一期验证后）

- Excel 三段导入（P07）+ 模板自动生成；
- cim_flow_qtime 加 timeout_action/alarm_code；step 加 npw 上限（产品域）；rework_count_limit per-返工流程；
- Flow 列表 stage 分组；generateDesign（工序列自动生成直线流程）；
- step_attr 的 param_def.required 强校验升级为 ERROR（灰度开关）。

## P3（评估项）

- 产品级参数覆盖 L3（拍板项 C：equip_assign 扩 scope 或独立覆盖表）；
- Hist 事件历史表（拍板项 D）；
- 通知域联动（validate 失败/绑定变更通知，依赖通知域 backlog）；
- MainProcessFlowOverAll 等价的总览视图（从 steps 实时推导，前端聚合即可，暂不建表）。

## 风险与依赖

| 风险 | 缓解 |
| --- | --- |
| X6 画布加右栏改动面大 | 参数面板挂画布外层 shell（不得放容器内，踩过坑） |
| equip_assign 引用 toolGroup/equipment/equipmentClass 守门 | MDS 内部同库，repository 直查；equipment 删除时级联清理引用（对齐 equipment delete 已有级联删 toolGroupMember 的模式） |
| chamber_rule/recipe_match JSON 校验 | 服务端用 Jackson 解析 + param_def 键集校验 |
| 对比性能（大流程） | 步骤/参数 diff 全内存比对（单流程 steps ≤ 数百，无压力）；画布对照复用只读渲染 |
