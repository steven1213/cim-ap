// 位置拓扑树（只读单选版）——设备页左侧过滤树用。
//
// 与 TopologyPage 的树同款视觉（复用 topo-* 样式类），但职责收敛为「选一个位置节点」：
// 无编辑、无分组分节、无右侧详情。选中 key 格式 `${level}:${id}`，调用方用
// collectSubtreeIds 把 key 展开成子树内全部位置 id 做设备过滤。
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  IconApps,
  IconChevron,
  IconInbox,
  IconList,
  IconMonitor,
  IconOrgGrant,
} from '@cim/ui';
import type { Area, Bay, Fab, Site, Stage } from '@/types';

export type LocLevel = 'site' | 'fab' | 'area' | 'bay' | 'stage';

/** 树节点（TopologyPage 的 TopoNode 的只读子集，字段名保持一致便于将来合流）。 */
export interface LocNode {
  level: LocLevel;
  id: string;
  code: string;
  name: string;
  parentCode: string | null;
  fabCode: string | null;
  enabled: boolean;
  children: LocNode[];
}

/** 五层全量数据 → 树（与 TopologyPage 的组树同构，但不带区域组信息）。 */
export function buildLocTree(
  sites: Site[],
  fabs: Fab[],
  areas: Area[],
  bays: Bay[],
  stages: Stage[],
): LocNode[] {
  const fabsBySite = new Map<string, Fab[]>();
  for (const f of fabs) {
    const list = fabsBySite.get(f.siteCode) ?? [];
    list.push(f);
    fabsBySite.set(f.siteCode, list);
  }
  const areasByFab = new Map<string, Area[]>();
  for (const a of areas) {
    const list = areasByFab.get(a.fabCode) ?? [];
    list.push(a);
    areasByFab.set(a.fabCode, list);
  }
  const baysByArea = new Map<string, Bay[]>();
  for (const b of bays) {
    const key = `${b.fabCode}/${b.areaCode}`;
    const list = baysByArea.get(key) ?? [];
    list.push(b);
    baysByArea.set(key, list);
  }
  const stagesByBay = new Map<string, Stage[]>();
  for (const s of stages) {
    const key = `${s.fabCode}/${s.bayCode}`;
    const list = stagesByBay.get(key) ?? [];
    list.push(s);
    stagesByBay.set(key, list);
  }
  return sites.map((s) => ({
    level: 'site' as const,
    id: s.id,
    code: s.siteCode,
    name: s.siteName,
    parentCode: null,
    fabCode: null,
    enabled: s.enabled,
    children: (fabsBySite.get(s.siteCode) ?? []).map((f) => ({
      level: 'fab' as const,
      id: f.id,
      code: f.fabCode,
      name: f.fabName,
      parentCode: f.siteCode,
      fabCode: f.fabCode,
      enabled: f.enabled,
      children: (areasByFab.get(f.fabCode) ?? []).map((a) => ({
        level: 'area' as const,
        id: a.id,
        code: a.areaCode,
        name: a.areaName,
        parentCode: a.fabCode,
        fabCode: a.fabCode,
        enabled: a.enabled,
        children: (baysByArea.get(`${a.fabCode}/${a.areaCode}`) ?? []).map((b) => ({
          level: 'bay' as const,
          id: b.id,
          code: b.bayCode,
          name: b.bayName,
          parentCode: b.areaCode,
          fabCode: b.fabCode,
          enabled: b.enabled,
          children: (stagesByBay.get(`${b.fabCode}/${b.bayCode}`) ?? []).map((st) => ({
            level: 'stage' as const,
            id: st.id,
            code: st.stageCode,
            name: st.stageName,
            parentCode: st.bayCode,
            fabCode: st.fabCode,
            enabled: st.enabled,
            children: [],
          })),
        })),
      })),
    })),
  }));
}

/** keyword 过滤（命中即保留祖先链）。空关键词原样返回。 */
export function filterLocTree(nodes: LocNode[], keyword: string): LocNode[] {
  const kw = keyword.trim().toLowerCase();
  if (!kw) return nodes;
  const walk = (list: LocNode[]): LocNode[] =>
    list
      .map((n) => {
        const children = walk(n.children);
        const hit =
          n.code.toLowerCase().includes(kw) || n.name.toLowerCase().includes(kw) || children.length > 0;
        return hit ? { ...n, children } : null;
      })
      .filter((n): n is LocNode => n !== null);
  return walk(nodes);
}

