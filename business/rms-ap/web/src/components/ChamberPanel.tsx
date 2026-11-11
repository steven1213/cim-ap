import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  DataTable,
  FormGrid,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import type { DeviceChamber } from '@/types';

const NO_ROWS: DeviceChamber[] = [];

interface Draft {
  code: string;
  name: string;
  sortNo: string;
}

const EMPTY: Draft = { code: '', name: '', sortNo: '0' };

/**
 * 机台腔体面板（W7，受控组件）。
 *
 * <p>多腔体机台的配方是按腔体装载的，故腔体是「机台视角」的必要维度。
 * 挂在机台详情抽屉内：指定 {@link deviceId} 即维护该机台的腔体列表——
 * 在哪台机上点开，就维护哪台机的腔体，不再需要单独选机台（一处搞定）。</p>
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>手写 `useEffect + load()` → `useResource`：切机台时旧请求会被取消，
 *       不会出现「切到 B 机台，A 机台的腔体后到、盖掉 B 的」。</li>
 *   <li>三个 `useState` 字段 + 手写 `<input>` → `FormGrid` 字段数组（`id` 与 label 关联，
 *       键盘可聚焦）。</li>
 *   <li>`window.confirm` → `useConfirm`；页内常驻红条 → toast（红条不会消失，
 *       下一次操作前一直挂着，看起来像当前状态）。</li>
 *   <li>手写 `<table class="data">` → `DataTable flat`（嵌在 `Panel` 里不套框）。</li>
 * </ul>
 */
export default function ChamberSection({
  deviceId,
  onChanged,
}: {
  deviceId: string;
  onChanged?: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [errors, setErrors] = useState<Partial<Record<keyof Draft, string>>>({});
  const [busy, setBusy] = useState(false);

  const res = useResource(
    () => (deviceId ? api.listChambers(deviceId) : Promise.resolve(NO_ROWS)),
    { deps: [deviceId], enabled: !!deviceId },
  );
  const rows = res.data ?? NO_ROWS;

  const fields: Array<FormField<Draft>> = [
    { key: 'code', kind: 'text', labelKey: 'rms.chamber.code', placeholder: 'CH-A', mono: true, required: true },
    { key: 'name', kind: 'text', labelKey: 'rms.chamber.name', placeholderKey: 'rms.chamber.namePh' },
    { key: 'sortNo', kind: 'text', labelKey: 'rms.chamber.sortNo', mono: true },
  ];

  async function add() {
    if (!draft.code.trim() || !draft.name.trim()) {
      setErrors({
        code: draft.code.trim() ? undefined : t('rms.chamber.required'),
        name: draft.name.trim() ? undefined : t('rms.chamber.required'),
      });
      return;
    }
    setErrors({});
    setBusy(true);
    try {
      await api.createChamber({
        deviceId,
        code: draft.code.trim(),
        name: draft.name.trim(),
        sortNo: Number(draft.sortNo) || 0,
      });
      setDraft(EMPTY);
      res.refresh();
      onChanged?.();
    } catch (e) {
      toast.error((e as Error)?.message || t('rms.common.opFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function remove(x: DeviceChamber) {
    const ok = await confirm({
      titleKey: 'rms.chamber.deleteConfirm',
      okKey: 'rms.common.delete',
      danger: true,
    });
    if (!ok) return;
    setBusy(true);
    try {
      await api.deleteChamber(x.id);
      res.refresh();
      onChanged?.();
    } catch (e) {
      toast.error((e as Error)?.message || t('rms.common.opFailed'));
    } finally {
      setBusy(false);
    }
  }

  const columns: Array<DataTableColumn<DeviceChamber>> = [
    { key: 'code', titleKey: 'rms.chamber.code', mono: true, width: 140 },
    { key: 'name', titleKey: 'rms.chamber.name' },
    { key: 'sortNo', titleKey: 'rms.chamber.sortNo', mono: true, width: 90 },
    {
      key: '__ops',
      titleKey: 'rms.common.actions',
      width: 110,
      render: (x) => (
        <Perms code="rms:device:delete">
          <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => remove(x)}>
            {t('rms.common.delete')}
          </button>
        </Perms>
      ),
    },
  ];

  return (
    <Panel
      titleKey="rms.chamber.title"
      subKey="rms.chamber.sub"
      actions={
        <Perms code="rms:device:create">
          <button className="btn btn-primary" disabled={busy} onClick={add}>
            {t('rms.chamber.add')}
          </button>
        </Perms>
      }
    >
      <FormGrid fields={fields} value={draft} onChange={(p) => setDraft({ ...draft, ...p })} errors={errors} columns={3} />
      <DataTable
        flat
        columns={columns}
        rows={rows}
        rowKey={(x) => x.id}
        loading={res.loading}
        error={res.error?.message}
        onRetry={res.refresh}
        onRefresh={res.refresh}
        emptyTitleKey="rms.chamber.empty"
      />
    </Panel>
  );
}
