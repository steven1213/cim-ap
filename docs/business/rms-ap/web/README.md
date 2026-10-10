# RMS-AP 前端设计文档（骨架）

> 本文件为 **骨架占位**：记录认证边界与信息架构方向。
> **需求权威源：👉 [Req.md](../Req.md)**（53 条）；后端对齐见 [../server/README.md](../server/README.md)。

## 0. 定位

- **归属**：`docs/business/rms-ap/web/`，业务前端，基于平台前端框架（`docs/platform/web`）构建。
- **登录**：对接 **IAM（`iam-ap`）** 统一登录，不直接对接 LDAP / AD。

## 1. 登录与路由守卫

- 登录走 IAM 统一门户；令牌含 `apps` / `roles` claim。
- 路由守卫校验本 ap 准入：令牌 `apps` 含 `rms-ap` 才允许进入。
- token 存储遵循平台约定：accessToken 仅内存、refreshToken 走 httpOnly Cookie（平台 §3 / §10）。

## 2. 权限对接（IAM 准入 + 业务 RBAC；对应 Req 7–12）

- **准入（IAM 管）**：由令牌 `apps` claim 决定能否进入本 ap。
- **业务权限（自管）**：进入后，菜单 / 按钮 / 数据权限用平台 `<Perms>` 权限组件 + 本 ap 的 RBAC（平台 §4）控制；设备可见性授权（Req 11）表现为数据行级过滤 + 树/列表按授权裁剪。

## 3. 工程约定（骨架即已遵循）

- Vite + React 工程 `business/rms-ap/web`，开发端口 **5174**，`/api` 代理到本 ap 后端 **8084**。
- 路径别名 `@` → `src`；单 tsconfig（include 仅 `src`，vite.config 由 Vite 自身处理）；依赖集与 `mds-ap`/`mes-ap` 同构（react-router / zustand / axios / i18next）。

## 4. 信息架构方向（待细化，按 Req 分组）

- [ ] 配方库：列表（通配符查询，Req 4）/ 详情（Recipe List + Body 预览）/ 新建与另存为（Req 1–2、6）/ 批量操作（状态、签核、生效，Req 3）。
- [ ] 生命周期：版本时间线与回退（Req 42）/ 签核流（Req 41）/ 预设限时使用管理（Req 20）/ Golden Recipe 管理（Req 16、30–31）。
- [ ] 比对中心：发起比对（设备↔RMS、设备↔设备、版本↔版本，Req 25–27、33）/ 差异报告展示（Req 28、37–38）/ Bypass 配置（Req 22–23）/ 校验日志与统计（Req 17–18）。
- [ ] 设备管理：设备类型 / 区域 / 设备台账（Req 46–48）/ EC/SV Spec 设定（Req 43–44）。
- [ ] 权限管理：角色 / 功能授权 / 设备授权（Req 7–12，数据面复用平台 `sys:*` 能力）。
- [ ] i18n 文案键命名空间（`rms.*`）。
