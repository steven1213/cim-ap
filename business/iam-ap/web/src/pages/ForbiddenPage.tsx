import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { IconBan } from '@/components/Icons';

/**
 * 403：已登录但无该页权限。
 *
 * <p>触发场景有两类：① 菜单被授权但对应权限码未授予（`sys_role_menu` 与 `sys_role_perm`
 * 是两套授权，允许交叉）；② 直接手输地址访问未授权路由。前端守卫只是体验层，
 * 后端 `@PreAuthorize` 才是最终判据。</p>
 */
export default function ForbiddenPage() {
  const { t } = useTranslation();
  return (
    <div className="panel">
      <div className="panel-body">
        <div className="empty">
          <IconBan width={28} height={28} />
          <b>{t('iam.common.forbidden.title')}</b>
          <p>{t('iam.common.forbidden.desc')}</p>
          <Link className="btn" to="/">{t('iam.common.backHome')}</Link>
        </div>
      </div>
    </div>
  );
}
