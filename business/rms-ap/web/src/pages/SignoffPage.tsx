import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import Panel from '@/components/Panel';
import Drawer from '@/components/Drawer';
import * as api from '@/lib/rmsApi';
import type {
  Signoff,
  SignoffDetail,
  SignoffSummary,
  SignoffStatus,
  TempAllowStatus,
  SignRoute,
  BatchResult,
  Recipe,
  RecipeVersion,
  DiffItem,
} from '@/types';
import {
  IconAlert,
  IconBan,
  IconCheckCircle,
  IconInbox,
  IconList,
  IconPlus,
  IconRefresh,
  IconSearch,
  IconShield,
  IconSync,
  IconEye,
} from '@/components/Icons';

/** 签核状态徽标着色。 */
function statusTone(s: SignoffStatus): string {
  if (s === 'APPROVED') return 'tag ok';
  if (s === 'REJECTED') return 'tag err';
  return 'tag warn';
}

/** 限时放行徽标着色。 */
function tempTone(s: TempAllowStatus): string {
  if (s === 'ACTIVE') return 'tag ok';
  if (s === 'EXPIRED') return 'tag err';
  if (s === 'SCHEDULED') return 'tag';
  return '';
}

/** 差异项字段标签（param:xxx 走原样，其余映射 i18n）。 */
function fieldLabel(t: (k: string, o?: any) => string, field: string): string {
  if (field.startsWith('param:')) return field.slice(6);
  return t(`rms.compare.f.${field}`, { defaultValue: field });
}

/** 事件类型 → 时间线圆点色。 */
function eventDot(e: string): string {
  if (e === 'APPROVED') return 'timeline-dot ok';
  if (e === 'REJECTED' || e === 'TEMP_EXPIRED') return 'timeline-dot err';
  if (e === 'TEMP_ALLOWED' || e === 'TEMP_CANCELLED') return 'timeline-dot warn';
  return 'timeline-dot';
}

