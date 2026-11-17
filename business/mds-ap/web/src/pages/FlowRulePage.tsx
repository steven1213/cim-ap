import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimCheckbox,
  CimInfoTable,
  CimModal,
  DataTable,
  FormGrid,
  Page,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  type FormOption,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useHasPermission } from '@/lib/usePermission';
import type { FlowRuleView, FlowView } from '@/types';

/**
 * 流程规则（FlowRulePage，V15）——独立规则层配置页。
 *
 * <p>规则锚定「版本族 familyCode + versionNo」：先选流程版本，再对该版本的规则
 * 增删改（仅 UNFROZEN 版本可维护，与画布编辑口径一致）+ 套用内置模板
 * （Q-time 超限挂起 / Bin 条件选路，按当前版本解析产物生成）+ evaluate
 * 调试面板（模拟 MES/MCS 传入运行期变量看命中与决策）。</p>
 */

const JOIN_POINTS = ['BEFORE_STEP_ENTER', 'AFTER_STEP_COMPLETE', 'BEFORE_DISPATCH'] as const;
const EFFECTS = ['ALLOW', 'HOLD', 'SCRAP', 'ROUTE_OVERRIDE', 'INSERT_SEQUENCE'] as const;
const EDGE_TYPES = ['MAIN', 'PASS', 'FAIL', 'REWORK'] as const;
const TPL_QTIME = 'QTIME_OVER_HOLD';
const TPL_BIN = 'BIN_ROUTE';

const NO_RULES: FlowRuleView[] = [];

