// MDS 业务 API（薄封装：路径 + 类型，拦截器与解包在 lib/api.ts）。
//
// 所有端点都在 `/api/v1/mds/**` 下（见 server `SiteAdminController`）。
import * as http from '@/lib/api';
import type {
  Area,
  AreaGroup,
  AreaGroupPayload,
  AreaPayload,
  BankTransferRule,
  BankTransferRulePayload,
  Bay,
  BayPayload,
  Capability,
  CapabilityPayload,
  DictInfo,
  DictItem,
  DictItemPayload,
  DictPayload,
  Equipment,
  EquipmentPayload,
  EquipmentType,
  EquipmentTypePayload,
  EquipmentUnit,
  EquipmentUnitPayload,
  Fab,
  FabPayload,
  LockState,
  Site,
  SitePayload,
  Stage,
  StagePayload,
  StockerBank,
  StockerBankPayload,
  StateModel,
  StateModelPayload,
  StateTransition,
  StateTransitionPayload,
  Technology,
  TechnologyPayload,
  Product,
  ProductPayload,
  NamingRule,
  NamingCounterView,
  NamingRuleObjectSpec,
  NamingRulePayload,
  NamingRulePreviewReq,
  NamingRulePreviewRes,
  ShiftView,
  ShiftPayload,
  ShiftPatternView,
  ShiftPatternPayload,
  ShiftAssignmentView,
  ShiftCellPayload,
  ShiftGeneratePayload,
  ShiftCopyPayload,
  ShiftUserOption,
  ShiftLogView,
  ShiftLogPayload,
  ControlSwitchView,
  ShiftSwapView,
  ShiftSwapPayload,
} from '@/types';

const BASE = '/mds';

// ---------- 基地 ----------

export function listSites(keyword?: string, signal?: AbortSignal): Promise<Site[]> {
  return http.get<Site[]>(`${BASE}/sites`, { keyword }, { signal });
}

export function createSite(payload: SitePayload): Promise<Site> {
  return http.post<Site>(`${BASE}/sites`, payload);
}

export function updateSite(id: string, payload: SitePayload): Promise<Site> {
  return http.put<Site>(`${BASE}/sites/${encodeURIComponent(id)}`, payload);
}

export function deleteSite(id: string): Promise<void> {
  return http.del<void>(`${BASE}/sites/${encodeURIComponent(id)}`);
}

// ---------- Fab ----------

export function listFabs(siteCode?: string, keyword?: string, signal?: AbortSignal): Promise<Fab[]> {
  return http.get<Fab[]>(`${BASE}/fabs`, { siteCode, keyword }, { signal });
}

export function createFab(payload: FabPayload): Promise<Fab> {
  return http.post<Fab>(`${BASE}/fabs`, payload);
}

export function updateFab(id: string, payload: FabPayload): Promise<Fab> {
  return http.put<Fab>(`${BASE}/fabs/${encodeURIComponent(id)}`, payload);
}

export function deleteFab(id: string): Promise<void> {
  return http.del<void>(`${BASE}/fabs/${encodeURIComponent(id)}`);
}

// ---------- Area ----------

export function listAreas(fabCode?: string, keyword?: string, signal?: AbortSignal): Promise<Area[]> {
  return http.get<Area[]>(`${BASE}/areas`, { fabCode, keyword }, { signal });
}

export function createArea(payload: AreaPayload): Promise<Area> {
  return http.post<Area>(`${BASE}/areas`, payload);
}

export function updateArea(id: string, payload: AreaPayload): Promise<Area> {
  return http.put<Area>(`${BASE}/areas/${encodeURIComponent(id)}`, payload);
}

export function deleteArea(id: string): Promise<void> {
  return http.del<void>(`${BASE}/areas/${encodeURIComponent(id)}`);
}

// ---------- Bay ----------

export function listBays(
  params?: { fabCode?: string; areaCode?: string; keyword?: string },
  signal?: AbortSignal,
): Promise<Bay[]> {
  return http.get<Bay[]>(`${BASE}/bays`, params, { signal });
}

export function createBay(payload: BayPayload): Promise<Bay> {
  return http.post<Bay>(`${BASE}/bays`, payload);
}

export function updateBay(id: string, payload: BayPayload): Promise<Bay> {
  return http.put<Bay>(`${BASE}/bays/${encodeURIComponent(id)}`, payload);
}

export function deleteBay(id: string): Promise<void> {
  return http.del<void>(`${BASE}/bays/${encodeURIComponent(id)}`);
}

// ---------- Stage ----------

export function listStages(
  params?: { fabCode?: string; bayCode?: string; keyword?: string },
  signal?: AbortSignal,
): Promise<Stage[]> {
  return http.get<Stage[]>(`${BASE}/stages`, params, { signal });
}

export function createStage(payload: StagePayload): Promise<Stage> {
  return http.post<Stage>(`${BASE}/stages`, payload);
}

