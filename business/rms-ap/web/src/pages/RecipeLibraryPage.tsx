import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import type { Recipe, RecipeVersion, DeviceType, DeviceArea, BodyFormat } from '@/types';

async function sha256Hex(text: string): Promise<string> {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

const STATUS_TONE: Record<string, string> = {
  DRAFT: 'info',
  ACTIVE: 'ok',
  OBSOLETE: 'warn',
};

export default function RecipeLibraryPage() {
  const { t } = useTranslation();
  const [list, setList] = useState<Recipe[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [goldenOnly, setGoldenOnly] = useState(false);

  const [types, setTypes] = useState<DeviceType[]>([]);
  const [areas, setAreas] = useState<DeviceArea[]>([]);

  // 新建弹窗
  const [showCreate, setShowCreate] = useState(false);
  // 版本抽屉
  const [active, setActive] = useState<Recipe | null>(null);
  const [versions, setVersions] = useState<RecipeVersion[]>([]);
  const [showNewVer, setShowNewVer] = useState(false);
  const [showSaveAs, setShowSaveAs] = useState(false);

  async function refresh() {
    setLoading(true);
    try {
      const data = await api.listRecipes({
        keyword: keyword || undefined,
        golden: goldenOnly || undefined,
      });
      setList(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let alive = true;
    Promise.all([api.listDeviceTypes(), api.listDeviceAreas()]).then(([ty, ar]) => {
      if (!alive) return;
      setTypes(ty);
      setAreas(ar);
    });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const id = setTimeout(refresh, 250);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, goldenOnly]);

  async function openVersions(r: Recipe) {
    setActive(r);
    setVersions(await api.listVersions(r.id));
  }

  async function doActivate(v: RecipeVersion) {
    if (!active) return;
    if (!confirm(t('rms.common.confirm'))) return;
    await api.activateVersion(active.id, v.id);
    setActive(null);
    await refresh();
  }

  async function doDelete(r: Recipe) {
    if (!confirm(t('rms.common.confirmDelete'))) return;
    await api.deleteRecipe(r.id);
    await refresh();
  }

  const typeName = useMemo(() => Object.fromEntries(types.map((x) => [x.id, x.name])), [types]);
  const areaName = useMemo(() => Object.fromEntries(areas.map((x) => [x.id, x.name])), [areas]);

  return (
    <div className="page">
      <div className="page-head">
        <h1>{t('rms.recipe.title')}</h1>
        <Perms code="rms:recipe:create">
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
            + {t('rms.recipe.createNew')}
          </button>
        </Perms>
      </div>

      <div className="toolbar">
        <input
          className="search-input"
          placeholder={t('rms.common.search')}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <label className="check">
          <input type="checkbox" checked={goldenOnly} onChange={(e) => setGoldenOnly(e.target.checked)} />
          {t('rms.recipe.goldenOnly')}
        </label>
        {loading && <span className="muted">{t('rms.common.loading')}</span>}
      </div>

      <div className="panel">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.recipe.code')}</th>
              <th>{t('rms.recipe.name')}</th>
              <th>{t('rms.recipe.deviceType')}</th>
              <th>{t('rms.recipe.area')}</th>
              <th>{t('rms.recipe.golden')}</th>
              <th>{t('rms.recipe.activeVersion')}</th>
              <th>{t('rms.recipe.versionCount')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {list.map((r) => (
              <tr key={r.id}>
                <td className="mono">{r.code}</td>
                <td>{r.name}</td>
                <td>{r.deviceTypeId ? typeName[r.deviceTypeId] ?? '?' : t('rms.common.dash')}</td>
                <td>{r.areaId ? areaName[r.areaId] ?? '?' : t('rms.common.dash')}</td>
                <td>{r.golden ? <span className="tag pri">{t('rms.recipe.golden')}</span> : t('rms.common.dash')}</td>
                <td className="mono">{r.activeVersionNo ?? t('rms.common.dash')}</td>
                <td className="num">{r.versionCount}</td>
                <td className="cell-actions">
                  <button className="btn btn-ghost btn-sm" onClick={() => openVersions(r)}>
                    {t('rms.recipe.version')}
                  </button>
                  <Perms code="rms:recipe:delete">
                    <button className="btn btn-ghost btn-sm danger" onClick={() => doDelete(r)}>
                      {t('rms.common.delete')}
                    </button>
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && list.length === 0 && (
              <tr>
                <td colSpan={8} className="empty-cell">
                  {t('rms.common.noData')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {showCreate && (
        <CreateModal
          types={types}
          areas={areas}
          onClose={() => setShowCreate(false)}
          onCreated={async () => {
            setShowCreate(false);
            await refresh();
          }}
        />
      )}

      {active && (
        <div className="drawer-backdrop" onClick={() => setActive(null)}>
          <div className="drawer" onClick={(e) => e.stopPropagation()}>
            <div className="drawer-head">
              <h3>
                {active.code} · {active.name}
              </h3>
              <button className="icon-btn" onClick={() => setActive(null)}>
                ✕
              </button>
            </div>
            <div className="drawer-body">
              <div className="toolbar">
                <Perms code="rms:recipe:version">
                  <button className="btn btn-primary btn-sm" onClick={() => setShowNewVer(true)}>
                    + {t('rms.recipe.newVersion')}
                  </button>
                </Perms>
                <Perms code="rms:recipe:create">
                  <button className="btn btn-ghost btn-sm" onClick={() => setShowSaveAs(true)}>
                    {t('rms.recipe.saveAs')}
                  </button>
                </Perms>
              </div>
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('rms.recipe.version')}</th>
                    <th>{t('rms.recipe.bodyFormat')}</th>
                    <th>{t('rms.recipe.status')}</th>
                    <th>{t('rms.common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {versions.map((v) => (
                    <tr key={v.id}>
                      <td className="mono">v{v.versionNo}</td>
                      <td>{v.bodyFormat}</td>
                      <td>
                        <span className={'tag ' + (STATUS_TONE[v.status] ?? 'info')}>
                          {t('rms.recipe.status.' + v.status)}
                        </span>
                      </td>
                      <td className="cell-actions">
                        <Perms code="rms:recipe:activate">
                          <button
                            className="btn btn-ghost btn-sm"
                            disabled={v.status !== 'DRAFT'}
                            title={v.status !== 'DRAFT' ? t('rms.recipe.activateBlocked') : ''}
                            onClick={() => doActivate(v)}
                          >
                            {t('rms.recipe.activate')}
                          </button>
                        </Perms>
                      </td>
                    </tr>
                  ))}
                  {versions.length === 0 && (
                    <tr>
                      <td colSpan={4} className="empty-cell">
                        {t('rms.common.noData')}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>

              {showNewVer && (
                <NewVersionModal
                  recipeId={active.id}
                  onClose={() => setShowNewVer(false)}
                  onDone={async () => {
                    setShowNewVer(false);
                    setVersions(await api.listVersions(active.id));
                  }}
                />
              )}
              {showSaveAs && (
                <SaveAsModal
                  recipeId={active.id}
                  defaultName={active.name + ' (copy)'}
                  onClose={() => setShowSaveAs(false)}
                  onDone={async () => {
                    setShowSaveAs(false);
                    setActive(null);
                    await refresh();
                  }}
                />
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ----------------------------------------------------------------- 新建配方

function CreateModal({
  types,
  areas,
  onClose,
  onCreated,
}: {
  types: DeviceType[];
  areas: DeviceArea[];
  onClose: () => void;
  onCreated: () => void;
}) {
  const { t } = useTranslation();
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [deviceTypeId, setDeviceTypeId] = useState('');
  const [areaId, setAreaId] = useState('');
  const [golden, setGolden] = useState(false);
  const [remark, setRemark] = useState('');
  const [bodyFormat, setBodyFormat] = useState<BodyFormat>('TEXT');
  const [body, setBody] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit() {
    if (!code || !name || !body) {
      setErr(t('rms.common.confirm'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const expectedBodyHash = await sha256Hex(body);
      await api.createRecipe({
        code,
        name,
        deviceTypeId: deviceTypeId || null,
        areaId: areaId || null,
        golden,
        remark: remark || null,
        bodyFormat,
        body,
        expectedBodyHash,
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
          <h3>{t('rms.recipe.createNew')}</h3>
          <button className="icon-btn" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.recipe.code')} *</label>
              <input className="text-input" value={code} onChange={(e) => setCode(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.recipe.name')} *</label>
              <input className="text-input" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.recipe.deviceType')}</label>
              <select className="text-input" value={deviceTypeId} onChange={(e) => setDeviceTypeId(e.target.value)}>
                <option value="">—</option>
                {types.map((x) => (
                  <option key={x.id} value={x.id}>
                    {x.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.recipe.area')}</label>
              <select className="text-input" value={areaId} onChange={(e) => setAreaId(e.target.value)}>
                <option value="">—</option>
                {areas.map((x) => (
                  <option key={x.id} value={x.id}>
                    {x.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.recipe.bodyFormat')}</label>
            <select className="text-input" value={bodyFormat} onChange={(e) => setBodyFormat(e.target.value as BodyFormat)}>
              <option value="TEXT">TEXT</option>
              <option value="XML">XML</option>
              <option value="JSON">JSON</option>
              <option value="BIN">BIN</option>
            </select>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.recipe.title')} Body *</label>
            <textarea className="text-input" rows={6} value={body} onChange={(e) => setBody(e.target.value)} />
          </div>
          <div className="field">
            <label className="field-label">{t('rms.common.remark')}</label>
            <input className="text-input" value={remark} onChange={(e) => setRemark(e.target.value)} />
          </div>
          <label className="check">
            <input type="checkbox" checked={golden} onChange={(e) => setGolden(e.target.checked)} />
            {t('rms.recipe.golden')}
          </label>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>
            {t('rms.common.cancel')}
          </button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>
            {t('rms.common.save')}
          </button>
        </div>
      </div>
    </div>
  );
}

// ----------------------------------------------------------------- 新版本

function NewVersionModal({
  recipeId,
  onClose,
  onDone,
}: {
  recipeId: string;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [bodyFormat, setBodyFormat] = useState<BodyFormat>('TEXT');
  const [body, setBody] = useState('');
  const [remark, setRemark] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit() {
    if (!body) {
      setErr(t('rms.common.confirm'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const expectedBodyHash = await sha256Hex(body);
      await api.newVersion(recipeId, { bodyFormat, body, expectedBodyHash, remark: remark || null });
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
          <h3>{t('rms.recipe.newVersion')}</h3>
          <button className="icon-btn" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="modal-body">
          <div className="field">
            <label className="field-label">{t('rms.recipe.bodyFormat')}</label>
            <select className="text-input" value={bodyFormat} onChange={(e) => setBodyFormat(e.target.value as BodyFormat)}>
              <option value="TEXT">TEXT</option>
              <option value="XML">XML</option>
              <option value="JSON">JSON</option>
              <option value="BIN">BIN</option>
            </select>
          </div>
          <div className="field">
            <label className="field-label">Body *</label>
            <textarea className="text-input" rows={8} value={body} onChange={(e) => setBody(e.target.value)} />
          </div>
          <div className="field">
            <label className="field-label">{t('rms.common.remark')}</label>
            <input className="text-input" value={remark} onChange={(e) => setRemark(e.target.value)} />
          </div>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>
            {t('rms.common.cancel')}
          </button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>
            {t('rms.common.save')}
          </button>
        </div>
      </div>
    </div>
  );
}

// ----------------------------------------------------------------- 另存为

function SaveAsModal({
  recipeId,
  defaultName,
  onClose,
  onDone,
}: {
  recipeId: string;
  defaultName: string;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [code, setCode] = useState('');
  const [name, setName] = useState(defaultName);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit() {
    if (!code || !name) {
      setErr(t('rms.common.confirm'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const expectedBodyHash = await sha256Hex(''); // 另存为沿用源版本 body，hash 由后端按源内容校验
      await api.saveAs(recipeId, { code, name, expectedBodyHash, remark: null });
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
          <h3>{t('rms.recipe.saveAs')}</h3>
          <button className="icon-btn" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.recipe.code')} *</label>
              <input className="text-input" value={code} onChange={(e) => setCode(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.recipe.name')} *</label>
              <input className="text-input" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
          </div>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>
            {t('rms.common.cancel')}
          </button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>
            {t('rms.common.save')}
          </button>
        </div>
      </div>
    </div>
  );
}
