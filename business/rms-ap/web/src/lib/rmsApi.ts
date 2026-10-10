// RMS 业务 API 封装：配方 + 设备（类型/区域/台账）。
// 所有路径前缀 /api/v1/rms（见 server 各 Controller 的 @RequestMapping）。
import * as api from '@/lib/api';
import type { Recipe, RecipeVersion, DeviceType, DeviceArea, Device } from '@/types';

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
  body: Partial<Pick<Recipe, 'name' | 'golden' | 'remark'>>,
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