export function updateStage(id: string, payload: StagePayload): Promise<Stage> {
  return http.put<Stage>(`${BASE}/stages/${encodeURIComponent(id)}`, payload);
}

export function deleteStage(id: string): Promise<void> {
  return http.del<void>(`${BASE}/stages/${encodeURIComponent(id)}`);
}

// ---------- 区域组（AreaGroup：Area 的统计口径分类，location-design.md §9.1） ----------

export function listAreaGroups(fabCode?: string, signal?: AbortSignal): Promise<AreaGroup[]> {
  return http.get<AreaGroup[]>(`${BASE}/area-groups`, { fabCode }, { signal });
}

export function createAreaGroup(payload: AreaGroupPayload): Promise<AreaGroup> {
  return http.post<AreaGroup>(`${BASE}/area-groups`, payload);
}

export function updateAreaGroup(id: string, payload: Partial<AreaGroupPayload>): Promise<AreaGroup> {
  return http.put<AreaGroup>(`${BASE}/area-groups/${encodeURIComponent(id)}`, payload);
}

export function deleteAreaGroup(id: string): Promise<void> {
  return http.del<void>(`${BASE}/area-groups/${encodeURIComponent(id)}`);
}

// ---------- 产品管理域（制程技术 Technology） ----------

export function listTechnologies(keyword?: string, signal?: AbortSignal): Promise<Technology[]> {
  return http.get<Technology[]>(`${BASE}/technologies`, { keyword }, { signal });
}

export function createTechnology(payload: TechnologyPayload): Promise<Technology> {
  return http.post<Technology>(`${BASE}/technologies`, payload);
}

export function updateTechnology(
  id: string,
  payload: Partial<TechnologyPayload>,
): Promise<Technology> {
  return http.put<Technology>(`${BASE}/technologies/${encodeURIComponent(id)}`, payload);
}

export function deleteTechnology(id: string): Promise<void> {
  return http.del<void>(`${BASE}/technologies/${encodeURIComponent(id)}`);
}

// ---------- 产品管理域（产品 Product） ----------

export function listProducts(keyword?: string, signal?: AbortSignal): Promise<Product[]> {
  return http.get<Product[]>(`${BASE}/products`, { keyword }, { signal });
}

export function createProduct(payload: ProductPayload): Promise<Product> {
  return http.post<Product>(`${BASE}/products`, payload);
}

export function updateProduct(
  id: string,
  payload: Partial<ProductPayload>,
): Promise<Product> {
  return http.put<Product>(`${BASE}/products/${encodeURIComponent(id)}`, payload);
}

export function deleteProduct(id: string): Promise<void> {
  return http.del<void>(`${BASE}/products/${encodeURIComponent(id)}`);
}

// ---------- 数据字典（平台 sys_dict，端点在根路径 /sys/**，不带 /api/v1 前缀） ----------
//
// ⚠️ 平台控制器（cim-system）不走 /api/v1 前缀（各 ap 业务控制器自带前缀），
// 必须用 root* 系列（裸 axios，baseURL=''）；用 get() 会被拼成 /api/v1/sys/** → 404。

/** 字典项原始结构（SysDictItem：itemKey=落库值，itemValue=显示文案）。 */
export interface DictItemRaw {
  itemKey: string;
  itemValue: string | null;
  i18nCode: string | null;
}

/** 按字典码取启用明细（产品分组/晶圆类型等分类字段下拉用；公共端点无鉴权）。 */
export function fetchDictItems(code: string, signal?: AbortSignal): Promise<DictItemRaw[]> {
  return http.rootGet<DictItemRaw[]>(
    `/sys/dicts/code/${encodeURIComponent(code)}/items`,
    undefined,
    { signal },
  );
}

// ---- 字典管理（管理页用；读 sys:dict:list / 写 sys:dict:save / 删 sys:dict:remove）----

export function listDicts(signal?: AbortSignal): Promise<DictInfo[]> {
  return http.rootGet<DictInfo[]>('/sys/dicts', undefined, { signal });
}

export function createDict(payload: DictPayload): Promise<DictInfo> {
  return http.rootPost<DictInfo>('/sys/dicts', payload);
}

/** 更新字典（code 不可改，后端忽略）。 */
export function updateDict(id: string, payload: Partial<DictPayload>): Promise<DictInfo> {
  return http.rootPut<DictInfo>('/sys/dicts', { id, ...payload });
}

export function deleteDict(id: string): Promise<void> {
  return http.rootDel<void>(`/sys/dicts/${encodeURIComponent(id)}`);
}

/** 某字典的全量明细（含停用；与下拉用 fetchDictItems 的「仅启用」口径不同）。 */
export function listDictItems(dictId: string, signal?: AbortSignal): Promise<DictItem[]> {
  return http.rootGet<DictItem[]>(
    `/sys/dicts/${encodeURIComponent(dictId)}/items`,
    undefined,
    { signal },
  );
}

export function createDictItem(dictId: string, payload: DictItemPayload): Promise<DictItem> {
  return http.rootPost<DictItem>(`/sys/dicts/${encodeURIComponent(dictId)}/items`, payload);
}

