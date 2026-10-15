import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Panel from '@/components/Panel';
import Modal from '@/components/Modal';
import { IconFlow, IconPlay, IconPencil, IconPlus, IconX } from '@/components/Icons';
import { listDefinitions, startProcess } from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { toast } from '@/store/toastStore';
import { parseVariables } from '@/lib/format';
import type { ProcessDefinitionView } from '@/types';

export default function DefinitionsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [defs, setDefs] = useState<ProcessDefinitionView[]>([]);
  const [showStart, setShowStart] = useState(false);
  const [busy, setBusy] = useState(false);

  const { userId, tenantId } = useIdentityStore();

  async function load() {
    try {
      setDefs(await listDefinitions());
    } catch (e) {
      toast((e as Error).message, 'err');
    }
  }
  useEffect(() => {
    load();
  }, []);

  // 同 key 最新版本 → 可编辑；历史版本 → 只可查看
  const latestVersionOf = new Map<string, number>();
  for (const d of defs) {
    latestVersionOf.set(d.key, Math.max(latestVersionOf.get(d.key) ?? 0, d.version));
  }
  const isLatest = (d: ProcessDefinitionView) => latestVersionOf.get(d.key) === d.version;

  return (
    <div className="page">
      <Panel
        title={t('def.title')}
        sub={t('def.sub')}
        actions={
          <>
            <button className="btn btn-sm" onClick={() => navigate('/designer')}>
              <IconPencil width={14} height={14} /> {t('def.newDesign')}
            </button>
            <button className="btn btn-sm" onClick={() => setShowStart(true)}>
              <IconPlay width={14} height={14} /> {t('def.start')}
            </button>
          </>
        }
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('def.colKey')}</th>
                <th>{t('def.colName')}</th>
                <th className="num">{t('def.colVersion')}</th>
                <th>{t('def.colStatus')}</th>
                <th>{t('def.colTenant')}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {defs.length === 0 && (
                <tr>
                  <td colSpan={6}>
                    <div className="empty">
                      <IconFlow width={26} height={26} />
                      <span>{t('common.empty')}</span>
                    </div>
                  </td>
                </tr>
              )}
              {defs.map((d) => {
                const latest = isLatest(d);
                return (
                  <tr key={d.id}>
                    <td className="mono">{d.key}</td>
                    <td>{d.name}</td>
                    <td className="num">v{d.version}</td>
                    <td>
                      <span className={'tag ' + (latest ? 'ok' : '')}>{t(latest ? 'def.status.latest' : 'def.status.history')}</span>
                    </td>
                    <td>{d.tenantId ?? '—'}</td>
                    <td>
                      <div className="cell-actions">
                        {latest ? (
                          <button
                            className="btn btn-sm"
                            onClick={() => navigate('/designer', { state: { definitionId: d.id } })}
                          >
                            <IconPencil width={13} height={13} /> {t('def.action.design')}
                          </button>
                        ) : (
                          <button
                            className="btn btn-ghost btn-sm"
                            onClick={() => navigate('/designer', { state: { definitionId: d.id, readonly: true } })}
                          >
                            {t('def.action.view')}
                          </button>
                        )}
                        {latest && (
                          <button className="btn btn-ghost btn-sm" onClick={() => setShowStart(true)}>
                            {t('def.start')}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Panel>

      {showStart && (
        <StartModal
          defs={defs}
          defaultTenant={tenantId}
          defaultInitiator={userId}
          busy={busy}
          onClose={() => setShowStart(false)}
          onSubmit={async (req) => {
            setBusy(true);
            try {
              const r = await startProcess(req);
              toast(t('def.started') + r.processInstanceId, 'ok');
              setShowStart(false);
              await load();
            } catch (e) {
              toast((e as Error).message, 'err');
            } finally {
              setBusy(false);
            }
          }}
        />
      )}
    </div>
  );
}

interface StartModalProps {
  defs: ProcessDefinitionView[];
  defaultTenant: string;
  defaultInitiator: string;
  busy: boolean;
  onClose: () => void;
  onSubmit: (req: import('@/types').StartProcessRequest) => void;
}

/** 发起表单草稿（localStorage 暂存，关闭/刷新不丢；提交成功后清除）。 */
const DRAFT_KEY = 'approval.draft.v1';

interface DraftShape {
  processKey: string;
  businessKey: string;
  title: string;
  assignee: string;
  approvers: string;
  variables: string;
  formRows: { key: string; label: string; type: string; value: string }[];
}

function loadDraft(): DraftShape | null {
  try {
    const raw = localStorage.getItem(DRAFT_KEY);
    if (!raw) return null;
    const d = JSON.parse(raw);
    return d && typeof d === 'object' ? (d as DraftShape) : null;
  } catch {
    return null;
  }
}

function StartModal({ defs, defaultTenant, defaultInitiator, busy, onClose, onSubmit }: StartModalProps) {
  const { t } = useTranslation();
  // 发起永远走同 key 最新版本（引擎亦如此），下拉中去重
  const latestByKey = new Map<string, ProcessDefinitionView>();
  for (const d of defs) {
    const cur = latestByKey.get(d.key);
    if (!cur || d.version > cur.version) latestByKey.set(d.key, d);
  }
  const latestDefs = [...latestByKey.values()];
  // 草稿恢复：仅当草稿里的 processKey 仍存在时才采用
  const [saved] = useState(loadDraft);
  const [processKey, setProcessKey] = useState(
    saved && latestByKey.has(saved.processKey) ? saved.processKey : (latestDefs[0]?.key ?? ''),
  );
  const [businessKey, setBusinessKey] = useState(saved?.businessKey ?? '');
  const [title, setTitle] = useState(saved?.title ?? '');
  const [assignee, setAssignee] = useState(saved?.assignee ?? '');
  const [approvers, setApprovers] = useState(saved?.approvers ?? '');
  const [variables, setVariables] = useState(saved?.variables ?? '');
  /** 结构化业务表单数据（审批内容主体）；label/type 组装 formSchema 供详情页正确展示 */
  const [formRows, setFormRows] = useState<
    { key: string; label: string; type: string; value: string }[]
  >(saved?.formRows?.length ? saved.formRows : [{ key: '', label: '', type: 'text', value: '' }]);
  const [draftRestored, setDraftRestored] = useState(
    !!saved && !!(saved.businessKey?.trim() || saved.title?.trim()),
  );

  // 草稿自动暂存：有任何实质内容即写 localStorage
  useEffect(() => {
    const hasContent =
      businessKey.trim() || title.trim() || formRows.some((r) => r.key.trim() || r.value.trim());
    if (!hasContent) return;
    const draft: DraftShape = { processKey, businessKey, title, assignee, approvers, variables, formRows };
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
    } catch {
      /* 存储满等异常静默 */
    }
  }, [processKey, businessKey, title, assignee, approvers, variables, formRows]);

  function submit() {
    if (!processKey || !businessKey || !title) {
      toast(t('common.confirm') + '：' + t('def.form.processKey') + ' / ' + t('def.form.businessKey') + ' / ' + t('def.form.title'), 'warn');
      return;
    }
    const vars = parseVariables(variables);
    if (variables.trim() && vars === null) {
      toast(t('def.form.variables') + ' JSON ' + t('common.empty'), 'warn');
      return;
    }
    const formData: Record<string, unknown> = {};
    const formSchema: import('@/types').FormSchemaField[] = [];
    for (const r of formRows) {
      const key = r.key.trim();
      if (!key) continue;
      // 按类型转换值：数值/金额转 Number，布尔转 boolean，其余保留字符串
      let v: unknown = r.value;
      if (r.type === 'number' || r.type === 'money') {
        const n = Number(r.value);
        if (r.value.trim() !== '' && !Number.isNaN(n)) v = n;
      } else if (r.type === 'boolean') {
        v = r.value === 'true' || r.value === '是';
      }
      formData[key] = v;
      if (r.label.trim()) {
        formSchema.push({ key, label: r.label.trim(), type: r.type });
      }
    }
    onSubmit({
      processKey,
      businessKey,
      tenantId: defaultTenant,
      initiator: defaultInitiator,
      title,
      assignee: approvers.trim() ? null : assignee.trim() || null,
      approvers: approvers.trim()
        ? approvers.split(',').map((s) => s.trim()).filter(Boolean)
        : null,
      variables: vars,
      formData: Object.keys(formData).length > 0 ? formData : null,
      formSchema: formSchema.length > 0 ? formSchema : null,
    });
    // 提交成功路径由父组件关闭弹窗；无论成败先清草稿（表单内容仍在弹窗中，失败可重试）
    localStorage.removeItem(DRAFT_KEY);
    setDraftRestored(false);
  }

  return (
    <Modal
      title={t('def.startTitle')}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>
            {t('common.cancel')}
          </button>
          <button className="btn btn-sm" disabled={busy} onClick={submit}>
            <IconPlay width={14} height={14} /> {t('common.submit')}
          </button>
        </>
      }
    >
      {draftRestored && (
        <div className="field-hint" style={{ color: 'var(--warn)', marginBottom: 8 }}>
          {t('def.form.draftRestored')}
          <button
            className="btn btn-ghost btn-sm"
            style={{ marginLeft: 8 }}
            onClick={() => {
              localStorage.removeItem(DRAFT_KEY);
              setDraftRestored(false);
            }}
          >
            {t('def.form.draftDiscard')}
          </button>
        </div>
      )}
      <div className="field">
        <label className="field-label">{t('def.form.processKey')}</label>
        <select value={processKey} onChange={(e) => setProcessKey(e.target.value)}>
          {latestDefs.map((d) => (
            <option key={d.id} value={d.key}>
              {d.name}（{d.key}）
            </option>
          ))}
        </select>
      </div>
      <div className="field">
        <label className="field-label">{t('def.form.businessKey')}</label>
        <input className="mono" value={businessKey} onChange={(e) => setBusinessKey(e.target.value)} placeholder="BK-2026-001" />
      </div>
      <div className="field">
        <label className="field-label">{t('def.form.title')}</label>
        <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="配方签核 / 请假申请" />
      </div>
      <div className="grid-2">
        <div className="field">
          <label className="field-label">
            {t('def.form.assignee')} <span className="opt">(single)</span>
          </label>
          <input value={assignee} onChange={(e) => setAssignee(e.target.value)} placeholder="alice" />
        </div>
        <div className="field">
          <label className="field-label">
            {t('def.form.approvers')} <span className="opt">(countersign)</span>
          </label>
          <input value={approvers} onChange={(e) => setApprovers(e.target.value)} placeholder="bob,carol" />
        </div>
      </div>
      <div className="field">
        <label className="field-label">
          {t('def.form.formData')} <span className="opt">{t('def.form.formDataHint')}</span>
        </label>
        {formRows.map((row, i) => (
          <div className="field-row form-data-row" key={i}>
            <input
              value={row.key}
              placeholder={t('def.form.fieldName')}
              onChange={(e) => setFormRows((rs) => rs.map((r, j) => (j === i ? { ...r, key: e.target.value } : r)))}
            />
            <input
              value={row.label}
              placeholder={t('def.form.fieldLabel')}
              onChange={(e) => setFormRows((rs) => rs.map((r, j) => (j === i ? { ...r, label: e.target.value } : r)))}
            />
            <select
              value={row.type}
              title={t('def.form.fieldType')}
              onChange={(e) => setFormRows((rs) => rs.map((r, j) => (j === i ? { ...r, type: e.target.value } : r)))}
            >
              <option value="text">{t('def.form.type.text')}</option>
              <option value="textarea">{t('def.form.type.textarea')}</option>
              <option value="number">{t('def.form.type.number')}</option>
              <option value="money">{t('def.form.type.money')}</option>
              <option value="boolean">{t('def.form.type.boolean')}</option>
              <option value="date">{t('def.form.type.date')}</option>
            </select>
            <input
              value={row.value}
              placeholder={t('def.form.fieldValue')}
              onChange={(e) => setFormRows((rs) => rs.map((r, j) => (j === i ? { ...r, value: e.target.value } : r)))}
            />
            <button
              className="icon-btn"
              title={t('common.delete')}
              onClick={() =>
                setFormRows((rs) =>
                  rs.length === 1 ? [{ key: '', label: '', type: 'text', value: '' }] : rs.filter((_, j) => j !== i),
                )
              }
            >
              <IconX width={13} height={13} />
            </button>
          </div>
        ))}
        <button
          className="btn btn-ghost btn-sm"
          onClick={() => setFormRows((rs) => [...rs, { key: '', label: '', type: 'text', value: '' }])}
        >
          <IconPlus width={13} height={13} /> {t('def.form.addField')}
        </button>
        <p className="field-hint">{t('def.form.formSchemaHint')}</p>
      </div>
      <div className="field">
        <label className="field-label">
          {t('def.form.variables')} <span className="opt">JSON</span>
        </label>
        <textarea rows={3} value={variables} onChange={(e) => setVariables(e.target.value)} placeholder='{"leader":"alice","manager":"bob"}' />
        <p className="field-hint">{t('def.form.variablesHint')}</p>
      </div>
    </Modal>
  );
}
