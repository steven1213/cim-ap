// 多标签导航条（tags-view）：点击菜单打开的页签在此聚合成一行。
//
// <p><b>交互契约</b>：
// <ul>
//   <li>「概览」为固定签（pinned），无关闭徽标，右键菜单的「关闭标签页」对其禁用；</li>
//   <li>每个签支持<b>右键菜单</b>：关闭标签页 / 关闭其它标签页 / 关闭右侧标签页 /
//       关闭全部标签页（对齐 Ant Design Pro / vue-element-admin 的 tags-view 惯例）；</li>
//   <li>页签溢出时两侧出现滚动箭头，激活签变化时自动滚入可视区；</li>
//   <li>关闭激活签时由 {@link useTabStore} 决定相邻签并导航过去（优先右邻）。</li>
// </ul></p>
//
// <p>标题解析优先取菜单库的 `i18nCode`（随授权即时更新），回退到开签时快照——
// 只存码不存译文，切语言即时生效。</p>
import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useMenuStore } from '@/store/menuStore';
import { HOME_PATH, useTabStore } from '@/store/tabStore';
import { IconChevron, IconX } from '@/components/Icons';

/** 右键菜单状态：屏幕坐标 + 目标签路径。 */
interface CtxMenu {
  x: number;
  y: number;
  path: string;
}

export default function TabBar() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const tabs = useTabStore((s) => s.tabs);
  const activePath = useTabStore((s) => s.activePath);
  const setActive = useTabStore((s) => s.setActive);
  const close = useTabStore((s) => s.close);
  const closeOthers = useTabStore((s) => s.closeOthers);
  const closeRight = useTabStore((s) => s.closeRight);
  const closeAll = useTabStore((s) => s.closeAll);
  const flat = useMenuStore((s) => s.flat);

  const scrollerRef = useRef<HTMLDivElement>(null);
  const ctxRef = useRef<HTMLDivElement>(null);
  const [ctx, setCtx] = useState<CtxMenu | null>(null);
  const [canLeft, setCanLeft] = useState(false);
  const [canRight, setCanRight] = useState(false);

  /** 标题：菜单库优先（码最新），回退开签快照；两者皆空显示路径。 */
  function titleOf(path: string, i18nCode: string): string {
    const code = flat.find((m) => m.path === path)?.i18nCode ?? i18nCode;
    return code ? t(code) : path;
  }

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
  }, [updateArrows, tabs]);

  /**
   * 把激活签滚入可视区（贴左/右缘留 24px 余量）。
   *
   * <p>触发时机不能只看 `activePath`/`tabs`——「激活签未变但滚动位置被
   * 挪走」（如用箭头翻页后再点同一菜单，`location.key` 仍会变）时也必须
   * 拉回，否则激活签滞留在可视区外。</p>
   */
  const ensureActiveVisible = useCallback(() => {
    const el = scrollerRef.current;
    if (!el) return;
    const active = el.querySelector<HTMLElement>('[data-active="true"]');
    if (!active) return;
    const l = active.offsetLeft;
    const r = l + active.offsetWidth;
    if (l < el.scrollLeft + 8) {
      el.scrollTo({ left: Math.max(0, l - 24), behavior: 'smooth' });
    } else if (r > el.scrollLeft + el.clientWidth - 8) {
      el.scrollTo({ left: r - el.clientWidth + 24, behavior: 'smooth' });
    }
  }, []);

  // 激活签变化、页签增删、以及任意路由跳转（含重复点击同一菜单）后滚入可视区
  useEffect(() => {
    ensureActiveVisible();
  }, [ensureActiveVisible, activePath, tabs, location.key]);

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

  function onTab(path: string) {
    if (path === activePath) return;
    setActive(path);
    navigate(path);
  }

  function onCloseBtn(e: React.MouseEvent, path: string) {
    e.stopPropagation();
    const next = close(path);
    if (next) navigate(next);
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

  function ctxCloseThis() {
    if (!ctx) return;
    const next = close(ctx.path);
    setCtx(null);
    if (next) navigate(next);
  }

  function ctxCloseOthers() {
    if (!ctx) return;
    closeOthers(ctx.path);
    setCtx(null);
  }

  function ctxCloseRight() {
    if (!ctx) return;
    const next = closeRight(ctx.path);
    setCtx(null);
    if (next) navigate(next);
  }

  function ctxCloseAll() {
    if (!ctx) return;
    const home = closeAll();
    setCtx(null);
    if (activePath !== home) navigate(home);
  }

  return (
    <div className="tabbar" role="tablist" aria-label={t('iam.shell.tabs.label')}>
      {canLeft && (
        <button
          type="button"
          className="icon-btn tab-nav"
          aria-label={t('iam.shell.tabs.scrollLeft')}
          title={t('iam.shell.tabs.scrollLeft')}
          onClick={() => scrollByDir(-1)}
        >
          <span className="rot-l">
            <IconChevron width={14} height={14} />
          </span>
        </button>
      )}

      <div className="tabbar-scroll" ref={scrollerRef}>
        {tabs.map((tab) => {
          const active = tab.path === activePath;
          const pinned = tab.path === HOME_PATH;
          const title = titleOf(tab.path, tab.i18nCode);
          return (
            <div
              key={tab.path}
              role="tab"
              aria-selected={active}
              data-active={active}
              className={'tab-item' + (active ? ' active' : '') + (pinned ? ' pinned' : '')}
              title={title}
              onClick={() => onTab(tab.path)}
              onContextMenu={(e) => onTabCtx(e, tab.path)}
            >
              <span className="tab-label">{title}</span>
              {!pinned && (
                <button
                  type="button"
                  className="tab-x"
                  aria-label={t('iam.shell.tabs.close')}
                  title={t('iam.shell.tabs.close')}
                  onClick={(e) => onCloseBtn(e, tab.path)}
                >
                  <IconX width={8} height={8} />
                </button>
              )}
            </div>
          );
        })}
      </div>

      {canRight && (
        <button
          type="button"
          className="icon-btn tab-nav"
          aria-label={t('iam.shell.tabs.scrollRight')}
          title={t('iam.shell.tabs.scrollRight')}
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
            disabled={ctx.path === HOME_PATH}
            onClick={ctxCloseThis}
          >
            {t('iam.shell.tabs.closeThis')}
          </button>
          <button type="button" role="menuitem" onClick={ctxCloseOthers}>
            {t('iam.shell.tabs.closeOthers')}
          </button>
          <button
            type="button"
            role="menuitem"
            disabled={tabs.findIndex((x) => x.path === ctx.path) >= tabs.length - 1}
            onClick={ctxCloseRight}
          >
            {t('iam.shell.tabs.closeRight')}
          </button>
          <button type="button" role="menuitem" onClick={ctxCloseAll}>
            {t('iam.shell.tabs.closeAll')}
          </button>
        </div>
      )}
    </div>
  );
}
