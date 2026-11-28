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
  Carrier,
  CarrierPayload,
  Consumable,
  ConsumablePayload,
  ConsBom,
  ConsBomPayload,
  ConsBomLine,
  ConsBomLinePayload,
  QueryDef,
  QueryDefPayload,
  QueryColumnMeta,
  ViewModel,
  ViewModelPayload,
  DynamicMeta,
  DynamicPageData,
  DictInfo,
  DictItem,
  DictItemPayload,
  DictPayload,
  Equipment,
  EquipmentCapabilityItem,
  EquipmentPayload,
  EquipmentClass,
  EquipmentClassPayload,
  InspectionItem,
  InspectionItemPayload,
  InspectionStd,
  InspectionStdPayload,
  Reticle,
  ReticlePayload,
  ToolGroup,
  ToolGroupPayload,
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

// ---------- 组织架构部门（V36 起部门真源 = IAM 组织架构，静态字典废弃） ----------

/** IAM 组织节点（全维度全量，带父子关系——前端建组织树；部门下拉仅启用 DEPT 可选）。 */
export interface OrgNodeOption {
  code: string;
  name: string;
  parentCode: string | null;
  nodeType: string;
  enabled: boolean;
  leaderName: string | null;
}

export function listOrgOptions(signal?: AbortSignal): Promise<OrgNodeOption[]> {
  return http.get<OrgNodeOption[]>('/mds/admin/directory/org-options', undefined, { signal });
}

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

// ---------- 设备域（机型模板 / 机台 / 机台组 / 能力，v2） ----------

export function listEquipmentClasses(keyword?: string, signal?: AbortSignal): Promise<EquipmentClass[]> {
  return http.get<EquipmentClass[]>(`${BASE}/equipment-classes`, { keyword }, { signal });
}

export function createEquipmentClass(payload: EquipmentClassPayload): Promise<EquipmentClass> {
  return http.post<EquipmentClass>(`${BASE}/equipment-classes`, payload);
}

export function updateEquipmentClass(
  id: string,
  payload: Partial<EquipmentClassPayload>,
): Promise<EquipmentClass> {
  return http.put<EquipmentClass>(`${BASE}/equipment-classes/${encodeURIComponent(id)}`, payload);
}

export function deleteEquipmentClass(id: string): Promise<void> {
  return http.del<void>(`${BASE}/equipment-classes/${encodeURIComponent(id)}`);
}

/** 机台列表。view=process|amhs 保留（详情面板按 kind 分块）；ownerUser 供责任人动线过滤。 */
export function listEquipments(
  params?: { keyword?: string; classCode?: string; ownerUser?: string; view?: 'process' | 'amhs' },
  signal?: AbortSignal,
): Promise<Equipment[]> {
  return http.get<Equipment[]>(`${BASE}/equipments`, params, { signal });
}

