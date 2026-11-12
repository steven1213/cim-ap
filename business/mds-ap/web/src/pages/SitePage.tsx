import { useCallback, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
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
import type { Site } from '@/types';

/** 模块级空数组：`data ?? []` 每次渲染都会造新引用。 */
const NO_SITES: Site[] = [];

/**
 * 基地维护（场地层级根节点）。
 *
 * <p>本页是 MDS 的样板 CRUD 页，结构对齐 RMS 的「设备区域」页：手写表格 → {@link DataTable}、
 * 手写模态框 → {@link CimModal}、手写三态 → loading/error/empty、`window.confirm` →
 * {@link useConfirm}。页面里只剩「取数 + 业务动作」。</p>
 *
 * <p>注意：删除受后端保护——基地下还有 Fab 时会被拒（{@code OPERATION_NOT_ALLOWED} / 2004）。
 * 判定只在后端做一次（前端不预判，否则「换个归属再删」这类情况本地判断不出来）；
 * 但 2004 的响应文案是**通用**的（平台 BizException 的动态原因不会进 message，见
 * docs/platform/server/api-convention.md），所以这里按本页的下级关系补一句具体提示。</p>
 */
export default function SitePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounced(keyword, 200);

  const [editing, setEditing] = useState<Site | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const { data, loading, error, refresh } = useResource(
    ({ signal }) => api.listSites(debouncedKeyword || undefined, signal),
    { deps: [debouncedKeyword], keepPreviousData: true },
  );
  const sites = data ?? NO_SITES;

  const doDelete = useCallback(
    async (x: Site) => {
      const ok = await confirm({
        title: t('mds.common.delete'),
        contentKey: 'mds.common.confirmDelete',
        danger: true,
        okKey: 'mds.common.delete',
      });
      if (!ok) return;
      try {
        await api.deleteSite(x.id);
        toast.success(t('mds.common.deleted'));
      } catch (e) {
        // 删除失败用 toast 而不占 error 态：列表本身仍可用，整块换成错误页反而更糟
        const code = (e as { code?: number }).code;
        toast.error(
          code === 2004
            ? t('mds.site.deleteBlocked')
            : e instanceof Error
              ? e.message
              : String(e),
        );
        return;
      }
      refresh();
    },
    [t, confirm, toast, refresh],
  );

  const columns = useMemo<Array<DataTableColumn<Site>>>(
    () => [
      { key: 'siteCode', titleKey: 'mds.site.code', mono: true, width: 160 },
      { key: 'siteName', titleKey: 'mds.site.name', width: 200 },
      {
        key: 'address',
        titleKey: 'mds.site.address',
        render: (r) => <span className="dim">{r.address ?? t('mds.common.dash')}</span>,
      },
      {
        key: 'contactPerson',
        titleKey: 'mds.site.contactPerson',
        width: 140,
        render: (r) => r.contactPerson ?? t('mds.common.dash'),
      },
      {
        key: 'remark',
        titleKey: 'mds.common.remark',
        width: 200,
        render: (r) => <span className="dim">{r.remark ?? t('mds.common.dash')}</span>,
      },
      {
        key: '__ops',
        titleKey: 'mds.common.actions',
        width: 160,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.site.update}>
              <CimButton size="small" onClick={() => setEditing(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.site.delete}>
              <CimButton size="small" onClick={() => void doDelete(r)}>
                {t('mds.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    [t, doDelete],
  );

  return (
    <Page>
      

      <DataTable<Site>
        headActions={<Can perm={MDS_PERMS.site.create}>
            <CimButton type="primary" size="small" onClick={() => setShowCreate(true)}>
              + {t('mds.site.createNew')}
            </CimButton>
          </Can>}
        columns={columns}
        rows={sites}
        rowKey={(r) => r.id}
        loading={loading}
        error={error?.message}
        onRetry={refresh}
        onRefresh={refresh}
        settingsKey="mds.site"
        search={{ value: keyword, onChange: setKeyword, placeholderKey: 'mds.common.search' }}
      />

      {(showCreate || editing) && (
        <SiteModal
          initial={editing}
          onClose={() => {
            setShowCreate(false);
            setEditing(null);
          }}
          onDone={() => {
            const wasEdit = !!editing;
            setShowCreate(false);
            setEditing(null);
            toast.success(t(wasEdit ? 'mds.common.updated' : 'mds.common.created'));
            refresh();
          }}
        />
      )}
    </Page>
  );
}

interface SiteForm {
  siteCode: string;
  siteName: string;
  address: string;
  contactPerson: string;
  contactPhone: string;
  remark: string;
}

function SiteModal({
  initial,
  onClose,
  onDone,
}: {
  initial: Site | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<SiteForm>({
    siteCode: initial?.siteCode ?? '',
    siteName: initial?.siteName ?? '',
    address: initial?.address ?? '',
    contactPerson: initial?.contactPerson ?? '',
    contactPhone: initial?.contactPhone ?? '',
    remark: initial?.remark ?? '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof SiteForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const fields = useMemo<Array<FormField<SiteForm>>>(
    () => [
      {
        kind: 'text',
        key: 'siteCode',
        labelKey: 'mds.site.code',
        required: true,
        mono: true,
        disabled: !!initial, // 编码是主标识，下游 site_code 引用会断
      },
      { kind: 'text', key: 'siteName', labelKey: 'mds.site.name', required: true },
      { kind: 'text', key: 'address', labelKey: 'mds.site.address', span: 2 },
      { kind: 'text', key: 'contactPerson', labelKey: 'mds.site.contactPerson' },
      { kind: 'text', key: 'contactPhone', labelKey: 'mds.site.contactPhone' },
      { kind: 'text', key: 'remark', labelKey: 'mds.common.remark', span: 2 },
    ],
    [initial],
  );

  async function submit() {
    const next: Partial<Record<keyof SiteForm, string>> = {};
    if (!form.siteCode.trim()) next.siteCode = t('mds.common.required');
    if (!form.siteName.trim()) next.siteName = t('mds.common.required');
    setFieldErrors(next);
    if (Object.keys(next).length) return;

    setBusy(true);
    setErr('');
    try {
      const payload = {
        siteCode: form.siteCode.trim(),
        siteName: form.siteName.trim(),
        address: form.address || null,
        contactPerson: form.contactPerson || null,
        contactPhone: form.contactPhone || null,
        remark: form.remark || null,
      };
      if (initial) await api.updateSite(initial.id, payload);
      else await api.createSite(payload);
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
      titleKey={initial ? 'mds.common.edit' : 'mds.site.createNew'}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={620}
    >
      <FormGrid<SiteForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        errors={fieldErrors}
        columns={2}
      />
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
