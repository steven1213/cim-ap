// RMS 业务 API 封装：配方 + 设备（类型/区域/台账）。
// 所有路径前缀 /api/v1/rms（见 server 各 Controller 的 @RequestMapping）。
import * as api from '@/lib/api';
import type {
  Recipe,
  RecipeVersion,
  DeviceType,
  DeviceArea,
  Device,
  Signoff,
  SignoffDetail,
  SignoffSummary,
  BatchResult,
  CompareResult,
  CompareMode,
  CompareRun,
  CompareLog,
  CompareLogDetail,
  CompareLogSummary,
  AuditRow,
  AuditSummary,
  ParamSpec,
  SpecTemplate,
  SpecTemplateItem,
  SpecCheck,
  DeviceAuth,
  Bypass,
  Qualification,
  QualCheck,
  GoldenOverride,
  EcSvSpec,
  EcSvValue,
  EcSvSummary,
  HoldView,
  AlarmView,
  Dashboard,
  OnlineCompareResult,
  RunCheckResult,
  ToolRecipeMap,
  DownloadJob,
  EquipmentEvent,
  EquipmentEventType,
  EquipmentChangeType,
  InventoryReport,
  InventoryRow,
  InventorySummary,
  InventoryItem,
  BatchProgress,
  DownloadReceipt,
  AreaNode,
  DeviceChamber,
  DeviceRecipeView,
  RecipeParsedView,
  RecipeValidationResult,
  RecipeToolUsage,
  RecipeImportReport,
  BodyFormat,
  NotificationPage,
  BatchOpResult,
  BatchDownloadItem,
  BodyTemplate,
  BodyTemplateDetail,
  BodyTemplateSkeleton,
  TemplateUpsert,
  PreviewResult,
  Experiment,
  ChangeEffect,
  BodyFormatInfo,
} from '@/types';

/** UTF-8 安全的文本→Base64（btoa 仅支持 Latin1，多字节需先编码）。 */
function utf8ToBase64(s: string): string {
  return btoa(unescape(encodeURIComponent(s)));
}

// ------------------------------------------------ 配方

export function listRecipes(params: {
  keyword?: string;
  deviceTypeId?: string;
  areaId?: string;
  golden?: boolean;
} = {}): Promise<Recipe[]> {
  return api.get<Recipe[]>('/rms/recipes', params);
}

export function getRecipe(id: string): Promise<Recipe> {
  return api.get<Recipe>(`/rms/recipes/${id}`);
}

export function createRecipe(body: {
  code: string;
  name: string;
  deviceTypeId?: string | null;
  areaId?: string | null;
  parentId?: string | null;
  golden?: boolean;
  remark?: string | null;
  bodyFormat: string;
  body: string;
  expectedBodyHash?: string | null;
}): Promise<Recipe> {
  // 后端 CreateReq 收 bodyBase64 + description（对原始文本做 UTF-8 Base64）；
  // expectedBodyHash 由调用方按原始字节 SHA-256 计算。
  const { body: rawBody, remark, ...rest } = body;
  return api.post<Recipe>('/rms/recipes', {
    ...rest,
    description: remark ?? null,
    bodyBase64: rawBody ? utf8ToBase64(rawBody) : null,
  });
}

export function updateRecipe(
  id: string,
  body: Partial<Pick<Recipe, 'name' | 'golden' | 'remark' | 'parentId'>>,
): Promise<Recipe> {
  const { remark, ...rest } = body;
  return api.put<Recipe>(`/rms/recipes/${id}`, { ...rest, description: remark ?? null });
}

export function deleteRecipe(id: string): Promise<void> {
  return api.del<void>(`/rms/recipes/${id}`);
}

export function listVersions(recipeId: string): Promise<RecipeVersion[]> {
  return api.get<RecipeVersion[]>(`/rms/recipes/${recipeId}/versions`);
}

export function newVersion(
  recipeId: string,
  body: {
    bodyFormat: string;
    body: string;
    expectedBodyHash?: string | null;
    remark?: string | null;
  },
): Promise<RecipeVersion> {
  const { body: rawBody, remark, ...rest } = body;
  return api.post<RecipeVersion>(`/rms/recipes/${recipeId}/versions`, {
    ...rest,
    changeSummary: remark ?? null,
    bodyBase64: rawBody ? utf8ToBase64(rawBody) : null,
  });
}

