# IAM 身份目录与组织架构设计（能力补齐 · Wave 0）

> 状态：**Wave 0 已落地**（设计于 2026-10-09 定稿，同日实现并验证 48/48 绿）· 决策见 §9
> 上游：`README.md` §0 定位与设计原则 / §3 ap 接入 / §4 已落地能力（§4(m)）
> 关联：`docs/platform/server/design.md` §8（与 IAM 对接边界）
> 范围：**只覆盖「用户的创建 + 组织架构的创建 + 同步到业务系统」**；班次/资质门控/破玻璃/双人复核等 Wave 1–3 能力本次不设计。

## 1. 本次范围与权威源划分（已与用户确认）

| 数据 | 权威源 | IAM 的处置 | 落地方式 |
| --- | --- | --- | --- |
| 工号 / 姓名 / 邮箱 / 手机 / 在职状态 | **AD / HR** | **只读同步，不新建员工** | 定时 + 手动拉取，写 `user_profile` |
| 行政组织（部门 / 科 / 组） | **AD** | 只读同步，作为组织树基础层 | `org_node.source = AD_SYNCED` |
| 制造组织（厂区→车间→产线→工序→责任区） | **IAM** | **自建**（AD 无此维度） | `org_node.source = IAM_MANAGED` |
| 岗位 / 责任区 /（后续）班次 | **IAM** | 自建属性 | `user_profile` / `user_org` |
| 非 AD 人员（设备厂商、访客、服务账号） | **IAM** | **自建**（AD 里根本不存在） | `user_profile.source = IAM_MANAGED` |
| 准入与 ap 内粗角色组 | **IAM** | 自建（已有） | `user_app_assignment` + 新增 `org_app_assignment` |
| 业务系统内部菜单 / 按钮 / 数据权限 | **各业务 ap** | **不介入**（边界铁律，README §0） | 各 ap 走平台 RBAC |

**同树混源不打架的关键**：组织节点带 `source` 字段；AD 同步器**只写 `AD_SYNCED` 节点**，绝不触碰 `IAM_MANAGED` 节点，两者共用一棵树但互不覆盖。

## 2. 为什么不「把用户表同步给业务系统」

业务 ap 是独立 Maven/Vite 工程 + 独立库（`docs/repo/conventions.md`）。若 IAM 向每个业务库推用户表，会得到 **N 份副本 + N 个同步器 + N 种不一致**。因此按数据类型分载体：

| 种类 | 载体 | 实时性 | 理由 |
| --- | --- | --- | --- |
| ① 准入同步 | **JWT claim `apps`/`roles`/`ver`**（已有） | 即时（改即 bump → 旧令牌 401 → 重签） | 小、每请求需判、失效闭环已通 |
| ② 档案同步 | **只读 Directory API + 业务侧缓存** | 准实时（TTL 5–15 min） | 中频读、不参与鉴权；塞进 token 会撑大且改属性要重签全体令牌 |
| ③ 组织同步 | **只读 Org API + 版本水位** | 低频（水位变化才拉） | 树形、体积大、变更少 |

**业务 ap 不落用户主数据**，只存 `uid` 引用 + 可失效重建的缓存——守住「IAM 管准入、业务自管内部权限」的既有边界。

## 3. 数据模型（新增 5 张表，Flyway V6）

### 3.1 `org_node` 组织节点（单表自引用树 + 物化路径）

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint PK | 走 `IdGenerator` 雪花 |
| `parent_id` | bigint NULL | 根节点为 NULL |
| `code` | varchar(64) | 组织编码，与 `source` 组合唯一 |
| `name` | varchar(128) | 显示名 |
| `node_type` | varchar(16) | `AREA` 厂区 / `WORKSHOP` 车间 / `LINE` 产线 / `PROCESS` 工序 / `TEAM` 班组 / `DEPT` 行政部门 |
| `path` | varchar(512) | 物化路径 `/{rootId}/{...}/{selfId}/`，**前缀匹配即可查全部祖先授予** |
| `sort_no` | int | 同级排序 |
| `source` | varchar(16) | `AD_SYNCED` / `IAM_MANAGED` |
| `external_id` | varchar(128) NULL | AD 侧 DN 或 objectGUID，仅 `AD_SYNCED` 有 |
| `status` | varchar(16) | `ENABLED` / `DISABLED` |
| `updated_at` | datetime | 供增量拉取比对 |

