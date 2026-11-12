import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import { CimButton, Can, CimTag, DataTable, Page, Panel, useToast, type DataTableColumn } from '@cim/ui';
import * as api from '@/lib/api';
import type { LockRow } from '@/types';
import { fmtDateTime } from '@/lib/format';
import { USER_UNLOCK } from '@/lib/permCodes';
import { IconKey } from '@/components/Icons';

/** 登录锁定管理：列出被锁账号并支持手动解锁（解锁按钮按 `iam:user:unlock` 显隐）。 */
export default function LockoutsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const [unlocking, setUnlocking] = useState<string | null>(null);

  const listRes = useResource<LockRow[]>(({ signal }) => api.get<LockRow[]>('/admin/lockouts', undefined, { signal }));
  const rows = listRes.data ?? [];

  async function onUnlock(username: string) {
    // 逐行锁：只禁当前行按钮。整页 busy 会让「连点两行」看起来像卡死
    setUnlocking(username);
    try {
      const cleared = await api.del<boolean>(`/admin/lockouts/${encodeURIComponent(username)}`);
      toast.success(
        cleared
          ? t('iam.lockouts.msgUnlocked', { user: username })
          : t('iam.lockouts.msgNoLock', { user: username }),
      );
      listRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.lockouts.errUnlock'));
    } finally {
      setUnlocking(null);
    }
  }

  const columns = useMemo<Array<DataTableColumn<LockRow>>>(
    () => [
      { key: 'username', titleKey: 'iam.lockouts.th.username', mono: true, render: (r) => r.username },
      {
        key: 'failCount',
        titleKey: 'iam.lockouts.th.failures',
        width: 110,
        align: 'right',
        render: (r) => r.failCount,
      },
      {
        key: 'firstFailAt',
        titleKey: 'iam.lockouts.th.first',
        width: 180,
        mono: true,
        render: (r) => <span className="dim">{fmtDateTime(r.firstFailAt)}</span>,
      },
      {
        key: 'lastFailAt',
        titleKey: 'iam.lockouts.th.last',
        width: 180,
        mono: true,
        render: (r) => <span className="dim">{fmtDateTime(r.lastFailAt)}</span>,
      },
      {
        key: 'lockedUntil',
        titleKey: 'iam.lockouts.th.until',
        width: 180,
        mono: true,
        render: (r) => fmtDateTime(r.lockedUntil),
      },
      {
        key: 'actions',
        titleKey: 'iam.lockouts.th.actions',
        width: 120,
        render: (r) => (
          <Can perm={USER_UNLOCK}>
            <CimButton
              size="small"
              onClick={() => void onUnlock(r.username)}
              disabled={unlocking === r.username}
            >
              <IconKey width={13} height={13} />
              {t('iam.lockouts.unlock')}
            </CimButton>
          </Can>
        ),
      },
    ],
    // onUnlock 只依赖 listRes.refresh 与 unlocking；两者都在闭包外，随渲染更新
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, unlocking],
  );

  return (
    <Page>
      <Panel
        title={t('iam.lockouts.title')}
        actions={<CimTag tone={rows.length ? 'warn' : 'idle'}>{t('iam.lockouts.count', { n: rows.length })}</CimTag>}
      >
        <DataTable<LockRow>
          flat
          columns={columns}
          rows={rows}
          rowKey={(r) => r.username}
          loading={listRes.loading}
          error={listRes.error?.message}
          onRetry={listRes.refresh}
          onRefresh={listRes.refresh}
          settingsKey="iam.lockouts"
          emptyTitleKey="iam.lockouts.emptyTitle"
          emptyHintKey="iam.lockouts.emptyHint"
        />
      </Panel>

      <Panel title={t('iam.lockouts.policyTitle')}>
        <ul className="note-list">
          <li>{t('iam.lockouts.policy1')}</li>
          <li>{t('iam.lockouts.policy2')}</li>
          <li>{t('iam.lockouts.policy3')}</li>
          <li>{t('iam.lockouts.policy4')}</li>
        </ul>
      </Panel>
    </Page>
  );
}