/** 选中节点 key → 子树内全部位置 id（含自身）。设备行的 locationId 落在其中即「在该位置下」。 */
export function collectSubtreeIds(nodes: LocNode[], key: string | null): Set<string> {
  const ids = new Set<string>();
  if (!key) return ids;
  const walk = (n: LocNode, hit: boolean): boolean => {
    const self = `${n.level}:${n.id}` === key;
    const inside = hit || self;
    if (inside) ids.add(n.id);
    for (const c of n.children) walk(c, inside);
    return inside;
  };
  for (const n of nodes) walk(n, false);
  return ids;
}

const LEVEL_ICON: Record<LocLevel, typeof IconApps> = {
  site: IconOrgGrant,
  fab: IconApps,
  area: IconList,
  bay: IconInbox,
  stage: IconMonitor,
};

/** 只读位置树：受控选中、内部折叠状态；点击节点回调 `${level}:${id}`。 */
export default function LocationTree({
  nodes,
  selectedKey,
  onSelect,
  emptyKey,
}: {
  nodes: LocNode[];
  selectedKey: string | null;
  onSelect: (key: string | null) => void;
  emptyKey: string;
}) {
  const { t } = useTranslation();
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());

  const branches = useMemo(
    () =>
      nodes.map((n) => (
        <LocBranch
          key={`${n.level}:${n.id}`}
          node={n}
          depth={0}
          collapsed={collapsed}
          onToggle={(key) =>
            setCollapsed((prev) => {
              const next = new Set(prev);
              if (next.has(key)) next.delete(key);
              else next.add(key);
              return next;
            })
          }
          onSelect={onSelect}
          selectedKey={selectedKey}
          t={t}
        />
      )),
    [nodes, collapsed, selectedKey, onSelect, t],
  );

  if (nodes.length === 0) {
    return <p className="dim topo-empty">{t(emptyKey)}</p>;
  }
  return <div className="topo-branch-root">{branches}</div>;
}

function LocBranch({
  node,
  depth,
  collapsed,
  onToggle,
  onSelect,
  selectedKey,
  t,
}: {
  node: LocNode;
  depth: number;
  collapsed: Set<string>;
  onToggle: (key: string) => void;
  onSelect: (key: string | null) => void;
  selectedKey: string | null;
  t: (key: string) => string;
}) {
  const key = `${node.level}:${node.id}`;
  const isCollapsed = collapsed.has(key);
  const hasChildren = node.children.length > 0;
  const { Icon } = { Icon: LEVEL_ICON[node.level] };
  const selected = selectedKey === key;

  return (
    <div className="topo-branch">
      <div
        className={`topo-row${selected ? ' selected' : ''}${node.enabled ? '' : ' off'}`}
        style={{ paddingLeft: 8 + depth * 18 }}
        onClick={() => onSelect(selected ? null : key)}
      >
        <span
          className={`topo-caret${isCollapsed ? '' : ' open'}`}
          onClick={(e) => {
            e.stopPropagation();
            if (hasChildren) onToggle(key);
          }}
        >
          {hasChildren ? <IconChevron width={12} height={12} /> : <span className="topo-dot" />}
        </span>
        <Icon width={14} height={14} />
        <span className="topo-code">{node.code}</span>
        <span className="topo-name">{node.name}</span>
        {!node.enabled && <span className="topo-badge dim-badge">{t('mds.topo.disabled')}</span>}
      </div>
      {hasChildren && !isCollapsed && (
        <div className="topo-children">
          {node.children.map((c) => (
            <LocBranch
              key={`${c.level}:${c.id}`}
              node={c}
              depth={depth + 1}
              collapsed={collapsed}
              onToggle={onToggle}
              onSelect={onSelect}
              selectedKey={selectedKey}
              t={t}
            />
          ))}
        </div>
      )}
    </div>
  );
}
