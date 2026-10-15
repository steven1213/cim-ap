import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import { BODY_TEMPLATE_MANAGE, BODY_TEMPLATE_VIEW } from '@/lib/permCodes';
import type { BodyTemplate, BodyTemplateDetail, DeviceType, TemplateUpsertItem } from '@/types';

/**
 * Body 模板页（W10，对齐 camLine ROBDEF）：强制键 + 默认值 + 骨架 Body。
 * 新建配方时向导从这里取骨架；enforced 模板在校验时把缺键记为错误。
 */
export default function BodyTemplatePage() {
  const { t } = useTranslation();
  const [rows, setRows] = useState<BodyTemplate[]>([]);
  const [types, setTypes] = useState<DeviceType[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<BodyTemplateDetail | null>(null);
  const [creating, setCreating] = useState(false);

  async function refresh() {
    setLoading(true);
    try {
      setRows(await api.listBodyTemplates());
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    refresh();
    api.listDeviceTypes().then(setTypes).catch(() => setTypes([]));
  }, []);

  return (
    <div className="page">
      <div className="page-head">
        <div><h1>{t('rms.btpl.title')}</h1><p className="muted">{t('rms.btpl.sub')}</p></div>
        <div className="toolbar">
          <Perms code={BODY_TEMPLATE_MANAGE}>
            <button className="btn btn-primary btn-sm" onClick={() => setCreating(true)}>
              + {t('rms.btpl.create')}
            </button>
          </Perms>
          <button className="btn btn-ghost btn-sm" onClick={refresh}>{t('rms.common.refresh')}</button>
        </div>
      </div>

      <div className="panel">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.btpl.code')}</th>
              <th>{t('rms.btpl.name')}</th>
              <th>{t('rms.recipe.bodyFormat')}</th>
              <th>{t('rms.btpl.scope')}</th>
              <th>{t('rms.btpl.enforced')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.code}</td>
                <td>{x.name}</td>
                <td><span className="tag">{x.bodyFormat}</span></td>
                <td className="muted">
                  {x.scopeDeviceTypeId
                    ? types.find((tp) => tp.id === x.scopeDeviceTypeId)?.name ?? x.scopeDeviceTypeId
                    : t('rms.btpl.scopeGeneric')}
                </td>
                <td>
                  <span className={'tag ' + (x.enforced ? 'err' : '')}>
                    {x.enforced ? t('rms.btpl.enforcedYes') : t('rms.btpl.enforcedNo')}
                  </span>
                </td>
                <td className="cell-actions">
                  <Perms code={BODY_TEMPLATE_VIEW}>
                    <button className="btn btn-ghost btn-sm" onClick={async () => setEditing(await api.getBodyTemplate(x.id))}>
                      {t('rms.common.view')}
                    </button>
                  </Perms>
                  <Perms code={BODY_TEMPLATE_MANAGE}>
                    <button
                      className="btn btn-ghost btn-sm danger"
                      onClick={async () => {
                        if (!window.confirm(t('rms.btpl.deleteConfirm'))) return;
                        await api.deleteBodyTemplate(x.id);
                        await refresh();
                      }}
                    >
                      {t('rms.common.delete')}
                    </button>
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && rows.length === 0 && (
              <tr><td colSpan={6} className="empty-cell">{t('rms.btpl.empty')}</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {(creating || editing) && (
        <TemplateModal
          types={types}
          initial={editing}
          onClose={() => { setCreating(false); setEditing(null); }}
          onSaved={async () => { setCreating(false); setEditing(null); await refresh(); }}
        />
      )}
    </div>
  );
}

function TemplateModal({
  types,
  initial,
  onClose,
  onSaved,
}: {
  types: DeviceType[];
  initial: BodyTemplateDetail | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useTranslation();
  const [code, setCode] = useState(initial?.code ?? '');
  const [name, setName] = useState(initial?.name ?? '');
  const [bodyFormat, setBodyFormat] = useState(initial?.bodyFormat ?? 'JSON_STRUCTURED');
  const [scope, setScope] = useState(initial?.scopeDeviceTypeId ?? '');
  const [enforced, setEnforced] = useState(initial?.enforced ?? true);
  const [templateBody, setTemplateBody] = useState(initial?.templateBody ?? '');
  const [items, setItems] = useState<TemplateUpsertItem[]>(
    (initial?.items ?? []).map((i) => ({ paramKey: i.paramKey, required: i.required, defaultValue: i.defaultValue, unit: i.unit })),
  );
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  function setItem(i: number, patch: Partial<TemplateUpsertItem>) {
    setItems((list) => list.map((x, idx) => (idx === i ? { ...x, ...patch } : x)));
  }

  async function save() {
    if (!code || !name) {
      setErr(t('rms.common.confirm'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const body = {
        code,
        name,
        bodyFormat,
        scopeDeviceTypeId: scope || null,
        enforced,
        templateBody: templateBody || null,
        items: items.filter((i) => i.paramKey?.trim()),
      };
      if (initial) {
        await api.updateBodyTemplate(initial.id, body);
      } else {
        await api.createBodyTemplate(body);
      }
      onSaved();
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
          <h3>{initial ? t('rms.btpl.edit') : t('rms.btpl.create')}</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="grid-2">
            <div className="field">
              <label className="field-label">{t('rms.btpl.code')} *</label>
              <input className="text-input mono" value={code} onChange={(e) => setCode(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.btpl.name')} *</label>
              <input className="text-input" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.recipe.bodyFormat')}</label>
              <select className="text-input" value={bodyFormat} onChange={(e) => setBodyFormat(e.target.value)}>
                <option value="JSON_STRUCTURED">JSON_STRUCTURED</option>
                <option value="TEXT">TEXT</option>
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.btpl.scope')}</label>
              <select className="text-input" value={scope} onChange={(e) => setScope(e.target.value)}>
                <option value="">{t('rms.btpl.scopeGeneric')}</option>
                {types.map((tp) => (
                  <option key={tp.id} value={tp.id}>{tp.name}</option>
                ))}
              </select>
            </div>
          </div>
          <label className="check">
            <input type="checkbox" checked={enforced} onChange={(e) => setEnforced(e.target.checked)} />
            {t('rms.btpl.enforcedHint')}
          </label>
          <div className="field">
            <label className="field-label">{t('rms.btpl.skeleton')}</label>
            <textarea className="text-input mono" rows={6} value={templateBody} onChange={(e) => setTemplateBody(e.target.value)} />
          </div>
          <div className="field">
            <label className="field-label">{t('rms.btpl.keys')}</label>
            <div className="pick-list">
              {items.map((it, i) => (
                <div className="grid-2" key={i}>
                  <div className="field">
                    <input
                      className="text-input mono"
                      placeholder="PARAM_KEY"
                      value={it.paramKey}
                      onChange={(e) => setItem(i, { paramKey: e.target.value })}
                    />
                  </div>
                  <div className="field">
                    <div className="inline-row">
                      <input
                        className="text-input"
                        placeholder={t('rms.btpl.defaultValue')}
                        value={it.defaultValue ?? ''}
                        onChange={(e) => setItem(i, { defaultValue: e.target.value })}
                      />
                      <label className="check">
                        <input type="checkbox" checked={it.required !== false} onChange={(e) => setItem(i, { required: e.target.checked })} />
                        {t('rms.btpl.required')}
                      </label>
                      <button className="icon-btn danger" onClick={() => setItems((l) => l.filter((_, idx) => idx !== i))}>✕</button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <button className="btn btn-ghost btn-sm" onClick={() => setItems((l) => [...l, { paramKey: '', required: true }])}>
              + {t('rms.btpl.addKey')}
            </button>
          </div>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{t('rms.common.cancel')}</button>
          <button className="btn btn-primary" disabled={busy} onClick={save}>{t('rms.common.save')}</button>
        </div>
      </div>
    </div>
  );
}
