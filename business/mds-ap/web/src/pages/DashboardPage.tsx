import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimSpin,
  DataTable,
  IconApps,
  IconMonitor,
  IconTree,
  InlineAlert,
  KpiCard,
  KpiGrid,
  Page,
  Panel,
  type DataTableColumn,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useHasPermission } from '@/lib/usePermission';
import type { Area, Fab, Site } from '@/types';

/** 模块级空数组：内联 `[]` 每次渲染都是新引用，会打断下游 memo。 */
const NO_SITES: Site[] = [];

interface Bucket {
  sites: Site[];
  fabs: Fab[];
  areas: Area[];
}

/**
 * 概览：场地层级的规模与结构。
 *
 * <p>取数说明（刻意的取舍）：这里并发拉三个列表接口在**前端**聚合 KPI，而不是新加一个
 * `GET /overview` 聚合端点——主数据规模是「基地个位数、Fab 十位数、Area 百位数」，
 * 三个列表的总量远小于一屏日志。等某个域涨到千行以上（如 Bay / Module）时，
 * 该域改成服务端聚合端点，不要把这个写法类推过去。</p>
 *
 * <p>按**权限码**逐个决定拉不拉：只授了 `mds:site:view` 的账号不该因为概览页而收到
 * 一个 403 错误横幅。未授的域对应 KPI 显示 `—`，而不是 0——0 会被读成「一个都没有」。</p>
 */
export default function DashboardPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const canSite = useHasPermission(MDS_PERMS.site.view);
  const canFab = useHasPermission(MDS_PERMS.fab.view);
  const canArea = useHasPermission(MDS_PERMS.area.view);
  const anyView = canSite || canFab || canArea;

  const { data, loading, error, refresh } = useResource<Bucket>(
    async ({ signal }) => {
      const [sites, fabs, areas] = await Promise.all([
        canSite ? api.listSites(undefined, signal) : Promise.resolve(NO_SITES),
        canFab ? api.listFabs(undefined, undefined, signal) : Promise.resolve<Fab[]>([]),
        canArea ? api.listAreas(undefined, undefined, signal) : Promise.resolve<Area[]>([]),
      ]);
      return { sites, fabs, areas };
    },
    { deps: [canSite, canFab, canArea], enabled: anyView },
  );

  const sites = data?.sites ?? NO_SITES;
  const fabs = data?.fabs;
  const areas = data?.areas;

  /** 每个基地下的 Fab 数（用于层级一览）。 */
  const fabCountOf = useMemo(() => {
    const m = new Map<string, number>();
    for (const f of fabs ?? []) m.set(f.siteCode, (m.get(f.siteCode) ?? 0) + 1);
    return m;
  }, [fabs]);

  const columns = useMemo<Array<DataTableColumn<Site>>>(
    () => [
      { key: 'siteCode', titleKey: 'mds.site.code', mono: true, width: 160 },
      { key: 'siteName', titleKey: 'mds.site.name', width: 200 },
      {
        key: 'address',
        titleKey: 'mds.site.address',
        render: (r) => <span className="dim">{r.address ?? t('mds.common.dash')}</span>,
      },
      {
        key: 'fabCount',
        titleKey: 'mds.site.fabCount',
        width: 100,
        align: 'right',
        render: (r) => {
          const n = fabCountOf.get(r.siteCode);
          // 没授 Fab 查看权时不算这个数：显示 0 是在撒谎
          return n == null ? t('mds.common.dash') : n;
        },
      },
      {
        key: 'enabled',
        titleKey: 'mds.common.enabled',
        width: 100,
        render: (r) => (
          <span className={r.enabled ? undefined : 'dim'}>
            {t(r.enabled ? 'mds.common.enabled' : 'mds.common.disabled')}
          </span>
        ),
      },
    ],
    [t, fabCountOf],
  );

  const disabled = (rows: Array<{ enabled: boolean }> | undefined) =>
    rows ? rows.filter((x) => !x.enabled).length : undefined;

  /** 一个 KPI 的值与副标题：无权限 → `—`，有权限 → 启用数 / 总数 + 停用数。 */
  function kpiOf(rows: Array<{ enabled: boolean }> | undefined) {
    if (!rows) return { value: t('mds.common.dash'), sub: undefined };
    const off = disabled(rows) ?? 0;
    return {
      value: `${rows.length - off} / ${rows.length}`,
      sub: off > 0 ? t('mds.dashboard.kpi.disabled', { n: off }) : undefined,
    };
  }

  const siteKpi = kpiOf(sites);
  const fabKpi = kpiOf(fabs);
  const areaKpi = kpiOf(areas);

  return (
    <Page>
      {!anyView ? (
        <InlineAlert tone="warn">{t('mds.forbiddenDesc')}</InlineAlert>
      ) : error ? (
        <Panel titleKey="mds.dashboard.title">
          <InlineAlert tone="err">{error.message || t('mds.common.none')}</InlineAlert>
          <CimButton size="small" style={{ marginTop: 12 }} onClick={refresh}>
            {t('mds.common.retry')}
          </CimButton>
        </Panel>
      ) : loading && !data ? (
        <Panel titleKey="mds.dashboard.title">
          <CimSpin block height={240} />
        </Panel>
      ) : (
        <>
          <KpiGrid>
            <KpiCard
              icon={<IconApps width={16} height={16} />}
              tone="ok"
              label={t('mds.dashboard.kpi.sites')}
              value={siteKpi.value}
              sub={siteKpi.sub}
            />
            <KpiCard
              icon={<IconMonitor width={16} height={16} />}
              tone="info"
              label={t('mds.dashboard.kpi.fabs')}
              value={fabKpi.value}
              sub={fabKpi.sub}
            />
            <KpiCard
              icon={<IconTree width={16} height={16} />}
              tone="info"
              label={t('mds.dashboard.kpi.areas')}
              value={areaKpi.value}
              sub={areaKpi.sub}
            />
          </KpiGrid>

          {/* 常用入口：标题与按钮放同一行（`actions`），不再用 `<Panel flush><Toolbar>`
              —— 那样是「标题行 + 只有左侧按钮的空工具栏行」，一个 60px 高的面板里
              装着三个按钮，看着像没填完。 */}
          <Panel
            titleKey="mds.dashboard.quick"
            actions={
              <>
                {canSite ? (
                  <CimButton size="small" onClick={() => navigate('/sites')}>
                    {t('mds.nav.sites')}
                  </CimButton>
                ) : null}
                {canFab ? (
                  <CimButton size="small" onClick={() => navigate('/fabs')}>
                    {t('mds.nav.fabs')}
                  </CimButton>
                ) : null}
                {canArea ? (
                  <CimButton size="small" onClick={() => navigate('/areas')}>
                    {t('mds.nav.areas')}
                  </CimButton>
                ) : null}
              </>
            }
          />

          {canSite ? (
              <DataTable<Site>
                titleKey="mds.dashboard.siteList"
                columns={columns}
                rows={sites}
                rowKey={(r) => r.id}
                loading={loading}
                onRefresh={refresh}
                emptyTitleKey="mds.common.none"
                emptyHintKey="mds.dashboard.emptyHint"
                scrollX="max-content"
              />
          ) : null}
        </>
      )}
    </Page>
  );
}
