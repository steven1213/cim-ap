import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import type { Recipe, RecipeVersion, DeviceType, DeviceArea, BodyFormat, BodyTemplate, PreviewResult } from '@/types';

async function sha256Hex(text: string): Promise<string> {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

/** UTF-8 安全的文本⇄Base64（btoa 仅支持 Latin1，多字节需先编码）。 */
function b64Utf8(s: string): string {
  return btoa(unescape(encodeURIComponent(s)));
}

function atobUtf8(b64: string): string {
  return decodeURIComponent(escape(atob(b64)));
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
  const [showImport, setShowImport] = useState(false);
  const [busyExport, setBusyExport] = useState(false);
  const [exportErr, setExportErr] = useState('');

  async function doExport(ids?: string[]) {
    setBusyExport(true);
    setExportErr('');
    try {
      const blob = await api.exportRecipes(ids);
      const ts = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '');
      api.saveBlob(blob, `rms-recipes-${ts}.json`);
    } catch (e: any) {
      setExportErr(e?.message || String(e));
    } finally {
      setBusyExport(false);
    }
  }

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
        <div className="cell-actions">
          <Perms code="rms:recipe:create">
            <button className="btn btn-ghost btn-sm" onClick={() => setShowImport(true)}>
              {t('rms.recipe.importBtn')}
            </button>
          </Perms>
          <Perms code="rms:body:export">
            <button className="btn btn-ghost btn-sm" disabled={busyExport} onClick={() => doExport()}>
              {busyExport ? t('rms.common.loading') : t('rms.recipe.exportAll')}
            </button>
          </Perms>
          <Perms code="rms:recipe:create">
            <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
              + {t('rms.recipe.createNew')}
            </button>
          </Perms>
        </div>
      </div>
      {exportErr && <div className="form-error">{exportErr}</div>}

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

      {showImport && (
        <ImportModal
          onClose={() => setShowImport(false)}
          onDone={async () => {
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
  // W10 新建向导：Body 模板骨架 + 保存前预检
  const [templates, setTemplates] = useState<BodyTemplate[]>([]);
  const [templateId, setTemplateId] = useState('');
  const [preview, setPreview] = useState<PreviewResult | null>(null);
  const [previewing, setPreviewing] = useState(false);

  useEffect(() => {
    api.listBodyTemplates().then(setTemplates).catch(() => setTemplates([]));
  }, []);

  const matchedTemplates = useMemo(
    () => templates.filter((x) => !x.scopeDeviceTypeId || x.scopeDeviceTypeId === deviceTypeId),
    [templates, deviceTypeId],
  );

  async function applyTemplate(id: string) {
    setTemplateId(id);
    if (!id) return;
    try {
      const sk = await api.templateSkeleton(id);
      setBodyFormat(sk.bodyFormat as BodyFormat);
      // 骨架 bodyBase64 → 明文填入 textarea（与 create 契约一致：提交时再算 hash）
      setBody(atobUtf8(sk.bodyBase64));
      setPreview(null);
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  async function runPreview() {
    if (!body.trim()) return;
    setPreviewing(true);
    setErr('');
    try {
      setPreview(await api.previewValidate({
        deviceTypeId: deviceTypeId || null,
        bodyFormat,
        bodyBase64: b64Utf8(body),
      }));
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setPreviewing(false);
    }
  }

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
              <option value="JSON_STRUCTURED">JSON_STRUCTURED</option>
              <option value="BINARY">BINARY</option>
            </select>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.btpl.applyTemplate')}</label>
            <select className="text-input" value={templateId} onChange={(e) => applyTemplate(e.target.value)}>
              <option value="">{t('rms.btpl.applyNone')}</option>
              {matchedTemplates.map((tp) => (
                <option key={tp.id} value={tp.id}>
                  {tp.code} · {tp.name}{tp.enforced ? '（' + t('rms.btpl.enforcedYes') + '）' : ''}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label className="field-label">
              {t('rms.recipe.title')} Body *
              <button className="btn btn-ghost btn-sm" style={{ marginLeft: 8 }} disabled={previewing || !body.trim()} onClick={runPreview}>
                {t('rms.recipe.previewValidate')}
              </button>
            </label>
            <textarea className="text-input" rows={6} value={body} onChange={(e) => { setBody(e.target.value); setPreview(null); }} />
          </div>
          {preview && (
            <div className={'alert ' + (preview.ok ? 'ok' : 'err')}>
              {preview.ok ? (
                <span>{t('rms.recipe.previewOk')}</span>
              ) : (
                <span>
                  {preview.errors.join('；')}
                  {preview.templateViolations.length > 0 && (
                    <> · {t('rms.recipe.previewTplMissing')}: {preview.templateViolations.map((v) => v.paramKey).join(', ')}</>
                  )}
                </span>
              )}
            </div>
          )}
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
              <option value="JSON_STRUCTURED">JSON_STRUCTURED</option>
              <option value="BINARY">BINARY</option>
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

// ==================== W8：导入弹窗（文件级备份恢复） ====================

function ImportModal({ onClose, onDone }: { onClose: () => void; onDone: () => void | Promise<void> }) {
  const { t } = useTranslation();
  const [content, setContent] = useState('');
  const [fileName, setFileName] = useState('');
  const [strategy, setStrategy] = useState<'SKIP' | 'NEW_VERSION'>('SKIP');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [report, setReport] = useState<import('@/types').RecipeImportReport | null>(null);

  function pickFile(f: File | null) {
    setErr('');
    if (!f) return;
    setFileName(f.name);
    const reader = new FileReader();
    reader.onload = () => setContent(String(reader.result || ''));
    reader.onerror = () => setErr(t('rms.recipe.importReadFail'));
    reader.readAsText(f, 'utf-8');
  }

  async function run() {
    setErr('');
    setBusy(true);
    try {
      const r = await api.importRecipes(content, strategy);
      setReport(r);
      await onDone();
    } catch (e: any) {
      setErr(e?.message || String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{t('rms.recipe.importTitle')}</h3>
          <button className="icon-btn" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="modal-body">
          {!report ? (
            <>
              <div className="field">
                <label className="field-label">{t('rms.recipe.importFile')} *</label>
                <input
                  type="file"
                  accept=".json,application/json"
                  className="text-input"
                  onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
                />
                {fileName && <div className="muted" style={{ marginTop: 4 }}>{fileName}</div>}
              </div>
              <div className="field">
                <label className="field-label">{t('rms.recipe.importStrategy')}</label>
                <label className="check">
                  <input
                    type="radio"
                    checked={strategy === 'SKIP'}
                    onChange={() => setStrategy('SKIP')}
                  />
                  {t('rms.recipe.strategySkip')}
                </label>
                <label className="check">
                  <input
                    type="radio"
                    checked={strategy === 'NEW_VERSION'}
                    onChange={() => setStrategy('NEW_VERSION')}
                  />
                  {t('rms.recipe.strategyNewVer')}
                </label>
              </div>
              <div className="muted">{t('rms.recipe.importHint')}</div>
            </>
          ) : (
            <div>
              <div className="kpi-grid">
                <div className="kpi">
                  <div className="kpi-main">
                    <span className="kpi-val">{report.created}</span>
                  </div>
                  <div className="kpi-label">{t('rms.recipe.importCreated')}</div>
                </div>
                <div className="kpi">
                  <div className="kpi-main">
                    <span className="kpi-val">{report.updated}</span>
                  </div>
                  <div className="kpi-label">{t('rms.recipe.importUpdated')}</div>
                </div>
                <div className="kpi">
                  <div className="kpi-main">
                    <span className="kpi-val">{report.skipped}</span>
                  </div>
                  <div className="kpi-label">{t('rms.recipe.importSkipped')}</div>
                </div>
              </div>
              {report.errors.length > 0 && (
                <div className="field">
                  <label className="field-label">{t('rms.recipe.importErrors')}</label>
                  <ul className="muted" style={{ margin: '4px 0 0', paddingLeft: 18 }}>
                    {report.errors.map((e, i) => (
                      <li key={i}>{e}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          {report ? (
            <button className="btn btn-primary" onClick={onClose}>
              {t('rms.common.close')}
            </button>
          ) : (
            <>
              <button className="btn btn-ghost" onClick={onClose}>
                {t('rms.common.cancel')}
              </button>
              <button className="btn btn-primary" disabled={busy || !content} onClick={run}>
                {t('rms.recipe.importRun')}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
