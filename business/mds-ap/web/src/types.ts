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

// ---------------------------------------------------------------- 设备域

/** 设备大类（行业口径定稿 8 类；页面归属依据：MATERIAL→搬运设备页，其余→设备页）。 */
export type EqKind =
  | 'PROCESS'
  | 'METRO'
  | 'TEST'
  | 'ASSY'
  | 'MATERIAL'
  | 'FACILITY'
  | 'TOOL'
  | 'OTHER';

/** 设备类型（EquipmentType）：ISA-95 Kind→Type—Instance 的「类」，挂大类+细类。 */
export interface EquipmentType {
  id: string;
  typeCode: string;
  typeName: string;
  kindCode: EqKind;
  equipClassCode: string | null;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

export interface EquipmentTypePayload {
  typeCode: string;
  typeName: string;
  kindCode: EqKind;
  equipClassCode?: string | null;
  remark?: string | null;
  enabled?: boolean;
  description?: string | null;
}

/** 设备（Equipment）：实例主数据（一张表，按类型 kind 分页视图）。 */
export interface Equipment {
  id: string;
  equipmentCode: string;
  equipmentName: string;
  typeCode: string;
  typeName: string | null;
  typeKind: string | null;
  locationType: 'FAB' | 'AREA' | 'BAY' | 'STAGE';
  locationId: string;
  stateCode: string | null;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

export interface EquipmentPayload {
  equipmentCode: string;
  equipmentName: string;
  typeCode: string;
  locationType: 'FAB' | 'AREA' | 'BAY' | 'STAGE';
  locationId: string;
  stateCode?: string | null;
  remark?: string | null;
  enabled?: boolean;
  description?: string | null;
}

/** 设备子单元（EquipmentUnit）：设备结构树节点（SEMI E120 口径）。 */
export interface EquipmentUnit {
  id: string;
  equipmentId: string;
  unitCode: string;
  unitName: string;
  unitClass: 'MODULE' | 'SUBSYSTEM';
  unitType: string;
  stateCode: string | null;
  portNo: number | null;
  capacity: number | null;
  enabled: boolean;
  description: string | null;
}

export interface EquipmentUnitPayload {
  unitCode: string;
  unitName: string;
  unitClass: 'MODULE' | 'SUBSYSTEM';
  unitType: string;
  stateCode?: string | null;
  portNo?: number | null;
  capacity?: number | null;
  enabled?: boolean;
  description?: string | null;
}

/** 设备能力（Capability）：能力字典（「能不能做」；recipe 归 MES 不混）。 */
export interface Capability {
  id: string;
  capabilityCode: string;
  capabilityName: string;
  remark: string | null;
  enabled: boolean;
  description: string | null;
  occupied: boolean;
  occupiedBy: string | null;
}

export interface CapabilityPayload {
  capabilityCode: string;
  capabilityName: string;
  remark?: string | null;
  enabled?: boolean;
  description?: string | null;
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
