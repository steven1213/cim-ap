import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  DataTable,
  EmptyState,
  Page,
  Panel,
  type DataTableColumn,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import type { FlowDetail, FlowStepView, FlowView } from '@/types';

/**
 * 流程总览（FlowOverviewPage，P3-4）——MainProcessFlowOverAll 等价视图：
 * 选流程 → 主线步骤一览（START→END 的 MAIN 链 BFS 推导）+ 旁路/返工回路/Hold 标注
 * + 跨站 Q-time 约束表。数据全部从流程 detail 实时推导，不建表、不落库（roadmap P3 口径）。
 */

/** 覆盖摘要紧凑串（语言无关 token：光罩/污染级/禁入/NPW）。 */
function coverageText(s: FlowStepView): string {
  const parts: string[] = [];
  if (s.reticleRef) parts.push(`RET ${s.reticleRef}`);
  if (s.contamLevel) parts.push(`CTM ${s.contamLevel}${s.contamForbidden ? `/×${s.contamForbidden}` : ''}`);
  if (s.npwLimit != null) parts.push(`NPW ${s.npwLimit}`);
  return parts.join(' · ');
}

/** 返工回路标注：REWORK 边的源站 → 目标站编码列表（回路跳回；边端点 = nodeId）。 */
function reworkTargets(d: FlowDetail): Map<string, string[]> {
  const codeById = new Map(d.steps.map((s) => [s.nodeId, s.stepCode]));
  const m = new Map<string, string[]>();
  for (const e of d.edges) {
    if (e.edgeType !== 'REWORK') continue;
    const from = codeById.get(e.fromStepId);
    const to = codeById.get(e.toStepId);
    if (!from || !to) continue;
    m.set(from, [...(m.get(from) ?? []), to]);
  }
  return m;
}

interface QtimeRow {
  key: string;
  from: string;
  to: string;
  maxMinutes: number;
  action: string;
  alarmCode: string;
}

