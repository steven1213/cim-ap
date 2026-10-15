// RMS 权限码常量（前端侧镜像，与 server `security/RmsPermissions` 一一对应）。
// 新增权限码时**两侧都要加**，避免「按钮可见但接口 403」。
// 注：RMS 管理员是一个角色（后端角色码 RMS_ADMIN），不是权限码；其权限由后端
// cim-system 的 DbLocalAuthorityLoader 按 sys_role_perm 展开为下列 rms:* 码，
// 超管另追加 SUPER_ADMIN（见 lib/permissions.ts）。

// ---- 配方 ----
export const RECIPE_VIEW = 'rms:recipe:view';
export const RECIPE_CREATE = 'rms:recipe:create';
export const RECIPE_UPDATE = 'rms:recipe:update';
export const RECIPE_DELETE = 'rms:recipe:delete';
export const RECIPE_ACTIVATE = 'rms:recipe:activate';
export const RECIPE_VERSION = 'rms:recipe:version';

// ---- 设备类型 ----
export const DEVICE_TYPE_VIEW = 'rms:device-type:view';
export const DEVICE_TYPE_CREATE = 'rms:device-type:create';
export const DEVICE_TYPE_UPDATE = 'rms:device-type:update';
export const DEVICE_TYPE_DELETE = 'rms:device-type:delete';

// ---- 设备区域 ----
export const DEVICE_AREA_VIEW = 'rms:device-area:view';
export const DEVICE_AREA_CREATE = 'rms:device-area:create';
export const DEVICE_AREA_UPDATE = 'rms:device-area:update';
export const DEVICE_AREA_DELETE = 'rms:device-area:delete';

// ---- 设备台账 ----
export const DEVICE_VIEW = 'rms:device:view';
export const DEVICE_CREATE = 'rms:device:create';
export const DEVICE_UPDATE = 'rms:device:update';
export const DEVICE_DELETE = 'rms:device:delete';

// ---- 签核流 ----
export const SIGNOFF_VIEW = 'rms:signoff:view';
export const SIGNOFF_APPROVE = 'rms:signoff:approve';

// ---- 比对 ----
export const COMPARE_VIEW = 'rms:compare:view';

// ---- 审计 ----
export const AUDIT_VIEW = 'rms:audit:view';

// ---- 参数规格 / 模板 ----
export const SPEC_VIEW = 'rms:spec:view';
export const SPEC_MANAGE = 'rms:spec:manage';

// ---- 设备授权 ----
export const DEVICE_AUTH_VIEW = 'rms:device-auth:view';
export const DEVICE_AUTH_MANAGE = 'rms:device-auth:manage';

// ---- Bypass ----
export const BYPASS_VIEW = 'rms:bypass:view';
export const BYPASS_MANAGE = 'rms:bypass:manage';

// ---- 资格矩阵 ----
export const QUALIFICATION_VIEW = 'rms:qualification:view';
export const QUALIFICATION_MANAGE = 'rms:qualification:manage';

// ---- Golden 机差 ----
export const GOLDEN_VIEW = 'rms:golden:view';
export const GOLDEN_MANAGE = 'rms:golden:manage';

// ---- EC/SV ----
export const ECSV_VIEW = 'rms:ecsv:view';
export const ECSV_MANAGE = 'rms:ecsv:manage';

// ---- Body IP 保护 ----
export const BODY_VIEW = 'rms:body:view';
export const BODY_EXPORT = 'rms:body:export';

// ---- Body 模板（W10） ----
export const BODY_TEMPLATE_VIEW = 'rms:btemplate:view';
export const BODY_TEMPLATE_MANAGE = 'rms:btemplate:manage';

// ---- 实验配方通道（W11） ----
export const EXPERIMENT_VIEW = 'rms:experiment:view';
export const EXPERIMENT_MANAGE = 'rms:experiment:manage';

// ---- 报表 / 集成 ----
export const REPORT_VIEW = 'rms:report:view';
export const INTEGRATION_VIEW = 'rms:integration:view';
export const INTEGRATION_OPERATE = 'rms:integration:operate';
