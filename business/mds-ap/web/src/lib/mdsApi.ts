// MDS 业务 API（薄封装：路径 + 类型，拦截器与解包在 lib/api.ts）。
//
// 所有端点都在 `/api/v1/mds/**` 下（见 server `SiteAdminController`）。
import * as http from '@/lib/api';
import type {
  Area,
  AreaGroup,
  AreaGroupPayload,
  AreaPayload,
  Bay,
  BayPayload,
  DictInfo,
  DictItem,
  DictItemPayload,
  DictPayload,
  Fab,
  FabPayload,
  LockState,
  Site,
  SitePayload,
  Stage,
  StagePayload,
  Technology,
  TechnologyPayload,
  Product,
  ProductPayload,
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

// ---------- 编辑锁（site/fab/area/areaGroup/bay/stage/technology/product 通用） ----------

export type LocationType =
  | 'site'
  | 'fab'
  | 'area'
  | 'areaGroup'
  | 'bay'
  | 'stage'
  | 'technology'
  | 'product';

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
