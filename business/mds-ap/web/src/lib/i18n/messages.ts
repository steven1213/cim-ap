// MDS 内置文案包（随前端发版）。
//
// 约定：
// - 键命名空间固定 `mds.*`，中英**成对**出现（`npm run check:i18n` 会校验对等与占位符）；
// - 组件库 @cim/ui 不收文案，页面向其传 `titleKey` / `labelKey`，翻译在这里发生；
// - 后端 `MdsConsoleCatalog.texts()` 里另有同一批菜单键的副本，供服务端 i18n 与动态菜单用，
//   改菜单名时**两处都要改**（前端内置包 + 后端目录）。

export const zhCN: Record<string, string> = {
  'mds.nav.dashboard': '概览',
  'mds.nav.group.site': '场地主数据',
  // ---------------- 外壳 ----------------
  'mds.shell.brandSub': '主数据服务',
  'mds.shell.tagline': '定义向下、实绩向上',
  'mds.shell.toggleNav': '收起/展开侧栏',
  'mds.shell.toggleGroup': '收起/展开分组',
  'mds.shell.toggleTheme': '切换主题',
  'mds.shell.theme.dark': '切换到深色',
  'mds.shell.theme.light': '切换到浅色',
  'mds.shell.logout': '退出登录',
  'mds.shell.language': '语言',
  'mds.shell.tabs.label': '页签',
  'mds.shell.tabs.scrollLeft': '向左滚动',
  'mds.shell.tabs.scrollRight': '向右滚动',
  'mds.shell.tabs.close': '关闭标签页',
  'mds.shell.tabs.closeThis': '关闭当前',
  'mds.shell.tabs.closeOthers': '关闭其他',
  'mds.shell.tabs.closeRight': '关闭右侧',
  'mds.shell.tabs.closeAll': '关闭全部',

  // ---------------- 菜单 ----------------
  'mds.nav.sites': '基地',
  'mds.nav.fabs': '晶圆厂',
  'mds.nav.areas': '区域',

  // ---------------- 通用 ----------------
  'mds.common.loading': '加载中…',
  'mds.common.retry': '重试',
  'mds.common.save': '保存',
  'mds.common.cancel': '取消',
  'mds.common.delete': '删除',
  'mds.common.edit': '编辑',
  'mds.common.search': '搜索编码或名称',
  'mds.common.actions': '操作',
  'mds.common.remark': '备注',
  'mds.common.required': '必填',
  'mds.common.dash': '—',
  'mds.common.confirmDelete': '删除后不可恢复，确认删除？',
  'mds.common.deleted': '已删除',
  'mds.common.enabled': '启用',
  'mds.common.all': '全部',
  'mds.common.none': '无数据',
  'mds.common.backHome': '返回概览',

  // ---------------- 登录 / 启动 ----------------
  'mds.login.title': '需要登录',
  'mds.login.redirecting': '正在跳转至统一身份认证中心…',
  'mds.login.admissionDenied': '当前账号没有 MDS 的准入权限，请联系系统管理员开通后再试。',
  'mds.login.ssoFailed': '登录未完成，请重新登录。',
  'mds.login.switchAccount': '换个账号登录',
  'mds.login.devPreview': 'DEV 预览（粘贴 IAM 签发的 JWT）',
  'mds.login.devApply': '以本地令牌进入',
  'mds.boot.backendDown': '连不上 MDS 后端',
  'mds.boot.backendDownHint': '页面没能取到用户档案与权限。通常是后端未启动或端口不通（本地默认 8082），也可能刚重启。服务恢复后点「重试」即可，不需要重新登录。',

  // ---------------- 403 / 404 ----------------
  'mds.forbidden': '无权访问',
  'mds.forbiddenDesc': '当前账号没有访问该页面的权限。如需开通，请联系系统管理员。',
  'mds.notFound': '页面不存在',
  'mds.notFoundDesc': '地址可能输错，或该功能已下线。',

  // ---------------- 概览 ----------------
  'mds.dashboard.title': '主数据概览',
  'mds.dashboard.kpi.sites': '基地',
  'mds.dashboard.kpi.fabs': '晶圆厂',
  'mds.dashboard.kpi.areas': '区域',
  'mds.dashboard.kpi.disabled': '其中停用 {{n}} 个',
  'mds.dashboard.quick': '常用入口',
  'mds.dashboard.siteList': '基地一览',
  'mds.dashboard.emptyHint': '还没有基地数据。先建基地，再往下建 Fab 与 Area——层级是 Site → Fab → Area。',

  // ---------------- 基地 ----------------
  'mds.site.createNew': '新建基地',
  'mds.site.code': '基地编码',
  'mds.site.name': '基地名称',
  'mds.site.address': '地址',
  'mds.site.contactPerson': '负责人',
  'mds.site.contactPhone': '联系电话',
  'mds.site.fabCount': 'Fab 数',
  'mds.site.deleteBlocked': '该基地下还有 Fab，先把 Fab 删掉或改到其他基地，再删基地',

  // ---------------- 晶圆厂 ----------------
  'mds.fab.createNew': '新建 Fab',
  'mds.fab.code': 'Fab 编码',
  'mds.fab.name': 'Fab 名称',
  'mds.fab.site': '所属基地',
  'mds.fab.sitePlaceholder': '选择所属基地',
  'mds.fab.type': '工厂类型',
  'mds.fab.amhsType': 'AMHS 类型',
  'mds.fab.cleanGrade': '洁净等级',
  'mds.fab.deleteBlocked': '该 Fab 下还有 Area，先把 Area 删掉或改到其他 Fab，再删 Fab',

  // ---------------- 区域 ----------------
  'mds.area.createNew': '新建区域',
  'mds.area.code': '区域编码',
  'mds.area.name': '区域名称',
  'mds.area.fab': '所属 Fab',
  'mds.area.fabPlaceholder': '选择所属 Fab',
  'mds.area.type': '工艺大类',
};