/** 单机台详情（主从页右侧详情块直查）。 */
export function getEquipment(id: string, signal?: AbortSignal): Promise<Equipment> {
  return http.get<Equipment>(`${BASE}/equipments/${encodeURIComponent(id)}`, undefined, { signal });
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

/** 机台组（ISA-95 Work Center）。 */
export function listToolGroups(
  params?: { keyword?: string; classCode?: string },
  signal?: AbortSignal,
): Promise<ToolGroup[]> {
  return http.get<ToolGroup[]>(`${BASE}/tool-groups`, params, { signal });
}

export function createToolGroup(payload: ToolGroupPayload): Promise<ToolGroup> {
  return http.post<ToolGroup>(`${BASE}/tool-groups`, payload);
}

export function updateToolGroup(id: string, payload: Partial<ToolGroupPayload>): Promise<ToolGroup> {
  return http.put<ToolGroup>(`${BASE}/tool-groups/${encodeURIComponent(id)}`, payload);
}

export function deleteToolGroup(id: string): Promise<void> {
  return http.del<void>(`${BASE}/tool-groups/${encodeURIComponent(id)}`);
}

export function addToolGroupMember(
  groupId: string,
  equipmentId: string,
  priority?: number | null,
): Promise<ToolGroup> {
  const qs = priority != null ? `?priority=${priority}` : '';
  return http.post<ToolGroup>(
    `${BASE}/tool-groups/${encodeURIComponent(groupId)}/members/${encodeURIComponent(equipmentId)}${qs}`,
  );
}

export function removeToolGroupMember(groupId: string, equipmentId: string): Promise<ToolGroup> {
  return http.del<ToolGroup>(
    `${BASE}/tool-groups/${encodeURIComponent(groupId)}/members/${encodeURIComponent(equipmentId)}`,
  );
}

/** 设备已关联的能力条目（V35：混线资格可按尺寸区分，空尺寸 = 全尺寸通用）。 */
export function listEquipmentCapabilities(
  id: string,
  signal?: AbortSignal,
): Promise<EquipmentCapabilityItem[]> {
  return http.get<EquipmentCapabilityItem[]>(
    `${BASE}/equipments/${encodeURIComponent(id)}/capabilities`,
    undefined,
    { signal },
  );
}

/** 能力 → 可加工设备解析（V21，AMAT 口径）：Step.capabilityCode 反查挂该能力的设备。 */
export function listEquipmentsByCapability(
  capabilityCode: string,
  signal?: AbortSignal,
): Promise<Equipment[]> {
  return http.get<Equipment[]>(`${BASE}/equipments/by-capability`, { capabilityCode }, { signal });
}

/** 全量替换设备能力关联（条目提交，含可选适用尺寸）。 */
export function setEquipmentCapabilities(
  id: string,
  items: EquipmentCapabilityItem[],
): Promise<EquipmentCapabilityItem[]> {
  return http.put<EquipmentCapabilityItem[]>(
    `${BASE}/equipments/${encodeURIComponent(id)}/capabilities`,
    items,
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

// ---------- 载具（V31：SEMI E87 Carrier 口径，AMHS 全链物理载体台账） ----------

export function listCarriers(
  params?: { status?: string; equipmentId?: string; keyword?: string },
  signal?: AbortSignal,
): Promise<Carrier[]> {
  return http.get<Carrier[]>(`${BASE}/carriers`, params, { signal });
}

export function createCarrier(payload: CarrierPayload): Promise<Carrier> {
  return http.post<Carrier>(`${BASE}/carriers`, payload);
}

export function updateCarrier(id: string, payload: Partial<CarrierPayload>): Promise<Carrier> {
  return http.put<Carrier>(`${BASE}/carriers/${encodeURIComponent(id)}`, payload);
}

export function deleteCarrier(id: string): Promise<void> {
  return http.del<void>(`${BASE}/carriers/${encodeURIComponent(id)}`);
}

// ---------- 光罩（V32：光刻关键耗材级主数据，工序/流程步骤按 code 引用） ----------

export function listReticles(
  params?: { status?: string; equipmentId?: string; keyword?: string },
  signal?: AbortSignal,
): Promise<Reticle[]> {
  return http.get<Reticle[]>(`${BASE}/reticles`, params, { signal });
}

export function createReticle(payload: ReticlePayload): Promise<Reticle> {
  return http.post<Reticle>(`${BASE}/reticles`, payload);
}

export function updateReticle(id: string, payload: Partial<ReticlePayload>): Promise<Reticle> {
  return http.put<Reticle>(`${BASE}/reticles/${encodeURIComponent(id)}`, payload);
}

export function deleteReticle(id: string): Promise<void> {
  return http.del<void>(`${BASE}/reticles/${encodeURIComponent(id)}`);
}

// ---------- 检验标准（V33：AQL/检验项模板，明细全量替换） ----------

export function listInspectionStds(
  params?: { productGroup?: string; keyword?: string },
  signal?: AbortSignal,
): Promise<InspectionStd[]> {
  return http.get<InspectionStd[]>(`${BASE}/inspection-stds`, params, { signal });
}

export function listInspectionItems(stdId: string, signal?: AbortSignal): Promise<InspectionItem[]> {
  return http.get<InspectionItem[]>(`${BASE}/inspection-stds/${encodeURIComponent(stdId)}/items`, undefined, { signal });
}

export function createInspectionStd(payload: InspectionStdPayload): Promise<InspectionStd> {
  return http.post<InspectionStd>(`${BASE}/inspection-stds`, payload);
}

export function updateInspectionStd(
  id: string,
  payload: Partial<InspectionStdPayload>,
): Promise<InspectionStd> {
  return http.put<InspectionStd>(`${BASE}/inspection-stds/${encodeURIComponent(id)}`, payload);
}

export function replaceInspectionItems(
  id: string,
  items: InspectionItemPayload[],
): Promise<InspectionItem[]> {
  return http.put<InspectionItem[]>(
    `${BASE}/inspection-stds/${encodeURIComponent(id)}/items`,
    items,
  );
}

export function deleteInspectionStd(id: string): Promise<void> {
  return http.del<void>(`${BASE}/inspection-stds/${encodeURIComponent(id)}`);
}

// ---------- 耗材（V34：生产辅助物料台账级主数据） ----------

export function listConsumables(
  params?: { consType?: string; equipmentId?: string; keyword?: string },
  signal?: AbortSignal,
): Promise<Consumable[]> {
  return http.get<Consumable[]>(`${BASE}/consumables`, params, { signal });
}

export function createConsumable(payload: ConsumablePayload): Promise<Consumable> {
  return http.post<Consumable>(`${BASE}/consumables`, payload);
}

export function updateConsumable(id: string, payload: Partial<ConsumablePayload>): Promise<Consumable> {
  return http.put<Consumable>(`${BASE}/consumables/${encodeURIComponent(id)}`, payload);
}

export function deleteConsumable(id: string): Promise<void> {
  return http.del<void>(`${BASE}/consumables/${encodeURIComponent(id)}`);
}

// ---------- 消耗品 BOM（V38：「产品×流程×工序×机台」消耗契约） ----------

export function listConsBoms(
  params?: { productId?: string; flowId?: string; operationId?: string; status?: string; keyword?: string },
  signal?: AbortSignal,
): Promise<ConsBom[]> {
  return http.get<ConsBom[]>(`${BASE}/cons-boms`, params, { signal });
}

export function listConsBomLines(id: string, signal?: AbortSignal): Promise<ConsBomLine[]> {
  return http.get<ConsBomLine[]>(`${BASE}/cons-boms/${encodeURIComponent(id)}/lines`, undefined, { signal });
}

export function createConsBom(payload: ConsBomPayload): Promise<ConsBom> {
  return http.post<ConsBom>(`${BASE}/cons-boms`, payload);
}

export function updateConsBom(id: string, payload: Partial<ConsBomPayload>): Promise<ConsBom> {
  return http.put<ConsBom>(`${BASE}/cons-boms/${encodeURIComponent(id)}`, payload);
}

export function replaceConsBomLines(id: string, lines: ConsBomLinePayload[]): Promise<ConsBomLine[]> {
  return http.put<ConsBomLine[]>(`${BASE}/cons-boms/${encodeURIComponent(id)}/lines`, lines);
}

export function activateConsBom(id: string): Promise<ConsBom> {
  return http.post<ConsBom>(`${BASE}/cons-boms/${encodeURIComponent(id)}/activate`, {});
}

export function disableConsBom(id: string): Promise<ConsBom> {
  return http.post<ConsBom>(`${BASE}/cons-boms/${encodeURIComponent(id)}/disable`, {});
}

export function deleteConsBom(id: string): Promise<void> {
  return http.del<void>(`${BASE}/cons-boms/${encodeURIComponent(id)}`);
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
  | 'equipmentClass'
  | 'equipment'
  | 'toolGroup'
  | 'capability'
  | 'stockerBank'
  | 'edcSet'
  | 'edcReportTpl'
  | 'edcPlan'
  | 'carrier'
  | 'reticle'
  | 'inspectionStd'
  | 'consumable'
  | 'consBom'
  | 'queryDef'
  | 'viewModel';

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
  FlowEventView,
  FlowPayload,
  FlowCanvasPayload,
  FlowValidationResult,
  OperationView,
  OperationPayload,
  OperationVarParam,
  OperationVarParamPayload,
  RecipeResolveRequest,
  RecipeResolveView,
} from '@/types';

export function listFlows(keyword?: string, signal?: AbortSignal): Promise<FlowView[]> {
  return http.get<FlowView[]>(`${BASE}/flows`, keyword ? { keyword } : undefined, { signal });
}

export function getFlow(id: string, signal?: AbortSignal): Promise<FlowDetail> {
  return http.get<FlowDetail>(`${BASE}/flows/${id}`, undefined, { signal });
}

/** 变更历史（V24，P3-2）：流程域动作级事件，时间倒序，最多 200 条。 */
export function listFlowEvents(id: string, signal?: AbortSignal): Promise<FlowEventView[]> {
  return http.get<FlowEventView[]>(`${BASE}/flows/${id}/events`, undefined, { signal });
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

/** 工序可变参数列表（V29；任意状态可读）。 */
export function listOperationVarParams(id: string, signal?: AbortSignal): Promise<OperationVarParam[]> {
  return http.get<OperationVarParam[]>(
    `${BASE}/operations/${id}/variable-params`, undefined, { signal });
}

/** 工序可变参数整单替换（仅 UNFROZEN；paramKey 须引用启用的参数定义）。 */
export function saveOperationVarParams(
  id: string, rows: OperationVarParamPayload[]): Promise<OperationVarParam[]> {
  return http.put<OperationVarParam[]>(`${BASE}/operations/${id}/variable-params`, rows);
}

/** 配方解算契约（V29）：快照 / BLOCKED（HTTP 200 + resolved=false + reason）。 */
export function resolveRecipe(payload: RecipeResolveRequest): Promise<RecipeResolveView> {
  return http.post<RecipeResolveView>(`${BASE}/recipe/resolve`, payload);
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

/** 删除码（软删；有子节点时后端拒绝）。 */
export function deleteReasonCode(id: string): Promise<void> {
  return http.del<void>(`${BASE}/reason-codes/${encodeURIComponent(id)}`);
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

// ============================================================ ICM 继承式约束模型（V22）

/** 统一指派行（scope=OPERATION/FLOW/STEP 三层一张表）。 */
export interface EquipAssign {
  id: string;
  scopeType: 'OPERATION' | 'FLOW' | 'STEP' | 'PRODUCT';
  operationCode: string;
  flowId: string;
  stepId: string; // scope=STEP 时 = cim_flow_step.node_id；scope=PRODUCT 非空 = 细化到站
  productCode?: string; // scope=PRODUCT（V25 L3）
  familyCode?: string; // scope=PRODUCT（V25 L3）
  assignType: 'GROUP' | 'EQUIP' | 'CLASS';
  assignRef: string;
  chamberRule: string | null;
  recipeRef: string | null;
  recipeRule: 'FIXED' | 'GENERATE' | 'EXACT_MATCH';
  recipeMatch: string | null;
  priority: number;
  enabled: boolean;
  remark: string | null;
}

/** 指派请求体（上下文键由端点路径决定）。 */
export interface AssignPayload {
  assignType: string;
  assignRef: string;
  chamberRule?: string | null;
  recipeRef?: string | null;
  recipeRule?: string;
  recipeMatch?: string | null;
  priority?: number;
  enabled?: boolean;
  remark?: string | null;
}

/** 参数定义字典行。 */
export interface ParamDef {
  id: string;
  attrKey: string;
  attrName: string;
  groupCode: string;
  valueType: 'STRING' | 'INT' | 'DECIMAL' | 'BOOLEAN' | 'ENUM' | 'JSON';
  enumDict: string | null;
  unit?: string | null;
  required: boolean;
  scopes: string; // CSV: OPERATION,FLOW,STEP
  sortNo: number;
  enabled: boolean;
}

export interface ParamDefPayload {
  attrKey?: string;
  attrName: string;
  groupCode: string;
  valueType: string;
  enumDict?: string | null;
  unit?: string | null;
  required?: boolean;
  scopes?: string[];
  sortNo?: number;
  enabled?: boolean;
}

/** KV 生效值（带来源层级）。 */
export interface AttrEffective {
  attrKey: string;
  value: string | null;
  source: 'STEP_OVERRIDE' | 'FLOW_OVERRIDE' | 'OPERATION_DEFAULT' | 'MISSING';
}

/** 单字段生效值（来源四级标注）。 */
export interface Effective<T> {
  value: T | null;
  source: 'STEP_OVERRIDE' | 'FLOW_OVERRIDE' | 'OPERATION_DEFAULT' | 'MISSING';
}

/** 节点生效值（ContextResolver 解析结果）。 */
export interface NodeEffective {
  nodeId: string;
  stepCode: string;
  stepName: string | null;
  stepType: string;
  operationCode: string | null;
  opStatus: string | null;
  opRefStale: boolean;
  recipeRef: Effective<string>;
  equipType: Effective<string>;
  qtimeMin: Effective<number>;
  qtimeMax: Effective<number>;
  reworkLimit: Effective<number>;
  batchMode: Effective<string>;
  batchSize: Effective<number>;
  holdPoint: Effective<boolean>;
  holdReason: Effective<string>;
  inspectPlan: Effective<string>;
  samplingRate: Effective<string>;
  reticleRef: Effective<string>;
  contamLevel: Effective<string>;
  contamForbidden: Effective<string>;
  capabilityCode: Effective<string>;
  edcPlanCode: Effective<string>;
  npwLimit: Effective<number>;
  poolSource: string;
  pool: EquipAssign[];
  attrs: Record<string, AttrEffective>;
}

/** 工序引用刷新 diff。 */
export interface OpRefreshDiff {
  nodeId: string;
  stepCode: string;
  oldOpCode: string;
  newOpCode: string;
  changed: boolean;
}

/** 列表页轻量聚合。 */
export interface StepFlags {
  flowId: string;
  businessCount: number;
  unassignedCount: number;
}

/** 产品绑定行。 */
export interface ProductFlowBinding {
  id: string;
  productCode: string;
  flowFamilyCode: string;
  flowVersionNo: number;
  bindState: 'ACTIVE' | 'HISTORY';
  activeTime: string | null;
  activeUser: string | null;
  // ---- V25 L3 列型覆盖（NULL = 继承） ----
  reticleRef?: string | null;
  contamLevel?: string | null;
  contamForbidden?: string | null;
  npwLimit?: number | null;
}

// ---- 统一指派（三 scope） ----

export function listOpAssigns(opCode: string, signal?: AbortSignal): Promise<EquipAssign[]> {
  return http.get<EquipAssign[]>(`${BASE}/operations/${encodeURIComponent(opCode)}/assigns`, {}, { signal });
}
export function createOpAssign(opCode: string, payload: AssignPayload): Promise<EquipAssign> {
  return http.post<EquipAssign>(`${BASE}/operations/${encodeURIComponent(opCode)}/assigns`, payload);
}
export function listFlowAssigns(flowId: string, signal?: AbortSignal): Promise<EquipAssign[]> {
  return http.get<EquipAssign[]>(`${BASE}/flows/${encodeURIComponent(flowId)}/assigns`, {}, { signal });
}
export function createFlowAssign(flowId: string, payload: AssignPayload): Promise<EquipAssign> {
  return http.post<EquipAssign>(`${BASE}/flows/${encodeURIComponent(flowId)}/assigns`, payload);
}
export function listStepAssigns(flowId: string, nodeId: string, signal?: AbortSignal): Promise<EquipAssign[]> {
  return http.get<EquipAssign[]>(`${BASE}/flows/${encodeURIComponent(flowId)}/steps/${encodeURIComponent(nodeId)}/assigns`, {}, { signal });
}
export function createStepAssign(flowId: string, nodeId: string, payload: AssignPayload): Promise<EquipAssign> {
  return http.post<EquipAssign>(`${BASE}/flows/${encodeURIComponent(flowId)}/steps/${encodeURIComponent(nodeId)}/assigns`, payload);
}
export function updateAssign(id: string, payload: AssignPayload): Promise<EquipAssign> {
  return http.put<EquipAssign>(`${BASE}/assigns/${encodeURIComponent(id)}`, payload);
}
export function deleteAssign(id: string): Promise<void> {
  return http.del<void>(`${BASE}/assigns/${encodeURIComponent(id)}`);
}

// ---- KV 约束参数（context 三层，PUT=全量快照） ----

export function listOpAttrs(opCode: string, signal?: AbortSignal): Promise<Record<string, string>> {
  return http.get<Record<string, string>>(`${BASE}/operations/${encodeURIComponent(opCode)}/attrs`, {}, { signal });
}
export function putOpAttrs(opCode: string, values: Record<string, string>): Promise<Record<string, string>> {
  return http.put<Record<string, string>>(`${BASE}/operations/${encodeURIComponent(opCode)}/attrs`, { values });
}
export function listFlowAttrs(flowId: string, signal?: AbortSignal): Promise<Record<string, string>> {
  return http.get<Record<string, string>>(`${BASE}/flows/${encodeURIComponent(flowId)}/attrs`, {}, { signal });
}
export function putFlowAttrs(flowId: string, values: Record<string, string>): Promise<Record<string, string>> {
  return http.put<Record<string, string>>(`${BASE}/flows/${encodeURIComponent(flowId)}/attrs`, { values });
}
export function listStepAttrs(flowId: string, nodeId: string, signal?: AbortSignal): Promise<Record<string, string>> {
  return http.get<Record<string, string>>(`${BASE}/flows/${encodeURIComponent(flowId)}/steps/${encodeURIComponent(nodeId)}/attrs`, {}, { signal });
}
export function putStepAttrs(flowId: string, nodeId: string, values: Record<string, string>): Promise<Record<string, string>> {
  return http.put<Record<string, string>>(`${BASE}/flows/${encodeURIComponent(flowId)}/steps/${encodeURIComponent(nodeId)}/attrs`, { values });
}

// ---- 参数定义字典 ----

export function listParamDefs(signal?: AbortSignal): Promise<ParamDef[]> {
  return http.get<ParamDef[]>(`${BASE}/param-defs`, {}, { signal });
}
export function createParamDef(payload: ParamDefPayload): Promise<ParamDef> {
  return http.post<ParamDef>(`${BASE}/param-defs`, payload);
}
export function updateParamDef(id: string, payload: ParamDefPayload): Promise<ParamDef> {
  return http.put<ParamDef>(`${BASE}/param-defs/${encodeURIComponent(id)}`, payload);
}
export function deleteParamDef(id: string): Promise<void> {
  return http.del<void>(`${BASE}/param-defs/${encodeURIComponent(id)}`);
}

// ---- 产品↔流程绑定 ----

export function listProductFlows(productCode?: string, signal?: AbortSignal): Promise<ProductFlowBinding[]> {
  return http.get<ProductFlowBinding[]>(`${BASE}/product-flows`, { productCode }, { signal });
}
export function bindProductFlow(productCode: string, flowFamilyCode: string, flowVersionNo: number): Promise<ProductFlowBinding> {
  return http.post<ProductFlowBinding>(`${BASE}/product-flows`, { productCode, flowFamilyCode, flowVersionNo });
}

/** V25 L3：列型覆盖更新（空值 = 清除覆盖回落继承）。 */
export function updateProductBindingOverrides(
  id: string,
  req: { reticleRef?: string; contamLevel?: string; contamForbidden?: string; npwLimit?: number },
): Promise<ProductFlowBinding> {
  return http.put<ProductFlowBinding>(`${BASE}/product-flows/${encodeURIComponent(id)}`, req);
}

/** V25 L3：产品级指派（PRODUCT scope）。 */
export function listProductAssigns(bindingId: string, signal?: AbortSignal): Promise<EquipAssign[]> {
  return http.get<EquipAssign[]>(`${BASE}/product-flows/${encodeURIComponent(bindingId)}/assigns`, {}, { signal });
}
export function createProductAssign(bindingId: string, nodeId: string, payload: AssignPayload): Promise<EquipAssign> {
  return http.post<EquipAssign>(
    `${BASE}/product-flows/${encodeURIComponent(bindingId)}/assigns${nodeId ? `?nodeId=${encodeURIComponent(nodeId)}` : ''}`,
    payload,
  );
}

/** V25 L3：产品级 KV 快照。 */
export function listProductAttrs(bindingId: string, signal?: AbortSignal): Promise<Record<string, string>> {
  return http.get<Record<string, string>>(`${BASE}/product-flows/${encodeURIComponent(bindingId)}/attrs`, {}, { signal });
}
export function putProductAttrs(bindingId: string, values: Record<string, string>): Promise<Record<string, string>> {
  return http.put<Record<string, string>>(`${BASE}/product-flows/${encodeURIComponent(bindingId)}/attrs`, { values });
}
export function unbindProductFlow(id: string): Promise<void> {
  return http.del<void>(`${BASE}/product-flows/${encodeURIComponent(id)}`);
}

// ---- ICM 解析 / 校验 / 生命周期增强 ----

export function getEffective(flowId: string, product?: string, signal?: AbortSignal): Promise<NodeEffective[]> {
  return http.get<NodeEffective[]>(
    `${BASE}/flows/${encodeURIComponent(flowId)}/effective`,
    product ? { product } : {},
    { signal },
  );
}
export function getStepEffective(flowId: string, nodeId: string, signal?: AbortSignal): Promise<NodeEffective> {
  return http.get<NodeEffective>(`${BASE}/flows/${encodeURIComponent(flowId)}/steps/${encodeURIComponent(nodeId)}/effective`, {}, { signal });
}
export function refreshOperations(flowId: string, stepIds?: string[]): Promise<OpRefreshDiff[]> {
  return http.post<OpRefreshDiff[]>(`${BASE}/flows/${encodeURIComponent(flowId)}/refresh-operations`, stepIds ?? []);
}
export function stepFlags(flowIds: string[], signal?: AbortSignal): Promise<StepFlags[]> {
  return http.get<StepFlags[]>(`${BASE}/flows/step-flags`, { flowIds: flowIds.join(',') }, { signal });
}
export function lockFlow(id: string): Promise<unknown> {
  return http.post(`${BASE}/flows/${encodeURIComponent(id)}/lock`, {});
}
export function unlockFlow(id: string): Promise<unknown> {
  return http.post(`${BASE}/flows/${encodeURIComponent(id)}/unlock`, {});
}

// ---- P2：直线流程生成 / 三段式 Excel 导入 ----

/** 直线流程生成：按工序列生成 START→N×PROCESS→END 画布并保存，返回重建后的详情。 */
export function generateDesign(flowId: string, operationCodes: string[]): Promise<FlowDetail> {
  return http.post<FlowDetail>(`${BASE}/flows/${encodeURIComponent(flowId)}/generate-design`, { operationCodes });
}

/** 导入预览（dryRun）：各段行数 + 错误清单。 */
export interface FlowImportPreview {
  flowRows: number;
  stepRows: number;
  assignRows: number;
  attrRows: number;
  errors: { sheet: string; row: number; message: string }[];
}

/** 导入提交结果：各段计数。 */
export interface FlowImportResult {
  flowsCreated: number;
  flowsUpdated: number;
  stepsImported: number;
  assignsImported: number;
  attrsImported: number;
}

/** 下载导入模板（base64 xlsx → Blob）。 */
export async function downloadImportTemplate(): Promise<void> {
  const r = await http.get<string>(`${BASE}/flows/import-template`, {});
  const bin = atob(r);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  const url = URL.createObjectURL(new Blob([bytes], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = 'flow-import-template.xlsx';
  a.click();
  URL.revokeObjectURL(url);
}

/** 本地 xlsx 文件 → base64。 */
export function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const s = String(reader.result ?? '');
      resolve(s.slice(s.indexOf(',') + 1));
    };
    reader.onerror = () => reject(new Error(String(reader.error?.message ?? '读取文件失败')));
    reader.readAsDataURL(file);
  });
}

/** 导入（dryRun=true 校验预览；false 提交落库）。 */
export async function importFlows(dataBase64: string, dryRun: boolean): Promise<FlowImportPreview | FlowImportResult> {
  return http.post<FlowImportPreview | FlowImportResult>(`${BASE}/flows/import`, { dataBase64, dryRun });
}

// ============================================================ EDC 工程数采（V28）
// 采集契约静态元数据：参数集 / 设备接口映射（SVID/CEID）/ 方案（版本化四态）。
// 运行时采集执行与判定归 MES/SPC/FDC 运行域。

/** EDC 参数（引用参数定义字典的轻量摘要）。 */
export interface EdcParamLite {
  attrKey: string;
  attrName: string | null;
  valueType: string | null;
  unit: string | null;
}

/** EDC 参数集。 */
export interface EdcParamSet {
  id: string;
  setCode: string;
  setName: string;
  opCode?: string | null;
  enabled?: boolean;
  description?: string | null;
  occupied?: boolean;
  occupiedBy?: string | null;
}

/** EDC 参数集详情（含成员）。 */
export interface EdcParamSetDetail extends EdcParamSet {
  items: Array<{
    id: string;
    attrKey: string;
    sortNo: number;
    param: EdcParamLite | null;
  }>;
}

export interface EdcParamSetPayload {
  setCode?: string;
  setName: string;
  opCode?: string | null;
  enabled?: boolean;
  description?: string | null;
}

/** EDC 报告模板（GEM 报告结构显式化：SVID→参数列表）。 */
export interface EdcReportTpl {
  id: string;
  tplCode: string;
  tplName: string;
  enabled?: boolean;
  description?: string | null;
  occupied?: boolean;
  occupiedBy?: string | null;
}

/** EDC 报告模板详情（含 SVID 成员）。 */
export interface EdcReportTplDetail extends EdcReportTpl {
  items: Array<{
    id: string;
    svid: number;
    attrKey: string;
    sortNo: number;
    param: EdcParamLite | null;
  }>;
}

export interface EdcReportTplPayload {
  tplCode?: string;
  tplName: string;
  enabled?: boolean;
  description?: string | null;
}

export type EdcTplItemPayload = { svid: number; attrKey: string; sortNo?: number };

/** EDC 设备接口映射行（机台 × CEID → 报告模板）。 */
export interface EdcEquipMap {
  id: string;
  equipmentCode: string;
  ceid: number;
  tplId: string;
  tplCode?: string | null;
  tplName?: string | null;
  sourceChannel: string;
  description?: string | null;
}

export interface EdcEquipMapPayload {
  equipmentCode: string;
  ceid: number;
  tplId: string;
  sourceChannel?: string;
  description?: string | null;
}

/** EDC 方案行。 */
export interface EdcPlan {
  id: string;
  planCode: string;
  versionNo: number;
  status: 'UNFROZEN' | 'FROZEN' | 'ACTIVE' | 'DISABLED';
  productCode?: string | null;
  flowFamilyCode: string;
  flowVersionNo: number;
  opCode: string;
  setCode?: string | null;
  description?: string | null;
  activatedAt?: string | null;
  activatedBy?: string | null;
  occupied?: boolean;
  occupiedBy?: string | null;
}

/** EDC 方案明细行（采集项 × Measurement Spec 合一）。 */
export interface EdcPlanItem {
  id: string;
  attrKey: string;
  collectMode: string;
  sampleRule: string;
  intervalMinutes?: number | null;
  required: boolean;
  target?: number | null;
  lsl?: number | null;
  usl?: number | null;
  oocAction: string;
  sortNo: number;
  param: EdcParamLite | null;
}

export interface EdcPlanDetail extends EdcPlan {
  items: EdcPlanItem[];
}

export interface EdcPlanPayload {
  planCode?: string;
  versionNo?: number;
  productCode?: string | null;
  flowFamilyCode: string;
  flowVersionNo?: number;
  opCode: string;
  setCode?: string | null;
  description?: string | null;
}

export type EdcPlanItemPayload = Partial<Omit<EdcPlanItem, 'id' | 'param'>> & { attrKey: string };

export type EdcSetItemPayload = { attrKey: string; sortNo?: number };

export interface EdcImportReport {
  kind: string;
  total: number;
  success: number;
  failed: number;
  failures: Array<{ row: number; message: string }>;
}

// ---------- 参数集 ----------

export function listEdcParamSets(signal?: AbortSignal): Promise<EdcParamSet[]> {
  return http.get<EdcParamSet[]>(`${BASE}/edc/param-sets`, {}, { signal });
}

export function getEdcParamSet(id: string, signal?: AbortSignal): Promise<EdcParamSetDetail> {
  return http.get<EdcParamSetDetail>(`${BASE}/edc/param-sets/${encodeURIComponent(id)}`, undefined, { signal });
}

export function createEdcParamSet(payload: EdcParamSetPayload): Promise<EdcParamSet> {
  return http.post<EdcParamSet>(`${BASE}/edc/param-sets`, payload);
}

export function updateEdcParamSet(id: string, payload: EdcParamSetPayload): Promise<EdcParamSet> {
  return http.put<EdcParamSet>(`${BASE}/edc/param-sets/${encodeURIComponent(id)}`, payload);
}

export function deleteEdcParamSet(id: string): Promise<void> {
  return http.del<void>(`${BASE}/edc/param-sets/${encodeURIComponent(id)}`);
}

export function saveEdcParamSetItems(
  id: string,
  items: EdcSetItemPayload[],
): Promise<EdcParamSetDetail> {
  return http.put<EdcParamSetDetail>(`${BASE}/edc/param-sets/${encodeURIComponent(id)}/items`, items);
}

// ---------- 报告模板 ----------

export function listEdcReportTpls(signal?: AbortSignal): Promise<EdcReportTpl[]> {
  return http.get<EdcReportTpl[]>(`${BASE}/edc/report-tpls`, {}, { signal });
}

export function getEdcReportTpl(id: string, signal?: AbortSignal): Promise<EdcReportTplDetail> {
  return http.get<EdcReportTplDetail>(`${BASE}/edc/report-tpls/${encodeURIComponent(id)}`, undefined, { signal });
}

export function createEdcReportTpl(payload: EdcReportTplPayload): Promise<EdcReportTpl> {
  return http.post<EdcReportTpl>(`${BASE}/edc/report-tpls`, payload);
}

export function updateEdcReportTpl(id: string, payload: EdcReportTplPayload): Promise<EdcReportTpl> {
  return http.put<EdcReportTpl>(`${BASE}/edc/report-tpls/${encodeURIComponent(id)}`, payload);
}

export function deleteEdcReportTpl(id: string): Promise<void> {
  return http.del<void>(`${BASE}/edc/report-tpls/${encodeURIComponent(id)}`);
}

export function saveEdcReportTplItems(
  id: string,
  items: EdcTplItemPayload[],
): Promise<EdcReportTplDetail> {
  return http.put<EdcReportTplDetail>(`${BASE}/edc/report-tpls/${encodeURIComponent(id)}/items`, items);
}

// ---------- 设备接口映射 ----------

export function listEdcEquipMaps(
  equipmentCode?: string,
  signal?: AbortSignal,
): Promise<EdcEquipMap[]> {
  return http.get<EdcEquipMap[]>(
    `${BASE}/edc/equip-maps`,
    equipmentCode ? { equipmentCode } : {},
    { signal },
  );
}

export function createEdcEquipMap(payload: EdcEquipMapPayload): Promise<EdcEquipMap> {
  return http.post<EdcEquipMap>(`${BASE}/edc/equip-maps`, payload);
}

export function updateEdcEquipMap(id: string, payload: EdcEquipMapPayload): Promise<EdcEquipMap> {
  return http.put<EdcEquipMap>(`${BASE}/edc/equip-maps/${encodeURIComponent(id)}`, payload);
}

export function deleteEdcEquipMap(id: string): Promise<void> {
  return http.del<void>(`${BASE}/edc/equip-maps/${encodeURIComponent(id)}`);
}

// ---------- 方案 ----------

export function listEdcPlans(signal?: AbortSignal): Promise<EdcPlan[]> {
  return http.get<EdcPlan[]>(`${BASE}/edc/plans`, {}, { signal });
}

export function getEdcPlan(id: string, signal?: AbortSignal): Promise<EdcPlanDetail> {
  return http.get<EdcPlanDetail>(`${BASE}/edc/plans/${encodeURIComponent(id)}`, undefined, { signal });
}

export function createEdcPlan(payload: EdcPlanPayload): Promise<EdcPlan> {
  return http.post<EdcPlan>(`${BASE}/edc/plans`, payload);
}

export function updateEdcPlan(id: string, payload: EdcPlanPayload): Promise<EdcPlan> {
  return http.put<EdcPlan>(`${BASE}/edc/plans/${encodeURIComponent(id)}`, payload);
}

export function deleteEdcPlan(id: string): Promise<void> {
  return http.del<void>(`${BASE}/edc/plans/${encodeURIComponent(id)}`);
}

export function saveEdcPlanItems(
  id: string,
  items: EdcPlanItemPayload[],
): Promise<EdcPlanDetail> {
  return http.put<EdcPlanDetail>(`${BASE}/edc/plans/${encodeURIComponent(id)}/items`, items);
}

export function applyEdcPlanSet(id: string, setCode: string): Promise<EdcPlanDetail> {
  return http.post<EdcPlanDetail>(
    `${BASE}/edc/plans/${encodeURIComponent(id)}/apply-set?setCode=${encodeURIComponent(setCode)}`,
    {},
  );
}

function planAction(id: string, action: string): Promise<EdcPlan> {
  return http.post<EdcPlan>(`${BASE}/edc/plans/${encodeURIComponent(id)}/${action}`, {});
}

export function freezeEdcPlan(id: string): Promise<EdcPlan> {
  return planAction(id, 'freeze');
}

export function unfreezeEdcPlan(id: string): Promise<EdcPlan> {
  return planAction(id, 'unfreeze');
}

export function activateEdcPlan(id: string): Promise<EdcPlan> {
  return planAction(id, 'activate');
}

export function disableEdcPlan(id: string): Promise<EdcPlan> {
  return planAction(id, 'disable');
}

// ---------- 模板与导入 ----------

/** 下载 EDC 模板（base64 xlsx → Blob，kind=PARAM_DEF/PARAM_SET_ITEM/PLAN_ITEM/EQUIP_MAP）。 */
export async function downloadEdcTemplate(kind: string, filename: string): Promise<void> {
  const r = await http.get<string>(`${BASE}/edc/import-template`, { kind });
  const bin = atob(r);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  const url = URL.createObjectURL(new Blob([bytes], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export function importEdcExcel(
  kind: string,
  dataBase64: string,
  targetId?: string | null,
): Promise<EdcImportReport> {
  return http.post<EdcImportReport>(`${BASE}/edc/import`, { kind, dataBase64, targetId: targetId ?? null });
}

// ============================================================ 动态查询/视图引擎（V39）

export function listQueryDefs(keyword?: string, signal?: AbortSignal): Promise<QueryDef[]> {
  return http.get<QueryDef[]>(`${BASE}/query-defs`, { keyword }, { signal });
}

export function queryDefColumns(id: string): Promise<QueryColumnMeta[]> {
  return http.get<QueryColumnMeta[]>(`${BASE}/query-defs/${encodeURIComponent(id)}/columns`);
}

export function createQueryDef(payload: QueryDefPayload): Promise<QueryDef> {
  return http.post<QueryDef>(`${BASE}/query-defs`, payload);
}

export function updateQueryDef(id: string, payload: QueryDefPayload): Promise<QueryDef> {
  return http.put<QueryDef>(`${BASE}/query-defs/${encodeURIComponent(id)}`, payload);
}

export function deleteQueryDef(id: string): Promise<void> {
  return http.del<void>(`${BASE}/query-defs/${encodeURIComponent(id)}`);
}

export function listViewModels(keyword?: string, signal?: AbortSignal): Promise<ViewModel[]> {
  return http.get<ViewModel[]>(`${BASE}/view-models`, { keyword }, { signal });
}

export function createViewModel(payload: ViewModelPayload): Promise<ViewModel> {
  return http.post<ViewModel>(`${BASE}/view-models`, payload);
}

export function updateViewModel(id: string, payload: ViewModelPayload): Promise<ViewModel> {
  return http.put<ViewModel>(`${BASE}/view-models/${encodeURIComponent(id)}`, payload);
}

export function deleteViewModel(id: string): Promise<void> {
  return http.del<void>(`${BASE}/view-models/${encodeURIComponent(id)}`);
}

/** 运行时契约：元数据。 */
export function dynamicMeta(viewCode: string, signal?: AbortSignal): Promise<DynamicMeta> {
  return http.get<DynamicMeta>(`${BASE}/view-models/${encodeURIComponent(viewCode)}/meta`, undefined, { signal });
}

/** 运行时契约：分页数据。 */
export function dynamicData(
  viewCode: string,
  body: { params: Record<string, string>; page: number; size: number },
  signal?: AbortSignal,
): Promise<DynamicPageData> {
  return http.post<DynamicPageData>(
    `${BASE}/view-models/${encodeURIComponent(viewCode)}/data`,
    body,
    { signal },
  );
}
