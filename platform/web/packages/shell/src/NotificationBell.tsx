// 顶栏通知铃（展示层 + 交互）。
//
// <p><b>为什么只做展示层</b>：RMS 与 approval 的「通知」根本不是同一个东西——
// RMS 有持久表（`rms_notification`）、未读数轮询、标记已读、点击直达相关页；
// approval 没有通知表，只有 SSE 收到即入内存的会话级列表（后端注释也写了
// 「后续可按各 AP 自管原则补通知表」）。把数据语义也塞进组件，就得为一个
// 尚未存在的表建模。所以边界画在 props：组件收「未读数 + 条目 + 三个回调」，
// 各自的数据来源、SSE 建连参数、toast 提示都留在应用侧。</p>
//
// <p><b>为什么要统一</b>：两边的**界面**曾经是两套——RMS 用 `.bell-panel`、
// approval 用 `.tab-ctx` 菜单，而 approval 的 `styles.css` 里还留着一整段
// 从未被引用的 `.bell-*`（1377–1470 行，与 RMS 那份逐字节相同）。段在那说明
// 本来就是这套，只是后来被临时替换掉了。这里把 `.bell-*` 收进来，两边共用。</p>

import { useEffect, useRef, useState } from 'react';
import { CimButton, IconBell } from '@cim/ui';

/** 一条通知（展示所需的最小集）。 */
export interface ShellNotifyItem {
  id: string;
  title: string;
  /** 正文摘要。允许 `null`：后端可空列直出时不必每个调用点先做兜底。 */
  content?: string | null;
  /** 已格式化好的时间文案（不做日期库依赖）。 */
  time?: string | null;
  /** 未读态：false 时条目不显示未读点、标题不加粗。不传按「已读」处理。 */
  read?: boolean;
}

/** 已翻译文案（外壳约定收字符串，见 types.ts）。 */
export interface NotificationBellLabels {
  /** 面板标题，同时用作按钮的 title / aria-label。 */
  title: string;
  empty: string;
  loading?: string;
  /** 「全部已读」；不传即不渲染该按钮。 */
  readAll?: string;
}

export interface NotificationBellProps {
  /** 未读数（角标）。>99 显示 `99+`。 */
  unread: number;
  items: ShellNotifyItem[];
  labels: NotificationBellLabels;
  /** 列表加载中：显示 loading 文案而非空态。 */
  loading?: boolean;
  /** 打开弹层时调用（拉一次列表）。不传则打开时不做任何事（纯内存列表）。 */
  onRefresh?: () => void | Promise<void>;
  /**
   * 开合状态变化。
   *
   * <p>需要它的理由很具体：RMS 的 SSE 回调要判断「面板开着才顺手刷新列表」，
   * 而开合状态由组件自己管。把状态提给应用会让三个应用重复写一遍点外关闭。</p>
   */
  onOpenChange?: (open: boolean) => void;
  /** 点条目：一般是「标记已读 + 跳转」。不传则条目不可点。 */
  onOpenItem?: (item: ShellNotifyItem) => void;
  /** 全部已读。不传则不渲染按钮。 */
  onReadAll?: () => void;
  /** 实时通道连接态；传了才在标题前渲染状态灯。 */
  connected?: boolean;
  /** 连接态的悬停说明（如「实时通知已连接 / 已断开」）。 */
  connectedTitle?: string;
}

/**
 * 通知铃铛。
 *
 * <p>交互细节：点外部关闭（`mousedown` 而非 `click`——否则点面板外的另一个按钮
 * 会先触发关闭再触发该按钮，视觉上是「点了一下没反应」）；每次打开都先调
 * `onRefresh`（红点的真相在数据侧，面板里的列表可能已过期）。</p>
 */
export function NotificationBell({
  unread,
  items,
  labels,
  loading = false,
  onRefresh,
  onOpenChange,
  onOpenItem,
  onReadAll,
  connected,
  connectedTitle,
}: NotificationBellProps) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  // 开合变化通知外部（含点外关闭），用 ref 承接回调避免把它挂进 effect 依赖
  const openChangeRef = useRef(onOpenChange);
  openChangeRef.current = onOpenChange;
  useEffect(() => {
    openChangeRef.current?.(open);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  function toggle() {
    const next = !open;
    setOpen(next);
    if (next) void onRefresh?.();
  }

  return (
    <div className="bell-wrap" ref={wrapRef}>
      <CimButton
        bare
        className="icon-btn"
        onClick={toggle}
        title={labels.title}
        aria-label={labels.title}
        aria-expanded={open}
      >
        <IconBell width={17} height={17} />
        {unread > 0 && <span className="bell-dot">{unread > 99 ? '99+' : unread}</span>}
      </CimButton>

      {open && (
        <div className="bell-panel">
          <div className="bell-head">
            <b>
              {connected !== undefined && (
                <span
                  className={`led ${connected ? 'ok' : 'err'}`}
                  style={{ marginRight: 6 }}
                  title={connectedTitle}
                />
              )}
              {labels.title}
            </b>
            {onReadAll && labels.readAll ? (
              <CimButton size="small" onClick={onReadAll} disabled={unread === 0}>
                {labels.readAll}
              </CimButton>
            ) : null}
          </div>

          <div className="bell-list">
            {loading && labels.loading ? <div className="bell-empty">{labels.loading}</div> : null}
            {!loading && items.length === 0 ? (
              <div className="bell-empty">{labels.empty}</div>
            ) : null}
            {!loading &&
              items.map((n) => {
                const unreadItem = n.read === false;
                const body = (
                  <>
                    <span className="bell-item-title">
                      {unreadItem && <i className="bell-unread-led" />}
                      {n.title}
                    </span>
                    {n.content && <span className="bell-item-sub">{n.content}</span>}
                    {n.time && <span className="bell-item-time">{n.time}</span>}
                  </>
                );
                // 不可点时用 div：`<button>` 不带 onClick 依然会有 hover/焦点态，
                // 用户会以为能点却点不动。
                return onOpenItem ? (
                  <CimButton
                    bare
                    key={n.id}
                    className={'bell-item' + (unreadItem ? ' unread' : '')}
                    onClick={() => onOpenItem(n)}
                  >
                    {body}
                  </CimButton>
                ) : (
                  <div key={n.id} className={'bell-item' + (unreadItem ? ' unread' : '')}>
                    {body}
                  </div>
                );
              })}
          </div>
        </div>
      )}
    </div>
  );
}
