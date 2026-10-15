// RMS 控制台共享类型（与 business/rms-ap/server 的 Result/Controller 契约对齐）

export interface MeDto {
  userId: string;
  username: string;
  tenantId: string | null;
  roles: string[];
  permissions: string[];
}

// ---------------------------------------------------------------- 配方

export type RecipeStatus = 'DRAFT' | 'PENDING_SIGN' | 'ACTIVE' | 'OBSOLETE';
/** 配方 Body 格式（须与后端 BodyFormat 枚举严格一致：BINARY / TEXT / JSON_STRUCTURED）。 */
export type BodyFormat = 'TEXT' | 'BINARY' | 'JSON_STRUCTURED';

export interface Recipe {
  id: string;
  code: string;
  name: string;
  deviceTypeId: string | null;
  areaId: string | null;
  golden: boolean;
  activeVersionId: string | null;
  activeVersionNo: number | null;
  versionCount: number;
  parentId: string | null;
  parentCode: string | null;
  recipeLevel: number;
  remark: string | null;
  createTime: string | null;
  updateTime: string | null;
}

export interface RecipeVersion {
  id: string;
  recipeId: string;
  versionNo: number;
  bodyFormat: BodyFormat;
  bodyHash: string;
  bodyLength: number;
  status: RecipeStatus;
  sourceVersionId: string | null;
  changeSummary: string | null;
  remark: string | null;
  createTime: string | null;
}

// ---------------------------------------------------------------- 设备

export type DeviceStatus = 'ENABLED' | 'DISABLED';

export interface DeviceType {
  id: string;
  code: string;
  name: string;
  manufacturer: string | null;
  model: string | null;
  remark: string | null;
}

export interface DeviceArea {
  id: string;
  code: string;
  name: string;
  parentId: string | null;
  parentName: string | null;
  remark: string | null;
}

export interface Device {
  id: string;
  code: string;
  name: string;
  deviceTypeId: string | null;
  deviceTypeName: string | null;
  areaId: string | null;
  areaName: string | null;
  ip: string | null;
  status: DeviceStatus;
  remark: string | null;
}

// ---------------------------------------------------------------- 签核流

export type SignoffStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type SignRoute = 'SINGLE' | 'COUNTERSIGN' | 'ANY';
/** 限时放行派生状态（Req 20）。 */
export type TempAllowStatus = 'NONE' | 'SCHEDULED' | 'ACTIVE' | 'EXPIRED';

export interface Signoff {
  id: string;
  recipeId: string;
  recipeCode: string | null;
  recipeName: string | null;
  versionId: string;
  versionNo: number | null;
  status: SignoffStatus;
  changeSummary: string | null;
  reason: string | null;
  signRoute: SignRoute;
  initiator: string | null;
  approver: string | null;
  decisionComment: string | null;
  createTime: string | null;
  decidedTime: string | null;
  tempAllowStatus: TempAllowStatus;
  tempAllowTo: string | null;
  diffCount: number;
}

export interface SignoffEvent {
  eventType: string;
  actor: string | null;
  comment: string | null;
  detail: string | null;
  occurredAt: string | null;
}

export interface SignoffDetail {
  id: string;
  recipeId: string;
  recipeCode: string | null;
  recipeName: string | null;
  versionId: string;
  versionNo: number | null;
  status: SignoffStatus;
  changeSummary: string | null;
  reason: string | null;
  signRoute: SignRoute;
  initiator: string | null;
  approver: string | null;
  decisionComment: string | null;
  createTime: string | null;
  decidedTime: string | null;
  tempAllowStatus: TempAllowStatus;
  tempAllowFrom: string | null;
  tempAllowTo: string | null;
  tempAllowScope: string | null;
  tempAllowReason: string | null;
  tempAllowBy: string | null;
  tempAllowAt: string | null;
  eSignHash: string | null;
  paramDiff: DiffItem[];
  timeline: SignoffEvent[];
}

export interface SignoffSummary {
  pending: number;
  approvedToday: number;
  rejectedToday: number;
  tempActive: number;
  expiringSoon: number;
}

export interface BatchResult {
  id: string;
  ok: boolean;
  message: string | null;
}

// ---------------------------------------------------------------- 比对

export interface VersionBrief {
  id: string;
  recipeId: string;
  recipeCode: string | null;
  recipeName: string | null;
  versionNo: number;
  status: RecipeStatus;
  bodyFormat: BodyFormat;
  bodyHash: string | null;
  bodyLength: number;
  changeSummary: string | null;
}

/** 比对方式（Req 37/38）。 */
export type CompareMode = 'FULL_BODY' | 'PARAMETER' | 'STRUCTURE';

