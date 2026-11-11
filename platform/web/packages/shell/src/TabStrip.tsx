// 页签条（tags-view）：把侧栏菜单打开的页面聚合成一行横向标签。
//
// <p><b>交互契约</b>（三份实现原本逐字相同，抽出来时一条没改）：
// <ul>
//   <li>固定签（`pinned`）无关闭徽标，右键「关闭标签页」对其禁用；</li>
//   <li>右键菜单：关闭标签页 / 关闭其它 / 关闭右侧 / 关闭全部；</li>
//   <li>页签溢出时两侧出现滚动箭头，激活签变化时自动滚入可视区；</li>
//   <li>关闭激活签后去哪，由应用的 store 决定（本组件只回调 `onClose`）。</li>
// </ul></p>
//
// <p>组件本身不持有任何全局状态：页签数组、激活路径、关闭语义全部从外部进来，
// 所以它既能接 zustand store，也能在探针页里用数组直接驱动（P3 的验收就是这么做的）。</p>
import { useCallback, useEffect, useRef, useState } from 'react';
import { IconChevron, IconX } from './icons';
import type { ShellTabLabels, ShellTabs } from './types';

/** 右键菜单状态：屏幕坐标 + 目标签路径。 */
interface CtxMenu {
  x: number;
  y: number;
  path: string;
}

export type TabStripProps = Omit<ShellTabs, 'syncToken'> & {
  labels: ShellTabLabels;
  /** 额外重算几何的触发值（路由 key / 语言 / 滚动票证），变化即重测。 */
  syncToken?: unknown;
};

