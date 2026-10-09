import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { IconAlert } from '@/components/Icons';

/** 404：路径不在当前用户的菜单集内（菜单即路由，菜单被移除/未授权即不可达）。 */
export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <div className="panel">
      <div className="panel-body">
        <div className="empty">
          <IconAlert width={28} height={28} />
          <b>{t('iam.common.notfound.title')}</b>
          <p>{t('iam.common.notfound.desc')}</p>
          <Link className="btn" to="/">{t('iam.common.backHome')}</Link>
        </div>
      </div>
    </div>
  );
}
