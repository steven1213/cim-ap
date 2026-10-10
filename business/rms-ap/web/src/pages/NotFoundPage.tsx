import { useTranslation } from 'react-i18next';

export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <div className="empty-state">
      <div className="empty-icon">🧭</div>
      <h2>404</h2>
      <p>{t('rms.notFound')}</p>
    </div>
  );
}
