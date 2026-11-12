// MDS 业务 API（薄封装：路径 + 类型，拦截器与解包在 lib/api.ts）。
//
// 所有端点都在 `/api/v1/mds/**` 下（见 server `SiteAdminController`）。
import * as http from '@/lib/api';
import type { Area, AreaPayload, Fab, FabPayload, Site, SitePayload } from '@/types';

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
