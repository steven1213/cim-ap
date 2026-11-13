// MDS 业务 API（薄封装：路径 + 类型，拦截器与解包在 lib/api.ts）。
//
// 所有端点都在 `/api/v1/mds/**` 下（见 server `SiteAdminController`）。
import * as http from '@/lib/api';
import type {
  Area,
  AreaPayload,
  Bay,
  BayPayload,
  Fab,
  FabPayload,
  LockState,
  Site,
  SitePayload,
  Stage,
  StagePayload,
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

// ---------- 编辑锁（site/fab/area/bay/stage 通用） ----------

export type LocationType = 'site' | 'fab' | 'area' | 'bay' | 'stage';

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
