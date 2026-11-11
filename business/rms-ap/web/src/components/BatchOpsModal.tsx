import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  CimModal,
  CimSelect,
  CimTag,
  DataTable,
  useToast,
  type DataTableColumn,
  CimButton,
  CimInput,
  CimField,
} from '@cim/ui';
import * as api from '@/lib/rmsApi';
import type { BatchDownloadItem, BatchOpResult, DeviceType } from '@/types';

const ACTION_OPTIONS = [
  { value: 'APPLY', labelKey: 'rms.batch.apply' },
  { value: 'RELEASE', labelKey: 'rms.batch.release' },
];

/**
 * 批量操作弹窗（W9）：批量 Hold（APPLY/RELEASE，按设备/机型/配方展开）与批量下发。
 * 结果逐项报告（部分成功语义），失败项明细可见。
 *
 * <p>迁移要点：手写 `.modal-backdrop` → `CimModal`；三组「勾选清单」改成可搜索的多选下拉
 * —— 设备上百台时原清单会把弹窗撑成一整屏，且无法按编码搜。</p>
 */
export function BatchHoldModal({
  devices,
  types,
  recipes,
  onClose,
  onDone,
}: {
  devices: { id: string; code: string; name: string }[];
  types: DeviceType[];
  recipes: { id: string; code: string; name: string }[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [action, setAction] = useState<'APPLY' | 'RELEASE'>('APPLY');
  const [form, setForm] = useState<{ deviceIds: string[]; typeIds: string[]; recipeIds: string[]; reason: string }>({
    deviceIds: [],
    typeIds: [],
    recipeIds: [],
    reason: '',
  });
  const [busy, setBusy] = useState(false);
  const [report, setReport] = useState<BatchOpResult | null>(null);

  const run = async () => {
    if (!form.reason.trim()) {
      toast.warning(t('rms.batch.reasonRequired'));
      return;
    }
    setBusy(true);
    try {
      const r = await api.batchHold({
        action,
        deviceIds: form.deviceIds,
        deviceTypeIds: form.typeIds,
        recipeIds: form.recipeIds,
        reason: form.reason.trim(),
      });
      setReport(r);
      onDone();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.batch.holdTitle"
      width={780}
      footer={
        report ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <CimButton type="primary" onClick={onClose}>
              {t('rms.common.close')}
            </CimButton>
          </div>
        ) : undefined
      }
      onOk={report ? undefined : run}
      okKey="rms.batch.run"
      okLoading={busy}
      cancelKey="rms.common.close"
      maskClosable={false}
    >
      <CimField label={t('rms.batch.action')}>
        <CimSelect
          value={action}
          onChange={(v) => setAction((typeof v === 'string' ? v : 'APPLY') as 'APPLY' | 'RELEASE')}
          options={ACTION_OPTIONS}
          allowClear={false}
          width="100%"
        />
      </CimField>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 10 }}>
        <CimField label={t('rms.batch.devices')}>
          <CimSelect
            multiple
            value={form.deviceIds}
            onChange={(v) => setForm((prev) => ({ ...prev, deviceIds: Array.isArray(v) ? v : v ? [v] : [] }))}
            options={devices.map((d) => ({ value: d.id, label: d.code, hint: d.name }))}
            width="100%"
          />
        </CimField>
        <CimField label={t('rms.batch.byTypes')}>
          <CimSelect
            multiple
            value={form.typeIds}
            onChange={(v) => setForm((prev) => ({ ...prev, typeIds: Array.isArray(v) ? v : v ? [v] : [] }))}
            options={types.map((d) => ({ value: d.id, label: d.code, hint: d.name }))}
            width="100%"
          />
        </CimField>
        <CimField
          label={
            <>
              {t('rms.batch.recipes')}{' '}
              <span className="dim" style={{ fontWeight: 400, marginLeft: 6 }}>
                {' '}
                {t('rms.batch.emptyMeansDeviceLevel')}{' '}
              </span>
            </>
          }
        >
          <CimSelect
            multiple
            value={form.recipeIds}
            onChange={(v) => setForm((prev) => ({ ...prev, recipeIds: Array.isArray(v) ? v : v ? [v] : [] }))}
            options={recipes.map((r) => ({ value: r.id, label: r.code, hint: r.name }))}
            width="100%"
          />
        </CimField>
        <CimField label={t('rms.batch.reason')} required>
          <CimInput value={form.reason} onChange={(v) => setForm((prev) => ({ ...prev, reason: v }))} />
        </CimField>
      </div>

      {report ? <BatchReport report={report} /> : null}
    </CimModal>
  );
}