export function saveAs(
  recipeId: string,
  body: { code: string; name: string; expectedBodyHash?: string | null; remark?: string | null },
): Promise<Recipe> {
  return api.post<Recipe>(`/rms/recipes/${recipeId}/copy`, body);
}

export function activateVersion(recipeId: string, versionId: string): Promise<void> {
  return api.post<void>(`/rms/recipes/${recipeId}/versions/${versionId}/activate`);
}

// ------------------------------------------------ 设备类型

export function listDeviceTypes(params: { keyword?: string } = {}): Promise<DeviceType[]> {
  return api.get<DeviceType[]>('/rms/device-types', params);
}

export function createDeviceType(body: {
  code: string;
  name: string;
  manufacturer?: string | null;
  model?: string | null;
  remark?: string | null;
}): Promise<DeviceType> {
  const { remark, ...rest } = body;
  return api.post<DeviceType>('/rms/device-types', { ...rest, description: remark ?? null });
}

export function updateDeviceType(
  id: string,
  body: Partial<Pick<DeviceType, 'name' | 'manufacturer' | 'model' | 'remark'>>,
): Promise<DeviceType> {
  const { remark, ...rest } = body;
  return api.put<DeviceType>(`/rms/device-types/${id}`, { ...rest, description: remark ?? null });
}

export function deleteDeviceType(id: string): Promise<void> {
  return api.del<void>(`/rms/device-types/${id}`);
}

// ------------------------------------------------ 设备区域

export function listDeviceAreas(params: { keyword?: string } = {}): Promise<DeviceArea[]> {
  return api.get<DeviceArea[]>('/rms/device-areas', params);
}

export function createDeviceArea(body: {
  code: string;
  name: string;
  parentId?: string | null;
  remark?: string | null;
}): Promise<DeviceArea> {
  const { remark, ...rest } = body;
  return api.post<DeviceArea>('/rms/device-areas', { ...rest, description: remark ?? null });
}

export function updateDeviceArea(
  id: string,
  body: Partial<Pick<DeviceArea, 'name' | 'parentId' | 'remark'>>,
): Promise<DeviceArea> {
  const { remark, ...rest } = body;
  return api.put<DeviceArea>(`/rms/device-areas/${id}`, { ...rest, description: remark ?? null });
}

export function deleteDeviceArea(id: string): Promise<void> {
  return api.del<void>(`/rms/device-areas/${id}`);
}

// ------------------------------------------------ 设备台账

export function listDevices(params: {
  keyword?: string;
  deviceTypeId?: string;
  areaId?: string;
  status?: string;
} = {}): Promise<Device[]> {
  return api.get<Device[]>('/rms/devices', params);
}

export function createDevice(body: {
  code: string;
  name: string;
  deviceTypeId?: string | null;
  areaId?: string | null;
  ip?: string | null;
  status?: string;
  remark?: string | null;
}): Promise<Device> {
  const { remark, ...rest } = body;
  return api.post<Device>('/rms/devices', { ...rest, description: remark ?? null });
}

export function updateDevice(
  id: string,
  body: Partial<Pick<Device, 'name' | 'deviceTypeId' | 'areaId' | 'ip' | 'status' | 'remark'>>,
): Promise<Device> {
  const { remark, ...rest } = body;
  return api.put<Device>(`/rms/devices/${id}`, { ...rest, description: remark ?? null });
}

export function deleteDevice(id: string): Promise<void> {
  return api.del<void>(`/rms/devices/${id}`);
}

// ------------------------------------------------ 签核流

export function listSignoffs(params: { status?: string; keyword?: string } = {}): Promise<Signoff[]> {
  return api.get<Signoff[]>('/rms/signoffs', params);
}

export function getSignoffSummary(): Promise<SignoffSummary> {
  return api.get<SignoffSummary>('/rms/signoffs/summary');
}

export function getSignoff(id: string): Promise<SignoffDetail> {
  return api.get<SignoffDetail>(`/rms/signoffs/${id}`);
}