export function updateDictItem(
  dictId: string,
  id: string,
  payload: Partial<DictItemPayload>,
): Promise<DictItem> {
  return http.rootPut<DictItem>(
    `/sys/dicts/${encodeURIComponent(dictId)}/items/${encodeURIComponent(id)}`,
    payload,
  );
}

export function deleteDictItem(dictId: string, id: string): Promise<void> {
  return http.rootDel<void>(
    `/sys/dicts/${encodeURIComponent(dictId)}/items/${encodeURIComponent(id)}`,
  );
}

// ---------- 设备域（设备类型 / 设备 / 能力） ----------

export function listEquipmentTypes(keyword?: string, signal?: AbortSignal): Promise<EquipmentType[]> {
  return http.get<EquipmentType[]>(`${BASE}/equipment-types`, { keyword }, { signal });
}

export function createEquipmentType(payload: EquipmentTypePayload): Promise<EquipmentType> {
  return http.post<EquipmentType>(`${BASE}/equipment-types`, payload);
}

export function updateEquipmentType(
  id: string,
  payload: Partial<EquipmentTypePayload>,
): Promise<EquipmentType> {
  return http.put<EquipmentType>(`${BASE}/equipment-types/${encodeURIComponent(id)}`, payload);
}

export function deleteEquipmentType(id: string): Promise<void> {
  return http.del<void>(`${BASE}/equipment-types/${encodeURIComponent(id)}`);
}

/** 设备列表。view=process|amhs：两个页面共用一套 CRUD，按类型 category 过滤视图。 */
export function listEquipments(
  params?: { keyword?: string; typeCode?: string; view?: 'process' | 'amhs' },
  signal?: AbortSignal,
): Promise<Equipment[]> {
  return http.get<Equipment[]>(`${BASE}/equipments`, params, { signal });
}

export function createEquipment(payload: EquipmentPayload): Promise<Equipment> {
  return http.post<Equipment>(`${BASE}/equipments`, payload);
}

export function updateEquipment(id: string, payload: Partial<EquipmentPayload>): Promise<Equipment> {
  return http.put<Equipment>(`${BASE}/equipments/${encodeURIComponent(id)}`, payload);
}

export function deleteEquipment(id: string): Promise<void> {
  return http.del<void>(`${BASE}/equipments/${encodeURIComponent(id)}`);
}

/** 设备已关联的能力码集合。 */
export function listEquipmentCapabilities(id: string, signal?: AbortSignal): Promise<string[]> {
  return http.get<string[]>(`${BASE}/equipments/${encodeURIComponent(id)}/capabilities`, undefined, {
    signal,
  });
}

/** 全量替换设备能力关联（勾选提交）。 */
export function setEquipmentCapabilities(id: string, capabilityCodes: string[]): Promise<string[]> {
  return http.put<string[]>(
    `${BASE}/equipments/${encodeURIComponent(id)}/capabilities`,
    capabilityCodes,
  );
}

export function listCapabilities(keyword?: string, signal?: AbortSignal): Promise<Capability[]> {
  return http.get<Capability[]>(`${BASE}/capabilities`, { keyword }, { signal });
}

export function createCapability(payload: CapabilityPayload): Promise<Capability> {
  return http.post<Capability>(`${BASE}/capabilities`, payload);
}

export function updateCapability(
  id: string,
  payload: Partial<CapabilityPayload>,
): Promise<Capability> {
  return http.put<Capability>(`${BASE}/capabilities/${encodeURIComponent(id)}`, payload);
}

export function deleteCapability(id: string): Promise<void> {
  return http.del<void>(`${BASE}/capabilities/${encodeURIComponent(id)}`);
}

// ---------- 存储区 Bank（SEMI E88 zone 口径：Stocker 内逻辑分区） ----------

export function listStockerBanks(
  params?: { equipmentId?: string; keyword?: string },
  signal?: AbortSignal,
): Promise<StockerBank[]> {
  return http.get<StockerBank[]>(`${BASE}/stocker-banks`, params, { signal });
}

export function createStockerBank(payload: StockerBankPayload): Promise<StockerBank> {
  return http.post<StockerBank>(`${BASE}/stocker-banks`, payload);
}

export function updateStockerBank(
  id: string,
  payload: Partial<StockerBankPayload>,
): Promise<StockerBank> {
  return http.put<StockerBank>(`${BASE}/stocker-banks/${encodeURIComponent(id)}`, payload);
}

export function deleteStockerBank(id: string): Promise<void> {
  return http.del<void>(`${BASE}/stocker-banks/${encodeURIComponent(id)}`);
}

// ---------- Bank 间转运规则（from→to allowlist；目的地分配归 Host，E88 6.1.1） ----------

export function listBankTransferRules(bankId?: string, signal?: AbortSignal): Promise<BankTransferRule[]> {
  return http.get<BankTransferRule[]>(`${BASE}/bank-transfer-rules`, { bankId }, { signal });
}