export interface DiffItem {
  field: string;
  left: string | null;
  right: string | null;
  changed: boolean;
  category: 'BODY' | 'PARAM' | 'STRUCTURE' | null;
}

export interface CompareResult {
  identical: boolean;
  left: VersionBrief;
  right: VersionBrief;
  items: DiffItem[];
}

export interface CompareRun {
  logId: string;
  batchId: string;
  mode: CompareMode;
  result: CompareResult;
}

/** 校验日志行（Req 17）。 */
export interface CompareLog {
  id: string;
  batchId: string | null;
  mode: CompareMode;
  recipeCode: string | null;
  identical: boolean;
  diffCount: number;
  result: string;
  createTime: string | null;
}

export interface CompareLogDetail {
  view: CompareLog;
  items: DiffItem[];
}

export interface CompareLogSummary {
  total: number;
  pass: number;
  fail: number;
  diffTotal: number;
}

// ---------------------------------------------------------------- 审计

export interface AuditRow {
  id: string;
  type: string;
  result: string;
  actor: string | null;
  target: string | null;
  detail: string | null;
  createTime: string | null;
}

export interface AuditTypeCount {
  type: string;
  count: number;
}

export interface AuditSummary {
  total: number;
  success: number;
  failure: number;
  today: number;
  topTypes: AuditTypeCount[];
}

// ---------------------------------------------------------------- 参数规格（FR-S1~S4）
export type SpecType = 'TARGET' | 'RANGE' | 'LIST_IN' | 'TOLERANCE';

export interface ParamSpec {
  id: string;
  recipeId: string;
  paramKey: string;
  paramName: string | null;
  specType: SpecType;
  dataType: string;
  targetValue: string | null;
  rangeMin: string | null;
  rangeMax: string | null;
  listValues: string | null;
  tolerancePct: number | null;
  toleranceAbs: number | null;
  unit: string | null;
  enabled: boolean;
  templateCode: string | null;
}

export interface SpecTemplate {
  id: string;
  code: string;
  name: string;
  templateVer: number;
  scopeType: string | null;
  remark: string | null;
  itemCount: number;
}

export interface SpecTemplateItem {
  id: string;
  paramKey: string;
  paramName: string | null;
  specType: SpecType;
  dataType: string;
  targetValue: string | null;
  rangeMin: string | null;
  rangeMax: string | null;
  listValues: string | null;
  tolerancePct: number | null;
  toleranceAbs: number | null;
  unit: string | null;
  enabled: boolean;
  sortNo: number;
}

export interface SpecCheck {
  paramKey: string;
  expected: string | null;
  actual: string | null;
  diffType: string | null;
}

// ---------------------------------------------------------------- 设备授权（FR-P4）
export interface DeviceAuth {
  id: string;
  subject: string;
  subjectType: string;
  deviceId: string | null;
  deviceLabel: string | null;
  deviceTypeId: string | null;
  areaId: string | null;
  canOperate: boolean;
  createTime: string | null;
}

// ---------------------------------------------------------------- Bypass（FR-B1~B3）
export type BypassScope = 'DEVICE' | 'RECIPE';
export type BypassType = 'COMPARE' | 'PARAM_CHECK' | 'BOTH';

export interface Bypass {
  id: string;
  scope: BypassScope;
  targetId: string;
  targetLabel: string | null;
  bypassType: BypassType;
  reason: string;
  active: boolean;
  status: 'ACTIVE' | 'SCHEDULED' | 'EXPIRED' | 'REVOKED';
  effectiveFrom: string | null;
  effectiveTo: string | null;
  createdBy: string | null;
  createTime: string | null;
}

// ---------------------------------------------------------------- 资格矩阵（FR-Q1~Q4）
export interface Qualification {
  id: string;
  recipeId: string;
  recipeCode: string | null;
  versionPolicy: string;
  versionId: string | null;
  deviceId: string | null;
  deviceCode: string | null;
  deviceTypeId: string | null;
  deviceTypeName: string | null;
  areaId: string | null;
  basis: string | null;
  status: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  createTime: string | null;
}

export interface QualCheck {
  result: string;
  qualificationId: string | null;
  versionPolicy: string | null;
  reason: string | null;
}

// ---------------------------------------------------------------- Golden 机差（FR-G1~G3）
export interface GoldenOverride {
  id: string;
  deviceId: string;
  deviceCode: string | null;
  recipeId: string | null;
  recipeCode: string | null;
  paramKey: string | null;
  allowMin: string | null;
  allowMax: string | null;
  reason: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  approvedBy: string | null;
  approvedAt: string | null;
  createTime: string | null;
}

