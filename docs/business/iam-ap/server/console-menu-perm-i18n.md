# IAM 控制台菜单 · 按钮权限 · 多语言设计（控制面配置化）

> 状态：**设计稿（待评审）** · 2026-10-09
> 上游：`README.md` §0 定位与设计原则 / §4 已落地能力；`docs/platform/server/README.md` §7（国际化）、§21.1（RBAC 权限模型）；`docs/platform/server/design.md` §2.7（cim-i18n-starter）、§2.10（cim-system）
> 关联：`docs/platform/web/README.md` §3（认证与路由守卫）、§4（权限体系：菜单/按钮/接口）、§6（国际化热切换）
> 范围：把 IAM 控制台的**导航菜单、功能按钮、界面文案**从「前端硬编码」改为「**入库 + 后端下发 + 多语言**」。不含业务系统的内部权限（那是各业务 ap 自己的事，见 §2 边界）。

## 0. 一句话结论

这三件事**平台侧早已设计好**，本设计的工作是「**把平台能力真正接进 IAM 控制台并补齐唯一缺口（i18n starter 仅占位）**」，而不是另造一套：

| 诉求 | 平台既有资产 | 本次要做的 |
| --- | --- | --- |
| 菜单父子节点、入库 | `cim-system` 的 `sys_menu`（`parent_id` / `type=DIR\|MENU\|BUTTON` / `i18n_code` / `perm_code` / `sort_no` / `visible`）+ `MenuTrees` 树构建 | IAM ap 引入 `cim-system`，把控制台菜单**种子入库**，前端改为**后端下发** |
| 按钮/接口权限、入库 | `sys_permission`（`module:res:action`）+ `sys_role_perm` + `sys_role_menu` + `@PreAuthorize` | 定义 IAM 控制台**权限码清单**并种子入库，前端加 `<Perms>` |
| 多语言、2 张表 | `README §7` 已定义 `sys_locale` + `sys_i18n`（`UNIQUE(locale_code, code)`）、四级兜底链 | **实现** `cim-i18n-starter`（当前是占位类）+ IAM 控制台文案种子 |

---

## 1. 现状与差距

| 能力 | 现状（IAM 控制台） | 目标 |
| --- | --- | --- |
| 导航菜单 | 前端 `web/src/lib/menu.tsx` **硬编码注册表**，`Layout.tsx` 按 `admin` 布尔过滤；改名/加项要发版 | `sys_menu` 父子树入库，后端按**角色**下发，前端**动态生成**菜单与路由 |
| 功能按钮 | **无**（只有页面级 `admin` 布尔 + 路由守卫） | 按钮/接口同一套权限码入库；前端 `<Perms code>` 不渲染无权限按钮，后端 `@PreAuthorize` 兜底 |
| 界面文案 | **写死中文**在 tsx 里 | `sys_locale` + `sys_i18n` 两表，DB 驱动、热更新、`zh-CN` 兜底 |
| 权限判定 | `isIamAdmin(user)` 读令牌 `roles` claim 的 `iam-ap:ADMIN` | 权威源改为**本 ap 库内 RBAC**（`cim-system`），令牌 claim 仅作兜底（§4.5） |

**唯一硬缺口**：`cim-i18n-starter` 当前只有 `CimI18nMarker` 占位类（`platform/server/cim-i18n-starter/.../CimI18nMarker.java`），`MessageResolver` 的默认实现 `DefaultMessageResolver` 直接返回兜底文案、**不做 i18n**。需按 `README §7` 实现。

---

## 2. 分层与边界（先厘清，否则会踩 §0 铁律）

> IAM 定位：**只管跨业务准入，不管业务系统内部权限**（`README §0`）。但「IAM 控制台自己的菜单/按钮」**不是**业务系统内部权限——它**就是 IAM 这个 ap 的内部权限**。

| 关注点 | 归属 | 说明 |
| --- | --- | --- |
| 能否进入 IAM ap（准入） | **IAM 自有**（`apps` claim） | 不变 |
| IAM 控制台内**菜单可见性 / 按钮 / 接口权限** | **平台 `cim-system`** | IAM 作为一个 ap，复用平台 RBAC；`sys_*` 表建在 **IAM 自己的库** |
| 界面**多语言** | **平台 `cim-i18n-starter`** | 平台级能力，所有 ap 复用 |
| 业务 ap（mds/mes）的内部菜单/按钮 | 各业务 ap 自建 | **IAM 不介入**（边界铁律不变） |

- **菜单与权限分离**（`README §21.1`）：菜单（导航）与权限（API/按钮）是两套数据；导航可见性由 `sys_role_menu` 决定，接口可调用性由 `sys_role_perm` 决定。二者由**同一角色**关联。
- **一张权限码贯穿三级**（`platform/web §4`）：菜单（路由过滤）、按钮（`<Perms>`）、接口（`@PreAuthorize`）共用 `module:res:action` 权限码，前后端语义一致。

---

## 3. 菜单模型（父子节点入库）

### 3.1 表结构（**复用** `cim-system` 的 `sys_menu`，不新建表）

已落地实体 `platform/server/cim-system/.../menu/SysMenu.java` + 迁移 `db/migration/system/{h2,mysql,postgresql,oracle,dm}/V1000__init_system_tables.sql`（版本段见 §8.1 / ADR-10）。

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | varchar(64) PK | `IdGenerator`（雪花/UUIDv7） |
| `parent_id` | varchar(64) NULL | **父节点**；顶级为 `null`（或 `"0"`） |
| `path` | varchar(256) | 前端路由（如 `/orgs`）；目录节点可为空 |
| `component` | varchar(256) | 前端组件标识（供动态路由映射组件） |
| `i18n_code` | varchar(128) | **名称 i18n 键**（本表**不存**中文名，见 §5） |
| `icon` | varchar(64) | 图标标识（前端图标名，非组件） |
| `type` | enum | `DIR` 目录 / `MENU` 菜单 / `BUTTON` 按钮 |
| `perm_code` | varchar(128) | 关联权限码（`BUTTON` 必填；`MENU` 可空） |
| `sort_no` | int | 同级排序 |
| `visible` | boolean | 是否在导航中可见（`BUTTON` 恒不显示） |
| `status` | enum | `ENABLED` / `DISABLED` |

