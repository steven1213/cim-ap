import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import type { StatusTone } from '@cim/theme';
import {
  CimButton,
  CimDescriptions,
  CimInfoTable,
  CimModal,
  CimTag,
  DataTable,
  EmptyState,
  useConfirm,
  useToast,
  type DataTableColumn,
  type DescItem,
  type InfoItem,
  StatusTag,
} from '@cim/ui';
import CompleteModal from '@/components/CompleteModal';
import { DisplayFields } from '@/components/DisplayFields';
import { FlowProgress } from '@/components/FlowProgress';
import ResubmitModal from '@/components/ResubmitModal';
import { IconBell, IconCheck, IconClip, IconDownload, IconRefresh, IconUndo, IconTrash } from '@/components/Icons';
import {
  attachmentDownloadUrl,
  completeTask,
  deleteAttachment,
  getProcessDetail,
  urgeInstance,
  uploadAttachment,
  withdrawInstance,
} from '@/lib/api';
import { fmtDate as formatDateTime } from '@/lib/format';
import { useIdentityStore } from '@/store/identityStore';
import type { AttachmentView, FormSchemaField, ProcessDetailView, TaskView } from '@/types';

function fmtSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

/** 详情页展示的一行审批内容字段。 */
interface FormRow {
  key: string;
  label: string;
  display: string;
  /** textarea 类型的长文本独占整行 */
  wide: boolean;
}