export function submitSignoff(body: {
  recipeId: string;
  versionId: string;
  reason?: string;
  signRoute?: string;
}): Promise<SignoffDetail> {
  return api.post<SignoffDetail>('/rms/signoffs', body);
}

export function approveSignoff(id: string, comment?: string): Promise<Signoff> {
  return api.post<Signoff>(`/rms/signoffs/${id}/approve`, { comment: comment ?? null });
}

export function rejectSignoff(id: string, comment?: string): Promise<Signoff> {
  return api.post<Signoff>(`/rms/signoffs/${id}/reject`, { comment: comment ?? null });
}

export function batchDecision(
  ids: string[],
  decision: 'APPROVE' | 'REJECT',
  comment?: string,
): Promise<BatchResult[]> {
  return api.post<BatchResult[]>('/rms/signoffs/batch-decision', { ids, decision, comment: comment ?? null });
}

export function presetTempAllow(
  id: string,
  body: { from?: string | null; to: string; scope?: string | null; reason: string },
): Promise<Signoff> {
  return api.post<Signoff>(`/rms/signoffs/${id}/temp-allow`, body);
}

export function cancelTempAllow(id: string, reason?: string): Promise<Signoff> {
  return api.post<Signoff>(`/rms/signoffs/${id}/temp-cancel`, { reason: reason ?? null });
}

// ------------------------------------------------ 比对中心

export function compareVersions(
  leftVersionId: string,
  rightVersionId: string,
  mode?: CompareMode,
): Promise<CompareResult> {
  return api.get<CompareResult>('/rms/compares/versions',
    { leftVersionId, rightVersionId, ...(mode ? { mode } : {}) });
}

export function runCompare(body: {
  baselineVersionId: string;
  targetVersionId: string;
  mode?: CompareMode;
}): Promise<CompareRun> {
  return api.post<CompareRun>('/rms/compares/run', body);
}

export function batchCompare(body: {
  baselineVersionId: string;
  targetVersionIds: string[];
  mode?: CompareMode;
}): Promise<CompareRun[]> {
  return api.post<CompareRun[]>('/rms/compares/batch', body);
}

export function listCompareLogs(params: { result?: string; limit?: number } = {}): Promise<CompareLog[]> {
  return api.get<CompareLog[]>('/rms/compares/logs', params);
}

export function compareLogSummary(): Promise<CompareLogSummary> {
  return api.get<CompareLogSummary>('/rms/compares/logs/summary');
}

export function compareLogDetail(id: string): Promise<CompareLogDetail> {
  return api.get<CompareLogDetail>(`/rms/compares/logs/${id}`);
}

// ------------------------------------------------ 审计

export function listAudit(params: {
  type?: string;
  result?: string;
  actor?: string;
  from?: string;
  to?: string;
  keyword?: string;
  limit?: number;
} = {}): Promise<AuditRow[]> {
  return api.get<AuditRow[]>('/rms/audit', params);
}

export function auditSummary(): Promise<AuditSummary> {
  return api.get<AuditSummary>('/rms/audit/summary');
}

export function recipeHistory(params: {
  code: string;
  from?: string;
  to?: string;
  max?: number;
}): Promise<AuditRow[]> {
  return api.get<AuditRow[]>('/rms/audit/recipe-history', params);
}

// ------------------------------------------------ 参数规格 / 模板（FR-S1~S4）

export function listSpecs(recipeId: string): Promise<ParamSpec[]> {
  return api.get<ParamSpec[]>('/rms/specs', { recipeId });
}

export function upsertSpec(recipeId: string, body: Partial<ParamSpec>): Promise<ParamSpec> {
  return api.post<ParamSpec>(`/rms/specs/recipes/${recipeId}`, body);
}

export function setSpecEnabled(id: string, enabled: boolean): Promise<ParamSpec> {
  return api.put<ParamSpec>(`/rms/specs/${id}/enabled?enabled=${enabled}`, {});
}

export function deleteSpec(id: string): Promise<void> {
  return api.del<void>(`/rms/specs/${id}`);
}

export function listSpecTemplates(): Promise<SpecTemplate[]> {
  return api.get<SpecTemplate[]>('/rms/specs/templates');
}

