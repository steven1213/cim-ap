import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimTag,
  DataTable,
  Page,
  useConfirm,
  useToast,
  StatusTag,
  type DataTableColumn,
} from '@cim/ui';
import DetailModal from '@/components/DetailModal';
import { IconBell, IconSearch, IconShield, IconUndo } from '@/components/Icons';
import { DisplayFields } from '@/components/DisplayFields';
import { listInitiated, urgeInstance, withdrawInstance } from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { fmtDate, fmtOverdue } from '@/lib/format';
import type { ProcessInstanceView } from '@/types';

const PAGE_SIZE = 20;

export default function InitiatedPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const confirm = useConfirm();
  const toast = useToast();
  const { userId } = useIdentityStore();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(PAGE_SIZE);
  const [q, setQ] = useState('');
  const [detailId, setDetailId] = useState<string | null>(null);
  /** 逐行锁：只锁被点的那一行，别把整张表的按钮都变灰。 */
  const [busyId, setBusyId] = useState<string | null>(null);

  /** 全量取回、本地分页（理由同 DonePage：关键字只在当前页生效等于搜不到）。 */
  const initRes = useResource<ProcessInstanceView[]>(
    ({ signal }) => listInitiated(undefined, undefined, { signal }),
    { deps: [userId] },
  );

  const all = initRes.data ?? [];
  const filtered = useMemo(() => {
    const kw = q.trim().toLowerCase();
    if (!kw) return all;
    return all.filter((pi) =>
      [pi.title, pi.businessKey, pi.processKey].some((v) => v && v.toLowerCase().includes(kw)),
    );
  }, [all, q]);

  const pageRows = filtered.slice((page - 1) * size, page * size);

  async function onUrge(pi: ProcessInstanceView) {
    setBusyId(pi.processInstanceId);
    try {
      await urgeInstance(pi.processInstanceId);
      toast.success(t('init.urgeSent'));
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusyId(null);
    }
  }

  async function onWithdraw(pi: ProcessInstanceView) {
    const ok = await confirm({
      title: t('init.withdraw'),
      content: t('init.withdrawConfirm', { bk: pi.businessKey }),
      okKey: 'init.withdraw',
      danger: true,
    });
    if (!ok) return;
    setBusyId(pi.processInstanceId);
    try {
      await withdrawInstance(pi.processInstanceId);
      toast.success(t('init.withdrawDone'));
      initRes.refresh();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusyId(null);
    }
  }

  const columns: Array<DataTableColumn<ProcessInstanceView>> = [
    { key: 'businessKey', titleKey: 'init.colBusinessKey', width: 170, mono: true },
    {
      key: 'title',
      titleKey: 'common.colTitle',
      ellipsis: true,
      render: (pi) => (
        <>
          {pi.title || '—'}
          <DisplayFields fields={pi.displayFields} />
        </>
      ),
    },
    { key: 'processKey', titleKey: 'init.colProcess', width: 150, mono: true },
    {
      key: 'status',
      titleKey: 'init.colStatus',
      width: 220,
      render: (pi) => (
        <span className="cell-inline">
          <StatusTag dict="approvalState" value={pi.status} label={t('init.status.' + pi.status)} />
          {pi.round != null && pi.round > 1 && <CimTag tone="run">{t('init.roundBadge', { n: pi.round })}</CimTag>}
          {pi.awaitingResubmit && <CimTag tone="warn">{t('init.awaitingResubmit')}</CimTag>}
          {pi.overdueMinutes != null && (
            <CimTag tone="err">
              {t('init.overdue')} {fmtOverdue(pi.overdueMinutes)}
            </CimTag>
          )}
        </span>
      ),
    },
    { key: 'startedAt', titleKey: 'init.colStarted', width: 160, mono: true, render: (pi) => fmtDate(pi.startedAt) },
    { key: 'endedAt', titleKey: 'init.colEnded', width: 160, mono: true, render: (pi) => fmtDate(pi.endedAt) },
    {
      key: 'ops',
      titleKey: 'common.actions',
      width: 300,
      render: (pi) => {
        const busy = busyId === pi.processInstanceId;
        return (
          <span className="cell-actions">
            <CimButton size="small" icon={<IconSearch width={13} height={13} />} onClick={() => setDetailId(pi.processInstanceId)}>
              {t('common.detail')}
            </CimButton>
            {pi.status === 'ACTIVE' && (
              <>
                <CimButton size="small" disabled={busy} icon={<IconBell width={13} height={13} />} onClick={() => onUrge(pi)}>
                  {t('init.urge')}
                </CimButton>
                <CimButton size="small" danger disabled={busy} icon={<IconUndo width={13} height={13} />} onClick={() => onWithdraw(pi)}>
                  {t('init.withdraw')}
                </CimButton>
              </>
            )}
            <CimButton
              size="small"
              icon={<IconShield width={13} height={13} />}
              onClick={() => navigate(`/audit?businessKey=${encodeURIComponent(pi.businessKey)}`)}
            >
              {t('init.viewAudit')}
            </CimButton>
          </span>
        );
      },
    },
  ];

  return (
    <Page>
        <DataTable<ProcessInstanceView>
          titleKey="init.title"
          columns={columns}
          rows={pageRows}
          rowKey={(pi) => pi.processInstanceId}
          loading={initRes.loading}
          error={initRes.error ? initRes.error.message : null}
          onRetry={initRes.refresh}
          onRefresh={initRes.refresh}
          search={{
            value: q,
            onChange: (v) => {
              setQ(v);
              setPage(1);
            },
            placeholderKey: 'common.searchPlaceholder',
            width: 240,
          }}
          pagination={{
            page,
            size,
            total: filtered.length,
            onChange: (p, s) => {
              setPage(s === size ? p : 1);
              setSize(s);
            },
            sizeOptions: [10, 20, 50, 100],
          }}
          emptyTitleKey="common.empty"
          scrollX="max-content"
        />

      {detailId && <DetailModal processInstanceId={detailId} onClose={() => setDetailId(null)} onChanged={initRes.refresh} />}
    </Page>
  );
}
