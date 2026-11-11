import { useTranslation } from 'react-i18next';
import { EmptyState, Panel } from '@cim/ui';

export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <Panel>
      <EmptyState large title="404" hint={t('rms.notFound')} />
    </Panel>
  );
}
