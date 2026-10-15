// 多标签导航条（tags-view）：点击菜单打开的页签在此聚合成一行。
//
// <p>交互契约对齐 IAM / RMS：固定签（工作台）、右键菜单（关闭/关闭其它/关闭右侧/关闭全部）、
// 溢出滚动箭头、激活签自动滚入、滑动胶囊指示器。</p>
//
// <p>标题解析优先取 {@link NAV_FLAT} 的文案码（随导航配置即时更新），回退到开签时快照。</p>
import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { NAV_FLAT } from '@/lib/navConfig';
import { HOME_PATH, useTabStore } from '@/store/tabStore';
import { IconChevron, IconX } from '@/components/Icons';

interface CtxMenu {
  x: number;
  y: number;
  path: string;
}

export default function TabBar() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const tabs = useTabStore((s) => s.tabs);
  const activePath = useTabStore((s) => s.activePath);
  const scrollTicket = useTabStore((s) => s.scrollTicket);
  const requestScroll = useTabStore((s) => s.requestScroll);
  const setActive = useTabStore((s) => s.setActive);
  const close = useTabStore((s) => s.close);
  const closeOthers = useTabStore((s) => s.closeOthers);
  const closeRight = useTabStore((s) => s.closeRight);
  const closeAll = useTabStore((s) => s.closeAll);

  const scrollerRef = useRef<HTMLDivElement>(null);
  const ctxRef = useRef<HTMLDivElement>(null);
  const [ctx, setCtx] = useState<CtxMenu | null>(null);
  const [canLeft, setCanLeft] = useState(false);
  const [canRight, setCanRight] = useState(false);
  const [indicator, setIndicator] = useState({ left: 0, width: 0, ready: false });

  function titleOf(path: string, i18nCode: string): string {
    const code = NAV_FLAT.find((m) => m.to === path)?.i18n ?? i18nCode;
    return code ? t(code) : path;
  }

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
        const left = active.getBoundingClientRect().left - track.getBoundingClientRect().left;
        const width = active.offsetWidth;
        setIndicator({ left, width, ready: true });
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
  }, [syncTabs, activePath, tabs, location.key, scrollTicket, i18n.language]);

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
    if (path === activePath) {
      requestScroll();
      return;
    }
    setActive(path);
    navigate(path);
  }

  function onCloseBtn(e: React.MouseEvent, path: string) {
    e.stopPropagation();
    const next = close(path);
    if (next) navigate(next);
  }

  function onTabCtx(e: React.MouseEvent, path: string) {
    e.preventDefault();
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
    <div className="tabbar" role="tablist" aria-label={t('shell.tabs.label')}>
      {canLeft && (
        <button
          type="button"
          className="icon-btn tab-nav"
          aria-label={t('shell.tabs.scrollLeft')}
          title={t('shell.tabs.scrollLeft')}
          onClick={() => scrollByDir(-1)}
        >
          <span className="rot-l">
            <IconChevron width={14} height={14} />
          </span>
        </button>
      )}

      <div className="tabbar-scroll" ref={scrollerRef}>
        <div className="tab-track">
          <span
            className={'tab-indicator' + (indicator.ready ? ' on' : '')}
            style={{ width: indicator.width, transform: `translateX(${indicator.left}px)` }}
            aria-hidden="true"
          />
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
                    aria-label={t('shell.tabs.close')}
                    title={t('shell.tabs.close')}
                    onClick={(e) => onCloseBtn(e, tab.path)}
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
          aria-label={t('shell.tabs.scrollRight')}
          title={t('shell.tabs.scrollRight')}
          onClick={() => scrollByDir(1)}
        >
          <IconChevron width={14} height={14} />
        </button>
      )}

      {ctx && (
        <div className="tab-ctx" role="menu" ref={ctxRef} style={{ left: ctx.x, top: ctx.y }}>
          <button type="button" role="menuitem" disabled={ctx.path === HOME_PATH} onClick={ctxCloseThis}>
            {t('shell.tabs.closeThis')}
          </button>
          <button type="button" role="menuitem" onClick={ctxCloseOthers}>
            {t('shell.tabs.closeOthers')}
          </button>
          <button
            type="button"
            role="menuitem"
            disabled={tabs.findIndex((x) => x.path === ctx.path) >= tabs.length - 1}
            onClick={ctxCloseRight}
          >
            {t('shell.tabs.closeRight')}
          </button>
          <button type="button" role="menuitem" onClick={ctxCloseAll}>
            {t('shell.tabs.closeAll')}
          </button>
        </div>
      )}
    </div>
  );
}
