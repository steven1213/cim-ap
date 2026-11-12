import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { NotFoundState } from '@cim/ui';

/** 404：路径不在当前用户的菜单集内（菜单即路由，菜单被移除/未授权即不可达）。 */
export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <NotFoundState titleKey="iam.common.notfound.title" hintKey="iam.common.notfound.desc">
      <Link className="btn" to="/">
        {t('iam.common.backHome')}
      </Link>
    </NotFoundState>
  );
}
