# 工艺流程/产品规格/节点参数 —— 对标重构设计（总览与拍板）

> 文档集导航：
> [10-ref-analysis.md](10-ref-analysis.md) 参考项目深度梳理（实体全字段/按钮全穷举/API 全清单）
> [20-our-design.md](20-our-design.md) 我们的完整设计：**ICM 继承式约束模型（定稿）**（ER/DDL/API/校验/版本复制）
> [50-roadmap.md](50-roadmap.md) 分期实施计划
> [60-industry-patterns.md](60-industry-patterns.md) 行业模式调研（Camstar/SiView/myCIM/Applied/国产）
> 原型：[../web/prototype/flow/index.html](../web/prototype/flow/index.html)（8 屏）
>
> 注：页面规格（字段级）与角色动线（含异常分支）两篇待评审拍板后按 ICM 定稿口径重新生成——
> 此前的旧稿基于被否决的 FSD 式分表设计，已清理，勿再引用。

## 0. 一页结论

**不用 FSD/FOD 体系（用户拍板 2026-11-25）**。行业对标（见 60 文档）：Camstar/SiView「规格引用」、myCIM「节点强类型参数+多层级 Context」、Applied「运行时校验」——共同收敛点是**「工序级复用 + 节点级差异」**。我们据此定稿自有方案 **ICM（继承式约束模型）**，并把 myCIM 的多层级 Context 升格为一等公民（用户拍板 2026-11-25）：**ContextChain = OPERATION 工序默认 → FLOW 流程覆盖 → STEP 节点覆盖（P3 加 PRODUCT）**，解析取最特异层并标注来源层级；指派与 KV 两张表按 scope 承载全链；validate 硬前置生效闸；产品绑定补缺口。基线优势：工序/节点列**逐一同名**（V12-V20 已天然对齐），迁移成本近零。多维评审（20-our-design §8）已修正「工序升版自动跟随」论断：工序引用按版本行钉死保证 WIP 稳定，「跟随」由「刷新工序引用」显式动作 + OP_REF_STALE 预警承担；另定：资格门（equip_type）vs 候选池（assign）分工、uk NULL 陷阱规避（空串占位）、非 UNFROZEN 覆盖数据只读、批量解析端点、多方案=多 family（不加 scenario 轴）。第二轮评审（代码库核实）另定：V22 给 cim_flow 补编辑锁三列（画布 XML 整份回传覆盖是并发高危点，锁管人 vs 人、四态管生命周期，两层独立）；validate/copy 端点复用现有（FlowController 已有，扩展而非新建）；权限码按现网四动作风格（view/create/update/delete，无 :read/:*）；copy 时 rule 不自动复制（V15 显式维护口径）；flow disable 前查 ACTIVE 产品绑定防悬挂。详见 20-our-design §8.1。

## 1. 范围与映射（十个菜单 → 我们）

| 参考菜单 | 我们的对应物 | 处置 |
| --- | --- | --- |
| 站点建模 / StageDef | cim_stage（位置五层已有） | 不动表；流程列表加 Stage 分组筛选（P2） |
| 流程建模 | cim_flow 四态版本族 | 增强：validate_state + 校验服务 + 复制搬运参数 |
| 流程设计 | X6 画布（已有） | 增强：节点参数面板 + 保存校验联动 |
| 流程对比 | **无** | P1 新建（compareTo + 双画布 + 属性 diff） |
| 产品规格 | cim_product（无流程绑定） | P1 新建 cim_product_flow 绑定 + 页面 |
| ModelFodCodeDef / ModelFodDef / ModelFodFsdDef | **不照搬**（选型 C：不引入元数据框架） | 参数族显式建表 + 参数定义页替代 |
| FsdModeler / FodValueModeler | **无** | P1 新建「节点参数维护」双区页 + 画布面板 |
| ImportFsd* 导入向导 | 无 | P2 三段式导入 |
| Hist 历史表 | 平台审计字段 | 评估后 P3（见拍板项 D） |

## 2. 架构定稿（三方案对比 → ICM）