export function createBankTransferRule(payload: BankTransferRulePayload): Promise<BankTransferRule> {
  return http.post<BankTransferRule>(`${BASE}/bank-transfer-rules`, payload);
}

export function updateBankTransferRule(
  id: string,
  payload: Partial<BankTransferRulePayload>,
): Promise<BankTransferRule> {
  return http.put<BankTransferRule>(`${BASE}/bank-transfer-rules/${encodeURIComponent(id)}`, payload);
}

export function deleteBankTransferRule(id: string): Promise<void> {
  return http.del<void>(`${BASE}/bank-transfer-rules/${encodeURIComponent(id)}`);
}

// ---------- 状态模型（SEMI E30 状态机口径：状态集 + 转换 allowlist + 触发条件） ----------

export function listStateModels(signal?: AbortSignal): Promise<StateModel[]> {
  return http.get<StateModel[]>(`${BASE}/state-models`, undefined, { signal });
}

export function resolveStateModel(
  objectType: string,
  unitForm?: string,
  signal?: AbortSignal,
): Promise<StateModel> {
  return http.get<StateModel>(`${BASE}/state-models/resolve`, { objectType, unitForm }, { signal });
}

export function listStateTransitions(modelCode: string, signal?: AbortSignal): Promise<StateTransition[]> {
  return http.get<StateTransition[]>(
    `${BASE}/state-models/${encodeURIComponent(modelCode)}/transitions`,
    undefined,
    { signal },
  );
}

export function listAllowedStateTargets(
  modelCode: string,
  from: string,
  signal?: AbortSignal,
): Promise<string[]> {
  return http.get<string[]>(
    `${BASE}/state-models/${encodeURIComponent(modelCode)}/allowed`,
    { from },
    { signal },
  );
}

/** 矩阵勾选/取消（allowed=false 后端物理删该行）。 */
export function setStateTransition(
  modelCode: string,
  payload: StateTransitionPayload,
): Promise<StateTransition | null> {
  return http.put<StateTransition | null>(
    `${BASE}/state-models/${encodeURIComponent(modelCode)}/transitions`,
    payload,
  );
}

/** 新建状态模型（modelCode/dictCode/objectType 建后不可改）。 */
export function createStateModel(payload: StateModelPayload): Promise<StateModel> {
  return http.post<StateModel>(`${BASE}/state-models`, payload);
}

/** 更新模型（仅 modelName/unitForm/stateCodes 生效；状态收缩有转换引用守门）。 */
export function updateStateModel(
  modelCode: string,
  payload: StateModelPayload,
): Promise<StateModel> {
  return http.put<StateModel>(
    `${BASE}/state-models/${encodeURIComponent(modelCode)}`,
    payload,
  );
}

/** 删除模型（存在转换规则时后端拒绝）。 */
export function deleteStateModel(modelCode: string): Promise<void> {
  return http.del<void>(`${BASE}/state-models/${encodeURIComponent(modelCode)}`);
}

// ---------- 编码规则（有序段列表 + 段类型解析器 + 分桶计数器） ----------

export function listNamingRules(signal?: AbortSignal): Promise<NamingRule[]> {
  return http.get<NamingRule[]>(`${BASE}/naming-rules`, undefined, { signal });
}

/** 对象白名单（object_type → 中文名 / 父对象提示 / 段可用变量）。 */
export function listNamingObjectTypes(signal?: AbortSignal): Promise<NamingRuleObjectSpec[]> {
  return http.get<NamingRuleObjectSpec[]>(`${BASE}/naming-rules/object-types`, undefined, { signal });
}

/** 新建规则（objectType 建后不可改；同对象至多一条启用规则，重复启用 409）。 */
export function createNamingRule(payload: NamingRulePayload): Promise<NamingRule> {
  return http.post<NamingRule>(`${BASE}/naming-rules`, payload);
}

/** 更新规则（仅段列表/名称/启停生效；objectType 建后不可改，计数器保留不回退）。 */
export function updateNamingRule(id: string, payload: NamingRulePayload): Promise<NamingRule> {
  return http.put<NamingRule>(`${BASE}/naming-rules/${encodeURIComponent(id)}`, payload);
}

/** 删除规则（连同计数器物理删）。 */
export function deleteNamingRule(id: string): Promise<void> {
  return http.del<void>(`${BASE}/naming-rules/${encodeURIComponent(id)}`);
}

/** 复制规则：名称加「·副本」、默认停用、不带计数器。 */
export function copyNamingRule(id: string): Promise<NamingRule> {
  return http.post<NamingRule>(`${BASE}/naming-rules/${encodeURIComponent(id)}/copy`);
}

/** 计数器只读查询（各 SEQ 段各桶当前值；号段只增不回收）。 */
export function listNamingCounters(id: string, signal?: AbortSignal): Promise<NamingCounterView[]> {
  return http.get<NamingCounterView[]>(
    `${BASE}/naming-rules/${encodeURIComponent(id)}/counters`, undefined, { signal });
}

