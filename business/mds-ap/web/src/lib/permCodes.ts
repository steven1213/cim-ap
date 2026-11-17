// MDS 权限码常量（前端镜像）。
//
// ⚠️ 三处必须同步：后端 `MdsPermissions`（鉴权）→ `MdsConsoleCatalog.permissions()`（入库）
//    → 本文件（前端 `<Can perm>` 引用）。只改一处会得到「按钮在但 403」或「权限在但按钮不显示」。
//
// 只用于体验（按钮显隐、路由守卫）；权威鉴权永远在后端 `@PreAuthorize`。

export const MDS_PERMS = {
  site: {
    view: 'mds:site:view',
    create: 'mds:site:create',
    update: 'mds:site:update',
    delete: 'mds:site:delete',
  },
  fab: {
    view: 'mds:fab:view',
    create: 'mds:fab:create',
    update: 'mds:fab:update',
    delete: 'mds:fab:delete',
  },
  area: {
    view: 'mds:area:view',
    create: 'mds:area:create',
    update: 'mds:area:update',
    delete: 'mds:area:delete',
  },
  bay: {
    view: 'mds:bay:view',
    create: 'mds:bay:create',
    update: 'mds:bay:update',
    delete: 'mds:bay:delete',
  },
  stage: {
    view: 'mds:stage:view',
    create: 'mds:stage:create',
    update: 'mds:stage:update',
    delete: 'mds:stage:delete',
  },
  tech: {
    view: 'mds:tech:view',
    create: 'mds:tech:create',
    update: 'mds:tech:update',
    delete: 'mds:tech:delete',
  },
  product: {
    view: 'mds:product:view',
    create: 'mds:product:create',
    update: 'mds:product:update',
    delete: 'mds:product:delete',
  },
  // 数据字典：码源在平台 cim-system PermissionCodes（sys:dict:*），MDS 目录入库沿用
  dict: {
    view: 'sys:dict:list',
    save: 'sys:dict:save',
    delete: 'sys:dict:remove',
  },
  equipmentType: {
    view: 'mds:equipmentType:view',
    create: 'mds:equipmentType:create',
    update: 'mds:equipmentType:update',
    delete: 'mds:equipmentType:delete',
  },
  equipment: {
    view: 'mds:equipment:view',
    create: 'mds:equipment:create',
    update: 'mds:equipment:update',
    delete: 'mds:equipment:delete',
  },
  capability: {
    view: 'mds:capability:view',
    create: 'mds:capability:create',
    update: 'mds:capability:update',
    delete: 'mds:capability:delete',
  },
  bank: {
    view: 'mds:bank:view',
    create: 'mds:bank:create',
    update: 'mds:bank:update',
    delete: 'mds:bank:delete',
  },
  // 状态模型：模型/状态集/转换矩阵均页面维护（SEMI E30 状态机口径）
  stateModel: {
    view: 'mds:stateModel:view',
    create: 'mds:stateModel:create',
    update: 'mds:stateModel:update',
    delete: 'mds:stateModel:delete',
  },
  // 编码规则：配置化 Key Generation（模板段 + 分桶计数器），各域 create 同事务守门
  namingRule: {
    view: 'mds:namingRule:view',
    create: 'mds:namingRule:create',
    update: 'mds:namingRule:update',
    delete: 'mds:namingRule:delete',
  },
  // 制造流程：Route 口径（XML 画布为源 + 拖拽编辑 + 图校验发布）
  flow: {
    view: 'mds:flow:view',
    create: 'mds:flow:create',
    update: 'mds:flow:update',
    delete: 'mds:flow:delete',
  },
  // 原因码：WIP 事件归因主数据（树即码 + 事件快照治理）
  reason: {
    view: 'mds:reason:view',
    create: 'mds:reason:create',
    update: 'mds:reason:update',
    delete: 'mds:reason:delete',
  },
} as const;
