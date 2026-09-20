# 业务扩展文档（docs/business）

本目录用于存放 **业务系统** 的设计与说明文档，作为 cim-ap 基础框架之上的扩展层。

## 定位
cim-ap 是统一底座（认证、多租户、审计/历史、i18n、RBAC、通用 CRUD 等），**业务领域**（MES / EAP / SPC / WMS 等）在其上按需扩展，相关设计文档统一放在此处，与基础框架文档（`docs/platform/server`、`docs/platform/web`）分离，避免框架与业务耦合。

## 约定
- 每个业务域一个子目录，如 `docs/business/mes/`、`docs/business/eap/`，内部自包含 README。
- 业务文档只允许引用框架已交付的能力，不得反向修改 `docs/platform/*` 的核心设计；如需新增横切能力，先回到框架层评审（见总文档 §8 落地路线）。
- 业务实体同样遵循框架通用数据模型（审计基类 + 分级历史，详见后端文档 §9），历史表遵循 `{X}`↔`{X}Hist` 同构约定。

> 当前为占位目录：仓库根 README 的目录树已列出 `docs/business/`，但 git 不跟踪空目录，故以本占位文件使其纳入版本管理。
