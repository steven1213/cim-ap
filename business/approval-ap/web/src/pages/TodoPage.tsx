import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimModal,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import DetailModal from '@/components/DetailModal';
import CompleteModal from '@/components/CompleteModal';
import ResubmitModal from '@/components/ResubmitModal';
import { DisplayFields } from '@/components/DisplayFields';
import {
  IconAddUser,
  IconCheck,
  IconRefresh,
  IconReturn,
  IconSearch,
  IconSwap,
  IconUser,
  IconWeCom,
} from '@/components/Icons';
import {
  addSignTask,
  batchComplete,
  completeTask,
  createWeComLink,
  delegateTask,
  getReturnTargets,
  listTodo,
  reassignTask,
  resolveTask,
  returnTask,
} from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { fmtDate, fmtOverdue } from '@/lib/format';
import type { AddSignRequest, ReassignRequest, ReturnRequest, TaskView } from '@/types';

type ModalType = 'complete' | 'return' | 'addsign' | 'reassign' | 'delegate';

/** 重提态任务名（与后端 ApprovalReturnService.RESUBMIT_TASK_NAME 对齐）。 */
const RESUBMIT_TASK_NAME = '修改并重新提交';

const PAGE_SIZE = 20;

export default function TodoPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const { userId } = useIdentityStore();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(PAGE_SIZE);
  const [q, setQ] = useState('');
  const [selected, setSelected] = useState<string[]>([]);
  const [active, setActive] = useState<{ type: ModalType; task: TaskView } | null>(null);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [resubmitId, setResubmitId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  /** 全量取回 + 本地分页（与已办页同口径，理由见 DonePage 注释）。 */
  const todoRes = useResource<TaskView[]>(({ signal }) => listTodo(undefined, undefined, { signal }), {
    deps: [userId],
  });

  const all = todoRes.data ?? [];
  const filtered = useMemo(() => {
    const kw = q.trim().toLowerCase();
    if (!kw) return all;
    return all.filter((tk) =>
      [tk.title, tk.businessKey, tk.name, tk.assignee].some((v) => v && v.toLowerCase().includes(kw)),
    );
  }, [all, q]);

  const pageRows = filtered.slice((page - 1) * size, page * size);

  // 刷新后清空勾选：任务可能已经不在了，留着旧 key 会让「批量通过 3 条」通过 0 条
  useEffect(() => {
    setSelected([]);
  }, [todoRes.lastUpdated]);

  async function run(fn: () => Promise<void>, okMsg?: string) {
    setBusy(true);
    try {
      await fn();
      setActive(null);
      toast.success(okMsg ?? t('common.operationOk'));
      todoRes.refresh();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function copyWeComLink(taskId: string) {
    try {
      const { link } = await createWeComLink(taskId, userId);
      await navigator.clipboard.writeText(`${location.origin}${link}`).catch(() => {});
      toast.success(t('wecom.linkCopied'));
    } catch (e) {
      toast.error((e as Error).message);
    }
  }

  async function runBatch() {
    if (selected.length === 0) return;
    const ok = await confirm({
      title: t('todo.batchApprove'),
      content: t('todo.batchConfirm').replace('{n}', String(selected.length)),
      okKey: 'todo.batchApprove',
    });
    if (!ok) return;
    setBusy(true);
    try {
      const results = await batchComplete({ taskIds: selected, outcome: 'APPROVE', actor: userId });
      const fail = results.filter((r) => !r.ok);
      if (fail.length === 0) {
        toast.success(t('todo.batchOk').replace('{n}', String(results.length)));
      } else {
        toast.warning(
          t('todo.batchPartial')
            .replace('{ok}', String(results.length - fail.length))
            .replace('{fail}', String(fail.length)),
        );
      }
      setSelected([]);
      todoRes.refresh();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const columns: Array<DataTableColumn<TaskView>> = [
    {
      key: 'name',
      titleKey: 'todo.colName',
      width: 200,
      render: (tk) => (
        <span className="cell-inline">
          {tk.name}
          {tk.name === RESUBMIT_TASK_NAME && <CimTag tone="warn">{t('init.awaitingResubmit')}</CimTag>}
          {tk.round != null && tk.round > 1 && <CimTag tone="run">{t('init.roundBadge', { n: tk.round })}</CimTag>}
          {tk.delegationState === 'PENDING' && <CimTag tone="warn">{t('todo.delegatedToMe')}</CimTag>}
          {tk.overdueMinutes != null && (
            <CimTag tone="err">
              {t('todo.overdue')} {fmtOverdue(tk.overdueMinutes)}
            </CimTag>
          )}
        </span>
      ),
    },
    {
      key: 'title',
      titleKey: 'common.colTitle',
      ellipsis: true,
      render: (tk) => (
        <>
          {tk.title || '—'}
          <DisplayFields fields={tk.displayFields} />
        </>
      ),
    },
    { key: 'businessKey', titleKey: 'todo.colBusinessKey', width: 160, mono: true },
    { key: 'assignee', titleKey: 'todo.colAssignee', width: 110, render: (tk) => tk.assignee ?? '—' },
    {
      key: 'candidates',
      titleKey: 'todo.colCandidates',
      width: 160,
      render: (tk) => (
        <span className="cell-inline">
          {tk.candidateUsers.length === 0 ? '—' : tk.candidateUsers.map((c) => <CimTag key={c}>{c}</CimTag>)}
        </span>
      ),
    },
    { key: 'createdAt', titleKey: 'todo.colCreated', width: 160, mono: true, render: (tk) => fmtDate(tk.createdAt) },
    {
      key: 'ops',
      titleKey: 'common.actions',
      width: 400,
      render: (tk) => (
        <span className="cell-actions">
          {tk.name === RESUBMIT_TASK_NAME ? (
            <CimButton
              size="small"
              type="primary"
              icon={<IconRefresh width={13} height={13} />}
              onClick={() => setResubmitId(tk.processInstanceId)}
            >
              {t('init.resubmit')}
            </CimButton>
          ) : tk.delegationState === 'PENDING' ? (
            <CimButton
              size="small"
              type="primary"
              icon={<IconReturn width={13} height={13} />}
              disabled={busy}
              onClick={() => run(() => resolveTask(tk.taskId, { actor: userId }))}
            >
              {t('todo.resolveBack')}
            </CimButton>
          ) : (
            <CimButton
              size="small"
              type="primary"
              icon={<IconCheck width={13} height={13} />}
              onClick={() => setActive({ type: 'complete', task: tk })}
            >
              {t('todo.approve')}/{t('todo.reject')}
            </CimButton>
          )}
          <CimButton size="small" icon={<IconSearch width={13} height={13} />} onClick={() => setDetailId(tk.processInstanceId)}>
            {t('common.detail')}
          </CimButton>
          <CimButton size="small" icon={<IconUser width={13} height={13} />} onClick={() => setActive({ type: 'delegate', task: tk })}>
            {t('todo.delegate')}
          </CimButton>
          <CimButton size="small" icon={<IconReturn width={13} height={13} />} onClick={() => setActive({ type: 'return', task: tk })}>
            {t('todo.return')}
          </CimButton>
          <CimButton size="small" icon={<IconAddUser width={13} height={13} />} onClick={() => setActive({ type: 'addsign', task: tk })}>
            {t('todo.addSign')}
          </CimButton>
          <CimButton size="small" icon={<IconSwap width={13} height={13} />} onClick={() => setActive({ type: 'reassign', task: tk })}>
            {t('todo.reassign')}
          </CimButton>
          <CimButton
            size="small"
            icon={<IconWeCom width={13} height={13} />}
            tooltipKey="wecom.copyLink"
            onClick={() => copyWeComLink(tk.taskId)}
          >
            {t('wecom.copyLink')}
          </CimButton>
        </span>
      ),
    },
  ];

  return (
    <Page>
      <Panel titleKey="todo.title" subKey="todo.sub" flush>
        <DataTable<TaskView>
          flat
          columns={columns}
          rows={pageRows}
          rowKey={(tk) => tk.taskId}
          loading={todoRes.loading}
          error={todoRes.error ? todoRes.error.message : null}
          onRetry={todoRes.refresh}
          onRefresh={todoRes.refresh}
          actions={
            selected.length > 0 ? (
              <CimButton
                size="small"
                type="primary"
                icon={<IconCheck width={13} height={13} />}
                disabled={busy}
                onClick={runBatch}
              >
                {t('todo.batchApprove')} ({selected.length})
              </CimButton>
            ) : null
          }
          search={{
            value: q,
            onChange: (v) => {
              setQ(v);
              setPage(1);
            },
            placeholderKey: 'common.searchPlaceholder',
            width: 240,
          }}
          selectedKeys={selected}
          onSelectionChange={setSelected}
          // 受托待归还的任务不能批量通过（要先归还），复选框置灰而不是允许勾选后静默失败
          selectable={(tk) => tk.delegationState !== 'PENDING'}
          pagination={{
            page,
            size,
            total: filtered.length,
            onChange: (p, s) => {
              setPage(s === size ? p : 1);
              setSize(s);
            },
            sizeOptions: [10, 20, 50, 100],
          }}
          emptyTitleKey="todo.empty"
          scrollX="max-content"
        />
      </Panel>

      {detailId && <DetailModal processInstanceId={detailId} onClose={() => setDetailId(null)} onChanged={todoRes.refresh} />}

      {resubmitId && (
        <ResubmitModal processInstanceId={resubmitId} onClose={() => setResubmitId(null)} onDone={todoRes.refresh} />
      )}

      {active?.type === 'complete' && (
        <CompleteModal
          task={active.task}
          busy={busy}
          onClose={() => setActive(null)}
          onSubmit={(r) => run(() => completeTask(active.task.taskId, { ...r, actor: userId }), t('todo.completeOk'))}
        />
      )}
      {active?.type === 'delegate' && (
        <DelegateModal
          task={active.task}
          busy={busy}
          onClose={() => setActive(null)}
          onSubmit={(assignee, reason) =>
            run(() => delegateTask(active.task.taskId, { assignee, reason, actor: userId }))
          }
        />
      )}
      {active?.type === 'return' && (
        <ReturnModal
          task={active.task}
          busy={busy}
          onClose={() => setActive(null)}
          onSubmit={(r) => run(() => returnTask(active.task.taskId, { ...r, actor: userId }))}
        />
      )}
      {active?.type === 'addsign' && (
        <AddSignModal
          task={active.task}
          busy={busy}
          onClose={() => setActive(null)}
          onSubmit={(r) => run(() => addSignTask(active.task.taskId, { ...r, actor: userId }))}
        />
      )}
      {active?.type === 'reassign' && (
        <ReassignModal
          task={active.task}
          busy={busy}
          onClose={() => setActive(null)}
          onSubmit={(r) => run(() => reassignTask(active.task.taskId, { ...r, actor: userId }))}
        />
      )}
    </Page>
  );
}

// ── 委派 ────────────────────────────────────────────────────

interface DelegateForm {
  assignee: string;
  reason: string;
}

function DelegateModal({
  task,
  busy,
  onClose,
  onSubmit,
}: {
  task: TaskView;
  busy: boolean;
  onClose: () => void;
  onSubmit: (assignee: string, reason: string) => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<DelegateForm>({ assignee: '', reason: '' });

  const fields: Array<FormField<DelegateForm>> = [
    { key: 'assignee', labelKey: 'todo.delegateUser', kind: 'text', required: true, placeholder: 'carol' },
    { key: 'reason', labelKey: 'todo.delegateReason', kind: 'textarea', rows: 3, span: 2 },
  ];

  return (
    <CimModal
      open
      onClose={onClose}
      title={`${t('todo.delegateTitle')} · ${task.name}`}
      onOk={() => onSubmit(form.assignee.trim(), form.reason)}
      okDisabled={busy || !form.assignee.trim()}
      okLoading={busy}
      okKey="common.submit"
      cancelKey="common.cancel"
    >
      <FormGrid<DelegateForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
      />
      <p className="dim" style={{ marginBottom: 0 }}>{t('todo.delegateHint')}</p>
    </CimModal>
  );
}

// ── 回退 ────────────────────────────────────────────────────

interface ReturnForm {
  mode: 'previous' | 'initiator' | 'specified';
  specified: string;
  reason: string;
}

function ReturnModal({
  task,
  busy,
  onClose,
  onSubmit,
}: {
  task: TaskView;
  busy: boolean;
  onClose: () => void;
  onSubmit: (r: ReturnRequest) => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<ReturnForm>({ mode: 'previous', specified: '', reason: '' });

  const targetsRes = useResource(({ signal }) => getReturnTargets(task.taskId, { signal }));
  const targets = targetsRes.data;

  const maxReached = targets != null && targets.currentRound >= targets.maxRounds;
  const targetId =
    targets == null
      ? null
      : form.mode === 'previous'
        ? targets.previousNodeId
        : form.mode === 'initiator'
          ? targets.initiatorNodeId
          : form.specified || null;
  const canSubmit = targets != null && !maxReached && !!targetId && !!form.reason.trim();

  // 字段按当前目标节点重建（而不是渲染后隐藏）：否则切过「指定节点」再切回来，
  // 上一次选的 nodeId 仍留在 state 里，随 payload 一起发出去
  const fields: Array<FormField<ReturnForm>> = [
    {
      key: 'mode',
      labelKey: 'todo.returnMode',
      kind: 'radio',
      span: 2,
      options: [
        {
          value: 'previous',
          label: t('todo.returnModePrevious') + (targets?.previousNodeName ? `（${targets.previousNodeName}）` : ''),
          disabled: !targets?.previousNodeId,
        },
        {
          value: 'initiator',
          label: t('todo.returnModeInitiator'),
          disabled: !!targets?.initiatorNodeMulti,
        },
        {
          value: 'specified',
          label: t('todo.returnModeSpecified'),
          disabled: (targets?.historicNodes.length ?? 0) === 0,
        },
      ],
    },
    ...(form.mode === 'specified'
      ? [
          {
            key: 'specified' as const,
            labelKey: 'todo.returnTarget',
            kind: 'select' as const,
            span: 2,
            required: true,
            options: (targets?.historicNodes ?? []).map((n) => ({ value: n.id, label: n.name })),
          },
        ]
      : []),
    { key: 'reason', labelKey: 'todo.returnReason', kind: 'textarea', rows: 3, span: 2, required: true },
  ];

  return (
    <CimModal
      open
      onClose={onClose}
      title={`${t('todo.returnTitle')} · ${task.name}`}
      width={560}
      onOk={() => onSubmit({ targetActivityId: targetId!, reason: form.reason.trim(), actor: '' })}
      okDisabled={busy || !canSubmit}
      okLoading={busy}
      okKey="common.submit"
      cancelKey="common.cancel"
    >
      {targetsRes.error && <p className="dim">{targetsRes.error.message}</p>}
      {!targets && !targetsRes.error && <p className="dim">{t('common.loading')}</p>}
      {targets && (
        <>
          <p className="dim" style={{ marginTop: 0 }}>
            {t('todo.returnRound', { n: targets.currentRound, max: targets.maxRounds })}
          </p>
          {maxReached && <p style={{ color: 'var(--err)' }}>{t('todo.returnMaxReached', { max: targets.maxRounds })}</p>}
          <FormGrid<ReturnForm>
            fields={fields}
            value={form}
            onChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
          />
          {targets.initiatorNodeMulti && form.mode === 'initiator' && (
            <p className="dim">{t('todo.returnMultiInitiatorHint')}</p>
          )}
          {form.mode !== 'initiator' && targets.parallelActive && (
            <p className="dim">{t('todo.returnParallelHint')}</p>
          )}
        </>
      )}
    </CimModal>
  );
}

// ── 加签 ────────────────────────────────────────────────────

interface AddSignForm {
  assignee: string;
  type: 'AFTER' | 'BEFORE';
  reason: string;
}

function AddSignModal({
  task,
  busy,
  onClose,
  onSubmit,
}: {
  task: TaskView;
  busy: boolean;
  onClose: () => void;
  onSubmit: (r: AddSignRequest) => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<AddSignForm>({ assignee: '', type: 'AFTER', reason: '' });

  const fields: Array<FormField<AddSignForm>> = [
    { key: 'assignee', labelKey: 'todo.addSignUser', kind: 'text', required: true, placeholder: 'dave' },
    {
      key: 'type',
      labelKey: 'todo.addSignType',
      kind: 'select',
      options: [
        { value: 'AFTER', labelKey: 'todo.addSign.after' },
        { value: 'BEFORE', labelKey: 'todo.addSign.before' },
      ],
    },
    { key: 'reason', labelKey: 'todo.addSignReason', kind: 'textarea', rows: 3, span: 2 },
  ];

  return (
    <CimModal
      open
      onClose={onClose}
      title={`${t('todo.addSignTitle')} · ${task.name}`}
      onOk={() => onSubmit({ assignee: form.assignee.trim(), type: form.type, reason: form.reason, actor: '' })}
      okDisabled={busy || !form.assignee.trim()}
      okLoading={busy}
      okKey="common.submit"
      cancelKey="common.cancel"
    >
      <FormGrid<AddSignForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
      />
    </CimModal>
  );
}

// ── 改派 ────────────────────────────────────────────────────

interface ReassignForm {
  newAssignee: string;
  reason: string;
}

function ReassignModal({
  task,
  busy,
  onClose,
  onSubmit,
}: {
  task: TaskView;
  busy: boolean;
  onClose: () => void;
  onSubmit: (r: ReassignRequest) => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<ReassignForm>({ newAssignee: '', reason: '' });

  const fields: Array<FormField<ReassignForm>> = [
    { key: 'newAssignee', labelKey: 'todo.reassignUser', kind: 'text', required: true, placeholder: 'bob' },
    { key: 'reason', labelKey: 'todo.reassignReason', kind: 'textarea', rows: 3, span: 2 },
  ];

  return (
    <CimModal
      open
      onClose={onClose}
      title={`${t('todo.reassignTitle')} · ${task.name}`}
      onOk={() => onSubmit({ newAssignee: form.newAssignee.trim(), reason: form.reason, actor: '' })}
      okDisabled={busy || !form.newAssignee.trim()}
      okLoading={busy}
      okKey="common.submit"
      cancelKey="common.cancel"
    >
      <FormGrid<ReassignForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
      />
    </CimModal>
  );
}
