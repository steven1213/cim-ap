import { useTranslation } from 'react-i18next';

export default function ComingSoon({ title }: { title?: string }) {
  const { t } = useTranslation();
  return (
    <div className="page">
      <div className="page-head">
        <h1>{title ?? t('rms.comingSoon')}</h1>
      </div>
      <div className="empty-state">
        <div className="empty-icon">🚧</div>
        <h2>{t('rms.comingSoon')}</h2>
        <p>{t('rms.comingSoon.desc')}</p>
      </div>
    </div>
  );
}
