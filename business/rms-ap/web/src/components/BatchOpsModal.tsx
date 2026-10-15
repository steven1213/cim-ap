import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/rmsApi';
import type { BatchOpResult, DeviceType, BatchDownloadItem } from '@/types';

/**
 * 批量操作弹窗（W9）：批量 Hold（APPLY/RELEASE，按设备/机型/配方展开）与批量下发。
 * 结果逐项报告（部分成功语义），失败项明细可见。
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
  const [action, setAction] = useState<'APPLY' | 'RELEASE'>('APPLY');
  const [deviceIds, setDeviceIds] = useState<string[]>([]);
  const [typeIds, setTypeIds] = useState<string[]>([]);
  const [recipeIds, setRecipeIds] = useState<string[]>([]);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [report, setReport] = useState<BatchOpResult | null>(null);

  function toggle(list: string[], v: string, set: (x: string[]) => void) {
    set(list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);
  }

  async function run() {
    if (!reason.trim()) {
      setErr(t('rms.batch.reasonRequired'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const r = await api.batchHold({
        action,
        deviceIds,
        deviceTypeIds: typeIds,
        recipeIds,
        reason: reason.trim(),
      });
      setReport(r);
      onDone();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{t('rms.batch.holdTitle')}</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="field">
            <label className="field-label">{t('rms.batch.action')}</label>
            <select className="text-input" value={action} onChange={(e) => setAction(e.target.value as 'APPLY' | 'RELEASE')}>
              <option value="APPLY">{t('rms.batch.apply')}</option>
              <option value="RELEASE">{t('rms.batch.release')}</option>
            </select>
          </div>
          <div className="field">
            <label className="field-label">
              {t('rms.batch.devices')}（{t('rms.batch.multiHint')}）
            </label>
            <div className="pick-list">
              {devices.map((d) => (
                <label key={d.id} className="check">
                  <input type="checkbox" checked={deviceIds.includes(d.id)} onChange={() => toggle(deviceIds, d.id, setDeviceIds)} />
                  {d.code} · {d.name}
                </label>
              ))}
            </div>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.batch.byTypes')}（{t('rms.batch.multiHint')}）</label>
            <div className="pick-list">
              {types.map((d) => (
                <label key={d.id} className="check">
                  <input type="checkbox" checked={typeIds.includes(d.id)} onChange={() => toggle(typeIds, d.id, setTypeIds)} />
                  {d.name}
                </label>
              ))}
            </div>
          </div>
          <div className="field">
            <label className="field-label">
              {t('rms.batch.recipes')}（{t('rms.batch.emptyMeansDeviceLevel')}）
            </label>
            <div className="pick-list">
              {recipes.map((r) => (
                <label key={r.id} className="check">
                  <input type="checkbox" checked={recipeIds.includes(r.id)} onChange={() => toggle(recipeIds, r.id, setRecipeIds)} />
                  {r.code} · {r.name}
                </label>
              ))}
            </div>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.batch.reason')} *</label>
            <input className="text-input" value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>
          {err && <div className="form-error">{err}</div>}
          {report && <BatchReport report={report} />}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{t('rms.common.close')}</button>
          <button className="btn btn-primary" disabled={busy} onClick={run}>
            {t('rms.batch.run')}
          </button>
        </div>
      </div>
    </div>
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
  const [rows, setRows] = useState<BatchDownloadItem[]>([{ toolId: '', recipeUid: '' }]);
  const [requestor, setRequestor] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [report, setReport] = useState<BatchOpResult | null>(null);

  function setRow(i: number, patch: Partial<BatchDownloadItem>) {
    setRows((list) => list.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  }

  async function run() {
    const valid = rows.filter((r) => r.toolId && r.recipeUid);
    if (valid.length === 0) {
      setErr(t('rms.batch.needOneItem'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const r = await api.batchDownload({ downloadMode: 'IMMEDIATE', requestor: requestor || undefined, items: valid });
      setReport(r);
      onDone();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{t('rms.batch.downloadTitle')}</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="field">
            <label className="field-label">{t('rms.batch.requestor')}</label>
            <input className="text-input" value={requestor} onChange={(e) => setRequestor(e.target.value)} />
          </div>
          {rows.map((row, i) => (
            <div className="grid-2" key={i}>
              <div className="field">
                <label className="field-label">{t('rms.batch.device')} {i + 1}</label>
                <select className="text-input" value={row.toolId} onChange={(e) => setRow(i, { toolId: e.target.value })}>
                  <option value="">—</option>
                  {devices.map((d) => (
                    <option key={d.id} value={d.id}>{d.code} · {d.name}</option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label className="field-label">{t('rms.batch.recipe')} {i + 1}</label>
                <select className="text-input" value={row.recipeUid} onChange={(e) => setRow(i, { recipeUid: e.target.value })}>
                  <option value="">—</option>
                  {recipes.map((r) => (
                    <option key={r.id} value={r.id}>{r.code} · {r.name}</option>
                  ))}
                </select>
              </div>
            </div>
          ))}
          <button className="btn btn-ghost btn-sm" onClick={() => setRows((l) => [...l, { toolId: '', recipeUid: '' }])}>
            + {t('rms.batch.addRow')}
          </button>
          {err && <div className="form-error">{err}</div>}
          {report && <BatchReport report={report} />}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{t('rms.common.close')}</button>
          <button className="btn btn-primary" disabled={busy} onClick={run}>
            {t('rms.batch.run')}
          </button>
        </div>
      </div>
    </div>
  );
}

/** 逐项结果报告（部分成功语义：成功/失败一目了然）。 */
export function BatchReport({ report }: { report: BatchOpResult }) {
  const { t } = useTranslation();
  return (
    <div className="batch-report">
      <div>
        <span className="tag ok">{t('rms.batch.okCount', { n: report.ok })}</span>{' '}
        <span className="tag err">{t('rms.batch.failedCount', { n: report.failed })}</span>
      </div>
      <div className="batch-report-list">
        {report.items.map((it, i) => (
          <div className="batch-report-row" key={i}>
            <span className={'tag ' + (it.ok ? 'ok' : 'err')}>{it.ok ? 'OK' : 'FAIL'}</span>
            <b className="mono">{it.key}</b>
            <span className="mono muted">{it.target}</span>
            <span className="muted">{it.message}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
