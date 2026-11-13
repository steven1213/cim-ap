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

/** 基地（Site）。`occupied*` 为编辑锁状态（location-design.md §8.1）。 */
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
  occupied: boolean;
  occupiedBy: string | null;
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
  occupied: boolean;
  occupiedBy: string | null;
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
  occupied: boolean;
  occupiedBy: string | null;
}

/** 湾（Bay），物理作业区；`fabName`/`areaName` 为后端联表派生。 */
export interface Bay {
  id: string;
  bayCode: string;
  fabCode: string;
  fabName: string | null;
  areaCode: string;
  areaName: string | null;
  bayName: string;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

/** 设备位（Stage），Bay 内物理落位；`bayName` 为后端联表派生。 */
export interface Stage {
  id: string;
  stageCode: string;
  fabCode: string;
  bayCode: string;
  bayName: string | null;
  stageName: string;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
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

export interface BayPayload {
  bayCode: string;
  fabCode: string;
  areaCode: string;
  bayName: string;
  remark?: string | null;
  enabled?: boolean;
}

export interface StagePayload {
  stageCode: string;
  fabCode: string;
  bayCode: string;
  stageName: string;
  remark?: string | null;
  enabled?: boolean;
}

/** 编辑锁状态（`GET /locations/{type}/{id}/lock`）。 */
export interface LockState {
  occupied: boolean;
  occupiedBy: string | null;
  occupiedAt: string | null;
}
