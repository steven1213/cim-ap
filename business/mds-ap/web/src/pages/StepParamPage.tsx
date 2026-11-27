import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  DataTable,
  EmptyState,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import GuideBar from '@/components/GuideBar';

type FlowRow = { id: string; flowCode: string; flowName: string; status: string; familyCode?: string | null; versionNo?: number | null };
type NodeEff = api.NodeEffective;
type AssignRow = api.EquipAssign;
type ParamDefRow = api.ParamDef;

/** 来源层级 → 展示（颜色由 CSS 类承载：蓝=节点覆盖 青=流程覆盖 灰=工序默认 红=缺失）。 */
function sourceClass(src: string, required?: boolean): string {
  if (src === 'STEP_OVERRIDE') return 'icm-src-step';
  if (src === 'FLOW_OVERRIDE') return 'icm-src-flow';
  if (src === 'OPERATION_DEFAULT') return 'icm-src-op';
  if (src === 'MISSING' && required) return 'icm-src-missing';
  return '';
}

/**
 * 来源展示分级：覆盖层 → 彩色标签；MISSING 按必填拆两级——
 * 必填缺失=红色「缺失」（真警报），可选未配=中性灰「未配置」（正常状态，不制造噪音）。
 */
function sourceView(src: string, required?: boolean): { cls: string; key: string } {
  if (src === 'MISSING' && !required) return { cls: 'dim', key: 'mds.stepParam.src.UNSET' };
  return { cls: sourceClass(src, required), key: `mds.stepParam.src.${src}` };
}

/**
 * 节点参数维护（StepParamPage，V22 ICM 核心动线页）。
 *
 * 动线：选流程 → 选节点 → 看生效值与来源 → 只改覆盖项 → 保存。
 * - 生效值 = ContextResolver 沿 ContextChain 解析（STEP > FLOW > OPERATION），四级来源标注；
 * - 候选池/KV 编辑 = STEP 层 assign/attr（一键还原=删本层行，回落上一层）；
 * - 顶部范围切换：本节点（STEP 层）/ 整条流程（FLOW 层，整流程统一机台池/策略参数）。
 */