> 关键点：**菜单只存 `i18n_code`，不存字面量名称**——这是与 `§7 全量 DB i18n` 一致的硬约束，避免「名称硬编码」与多语言矛盾（`SysMenu` 类注释已写明）。

### 3.2 树构建

- 后端 `SysMenuService.tree(includeButtons)` / `rbacService.menuTree(userId, includeButtons)` 已实现父子组装（`MenuNode` / `MenuTrees`）。
- **两种视角**：
  - **用户视角**（`GET /sys/me/menus`）：按当前用户角色的 `sys_role_menu` 过滤，`type ∈ {DIR, MENU}`，供前端生成导航与路由。
  - **管理视角**（`GET /sys/menus/tree?includeButtons=true`）：全量树含 `BUTTON`，供「角色授权」界面勾选。

### 3.3 IAM 控制台菜单种子（把现有 10 项入库）

现有 `menu.tsx` 的分组（cap）与条目 → `sys_menu` 行。分组升格为 `DIR` 节点，条目为 `MENU`，页面内按钮为 `BUTTON`：

| parent | type | path | i18n_code | icon | perm_code |
| --- | --- | --- | --- | --- | --- |
| — | MENU | `/` | `iam.menu.overview` | `gauge` | — |
| — | DIR | — | `iam.menu.group.identity` | — | — |
| ↑ | MENU | `/orgs` | `iam.menu.orgs` | `tree` | `iam:org:list` |
| ↑ | MENU | `/users` | `iam.menu.users` | `users` | `iam:user:list` |
| ↑ | MENU | `/lockouts` | `iam.menu.lockouts` | `lock` | `iam:user:list` |
| — | DIR | — | `iam.menu.group.access` | — | — |
| ↑ | MENU | `/apps` | `iam.menu.apps` | `apps` | `iam:app:list` |
| ↑ | MENU | `/admissions` | `iam.menu.admissions` | `link` | `iam:admission:list` |
| ↑ | MENU | `/org-grants` | `iam.menu.orgGrants` | `org-grant` | `iam:grant:list` |
| ↑ | MENU | `/roles` | `iam.menu.roles` | `shield` | `iam:role:list` |
| — | DIR | — | `iam.menu.group.security` | — | — |
| ↑ | MENU | `/sessions` | `iam.menu.sessions` | `monitor` | `iam:session:list` |
| ↑ | MENU | `/audit` | `iam.menu.audit` | `list` | `iam:audit:list` |
| ↑ | MENU | `/settings` | `iam.menu.settings` | `sliders` | `iam:settings:view` |
| — | MENU | `/profile` | `iam.menu.profile` | `user` | — |
| — | DIR | — | `iam.menu.group.system` | — | — |
| ↑ | MENU | `/menus` | `iam.menu.menus` | `tree` | `iam:menu:list` |
| ↑ | MENU | `/permissions` | `iam.menu.permissions` | `shield` | `iam:perm:list` |
| ↑ | MENU | `/roles-admin` | `iam.menu.rolesAdmin` | `shield` | `iam:role-admin:list` |
| ↑ | MENU | `/i18n` | `iam.menu.i18n` | `list` | `iam:i18n:list` |

> 新增的 4 个管理页（菜单管理 / 权限管理 / 角色管理 / 多语言）见 §7.4；它们是「配置化」的**自助闭环**——菜单/权限/文案以后由管理界面维护，无需发版。

### 3.4 前端注册表 → 动态菜单的迁移

| 现状（`menu.tsx`） | 迁移后 |
| --- | --- |
| `MenuEntry.label`（中文硬编码） | 后端 `i18n_code` → 前端 `t(i18n_code)` |
| `MenuEntry.icon: ReactNode` | 后端 `icon: string` → 前端**图标注册表**（字符串→组件）映射 |
| `MenuEntry.admin: boolean` | 后端按 `sys_role_menu` 过滤（前端不再判断） |
| `MenuEntry.to / sub / end` | `path` + `component` 动态路由；`sub`/`desc` 并入 i18n（可选键） |
| `MenuGroup.cap` | `type=DIR` 节点 |

---

## 4. 权限模型（按钮 / 接口入库）

### 4.1 权限点 `sys_permission`

已落地实体 `SysPermission`：`code`（`module:res:action`，`UNIQUE(code, tenant_id, deleted)`）、`module` / `res` / `action`、`name`、`status`。

### 4.2 按钮权限挂在 `sys_menu.type=BUTTON`

新增/编辑/删除/导出等按钮行：`type=BUTTON`，`perm_code` 指向 §4.4 的权限码，`parent_id` 挂到所属 `MENU` 下（**不出现在导航**，仅用于授权界面勾选与前端 `<Perms>` 判定）。

### 4.3 角色授权（两张表、两个端点，不合并）

| 表 | 作用 | 端点 |
| --- | --- | --- |
| `sys_role_menu` | 角色 → **可见菜单**（导航过滤） | 角色菜单授权 |
| `sys_role_perm` | 角色 → **可调用权限**（`@PreAuthorize`） | 角色权限授权 |
| `sys_user_role` | 用户 → 角色 | 用户角色分配 |

> 写为「先删后插」的**覆盖式授权**（幂等），见 `README §21.1` 落地要点 4。

### 4.4 IAM 控制台权限码清单（`module:res:action`）

| 模块 | 权限码 |
| --- | --- |
| 概览 | `iam:overview:view` *（实现补充）* |
| 组织 | `iam:org:list` `iam:org:create` `iam:org:update` `iam:org:move` `iam:org:delete` `iam:org:member` |
| 用户/档案 | `iam:user:list` `iam:user:create` `iam:user:update` `iam:user:status` `iam:user:reset-pwd` `iam:user:unlock` `iam:user:orgs` `iam:user:delete` |
| 应用注册 | `iam:app:list` `iam:app:create` `iam:app:update` |
| 个人准入 | `iam:admission:list` `iam:admission:grant` `iam:admission:revoke` |
| 组织授予 | `iam:grant:list` `iam:grant:grant` `iam:grant:revoke` |
| 角色组 | `iam:role:list` |
| 会话 | `iam:session:list` `iam:session:kick` |
| 审计 | `iam:audit:list` |
| 设置 | `iam:settings:view` |
| 目录同步 | `iam:sync:run` *（实现补充）* |
| 菜单管理 | `iam:menu:list` `iam:menu:create` `iam:menu:update` `iam:menu:delete` |
| 权限管理 | `iam:perm:list` `iam:perm:create` `iam:perm:update` `iam:perm:delete` |
| 角色管理 | `iam:role-admin:list` `iam:role-admin:create` `iam:role-admin:update` `iam:role-admin:delete` `iam:role-admin:grant` |
| 多语言 | `iam:i18n:list` `iam:i18n:create` `iam:i18n:update` `iam:i18n:delete` `iam:i18n:locale` |
| 类级兜底 | `iam:console:admin` *（实现补充）* |

