// 语义状态色与业务枚举的映射表。
//
// 设计原则（见 docs/platform/web/component-platform-design.md §2.3）：
// 1. 只允许 5 档语义：ok（正常/生效）、warn（需人处理）、err（失败/异常）、
//    run（进行中）、idle（草稿/历史/停用）；
// 2. **按语义取色，不按颜色名取** —— 批次 RUN 是「进行中」，用 run（蓝）而非 ok（绿）；
//    配方 ACTIVE 是「已生效」，用 ok（绿）。两者混用会让现场误判；
// 3. 映射表集中在此，业务代码不得自定义状态色。
//
// ⚠️ 只登记**已在后端枚举中核实**的取值。设备/批次/工单状态待 MDS/MES 落地后补，
//    不要凭猜测添加 —— 编错的枚举值比没有更难排查。

export type StatusTone = 'ok' | 'warn' | 'err' | 'run' | 'idle';

/** CSS 变量名（`@cim/theme/tokens.css` 里的 --cim-status-* 别名）。 */
export const TONE_VAR: Record<StatusTone, string> = {
  ok: 'var(--cim-status-ok)',
  warn: 'var(--cim-status-warn)',
  err: 'var(--cim-status-err)',
  run: 'var(--cim-status-run)',
  idle: 'var(--cim-status-idle)',
};

/** 浅底（用于徽标背景）。 */
export const TONE_SOFT_VAR: Record<StatusTone, string> = {
  ok: 'var(--cim-status-ok-soft)',
  warn: 'var(--cim-status-warn-soft)',
  err: 'var(--cim-status-err-soft)',
  run: 'var(--cim-status-run-soft)',
  idle: 'var(--cim-status-idle-soft)',
};

/**
 * 域 → 枚举值 → 语义档。
 *
 * 依据（**每条都能在代码里指到出处**，这是本表唯一的准入标准）：
 * - `recipeState`：RMS `RecipeVersionStatus`（DRAFT / PENDING_SIGN / ACTIVE / OBSOLETE）。
 * - `downloadStage`：RMS IF-E2 下发阶段 `DownloadStage`
 *   （QUEUED → GRANTED → SENT_TO_TOOL → TOOL_ACKED → VERIFIED / FAILED）。
 * - `signoffState`：RMS `SignoffStatus`（PENDING / APPROVED / REJECTED）。
 * - `approvalState`：approval-ap 流程实例状态——服务端 `ApprovalProcessService` 里按
 *   `running ? "ACTIVE" : (deleteReason != null ? "WITHDRAWN" : "COMPLETED")` 产出，
 *   即 **ACTIVE / COMPLETED / WITHDRAWN**。
 * - `bypassState`：RMS `Bypass['status']`（ACTIVE / SCHEDULED / EXPIRED / REVOKED）。
 * - `overrideState`：RMS 资格矩阵 `Qualification.status` 与黄金覆盖 `GoldenOverride.status`
 *   共用（两者枚举与语义相同：PENDING / APPROVED / REJECTED 的审批结论）。
 *
 * ⚠️ **2026-11-12 修正**：`approvalState` 原先登记为
 * RUNNING / APPROVED / REJECTED / RETURNED —— 那是 Flowable 的结束事件语义，
 * 不是本 ap 的实例状态，与后端实际产出**不符**（值全错、多两个不存在的值）。
 * 同时 `RecipeDetailDrawer` 自己写了份 `DRAFT → run`，与本表的 `DRAFT → idle` 分叉。
 * 这类「表里写一套、页面写另一套」的错误比没有表更难查，所以下面只留核实过的值。
 */
export const STATUS_TONES: Record<string, Record<string, StatusTone>> = {
  recipeState: {
    DRAFT: 'idle',
    PENDING_SIGN: 'warn',
    ACTIVE: 'ok',
    OBSOLETE: 'idle',
  },
  downloadStage: {
    QUEUED: 'idle',
    GRANTED: 'run',
    SENT_TO_TOOL: 'run',
    TOOL_ACKED: 'run',
    VERIFIED: 'ok',
    FAILED: 'err',
  },
  signoffState: {
    PENDING: 'warn',
    APPROVED: 'ok',
    REJECTED: 'err',
  },
  approvalState: {
    ACTIVE: 'run',
    COMPLETED: 'ok',
    WITHDRAWN: 'warn',
  },
  bypassState: {
    ACTIVE: 'ok',
    // 已排期未生效：给「需关注」的橙，与 ACTIVE 的绿拉开，避免现场把未生效当已生效
    SCHEDULED: 'warn',
    EXPIRED: 'err',
    REVOKED: 'err',
  },
  overrideState: {
    PENDING: 'warn',
    APPROVED: 'ok',
    REJECTED: 'err',
  },
  /** 签核单上的「预设限时放行」窗口状态；`NONE` = 未预设（页面据此显示占位符而非标签）。 */
  tempReleaseState: {
    NONE: 'idle',
    ACTIVE: 'ok',
    EXPIRED: 'err',
  },
};

/** 取某枚举值的语义档；未登记的域/值一律回落 `idle`（不抛错，避免一处漏登记导致白屏）。 */
export function toneOf(dict: string, value: string | null | undefined): StatusTone {
  if (!value) return 'idle';
  return STATUS_TONES[dict]?.[value] ?? 'idle';
}

/** 该域是否已登记（供组件在开发态提示「字典未登记」，不用于运行时分支判断）。 */
export function hasDict(dict: string): boolean {
  return Object.prototype.hasOwnProperty.call(STATUS_TONES, dict);
}
