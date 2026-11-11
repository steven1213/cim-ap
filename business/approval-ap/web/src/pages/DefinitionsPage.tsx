import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import { IconPencil, IconPlay, IconPlus, IconX } from '@/components/Icons';
import {
  duplicateDefinition,
  getDefinitionXml,
  listDefinitions,
  startProcess,
  suspendDefinition,
} from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { parseVariables } from '@/lib/format';
import type { FormSchemaField, ProcessDefinitionView, StartProcessRequest } from '@/types';

/** 从 BPMN 提取角色型审批节点（G6 / D8）：userTask 上 flowable:candidateGroups="${ccg_<nodeId>}"。 */
function parseRoleNodes(xml: string): { nodeId: string; name: string }[] {
  try {
    const doc = new DOMParser().parseFromString(xml, 'text/xml');
    if (doc.querySelector('parsererror')) return [];
    const tasks = Array.from(doc.getElementsByTagNameNS('*', 'userTask'));
    const out: { nodeId: string; name: string }[] = [];
    for (const t of tasks) {
      const cg = t.getAttribute('flowable:candidateGroups') || '';
      const m = cg.match(/ccg_([A-Za-z0-9_]+)/);
      if (m) out.push({ nodeId: m[1], name: t.getAttribute('name') || '角色审批' });
    }
    return out;
  } catch {
    return [];
  }
}

export default function DefinitionsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const toast = useToast();
  const { userId, tenantId } = useIdentityStore();

  const [showStart, setShowStart] = useState(false);
  const [busy, setBusy] = useState(false);
  /** 逐行锁：只锁被操作的那行。 */
  const [busyDef, setBusyDef] = useState<string | null>(null);

  const defsRes = useResource<ProcessDefinitionView[]>(({ signal }) => listDefinitions({ signal }));
  const defs = defsRes.data ?? [];

  /** 同 key 最新版本 → 可编辑；历史版本 → 只可查看。 */
  const latestVersionOf = useMemo(() => {
    const m = new Map<string, number>();
    for (const d of defs) m.set(d.key, Math.max(m.get(d.key) ?? 0, d.version));
    return m;
  }, [defs]);
  const isLatest = (d: ProcessDefinitionView) => latestVersionOf.get(d.key) === d.version;

  async function onDuplicate(d: ProcessDefinitionView) {
    setBusyDef(d.id);
    try {
      const copy = await duplicateDefinition(d.id);
      toast.success(t('def.duplicateOk') + '：' + copy.key);
      defsRes.refresh();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusyDef(null);
    }
  }

  async function onToggleSuspend(d: ProcessDefinitionView) {
    setBusyDef(d.id);
    try {
      await suspendDefinition(d.id, !d.suspended);
      toast.success(d.suspended ? t('def.activateOk') : t('def.suspendOk'));
      defsRes.refresh();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusyDef(null);
    }
  }

  const columns: Array<DataTableColumn<ProcessDefinitionView>> = [
    { key: 'key', titleKey: 'def.colKey', width: 170, mono: true },
    { key: 'name', titleKey: 'def.colName', width: 180 },
    { key: 'version', titleKey: 'def.colVersion', width: 80, align: 'right', render: (d) => 'v' + d.version },
    {
      key: 'status',
      titleKey: 'def.colStatus',
      width: 190,
      render: (d) => (
        <span className="cell-inline">
          <CimTag tone={isLatest(d) ? 'ok' : 'idle'}>{t(isLatest(d) ? 'def.status.latest' : 'def.status.history')}</CimTag>
          {d.suspended && <CimTag tone="warn">{t('def.suspended')}</CimTag>}
        </span>
      ),
    },
    { key: 'tenantId', titleKey: 'def.colTenant', width: 120, render: (d) => d.tenantId ?? '—' },
    {
      key: 'ops',
      titleKey: 'common.actions',
      width: 420,
      render: (d) => {
        const latest = isLatest(d);
        const rowBusy = busyDef === d.id;
        return (
          <span className="cell-actions">
            {latest ? (
              <CimButton
                size="small"
                type="primary"
                icon={<IconPencil width={13} height={13} />}
                onClick={() => navigate('/designer', { state: { definitionId: d.id } })}
              >
                {t('def.action.design')}
              </CimButton>
            ) : (
              <CimButton
                size="small"
                onClick={() => navigate('/designer', { state: { definitionId: d.id, readonly: true } })}
              >
                {t('def.action.view')}
              </CimButton>
            )}
            {latest && !d.suspended && (
              <CimButton size="small" onClick={() => setShowStart(true)}>
                {t('def.start')}
              </CimButton>
            )}
            {latest && (
              <>
                <CimButton size="small" disabled={rowBusy} onClick={() => onDuplicate(d)}>
                  {t('def.duplicate')}
                </CimButton>
                <CimButton size="small" disabled={rowBusy} onClick={() => onToggleSuspend(d)}>
                  {d.suspended ? t('def.activate') : t('def.suspendAction')}
                </CimButton>
              </>
            )}
          </span>
        );
      },
    },
  ];

  return (
    <Page>
      <Panel
        titleKey="def.title"
        subKey="def.sub"
        actions={
          <>
            <CimButton size="small" icon={<IconPencil width={13} height={13} />} onClick={() => navigate('/designer')}>
              {t('def.newDesign')}
            </CimButton>
            <CimButton size="small" type="primary" icon={<IconPlay width={13} height={13} />} onClick={() => setShowStart(true)}>
              {t('def.start')}
            </CimButton>
          </>
        }
        flush
      >
        <DataTable<ProcessDefinitionView>
          flat
          columns={columns}
          rows={defs}
          rowKey={(d) => d.id}
          loading={defsRes.loading}
          error={defsRes.error ? defsRes.error.message : null}
          onRetry={defsRes.refresh}
          onRefresh={defsRes.refresh}
          emptyTitleKey="common.empty"
          scrollX="max-content"
        />
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
              toast.success(t('def.started') + r.processInstanceId);
              setShowStart(false);
              defsRes.refresh();
            } catch (e) {
              toast.error((e as Error).message);
            } finally {
              setBusy(false);
            }
          }}
        />
      )}
    </Page>
  );
}