/** 按类型格式化字段值（money/number/boolean/date/list…），空值统一展示 —。 */
function formatFieldValue(v: unknown, type: string): string {
  if (v === null || v === undefined || v === '') return '—';
  switch (type) {
    case 'money': {
      const n = Number(v);
      return Number.isNaN(n)
        ? String(v)
        : `¥ ${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
    case 'number': {
      const n = Number(v);
      return Number.isNaN(n) ? String(v) : n.toLocaleString('zh-CN');
    }
    case 'boolean':
      return v === true || v === 'true' ? '是' : '否';
    case 'date':
      return String(v).slice(0, 10);
    case 'datetime': {
      const d = new Date(String(v));
      return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleString('zh-CN');
    }
    case 'list':
      return Array.isArray(v) ? v.map(String).join('、') : String(v);
    default:
      return String(v);
  }
}

/**
 * 把 formData + formSchema 组装为分组的展示行：
 * schema 字段按 group/order 归组排序并换上中文标签；
 * formData 中未在 schema 声明的键追加到「其他」组，保证不丢数据。
 */
function buildFormGroups(
  formData: Record<string, unknown>,
  schema: FormSchemaField[],
  otherLabel: string,
): { name: string; rows: FormRow[] }[] {
  const usedKeys = new Set<string>();
  const groups = new Map<string, FormRow[]>();
  const push = (group: string, row: FormRow) => {
    const g = group?.trim() || otherLabel;
    if (!groups.has(g)) groups.set(g, []);
    groups.get(g)!.push(row);
  };

  schema
    .filter((f) => f.key && f.key in formData)
    .forEach((f) => {
      usedKeys.add(f.key);
      const type = f.type ?? 'text';
      const display = formatFieldValue(formData[f.key], type);
      const unit = f.unit ? ` ${f.unit}` : '';
      push(f.group ?? '', {
        key: f.key,
        label: f.label || f.key,
        display: display === '—' ? '—' : display + unit,
        wide: type === 'textarea' || display.length > 40,
      });
    });
  // schema 未覆盖的字段按原顺序追加，不丢数据
  for (const [k, v] of Object.entries(formData)) {
    if (!usedKeys.has(k)) {
      push('', { key: k, label: k, display: formatFieldValue(v, 'text'), wide: false });
    }
  }
  return [...groups.entries()].map(([name, rows]) => ({ name, rows }));
}

/** 审计动作 → 语义档（只有 5 档；原 `info` 类一律归 run，避免出现第 6 色）。 */
const ACTION_TONE: Record<string, StatusTone> = {
  START: 'run',
  COMPLETE_APPROVE: 'ok',
  COMPLETE_REJECT: 'err',
  RETURN: 'warn',
  ADD_SIGN: 'run',
  REASSIGN: 'run',
  URGE: 'warn',
  WITHDRAW: 'err',
  ATTACHMENT_ADD: 'idle',
  ATTACHMENT_DEL: 'idle',
  CALLBACK_SENT: 'idle',
  CALLBACK_FAILED: 'err',
};

/** 审批详情弹窗：基本信息 / 审批内容 / 当前待办 / 附件 / 审批历史。
 *  <p>详情内直达操作：办理人可审批（通过/驳回），发起人可催办/撤回——消除「关弹窗回列表」断点。</p> */
export default function DetailModal({
  processInstanceId,
  onClose,
  onChanged,
}: {
  processInstanceId: string;
  onClose: () => void;
  /** 详情内操作成功后通知父页面刷新列表。 */
  onChanged?: () => void;
}) {
  const { t } = useTranslation();
  const { userId } = useIdentityStore();
  const toast = useToast();
  const confirm = useConfirm();
  const [busy, setBusy] = useState(false);
  const [completeFor, setCompleteFor] = useState<{ task: TaskView; outcome: 'APPROVE' | 'REJECT' } | null>(null);
  /** 发起人重提弹窗（awaitingResubmit 态）。 */
  const [resubmitOpen, setResubmitOpen] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  // 取数走 useResource：竞态/取消内建；**失败留在弹窗里**给重试，不再 toast 完直接关掉
  // （关掉后用户看不到原因，也没有任何可操作的东西）。
  const detailRes = useResource<ProcessDetailView>(
    ({ signal }) => getProcessDetail(processInstanceId, userId, { signal }),
    { deps: [processInstanceId, userId] },
  );
  const detail = detailRes.data;
  const reload = detailRes.refresh;

  const running = detail?.status === 'ACTIVE';

  // 详情内直达操作：当前用户是待办办理人/候选人 → 可审批；是发起人 → 可催办/撤回
  const operableTask: TaskView | null =
    detail && running
      ? (detail.activeTasks.find(
          (tk) => tk.assignee === userId || (tk.candidateUsers ?? []).includes(userId),
        ) ?? null)
      : null;
  const isInitiator = detail?.initiator === userId;

  async function onUpload(files: FileList | null) {
    if (!files || files.length === 0 || !detail) return;
    setBusy(true);
    try {
      for (const f of Array.from(files)) {
        await uploadAttachment(f, detail.businessKey, {
          processInstanceId: detail.processInstanceId,
          tenantId: detail.tenantId ?? undefined,
        });
      }
      toast.success(t('detail.attachUploaded'));
      reload();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
      // 清空 input：否则连着再选同一个文件不会触发 change
      if (fileRef.current) fileRef.current.value = '';
    }
  }

  async function onDeleteAttachment(id: string) {
    setBusy(true);
    try {
      await deleteAttachment(id);
      toast.success(t('detail.attachDeleted'));
      reload();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function runAction(fn: () => Promise<void>, okMsg: string) {
    setBusy(true);
    try {
      await fn();
      toast.success(okMsg);
      reload();
      onChanged?.();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function withdraw() {
    if (!detail) return;
    const ok = await confirm({
      titleKey: 'init.withdraw',
      okKey: 'init.withdraw',
      content: t('init.withdrawConfirm', { bk: detail.businessKey }),
      danger: true,
    });
    if (!ok) return;
    await runAction(() => withdrawInstance(detail.processInstanceId, userId), t('init.withdrawDone'));
  }

  const basicItems = useMemo<Array<DescItem<never>>>(() => {
    if (!detail) return [];
    const tags = [
      <StatusTag
        key="s"
        dict="approvalState"
        value={detail.status}
        label={
          detail.status === 'ACTIVE'
            ? t('detail.statusActive')
            : detail.status === 'WITHDRAWN'
              ? t('detail.statusWithdrawn')
              : t('detail.statusCompleted')
        }
      />,
    ];
    if (detail.round != null && detail.round > 1) {
      tags.push(
        <CimTag key="r" tone="run">
          {t('init.roundBadge', { n: detail.round })}
        </CimTag>,
      );
    }
    if (detail.awaitingResubmit) {
      tags.push(
        <CimTag key="w" tone="warn">
          {t('init.awaitingResubmit')}
        </CimTag>,
      );
    }
    return [
      { key: 'status', label: t('detail.status'), value: <span className="cell-inline">{tags}</span> },
      { key: 'bk', label: t('detail.businessKey'), value: <span className="mono">{detail.businessKey}</span> },
      { key: 'proc', label: t('detail.process'), value: detail.definitionName || detail.processKey },
      { key: 'initiator', label: t('detail.initiator'), value: detail.initiator ?? '—' },
      {
        key: 'startedAt',
        label: t('detail.startedAt'),
        value: detail.startedAt ? formatDateTime(detail.startedAt) : '—',
      },
      { key: 'endedAt', label: t('detail.endedAt'), value: detail.endedAt ? formatDateTime(detail.endedAt) : '—' },
    ];
    // t 随语言变化，语言切换时 useTranslation 会触发重渲染，这里跟着重算
  }, [detail, t]);

  /** 无 schema 时的回退：原始键值表（长文本折行，不截断）。 */
  const rawItems = useMemo<Array<InfoItem<never>>>(() => {
    if (!detail) return [];
    return Object.entries(detail.formData ?? {}).map(([k, v]) => ({
      key: k,
      label: k,
      value: String(v),
      wrap: true,
      mono: true,
    }));
  }, [detail]);

  const formGroups = useMemo(
    () => (detail ? buildFormGroups(detail.formData ?? {}, detail.formSchema ?? [], t('detail.groupOther')) : []),
    [detail, t],
  );
  // schema 一个 group 都没声明时，整块只有「其他信息」这一个伪分组 —— 别给它加标题，
  // 否则详情里会凭空多出一行分组名，看着像漏配了分组
  const hasExplicitGroup = (detail?.formSchema ?? []).some((f) => (f.group ?? '').trim());

  const taskColumns = useMemo<Array<DataTableColumn<TaskView>>>(
    () => [
      {
        key: 'name',
        titleKey: 'detail.taskNode',
        width: 160,
        render: (tk) => <CimTag tone="run">{tk.name}</CimTag>,
      },
      {
        key: 'who',
        titleKey: 'detail.assignee',
        render: (tk) =>
          tk.assignee
            ? tk.assignee
            : `${t('detail.candidates')}: ${tk.candidateUsers.join(', ') || '—'}`,
      },
      {
        key: 'createdAt',
        titleKey: 'common.createdAt',
        width: 150,
        render: (tk) => formatDateTime(tk.createdAt),
      },
    ],
    [t],
  );

  const attachColumns = useMemo<Array<DataTableColumn<AttachmentView>>>(
    () => [
      {
        key: 'fileName',
        titleKey: 'detail.attachName',
        // 文件名本身就是下载入口（action 列不再重复放一个「下载」按钮）
        render: (a) => (
          <a className="detail-file" href={attachmentDownloadUrl(a.id)} download>
            <IconDownload width={14} height={14} /> {a.fileName}
          </a>
        ),
      },
      {
        key: 'sizeBytes',
        titleKey: 'detail.attachSize',
        width: 90,
        render: (a) => fmtSize(a.sizeBytes),
      },
      { key: 'uploader', titleKey: 'detail.attachUploader', width: 110 },
      {
        key: 'actions',
        titleKey: 'common.actions',
        width: 70,
        align: 'right',
        render: (a) => (
          <CimButton
            size="small"
            danger
            icon={<IconTrash width={13} height={13} />}
            iconOnly
            tooltipKey="common.delete"
            disabled={busy}
            onClick={() => onDeleteAttachment(a.id)}
          />
        ),
      },
    ],
    // onDeleteAttachment 依赖 detail/busy，闭包每次 render 重建；列定义只依赖 busy 与文案
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [busy, t],
  );

  const body = () => {
    if (detailRes.error && !detail) {
      // 取不到详情：留在弹窗里给原因 + 重试，关掉弹窗等于把错误一起关掉
      return (
        <div className="detail-load-error">
          <EmptyState title={detailRes.error.message} hint={t('detail.loadFailedHint')} />
          <CimButton size="small" onClick={reload}>
            {t('common.refresh')}
          </CimButton>
        </div>
      );
    }
    if (!detail) return <EmptyState title={t('common.loading')} />;
    return (
      <div className="detail-body">
        {/* 基本信息 */}
        <section className="detail-section">
          <h3 className="detail-section-title">{detail.title || detail.businessKey}</h3>
          <DisplayFields fields={detail.displayFields} />
          {/* 审批进度节点条：已到达节点亮起，未到达置灰 */}
          <FlowProgress nodes={detail.flowNodes} />
          <CimDescriptions items={basicItems} column={3} />
        </section>

        {/* 审批内容 */}
        <section className="detail-section">
          <h4 className="detail-heading">{t('detail.formData')}</h4>
          {Object.keys(detail.formData ?? {}).length === 0 ? (
            <p className="detail-empty">{t('detail.formDataEmpty')}</p>
          ) : (detail.formSchema ?? []).length > 0 ? (
            /* schema 模式：按分组渲染中文标签 + 类型化值 */
            formGroups.map((g) => (
              <CimDescriptions
                key={g.name}
                title={hasExplicitGroup ? g.name : undefined}
                column={2}
                items={g.rows.map((r) => ({
                  key: r.key,
                  label: r.label,
                  value: r.display,
                  span: r.wide ? 2 : 1,
                }))}
              />
            ))
          ) : (
            /* 无 schema：回退为原始键值表 */
            <CimInfoTable items={rawItems} />
          )}
        </section>

        {/* 当前待办（运行中） */}
        {running && (
          <section className="detail-section">
            <h4 className="detail-heading">{t('detail.currentTasks')}</h4>
            <DataTable
              flat
              size="small"
              columns={taskColumns}
              rows={detail.activeTasks}
              rowKey={(tk) => tk.taskId}
              emptyTitleKey="detail.noActiveTask"
            />
          </section>
        )}

        {/* 附件 */}
        <section className="detail-section">
          <h4 className="detail-heading">
            <IconClip width={14} height={14} /> {t('detail.attachments')}
            <input
              ref={fileRef}
              type="file"
              multiple
              style={{ display: 'none' }}
              onChange={(e) => onUpload(e.target.files)}
            />
            <CimButton
              size="small"
              className="detail-upload-btn"
              icon={<IconClip width={13} height={13} />}
              disabled={busy}
              onClick={() => fileRef.current?.click()}
            >
              {t('detail.upload')}
            </CimButton>
          </h4>
          <DataTable
            flat
            size="small"
            columns={attachColumns}
            rows={detail.attachments}
            rowKey={(a) => a.id}
            emptyTitleKey="detail.attachEmpty"
          />
        </section>

        {/* 审批历史（哈希链：保留自定义时间线，标签色走 5 档语义） */}
        <section className="detail-section">
          <h4 className="detail-heading">{t('detail.history')}</h4>
          <div className="detail-timeline">
            {detail.history.map((h) => {
              // 仅 COMPLETE_* 的 detail 是 {comment} JSON；其余动作为 key=value 摘要，不作为意见展示
              let comment = '';
              try {
                const parsed = JSON.parse(h.detail);
                if (parsed && typeof parsed === 'object' && 'comment' in parsed && parsed.comment) {
                  comment = String(parsed.comment);
                }
              } catch {
                // 非 JSON：非审批意见，忽略
              }
              return (
                <div key={h.id} className="detail-timeline-item">
                  <CimTag tone={ACTION_TONE[h.action] ?? 'idle'} size="small">
                    {t(`detail.action.${h.action}`, h.action)}
                  </CimTag>
                  <div className="detail-timeline-body">
                    <div>
                      <b>{h.actor}</b>
                      {h.afterState && <span className="dim"> → {h.afterState}</span>}
                      <span className="dim" style={{ marginLeft: 8 }}>
                        {formatDateTime(h.createdAt)}
                      </span>
                    </div>
                    {comment && <div className="detail-comment">“{comment}”</div>}
                    <div className="detail-hash mono" title={h.hash}>
                      #{h.seq} {h.hash.slice(0, 12)}…
                    </div>
                  </div>
                </div>
              );
            })}
            {detail.history.length === 0 && <p className="detail-empty">{t('common.empty')}</p>}
          </div>
        </section>
      </div>
    );
  };

  return (
    <CimModal
      open
      onClose={onClose}
      title={t('detail.title')}
      width={820}
      footer={
        <div className="detail-actions">
          {operableTask && (
            <>
              <CimButton
                size="small"
                type="primary"
                icon={<IconCheck width={14} height={14} />}
                disabled={busy}
                onClick={() => setCompleteFor({ task: operableTask, outcome: 'APPROVE' })}
              >
                {t('detail.act.approve')}
              </CimButton>
              <CimButton
                size="small"
                danger
                disabled={busy}
                onClick={() => setCompleteFor({ task: operableTask, outcome: 'REJECT' })}
              >
                {t('detail.act.reject')}
              </CimButton>
            </>
          )}
          {isInitiator && running && (
            <>
              {detail?.awaitingResubmit && (
                <CimButton
                  size="small"
                  icon={<IconRefresh width={14} height={14} />}
                  disabled={busy}
                  onClick={() => setResubmitOpen(true)}
                >
                  {t('init.resubmit')}
                </CimButton>
              )}
              <CimButton
                size="small"
                icon={<IconBell width={14} height={14} />}
                disabled={busy}
                onClick={() => runAction(() => urgeInstance(detail!.processInstanceId, userId), t('init.urgeSent'))}
              >
                {t('init.urge')}
              </CimButton>
              <CimButton
                size="small"
                icon={<IconUndo width={14} height={14} />}
                disabled={busy}
                onClick={withdraw}
              >
                {t('init.withdraw')}
              </CimButton>
            </>
          )}
          <CimButton size="small" type="text" onClick={onClose}>
            {t('common.close')}
          </CimButton>
        </div>
      }
    >
      {body()}
      {completeFor && (
        <CompleteModal
          task={completeFor.task}
          busy={busy}
          defaultOutcome={completeFor.outcome}
          onClose={() => setCompleteFor(null)}
          onSubmit={async (r) => {
            await runAction(
              () => completeTask(completeFor.task.taskId, { ...r, actor: userId }),
              t('todo.completeOk'),
            );
            setCompleteFor(null);
            onClose();
          }}
        />
      )}
      {resubmitOpen && detail && (
        <ResubmitModal
          processInstanceId={detail.processInstanceId}
          initial={detail}
          onClose={() => setResubmitOpen(false)}
          onDone={() => {
            reload();
            onChanged?.();
          }}
        />
      )}
    </CimModal>
  );
}