### 方案 A：照搬参考项目元数据框架（FOD/FSD）——**已否决**

动态表（FodT* 15 张）+ ModelFodDef 注册表 + TableColumnDef 动态列 + 通用 Modeler。扩展性极高，但开发成本极高、可维护性差、语义风险大（宽表+动态列跨库迁移困难）。**用户拍板不用（2026-11-25）**。

### 方案 B：全显式分表（每类参数一张表）

类型安全、审计清晰；但长尾参数逐类建表总量大，且**每张表都要独立复制/对比/导入逻辑**，配置心智负担重。

### 方案 C+（定稿）：**ICM 继承式约束模型** = B 的强类型 + A 的复用精髓 + 统一收敛

- **三级 ContextChain 继承**：L0 工序模板（cim_operation，V20 已有全套约束列）→ L1 流程级覆盖（cim_flow/cim_equip_assign scope=FLOW）→ L2 节点覆盖（cim_flow_step 同名列，NULL=继承）——新建流程零覆盖即可通过校验；
- **统一指派表** `cim_equip_assign`（scope=OPERATION/FLOW/STEP 三用）：工序默认指派、流程级覆盖指派与节点覆盖指派同一张表/服务/编辑组件/Excel 模板；
- **长尾 KV**：cim_param_def + cim_constraint_attr（按 ContextChain 泛化，OPERATION/FLOW/STEP 一张表），参数定义页维护；
- **生效闸**：validate_state + 表驱动校验（解析值校验）+ 产品绑定 cim_product_flow。

| 维度 | A（FSD 框架） | B（全显式分表） | **C+（ICM）** |
| --- | --- | --- | --- |
| 开发成本 | 很高 | 中 | **低**（两层列已同名，迁移近零） |
| 配置效率 | 中（逐版本搬运） | 低（每流程逐节点配） | **高**（工序一处配置，全流程继承） |
| 扩展性 | 极高 | 中 | **高**（新参数=定义+KV；高频参数渐进显式化） |
| 可维护性 | 低 | 高 | **高**（显式建模+单一解析器） |
| 与现有平台一致性 | 低 | 高 | **高** |

**ICM 独有增量**（三家对标产品均未作为一等公民呈现）：**ContextChain 三级 scope 一张指派表复用**、来源层级标注（节点覆盖/流程覆盖/工序默认/缺失）、effective 单一解析器四链路共用、工序默认改版自动惠及所有未覆盖节点。详见 [20-our-design.md](20-our-design.md)。

## 3. 拍板项清单（状态更新 2026-11-25）

| # | 拍板项 | 状态/建议 | 备选 |
| --- | --- | --- | --- |
| A | 架构选型 | **已拍板：不用 FSD，ICM 定稿** | — |
| B | 参数冻结粒度 | **已拍板（2026-11-25 用户确认按建议执行）：合并进 Flow 四态（UNFROZEN 全可改、FROZEN 全只读），不加 param_frozen_state** | — |
| C | 产品级参数覆盖（L3） | **已拍板：P3**（ContextChain 已预留 scope=PRODUCT 扩展位；P1 先用 product_flow 绑不同版本表达差异） | — |
| D | Hist 事件历史表 | **已拍板：P3**（平台审计字段先行） | — |
| E | Context 层级与优先级 | **已拍板（2026-11-25）：用上 Context**——ContextChain OPERATION→FLOW→STEP（P3 加 PRODUCT），解析 STEP > FLOW > OPERATION；批次级归 MES 边界外 | — |
| F | 校验规则灰度 | **已拍板：表驱动 rule_code 可启停**（§5 规则注册表） | — |
| G | Stage 分组轴 | **已拍板：P2**（flow.stage_code 引 cim_stage，P1 只建列不填数据） | — |

## 4. 交付节奏

评审通过 → 按 [50-roadmap.md](50-roadmap.md) P1 实施（1 张统一指派表 + 1 张绑定表 + KV 两张 + validate 三列 + 页面/服务/校验）。
