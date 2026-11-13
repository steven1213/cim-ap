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
  useDict,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { Product, Technology } from '@/types';

/**
 * 产品（Product）——产品管理域第二个页面（SiView：Technology → Product → Route）。
 *
 * <p>行业通用口径：类型 PROD=量产/NPW=陪片；tech_code 逻辑引用制程技术（下拉选择，
 * 引用守门在后端）；productCode 建后不可改（MES 按 code 引用）。列表页动线与
 * 制程技术同款：编辑先抢锁（type=product）、保存同事务清锁、取消显式释放。</p>
 */

const NO_PRODUCTS: Product[] = [];
const NO_TECHS: Technology[] = [];

export default function ProductPage() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();
  const groupDict = useDict('mds.product_group');
  const waferDict = useDict('mds.wafer_type');

  const [keyword, setKeyword] = useState('');
  const productRes = useResource(({ signal }) => api.listProducts(undefined, signal));
  const techRes = useResource(({ signal }) => api.listTechnologies(undefined, signal));
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);

  const rows = useMemo(() => {
    const list = productRes.data ?? NO_PRODUCTS;
    const kw = keyword.trim().toLowerCase();
    if (!kw) return list;
    return list.filter(
      (r) =>
        r.productCode.toLowerCase().includes(kw) ||
        r.productName.toLowerCase().includes(kw) ||
        (r.productGroup ?? '').toLowerCase().includes(kw) ||
        (r.techCode ?? '').toLowerCase().includes(kw),
    );
  }, [productRes.data, keyword]);

  /** 编辑先抢锁：抢不到提示「谁正在编辑」，不进表单。 */
  const openEdit = async (row: Product) => {
    try {
      await api.claimLock('product', row.id);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    setEditing(row);
  };

  const closeEdit = () => {
    if (editing) {
      void api.releaseLock('product', editing.id).catch(() => {});
    }
    setEditing(null);
  };

  const doDelete = async (row: Product) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      contentKey: 'mds.common.confirmDelete',
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteProduct(row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    productRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<Product>>>(
    () => [
      { key: 'productCode', titleKey: 'mds.product.code', width: 150, mono: true },
      { key: 'productName', titleKey: 'mds.product.name', width: 220 },
      {
        key: 'productType',
        titleKey: 'mds.product.type',
        width: 90,
        render: (r) => t(r.productType === 'NPW' ? 'mds.product.typeNpw' : 'mds.product.typeProd'),
      },
      { key: 'productGroup', titleKey: 'mds.product.group', width: 150, render: (r) => groupDict.labelOf(r.productGroup) },
      { key: 'techCode', titleKey: 'mds.product.tech', width: 110, mono: true, render: (r) => r.techCode ?? '—' },
      { key: 'waferType', titleKey: 'mds.product.waferType', width: 110, render: (r) => waferDict.labelOf(r.waferType) },
      { key: 'owner', titleKey: 'mds.product.owner', width: 100, render: (r) => r.owner ?? '—' },
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
            <Can perm={MDS_PERMS.product.update}>
              <CimButton size="small" onClick={() => void openEdit(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.product.delete}>
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
      <DataTable<Product>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={productRes.loading}
        error={productRes.error?.message ?? null}
        onRetry={productRes.refresh}
        headActions={
          <Can perm={MDS_PERMS.product.create}>
            <CimButton type="primary" size="small" onClick={() => setCreating(true)}>
              {t('mds.product.createNew')}
            </CimButton>
          </Can>
        }
        search={{
          value: keyword,
          onChange: setKeyword,
          placeholderKey: 'mds.product.searchPlaceholder',
        }}
        onRefresh={productRes.refresh}
      />

      {creating && (
        <ProductFormModal
          initial={null}
          techs={techRes.data ?? NO_TECHS}
          onClose={() => setCreating(false)}
          onDone={() => {
            setCreating(false);
            toast.success(t('mds.common.created'));
            productRes.refresh();
          }}
        />
      )}
      {editing && (
        <ProductFormModal
          initial={editing}
          techs={techRes.data ?? NO_TECHS}
          onClose={closeEdit}
          onDone={() => {
            setEditing(null);
            toast.success(t('mds.common.updated'));
            productRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

/** 弹窗标题键（属性名以 Key 结尾的对象字面量，供 check-i18n 扫描；三元里写裸字面量会被判「无人引用」）。 */
const PRODUCT_TITLE = {
  editKey: 'mds.product.editTitle',
  createKey: 'mds.product.createTitle',
} as const;

/** 产品新建/编辑弹窗（编辑锁由页面层抢/放；productCode 建后不可改）。 */
function ProductFormModal({
  initial,
  techs,
  onClose,
  onDone,
}: {
  initial: Product | null;
  techs: Technology[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    code: initial?.productCode ?? '',
    name: initial?.productName ?? '',
    tech: initial?.techCode ?? '',
    type: initial?.productType ?? 'PROD',
    group: initial?.productGroup ?? '',
    wafer: initial?.waferType ?? '',
    owner: initial?.owner ?? '',
    remark: initial?.remark ?? '',
    enabled: initial?.enabled ?? true,
  });

  const techOptions = useMemo(
    () => techs.map((x) => ({ value: x.techCode, label: `${x.techCode} · ${x.techName}` })),
    [techs],
  );

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [{ kind: 'text' as const, key: 'code' as const, labelKey: 'mds.product.code', required: true, mono: true }]
        : []),
      { kind: 'text' as const, key: 'name' as const, labelKey: 'mds.product.name', required: true },
      { kind: 'select' as const, key: 'type' as const, labelKey: 'mds.product.type', options: [
        { value: 'PROD', label: t('mds.product.typeProd') },
        { value: 'NPW', label: t('mds.product.typeNpw') },
      ] },
      { kind: 'select' as const, key: 'tech' as const, labelKey: 'mds.product.tech', options: techOptions, allowClear: true, placeholderKey: 'mds.product.techPlaceholder' },
      { kind: 'select' as const, key: 'group' as const, labelKey: 'mds.product.group', dict: 'mds.product_group', allowClear: true },
      { kind: 'select' as const, key: 'wafer' as const, labelKey: 'mds.product.waferType', dict: 'mds.wafer_type', allowClear: true },
      { kind: 'text' as const, key: 'owner' as const, labelKey: 'mds.product.owner' },
      { kind: 'text' as const, key: 'remark' as const, labelKey: 'mds.common.remark', span: 2 },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
    ],
    [initial, techOptions, t],
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
        await api.updateProduct(initial.id, {
          productName: form.name.trim(),
          techCode: form.tech || null,
          productType: form.type,
          productGroup: form.group || null,
          waferType: form.wafer || null,
          owner: form.owner || null,
          remark: form.remark || null,
          enabled: form.enabled,
        });
      } else {
        await api.createProduct({
          productCode: form.code.trim(),
          productName: form.name.trim(),
          techCode: form.tech || null,
          productType: form.type,
          productGroup: form.group || null,
          waferType: form.wafer || null,
          owner: form.owner || null,
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
      titleKey={initial ? PRODUCT_TITLE.editKey : PRODUCT_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={520}
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