/** 试算：逐段渲染明细 + 示例编码 + 派生正则 + 缺失变量（不占号）。 */
export function previewNamingRule(payload: NamingRulePreviewReq): Promise<NamingRulePreviewRes> {
  return http.post<NamingRulePreviewRes>(`${BASE}/naming-rules/preview`, payload);
}

// ---------- 设备子单元（E120 结构树：LoadPort / Chamber / Aligner / Robot…） ----------

export function listEquipmentUnits(equipmentId: string, signal?: AbortSignal): Promise<EquipmentUnit[]> {
  return http.get<EquipmentUnit[]>(
    `${BASE}/equipments/${encodeURIComponent(equipmentId)}/units`,
    undefined,
    { signal },
  );
}

export function createEquipmentUnit(
  equipmentId: string,
  payload: EquipmentUnitPayload,
): Promise<EquipmentUnit> {
  return http.post<EquipmentUnit>(
    `${BASE}/equipments/${encodeURIComponent(equipmentId)}/units`,
    payload,
  );
}

export function updateEquipmentUnit(
  equipmentId: string,
  unitId: string,
  payload: Partial<EquipmentUnitPayload>,
): Promise<EquipmentUnit> {
  return http.put<EquipmentUnit>(
    `${BASE}/equipments/${encodeURIComponent(equipmentId)}/units/${encodeURIComponent(unitId)}`,
    payload,
  );
}

export function deleteEquipmentUnit(equipmentId: string, unitId: string): Promise<void> {
  return http.del<void>(
    `${BASE}/equipments/${encodeURIComponent(equipmentId)}/units/${encodeURIComponent(unitId)}`,
  );
}

// ---------- 编辑锁（site/fab/…/capability/stockerBank 通用） ----------

export type LocationType =
  | 'site'
  | 'fab'
  | 'area'
  | 'areaGroup'
  | 'bay'
  | 'stage'
  | 'technology'
  | 'product'
  | 'equipmentType'
  | 'equipment'
  | 'capability'
  | 'stockerBank';

/** 抢锁：成功返回 claimed=true；失败后端抛「谁正在编辑」。 */
export function claimLock(type: LocationType, id: string): Promise<{ claimed: boolean }> {
  return http.put<{ claimed: boolean }>(
    `${BASE}/locations/${type}/${encodeURIComponent(id)}/lock`,
    {},
  );
}

/** 释放锁：默认只清自己的（服务端按 occupied_by 条件）。 */
export function releaseLock(type: LocationType, id: string): Promise<void> {
  return http.del<void>(`${BASE}/locations/${type}/${encodeURIComponent(id)}/lock`);
}

export function lockState(type: LocationType, id: string, signal?: AbortSignal): Promise<LockState> {
  return http.get<LockState>(`${BASE}/locations/${type}/${encodeURIComponent(id)}/lock`, undefined, {
    signal,
  });
}

// ---------- 制造流程（Flow，Route 口径：XML 画布为源 + 解析产物表） ----------

import type {
  FlowView,
  FlowDetail,
  FlowPayload,
  FlowCanvasPayload,
  FlowValidationResult,
  OperationView,
  OperationPayload,
} from '@/types';

export function listFlows(keyword?: string, signal?: AbortSignal): Promise<FlowView[]> {
  return http.get<FlowView[]>(`${BASE}/flows`, keyword ? { keyword } : undefined, { signal });
}

export function getFlow(id: string, signal?: AbortSignal): Promise<FlowDetail> {
  return http.get<FlowDetail>(`${BASE}/flows/${id}`, undefined, { signal });
}

export function createFlow(payload: FlowPayload): Promise<FlowView> {
  return http.post<FlowView>(`${BASE}/flows`, payload);
}

export function updateFlow(id: string, payload: FlowPayload): Promise<FlowView> {
  return http.put<FlowView>(`${BASE}/flows/${id}`, payload);
}

export function deleteFlow(id: string): Promise<void> {
  return http.del<void>(`${BASE}/flows/${id}`);
}

// ================================================================ 工序库（Operation，V20）

/** 工序列表（关键字模糊：编码/名称/工艺编码）。 */
export function listOperations(keyword?: string, signal?: AbortSignal): Promise<OperationView[]> {
  return http.get<OperationView[]>(`${BASE}/operations`, keyword ? { keyword } : undefined, { signal });
}

/** 生效工序清单（路线画布「选工序」下拉用；仅 ACTIVE）。 */
export function listActiveOperations(signal?: AbortSignal): Promise<OperationView[]> {
  return http.get<OperationView[]>(`${BASE}/operations/active`, undefined, { signal });
}

export function createOperation(payload: OperationPayload): Promise<OperationView> {
  return http.post<OperationView>(`${BASE}/operations`, payload);
}

export function updateOperation(id: string, payload: OperationPayload): Promise<OperationView> {
  return http.put<OperationView>(`${BASE}/operations/${id}`, payload);
}

export function deleteOperation(id: string): Promise<void> {
  return http.del<void>(`${BASE}/operations/${id}`);
}

