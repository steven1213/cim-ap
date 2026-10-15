import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Modal from '@/components/Modal';
import { IconClip, IconDownload, IconTrash } from '@/components/Icons';
import {
  attachmentDownloadUrl,
  deleteAttachment,
  getProcessDetail,
  uploadAttachment,
} from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { toast } from '@/store/toastStore';
import { fmtDate as formatDateTime } from '@/lib/format';
import type { FormSchemaField, ProcessDetailView } from '@/types';

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

/** 审计动作 → 时间线标签色。 */
const ACTION_STYLE: Record<string, string> = {
  START: 'tag info',
  COMPLETE_APPROVE: 'tag ok',
  COMPLETE_REJECT: 'tag err',
  RETURN: 'tag warn',
  ADD_SIGN: 'tag info',
  REASSIGN: 'tag info',
  URGE: 'tag warn',
  WITHDRAW: 'tag err',
  ATTACHMENT_ADD: 'tag',
  ATTACHMENT_DEL: 'tag',
  CALLBACK_SENT: 'tag',
  CALLBACK_FAILED: 'tag err',
};

/** 审批详情弹窗：基本信息 / 审批内容 / 当前待办 / 附件 / 审批历史。 */
export default function DetailModal({
  processInstanceId,
  onClose,
}: {
  processInstanceId: string;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const { userId } = useIdentityStore();
  const [detail, setDetail] = useState<ProcessDetailView | null>(null);
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    try {
      setDetail(await getProcessDetail(processInstanceId, userId));
    } catch (e) {
      toast((e as Error).message, 'err');
      onClose();
    }
  }, [processInstanceId, userId, onClose]);

  useEffect(() => {
    load();
  }, [load]);

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
      toast(t('detail.attachUploaded'), 'ok');
      await load();
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  }

  async function onDeleteAttachment(id: string) {
    setBusy(true);
    try {
      await deleteAttachment(id);
      toast(t('detail.attachDeleted'), 'ok');
      await load();
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setBusy(false);
    }
  }

  const formDataEntries = detail ? Object.entries(detail.formData ?? {}) : [];
  const running = detail?.status === 'ACTIVE';

  return (
    <Modal title={t('detail.title')} onClose={onClose} width={560}>
      {!detail ? (
        <div className="empty" style={{ padding: '24px 0' }}>
          <span>{t('common.loading')}</span>
        </div>
      ) : (
        <div className="detail-body">
          {/* 基本信息 */}
          <section className="detail-section">
            <h3 className="detail-section-title">{detail.title || detail.businessKey}</h3>
            <div className="detail-grid">
              <div className="detail-grid-item">
                <span className="detail-label">{t('detail.status')}</span>
                <span className={`tag ${detail.status === 'ACTIVE' ? 'info' : detail.status === 'WITHDRAWN' ? 'warn' : 'ok'}`}>
                  {detail.status === 'ACTIVE'
                    ? t('detail.statusActive')
                    : detail.status === 'WITHDRAWN'
                      ? t('detail.statusWithdrawn')
                      : t('detail.statusCompleted')}
                </span>
              </div>
              <div className="detail-grid-item">
                <span className="detail-label">{t('detail.businessKey')}</span>
                <span className="mono">{detail.businessKey}</span>
              </div>
              <div className="detail-grid-item">
                <span className="detail-label">{t('detail.process')}</span>
                <span>{detail.definitionName || detail.processKey}</span>
              </div>
              <div className="detail-grid-item">
                <span className="detail-label">{t('detail.initiator')}</span>
                <span>{detail.initiator ?? '—'}</span>
              </div>
              <div className="detail-grid-item">
                <span className="detail-label">{t('detail.startedAt')}</span>
                <span>{detail.startedAt ? formatDateTime(detail.startedAt) : '—'}</span>
              </div>
              <div className="detail-grid-item">
                <span className="detail-label">{t('detail.endedAt')}</span>
                <span>{detail.endedAt ? formatDateTime(detail.endedAt) : '—'}</span>
              </div>
            </div>
          </section>

          {/* 审批内容 */}
          <section className="detail-section">
            <h4 className="detail-heading">{t('detail.formData')}</h4>
            {formDataEntries.length === 0 ? (
              <p className="detail-empty">{t('detail.formDataEmpty')}</p>
            ) : detail.formSchema.length > 0 ? (
              /* schema 模式：按分组渲染中文标签 + 类型化值 */
              buildFormGroups(detail.formData, detail.formSchema, t('detail.groupOther')).map((g) => (
                <div className="detail-form-group" key={g.name}>
                  <div className="detail-form-group-title">{g.name}</div>
                  <div className="detail-form-grid">
                    {g.rows.map((r) => (
                      <div className={`detail-form-item${r.wide ? ' wide' : ''}`} key={r.key}>
                        <span className="detail-form-label">{r.label}</span>
                        <span className={`detail-form-value${r.display.length > 40 ? ' long' : ''}`}>
                          {r.display}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              ))
            ) : (
              /* 无 schema：回退为原始键值表 */
              <div className="table-wrap detail-kv">
                <table className="data">
                  <thead>
                    <tr>
                      <th style={{ width: 130 }}>{t('detail.field')}</th>
                      <th>{t('detail.value')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {formDataEntries.map(([k, v]) => (
                      <tr key={k}>
                        <td className="dim">{k}</td>
                        <td className="detail-kv-value">{String(v)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {/* 当前待办（运行中） */}
          {running && (
            <section className="detail-section">
              <h4 className="detail-heading">{t('detail.currentTasks')}</h4>
              {detail.activeTasks.length === 0 ? (
                <p className="detail-empty">{t('detail.noActiveTask')}</p>
              ) : (
                <div className="detail-kv">
                  {detail.activeTasks.map((tk) => (
                    <div key={tk.taskId} className="detail-task-row">
                      <span className="tag info">{tk.name}</span>
                      <span className="detail-task-meta">
                        {tk.assignee
                          ? `${t('detail.assignee')}: ${tk.assignee}`
                          : `${t('detail.candidates')}: ${tk.candidateUsers.join(', ') || '—'}`}
                        <span className="dim"> · {formatDateTime(tk.createdAt)}</span>
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}

          {/* 附件 */}
          <section className="detail-section">
            <h4 className="detail-heading">
              <IconClip width={14} height={14} /> {t('detail.attachments')}
              <label className="btn btn-sm detail-upload-btn">
                <input
                  ref={fileRef}
                  type="file"
                  multiple
                  style={{ display: 'none' }}
                  onChange={(e) => onUpload(e.target.files)}
                  disabled={busy}
                />
                {t('detail.upload')}
              </label>
            </h4>
            {detail.attachments.length === 0 ? (
              <p className="detail-empty">{t('detail.attachEmpty')}</p>
            ) : (
              <div className="table-wrap detail-kv">
                <table className="data">
                  <thead>
                    <tr>
                      <th>{t('detail.attachName')}</th>
                      <th style={{ width: 80 }}>{t('detail.attachSize')}</th>
                      <th style={{ width: 90 }}>{t('detail.attachUploader')}</th>
                      <th style={{ width: 120 }}></th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.attachments.map((a) => (
                      <tr key={a.id}>
                        <td>
                          <a className="detail-file" href={attachmentDownloadUrl(a.id)} download>
                            <IconDownload width={14} height={14} /> {a.fileName}
                          </a>
                        </td>
                        <td>{fmtSize(a.sizeBytes)}</td>
                        <td>{a.uploader}</td>
                        <td>
                          <div className="cell-actions">
                            <a className="btn btn-sm" href={attachmentDownloadUrl(a.id)} download>
                              <IconDownload width={13} height={13} /> {t('detail.download')}
                            </a>
                            <button
                              className="btn btn-sm btn-danger"
                              disabled={busy}
                              onClick={() => onDeleteAttachment(a.id)}
                            >
                              <IconTrash width={13} height={13} />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {/* 审批历史 */}
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
                    <span className={`tag ${ACTION_STYLE[h.action] ?? 'tag-muted'}`}>
                      {t(`detail.action.${h.action}`, h.action)}
                    </span>
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
      )}
    </Modal>
  );
}