索引：`uk(source, code)`、`idx(parent_id)`、`idx(path 前缀)`。

### 3.2 `user_profile` 用户档案

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | varchar(128) PK | **与 `local_credential.user_id`、令牌 `uid` 同源**，不另造 ID |
| `employee_no` | varchar(64) NULL | 工号（AD `employeeID`） |
| `display_name` | varchar(128) | 姓名 |
| `email` / `mobile` | varchar(128) | 联系方式 |
| `job_title` | varchar(64) NULL | 岗位（IAM 维护） |
| `status` | varchar(16) | `ACTIVE` / `INACTIVE`（AD `userAccountControl` 映射） |
| `source` | varchar(16) | `AD_SYNCED` / `IAM_MANAGED` |
| `synced_at` | datetime NULL | 最近一次 AD 同步时间 |

> 与 `local_credential` 的关系：**凭证与档案解耦**。AD 人员可能没有本地凭证（改密在目录侧）；本地凭证账号（厂商/服务账号）可能没有 AD 档案来源。二者靠 `user_id` 关联，**互不要求存在**。

### 3.3 `user_org` 用户—组织归属

| 列 | 说明 |
| --- | --- |
| `user_id` + `org_id` | 联合唯一 |
| `is_primary` | 主属组织标记（用于展示与默认数据权限范围） |
| `source` | `AD_SYNCED` / `IAM_MANAGED` |

**多归属**：支持一人多工序（多能工 / 跨线支援）—— 已确认，见 §9。`user_org` 保留一对多，**不**简化为 `user_profile.org_id`。

### 3.4 `org_app_assignment` 组织级准入授予

| 列 | 说明 |
| --- | --- |
| `org_id` + `app_code` | 联合唯一 |
| `roles` | 逗号分隔粗角色组（与 `user_app_assignment.roles` 同构） |
| `include_children` | 是否覆盖子组织（默认 true，配合 `path` 前缀生效） |
| `status` | `ENABLED` / `DISABLED` |

### 3.5 `directory_watermark` 目录水位

| 列 | 说明 |
| --- | --- |
| `scope` | `USER` / `ORG`（可扩展） |
| `version` | 单调递增整数 |
| `updated_at` | 最近变更时间 |

**与既有 `token_version` 同构**：变更即 +1；业务侧只做「轻量比对 → 变了才全量拉」，避免轮询大表。

## 4. 准入解析：组织授予如何派生到人

**关键决策：运行时展开，不落派生行**（避免「组织改了但派生行没同步」的不一致）。

```
effectiveApps(uid)  = 个人授予(uid)                    ∪  组织授予(uid 的全部归属组织及其祖先链)
effectiveRoles(uid, app) =                                   上述来源的 roles 并集
```

- **祖先链**靠 `org_node.path` 前缀匹配一次查出（物化路径的核心价值）：`WHERE path LIKE '/{orgId}/%'` 或按 `include_children` 决定是否仅精确匹配。
- **展开时机**：① `TokenIssuerService.issue` 写 `apps`/`roles` claim 时；② `GET /me`；③ 准入查询端点。三处共用同一个解析器（建议抽 `EffectiveAccessResolver`）。
- **一致性**：组织节点/归属/组织授予任一变更 → **bump 受影响用户的 `tokenVersion`** → 旧令牌 401 → 重签即带新准入。与现有 `LocalTokenVersionChecker` 闭环直接复用，无新增机制。
- **优先级**：个人授予与组织授予取**并集**（个人可额外追加，不能减）。要收回只能撤销组织授予——避免引入「排除规则」的复杂度。

