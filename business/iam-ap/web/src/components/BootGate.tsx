import { useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/store/authStore';
import { useMenuStore } from '@/store/menuStore';
import { IconAlert } from '@/components/Icons';

/**
 * 启动数据闸门：登录态下，先取齐「菜单树 + 权限码集」再渲染控制台。
 *
 * <p><b>为什么必须等齐</b>：动态路由由菜单树派生、按钮显隐由权限码集派生。若先渲染再补齐，
 * 会出现「先闪一下 403 / 按钮缺失 → 再跳变」的抖动，甚至因为菜单未到而把当前地址判为未授权
 * （误跳 404）。集中在一处等待，页面内部即可假定两者已就绪。</p>
 */
export default function BootGate({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const token = useAuthStore((s) => s.token);
  const permissionsLoaded = useAuthStore((s) => s.permissionsLoaded);
  const loadPermissions = useAuthStore((s) => s.loadPermissions);
  const loadMe = useAuthStore((s) => s.loadMe);
  const menuLoaded = useMenuStore((s) => s.loaded);
  const loadMenus = useMenuStore((s) => s.load);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!token) return;
    let alive = true;
    setError(null);
    Promise.all([
      loadMenus(attempt > 0),
      loadPermissions(),
      // 档案失败不阻塞进入控制台（页头显示「未登录」而非白屏）
      loadMe().catch(() => {}),
    ]).catch((e: any) => {
      if (alive) setError(e?.message || t('iam.common.loading'));
    });
    return () => {
      alive = false;
    };
    // loadMe/loadMenus/loadPermissions 均为 store 上的稳定引用
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, attempt]);

  if (token && !error && (!menuLoaded || !permissionsLoaded)) {
    return (
      <div className="boot-splash">
        <div className="panel">
          <div className="panel-body">
            <div className="empty">
              <b>{t('iam.common.loading')}</b>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="boot-splash">
        <div className="panel">
          <div className="panel-body">
            <div className="empty">
              <IconAlert width={28} height={28} />
              <b>{t('iam.common.menuFailed')}</b>
              <p>{error}</p>
              <button onClick={() => setAttempt((v) => v + 1)}>{t('iam.common.retry')}</button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
