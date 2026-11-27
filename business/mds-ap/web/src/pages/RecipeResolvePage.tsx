import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimInfoTable,
  EmptyState,
  Page,
  Panel,
  useToast,
  type InfoItem,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { FlowDetail, RecipeResolveOverride, RecipeResolveView } from '@/types';
import GuideBar from '@/components/GuideBar';

/**
 * 配方解算（RecipeResolvePage，V29 解算契约执行页）。
 *
 * <p>动线：选流程 → 选节点 → 选机台 →（可选）产品与参数覆盖 → 执行解算。
 * 左侧请求表单，右侧结果面板：</p>
 * <ul>
 *   <li>解算成功（resolved=true）：完整快照——流程/节点/工序 → 逻辑配方来源 → 候选池命中行
 *       → PPID（来源/腔体/RMS 版本/配方体哈希）→ 可变参数最终取值。</li>
 *   <li>阻断（resolved=false）：HTTP 200 + 原因码（AMBIGUOUS_POOL / RECIPE_NOT_AUTHORIZED /
 *       PPID_MAPPING_INACTIVE / OVERRIDE_OUT_OF_RANGE 等）+ 原因说明 + 入参回显。</li>
 * </ul>
 *
 * <p>本页只读不落库（解算端点本身是纯查询契约）；可变参数维护在工序库详情（配方链上游）。</p>
 */

type VarValue = NonNullable<RecipeResolveView['variableParams']>[number];