export function listSpecTemplateItems(id: string): Promise<SpecTemplateItem[]> {
  return api.get<SpecTemplateItem[]>(`/rms/specs/templates/${id}/items`);
}

export function createSpecTemplate(body: any): Promise<SpecTemplate> {
  return api.post<SpecTemplate>('/rms/specs/templates', body);
}

export function updateSpecTemplate(id: string, body: any): Promise<SpecTemplate> {
  return api.put<SpecTemplate>(`/rms/specs/templates/${id}`, body);
}

export function deleteSpecTemplate(id: string): Promise<void> {
  return api.del<void>(`/rms/specs/templates/${id}`);
}

export function applySpecTemplate(recipeId: string, templateCode: string): Promise<number> {
  return api.post<number>('/rms/specs/apply-template', { recipeId, templateCode });
}

export function inheritSpec(targetRecipeId: string, sourceRecipeId: string): Promise<number> {
  return api.post<number>('/rms/specs/inherit', { targetRecipeId, sourceRecipeId });
}

export function checkSpecs(recipeId: string, params: Record<string, string>): Promise<SpecCheck[]> {
  return api.post<SpecCheck[]>('/rms/specs/check', { recipeId, params });
}

// ------------------------------------------------ 设备授权（FR-P4）

export function listDeviceAuth(params: { subject?: string } = {}): Promise<DeviceAuth[]> {
  return api.get<DeviceAuth[]>('/rms/device-auth', params);
}

export function grantDeviceAuth(body: {
  subject: string;
  subjectType?: string;
  deviceId?: string | null;
  deviceTypeId?: string | null;
  areaId?: string | null;
  canOperate?: boolean;
}): Promise<DeviceAuth> {
  return api.post<DeviceAuth>('/rms/device-auth', body);
}

export function revokeDeviceAuth(id: string): Promise<void> {
  return api.del<void>(`/rms/device-auth/${id}`);
}

// ------------------------------------------------ Bypass（FR-B1~B3）

export function listBypasses(): Promise<Bypass[]> {
  return api.get<Bypass[]>('/rms/bypasses');
}

export function createBypass(body: {
  scope: string;
  targetId: string;
  bypassType: string;
  reason: string;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
}): Promise<Bypass> {
  return api.post<Bypass>('/rms/bypasses', body);
}

export function setBypassActive(id: string, active: boolean): Promise<Bypass> {
  return api.put<Bypass>(`/rms/bypasses/${id}/active?active=${active}`, {});
}

export function deleteBypass(id: string): Promise<void> {
  return api.del<void>(`/rms/bypasses/${id}`);
}

// ------------------------------------------------ 资格矩阵（FR-Q1~Q4）

export function listQualifications(params: { recipeId?: string; deviceId?: string } = {}): Promise<Qualification[]> {
  return api.get<Qualification[]>('/rms/qualifications', params);
}

export function grantQualification(body: {
  recipeId: string;
  versionPolicy?: string;
  versionId?: string | null;
  deviceId?: string | null;
  deviceTypeId?: string | null;
  areaId?: string | null;
  basis?: string | null;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
}): Promise<Qualification> {
  return api.post<Qualification>('/rms/qualifications', body);
}

export function revokeQualification(id: string): Promise<Qualification> {
  return api.post<Qualification>(`/rms/qualifications/${id}/revoke`);
}

export function deleteQualification(id: string): Promise<void> {
  return api.del<void>(`/rms/qualifications/${id}`);
}

export function checkQualification(recipeId: string, deviceId: string): Promise<QualCheck> {
  return api.get<QualCheck>('/rms/qualifications/check', { recipeId, deviceId });
}

// ------------------------------------------------ Golden 机差（FR-G1~G3）

export function listGoldenOverrides(params: { deviceId?: string; status?: string } = {}): Promise<GoldenOverride[]> {
  return api.get<GoldenOverride[]>('/rms/golden-overrides', params);
}

export function submitGoldenOverride(body: {
  deviceId: string;
  recipeId?: string | null;
  paramKey: string;
  allowMin?: string | null;
  allowMax?: string | null;
  reason?: string | null;
}): Promise<GoldenOverride> {
  return api.post<GoldenOverride>('/rms/golden-overrides', body);
}