// ---------------------------------------------------------------- EC/SV（FR-D2~D4）
export interface EcSvSpec {
  id: string;
  scope: 'RECIPE' | 'PRODUCT';
  refId: string;
  ecsvType: 'EC' | 'SV';
  svId: string | null;
  paramKey: string;
  specType: string;
  targetValue: string | null;
  rangeMin: string | null;
  rangeMax: string | null;
  tolerancePct: number | null;
  toleranceAbs: number | null;
  unit: string | null;
  enabled: boolean;
}

export interface EcSvValue {
  id: string;
  deviceId: string;
  ecsvType: string;
  paramKey: string;
  actualValue: string | null;
  specId: string | null;
  result: string;
  detail: string | null;
  capturedAt: string | null;
}

export interface EcSvSummary {
  specs: number;
  pass: number;
  fail: number;
  noSpec: number;
}

// ---------------------------------------------------------------- 集成（IF-E1~M1/A1）
export interface HoldView {
  id: string;
  deviceId: string | null;
  deviceCode: string | null;
  recipeId: string | null;
  recipeCode: string | null;
  reason: string | null;
  source: string;
  active: boolean;
  releasedBy: string | null;
  releasedAt: string | null;
  releaseReason: string | null;
  createTime: string | null;
}

export interface AlarmView {
  id: string;
  alarmCode: string;
  severity: string;
  deviceId: string | null;
  lotId: string | null;
  recipeCode: string | null;
  message: string | null;
  correlationId: string | null;
  acknowledged: boolean;
  occurredAt: string | null;
}

// ---------------------------------------------------------------- 报表（FR-RPT1）
export interface TypeDist {
  name: string;
  count: number;
}

export interface Dashboard {
  recipes: number;
  goldenRecipes: number;
  versions: number;
  activeVersions: number;
  specs: number;
  devices: number;
  pendingSignoff: number;
  comparisons: number;
  comparePass: number;
  compareFail: number;
  comparePassRate: number;
  activeHolds: number;
  unackAlarms: number;
  qualifications: number;
  activeBypasses: number;
  ecsvSpecs: number;
  ecsvPass: number;
  ecsvFail: number;
  auditTotal: number;
  deviceByType: TypeDist[];
}

// ---------------------------------------------------------------- 集成接口（IF-E1~E3 / IF-M1）
export interface OnlineCompareResult {
  correlationId: string | null;
  cmpResult: 'PASS' | 'FAIL' | 'BYPASSED';
  recipeUid: string | null;
  baselineVersion: number | null;
  diffSummary: SpecCheck[];
  holdApplied: boolean;
  alarmRaised: boolean;
  durationMs: number;
  detail: string | null;
}

export interface RunCheckResult {
  checkResult: 'QUALIFIED' | 'NOT_QUALIFIED' | 'HOLD' | 'NOT_FOUND' | 'VERSION_MISMATCH';
  recipeUid: string | null;
  activeVersion: number | null;
  reason: string | null;
  resultAt: string;
}

// ---------------------------------------------------------------- 机台 PPID ↔ 配方映射（D7）
export interface ToolRecipeMap {
  id: string;
  deviceId: string | null;
  deviceCode: string | null;
  deviceTypeId: string | null;
  deviceTypeName: string | null;
  ppidOnTool: string;
  recipeId: string;
  recipeCode: string | null;
  active: boolean;
  createTime: string | null;
}

// ---------------------------------------------------------------- IF-E2 下发任务（阶段机）
export type DownloadStage =
  | 'QUEUED'
  | 'GRANTED'
  | 'SENT_TO_TOOL'
  | 'TOOL_ACKED'
  | 'VERIFIED'
  | 'FAILED';

export interface DownloadJob {
  id: string;
  correlationId: string;
  deviceId: string;
  recipeId: string;
  versionId: string | null;
  ppidOnTool: string | null;
  batchId: string | null;
  lotId: string | null;
  requestor: string | null;
  downloadMode: 'IMMEDIATE' | 'ON_IDLE' | 'BATCH';
  stage: DownloadStage;
  terminal: boolean;
  verified: boolean;
  toolAck: string | null;
  errorDesc: string | null;
  bodyHash: string | null;
  createTime: string | null;
}

export interface BatchProgress {
  batchId: string | null;
  total: number;
  verified: number;
  failed: number;
  pending: number;
}

/** IF-E2 下发受理回执（闸门拒绝时 result=REJECTED 且不建任务）。 */
export interface DownloadReceipt {
  correlationId: string;
  stage: DownloadStage;
  result: 'ACCEPTED' | 'REJECTED';
  recipeUid: string | null;
  version: number | null;
  bodyBase64: string | null;
  toolAck: string | null;
  errorDesc: string | null;
  batchId: string | null;
}

