-- V36: Area 增加默认 Hold 处置部门（Hold Setting 第一层）。
-- 背景：WIP 批被 Hold 后必须明确处置责任部门，否则出现无人认领的「僵尸 Hold」。
-- 行业口径（SiView 系）：Hold 记录 = holdReasonCode + 处置部门/owner；
-- 处置责任上下文默认值按批所在区域带出（PHOTO 区 Hold → 光刻工艺部）。
-- MDS 边界：只存「Area → 默认处置部门」配置（台账与契约），
-- Hold 执行/流转/UnHold 权限归 MES（与 Operation.holdPoint 的边界声明一致）。
-- Hold Code 原因码域暂不建（用户明确先不做），MES 侧以后可以：
--   Hold Code 默认部门优先 → Area 默认部门兜底 → 人工可改。

-- 部门真源 = IAM 组织架构（nodeType=DEPT），V36 起静态字典 mds.owning_dept 废弃删除，
-- 业务取数/守门统一走 DirectoryClient（dept-options）。
ALTER TABLE cim_area ADD COLUMN hold_dept VARCHAR(32);
