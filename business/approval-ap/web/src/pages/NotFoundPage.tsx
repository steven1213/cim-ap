import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { NotFoundState } from '@cim/ui';

/** 404：地址不在审批中心的路由表内。 */
export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <NotFoundState titleKey="notfound.title" hintKey="notfound.desc">
      <Link className="btn" to="/">
        {t('notfound.back')}
      </Link>
    </NotFoundState>
  );
}