// ------------------------------------------------ GEM 设备事件（SEMI E30）

export type EquipmentEventType = 'PP_CHANGE' | 'PP_VERIFICATION_FAILED';
export type EquipmentChangeType = 'CREATED' | 'EDITED' | 'DELETED';

/** GEM 设备事件（机台主动上报的配方变更 / 机台校验失败）。 */
export interface EquipmentEvent {
  id: string;
  toolId: string;
  deviceCode: string | null;
  eventType: EquipmentEventType;
  ppid: string | null;
  changeType: EquipmentChangeType | null;
  opId: string | null;
  correlationId: string | null;
  occurredAt: string | null;
  handled: boolean;
  handlingResult: string | null;
  driftDetected: boolean;
  alarmRaised: boolean;
  holdApplied: boolean;
  createTime: string | null;
}

// ------------------------------------------------ 机台配方清单（S7F19 核对）

export type ReconcileResult = 'OK' | 'DRIFT' | 'ORPHAN';

/** 机台配方目录里的一条（PPID + 机台回读 HASH）。 */
export interface InventoryItem {
  ppid: string;
  bodyHash?: string | null;
}

/** 机台配方清单核对报告。 */
export interface InventoryReport {
  toolId: string;
  deviceCode: string | null;
  total: number;
  ok: number;
  drift: number;
  orphan: number;
  newlyDetected: number;
  missing: string[];
  reportedAt: string | null;
}

/** 机台配方清单行。 */
export interface InventoryRow {
  id: string;
  deviceId: string;
  deviceCode: string | null;
  ppidOnTool: string;
  toolBodyHash: string | null;
  mappedRecipeId: string | null;
  recipeCode: string | null;
  expectedHash: string | null;
  reconcileResult: ReconcileResult;
  lastSeenAt: string | null;
}

/** 机台配方清单汇总。 */
export interface InventorySummary {
  deviceId: string | null;
  total: number;
  ok: number;
  drift: number;
  orphan: number;
}


// ================================================================ W7：配方 Body 解析

/** 解析出的参数。 */
export interface ParsedParam {
  key: string;
  name: string | null;
  type: string | null;
  value: string | null;
  unit: string | null;
  min: string | null;
  max: string | null;
}

/** 解析出的步序。 */
export interface ParsedStep {
  stepNo: number | null;
  name: string | null;
  remark: string | null;
  params: string[];
}

/** 解析出的子配方。 */
export interface ParsedSubRecipe {
  name: string | null;
  ref: string | null;
  stepNo: number | null;
  params: ParsedParam[];
}

/** 结构化解析结果（对齐 SEMI E139 的 Header + Parameters + SubRecipes 视图）。 */
export interface ParsedRecipe {
  format: string;
  meta: Record<string, string>;
  steps: ParsedStep[];
  params: ParsedParam[];
  subRecipes: ParsedSubRecipe[];
  warnings: string[];
}

/** 版本 Body 解析视图。 */
export interface RecipeParsedView {
  versionId: string;
  bodyFormat: string | null;
  ok: boolean;
  error: string | null;
  paramSnapshot: string | null;
  parsed: ParsedRecipe | null;
}

/** 良构 / 可执行性校验结论。 */
export interface RecipeValidationResult {
  recipeId: string;
  versionId: string;
  wellFormed: boolean;
  executable: boolean;
  errors: string[];
  warnings: string[];
  checks: SpecCheck[];
  parsed: ParsedRecipe | null;
}

// ================================================================ W7：腔体 / 区域树 / 设备视角

/** 机台腔体。 */
export interface DeviceChamber {
  id: string;
  deviceId: string;
  deviceCode: string | null;
  deviceName: string | null;
  code: string;
  name: string;
  sortNo: number;
  remark: string | null;
}

/** 区域树节点（嵌套）。 */
export interface AreaNode {
  id: string;
  code: string;
  name: string;
  parentId: string | null;
  sortNo: number;
  children: AreaNode[];
}

/** 设备视角配方来源。 */
export type DeviceRecipeSource = 'TYPE_APPLICABLE' | 'PPID_MAPPED' | 'QUALIFIED' | 'ON_TOOL';

/** 设备视角配方行（recipeId 为空 = 机台有、RMS 未纳管的孤儿）。 */
export interface DeviceRecipeRow {
  recipeId: string | null;
  code: string | null;
  name: string | null;
  deviceTypeId: string;
  deviceTypeName: string | null;
  activeVersionId: string | null;
  activeVersionNo: number | null;
  versionStatus: string | null;
  bodyHash: string | null;
  sources: DeviceRecipeSource[];
  mapId: string | null;
  ppidOnTool: string | null;
  chamberId: string | null;
  chamberCode: string | null;
  chamberName: string | null;
  qualificationStatus: string | null;
  onToolState: string | null;
}