export function approveGoldenOverride(id: string, comment?: string): Promise<GoldenOverride> {
  return api.post<GoldenOverride>(`/rms/golden-overrides/${id}/approve`, { comment: comment ?? null });
}

export function rejectGoldenOverride(id: string, comment?: string): Promise<GoldenOverride> {
  return api.post<GoldenOverride>(`/rms/golden-overrides/${id}/reject`, { comment: comment ?? null });
}

export function deleteGoldenOverride(id: string): Promise<void> {
  return api.del<void>(`/rms/golden-overrides/${id}`);
}

// ------------------------------------------------ EC/SV（FR-D2~D4）

export function listEcSvSpecs(params: { scope?: string; refId?: string } = {}): Promise<EcSvSpec[]> {
  return api.get<EcSvSpec[]>('/rms/ecsv/specs', params);
}

export function saveEcSvSpec(body: Partial<EcSvSpec>): Promise<EcSvSpec> {
  return api.post<EcSvSpec>('/rms/ecsv/specs', body);
}

export function updateEcSvSpec(id: string, body: Partial<EcSvSpec>): Promise<EcSvSpec> {
  return api.put<EcSvSpec>(`/rms/ecsv/specs/${id}`, body);
}

export function deleteEcSvSpec(id: string): Promise<void> {
  return api.del<void>(`/rms/ecsv/specs/${id}`);
}

export function ecSvSummary(): Promise<EcSvSummary> {
  return api.get<EcSvSummary>('/rms/ecsv/summary');
}

export function listEcSvValues(params: { deviceId?: string; limit?: number } = {}): Promise<EcSvValue[]> {
  return api.get<EcSvValue[]>('/rms/ecsv/values', params);
}

export function uploadEcSv(body: {
  deviceId: string;
  scope: string;
  refId: string;
  ecsvType: string;
  values: Record<string, string>;
}): Promise<{ total: number; fail: number; items: EcSvValue[] }> {
  return api.post('/rms/ecsv/upload', body);
}

// ------------------------------------------------ 集成（IF-E1~M1/A1）

export function listHolds(params: { activeOnly?: boolean } = {}): Promise<HoldView[]> {
  return api.get<HoldView[]>('/rms/integration/holds', params);
}

export function releaseHold(id: string, reason: string): Promise<HoldView> {
  return api.post<HoldView>(`/rms/integration/holds/${id}/release`, { reason });
}

export function listAlarms(params: { unackOnly?: boolean } = {}): Promise<AlarmView[]> {
  return api.get<AlarmView[]>('/rms/integration/alarms', params);
}

export function ackAlarm(id: string): Promise<AlarmView> {
  return api.post<AlarmView>(`/rms/integration/alarms/${id}/ack`);
}

export function alarmCodes(): Promise<Record<string, string>> {
  return api.get<Record<string, string>>('/rms/integration/alarm-codes');
}

// ------------------------------------------------ 机台 PPID ↔ 配方映射（D7 / IF-E2）

export function listPpidMaps(deviceId?: string): Promise<ToolRecipeMap[]> {
  return api.get<ToolRecipeMap[]>('/rms/integration/ppid-maps', deviceId ? { deviceId } : undefined);
}

export function registerPpidMap(body: {
  deviceId?: string | null;
  deviceTypeId?: string | null;
  ppidOnTool: string;
  recipeId: string;
  chamberId?: string | null;
}): Promise<ToolRecipeMap> {
  return api.post<ToolRecipeMap>('/rms/integration/ppid-maps', body);
}

export function deletePpidMap(id: string): Promise<void> {
  return api.del<void>(`/rms/integration/ppid-maps/${id}`);
}

// ------------------------------------------------ IF-E2 下发任务（阶段机回执闭环）

export function listDownloads(params: {
  stage?: string;
  batchId?: string;
  deviceId?: string;
  pendingOnly?: boolean;
} = {}): Promise<DownloadJob[]> {
  return api.get<DownloadJob[]>('/rms/integration/downloads', params);
}

