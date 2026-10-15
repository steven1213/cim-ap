import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import { QUALIFICATION_MANAGE } from '@/lib/permCodes';
import type { Qualification, Device, DeviceType, DeviceArea, Recipe } from '@/types';

/** 配方-机台资格矩阵页（FR-Q1~Q4）。 */
export default function QualificationPage() {
  const { t } = useTranslation();
  const [list, setList] = useState<Qualification[]>([]);
  const [loading, setLoading] = useState(true);
  const [show, setShow] = useState(false);
  const [devices, setDevices] = useState<Device[]>([]);
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [checkRecipe, setCheckRecipe] = useState('');
  const [checkDevice, setCheckDevice] = useState('');
  const [checkResult, setCheckResult] = useState<string>('');

  async function refresh() {
    setLoading(true);
    try { setList(await api.listQualifications()); } finally { setLoading(false); }
  }
  useEffect(() => {
    api.listDevices().then(setDevices);
    api.listRecipes().then(setRecipes);
    refresh();
  }, []);

  async function revoke(x: Qualification) {
    if (!confirm(t('rms.common.revoke') + '?')) return;
    await api.revokeQualification(x.id);
    await refresh();
  }
  async function doDelete(x: Qualification) {
    if (!confirm(t('rms.common.confirmDelete'))) return;
    await api.deleteQualification(x.id);
    await refresh();
  }
  async function runCheck() {
    if (!checkRecipe || !checkDevice) return;
    const r = await api.checkQualification(checkRecipe, checkDevice);
    setCheckResult(`${r.result}${r.reason ? ' · ' + r.reason : ''}`);
  }

  return (
    <div className="page">
      <div className="page-head">
        <div><h1>{t('rms.qual.title')}</h1><p className="muted">{t('rms.qual.sub')}</p></div>
        <Perms code={QUALIFICATION_MANAGE}>
          <button className="btn btn-primary btn-sm" onClick={() => setShow(true)}>+ {t('rms.qual.grant')}</button>
        </Perms>
      </div>

      <div className="toolbar">
        <label className="muted">{t('rms.qual.check')}</label>
        <select className="text-input" value={checkRecipe} onChange={(e) => setCheckRecipe(e.target.value)}>
          <option value="">{t('rms.qual.recipe')}</option>
          {recipes.map((r) => <option key={r.id} value={r.id}>{r.code}</option>)}
        </select>
        <select className="text-input" value={checkDevice} onChange={(e) => setCheckDevice(e.target.value)}>
          <option value="">{t('rms.qual.device')}</option>
          {devices.map((d) => <option key={d.id} value={d.id}>{d.code}</option>)}
        </select>
        <button className="btn btn-ghost btn-sm" onClick={runCheck}>{t('rms.qual.check')}</button>
        {checkResult && <span className={`tag ${checkResult.startsWith('QUALIFIED') ? 'ok' : 'err'}`}>{checkResult}</span>}
        {loading && <span className="muted">{t('rms.common.loading')}</span>}
      </div>

      <div className="panel">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.qual.recipe')}</th>
              <th>{t('rms.qual.device')}</th>
              <th>{t('rms.qual.versionPolicy')}</th>
              <th>{t('rms.qual.basis')}</th>
              <th>{t('rms.qual.status')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {list.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.recipeCode ?? x.recipeId}</td>
                <td>
                  {x.deviceCode ?? (x.deviceTypeName ? `${t('rms.qual.deviceType')}: ${x.deviceTypeName}` : x.areaId ?? '—')}
                </td>
                <td>{t(`rms.qual.policy.${x.versionPolicy}`)}</td>
                <td className="muted">{x.basis ?? '—'}</td>
                <td><span className={`tag ${x.status === 'GRANTED' ? 'ok' : 'err'}`}>{x.status}</span></td>
                <td className="cell-actions">
                  <Perms code={QUALIFICATION_MANAGE}>
                    {x.status === 'GRANTED' && (
                      <button className="btn btn-ghost btn-sm danger" onClick={() => revoke(x)}>{t('rms.common.revoke')}</button>
                    )}
                    <button className="btn btn-ghost btn-sm danger" onClick={() => doDelete(x)}>{t('rms.common.delete')}</button>
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && list.length === 0 && <tr><td colSpan={6} className="empty-cell">{t('rms.qual.empty')}</td></tr>}
          </tbody>
        </table>
      </div>

      {show && <QualModal recipes={recipes} devices={devices} types={[]} areas={[]} onClose={() => setShow(false)}
        onDone={async () => { setShow(false); await refresh(); }} />}
    </div>
  );
}