export default function FlowOverviewPage() {
  const { t } = useTranslation();

  const flowsRes = useResource(({ signal }) => api.listFlows(undefined, signal));
  const flows = flowsRes.data ?? [];
  const [flowId, setFlowId] = useState('');

  const detailRes = useResource(
    ({ signal }) => (flowId ? api.getFlow(flowId, signal) : Promise.resolve(null as FlowDetail | null)),
    { deps: [flowId] },
  );
  const detail = detailRes.data ?? null;

  const model = useMemo(() => {
    if (!detail) return null;
    const steps = [...detail.steps].sort((a, b) => (a.stepNo ?? 0) - (b.stepNo ?? 0));
    const codeById = new Map(steps.map((s) => [s.nodeId, s.stepCode]));
    // 主线：START 沿 MAIN 边 BFS（PASS/FAIL/REWORK 分支一律算旁路）。
    // ⚠️ 边端点是 nodeId（XML 节点 id，saveXml 重建后行 id 会换），全链统一用 nodeId。
    const outMain = new Map<string, string[]>();
    for (const e of detail.edges) {
      if (e.edgeType !== 'MAIN') continue;
      outMain.set(e.fromStepId, [...(outMain.get(e.fromStepId) ?? []), e.toStepId]);
    }
    const mainline = new Set<string>();
    const start = steps.find((s) => s.stepType === 'START');
    if (start) {
      const q = [start.nodeId];
      const seen = new Set(q);
      while (q.length) {
        const cur = q.shift() as string;
        mainline.add(cur);
        for (const nx of outMain.get(cur) ?? []) {
          if (!seen.has(nx)) {
            seen.add(nx);
            q.push(nx);
          }
        }
      }
    }
    const rework = reworkTargets(detail);
    const qtimeRows: QtimeRow[] = (detail.qtimes ?? []).map((q) => ({
      key: q.id,
      from: codeById.get(q.fromNodeId) ?? q.fromNodeId,
      to: codeById.get(q.toNodeId) ?? q.toNodeId,
      maxMinutes: q.maxMinutes,
      action: q.action,
      alarmCode: q.alarmCode ?? '',
    }));
    const business = steps.filter((s) => s.stepType !== 'START' && s.stepType !== 'END');
    return {
      steps,
      mainline,
      rework,
      qtimeRows,
      stats: {
        mainline: business.filter((s) => mainline.has(s.nodeId)).length,
        bypass: business.filter((s) => !mainline.has(s.nodeId)).length,
        rework: rework.size,
        hold: business.filter((s) => s.holdPoint).length,
        qtimes: qtimeRows.length,
      },
    };
  }, [detail]);

  const stepColumns = useMemo<Array<DataTableColumn<FlowStepView>>>(
    () => [
      { key: 'stepNo', titleKey: 'mds.flow.field.stepNo', width: 60, mono: true },
      {
        key: 'stepCode',
        titleKey: 'mds.flow.field.stepCode',
        width: 180,
        render: (r) => (
          <span>
            <span className="mono">{r.stepCode}</span>
            {r.stepName ? <span className="dim"> · {r.stepName}</span> : null}
          </span>
        ),
      },
      {
        key: 'stepType',
        titleKey: 'mds.flow.field.stepType',
        width: 88,
        render: (r) => t(`mds.flow.stepType.${r.stepType}`),
      },
      {
        key: 'operationCode',
        titleKey: 'mds.flow.field.operationCode',
        width: 130,
        render: (r) => (r.stepType === 'SUBFLOW' ? (
          <span className="icm-src-flow">→ {r.flowRef}</span>
        ) : r.operationCode ? (
          <span className="mono">{r.operationCode}</span>
        ) : (
          <span className="dim">—</span>
        )),
      },
      {
        key: 'equipType',
        titleKey: 'mds.flow.field.equipType',
        width: 96,
        render: (r) => (r.equipType ? <span className="mono">{r.equipType}</span> : <span className="dim">—</span>),
      },
      {
        key: 'recipeRef',
        titleKey: 'mds.flow.field.recipeRef',
        width: 110,
        render: (r) => (r.recipeRef ? <span className="mono">{r.recipeRef}</span> : <span className="dim">—</span>),
      },
      {
        key: 'coverage',
        titleKey: 'mds.overview.col.coverage',
        width: 210,
        render: (r) => {
          const txt = coverageText(r);
          return txt ? <span className="mono">{txt}</span> : <span className="dim">—</span>;
        },
      },
      {
        key: 'qtime',
        titleKey: 'mds.flow.field.qtimeMax',
        width: 110,
        render: (r) => (r.qtimeMin != null || r.qtimeMax != null ? (
          <span className="mono">{r.qtimeMin ?? '—'} ~ {r.qtimeMax ?? '—'} min</span>
        ) : (
          <span className="dim">—</span>
        )),
      },
      {
        key: 'reworkLimit',
        titleKey: 'mds.flow.field.reworkLimit',
        width: 88,
        render: (r) => (r.reworkLimit != null ? r.reworkLimit : <span className="dim">—</span>),
      },
      {
        key: 'mark',
        titleKey: 'mds.overview.col.mark',
        render: (r) => {
          if (r.stepType === 'START' || r.stepType === 'END') {
            return <span className="dim">{t('mds.overview.mark.startEnd')}</span>;
          }
          const marks: ReactNode[] = [];
          if (model?.mainline.has(r.nodeId)) {
            marks.push(<em key="m" className="ov-tag-main">{t('mds.overview.mark.mainline')}</em>);
          } else {
            marks.push(<em key="b" className="ov-tag-bypass">{t('mds.overview.mark.bypass')}</em>);
          }
          if (r.holdPoint) marks.push(<em key="h" className="icm-warn">Hold</em>);
          const rw = model?.rework.get(r.stepCode);
          if (rw) {
            marks.push(<em key="r" className="icm-warn" title={rw.join(', ')}>{t('mds.overview.mark.rework')}→{rw[0]}</em>);
          }
          return <span className="ov-marks">{marks}</span>;
        },
      },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ],
    [t, model],
  );

  const qtimeColumns = useMemo<Array<DataTableColumn<QtimeRow>>>(
    () => [
      { key: 'from', titleKey: 'mds.flow.qtime.from', width: 150, render: (r) => <span className="mono">{r.from}</span> },
      { key: 'to', titleKey: 'mds.flow.qtime.to', width: 150, render: (r) => <span className="mono">{r.to}</span> },
      { key: 'maxMinutes', titleKey: 'mds.flow.field.qtimeMax', width: 110, render: (r) => <span className="mono">{r.maxMinutes} min</span> },
      { key: 'action', titleKey: 'mds.overview.col.qtimeAction', width: 90, render: (r) => <span className="mono">{r.action}</span> },
      {
        key: 'alarmCode',
        titleKey: 'mds.flow.qtime.alarmCode',
        render: (r) => (r.alarmCode ? <span className="mono">{r.alarmCode}</span> : <span className="dim">—</span>),
      },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ],
    [t],
  );

  const selFlow: FlowView | undefined = flows.find((f) => f.id === flowId);

  return (
    <Page>
      <div className="flow-overview-col">
        <Panel className="flow-overview-head">
          <div style={{ display: 'flex', gap: 8 }}>
            <select
              className="op-search"
              style={{ width: 340 }}
              value={flowId}
              onChange={(e) => setFlowId(e.target.value)}
            >
              <option value="">{t('mds.overview.selectFlow')}</option>
              {flows.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.flowCode} · {f.flowName}（{t(`mds.flow.status.${f.status}`)}）
                </option>
              ))}
            </select>
          </div>
          <p className="dim">{t('mds.overview.hint')}</p>
          {selFlow && (
            <div className="ov-stats">
              <span className="ov-stat"><b>{model?.stats.mainline ?? 0}</b>{t('mds.overview.stat.mainline')}</span>
              <span className="ov-stat"><b>{model?.stats.bypass ?? 0}</b>{t('mds.overview.stat.bypass')}</span>
              <span className="ov-stat icm-warn"><b>{model?.stats.rework ?? 0}</b>{t('mds.overview.stat.rework')}</span>
              <span className="ov-stat icm-warn"><b>{model?.stats.hold ?? 0}</b>{t('mds.overview.stat.hold')}</span>
              <span className="ov-stat"><b>{model?.stats.qtimes ?? 0}</b>{t('mds.overview.stat.qtime')}</span>
            </div>
          )}
        </Panel>

        {flowId && model ? (
          <>
            <DataTable<FlowStepView>
              titleKey="mds.overview.stepsTitle"
              columns={stepColumns}
              rows={model.steps}
              rowKey={(r) => r.id}
              loading={detailRes.loading}
            />
            <DataTable<QtimeRow>
              titleKey="mds.flow.qtime.title"
              columns={qtimeColumns}
              rows={model.qtimeRows}
              rowKey={(r) => r.key}
              loading={detailRes.loading}
              emptyTitleKey="mds.overview.qtimeEmpty"
            />
          </>
        ) : (
          <EmptyState titleKey="mds.overview.selectFlow" />
        )}
      </div>
    </Page>
  );
}