## 5. 接口契约（新增端点）

### 5.1 目录侧（供业务 ap 只读消费）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/directory/watermark` | `{user: 12, org: 5}` → 业务侧比对决定是否重拉 |
| GET | `/api/v1/directory/users/{userId}` | 单用户档案 `UserProfileDto`（含归属组织列表） |
| POST | `/api/v1/directory/users/batch` | 批量 `{userIds: []}` → 列表（列表页渲染用，避免 N+1） |
| GET | `/api/v1/directory/orgs?since=` | 组织树/增量（含 `updatedAt`，供业务侧增量合并） |

> 返回体不含任何凭证与权限内部结构；档案字段可按需裁剪。

### 5.2 管理侧（`hasAuthority('iam-ap:ADMIN')`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST/PUT/DELETE | `/api/v1/admin/orgs[/{id}]` | 制造组织节点 CRUD（**仅允许改 `IAM_MANAGED`**） |
| GET/PUT | `/api/v1/admin/profiles[/{userId}]` | 档案查询 / 维护 IAM 侧属性（岗位等） |
| PUT | `/api/v1/admin/users/{userId}/orgs` | 设置用户归属（含主属标记） |
| GET/POST/DELETE | `/api/v1/admin/org-grants[/{orgId}/{appCode}]` | 组织级准入授予 |
| POST | `/api/v1/admin/sync/ad` | 手动触发一次 AD 同步（并返回统计） |

### 5.3 ⚠️ 新暴露的前置依赖：目录读接口的「服务身份」

业务 ap 调 §5.1 的读接口**不能带某个真人用户的令牌**（会串用真人身份、且真人离职即失效）。现有 IAM 只有**用户令牌**，没有服务身份。两个方案：

- **方案 A（建议起步）**：配置化 **Directory API Key**（请求头 `X-Directory-Key`，双方配置同一密钥，可轮换）。1 天内可落地，不引入新模块。
- **方案 B（目标态）**：标准 **OAuth2 client credentials**，签发**服务令牌**（claim 带 `client_id` + 仅 `directory:read` 权限）。这需要 NHI（非人类身份）模块——原本归类为「P2 可选」，**因本次设计升级为 Wave 0 必需**。

建议：**A 起步、B 作为后续替换**，接口契约保持不变（只换认证头），业务侧无需改代码。

**落地（Wave 0，已确认取方案 A）**：`directory/DirectoryApiProperties`（配置键 `cim.iam.directory.api.key`）+ `directory/DirectoryApiKeyFilter`（`OncePerRequestFilter`，只拦 `/api/v1/directory/`）。
- **未配置密钥 → `503`**（安全默认拒绝，不是放行）；**密钥不匹配 → `401`**；比较用 `MessageDigest.isEqual`（**常量时间**，防时序侧信道）。
- 后续换方案 B 时只替换该 Filter 的认证逻辑，**4 个端点契约与业务侧调用代码零改动**。

## 6. AD 同步器

- **触发**：`@Scheduled(fixedDelayString = "${cim.iam.directory.sync-interval:300s}")` + 管理端手动触发（§5.2）。
- **拉取**：沿用现有 JNDI 路线（`auth/LdapAdAuthenticationSource` 已用 JNDI simple bind），用 **PagedResultsControl** 分页检索，**不引入新依赖**。
- **增量**：优先 `whenChanged >= lastSync`（跨域通用）；若可用 `uSNChanged`（AD 专有，更可靠）则优先。全量对账兜底（每日一次）。
- **属性映射**：

