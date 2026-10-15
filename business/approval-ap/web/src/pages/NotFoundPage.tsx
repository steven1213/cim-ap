import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { IconShield } from '@/components/Icons';

export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <div className="page">
      <div className="empty-state">
        <div className="empty-icon">404</div>
        <h2>{t('notfound.title')}</h2>
        <p>{t('notfound.desc')}</p>
        <Link to="/" className="btn btn-sm" style={{ marginTop: 8 }}>
          <IconShield width={14} height={14} /> {t('notfound.back')}
        </Link>
      </div>
    </div>
  );
}
