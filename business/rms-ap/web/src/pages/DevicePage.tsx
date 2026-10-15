import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import DeviceDetailDrawer from '@/components/DeviceDetailDrawer';
import RecipeDetailDrawer from '@/components/RecipeDetailDrawer';
import * as api from '@/lib/rmsApi';
import type {
  Device,
  DeviceType,
  DeviceArea,
  DeviceStatus,
  DeviceRecipeRow,
  RecipeVersion,
} from '@/types';

const STATUS_TONE: Record<DeviceStatus, string> = {
  ENABLED: 'ok',
  DISABLED: 'warn',
};

interface AreaGroup {
  root: DeviceArea;
  items: { area: DeviceArea; depth: number }[];
}

/**
 * 把扁平区域压成「工厂为组头、其下区域缩进」的分组，供设备归属选择。
 * 工厂（parentId 为空或指向不存在节点）= 顶层组头，但不可作为设备父节点；
 * 设备只能选工厂下的区域，从而把「工厂父节点」在录入时显式呈现。
 */
function buildAreaGroups(areas: DeviceArea[]): AreaGroup[] {
  const map = new Map<string, DeviceArea>();
  areas.forEach((a) => map.set(a.id, a));
  const childrenOf = new Map<string | null, DeviceArea[]>();
  for (const a of areas) {
    const pid = a.parentId && a.parentId.trim() ? a.parentId : null;
    const resolved = pid && map.has(pid) ? pid : null; // 悬空父 → 视为顶层（与后端 areaTree 一致）
    if (!childrenOf.has(resolved)) childrenOf.set(resolved, []);
    childrenOf.get(resolved)!.push(a);
  }
  const roots = childrenOf.get(null) ?? [];
  return roots.map((root) => {
    const items: { area: DeviceArea; depth: number }[] = [];
    const walk = (pid: string, depth: number) => {
      for (const c of childrenOf.get(pid) ?? []) {
        items.push({ area: c, depth });
        walk(c.id, depth + 1);
      }
    };
    walk(root.id, 1);
    return { root, items };
  });
}