/** 冻结（UNFROZEN → FROZEN）。 */
export function freezeOperation(id: string): Promise<OperationView> {
  return http.post<OperationView>(`${BASE}/operations/${id}/freeze`, {});
}

/** 解冻（FROZEN → UNFROZEN）。 */
export function unfreezeOperation(id: string): Promise<OperationView> {
  return http.post<OperationView>(`${BASE}/operations/${id}/unfreeze`, {});
}

/** 激活（FROZEN → ACTIVE；同族旧 ACTIVE 自动降级 FROZEN）。 */
export function activateOperation(id: string): Promise<OperationView> {
  return http.post<OperationView>(`${BASE}/operations/${id}/activate`, {});
}

/** 禁用（FROZEN/ACTIVE → DISABLED；被引用禁禁用）。 */
export function disableOperation(id: string): Promise<OperationView> {
  return http.post<OperationView>(`${BASE}/operations/${id}/disable`, {});
}

/** 复制为同族新版本（opCode 手填必填）。 */
export function copyOperation(id: string, newOpCode: string): Promise<OperationView> {
  return http.post<OperationView>(
    `${BASE}/operations/${id}/copy?newOpCode=${encodeURIComponent(newOpCode)}`, {});
}

/** 画布保存：整份 XML 回传，服务端解析重建解析产物表。 */
export function saveFlowCanvas(id: string, payload: FlowCanvasPayload): Promise<FlowDetail> {
  return http.put<FlowDetail>(`${BASE}/flows/${id}/canvas`, payload);
}

export function validateFlow(id: string): Promise<FlowValidationResult> {
  return http.post<FlowValidationResult>(`${BASE}/flows/${id}/validate`, {});
}

/** 冻结（UNFROZEN → FROZEN，图校验前置）。 */
export function freezeFlow(id: string): Promise<FlowView> {
  return http.post<FlowView>(`${BASE}/flows/${id}/freeze`, {});
}

/** 解冻（FROZEN → UNFROZEN）。 */
export function unfreezeFlow(id: string): Promise<FlowView> {
  return http.post<FlowView>(`${BASE}/flows/${id}/unfreeze`, {});
}

/** 激活（FROZEN → ACTIVE，图校验前置；同族旧 ACTIVE 自动降级 FROZEN）。 */
export function activateFlow(id: string): Promise<FlowView> {
  return http.post<FlowView>(`${BASE}/flows/${id}/activate`, {});
}

/** 禁用（FROZEN/ACTIVE → DISABLED）。 */
export function disableFlow(id: string): Promise<FlowView> {
  return http.post<FlowView>(`${BASE}/flows/${id}/disable`, {});
}

export function copyFlow(id: string): Promise<FlowView> {
  return http.post<FlowView>(`${BASE}/flows/${id}/copy`, {});
}

// ---------------------------------------------------------------- 流程规则（V15）

import type {
  FlowRuleView,
  FlowRulePayload,
  FlowRuleEvaluatePayload,
  FlowRuleEvaluateResult,
} from '@/types';

/** 某流程版本的规则清单（按 priority 升序）。 */
export function listFlowRules(flowId: string, signal?: AbortSignal): Promise<FlowRuleView[]> {
  return http.get<FlowRuleView[]>(`${BASE}/flow-rules`, { flowId }, { signal });
}

/** 新建规则（仅 UNFROZEN 版本；ruleCode 族内同版唯一）。 */
export function createFlowRule(flowId: string, payload: FlowRulePayload): Promise<FlowRuleView> {
  return http.post<FlowRuleView>(`${BASE}/flow-rules${'?flowId=' + encodeURIComponent(flowId)}`, payload);
}

/** 更新规则。 */
export function updateFlowRule(id: string, payload: FlowRulePayload): Promise<FlowRuleView> {
  return http.put<FlowRuleView>(`${BASE}/flow-rules/${id}`, payload);
}

/** 删除规则（软删；仅 UNFROZEN 版本）。 */
export function deleteFlowRule(id: string): Promise<void> {
  return http.del<void>(`${BASE}/flow-rules/${id}`);
}

/** 套用内置模板（QTIME_OVER_HOLD / BIN_ROUTE）。 */
export function applyFlowRuleTemplate(flowId: string, templateKey: string): Promise<FlowRuleView[]> {
  return http.post<FlowRuleView[]>(`${BASE}/flow-rules/apply-template`, { flowId, templateKey });
}

/** evaluate 契约（运行期调用 + 页面调试面板）。 */
export function evaluateFlowRules(payload: FlowRuleEvaluatePayload): Promise<FlowRuleEvaluateResult> {
  return http.post<FlowRuleEvaluateResult>(`${BASE}/flow-rules/evaluate`, payload);
}

// ---------------------------------------------------------------- 原因码（V16）

import type {
  ReasonCodeTypeView,
  ReasonCodeTypePayload,
  ReasonCodeView,
  ReasonCodeCreatePayload,
  ReasonCodeUpdatePayload,
  WipReasonEventView,
  WipReasonEventPayload,
} from '@/types';

