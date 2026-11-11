import { Fragment, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimTag,
  CimTree,
  DataTable,
  Page,
  PageHead,
  Panel,
  useToast,
  type CimTreeNode,
  type DataTableColumn,
  CimButton,
  InlineAlert,
  KpiCard,
} from '@cim/ui';
import RecipeDetailDrawer from '@/components/RecipeDetailDrawer';
import * as api from '@/lib/rmsApi';
import type {
  AreaNode,
  Device,
  DeviceRecipeRow,
  DeviceRecipeView,
  RecipeVersion,
} from '@/types';

const NO_AREAS: AreaNode[] = [];
const NO_DEVICES: Device[] = [];
const NO_ROWS: DeviceRecipeRow[] = [];

/** 树节点标题：主文案 + 右侧补充（编码 / 标签），单行省略。 */
function nodeLabel(main: string, extra?: ReactNode) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, minWidth: 0, maxWidth: '100%' }}>
      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{main}</span>
      {extra}
    </span>
  );
}

/**
 * 设备视角动线（一条线走完）：工厂 → 区域 → 机台 → 配方 → 配方详情。
 *
 * <p>左侧树顶层节点即「工厂」（区域树自引用，parent=null 的顶层=工厂），
 * 选中机台后自动展开其祖先链，并在右侧以面包屑呈现完整路径，
 * 点面包屑任一层级可跳回树中对应位置。</p>
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>手写递归树 → `CimTree`（受控展开/选中）。旧实现每次渲染都重建整棵 DOM，
 *       且折叠箭头对「无子节点的区域」也渲染成可点，点了没反应。</li>
 *   <li>机台与区域同树，选中靠 `selectedKeys`；旧实现给机台行加 `.on` 类，
 *       切机台后旧行不会清（浏览器里能看到两个高亮）。</li>
 *   <li>取数走 `useResource`：切机台时旧视图保留到新回包到达（`keepPreviousData`），
 *       不再出现右侧先空一帧再填。</li>
 * </ul>
 */
