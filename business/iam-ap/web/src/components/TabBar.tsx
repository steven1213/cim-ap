// 多标签导航条（tags-view）：点击菜单打开的页签在此聚合成一行。
//
// <p><b>交互契约</b>：
// <ul>
//   <li>「概览」为固定签（pinned），无关闭钮，关闭全部/其他后仍保留；</li>
//   <li>页签溢出时两侧出现滚动箭头（按容器 60% 步进平滑滚动），激活签变化时自动滚入可视区；</li>
//   <li>右侧「更多」下拉：关闭其他 / 关闭全部；关闭激活签时由 {@link useTabStore}
//       决定相邻签并导航过去（优先右邻）。</li>
// </ul></p>
//
// <p>标题解析优先取菜单库的 `i18nCode`（随授权即时更新），回退到开签时快照——
// 只存码不存译文，切语言即时生效。</p>
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useMenuStore } from '@/store/menuStore';
import { HOME_PATH, useTabStore } from '@/store/tabStore';
import { IconChevron, IconX } from '@/components/Icons';

export default function TabBar() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const tabs = useTabStore((s) => s.tabs);
  const activePath = useTabStore((s) => s.activePath);
  const setActive = useTabStore((s) => s.setActive);
  const close = useTabStore((s) => s.close);
  const closeOthers = useTabStore((s) => s.closeOthers);
  const closeAll = useTabStore((s) => s.closeAll);
  const flat = useMenuStore((s) => s.flat);

  const scrollerRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const [menuOpen, setMenuOpen] = useState(false);
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

  // 激活签变化（含切语言导致宽度变化后）自动滚入可视区
  useEffect(() => {
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
  }, [activePath, tabs]);

  // 下拉菜单：点击外部 / Escape 关闭
  useEffect(() => {
    if (!menuOpen) return;
    const onDown = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [menuOpen]);

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

  function onClose(e: React.MouseEvent, path: string) {
    e.stopPropagation();
    const next = close(path);
    if (next) navigate(next);
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
            >
              <span className="tab-dot" aria-hidden="true" />
              <span className="tab-label">{title}</span>
              {!pinned && (
                <button
                  type="button"
                  className="tab-x"
                  aria-label={t('iam.shell.tabs.close')}
                  title={t('iam.shell.tabs.close')}
                  onClick={(e) => onClose(e, tab.path)}
                >
                  <IconX width={10} height={10} />
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

      <div className="tabbar-actions" ref={menuRef}>
        <button
          type="button"
          className={'icon-btn' + (menuOpen ? ' on' : '')}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          aria-label={t('iam.shell.tabs.more')}
          title={t('iam.shell.tabs.more')}
          onClick={() => setMenuOpen((v) => !v)}
        >
          <span className="rot-d">
            <IconChevron width={14} height={14} />
          </span>
        </button>
        {menuOpen && (
          <div className="tab-menu" role="menu">
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                closeOthers(activePath);
                setMenuOpen(false);
              }}
            >
              {t('iam.shell.tabs.closeOthers')}
            </button>
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                const home = closeAll();
                setMenuOpen(false);
                if (activePath !== home) navigate(home);
              }}
            >
              {t('iam.shell.tabs.closeAll')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