> 三个「实现补充」：① `iam:overview:view` 给总览端点一个明确码（原表未列）；
> ② `iam:sync:run` 区分「手动触发 AD 同步」这一特权动作；
> ③ **`iam:console:admin`** 是**类级兜底码**——各管理控制器保留类级 `@PreAuthorize`，
> 方法级优先，未逐个标注的方法退回本码，使「新增端点忘写方法级注解」的最坏结果是
> 「仅控制台管理员可调」而非「任何人可调」。旧的两段式 `iam-ap:ADMIN` 因不满足
> `module:res:action` 校验（会被 `cim-system` 的 `SysPermissionService` 拒绝），
> 由 `iam:console:admin` 取代。
>
> ⚠️ **「方法级优先」的确切含义 = 覆盖，不是「与」**（2026-10-09 实测钉死）：
> Spring Security 取「最具体」的注解（`AuthorizationAnnotationUtils` 先查方法、命中即返回，
> 不再看类），因此类级与方法级**不会**做 AND 叠加。实测：只持 `iam:user:list`、**不含**
> `iam:console:admin` 时 `GET /api/v1/admin/users` 返回 **200**；只持 `iam:console:admin`
> 时返回 **403**。由 `UserListProjectionTest#methodLevelPermissionOverridesClassLevelUmbrella` 固化。
> 推论：IAM 各控制器每个端点都带自己的细码，故**类级注解当前实际不生效**，只作未来漏标的防线；
> 判断「某人能否调某端点」只需看该端点的方法级码。若要把类级改成真正的「与」，必须显式在
> 方法级表达式里写出两个 `hasAuthority`（并同步前端入口闸门口径）。
>
> **✅ W3 已收口**：前端已不再读令牌 `roles` claim——控制台权限改由 `GET /sys/me/permissions`
> （库内 RBAC 解析结果）驱动，`isIamAdmin()` 已删除（见 §7.2）。

- 常量集中到 IAM 侧的 `support/IamPermissionCodes`（对齐 `cim-system` 的 `support/PermissionCodes`）。
- 后端各 `*AdminController` 的类级 `hasAuthority('iam-ap:ADMIN')` **已改为方法级** `hasAuthority('iam:xxx:yyy')`；
  类级保留 `iam:console:admin` 作**兜底安全网**（**刻意不移除**——它把「漏标方法级注解」的后果限制为
  「只有控制台管理员能调」，是低成本高收益的防线；§9-2 的"一步切"即指方法级已全量落地）。

- ⚠️ **超管与 `sys:*` 的关系**：`DbLocalAuthorityLoader` 对超管**短路展开为「库内全部启用权限码」**。
  故 `sys_permission` 目录若为空，超管的 authorities 里**不含** `sys:menu:list` 之类，
  而 `@PreAuthorize("hasAuthority('sys:menu:list')")` 走**精确串匹配**（不查 `PermissionEvaluator`），
  会让超管被 `cim-system` 自己的端点拒绝。**故 `cim-system` 新增 `SysPermissionSeeder`**
  在启动时种子 `PermissionCodes`（受 `cim.system.seed-permissions` 控制，默认开）。

### 4.5 与现有 `iam-ap:ADMIN` 的关系（迁移策略）

现状：`cim-auth-starter` 的 `LocalAuthorityLoader` 默认 `ClaimLocalAuthorityLoader`（读令牌 `roles` claim），故 `hasAuthority('iam-ap:ADMIN')` = 令牌里 `iam-ap` 组含 `ADMIN`。

引入 `cim-system` 后，`DbLocalAuthorityLoader` 会**顶替**默认实现（SPI + `@ConditionalOnMissingBean`，见 `design.md §2.10`），IAM 控制台权限改由**本 ap 库内 RBAC** 决定。三选一（**见 §9 决策点**）：

- **A（推荐）**：切到 `DbLocalAuthorityLoader`；用 `sys_role`（如 `IAM_ADMIN` 角色 + `sys_role_perm` 全量权限码）授权，令牌 `roles` claim 不再决定控制台权限。`cim.system.rbac.fallback-to-claims=false`（默认拒绝）。
- **B**：迁移期灰度——`fallback-to-claims=true`，库内无档案时回退令牌 claim，逐步建档后收紧。
- **C**：暂不引入 `cim-system`，自建轻量菜单/权限表（**不推荐**，与平台重复）。

> 注意 `sys_user` 是「**授权档案**」而非账号表（无口令列），按 `external_id`（IAM 令牌 `sub`）优先、`username` 兜底对齐——IAM 自己签发令牌，两个键天然一致。

---

## 5. 多语言模型（两表）

### 5.1 `sys_locale`（多语言 / 语言目录）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | varchar(64) PK | |
| `code` | varchar(32) | `zh-CN` / `en-US` / `zh-TW`…，`UNIQUE` |
| `name` | varchar(64) | 语言显示名（如「简体中文」） |
| `is_default` | boolean | 默认语言（**`zh-CN` 必须为 true**） |
| `sort_no` | int | |
| `status` | enum | `ENABLED` / `DISABLED` |

