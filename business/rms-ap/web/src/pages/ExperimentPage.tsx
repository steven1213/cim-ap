import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import { EXPERIMENT_MANAGE } from '@/lib/permCodes';
import type { Device, Experiment, Recipe, RecipeVersion } from '@/types';

/**
 * 实验配方通道页（W11）：DOE 临时配方受控通道——审批后在「机台 × 版本 × 窗口」内
 * 放行 IF-E2 下发，绕开「仅 ACTIVE 可下发」闸门；过期/关闭自动失效，全程留痕。
 */
export default function ExperimentPage() {
  const { t } = useTranslation();
  const [rows, setRows] = useState<Experiment[]>([]);
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);

  async function refresh() {
    setLoading(true);
    try {
      setRows(await api.listExperiments(status || undefined));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, [status]);

  async function decide(x: Experiment, action: 'approve' | 'reject') {
    const comment = window.prompt(t('rms.exp.commentPrompt')) ?? '';
    await api.decideExperiment(x.id, action, comment);
    await refresh();
  }

  async function close(x: Experiment) {
    const reason = window.prompt(t('rms.exp.closeReasonPrompt'));
    if (reason === null) return;
    await api.closeExperiment(x.id, reason);
    await refresh();
  }

  function statusTag(x: Experiment) {
    if (x.status === 'APPROVED' && x.expired) return <span className="tag warn">{t('rms.exp.expired')}</span>;
    const cls = x.status === 'APPROVED' ? 'ok' : x.status === 'REJECTED' || x.status === 'CLOSED' ? 'err' : 'warn';
    return <span className={'tag ' + cls}>{t('rms.exp.status.' + x.status)}</span>;
  }

  return (
    <div className="page">
      <div className="page-head">
        <div><h1>{t('rms.exp.title')}</h1><p className="muted">{t('rms.exp.sub')}</p></div>
        <div className="toolbar">
          <Perms code={EXPERIMENT_MANAGE}>
            <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
              + {t('rms.exp.create')}
            </button>
          </Perms>
          <select className="text-input text-input-sm" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">{t('rms.exp.allStatus')}</option>
            {['REQUESTED', 'APPROVED', 'REJECTED', 'CLOSED'].map((s) => (
              <option key={s} value={s}>{t('rms.exp.status.' + s)}</option>
            ))}
          </select>
          <button className="btn btn-ghost btn-sm" onClick={refresh}>{t('rms.common.refresh')}</button>
        </div>
      </div>

      <div className="panel">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.exp.code')}</th>
              <th>{t('rms.exp.recipe')}</th>
              <th>{t('rms.exp.device')}</th>
              <th>{t('rms.exp.window')}</th>
              <th>{t('rms.exp.statusLabel')}</th>
              <th>{t('rms.exp.requestedBy')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.code}</td>
                <td className="mono">{x.recipeCode} v{x.versionNo}</td>
                <td className="mono">{x.deviceCode}</td>
                <td className="muted">{x.validFrom} ~ {x.validUntil}</td>
                <td>{statusTag(x)}</td>
                <td className="muted">{x.requestedBy}</td>
                <td className="cell-actions">
                  <Perms code={EXPERIMENT_MANAGE}>
                    {x.status === 'REQUESTED' && (
                      <>
                        <button className="btn btn-ghost btn-sm" onClick={() => decide(x, 'approve')}>{t('rms.exp.approve')}</button>
                        <button className="btn btn-ghost btn-sm danger" onClick={() => decide(x, 'reject')}>{t('rms.exp.reject')}</button>
                      </>
                    )}
                    {x.status === 'APPROVED' && !x.expired && (
                      <button className="btn btn-ghost btn-sm" onClick={() => close(x)}>{t('rms.exp.close')}</button>
                    )}
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && rows.length === 0 && (
              <tr><td colSpan={7} className="empty-cell">{t('rms.exp.empty')}</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {showCreate && (
        <CreateModal onClose={() => setShowCreate(false)} onCreated={refresh} />
      )}
    </div>
  );
}

function CreateModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const { t } = useTranslation();
  const [devices, setDevices] = useState<Device[]>([]);
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [versions, setVersions] = useState<RecipeVersion[]>([]);
  const [deviceId, setDeviceId] = useState('');
  const [recipeId, setRecipeId] = useState('');
  const [versionId, setVersionId] = useState('');
  const [reason, setReason] = useState('');
  const [validUntil, setValidUntil] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.listDevices().then(setDevices).catch(() => setDevices([]));
    api.listRecipes().then(setRecipes).catch(() => setRecipes([]));
  }, []);

  useEffect(() => {
    if (!recipeId) { setVersions([]); setVersionId(''); return; }
    api.listVersions(recipeId).then(setVersions).catch(() => setVersions([]));
  }, [recipeId]);

  async function submit() {
    if (!deviceId || !recipeId || !versionId || !validUntil) {
      setErr(t('rms.common.confirm'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      await api.createExperiment({
        recipeId, versionId, deviceId,
        reason: reason || undefined,
        validUntil: new Date(validUntil).toISOString(),
      });
      onCreated();
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
          <h3>{t('rms.exp.create')}</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="field">
            <label className="field-label">{t('rms.exp.device')} *</label>
            <select className="text-input" value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
              <option value="">—</option>
              {devices.map((d) => (
                <option key={d.id} value={d.id}>{d.code} · {d.name}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.exp.recipe')} *</label>
            <select className="text-input" value={recipeId} onChange={(e) => setRecipeId(e.target.value)}>
              <option value="">—</option>
              {recipes.map((r) => (
                <option key={r.id} value={r.id}>{r.code} · {r.name}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.exp.version')} *（{t('rms.exp.versionHint')}）</label>
            <select className="text-input" value={versionId} onChange={(e) => setVersionId(e.target.value)}>
              <option value="">—</option>
              {versions.map((v) => (
                <option key={v.id} value={v.id}>v{v.versionNo} · {v.status}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.exp.reason')}</label>
            <input className="text-input" value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>
          <div className="field">
            <label className="field-label">{t('rms.exp.validUntil')} *</label>
            <input className="text-input" type="datetime-local" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} />
          </div>
          <p className="muted">{t('rms.exp.hint')}</p>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{t('rms.common.cancel')}</button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>{t('rms.common.save')}</button>
        </div>
      </div>
    </div>
  );
}