export default function RecipeResolvePage() {
  const { t } = useTranslation();
  const toast = useToast();

  // ---- 左：请求表单 ----
  const flowsRes = useResource(({ signal }) => api.listFlows(undefined, signal));
  const eqpsRes = useResource(({ signal }) => api.listEquipments({}, signal));
  const paramDefsRes = useResource(({ signal }) => api.listParamDefs(signal));

  const [flowId, setFlowId] = useState('');
  const [nodeId, setNodeId] = useState('');
  const [equipmentCode, setEquipmentCode] = useState('');
  const [productCode, setProductCode] = useState('');
  const [overrides, setOverrides] = useState<RecipeResolveOverride[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  // 节点候选（选中流程后拉详情，取 PROCESS 步；START/END 无工序引用不可解算）
  const [flowDetail, setFlowDetail] = useState<FlowDetail | null>(null);
  useEffect(() => {
    setNodeId('');
    setFlowDetail(null);
    if (!flowId) return;
    let alive = true;
    api
      .getFlow(flowId)
      .then((d) => {
        if (alive) setFlowDetail(d);
      })
      .catch(() => {
        if (alive) setFlowDetail(null);
      });
    return () => {
      alive = false;
    };
  }, [flowId]);

  const processSteps = useMemo(
    () => (flowDetail?.steps ?? []).filter((s) => s.stepType === 'PROCESS'),
    [flowDetail],
  );

  // ---- 右：结果 ----
  const [result, setResult] = useState<RecipeResolveView | null>(null);

  const addOverride = () =>
    setOverrides((o) => [...o, { paramKey: '', value: '' }]);
  const removeOverride = (idx: number) =>
    setOverrides((o) => o.filter((_, i) => i !== idx));
  const setOverride = (idx: number, patch: Partial<RecipeResolveOverride>) =>
    setOverrides((o) => o.map((r, i) => (i === idx ? { ...r, ...patch } : r)));

  const run = async () => {
    setErr('');
    if (!flowId || !nodeId || !equipmentCode.trim()) {
      setErr(t('mds.recipeResolve.errRequired'));
      return;
    }
    if (overrides.some((o) => !o.paramKey.trim())) {
      setErr(t('mds.recipeResolve.errOverrideKey'));
      return;
    }
    setBusy(true);
    try {
      const view = await api.resolveRecipe({
        flowId,
        nodeId,
        equipmentCode: equipmentCode.trim(),
        productCode: productCode.trim() || null,
        overrides: overrides.length
          ? overrides.map((o) => ({ paramKey: o.paramKey.trim(), value: o.value }))
          : null,
      });
      setResult(view);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  // 快照键值（resolved=true 才有完整字段）
  const snapshotItems = useMemo<Array<InfoItem<RecipeResolveView>>>(() => {
    if (!result || !result.resolved) return [];
    return [
      { key: 'flowCode', labelKey: 'mds.recipeResolve.f.flowCode', dataIndex: 'flowCode', mono: true },
      { key: 'stepCode', labelKey: 'mds.recipeResolve.f.stepCode', dataIndex: 'stepCode', mono: true },
      { key: 'operationCode', labelKey: 'mds.recipeResolve.f.operationCode', dataIndex: 'operationCode', mono: true },
      {
        key: 'logicalSource',
        labelKey: 'mds.recipeResolve.f.logicalSource',
        render: (r) =>
          r.logicalSource && r.logicalSource !== 'MISSING'
            ? t(`mds.recipeResolve.logicalSource.${r.logicalSource}`)
            : '—',
      },
      { key: 'logicalRecipeRef', labelKey: 'mds.recipeResolve.f.logicalRecipeRef', dataIndex: 'logicalRecipeRef', mono: true },
      { key: 'recipeRule', labelKey: 'mds.recipeResolve.f.recipeRule', dataIndex: 'recipeRule', mono: true },
      { key: 'assignScope', labelKey: 'mds.recipeResolve.f.assignScope', dataIndex: 'assignScope', mono: true },
      { key: 'assignPriority', labelKey: 'mds.recipeResolve.f.assignPriority', dataIndex: 'assignPriority' },
      { key: 'ppid', labelKey: 'mds.recipeResolve.f.ppid', dataIndex: 'ppid', mono: true },
      {
        key: 'ppidSource',
        labelKey: 'mds.recipeResolve.f.ppidSource',
        render: (r) => (r.ppidSource ? t(`mds.recipeResolve.ppidSource.${r.ppidSource}`) : '—'),
      },
      { key: 'chamberCode', labelKey: 'mds.recipeResolve.f.chamberCode', dataIndex: 'chamberCode', mono: true },
      { key: 'rmsVersion', labelKey: 'mds.recipeResolve.f.rmsVersion', render: (r) => (r.rmsVersionNo != null ? `V${r.rmsVersionNo}（${r.rmsVersionStatus ?? ''}）` : '—') },
      { key: 'bodyHash', labelKey: 'mds.recipeResolve.f.bodyHash', dataIndex: 'bodyHash', mono: true },
      { key: 'resolvedAt', labelKey: 'mds.recipeResolve.f.resolvedAt', dataIndex: 'resolvedAt', mono: true },
    ];
  }, [result, t]);

  const vars: VarValue[] = result?.resolved ? (result.variableParams ?? []) : [];

  return (
    <Page>
      <GuideBar />
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 420px) minmax(0, 1fr)',
          gap: 12,
          flex: '1 1 auto',
          minHeight: 0,
        }}
      >
        {/* 左：请求表单 */}
        <Panel titleKey="mds.recipeResolve.formTitle">
          {err && <p className="op-error">{err}</p>}
          <div className="op-detail-body" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <label className="dim" htmlFor="rr-flow">{t('mds.recipeResolve.selectFlow')}</label>
            <select
              id="rr-flow"
              className="op-search"
              value={flowId}
              onChange={(e) => setFlowId(e.target.value)}
            >
              <option value="">{t('mds.recipeResolve.selectFlow')}</option>
              {(flowsRes.data ?? []).map((f) => (
                <option key={f.id} value={f.id}>
                  {`${f.flowCode} V${f.versionNo ?? 1} ${f.flowName ?? ''}`}
                </option>
              ))}
            </select>

            <label className="dim" htmlFor="rr-node">{t('mds.recipeResolve.selectNode')}</label>
            <select
              id="rr-node"
              className="op-search"
              value={nodeId}
              disabled={!flowId}
              onChange={(e) => setNodeId(e.target.value)}
            >
              <option value="">{t('mds.recipeResolve.selectNode')}</option>
              {processSteps.map((s) => (
                <option key={s.nodeId} value={s.nodeId}>
                  {`${s.stepCode}（${s.operationCode ?? '—'}）`}
                </option>
              ))}
            </select>

            <label className="dim" htmlFor="rr-eqp">{t('mds.recipeResolve.selectEquipment')}</label>
            <select
              id="rr-eqp"
              className="op-search"
              value={equipmentCode}
              onChange={(e) => setEquipmentCode(e.target.value)}
            >
              <option value="">{t('mds.recipeResolve.selectEquipment')}</option>
              {(eqpsRes.data ?? []).map((e) => (
                <option key={e.equipmentCode} value={e.equipmentCode}>
                  {`${e.equipmentName}（${e.equipmentCode}）`}
                </option>
              ))}
            </select>

            <label className="dim" htmlFor="rr-prod">{t('mds.recipeResolve.field.productCode')}</label>
            <input
              id="rr-prod"
              className="op-search"
              value={productCode}
              onChange={(e) => setProductCode(e.target.value)}
            />

            <div style={{ marginTop: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
              <h4 style={{ margin: 0 }}>{t('mds.recipeResolve.overridesTitle')}</h4>
              <CimButton size="small" onClick={addOverride}>
                {t('mds.recipeResolve.addOverride')}
              </CimButton>
            </div>
            <table className="icm-table">
              <thead>
                <tr>
                  <th>{t('mds.recipeResolve.col.paramKey')}</th>
                  <th>{t('mds.recipeResolve.col.value')}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {overrides.map((o, idx) => (
                  <tr key={idx}>
                    <td>
                      <select
                        className="op-search"
                        style={{ width: 160 }}
                        value={o.paramKey}
                        onChange={(e) => setOverride(idx, { paramKey: e.target.value })}
                      >
                        <option value="">{t('mds.recipeResolve.selectParamHint')}</option>
                        {(paramDefsRes.data ?? [])
                          .filter((d) => d.enabled)
                          .map((d) => (
                            <option key={d.attrKey} value={d.attrKey}>{d.attrKey}</option>
                          ))}
                      </select>
                    </td>
                    <td>
                      <input
                        className="op-search"
                        style={{ width: 120 }}
                        value={o.value}
                        onChange={(e) => setOverride(idx, { value: e.target.value })}
                      />
                    </td>
                    <td>
                      <CimButton size="small" danger onClick={() => removeOverride(idx)}>
                        {t('mds.recipeResolve.removeOverride')}
                      </CimButton>
                    </td>
                  </tr>
                ))}
                {!overrides.length && (
                  <tr>
                    <td colSpan={3} className="dim">{t('mds.operation.varParamHint')}</td>
                  </tr>
                )}
              </tbody>
            </table>

            <div style={{ marginTop: 12 }}>
              <Can perm={MDS_PERMS.recipeResolve.resolve}>
                <CimButton type="primary" size="small" disabled={busy} onClick={() => void run()}>
                  {t('mds.recipeResolve.run')}
                </CimButton>
              </Can>
            </div>
          </div>
        </Panel>

        {/* 右：结果面板 */}
        <Panel titleKey="mds.recipeResolve.resultTitle">
          {!result ? (
            <EmptyState titleKey="mds.recipeResolve.noResult" />
          ) : (
            <div className="op-detail-body">
              <div className="op-detail-head">
                <span
                  className={
                    result.resolved
                      ? 'flow-status flow-status-active'
                      : 'icm-src-missing'
                  }
                >
                  {result.resolved ? t('mds.recipeResolve.resolvedYes') : t('mds.recipeResolve.blocked')}
                </span>
                {!result.resolved && (
                  <>
                    <span className="mono">{result.reason ?? '—'}</span>
                    {result.reasonDetail && <span className="dim">{result.reasonDetail}</span>}
                  </>
                )}
              </div>

              {result.resolved ? (
                <>
                  <CimInfoTable<RecipeResolveView> record={result} items={snapshotItems} columns={2} />
                  <h4 style={{ marginTop: 16 }}>{t('mds.operation.varParamTitle')}</h4>
                  <table className="icm-table">
                    <thead>
                      <tr>
                        <th>{t('mds.recipeResolve.col.paramKey')}</th>
                        <th>{t('mds.recipeResolve.col.value')}</th>
                        <th>{t('mds.recipeResolve.col.source')}</th>
                        <th>{t('mds.recipeResolve.col.range')}</th>
                        <th>{t('mds.recipeResolve.col.level')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {vars.map((v) => (
                        <tr key={v.paramKey}>
                          <td className="mono">{v.paramKey}</td>
                          <td>{v.value}</td>
                          <td>{t(`mds.recipeResolve.src.${v.source}`)}</td>
                          <td className="dim">
                            {v.minValue || v.maxValue ? `${v.minValue ?? ''} ~ ${v.maxValue ?? ''}` : '—'}
                          </td>
                          <td>{t(`mds.operation.varLevel.${v.overrideLevel}`)}</td>
                        </tr>
                      ))}
                      {!vars.length && (
                        <tr><td colSpan={5} className="dim">{t('mds.operation.varParamHint')}</td></tr>
                      )}
                    </tbody>
                  </table>
                </>
              ) : (
                <CimInfoTable<RecipeResolveView>
                  record={result}
                  columns={2}
                  items={[
                    { key: 'flowCode', labelKey: 'mds.recipeResolve.f.flowCode', dataIndex: 'flowCode', mono: true },
                    { key: 'stepCode', labelKey: 'mds.recipeResolve.f.stepCode', dataIndex: 'stepCode', mono: true },
                    { key: 'operationCode', labelKey: 'mds.recipeResolve.f.operationCode', dataIndex: 'operationCode', mono: true },
                    { key: 'equipmentCode', labelKey: 'mds.recipeResolve.selectEquipment', dataIndex: 'equipmentCode', mono: true },
                    { key: 'reason', labelKey: 'mds.recipeResolve.reason', dataIndex: 'reason', mono: true },
                    { key: 'reasonDetail', labelKey: 'mds.recipeResolve.reasonDetail', dataIndex: 'reasonDetail', wrap: true },
                  ]}
                />
              )}
            </div>
          )}
        </Panel>
      </div>
    </Page>
  );
}