// ── 发起表单 ────────────────────────────────────────────────

/** 草稿按 processKey 分槽，避免不同定义的字段串台。 */
const draftKeyOf = (pk: string) => `approval.draft.v1:${pk}`;
/** 旧版单槽（无 processKey 维度）的键，只用于读取它的 processKey 后按槽迁移。 */
const LEGACY_DRAFT_KEY = 'approval.draft.v1';

interface DraftShape {
  processKey: string;
  businessKey: string;
  title: string;
  assignee: string;
  approvers: string;
  variables: string;
  formRows: { key: string; label: string; type: string; value: string }[];
}

interface StartForm {
  processKey: string;
  businessKey: string;
  title: string;
  assignee: string;
  approvers: string;
  variables: string;
}

type FormRow = { key: string; label: string; type: string; value: string };

const EMPTY_ROW: FormRow = { key: '', label: '', type: 'text', value: '' };

/**
 * 只读**指定 processKey 的槽**。
 *
 * <p>改造前是「遍历 localStorage 取第一个 `approval.draft.v1:` 开头的草稿」：
 * 同时存在两个流程的草稿时，打开 A 的发起表单可能加载到 B 的内容（并且把 processKey
 * 也切到 B）。按 key 精确读槽，行为才是确定的。</p>
 */
function loadDraft(processKey: string): DraftShape | null {
  try {
    const raw = localStorage.getItem(draftKeyOf(processKey)) ?? localStorage.getItem(LEGACY_DRAFT_KEY);
    if (!raw) return null;
    const d = JSON.parse(raw);
    if (!d || typeof d !== 'object') return null;
    // 旧单槽草稿没有 processKey 维度：只在它属于当前流程时才采用
    if (d.processKey && d.processKey !== processKey) return null;
    return d as DraftShape;
  } catch {
    return null;
  }
}