| AD 属性 | IAM 字段 |
| --- | --- |
| `sAMAccountName` | `user_profile.user_id` / `local_credential.username`（关联键） |
| `employeeID` | `employee_no` |
| `displayName` | `display_name` |
| `mail` / `mobile` | `email` / `mobile` |
| `department` → DN 链 | `org_node`（`DEPT` 层）+ `user_org` |
| `userAccountControl` | `status`（禁用位 → `INACTIVE`） |

- **冲突原则**：同步器**只写 `source = AD_SYNCED` 的行**；`IAM_MANAGED` 的组织与档案不参与覆盖。AD 侧删除 → IAM 侧置 `INACTIVE`（**不物理删除**，保留审计与准入历史）。
- **对账**：同步完成后校验「有归属无档案 / 有档案无归属 / 有凭证无档案」三类异常并写审计。

## 7. 前端影响（business/iam-ap/web）

菜单 IA 调整为：

| 分组 | 菜单项 | 变更 |
| --- | --- | --- |
| 身份管理 | **组织架构**（树 + 节点 CRUD + 挂人） | **新增** |
| 身份管理 | **用户与档案**（账号 + 档案 + 归属，合并原「用户账号」） | 改造 |
| 身份管理 | 登录锁定 | 不变 |
| 接入管理 | 应用注册 / 准入授权 / 角色组 | 不变 |
| 接入管理 | **组织授权**（按组织授予 ap + 角色组） | **新增** |
| 安全与会话 | 在线会话 / 审计日志 / 系统设置 | 不变 |

「用户与档案」页需标注每行的**来源**（AD 同步 / IAM 自建）与**最近同步时间**，AD 来源的行禁用本地编辑（只读），避免用户误以为能在 IAM 改 AD 字段。

## 8. 迁移与任务切分（Wave 0）

| 任务 | 内容 | 依赖 |
| --- | --- | --- |
| **T0-1** | V6 迁移（h2 + mysql）：5 张新表 | — |
| **T0-2** | `org` 包：`OrgNode` 实体/仓储/服务（树 CRUD、path 维护、跨源保护） | T0-1 |
| **T0-3** | `profile` 包：`UserProfile` / `UserOrg` 实体与服务（含 IAM 自建人员） | T0-1 |
| **T0-4** | `EffectiveAccessResolver`：个人 ∪ 组织（含祖先链）→ 接入 `TokenIssuerService` / `ProfileController` | T0-2,3 |
| **T0-5** | `org_app_assignment` 组织级授予 + bump 传播 | T0-4 |
| **T0-6** | `directory` 包：水位 + 4 个只读端点 + **API Key 校验** | T0-1,3 |
| **T0-7** | `directory/AdDirectorySyncService`：JNDI 分页 + 增量 + 属性映射 + 对账审计 | T0-3 |
| **T0-8** | `admin` 包新增端点（orgs / profiles / orgs 归属 / org-grants / sync/ad） | T0-2,3,5,7 |
| **T0-9** | 前端：组织架构页 + 组织授权页 + 用户与档案页改造 + 菜单 IA 更新 | T0-8 |
| **T0-10** | 测试 + 文档（README §6 本文件入口）+ PROCESS.md 里程碑 + 提交推送 | 全部 |

**测试要点**：跨源保护（同步不覆盖 IAM 节点）、祖先链授予展开、组织变更 bump 后旧令牌 401、水位变更后业务侧失效、AD 不可达时同步失败不阻塞启动。

## 9. 决策定稿（2026-10-09，已全部拍板并落码）

原「待确认问题」逐条已确认，结论如下（**均已实现，见 §10 落地对照**）：