export const enUS: Record<string, string> = {
  'mds.nav.dashboard': 'Overview',
  'mds.nav.group.site': 'Site master data',
  // ---------------- shell ----------------
  'mds.shell.brandSub': 'Master Data Service',
  'mds.shell.tagline': 'Definitions down, actuals up',
  'mds.shell.toggleNav': 'Collapse or expand navigation',
  'mds.shell.toggleGroup': 'Collapse or expand group',
  'mds.shell.toggleTheme': 'Toggle theme',
  'mds.shell.theme.dark': 'Switch to dark',
  'mds.shell.theme.light': 'Switch to light',
  'mds.shell.logout': 'Sign out',
  'mds.shell.language': 'Language',
  'mds.shell.tabs.label': 'Tabs',
  'mds.shell.tabs.scrollLeft': 'Scroll left',
  'mds.shell.tabs.scrollRight': 'Scroll right',
  'mds.shell.tabs.close': 'Close tab',
  'mds.shell.tabs.closeThis': 'Close this',
  'mds.shell.tabs.closeOthers': 'Close others',
  'mds.shell.tabs.closeRight': 'Close to the right',
  'mds.shell.tabs.closeAll': 'Close all',

  // ---------------- navigation ----------------
  'mds.nav.sites': 'Sites',
  'mds.nav.fabs': 'Fabs',
  'mds.nav.areas': 'Areas',

  // ---------------- common ----------------
  'mds.common.loading': 'Loading…',
  'mds.common.retry': 'Retry',
  'mds.common.save': 'Save',
  'mds.common.cancel': 'Cancel',
  'mds.common.delete': 'Delete',
  'mds.common.edit': 'Edit',
  'mds.common.search': 'Search code or name',
  'mds.common.actions': 'Actions',
  'mds.common.remark': 'Remark',
  'mds.common.required': 'Required',
  'mds.common.dash': '—',
  'mds.common.confirmDelete': 'This cannot be undone. Delete anyway?',
  'mds.common.deleted': 'Deleted',
  'mds.common.enabled': 'Enabled',
  'mds.common.all': 'All',
  'mds.common.none': 'No data',
  'mds.common.backHome': 'Back to overview',

  // ---------------- login / boot ----------------
  'mds.login.title': 'Sign-in required',
  'mds.login.redirecting': 'Redirecting to identity provider…',
  'mds.login.admissionDenied': 'This account has no admission to MDS. Ask your administrator to grant it, then try again.',
  'mds.login.ssoFailed': 'Sign-in did not complete. Please try again.',
  'mds.login.switchAccount': 'Sign in with another account',
  'mds.login.devPreview': 'DEV preview (paste a JWT issued by IAM)',
  'mds.login.devApply': 'Enter with this token',
  'mds.boot.backendDown': 'Cannot reach the MDS backend',
  'mds.boot.backendDownHint': 'The console could not load your profile and permissions. Usually the backend is not running or the port is unreachable (8082 in local dev). Press Retry once it is back — no need to sign in again.',

  // ---------------- 403 / 404 ----------------
  'mds.forbidden': 'Access denied',
  'mds.forbiddenDesc': 'This account has no permission for this page. Ask your administrator to grant it.',
  'mds.notFound': 'Page not found',
  'mds.notFoundDesc': 'The address may be wrong, or the feature has been retired.',

  // ---------------- dashboard ----------------
  'mds.dashboard.title': 'Master data overview',
  'mds.dashboard.kpi.sites': 'Sites',
  'mds.dashboard.kpi.fabs': 'Fabs',
  'mds.dashboard.kpi.areas': 'Areas',
  'mds.dashboard.kpi.disabled': '{{n}} disabled',
  'mds.dashboard.quick': 'Shortcuts',
  'mds.dashboard.siteList': 'Sites at a glance',
  'mds.dashboard.emptyHint': 'No site yet. Create a site first, then Fabs and Areas — the hierarchy is Site → Fab → Area.',

  // ---------------- sites ----------------
  'mds.site.createNew': 'New site',
  'mds.site.code': 'Site code',
  'mds.site.name': 'Site name',
  'mds.site.address': 'Address',
  'mds.site.contactPerson': 'Owner',
  'mds.site.contactPhone': 'Phone',
  'mds.site.fabCount': 'Fabs',
  'mds.site.deleteBlocked': 'This site still has fabs. Delete them or move them to another site first.',

  // ---------------- fabs ----------------
  'mds.fab.createNew': 'New fab',
  'mds.fab.code': 'Fab code',
  'mds.fab.name': 'Fab name',
  'mds.fab.site': 'Site',
  'mds.fab.sitePlaceholder': 'Select a site',
  'mds.fab.type': 'Fab type',
  'mds.fab.amhsType': 'AMHS type',
  'mds.fab.cleanGrade': 'Clean grade',
  'mds.fab.deleteBlocked': 'This fab still has areas. Delete them or move them to another fab first.',

  // ---------------- areas ----------------
  'mds.area.createNew': 'New area',
  'mds.area.code': 'Area code',
  'mds.area.name': 'Area name',
  'mds.area.fab': 'Fab',
  'mds.area.fabPlaceholder': 'Select a fab',
  'mds.area.type': 'Process family',
};
