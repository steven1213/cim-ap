import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import { BYPASS_MANAGE } from '@/lib/permCodes';
import type { Bypass, Device, Recipe } from '@/types';

/** Bypass 豁免页（FR-B1~B3，Req 22/23）。 */
export default function BypassPage() {
  const { t } = useTranslation();
  const [list, setList] = useState<Bypass[]>([]);
  const [loading, setLoading] = useState(true);
  const [show, setShow] = useState(false);

  async function refresh() {
    setLoading(true);
    try {
      setList(await api.listBypasses());
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { refresh(); }, []);

  async function toggle(x: Bypass) {
    await api.setBypassActive(x.id, !x.active);
    await refresh();
  }
  async function doDelete(x: Bypass) {
    if (!confirm(t('rms.common.confirmDelete'))) return;
    await api.deleteBypass(x.id);
    await refresh();
  }

  const statusTag = (s: Bypass['status']) => {
    const cls = s === 'ACTIVE' ? 'tag ok' : s === 'EXPIRED' || s === 'REVOKED' ? 'tag err' : 'tag warn';
    return <span className={cls}>{t(`rms.bypass.status.${s}`)}</span>;
  };

  return (
    <div className="page">
      <div className="page-head">
        <div><h1>{t('rms.bypass.title')}</h1><p className="muted">{t('rms.bypass.sub')}</p></div>
        <Perms code={BYPASS_MANAGE}>
          <button className="btn btn-primary btn-sm" onClick={() => setShow(true)}>+ {t('rms.bypass.create')}</button>
        </Perms>
      </div>
      <div className="toolbar">{loading && <span className="muted">{t('rms.common.loading')}</span>}</div>

      <div className="panel">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.bypass.scope')}</th>
              <th>{t('rms.bypass.target')}</th>
              <th>{t('rms.bypass.bypassType')}</th>
              <th>{t('rms.bypass.reason')}</th>
              <th>{t('rms.bypass.status')}</th>
              <th>{t('rms.bypass.effectiveFrom')} ~ {t('rms.bypass.effectiveTo')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {list.map((x) => (
              <tr key={x.id}>
                <td>{t(`rms.bypass.scope.${x.scope}`)}</td>
                <td className="mono">{x.targetLabel ?? x.targetId}</td>
                <td>{t(`rms.bypass.type.${x.bypassType}`)}</td>
                <td className="muted">{x.reason}</td>
                <td>{statusTag(x.status)}</td>
                <td className="muted">{x.effectiveFrom ?? '—'} ~ {x.effectiveTo ?? '∞'}</td>
                <td className="cell-actions">
                  <Perms code={BYPASS_MANAGE}>
                    <button className="btn btn-ghost btn-sm" onClick={() => toggle(x)}>
                      {x.active ? t('rms.common.disable') : t('rms.common.enable')}
                    </button>
                    <button className="btn btn-ghost btn-sm danger" onClick={() => doDelete(x)}>{t('rms.common.delete')}</button>
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && list.length === 0 && <tr><td colSpan={7} className="empty-cell">{t('rms.bypass.empty')}</td></tr>}
          </tbody>
        </table>
      </div>

      {show && <BypassModal onClose={() => setShow(false)} onDone={async () => { setShow(false); await refresh(); }} />}
    </div>
  );
}

function BypassModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [scope, setScope] = useState<'DEVICE' | 'RECIPE'>('DEVICE');
  const [targetId, setTargetId] = useState('');
  const [bypassType, setBypassType] = useState<'COMPARE' | 'PARAM_CHECK' | 'BOTH'>('BOTH');
  const [reason, setReason] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [devices, setDevices] = useState<Device[]>([]);
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.listDevices().then(setDevices);
    api.listRecipes().then(setRecipes);
  }, []);

  async function submit() {
    if (!targetId || !reason) { setErr(t('rms.bypass.target') + ' / ' + t('rms.bypass.reason') + ' *'); return; }
    setBusy(true); setErr('');
    try {
      await api.createBypass({
        scope, targetId, bypassType, reason,
        effectiveFrom: from || null, effectiveTo: to || null,
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
        <div className="modal-head"><h3>{t('rms.bypass.create')}</h3><button className="icon-btn" onClick={onClose}>✕</button></div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.bypass.scope')}</label>
              <select className="text-input" value={scope} onChange={(e) => { setScope(e.target.value as any); setTargetId(''); }}>
                <option value="DEVICE">{t('rms.bypass.scope.DEVICE')}</option>
                <option value="RECIPE">{t('rms.bypass.scope.RECIPE')}</option>
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.bypass.target')} *</label>
              <select className="text-input" value={targetId} onChange={(e) => setTargetId(e.target.value)}>
                <option value="">{t('rms.common.select')}</option>
                {(scope === 'DEVICE' ? devices : recipes).map((x: any) => (
                  <option key={x.id} value={x.id}>{x.code}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.bypass.bypassType')}</label>
              <select className="text-input" value={bypassType} onChange={(e) => setBypassType(e.target.value as any)}>
                <option value="COMPARE">{t('rms.bypass.type.COMPARE')}</option>
                <option value="PARAM_CHECK">{t('rms.bypass.type.PARAM_CHECK')}</option>
                <option value="BOTH">{t('rms.bypass.type.BOTH')}</option>
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.bypass.effectiveFrom')}</label>
              <input type="datetime-local" className="text-input" value={from} onChange={(e) => setFrom(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.bypass.effectiveTo')}</label>
              <input type="datetime-local" className="text-input" value={to} onChange={(e) => setTo(e.target.value)} />
            </div>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.bypass.reason')} *</label>
            <input className="text-input" value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>
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
