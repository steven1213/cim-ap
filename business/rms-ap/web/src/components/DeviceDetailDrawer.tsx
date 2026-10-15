import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import ChamberSection from '@/components/ChamberPanel';
import * as api from '@/lib/rmsApi';
import type {
  Device,
  DeviceChamber,
  DeviceRecipeRow,
  DeviceRecipeView,
  Recipe,
} from '@/types';

interface Props {
  device: Device;
  /** 机型/区域名（外层已加载，直接透传避免重复请求）。 */
  typeName?: string;
  areaName?: string;
  onClose: () => void;
  /** 腔体/绑定等变更后回调（外层刷新列表）。 */
  onChanged?: () => void;
  /** 点开装载的配方行（外层切到配方详情抽屉）。 */
  onOpenRecipe?: (row: DeviceRecipeRow) => void;
  /** 触发外层编辑弹窗。 */
  onEdit?: () => void;
}

/**
 * 机台详情抽屉（W7 体验整合的「机台枢纽」）。
 *
 * <p>用户动线：在设备台账点开一台机 → <b>一处</b>看到并维护它的全部配方相关事项：
 * 基本信息 / 腔体 / 装载的配方（含资格与漂移）/ 绑定新配方（PPID + 腔体）。
 * 不再需要跨「设备台账 → 集成监控 → 设备配方」三个页面。</p>
 */
