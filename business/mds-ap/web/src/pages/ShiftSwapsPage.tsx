import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimCheckTag,
  CimModal,
  DataTable,
  FormGrid,
  Page,
  Panel,
  StatusTag,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { ShiftSwapPayload, ShiftSwapView, ShiftUserOption } from '@/types';

/**
 * 换班申请（ShiftSwapsPage，V19）——员工自助换班/代班动线。
 *
 * <p>状态机（方案定稿）：申请（PENDING）→ 对方确认即生效（APPLIED，排班同事务落库）/
 * 拒绝（REJECTED）/ 发起人取消（CANCELLED）；班长审批为预留位（APPROVING 预留档 +
 * approval_id 列，将来接 approval-ap 平滑升级）。调班走排班安排页单格编辑（管理员
 * 动线），本页只管「员工间换班」。</p>
 */

const NO_SWAPS: ShiftSwapView[] = [];
const NO_USERS: ShiftUserOption[] = [];

/** 状态过滤档（全部 + 五个业务态；预留档本期不落库不进过滤器）。 */
const STATUS_FILTERS = ['ALL', 'PENDING', 'APPLIED', 'REJECTED', 'CANCELLED'] as const;

export default function ShiftSwapsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const swapsRes = useResource(({ signal }) => api.listShiftSwaps(undefined, signal));
  const swaps = swapsRes.data ?? NO_SWAPS;
  const rows = useMemo(
    () =>
      statusFilter === 'ALL' ? swaps : swaps.filter((s) => s.status === statusFilter),
    [swaps, statusFilter],
  );

  const [applyOpen, setApplyOpen] = useState(false);

  const doConfirm = async (row: ShiftSwapView) => {
    const ok = await confirm({
      title: t('mds.shiftSwap.confirmTitle'),
      content: t('mds.shiftSwap.confirmContent', {
        initiator: row.initiatorName ?? row.initiatorId,
        date: row.initiatorWorkDate,
        shift: row.initiatorShiftCode,
      }),
      okKey: 'mds.shiftSwap.confirmOk',
    });
    if (!ok) return;
    try {
      await api.confirmShiftSwap(row.id);
      toast.success(t('mds.shiftSwap.appliedToast'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    swapsRes.refresh();
  };

  const doReject = async (row: ShiftSwapView) => {
    const ok = await confirm({
      title: t('mds.shiftSwap.rejectTitle'),
      content: t('mds.shiftSwap.rejectContent', {
        initiator: row.initiatorName ?? row.initiatorId,
      }),
      danger: true,
      okKey: 'mds.shiftSwap.rejectOk',
    });
    if (!ok) return;
    try {
      await api.rejectShiftSwap(row.id);
      toast.success(t('mds.common.saved'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    swapsRes.refresh();
  };

  const doCancel = async (row: ShiftSwapView) => {
    const ok = await confirm({
      title: t('mds.shiftSwap.cancelTitle'),
      content: t('mds.shiftSwap.cancelContent'),
      danger: true,
      okKey: 'mds.shiftSwap.cancelOk',
    });
    if (!ok) return;
    try {
      await api.cancelShiftSwap(row.id);
      toast.success(t('mds.common.saved'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    swapsRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<ShiftSwapView>>>(
    () => [
      {
        key: 'swapType',
        titleKey: 'mds.shiftSwap.type',
        width: 90,
        render: (r) => t(r.swapType === 'SWAP' ? 'mds.shiftSwap.typeSwap' : 'mds.shiftSwap.typeSubstitute'),
      },
      {
        key: 'initiator',
        titleKey: 'mds.shiftSwap.initiator',
        width: 130,
        render: (r) => (
          <span>
            {r.initiatorName ?? r.initiatorId}
            <span className="dim mono"> · {r.initiatorWorkDate} {r.initiatorShiftCode}</span>
          </span>
        ),
      },
      {
        key: 'target',
        titleKey: 'mds.shiftSwap.target',
        width: 170,
        render: (r) => (
          <span>
            {r.targetName ?? r.targetId}
            {r.targetWorkDate ? (
              <span className="dim mono"> · {r.targetWorkDate} {r.targetShiftCode}</span>
            ) : null}
          </span>
        ),
      },
      { key: 'reason', titleKey: 'mds.shiftSwap.reason', ellipsis: true, render: (r) => <span className="dim">{r.reason || '—'}</span> },
      {
        key: 'status',
        titleKey: 'mds.shiftSwap.status',
        width: 110,
        render: (r) => (
          <StatusTag dict="swapStatus" value={r.status} label={t(`mds.shiftSwap.status.${r.status}`)} />
        ),
      },
      {
        key: 'result',
        titleKey: 'mds.shiftSwap.result',
        width: 170,
        render: (r) => {
          if (r.status === 'APPLIED') {
            return <span className="dim">{t('mds.shiftSwap.appliedAt')} {r.appliedAt?.replace('T', ' ').slice(0, 16)}</span>;
          }
          if (r.status === 'REJECTED') return <span className="dim">{r.rejectReason || '—'}</span>;
          if (r.targetConfirmName) {
            return <span className="dim">{t('mds.shiftSwap.confirmedBy', { name: r.targetConfirmName })}</span>;
          }
          return <span className="dim">—</span>;
        },
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 180,
        render: (r) =>
          r.status === 'PENDING' ? (
            <span className="cell-actions">
              <Can perm={MDS_PERMS.shiftswap.save}>
                <CimButton size="small" onClick={() => void doConfirm(r)}>
                  {t('mds.shiftSwap.confirmOk')}
                </CimButton>
                <CimButton size="small" danger onClick={() => void doReject(r)}>
                  {t('mds.shiftSwap.rejectOk')}
                </CimButton>
                <CimButton size="small" onClick={() => void doCancel(r)}>
                  {t('mds.shiftSwap.cancelOk')}
                </CimButton>
              </Can>
            </span>
          ) : (
            <span className="dim">—</span>
          ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <div className="shift-swap-filter">
        {STATUS_FILTERS.map((s) => (
          <CimCheckTag
            key={s}
            checked={statusFilter === s}
            onChange={() => setStatusFilter(s)}
          >
            {t(s === 'ALL' ? 'mds.shiftSwap.filterAll' : `mds.shiftSwap.status.${s}`)}
          </CimCheckTag>
        ))}
        <span className="dim shift-swap-hint">{t('mds.shiftSwap.hint')}</span>
      </div>

      <Panel
        title={t('mds.shiftSwap.panelTitle')}
        sub={t('mds.shiftSwap.panelSub')}
        flush
        actions={
          <Can perm={MDS_PERMS.shiftswap.save}>
            <CimButton size="small" onClick={() => setApplyOpen(true)}>
              {t('mds.shiftSwap.applyBtn')}
            </CimButton>
          </Can>
        }
      >
        <DataTable<ShiftSwapView>
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id}
          loading={swapsRes.loading}
          error={swapsRes.error?.message ?? null}
          onRetry={swapsRes.refresh}
        />
      </Panel>

      {applyOpen && (
        <ApplyModal
          onClose={() => setApplyOpen(false)}
          onDone={() => {
            setApplyOpen(false);
            swapsRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

/** 申请弹窗（SWAP 需对方班定位；SUBSTITUTE 只填发起人班与顶班人）。 */
function ApplyModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    swapType: 'SWAP' as 'SWAP' | 'SUBSTITUTE',
    myWorkDate: '',
    myShiftCode: '',
    targetId: '',
    targetWorkDate: '',
    targetShiftCode: '',
    reason: '',
  });

  const shiftsRes = useResource(({ signal }) => api.listShifts(signal));
  const usersRes = useResource(({ signal }) => api.listShiftUsers(signal));
  const shiftOptions = (shiftsRes.data ?? []).map((s) => ({ value: s.shiftCode, label: s.shiftName }));
  const userOptions = (usersRes.data ?? NO_USERS).map((u) => ({
    value: u.userId,
    label: u.username,
  }));

  const swap = form.swapType === 'SWAP';
  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      {
        kind: 'select' as const,
        key: 'swapType' as const,
        labelKey: 'mds.shiftSwap.type',
        options: [
          { value: 'SWAP', label: t('mds.shiftSwap.typeSwap') },
          { value: 'SUBSTITUTE', label: t('mds.shiftSwap.typeSubstitute') },
        ],
      },
      { kind: 'date' as const, key: 'myWorkDate' as const, labelKey: 'mds.shiftSwap.myWorkDate', valueFormat: 'YYYY-MM-DD', required: true },
      { kind: 'select' as const, key: 'myShiftCode' as const, labelKey: 'mds.shiftSwap.myShift', options: shiftOptions, required: true },
      { kind: 'select' as const, key: 'targetId' as const, labelKey: 'mds.shiftSwap.targetUser', options: userOptions, required: true, placeholderKey: 'mds.shiftSwap.targetPlaceholder' },
      ...(swap
        ? [
            { kind: 'date' as const, key: 'targetWorkDate' as const, labelKey: 'mds.shiftSwap.targetWorkDate', valueFormat: 'YYYY-MM-DD', required: true },
            { kind: 'select' as const, key: 'targetShiftCode' as const, labelKey: 'mds.shiftSwap.targetShift', options: shiftOptions, required: true },
          ]
        : []),
      { kind: 'textarea' as const, key: 'reason' as const, labelKey: 'mds.shiftSwap.reason', span: 2, rows: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [swap, shiftsRes.data, usersRes.data, t],
  );

  async function submit() {
    if (!form.myWorkDate || !form.myShiftCode || !form.targetId) {
      setErr(t('mds.common.required'));
      return;
    }
    if (swap && (!form.targetWorkDate || !form.targetShiftCode)) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    const payload: ShiftSwapPayload = {
      swapType: form.swapType,
      targetId: form.targetId,
      myWorkDate: form.myWorkDate,
      myShiftCode: form.myShiftCode,
      reason: form.reason || undefined,
      ...(swap
        ? { targetWorkDate: form.targetWorkDate, targetShiftCode: form.targetShiftCode }
        : {}),
    };
    try {
      await api.applyShiftSwap(payload);
      toast.success(t('mds.common.saved'));
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="mds.shiftSwap.applyTitle"
      onOk={() => void submit()}
      okKey="mds.shiftSwap.submitOk"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={520}
    >
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        columns={2}
      />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
