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
  // 机型模板（v2 取代 equipmentType；ISA-95 EquipmentClass 口径，带默认约束）
  equipmentClass: {
    view: 'mds:equipmentClass:view',
    create: 'mds:equipmentClass:create',
    update: 'mds:equipmentClass:update',
    delete: 'mds:equipmentClass:delete',
  },
  // 机台组（v2 新增；ISA-95 Work Center 派工候选池，成员守门同机型）
  toolGroup: {
    view: 'mds:toolGroup:view',
    create: 'mds:toolGroup:create',
    update: 'mds:toolGroup:update',
    delete: 'mds:toolGroup:delete',
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
    validate: 'mds:flow:validate',
  },
  // ICM 统一指派（V22：ContextChain 三 scope 一张表，三层分开授权）
  opAssign: {
    view: 'mds:opAssign:view',
    create: 'mds:opAssign:create',
    update: 'mds:opAssign:update',
    delete: 'mds:opAssign:delete',
  },
  flowAssign: {
    view: 'mds:flowAssign:view',
    create: 'mds:flowAssign:create',
    update: 'mds:flowAssign:update',
    delete: 'mds:flowAssign:delete',
  },
  stepAssign: {
    view: 'mds:stepAssign:view',
    create: 'mds:stepAssign:create',
    update: 'mds:stepAssign:update',
    delete: 'mds:stepAssign:delete',
  },
  // KV 约束参数（context 三层）
  opAttr: { view: 'mds:opAttr:view', update: 'mds:opAttr:update' },
  flowAttr: { view: 'mds:flowAttr:view', update: 'mds:flowAttr:update' },
  stepAttr: { view: 'mds:stepAttr:view', update: 'mds:stepAttr:update' },
  // 参数定义字典 / 产品绑定
  paramDef: {
    view: 'mds:paramDef:view',
    create: 'mds:paramDef:create',
    update: 'mds:paramDef:update',
    delete: 'mds:paramDef:delete',
  },
  // EDC 工程数采（V28：参数集 / 设备接口映射 / 方案）
  edcSet: {
    view: 'mds:edcSet:view',
    create: 'mds:edcSet:create',
    update: 'mds:edcSet:update',
    delete: 'mds:edcSet:delete',
  },
  edcTpl: {
    view: 'mds:edcTpl:view',
    create: 'mds:edcTpl:create',
    update: 'mds:edcTpl:update',
    delete: 'mds:edcTpl:delete',
  },
  edcMap: {
    view: 'mds:edcMap:view',
    create: 'mds:edcMap:create',
    update: 'mds:edcMap:update',
    delete: 'mds:edcMap:delete',
  },
  edcPlan: {
    view: 'mds:edcPlan:view',
    create: 'mds:edcPlan:create',
    update: 'mds:edcPlan:update',
    delete: 'mds:edcPlan:delete',
  },
  productFlow: {
    view: 'mds:productFlow:view',
    create: 'mds:productFlow:create',
    update: 'mds:productFlow:update',
    delete: 'mds:productFlow:delete',
  },
  // 工序库：P-T-R-O 四层建模 O 层（Operation 独立主数据，跨路线复用；V20）
  operation: {
    view: 'mds:operation:view',
    create: 'mds:operation:create',
    update: 'mds:operation:update',
    delete: 'mds:operation:delete',
  },
  // 配方解算契约（V29）：POST /recipe/resolve 执行权（工序可变参数维护复用 operation.update）
  recipeResolve: {
    resolve: 'mds:recipe:resolve',
  },
  // 原因码：WIP 事件归因主数据（树即码；消费方 MES 运行域按事件引用）
  reason: {
    view: 'mds:reason:view',
    create: 'mds:reason:create',
    update: 'mds:reason:update',
    delete: 'mds:reason:delete',
  },
  // 班组排班（V17）：班次/班制/排班三块共用一组码（维护人是同一批管理员）
  shift: {
    view: 'mds:shift:view',
    create: 'mds:shift:create',
    update: 'mds:shift:update',
    delete: 'mds:shift:delete',
  },
  // 交接班记录：值班人员全员可写，与排班管理员分开
  shiftlog: {
    view: 'mds:shiftlog:view',
    save: 'mds:shiftlog:save',
  },
  // 换班申请（V19）：员工自助发起，全员可写，与排班管理员分开
  shiftswap: {
    view: 'mds:shiftswap:view',
    save: 'mds:shiftswap:save',
  },
} as const;

/** 平台管理码（端点在 cim-system，与后端 PermissionCodes 对齐——菜单维护页双闸门之一）。 */
export const SYS = {
  MENU_LIST: 'sys:menu:list',
  MENU_SAVE: 'sys:menu:save',
  MENU_REMOVE: 'sys:menu:remove',
  ROLE_LIST: 'sys:role:list',
  ROLE_GRANT: 'sys:role:grant',
  I18N_SAVE: 'sys:i18n:save',
} as const;
