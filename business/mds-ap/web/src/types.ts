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
  areaGroupId: string | null;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

/** 区域组（AreaGroup）—— Area 的统计口径分类（location-design.md §9.1）。 */
export interface AreaGroup {
  id: string;
  groupCode: string;
  fabCode: string;
  groupName: string;
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
  areaGroupId?: string | null;
  remark?: string | null;
  enabled?: boolean;
}

export interface AreaGroupPayload {
  groupCode: string;
  fabCode: string;
  groupName: string;
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

// ---------------------------------------------------------------- 产品管理域

/** 制程技术（Technology）：产品之上的技术节点分类（全局唯一编码）。 */
export interface Technology {
  id: string;
  techCode: string;
  techName: string;
  techNode: string | null;
  remark: string | null;
  enabled: boolean;
  occupied: boolean;
  occupiedBy: string | null;
}

export interface TechnologyPayload {
  techCode: string;
  techName: string;
  techNode?: string | null;
  remark?: string | null;
  enabled?: boolean;
}

/** 产品（Product）：产品管理域主数据（tech_code 逻辑引用制程技术；类型 PROD=量产/NPW=陪片）。 */
export interface Product {
  id: string;
  productCode: string;
  productName: string;
  techCode: string | null;
  productType: 'PROD' | 'NPW';
  productGroup: string | null;
  waferType: string | null;
  owner: string | null;
  remark: string | null;
  enabled: boolean;
  occupied: boolean;
  occupiedBy: string | null;
}

export interface ProductPayload {
  productCode: string;
  productName: string;
  techCode?: string | null;
  productType?: 'PROD' | 'NPW';
  productGroup?: string | null;
  waferType?: string | null;
  owner?: string | null;
  remark?: string | null;
  enabled?: boolean;
}

// ---------------------------------------------------------------- 数据字典（平台 sys_dict）

/** 字典（sys_dict）。isSystem=true 的不可删除（种子/内置枚举保护位）。 */
export interface DictInfo {
  id: string;
  code: string;
  name: string | null;
  status: 'ENABLED' | 'DISABLED';
  isSystem: boolean;
  description: string | null;
}

/** 字典明细（sys_dict_item）。itemKey=落库值，itemValue=显示文案。 */
export interface DictItem {
  id: string;
  dictId: string;
  itemKey: string;
  i18nCode: string | null;
  itemValue: string | null;
  sortNo: number | null;
  status: 'ENABLED' | 'DISABLED';
  isSystem: boolean;
}

/** 字典写入载荷（code 建后不可改，编辑时后端忽略）。 */
export interface DictPayload {
  code: string;
  name?: string | null;
  isSystem?: boolean;
  status?: 'ENABLED' | 'DISABLED';
  description?: string | null;
}

/** 字典明细写入载荷（itemKey 改动后端会重查重）。 */
export interface DictItemPayload {
  itemKey: string;
  itemValue?: string | null;
  i18nCode?: string | null;
  sortNo?: number | null;
  status?: 'ENABLED' | 'DISABLED';
  isSystem?: boolean;
}
