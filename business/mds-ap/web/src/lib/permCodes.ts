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
} as const;