export function BatchDownloadModal({
  devices,
  recipes,
  onClose,
  onDone,
}: {
  devices: { id: string; code: string; name: string }[];
  recipes: { id: string; code: string; name: string }[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [rows, setRows] = useState<BatchDownloadItem[]>([{ toolId: '', recipeUid: '' }]);
  const [requestor, setRequestor] = useState('');
  const [busy, setBusy] = useState(false);
  const [report, setReport] = useState<BatchOpResult | null>(null);

  const setRow = (i: number, patch: Partial<BatchDownloadItem>) =>
    setRows((list) => list.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  const run = async () => {
    const valid = rows.filter((r) => r.toolId && r.recipeUid);
    if (valid.length === 0) {
      toast.warning(t('rms.batch.needOneItem'));
      return;
    }
    setBusy(true);
    try {
      const r = await api.batchDownload({
        downloadMode: 'IMMEDIATE',
        requestor: requestor || undefined,
        items: valid,
      });
      setReport(r);
      onDone();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.batch.downloadTitle"
      width={820}
      footer={
        report ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <CimButton type="primary" onClick={onClose}>
              {t('rms.common.close')}
            </CimButton>
          </div>
        ) : undefined
      }
      onOk={report ? undefined : run}
      okKey="rms.batch.run"
      okLoading={busy}
      cancelKey="rms.common.close"
      maskClosable={false}
    >
      <CimField label={t('rms.batch.requestor')} style={{ marginBottom: 10 }}>
        <CimInput value={requestor} onChange={setRequestor} />
      </CimField>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {rows.map((row, i) => (
          <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
            <CimField label={<>{t('rms.batch.device')} {i + 1}</>}>
              <CimSelect
                value={row.toolId || null}
                onChange={(v) => setRow(i, { toolId: typeof v === 'string' ? v : '' })}
                options={devices.map((d) => ({ value: d.id, label: d.code, hint: d.name }))}
                width="100%"
              />
            </CimField>
            <CimField label={<>{t('rms.batch.recipe')} {i + 1}</>}>
              <CimSelect
                value={row.recipeUid || null}
                onChange={(v) => setRow(i, { recipeUid: typeof v === 'string' ? v : '' })}
                options={recipes.map((r) => ({ value: r.id, label: r.code, hint: r.name }))}
                width="100%"
              />
            </CimField>
          </div>
        ))}
      </div>

      <div style={{ marginTop: 8 }}>
        <CimButton
          size="small"
          onClick={() => setRows((l) => [...l, { toolId: '', recipeUid: '' }])}
        >
          + {t('rms.batch.addRow')}
        </CimButton>
      </div>

      {report ? <BatchReport report={report} /> : null}
    </CimModal>
  );
}

/** 逐项结果报告（部分成功语义：成功/失败一目了然）。 */
export function BatchReport({ report }: { report: BatchOpResult }) {
  const { t } = useTranslation();

  const columns = useMemo<Array<DataTableColumn<BatchOpResult['items'][number]>>>(
    () => [
      {
        key: 'ok',
        titleKey: 'rms.common.status',
        width: 80,
        render: (it) => (
          <CimTag size="small" tone={it.ok ? 'ok' : 'err'}>
            {it.ok ? 'OK' : 'FAIL'}
          </CimTag>
        ),
      },
      { key: 'key', titleKey: 'rms.batch.key', mono: true, width: 140 },
      {
        key: 'target',
        titleKey: 'rms.batch.target',
        mono: true,
        width: 180,
        render: (it) => <span className="muted">{it.target}</span>,
      },
      {
        key: 'message',
        titleKey: 'rms.audit.th.detail',
        render: (it) => <span className="muted">{it.message}</span>,
      },
    ],
    [t],
  );

  return (
    <div style={{ marginTop: 12 }}>
      <div style={{ marginBottom: 6 }}>
        <CimTag tone="ok">{t('rms.batch.okCount', { n: report.ok })}</CimTag>{' '}
        <CimTag tone={report.failed > 0 ? 'err' : 'idle'}>
          {t('rms.batch.failedCount', { n: report.failed })}
        </CimTag>
      </div>
      <DataTable
        flat
        columns={columns}
        rows={report.items}
        rowKey={(it) => `${it.key}-${it.target}`}
        scrollX="max-content"
        showCount={false}
        emptyTitleKey="rms.common.noData"
      />
    </div>
  );
}
