import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import { SPEC_MANAGE } from '@/lib/permCodes';
import type { SpecTemplate, SpecTemplateItem, SpecType } from '@/types';

const SPEC_TYPES: SpecType[] = ['TARGET', 'RANGE', 'LIST_IN', 'TOLERANCE'];

interface ItemDraft {
  paramKey: string;
  paramName: string;
  specType: SpecType;
  dataType: string;
  targetValue: string;
  rangeMin: string;
  rangeMax: string;
  listValues: string;
  tolerancePct: string;
  toleranceAbs: string;
  unit: string;
}

const emptyItem = (): ItemDraft => ({
  paramKey: '', paramName: '', specType: 'TARGET', dataType: 'NUMBER',
  targetValue: '', rangeMin: '', rangeMax: '', listValues: '',
  tolerancePct: '', toleranceAbs: '', unit: '',
});

/** 规格模板页（FR-S2，Req 35）。 */
export default function SpecTemplatePage() {
  const { t } = useTranslation();
  const [list, setList] = useState<SpecTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<SpecTemplate | null>(null);
  const [viewing, setViewing] = useState<{ tpl: SpecTemplate; items: SpecTemplateItem[] } | null>(null);

  async function refresh() {
    setLoading(true);
    try {
      setList(await api.listSpecTemplates());
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { refresh(); }, []);

  async function view(x: SpecTemplate) {
    const items = await api.listSpecTemplateItems(x.id);
    setViewing({ tpl: x, items });
  }

  async function doDelete(x: SpecTemplate) {
    if (!confirm(t('rms.common.confirmDelete'))) return;
    await api.deleteSpecTemplate(x.id);
    await refresh();
  }

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{t('rms.specTpl.title')}</h1>
          <p className="muted">{t('rms.specTpl.sub')}</p>
        </div>
        <Perms code={SPEC_MANAGE}>
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>+ {t('rms.specTpl.add')}</button>
        </Perms>
      </div>

      <div className="toolbar">
        {loading && <span className="muted">{t('rms.common.loading')}</span>}
      </div>

      <div className="panel">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.specTpl.code')}</th>
              <th>{t('rms.specTpl.name')}</th>
              <th>{t('rms.specTpl.ver')}</th>
              <th>{t('rms.specTpl.itemCount')}</th>
              <th>{t('rms.specTpl.scopeType')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {list.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.code}</td>
                <td>{x.name}</td>
                <td>v{x.templateVer}</td>
                <td>{x.itemCount}</td>
                <td className="muted">{x.scopeType ?? t('rms.common.dash')}</td>
                <td className="cell-actions">
                  <button className="btn btn-ghost btn-sm" onClick={() => view(x)}>{t('rms.specTpl.items')}</button>
                  <Perms code={SPEC_MANAGE}>
                    <button className="btn btn-ghost btn-sm" onClick={() => setEditing(x)}>{t('rms.common.edit')}</button>
                    <button className="btn btn-ghost btn-sm danger" onClick={() => doDelete(x)}>{t('rms.common.delete')}</button>
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && list.length === 0 && (
              <tr><td colSpan={6} className="empty-cell">{t('rms.specTpl.empty')}</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {viewing && (
        <div className="panel">
          <div className="panel-head"><h3>{viewing.tpl.code} · {t('rms.specTpl.items')}</h3>
            <button className="btn btn-ghost btn-sm" onClick={() => setViewing(null)}>{t('rms.common.close')}</button>
          </div>
          <table className="data">
            <thead>
              <tr><th>{t('rms.spec.paramKey')}</th><th>{t('rms.spec.specType')}</th><th>{t('rms.spec.target')}</th><th>{t('rms.spec.unit')}</th></tr>
            </thead>
            <tbody>
              {viewing.items.map((it) => (
                <tr key={it.id}>
                  <td className="mono">{it.paramKey}</td>
                  <td><span className="tag">{t(`rms.spec.type.${it.specType}`)}</span></td>
                  <td className="mono">
                    {it.specType === 'RANGE' ? `[${it.rangeMin ?? '-'}, ${it.rangeMax ?? '-'}]`
                      : it.specType === 'LIST_IN' ? `{${it.listValues ?? '-'}}`
                      : it.targetValue ?? '-'}
                  </td>
                  <td>{it.unit ?? t('rms.common.dash')}</td>
                </tr>
              ))}
              {viewing.items.length === 0 && <tr><td colSpan={4} className="empty-cell">{t('rms.common.noData')}</td></tr>}
            </tbody>
          </table>
        </div>
      )}

      {(showCreate || editing) && (
        <TemplateModal
          initial={editing}
          onClose={() => { setShowCreate(false); setEditing(null); }}
          onDone={async () => { setShowCreate(false); setEditing(null); await refresh(); }}
        />
      )}
    </div>
  );
}

function TemplateModal({ initial, onClose, onDone }: {
  initial: SpecTemplate | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [code, setCode] = useState(initial?.code ?? '');
  const [name, setName] = useState(initial?.name ?? '');
  const [scopeType, setScopeType] = useState(initial?.scopeType ?? '');
  const [remark, setRemark] = useState(initial?.remark ?? '');
  const [items, setItems] = useState<ItemDraft[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    if (initial) {
      api.listSpecTemplateItems(initial.id).then((its) =>
        setItems(its.map((i) => ({
          paramKey: i.paramKey, paramName: i.paramName ?? '', specType: i.specType, dataType: i.dataType,
          targetValue: i.targetValue ?? '', rangeMin: i.rangeMin ?? '', rangeMax: i.rangeMax ?? '',
          listValues: i.listValues ?? '', tolerancePct: i.tolerancePct?.toString() ?? '',
          toleranceAbs: i.toleranceAbs?.toString() ?? '', unit: i.unit ?? '',
        }))));
    } else {
      setItems([emptyItem()]);
    }
  }, [initial]);

  function upd(i: number, patch: Partial<ItemDraft>) {
    setItems((prev) => prev.map((x, idx) => (idx === i ? { ...x, ...patch } : x)));
  }

  async function submit() {
    if (!code || !name) { setErr(t('rms.specTpl.code') + ' / ' + t('rms.specTpl.name') + ' *'); return; }
    setBusy(true); setErr('');
    try {
      const payload = {
        code, name, scopeType: scopeType || null, description: remark || null,
        items: items.filter((i) => i.paramKey).map((i) => ({
          paramKey: i.paramKey, paramName: i.paramName || null, specType: i.specType, dataType: i.dataType,
          targetValue: i.targetValue || null, rangeMin: i.rangeMin || null, rangeMax: i.rangeMax || null,
          listValues: i.listValues || null,
          tolerancePct: i.tolerancePct ? Number(i.tolerancePct) : null,
          toleranceAbs: i.toleranceAbs ? Number(i.toleranceAbs) : null,
          unit: i.unit || null, enabled: true,
        })),
      };
      if (initial) await api.updateSpecTemplate(initial.id, payload);
      else await api.createSpecTemplate(payload);
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
          <h3>{initial ? t('rms.specTpl.edit') : t('rms.specTpl.add')}</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.specTpl.code')} *</label>
              <input className="text-input" value={code} disabled={!!initial} onChange={(e) => setCode(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.specTpl.name')} *</label>
              <input className="text-input" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.specTpl.scopeType')}</label>
              <input className="text-input" value={scopeType} onChange={(e) => setScopeType(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.common.remark')}</label>
              <input className="text-input" value={remark} onChange={(e) => setRemark(e.target.value)} />
            </div>
          </div>

          <div className="panel-sub">
            <b>{t('rms.specTpl.items')}</b>
            <button className="btn btn-ghost btn-sm" onClick={() => setItems((p) => [...p, emptyItem()])}>
              + {t('rms.specTpl.addItem')}
            </button>
          </div>
          <table className="data">
            <thead>
              <tr>
                <th>{t('rms.spec.paramKey')}</th>
                <th>{t('rms.spec.specType')}</th>
                <th>{t('rms.spec.target')} / {t('rms.spec.rangeMin')}</th>
                <th>{t('rms.spec.unit')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.map((it, i) => (
                <tr key={i}>
                  <td><input className="text-input" value={it.paramKey} placeholder="CHAMBER.TEMP" onChange={(e) => upd(i, { paramKey: e.target.value })} /></td>
                  <td>
                    <select className="text-input" value={it.specType} onChange={(e) => upd(i, { specType: e.target.value as SpecType })}>
                      {SPEC_TYPES.map((s) => <option key={s} value={s}>{t(`rms.spec.type.${s}`)}</option>)}
                    </select>
                  </td>
                  <td>
                    {it.specType === 'RANGE' ? (
                      <div className="grid-2">
                        <input className="text-input" placeholder="min" value={it.rangeMin} onChange={(e) => upd(i, { rangeMin: e.target.value })} />
                        <input className="text-input" placeholder="max" value={it.rangeMax} onChange={(e) => upd(i, { rangeMax: e.target.value })} />
                      </div>
                    ) : it.specType === 'LIST_IN' ? (
                      <input className="text-input" placeholder="A,B,C" value={it.listValues} onChange={(e) => upd(i, { listValues: e.target.value })} />
                    ) : (
                      <input className="text-input" value={it.targetValue} onChange={(e) => upd(i, { targetValue: e.target.value })} />
                    )}
                  </td>
                  <td><input className="text-input" value={it.unit} onChange={(e) => upd(i, { unit: e.target.value })} /></td>
                  <td>
                    <button className="btn btn-ghost btn-sm danger" onClick={() => setItems((p) => p.filter((_, idx) => idx !== i))}>✕</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
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
