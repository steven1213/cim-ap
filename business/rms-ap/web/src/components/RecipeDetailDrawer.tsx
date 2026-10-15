import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/rmsApi';
import Perms from '@/components/Perms';
import SpecEditModal from '@/components/SpecEditModal';
import type {
  RecipeVersion,
  RecipeParsedView,
  RecipeValidationResult,
  RecipeToolUsage,
  Device,
  DeviceChamber,
  ParamSpec,
  SignRoute,
  BodyFormat,
} from '@/types';

/** UTF-8 安全的文本 → Base64。 */
function utf8ToBase64(s: string): string {
  return btoa(unescape(encodeURIComponent(s)));
}

/** Base64 → UTF-8 文本。 */
function base64ToUtf8(b64: string): string {
  try {
    return decodeURIComponent(escape(atob(b64)));
  } catch {
    return b64;
  }
}

const STATUS_TONE: Record<string, string> = {
  DRAFT: 'info',
  PENDING_SIGN: 'warn',
  ACTIVE: 'ok',
  OBSOLETE: 'muted',
};

/** HASH 缩略。 */
function shortHash(h: string | null | undefined): string {
  if (!h) return '—';
  return h.length <= 20 ? h : `${h.slice(0, 8)}…${h.slice(-8)}`;
}

interface Props {
  recipeId: string;
  recipeCode?: string | null;
  recipeName?: string | null;
  /** 版本列表（版本号倒序）；抽屉内可切换版本。 */
  versions: RecipeVersion[];
  /** 初始展示的版本（通常取生效版）。 */
  initialVersionId?: string | null;
  onClose: () => void;
  /** 编辑成功后回调（刷新外层列表）。 */
  onChanged?: () => void;
}

/**
 * 配方详情抽屉（W7）。
 *
 * <p>回答「这个配方里到底是什么」：结构化参数 / 步序 / 子配方 + 良构可执行校验 +
 * Body 原文，并支持对 <b>DRAFT</b> 版本直接编辑（已生效版本不可变，须另存新版本）。</p>
 */
