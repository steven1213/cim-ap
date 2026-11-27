# 行业模式调研：流程节点参数如何建模（domain_search，2026-11-25）

> 结论先行：**没有跨厂商的「节点参数统一模板」标准**。SEMI 只标准化设备侧对象（E30/E40/E87/E90/E94/E142）与 Recipe/PPID；Route 节点参数（机台指派/Q-time/光罩/返工）是各家**产品级私有模型**，但二十多年已收敛出三种设计模式。

## 1. 各家形态（可查证部分）

| 厂商 | 模式 | 关键机制 | 公开程度 |
| --- | --- | --- | --- |
| 西门子 Camstar（Opcenter Execution Semi） | A 规格引用 | Workflow 步骤**引用 Spec**（可复用规格对象，独立版本化）；Data Collection Definition 独立对象 | 中文建模手册到对象级，字段级未公开 |
| IBM SiView | A 规格引用 | Specification Manager 70+ 类（products/processes/routing/**operation specifics**），SEMATECH CIM Framework 对齐 | 类清单与字段未公开 |
| Applied SmartFactory（FAB300） | C 运行时校验 | 预置工艺场景模板 + **runtime spec validation** | 能力级描述 |
| 上扬 myCIM（国产，最具体） | B 节点参数 | PRP：Q-Time（**跨站正负**）、机台限制、工艺/设备机限、污染等级、Future Action；返工**多层级 Context**（工步/工序/流程/产品/批次）；Recipe 三级 logical→actual；RTL/CMS/EDC 独立主数据，节点只引用 | 官网功能级，字段未公开 |
| 哥瑞利 MESwell | B+C | 48 模块统一数据模型；RMS 独立做 Recipe 版本/合规/下发；RTD 规则（loop Q-time 预测联动） | 功能级 |
| 芯享科技 | C | PPC 参数实时比对（生产过程/设备/批次参数一致性校验） | 功能级 |

## 2. 三种收敛模式

| 模式 | 含义 | 适合谁 |
| --- | --- | --- |
| A. 可复用规格对象 + 流程引用 | 节点=引用(Spec)+位置；Spec 独立版本化 | 大厂多产品共享工艺（Camstar/SiView） |
| B. 节点属性袋 + 强类型参数组 | 节点直接挂 Q-time/机台限制/污染/Future Action/返工层级 | 中小厂快速配置（myCIM/多数国产 MES） |
| C. 规格声明 + 运行时校验引擎 | 参数声明为规则，执行期拦截 | 全自动化高成熟厂（Applied/芯享） |

## 3. 通用抽象（若自研的标准答案）

`Route(版本化) → Step(seq + logical_name，可复用) → {Spec 引用, Recipe 引用, 就地流程控制参数}`；约束统一为 `{类型, 作用域(工步/工序/流程/产品/批次), 参数值, 触发事件, 违规动作}`。

## 4. 实操注意三条（调研明确警示）

1. **Q-time 不能是全局单值**：必须绑定起算/截止事件、正负双方向（myCIM「跨站正负时间限制」）；跨 lot/回路 Q-time 最易错。
2. **槽位/载体归 Container 而非 Route**：slot map 校验是 E87/E90 独立数据流，Route 只引用载体类型或槽位规则。
3. **Recipe 的风险在一致性校验而非绑定**：RMS 金版比对 + PP-SELECT 前校验 + 参数比对（芯享 PPC/哥瑞利 RMS/Camstar 同步）是独立于 Route 的控制链。

## 5. 我们的取舍（详见 20-our-design.md §7）

站 B 为主（强类型+快速配置），吸收 A 的复用精髓（工序模板默认→沿 ContextChain 继承），内置 C 的校验闸（validate_state 硬前置）。**myCIM 的「多层级 Context」已升格为 ICM 一等公民（用户拍板 2026-11-25）**：ContextChain = OPERATION→FLOW→STEP（P3 加 PRODUCT），指派/KV 按 scope 一张表承载全链、解析取最特异层并标注来源。**舍弃**：FSD/FOD 框架（用户拍板）、批次级 Context（MES 运行态）、运行时规则引擎（MES 期再做）。

## 来源

myCIM 官网/软服之家（PRP/RCP/RTL/CMS/EDC 模块描述）、IBM SiView 产品页/SoftwareOne/架构综述、Camstar 工厂建模中文手册、SEMI GEM300 综述（eInnoSys）、Q-time 卡控实践综述。
