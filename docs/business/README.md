# 业务扩展文档（docs/business）

本目录用于存放 **业务系统** 的设计与说明文档，作为 cim-ap 基础框架之上的扩展层。

## 定位
cim-ap 是统一底座（认证、多租户、审计/历史、i18n、RBAC、通用 CRUD 等），**业务领域**（MES / EAP / SPC / WMS 等）在其上按需扩展，相关设计文档统一放在此处，与基础框架文档（`docs/platform/server`、`docs/platform/web`）分离，避免框架与业务耦合。

### 统一登录与准入（IAM）

- **`iam-ap` 是业务层中的基础设施型 ap**，承载**企业内统一登录授权认证**与**跨业务准入控制**（无三方登录）。
- **边界原则（已确认）**：IAM **只做各业务系统的准入权限控制，不做业务系统内部的权限控制**。
  - IAM 负责：企业内统一登录、令牌颁发/刷新、用户能否进入某 ap（准入）、ap 内粗角色组（如管理员/操作员）。
  - 业务系统负责：自身菜单 / 按钮 / 数据行级权限（复用平台框架 RBAC，后端文档 §21.1）。
- 认证源：企业内 **LDAP / AD**（员工身份由 AD 托管）；令牌采用 **JWT(RS256) + JWKS 本地验签**，claim 含 `apps`（可进入 ap 列表）与 `roles`（ap 内粗角色组）。
- `mds-ap` / `mes-ap` 等业务 ap 经 IAM 统一登录拿令牌，本地验签 + 校验 `apps` claim 准入后，再用本 ap 的 RBAC 控制内部权限。

## 约定
- 每个业务系统（ap）一个子目录，命名形如 `{ap}-ap`（如 `mds-ap`、`mes-ap`），例如 `docs/business/mds-ap/`、`docs/business/mes-ap/`；**每个 ap 内部再分 `server/` 与 `web/` 子文档**，与基础底座 `docs/platform/{server,web}` 保持同构（见下方规划结构）。
- 业务文档只允许引用框架已交付的能力，不得反向修改 `docs/platform/*` 的核心设计；如需新增横切能力，先回到框架层评审（见总文档 §8 落地路线）。
- 业务实体同样遵循框架通用数据模型（审计基类 + 分级历史，详见后端文档 §9），历史表遵循 `{X}`↔`{X}Hist` 同构约定。

## 已建立的骨架

> 以下为**已建立目录框架**的 ap 列表（各 ap 的 `server/`、`web/` 为骨架占位，待后续按章填充业务设计）。

docs/business/
├── iam-ap/                 # 基础设施型 ap：统一登录授权认证 + 跨业务准入控制
│   ├── server/README.md    # IAM 后端设计（骨架）
│   └── web/README.md       # IAM 前端（登录门户 + 接入管理）设计（骨架）
├── mds-ap/                # 业务 ap：主数据 / 设备主数据
│   ├── server/README.md    # MDS 业务后端设计（骨架）
│   └── web/README.md       # MDS 业务前端设计（骨架）
└── mes-ap/                # 业务 ap：制造执行系统
    ├── server/README.md    # MES 业务后端设计（骨架）
    └── web/README.md       # MES 业务前端设计（骨架）

> 新增业务 ap 时，在 `docs/business/` 下建 `{ap}-ap/{server,web}/README.md` 即可，保持与既有同构。
