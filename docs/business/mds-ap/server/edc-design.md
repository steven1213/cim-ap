# EDC 工程数采设计（V28，2026-11-26 定稿：显式报告模板）

> 边界：MDS 只做「采集契约」静态元数据——采什么、从哪采、按什么规则记录。
> 实时数据接收/存储、OOC/OOS 判定执行、SPC 控制限计算均归运行域（MES/SPC/FDC）。

## 1. 五层模型

| 层 | 对象 | 表 | 语义 |
|---|---|---|---|
| 语义层 | 参数定义 | `cim_param_def`（V28 加 `unit`） | 产品无关的语义锚点：编码/名称/值类型/物理单位。**不带限值**（行业红线：规格限挂「参数×产品×工序」） |
| 语义层 | 参数集 | `cim_edc_param_set` + `_item` | 按工序打包参数（行业 DCP Template 口径）；成员 attr_key 引用参数定义，服务端守门存在且启用 |
| 接口层 | 报告模板 | `cim_edc_report_tpl` + `_item` | **GEM 报告结构显式化**：模板 = 一组「SVID→参数」行；模板与机台/事件解耦，可被多机台、多 CEID 复用（§3） |
| 接口层 | 设备接口映射 | `cim_edc_equip_map` | **机台 × CEID → 报告模板**绑定；通道 GEM\|EDA\|FILE；一个 CEID 可绑多模板（GEM 允许一事件多报告） |
| 聚合层 | EDC 方案 | `cim_edc_plan` + `_item` | 版本化采集契约：绑 Product(可空=工序级通用)+流程族+工序；**规格限（LSL/目标/USL）挂明细行**；OOC 静态响应动作（NONE/HOLD/NOTIFY，执行归运行域） |

方案生命周期镜像流程域：UNFROZEN→FROZEN→ACTIVE→DISABLED；同族唯一 ACTIVE，activate 自动降级旧版；明细仅 UNFROZEN 可维护；freeze 前置明细非空。

## 2. 规格限（LSL ≤ 目标 ≤ USL）的来源与用途

- **存储**：`cim_edc_plan_item.lsl / target / usl`，DECIMAL(18,6)；行内校验 LSL < USL（可单边）。
- **填值入口（界面与模板都有）**：
  1. **Excel 导入**（主动线）：PLAN_ITEM 模板含 `target | lsl | usl` 三列——数据源头是工艺/设备工程师的工艺规格书（spec sheet）；
  2. **UI 逐条录入**：方案明细维护页逐行填写（采集方式/采样规则/规格限/OOC 动作同屏编辑）；
  3. **从参数集带入（applySet）**：只带采集默认属性，**不带限值**（参数集是语义层）——带入后仍需按产品补规格。
- **用途**：OOS 判定的静态依据。运行域（SPC/MES）读方案明细比对实测值；控制限由 SPC 动态计算，**绝不落本表**。
- **变更语义**：规格随方案版本固化（FROZEN 起只读）；改规格 = 建新版本方案 → 冻结 → 发布（自动降级旧版），全程可追溯。

## 3. CEID × SVID 与 SECS/GEM——显式报告模板

### 3.1 GEM 标准链路

设备端上报：**CEID 触发 → Report(RPTID) → 包含多个 SVID**。Host 侧通过
S2F33 定义报告（RPTID 包含哪些 SVID）、S2F35 把报告关联到事件（CEID）。

### 3.2 本模型的立场：显式 Report Template（2026-11-26 用户拍板）

- `cim_edc_report_tpl`（tpl_code/tpl_name/enabled + 编辑锁三列）承载报告结构；
  成员表 `cim_edc_report_tpl_item`（tpl_id × svid 唯一，attr_key 引用参数定义，
  服务端守门存在且启用）。
- `cim_edc_equip_map` 只做绑定：**(equipment_code, ceid, tpl_id)** 唯一；
  绑定守门 = 模板必须存在且**启用**。
- **RPTID 仍不落 MDS**——它是 GEM 会话态标识（每次通讯建立时由 Host 动态分配），
  属运行域。EAP/设备集成层落地 S2F33/S2F35 时按绑定关系推导：
  绑定行 → 一个 RPTID（含模板内全部 SVID）→ 关联到该 CEID。
- 相比初版「(机台,CEID) 隐式分组」的优势：报告结构可复用（一个模板多机台/事件）、
  语义显式（页面直接呈现报告成员）、为将来「同 CEID 按 Recipe 差异化报告」
  留出扩展位（绑定时按条件选不同模板即可，无需改表）。
- **从参数集带入（2026-11-27）**：模板编辑态提供「从参数集带入」——选参数集后
  其成员追加为报告行；SVID 从现有最大值（≥1000）起自动连续分配，
  模板已有参数自动跳过（toast 汇总带入/跳过数）。参数集→报告模板为
  1:N 复用（集合给参数口径，模板另配 SVID）。

## 4. 操作动线

GuideBar 第 4 链（5 步）：**参数定义 → 参数集 → 报告模板 → 设备接口映射 → EDC 方案**。

导入（主动线，逐条新建保留）：五类 KIND——PARAM_DEF（upsert by attr_key）/
PARAM_SET_ITEM（upsert by attr_key）/ PLAN_ITEM（统一校验 = upsert）/
REPORT_ITEM（targetId=模板 id，upsert by svid）/
EQUIP_MAP（列 = equipment_code|ceid|tpl_code|source_channel，upsert by 机台×CEID×模板）；
base64 JSON 通道，逐行校验、好行落库、坏行按行号报告（不整单回滚）。

演示种子 `EdcDemoSeed`（幂等 ensure，重启保持）：10 参数定义 → 2 参数集 →
2 报告模板（RPT-ETCH-PROC 5 SVID / RPT-LITHO-PROC 3 SVID）→ 2 绑定 →
3 方案三态（ACTIVE/FROZEN/UNFROZEN）。

## 5. 明确排除项（归运行域）

实时采集数据接收/存储、OOC/OOS 判定执行、SPC 控制限、RPTID 管理、
型号级模板映射+机台覆盖、wafer 级采样策略。
