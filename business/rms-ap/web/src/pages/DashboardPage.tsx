import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { useResource } from '@cim/data';
import {
  CimTag,
  DataTable,
  KpiGrid,
  Page,
  PageHead,
  Panel,
  type DataTableColumn,
} from '@cim/ui';
import * as api from '@/lib/rmsApi';
import { fmtDateTime } from '@/lib/format';
import type {
  AlarmView,
  InventoryRow,
  InventorySummary,
  Recipe,
  Signoff,
  SignoffSummary,
} from '@/types';

const NO_ROWS: never[] = [];

/** 计数用的小账本：记录有几路请求失败（allSettled 语义下失败不应静默成 0）。 */
interface Ledger {
  failed: number;
}

async function settle<T>(p: Promise<T>, fallback: T, ledger: Ledger): Promise<T> {
  try {
    return await p;
  } catch {
    ledger.failed += 1;
    return fallback;
  }
}

/**
 * 概览 · 任务中心（P2 重组）：进系统第一屏回答「现在有什么要处理」——
 * 待批签核 / 机台配方漂移 / 孤儿配方 / 未确认报警，每条可点击直达处理现场；
 * 数量型统计（配方/机台总数）降级为次要信息。
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>七路请求合并成一个 `useResource`（`Promise.allSettled` 语义保留：任一路挂掉
 *       不影响其余卡片，但**失败路数会被统计出来**并在页头提示 —— 旧实现静默成 0，
 *       一眼看去像「没有待办」）。</li>
 *   <li>三张清单从手写 `<table>` 改为 `DataTable flat`（嵌在 `Panel` 里，不画第二层框），
 *       表头/空态/列设置与其它页一致。</li>
 * </ul>
 */