/** 类型清单（含挂靠码计数）。 */
export function listReasonCodeTypes(signal?: AbortSignal): Promise<ReasonCodeTypeView[]> {
  return http.get<ReasonCodeTypeView[]>(`${BASE}/reason-code-types`, undefined, { signal });
}

export function createReasonCodeType(payload: ReasonCodeTypePayload): Promise<ReasonCodeTypeView> {
  return http.post<ReasonCodeTypeView>(`${BASE}/reason-code-types`, payload);
}

/** 更新类型（typeKey 建后不可改，后端拒绝改键）。 */
export function updateReasonCodeType(
  id: string,
  payload: Partial<ReasonCodeTypePayload>,
): Promise<ReasonCodeTypeView> {
  return http.put<ReasonCodeTypeView>(
    `${BASE}/reason-code-types/${encodeURIComponent(id)}`,
    payload,
  );
}

/** 删除类型（类型下仍有码时后端拒绝）。 */
export function deleteReasonCodeType(id: string): Promise<void> {
  return http.del<void>(`${BASE}/reason-code-types/${encodeURIComponent(id)}`);
}

/** 码清单（typeKey 过滤 + keyword 模糊；node_path 升序 = 树展开序）。 */
export function listReasonCodes(
  params?: { typeKey?: string; keyword?: string },
  signal?: AbortSignal,
): Promise<ReasonCodeView[]> {
  return http.get<ReasonCodeView[]>(`${BASE}/reason-codes`, params, { signal });
}

/** WIP 下拉口径：type 匹配 + 启用。 */
export function listEnabledReasonCodes(
  typeKey: string,
  signal?: AbortSignal,
): Promise<ReasonCodeView[]> {
  return http.get<ReasonCodeView[]>(`${BASE}/reason-codes/enabled`, { typeKey }, { signal });
}

export function createReasonCode(payload: ReasonCodeCreatePayload): Promise<ReasonCodeView> {
  return http.post<ReasonCodeView>(`${BASE}/reason-codes`, payload);
}

/** 更新码（reasonCode/typeKey 建后不可改；改父节点时子树路径由后端整段重算）。 */
export function updateReasonCode(
  id: string,
  payload: ReasonCodeUpdatePayload,
): Promise<ReasonCodeView> {
  return http.put<ReasonCodeView>(`${BASE}/reason-codes/${encodeURIComponent(id)}`, payload);
}

/** 删除码（软删；有子节点或被事件引用时后端拒绝）。 */
export function deleteReasonCode(id: string): Promise<void> {
  return http.del<void>(`${BASE}/reason-codes/${encodeURIComponent(id)}`);
}

/** WIP 事件履历（lotId 可选过滤；按发生时间倒序）。 */
export function listWipReasonEvents(
  lotId?: string,
  signal?: AbortSignal,
): Promise<WipReasonEventView[]> {
  return http.get<WipReasonEventView[]>(`${BASE}/wip-reason-events`, { lotId }, { signal });
}

/** 创建事件（快照五件套同事务落库）。 */
export function createWipReasonEvent(payload: WipReasonEventPayload): Promise<WipReasonEventView> {
  return http.post<WipReasonEventView>(`${BASE}/wip-reason-events`, payload);
}

// ============================================================ 班组排班（V17）

/** 班次全量（sort_no 升序）。 */
export function listShifts(signal?: AbortSignal): Promise<ShiftView[]> {
  return http.get<ShiftView[]>(`${BASE}/shifts`, undefined, { signal });
}

/** 启用班次（排班下拉口径）。 */
export function listEnabledShifts(signal?: AbortSignal): Promise<ShiftView[]> {
  return http.get<ShiftView[]>(`${BASE}/shifts/enabled`, undefined, { signal });
}

export function createShift(payload: ShiftPayload): Promise<ShiftView> {
  return http.post<ShiftView>(`${BASE}/shifts`, payload);
}

export function updateShift(id: string, payload: ShiftPayload): Promise<ShiftView> {
  return http.put<ShiftView>(`${BASE}/shifts/${id}`, payload);
}

/** 删除班次（被引用返回 2004，只能停用）。 */
export function deleteShift(id: string): Promise<void> {
  return http.del<void>(`${BASE}/shifts/${id}`);
}

/** 班制全量。 */
export function listShiftPatterns(signal?: AbortSignal): Promise<ShiftPatternView[]> {
  return http.get<ShiftPatternView[]>(`${BASE}/shift-patterns`, undefined, { signal });
}

/** 启用班制（铺排入口下拉）。 */
export function listEnabledShiftPatterns(signal?: AbortSignal): Promise<ShiftPatternView[]> {
  return http.get<ShiftPatternView[]>(`${BASE}/shift-patterns/enabled`, undefined, { signal });
}

export function createShiftPattern(payload: ShiftPatternPayload): Promise<ShiftPatternView> {
  return http.post<ShiftPatternView>(`${BASE}/shift-patterns`, payload);
}

