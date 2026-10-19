import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Panel from '@/components/Panel';
import Modal from '@/components/Modal';
import DetailModal from '@/components/DetailModal';
import CompleteModal from '@/components/CompleteModal';
import ResubmitModal from '@/components/ResubmitModal';
import { IconInbox, IconCheck, IconReturn, IconAddUser, IconSwap, IconRefresh, IconSearch, IconUser, IconWeCom } from '@/components/Icons';
import {
  listTodoPaged, completeTask, returnTask, getReturnTargets, addSignTask, reassignTask,
  batchComplete, delegateTask, resolveTask, createWeComLink, type PageResult,
} from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { toast } from '@/store/toastStore';
import { fmtDate, fmtOverdue } from '@/lib/format';
import { DisplayFields } from '@/components/DisplayFields';
import type { TaskView } from '@/types';

type ModalType = 'complete' | 'return' | 'addsign' | 'reassign' | 'delegate';

/** 重提态任务名（与后端 ApprovalReturnService.RESUBMIT_TASK_NAME 对齐）。 */
const RESUBMIT_TASK_NAME = '修改并重新提交';

const PAGE_SIZE = 10;

export default function TodoPage() {
  const { t } = useTranslation();
  const { userId } = useIdentityStore();
  const [paged, setPaged] = useState<PageResult<TaskView> | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [active, setActive] = useState<{ type: ModalType; task: TaskView } | null>(null);
  const [detailId, setDetailId] = useState<string | null>(null);
  /** 重提态任务的重新提交弹窗（按流程实例）。 */
  const [resubmitId, setResubmitId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  /** 勾选批量通过 */
  const [checked, setChecked] = useState<Set<string>>(new Set());
  /** 关键字过滤（标题/业务单号/节点名/办理人），客户端过滤即可 */
  const [q, setQ] = useState('');

  const tasks = paged?.records ?? [];
  const filtered = tasks.filter((tk) => {
    if (!q.trim()) return true;
    const kw = q.trim().toLowerCase();
    return [tk.title, tk.businessKey, tk.name, tk.assignee]
      .some((v) => v && v.toLowerCase().includes(kw));
  });
  const totalPages = paged ? Math.max(1, Math.ceil(paged.total / PAGE_SIZE)) : 1;

  async function load(p = page) {
    setLoading(true);
    try {
      const res = await listTodoPaged(p + 1, PAGE_SIZE);
      setPaged(res);
      setChecked(new Set());
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    load(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  function goto(p: number) {
    setPage(p);
    load(p);
  }

  async function run(fn: () => Promise<void>) {
    setBusy(true);
    try {
      await fn();
      setActive(null);
      toast(t('common.confirm') + ' ✓', 'ok');
      await load();
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setBusy(false);
    }
  }

  function toggleCheck(taskId: string) {
    setChecked((prev) => {
      const next = new Set(prev);
      if (next.has(taskId)) next.delete(taskId);
      else next.add(taskId);
      return next;
    });
  }

  async function copyWeComLink(taskId: string) {
    try {
      const { link } = await createWeComLink(taskId, userId);
      const url = `${location.origin}${link}`;
      await navigator.clipboard.writeText(url).catch(() => {});
      toast(t('wecom.linkCopied'), 'ok');
    } catch (e) {
      toast((e as Error).message, 'err');
    }
  }

  async function runBatch() {
    if (checked.size === 0) return;
    if (!window.confirm(t('todo.batchConfirm').replace('{n}', String(checked.size)))) return;
    setBusy(true);
    try {
      const results = await batchComplete({ taskIds: [...checked], outcome: 'APPROVE', actor: userId });
      const fail = results.filter((r) => !r.ok);
      if (fail.length === 0) {
        toast(t('todo.batchOk').replace('{n}', String(results.length)), 'ok');
      } else {
        toast(t('todo.batchPartial').replace('{ok}', String(results.length - fail.length)).replace('{fail}', String(fail.length)), 'warn');
      }
      setChecked(new Set());
      await load();
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <Panel
        title={t('todo.title')}
        sub={t('todo.sub')}
        actions={
          <>
            <span className="search-inline">
              <IconSearch width={13} height={13} />
              <input
                className="text-input"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder={t('common.searchPlaceholder')}
              />
            </span>
            {checked.size > 0 && (
              <button className="btn btn-sm" disabled={busy} onClick={runBatch}>
                <IconCheck width={13} height={13} /> {t('todo.batchApprove')} ({checked.size})
              </button>
            )}
            <button className="icon-btn" onClick={() => load()} title={t('common.refresh')} aria-label={t('common.refresh')}>
              <IconRefresh width={16} height={16} />
            </button>
          </>
        }
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th style={{ width: 32 }} />
                <th>{t('todo.colName')}</th>
                <th>{t('common.colTitle')}</th>
                <th>{t('todo.colBusinessKey')}</th>
                <th>{t('todo.colAssignee')}</th>
                <th>{t('todo.colCandidates')}</th>
                <th>{t('todo.colCreated')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={8} className="dim" style={{ textAlign: 'center' }}>{t('common.loading')}</td>
                </tr>
              )}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td colSpan={8}>
                    <div className="empty">
                      <IconInbox width={26} height={26} />
                      <span>{t('todo.empty')}</span>
                    </div>
                  </td>
                </tr>
              )}
              {filtered.map((tk) => (
                <tr key={tk.taskId}>
                  <td>
                    {tk.delegationState !== 'PENDING' && (
                      <input type="checkbox" checked={checked.has(tk.taskId)} onChange={() => toggleCheck(tk.taskId)} />
                    )}
                  </td>
                  <td>
                    {tk.name}
                    {tk.name === RESUBMIT_TASK_NAME && (
                      <span className="tag warn" style={{ marginLeft: 6 }}>{t('init.awaitingResubmit')}</span>
                    )}
                    {tk.round != null && tk.round > 1 && (
                      <span className="tag info" style={{ marginLeft: 6 }}>{t('init.roundBadge', { n: tk.round })}</span>
                    )}
                    {tk.delegationState === 'PENDING' && (
                      <span className="tag warn" style={{ marginLeft: 6 }}>{t('todo.delegatedToMe')}</span>
                    )}
                    {tk.overdueMinutes != null && (
                      <span className="tag err" style={{ marginLeft: 6 }}>
                        {t('todo.overdue')} {fmtOverdue(tk.overdueMinutes)}
                      </span>
                    )}
                  </td>
                  <td>
                    {tk.title || '—'}
                    <DisplayFields fields={tk.displayFields} />
                  </td>
                  <td className="mono">{tk.businessKey}</td>
                  <td>{tk.assignee ?? '—'}</td>
                  <td className="tag-list">
                    {tk.candidateUsers.map((c) => (
                      <span key={c} className="tag">{c}</span>
                    ))}
                    {tk.candidateUsers.length === 0 && '—'}
                  </td>
                  <td className="mono">{fmtDate(tk.createdAt)}</td>
                  <td>
                    <div className="cell-actions">
                      {tk.name === RESUBMIT_TASK_NAME ? (
                        <button className="btn btn-sm" onClick={() => setResubmitId(tk.processInstanceId)}>
                          <IconRefresh width={13} height={13} /> {t('init.resubmit')}
                        </button>
                      ) : tk.delegationState === 'PENDING' ? (
                        <button className="btn btn-sm" onClick={() => run(() => resolveTask(tk.taskId, { actor: userId }))}>
                          <IconReturn width={13} height={13} /> {t('todo.resolveBack')}
                        </button>
                      ) : (
                        <button className="btn btn-sm" onClick={() => setActive({ type: 'complete', task: tk })}>
                          <IconCheck width={13} height={13} /> {t('todo.approve')}/{t('todo.reject')}
                        </button>
                      )}
                      <button className="btn btn-ghost btn-sm" onClick={() => setDetailId(tk.processInstanceId)} title={tk.title || tk.businessKey}>
                        <IconSearch width={13} height={13} /> {t('common.detail')}
                      </button>
                      <button className="btn btn-ghost btn-sm" onClick={() => setActive({ type: 'delegate', task: tk })}>
                        <IconUser width={13} height={13} /> {t('todo.delegate')}
                      </button>
                      <button className="btn btn-ghost btn-sm" onClick={() => setActive({ type: 'return', task: tk })}>
                        <IconReturn width={13} height={13} /> {t('todo.return')}
                      </button>
                      <button className="btn btn-ghost btn-sm" onClick={() => copyWeComLink(tk.taskId)} title={t('wecom.copyLink')}>
                        <IconWeCom width={13} height={13} /> {t('wecom.copyLink')}
                      </button>
                      <button className="btn btn-ghost btn-sm" onClick={() => setActive({ type: 'addsign', task: tk })}>
                        <IconAddUser width={13} height={13} /> {t('todo.addSign')}
                      </button>
                      <button className="btn btn-ghost btn-sm" onClick={() => setActive({ type: 'reassign', task: tk })}>
                        <IconSwap width={13} height={13} /> {t('todo.reassign')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {paged && paged.total > PAGE_SIZE && (
          <div className="pager">
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => goto(page - 1)}>
              {t('page.prev')}
            </button>
            <span className="dim">{t('page.info').replace('{page}', String(page + 1)).replace('{total}', String(totalPages))}</span>
            <button className="btn btn-ghost btn-sm" disabled={page >= totalPages - 1} onClick={() => goto(page + 1)}>
              {t('page.next')}
            </button>
          </div>
        )}
      </Panel>

      {detailId && <DetailModal processInstanceId={detailId} onClose={() => setDetailId(null)} onChanged={() => load()} />}

      {resubmitId && (
        <ResubmitModal
          processInstanceId={resubmitId}
          onClose={() => setResubmitId(null)}
          onDone={() => load()}
        />
      )}

      {active?.type === 'complete' && (
        <CompleteModal task={active.task} busy={busy} onClose={() => setActive(null)} onSubmit={(r) => run(() => completeTask(active.task.taskId, { ...r, actor: userId }))} />
      )}
      {active?.type === 'delegate' && (
        <DelegateModal task={active.task} busy={busy} onClose={() => setActive(null)} onSubmit={(assignee, reason) => run(() => delegateTask(active.task.taskId, { assignee, reason, actor: userId }))} />
      )}
      {active?.type === 'return' && (
        <ReturnModal task={active.task} busy={busy} onClose={() => setActive(null)} onSubmit={(r) => run(() => returnTask(active.task.taskId, { ...r, actor: userId }))} />
      )}
      {active?.type === 'addsign' && (
        <AddSignModal task={active.task} busy={busy} onClose={() => setActive(null)} onSubmit={(r) => run(() => addSignTask(active.task.taskId, { ...r, actor: userId }))} />
      )}
      {active?.type === 'reassign' && (
        <ReassignModal task={active.task} busy={busy} onClose={() => setActive(null)} onSubmit={(r) => run(() => reassignTask(active.task.taskId, { ...r, actor: userId }))} />
      )}
    </div>
  );
}

function DelegateModal({ task, busy, onClose, onSubmit }: { task: TaskView; busy: boolean; onClose: () => void; onSubmit: (assignee: string, reason: string) => void }) {
  const { t } = useTranslation();
  const [assignee, setAssignee] = useState('');
  const [reason, setReason] = useState('');
  return (
    <Modal
      title={`${t('todo.delegateTitle')} · ${task.name}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn-sm" disabled={busy || !assignee.trim()} onClick={() => onSubmit(assignee.trim(), reason)}>
            <IconUser width={14} height={14} /> {t('common.submit')}
          </button>
        </>
      }
    >
      <div className="field">
        <label className="field-label">{t('todo.delegateUser')}</label>
        <input value={assignee} onChange={(e) => setAssignee(e.target.value)} placeholder="carol" />
      </div>
      <div className="field">
        <label className="field-label">{t('todo.delegateReason')}</label>
        <textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
      </div>
      <p className="dim">{t('todo.delegateHint')}</p>
    </Modal>
  );
}

function ReturnModal({ task, busy, onClose, onSubmit }: { task: TaskView; busy: boolean; onClose: () => void; onSubmit: (r: import('@/types').ReturnRequest) => void }) {
  const { t } = useTranslation();
  const [targets, setTargets] = useState<import('@/types').ReturnTargetsView | null>(null);
  const [loadErr, setLoadErr] = useState('');
  const [mode, setMode] = useState<'previous' | 'initiator' | 'specified'>('previous');
  const [specified, setSpecified] = useState('');
  const [reason, setReason] = useState('');

  useEffect(() => {
    getReturnTargets(task.taskId)
      .then(setTargets)
      .catch((e) => setLoadErr((e as Error).message));
  }, [task.taskId]);

  const maxReached = targets != null && targets.currentRound >= targets.maxRounds;
  const targetId = targets == null ? null
    : mode === 'previous' ? targets.previousNodeId
      : mode === 'initiator' ? targets.initiatorNodeId
        : specified || null;
  const canSubmit = targets != null && !maxReached && !!targetId && !!reason.trim();

  return (
    <Modal
      title={`${t('todo.returnTitle')} · ${task.name}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn-sm" disabled={busy || !canSubmit} onClick={() => onSubmit({ targetActivityId: targetId, reason: reason.trim(), actor: '' })}>
            <IconReturn width={14} height={14} /> {t('common.submit')}
          </button>
        </>
      }
    >
      {loadErr && <p className="dim">{loadErr}</p>}
      {targets && (
        <>
          <p className="dim" style={{ marginTop: 0 }}>
            {t('todo.returnRound', { n: targets.currentRound, max: targets.maxRounds })}
          </p>
          {maxReached && (
            <p style={{ color: 'var(--err, #d03050)' }}>{t('todo.returnMaxReached', { max: targets.maxRounds })}</p>
          )}
          <div className="field">
            <label className="field-label">{t('todo.returnMode')}</label>
            <label className="radio-row">
              <input type="radio" name="return-mode" checked={mode === 'previous'} disabled={!targets.previousNodeId} onChange={() => setMode('previous')} />
              <span>{t('todo.returnModePrevious')}{targets.previousNodeName ? `（${targets.previousNodeName}）` : ''}</span>
            </label>
            <label className="radio-row">
              <input type="radio" name="return-mode" checked={mode === 'initiator'} disabled={targets.initiatorNodeMulti} onChange={() => setMode('initiator')} />
              <span>{t('todo.returnModeInitiator')}</span>
            </label>
            {targets.initiatorNodeMulti && (
              <p className="dim" style={{ marginTop: 0 }}>{t('todo.returnMultiInitiatorHint')}</p>
            )}
            <label className="radio-row">
              <input type="radio" name="return-mode" checked={mode === 'specified'} disabled={targets.historicNodes.length === 0} onChange={() => setMode('specified')} />
              <span>{t('todo.returnModeSpecified')}</span>
            </label>
            {mode === 'specified' && (
              <select value={specified} onChange={(e) => setSpecified(e.target.value)}>
                <option value="">—</option>
                {targets.historicNodes.map((n) => (
                  <option key={n.id} value={n.id}>{n.name}</option>
                ))}
              </select>
            )}
          </div>
          {mode !== 'initiator' && targets.parallelActive && (
            <p className="dim">{t('todo.returnParallelHint')}</p>
          )}
          <div className="field">
            <label className="field-label">{t('todo.returnReason')}</label>
            <textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>
        </>
      )}
      {!targets && !loadErr && <p className="dim">{t('common.loading')}</p>}
    </Modal>
  );
}

function AddSignModal({ task, busy, onClose, onSubmit }: { task: TaskView; busy: boolean; onClose: () => void; onSubmit: (r: import('@/types').AddSignRequest) => void }) {
  const { t } = useTranslation();
  const [assignee, setAssignee] = useState('');
  const [type, setType] = useState<'AFTER' | 'BEFORE'>('AFTER');
  const [reason, setReason] = useState('');
  return (
    <Modal
      title={`${t('todo.addSignTitle')} · ${task.name}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn-sm" disabled={busy || !assignee.trim()} onClick={() => onSubmit({ assignee: assignee.trim(), type, reason, actor: '' })}>
            <IconAddUser width={14} height={14} /> {t('common.submit')}
          </button>
        </>
      }
    >
      <div className="field">
        <label className="field-label">{t('todo.addSignUser')}</label>
        <input value={assignee} onChange={(e) => setAssignee(e.target.value)} placeholder="dave" />
      </div>
      <div className="field">
        <label className="field-label">{t('todo.addSignType')}</label>
        <select value={type} onChange={(e) => setType(e.target.value as 'AFTER' | 'BEFORE')}>
          <option value="AFTER">{t('todo.addSign.after')}</option>
          <option value="BEFORE">{t('todo.addSign.before')}</option>
        </select>
      </div>
      <div className="field">
        <label className="field-label">{t('todo.addSignReason')}</label>
        <textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
      </div>
    </Modal>
  );
}

function ReassignModal({ task, busy, onClose, onSubmit }: { task: TaskView; busy: boolean; onClose: () => void; onSubmit: (r: import('@/types').ReassignRequest) => void }) {
  const { t } = useTranslation();
  const [newAssignee, setNewAssignee] = useState('');
  const [reason, setReason] = useState('');
  return (
    <Modal
      title={`${t('todo.reassignTitle')} · ${task.name}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn-sm" disabled={busy || !newAssignee.trim()} onClick={() => onSubmit({ newAssignee: newAssignee.trim(), reason, actor: '' })}>
            <IconSwap width={14} height={14} /> {t('common.submit')}
          </button>
        </>
      }
    >
      <div className="field">
        <label className="field-label">{t('todo.reassignUser')}</label>
        <input value={newAssignee} onChange={(e) => setNewAssignee(e.target.value)} placeholder="bob" />
      </div>
      <div className="field">
        <label className="field-label">{t('todo.reassignReason')}</label>
        <textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
      </div>
    </Modal>
  );
}