### 5.2 `sys_i18n`（翻译表，`UNIQUE(locale_code, code)`）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | varchar(64) PK | |
| `locale_code` | varchar(32) FK | →`sys_locale.code` |
| `code` | varchar(200) | i18n 键，如 `iam.menu.orgs` |
| `scope` | varchar(16) | `SYSTEM`（平台内置）/ `USER`（业务/UI） |
| `module` | varchar(64) | 归属模块（`system` / `iam`…），便于筛选与增量拉取 |
| `description` | varchar(512) | 备注（给翻译人员的上下文） |
| `content` | varchar(2000) | 译文，支持 `{name}` 占位符 |
| `tenant_id` | varchar(64) | 多租户覆盖（见 `README §10`） |

- **唯一键 = `(locale_code, code)`**（同一语言下键唯一），跨语言同键多行。
- `SCOPE=SYSTEM` 由应用启动**种子**写入、管理端锁定不可删；`SCOPE=USER` 由管理后台维护。
- 不另设键目录表：i18n 体量小，键级元数据随译文行冗余可忽略，换来零 join、更易扩展语种（`README §7` 已论证）。

> **对应你的「至少 2 张表」**：① `sys_locale`（多语言表）+ ② `sys_i18n`（`key + 语言` 唯一键的翻译表）。满足；如后续需要「键目录 / 待翻译工单」，可再加第 3 张（非本期必须）。

### 5.3 四级兜底链（自上而下，永不到底）

| 级别 | 来源 | 说明 |
| --- | --- | --- |
| L1 目标语言 | DB（→ 缓存） | 用户当前语言（`Accept-Language` / `sys_user.lang`） |
| L2 **默认兜底** | DB（→ 缓存） | **固定 `zh-CN`**——「只要有中文就一定看得懂」 |
| L3 内置兜底包 | 随 jar 发布的静态最小集 | 防 DB/缓存不可用 |
| L4 人性化降级 | `humanize(code)` | 前三级全缺才触发（`iam.menu.orgs` → `Orgs`），**同步上报缺失工单**，绝不返回裸 key |

### 5.4 键命名规范

`{namespace}.{module}.{item}`，全小写，点分层：

- `common.*`（通用：确认/取消/保存/删除…）
- `iam.menu.*`（菜单名）、`iam.page.*`（页面标题/副标题）、`iam.field.*`、`iam.btn.*`、`iam.status.*`、`iam.audit.*`
- 平台内置：`sys.error.*`、`validation.*`

### 5.5 后端实现（补齐 `cim-i18n-starter`）

```
com.cim.i18n
├── config/{CimI18nProperties, I18nAutoConfiguration}   # cim.i18n.*（enabled / default-locale / cache-ttl）
├── model/{SysLocale, SysI18n}                          # ★ 两表实体（scope: SYSTEM/USER）
├── source/{DatabaseMessageSource, I18nCache}          # DB 驱动 + 缓存（复用 cim-cache-starter）
├── seed/I18nSeedLoader                                 # 启动幂等写入 SYSTEM 级文案
├── web/{LocaleResolverCim, LocaleHeaderFilter,
│         I18nController, MissingI18nReporter}          # Accept-Language 解析 + /api/i18n/messages + 缺失上报
└── CimI18nMarker
```

- `DatabaseMessageSource extends AbstractMessageSource`，**声明为 Primary `MessageSource`**；实现 `README §7` 的四级兜底（含 `MissingI18nReporter` + `humanize`）。
- 同时提供 `MessageResolver` 实现，**顶替** `cim-spring-support` 的 `DefaultMessageResolver`（`@ConditionalOnMissingBean` 模式，见 `design.md §5`）。
- 缓存复用 `cim-cache-starter`（多级缓存 + 跨节点失效，见 `design.md §2.5`）。
- 依赖：`cim-core`、`cim-spring-support`、`cim-jpa-starter`、`cim-cache-starter`（可选）、`spring-boot-starter-web`。
- 迁移：`db/migration/i18n/{h2,mysql,postgresql,oracle,dm}/V2000__init_i18n_tables.sql`（`sys_locale` + `sys_i18n`），由 `I18nFlywayConfiguration` 追加 Flyway location（**追加**语义依赖 `cim-jpa-starter` 的 customizer 声明 `@Order(HIGHEST_PRECEDENCE)`，见 §8.1 / ADR-10）。

### 5.6 前端实现（运行时拉取 + 热切换）

- 用 `i18next` + `react-i18next` 初始化；**文案运行时从后端拉取**：`GET /api/i18n/messages?lang=&v=`（`v` 为版本号，未变则命中本地缓存）。
- 缺 key 时：开发环境控制台告警 + `POST /api/i18n/missing` 生成待翻译工单；**渲染用兜底文案**（不显示裸 key）。
- 语言切换：写 `localStorage` + 更新 `Accept-Language` 请求头 + 重拉 bundle，**不刷新页面**。
- 后端响应统一带 `Accept-Language` 解析结果；`Result.msg` 也可按 code 映射 i18n（`platform/web §2` 统一错误出口）。

### 5.7 种子

| scope | 内容 | 来源 |
| --- | --- | --- |
| SYSTEM | 平台内置（校验/异常/错误码/通用） | `I18nSeedLoader` 启动幂等写入 |
| USER | IAM 控制台菜单名/页面标题/字段/按钮/状态/审计标签 | IAM 侧 Flyway 种子（§8） |

---

## 6. 后端契约（新增 / 复用）

**复用（`cim-system` 已实现）**：

| 端点 | 说明 |
| --- | --- |
| `GET /sys/me/menus` | 当前用户**角色过滤后**的菜单树（受 `cim.system.menu.include-buttons` 控制） |
| `GET /sys/me/permissions` | 当前用户权限码集（**直接回显已注入的 authorities，不重查库**，保证前后端同源） |
| `GET /sys/me/profile` | 本地授权档案概要（未建档 `provisioned=false`） |
| `/sys/menus` `/sys/permissions` `/sys/roles` `/sys/users` | 管理端 CRUD（`BaseController` 泛型） |

**新增**：

| 端点 | 说明 |
| --- | --- |
| `GET /api/i18n/messages?lang=&v=` | 译文 bundle + 版本号（**由 `cim-i18n-starter` 提供，公开**） |
| `POST /api/i18n/missing` | 缺失 key 上报（同上） |
| `GET/POST /api/v1/admin/i18n/locales`、`DELETE .../locales/{id}` | 语言目录管理（**IAM 侧 `I18nAdminController`**） |
| `GET/POST /api/v1/admin/i18n/messages`、`DELETE .../messages/{id}` | 译文管理 |
| `GET /api/v1/admin/i18n/version`、`GET/DELETE /api/v1/admin/i18n/missing` | 版本号 / 缺失汇总与清空 |

