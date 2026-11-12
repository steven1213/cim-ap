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

/** 基地（Site）。 */
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
}

/** 区域（Area），`fabName` 为后端联表派生。 */
export interface Area {
  id: string;
  areaCode: string;
  fabCode: string;
  fabName: string | null;
  areaName: string;
  areaType: string | null;
  remark: string | null;
  enabled: boolean;
  description: string | null;
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
  remark?: string | null;
  enabled?: boolean;
}
