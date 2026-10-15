import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import { ECSV_MANAGE } from '@/lib/permCodes';
import type { EcSvSpec, EcSvValue, EcSvSummary, Device, Recipe } from '@/types';

const SPEC_TYPES = ['TARGET', 'RANGE', 'TOLERANCE'];

/** EC/SV 规格与校验页（FR-D2~D4，Req 43/44/45）。 */
export default function EcSvPage() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<'spec' | 'value'>('spec');
  const [specs, setSpecs] = useState<EcSvSpec[]>([]);
  const [values, setValues] = useState<EcSvValue[]>([]);
  const [summary, setSummary] = useState<EcSvSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [showSpec, setShowSpec] = useState(false);
  const [showUpload, setShowUpload] = useState(false);
  const [scopeFilter, setScopeFilter] = useState('');
  const [refFilter, setRefFilter] = useState('');

  async function refresh() {
    setLoading(true);
    try {
      setSummary(await api.ecSvSummary());
      setSpecs(await api.listEcSvSpecs({ scope: scopeFilter || undefined, refId: refFilter || undefined }));
      setValues(await api.listEcSvValues({ limit: 100 }));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, [scopeFilter, refFilter]);

  async function del(x: EcSvSpec) {
    if (!confirm(t('rms.common.confirmDelete'))) return;
    await api.deleteEcSvSpec(x.id);
    await refresh();
  }

  return (
    <div className="page">
      <div className="page-head">
        <div><h1>{t('rms.ecsv.title')}</h1><p className="muted">{t('rms.ecsv.sub')}</p></div>
        <Perms code={ECSV_MANAGE}>
          <button className="btn btn-ghost btn-sm" onClick={() => setShowUpload(true)}>{t('rms.ecsv.upload')}</button>
          <button className="btn btn-primary btn-sm" onClick={() => setShowSpec(true)}>+ {t('rms.ecsv.addSpec')}</button>
        </Perms>
      </div>

      {summary && (
        <div className="kpi-grid">
          <div className="kpi"><div className="kpi-label">{t('rms.ecsv.kpi.specs')}</div><div className="kpi-val">{summary.specs}</div></div>
          <div className="kpi ok"><div className="kpi-label">{t('rms.ecsv.kpi.pass')}</div><div className="kpi-val">{summary.pass}</div></div>
          <div className="kpi warn"><div className="kpi-label">{t('rms.ecsv.kpi.fail')}</div><div className="kpi-val">{summary.fail}</div></div>
          <div className="kpi info"><div className="kpi-label">{t('rms.ecsv.kpi.noSpec')}</div><div className="kpi-val">{summary.noSpec}</div></div>
        </div>
      )}

      <div className="toolbar">
        <button className={`btn btn-sm ${tab === 'spec' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('spec')}>{t('rms.ecsv.specTab')}</button>
        <button className={`btn btn-sm ${tab === 'value' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('value')}>{t('rms.ecsv.valueTab')}</button>
        {loading && <span className="muted">{t('rms.common.loading')}</span>}
        {tab === 'spec' && (
          <>
            <span style={{ flex: 1 }} />
            <select className="text-input" value={scopeFilter} onChange={(e) => setScopeFilter(e.target.value)}>
              <option value="">{t('rms.ecsv.scope')}</option>
              <option value="RECIPE">{t('rms.ecsv.scope.RECIPE')}</option>
              <option value="PRODUCT">{t('rms.ecsv.scope.PRODUCT')}</option>
            </select>
            <input className="text-input" style={{ width: 180 }} placeholder={t('rms.ecsv.refId')}
              value={refFilter} onChange={(e) => setRefFilter(e.target.value)} />
          </>
        )}
      </div>

      {tab === 'spec' ? (
        <div className="panel">
          <table className="data">
            <thead>
              <tr>
                <th>{t('rms.ecsv.scope')}</th>
                <th>{t('rms.ecsv.refId')}</th>
                <th>{t('rms.ecsv.ecsvType')}</th>
                <th>{t('rms.ecsv.paramKey')}</th>
                <th>{t('rms.spec.specType')}</th>
                <th>{t('rms.spec.target')}</th>
                <th>{t('rms.spec.unit')}</th>
                <th>{t('rms.common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {specs.map((x) => (
                <tr key={x.id}>
                  <td>{t(`rms.ecsv.scope.${x.scope}`)}</td>
                  <td className="mono">{x.refId}</td>
                  <td><span className="tag">{x.ecsvType}</span></td>
                  <td className="mono">{x.paramKey}</td>
                  <td>{x.specType === 'RANGE' ? t('rms.spec.type.RANGE') : x.specType === 'TOLERANCE' ? t('rms.spec.type.TOLERANCE') : t('rms.spec.type.TARGET')}</td>
                  <td className="mono">
                    {x.specType === 'RANGE' ? `[${x.rangeMin ?? '-'}, ${x.rangeMax ?? '-'}]`
                      : x.specType === 'TOLERANCE' ? `*=${x.targetValue ?? '-'} ±${x.tolerancePct ?? '-'}%/±${x.toleranceAbs ?? '-'}`
                      : x.targetValue ?? '-'}
                  </td>
                  <td>{x.unit ?? t('rms.common.dash')}</td>
                  <td className="cell-actions">
                    <Perms code={ECSV_MANAGE}>
                      <button className="btn btn-ghost btn-sm danger" onClick={() => del(x)}>{t('rms.common.delete')}</button>
                    </Perms>
                  </td>
                </tr>
              ))}
              {!loading && specs.length === 0 && <tr><td colSpan={8} className="empty-cell">{t('rms.ecsv.emptySpec')}</td></tr>}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="panel">
          <table className="data">
            <thead>
              <tr>
                <th>{t('rms.ecsv.device')}</th>
                <th>{t('rms.ecsv.ecsvType')}</th>
                <th>{t('rms.ecsv.paramKey')}</th>
                <th>{t('rms.ecsv.actual')}</th>
                <th>{t('rms.ecsv.result')}</th>
                <th>{t('rms.ecsv.detail')}</th>
                <th>{t('rms.ecsv.capturedAt')}</th>
              </tr>
            </thead>
            <tbody>
              {values.map((x) => (
                <tr key={x.id}>
                  <td className="mono">{x.deviceId}</td>
                  <td>{x.ecsvType}</td>
                  <td className="mono">{x.paramKey}</td>
                  <td className="mono">{x.actualValue}</td>
                  <td><span className={`tag ${x.result === 'PASS' ? 'ok' : x.result === 'FAIL' ? 'err' : 'warn'}`}>{x.result}</span></td>
                  <td className="muted">{x.detail}</td>
                  <td className="muted">{x.capturedAt}</td>
                </tr>
              ))}
              {!loading && values.length === 0 && <tr><td colSpan={7} className="empty-cell">{t('rms.ecsv.emptyValue')}</td></tr>}
            </tbody>
          </table>
        </div>
      )}

      {showSpec && <SpecModal onClose={() => setShowSpec(false)} onDone={async () => { setShowSpec(false); await refresh(); }} />}
      {showUpload && <UploadModal onClose={() => setShowUpload(false)} onDone={async () => { setShowUpload(false); await refresh(); }} />}
    </div>
  );
}

function SpecModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [scope, setScope] = useState<'RECIPE' | 'PRODUCT'>('RECIPE');
  const [refId, setRefId] = useState('');
  const [ecsvType, setEcsvType] = useState<'EC' | 'SV'>('EC');
  const [svId, setSvId] = useState('');
  const [paramKey, setParamKey] = useState('');
  const [specType, setSpecType] = useState('TARGET');
  const [targetValue, setTargetValue] = useState('');
  const [rangeMin, setRangeMin] = useState('');
  const [rangeMax, setRangeMax] = useState('');
  const [tolerancePct, setTolerancePct] = useState('');
  const [toleranceAbs, setToleranceAbs] = useState('');
  const [unit, setUnit] = useState('');
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => { api.listRecipes().then(setRecipes); }, []);

  async function submit() {
    if (!refId || !paramKey) { setErr(t('rms.ecsv.refId') + ' / ' + t('rms.ecsv.paramKey') + ' *'); return; }
    setBusy(true); setErr('');
    try {
      await api.saveEcSvSpec({
        scope, refId, ecsvType, svId: svId || null, paramKey, specType,
        targetValue: targetValue || null, rangeMin: rangeMin || null, rangeMax: rangeMax || null,
        tolerancePct: tolerancePct ? Number(tolerancePct) : null,
        toleranceAbs: toleranceAbs ? Number(toleranceAbs) : null, unit: unit || null, enabled: true,
      } as any);
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
        <div className="modal-head"><h3>{t('rms.ecsv.addSpec')}</h3><button className="icon-btn" onClick={onClose}>✕</button></div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.ecsv.scope')}</label>
              <select className="text-input" value={scope} onChange={(e) => setScope(e.target.value as any)}>
                <option value="RECIPE">{t('rms.ecsv.scope.RECIPE')}</option>
                <option value="PRODUCT">{t('rms.ecsv.scope.PRODUCT')}</option>
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.ecsv.refId')} *</label>
              {scope === 'RECIPE' ? (
                <select className="text-input" value={refId} onChange={(e) => setRefId(e.target.value)}>
                  <option value="">{t('rms.common.select')}</option>
                  {recipes.map((r) => <option key={r.id} value={r.id}>{r.code}</option>)}
                </select>
              ) : (
                <input className="text-input" value={refId} onChange={(e) => setRefId(e.target.value)} />
              )}
            </div>
            <div className="field">
              <label className="field-label">{t('rms.ecsv.ecsvType')}</label>
              <select className="text-input" value={ecsvType} onChange={(e) => setEcsvType(e.target.value as any)}>
                <option value="EC">EC</option>
                <option value="SV">SV</option>
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.ecsv.svId')}</label>
              <input className="text-input" value={svId} onChange={(e) => setSvId(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.ecsv.paramKey')} *</label>
              <input className="text-input" value={paramKey} onChange={(e) => setParamKey(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.spec.specType')}</label>
              <select className="text-input" value={specType} onChange={(e) => setSpecType(e.target.value)}>
                {SPEC_TYPES.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
          </div>
          {specType === 'RANGE' ? (
            <div className="form-grid">
              <div className="field"><label className="field-label">{t('rms.spec.rangeMin')}</label>
                <input className="text-input" value={rangeMin} onChange={(e) => setRangeMin(e.target.value)} /></div>
              <div className="field"><label className="field-label">{t('rms.spec.rangeMax')}</label>
                <input className="text-input" value={rangeMax} onChange={(e) => setRangeMax(e.target.value)} /></div>
            </div>
          ) : specType === 'TOLERANCE' ? (
            <div className="form-grid">
              <div className="field"><label className="field-label">{t('rms.spec.target')}</label>
                <input className="text-input" value={targetValue} onChange={(e) => setTargetValue(e.target.value)} /></div>
              <div className="field"><label className="field-label">{t('rms.spec.tolPct')}</label>
                <input className="text-input" value={tolerancePct} onChange={(e) => setTolerancePct(e.target.value)} /></div>
              <div className="field"><label className="field-label">{t('rms.spec.tolAbs')}</label>
                <input className="text-input" value={toleranceAbs} onChange={(e) => setToleranceAbs(e.target.value)} /></div>
            </div>
          ) : (
            <div className="field"><label className="field-label">{t('rms.spec.target')}</label>
              <input className="text-input" value={targetValue} onChange={(e) => setTargetValue(e.target.value)} /></div>
          )}
          <div className="field"><label className="field-label">{t('rms.spec.unit')}</label>
            <input className="text-input" value={unit} onChange={(e) => setUnit(e.target.value)} /></div>
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

function UploadModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [devices, setDevices] = useState<Device[]>([]);
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [deviceId, setDeviceId] = useState('');
  const [scope, setScope] = useState<'RECIPE' | 'PRODUCT'>('RECIPE');
  const [refId, setRefId] = useState('');
  const [ecsvType, setEcsvType] = useState<'EC' | 'SV'>('EC');
  const [values, setValues] = useState('{\n  "CHAMBER.TEMP": "70"\n}');
  const [result, setResult] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.listDevices().then(setDevices);
    api.listRecipes().then(setRecipes);
  }, []);

  async function submit() {
    if (!deviceId || !refId) { setErr(t('rms.ecsv.device') + ' / ' + t('rms.ecsv.refId') + ' *'); return; }
    setBusy(true); setErr('');
    try {
      const parsed = JSON.parse(values);
      const r = await api.uploadEcSv({ deviceId, scope, refId, ecsvType, values: parsed });
      setResult(`${t('rms.common.total')} ${r.total} · ${t('rms.common.fail')} ${r.fail}`);
      setTimeout(onDone, 900);
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head"><h3>{t('rms.ecsv.upload')}</h3><button className="icon-btn" onClick={onClose}>✕</button></div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.ecsv.device')} *</label>
              <select className="text-input" value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
                <option value="">{t('rms.common.select')}</option>
                {devices.map((d) => <option key={d.id} value={d.id}>{d.code}</option>)}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.ecsv.ecsvType')}</label>
              <select className="text-input" value={ecsvType} onChange={(e) => setEcsvType(e.target.value as any)}>
                <option value="EC">EC</option>
                <option value="SV">SV</option>
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.ecsv.scope')}</label>
              <select className="text-input" value={scope} onChange={(e) => setScope(e.target.value as any)}>
                <option value="RECIPE">{t('rms.ecsv.scope.RECIPE')}</option>
                <option value="PRODUCT">{t('rms.ecsv.scope.PRODUCT')}</option>
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.ecsv.refId')} *</label>
              {scope === 'RECIPE' ? (
                <select className="text-input" value={refId} onChange={(e) => setRefId(e.target.value)}>
                  <option value="">{t('rms.common.select')}</option>
                  {recipes.map((r) => <option key={r.id} value={r.id}>{r.code}</option>)}
                </select>
              ) : (
                <input className="text-input" value={refId} onChange={(e) => setRefId(e.target.value)} />
              )}
            </div>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.ecsv.values')}</label>
            <textarea className="text-input" rows={6} value={values} onChange={(e) => setValues(e.target.value)} />
          </div>
          {result && <div className="alert ok">{result}</div>}
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{t('rms.common.close')}</button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>{t('rms.ecsv.upload')}</button>
        </div>
      </div>
    </div>
  );
}