> **路径说明（实现调整）**：多语言管理端点落在 **`/api/v1/admin/i18n/**`** 而非原拟的 `/sys/i18n/**`——
> 对齐 IAM 既有管理面前缀（`/api/v1/admin/**`），前端 axios `baseURL='/api/v1'` 可直接复用。
> 平台复用的 `cim-system` 端点仍为其原生的 **`/sys/**`**（无 `/api/v1` 前缀），前端需按绝对路径调用。
> 属「某 ap 的内部权限」的端点由各 ap 自己提供（starter 只给服务与公开运行时接口）。

**装配要点（IAM 侧）**：

- `iam-ap/server/pom.xml` 增依赖 `cim-system`（+ `cim-i18n-starter`）。✅
- 实体/仓储扫描：`iam-ap/server` 启动类 `@EntityScan/@EnableJpaRepositories` 由 `com.cim.iam.server`
  **拓宽为 `com.cim`**（与 `cim-system` 的 `CimSystemConfiguration` 同根，重复声明不冲突）。
  ✅ 实测通过。
- 迁移顺序与**版本空间**：见 §8——**必须**按版本段分配，否则多模块同库会 `FlywayException`。

---

## 7. 前端改造（`business/iam-ap/web`）

> **状态（2026-10-09，W3 已落地）**：菜单/路由/按钮权限/多语言的**框架层**已全部按本节实现并通过
> `tsc + vite build` 与接口级端到端验证；**4 个管理页（§7.4）为占位**，属 W4。
> 实现细节与踩坑见 [`../web/README.md`](../web/README.md) §0/§1.2/§4。

### 7.1 菜单：从注册表到后端驱动 ✅

- **已删除** `src/lib/menu.tsx`（硬编码注册表）与 `src/components/RequireAdmin.tsx`。
- **新增** `src/store/menuStore.ts`（`GET /sys/me/menus` → 菜单树 + 扁平可路由菜单，**按 path 去重**）、
  `src/lib/iconRegistry.ts`（`icon: string` → 组件，**未知标识回退** `IconFallback`）、
  `src/pages/pageRegistry.tsx`（`component: string` → 页面组件，**未知标识回退** `ComingSoon`）。
- `Layout.tsx` 的侧栏改为读 store：`DIR` → 分组标题、其子 `MENU` → 条目（名称 `t(i18n_code)`、
  副标题 `t(i18n_code + '.desc')`、图标 `resolveIcon(icon)`）；`visible=false` 不进导航（路由仍可达）。
- **动态路由**：`App.tsx` 由 `flat` 生成 `<Route>`；`path` → 路由、`component` → 组件、
  `permCode` → `RequirePerm` 守卫。**新增** `pages/ForbiddenPage.tsx`（403，已登录但无权限）与
  `pages/NotFoundPage.tsx`（404，路径不在菜单集内）。
- **启动闸门** `components/BootGate.tsx`：等齐「菜单 + 权限」再渲染控制台。⚠️ 这是必需的——
  否则首帧菜单为空会把当前地址判为未授权而误跳 404，或先闪 403 再跳变。
  （§11 风险表中「动态路由替换静态路由 → 启动白屏/404」的应对，最终落点即此。）

### 7.2 按钮权限 ✅

- **新增** `src/components/Perms.tsx`（`<Perms code="iam:user:create">{children}</Perms>`，
  无权限**不挂载**而非 `display:none`）+ `src/lib/usePermission.ts` + `src/lib/permCodes.ts`
  （权限码常量，与 server 端 `IamPermissionCodes` 一一对应，避免注解/前端双侧字符串漂移）。
- `permissions` 来自 `GET /sys/me/permissions`，存 `authStore`（**只存内存，不落盘**——
  用 zustand `partialize` **显式裁剪**，与 `platform/web §5` 一致）。
- **已包裹的按钮**（新建/编辑/删除/启停/授权/撤销/重置口令/解锁/强制下线/目录同步等）
  见 `../web/README.md` §3 的「按钮 → 权限码对照」表。只读的「刷新」类按钮不单独包裹。
- ⚠️ **判定必须用权限码、不能用角色名**：`isIamAdmin(user)`（判 `apps`+`roles`）已删除，
  概览页的「管理视角 / 个人视角」二分也改为按 `iam:overview:view` 判定。
- ⚠️ **不做通配**：后端 `hasAuthority` 是精确匹配；超管靠**角色展开为全量权限码**获得全部权限，
  前端若把 `SUPER_ADMIN` 当 `*` 放通会出现「显示按钮但后端 403」的假阳性。

### 7.3 多语言 ✅（框架层）

- **新增** `src/lib/i18n.ts`（i18next 初始化 + 运行时拉取 + **整包版本缓存** + 缺失上报）、
  `src/components/LanguageSwitcher.tsx`（顶栏与登录页共用）。
- **全部改走 `t()` 的范围**：菜单名/分组名/副标题、页头标题与说明、面包屑、外壳（品牌/页脚/提示/
  tooltip/环境标识/未登录）、登录页全部文案、403/404/占位页、按钮权限相关提示。
- **未完成**：各业务页**正文文案**（表格列名、表单标签、提示语、`format.ts` 的枚举标签）
  仍为中文硬编码，**随 W4 逐页迁移**。切 `en-US` 时表现为「框架英文 + 页内中文」的阶段性混合。
- **语言列表来自公开端点** `GET /api/i18n/locales`：库内新增语种下拉自动出现，无需改前端。
- 顶部 header 已增加语言切换控件（与主题切换并列）；登录页亦提供（译文端点是公开的）。
- ⚠️ **两个必须固化的实现约束**（否则会出「界面全空/白屏」）：
  1. **本地缓存必须是「整包」而非仅版本号**——端点约定 `v` 命中即回空 `messages`，
     只缓存版本号会导致刷新页面后拿到空包；按语言整体落 `localStorage['iam-i18n-bundle:<lang>']`。
  2. **不能用 Suspense**——`initImmediate: false` + `react.useSuspense: false`，
     文案是「先渲染缓存、后台刷新」模式。

