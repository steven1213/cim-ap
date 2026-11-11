import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { EmptyState, Panel } from '@cim/ui';
import { IconShield } from '@/components/Icons';

export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <Panel>
      <EmptyState large title="404" hint={t('notfound.desc')}>
        <Link to="/" className="btn btn-sm">
          <IconShield width={14} height={14} /> {t('notfound.back')}
        </Link>
      </EmptyState>
    </Panel>
  );
}