export default function DevicePage() {
  const { t } = useTranslation();
  const [list, setList] = useState<Device[]>([]);
  const [types, setTypes] = useState<DeviceType[]>([]);
  const [areas, setAreas] = useState<DeviceArea[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');

  const [editing, setEditing] = useState<Device | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  // 机台详情抽屉（枢纽）+ 配方详情抽屉（互斥渲染：开配方时隐藏机台抽屉，关闭后回到机台）
  const [detail, setDetail] = useState<Device | null>(null);
  const [recipeDetail, setRecipeDetail] = useState<{ row: DeviceRecipeRow; versions: RecipeVersion[] } | null>(null);

  async function openRecipe(row: DeviceRecipeRow) {
    if (!row.recipeId) return;
    try {
      const versions = await api.listVersions(row.recipeId);
      setRecipeDetail({ row, versions });
    } catch {
      // 版本拉取失败时仍打开抽屉（抽屉内会展示错误）
      setRecipeDetail({ row, versions: [] });
    }
  }

  async function refresh() {
    setLoading(true);
    try {
      const data = await api.listDevices({ keyword: keyword || undefined });
      setList(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let alive = true;
    Promise.all([api.listDeviceTypes(), api.listDeviceAreas()]).then(([ty, ar]) => {
      if (!alive) return;
      setTypes(ty);
      setAreas(ar);
    });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const id = setTimeout(refresh, 200);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword]);

  async function doDelete(x: Device) {
    if (!confirm(t('rms.common.confirmDelete'))) return;
    await api.deleteDevice(x.id);
    await refresh();
  }

  const typeName = useMemo(() => Object.fromEntries(types.map((x) => [x.id, x.name])), [types]);
  const areaName = useMemo(() => Object.fromEntries(areas.map((x) => [x.id, x.name])), [areas]);
  const areaById = useMemo(() => Object.fromEntries(areas.map((x) => [x.id, x])), [areas]);

  /** 设备所属区域的全路径（工厂 / 区域 …），强化列表里的工厂归属上下文。 */
  function deviceAreaPath(id: string | null | undefined): string {
    if (!id) return '';
    const parts: string[] = [];
    const seen = new Set<string>();
    let cur: DeviceArea | undefined = areaById[id];
    while (cur && !seen.has(cur.id)) {
      seen.add(cur.id);
      parts.unshift(cur.name);
      cur = cur.parentId ? areaById[cur.parentId] : undefined;
    }
    return parts.join(' / ');
  }

  const filtered = useMemo(() => {
    if (!keyword) return list;
    const k = keyword.toLowerCase();
    return list.filter(
      (x) => x.code.toLowerCase().includes(k) || x.name.toLowerCase().includes(k) ||
        (x.ip ?? '').toLowerCase().includes(k),
    );
  }, [list, keyword]);

  return (
    <div className="page">
      <div className="page-head">
        <h1>{t('rms.device.title')}</h1>
        <Perms code="rms:device:create">
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
            + {t('rms.device.createNew')}
          </button>
        </Perms>
      </div>

      <div className="toolbar">
        <input
          className="search-input"
          placeholder={t('rms.common.search')}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        {loading && <span className="muted">{t('rms.common.loading')}</span>}
      </div>

      <div className="panel">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.device.code')}</th>
              <th>{t('rms.device.name')}</th>
              <th>{t('rms.device.type')}</th>
              <th>{t('rms.device.area')}</th>
              <th>{t('rms.device.ip')}</th>
              <th>{t('rms.common.status')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((x) => (
              <tr
                key={x.id}
                className="clickable"
                onClick={() => setDetail(x)}
              >
                <td className="mono">{x.code}</td>
                <td>{x.name}</td>
                <td>{x.deviceTypeName ?? (x.deviceTypeId ? typeName[x.deviceTypeId] ?? '?' : t('rms.common.dash'))}</td>
                <td>{x.areaId ? (deviceAreaPath(x.areaId) || x.areaName || '?') : t('rms.common.dash')}</td>
                <td className="mono">{x.ip ?? t('rms.common.dash')}</td>
                <td>
                  <span className={'tag ' + (STATUS_TONE[x.status] ?? 'info')}>
                    {t('rms.device.status.' + x.status)}
                  </span>
                </td>
                <td className="cell-actions">
                  <Perms code="rms:device:update">
                    <button className="btn btn-ghost btn-sm" onClick={(e) => { e.stopPropagation(); setEditing(x); }}>
                      {t('rms.common.edit')}
                    </button>
                  </Perms>
                  <Perms code="rms:device:delete">
                    <button
                      className="btn btn-ghost btn-sm danger"
                      onClick={(e) => { e.stopPropagation(); doDelete(x); }}
                    >
                      {t('rms.common.delete')}
                    </button>
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && filtered.length === 0 && (
              <tr>
                <td colSpan={7} className="empty-cell">
                  {t('rms.common.noData')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* 机台枢纽（W7 体验整合）：点开一台机 = 一处维护腔体 + 装载配方 + 绑定/解绑。
          配方详情打开时与机台详情互斥渲染（关闭配方详情回到机台详情）。 */}
      {detail && !recipeDetail && (
        <DeviceDetailDrawer
          device={detail}
          typeName={detail.deviceTypeId ? typeName[detail.deviceTypeId] : undefined}
          areaName={detail.areaId ? areaName[detail.areaId] : undefined}
          onClose={() => setDetail(null)}
          onChanged={refresh}
          onOpenRecipe={(row) => openRecipe(row)}
          onEdit={() => setEditing(detail)}
        />
      )}

      {recipeDetail && (
        <RecipeDetailDrawer
          recipeId={recipeDetail.row.recipeId!}
          recipeCode={recipeDetail.row.code}
          recipeName={recipeDetail.row.name}
          versions={recipeDetail.versions}
          initialVersionId={recipeDetail.row.activeVersionId}
          onClose={() => setRecipeDetail(null)}
          onChanged={refresh}
        />
      )}

      {(showCreate || editing) && (
        <DeviceModal
          types={types}
          areas={areas}
          initial={editing}
          onClose={() => {
            setShowCreate(false);
            setEditing(null);
          }}
          onDone={async () => {
            setShowCreate(false);
            setEditing(null);
            await refresh();
          }}
        />
      )}
    </div>
  );
}

function DeviceModal({
  types,
  areas,
  initial,
  onClose,
  onDone,
}: {
  types: DeviceType[];
  areas: DeviceArea[];
  initial: Device | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [code, setCode] = useState(initial?.code ?? '');
  const [name, setName] = useState(initial?.name ?? '');
  const [deviceTypeId, setDeviceTypeId] = useState(initial?.deviceTypeId ?? '');
  const [areaId, setAreaId] = useState(initial?.areaId ?? '');
  const [ip, setIp] = useState(initial?.ip ?? '');
  const [status, setStatus] = useState<DeviceStatus>(initial?.status ?? 'ENABLED');
  const [remark, setRemark] = useState(initial?.remark ?? '');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  // 区域按「工厂 / 区域」层级分组显示，工厂作为不可选组头，设备只能选工厂下的区域
  const areaGroups = useMemo(() => buildAreaGroups(areas), [areas]);

  async function submit() {
    if (!code || !name || !deviceTypeId) {
      setErr(t('rms.common.code') + ' / ' + t('rms.common.name') + ' / ' + t('rms.device.type') + ' *');
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const payload = {
        code,
        name,
        deviceTypeId,
        areaId: areaId || null,
        ip: ip || null,
        status,
        remark: remark || null,
      };
      if (initial) {
        await api.updateDevice(initial.id, payload);
      } else {
        await api.createDevice(payload);
      }
      onDone();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{initial ? t('rms.common.edit') : t('rms.device.createNew')}</h3>
          <button className="icon-btn" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.device.code')} *</label>
              <input className="text-input" value={code} disabled={!!initial} onChange={(e) => setCode(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.device.name')} *</label>
              <input className="text-input" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.device.type')} *</label>
              <select className="text-input" value={deviceTypeId} onChange={(e) => setDeviceTypeId(e.target.value)}>
                <option value="">—</option>
                {types.map((x) => (
                  <option key={x.id} value={x.id}>
                    {x.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.device.area')}</label>
              <select className="text-input" value={areaId} onChange={(e) => setAreaId(e.target.value)}>
                <option value="">{t('rms.device.areaEmpty')}</option>
                {areaGroups.map((g) => (
                  <optgroup key={g.root.id} label={`${t('rms.deviceRecipes.factoryTag')} · ${g.root.name}`}>
                    {g.items.map(({ area, depth }) => (
                      <option key={area.id} value={area.id}>
                        {'　'.repeat(depth - 1)}
                        {area.name}（{area.code}）
                      </option>
                    ))}
                  </optgroup>
                ))}
              </select>
              <p className="field-hint">{t('rms.device.areaHint')}</p>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.device.ip')}</label>
              <input className="text-input" value={ip} placeholder="192.168.x.x" onChange={(e) => setIp(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.common.status')}</label>
              <select className="text-input" value={status} onChange={(e) => setStatus(e.target.value as DeviceStatus)}>
                <option value="ENABLED">{t('rms.device.status.ENABLED')}</option>
                <option value="DISABLED">{t('rms.device.status.DISABLED')}</option>
              </select>
            </div>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.common.remark')}</label>
            <input className="text-input" value={remark} onChange={(e) => setRemark(e.target.value)} />
          </div>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>
            {t('rms.common.cancel')}
          </button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>
            {t('rms.common.save')}
          </button>
        </div>
      </div>
    </div>
  );
}