### 7.4 四个管理页（配置化自助闭环）✅（W4 已落地）

| 页面 | 路由 | 能力 | 权限码（入口） | 数据面 |
| --- | --- | --- | --- | --- |
| 菜单管理 | `/menus` | 树形 CRUD（新增/编辑/删除、上下级切换、显隐、`i18n_code`/`perm_code`/`icon` 绑定） | `iam:menu:*` | 平台 `/sys/menus[/tree]` |
| 权限管理 | `/permissions` | 权限点 CRUD（`module:res:action`），按 module 分组 + 关键字过滤 | `iam:perm:*` | 平台 `/sys/permissions` |
| 角色管理 | `/roles-admin` | 角色 CRUD + **菜单授权**（`sys_role_menu`）+ **权限授权**（`sys_role_perm`） | `iam:role-admin:*` | 平台 `/sys/roles[/{id}/permissions\|menus]` |
| 多语言 | `/i18n` | 语言目录 CRUD + 译文编辑（按语言/模块/关键字筛选）+ 缺失上报汇总与清空 | `iam:i18n:*` | IAM `/api/v1/admin/i18n/**` |

> 页面文件：`MenusAdminPage.tsx` / `PermissionsAdminPage.tsx` / `RolesAdminPage.tsx` / `I18nAdminPage.tsx`，
> 路由与 `component` 映射沿用 `pageRegistry`，**未改动菜单/路由/权限配置**。

#### 7.4.1 ⚠️ 配置页的「双闸门」（实施补充，本轮新增）

菜单/权限/角色三页的**数据面复用平台 `cim-system` 端点**，而**那些端点用平台码 `sys:*` 鉴权**；
控制台自己的 `iam:menu:*` / `iam:perm:*` / `iam:role-admin:*` 只决定**入口显隐**。二者缺一不可：

```
能操作（或能读） ⟺ 持有 IAM 控制台码 ∧ 持有对应平台码
```

前端据此用 `<Perms codes={[...]}>`（**AND** 语义）渲染，避免「按钮可见但接口 403」的错配
（`components/Perms.tsx` 已支持 `codes` 数组）。

- **超管**（`IAM_ADMIN`，`is_super=true`）由 `DbLocalAuthorityLoader` 短路展开为「库内全部启用权限码」，
  天然同时具备两族 → 开箱即用。
- **只读运维**（`IAM_OPERATOR`）本轮由种子**补授平台只读码**（`sys:menu:list` / `sys:permission:list` /
  `sys:role:list`），否则会出现「配置页可见但数据 403」（见 §11 新增风险行）。
- **自定义角色**需在「角色管理」页把两族都勾上——该页权限选择器直接列出全部 `sys_permission`
  行（含 `sys:*`），故可发现、可勾选，无需额外文档。
- `/i18n` 页**不受此约束**：它的数据面是 IAM 自己的 `/api/v1/admin/i18n/**`，鉴权用 `iam:i18n:*`，单一闸门。

#### 7.4.2 其他实现口径

- **树/列表复用既有样式**：`.split` + `.tree*`（菜单树、角色列表、权限分组）、`table.data`（语言/译文）、
  `.form-grid`/`.field`（表单），未新增自定义组件。
- **编辑即「原实体 + 本次编辑」整体提交**：`{...origin, ...edits}` 回填基础字段（租户/审计列），
  避免平台 `AbstractJpaService.update` 因局部对象丢列。
- **权限码三段由平台拆解**：菜单/权限页只填 `code`（`module:res:action`），平台
  `SysPermissionService.applyCode` 负责拆出 `module`/`res`/`action`，前端不重复实现该校验
  （仅做「必须是三段」的前置提示）。
- **删除默认语言被后端拒绝**：`I18nService.deleteLocale` 对 `is_default=true` 抛异常，前端亦隐藏其删除按钮。



---

## 8. 数据与迁移

### 8.1 ⚠️ 迁移**版本空间**分配（实施中发现的硬约束，ADR-10）

Flyway 把所有 `locations` 合并为**同一套版本序列**：只要两处出现相同版本号，启动即
`FlywayException: Found more than one migration with version X`。IAM 原自有迁移在
`db/migration/{vendor}/V1..V6`，而 `cim-system` 与 `cim-i18n-starter` 原本**各自也是 `V1`** ——
三者同库必然冲突。故定义**版本段**（详表见 `platform/server/cim-jpa-starter/.../db/migration/README.md`）：

| 版本段 | 归属 |
| --- | --- |
| `V1–V999` | **宿主 ap 自有迁移**（IAM 的 `V1..V6` 不动，新增种子 `V7`） |
| `V1000–V1999` | 平台模块 `cim-system`（原 `V1__init_system_tables` → **`V1000__init_system_tables`**） |
| `V2000–V2999` | 平台模块 `cim-i18n-starter`（原 `V1__init_i18n_tables` → **`V2000__init_i18n_tables`**） |

> 这是一次**一次性**约定落地（两模块各一套建表脚本改名 + `DdlExportTest` 输出名 + README），
> 之后新增模块按 1000 递增段登记即可，宿主 ap 永不受影响。属 `design.md` ADR-10。

### 8.2 表与种子

| 迁移/种子 | 内容 | 归属 |
| --- | --- | --- |
| `system/V1000__init_system_tables.sql` | `sys_menu` / `sys_permission` / `sys_role*` / `sys_user*` / `sys_dict*` / `sys_config` / `sys_log` | 平台 `cim-system` |
| `i18n/V2000__init_i18n_tables.sql` | `sys_locale` + `sys_i18n` | 平台 `cim-i18n-starter` |
| IAM 自身 `V1..V6` | 身份目录（既有，不动）；**无新增 DDL** | `business/iam-ap/server` |

**种子改为 Java（不是 Flyway SQL）**——实施决策，理由：菜单父子关系里父 ID 由 `IdGenerator`
运行期生成，SQL 种子无法表达；且需按 `(code, locale)` upsert 幂等、避免覆盖管理端在线编辑。
与平台 `I18nSeedLoader` 同源做法。