export function downloadBatchProgress(batchId: string): Promise<BatchProgress> {
  return api.get<BatchProgress>('/rms/integration/downloads/batch-progress', { batchId });
}

/** 下发（RMS → EAP → 机台），返回受理回执（stage 起点 QUEUED）。 */
export function downloadRecipe(body: {
  toolId: string;
  ppidOnTool?: string | null;
  recipeUid: string;
  version?: number | null;
  downloadMode?: 'IMMEDIATE' | 'ON_IDLE' | 'BATCH';
  batchId?: string | null;
  lotId?: string | null;
  requestor?: string | null;
  correlationId?: string | null;
}): Promise<DownloadReceipt> {
  return api.post<DownloadReceipt>('/rms/integration/download', body);
}

/** EAP 回执：推进下发阶段（非法迁移会被后端拒为 400）。 */
export function ackDownload(
  correlationId: string,
  body: { stage: string; toolAck?: string | null; errorDesc?: string | null; readbackHash?: string | null },
): Promise<DownloadJob> {
  return api.post<DownloadJob>(
    `/rms/integration/downloads/${encodeURIComponent(correlationId)}/ack`,
    body,
  );
}

export function onlineCompare(body: {
  toolId: string;
  ppidOnTool?: string | null;
  recipeUid?: string | null;
  bodyFormat?: string | null;
  bodyBase64?: string | null;
  bodyHash?: string | null;
  cmpMode?: string;
  baselineType?: string;
  baselineVersionId?: string | null;
  paramSnapshot?: string | null;
  lotId?: string | null;
  correlationId?: string | null;
}): Promise<OnlineCompareResult> {
  return api.post<OnlineCompareResult>('/rms/integration/online-compare', body);
}

export function runCheck(body: {
  toolId: string;
  recipeUid?: string | null;
  ppidOnTool?: string | null;
  versionPolicy?: string;
  exactVersionId?: string | null;
  lotId?: string | null;
  verifyRequired?: boolean;
}): Promise<RunCheckResult> {
  return api.post<RunCheckResult>('/rms/integration/run-check', body);
}

// ------------------------------------------------ 报表（FR-RPT1）

export function dashboard(): Promise<Dashboard> {
  return api.get<Dashboard>('/rms/reports/dashboard');
}

// ------------------------------------------------ Body 受控查看（FR-SEC2）

export function getVersionSnapshot(versionId: string): Promise<{ versionId: string; paramSnapshot: string | null; bodyHash: string | null }> {
  return api.get(`/rms/versions/${versionId}/snapshot`);
}

export function getVersionBody(versionId: string): Promise<{ versionId: string; bodyFormat: string | null; bodyHash: string | null; bodyLength: number; bodyBase64: string | null }> {
  return api.get(`/rms/versions/${versionId}/body`);
}

// ------------------------------------------------ GEM 设备事件（SEMI E30 PPChange / PPVerificationFailed）

/** 接收 GEM 设备事件（EAP → RMS）。同一 eventId 重复上报会被幂等回放。 */
export function postEquipmentEvent(body: {
  eventId?: string | null;
  toolId: string;
  eventType: EquipmentEventType;
  ppid?: string | null;
  changeType?: EquipmentChangeType | null;
  opId?: string | null;
  correlationId?: string | null;
  payload?: string | null;
  errorDesc?: string | null;
  occurredAt?: string | null;
  holdOnDrift?: boolean | null;
}): Promise<EquipmentEvent> {
  return api.post<EquipmentEvent>('/rms/integration/equipment-events', body);
}

export function listEquipmentEvents(params: { toolId?: string; eventType?: string } = {}): Promise<EquipmentEvent[]> {
  return api.get<EquipmentEvent[]>('/rms/integration/equipment-events', params);
}

// ------------------------------------------------ 机台配方清单（S7F19 目录核对）

/** 上报机台配方目录（EAP 从 S7F19/F20 取回后上报），返回核对报告。 */
export function reportToolInventory(body: {
  toolId: string;
  correlationId?: string | null;
  seenAt?: string | null;
  items: InventoryItem[];
}): Promise<InventoryReport> {
  return api.post<InventoryReport>('/rms/integration/tool-inventory', body);
}

