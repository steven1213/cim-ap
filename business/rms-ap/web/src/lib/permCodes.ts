// RMS 权限码常量（前端侧镜像，与 server `security/RmsPermissions` 一一对应）。
// 新增权限码时**两侧都要加**，避免「按钮可见但接口 403」。

export const RMS_ADMIN = 'rms:admin'; // 占位：W2 阶段超管/管理员的短路标识

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
