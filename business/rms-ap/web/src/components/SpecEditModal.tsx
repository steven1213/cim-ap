import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/rmsApi';
import type { ParamSpec, SpecType } from '@/types';

const SPEC_TYPES: SpecType[] = ['TARGET', 'RANGE', 'LIST_IN', 'TOLERANCE'];
const DATA_TYPES = ['NUMBER', 'TEXT', 'BOOL'];

/**
 * 参数规格编辑弹窗（自 SpecPage 抽出的共享组件）。
 *
 * <p>配方详情抽屉与规格参数页共用同一套表单——「一处搞定」原则：
 * 用户在配方抽屉里即可维护规格，不必跳页后重新定位配方。</p>
 */
export default function SpecEditModal({ recipeId, initial, onClose, onDone }: {
  recipeId: string;
  initial: ParamSpec | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [paramKey, setParamKey] = useState(initial?.paramKey ?? '');
  const [paramName, setParamName] = useState(initial?.paramName ?? '');
  const [specType, setSpecType] = useState<SpecType>(initial?.specType ?? 'TARGET');
  const [dataType, setDataType] = useState(initial?.dataType ?? 'NUMBER');
  const [targetValue, setTargetValue] = useState(initial?.targetValue ?? '');
  const [rangeMin, setRangeMin] = useState(initial?.rangeMin ?? '');
  const [rangeMax, setRangeMax] = useState(initial?.rangeMax ?? '');
  const [listValues, setListValues] = useState(initial?.listValues ?? '');
  const [tolerancePct, setTolerancePct] = useState(initial?.tolerancePct?.toString() ?? '');
  const [toleranceAbs, setToleranceAbs] = useState(initial?.toleranceAbs?.toString() ?? '');
  const [unit, setUnit] = useState(initial?.unit ?? '');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit() {
    if (!paramKey) {
      setErr(t('rms.spec.paramKey') + ' *');
      return;
    }
    setBusy(true);
    setErr('');
    try {
      await api.upsertSpec(recipeId, {
        paramKey,
        paramName: paramName || null,
        specType,
        dataType,
        targetValue: targetValue || null,
        rangeMin: rangeMin || null,
        rangeMax: rangeMax || null,
        listValues: listValues || null,
        tolerancePct: tolerancePct ? Number(tolerancePct) : null,
        toleranceAbs: toleranceAbs ? Number(toleranceAbs) : null,
        unit: unit || null,
        enabled: initial?.enabled ?? true,
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
        <div className="modal-head">
          <h3>{initial ? t('rms.spec.editSpec') : t('rms.spec.add')}</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.spec.paramKey')} *</label>
              <input className="text-input" value={paramKey} disabled={!!initial} onChange={(e) => setParamKey(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.spec.paramName')}</label>
              <input className="text-input" value={paramName} onChange={(e) => setParamName(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.spec.specType')}</label>
              <select className="text-input" value={specType} onChange={(e) => setSpecType(e.target.value as SpecType)}>
                {SPEC_TYPES.map((s) => <option key={s} value={s}>{t(`rms.spec.type.${s}`)}</option>)}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.spec.dataType')}</label>
              <select className="text-input" value={dataType} onChange={(e) => setDataType(e.target.value)}>
                {DATA_TYPES.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
          </div>
          {(specType === 'TARGET' || specType === 'TOLERANCE') && (
            <div className="field">
              <label className="field-label">{t('rms.spec.target')}</label>
              <input className="text-input" value={targetValue} onChange={(e) => setTargetValue(e.target.value)} />
            </div>
          )}
          {specType === 'RANGE' && (
            <div className="form-grid">
              <div className="field">
                <label className="field-label">{t('rms.spec.rangeMin')}</label>
                <input className="text-input" value={rangeMin} onChange={(e) => setRangeMin(e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label">{t('rms.spec.rangeMax')}</label>
                <input className="text-input" value={rangeMax} onChange={(e) => setRangeMax(e.target.value)} />
              </div>
            </div>
          )}
          {specType === 'LIST_IN' && (
            <div className="field">
              <label className="field-label">{t('rms.spec.list')}</label>
              <input className="text-input" value={listValues} onChange={(e) => setListValues(e.target.value)} />
            </div>
          )}
          {specType === 'TOLERANCE' && (
            <div className="form-grid">
              <div className="field">
                <label className="field-label">{t('rms.spec.tolPct')}</label>
                <input className="text-input" value={tolerancePct} onChange={(e) => setTolerancePct(e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label">{t('rms.spec.tolAbs')}</label>
                <input className="text-input" value={toleranceAbs} onChange={(e) => setToleranceAbs(e.target.value)} />
              </div>
            </div>
          )}
          <div className="field">
            <label className="field-label">{t('rms.spec.unit')}</label>
            <input className="text-input" value={unit} onChange={(e) => setUnit(e.target.value)} />
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
