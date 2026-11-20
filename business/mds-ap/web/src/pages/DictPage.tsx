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
import type { DictInfo, DictItem } from '@/types';

/**
 * 数据字典——平台 sys_dict / sys_dict_item 的管理页（主从：上字典、下选中字典的明细）。
 *
 * <p>产品分组 / 晶圆类型等分类字段的枚举源在这里维护。系统级字典
 * （{@code is_system=true}，种子写入的行业固定枚举）删除被后端守门，
 * 标记本身可在表单里设置/取消——勾上即受保护，取消即恢复可删。</p>
 */

const NO_DICTS: DictInfo[] = [];
const NO_ITEMS: DictItem[] = [];

export default function DictPage() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();

  const [keyword, setKeyword] = useState('');
  const dictRes = useResource(({ signal }) => api.listDicts(signal));
  const [selected, setSelected] = useState<DictInfo | null>(null);
  const itemRes = useResource(
    ({ signal }) =>
      selected ? api.listDictItems(selected.id, signal) : Promise.resolve(NO_ITEMS),
    { deps: [selected?.id ?? null], enabled: !!selected },
  );
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<DictInfo | null>(null);
  const [itemCreating, setItemCreating] = useState(false);
  const [itemEditing, setItemEditing] = useState<DictItem | null>(null);

  const rows = useMemo(() => {
    const list = dictRes.data ?? NO_DICTS;
    const kw = keyword.trim().toLowerCase();
    if (!kw) return list;
    return list.filter(
      (r) => r.code.toLowerCase().includes(kw) || (r.name ?? '').toLowerCase().includes(kw),
    );
  }, [dictRes.data, keyword]);

  // 刷新字典后若选中项已不在列表（被删/被过滤），清掉选中避免明细表悬空
  const selectOf = (d: DictInfo | null) => {
    setSelected(d);
  };

  const refreshDicts = (keep: string | null) => {
    dictRes.refresh();
    if (!keep) setSelected(null);
  };

  const doDeleteDict = async (row: DictInfo) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      contentKey: 'mds.dict.confirmDeleteDict',
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteDict(row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refreshDicts(null);
  };

  const doDeleteItem = async (row: DictItem) => {
    if (!selected) return;
    const ok = await confirm({
      title: t('mds.common.delete'),
      contentKey: 'mds.dict.confirmDeleteItem',
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteDictItem(selected.id, row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    itemRes.refresh();
  };

  const dictColumns = useMemo<Array<DataTableColumn<DictInfo>>>(
    () => [
      { key: 'name', titleKey: 'mds.dict.name', width: 200, render: (r) => r.name ?? '—' },
      { key: 'code', titleKey: 'mds.dict.code', width: 220, mono: true },
      {
        key: 'isSystem',
        titleKey: 'mds.dict.system',
        width: 110,
        render: (r) => (r.isSystem ? t('mds.dict.systemYes') : <span className="dim">—</span>),
      },
      {
        key: 'status',
        titleKey: 'mds.topo.status',
        width: 90,
        render: (r) => (
          <span className={r.status === 'ENABLED' ? '' : 'dim'}>
            {r.status === 'ENABLED' ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 140,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.dict.save}>
              <CimButton size="small" onClick={() => setEditing(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            {!r.isSystem && (
              <Can perm={MDS_PERMS.dict.delete}>
                <CimButton size="small" danger onClick={() => void doDeleteDict(r)}>
                  {t('mds.common.delete')}
                </CimButton>
              </Can>
            )}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const itemColumns = useMemo<Array<DataTableColumn<DictItem>>>(
    () => [
      { key: 'itemKey', titleKey: 'mds.dict.itemKey', width: 180, mono: true },
      { key: 'itemValue', titleKey: 'mds.dict.itemValue', width: 220, render: (r) => r.itemValue ?? '—' },
      { key: 'i18nCode', titleKey: 'mds.dict.i18nCode', width: 200, render: (r) => r.i18nCode ?? '—' },
      { key: 'sortNo', titleKey: 'mds.dict.sortNo', width: 90 },
      {
        key: 'isSystem',
        titleKey: 'mds.dict.system',
        width: 110,
        render: (r) => (r.isSystem ? t('mds.dict.systemYes') : <span className="dim">—</span>),
      },
      {
        key: 'status',
        titleKey: 'mds.topo.status',
        width: 90,
        render: (r) => (
          <span className={r.status === 'ENABLED' ? '' : 'dim'}>
            {r.status === 'ENABLED' ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 140,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.dict.save}>
              <CimButton size="small" onClick={() => setItemEditing(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            {!r.isSystem && (
              <Can perm={MDS_PERMS.dict.delete}>
                <CimButton size="small" danger onClick={() => void doDeleteItem(r)}>
                  {t('mds.common.delete')}
                </CimButton>
              </Can>
            )}
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <DataTable<DictInfo>
        columns={dictColumns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={dictRes.loading}
        error={dictRes.error?.message ?? null}
        onRetry={dictRes.refresh}
        onRowClick={(r) => selectOf(r)}
        rowClassName={(r) => (selected?.id === r.id ? 'row-selected' : '')}
        headActions={
          <Can perm={MDS_PERMS.dict.save}>
            <CimButton type="primary" size="small" onClick={() => setCreating(true)}>
              {t('mds.common.create')}
            </CimButton>
          </Can>
        }
        search={{
          value: keyword,
          onChange: setKeyword,
          placeholderKey: 'mds.dict.searchPlaceholder',
        }}
        onRefresh={dictRes.refresh}
      />

      {selected && (
        <>
          <h3 className="dict-items-title">
            {t('mds.dict.itemsTitle', { name: selected.name ?? selected.code, code: selected.code })}
          </h3>
          <DataTable<DictItem>
            columns={itemColumns}
            rows={itemRes.data ?? NO_ITEMS}
            rowKey={(r) => r.id}
            loading={itemRes.loading}
            error={itemRes.error?.message ?? null}
            onRetry={itemRes.refresh}
            headActions={
              <Can perm={MDS_PERMS.dict.save}>
                <CimButton type="primary" size="small" onClick={() => setItemCreating(true)}>
                  {t('mds.dict.createItem')}
                </CimButton>
              </Can>
            }
            onRefresh={itemRes.refresh}
          />
        </>
      )}

      {creating && (
        <DictFormModal
          initial={null}
          onClose={() => setCreating(false)}
          onDone={(d) => {
            setCreating(false);
            toast.success(t('mds.common.created'));
            setSelected(d);
            refreshDicts(d.id);
          }}
        />
      )}
      {editing && (
        <DictFormModal
          initial={editing}
          onClose={() => setEditing(null)}
          onDone={(d) => {
            setEditing(null);
            toast.success(t('mds.common.updated'));
            setSelected(d);
            refreshDicts(d.id);
          }}
        />
      )}
      {selected && itemCreating && (
        <DictItemFormModal
          dict={selected}
          initial={null}
          onClose={() => setItemCreating(false)}
          onDone={() => {
            setItemCreating(false);
            toast.success(t('mds.common.created'));
            itemRes.refresh();
          }}
        />
      )}
      {selected && itemEditing && (
        <DictItemFormModal
          dict={selected}
          initial={itemEditing}
          onClose={() => setItemEditing(null)}
          onDone={() => {
            setItemEditing(null);
            toast.success(t('mds.common.updated'));
            itemRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

/** 弹窗标题键（属性名以 Key 结尾，供 check-i18n 扫描；三元裸字面量会被判死键）。 */
const DICT_TITLE = {
  editKey: 'mds.dict.editDictTitle',
  createKey: 'mds.dict.createDictTitle',
} as const;
const ITEM_TITLE = {
  editKey: 'mds.dict.editItemTitle',
  createKey: 'mds.dict.createItemTitle',
} as const;

/** 字典新建/编辑弹窗（code 建后不可改；系统级标记可设置）。 */
function DictFormModal({
  initial,
  onClose,
  onDone,
}: {
  initial: DictInfo | null;
  onClose: () => void;
  onDone: (d: DictInfo) => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    code: initial?.code ?? '',
    name: initial?.name ?? '',
    system: initial?.isSystem ?? false,
    enabled: initial?.status !== 'DISABLED',
  });

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [{ kind: 'text' as const, key: 'code' as const, labelKey: 'mds.dict.code', required: true, mono: true, placeholderKey: 'mds.dict.codePlaceholder' }]
        : []),
      { kind: 'text' as const, key: 'name' as const, labelKey: 'mds.dict.name', required: true },
      { kind: 'switch' as const, key: 'system' as const, labelKey: 'mds.dict.system' },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
    ],
    [initial],
  );

  async function submit() {
    if (!initial && !form.code.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    if (!form.name.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      if (initial) {
        const d = await api.updateDict(initial.id, {
          name: form.name.trim(),
          isSystem: form.system,
          status: form.enabled ? 'ENABLED' : 'DISABLED',
        });
        onDone(d);
      } else {
        const d = await api.createDict({
          code: form.code.trim(),
          name: form.name.trim(),
          isSystem: form.system,
          status: form.enabled ? 'ENABLED' : 'DISABLED',
        });
        onDone(d);
      }
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
      titleKey={initial ? DICT_TITLE.editKey : DICT_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={480}
    >
      <FormGrid fields={fields} value={form} onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))} columns={2} />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}

/** 明细新建/编辑弹窗（itemKey=落库值；itemValue=显示文案；系统级明细不可删）。 */
function DictItemFormModal({
  dict,
  initial,
  onClose,
  onDone,
}: {
  dict: DictInfo;
  initial: DictItem | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    itemKey: initial?.itemKey ?? '',
    itemValue: initial?.itemValue ?? '',
    i18nCode: initial?.i18nCode ?? '',
    sortNo: initial?.sortNo ?? 10,
    system: initial?.isSystem ?? false,
    enabled: initial?.status !== 'DISABLED',
  });

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      { kind: 'text' as const, key: 'itemKey' as const, labelKey: 'mds.dict.itemKey', required: true, mono: true, placeholderKey: 'mds.dict.itemKeyPlaceholder' },
      { kind: 'text' as const, key: 'itemValue' as const, labelKey: 'mds.dict.itemValue' },
      { kind: 'text' as const, key: 'i18nCode' as const, labelKey: 'mds.dict.i18nCode', placeholderKey: 'mds.dict.i18nPlaceholder' },
      { kind: 'number' as const, key: 'sortNo' as const, labelKey: 'mds.dict.sortNo', precision: 0 },
      { kind: 'switch' as const, key: 'system' as const, labelKey: 'mds.dict.system' },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
    ],
    [],
  );

  async function submit() {
    if (!form.itemKey.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const payload = {
        itemKey: form.itemKey.trim(),
        itemValue: form.itemValue.trim() || null,
        i18nCode: form.i18nCode.trim() || null,
        sortNo: form.sortNo ?? 0,
        isSystem: form.system,
        status: (form.enabled ? 'ENABLED' : 'DISABLED') as 'ENABLED' | 'DISABLED',
      };
      if (initial) {
        await api.updateDictItem(dict.id, initial.id, payload);
      } else {
        await api.createDictItem(dict.id, payload);
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
      titleKey={initial ? ITEM_TITLE.editKey : ITEM_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={480}
    >
      <p className="topo-fab-hint">{t('mds.dict.itemOfDict', { code: dict.code })}</p>
      <FormGrid fields={fields} value={form} onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))} columns={2} />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
