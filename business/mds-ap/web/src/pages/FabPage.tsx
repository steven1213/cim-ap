import { useCallback, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimModal,
  CimSelect,
  DataTable,
  FormGrid,
  Page,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  type FormOption,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useHasPermission } from '@/lib/usePermission';
import type { Fab, Site } from '@/types';

/** 模块级空数组：`data ?? []` 每次渲染都会造新引用。 */
const NO_FABS: Fab[] = [];
const NO_SITES: Site[] = [];

/**
 * 晶圆厂维护。
 *
 * <p>与「基地」页的差别只有两处：① 多一个「所属基地」下拉（顶部筛选 + 表单字段共用同一份
 * 选项）；② 归属可改（后端 `updateFab` 允许改 siteCode，编码不可改）。</p>
 */
export default function FabPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const canViewSite = useHasPermission(MDS_PERMS.site.view);

  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounced(keyword, 200);
  const [siteFilter, setSiteFilter] = useState<string | null>(null);

  const [editing, setEditing] = useState<Fab | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  // 基地下拉：没授「基地查看」就不拉（否则整页被一个 403 带崩），选项空时表单会拦住提交
  const sitesRes = useResource(({ signal }) => api.listSites(undefined, signal), { enabled: canViewSite });
  const siteOptions = useMemo<FormOption[]>(
    () => (sitesRes.data ?? NO_SITES).map((s) => ({ value: s.siteCode, label: s.siteName, hint: s.siteCode })),
    [sitesRes.data],
  );

  const { data, loading, error, refresh } = useResource(
    ({ signal }) => api.listFabs(siteFilter ?? undefined, debouncedKeyword || undefined, signal),
    { deps: [siteFilter, debouncedKeyword], keepPreviousData: true },
  );
  const fabs = data ?? NO_FABS;

  const doDelete = useCallback(
    async (x: Fab) => {
      const ok = await confirm({
        title: t('mds.common.delete'),
        contentKey: 'mds.common.confirmDelete',
        danger: true,
        okKey: 'mds.common.delete',
      });
      if (!ok) return;
      try {
        await api.deleteFab(x.id);
        toast.success(t('mds.common.deleted'));
      } catch (e) {
        // Fab 下还有 Area 时后端会拒（2004）。规则只在后端判；2004 的文案是通用的
        // （动态原因不进 message），故按本页的下级关系补一句具体提示。
        const code = (e as { code?: number }).code;
        toast.error(
          code === 2004
            ? t('mds.fab.deleteBlocked')
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

  const columns = useMemo<Array<DataTableColumn<Fab>>>(
    () => [
      { key: 'fabCode', titleKey: 'mds.fab.code', mono: true, width: 140 },
      { key: 'fabName', titleKey: 'mds.fab.name', width: 180 },
      {
        key: 'siteName',
        titleKey: 'mds.fab.site',
        width: 180,
        render: (r) => r.siteName ?? <span className="dim">{r.siteCode}</span>,
      },
      {
        key: 'fabType',
        titleKey: 'mds.fab.type',
        width: 120,
        render: (r) => r.fabType ?? <span className="dim">{t('mds.common.dash')}</span>,
      },
      {
        key: 'amhsType',
        titleKey: 'mds.fab.amhsType',
        width: 130,
        render: (r) => r.amhsType ?? <span className="dim">{t('mds.common.dash')}</span>,
      },
      {
        key: 'cleanGrade',
        titleKey: 'mds.fab.cleanGrade',
        width: 120,
        render: (r) => r.cleanGrade ?? <span className="dim">{t('mds.common.dash')}</span>,
      },
      {
        key: '__ops',
        titleKey: 'mds.common.actions',
        width: 160,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.fab.update}>
              <CimButton size="small" onClick={() => setEditing(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.fab.delete}>
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
      

      <DataTable<Fab>
        headActions={<Can perm={MDS_PERMS.fab.create}>
            <CimButton type="primary" size="small" onClick={() => setShowCreate(true)}>
              + {t('mds.fab.createNew')}
            </CimButton>
          </Can>}
        columns={columns}
        rows={fabs}
        rowKey={(r) => r.id}
        loading={loading}
        error={error?.message}
        onRetry={refresh}
        onRefresh={refresh}
        settingsKey="mds.fab"
        scrollX="max-content"
        search={{ value: keyword, onChange: setKeyword, placeholderKey: 'mds.common.search' }}
        actions={
          <CimSelect
            value={siteFilter}
            onChange={(v) => setSiteFilter(typeof v === 'string' && v ? v : null)}
            options={siteOptions}
            placeholderKey="mds.common.all"
            allowClear
            loading={sitesRes.loading}
            width={200}
          />
        }
      />

      {(showCreate || editing) && (
        <FabModal
          initial={editing}
          siteOptions={siteOptions}
          defaultSiteCode={siteFilter}
          canPickSite={canViewSite}
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

interface FabForm {
  fabCode: string;
  siteCode: string;
  fabName: string;
  fabType: string;
  amhsType: string;
  cleanGrade: string;
  remark: string;
}

function FabModal({
  initial,
  siteOptions,
  defaultSiteCode,
  canPickSite,
  onClose,
  onDone,
}: {
  initial: Fab | null;
  siteOptions: FormOption[];
  defaultSiteCode: string | null;
  canPickSite: boolean;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<FabForm>({
    fabCode: initial?.fabCode ?? '',
    // 新建时若顶部已按某基地筛选，默认落在该基地上——省一次选择，且不会建错归属
    siteCode: initial?.siteCode ?? defaultSiteCode ?? '',
    fabName: initial?.fabName ?? '',
    fabType: initial?.fabType ?? '',
    amhsType: initial?.amhsType ?? '',
    cleanGrade: initial?.cleanGrade ?? '',
    remark: initial?.remark ?? '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof FabForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const fields = useMemo<Array<FormField<FabForm>>>(
    () => [
      {
        kind: 'text',
        key: 'fabCode',
        labelKey: 'mds.fab.code',
        required: true,
        mono: true,
        disabled: !!initial, // 编码是主标识（Area 靠它挂靠），不可改
      },
      {
        kind: 'select',
        key: 'siteCode',
        labelKey: 'mds.fab.site',
        required: true,
        options: siteOptions,
        showSearch: true,
        placeholderKey: 'mds.fab.sitePlaceholder',
        // 归属可改（后端允许），但没有「基地查看」权限时拿不到选项，此时禁用而非给空下拉
        disabled: !canPickSite,
      },
      { kind: 'text', key: 'fabName', labelKey: 'mds.fab.name', required: true },
      { kind: 'text', key: 'fabType', labelKey: 'mds.fab.type', placeholder: 'FAB' },
      { kind: 'text', key: 'amhsType', labelKey: 'mds.fab.amhsType', placeholder: 'OHT' },
      { kind: 'text', key: 'cleanGrade', labelKey: 'mds.fab.cleanGrade', placeholder: 'Class 100' },
      { kind: 'text', key: 'remark', labelKey: 'mds.common.remark', span: 2 },
    ],
    [initial, siteOptions, canPickSite],
  );

  async function submit() {
    const next: Partial<Record<keyof FabForm, string>> = {};
    if (!form.fabCode.trim()) next.fabCode = t('mds.common.required');
    if (!form.siteCode.trim()) next.siteCode = t('mds.common.required');
    if (!form.fabName.trim()) next.fabName = t('mds.common.required');
    setFieldErrors(next);
    if (Object.keys(next).length) return;

    setBusy(true);
    setErr('');
    try {
      const payload = {
        fabCode: form.fabCode.trim(),
        siteCode: form.siteCode.trim(),
        fabName: form.fabName.trim(),
        // 三个可选属性留空时发 null：发 '' 会在库里留下空串，筛选与导出都当成有值
        fabType: form.fabType.trim() || null,
        amhsType: form.amhsType.trim() || null,
        cleanGrade: form.cleanGrade.trim() || null,
        remark: form.remark || null,
      };
      if (initial) await api.updateFab(initial.id, payload);
      else await api.createFab(payload);
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
      titleKey={initial ? 'mds.common.edit' : 'mds.fab.createNew'}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={620}
    >
      <FormGrid<FabForm>
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