| 种子器 | 内容 | 幂等口径 |
| --- | --- | --- |
| `cim-system` · `SysPermissionSeeder` | `PermissionCodes`（`sys:*`）→ `sys_permission` | 按 `code` 存在即跳过（受 `cim.system.seed-permissions`） |
| `iam-ap` · `IamConsoleSeedService` | IAM 菜单树（20 节点 + 34 按钮）· `iam:*` 权限码 · `IAM_ADMIN`/`IAM_OPERATOR` 角色与授权 · `admin` 的 `sys_user` 档案 + 角色 · `sys_locale`(zh-CN 默认/en-US) + `iam.*` 译文（zh 必填，en 可关） | **已存在即跳过**（角色授权仅在「该角色尚无授权」时写入） |

- `zh-CN` 为**强制兜底**：`IamConsoleCatalog` 的每条 `TextDef` 都带中文；`en` 可经
  `cim.iam.console.seed-english=false` 关闭。
- 种子器失败**不吞异常**（启动失败优于带病运行：无种子的控制台 = 管理员零权限）。
- **实测种子规模**（2026-10-09，空库首启）：
  `权限 +49 · 菜单 +54 · 角色 2 · 授权 +137 · 管理员档案 新建 · 译文 zh +137 / en +137`，
  另有平台侧 `[cim-system] 权限目录种子：写入 21 条平台权限码` 与
  `[cim-i18n] 种子完成：76 条内置文案写入`。
  ⚠️ 译文数已从设计初稿的 ~68 增至 **137**（补齐了菜单副标题 `iam.menu.*.desc`、外壳 `iam.shell.*`、
  通用 `iam.common.*`、登录页 `iam.login.*`、管理页 `iam.page.*` 等）。
- ⚠️ **种子是"已存在即跳过"**，因此**对已建库修改种子内容（如改 `sort_no`、就地改文案）不会生效**；
  开发用 H2 内存库每次重建不受影响，持久化环境需以管理端在线编辑或新增键（键是新增、不冲突）为准。
  「改已有键的值」属刻意的保护行为（避免重启冲掉管理员编辑）。

---

### 8.3 ⚠️ 前端装配要点（实施补充）

- **IAM 服务端**需放行公开的 `/api/i18n/**`（未认证可访问，登录页要用）；平台默认链是
  `anyRequest().permitAll()` + `@PreAuthorize` 兜底，故天然放行，**但若将来收紧 `authorizeHttpRequests`
  必须显式放行** `/api/i18n/**`。
- **前端部署需反代两个前缀**：`/api`（IAM `/api/v1/**` + `/api/i18n/**`）与 `/sys`（`cim-system` 端点）。
  开发态由 `vite.config.ts` 的 proxy 完成（见 `../web/README.md` §1）。


---

## 9. 决策点（**已定**，2026-10-09）

| # | 决策点 | 结论 | 落点 |
| --- | --- | --- | --- |
| 1 | 菜单/权限归属 | **复用平台 `cim-system`**，不新建表、不另起一套；`fallback-to-claims=true` 作灰度开关（生产建议 `false`） | §4.5 / §8.2 |
| 2 | 权限码体系迁移 | **一步切到方法级 `iam:xxx:yyy`**，类级保留 `iam:console:admin` 作兜底安全网（旧两段式 `iam-ap:ADMIN` 因不合法被替换） | §4.4 / §4.5 |
| 3 | 多语言落地范围 | **平台级 `cim-i18n-starter`**（两表 + DB `MessageSource` + 公开运行时端点），全 ap 复用 | §5 / `docs/platform/server/design.md` §2.7 |
| 4 | 前端路由来源 | **后端下发菜单 → 前端动态生成路由**；前端不再持有路由表 | §7.1 |
| 5 | 首批语种 | **`zh-CN`（强制兜底）+ `en-US`**；译文由 `IamConsoleCatalog` 成对提供（后续可经管理端在线补 `zh-TW` 等） | §5.7 |
| 6 | 菜单文案键粒度 | **每菜单/分组/按钮各一键**（`iam.menu.*` / `iam.btn.*`）；副标题不落库，用 `i18n_code + '.desc'` 约定 | §7.3 |
| 7 | **（实施补充）** 迁移版本空间 | 各模块独占 1000 段位：`cim-system` = V1000–V1999、`cim-i18n` = V2000–V2999、宿主 ap 自有迁移 = V1–V999 | §8.1 / ADR-10 |
| 8 | **（实施补充）** 实体/仓储扫描根 | 各单元只声明**自己的包**，互不重叠（重叠 → `BeanDefinitionOverrideException`） | ADR-11 |
| 9 | **（实施补充）** `@FilterDef` 声明点 | 每持久化单元**恰好一处**（`com.cim.system.package-info`），其余模块不得再声明同名同参（Hibernate 6 硬报错） | ADR-12 |
| 10 | **（实施补充）** 前端 API 双前缀 | IAM 端点走 `/api/v1`（`api` 实例），平台 `cim-system` 端点走根路径（`rootApi` 实例）；部署需同时反代 `/api` 与 `/sys` | §7.1 |
| 11 | **（实施补充）** 未授角色用户的可见性 | **无菜单 = 无页面**（落到 404）。「准入 ≠ 授权」，能进 `iam-ap` 不等于能看控制台 | §7.1 |
| 12 | **（实施补充）** 配置页权限族 | 菜单/权限/角色三页数据面复用平台端点 → **双闸门**「IAM 控制台码 ∧ 平台码」，前端按 AND 渲染；`/i18n` 页为 IAM 自有端点 → 单闸门 | §7.4.1 |


---

## 10. 实施计划（分阶段）

