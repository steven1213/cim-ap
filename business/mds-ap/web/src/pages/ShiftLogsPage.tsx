import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimModal,
  DataTable,
  FormGrid,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { ShiftLogPayload, ShiftLogView, ShiftUserOption } from '@/types';

/**
 * 交接班记录（ShiftLogsPage，V17）——责任文书：交班提交 → 接班签收 → 锁定只读。
 *
 * <p>内容三结构化字段（设备异常/遗留事项/备注）。签收走乐观锁；已签收记录任何
 * 字段不可改。workDate 留空时后端按当前时刻落在哪个班次推算归属（跨天班次的
 * 凌晨交接归前一日的值守日）。</p>
 */

const NO_LOGS: ShiftLogView[] = [];
const NO_USERS: ShiftUserOption[] = [];

const LOG_TITLE = {
  editKey: 'mds.shiftLog.editTitle',
  createKey: 'mds.shiftLog.createTitle',
} as const;

export default function ShiftLogsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [shiftFilter, setShiftFilter] = useState('');
  const logsRes = useResource(({ signal }) => api.listShiftLogs(shiftFilter || undefined, signal), {
    deps: [shiftFilter],
  });
  const logs = logsRes.data ?? NO_LOGS;
  const usersRes = useResource(({ signal }) => api.listShiftUsers(signal));
  const users = usersRes.data ?? NO_USERS;
  const shiftsRes = useResource(({ signal }) => api.listEnabledShifts(signal));
  const shifts = shiftsRes.data ?? [];

  const [form, setForm] = useState<{ mode: 'create' } | { mode: 'edit'; log: ShiftLogView } | null>(
    null,
  );
  const [detail, setDetail] = useState<ShiftLogView | null>(null);

  const doAck = async (row: ShiftLogView) => {
    const ok = await confirm({
      title: t('mds.shiftLog.ackTitle'),
      content: t('mds.shiftLog.ackConfirm', { date: row.workDate, shift: row.shiftName }),
      okKey: 'mds.shiftLog.ackOk',
    });
    if (!ok) return;
    try {
      await api.ackShiftLog(row.id, row.version);
      toast.success(t('mds.shiftLog.acked'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    logsRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<ShiftLogView>>>(
    () => [
      { key: 'workDate', titleKey: 'mds.shiftLog.workDate', width: 110, mono: true },
      { key: 'shiftName', titleKey: 'mds.shiftLog.shift', width: 110 },
      { key: 'handoverName', titleKey: 'mds.shiftLog.handover', width: 110, render: (r) => r.handoverName ?? r.handoverUser },
      { key: 'receiverName', titleKey: 'mds.shiftLog.receiver', width: 110, render: (r) => r.receiverName ?? r.receiverUser ?? '—' },
      {
        key: 'pendingItems',
        titleKey: 'mds.shiftLog.pendingItems',
        render: (r) => <span className="dim">{r.pendingItems ?? '—'}</span>,
      },
      {
        key: 'acked',
        titleKey: 'mds.shiftLog.status',
        width: 100,
        render: (r) =>
          r.acked ? (
            <span className="dim">
              {t('mds.shiftLog.acked')} · {r.ackName ?? r.ackBy}
            </span>
          ) : (
            <span className="cim-mono">{t('mds.shiftLog.unacked')}</span>
          ),
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 190,
        render: (r) => (
          <span className="cell-actions">
            <CimButton size="small" onClick={() => setDetail(r)}>
              {t('mds.common.detail')}
            </CimButton>
            {!r.acked && (
              <>
                <Can perm={MDS_PERMS.shiftlog.save}>
                  <CimButton size="small" onClick={() => setForm({ mode: 'edit', log: r })}>
                    {t('mds.common.edit')}
                  </CimButton>
                  <CimButton size="small" onClick={() => void doAck(r)}>
                    {t('mds.shiftLog.ackOk')}
                  </CimButton>
                </Can>
              </>
            )}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <Panel
        title={t('mds.shiftLog.panelTitle')}
        sub={t('mds.shiftLog.panelSub')}
        flush
        actions={
          <>
            <select
              className="cim-select"
              value={shiftFilter}
              onChange={(e) => setShiftFilter(e.target.value)}
              aria-label={t('mds.shiftLog.shift')}
            >
              <option value="">{t('mds.shiftLog.allShifts')}</option>
              {shifts.map((s) => (
                <option key={s.shiftCode} value={s.shiftCode}>
                  {s.shiftName}
                </option>
              ))}
            </select>
            <Can perm={MDS_PERMS.shiftlog.save}>
              <CimButton size="small" onClick={() => setForm({ mode: 'create' })}>
                {t('mds.shiftLog.writeNew')}
              </CimButton>
            </Can>
          </>
        }
      >
        <DataTable<ShiftLogView>
          columns={columns}
          rows={logs}
          rowKey={(r) => r.id}
          loading={logsRes.loading}
          error={logsRes.error?.message ?? null}
          onRetry={logsRes.refresh}
        />
      </Panel>

      {form && (
        <LogFormModal
          state={form}
          users={users}
          onClose={() => setForm(null)}
          onDone={() => {
            setForm(null);
            logsRes.refresh();
          }}
        />
      )}
      {detail && (
        <CimModal
          open
          onClose={() => setDetail(null)}
          titleKey="mds.shiftLog.detailTitle"
          onOk={() => setDetail(null)}
          okKey="mds.common.close"
          cancelKey="mds.common.close"
          width={560}
        >
          <div className="log-detail">
            <p>
              <span className="dim">{t('mds.shiftLog.workDate')}：</span>
              <span className="mono">{detail.workDate}</span>
              <span className="dim">　{t('mds.shiftLog.shift')}：</span>{detail.shiftName}
            </p>
            <p>
              <span className="dim">{t('mds.shiftLog.handover')}：</span>
              {detail.handoverName ?? detail.handoverUser}
              <span className="dim">　→　{t('mds.shiftLog.receiver')}：</span>
              {detail.receiverName ?? detail.receiverUser ?? '—'}
            </p>
            <h4>{t('mds.shiftLog.equipmentIssue')}</h4>
            <p className="log-detail-text">{detail.equipmentIssue ?? '—'}</p>
            <h4>{t('mds.shiftLog.pendingItems')}</h4>
            <p className="log-detail-text">{detail.pendingItems ?? '—'}</p>
            <h4>{t('mds.shiftLog.remarks')}</h4>
            <p className="log-detail-text">{detail.remarks ?? '—'}</p>
            <p className="dim">
              {detail.acked
                ? t('mds.shiftLog.ackedBy', { name: detail.ackName ?? detail.ackBy ?? '', at: detail.ackAt ?? '' })
                : t('mds.shiftLog.unacked')}
            </p>
          </div>
        </CimModal>
      )}
    </Page>
  );
}

/** 交接记录新建/编辑弹窗（workDate 留空 = 后端按当前时刻推算）。 */
function LogFormModal({
  state,
  users,
  onClose,
  onDone,
}: {
  state: { mode: 'create' } | { mode: 'edit'; log: ShiftLogView };
  users: ShiftUserOption[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const initial = state.mode === 'edit' ? state.log : null;
  const [form, setForm] = useState({
    shiftCode: initial?.shiftCode ?? '',
    workDate: initial?.workDate ?? '',
    receiverUser: initial?.receiverUser ?? '',
    equipmentIssue: initial?.equipmentIssue ?? '',
    pendingItems: initial?.pendingItems ?? '',
    remarks: initial?.remarks ?? '',
  });

  const shiftsRes = useResource(({ signal }) => api.listEnabledShifts(signal));
  const shifts = shiftsRes.data ?? [];

  const userOptions = users.map((u) => ({ value: u.userId, label: u.username }));
  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      { kind: 'select', key: 'shiftCode', labelKey: 'mds.shiftLog.shift', options: shifts.map((s) => ({ value: s.shiftCode, label: s.shiftName })), required: true },
      { kind: 'date', key: 'workDate', labelKey: 'mds.shiftLog.workDate', valueFormat: 'yyyy-MM-dd', allowClear: true, placeholderKey: 'mds.shiftLog.workDatePlaceholder' },
      { kind: 'select', key: 'receiverUser', labelKey: 'mds.shiftLog.receiver', options: userOptions, allowClear: true, placeholderKey: 'mds.shiftLog.receiverPlaceholder' },
      { kind: 'textarea', key: 'equipmentIssue', labelKey: 'mds.shiftLog.equipmentIssue', span: 2, rows: 3 },
      { kind: 'textarea', key: 'pendingItems', labelKey: 'mds.shiftLog.pendingItems', span: 2, rows: 3 },
      { kind: 'textarea', key: 'remarks', labelKey: 'mds.shiftLog.remarks', span: 2, rows: 3 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [shifts, userOptions],
  );

  async function submit() {
    if (!form.shiftCode) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const receiver = users.find((u) => u.userId === form.receiverUser);
      const payload: ShiftLogPayload = {
        id: initial?.id ?? null,
        shiftCode: form.shiftCode,
        workDate: form.workDate || null,
        receiverUser: form.receiverUser || null,
        receiverName: receiver?.username ?? null,
        equipmentIssue: form.equipmentIssue || null,
        pendingItems: form.pendingItems || null,
        remarks: form.remarks || null,
      };
      await api.saveShiftLog(payload);
      toast.success(t('mds.common.saved'));
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey={initial ? LOG_TITLE.editKey : LOG_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.shiftLog.submitOk"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={620}
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
