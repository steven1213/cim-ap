import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import { GOLDEN_MANAGE } from '@/lib/permCodes';
import type { GoldenOverride, Device, Recipe } from '@/types';

/** Golden 机差覆盖层页（FR-G1~G3，Req 16/30/31）。 */
export default function GoldenPage() {
  const { t } = useTranslation();
  const [list, setList] = useState<GoldenOverride[]>([]);
  const [loading, setLoading] = useState(true);
  const [show, setShow] = useState(false);

  async function refresh() {
    setLoading(true);
    try { setList(await api.listGoldenOverrides()); } finally { setLoading(false); }
  }
  useEffect(() => { refresh(); }, []);

  async function approve(x: GoldenOverride) {
    await api.approveGoldenOverride(x.id, 'ok');
    await refresh();
  }
  async function reject(x: GoldenOverride) {
    await api.rejectGoldenOverride(x.id);
    await refresh();
  }
  async function doDelete(x: GoldenOverride) {
    if (!confirm(t('rms.common.confirmDelete'))) return;
    await api.deleteGoldenOverride(x.id);
    await refresh();
  }

  return (
    <div className="page">
      <div className="page-head">
        <div><h1>{t('rms.golden.title')}</h1><p className="muted">{t('rms.golden.sub')}</p></div>
        <Perms code={GOLDEN_MANAGE}>
          <button className="btn btn-primary btn-sm" onClick={() => setShow(true)}>+ {t('rms.golden.submit')}</button>
        </Perms>
      </div>
      <div className="toolbar">{loading && <span className="muted">{t('rms.common.loading')}</span>}</div>

      <div className="panel">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.golden.device')}</th>
              <th>{t('rms.golden.recipe')}</th>
              <th>{t('rms.golden.paramKey')}</th>
              <th>{t('rms.golden.allowMin')} ~ {t('rms.golden.allowMax')}</th>
              <th>{t('rms.golden.reason')}</th>
              <th>{t('rms.golden.status')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {list.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.deviceCode ?? x.deviceId}</td>
                <td className="mono">{x.recipeCode ?? t('rms.common.dash')}</td>
                <td className="mono">{x.paramKey}</td>
                <td className="mono">[{x.allowMin ?? '-'}, {x.allowMax ?? '-'}]</td>
                <td className="muted">{x.reason ?? '—'}</td>
                <td>
                  <span className={`tag ${x.status === 'APPROVED' ? 'ok' : x.status === 'REJECTED' ? 'err' : 'warn'}`}>
                    {t(`rms.golden.status.${x.status}`)}
                  </span>
                </td>
                <td className="cell-actions">
                  <Perms code={GOLDEN_MANAGE}>
                    {x.status === 'PENDING' && (
                      <>
                        <button className="btn btn-ghost btn-sm" onClick={() => approve(x)}>{t('rms.common.approve')}</button>
                        <button className="btn btn-ghost btn-sm danger" onClick={() => reject(x)}>{t('rms.common.reject')}</button>
                      </>
                    )}
                    <button className="btn btn-ghost btn-sm danger" onClick={() => doDelete(x)}>{t('rms.common.delete')}</button>
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && list.length === 0 && <tr><td colSpan={7} className="empty-cell">{t('rms.golden.empty')}</td></tr>}
          </tbody>
        </table>
      </div>

      {show && <GoldenModal onClose={() => setShow(false)} onDone={async () => { setShow(false); await refresh(); }} />}
    </div>
  );
}

function GoldenModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [devices, setDevices] = useState<Device[]>([]);
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [deviceId, setDeviceId] = useState('');
  const [recipeId, setRecipeId] = useState('');
  const [paramKey, setParamKey] = useState('');
  const [allowMin, setAllowMin] = useState('');
  const [allowMax, setAllowMax] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.listDevices().then(setDevices);
    api.listRecipes({ golden: true }).then(setRecipes);
  }, []);

  async function submit() {
    if (!deviceId || !paramKey) { setErr(t('rms.golden.device') + ' / ' + t('rms.golden.paramKey') + ' *'); return; }
    setBusy(true); setErr('');
    try {
      await api.submitGoldenOverride({
        deviceId, recipeId: recipeId || null, paramKey,
        allowMin: allowMin || null, allowMax: allowMax || null, reason: reason || null,
      });
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
        <div className="modal-head"><h3>{t('rms.golden.submit')}</h3><button className="icon-btn" onClick={onClose}>✕</button></div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.golden.device')} *</label>
              <select className="text-input" value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
                <option value="">{t('rms.common.select')}</option>
                {devices.map((d) => <option key={d.id} value={d.id}>{d.code}</option>)}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.golden.recipe')}</label>
              <select className="text-input" value={recipeId} onChange={(e) => setRecipeId(e.target.value)}>
                <option value="">{t('rms.common.select')}</option>
                {recipes.map((r) => <option key={r.id} value={r.id}>{r.code}</option>)}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.golden.paramKey')} *</label>
              <input className="text-input" value={paramKey} placeholder="CHAMBER.TEMP" onChange={(e) => setParamKey(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.golden.allowMin')}</label>
              <input className="text-input" value={allowMin} onChange={(e) => setAllowMin(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.golden.allowMax')}</label>
              <input className="text-input" value={allowMax} onChange={(e) => setAllowMax(e.target.value)} />
            </div>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.golden.reason')}</label>
            <input className="text-input" value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{t('rms.common.cancel')}</button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>{t('rms.common.submit')}</button>
        </div>
      </div>
    </div>
  );
}
