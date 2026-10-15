import { Fragment, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/rmsApi';
import RecipeDetailDrawer from '@/components/RecipeDetailDrawer';
import type {
  AreaNode,
  Device,
  DeviceRecipeRow,
  DeviceRecipeView,
  RecipeVersion,
} from '@/types';

/** 树折叠箭头（内联 SVG，避免图标库）。 */
function Chevron() {
  return (
    <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
      <path d="M3 1.5 L7 5 L3 8.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * 设备视角动线（一条线走完）：工厂 → 区域 → 机台 → 配方 → 配方详情。
 *
 * <p>左侧树顶层节点即「工厂」（区域树自引用，parent=null 的顶层=工厂），
 * 选中机台后自动展开其祖先链，并在右侧以面包屑呈现完整路径，
 * 点面包屑任一层级可跳回树中对应位置。</p>
 */
export default function DeviceRecipePage() {
  const { t } = useTranslation();
  const [areas, setAreas] = useState<AreaNode[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [deviceId, setDeviceId] = useState('');
  const [view, setView] = useState<DeviceRecipeView | null>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState('');
  const [detail, setDetail] = useState<{ row: DeviceRecipeRow; versions: RecipeVersion[] } | null>(null);

  useEffect(() => {
    Promise.all([api.areaTree(), api.listDevices()])
      .then(([ar, dv]) => {
        setAreas(ar);
        setDevices(dv);
        // 展开所有顶层工厂；选中首台机台并展开其祖先链（首屏即可见选中态）
        const flat: Record<string, AreaNode> = {};
        const walk = (ns: AreaNode[]) => {
          for (const n of ns) {
            flat[n.id] = n;
            if (n.children) walk(n.children);
          }
        };
        walk(ar);
        const exp = new Set<string>(ar.map((a) => a.id));
        const first = dv[0];
        if (first) {
          setDeviceId(first.id);
          let cur = first.areaId ? flat[first.areaId] : undefined;
          while (cur) {
            exp.add(cur.id);
            cur = cur.parentId ? flat[cur.parentId] : undefined;
          }
        }
        setExpanded(exp);
      })
      .catch((e) => setErr(e?.message || 'error'));
  }, []);

  useEffect(() => {
    if (!deviceId) return;
    setLoading(true);
    api
      .deviceRecipes(deviceId)
      .then(setView)
      .catch((e) => setErr(e?.message || 'error'))
      .finally(() => setLoading(false));
  }, [deviceId]);

  /** 区域扁平索引（含子孙），供面包屑上溯与展开链计算。 */
  const areaById = useMemo(() => {
    const m: Record<string, AreaNode> = {};
    const walk = (ns: AreaNode[]) => {
      for (const n of ns) {
        m[n.id] = n;
        if (n.children) walk(n.children);
      }
    };
    walk(areas);
    return m;
  }, [areas]);

  /** 机台 → [工厂, ..., 直属区域] 路径。 */
  const pathOf = useMemo(() => {
    const m: Record<string, AreaNode[]> = {};
    for (const d of devices) {
      const chain: AreaNode[] = [];
      let cur = d.areaId ? areaById[d.areaId] : undefined;
      while (cur) {
        chain.unshift(cur);
        cur = cur.parentId ? areaById[cur.parentId] : undefined;
      }
      m[d.id] = chain;
    }
    return m;
  }, [devices, areaById]);

  /** 选中机台并展开其全部祖先（保持树与面包屑同步）。 */
  function revealDevice(d: Device) {
    setDeviceId(d.id);
    const chain = pathOf[d.id] ?? [];
    setExpanded((prev) => {
      const next = new Set(prev);
      chain.forEach((a) => next.add(a.id));
      return next;
    });
  }

  /** 面包屑点击：展开到该层级（含祖先），机台层则直接选中。 */
  function expandToArea(areaId: string) {
    const chain: string[] = [];
    let cur: AreaNode | undefined = areaById[areaId];
    while (cur) {
      chain.push(cur.id);
      cur = cur.parentId ? areaById[cur.parentId] : undefined;
    }
    setExpanded((prev) => {
      const next = new Set(prev);
      chain.forEach((id) => next.add(id));
      return next;
    });
  }

  const devicesByArea = useMemo(() => {
    const m: Record<string, Device[]> = {};
    for (const d of devices) {
      const key = d.areaId || '__none__';
      (m[key] ||= []).push(d);
    }
    return m;
  }, [devices]);

  function toggle(id: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function openDetail(row: DeviceRecipeRow) {
    if (!row.recipeId) return;
    try {
      const versions = await api.listVersions(row.recipeId);
      setDetail({ row, versions });
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  function renderBranch(node: AreaNode, isRoot = false) {
    const kids = node.children || [];
    const devs = devicesByArea[node.id] || [];
    const total = kids.length + devs.length;
    const open = expanded.has(node.id);
    return (
      <div key={node.id} className="ptree-node">
        <div className="tree-row">
          <button
            className={'tree-toggle' + (open ? ' open' : '') + (total === 0 ? ' hollow' : '')}
            onClick={() => toggle(node.id)}
            aria-label={node.name}
          >
            <Chevron />
          </button>
          <span className="tree-name">
            {node.name}
            {isRoot && (
              <span className="tag info">{t('rms.deviceRecipes.factoryTag')}</span>
            )}
          </span>
          <span className="tree-code dim">{node.code}</span>
        </div>
        {open && (
          <div className="ptree-kids">
            {kids.map((k) => renderBranch(k))}
            {devs.map((d) => (
              <div
                key={d.id}
                className={'tree-row is-leaf' + (d.id === deviceId ? ' on' : '')}
                onClick={() => revealDevice(d)}
              >
                <span className="tree-toggle hollow">
                  <Chevron />
                </span>
                <span className="tree-name">
                  {d.code} · {d.name}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  const unassigned = devicesByArea['__none__'] || [];
  const currentDevice = devices.find((d) => d.id === deviceId) ?? null;
  const currentPath = currentDevice ? pathOf[currentDevice.id] ?? [] : [];

  return (
    <div className="page">
      <div className="page-head">
        <h1>{t('rms.deviceRecipes.title')}</h1>
        <span className="muted">{t('rms.deviceRecipes.sub')}</span>
      </div>

      {err && (
        <div className="alert err">
          <span>{err}</span>
        </div>
      )}

      <div className="split">
        <div className="panel">
          <div className="panel-head">
            <h3>{t('rms.deviceRecipes.areas')}</h3>
            {loading && <span className="muted">{t('rms.common.loading')}</span>}
          </div>
          <div className="panel-body">
            <div className="ptree">
              {areas.map((a) => renderBranch(a, true))}
              {unassigned.length > 0 && (
                <div className="ptree-node">
                  <div className="tree-row">
                    <span className="tree-toggle hollow">
                      <Chevron />
                    </span>
                    <span className="tree-name">{t('rms.deviceRecipes.unassigned')}</span>
                  </div>
                  <div className="ptree-kids">
                    {unassigned.map((d) => (
                      <div
                        key={d.id}
                        className={'tree-row is-leaf' + (d.id === deviceId ? ' on' : '')}
                        onClick={() => revealDevice(d)}
                      >
                        <span className="tree-toggle hollow">
                          <Chevron />
                        </span>
                        <span className="tree-name">
                          {d.code} · {d.name}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {areas.length === 0 && unassigned.length === 0 && (
                <div className="empty">{t('rms.deviceRecipes.noAreas')}</div>
              )}
            </div>
          </div>
        </div>

        <div className="panel">
          {currentDevice && (
            <div className="dcrumb" title={t('rms.deviceRecipes.pathTitle')}>
              {currentPath.map((a, i) => (
                <Fragment key={a.id}>
                  {i > 0 && <span className="dcrumb-sep">/</span>}
                  <button className="dcrumb-link" onClick={() => expandToArea(a.id)}>
                    {a.name}
                    {i === 0 && <span className="tag info">{t('rms.deviceRecipes.factoryTag')}</span>}
                  </button>
                </Fragment>
              ))}
              <span className="dcrumb-sep">/</span>
              <span className="dcrumb-here">
                {currentDevice.code} · {currentDevice.name}
              </span>
            </div>
          )}
          <div className="panel-head">
            <h3>
              {view ? `${view.deviceCode} · ${view.deviceName}` : t('rms.deviceRecipes.pickDevice')}
            </h3>
            {view?.deviceTypeName && <span className="muted">{view.deviceTypeName}</span>}
          </div>
          <div className="panel-body">
            {view && (
              <div className="kpi-grid">
                <div className="kpi">
                  <div className="kpi-label">{t('rms.deviceRecipes.total')}</div>
                  <div className="kpi-val">{view.total}</div>
                </div>
                <div className="kpi">
                  <div className="kpi-label">{t('rms.deviceRecipes.orphan')}</div>
                  <div className="kpi-val">{view.orphan}</div>
                </div>
                <div className="kpi">
                  <div className="kpi-label">{t('rms.deviceRecipes.drift')}</div>
                  <div className="kpi-val">{view.drift}</div>
                </div>
                <div className="kpi">
                  <div className="kpi-label">{t('rms.deviceRecipes.unqualified')}</div>
                  <div className="kpi-val">{view.unqualified}</div>
                </div>
              </div>
            )}

            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('rms.deviceRecipes.recipe')}</th>
                    <th>{t('rms.deviceRecipes.ppid')}</th>
                    <th>{t('rms.deviceRecipes.chamber')}</th>
                    <th>{t('rms.deviceRecipes.version')}</th>
                    <th>{t('rms.deviceRecipes.qualification')}</th>
                    <th>{t('rms.deviceRecipes.onTool')}</th>
                    <th>{t('rms.deviceRecipes.sources')}</th>
                  </tr>
                </thead>
                <tbody>
                  {(view?.rows ?? []).map((r, i) => (
                    <tr
                      key={r.recipeId ?? `orphan-${r.ppidOnTool}-${i}`}
                      className={r.recipeId ? 'clickable' : ''}
                      onClick={() => openDetail(r)}
                    >
                      <td className="mono">
                        {r.code ?? (
                          <span className="muted">{t('rms.deviceRecipes.unmanaged')}</span>
                        )}
                        {r.name ? <span className="muted"> · {r.name}</span> : null}
                      </td>
                      <td className="mono">{r.ppidOnTool ?? t('rms.common.dash')}</td>
                      <td className="mono">
                        {r.chamberCode ?? t('rms.common.dash')}
                        {r.chamberName ? <span className="muted"> · {r.chamberName}</span> : null}
                      </td>
                      <td className="mono">
                        {r.activeVersionNo ? `v${r.activeVersionNo}` : t('rms.common.dash')}
                        {r.versionStatus ? (
                          <span className={'tag ' + (r.versionStatus === 'ACTIVE' ? 'ok' : 'info')}>
                            {r.versionStatus}
                          </span>
                        ) : null}
                      </td>
                      <td>
                        <span className={'tag ' + (r.qualificationStatus === 'GRANTED' ? 'ok' : 'warn')}>
                          {r.qualificationStatus ?? 'NOT_QUALIFIED'}
                        </span>
                      </td>
                      <td>
                        {r.onToolState ? (
                          <span
                            className={
                              'tag ' +
                              (r.onToolState === 'OK'
                                ? 'ok'
                                : r.onToolState === 'DRIFT'
                                  ? 'err'
                                  : 'warn')
                            }
                          >
                            {r.onToolState}
                          </span>
                        ) : (
                          <span className="muted">{t('rms.common.dash')}</span>
                        )}
                      </td>
                      <td className="muted">{r.sources.join(' · ')}</td>
                    </tr>
                  ))}
                  {(view?.rows ?? []).length === 0 && (
                    <tr>
                      <td colSpan={7} className="empty-cell">
                        {t('rms.deviceRecipes.empty')}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      {detail && (
        <RecipeDetailDrawer
          recipeId={detail.row.recipeId as string}
          recipeCode={detail.row.code}
          recipeName={detail.row.name}
          versions={detail.versions}
          initialVersionId={detail.row.activeVersionId}
          onClose={() => setDetail(null)}
          onChanged={() => deviceId && api.deviceRecipes(deviceId).then(setView).catch(() => undefined)}
        />
      )}
    </div>
  );
}