function QualModal({ recipes, devices, onClose, onDone }: {
  recipes: Recipe[]; devices: Device[]; types: DeviceType[]; areas: DeviceArea[];
  onClose: () => void; onDone: () => void;
}) {
  const { t } = useTranslation();
  const [recipeId, setRecipeId] = useState('');
  const [versionPolicy, setVersionPolicy] = useState('LATEST_ACTIVE');
  const [mode, setMode] = useState<'DEVICE' | 'TYPE' | 'AREA'>('DEVICE');
  const [deviceId, setDeviceId] = useState('');
  const [deviceTypeId, setDeviceTypeId] = useState('');
  const [areaId, setAreaId] = useState('');
  const [basis, setBasis] = useState('');
  const [types, setTypes] = useState<DeviceType[]>([]);
  const [areas, setAreas] = useState<DeviceArea[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.listDeviceTypes().then(setTypes);
    api.listDeviceAreas().then(setAreas);
  }, []);

  async function submit() {
    if (!recipeId) { setErr(t('rms.qual.recipe') + ' *'); return; }
    setBusy(true); setErr('');
    try {
      await api.grantQualification({
        recipeId, versionPolicy,
        deviceId: mode === 'DEVICE' ? deviceId || null : null,
        deviceTypeId: mode === 'TYPE' ? deviceTypeId || null : null,
        areaId: mode === 'AREA' ? areaId || null : null,
        basis: basis || null,
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
        <div className="modal-head"><h3>{t('rms.qual.grant')}</h3><button className="icon-btn" onClick={onClose}>✕</button></div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.qual.recipe')} *</label>
              <select className="text-input" value={recipeId} onChange={(e) => setRecipeId(e.target.value)}>
                <option value="">{t('rms.common.select')}</option>
                {recipes.map((r) => <option key={r.id} value={r.id}>{r.code}</option>)}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.qual.versionPolicy')}</label>
              <select className="text-input" value={versionPolicy} onChange={(e) => setVersionPolicy(e.target.value)}>
                <option value="LATEST_ACTIVE">{t('rms.qual.policy.LATEST_ACTIVE')}</option>
                <option value="EXACT_VERSION">{t('rms.qual.policy.EXACT_VERSION')}</option>
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.deviceAuth.target')}</label>
              <select className="text-input" value={mode} onChange={(e) => setMode(e.target.value as any)}>
                <option value="DEVICE">{t('rms.qual.device')}</option>
                <option value="TYPE">{t('rms.qual.deviceType')}</option>
                <option value="AREA">{t('rms.qual.area')}</option>
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.qual.device')} / {t('rms.qual.deviceType')} / {t('rms.qual.area')}</label>
              {mode === 'DEVICE' && (
                <select className="text-input" value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
                  <option value="">{t('rms.common.select')}</option>
                  {devices.map((d) => <option key={d.id} value={d.id}>{d.code}</option>)}
                </select>
              )}
              {mode === 'TYPE' && (
                <select className="text-input" value={deviceTypeId} onChange={(e) => setDeviceTypeId(e.target.value)}>
                  <option value="">{t('rms.common.select')}</option>
                  {types.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                </select>
              )}
              {mode === 'AREA' && (
                <select className="text-input" value={areaId} onChange={(e) => setAreaId(e.target.value)}>
                  <option value="">{t('rms.common.select')}</option>
                  {areas.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                </select>
              )}
            </div>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.qual.basis')}</label>
            <input className="text-input" value={basis} onChange={(e) => setBasis(e.target.value)} />
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