export default function DashboardPage() {
  const { t } = useTranslation();
  const nav = useNavigate();

  const res = useResource(async () => {
    const ledger: Ledger = { failed: 0 };
    const [summary, inv, pending, drift, orphan, alarms, recipes] = await Promise.all([
      settle<SignoffSummary | null>(api.getSignoffSummary(), null, ledger),
      settle<InventorySummary | null>(api.toolInventorySummary(), null, ledger),
      settle<Signoff[]>(api.listSignoffs({ status: 'PENDING' }), [], ledger),
      settle<InventoryRow[]>(api.listToolInventory({ result: 'DRIFT' }), [], ledger),
      settle<InventoryRow[]>(api.listToolInventory({ result: 'ORPHAN' }), [], ledger),
      settle<AlarmView[]>(api.listAlarms({ unackOnly: true }), [], ledger),
      settle<Recipe[]>(api.listRecipes(), [], ledger),
    ]);
    return { summary, inv, pending, drift, orphan, alarms, recipes, failed: ledger.failed };
  });

  const data = res.data;
  const loading = res.loading && !res.loaded;
  const summary = data?.summary;
  const inv = data?.inv;
  const pending = data?.pending ?? NO_ROWS;
  const drift = data?.drift ?? NO_ROWS;
  const orphan = data?.orphan ?? NO_ROWS;
  const alarms = data?.alarms ?? NO_ROWS;
  const recipes = data?.recipes ?? NO_ROWS;

  const cards = [
    {
      n: summary?.pending ?? 0,
      label: t('rms.dashboard.todoSignoff'),
      to: '/signoff',
      tone: (summary?.pending ?? 0) > 0 ? 'warn' : 'ok',
    },
    {
      n: inv?.drift ?? 0,
      label: t('rms.dashboard.todoDrift'),
      to: '/device-recipes',
      tone: (inv?.drift ?? 0) > 0 ? 'err' : 'ok',
    },
    {
      n: inv?.orphan ?? 0,
      label: t('rms.dashboard.todoOrphan'),
      to: '/device-recipes',
      tone: (inv?.orphan ?? 0) > 0 ? 'err' : 'ok',
    },
    {
      n: alarms.length,
      label: t('rms.dashboard.todoAlarm'),
      to: '/integration',
      tone: alarms.length > 0 ? 'warn' : 'ok',
    },
    {
      n: summary?.tempActive ?? 0,
      label: t('rms.dashboard.todoTemp'),
      to: '/signoff',
      tone: (summary?.tempActive ?? 0) > 0 ? 'warn' : 'ok',
    },
    {
      n: summary?.expiringSoon ?? 0,
      label: t('rms.dashboard.todoExpiring'),
      to: '/signoff',
      tone: (summary?.expiringSoon ?? 0) > 0 ? 'warn' : 'ok',
    },
  ];

  const todos = loading ? 0 : pending.length + drift.length + orphan.length;
  const banner = loading
    ? ''
    : todos > 0
      ? t('rms.dashboard.todoBanner', { n: todos })
      : t('rms.dashboard.allClear');
  const partial = (data?.failed ?? 0) > 0 ? t('rms.dashboard.partialFailed', { n: data?.failed }) : '';

  const pendingColumns = useMemo<Array<DataTableColumn<Signoff>>>(
    () => [
      {
        key: 'recipeCode',
        titleKey: 'rms.recipe.code',
        mono: true,
        width: 170,
        render: (s) => s.recipeCode ?? t('rms.common.dash'),
      },
      {
        key: 'versionNo',
        titleKey: 'rms.recipe.version',
        mono: true,
        width: 90,
        render: (s) => (s.versionNo != null ? `v${s.versionNo}` : t('rms.common.dash')),
      },
      {
        key: 'reason',
        titleKey: 'rms.signoff.reason',
        ellipsis: true,
        render: (s) => s.reason ?? t('rms.common.dash'),
      },
      {
        key: 'createTime',
        titleKey: 'rms.common.time',
        width: 160,
        render: (s) => <span className="muted">{fmtDateTime(s.createTime)}</span>,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const issueColumns = useMemo<Array<DataTableColumn<InventoryRow>>>(
    () => [
      {
        key: 'deviceCode',
        titleKey: 'rms.device.code',
        mono: true,
        width: 150,
        render: (r) => r.deviceCode ?? t('rms.common.dash'),
      },
      { key: 'ppidOnTool', titleKey: 'rms.dashboard.ppid', mono: true, width: 130 },
      {
        key: 'reconcileResult',
        titleKey: 'rms.common.status',
        width: 100,
        render: (r) => (
          <CimTag size="small" tone={r.reconcileResult === 'DRIFT' ? 'err' : 'warn'}>
            {r.reconcileResult === 'DRIFT' ? t('rms.dashboard.drift') : t('rms.dashboard.orphan')}
          </CimTag>
        ),
      },
      {
        key: 'lastSeenAt',
        titleKey: 'rms.common.time',
        width: 160,
        render: (r) => <span className="muted">{fmtDateTime(r.lastSeenAt)}</span>,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const recipeColumns = useMemo<Array<DataTableColumn<Recipe>>>(
    () => [
      { key: 'code', titleKey: 'rms.recipe.code', mono: true, width: 170 },
      { key: 'name', titleKey: 'rms.recipe.name' },
      {
        key: 'activeVersionNo',
        titleKey: 'rms.recipe.activeVersion',
        mono: true,
        width: 110,
        render: (r) => r.activeVersionNo ?? t('rms.common.dash'),
      },
      {
        key: 'updateTime',
        titleKey: 'rms.common.time',
        width: 160,
        render: (r) => <span className="muted">{fmtDateTime(r.updateTime)}</span>,
      },
    ],
    [t],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.dashboard.title"
        subtitle={banner}
        actions={<span className="muted">{partial}</span>}
      />

      {/* 行动卡片：点卡片直达处理现场 */}
      <KpiGrid>
        {cards.map((k) => (
          <Link key={k.label} to={k.to} className="kpi">
            <div className={'tag ' + k.tone} style={{ marginBottom: 6 }}>
              {loading ? '—' : k.n}
            </div>
            <div className="kpi-label">{k.label}</div>
          </Link>
        ))}
      </KpiGrid>

      <div className="grid-2">
        {/* 待批签核清单 */}
        <Panel
          titleKey="rms.dashboard.pendingSignoffs"
          actions={
            <Link to="/signoff" className="btn btn-ghost btn-sm">
              {t('rms.nav.signoff')}
            </Link>
          }
          flush
        >
          <DataTable<Signoff>
            flat
            columns={pendingColumns}
            rows={pending.slice(0, 8)}
            rowKey={(s) => s.id}
            loading={loading}
            scrollX="max-content"
            emptyTitleKey="rms.dashboard.nonePending"
            onRowClick={() => nav('/signoff')}
          />
        </Panel>

        {/* 机台配方异常清单（漂移 + 孤儿） */}
        <Panel
          titleKey="rms.dashboard.deviceIssues"
          actions={
            <Link to="/device-recipes" className="btn btn-ghost btn-sm">
              {t('rms.nav.deviceRecipes')}
            </Link>
          }
          flush
        >
          <DataTable<InventoryRow>
            flat
            columns={issueColumns}
            rows={[...drift, ...orphan].slice(0, 8)}
            rowKey={(r) => r.id}
            loading={loading}
            scrollX="max-content"
            emptyTitleKey="rms.dashboard.noneIssues"
            onRowClick={() => nav('/device-recipes')}
          />
        </Panel>
      </div>

      {/* 次要：最近配方（保留原有上下文） */}
      <Panel
        titleKey="rms.dashboard.recent"
        actions={
          <Link to="/recipes" className="btn btn-ghost btn-sm">
            {t('rms.nav.recipes')}
          </Link>
        }
        flush
      >
        <DataTable<Recipe>
          flat
          columns={recipeColumns}
          rows={recipes.slice(0, 8)}
          rowKey={(r) => r.id}
          loading={loading}
          error={res.error?.message}
          onRetry={res.refresh}
          onRefresh={res.refresh}
          scrollX="max-content"
          emptyTitleKey="rms.common.noData"
        />
      </Panel>
    </Page>
  );
}
