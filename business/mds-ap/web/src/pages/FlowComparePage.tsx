import { useEffect, useMemo, useState } from 'react';
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
import type { FlowView } from '@/types';

type FlowRow = FlowView;
type NodeEff = api.NodeEffective;

interface DiffRow {
  key: string;
  leftValue: string;
  rightValue: string;
  same: boolean;
}

/** 节点生效值扁平为「字段 → 值(来源)」字符串集。 */
function flatten(n: NodeEff): Map<string, string> {
  const m = new Map<string, string>();
  const put = (k: string, e: { value: unknown; source: string }) => {
    m.set(k, `${e.value ?? '—'}〔${e.source}〕`);
  };
  put('recipeRef', n.recipeRef);
  put('equipType', n.equipType);
  put('qtimeMin', n.qtimeMin);
  put('qtimeMax', n.qtimeMax);
  put('reworkLimit', n.reworkLimit);
  put('batchMode', n.batchMode);
  put('batchSize', n.batchSize);
  put('reticleRef', n.reticleRef);
  put('contamLevel', n.contamLevel);
  put('contamForbidden', n.contamForbidden);
  put('capabilityCode', n.capabilityCode);
  put('edcPlanCode', n.edcPlanCode);
  m.set('pool', n.pool.length ? `${n.poolSource} ×${n.pool.length}` : 'MISSING');
  Object.entries(n.attrs).forEach(([k, v]) => m.set(`attr:${k}`, `${v.value ?? '—'}〔${v.source}〕`));
  return m;
}

/**
 * 流程对比（FlowComparePage，V22 ICM）——按 ContextResolver 解析生效值 diff：
 * 继承/流程级变化也可见（不是裸字段对比）；左选框选流程，diff 按 stepCode 对齐。
 */
export default function FlowComparePage() {
  const { t } = useTranslation();

  const flowsRes = useResource(({ signal }) => api.listFlows(undefined, signal));
  const flows = flowsRes.data ?? [];

  const [leftId, setLeftId] = useState('');
  const [rightId, setRightId] = useState('');

  const leftRes = useResource(
    ({ signal }) => (leftId ? api.getEffective(leftId, undefined, signal) : Promise.resolve([] as NodeEff[])),
    { deps: [leftId] },
  );
  const rightRes = useResource(
    ({ signal }) => (rightId ? api.getEffective(rightId, undefined, signal) : Promise.resolve([] as NodeEff[])),
    { deps: [rightId] },
  );

  const leftLabel = flows.find((f) => f.id === leftId);
  const rightLabel = flows.find((f) => f.id === rightId);

  const diffRows = useMemo<DiffRow[]>(() => {
    if (!leftId || !rightId) return [];
    const L = leftRes.data ?? [];
    const R = rightRes.data ?? [];
    const lByCode = new Map(L.map((n) => [n.stepCode, n]));
    const rByCode = new Map(R.map((n) => [n.stepCode, n]));
    const codes = Array.from(new Set([...lByCode.keys(), ...rByCode.keys()])).sort();
    const rows: DiffRow[] = [];
    for (const code of codes) {
      const l = lByCode.get(code);
      const r = rByCode.get(code);
      if (!l || !r) {
        rows.push({ key: code, leftValue: l ? t('mds.compare.nodePresent') : '—', rightValue: r ? t('mds.compare.nodePresent') : '—', same: false });
        continue;
      }
      const lm = flatten(l);
      const rm = flatten(r);
      const keys = Array.from(new Set([...lm.keys(), ...rm.keys()])).sort();
      for (const k of keys) {
        const lv = lm.get(k) ?? '—';
        const rv = rm.get(k) ?? '—';
        rows.push({ key: `${code} · ${k}`, leftValue: lv, rightValue: rv, same: lv === rv });
      }
    }
    return rows;
  }, [leftId, rightId, leftRes.data, rightRes.data, t]);

  useEffect(() => {
    // 默认选中前两个不同流程，减少空操作
    if (!leftId && flows.length) setLeftId(flows[0].id);
    if (!rightId && flows.length > 1) setRightId(flows[1].id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flows]);

  const columns = useMemo<Array<DataTableColumn<DiffRow>>>(
    () => [
      { key: 'key', titleKey: 'mds.compare.col.item', width: 240, mono: true },
      {
        key: 'leftValue',
        titleKey: 'mds.compare.col.left',
        width: 220,
        render: (r) => <span className={r.same ? '' : 'icm-diff-l'}>{r.leftValue}</span>,
      },
      {
        key: 'rightValue',
        titleKey: 'mds.compare.col.right',
        width: 220,
        render: (r) => <span className={r.same ? '' : 'icm-diff-r'}>{r.rightValue}</span>,
      },
      {
        key: 'same',
        titleKey: 'mds.compare.col.same',
        width: 70,
        render: (r) => (r.same ? <span className="dim">{t('mds.compare.same')}</span> : <span className="icm-diff-l">{t('mds.compare.diff')}</span>),
      },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ], [t]);

  return (
    <Page>
      <div className="flow-compare-col">
        <Panel className="flow-compare-panel">
          <div className="op-copy-bar" style={{ display: 'flex', gap: 8 }}>
            <select className="op-search" style={{ width: 260 }} value={leftId} onChange={(e) => setLeftId(e.target.value)}>
              <option value="">{t('mds.compare.selectLeft')}</option>
              {flows.map((f: FlowRow) => (
                <option key={f.id} value={f.id}>{f.flowCode} · {f.flowName}（{t(`mds.flow.status.${f.status}`)}）</option>
              ))}
            </select>
            <span className="dim">⇄</span>
            <select className="op-search" style={{ width: 260 }} value={rightId} onChange={(e) => setRightId(e.target.value)}>
              <option value="">{t('mds.compare.selectRight')}</option>
              {flows.map((f: FlowRow) => (
                <option key={f.id} value={f.id}>{f.flowCode} · {f.flowName}（{t(`mds.flow.status.${f.status}`)}）</option>
              ))}
            </select>
          </div>
          <p className="dim">{t('mds.compare.hint')}</p>
        </Panel>

        {leftId && rightId && leftLabel && rightLabel ? (
          <DataTable<DiffRow>
            titleKey="mds.compare.resultTitle"
            columns={columns}
            rows={diffRows}
            rowKey={(r) => r.key}
            loading={leftRes.loading || rightRes.loading}
            emptyTitleKey="mds.compare.empty"
          />
        ) : (
          <EmptyState titleKey="mds.compare.selectHint" />
        )}
      </div>
    </Page>
  );
}
