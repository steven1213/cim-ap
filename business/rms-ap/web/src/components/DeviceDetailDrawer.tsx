import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimDescriptions,
  CimDrawer,
  CimTag,
  DataTable,
  FormGrid,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type DescItem,
  type FormField,
  CimButton,
} from '@cim/ui';
import type { StatusTone } from '@cim/theme';
import ChamberSection from '@/components/ChamberPanel';
import * as api from '@/lib/rmsApi';
import type { Device, DeviceChamber, DeviceRecipeRow, Recipe } from '@/types';

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

const NO_RECIPES: Recipe[] = [];
const NO_CHAMBERS: DeviceChamber[] = [];

interface BindDraft {
  ppid: string;
  recipeId: string;
  chamberId: string;
}

const EMPTY_BIND: BindDraft = { ppid: '', recipeId: '', chamberId: '' };

/**
 * 机台详情抽屉（W7 体验整合的「机台枢纽」）。
 *
 * <p>用户动线：在设备台账点开一台机 → <b>一处</b>看到并维护它的全部配方相关事项：
 * 基本信息 / 腔体 / 装载的配方（含资格与漂移）/ 绑定新配方（PPID + 腔体）。
 * 不再需要跨「设备台账 → 集成监控 → 设备配方」三个页面。</p>
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>手写 `.drawer-backdrop` → `CimDrawer`（焦点/Esc/锁滚动由 antd 承担）。</li>
 *   <li>`view` / `recipes` / `chambers` 三份状态各一个 `useResource`，
 *       `deps:[device.id]`：抽屉换机台时旧请求被取消，不会把上一台的配方画到这一台。</li>
 *   <li>`window.confirm` → `useConfirm`；页内常驻红条 → toast。</li>
 *   <li>两个原生 `<select>` → `CimSelect`（可搜索：配方上百条时下拉可筛）。</li>
 *   <li>手写 `<table class="data">` → `DataTable flat`；行点击进配方详情用 `onRowClick`，
 *       孤儿行（`recipeId` 为空）不可点。</li>
 * </ul>
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
  const toast = useToast();
  const confirm = useConfirm();

  const [draft, setDraft] = useState<BindDraft>(EMPTY_BIND);
  const [busy, setBusy] = useState(false);

  const viewRes = useResource(() => api.deviceRecipes(device.id), { deps: [device.id] });
  const recipesRes = useResource(() => api.listRecipes(), { deps: [device.id] });
  const chambersRes = useResource(() => api.listChambers(device.id), { deps: [device.id] });

  const view = viewRes.data;
  const rows = view?.rows ?? [];
  const recipes = recipesRes.data ?? NO_RECIPES;
  const chambers = chambersRes.data ?? NO_CHAMBERS;

  const recipeOptions = recipes.map((r) => ({ value: r.id, label: `${r.code} · ${r.name}` }));
  const chamberOptions = chambers.map((c) => ({ value: c.id, label: `${c.code} · ${c.name}` }));

  const fields: Array<FormField<BindDraft>> = [
    { key: 'ppid', kind: 'text', labelKey: 'rms.tools.ppid', placeholder: 'PPID-001', mono: true, required: true },
    { key: 'recipeId', kind: 'select', labelKey: 'rms.deviceRecipes.recipe', options: recipeOptions, placeholderKey: 'rms.tools.pickRecipe', required: true },
    { key: 'chamberId', kind: 'select', labelKey: 'rms.tools.chamber', options: chamberOptions, placeholderKey: 'rms.tools.noChamber' },
  ];

  const headerItems: Array<DescItem<Device>> = [
    { key: 'type', labelKey: 'rms.device.type', value: typeName ?? '—' },
    { key: 'area', labelKey: 'rms.device.area', value: areaName ?? '—' },
    { key: 'ip', labelKey: 'rms.device.ip', value: <span className="mono">{device.ip ?? '—'}</span> },
    {
      key: 'status',
      labelKey: 'rms.device.status',
      value: (
        <CimTag tone={device.status === 'ENABLED' ? 'ok' : 'warn'} size="small">
          {device.status}
        </CimTag>
      ),
    },
  ];

  const columns: Array<DataTableColumn<DeviceRecipeRow>> = [
    {
      key: 'code',
      titleKey: 'rms.deviceRecipes.recipe',
      render: (r) => (
        <>
          <span className="mono">{r.code ?? t('rms.deviceRecipes.unmanaged')}</span>
          {r.name ? <span className="muted"> · {r.name}</span> : null}
          {r.recipeId ? null : (
            <span style={{ marginLeft: 6 }}>
              <CimTag tone="err" size="small">
                {t('rms.deviceRecipes.orphan')}
              </CimTag>
            </span>
          )}
        </>
      ),
    },
    { key: 'ppidOnTool', titleKey: 'rms.deviceRecipes.ppid', mono: true, width: 130 },
    { key: 'chamberCode', titleKey: 'rms.deviceRecipes.chamber', mono: true, width: 120 },
    {
      key: 'activeVersionNo',
      titleKey: 'rms.deviceRecipes.version',
      width: 140,
      render: (r) => (
        <>
          <span className="mono">{r.activeVersionNo ? `v${r.activeVersionNo}` : '—'}</span>
          {r.versionStatus ? (
            <span style={{ marginLeft: 6 }}>
              <CimTag tone={r.versionStatus === 'ACTIVE' ? 'ok' : 'idle'} size="small">
                {r.versionStatus}
              </CimTag>
            </span>
          ) : null}
        </>
      ),
    },
    {
      key: 'qualificationStatus',
      titleKey: 'rms.deviceRecipes.qualification',
      width: 140,
      render: (r) => {
        const tone: StatusTone = r.qualificationStatus === 'GRANTED' ? 'ok' : 'warn';
        return (
          <CimTag tone={tone} size="small">
            {r.qualificationStatus ?? 'NOT_QUALIFIED'}
          </CimTag>
        );
      },
    },
    {
      key: '__ops',
      titleKey: 'rms.common.actions',
      width: 110,
      render: (r) =>
        r.mapId ? (
          <Can perm="rms:device:update">
            <CimButton size="small" disabled={busy} onClick={() => unbind(r)}>
              {t('rms.tools.unbind')}
            </CimButton>
          </Can>
        ) : null,
    },
  ];

  async function bind() {
    if (!draft.ppid.trim() || !draft.recipeId) {
      toast.error(t('rms.deviceDetail.bindRequired'));
      return;
    }
    setBusy(true);
    try {
      await api.registerPpidMap({
        deviceId: device.id,
        deviceTypeId: null,
        ppidOnTool: draft.ppid.trim(),
        recipeId: draft.recipeId,
        chamberId: draft.chamberId || null,
      });
      setDraft(EMPTY_BIND);
      viewRes.refresh();
      chambersRes.refresh();
      onChanged?.();
    } catch (e) {
      toast.error((e as Error)?.message || t('rms.common.opFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function unbind(row: DeviceRecipeRow) {
    const ok = await confirm({
      titleKey: 'rms.tools.unbindConfirm',
      okKey: 'rms.tools.unbind',
      danger: true,
    });
    if (!ok) return;
    setBusy(true);
    try {
      await api.deletePpidMap(row.mapId as string);
      viewRes.refresh();
      onChanged?.();
    } catch (e) {
      toast.error((e as Error)?.message || t('rms.common.opFailed'));
    } finally {
      setBusy(false);
    }
  }

  const editButton = onEdit ? (
    <Can perm="rms:device:update">
      <CimButton size="small" onClick={onEdit}>
        {t('rms.common.edit')}
      </CimButton>
    </Can>
  ) : undefined;

  return (
    <CimDrawer
      open
      onClose={onClose}
      title={`${device.code} · ${device.name}`}
      width={860}
      extra={editButton}
    >
      <CimDescriptions items={headerItems} record={device} column={2} size="small" bordered />

      {/* 腔体（受控面板：在哪台机上点开就维护哪台机） */}
      <ChamberSection
        deviceId={device.id}
        onChanged={() => {
          chambersRes.refresh();
          onChanged?.();
        }}
      />

      {/* 绑定配方：PPID + 配方 + 腔体（可选）——装载操作收进机台详情，一处搞定 */}
      <Panel
        titleKey="rms.deviceDetail.bind"
        subKey="rms.deviceDetail.bindHint"
        actions={
          <Can perm="rms:device:update">
            <CimButton type="primary" disabled={busy} onClick={bind}>
              {t('rms.tools.bind')}
            </CimButton>
          </Can>
        }
      >
        <FormGrid
          fields={fields}
          value={draft}
          onChange={(p) => setDraft({ ...draft, ...p })}
          columns={3}
        />
      </Panel>

      {/* 装载的配方（四来源聚合，行可点开配方详情） */}
      <Panel
        titleKey="rms.deviceDetail.loaded"
        sub={
          view
            ? `${t('rms.deviceRecipes.total')} ${view.total} · ${t('rms.deviceRecipes.orphan')} ${view.orphan} · ${t('rms.deviceRecipes.drift')} ${view.drift}`
            : undefined
        }
      >
        <DataTable
          flat
          columns={columns}
          rows={rows}
          rowKey={(r) => r.recipeId ?? `orphan:${r.ppidOnTool ?? ''}:${r.code ?? ''}`}
          loading={viewRes.loading}
          error={viewRes.error?.message}
          onRetry={viewRes.refresh}
          onRefresh={viewRes.refresh}
          onRowClick={(r) => (r.recipeId ? onOpenRecipe?.(r) : undefined)}
          emptyTitleKey="rms.deviceRecipes.empty"
        />
      </Panel>
    </CimDrawer>
  );
}