export default function DeviceDetailDrawer({
  device,
  typeName,
  areaName,
  onClose,
  onChanged,
  onOpenRecipe,
  onEdit,
}: Props) {
  const { t } = useTranslation();
  const [view, setView] = useState<DeviceRecipeView | null>(null);
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [recipeId, setRecipeId] = useState('');
  const [chambers, setChambers] = useState<DeviceChamber[]>([]);
  const [chamberId, setChamberId] = useState('');
  const [ppid, setPpid] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function loadView() {
    try {
      setView(await api.deviceRecipes(device.id));
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  useEffect(() => {
    setErr('');
    loadView();
    api.listRecipes().then(setRecipes).catch(() => undefined);
    api.listChambers(device.id).then(setChambers).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [device.id]);

  useEffect(() => {
    setChamberId('');
  }, [ppid]);

  async function bind() {
    if (!ppid.trim() || !recipeId) {
      setErr(t('rms.deviceDetail.bindRequired'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      await api.registerPpidMap({
        deviceId: device.id,
        deviceTypeId: null,
        ppidOnTool: ppid.trim(),
        recipeId,
        chamberId: chamberId || null,
      });
      setPpid('');
      setRecipeId('');
      await loadView();
      api.listChambers(device.id).then(setChambers).catch(() => undefined);
      onChanged?.();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  async function unbind(row: DeviceRecipeRow) {
    if (!window.confirm(t('rms.tools.unbindConfirm'))) return;
    setBusy(true);
    setErr('');
    try {
      await api.deletePpidMap(row.mapId!);
      await loadView();
      onChanged?.();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <div className="drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-head">
          <h3>
            {device.code} · {device.name}
          </h3>
          <div className="cell-actions">
            {onEdit && (
              <Perms code="rms:device:update">
                <button className="btn btn-ghost btn-sm" onClick={onEdit}>
                  {t('rms.common.edit')}
                </button>
              </Perms>
            )}
            <button className="icon-btn" onClick={onClose}>
              ✕
            </button>
          </div>
        </div>
        <div className="drawer-body">
          {err && (
            <div className="alert err">
              <span>{err}</span>
            </div>
          )}

          {/* 基本信息 */}
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.device.type')}</label>
              <div>{typeName ?? '—'}</div>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.device.area')}</label>
              <div>{areaName ?? '—'}</div>
            </div>
            <div className="field">
              <label className="field-label">IP</label>
              <div className="mono">{device.ip ?? '—'}</div>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.device.status')}</label>
              <div>
                <span className={'tag ' + (device.status === 'ENABLED' ? 'ok' : 'warn')}>
                  {device.status}
                </span>
              </div>
            </div>
          </div>

          {/* 腔体（受控面板：在哪台机上点开就维护哪台机） */}
          <ChamberSection deviceId={device.id} onChanged={() => {
            api.listChambers(device.id).then(setChambers).catch(() => undefined);
            onChanged?.();
          }} />

          {/* 绑定配方：PPID + 配方 + 腔体（可选）——装载操作收进机台详情，一处搞定 */}
          <div className="panel">
            <div className="panel-head">
              <h3>{t('rms.deviceDetail.bind')}</h3>
              <span className="muted">{t('rms.deviceDetail.bindHint')}</span>
            </div>
            <div className="form-grid">
              <div className="field">
                <label className="field-label">{t('rms.tools.ppid')}</label>
                <input
                  className="text-input"
                  value={ppid}
                  placeholder="PPID-001"
                  onChange={(e) => setPpid(e.target.value)}
                />
              </div>
              <div className="field">
                <label className="field-label">{t('rms.deviceRecipes.recipe')}</label>
                <select
                  className="text-input"
                  value={recipeId}
                  onChange={(e) => setRecipeId(e.target.value)}
                >
                  <option value="">{t('rms.tools.pickRecipe')}</option>
                  {recipes.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.code} · {r.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label className="field-label">{t('rms.tools.chamber')}</label>
                <select
                  className="text-input"
                  value={chamberId}
                  onChange={(e) => setChamberId(e.target.value)}
                >
                  <option value="">{t('rms.tools.noChamber')}</option>
                  {chambers.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.code} · {c.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label className="field-label">&nbsp;</label>
                <Perms code="rms:device:update">
                  <button className="btn btn-primary" disabled={busy} onClick={bind}>
                    {t('rms.tools.bind')}
                  </button>
                </Perms>
              </div>
            </div>
          </div>

          {/* 装载的配方（四来源聚合，行可点开配方详情） */}
          <div className="panel">
            <div className="panel-head">
              <h3>{t('rms.deviceDetail.loaded')}</h3>
              {view && (
                <span className="muted">
                  {t('rms.deviceRecipes.total')} {view.total} · {t('rms.deviceRecipes.orphan')}{' '}
                  {view.orphan} · {t('rms.deviceRecipes.drift')} {view.drift}
                </span>
              )}
            </div>
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>{t('rms.deviceRecipes.recipe')}</th>
                    <th>{t('rms.deviceRecipes.ppid')}</th>
                    <th>{t('rms.deviceRecipes.chamber')}</th>
                    <th>{t('rms.deviceRecipes.version')}</th>
                    <th>{t('rms.deviceRecipes.qualification')}</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {(view?.rows ?? []).map((r, i) => (
                    <tr
                      key={r.recipeId ?? `orphan-${r.ppidOnTool}-${i}`}
                      className={r.recipeId ? 'clickable' : ''}
                      onClick={() => r.recipeId && onOpenRecipe?.(r)}
                    >
                      <td className="mono">
                        {r.code ?? <span className="muted">{t('rms.deviceRecipes.unmanaged')}</span>}
                        {r.name ? <span className="muted"> · {r.name}</span> : null}
                        {r.recipeId ? null : (
                          <span className="tag err">{t('rms.deviceRecipes.orphan')}</span>
                        )}
                      </td>
                      <td className="mono">{r.ppidOnTool ?? '—'}</td>
                      <td className="mono">{r.chamberCode ?? '—'}</td>
                      <td className="mono">
                        {r.activeVersionNo ? `v${r.activeVersionNo}` : '—'}
                        {r.versionStatus ? (
                          <span
                            className={'tag ' + (r.versionStatus === 'ACTIVE' ? 'ok' : 'info')}
                          >
                            {r.versionStatus}
                          </span>
                        ) : null}
                      </td>
                      <td>
                        <span
                          className={'tag ' + (r.qualificationStatus === 'GRANTED' ? 'ok' : 'warn')}
                        >
                          {r.qualificationStatus ?? 'NOT_QUALIFIED'}
                        </span>
                      </td>
                      <td className="cell-actions">
                        {r.mapId && (
                          <Perms code="rms:device:update">
                            <button
                              className="btn btn-ghost btn-sm"
                              disabled={busy}
                              onClick={(e) => {
                                e.stopPropagation();
                                unbind(r);
                              }}
                            >
                              {t('rms.tools.unbind')}
                            </button>
                          </Perms>
                        )}
                      </td>
                    </tr>
                  ))}
                  {(view?.rows ?? []).length === 0 && (
                    <tr>
                      <td colSpan={6} className="empty-cell">
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
    </div>
  );
}
