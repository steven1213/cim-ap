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
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type {
  NamingCounterView,
  NamingRule,
  NamingRuleObjectSpec,
  NamingRulePreviewRes,
  NamingRulePreviewSegment,
  NamingSegment,
} from '@/types';

/**
 * 编码规则（NamingRulePage）——左侧规则列表，右侧三块：规则信息 / 段配置 / 试算与计数器。
 *
 * <p>配置化 Key Generation（MES 行业惯例）：规则模板 = 有序段列表（列表顺序即编码顺序），
 * 段类型 LITERAL/ENUM/DATE/SEQ 各有后端解析器，每段声明字符长度（整码定长）。
 * 各业务域 create 同事务调 apply 守门——编码留空+规则启用=自动占号；手填不匹配段派生
 * 正则=拒绝；规则停用=放行回退人工命名。objectType 建后不可改；同对象至多一条启用规则（409）。</p>
 *
 * <p>渲染走 @cim/ui 公用组件；段编辑器为动态嵌套列表（一规则多段、每段参数面随类型变化），
 * 无对应公用组件，与状态矩阵同口径属特例：原生控件 + styles.css 布局类，不写皮肤样式。</p>
 */

const NO_RULES: NamingRule[] = [];
const NO_SPECS: NamingRuleObjectSpec[] = [];
const NO_COUNTERS: NamingCounterView[] = [];

const SEGMENT_TYPES = ['LITERAL', 'VAR', 'ENUM', 'DATE', 'SEQ'] as const;
const SEQ_SCOPES = ['GLOBAL', 'BY_PARENT', 'BY_LOCATION', 'BY_TYPE', 'BY_DATE'] as const;

/** 新段的默认配置（类型对应）。 */
function defaultSegment(type: NamingSegment['type']): NamingSegment {
  switch (type) {
    case 'LITERAL':
      return { type, text: '', length: undefined };
    case 'ENUM':
      return { type, var: undefined, dictKey: '', values: undefined, length: 2 };
    case 'VAR':
      return { type, var: undefined };
    case 'DATE':
      return { type, pattern: 'yyyyMMdd' };
    case 'SEQ':
      return { type, length: 3, start: 1, scope: 'GLOBAL' };
  }
}

/** 段摘要（列表列与详情展示）：如 `LITERAL"EQP-"(4)` / `SEQ[GLOBAL]宽3`。 */
function segmentSummary(seg: NamingSegment, t: (k: string) => string): string {
  switch (seg.type) {
    case 'LITERAL':
      return `"${seg.text ?? ''}"·${seg.length ?? (seg.text ?? '').length}`;
    case 'ENUM':
      return `{${seg.var ?? '?'}}=${seg.dictKey ? `dict:${seg.dictKey}` : (seg.values ?? []).join('|')}`;
    case 'VAR':
      return `{${seg.var ?? '?'}}`;
    case 'DATE':
      return seg.pattern ?? '';
    case 'SEQ':
      return `${t(`mds.namingRule.scope.${seg.scope ?? 'GLOBAL'}`)}·${seg.length ?? '?'}`;
  }
}