export function listToolInventory(params: { deviceId?: string; result?: string } = {}): Promise<InventoryRow[]> {
  return api.get<InventoryRow[]>('/rms/integration/tool-inventory', params);
}

export function toolInventorySummary(deviceId?: string): Promise<InventorySummary> {
  return api.get<InventorySummary>('/rms/integration/tool-inventory/summary', deviceId ? { deviceId } : undefined);
}

// ------------------------------------------------ W7：设备视角 / 腔体 / 配方解析

/** 区域树（嵌套），供设备视角左侧导航渲染。 */
export function areaTree(): Promise<AreaNode[]> {
  return api.get<AreaNode[]>('/rms/device-areas/tree');
}

export function listChambers(deviceId?: string): Promise<DeviceChamber[]> {
  return api.get<DeviceChamber[]>('/rms/device-chambers', deviceId ? { deviceId } : undefined);
}

export function createChamber(body: {
  deviceId: string;
  code: string;
  name: string;
  sortNo?: number | null;
  description?: string | null;
}): Promise<DeviceChamber> {
  return api.post<DeviceChamber>('/rms/device-chambers', body);
}

export function updateChamber(id: string, body: {
  name?: string | null;
  sortNo?: number | null;
  description?: string | null;
}): Promise<DeviceChamber> {
  return api.put<DeviceChamber>(`/rms/device-chambers/${id}`, body);
}

export function deleteChamber(id: string): Promise<void> {
  return api.del<void>(`/rms/device-chambers/${id}`);
}

/** 设备视角：该机台的配方列表（四路来源合并 + 孤儿/漂移标注）。 */
export function deviceRecipes(deviceId: string): Promise<DeviceRecipeView> {
  return api.get<DeviceRecipeView>(`/rms/devices/${deviceId}/recipes`);
}

/** 版本 Body 的结构化解析（参数/步序/子配方）。 */
export function parsedVersion(recipeId: string, versionId: string): Promise<RecipeParsedView> {
  return api.get<RecipeParsedView>(`/rms/recipes/${recipeId}/versions/${versionId}/parsed`);
}

/** 良构 / 可执行性校验。 */
export function validateVersion(recipeId: string, versionId: string): Promise<RecipeValidationResult> {
  return api.get<RecipeValidationResult>(`/rms/recipes/${recipeId}/versions/${versionId}/validate`);
}

/** 编辑版本 Body（仅 DRAFT 可改，后端强制）。 */
export function updateVersionBody(recipeId: string, versionId: string, body: {
  bodyFormat: BodyFormat;
  bodyBase64: string;
  expectedBodyHash?: string | null;
  changeSummary?: string | null;
}): Promise<RecipeVersion> {
  return api.put<RecipeVersion>(`/rms/recipes/${recipeId}/versions/${versionId}/body`, body);
}

/** 配方视角反查：该配方被装载到哪些机台（含腔体与资格）。 */
export function recipeTools(recipeId: string): Promise<RecipeToolUsage[]> {
  return api.get<RecipeToolUsage[]>(`/rms/recipes/${recipeId}/tools`);
}

// ==================== W8：导入/导出（文件级备份） ====================

/** 触发浏览器保存文件。 */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

/** 配方导出（需 rms:body:export）：ids 缺省 = 全部。返回 JSON Blob 供保存。 */
export function exportRecipes(ids?: string[]): Promise<Blob> {
  return api.downloadBlob('/rms/recipes/export', ids && ids.length ? { ids: ids.join(',') } : undefined);
}

/** 配方导入（需 rms:recipe:create）：content 为导出文件 JSON 文本；逐条独立事务，部分成功。 */
export function importRecipes(content: string, conflictStrategy: 'SKIP' | 'NEW_VERSION'): Promise<RecipeImportReport> {
  return api.post<RecipeImportReport>('/rms/recipes/import', { content, conflictStrategy });
}

// ==================== W9：通知 + 批量操作 ====================

/** 通知列表（unreadOnly 只回未读）。 */
export function listNotifications(unreadOnly = false): Promise<NotificationPage> {
  return api.get<NotificationPage>('/rms/notifications', { unreadOnly });
}