| 阶段 | 内容 | 交付 | 状态 |
| --- | --- | --- | --- |
| **W1** 平台 i18n starter | 实现 `cim-i18n-starter`（两表 + `DatabaseMessageSource` + `MessageResolver` 顶替 + `/api/i18n/*` + 种子 + 缓存） | 平台迁移 + 单测 + `BootstrapAssembly` 冒烟 | ✅ 完成（9 run/0 fail） |
| **W2** IAM 接入 cim-system | 加依赖 + 装配校验 + 菜单/权限/角色/译文**种子** + 权限码常量 + `@PreAuthorize` 改造 | IAM 迁移 + 测试绿 | ✅ 完成（55/55 绿，`ConsolePermissionI18nTest` 7/7） |
| **W3** 前端动态化 | 动态菜单/路由 + `<Perms>` + i18n 初始化 + 主壳改造 | `tsc` + `vite build` 绿 | ✅ 完成（另含 403/404、启动闸门、语言切换；接口级端到端验证通过） |
| **W4** 四个管理页 | 菜单/权限/角色/多语言管理页 | 端到端 + 视觉回归（浅/深） | ✅ 完成（33/33 接口端到端 + 19/19 视觉回归含交互；`ConsolePermissionI18nTest` 9/9，全量 55/55） |
| **W4b** 存量页面文案 i18n | 既有业务页正文（表头/标签/按钮/提示/`format.ts` 枚举）迁移到 `t()` + 补 `TextDef` | 切 `en-US` 无中文残留 | ⏳ 待办（W3 只覆盖框架层，见 §7.3） |
| **W5** 收口 | 文档、PROCESS/AUDIT、提交推送 | 分笔提交 | ⏳ 待办 |

**实施中发现的、超出原计划的真实缺陷**（均已修复并固化进文档/ADR）：
`@FilterDef` 重复声明导致启动即失败（ADR-12）、实体/仓储扫描根重叠导致 `BeanDefinitionOverrideException`（ADR-11）、
迁移版本号跨模块冲突（ADR-10）、宿主启动类漏扫域模块配置类、超管权限目录为空导致自拒（`SysPermissionSeeder`）、
i18n 前端整包未合并中文兜底。


---

## 11. 风险与应对

| 风险 | 影响 | 应对 | 实测结果 |
| --- | --- | --- | --- |
| `cim-system` 引入改变 IAM 鉴权源（§4.5） | 现存 `iam-ap:ADMIN` 判定失效、控制台全 403 | 灰度开关 `fallback-to-claims` + 测试覆盖「切前/切后」两态 | ✅ 已按 §9-2 一步切；`fallback-to-claims=true` 作灰度兜底 |
| `sys_user` 档案未建 | 权限解析为空 → 拒绝（`fallback-to-claims=false`） | 种子为 `admin` 建档案 + 分配 `IAM_ADMIN`；首管理员引导逻辑同步 | ✅ `IamConsoleSeedService#seedAdminProfile` 已实现 |
| **超管权限目录为空**（原计划未预见） | 超管展开为「全部启用权限码」，目录空 → 连 `cim-system` 自身端点都拒绝，控制台自锁 | 启动种子平台权限目录（`sys:*`） | ✅ 新增 `cim-system` 的 `SysPermissionSeeder`（21 条） |
| **迁移版本号跨模块冲突**（原计划未预见） | 启动即 `FlywayException: Found more than one migration with version X` | 版本段分配 | ✅ ADR-10，见 §8.1 |
| **实体/仓储扫描根重叠**（原计划未预见） | 启动即 `BeanDefinitionOverrideException` | 各单元只声明自己的包 | ✅ ADR-11 |
| **`@FilterDef` 重复声明**（原计划未预见） | 启动即 `AnnotationException: Multiple '@FilterDef' …` | 每持久化单元恰好声明一次 | ✅ ADR-12 |
| i18n 缺译文 | 界面出现裸 key | 四级兜底 + `zh-CN` 强制 + 缺失上报 + CI 校验 | ✅ 三层保障；另需「整包缓存 + 不用 Suspense」两点实现约束（§7.3） |
| 动态路由替换静态路由 | 启动白屏/404 | 「权限就绪后再渲染」+ 保持既有页面组件不变、只改路由来源 | ✅ `BootGate` 启动闸门；未知路径 → 404、无权限 → 403 |
| 菜单种子与前端图标名漂移 | 图标缺失（静默回落兜底图标） | `iconRegistry` 未命中回落默认图标；并新增**跨端契约测试** `ConsoleMenuIconContractTest`（直接读 `lib/iconRegistry.ts` 解析键集，比对 `IamConsoleCatalog.menus()` 的 `icon`）把这条 Java↔TS「字符串约定」变成可失败断言 | ✅ 已回落 + 已补测试（2 条：种子 icon 全部命中注册表 / 注册表键不重复且解析非退化） |
| **前端两个 API 前缀**（实施中发现） | 只代 `/api` 时 `/sys/**` 404 → 卡在启动闸门 | 双实例 + 双前缀反代 | ✅ 见 §7.1；生产 nginx 必须同时反代 `/api` 与 `/sys` |
| **权限不落盘的落地方式**（实施细化） | 若随 persist 一起落盘 → 「已撤权仍看到按钮」 | zustand `partialize` 显式裁剪 | ✅ 见 §7.2 |
| **菜单/权限/角色三页数据面复用平台 `sys:*` 端点**（实施中发现） | 控制台码 `iam:*` 只管入口显隐、平台码才管读写 → 只持 IAM 码会「页面可见但操作 403」 | **双闸门**：`canRead` 用 `useHasAllPermissions([IAM码, SYS码])` 缺平台码即整页提示、不发起注定 403 请求；写按钮 `<Perms codes={[IAM码,SYS码]}>`（AND 语义）；只读运维角色补授平台只读码；`/i18n` 页单闸门（数据面是 IAM 自己的端点） | ✅ 见 §7.4.2；`ConsolePermissionI18nTest` 9/9 实测钉死「能读/写 ⟺ IAM 码 ∧ 平台码」 |


---

## 12. 附：与既有设计的关系（不重复造）

- **不新建菜单/权限表**：复用 `sys_menu` / `sys_permission` / `sys_role*`（`README §21.1`）。
- **不新建 i18n 表结构**：按 `README §7` 的 `sys_locale` + `sys_i18n` 落地。
- **不改变准入边界**：`apps` claim 准入仍由 IAM 管（`identity-directory.md`）；本次只补「IAM ap 内部」的菜单/按钮/文案配置化。
- **前端规范沿用**：`platform/web §3/§4/§5/§6`（动态路由、双层守卫、`<Perms>`、权限不落盘、i18n 热切换）。