export function TabStrip({ tabs, activePath, labels, syncToken, ...handlers }: TabStripProps) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const ctxRef = useRef<HTMLDivElement>(null);
  const [ctx, setCtx] = useState<CtxMenu | null>(null);
  const [canLeft, setCanLeft] = useState(false);
  const [canRight, setCanRight] = useState(false);
  /** 滑动胶囊指示器：激活签在滚动内容坐标系里的位置与宽度（content 坐标，随滚动自然移动）。 */
  const [indicator, setIndicator] = useState({ left: 0, width: 0, ready: false });

  /**
   * 页签内容签名。effect 的依赖不能用 `tabs` 数组本身：调用方（`useShellTabs`）
   * 每次 render 都会 `.map` 出新数组，引用永远不等 → effect 每次都跑 →
   * `setIndicator` 又触发 render → 无限循环。用内容签名把「真的变了」和
   * 「引用变了」分开，切语言时 label 变（宽度变）也能正确重测。
   */
  const tabsSig = tabs.map((t) => t.path + '\u0001' + t.label).join('\u0002');

  /** 依据滚动位置刷新左右箭头的可用态（scroll 事件 + ResizeObserver 双触发）。 */
  const updateArrows = useCallback(() => {
    const el = scrollerRef.current;
    if (!el) return;
    setCanLeft(el.scrollLeft > 1);
    setCanRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 1);
  }, []);

  useEffect(() => {
    updateArrows();
    const el = scrollerRef.current;
    if (!el) return;
    const onScroll = () => updateArrows();
    el.addEventListener('scroll', onScroll, { passive: true });
    const ro = new ResizeObserver(onScroll);
    ro.observe(el);
    return () => {
      el.removeEventListener('scroll', onScroll);
      ro.disconnect();
    };
  }, [updateArrows, tabsSig]);

  /**
   * 同步激活签：滚入可视区（贴缘留 24px）+ 更新滑动胶囊指示器几何。
   *
   * <p>双 rAF 延后一帧再量几何，等 flex/字体布局稳定。测量用
   * `getBoundingClientRect` 差值 + `scrollLeft`（`offsetLeft` 相对 offsetParent，
   * 受祖先定位影响会算偏）。</p>
   */
  const syncTabs = useCallback(() => {
    requestAnimationFrame(() =>
      requestAnimationFrame(() => {
        const el = scrollerRef.current;
        if (!el) return;
        const track = el.querySelector<HTMLElement>('.tab-track');
        const active = el.querySelector<HTMLElement>('[data-active="true"]');
        if (!track || !active) {
          setIndicator((s) => ({ ...s, ready: false }));
          return;
        }
        // 指示器与签同以 track 为参照（同一滚动内容系，rect 差值免滚动、免 padding 干扰）
        const left = active.getBoundingClientRect().left - track.getBoundingClientRect().left;
        const width = active.offsetWidth;
        setIndicator({ left, width, ready: true });
        // 滚动判定仍以 scroller 为参照：签的视口位置决定是否需要滚入
        const contentLeft = left + (track.getBoundingClientRect().left - el.getBoundingClientRect().left) + el.scrollLeft;
        const r = contentLeft + width;
        if (contentLeft < el.scrollLeft + 8) {
          el.scrollTo({ left: Math.max(0, contentLeft - 24), behavior: 'smooth' });
        } else if (r > el.scrollLeft + el.clientWidth - 8) {
          el.scrollTo({ left: r - el.clientWidth + 24, behavior: 'smooth' });
        }
      }),
    );
  }, []);

  useEffect(() => {
    syncTabs();
  }, [syncTabs, activePath, tabsSig, syncToken]);

  // 右键菜单：点击外部 / Escape 关闭
  useEffect(() => {
    if (!ctx) return;
    const onDown = (e: MouseEvent) => {
      if (ctxRef.current && !ctxRef.current.contains(e.target as Node)) setCtx(null);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setCtx(null);
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [ctx]);

  function scrollByDir(dir: -1 | 1) {
    const el = scrollerRef.current;
    if (!el) return;
    el.scrollBy({ left: dir * Math.max(180, el.clientWidth * 0.6), behavior: 'smooth' });
  }

  /** 页签右键菜单（对齐参考实现：关闭标签页 / 关闭其它 / 关闭右侧）。 */
  function onTabCtx(e: React.MouseEvent, path: string) {
    e.preventDefault();
    // 简单钳位，避免菜单溢出视口右/下缘
    setCtx({
      x: Math.min(e.clientX, window.innerWidth - 170),
      y: Math.min(e.clientY, window.innerHeight - 130),
      path,
    });
  }

  const ctxIdx = ctx ? tabs.findIndex((x) => x.path === ctx.path) : -1;

  return (
    <div className="tabbar" role="tablist" aria-label={labels.label}>
      {canLeft && (
        <button
          type="button"
          className="icon-btn tab-nav"
          aria-label={labels.scrollLeft}
          title={labels.scrollLeft}
          onClick={() => scrollByDir(-1)}
        >
          <span className="rot-l">
            <IconChevron width={14} height={14} />
          </span>
        </button>
      )}

      <div className="tabbar-scroll" ref={scrollerRef}>
        <div className="tab-track">
          {/* 滑动胶囊指示器：位于签层之下，随激活签平滑滑动（content 坐标，随滚动平移） */}
          <span
            className={'tab-indicator' + (indicator.ready ? ' on' : '')}
            style={{
              width: indicator.width,
              transform: `translateX(${indicator.left}px)`,
            }}
            aria-hidden="true"
          />
          {tabs.map((tab) => {
            const active = tab.path === activePath;
            return (
              <div
                key={tab.path}
                role="tab"
                aria-selected={active}
                data-active={active}
                className={'tab-item' + (active ? ' active' : '') + (tab.pinned ? ' pinned' : '')}
                title={tab.label}
                onClick={() => handlers.onSelect(tab.path)}
                onContextMenu={(e) => onTabCtx(e, tab.path)}
              >
                <span className="tab-label">{tab.label}</span>
                {!tab.pinned && (
                  <button
                    type="button"
                    className="tab-x"
                    aria-label={labels.close}
                    title={labels.close}
                    onClick={(e) => {
                      e.stopPropagation();
                      handlers.onClose(tab.path);
                    }}
                  >
                    <IconX width={8} height={8} />
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {canRight && (
        <button
          type="button"
          className="icon-btn tab-nav"
          aria-label={labels.scrollRight}
          title={labels.scrollRight}
          onClick={() => scrollByDir(1)}
        >
          <IconChevron width={14} height={14} />
        </button>
      )}

      {ctx && (
        <div className="tab-ctx" role="menu" ref={ctxRef} style={{ left: ctx.x, top: ctx.y }}>
          <button
            type="button"
            role="menuitem"
            disabled={!!tabs.find((x) => x.path === ctx.path)?.pinned}
            onClick={() => {
              const p = ctx.path;
              setCtx(null);
              handlers.onClose(p);
            }}
          >
            {labels.closeThis}
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              const p = ctx.path;
              setCtx(null);
              handlers.onCloseOthers(p);
            }}
          >
            {labels.closeOthers}
          </button>
          <button
            type="button"
            role="menuitem"
            disabled={ctxIdx >= tabs.length - 1}
            onClick={() => {
              const p = ctx.path;
              setCtx(null);
              handlers.onCloseRight(p);
            }}
          >
            {labels.closeRight}
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setCtx(null);
              handlers.onCloseAll();
            }}
          >
            {labels.closeAll}
          </button>
        </div>
      )}
    </div>
  );
}
