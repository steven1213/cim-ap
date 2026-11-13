import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimModal,
  DataTable,
  FormGrid,
  Page,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { Capability } from '@/types';

/**
 * 设备能力（Capability）——能力字典。
 *
 * <p>能力回答「能不能做」（如 E84 接口、双端口搬运、6 英寸加工）；设备↔能力关联在
 * 设备行内维护。capabilityCode 建后不可改（关联表按 code 引用）；被设备关联的能力
 * 不可删（后端守门 2004）。</p>
 */

const NO_CAPS: Capability[] = [];

export default function CapabilityPage() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();

  const [keyword, setKeyword] = useState('');
  const capRes = useResource(({ signal }) => api.listCapabilities(undefined, signal));
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Capability | null>(null);

  const rows = useMemo(() => {
    const list = capRes.data ?? NO_CAPS;
    const kw = keyword.trim().toLowerCase();
    if (!kw) return list;
    return list.filter(
      (r) =>
        r.capabilityCode.toLowerCase().includes(kw) ||
        r.capabilityName.toLowerCase().includes(kw),
    );
  }, [capRes.data, keyword]);

  const openEdit = async (row: Capability) => {
    try {
      await api.claimLock('capability', row.id);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    setEditing(row);
  };

  const closeEdit = () => {
    if (editing) {
      void api.releaseLock('capability', editing.id).catch(() => {});
    }
    setEditing(null);
  };

  const doDelete = async (row: Capability) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      contentKey: 'mds.common.confirmDelete',
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteCapability(row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    capRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<Capability>>>(
    () => [
      { key: 'capabilityCode', titleKey: 'mds.capability.code', width: 180, mono: true },
      { key: 'capabilityName', titleKey: 'mds.capability.name', width: 240 },
      {
        key: 'enabled',
        titleKey: 'mds.topo.status',
        width: 90,
        render: (r) => (
          <span className={r.enabled ? '' : 'dim'}>
            {r.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
      { key: 'remark', titleKey: 'mds.common.remark', ellipsis: true, render: (r) => r.remark ?? '—' },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 140,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.capability.update}>
              <CimButton size="small" onClick={() => void openEdit(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.capability.delete}>
              <CimButton size="small" danger onClick={() => void doDelete(r)}>
                {t('mds.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <DataTable<Capability>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={capRes.loading}
        error={capRes.error?.message ?? null}
        onRetry={capRes.refresh}
        headActions={
          <Can perm={MDS_PERMS.capability.create}>
            <CimButton type="primary" size="small" onClick={() => setCreating(true)}>
              {t('mds.capability.createNew')}
            </CimButton>
          </Can>
        }
        search={{
          value: keyword,
          onChange: setKeyword,
          placeholderKey: 'mds.capability.searchPlaceholder',
        }}
        onRefresh={capRes.refresh}
      />

      {creating && (
        <CapabilityFormModal
          initial={null}
          onClose={() => setCreating(false)}
          onDone={() => {
            setCreating(false);
            toast.success(t('mds.common.created'));
            capRes.refresh();
          }}
        />
      )}
      {editing && (
        <CapabilityFormModal
          initial={editing}
          onClose={closeEdit}
          onDone={() => {
            setEditing(null);
            toast.success(t('mds.common.updated'));
            capRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

const CAP_TITLE = {
  editKey: 'mds.capability.editTitle',
  createKey: 'mds.capability.createTitle',
} as const;

/** 能力新建/编辑弹窗（编辑锁由页面层抢/放；capabilityCode 建后不可改）。 */
function CapabilityFormModal({
  initial,
  onClose,
  onDone,
}: {
  initial: Capability | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    code: initial?.capabilityCode ?? '',
    name: initial?.capabilityName ?? '',
    remark: initial?.remark ?? '',
    enabled: initial?.enabled ?? true,
  });

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [
            {
              kind: 'text' as const,
              key: 'code' as const,
              labelKey: 'mds.capability.code',
              required: true,
              mono: true,
            },
          ]
        : []),
      { kind: 'text' as const, key: 'name' as const, labelKey: 'mds.capability.name', required: true },
      { kind: 'text' as const, key: 'remark' as const, labelKey: 'mds.common.remark', span: 2 },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
    ],
    [initial],
  );

  async function submit() {
    if (!form.name.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      if (initial) {
        await api.updateCapability(initial.id, {
          capabilityName: form.name.trim(),
          remark: form.remark || null,
          enabled: form.enabled,
        });
      } else {
        await api.createCapability({
          capabilityCode: form.code.trim(),
          capabilityName: form.name.trim(),
          remark: form.remark || null,
          enabled: form.enabled,
        });
      }
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey={initial ? CAP_TITLE.editKey : CAP_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={480}
    >
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        columns={2}
      />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
