// RMS 控制台共享类型（与 business/rms-ap/server 的 Result/Controller 契约对齐）

export interface MeDto {
  userId: string;
  username: string;
  tenantId: string | null;
  roles: string[];
  permissions: string[];
}

// ---------------------------------------------------------------- 配方

export type RecipeStatus = 'DRAFT' | 'ACTIVE' | 'OBSOLETE';
export type BodyFormat = 'TEXT' | 'XML' | 'BIN' | 'JSON';

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
