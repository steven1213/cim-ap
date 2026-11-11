import { useTranslation } from 'react-i18next';
import { EmptyState, Panel } from '@cim/ui';

export default function ForbiddenPage() {
  const { t } = useTranslation();
  return (
    <Panel>
      <EmptyState large title="403" hint={t('rms.forbidden')} />
    </Panel>
  );
}
