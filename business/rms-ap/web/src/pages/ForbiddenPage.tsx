import { useTranslation } from 'react-i18next';

export default function ForbiddenPage() {
  const { t } = useTranslation();
  return (
    <div className="empty-state">
      <div className="empty-icon">🔒</div>
      <h2>403</h2>
      <p>{t('rms.forbidden')}</p>
    </div>
  );
}