export default function FlowRulePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const canUpdate = useHasPermission(MDS_PERMS.flow.update);

  const flowsRes = useResource(({ signal }) => api.listFlows(undefined, signal));
  const flows = flowsRes.data ?? [];
  const [flowId, setFlowId] = useState<string>('');
  const selectedFlow: FlowView | null = useMemo(
    () => flows.find((f) => f.id === flowId) ?? null,
    [flows, flowId],
  );
  const editable = selectedFlow?.status === 'UNFROZEN' && canUpdate;

  const rulesRes = useResource(
    ({ signal }) => (flowId ? api.listFlowRules(flowId, signal) : Promise.resolve(NO_RULES)),
    { deps: [flowId] },
  );
  const rules = rulesRes.data ?? NO_RULES;

  // 步骤/节点锚点下拉（详情解析产物）
  const detailRes = useResource(
    ({ signal }) => (flowId ? api.getFlow(flowId, signal) : Promise.resolve(null)),
    { deps: [flowId] },
  );
  const steps = detailRes.data?.steps ?? [];

  const [editing, setEditing] = useState<FlowRuleView | null>(null);
  const [creating, setCreating] = useState(false);
  const [evalOpen, setEvalOpen] = useState(false);

  const reload = () => {
    rulesRes.refresh();
  };

  const doDelete = async (rule: FlowRuleView) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      content: t('mds.flowRule.deleteConfirm', { code: rule.ruleCode }),
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteFlowRule(rule.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    reload();
  };

  const doToggle = async (rule: FlowRuleView, enabled: boolean) => {
    try {
      await api.updateFlowRule(rule.id, toPayload(rule, enabled));
      toast.success(t('mds.common.updated'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    reload();
  };

  const doTemplate = async (templateKey: string) => {
    const ok = await confirm({
      title: t(`mds.flowRule.template.${templateKey}`),
      content: t('mds.flowRule.templateConfirm', {
        flow: `${selectedFlow?.flowCode ?? ''} V${selectedFlow?.versionNo ?? ''}`,
      }),
      okKey: 'mds.flowRule.template.run',
    });
    if (!ok) return;
    try {
      const created = await api.applyFlowRuleTemplate(flowId, templateKey);
      toast.success(t('mds.flowRule.templateOk', { count: created.length }));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    reload();
  };

  const columns = useMemo<Array<DataTableColumn<FlowRuleView>>>(
    () => [
      { key: 'ruleCode', titleKey: 'mds.flowRule.field.ruleCode', width: 130, mono: true, ellipsis: true },
      { key: 'ruleName', titleKey: 'mds.flowRule.field.ruleName', width: 150, ellipsis: true },
      {
        key: 'joinPoint',
        titleKey: 'mds.flowRule.field.joinPoint',
        width: 150,
        render: (r) => t(`mds.flowRule.join.${r.joinPoint}`),
      },
      {
        key: 'anchor',
        titleKey: 'mds.flowRule.field.anchor',
        width: 130,
        ellipsis: true,
        render: (r) => r.stepCode ?? r.nodeId ?? '—',
        mono: true,
      },
      {
        key: 'conditionExpr',
        titleKey: 'mds.flowRule.field.condition',
        ellipsis: true,
        render: (r) => (
          <code className="mono">{r.conditionExpr || t('mds.flowRule.conditionAlways')}</code>
        ),
      },
      {
        key: 'effect',
        titleKey: 'mds.flowRule.field.effect',
        width: 150,
        render: (r) => (
          <span title={r.effectParam ?? ''}>
            {t(`mds.flowRule.effect.${r.effect}`)}
            {r.effectParam ? <span className="dim"> · {r.effectParam}</span> : null}
          </span>
        ),
      },
      { key: 'priority', titleKey: 'mds.flowRule.field.priority', width: 70, mono: true },
      {
        key: 'enabled',
        titleKey: 'mds.flowRule.field.enabled',
        width: 80,
        render: (r) => (
          <Can perm={MDS_PERMS.flow.update}>
            <CimCheckbox
              checked={r.enabled}
              title={editable ? undefined : t('mds.flowRule.readonlyHint')}
              onChange={(next) => void doToggle(r, next)}
            />
          </Can>
        ),
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 100,
        render: (r) => (
          <span className="cell-actions" onClick={(e) => e.stopPropagation()}>
            {editable && (
              <CimButton size="small" onClick={() => setEditing(r)}>
                {t('mds.common.edit')}
              </CimButton>
            )}
            {editable && (
              <CimButton size="small" danger onClick={() => void doDelete(r)}>
                {t('mds.common.delete')}
              </CimButton>
            )}
            {!editable && <span className="dim">—</span>}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, editable],
  );

  return (
    <Page>
      <div className="flow-rule-picker">
        <label>
          <span>{t('mds.flowRule.picker')}</span>
          <select className="cim-select" value={flowId} onChange={(e) => setFlowId(e.target.value)}>
            <option value="">—</option>
            {flows.map((f) => (
              <option key={f.id} value={f.id}>
                {`${f.flowCode} · V${f.versionNo ?? 1} · ${t(`mds.flow.status.${f.status}`)}`}
              </option>
            ))}
          </select>
        </label>
        {selectedFlow && selectedFlow.status !== 'UNFROZEN' && (
          <span className="dim">{t('mds.flowRule.readonlyHint')}</span>
        )}
        <div className="flow-rule-picker-ops">
          {editable && (
            <>
              <CimButton size="small" onClick={() => void doTemplate(TPL_QTIME)}>
                {t(`mds.flowRule.template.${TPL_QTIME}`)}
              </CimButton>
              <CimButton size="small" onClick={() => void doTemplate(TPL_BIN)}>
                {t(`mds.flowRule.template.${TPL_BIN}`)}
              </CimButton>
              <Can perm={MDS_PERMS.flow.update}>
                <CimButton type="primary" size="small" onClick={() => setCreating(true)}>
                  {t('mds.flowRule.create')}
                </CimButton>
              </Can>
            </>
          )}
          {selectedFlow && (
            <CimButton size="small" onClick={() => setEvalOpen(true)}>
              {t('mds.flowRule.eval.open')}
            </CimButton>
          )}
        </div>
      </div>

      <DataTable<FlowRuleView>
        columns={columns}
        rows={rules}
        rowKey={(r) => r.id}
        loading={rulesRes.loading || flowsRes.loading}
        error={rulesRes.error?.message ?? null}
        onRetry={reload}
        emptyTitleKey={flowId ? undefined : 'mds.flowRule.pickFirst'}
        showCount={false}
      />

      {creating && (
        <RuleFormModal
          flowId={flowId}
          steps={steps}
          onClose={() => setCreating(false)}
          onDone={() => {
            setCreating(false);
            toast.success(t('mds.common.created'));
            reload();
          }}
        />
      )}
      {editing && (
        <RuleFormModal
          flowId={flowId}
          steps={steps}
          initial={editing}
          onClose={() => setEditing(null)}
          onDone={() => {
            setEditing(null);
            toast.success(t('mds.common.updated'));
            reload();
          }}
        />
      )}
      {evalOpen && selectedFlow && (
        <EvaluateModal flow={selectedFlow} onClose={() => setEvalOpen(false)} />
      )}
    </Page>
  );
}

function toPayload(rule: FlowRuleView, enabled: boolean) {
  return {
    ruleCode: rule.ruleCode,
    ruleName: rule.ruleName,
    joinPoint: rule.joinPoint,
    stepCode: rule.stepCode,
    nodeId: rule.nodeId,
    edgeType: rule.edgeType,
    conditionExpr: rule.conditionExpr,
    effect: rule.effect,
    effectParam: rule.effectParam,
    priority: rule.priority,
    enabled,
  };
}

const RULE_TITLE = {
  editKey: 'mds.flowRule.editTitle',
  createKey: 'mds.flowRule.createTitle',
} as const;

/** 规则新建/编辑弹窗：全 FormGrid；步骤/节点/边锚点从当前版本解析产物下拉。 */
function RuleFormModal({
  flowId,
  steps,
  initial: initialProp,
  onClose,
  onDone,
}: {
  flowId: string;
  steps: Array<{ nodeId: string; stepCode: string; stepName?: string | null }>;
  initial?: FlowRuleView | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const initial = initialProp ?? null;
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    ruleCode: initial?.ruleCode ?? '',
    ruleName: initial?.ruleName ?? '',
    joinPoint: initial?.joinPoint ?? 'BEFORE_DISPATCH',
    stepCode: initial?.stepCode ?? '',
    nodeId: initial?.nodeId ?? '',
    edgeType: initial?.edgeType ?? '',
    conditionExpr: initial?.conditionExpr ?? '',
    effect: initial?.effect ?? 'HOLD',
    effectParam: initial?.effectParam ?? '',
    priority: initial?.priority ?? 100,
    enabled: initial?.enabled ?? true,
  });

  const stepOptions: FormOption[] = useMemo(
    () => steps.map((s) => ({ value: s.stepCode, label: `${s.stepCode} · ${s.stepName ?? ''}` })),
    [steps],
  );
  const nodeOptions: FormOption[] = useMemo(
    () => steps.map((s) => ({ value: s.nodeId, label: `${s.nodeId} · ${s.stepCode}` })),
    [steps],
  );

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      { kind: 'text', key: 'ruleCode', labelKey: 'mds.flowRule.field.ruleCode', required: true },
      { kind: 'text', key: 'ruleName', labelKey: 'mds.flowRule.field.ruleName' },
      {
        kind: 'select',
        key: 'joinPoint',
        labelKey: 'mds.flowRule.field.joinPoint',
        options: JOIN_POINTS.map((j) => ({ value: j, label: t(`mds.flowRule.join.${j}`) })),
      },
      {
        kind: 'select',
        key: 'effect',
        labelKey: 'mds.flowRule.field.effect',
        options: EFFECTS.map((e) => ({ value: e, label: t(`mds.flowRule.effect.${e}`) })),
      },
      {
        kind: 'select',
        key: 'stepCode',
        labelKey: 'mds.flowRule.field.stepCode',
        placeholderKey: 'mds.flowRule.anchorAny',
        options: [{ value: '', label: '—' }, ...stepOptions],
      },
      {
        kind: 'select',
        key: 'nodeId',
        labelKey: 'mds.flowRule.field.nodeId',
        placeholderKey: 'mds.flowRule.anchorAny',
        options: [{ value: '', label: '—' }, ...nodeOptions],
      },
      {
        kind: 'select',
        key: 'edgeType',
        labelKey: 'mds.flowRule.field.edgeType',
        placeholderKey: 'mds.flowRule.anchorAny',
        options: [{ value: '', label: '—' }, ...EDGE_TYPES.map((e) => ({ value: e, label: e }))],
      },
      { kind: 'number', key: 'priority', labelKey: 'mds.flowRule.field.priority' },
      {
        kind: 'textarea',
        key: 'conditionExpr',
        labelKey: 'mds.flowRule.field.condition',
        rows: 2,
        placeholderKey: 'mds.flowRule.conditionPlaceholder',
      },
      {
        kind: 'textarea',
        key: 'effectParam',
        labelKey: 'mds.flowRule.field.effectParam',
        rows: 2,
        placeholderKey: 'mds.flowRule.effectParamPlaceholder',
      },
      { kind: 'switch', key: 'enabled', labelKey: 'mds.flowRule.field.enabled' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, stepOptions, nodeOptions],
  );

  async function submit() {
    if (!form.ruleCode.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    const payload = {
      ruleCode: form.ruleCode.trim(),
      ruleName: form.ruleName.trim() || null,
      joinPoint: form.joinPoint,
      stepCode: form.stepCode || null,
      nodeId: form.nodeId || null,
      edgeType: form.edgeType || null,
      conditionExpr: form.conditionExpr.trim() || null,
      effect: form.effect,
      effectParam: form.effectParam.trim() || null,
      priority: Number(form.priority) || 100,
      enabled: form.enabled,
    };
    try {
      if (initial) {
        await api.updateFlowRule(initial.id, payload);
      } else {
        await api.createFlowRule(flowId, payload);
      }
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey={initial ? RULE_TITLE.editKey : RULE_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={720}
    >
      <p className="dim">{t('mds.flowRule.conditionHint')}</p>
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        columns={2}
      />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}

/** evaluate 调试弹窗：模拟运行期入参，看命中规则与最终决策。 */
function EvaluateModal({ flow, onClose }: { flow: FlowView; onClose: () => void }) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    joinPoint: 'BEFORE_DISPATCH',
    stepCode: '',
    nodeId: '',
    vars: 'bin=B1\nqty=24\nelapsedMinutes=999',
  });
  const [result, setResult] = useState<import('@/types').FlowRuleEvaluateResult | null>(null);

  const stepsRes = useResource(({ signal }) => api.getFlow(flow.id, signal));
  const steps = stepsRes.data?.steps ?? [];

  const run = async () => {
    setBusy(true);
    setErr('');
    try {
      const vars: Record<string, string | number | boolean> = {};
      for (const line of form.vars.split('\n')) {
        const kv = line.split('=');
        if (kv.length !== 2 || !kv[0].trim()) continue;
        const raw = kv[1].trim();
        vars[kv[0].trim()] = raw === 'true' || raw === 'false' ? raw === 'true'
          : Number.isNaN(Number(raw)) ? raw : Number(raw);
      }
      setResult(await api.evaluateFlowRules({
        flowId: flow.id,
        joinPoint: form.joinPoint,
        stepCode: form.stepCode || null,
        nodeId: form.nodeId || null,
        vars,
      }));
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="mds.flowRule.eval.title"
      footer={null}
      width={760}
    >
      <p className="dim">
        {t('mds.flowRule.eval.hint', { flow: `${flow.flowCode} V${flow.versionNo ?? 1}` })}
      </p>
      <FormGrid
        fields={[
          {
            kind: 'select',
            key: 'joinPoint',
            labelKey: 'mds.flowRule.field.joinPoint',
            options: JOIN_POINTS.map((j) => ({ value: j, label: t(`mds.flowRule.join.${j}`) })),
          },
          {
            kind: 'select',
            key: 'stepCode',
            labelKey: 'mds.flowRule.field.stepCode',
            placeholderKey: 'mds.flowRule.anchorAny',
            options: [{ value: '', label: '—' }, ...steps.map((s) => ({ value: s.stepCode, label: s.stepCode }))],
          },
          {
            kind: 'textarea',
            key: 'vars',
            labelKey: 'mds.flowRule.eval.vars',
            rows: 3,
            placeholderKey: 'mds.flowRule.eval.varsHint',
          },
        ]}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        columns={2}
      />
      <div className="flow-rule-eval-run">
        <CimButton type="primary" size="small" loading={busy} onClick={() => void run()}>
          {t('mds.flowRule.eval.run')}
        </CimButton>
      </div>
      {err ? <p className="cim-field-error">{err}</p> : null}
      {result && (
        <>
          <CimInfoTable
            record={result.decision}
            columns={2}
            items={[
              {
                key: 'action',
                labelKey: 'mds.flowRule.eval.decision',
                render: (d) => t(`mds.flowRule.decision.${d.action}`),
              },
              { key: 'ruleCode', labelKey: 'mds.flowRule.field.ruleCode', dataIndex: 'ruleCode', mono: true },
              { key: 'param', labelKey: 'mds.flowRule.field.effectParam', dataIndex: 'param', mono: true },
            ]}
          />
          {result.matched.length > 0 ? (
            <table className="naming-seg-table">
              <thead>
                <tr>
                  <th>{t('mds.flowRule.field.ruleCode')}</th>
                  <th>{t('mds.flowRule.field.effect')}</th>
                  <th>{t('mds.flowRule.field.priority')}</th>
                  <th>{t('mds.flowRule.eval.unknown')}</th>
                </tr>
              </thead>
              <tbody>
                {result.matched.map((h) => (
                  <tr key={h.ruleCode}>
                    <td className="mono">{h.ruleCode}</td>
                    <td>{t(`mds.flowRule.effect.${h.effect}`)}</td>
                    <td className="mono">{h.priority}</td>
                    <td className="mono">{h.unknownVars.length ? h.unknownVars.join(',') : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="dim">{t('mds.flowRule.eval.empty')}</p>
          )}
        </>
      )}
    </CimModal>
  );
}
