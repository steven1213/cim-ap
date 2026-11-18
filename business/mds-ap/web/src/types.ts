// MDS 前端领域类型：与后端 `SiteAdminService.*View` / `MeController.MeDto` 一一对应。
// 后端改了 record 字段这里必须同步（TS 只能保证前端自身一致，跨语言靠人）。

/** 当前用户档案（`GET /api/v1/mds/me`）。 */
export interface MeDto {
  userId: string | null;
  username: string | null;
  tenantId: string | null;
  roles: string[];
  permissions: string[];
}

/** 基地（Site）。`occupied*` 为编辑锁状态（location-design.md §8.1）。 */
export interface Site {
  id: string;
  siteCode: string;
  siteName: string;
  address: string | null;
  contactPerson: string | null;
  contactPhone: string | null;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

/** 晶圆厂（Fab），`siteName` 为后端联表派生。 */
export interface Fab {
  id: string;
  fabCode: string;
  siteCode: string;
  siteName: string | null;
  fabName: string;
  fabType: string | null;
  amhsType: string | null;
  cleanGrade: string | null;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

/** 区域（Area），`fabName` 为后端联表派生。 */
export interface Area {
  id: string;
  areaCode: string;
  fabCode: string;
  fabName: string | null;
  areaName: string;
  areaType: string | null;
  areaGroupId: string | null;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

/** 区域组（AreaGroup）—— Area 的统计口径分类（location-design.md §9.1）。 */
export interface AreaGroup {
  id: string;
  groupCode: string;
  fabCode: string;
  groupName: string;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

/** 湾（Bay），物理作业区；`fabName`/`areaName` 为后端联表派生。 */
export interface Bay {
  id: string;
  bayCode: string;
  fabCode: string;
  fabName: string | null;
  areaCode: string;
  areaName: string | null;
  bayName: string;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

/** 设备位（Stage），Bay 内物理落位；`bayName` 为后端联表派生。 */
export interface Stage {
  id: string;
  stageCode: string;
  fabCode: string;
  bayCode: string;
  bayName: string | null;
  stageName: string;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

/** 写入载荷（新建 / 编辑共用；编码在主标识上不可改，后端会忽略）。 */
export interface SitePayload {
  siteCode: string;
  siteName: string;
  address?: string | null;
  contactPerson?: string | null;
  contactPhone?: string | null;
  remark?: string | null;
  enabled?: boolean;
}

export interface FabPayload {
  fabCode: string;
  siteCode: string;
  fabName: string;
  fabType?: string | null;
  amhsType?: string | null;
  cleanGrade?: string | null;
  remark?: string | null;
  enabled?: boolean;
}

export interface AreaPayload {
  areaCode: string;
  fabCode: string;
  areaName: string;
  areaType?: string | null;
  areaGroupId?: string | null;
  remark?: string | null;
  enabled?: boolean;
}

export interface AreaGroupPayload {
  groupCode: string;
  fabCode: string;
  groupName: string;
  remark?: string | null;
  enabled?: boolean;
}

export interface BayPayload {
  bayCode: string;
  fabCode: string;
  areaCode: string;
  bayName: string;
  remark?: string | null;
  enabled?: boolean;
}

export interface StagePayload {
  stageCode: string;
  fabCode: string;
  bayCode: string;
  stageName: string;
  remark?: string | null;
  enabled?: boolean;
}

/** 编辑锁状态（`GET /locations/{type}/{id}/lock`）。 */
export interface LockState {
  occupied: boolean;
  occupiedBy: string | null;
  occupiedAt: string | null;
}

// ---------------------------------------------------------------- 产品管理域

/** 制程技术（Technology）：产品之上的技术节点分类（全局唯一编码）。 */
export interface Technology {
  id: string;
  techCode: string;
  techName: string;
  techNode: string | null;
  remark: string | null;
  enabled: boolean;
  occupied: boolean;
  occupiedBy: string | null;
}

export interface TechnologyPayload {
  techCode: string;
  techName: string;
  techNode?: string | null;
  remark?: string | null;
  enabled?: boolean;
}

/** 产品（Product）：产品管理域主数据（tech_code 逻辑引用制程技术；类型 PROD=量产/NPW=陪片）。 */
export interface Product {
  id: string;
  productCode: string;
  productName: string;
  techCode: string | null;
  productType: 'PROD' | 'NPW';
  productGroup: string | null;
  waferType: string | null;
  owner: string | null;
  remark: string | null;
  enabled: boolean;
  occupied: boolean;
  occupiedBy: string | null;
}

export interface ProductPayload {
  productCode: string;
  productName: string;
  techCode?: string | null;
  productType?: 'PROD' | 'NPW';
  productGroup?: string | null;
  waferType?: string | null;
  owner?: string | null;
  remark?: string | null;
  enabled?: boolean;
}

// ---------------------------------------------------------------- 设备域

/** 设备大类（行业口径定稿 8 类；页面归属依据：MATERIAL→搬运设备页，其余→设备页）。 */
export type EqKind =
  | 'PROCESS'
  | 'METRO'
  | 'TEST'
  | 'ASSY'
  | 'MATERIAL'
  | 'FACILITY'
  | 'TOOL'
  | 'OTHER';

/** 设备类型（EquipmentType）：ISA-95 Kind→Type—Instance 的「类」，挂大类+细类。 */
export interface EquipmentType {
  id: string;
  typeCode: string;
  typeName: string;
  kindCode: EqKind;
  equipClassCode: string | null;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

export interface EquipmentTypePayload {
  typeCode: string;
  typeName: string;
  kindCode: EqKind;
  equipClassCode?: string | null;
  remark?: string | null;
  enabled?: boolean;
  description?: string | null;
}

/** 设备（Equipment）：实例主数据（一张表，按类型 kind 分页视图）。 */
export interface Equipment {
  id: string;
  equipmentCode: string;
  equipmentName: string;
  typeCode: string;
  typeName: string | null;
  typeKind: string | null;
  locationType: 'FAB' | 'AREA' | 'BAY' | 'STAGE';
  locationId: string;
  stateCode: string | null;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

export interface EquipmentPayload {
  equipmentCode: string;
  equipmentName: string;
  typeCode: string;
  locationType: 'FAB' | 'AREA' | 'BAY' | 'STAGE';
  locationId: string;
  stateCode?: string | null;
  remark?: string | null;
  enabled?: boolean;
  description?: string | null;
}

/** 设备子单元（EquipmentUnit）：设备结构树节点（SEMI E120 口径）。 */
export interface EquipmentUnit {
  id: string;
  equipmentId: string;
  unitCode: string;
  unitName: string;
  unitClass: 'MODULE' | 'SUBSYSTEM';
  unitType: string;
  stateCode: string | null;
  portNo: number | null;
  capacity: number | null;
  enabled: boolean;
  description: string | null;
}

export interface EquipmentUnitPayload {
  unitCode: string;
  unitName: string;
  unitClass: 'MODULE' | 'SUBSYSTEM';
  unitType: string;
  stateCode?: string | null;
  portNo?: number | null;
  capacity?: number | null;
  enabled?: boolean;
  description?: string | null;
}

/** 存储区 Bank：Stocker 内逻辑分区（SEMI E88 zone 口径；BankType=业务用途字典）。 */
export interface StockerBank {
  id: string;
  equipmentId: string;
  equipmentCode: string | null;
  equipmentName: string | null;
  bankCode: string;
  bankName: string;
  bankType: string | null;
  capacity: number | null;
  enabled: boolean;
  occupied: boolean;
  occupiedBy: string | null;
}

export interface StockerBankPayload {
  equipmentId: string;
  bankCode: string;
  bankName: string;
  bankType?: string | null;
  capacity?: number | null;
  enabled?: boolean;
}

/** Bank 间转运规则：from→to allowlist（E88 6.1.1 目的地由 Host 指定；跨 Stocker 允许）。 */
export interface BankTransferRule {
  id: string;
  fromBankId: string;
  toBankId: string;
  priority: number | null;
  enabled: boolean;
}

export interface BankTransferRulePayload {
  fromBankId: string;
  toBankId: string;
  priority?: number | null;
  enabled?: boolean;
}

/** 设备能力（Capability）：能力字典（「能不能做」；recipe 归 MES 不混）。 */
export interface Capability {
  id: string;
  capabilityCode: string;
  capabilityName: string;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

export interface CapabilityPayload {
  capabilityCode: string;
  capabilityName: string;
  remark?: string | null;
  enabled?: boolean;
  description?: string | null;
}

// ---------------------------------------------------------------- 数据字典（平台 sys_dict）

/** 字典（sys_dict）。isSystem=true 的不可删除（种子/内置枚举保护位）。 */
export interface DictInfo {
  id: string;
  code: string;
  name: string | null;
  status: 'ENABLED' | 'DISABLED';
  isSystem: boolean;
  description: string | null;
}

/** 字典明细（sys_dict_item）。itemKey=落库值，itemValue=显示文案。 */
export interface DictItem {
  id: string;
  dictId: string;
  itemKey: string;
  i18nCode: string | null;
  itemValue: string | null;
  sortNo: number | null;
  status: 'ENABLED' | 'DISABLED';
  isSystem: boolean;
}

/** 字典写入载荷（code 建后不可改，编辑时后端忽略）。 */
export interface DictPayload {
  code: string;
  name?: string | null;
  isSystem?: boolean;
  status?: 'ENABLED' | 'DISABLED';
  description?: string | null;
}

/** 字典明细写入载荷（itemKey 改动后端会重查重）。 */
export interface DictItemPayload {
  itemKey: string;
  itemValue?: string | null;
  i18nCode?: string | null;
  sortNo?: number | null;
  status?: 'ENABLED' | 'DISABLED';
  isSystem?: boolean;
}

/** 状态模型（StateModel）：一类对象的状态机注册表（SEMI E30 状态模型方法论口径）。 */
export interface StateModel {
  id: string;
  modelCode: string;
  modelName: string;
  dictCode: string;
  objectType: string;
  unitForm: string | null;
  states: string[];
}

/** 状态转换规则：from→to allowlist 一行（triggerNote=E30「转换=契约」的触发条件）。 */
export interface StateTransition {
  id: string;
  fromState: string;
  toState: string;
  triggerNote: string | null;
}

/** 状态转换矩阵勾选载荷：allowed=true 建/更新，false 取消（后端物理删）。 */
export interface StateTransitionPayload {
  fromState: string;
  toState: string;
  allowed: boolean;
  triggerNote?: string | null;
}

/** 状态模型新建/更新载荷：update 仅 modelName/unitForm/stateCodes 生效（后端裁定）。 */
export interface StateModelPayload {
  modelCode?: string;
  modelName?: string;
  dictCode?: string;
  objectType?: string;
  unitForm?: string | null;
  stateCodes?: string[];
}

/** 编码段配置（规则 segments 列表元素，列表顺序即编码顺序）。 */
export interface NamingSegment {
  type: 'LITERAL' | 'ENUM' | 'DATE' | 'SEQ' | 'VAR';
  /** LITERAL: 常量文本。 */
  text?: string;
  /** ENUM: 上下文变量（须在对象 vars 白名单内）。 */
  var?: string;
  /** ENUM: 平台字典键（与 values 二选一）。 */
  dictKey?: string;
  /** ENUM: 自定义值列表。 */
  values?: string[];
  /** 段字符长度（SEQ=流水宽度必填 1-9；其余段可选，声明后校验等长——整码定长）。 */
  length?: number;
  /** DATE: 格式（yyyyMMdd / yyMMdd 等）。 */
  pattern?: string;
  /** SEQ: 起始值（缺省 1）。 */
  start?: number;
  /** SEQ: 分桶口径（含 BY_DATE 按天重置）。 */
  scope?: string;
}

/** 编码规则（NamingRule）：有序段列表 + 段类型解析器 + 分桶计数器。 */
export interface NamingRule {
  id: string;
  objectType: string;
  ruleName: string;
  segments: NamingSegment[];
  enabled: boolean;
  remark: string | null;
  description: string | null;
}

/** 编码规则对象域声明：object_type → 中文名 / 父对象提示 / 段可用变量。 */
export interface NamingRuleObjectSpec {
  code: string;
  label: string;
  parentHint: string | null;
  vars: string[];
}

/** 编码规则新建/更新载荷：objectType 仅 create 生效（建后不可改）。 */
export interface NamingRulePayload {
  objectType?: string;
  ruleName?: string;
  segments?: NamingSegment[];
  enabled?: boolean;
  remark?: string | null;
  description?: string | null;
}

/** 编码规则试算：ctx 缺的变量回列 missingVars，示例码用 <VAR> 占位。 */
export interface NamingRulePreviewReq {
  objectType: string;
  segments: NamingSegment[];
  ctx?: Record<string, string>;
}

/** 单段试算明细。 */
export interface NamingRulePreviewSegment {
  index: number;
  type: string;
  rendered: string;
  missing: boolean;
}

export interface NamingRulePreviewRes {
  example: string;
  regex: string;
  missingVars: string[];
  segmentResults: NamingRulePreviewSegment[];
}

/** 计数器只读视图（各 SEQ 段各桶当前值）。 */
export interface NamingCounterView {
  segmentIndex: number;
  scopeKey: string;
  currentValue: number;
}

// ================================================================ 制造流程（Flow，Route 口径）

/**
 * 工序生命周期（V20，与 Flow 同款四态）：UNFROZEN 未冻结（草稿）/ FROZEN 已冻结（待激活）/
 * ACTIVE 生效（同族唯一）/ DISABLED 禁用。
 */
export type OperationStatus = 'UNFROZEN' | 'FROZEN' | 'ACTIVE' | 'DISABLED';

/**
 * 工序库主数据（Operation，V20）——P-T-R-O 四层建模 O 层：独立主数据跨路线复用。
 * 术语纪律：Operation = 工序（本类型）；Step = 路线内工序实例（FlowStepView）。
 */
export interface OperationView {
  id: string;
  opCode: string;
  opName: string;
  /** PROCESS 加工 / INSPECT 检验 / REWORK 返工（业务工序三类；结构工件不入库）。 */
  opType: 'PROCESS' | 'INSPECT' | 'REWORK';
  techCode?: string | null;
  status: OperationStatus;
  opFamilyCode?: string | null;
  versionNo?: number | null;
  recipeRef?: string | null;
  equipType?: string | null;
  qtimeMin?: number | null;
  qtimeMax?: number | null;
  reworkLimit?: number | null;
  holdPoint?: boolean | null;
  holdReason?: string | null;
  batchMode?: string | null;
  batchSize?: number | null;
  inspectPlan?: string | null;
  samplingRate?: number | null;
  /** 12 寸预留（建列不启用）：光罩 / CP 测试程序 / 污染互斥组 / 监测片计划 / 腔体约束。 */
  reticleRef?: string | null;
  testProgram?: string | null;
  inhibitGroup?: string | null;
  npwPlan?: string | null;
  chamberSelect?: string | null;
  remark?: string | null;
  description?: string | null;
  /** 是否被路线步骤引用（禁删/禁禁用按钮判据）。 */
  referenced: boolean;
}

/** 工序新建/更新请求（opCode 仅新建生效；建后不可改）。 */
export interface OperationPayload {
  opCode?: string | null;
  opName: string;
  opType: 'PROCESS' | 'INSPECT' | 'REWORK';
  techCode?: string | null;
  recipeRef?: string | null;
  equipType?: string | null;
  qtimeMin?: number | null;
  qtimeMax?: number | null;
  reworkLimit?: number | null;
  holdPoint?: boolean | null;
  holdReason?: string | null;
  batchMode?: string | null;
  batchSize?: number | null;
  inspectPlan?: string | null;
  samplingRate?: number | null;
  remark?: string | null;
  description?: string | null;
}


/**
 * 流程版本生命周期（V14）：UNFROZEN 未冻结（草稿，可编辑）/ FROZEN 已冻结（待激活）/
 * ACTIVE 生效（生产线现行，同族唯一）/ DISABLED 禁用（淘汰）。
 */
export type FlowStatus = 'UNFROZEN' | 'FROZEN' | 'ACTIVE' | 'DISABLED';

/** 流程主档视图（flowXml = 画布 XML 原文件，渲染/编辑的单一事实源）。 */
export interface FlowView {
  id: string;
  flowCode: string;
  flowName: string;
  techCode?: string | null;
  status: FlowStatus;
  flowXml?: string | null;
  remark?: string | null;
  description?: string | null;
  /** 流程种类：MAIN 主流程 / SUB 子流程（仅分类筛选，均可被 SUBFLOW 节点引用）。 */
  flowKind: 'MAIN' | 'SUB';
  stepCount: number;
  /** 版本族（同一套 Flow）；新建 = 自成族。 */
  familyCode?: string | null;
  /** 族内版本号（1..n）。 */
  versionNo?: number | null;
}

/** 解析产物节点视图（stepNo = XML 原顺序；nodeId = 边引用锚点）。 */
export interface FlowStepView {
  id: string;
  nodeId: string;
  stepCode: string;
  stepName?: string | null;
  stepType: string;
  stepNo: number;
  recipeRef?: string | null;
  equipType?: string | null;
  qtimeMin?: number | null;
  qtimeMax?: number | null;
  reworkLimit?: number | null;
  /** SUBFLOW 节点引用的目标流程编码。 */
  flowRef?: string | null;
  /** 批处理模式：SERIAL/PARALLEL/BATCH。 */
  batchMode?: string | null;
  batchSize?: number | null;
  /** Hold 点（关键站后暂停等释放）。 */
  holdPoint?: boolean | null;
  holdReason?: string | null;
  /** 检验计划引用（INSPECT，V14）。 */
  inspectPlan?: string | null;
  /** 抽样率（INSPECT，V14；百分比 0-100）。 */
  samplingRate?: number | null;
}

/** 解析产物边视图（from/to = XML 节点 id）。 */
export interface FlowEdgeView {
  id: string;
  fromStepId: string;
  toStepId: string;
  edgeType: string;
  conditionExpr?: string | null;
}

/** 跨站 Q-time 约束（V14；from/to = XML 节点 id）。 */
export interface FlowQtimeView {
  id: string;
  fromNodeId: string;
  toNodeId: string;
  maxMinutes: number;
  action: 'HOLD' | 'SCRAP';
}

/** 流程详情（主档含 XML + 全部解析产物；画布页一次拉全）。 */
export interface FlowDetail {
  flow: FlowView;
  steps: FlowStepView[];
  edges: FlowEdgeView[];
  qtimes?: FlowQtimeView[];
}

/** 主档新建/更新请求（flowCode 服务端走 NamingRule 占号）。 */
export interface FlowPayload {
  flowName: string;
  techCode?: string | null;
  remark?: string | null;
  description?: string | null;
  flowKind?: 'MAIN' | 'SUB' | null;
}

/** 画布保存请求：整份 XML 回传（单一事实源口径）。 */
export interface FlowCanvasPayload {
  xml: string;
}

/** 图校验结果。 */
export interface FlowValidationResult {
  valid: boolean;
  problems: string[];
}

/** 流程规则视图（V15：锚定版本族 familyCode + versionNo）。 */
export interface FlowRuleView {
  id: string;
  flowId: string;
  familyCode: string;
  versionNo: number;
  ruleCode: string;
  ruleName?: string | null;
  /** BEFORE_STEP_ENTER / AFTER_STEP_COMPLETE / BEFORE_DISPATCH。 */
  joinPoint: string;
  stepCode?: string | null;
  nodeId?: string | null;
  edgeType?: string | null;
  conditionExpr?: string | null;
  /** ALLOW / HOLD / SCRAP / ROUTE_OVERRIDE / INSERT_SEQUENCE。 */
  effect: string;
  effectParam?: string | null;
  priority: number;
  enabled: boolean;
  templateKey?: string | null;
}

/** 规则新建/更新请求。 */
export interface FlowRulePayload {
  ruleCode: string;
  ruleName?: string | null;
  joinPoint: string;
  stepCode?: string | null;
  nodeId?: string | null;
  edgeType?: string | null;
  conditionExpr?: string | null;
  effect: string;
  effectParam?: string | null;
  priority?: number | null;
  enabled?: boolean | null;
}

/** 规则评估请求（vars = 运行期变量，MES/MCS 传入）。 */
export interface FlowRuleEvaluatePayload {
  flowId: string;
  joinPoint: string;
  stepCode?: string | null;
  nodeId?: string | null;
  edgeType?: string | null;
  vars?: Record<string, string | number | boolean> | null;
}

/** 单条命中。 */
export interface FlowRuleHit {
  ruleCode: string;
  ruleName?: string | null;
  effect: string;
  effectParam?: string | null;
  priority: number;
  conditionExpr?: string | null;
  unknownVars: string[];
}

/** 评估决策：ALLOW（含隐式放行）/ HOLD / SCRAP / ROUTE / INSERT。 */
export interface FlowRuleDecision {
  action: string;
  ruleCode?: string | null;
  param?: string | null;
}

export interface FlowRuleEvaluateResult {
  matched: FlowRuleHit[];
  decision: FlowRuleDecision;
}

/** 原因码类型视图（V16：WIP 事务语义域；codeCount 供删除守门提示）。 */
export interface ReasonCodeTypeView {
  id: string;
  typeKey: string;
  typeName: string;
  sortNo: number;
  description?: string | null;
  codeCount: number;
}

/** 类型新建/更新请求（typeKey 建后不可改）。 */
export interface ReasonCodeTypePayload {
  typeKey: string;
  typeName: string;
  sortNo?: number | null;
  description?: string | null;
}

/** 原因码视图（node_path 升序 = 树深度优先展开序）。 */
export interface ReasonCodeView {
  id: string;
  reasonCode: string;
  typeKey: string;
  shortName?: string | null;
  owningDept?: string | null;
  parentId?: string | null;
  parentCode?: string | null;
  nodePath: string;
  levelNo: number;
  enabled: boolean;
  sortNo: number;
  description?: string | null;
}

/** 原因码新建请求（reasonCode 走命名规则守门；typeKey/parentId 建后不可改）。 */
export interface ReasonCodeCreatePayload {
  reasonCode: string;
  typeKey: string;
  shortName?: string | null;
  owningDept?: string | null;
  parentId?: string | null;
  sortNo?: number | null;
  description?: string | null;
  enabled?: boolean | null;
}

/** 原因码更新请求（只吃治理属性与树位）。 */
export interface ReasonCodeUpdatePayload {
  shortName?: string | null;
  owningDept?: string | null;
  parentId?: string | null;
  sortNo?: number | null;
  description?: string | null;
  enabled?: boolean | null;
}

/** WIP 原因事件视图（展示全部走快照字段，不随主数据演进变化）。 */
export interface WipReasonEventView {
  id: string;
  lotId: string;
  waferId?: string | null;
  operationCode?: string | null;
  equipmentCode?: string | null;
  eventType: string;
  reasonId: string;
  occurredAt: string;
  operator?: string | null;
  reasonCodeSnapshot: string;
  reasonTypeSnapshot: string;
  reasonPathSnapshot: string;
  owningDeptSnapshot?: string | null;
  reasonNameSnapshot?: string | null;
}

/** WIP 事件创建请求（occurredAt 空 = 当前时刻）。 */
export interface WipReasonEventPayload {
  lotId: string;
  waferId?: string | null;
  operationCode?: string | null;
  equipmentCode?: string | null;
  eventType: string;
  reasonId: string;
  occurredAt?: string | null;
  operator?: string | null;
}

// ============================================================ 班组排班（V17）

/** 班次定义行。 */
export interface ShiftView {
  id: string;
  shiftCode: string;
  shiftName: string;
  startTime: string;
  endTime: string;
  crossDay: boolean;
  color?: string | null;
  enabled: boolean;
  sortNo: number;
  description?: string | null;
}

/** 班次保存请求（shiftCode 建后不可改）。 */
export interface ShiftPayload {
  shiftCode: string;
  shiftName: string;
  startTime: string;
  endTime: string;
  crossDay: boolean;
  color?: string | null;
  enabled: boolean;
  sortNo: number;
  description?: string | null;
}

/** 班制定义行（detail 已拆数组，第 N 天 → 班次 code）。 */
export interface ShiftPatternView {
  id: string;
  patternCode: string;
  patternName: string;
  cycleDays: number;
  detail: string[];
  enabled: boolean;
  sortNo: number;
  description?: string | null;
}

/** 班制保存请求。 */
export interface ShiftPatternPayload {
  patternCode: string;
  patternName: string;
  cycleDays: number;
  detail: string[];
  enabled: boolean;
  sortNo: number;
  description?: string | null;
}

/** 排班格（周视图单元格）。 */
export interface ShiftAssignmentView {
  id: string;
  workDate: string;
  userId: string;
  username: string;
  shiftCode: string;
  source: string;
  version: number;
}

/** 单格保存请求（shiftCode 空 = 清空该格）。 */
export interface ShiftCellPayload {
  workDate: string;
  userId: string;
  username?: string;
  shiftCode?: string | null;
}

/** 班制铺排请求（一步落库）。 */
export interface ShiftGeneratePayload {
  patternCode: string;
  startDate: string;
  userIds: string[];
  usernames?: Record<string, string>;
}

/** 复制上周请求（两端均为周一）。 */
export interface ShiftCopyPayload {
  fromWeekStart: string;
  toWeekStart: string;
}

/** 选人下拉行（IAM 目录摘要）。 */
export interface ShiftUserOption {
  userId: string;
  username: string;
  enabled: boolean;
}

/** 交接班记录行。 */
export interface ShiftLogView {
  id: string;
  workDate: string;
  shiftCode: string;
  shiftName: string;
  handoverUser: string;
  handoverName?: string | null;
  receiverUser?: string | null;
  receiverName?: string | null;
  equipmentIssue?: string | null;
  pendingItems?: string | null;
  remarks?: string | null;
  acked: boolean;
  ackBy?: string | null;
  ackName?: string | null;
  ackAt?: string | null;
  version: number;
}

/** 交接记录保存请求（workDate 空 = 按当前时刻推算归属）。 */
export interface ShiftLogPayload {
  id?: string | null;
  workDate?: string | null;
  shiftCode: string;
  receiverUser?: string | null;
  receiverName?: string | null;
  equipmentIssue?: string | null;
  pendingItems?: string | null;
  remarks?: string | null;
}

/** 卡控开关行（V18）——一行一个卡控点，MES 消费方按 TTL 缓存拉取决定卡控是否生效。 */
export interface ControlSwitchView {
  id: string;
  controlCode: string;
  domainCode: string;
  name: string;
  enabled: boolean;
  description?: string | null;
}

/** 换班申请行（V19）。 */
export interface ShiftSwapView {
  id: string;
  swapType: 'SWAP' | 'SUBSTITUTE';
  status: 'PENDING' | 'TARGET_CONFIRMED' | 'APPROVING' | 'APPLIED' | 'REJECTED' | 'CANCELLED';
  initiatorId: string;
  initiatorName?: string | null;
  targetId: string;
  targetName?: string | null;
  initiatorWorkDate: string;
  initiatorShiftCode: string;
  targetWorkDate?: string | null;
  targetShiftCode?: string | null;
  reason?: string | null;
  targetConfirmName?: string | null;
  targetConfirmAt?: string | null;
  rejectReason?: string | null;
  appliedAt?: string | null;
  version: number;
}

/** 换班申请请求（SWAP 另需对方班定位；SUBSTITUTE 不填对方班）。 */
export interface ShiftSwapPayload {
  swapType: 'SWAP' | 'SUBSTITUTE';
  targetId: string;
  targetName?: string;
  myWorkDate: string;
  myShiftCode: string;
  targetWorkDate?: string | null;
  targetShiftCode?: string | null;
  reason?: string;
}
