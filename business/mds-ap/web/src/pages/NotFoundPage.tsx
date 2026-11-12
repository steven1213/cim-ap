import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { NotFoundState } from '@cim/ui';

/** 404：地址不在 MDS 的路由表内（菜单即路由，菜单被移除/未授权即不可达）。 */
export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <NotFoundState titleKey="mds.notFound" hintKey="mds.notFoundDesc">
      <Link className="btn" to="/">
        {t('mds.common.backHome')}
      </Link>
    </NotFoundState>
  );
}