function StartModal({
  defs,
  defaultTenant,
  defaultInitiator,
  busy,
  onClose,
  onSubmit,
}: {
  defs: ProcessDefinitionView[];
  defaultTenant: string;
  defaultInitiator: string;
  busy: boolean;
  onClose: () => void;
  onSubmit: (req: StartProcessRequest) => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();

  /** 发起永远走同 key 最新版本（引擎亦如此），下拉去重。 */
  const latestDefs = useMemo(() => {
    const byKey = new Map<string, ProcessDefinitionView>();
    for (const d of defs) {
      const cur = byKey.get(d.key);
      if (!cur || d.version > cur.version) byKey.set(d.key, d);
    }
    return [...byKey.values()];
  }, [defs]);

  const [saved] = useState<DraftShape | null>(() => loadDraft(latestDefs[0]?.key ?? ''));
  const [form, setForm] = useState<StartForm>({
    processKey: saved?.processKey || latestDefs[0]?.key || '',
    businessKey: saved?.businessKey ?? '',
    title: saved?.title ?? '',
    assignee: saved?.assignee ?? '',
    approvers: saved?.approvers ?? '',
    variables: saved?.variables ?? '',
  });
  const [formRows, setFormRows] = useState<FormRow[]>(
    saved?.formRows?.length ? saved.formRows : [EMPTY_ROW],
  );
  const [roleInput, setRoleInput] = useState<Record<string, string>>({});
  const [draftRestored, setDraftRestored] = useState(
    !!saved && !!(saved.businessKey?.trim() || saved.title?.trim()),
  );

  /** 会签定义（key 含 countersign）需要 approvers 列表；其余显示单人 assignee。 */
  const isCountersign = form.processKey.toLowerCase().includes('countersign');

  /**
   * 角色型节点：解析选中定义 BPMN 里 `ccg_<id>` 引用的审批节点。
   *
   * <p>改造前这个 effect 的依赖里放了每次 render 新建的 `Map`，等于每一次渲染都重发
   * 一次 XML 请求，而回包又 setState → 再渲染 → 再请求（自激循环）。这里只依赖
   * **定义 id** 这个稳定值。</p>
   */
  const defId = useMemo(
    () => latestDefs.find((d) => d.key === form.processKey)?.id ?? '',
    [latestDefs, form.processKey],
  );
  const xmlRes = useResource(({ signal }) => getDefinitionXml(defId, { signal }), {
    deps: [defId],
    enabled: !!defId,
  });
  const roleNodes = useMemo(() => (xmlRes.data ? parseRoleNodes(xmlRes.data.bpmnXml) : []), [xmlRes.data]);

  // 草稿自动暂存：有任何实质内容即写该流程的槽
  useEffect(() => {
    const hasContent =
      form.businessKey.trim() || form.title.trim() || formRows.some((r) => r.key.trim() || r.value.trim());
    if (!hasContent) return;
    const draft: DraftShape = { ...form, formRows };
    try {
      localStorage.setItem(draftKeyOf(form.processKey), JSON.stringify(draft));
      localStorage.removeItem(LEGACY_DRAFT_KEY);
    } catch {
      /* 存储满等异常静默 */
    }
  }, [form, formRows]);

  const fields: Array<FormField<StartForm>> = [
    {
      key: 'processKey',
      labelKey: 'def.form.processKey',
      kind: 'select',
      required: true,
      options: latestDefs.map((d) => ({ value: d.key, label: `${d.name}`, hint: d.key })),
      span: 2,
    },
    { key: 'businessKey', labelKey: 'def.form.businessKey', kind: 'text', required: true, mono: true, placeholder: 'BK-2026-001' },
    { key: 'title', labelKey: 'def.form.title', kind: 'text', required: true, placeholder: '配方签核 / 请假申请' },
    isCountersign
      ? {
          key: 'approvers',
          labelKey: 'def.form.approvers',
          kind: 'text',
          required: true,
          placeholder: 'bob,carol',
          hint: 'countersign · comma separated',
          span: 2,
        }
      : { key: 'assignee', labelKey: 'def.form.assignee', kind: 'text', placeholder: 'alice', hint: 'single', span: 2 },
    { key: 'variables', labelKey: 'def.form.variables', kind: 'textarea', rows: 3, span: 2, hintKey: 'def.form.variablesHint' },
  ];

  function submit() {
    if (!form.processKey || !form.businessKey || !form.title) {
      toast.warning(
        t('common.confirm') +
          '：' +
          [t('def.form.processKey'), t('def.form.businessKey'), t('def.form.title')].join(' / '),
      );
      return;
    }
    const vars = parseVariables(form.variables);
    if (form.variables.trim() && vars === null) {
      toast.warning(t('def.form.variables') + ' JSON ' + t('common.empty'));
      return;
    }
    const formData: Record<string, unknown> = {};
    const formSchema: FormSchemaField[] = [];
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
      if (r.label.trim()) formSchema.push({ key, label: r.label.trim(), type: r.type });
    }
    const candidateGroups: Record<string, string[]> = {};
    for (const rn of roleNodes) {
      const raw = (roleInput[rn.nodeId] ?? '').trim();
      if (raw) candidateGroups[rn.nodeId] = raw.split(',').map((s) => s.trim()).filter(Boolean);
    }
    onSubmit({
      processKey: form.processKey,
      businessKey: form.businessKey,
      tenantId: defaultTenant,
      initiator: defaultInitiator,
      title: form.title,
      assignee: form.approvers.trim() ? null : form.assignee.trim() || null,
      approvers: form.approvers.trim() ? form.approvers.split(',').map((s) => s.trim()).filter(Boolean) : null,
      variables: vars,
      formData: Object.keys(formData).length > 0 ? formData : null,
      formSchema: formSchema.length > 0 ? formSchema : null,
      candidateGroups: Object.keys(candidateGroups).length > 0 ? candidateGroups : null,
    });
    // 提交成功路径由父组件关闭弹窗；无论成败先清草稿（表单内容仍在弹窗中，失败可重试）
    localStorage.removeItem(draftKeyOf(form.processKey));
    setDraftRestored(false);
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="def.startTitle"
      width={720}
      onOk={submit}
      okLoading={busy}
      okKey="common.submit"
      cancelKey="common.cancel"
    >
      {draftRestored && (
        <div className="cim-field-hint" style={{ color: 'var(--warn)', marginBottom: 8 }}>
          {t('def.form.draftRestored')}
          <CimButton
            size="small"
            type="link"
            onClick={() => {
              localStorage.removeItem(draftKeyOf(form.processKey));
              setDraftRestored(false);
            }}
          >
            {t('def.form.draftDiscard')}
          </CimButton>
        </div>
      )}

      <FormGrid<StartForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
      />

      {/* G6 角色选人：每个角色型审批节点收集一组角色 key（逗号分隔）。
          节点数是后端 BPMN 决定的，没法写成 FormGrid 的静态字段数组。 */}
      {roleNodes.length > 0 && (
        <div className="field" style={{ marginTop: 10 }}>
          <label className="field-label">{t('def.form.roleCandidates')}</label>
          <p className="cim-field-hint" style={{ marginBottom: 6 }}>{t('def.form.roleCandidatesHint')}</p>
          <div className="role-node-list">
            {roleNodes.map((rn) => (
              <div className="field-row" key={rn.nodeId}>
                <span className="role-node-name">{rn.name}</span>
                <input
                  className="mono"
                  value={roleInput[rn.nodeId] ?? ''}
                  onChange={(e) => setRoleInput((p) => ({ ...p, [rn.nodeId]: e.target.value }))}
                  placeholder="manager,finance"
                />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 业务表单字段：行数由用户增删，同样不适合静态字段数组 */}
      <div className="field" style={{ marginTop: 10 }}>
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
              type="button"
              title={t('common.delete')}
              onClick={() =>
                setFormRows((rs) => (rs.length === 1 ? [EMPTY_ROW] : rs.filter((_, j) => j !== i)))
              }
            >
              <IconX width={13} height={13} />
            </button>
          </div>
        ))}
        <CimButton size="small" icon={<IconPlus width={13} height={13} />} onClick={() => setFormRows((rs) => [...rs, EMPTY_ROW])}>
          {t('def.form.addField')}
        </CimButton>
        <p className="cim-field-hint">{t('def.form.formSchemaHint')}</p>
      </div>
    </CimModal>
  );
}
