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
import type { Technology } from '@/types';

/**
 * 制程技术（Technology）——产品管理域第一个页面（技术节点分类，全局唯一编码）。
 *
 * <p>列表页动线与位置拓扑同款：编辑先抢锁（type=technology）、保存同事务清锁、
 * 取消显式释放；techCode 建后不可改（将来 Product 按 code 引用）。</p>
 */

const NO_TECHS: Technology[] = [];

export default function TechnologyPage() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();

  const [keyword, setKeyword] = useState('');
  const techRes = useResource(({ signal }) => api.listTechnologies(undefined, signal));
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Technology | null>(null);

  const rows = useMemo(() => {
    const list = techRes.data ?? NO_TECHS;
    const kw = keyword.trim().toLowerCase();
    if (!kw) return list;
    return list.filter(
      (r) =>
        r.techCode.toLowerCase().includes(kw) ||
        r.techName.toLowerCase().includes(kw) ||
        (r.techNode ?? '').toLowerCase().includes(kw),
    );
  }, [techRes.data, keyword]);

  /** 编辑先抢锁：抢不到提示「谁正在编辑」，不进表单。 */
  const openEdit = async (row: Technology) => {
    try {
      await api.claimLock('technology', row.id);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    setEditing(row);
  };

  const closeEdit = () => {
    if (editing) {
      void api.releaseLock('technology', editing.id).catch(() => {});
    }
    setEditing(null);
  };

  const doDelete = async (row: Technology) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      contentKey: 'mds.common.confirmDelete',
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteTechnology(row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    techRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<Technology>>>(
    () => [
      { key: 'techCode', titleKey: 'mds.tech.code', width: 180, mono: true },
      { key: 'techName', titleKey: 'mds.tech.name', width: 220 },
      { key: 'techNode', titleKey: 'mds.tech.node', width: 120, mono: true, render: (r) => r.techNode ?? '—' },
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
            <Can perm={MDS_PERMS.tech.update}>
              <CimButton size="small" onClick={() => void openEdit(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.tech.delete}>
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
      <DataTable<Technology>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={techRes.loading}
        error={techRes.error?.message ?? null}
        onRetry={techRes.refresh}
        headActions={
          <Can perm={MDS_PERMS.tech.create}>
            <CimButton type="primary" size="small" onClick={() => setCreating(true)}>
              {t('mds.tech.createNew')}
            </CimButton>
          </Can>
        }
        search={{
          value: keyword,
          onChange: setKeyword,
          placeholderKey: 'mds.tech.searchPlaceholder',
        }}
        onRefresh={techRes.refresh}
      />

      {creating && (
        <TechFormModal
          initial={null}
          onClose={() => setCreating(false)}
          onDone={() => {
            setCreating(false);
            toast.success(t('mds.common.created'));
            techRes.refresh();
          }}
        />
      )}
      {editing && (
        <TechFormModal
          initial={editing}
          onClose={closeEdit}
          onDone={() => {
            setEditing(null);
            toast.success(t('mds.common.updated'));
            techRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

/** 弹窗标题键（属性名以 Key 结尾的对象字面量，供 check-i18n 扫描；三元里写裸字面量会被判「无人引用」）。 */
const TECH_TITLE = {
  editKey: 'mds.tech.editTitle',
  createKey: 'mds.tech.createTitle',
} as const;

/** 制程技术新建/编辑弹窗（编辑锁由页面层抢/放；techCode 建后不可改）。 */
function TechFormModal({
  initial,
  onClose,
  onDone,
}: {
  initial: Technology | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    code: initial?.techCode ?? '',
    name: initial?.techName ?? '',
    node: initial?.techNode ?? '',
    remark: initial?.remark ?? '',
    enabled: initial?.enabled ?? true,
  });

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [{ kind: 'text' as const, key: 'code' as const, labelKey: 'mds.tech.code', required: true, mono: true }]
        : []),
      { kind: 'text' as const, key: 'name' as const, labelKey: 'mds.tech.name', required: true },
      { kind: 'text' as const, key: 'node' as const, labelKey: 'mds.tech.node', mono: true, placeholder: '0.35um' },
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
        await api.updateTechnology(initial.id, {
          techName: form.name.trim(),
          techNode: form.node.trim() || null,
          remark: form.remark || null,
          enabled: form.enabled,
        });
      } else {
        await api.createTechnology({
          techCode: form.code.trim(),
          techName: form.name.trim(),
          techNode: form.node.trim() || null,
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
      titleKey={initial ? TECH_TITLE.editKey : TECH_TITLE.createKey}
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