export function updateShiftPattern(id: string, payload: ShiftPatternPayload): Promise<ShiftPatternView> {
  return http.put<ShiftPatternView>(`${BASE}/shift-patterns/${id}`, payload);
}

export function deleteShiftPattern(id: string): Promise<void> {
  return http.del<void>(`${BASE}/shift-patterns/${id}`);
}

/** 周区间排班（from = 周一）。 */
export function listWeekAssignments(from: string, signal?: AbortSignal): Promise<ShiftAssignmentView[]> {
  return http.get<ShiftAssignmentView[]>(`${BASE}/shift-assignments`, { from }, { signal });
}

/** 单格保存（shiftCode 空 = 清空；返回 null 表示已清空）。 */
export function saveShiftCell(payload: ShiftCellPayload): Promise<ShiftAssignmentView | null> {
  return http.put<ShiftAssignmentView | null>(`${BASE}/shift-assignments`, payload);
}

/** 班制铺排（一步落库；返回写入行数）。 */
export function generateFromPattern(payload: ShiftGeneratePayload): Promise<number> {
  return http.post<number>(`${BASE}/shift-assignments/generate`, payload);
}

/** 复制上周。 */
export function copyLastWeek(payload: ShiftCopyPayload): Promise<number> {
  return http.post<number>(`${BASE}/shift-assignments/copy`, payload);
}

/** 选人下拉（IAM 目录启用档案）。 */
export function listShiftUsers(signal?: AbortSignal): Promise<ShiftUserOption[]> {
  return http.get<ShiftUserOption[]>(`${BASE}/shift-users`, undefined, { signal });
}

/** 交接记录清单（shiftCode 过滤可选）。 */
export function listShiftLogs(shiftCode?: string, signal?: AbortSignal): Promise<ShiftLogView[]> {
  return http.get<ShiftLogView[]>(`${BASE}/shift-logs`, { shiftCode }, { signal });
}

/** 交接记录详情。 */
export function getShiftLog(id: string, signal?: AbortSignal): Promise<ShiftLogView> {
  return http.get<ShiftLogView>(`${BASE}/shift-logs/${id}`, undefined, { signal });
}

/** 交班提交 / 修改（仅未签收可改）。 */
export function saveShiftLog(payload: ShiftLogPayload): Promise<ShiftLogView> {
  return http.post<ShiftLogView>(`${BASE}/shift-logs`, payload);
}

/** 接班签收（乐观锁；签收后锁定只读）。 */
export function ackShiftLog(id: string, version: number): Promise<ShiftLogView> {
  return http.post<ShiftLogView>(`${BASE}/shift-logs/${id}/ack?version=${version}`, {});
}

// ============================================================ 卡控开关（V18）

/** 卡控开关清单（domainCode 过滤可选；页面按排班域拉取）。 */
export function listControlSwitches(
  domainCode?: string,
  signal?: AbortSignal,
): Promise<ControlSwitchView[]> {
  return http.get<ControlSwitchView[]>(`${BASE}/control-switches`, { domainCode }, { signal });
}

/** 卡控开关启停（幂等；与现值相同后端直接返回）。 */
export function setControlSwitch(code: string, enabled: boolean): Promise<ControlSwitchView> {
  return http.put<ControlSwitchView>(
    `${BASE}/control-switches/${code}?enabled=${enabled}`,
    {},
  );
}

// ============================================================ 换班申请（V19）

/** 换班申请清单（status 过滤可选；create_time 倒序）。 */
export function listShiftSwaps(status?: string, signal?: AbortSignal): Promise<ShiftSwapView[]> {
  return http.get<ShiftSwapView[]>(`${BASE}/shift-swaps`, { status }, { signal });
}

/** 发起换班/代班申请（落 PENDING）。 */
export function applyShiftSwap(payload: ShiftSwapPayload): Promise<ShiftSwapView> {
  return http.post<ShiftSwapView>(`${BASE}/shift-swaps`, payload);
}

/** 对方确认（本期确认即生效，排班同事务落库）。 */
export function confirmShiftSwap(id: string, note?: string): Promise<ShiftSwapView> {
  const q = note ? `?note=${encodeURIComponent(note)}` : '';
  return http.post<ShiftSwapView>(`${BASE}/shift-swaps/${id}/confirm${q}`, {});
}

/** 对方拒绝（PENDING → REJECTED）。 */
export function rejectShiftSwap(id: string, reason?: string): Promise<ShiftSwapView> {
  const q = reason ? `?reason=${encodeURIComponent(reason)}` : '';
  return http.post<ShiftSwapView>(`${BASE}/shift-swaps/${id}/reject${q}`, {});
}

/** 发起人取消（PENDING → CANCELLED）。 */
export function cancelShiftSwap(id: string): Promise<ShiftSwapView> {
  return http.post<ShiftSwapView>(`${BASE}/shift-swaps/${id}/cancel`, {});
}