| # | 问题 | 结论 | 对设计的影响 |
| --- | --- | --- | --- |
| 1 | **多归属**：一人可否同属多个工序 / 产线？ | **存在**（多能工 / 跨线支援场景真实存在） | `user_org` **保留一对多**，不简化为 `user_profile.org_id`；`is_primary` 仅作主属标记（展示与默认数据权限范围），**不限制行数**；`effectiveApps` 对多归属取**并集** |
| 2 | **AD 可达性**：本地 / 沙箱无 AD 可连 | **同步器必须支持「未配置即跳过」**：不连接、不报错、不阻塞启动，**纯 IAM 自建照常可用** | `AdDirectorySyncProperties.isConfigured()` 门控：`false` 时 `syncNow()` 返回 `SyncResult{skipped:true, reason:...}` 且**不抛异常**；`@EnableScheduling` 由 `@ConditionalOnProperty(cim.iam.directory.ad.enabled)` 门控，未启用则**连定时任务都不注册** |
| 3 | **组织编码是否与 MES 对齐** | 经查 `docs/business/mes-ap/` **仅有骨架 README、无既定编码规则** → `org_node.code` **即唯一编码载体**，未来 MES 直接复用，**不发明第二套** | `org_node.code` 与 `source` 组合唯一（`uk(source, code)`）；`code` 在 API/前端作为稳定业务键暴露（接入码、组织编码均走等宽字体呈现）；**若日后 MES 出现既定编码，应回填 `org_node.code` 而非新增列** |
| 4 | **服务身份选型** | **A 起步**（配置化 `X-Directory-Key`，常量时间比较）、**B 作为后续替换**（OAuth2 `client_credentials` / NHI 服务令牌） | §5.3 已按方案 A 落地；**接口契约不变**，换 B 只替换 Filter 认证逻辑 |

## 10. 落地对照（Wave 0 实现映射）

| 任务 | 落地物 |
| --- | --- |
| T0-1 | `db/migration/{h2,mysql}/V6__identity_directory.sql`（5 张表） |
| T0-2 | `org` 包：`OrgNode`/`OrgNodeRepository`/`OrgNodeService`（树 CRUD、`path` 维护、跨源保护 `guardManaged`）、`OrgNodeType`/`OrgStatus` |
| T0-3 | `profile` 包：`UserProfile`/`UserOrg` + 仓储 + `ProfileService`；`common/DataOrigin`（`AD_SYNCED`/`IAM_MANAGED`） |
| T0-4 | `app/EffectiveAccessResolver`（个人 ∪ 组织含 `path` 祖先链）；`TokenIssuerService` 与 `ProfileController` 改走该解析器 |
| T0-5 | `org/OrgAppAssignment` + `OrgGrantService`（授予/撤销 → `bumpUsers` + 水位 + 审计） |
| T0-6 | `watermark` 包（`DirectoryWatermarkService`）+ `directory` 包（`DirectoryService`/`DirectoryController` 4 端点 + `DirectoryApiKeyFilter`） |
| T0-7 | `directory/AdDirectorySyncService`（JNDI + 分页 + 增量 + 属性映射 + 删除置 `INACTIVE` + 对账审计；未配置走 skip） |
| T0-8 | `admin` 包：`OrganizationAdminController`、`DirectoryAdminController`；`UserAdminController`/`AdminOverviewController`/`SettingsAdminController` 扩展 |
| T0-9 | 前端：`OrgPage`（树 + 节点 CRUD + 挂人）、`OrgGrantsPage`（组织授权）、`UsersPage` 改造（档案列 + 归属多选）、菜单 IA v4 |
| T0-10 | 测试 `IdentityDirectoryTest`×7（iam-ap 合计 **48/48**）+ 本文档 + README §4(m) + PROCESS.md/AUDIT.md |

**遗留（不属本次范围）**：
- **AD 实际 schema 样例**仍缺（`employeeID`/`department` 在不同 AD 部署命名不一）→ 拿到真实样例后固化映射表；当前映射按 AD 通用命名（`sAMAccountName`/`employeeID`/`displayName`/`mail`/`department` DN 链/`userAccountControl`）。
- AD 增量拉取目前以「全量对账 + DN 深度排序」为主；`whenChanged`/`uSNChanged` 增量水位待有真实 AD 后可开启。
- 方案 B（NHI 服务令牌）待后续替换。
