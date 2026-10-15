import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import SpecEditModal from '@/components/SpecEditModal';
import * as api from '@/lib/rmsApi';
import { SPEC_MANAGE } from '@/lib/permCodes';
import type { ParamSpec, Recipe } from '@/types';

/** 参数规格页（FR-S1~S4，Req 29/32/34/35/36）：四类规格 + 双轨容差 + 比对开关 + 模板应用/继承。 */
export default function SpecPage() {
  const { t } = useTranslation();
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [recipeId, setRecipeId] = useState('');
  const [list, setList] = useState<ParamSpec[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<ParamSpec | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [tplCode, setTplCode] = useState('');
  const [srcId, setSrcId] = useState('');
  const [msg, setMsg] = useState('');

  useEffect(() => {
    api.listRecipes().then((r) => {
      setRecipes(r);
      if (r.length && !recipeId) setRecipeId(r[0].id);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function refresh() {
    if (!recipeId) {
      setList([]);
      return;
    }
    setLoading(true);
    try {
      setList(await api.listSpecs(recipeId));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recipeId]);

  async function toggle(x: ParamSpec) {
    await api.setSpecEnabled(x.id, !x.enabled);
    await refresh();
  }

  async function doDelete(x: ParamSpec) {
    if (!confirm(t('rms.common.confirmDelete'))) return;
    await api.deleteSpec(x.id);
    await refresh();
  }

  async function applyTpl() {
    if (!tplCode) return;
    const n = await api.applySpecTemplate(recipeId, tplCode);
    setMsg(t('rms.spec.templateApplied', { n }));
    await refresh();
  }

  async function inherit() {
    if (!srcId) return;
    const n = await api.inheritSpec(recipeId, srcId);
    setMsg(t('rms.spec.inherited', { n }));
    await refresh();
  }

  const recipe = useMemo(() => recipes.find((r) => r.id === recipeId), [recipes, recipeId]);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{t('rms.spec.title')}</h1>
          <p className="muted">{t('rms.spec.sub')}</p>
        </div>
        <Perms code={SPEC_MANAGE}>
          <button className="btn btn-primary btn-sm" disabled={!recipeId} onClick={() => setShowCreate(true)}>
            + {t('rms.spec.add')}
          </button>
        </Perms>
      </div>

      <div className="toolbar">
        <label className="muted">{t('rms.spec.pickRecipe')}</label>
        <select className="text-input" value={recipeId} onChange={(e) => setRecipeId(e.target.value)}>
          {recipes.map((r) => (
            <option key={r.id} value={r.id}>{r.code} · {r.name}</option>
          ))}
        </select>
        {loading && <span className="muted">{t('rms.common.loading')}</span>}
        <span style={{ flex: 1 }} />
        <Perms code={SPEC_MANAGE}>
          <input className="text-input" style={{ width: 160 }} placeholder={t('rms.spec.applyTemplatePh')}
            value={tplCode} onChange={(e) => setTplCode(e.target.value)} />
          <button className="btn btn-ghost btn-sm" onClick={applyTpl}>{t('rms.spec.applyTemplate')}</button>
          <input className="text-input" style={{ width: 160 }} placeholder={t('rms.spec.inheritPh')}
            value={srcId} onChange={(e) => setSrcId(e.target.value)} />
          <button className="btn btn-ghost btn-sm" onClick={inherit}>{t('rms.spec.inherit')}</button>
        </Perms>
      </div>

      {msg && <div className="alert info">{msg}</div>}

      {!recipeId ? (
        <div className="empty"><b>{t('rms.spec.noRecipe')}</b></div>
      ) : (
        <div className="panel">
          <table className="data">
            <thead>
              <tr>
                <th>{t('rms.spec.paramKey')}</th>
                <th>{t('rms.spec.paramName')}</th>
                <th>{t('rms.spec.specType')}</th>
                <th>{t('rms.spec.target')} / {t('rms.spec.rangeMin')}</th>
                <th>{t('rms.spec.type.TOLERANCE')}</th>
                <th>{t('rms.spec.unit')}</th>
                <th>{t('rms.spec.enabled')}</th>
                <th>{t('rms.spec.template')}</th>
                <th>{t('rms.common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {list.map((x) => (
                <tr key={x.id}>
                  <td className="mono">{x.paramKey}</td>
                  <td>{x.paramName ?? t('rms.common.dash')}</td>
                  <td><span className="tag">{t(`rms.spec.type.${x.specType}`)}</span></td>
                  <td className="mono">{specValue(x)}</td>
                  <td className="mono">{tolText(x)}</td>
                  <td>{x.unit ?? t('rms.common.dash')}</td>
                  <td>
                    <Perms code={SPEC_MANAGE} fallback={<span>{x.enabled ? t('rms.common.yes') : t('rms.common.no')}</span>}>
                      <button className={`led ${x.enabled ? 'ok' : 'off'}`} onClick={() => toggle(x)}>
                        {x.enabled ? t('rms.common.yes') : t('rms.common.no')}
                      </button>
                    </Perms>
                  </td>
                  <td className="muted">{x.templateCode ?? t('rms.common.dash')}</td>
                  <td className="cell-actions">
                    <Perms code={SPEC_MANAGE}>
                      <button className="btn btn-ghost btn-sm" onClick={() => setEditing(x)}>{t('rms.common.edit')}</button>
                      <button className="btn btn-ghost btn-sm danger" onClick={() => doDelete(x)}>{t('rms.common.delete')}</button>
                    </Perms>
                  </td>
                </tr>
              ))}
              {!loading && list.length === 0 && (
                <tr><td colSpan={9} className="empty-cell">{t('rms.spec.empty')}</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {recipe && <p className="muted">{recipe.code} · {t('rms.spec.sub')}</p>}

      {(showCreate || editing) && (
        <SpecEditModal
          recipeId={recipeId}
          initial={editing}
          onClose={() => { setShowCreate(false); setEditing(null); }}
          onDone={async () => { setShowCreate(false); setEditing(null); await refresh(); }}
        />
      )}
    </div>
  );
}

function specValue(x: ParamSpec): string {
  switch (x.specType) {
    case 'RANGE': return `[${x.rangeMin ?? '-'}, ${x.rangeMax ?? '-'}]`;
    case 'LIST_IN': return `{${x.listValues ?? '-'}}`;
    default: return x.targetValue ?? '-';
  }
}

function tolText(x: ParamSpec): string {
  if (x.specType !== 'TOLERANCE') return '—';
  const p = x.tolerancePct != null ? `${x.tolerancePct}%` : '-';
  const a = x.toleranceAbs != null ? `±${x.toleranceAbs}` : '-';
  return `${p} / ${a}`;
}