export default function NamingRulePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const rulesRes = useResource(({ signal }) => api.listNamingRules(signal));
  const specsRes = useResource(({ signal }) => api.listNamingObjectTypes(signal));
  const rules = rulesRes.data ?? NO_RULES;
  const specs = specsRes.data ?? NO_SPECS;

  const [selected, setSelected] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');
  const [formState, setFormState] = useState<
    { mode: 'create' } | { mode: 'edit'; rule: NamingRule } | null
  >(null);

  const rows = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return rules;
    return rules.filter(
      (r) =>
        r.ruleName.toLowerCase().includes(kw) ||
        r.objectType.toLowerCase().includes(kw) ||
        r.segments.some((s) => segmentSummary(s, t).toLowerCase().includes(kw)),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rules, keyword, t]);

  const active = useMemo(
    () => rules.find((r) => r.id === selected) ?? rules[0] ?? null,
    [rules, selected],
  );
  const activeSpec = useMemo(
    () => (active ? specs.find((s) => s.code === active.objectType) ?? null : null),
    [specs, active],
  );

  const specLabel = (code: string) => {
    const spec = specs.find((s) => s.code === code);
    return spec ? `${code} · ${spec.label}` : code;
  };

  const doDelete = async (rule: NamingRule) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      content: t('mds.namingRule.deleteConfirm', { name: rule.ruleName }),
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteNamingRule(rule.id);
      toast.success(t('mds.common.deleted'));
      setSelected(null);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    rulesRes.refresh();
  };

  const doToggle = async (rule: NamingRule, enabled: boolean) => {
    try {
      await api.updateNamingRule(rule.id, {
        ruleName: rule.ruleName,
        segments: rule.segments,
        enabled,
      });
      toast.success(t('mds.common.updated'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    rulesRes.refresh();
  };

  const doCopy = async (rule: NamingRule) => {
    try {
      const copied = await api.copyNamingRule(rule.id);
      toast.success(t('mds.common.created'));
      setSelected(copied.id);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    rulesRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<NamingRule>>>(
    () => [
      { key: 'ruleName', titleKey: 'mds.namingRule.field.name', width: 180 },
      { key: 'objectType', titleKey: 'mds.namingRule.field.objectType', width: 130, mono: true },
      {
        key: 'segments',
        titleKey: 'mds.namingRule.field.segments',
        render: (r) => (
          <code className="mono">{r.segments.map((s) => segmentSummary(s, t)).join(' ')}</code>
        ),
      },
      {
        key: 'enabled',
        titleKey: 'mds.namingRule.field.enabled',
        width: 90,
        render: (r) => (
          <Can perm={MDS_PERMS.namingRule.update}>
            <CimCheckbox
              checked={r.enabled}
              title={r.enabled ? t('mds.namingRule.clickToDisable') : t('mds.namingRule.clickToEnable')}
              onChange={(next) => void doToggle(r, next)}
            />
          </Can>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, specs],
  );

  return (
    <Page>
      <div className="state-model-layout">
        <DataTable<NamingRule>
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id}
          rowClassName={(r) => (active?.id === r.id ? 'row-selected' : '')}
          onRowClick={(r) => setSelected(r.id)}
          loading={rulesRes.loading}
          error={rulesRes.error?.message ?? null}
          onRetry={rulesRes.refresh}
          headActions={
            <Can perm={MDS_PERMS.namingRule.create}>
              <CimButton type="primary" size="small" onClick={() => setFormState({ mode: 'create' })}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
          search={{
            value: keyword,
            onChange: setKeyword,
            placeholderKey: 'mds.namingRule.searchPlaceholder',
          }}
        />

        {active && (
          <div className="state-model-main">
            {/* 块一：规则信息（objectType 建后不可改） */}
            <Panel
              titleKey="mds.namingRule.info"
              actions={
                <>
                  <Can perm={MDS_PERMS.namingRule.update}>
                    <CimButton
                      size="small"
                      onClick={() => setFormState({ mode: 'edit', rule: active })}
                    >
                      {t('mds.common.edit')}
                    </CimButton>
                  </Can>
                  <Can perm={MDS_PERMS.namingRule.create}>
                    <CimButton size="small" onClick={() => void doCopy(active)}>
                      {t('mds.namingRule.copy')}
                    </CimButton>
                  </Can>
                  <Can perm={MDS_PERMS.namingRule.delete}>
                    <CimButton size="small" danger onClick={() => void doDelete(active)}>
                      {t('mds.common.delete')}
                    </CimButton>
                  </Can>
                </>
              }
            >
              <CimInfoTable
                record={active}
                columns={2}
                items={[
                  { key: 'ruleName', labelKey: 'mds.namingRule.field.name', dataIndex: 'ruleName' },
                  { key: 'objectType', labelKey: 'mds.namingRule.field.objectType', render: (r) => specLabel(r.objectType), mono: true },
                  {
                    key: 'segments',
                    labelKey: 'mds.namingRule.field.segments',
                    render: (r) => r.segments.map((s) => segmentSummary(s, t)).join('  '),
                    mono: true,
                  },
                  {
                    key: 'enabled',
                    labelKey: 'mds.namingRule.field.enabled',
                    render: (r) => (r.enabled ? t('mds.topo.enabled') : t('mds.namingRule.disabled')),
                  },
                  { key: 'remark', labelKey: 'mds.common.remark', dataIndex: 'remark' },
                ]}
              />
            </Panel>

            {/* 块二：段配置明细（编辑在弹窗内） */}
            <Panel
              titleKey="mds.namingRule.segmentsTitle"
              sub={t('mds.namingRule.segmentsMeta', {
                vars: activeSpec ? activeSpec.vars.map((v) => `{${v}}`).join(' ') : '—',
              })}
            >
              {active.segments.length > 0 ? (
                <table className="naming-seg-table">
                  <thead>
                    <tr>
                      <th>{t('mds.namingRule.seg.index')}</th>
                      <th>{t('mds.namingRule.seg.type')}</th>
                      <th>{t('mds.namingRule.seg.config')}</th>
                      <th>{t('mds.namingRule.seg.length')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {active.segments.map((seg, i) => (
                      <tr key={i}>
                        <td className="mono">{i + 1}</td>
                        <td className="mono">{seg.type}</td>
                        <td className="mono">{segmentSummary(seg, t)}</td>
                        <td className="mono">{seg.length ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <p className="dim">{t('mds.namingRule.segmentsEmpty')}</p>
              )}
            </Panel>

            {/* 块三：试算（逐段）+ 计数器（只读） */}
            <PreviewPanel rule={active} />
            <CounterPanel ruleId={active.id} />
          </div>
        )}
      </div>

      {formState && (
        <RuleFormModal
          initial={formState.mode === 'edit' ? formState.rule : null}
          specs={specs}
          onClose={() => setFormState(null)}
          onDone={() => {
            const wasCreate = formState.mode === 'create';
            setFormState(null);
            toast.success(t(wasCreate ? 'mds.common.created' : 'mds.common.updated'));
            rulesRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

/** 试算面板：逐段渲染明细 + 示例码 + 派生正则（缺的变量用 <VAR> 占位），不占号。 */
function PreviewPanel({ rule }: { rule: NamingRule }) {
  const { t } = useTranslation();
  const [result, setResult] = useState<NamingRulePreviewRes | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const run = async () => {
    setBusy(true);
    setErr('');
    try {
      setResult(await api.previewNamingRule({
        objectType: rule.objectType,
        segments: rule.segments,
      }));
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Panel
      titleKey="mds.namingRule.previewTitle"
      actions={
        <Can perm={MDS_PERMS.namingRule.view}>
          <CimButton size="small" loading={busy} onClick={() => void run()}>
            {t('mds.namingRule.previewRun')}
          </CimButton>
        </Can>
      }
    >
      {err ? <p className="cim-field-error">{err}</p> : null}
      {!result ? (
        <p className="dim">{t('mds.namingRule.previewHint')}</p>
      ) : (
        <>
          <CimInfoTable
            record={result}
            columns={1}
            items={[
              { key: 'example', labelKey: 'mds.namingRule.previewExample', dataIndex: 'example', mono: true },
              { key: 'regex', labelKey: 'mds.namingRule.previewRegex', dataIndex: 'regex', mono: true },
              {
                key: 'missing',
                labelKey: 'mds.namingRule.previewMissing',
                render: (r) => (r.missingVars.length > 0 ? r.missingVars.map((m) => `{${m}}`).join(' ') : '—'),
              },
            ]}
          />
          {result.segmentResults.length > 0 && (
            <table className="naming-seg-table">
              <thead>
                <tr>
                  <th>{t('mds.namingRule.seg.index')}</th>
                  <th>{t('mds.namingRule.seg.type')}</th>
                  <th>{t('mds.namingRule.seg.rendered')}</th>
                </tr>
              </thead>
              <tbody>
                {result.segmentResults.map((s: NamingRulePreviewSegment) => (
                  <tr key={s.index}>
                    <td className="mono">{s.index + 1}</td>
                    <td className="mono">{s.type}</td>
                    <td className="mono">{s.rendered}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </Panel>
  );
}

/** 计数器面板：各 SEQ 段各桶当前值（只读；号段只增不回收，不提供重置）。 */
function CounterPanel({ ruleId }: { ruleId: string }) {
  const { t } = useTranslation();
  const countersRes = useResource(
    ({ signal }) => api.listNamingCounters(ruleId, signal),
    { deps: [ruleId] },
  );
  const counters = countersRes.data ?? NO_COUNTERS;

  return (
    <Panel titleKey="mds.namingRule.countersTitle">
      {counters.length > 0 ? (
        <CimInfoTable
          record={counters[0]}
          columns={1}
          items={[{
            key: 'all',
            labelKey: 'mds.namingRule.countersAll',
            render: () => (
              <span className="mono">
                {counters.map((c) => `#${c.segmentIndex + 1} ${c.scopeKey}=${c.currentValue}`).join('  ')}
              </span>
            ),
          }]}
        />
      ) : (
        <p className="dim">{t('mds.namingRule.countersEmpty')}</p>
      )}
    </Panel>
  );
}

const RULE_TITLE = {
  editKey: 'mds.namingRule.editTitle',
  createKey: 'mds.namingRule.createTitle',
} as const;

/**
 * 规则新建/编辑弹窗：基础信息走 FormGrid；段列表为动态嵌套结构（每段参数面随类型变化），
 * 用受控卡片列表（原生控件 + 布局类，与状态矩阵同口径的特例）。
 */
function RuleFormModal({
  initial,
  specs,
  onClose,
  onDone,
}: {
  initial: NamingRule | null;
  specs: NamingRuleObjectSpec[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [objectType, setObjectType] = useState(
    initial?.objectType ?? specs[0]?.code ?? 'EQUIPMENT',
  );
  const [form, setForm] = useState({
    ruleName: initial?.ruleName ?? '',
    enabled: initial?.enabled ?? false,
    remark: initial?.remark ?? '',
  });
  const [segments, setSegments] = useState<NamingSegment[]>(
    initial?.segments?.map((s) => ({ ...s })) ??
      [defaultSegment('LITERAL'), defaultSegment('SEQ')],
  );

  const spec = specs.find((s) => s.code === objectType) ?? null;

  const patchSegment = (index: number, patch: Partial<NamingSegment>) => {
    setSegments((prev) => prev.map((s, i) => (i === index ? { ...s, ...patch } : s)));
  };
  const moveSegment = (index: number, delta: -1 | 1) => {
    setSegments((prev) => {
      const next = [...prev];
      const target = index + delta;
      if (target < 0 || target >= next.length) return prev;
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };
  const removeSegment = (index: number) => {
    setSegments((prev) => prev.filter((_, i) => i !== index));
  };
  const addSegment = (type: NamingSegment['type']) => {
    setSegments((prev) => [...prev, defaultSegment(type)]);
  };

  const baseFields = useMemo<Array<FormField<typeof form>>>(
    () => [
      { kind: 'text' as const, key: 'ruleName' as const, labelKey: 'mds.namingRule.field.name', required: true },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.namingRule.field.enabled' },
      { kind: 'text' as const, key: 'remark' as const, labelKey: 'mds.common.remark' },
    ],
    [],
  );

  async function submit() {
    if (!form.ruleName.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    if (segments.length === 0) {
      setErr(t('mds.namingRule.segmentsEmpty'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      if (initial) {
        await api.updateNamingRule(initial.id, {
          ruleName: form.ruleName.trim(),
          segments,
          enabled: form.enabled,
          remark: form.remark || null,
        });
      } else {
        await api.createNamingRule({
          objectType,
          ruleName: form.ruleName.trim(),
          segments,
          enabled: form.enabled,
          remark: form.remark || null,
        });
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
      width={680}
    >
      {!initial && (
        <div className="cim-field">
          <label className="cim-field-label">{t('mds.namingRule.field.objectType')}</label>
          <select
            className="cim-select"
            value={objectType}
            onChange={(e) => setObjectType(e.target.value)}
          >
            {specs.map((s) => (
              <option key={s.code} value={s.code}>{`${s.code} · ${s.label}`}</option>
            ))}
          </select>
        </div>
      )}
      <FormGrid
        fields={baseFields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        columns={2}
      />

      <p className="dim naming-seg-hint">
        {t('mds.namingRule.segmentsEditorHint', {
          vars: spec ? spec.vars.map((v) => `{${v}}`).join(' ') : '—',
        })}
      </p>

      {/* 段列表编辑器：顺序即编码顺序（上移/下移调序） */}
      <div className="naming-seg-list">
        {segments.map((seg, i) => (
          <div key={i} className="naming-seg-card">
            <div className="naming-seg-head">
              <span className="mono">{i + 1}</span>
              <select
                className="cim-select"
                value={seg.type}
                onChange={(e) => patchSegment(i, defaultSegment(e.target.value as NamingSegment['type']))}
              >
                {SEGMENT_TYPES.map((tp) => (
                  <option key={tp} value={tp}>{t(`mds.namingRule.type.${tp}`)}</option>
                ))}
              </select>
              <div className="naming-seg-ops">
                <button type="button" className="cim-btn" disabled={i === 0}
                        onClick={() => moveSegment(i, -1)}>{t('mds.namingRule.seg.up')}</button>
                <button type="button" className="cim-btn" disabled={i === segments.length - 1}
                        onClick={() => moveSegment(i, 1)}>{t('mds.namingRule.seg.down')}</button>
                <button type="button" className="cim-btn" onClick={() => removeSegment(i)}>
                  {t('mds.namingRule.seg.remove')}
                </button>
              </div>
            </div>
            <div className="naming-seg-body">
              {seg.type === 'LITERAL' && (
                <label>
                  <span>{t('mds.namingRule.seg.text')}</span>
                  <input className="cim-input mono" value={seg.text ?? ''} onChange={(e) => patchSegment(i, { text: e.target.value, length: e.target.value.length })} />
                </label>
              )}
              {seg.type === 'VAR' && (
                <label>
                  <span>{t('mds.namingRule.seg.var')}</span>
                  <select className="cim-select" value={seg.var ?? ''}
                          onChange={(e) => patchSegment(i, { var: e.target.value })}>
                    <option value="">—</option>
                    {(spec?.vars ?? []).map((v) => <option key={v} value={v}>{`{${v}}`}</option>)}
                  </select>
                </label>
              )}
              {seg.type === 'ENUM' && (
                <>
                  <label>
                    <span>{t('mds.namingRule.seg.var')}</span>
                    <select className="cim-select" value={seg.var ?? ''}
                            onChange={(e) => patchSegment(i, { var: e.target.value })}>
                      <option value="">—</option>
                      {(spec?.vars ?? []).map((v) => <option key={v} value={v}>{`{${v}}`}</option>)}
                    </select>
                  </label>
                  <label>
                    <span>{t('mds.namingRule.seg.dictKey')}</span>
                    <input className="cim-input mono" value={seg.dictKey ?? ''} placeholder="equip_kind"
                           onChange={(e) => patchSegment(i, { dictKey: e.target.value, values: undefined })} />
                  </label>
                  <label>
                    <span>{t('mds.namingRule.seg.values')}</span>
                    <input className="cim-input mono" value={(seg.values ?? []).join('|')}
                           placeholder="A|B|C"
                           onChange={(e) => patchSegment(i, {
                             values: e.target.value ? e.target.value.split('|') : undefined,
                             dictKey: '',
                           })} />
                  </label>
                  <label>
                    <span>{t('mds.namingRule.seg.length')}</span>
                    <input className="cim-input" type="number" min={1} max={32} value={seg.length ?? ''}
                           onChange={(e) => patchSegment(i, { length: e.target.value ? Number(e.target.value) : undefined })} />
                  </label>
                </>
              )}
              {seg.type === 'DATE' && (
                <label>
                  <span>{t('mds.namingRule.seg.pattern')}</span>
                  <input className="cim-input mono" value={seg.pattern ?? ''} placeholder="yyyyMMdd"
                         onChange={(e) => patchSegment(i, { pattern: e.target.value })} />
                </label>
              )}
              {seg.type === 'SEQ' && (
                <>
                  <label>
                    <span>{t('mds.namingRule.seg.length')}</span>
                    <input className="cim-input" type="number" min={1} max={9} value={seg.length ?? 3}
                           onChange={(e) => patchSegment(i, { length: Number(e.target.value) })} />
                  </label>
                  <label>
                    <span>{t('mds.namingRule.field.seqStart')}</span>
                    <input className="cim-input" type="number" min={0} value={seg.start ?? 1}
                           onChange={(e) => patchSegment(i, { start: Number(e.target.value) })} />
                  </label>
                  <label>
                    <span>{t('mds.namingRule.field.seqScope')}</span>
                    <select className="cim-select" value={seg.scope ?? 'GLOBAL'}
                            onChange={(e) => patchSegment(i, { scope: e.target.value })}>
                      {SEQ_SCOPES.map((s) => (
                        <option key={s} value={s}>{t(`mds.namingRule.scope.${s}`)}</option>
                      ))}
                    </select>
                  </label>
                </>
              )}
            </div>
          </div>
        ))}
      </div>
      <div className="naming-seg-add">
        {SEGMENT_TYPES.map((tp) => (
          <button key={tp} type="button" className="cim-btn" onClick={() => addSegment(tp)}>
            ＋{t(`mds.namingRule.type.${tp}`)}
          </button>
        ))}
      </div>

      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