/** Date → datetime-local 输入值。 */
function toLocalInput(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

/**
 * 签核流（Req.md FR-L2/L4/L5、Req 3/20/41）：待办 KPI + 审批队列 + 批量决策 +
 * 预设限时使用 + 详情抽屉（参数级差异附件 / 事件时间线 / 电子签证据链）。
 */
export default function SignoffPage() {
  const { t } = useTranslation();
  const [rows, setRows] = useState<Signoff[]>([]);
  const [summary, setSummary] = useState<SignoffSummary | null>(null);
  const [status, setStatus] = useState('');
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState('');
  const [banner, setBanner] = useState<{ tone: string; text: string } | null>(null);
  const [showSubmit, setShowSubmit] = useState(false);
  const [decision, setDecision] = useState<{ row: Signoff; kind: 'APPROVE' | 'REJECT' } | null>(null);
  const [tempRow, setTempRow] = useState<Signoff | null>(null);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [batchResults, setBatchResults] = useState<BatchResult[] | null>(null);

  async function load() {
    setLoading(true);
    setErr('');
    try {
      const [list, sum] = await Promise.all([
        api.listSignoffs({ ...(status ? { status } : {}), ...(keyword ? { keyword } : {}) }),
        api.getSignoffSummary(),
      ]);
      setRows(list);
      setSummary(sum);
      setSelected(new Set());
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  function toggleRow(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  const pendingRows = rows.filter((r) => r.status === 'PENDING');
  const allPendingSelected = pendingRows.length > 0 && pendingRows.every((r) => selected.has(r.id));

  function toggleAll() {
    setSelected(allPendingSelected ? new Set() : new Set(pendingRows.map((r) => r.id)));
  }

  async function doBatch(kind: 'APPROVE' | 'REJECT') {
    const ids = [...selected];
    if (!ids.length) {
      return;
    }
    setBanner(null);
    try {
      const res = await api.batchDecision(ids, kind, undefined);
      setBatchResults(res);
      await load();
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  async function cancelTemp(row: Signoff) {
    try {
      await api.cancelTempAllow(row.id);
      await load();
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  return (
    <div className="page">
      {err && (
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      )}
      {banner && (
        <div className={'alert ' + banner.tone}>
          <IconCheckCircle width={15} height={15} />
          <span>{banner.text}</span>
        </div>
      )}

      <div className="page-head">
        <div>
          <h1>{t('rms.signoff.title')}</h1>
          <p className="dim" style={{ margin: '2px 0 0' }}>{t('rms.signoff.sub')}</p>
        </div>
        <Perms code="rms:signoff:approve">
          <button className="btn btn-primary btn-sm" onClick={() => setShowSubmit(true)}>
            <IconPlus width={14} height={14} /> {t('rms.signoff.submit')}
          </button>
        </Perms>
      </div>

      {/* KPI 指标块 */}
      <div className="kpi-grid">
        <div className="kpi">
          <div className="kpi-ico"><IconInbox width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.signoff.kpi.pending')}</div>
            <div className="kpi-val">{summary?.pending ?? '—'}</div>
          </div>
        </div>
        <div className="kpi ok">
          <div className="kpi-ico"><IconCheckCircle width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.signoff.kpi.approvedToday')}</div>
            <div className="kpi-val">{summary?.approvedToday ?? '—'}</div>
          </div>
        </div>
        <div className="kpi">
          <div className="kpi-ico"><IconBan width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.signoff.kpi.rejectedToday')}</div>
            <div className="kpi-val">{summary?.rejectedToday ?? '—'}</div>
          </div>
        </div>
        <div className="kpi info">
          <div className="kpi-ico"><IconSync width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.signoff.kpi.tempActive')}</div>
            <div className="kpi-val">{summary?.tempActive ?? '—'}</div>
          </div>
        </div>
        <div className="kpi warn">
          <div className="kpi-ico"><IconAlert width={16} height={16} /></div>
          <div className="kpi-main">
            <div className="kpi-label">{t('rms.signoff.kpi.expiringSoon')}</div>
            <div className="kpi-val">{summary?.expiringSoon ?? '—'}</div>
          </div>
        </div>
      </div>

      <Panel
        title={t('rms.signoff.title')}
        sub={t('rms.signoff.sub')}
        flush
        actions={
          <>
            <select className="select-sm" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">{t('rms.signoff.allStatus')}</option>
              <option value="PENDING">{t('rms.signoff.pending')}</option>
              <option value="APPROVED">{t('rms.signoff.approved')}</option>
              <option value="REJECTED">{t('rms.signoff.rejected')}</option>
            </select>
            <div className="search-inline">
              <IconSearch width={13} height={13} />
              <input
                className="text-input"
                placeholder={t('rms.signoff.searchPh')}
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') load(); }}
              />
            </div>
            <button className="btn btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? t('rms.common.loading') : t('rms.common.refresh')}
            </button>
          </>
        }
      >
        {selected.size > 0 && (
          <div className="toolbar batch-bar">
            <span className="tag pri">{t('rms.signoff.selectedN', { n: selected.size })}</span>
            <Perms code="rms:signoff:approve">
              <button className="btn btn-primary btn-sm" onClick={() => doBatch('APPROVE')}>
                <IconCheckCircle width={13} height={13} /> {t('rms.signoff.batchApprove')}
              </button>
              <button className="btn btn-ghost btn-sm danger" onClick={() => doBatch('REJECT')}>
                <IconBan width={13} height={13} /> {t('rms.signoff.batchReject')}
              </button>
            </Perms>
            <button className="btn btn-ghost btn-sm" onClick={() => setSelected(new Set())}>
              {t('rms.common.cancel')}
            </button>
          </div>
        )}

        {rows.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 34 }}>
                    <input type="checkbox" checked={allPendingSelected} onChange={toggleAll}
                      title={t('rms.signoff.selectAll')} />
                  </th>
                  <th style={{ width: 170 }}>{t('rms.recipe.title')}</th>
                  <th style={{ width: 64 }}>{t('rms.recipe.version')}</th>
                  <th>{t('rms.signoff.reason')}</th>
                  <th style={{ width: 84 }}>{t('rms.signoff.diffCount')}</th>
                  <th style={{ width: 84 }}>{t('rms.common.status')}</th>
                  <th style={{ width: 96 }}>{t('rms.signoff.temp')}</th>
                  <th style={{ width: 96 }}>{t('rms.signoff.initiator')}</th>
                  <th style={{ width: 96 }}>{t('rms.signoff.approver')}</th>
                  <th style={{ width: 150 }}>{t('rms.audit.th.time')}</th>
                  <th style={{ width: 210 }}>{t('rms.common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const selectable = r.status === 'PENDING';
                  return (
                    <tr key={r.id} className={selected.has(r.id) ? 'row-sel' : undefined}>
                      <td>
                        <input type="checkbox" disabled={!selectable}
                          checked={selected.has(r.id)} onChange={() => toggleRow(r.id)} />
                      </td>
                      <td>
                        <span className="mono">{r.recipeCode ?? t('rms.common.dash')}</span>
                        <div className="dim" style={{ fontSize: 11 }}>{r.recipeName ?? ''}</div>
                      </td>
                      <td className="mono">v{r.versionNo ?? '-'}</td>
                      <td className="dim" style={{ maxWidth: 260 }}>{r.reason ?? t('rms.signoff.noReason')}</td>
                      <td>
                        {r.diffCount > 0
                          ? <span className="tag warn">{t('rms.signoff.diffUnit', { n: r.diffCount })}</span>
                          : <span className="dim">{t('rms.common.dash')}</span>}
                      </td>
                      <td><span className={statusTone(r.status)}>{t('rms.signoff.' + r.status.toLowerCase())}</span></td>
                      <td>
                        {r.tempAllowStatus !== 'NONE' ? (
                          <span className={tempTone(r.tempAllowStatus)}
                            title={r.tempAllowTo ? t('rms.signoff.tempUntil', { t: r.tempAllowTo }) : ''}>
                            {t('rms.signoff.tempStatus.' + r.tempAllowStatus)}
                          </span>
                        ) : <span className="dim">{t('rms.common.dash')}</span>}
                      </td>
                      <td className="mono">{r.initiator ?? t('rms.common.dash')}</td>
                      <td className="mono">{r.approver ?? t('rms.common.dash')}</td>
                      <td className="mono dim">{r.createTime ?? t('rms.common.dash')}</td>
                      <td className="cell-actions">
                        <button className="btn btn-ghost btn-sm" onClick={() => setDetailId(r.id)}>
                          <IconEye width={13} height={13} /> {t('rms.signoff.detail')}
                        </button>
                        {r.status === 'PENDING' && (
                          <Perms code="rms:signoff:approve">
                            <button className="btn btn-ghost btn-sm" onClick={() => setDecision({ row: r, kind: 'APPROVE' })}>
                              <IconCheckCircle width={13} height={13} /> {t('rms.signoff.approve')}
                            </button>
                            <button className="btn btn-ghost btn-sm danger" onClick={() => setDecision({ row: r, kind: 'REJECT' })}>
                              <IconBan width={13} height={13} /> {t('rms.signoff.reject')}
                            </button>
                            {r.tempAllowStatus === 'NONE' || r.tempAllowStatus === 'EXPIRED' ? (
                              <button className="btn btn-ghost btn-sm" onClick={() => setTempRow(r)}>
                                <IconSync width={13} height={13} /> {t('rms.signoff.temp')}
                              </button>
                            ) : (
                              <button className="btn btn-ghost btn-sm" onClick={() => cancelTemp(r)}>
                                {t('rms.signoff.cancelTemp')}
                              </button>
                            )}
                          </Perms>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconShield width={22} height={22} />
            <b>{t('rms.signoff.empty')}</b>
            <span>{t('rms.signoff.emptyHint')}</span>
          </div>
        )}
      </Panel>

      {showSubmit && (
        <SubmitModal onClose={() => setShowSubmit(false)} onDone={async () => { setShowSubmit(false); await load(); }} />
      )}
      {decision && (
        <DecisionModal row={decision.row} kind={decision.kind}
          onClose={() => setDecision(null)} onDone={async () => { setDecision(null); await load(); }} />
      )}
      {tempRow && (
        <TempAllowModal row={tempRow} onClose={() => setTempRow(null)}
          onDone={async () => { setTempRow(null); await load(); }} />
      )}
      {detailId && <DetailDrawer id={detailId} onClose={() => setDetailId(null)} />}
      {batchResults && (
        <BatchResultModal results={batchResults} onClose={() => setBatchResults(null)} />
      )}
    </div>
  );
}

/** 发起签核：选配方 → 选 DRAFT 版本 → 填原因/路由。 */
function SubmitModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [recipeId, setRecipeId] = useState('');
  const [versions, setVersions] = useState<RecipeVersion[]>([]);
  const [versionId, setVersionId] = useState('');
  const [reason, setReason] = useState('');
  const [route, setRoute] = useState<SignRoute>('SINGLE');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.listRecipes().then(setRecipes).catch(() => setRecipes([]));
  }, []);

  useEffect(() => {
    if (!recipeId) {
      setVersions([]);
      setVersionId('');
      return;
    }
    api.listVersions(recipeId)
      .then((vs) => setVersions(vs.filter((v) => v.status === 'DRAFT')))
      .catch(() => setVersions([]));
  }, [recipeId]);

  async function submit() {
    if (!recipeId || !versionId) {
      setErr(t('rms.signoff.pickRequired'));
      return;
    }
    if (!reason.trim()) {
      setErr(t('rms.signoff.reasonRequired'));
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
          <h3><IconShield width={15} height={15} /> {t('rms.signoff.submit')}</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="field">
            <label className="field-label">{t('rms.recipe.title')} *</label>
            <select className="select-sm" style={{ width: '100%' }} value={recipeId}
              onChange={(e) => setRecipeId(e.target.value)}>
              <option value="">{t('rms.signoff.pickRecipe')}</option>
              {recipes.map((r) => <option key={r.id} value={r.id}>{r.code} · {r.name}</option>)}
            </select>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.signoff.draftVersion')} *</label>
            <select className="select-sm" style={{ width: '100%' }} value={versionId}
              onChange={(e) => setVersionId(e.target.value)} disabled={!recipeId}>
              <option value="">{t('rms.signoff.pickVersion')}</option>
              {versions.map((v) => (
                <option key={v.id} value={v.id}>v{v.versionNo} · {v.changeSummary ?? ''}</option>
              ))}
            </select>
            {recipeId && versions.length === 0 && (
              <div className="dim" style={{ fontSize: 12, marginTop: 4 }}>{t('rms.signoff.noDraft')}</div>
            )}
          </div>
          <div className="field">
            <label className="field-label">{t('rms.signoff.reason')} *</label>
            <textarea className="text-input" rows={2} placeholder={t('rms.signoff.reasonPh')}
              value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>
          <div className="field">
            <label className="field-label">{t('rms.signoff.route')}</label>
            <select className="select-sm" style={{ width: '100%' }} value={route}
              onChange={(e) => setRoute(e.target.value as SignRoute)}>
              <option value="SINGLE">{t('rms.signoff.route.SINGLE')}</option>
              <option value="COUNTERSIGN">{t('rms.signoff.route.COUNTERSIGN')}</option>
              <option value="ANY">{t('rms.signoff.route.ANY')}</option>
            </select>
            <div className="dim" style={{ fontSize: 11, marginTop: 4 }}>{t('rms.signoff.routeHint')}</div>
          </div>
          <div className="alert info" style={{ marginTop: 4 }}>
            <IconList width={15} height={15} />
            <span>{t('rms.signoff.diffWillGenerate')}</span>
          </div>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{t('rms.common.cancel')}</button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>{t('rms.common.confirm')}</button>
        </div>
      </div>
    </div>
  );
}

/** 审批决定：通过 / 拒绝 + 意见。 */
function DecisionModal({ row, kind, onClose, onDone }: {
  row: Signoff; kind: 'APPROVE' | 'REJECT'; onClose: () => void; onDone: () => void;
}) {
  const { t } = useTranslation();
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit() {
    setBusy(true);
    setErr('');
    try {
      if (kind === 'APPROVE') await api.approveSignoff(row.id, comment || undefined);
      else await api.rejectSignoff(row.id, comment || undefined);
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
          <h3>
            <IconShield width={15} height={15} />{' '}
            {kind === 'APPROVE' ? t('rms.signoff.approve') : t('rms.signoff.reject')}
            {' · '}{row.recipeCode} v{row.versionNo}
          </h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          {row.reason && (
            <div className="field">
              <label className="field-label">{t('rms.signoff.reason')}</label>
              <div className="dim">{row.reason}</div>
            </div>
          )}
          <div className="field">
            <label className="field-label">{t('rms.signoff.comment')}</label>
            <textarea className="text-input" rows={3} value={comment} onChange={(e) => setComment(e.target.value)} />
          </div>
          {kind === 'APPROVE' ? (
            <div className="alert warn"><IconAlert width={15} height={15} /><span>{t('rms.signoff.approveHint')}</span></div>
          ) : (
            <div className="alert warn"><IconAlert width={15} height={15} /><span>{t('rms.signoff.decisionHint')}</span></div>
          )}
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{t('rms.common.cancel')}</button>
          <button className={'btn ' + (kind === 'APPROVE' ? 'btn-primary' : 'btn-primary danger')}
            disabled={busy} onClick={submit}>{t('rms.common.confirm')}</button>
        </div>
      </div>
    </div>
  );
}

/** 预设限时使用（Req 20）。 */
function TempAllowModal({ row, onClose, onDone }: { row: Signoff; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const now = new Date();
  const [from, setFrom] = useState(toLocalInput(now));
  const [to, setTo] = useState(toLocalInput(new Date(now.getTime() + 4 * 3600_000)));
  const [scope, setScope] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit() {
    if (!to) {
      setErr(t('rms.signoff.tempDialog.toRequired'));
      return;
    }
    if (!reason.trim()) {
      setErr(t('rms.signoff.tempDialog.reasonRequired'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      await api.presetTempAllow(row.id, {
        from: from || null, to, scope: scope || null, reason: reason.trim(),
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
          <h3><IconSync width={15} height={15} /> {t('rms.signoff.tempDialog.title')} · {row.recipeCode} v{row.versionNo}</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="alert info"><IconAlert width={15} height={15} /><span>{t('rms.signoff.tempDialog.hint')}</span></div>
          <div className="field">
            <label className="field-label">{t('rms.signoff.tempDialog.from')}</label>
            <input type="datetime-local" className="text-input" value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="field">
            <label className="field-label">{t('rms.signoff.tempDialog.to')} *</label>
            <input type="datetime-local" className="text-input" value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
          <div className="field">
            <label className="field-label">{t('rms.signoff.tempDialog.scope')}</label>
            <input className="text-input" placeholder={t('rms.signoff.tempDialog.scopePh')}
              value={scope} onChange={(e) => setScope(e.target.value)} />
          </div>
          <div className="field">
            <label className="field-label">{t('rms.signoff.tempDialog.reason')} *</label>
            <input className="text-input" placeholder={t('rms.signoff.tempDialog.reasonPh')}
              value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{t('rms.common.cancel')}</button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>{t('rms.common.confirm')}</button>
        </div>
      </div>
    </div>
  );
}

/** 批量决策逐项结果（Req 3 部分成功语义）。 */
function BatchResultModal({ results, onClose }: { results: BatchResult[]; onClose: () => void }) {
  const { t } = useTranslation();
  const ok = results.filter((r) => r.ok).length;
  const fail = results.length - ok;
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{t('rms.signoff.batchResultTitle')}</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="kv-inline" style={{ marginBottom: 8 }}>
            <span><b>{t('rms.signoff.batchOk')}</b>{ok}</span>
            <span><b>{t('rms.signoff.batchFail')}</b>{fail}</span>
          </div>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr><th style={{ width: 90 }}>{t('rms.common.status')}</th><th>{t('rms.audit.th.detail')}</th></tr>
              </thead>
              <tbody>
                {results.map((r, i) => (
                  <tr key={r.id + i}>
                    <td>
                      <span className="status">
                        <i className={'led ' + (r.ok ? 'ok' : 'err')} />
                        <b>{r.ok ? t('rms.signoff.batchOk') : t('rms.signoff.batchFail')}</b>
                      </span>
                    </td>
                    <td className="dim">{r.message ?? r.id}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        <div className="modal-foot">
          <button className="btn btn-primary" onClick={onClose}>{t('rms.common.close')}</button>
        </div>
      </div>
    </div>
  );
}

/** 签核单详情抽屉：基本信息 + 参数级差异附件 + 事件时间线 + 电子签证据链。 */
function DetailDrawer({ id, onClose }: { id: string; onClose: () => void }) {
  const { t } = useTranslation();
  const [detail, setDetail] = useState<SignoffDetail | null>(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.getSignoff(id).then(setDetail).catch((e) => setErr(e?.message || 'error'));
  }, [id]);

  const changed: DiffItem[] = (detail?.paramDiff ?? []).filter((d) => d.changed);

  return (
    <Drawer
      title={<><IconShield width={15} height={15} /> {t('rms.signoff.detail.title')}</>}
      onClose={onClose}
    >
      {err && <div className="alert err"><IconAlert width={15} height={15} /><span>{err}</span></div>}
      {!detail ? (
        <div className="dim">{t('rms.common.loading')}</div>
      ) : (
        <>
          <dl className="kv">
            <dt>{t('rms.recipe.title')}</dt>
            <dd className="mono">{detail.recipeCode} · {detail.recipeName}</dd>
            <dt>{t('rms.recipe.version')}</dt>
            <dd className="mono">v{detail.versionNo}</dd>
            <dt>{t('rms.common.status')}</dt>
            <dd><span className={statusTone(detail.status)}>{t('rms.signoff.' + detail.status.toLowerCase())}</span></dd>
            <dt>{t('rms.signoff.route')}</dt>
            <dd>{t('rms.signoff.route.' + detail.signRoute)}</dd>
            <dt>{t('rms.signoff.reason')}</dt>
            <dd>{detail.reason ?? t('rms.signoff.noReason')}</dd>
            <dt>{t('rms.signoff.initiator')}</dt>
            <dd className="mono">{detail.initiator ?? t('rms.common.dash')} · {detail.createTime ?? ''}</dd>
            {detail.approver && (
              <>
                <dt>{t('rms.signoff.approver')}</dt>
                <dd className="mono">{detail.approver} · {detail.decidedTime ?? ''}</dd>
              </>
            )}
            {detail.decisionComment && (
              <>
                <dt>{t('rms.signoff.comment')}</dt>
                <dd>{detail.decisionComment}</dd>
              </>
            )}
            <dt>{t('rms.signoff.temp')}</dt>
            <dd>
              <span className={tempTone(detail.tempAllowStatus)}>
                {t('rms.signoff.tempStatus.' + detail.tempAllowStatus)}
              </span>
              {detail.tempAllowTo && <span className="dim"> · {t('rms.signoff.tempUntil', { t: detail.tempAllowTo })}</span>}
              {detail.tempAllowScope && <div className="dim">{detail.tempAllowScope}</div>}
              {detail.tempAllowReason && <div className="dim">{detail.tempAllowReason}</div>}
            </dd>
            {detail.eSignHash && (
              <>
                <dt>{t('rms.signoff.eSign')}</dt>
                <dd className="mono" style={{ wordBreak: 'break-all', fontSize: 11 }}>{detail.eSignHash}</dd>
              </>
            )}
          </dl>

          <div className="sub-title">{t('rms.signoff.paramDiff')}</div>
          {detail.paramDiff.length === 0 ? (
            <div className="empty" style={{ padding: '18px 0' }}>
              <IconList width={18} height={18} />
              <b>{t('rms.signoff.diffNone')}</b>
            </div>
          ) : (
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('rms.compare.field')}</th>
                    <th>{t('rms.compare.leftValue')}</th>
                    <th>{t('rms.compare.rightValue')}</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.paramDiff.map((it) => (
                    <tr key={it.field} className={it.changed ? 'diff-changed' : undefined}>
                      <td className="mono">{fieldLabel(t, it.field)}</td>
                      <td className="mono" style={{ wordBreak: 'break-all' }}>{it.left ?? t('rms.common.dash')}</td>
                      <td className="mono" style={{ wordBreak: 'break-all' }}>
                        {it.changed ? <b>{it.right ?? t('rms.common.dash')}</b> : (it.right ?? t('rms.common.dash'))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="dim" style={{ padding: '6px 2px', fontSize: 11 }}>
                {changed.length} / {detail.paramDiff.length}
              </div>
            </div>
          )}

          <div className="sub-title">{t('rms.signoff.timeline')}</div>
          <div className="timeline">
            {detail.timeline.map((e, i) => (
              <div className="timeline-item" key={e.eventType + i}>
                <span className={eventDot(e.eventType)} />
                <div className="timeline-title">
                  {t('rms.signoff.event.' + e.eventType, { defaultValue: e.eventType })}
                  {e.actor && <span className="dim mono" style={{ fontWeight: 400 }}> · {e.actor}</span>}
                </div>
                <div className="timeline-time">{e.occurredAt ?? ''}</div>
                {(e.detail || e.comment) && (
                  <div className="timeline-detail">{e.detail ?? e.comment}</div>
                )}
              </div>
            ))}
          </div>
        </>
      )}
    </Drawer>
  );
}