/** 未读数（铃铛红点轮询）。 */
export function unreadCount(): Promise<number> {
  return api.get<{ count: number }>('/rms/notifications/unread-count').then((r) => r.count);
}

export function markNotificationRead(id: string): Promise<void> {
  return api.post<void>(`/rms/notifications/${id}/read`);
}

export function markAllNotificationsRead(): Promise<number> {
  return api.post<{ marked: number }>('/rms/notifications/read-all').then((r) => r.marked);
}

/** 批量 Hold / 解除（按设备/机型/配方展开，逐条独立事务）。 */
export function batchHold(body: {
  action: 'APPLY' | 'RELEASE';
  deviceIds?: string[];
  deviceTypeIds?: string[];
  recipeIds?: string[];
  reason: string;
  source?: string;
}): Promise<BatchOpResult> {
  return api.post<BatchOpResult>('/rms/integration/holds/batch', body);
}

/** 批量下发（逐条继承幂等/闸门，共用 batchId）。 */
export function batchDownload(body: {
  downloadMode?: string;
  batchId?: string;
  requestor?: string;
  items: BatchDownloadItem[];
}): Promise<BatchOpResult> {
  return api.post<BatchOpResult>('/rms/integration/downloads/batch', body);
}

// ==================== W10：Body 模板 ====================

export function listBodyTemplates(): Promise<BodyTemplate[]> {
  return api.get<BodyTemplate[]>('/rms/body-templates');
}

export function getBodyTemplate(id: string): Promise<BodyTemplateDetail> {
  return api.get<BodyTemplateDetail>(`/rms/body-templates/${id}`);
}

/** 模板骨架（新建配方向导「应用模板」）：bodyBase64 与配方 create 契约一致。 */
export function templateSkeleton(id: string): Promise<BodyTemplateSkeleton> {
  return api.get<BodyTemplateSkeleton>(`/rms/body-templates/${id}/skeleton`);
}

export function createBodyTemplate(body: TemplateUpsert): Promise<BodyTemplate> {
  return api.post<BodyTemplate>('/rms/body-templates', body);
}

export function updateBodyTemplate(id: string, body: TemplateUpsert): Promise<BodyTemplate> {
  return api.put<BodyTemplate>(`/rms/body-templates/${id}`, body);
}

export function deleteBodyTemplate(id: string): Promise<void> {
  return api.del<void>(`/rms/body-templates/${id}`);
}

/** 保存前预检（解析 + 模板强制键，不落库）。 */
export function previewValidate(body: {
  deviceTypeId?: string | null;
  bodyFormat: string;
  bodyBase64: string;
}): Promise<PreviewResult> {
  return api.post<PreviewResult>('/rms/recipes/preview-validate', body);
}

// ==================== W11：实验通道 + 变更效果 + 解析器 SPI ====================

export function listExperiments(status?: string): Promise<Experiment[]> {
  return api.get<Experiment[]>('/rms/experiments', status ? { status } : undefined);
}

export function createExperiment(body: {
  recipeId: string;
  versionId: string;
  deviceId: string;
  reason?: string;
  validFrom?: string | null;
  validUntil: string;
}): Promise<Experiment> {
  return api.post<Experiment>('/rms/experiments', body);
}

export function decideExperiment(id: string, action: 'approve' | 'reject', comment?: string): Promise<Experiment> {
  return api.post<Experiment>(`/rms/experiments/${id}/${action}`, { comment: comment ?? null });
}

export function closeExperiment(id: string, reason?: string): Promise<Experiment> {
  return api.post<Experiment>(`/rms/experiments/${id}/close`, { reason: reason ?? null });
}

/** 配方变更效果历史（激活挂钩，时间倒序）。 */
export function listChangeEffects(recipeId: string): Promise<ChangeEffect[]> {
  return api.get<ChangeEffect[]>('/rms/change-effects', { recipeId });
}

/** 已注册的 Body 解析格式（解析器 SPI 注册表）。 */
export function listBodyFormats(): Promise<BodyFormatInfo[]> {
  return api.get<BodyFormatInfo[]>('/rms/recipes/body-formats');
}