/** 设备视角汇总。 */
export interface DeviceRecipeView {
  deviceId: string;
  deviceCode: string;
  deviceName: string;
  deviceTypeId: string;
  deviceTypeName: string | null;
  areaId: string | null;
  total: number;
  orphan: number;
  drift: number;
  unqualified: number;
  rows: DeviceRecipeRow[];
}

/** 配方视角反查：配方被装载到哪些机台（W7 体验整合，配方详情抽屉「装载机台」区块）。 */
export interface RecipeToolUsage {
  id: string;
  deviceId: string | null;
  deviceCode: string | null;
  deviceName: string | null;
  ppidOnTool: string;
  chamberId: string | null;
  chamberCode: string | null;
  qualificationStatus: string | null;
  active: boolean;
}

/** 配方导入报告（W8）：逐条独立事务，errors 为「code: 原因」明细。 */
export interface RecipeImportReport {
  created: number;
  updated: number;
  skipped: number;
  errors: string[];
}

// ==================== W9：通知 + 批量操作 ====================

/** 站内通知（收件二选一：recipient 到人 / audience 权限受众广播）。 */
export interface AppNotification {
  id: string;
  type: string;
  title: string;
  content: string | null;
  recipient: string | null;
  audience: string | null;
  refType: string | null;
  refId: string | null;
  read: boolean;
  occurredAt: string;
}

export interface NotificationPage {
  items: AppNotification[];
  unread: number;
  truncated: boolean;
}

export interface BatchItem {
  key: string;
  target: string;
  ok: boolean;
  message: string;
}

export interface BatchOpResult {
  total: number;
  ok: number;
  failed: number;
  items: BatchItem[];
}

export interface BatchDownloadItem {
  toolId: string;
  recipeUid: string;
  version?: number | null;
  ppidOnTool?: string | null;
  lotId?: string | null;
}

// ==================== W10：Body 模板 ====================

export interface BodyTemplate {
  id: string;
  code: string;
  name: string;
  bodyFormat: string;
  scopeDeviceTypeId: string | null;
  enforced: boolean;
}

export interface BodyTemplateItem {
  id: string;
  paramKey: string;
  required: boolean;
  defaultValue: string | null;
  unit: string | null;
  sortNo: number;
}

export interface BodyTemplateDetail extends BodyTemplate {
  templateBody: string | null;
  items: BodyTemplateItem[];
}

export interface BodyTemplateSkeleton {
  id: string;
  code: string;
  bodyFormat: string;
  bodyBase64: string;
  items: BodyTemplateItem[];
}

export interface TemplateViolation {
  templateId: string;
  templateCode: string;
  paramKey: string;
  required: boolean;
  defaultValue: string | null;
  unit: string | null;
  message: string;
}

export interface TemplateUpsertItem {
  paramKey: string;
  required?: boolean;
  defaultValue?: string | null;
  unit?: string | null;
}

export interface TemplateUpsert {
  code: string;
  name: string;
  bodyFormat: string;
  scopeDeviceTypeId?: string | null;
  enforced?: boolean;
  templateBody?: string | null;
  items: TemplateUpsertItem[];
}

export interface PreviewResult {
  deviceTypeId: string | null;
  bodyFormat: string;
  ok: boolean;
  errors: string[];
  warnings: string[];
  templateViolations: TemplateViolation[];
  params: Record<string, string> | null;
}

// ==================== W11：实验通道 + 变更效果 + 解析器 SPI ====================

export interface Experiment {
  id: string;
  code: string;
  recipeId: string;
  recipeCode: string | null;
  versionId: string;
  versionNo: number | null;
  deviceId: string;
  deviceCode: string | null;
  reason: string | null;
  status: 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'CLOSED';
  requestedBy: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  validFrom: string | null;
  validUntil: string | null;
  closedAt: string | null;
  closeReason: string | null;
  expired: boolean;
}

export interface ChangeEffect {
  id: string;
  recipeId: string;
  recipeCode: string | null;
  fromVersionId: string | null;
  fromVersionNo: number | null;
  toVersionId: string;
  toVersionNo: number | null;
  activatedBy: string | null;
  activatedAt: string;
  paramDiffCount: number;
  changeSummary: string | null;
  spcRef: string | null;
}

export interface BodyFormatInfo {
  format: string | null;
  description: string;
}