export default function StepParamPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  // 左：流程列表
  const flowsRes = useResource(({ signal }) => api.listFlows(undefined, signal));
  const flows = (flowsRes.data ?? []) as FlowRow[];
  const [flowId, setFlowId] = useState<string | null>(null);
  const [flowKeyword, setFlowKeyword] = useState('');

  // 范围：node（本节点=STEP 层）/ flow（整条流程=FLOW 层）
  const [scope, setScope] = useState<'node' | 'flow'>('node');

  // 中：节点生效值
  const effRes = useResource(
    ({ signal }) => (flowId ? api.getEffective(flowId, undefined, signal) : Promise.resolve([] as NodeEff[])),
    { deps: [flowId] },
  );
  const nodes = effRes.data ?? [];
  const [nodeId, setNodeId] = useState<string | null>(null);
  const selectedNode = useMemo(
    () => nodes.find((n) => n.nodeId === nodeId) ?? null,
    [nodes, nodeId],
  );

  // 右：参数定义（KV 键全集）
  const defsRes = useResource(({ signal }) => api.listParamDefs(signal));
  const defs = defsRes.data ?? [];

  // 候选池行（按 scope 端点取）
  const poolRes = useResource(
    ({ signal }) => {
      if (!flowId) return Promise.resolve([] as AssignRow[]);
      if (scope === 'flow') return api.listFlowAssigns(flowId, signal);
      if (nodeId) return api.listStepAssigns(flowId, nodeId, signal);
      return Promise.resolve([] as AssignRow[]);
    },
    { deps: [flowId, nodeId, scope] },
  );
  const pool = poolRes.data ?? [];

  // KV 值（按 scope 端点取）
  const attrsRes = useResource(
    ({ signal }) => {
      if (!flowId) return Promise.resolve({} as Record<string, string>);
      if (scope === 'flow') return api.listFlowAttrs(flowId, signal);
      if (nodeId) return api.listStepAttrs(flowId, nodeId, signal);
      return Promise.resolve({} as Record<string, string>);
    },
    { deps: [flowId, nodeId, scope] },
  );
  const attrValues = attrsRes.data ?? {};

  // 指派新增表单（配方规则显式选择：后端对空白 recipeRule 默认 FIXED 且要求 recipe_ref 必填）
  const [showAssignForm, setShowAssignForm] = useState(false);
  const [assignType, setAssignType] = useState('GROUP');
  const [assignRef, setAssignRef] = useState('');
  const [recipeRule, setRecipeRule] = useState('GENERATE');
  const [recipeRef, setRecipeRef] = useState('');
  const [recipeMatch, setRecipeMatch] = useState('');
  const [priority, setPriority] = useState('100');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  // 下拉数据源（assignRef 候选）
  const toolGroupsRes = useResource(({ signal }) => api.listToolGroups({}, signal));
  const equipmentsRes = useResource(({ signal }) => api.listEquipments({}, signal));
  const equipClassesRes = useResource(({ signal }) => api.listEquipmentClasses(undefined, signal));

  useEffect(() => {
    setNodeId(null);
    setShowAssignForm(false);
  }, [flowId, scope]);

  const filteredFlows = useMemo(() => {
    const kw = flowKeyword.trim().toLowerCase();
    if (!kw) return flows;
    return flows.filter((f) =>
      f.flowCode.toLowerCase().includes(kw) || (f.flowName ?? '').toLowerCase().includes(kw));
  }, [flows, flowKeyword]);

  const doCreateAssign = async () => {
    setErr('');
    if (!assignRef.trim()) {
      setErr(t('mds.stepParam.errRefRequired'));
      return;
    }
    // 前端预校验（与后端 applyFields 守门同口径：FIXED→recipeRef 必填；EXACT_MATCH→合法 JSON）
    if (recipeRule === 'FIXED' && !recipeRef.trim()) {
      setErr(t('mds.stepParam.errRecipeRefRequired'));
      return;
    }
    if (recipeRule === 'EXACT_MATCH' && recipeMatch.trim()) {
      try {
        JSON.parse(recipeMatch);
      } catch {
        setErr(t('mds.stepParam.errRecipeMatchJson'));
        return;
      }
    }
    setBusy(true);
    try {
      const payload: api.AssignPayload = {
        assignType,
        assignRef: assignRef.trim(),
        recipeRule,
        recipeRef: recipeRef.trim() || undefined,
        recipeMatch: recipeRule === 'EXACT_MATCH' && recipeMatch.trim() ? recipeMatch.trim() : undefined,
        priority: Number(priority) || 100,
        enabled: true,
      };
      if (scope === 'flow') await api.createFlowAssign(flowId!, payload);
      else await api.createStepAssign(flowId!, nodeId!, payload);
      toast.success(t('mds.common.saved'));
      setShowAssignForm(false);
      setAssignRef('');
      setRecipeRef('');
      setRecipeMatch('');
      poolRes.refresh();
      effRes.refresh();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const doDeleteAssign = async (row: AssignRow) => {
    const ok = await confirm({
      titleKey: 'mds.stepParam.assignDeleteConfirm',
      contentKey: 'mds.stepParam.assignDeleteConfirmText',
      okKey: 'mds.common.delete',
      danger: true,
    });
    if (!ok) return;
    try {
      await api.deleteAssign(row.id);
      toast.success(t('mds.common.deleted'));
      poolRes.refresh();
      effRes.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  // KV 草稿（编辑态）
  const [kvDraft, setKvDraft] = useState<Record<string, string>>({});
  const [kvEditing, setKvEditing] = useState(false);
  useEffect(() => {
    setKvDraft({ ...attrValues });
    setKvEditing(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attrValues]);

  const saveKv = async () => {
    setErr('');
    setBusy(true);
    try {
      if (scope === 'flow') await api.putFlowAttrs(flowId!, kvDraft);
      else await api.putStepAttrs(flowId!, nodeId!, kvDraft);
      toast.success(t('mds.common.saved'));
      setKvEditing(false);
      attrsRes.refresh();
      effRes.refresh();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const flowColumns = useMemo<Array<DataTableColumn<FlowRow>>>(
    () => [
      { key: 'flowCode', titleKey: 'mds.flow.field.flowCode', width: 170, mono: true },
      { key: 'flowName', titleKey: 'mds.flow.field.flowName', width: 140, ellipsis: true },
      { key: 'status', titleKey: 'mds.flow.field.status', width: 84 },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ], []);

  const nodeColumns = useMemo<Array<DataTableColumn<NodeEff>>>(
    () => [
      { key: 'stepCode', titleKey: 'mds.flow.field.stepCode', width: 120, mono: true },
      { key: 'stepType', titleKey: 'mds.flow.field.stepType', width: 90 },
      {
        key: 'operationCode',
        titleKey: 'mds.stepParam.col.operation',
        width: 150,
        mono: true,
        ellipsis: true,
        render: (r) => (r.operationCode ?? '—'),
      },
      {
        key: 'pool',
        titleKey: 'mds.stepParam.col.pool',
        width: 130,
        render: (r) =>
          r.poolSource === 'MISSING'
            ? <span className="icm-src-missing">{t('mds.stepParam.poolMissing')}</span>
            : <span className={sourceClass(r.poolSource)}>{t(`mds.stepParam.src.${r.poolSource}`)} ×{r.pool.length}</span>,
      },
      {
        key: 'stale',
        titleKey: 'mds.stepParam.col.stale',
        width: 80,
        render: (r) => (r.opRefStale
          ? <span className="icm-warn">{t('mds.stepParam.staleYes')}</span>
          : <span className="dim">—</span>),
      },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ], [t]);

  // 生效值展示行（字段名/值/来源）
  const effectiveRows = useMemo(() => {
    if (!selectedNode) return [];
    const n = selectedNode;
    const mk = (key: string, eff: { value: unknown; source: string }) => ({
      key,
      value: eff.value == null ? '—' : String(eff.value),
      source: eff.source,
    });
    return [
      mk('recipeRef', n.recipeRef),
      mk('equipType', n.equipType),
      mk('qtimeMin', n.qtimeMin),
      mk('qtimeMax', n.qtimeMax),
      mk('reworkLimit', n.reworkLimit),
      mk('batchMode', n.batchMode),
      mk('batchSize', n.batchSize),
      mk('holdPoint', n.holdPoint),
      mk('inspectPlan', n.inspectPlan),
      mk('samplingRate', n.samplingRate),
      mk('reticleRef', n.reticleRef),
      mk('contamLevel', n.contamLevel),
      mk('capabilityCode', n.capabilityCode),
      mk('edcPlanCode', n.edcPlanCode),
    ];
  }, [selectedNode]);

  const assignRefOptions = useMemo(() => {
    if (assignType === 'GROUP') {
      return (toolGroupsRes.data ?? []).map((g: { groupCode: string; groupName?: string | null }) => ({
        value: g.groupCode, label: g.groupName ? `${g.groupName}（${g.groupCode}）` : g.groupCode,
      }));
    }
    if (assignType === 'EQUIP') {
      return (equipmentsRes.data ?? []).map((e: { equipmentCode: string; equipmentName?: string | null }) => ({
        value: e.equipmentCode, label: e.equipmentName ? `${e.equipmentName}（${e.equipmentCode}）` : e.equipmentCode,
      }));
    }
    return (equipClassesRes.data ?? []).map((c: { classCode: string; className?: string | null }) => ({
      value: c.classCode, label: c.className ? `${c.className}（${c.classCode}）` : c.classCode,
    }));
  }, [assignType, toolGroupsRes.data, equipmentsRes.data, equipClassesRes.data]);

  // KV 展示行：参数定义按当前层过滤（scopes 守门的 UI 侧镜像——
  // flow 层只列 scopes 含 FLOW 的定义、node 层只列含 STEP 的；
  // 空 scopes = 纯字典参数，任何 KV 层都不出现）
  const kvRows = useMemo(() => {
    const scopeTag = scope === 'flow' ? 'FLOW' : 'STEP';
    return defs
      .filter((d: ParamDefRow) => d.enabled && (d.scopes ?? '').split(',').includes(scopeTag))
      .map((d: ParamDefRow) => {
        const eff = selectedNode && scope === 'node' ? selectedNode.attrs[d.attrKey] : null;
        const hasValue = attrValues[d.attrKey] != null && attrValues[d.attrKey] !== '';
        return {
          def: d,
          value: hasValue ? attrValues[d.attrKey] : '',
          source: scope === 'flow' ? (hasValue ? 'FLOW_OVERRIDE' : 'MISSING') : (eff?.source ?? 'MISSING'),
        };
      });
  }, [defs, attrValues, selectedNode, scope]);

  const canEdit = !!(flowId && (scope === 'flow' || nodeId));
  const editPerm = scope === 'flow' ? MDS_PERMS.flowAssign.update : MDS_PERMS.stepAssign.update;

  return (
    <Page>
      <GuideBar />
      <div className="icm-layout">
        {/* 左：流程列表 */}
        <DataTable<FlowRow>
          titleKey="mds.stepParam.flowListTitle"
          columns={flowColumns}
          rows={filteredFlows}
          rowKey={(r) => r.id}
          loading={flowsRes.loading}
          error={flowsRes.error?.message ?? null}
          onRetry={flowsRes.refresh}
          search={{ value: flowKeyword, onChange: setFlowKeyword, placeholderKey: 'mds.stepParam.flowSearchPlaceholder' }}
          onRefresh={flowsRes.refresh}
          onRowClick={(r) => setFlowId(r.id)}
          rowClassName={(r) => (r.id === flowId ? 'row-selected' : '')}
          emptyTitleKey="mds.stepParam.flowEmpty"
        />

        {/* 中：节点列表（本节点范围） */}
        {scope === 'node' && (
          <DataTable<NodeEff>
            titleKey={flowId ? 'mds.stepParam.nodeListTitle' : 'mds.stepParam.selectFlowHint'}
            columns={nodeColumns}
            rows={nodes}
            rowKey={(r) => r.nodeId}
            loading={effRes.loading}
            onRowClick={(r) => setNodeId(r.nodeId)}
            rowClassName={(r) => (r.nodeId === nodeId ? 'row-selected' : '')}
            emptyTitleKey="mds.stepParam.nodeEmpty"
          />
        )}

        {/* 中（整条流程范围）：FLOW 级指派+KV 一体面板 */}
        {scope === 'flow' && (
          <Panel className="icm-detail">
            {!flowId ? (
              <EmptyState titleKey="mds.stepParam.selectFlowHint" />
            ) : (
              <div className="op-detail-body">
                <div className="op-detail-head">
                  <h3 className="op-detail-title">{t('mds.stepParam.flowScopeTitle')}</h3>
                  <span className="icm-src-flow">{t('mds.stepParam.src.FLOW_OVERRIDE')}</span>
                </div>
                <p className="dim">{t('mds.stepParam.flowScopeHint')}</p>
              </div>
            )}
          </Panel>
        )}

        {/* 右：详情面板 */}
        <Panel className="icm-detail">
          <div className="op-detail-head">
            <select
              className="op-search"
              style={{ width: 140 }}
              value={scope}
              onChange={(e) => setScope(e.target.value as 'node' | 'flow')}
            >
              <option value="node">{t('mds.stepParam.scopeNode')}</option>
              <option value="flow">{t('mds.stepParam.scopeFlow')}</option>
            </select>
            <span style={{ flex: 1 }} />
            {canEdit && (
              <Can perm={editPerm}>
                <CimButton size="small" onClick={() => setShowAssignForm((v) => !v)}>
                  {t('mds.stepParam.addAssign')}
                </CimButton>
              </Can>
            )}
          </div>

          {!canEdit ? (
            <EmptyState titleKey="mds.stepParam.selectHint" />
          ) : (
            <div className="op-detail-body">
              {err && <p className="op-error">{err}</p>}

              {/* 新增指派内联表单 */}
              {showAssignForm && (
                <div className="op-copy-bar" style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
                  <select className="op-search" style={{ width: 110 }} value={assignType}
                    onChange={(e) => { setAssignType(e.target.value); setAssignRef(''); }}>
                    <option value="GROUP">{t('mds.stepParam.assignType.GROUP')}</option>
                    <option value="EQUIP">{t('mds.stepParam.assignType.EQUIP')}</option>
                    <option value="CLASS">{t('mds.stepParam.assignType.CLASS')}</option>
                  </select>
                  <select className="op-search" style={{ width: 220 }} value={assignRef}
                    onChange={(e) => setAssignRef(e.target.value)}>
                    <option value="">{t('mds.stepParam.selectRef')}</option>
                    {assignRefOptions.map((o) => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </select>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <span className="dim" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                      {t('mds.stepParam.col.recipeRule')}
                    </span>
                    <select className="op-search" style={{ width: 110 }} value={recipeRule}
                      onChange={(e) => setRecipeRule(e.target.value)}>
                      <option value="GENERATE">{t('mds.stepParam.recipeRule.GENERATE')}</option>
                      <option value="FIXED">{t('mds.stepParam.recipeRule.FIXED')}</option>
                      <option value="EXACT_MATCH">{t('mds.stepParam.recipeRule.EXACT_MATCH')}</option>
                    </select>
                  </span>
                  {recipeRule === 'FIXED' && (
                    <input className="op-search" style={{ width: 150 }} placeholder={t('mds.stepParam.recipeRef')}
                      value={recipeRef} onChange={(e) => setRecipeRef(e.target.value)} />
                  )}
                  {recipeRule === 'EXACT_MATCH' && (
                    <input className="op-search" style={{ width: 180 }} placeholder={t('mds.stepParam.recipeMatch')}
                      value={recipeMatch} onChange={(e) => setRecipeMatch(e.target.value)} />
                  )}
                  <input className="op-search" style={{ width: 80 }} placeholder={t('mds.stepParam.priority')}
                    value={priority} onChange={(e) => setPriority(e.target.value)} />
                  <CimButton type="primary" size="small" disabled={busy} onClick={() => void doCreateAssign()}>
                    {t('mds.common.save')}
                  </CimButton>
                </div>
              )}

              {/* 候选池 */}
              <h4>{scope === 'flow' ? t('mds.stepParam.flowPoolTitle') : t('mds.stepParam.poolTitle')}</h4>
              <table className="icm-table">
                <thead>
                  <tr>
                    <th>{t('mds.stepParam.col.assignType')}</th>
                    <th>{t('mds.stepParam.col.assignRef')}</th>
                    <th>{t('mds.stepParam.col.priority')}</th>
                    <th>{t('mds.stepParam.col.recipeRule')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {pool.length === 0 && (
                    <tr><td colSpan={5} className="dim">{t('mds.stepParam.poolEmpty')}</td></tr>
                  )}
                  {pool.map((a) => (
                    <tr key={a.id}>
                      <td>{t(`mds.stepParam.assignType.${a.assignType}`)}</td>
                      <td className="mono">{a.assignRef}</td>
                      <td>{a.priority}</td>
                      <td className="mono">{a.recipeRule}{a.recipeRef ? ` · ${a.recipeRef}` : ''}</td>
                      <td>
                        <Can perm={editPerm}>
                          <CimButton size="small" danger onClick={() => void doDeleteAssign(a)}>
                            {t('mds.common.delete')}
                          </CimButton>
                        </Can>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* KV */}
              <h4 style={{ marginTop: 16 }}>{t('mds.stepParam.kvTitle')}</h4>
              <table className="icm-table">
                <thead>
                  <tr>
                    <th>{t('mds.stepParam.col.attrKey')}</th>
                    <th>{t('mds.stepParam.col.attrValue')}</th>
                    <th>{t('mds.stepParam.col.source')}</th>
                  </tr>
                </thead>
                <tbody>
                  {kvRows.map(({ def, value, source }) => (
                    <tr key={def.attrKey}>
                      <td className="mono">{def.attrKey}{def.required ? <span className="icm-req">*</span> : null}</td>
                      <td>
                        {kvEditing ? (
                          <input
                            className="op-search"
                            value={kvDraft[def.attrKey] ?? ''}
                            onChange={(e) => setKvDraft((d) => ({ ...d, [def.attrKey]: e.target.value }))}
                          />
                        ) : (
                          <span className={value ? '' : 'dim'}>{value || '—'}</span>
                        )}
                      </td>
                      <td><span className={sourceView(source, def.required).cls}>{t(sourceView(source, def.required).key)}</span></td>
                    </tr>
                  ))}
                  {!kvRows.length && (
                    <tr><td colSpan={3} className="dim">{t('mds.stepParam.kvNoDefs')}</td></tr>
                  )}
                </tbody>
              </table>
              <div style={{ marginTop: 8, display: 'flex', gap: 8 }}>
                {kvEditing ? (
                  <>
                    <Can perm={scope === 'flow' ? MDS_PERMS.flowAttr.update : MDS_PERMS.stepAttr.update}>
                      <CimButton type="primary" size="small" disabled={busy} onClick={() => void saveKv()}>
                        {t('mds.common.save')}
                      </CimButton>
                    </Can>
                    <CimButton size="small" onClick={() => { setKvEditing(false); setKvDraft({ ...attrValues }); }}>
                      {t('mds.common.cancel')}
                    </CimButton>
                  </>
                ) : (
                  <Can perm={scope === 'flow' ? MDS_PERMS.flowAttr.update : MDS_PERMS.stepAttr.update}>
                    <CimButton
                      size="small"
                      onClick={() => {
                        // 空态守卫：KV 行按当前层过滤后的定义渲染，层内无可配参数时无行可编辑
                        if (!kvRows.length) {
                          toast.error(t('mds.stepParam.kvNoDefs'));
                          return;
                        }
                        setKvEditing(true);
                      }}
                    >
                      {t('mds.stepParam.editKv')}
                    </CimButton>
                  </Can>
                )}
              </div>

              {/* 生效值（仅本节点范围展示） */}
              {scope === 'node' && selectedNode && (
                <>
                  <h4 style={{ marginTop: 16 }}>{t('mds.stepParam.effectiveTitle')}</h4>
                  {(() => {
                    const ok = effectiveRows.filter((r) => r.source !== 'MISSING').length;
                    const reqMissing = kvRows.filter(({ def, value }) => !!def.required && !value).length;
                    return (
                      <p className="dim" style={{ margin: '4px 0' }}>
                        {t('mds.stepParam.effSummary', { ok, unset: effectiveRows.length - ok })}
                        {reqMissing > 0 && (
                          <span className="icm-src-missing"> · {t('mds.stepParam.effMissing', { n: reqMissing })}</span>
                        )}
                      </p>
                    );
                  })()}
                  {selectedNode.opRefStale && (
                    <p className="icm-warn">
                      {t('mds.stepParam.staleHint', { op: selectedNode.operationCode ?? '' })}
                    </p>
                  )}
                  <table className="icm-table">
                    <thead>
                      <tr>
                        <th>{t('mds.stepParam.col.field')}</th>
                        <th>{t('mds.stepParam.col.effective')}</th>
                        <th>{t('mds.stepParam.col.source')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {effectiveRows.map((r) => (
                        <tr key={r.key}>
                          <td className="mono">{r.key}</td>
                          <td className={r.value === '—' ? 'dim' : ''}>{r.value}</td>
                          <td><span className={sourceView(r.source).cls}>{t(sourceView(r.source).key)}</span></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </>
              )}
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