export default function RecipeDetailDrawer({
  recipeId,
  recipeCode,
  recipeName,
  versions,
  initialVersionId,
  onClose,
  onChanged,
}: Props) {
  const { t } = useTranslation();
  const [versionId, setVersionId] = useState<string>('');
  const [parsed, setParsed] = useState<RecipeParsedView | null>(null);
  const [validation, setValidation] = useState<RecipeValidationResult | null>(null);
  const [bodyText, setBodyText] = useState('');
  const [showRaw, setShowRaw] = useState(false);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [notice, setNotice] = useState('');
  const [showSignoff, setShowSignoff] = useState(false);
  const [busyExport, setBusyExport] = useState(false);
  const [exportErr, setExportErr] = useState('');

  useEffect(() => {
    const v = initialVersionId || (versions.length ? versions[0].id : '');
    setVersionId(v);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recipeId, initialVersionId, versions]);

  const current = versions.find((x) => x.id === versionId) || null;
  const editable = current?.status === 'DRAFT';

  useEffect(() => {
    if (!versionId) return;
    setErr('');
    api.parsedVersion(recipeId, versionId).then(setParsed).catch((e) => setErr(e?.message || 'error'));
    api.validateVersion(recipeId, versionId).then(setValidation).catch(() => setValidation(null));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recipeId, versionId]);

  async function loadRaw() {
    if (bodyText) {
      setShowRaw(!showRaw);
      return;
    }
    try {
      const b = await api.getVersionBody(versionId);
      setBodyText(b.bodyBase64 ? base64ToUtf8(b.bodyBase64) : '');
      setShowRaw(true);
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  function startEdit() {
    setEditing(true);
    setNotice('');
    if (!bodyText) {
      api
        .getVersionBody(versionId)
        .then((b) => {
          const txt = b.bodyBase64 ? base64ToUtf8(b.bodyBase64) : '';
          setBodyText(txt);
          setDraft(txt);
        })
        .catch((e) => setErr(e?.message || 'error'));
    } else {
      setDraft(bodyText);
    }
  }

  async function save() {
    if (!current) return;
    setBusy(true);
    setErr('');
    try {
      const b64 = utf8ToBase64(draft);
      // 乐观锁传「库中旧 hash」（后端先比对旧值再覆盖）；sha256Hex(draft) 是新值语义，勿混用
      await api.updateVersionBody(recipeId, versionId, {
        bodyFormat: current.bodyFormat as BodyFormat,
        bodyBase64: b64,
        expectedBodyHash: current.bodyHash,
        changeSummary: t('rms.recipe.detail.edited'),
      });
      setBodyText(draft);
      setEditing(false);
      setNotice(t('rms.recipe.detail.saved'));
      // 重取解析与校验
      api.parsedVersion(recipeId, versionId).then(setParsed).catch(() => undefined);
      api.validateVersion(recipeId, versionId).then(setValidation).catch(() => undefined);
      onChanged?.();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  const p = parsed?.parsed || null;

  /**
   * 另存新版本（生命周期闭环的关键一环）：
   * ACTIVE/OBSOLETE 版本不可变，以当前内容建新 DRAFT 并切换过去——
   * 用户在抽屉内即可开始下一轮「编辑 → 发起签核」，无需跳回配方库页。
   */
  async function saveAsNewVersion() {
    if (!current) return;
    setBusy(true);
    setErr('');
    try {
      let txt = bodyText;
      if (!txt) {
        const b = await api.getVersionBody(versionId);
        txt = b.bodyBase64 ? base64ToUtf8(b.bodyBase64) : '';
        setBodyText(txt);
      }
      const v = await api.newVersion(recipeId, {
        bodyFormat: current.bodyFormat,
        body: txt,
        expectedBodyHash: current.bodyHash,
        remark: t('rms.recipe.detail.saveAsNewRemark', { n: current.versionNo }),
      });
      setNotice(t('rms.recipe.detail.saveAsNewDone', { n: v.versionNo }));
      setVersionId(v.id);
      setEditing(false);
      setBodyText('');
      setShowRaw(false);
      onChanged?.();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <div className="drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-head">
          <h3>
            {recipeCode ?? '—'}
            {recipeName ? ` · ${recipeName}` : ''}
          </h3>
          <div className="cell-actions">
            <Perms code="rms:body:export">
              <button
                className="btn btn-ghost btn-sm"
                disabled={busyExport}
                title={t('rms.recipe.exportAll')}
                onClick={async () => {
                  setBusyExport(true);
                  setExportErr('');
                  try {
                    const blob = await api.exportRecipes(recipeId ? [recipeId] : undefined);
                    api.saveBlob(blob, `${recipeCode || 'recipe'}-export.json`);
                  } catch (e: any) {
                    setExportErr(e?.message || String(e));
                  } finally {
                    setBusyExport(false);
                  }
                }}
              >
                {t('rms.recipe.exportOne')}
              </button>
            </Perms>
            <button className="icon-btn" onClick={onClose}>
              ✕
            </button>
          </div>
        </div>
        <div className="drawer-body">
          {err && (
            <div className="alert err">
              <span>{err}</span>
            </div>
          )}
          {exportErr && (
            <div className="alert err">
              <span>{exportErr}</span>
            </div>
          )}
          {notice && (
            <div className="alert ok">
              <span>{notice}</span>
            </div>
          )}

          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.recipe.version')}</label>
              <select
                className="text-input"
                value={versionId}
                onChange={(e) => {
                  setVersionId(e.target.value);
                  setEditing(false);
                  setBodyText('');
                  setShowRaw(false);
                  setNotice('');
                }}
              >
                {versions.map((v) => (
                  <option key={v.id} value={v.id}>
                    v{v.versionNo} · {v.status}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.recipe.bodyFormat')}</label>
              <div>
                <span className="tag info">{current?.bodyFormat ?? '—'}</span>{' '}
                <span className={'tag ' + (STATUS_TONE[current?.status ?? ''] ?? 'info')}>
                  {current ? t('rms.recipe.status.' + current.status) : '—'}
                </span>
              </div>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.recipe.detail.bodyHash')}</label>
              <div className="mono muted">{shortHash(current?.bodyHash)}</div>
            </div>
            <div className="field">
              <label className="field-label">&nbsp;</label>
              <div className="cell-actions">
                <button className="btn btn-ghost btn-sm" onClick={loadRaw}>
                  {showRaw ? t('rms.recipe.detail.hideRaw') : t('rms.recipe.detail.showRaw')}
                </button>
                <Perms code="rms:recipe:version">
                  <button
                    className="btn btn-ghost btn-sm"
                    disabled={!editable || editing}
                    title={!editable ? t('rms.recipe.detail.editBlocked') : ''}
                    onClick={startEdit}
                  >
                    {t('rms.common.edit')}
                  </button>
                </Perms>
                <Perms code="rms:recipe:update">
                  <button
                    className="btn btn-primary btn-sm"
                    disabled={!editable || busy}
                    title={!editable ? t('rms.recipe.detail.signoffBlocked') : t('rms.recipe.detail.signoffGo')}
                    onClick={() => setShowSignoff(true)}
                  >
                    {t('rms.recipe.detail.signoffGo')}
                  </button>
                </Perms>
                {!editable && (
                  <Perms code="rms:recipe:version">
                    <button className="btn btn-ghost btn-sm" disabled={busy} onClick={saveAsNewVersion}>
                      {t('rms.recipe.detail.saveAsNew')}
                    </button>
                  </Perms>
                )}
              </div>
            </div>
          </div>

          {validation && (
            <div className={'alert ' + (validation.executable ? 'ok' : 'err')}>
              <span>
                {validation.wellFormed
                  ? t('rms.recipe.detail.wellFormed')
                  : t('rms.recipe.detail.notWellFormed')}
                {' · '}
                {validation.executable
                  ? t('rms.recipe.detail.executable')
                  : t('rms.recipe.detail.notExecutable')}
                {validation.errors.length > 0 ? ` · ${validation.errors.join(' | ')}` : ''}
              </span>
            </div>
          )}

          {parsed && !parsed.ok && (
            <div className="alert warn">
              <span>
                {t('rms.recipe.detail.parseFail')}: {parsed.error}
              </span>
            </div>
          )}

          {editing && (
            <div className="panel">
              <div className="panel-head">
                <h3>{t('rms.recipe.detail.editBody')}</h3>
                <span className="muted">{t('rms.recipe.detail.editHint')}</span>
              </div>
              <textarea
                className="text-input"
                rows={10}
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                style={{ fontFamily: 'monospace' }}
              />
              <div className="toolbar">
                <button className="btn btn-primary btn-sm" disabled={busy} onClick={save}>
                  {t('rms.common.save')}
                </button>
                <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => setEditing(false)}>
                  {t('rms.common.cancel')}
                </button>
              </div>
            </div>
          )}

          {p && p.meta && Object.keys(p.meta).length > 0 && (
            <div className="panel">
              <div className="panel-head">
                <h3>{t('rms.recipe.detail.meta')}</h3>
              </div>
              <div className="table-wrap">
                <table className="data">
                  <tbody>
                    {Object.entries(p.meta).map(([k, v]) => (
                      <tr key={k}>
                        <td className="mono">{k}</td>
                        <td className="mono">{v}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          <div className="panel">
            <div className="panel-head">
              <h3>{t('rms.recipe.detail.params')}</h3>
              <span className="muted">{p ? p.params.length : 0}</span>
            </div>
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('rms.recipe.detail.key')}</th>
                    <th>{t('rms.recipe.detail.name')}</th>
                    <th>{t('rms.recipe.detail.value')}</th>
                    <th>{t('rms.recipe.detail.unit')}</th>
                    <th>{t('rms.recipe.detail.range')}</th>
                  </tr>
                </thead>
                <tbody>
                  {(p?.params ?? []).map((x, i) => {
                    const bad = (validation?.checks ?? []).find((c) => c.paramKey === x.key);
                    return (
                      <tr key={`${x.key}-${i}`} className={bad ? 'diff-changed' : ''}>
                        <td className="mono">{x.key}</td>
                        <td>{x.name ?? '—'}</td>
                        <td className="mono">{x.value ?? '—'}</td>
                        <td className="muted">{x.unit ?? '—'}</td>
                        <td className="muted">
                          {x.min || x.max ? `${x.min ?? ''} ~ ${x.max ?? ''}` : '—'}
                        </td>
                      </tr>
                    );
                  })}
                  {(p?.params ?? []).length === 0 && (
                    <tr>
                      <td colSpan={5} className="empty-cell">
                        {t('rms.recipe.detail.noParams')}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {(p?.steps ?? []).length > 0 && (
            <div className="panel">
              <div className="panel-head">
                <h3>{t('rms.recipe.detail.steps')}</h3>
              </div>
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>{t('rms.recipe.detail.name')}</th>
                      <th>{t('rms.recipe.detail.params')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(p?.steps ?? []).map((s, i) => (
                      <tr key={`s-${i}`}>
                        <td className="mono">{s.stepNo ?? i + 1}</td>
                        <td>{s.name ?? '—'}</td>
                        <td className="mono muted">{s.params.join(', ') || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {(p?.subRecipes ?? []).length > 0 && (
            <div className="panel">
              <div className="panel-head">
                <h3>{t('rms.recipe.detail.subRecipes')}</h3>
              </div>
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('rms.recipe.detail.name')}</th>
                      <th>{t('rms.recipe.detail.ref')}</th>
                      <th>{t('rms.recipe.detail.params')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(p?.subRecipes ?? []).map((s, i) => (
                      <tr key={`sr-${i}`}>
                        <td>{s.name ?? '—'}</td>
                        <td className="mono">{s.ref ?? '—'}</td>
                        <td className="mono muted">{s.params.map((x) => x.key).join(', ') || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {p && p.warnings && p.warnings.length > 0 && (
            <div className="alert warn">
              <span>{p.warnings.join(' | ')}</span>
            </div>
          )}

          {showRaw && (
            <div className="panel">
              <div className="panel-head">
                <h3>{t('rms.recipe.detail.raw')}</h3>
              </div>
              <pre className="mono muted" style={{ whiteSpace: 'pre-wrap', margin: 0 }}>
                {bodyText || t('rms.common.noData')}
              </pre>
            </div>
          )}

          <SpecsSection recipeId={recipeId} />

          <ToolsSection recipeId={recipeId} onChanged={onChanged} />

          <ChangeEffectsSection recipeId={recipeId} />

          {showSignoff && current && (
            <SignoffSubmitModal
              recipeId={recipeId}
              versionId={versionId}
              versionNo={current.versionNo ?? null}
              onClose={() => setShowSignoff(false)}
              onDone={() => {
                setShowSignoff(false);
                setNotice(t('rms.recipe.detail.signoffDone'));
                onChanged?.();
              }}
            />
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * 发起签核弹窗（就地闭环）：对当前 DRAFT 版本直接提交签核——
 * 不再要求用户跳到签核页重新选配方、选版本。
 */
function SignoffSubmitModal({ recipeId, versionId, versionNo, onClose, onDone }: {
  recipeId: string;
  versionId: string;
  versionNo: number | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [reason, setReason] = useState('');
  const [route, setRoute] = useState<SignRoute>('SINGLE');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit() {
    if (!reason.trim()) {
      setErr(t('rms.signoff.reason') + ' *');
      return;
    }
    setBusy(true);
    setErr('');
    try {
      await api.submitSignoff({ recipeId, versionId, reason: reason.trim(), signRoute: route });
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
          <h3>{t('rms.recipe.detail.signoffGo')}{versionNo != null ? ` · v${versionNo}` : ''}</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="field">
            <label className="field-label">{t('rms.signoff.reason')} *</label>
            <textarea
              className="text-input"
              rows={2}
              placeholder={t('rms.signoff.reasonPh')}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
          <div className="field">
            <label className="field-label">{t('rms.signoff.route')}</label>
            <select
              className="text-input"
              value={route}
              onChange={(e) => setRoute(e.target.value as SignRoute)}
            >
              <option value="SINGLE">{t('rms.signoff.route.SINGLE')}</option>
              <option value="COUNTERSIGN">{t('rms.signoff.route.COUNTERSIGN')}</option>
              <option value="ANY">{t('rms.signoff.route.ANY')}</option>
            </select>
            <div className="dim" style={{ fontSize: 11, marginTop: 4 }}>{t('rms.signoff.routeHint')}</div>
          </div>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{t('rms.common.cancel')}</button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>
            {t('rms.signoff.submit')}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * 规格参数区块（就地维护）：配方详情内直接增删改规格，
 * 复用与规格参数页相同的共享编辑弹窗——规格与 Body/解析同屏，无需跨页定位。
 */
function SpecsSection({ recipeId }: { recipeId: string }) {
  const { t } = useTranslation();
  const [specs, setSpecs] = useState<ParamSpec[]>([]);
  const [editing, setEditing] = useState<ParamSpec | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function load() {
    try {
      setSpecs(await api.listSpecs(recipeId));
      setErr('');
    } catch {
      // 无 rms:spec:view 权限时静默隐藏（外层已用 Perms 控制渲染，这里兜底）
      setSpecs([]);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recipeId]);

  async function toggle(x: ParamSpec) {
    setBusy(true);
    try {
      await api.setSpecEnabled(x.id, !x.enabled);
      await load();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  async function remove(x: ParamSpec) {
    if (!window.confirm(t('rms.spec.deleteConfirm'))) return;
    setBusy(true);
    try {
      await api.deleteSpec(x.id);
      await load();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>{t('rms.recipe.detail.specs')}</h3>
        <Perms code="rms:spec:manage">
          <button className="btn btn-ghost btn-sm" onClick={() => setShowCreate(true)}>
            {t('rms.spec.add')}
          </button>
        </Perms>
      </div>
      {err && (
        <div className="alert err">
          <span>{err}</span>
        </div>
      )}
      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.spec.paramKey')}</th>
              <th>{t('rms.spec.specType')}</th>
              <th>{t('rms.recipe.detail.value')}</th>
              <th>{t('rms.spec.unit')}</th>
              <th>{t('rms.common.status')}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {specs.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.paramKey}</td>
                <td>
                  <span className="tag info">{t('rms.spec.type.' + x.specType)}</span>
                </td>
                <td className="mono">{specValueText(x)}</td>
                <td className="muted">{x.unit ?? '—'}</td>
                <td>
                  <span className={'tag ' + (x.enabled ? 'ok' : 'muted')}>
                    {x.enabled ? t('rms.spec.enabled') : t('rms.spec.disabled')}
                  </span>
                </td>
                <td className="cell-actions">
                  <Perms code="rms:spec:manage">
                    <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => setEditing(x)}>
                      {t('rms.common.edit')}
                    </button>
                    <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => toggle(x)}>
                      {x.enabled ? t('rms.spec.disable') : t('rms.spec.enable')}
                    </button>
                    <button className="btn btn-ghost btn-sm danger" disabled={busy} onClick={() => remove(x)}>
                      {t('rms.common.delete')}
                    </button>
                  </Perms>
                </td>
              </tr>
            ))}
            {specs.length === 0 && (
              <tr>
                <td colSpan={6} className="empty-cell">
                  {t('rms.spec.empty')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {(showCreate || editing) && (
        <SpecEditModal
          recipeId={recipeId}
          initial={editing}
          onClose={() => {
            setShowCreate(false);
            setEditing(null);
          }}
          onDone={() => {
            setShowCreate(false);
            setEditing(null);
            load();
          }}
        />
      )}
    </div>
  );
}

/** 规格值摘要（与规格参数页同规则）。 */
function specValueText(x: ParamSpec): string {
  switch (x.specType) {
    case 'RANGE':
      return `[${x.rangeMin ?? '-'}, ${x.rangeMax ?? '-'}]`;
    case 'LIST_IN':
      return `{${x.listValues ?? '-'}}`;
    case 'TOLERANCE':
      return `${x.targetValue ?? '-'} ±${x.tolerancePct ?? '-'}% / ${x.toleranceAbs ?? '-'}`;
    default:
      return x.targetValue ?? '-';
  }
}

/**
 * 装载机台区块（W7 体验整合）：配方视角直接看/绑/解机台——
 * 不再要求用户跳到集成监控页交叉比对；绑定 = 机台 + PPID（腔体可选，随所选机台联动）。
 */
function ToolsSection({ recipeId, onChanged }: { recipeId: string; onChanged?: () => void }) {
  const { t } = useTranslation();
  const [usages, setUsages] = useState<RecipeToolUsage[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [deviceId, setDeviceId] = useState('');
  const [chambers, setChambers] = useState<DeviceChamber[]>([]);
  const [chamberId, setChamberId] = useState('');
  const [ppid, setPpid] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function load() {
    try {
      setUsages(await api.recipeTools(recipeId));
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  useEffect(() => {
    load();
    api.listDevices().then(setDevices).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recipeId]);

  useEffect(() => {
    setChamberId('');
    if (!deviceId) {
      setChambers([]);
      return;
    }
    api.listChambers(deviceId).then(setChambers).catch(() => setChambers([]));
  }, [deviceId]);

  async function bind() {
    if (!deviceId || !ppid.trim()) {
      setErr(t('rms.tools.bindRequired'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      await api.registerPpidMap({
        deviceId,
        deviceTypeId: null,
        ppidOnTool: ppid.trim(),
        recipeId,
        chamberId: chamberId || null,
      });
      setPpid('');
      await load();
      onChanged?.();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  async function unbind(x: RecipeToolUsage) {
    if (!window.confirm(t('rms.tools.unbindConfirm'))) return;
    setBusy(true);
    setErr('');
    try {
      await api.deletePpidMap(x.id);
      await load();
      onChanged?.();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>{t('rms.tools.title')}</h3>
        <span className="muted">{usages.length}</span>
      </div>
      {err && (
        <div className="alert err">
          <span>{err}</span>
        </div>
      )}

      <div className="form-grid">
        <div className="field">
          <label className="field-label">{t('rms.tools.device')}</label>
          <select
            className="text-input"
            value={deviceId}
            onChange={(e) => setDeviceId(e.target.value)}
          >
            <option value="">{t('rms.tools.pickDevice')}</option>
            {devices.map((d) => (
              <option key={d.id} value={d.id}>
                {d.code} · {d.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label className="field-label">{t('rms.tools.ppid')}</label>
          <input
            className="text-input"
            value={ppid}
            placeholder="PPID-001"
            onChange={(e) => setPpid(e.target.value)}
          />
        </div>
        <div className="field">
          <label className="field-label">{t('rms.tools.chamber')}</label>
          <select
            className="text-input"
            value={chamberId}
            onChange={(e) => setChamberId(e.target.value)}
            disabled={!deviceId}
          >
            <option value="">{t('rms.tools.noChamber')}</option>
            {chambers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.code} · {c.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label className="field-label">&nbsp;</label>
          <Perms code="rms:recipe:update">
            <button className="btn btn-primary" disabled={busy} onClick={bind}>
              {t('rms.tools.bind')}
            </button>
          </Perms>
        </div>
      </div>

      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.tools.device')}</th>
              <th>{t('rms.tools.ppid')}</th>
              <th>{t('rms.tools.chamber')}</th>
              <th>{t('rms.tools.qualification')}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {usages.map((u) => (
              <tr key={u.id}>
                <td className="mono">
                  {u.deviceCode ?? '—'}
                  {u.deviceName ? <span className="muted"> · {u.deviceName}</span> : null}
                </td>
                <td className="mono">{u.ppidOnTool}</td>
                <td className="mono">
                  {u.chamberCode ? (
                    <>
                      {u.chamberCode}
                      {u.deviceId ? null : <span className="muted"> · {t('rms.tools.typeLevel')}</span>}
                    </>
                  ) : (
                    <span className="muted">{t('rms.tools.noChamberBound')}</span>
                  )}
                </td>
                <td>
                  {u.qualificationStatus ? (
                    <span
                      className={'tag ' + (u.qualificationStatus === 'GRANTED' ? 'ok' : 'warn')}
                    >
                      {u.qualificationStatus}
                    </span>
                  ) : (
                    <span className="muted">{t('rms.tools.unqualified')}</span>
                  )}
                </td>
                <td className="cell-actions">
                  <Perms code="rms:recipe:update">
                    <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => unbind(u)}>
                      {t('rms.tools.unbind')}
                    </button>
                  </Perms>
                </td>
              </tr>
            ))}
            {usages.length === 0 && (
              <tr>
                <td colSpan={5} className="empty-cell">
                  {t('rms.tools.empty')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/**
 * 变更效果区块（W11）：该配方的版本生效历史（谁在何时从哪版切到哪版、参数差异几项），
 * 供质量侧按变更时点回溯 SPC/Cpk（跨域引用 spcRef 预留）。
 */
function ChangeEffectsSection({ recipeId }: { recipeId: string }) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<import('@/types').ChangeEffect[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let alive = true;
    api
      .listChangeEffects(recipeId)
      .then((r) => {
        if (alive) setRows(r);
      })
      .catch(() => undefined)
      .finally(() => {
        if (alive) setLoaded(true);
      });
    return () => {
      alive = false;
    };
  }, [recipeId]);

  if (!loaded) return null;
  return (
    <div className="panel">
      <div className="panel-head">
        <h3>{t('rms.effect.title')}</h3>
        <span className="muted">{t('rms.effect.sub')}</span>
      </div>
      {rows.length === 0 ? (
        <div className="empty-cell">{t('rms.effect.empty')}</div>
      ) : (
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.effect.from')}</th>
              <th>{t('rms.effect.to')}</th>
              <th>{t('rms.effect.by')}</th>
              <th>{t('rms.effect.at')}</th>
              <th>{t('rms.effect.diffCount')}</th>
              <th>{t('rms.common.remark')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((e) => (
              <tr key={e.id}>
                <td className="mono">{e.fromVersionNo == null ? t('rms.common.dash') : 'v' + e.fromVersionNo}</td>
                <td className="mono">v{e.toVersionNo}</td>
                <td>{e.activatedBy ?? t('rms.common.dash')}</td>
                <td className="muted">{e.activatedAt}</td>
                <td>{e.paramDiffCount}</td>
                <td className="muted">{e.changeSummary ?? t('rms.common.dash')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