export default function DeviceRecipePage() {
  const { t } = useTranslation();
  const toast = useToast();

  const [deviceId, setDeviceId] = useState('');
  const [expanded, setExpanded] = useState<string[]>([]);
  const [detail, setDetail] = useState<{ row: DeviceRecipeRow; versions: RecipeVersion[] } | null>(null);
  const seeded = useRef(false);

  const baseRes = useResource(async () => {
    const [areas, devices] = await Promise.all([api.areaTree(), api.listDevices()]);
    return { areas, devices };
  });
  const areas = baseRes.data?.areas ?? NO_AREAS;
  const devices = baseRes.data?.devices ?? NO_DEVICES;

  const areaById = useMemo(() => {
    const m = new Map<string, AreaNode>();
    const walk = (ns: AreaNode[]) => {
      for (const n of ns) {
        m.set(n.id, n);
        if (n.children) walk(n.children);
      }
    };
    walk(areas);
    return m;
  }, [areas]);

  /** 机台 → [工厂, ..., 直属区域] 路径。 */
  const pathOf = useMemo(() => {
    const m = new Map<string, AreaNode[]>();
    for (const d of devices) {
      const chain: AreaNode[] = [];
      let cur = d.areaId ? areaById.get(d.areaId) : undefined;
      while (cur) {
        chain.unshift(cur);
        cur = cur.parentId ? areaById.get(cur.parentId) : undefined;
      }
      m.set(d.id, chain);
    }
    return m;
  }, [devices, areaById]);

  // 首屏：展开所有工厂 + 选中首台机台并展开其祖先链（只做一次，之后尊重用户展开状态）
  useEffect(() => {
    if (seeded.current || !baseRes.loaded) return;
    if (!areas.length && !devices.length) return;
    seeded.current = true;
    const exp = new Set<string>(areas.map((a) => a.id));
    const first = devices[0];
    if (first) {
      setDeviceId(first.id);
      (pathOf.get(first.id) ?? []).forEach((a) => exp.add(a.id));
    }
    setExpanded([...exp]);
  }, [baseRes.loaded, areas, devices, pathOf]);

  const viewRes = useResource(() => api.deviceRecipes(deviceId), {
    deps: [deviceId],
    enabled: !!deviceId,
    keepPreviousData: true,
  });
  const view: DeviceRecipeView | undefined = viewRes.data;

  const devicesByArea = useMemo(() => {
    const m = new Map<string, Device[]>();
    for (const d of devices) {
      const key = d.areaId || '__none__';
      const list = m.get(key);
      if (list) list.push(d);
      else m.set(key, [d]);
    }
    return m;
  }, [devices]);

  const deviceIds = useMemo(() => new Set(devices.map((d) => d.id)), [devices]);

  const nodes = useMemo<CimTreeNode[]>(() => {
    const factoryTag = (
      <CimTag size="small" tone="run">
        {t('rms.deviceRecipes.factoryTag')}
      </CimTag>
    );
    const walk = (n: AreaNode, isRoot: boolean): CimTreeNode => ({
      key: n.id,
      searchText: `${n.name} ${n.code}`,
      labelNode: nodeLabel(n.name, isRoot ? factoryTag : <span className="dim">{n.code}</span>),
      children: [
        ...(n.children ?? []).map((c) => walk(c, false)),
        ...(devicesByArea.get(n.id) ?? []).map((d) => ({
          key: d.id,
          searchText: `${d.code} ${d.name}`,
          labelNode: nodeLabel(`${d.code} · ${d.name}`),
          isLeaf: true,
        })),
      ],
    });
    const out = areas.map((a) => walk(a, true));
    const unassigned = devicesByArea.get('__none__') ?? [];
    if (unassigned.length) {
      out.push({
        key: '__unassigned__',
        label: t('rms.deviceRecipes.unassigned'),
        children: unassigned.map((d) => ({
          key: d.id,
          searchText: `${d.code} ${d.name}`,
          labelNode: nodeLabel(`${d.code} · ${d.name}`),
          isLeaf: true,
        })),
      });
    }
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [areas, devicesByArea, t]);

  /** 选中机台并展开其全部祖先（保持树与面包屑同步）。 */
  const revealDevice = (id: string) => {
    setDeviceId(id);
    const chain = pathOf.get(id) ?? [];
    setExpanded((prev) => [...new Set([...prev, ...chain.map((a) => a.id)])]);
  };

  const onSelectNode = (key: string) => {
    if (deviceIds.has(key)) revealDevice(key);
    else setExpanded((prev) => [...new Set([...prev, key])]);
  };

  /** 面包屑点击：展开到该层级（含祖先）。 */
  const expandToArea = (areaId: string) => {
    const chain: string[] = [];
    let cur: AreaNode | undefined = areaById.get(areaId);
    while (cur) {
      chain.push(cur.id);
      cur = cur.parentId ? areaById.get(cur.parentId) : undefined;
    }
    setExpanded((prev) => [...new Set([...prev, ...chain])]);
  };

  const openDetail = async (row: DeviceRecipeRow) => {
    if (!row.recipeId) return;
    try {
      const versions = await api.listVersions(row.recipeId);
      setDetail({ row, versions });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const currentDevice = devices.find((d) => d.id === deviceId) ?? null;
  const currentPath = currentDevice ? pathOf.get(currentDevice.id) ?? [] : [];

  const columns = useMemo<Array<DataTableColumn<DeviceRecipeRow>>>(
    () => [
      {
        key: 'code',
        titleKey: 'rms.deviceRecipes.recipe',
        mono: true,
        width: 190,
        render: (r) =>
          r.code ? (
            <>
              {r.code}
              {r.name ? <span className="muted"> · {r.name}</span> : null}
            </>
          ) : (
            <span className="muted">{t('rms.deviceRecipes.unmanaged')}</span>
          ),
      },
      {
        key: 'ppidOnTool',
        titleKey: 'rms.deviceRecipes.ppid',
        mono: true,
        width: 130,
        render: (r) => r.ppidOnTool ?? t('rms.common.dash'),
      },
      {
        key: 'chamberCode',
        titleKey: 'rms.deviceRecipes.chamber',
        mono: true,
        width: 150,
        render: (r) =>
          r.chamberCode ? (
            <>
              {r.chamberCode}
              {r.chamberName ? <span className="muted"> · {r.chamberName}</span> : null}
            </>
          ) : (
            t('rms.common.dash')
          ),
      },
      {
        key: 'activeVersionNo',
        titleKey: 'rms.deviceRecipes.version',
        mono: true,
        width: 130,
        render: (r) => (
          <>
            {r.activeVersionNo ? `v${r.activeVersionNo}` : t('rms.common.dash')}
            {r.versionStatus ? (
              <CimTag size="small" tone={r.versionStatus === 'ACTIVE' ? 'ok' : 'idle'}>
                {t('rms.recipe.status.' + r.versionStatus, { defaultValue: r.versionStatus })}
              </CimTag>
            ) : null}
          </>
        ),
      },
      {
        key: 'qualificationStatus',
        titleKey: 'rms.deviceRecipes.qualification',
        width: 120,
        render: (r) => (
          <CimTag size="small" tone={r.qualificationStatus === 'GRANTED' ? 'ok' : 'warn'}>
            {r.qualificationStatus ?? 'NOT_QUALIFIED'}
          </CimTag>
        ),
      },
      {
        key: 'onToolState',
        titleKey: 'rms.deviceRecipes.onTool',
        width: 110,
        render: (r) =>
          r.onToolState ? (
            <CimTag
              size="small"
              tone={r.onToolState === 'OK' ? 'ok' : r.onToolState === 'DRIFT' ? 'err' : 'warn'}
            >
              {r.onToolState}
            </CimTag>
          ) : (
            <span className="muted">{t('rms.common.dash')}</span>
          ),
      },
      {
        key: 'sources',
        titleKey: 'rms.deviceRecipes.sources',
        render: (r) => <span className="muted">{r.sources.join(' · ')}</span>,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <PageHead titleKey="rms.deviceRecipes.title" subtitleKey="rms.deviceRecipes.sub" />

      <div className="split">
        <Panel
          titleKey="rms.deviceRecipes.areas"
          flush
          className="scroll-y"
        >
          {baseRes.error ? (
            <InlineAlert tone="err" style={{ margin: 14 }}>{baseRes.error.message}</InlineAlert>
          ) : null}
          {nodes.length === 0 && baseRes.loaded ? (
            <div className="empty">{t('rms.deviceRecipes.noAreas')}</div>
          ) : (
            <div style={{ padding: '8px 6px' }}>
              <CimTree
                nodes={nodes}
                selectedKeys={deviceId ? [deviceId] : []}
                onSelect={onSelectNode}
                expandedKeys={expanded}
                onExpand={setExpanded}
              />
            </div>
          )}
        </Panel>

        <Panel
          title={view ? `${view.deviceCode} · ${view.deviceName}` : t('rms.deviceRecipes.pickDevice')}
          actions={view?.deviceTypeName ? <span className="muted">{view.deviceTypeName}</span> : undefined}
          flush
        >
          {currentDevice && (
            <div className="dcrumb" title={t('rms.deviceRecipes.pathTitle')} style={{ margin: '10px 14px 0' }}>
              {currentPath.map((a, i) => (
                <Fragment key={a.id}>
                  {i > 0 && <span className="dcrumb-sep">/</span>}
                  <CimButton bare className="dcrumb-link" onClick={() => expandToArea(a.id)}>
                    {a.name}
                    {i === 0 && (
                      <CimTag size="small" tone="run">
                        {t('rms.deviceRecipes.factoryTag')}
                      </CimTag>
                    )}
                  </CimButton>
                </Fragment>
              ))}
              <span className="dcrumb-sep">/</span>
              <span className="dcrumb-here">
                {currentDevice.code} · {currentDevice.name}
              </span>
            </div>
          )}

          {view && (
            <div className="kpi-grid" style={{ margin: '10px 14px 0' }}>
              <KpiCard label={t('rms.deviceRecipes.total')} value={view.total} />
              <KpiCard tone="warn" label={t('rms.deviceRecipes.orphan')} value={view.orphan} />
              <KpiCard tone="info" label={t('rms.deviceRecipes.drift')} value={view.drift} />
              <KpiCard label={t('rms.deviceRecipes.unqualified')} value={view.unqualified} />
            </div>
          )}

          <DataTable<DeviceRecipeRow>
            flat
            columns={columns}
            rows={view?.rows ?? NO_ROWS}
            rowKey={(r) => r.recipeId ?? `orphan-${r.ppidOnTool}-${r.chamberCode ?? ''}`}
            loading={viewRes.loading}
            error={viewRes.error?.message}
            onRetry={viewRes.refresh}
            onRefresh={viewRes.refresh}
            scrollX="max-content"
            emptyTitleKey="rms.deviceRecipes.empty"
            onRowClick={(r) => void openDetail(r)}
          />
        </Panel>
      </div>

      {detail && (
        <RecipeDetailDrawer
          recipeId={detail.row.recipeId as string}
          recipeCode={detail.row.code}
          recipeName={detail.row.name}
          versions={detail.versions}
          initialVersionId={detail.row.activeVersionId}
          onClose={() => setDetail(null)}
          onChanged={() => viewRes.refresh()}
        />
      )}
    </Page>
  );
}
