import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Panel from '@/components/Panel';
import Modal from '@/components/Modal';
import DetailModal from '@/components/DetailModal';
import { IconInbox, IconCheck, IconReturn, IconAddUser, IconSwap, IconRefresh, IconSearch } from '@/components/Icons';
import { listTodo, completeTask, returnTask, addSignTask, reassignTask } from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { toast } from '@/store/toastStore';
import { fmtDate, fmtOverdue } from '@/lib/format';
import type { TaskView } from '@/types';

type ModalType = 'complete' | 'return' | 'addsign' | 'reassign';

export default function TodoPage() {
  const { t } = useTranslation();
  const { userId } = useIdentityStore();
  const [tasks, setTasks] = useState<TaskView[]>([]);
  const [loading, setLoading] = useState(true);
  const [active, setActive] = useState<{ type: ModalType; task: TaskView } | null>(null);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  /** 关键字过滤（标题/业务单号/节点名/办理人），客户端过滤即可 */
  const [q, setQ] = useState('');

  const filtered = tasks.filter((tk) => {
    if (!q.trim()) return true;
    const kw = q.trim().toLowerCase();
    return [tk.title, tk.businessKey, tk.name, tk.assignee]
      .some((v) => v && v.toLowerCase().includes(kw));
  });

  async function load() {
    setLoading(true);
    try {
      setTasks(await listTodo());
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

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
            <button className="icon-btn" onClick={load} title={t('common.refresh')} aria-label={t('common.refresh')}>
              <IconRefresh width={16} height={16} />
            </button>
          </>
        }
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
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
                  <td colSpan={7} className="dim" style={{ textAlign: 'center' }}>{t('common.loading')}</td>
                </tr>
              )}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td colSpan={7}>
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
                    {tk.name}
                    {tk.overdueMinutes != null && (
                      <span className="tag err" style={{ marginLeft: 6 }}>
                        {t('todo.overdue')} {fmtOverdue(tk.overdueMinutes)}
                      </span>
                    )}
                  </td>
                  <td>{tk.title || '—'}</td>
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
                      <button className="btn btn-sm" onClick={() => setActive({ type: 'complete', task: tk })}>
                        <IconCheck width={13} height={13} /> {t('todo.approve')}/{t('todo.reject')}
                      </button>
                      <button className="btn btn-ghost btn-sm" onClick={() => setDetailId(tk.processInstanceId)} title={tk.title || tk.businessKey}>
                        <IconSearch width={13} height={13} /> {t('common.detail')}
                      </button>
                      <button className="btn btn-ghost btn-sm" onClick={() => setActive({ type: 'return', task: tk })}>
                        <IconReturn width={13} height={13} /> {t('todo.return')}
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
      </Panel>

      {detailId && <DetailModal processInstanceId={detailId} onClose={() => setDetailId(null)} />}

      {active?.type === 'complete' && (
        <CompleteModal task={active.task} busy={busy} onClose={() => setActive(null)} onSubmit={(r) => run(() => completeTask(active.task.taskId, { ...r, actor: userId }))} />
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

function CompleteModal({ task, busy, onClose, onSubmit }: { task: TaskView; busy: boolean; onClose: () => void; onSubmit: (r: import('@/types').CompleteTaskRequest) => void }) {
  const { t } = useTranslation();
  const [outcome, setOutcome] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [comment, setComment] = useState('');
  return (
    <Modal
      title={`${t('todo.completeTitle')} · ${task.name}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn-sm" disabled={busy} onClick={() => onSubmit({ outcome, comment, actor: '' })}>
            <IconCheck width={14} height={14} /> {t('common.submit')}
          </button>
        </>
      }
    >
      <div className="field">
        <label className="field-label">{t('todo.outcome')}</label>
        <div className="row">
          <label className="check"><input type="radio" name="outcome" checked={outcome === 'APPROVE'} onChange={() => setOutcome('APPROVE')} /> {t('todo.outcome.approve')}</label>
          <label className="check"><input type="radio" name="outcome" checked={outcome === 'REJECT'} onChange={() => setOutcome('REJECT')} /> {t('todo.outcome.reject')}</label>
        </div>
      </div>
      <div className="field">
        <label className="field-label">{t('todo.comment')}</label>
        <textarea rows={3} value={comment} onChange={(e) => setComment(e.target.value)} />
      </div>
    </Modal>
  );
}

function ReturnModal({ task, busy, onClose, onSubmit }: { task: TaskView; busy: boolean; onClose: () => void; onSubmit: (r: import('@/types').ReturnRequest) => void }) {
  const { t } = useTranslation();
  const [target, setTarget] = useState('');
  const [reason, setReason] = useState('');
  return (
    <Modal
      title={`${t('todo.returnTitle')} · ${task.name}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn-sm" disabled={busy} onClick={() => onSubmit({ targetActivityId: target.trim() || null, reason, actor: '' })}>
            <IconReturn width={14} height={14} /> {t('common.submit')}
          </button>
        </>
      }
    >
      <div className="field">
        <label className="field-label">{t('todo.returnTarget')}</label>
        <input className="mono" value={target} onChange={(e) => setTarget(e.target.value)} placeholder="(auto = previous node)" />
      </div>
      <div className="field">
        <label className="field-label">{t('todo.returnReason')}</label>
        <textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
      </div>
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
