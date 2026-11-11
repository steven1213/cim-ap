import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDebounced, useResource } from '@cim/data';
import {
  Can,
  CimInput,
  CimModal,
  CimSelect,
  CimTabs,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  PageHead,
  Toolbar,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  CimButton,
  KpiCard,
} from '@cim/ui';
import * as api from '@/lib/rmsApi';
import { ECSV_MANAGE } from '@/lib/permCodes';
import type { Device, EcSvSpec, EcSvValue, Recipe } from '@/types';

const SPEC_TYPES = ['TARGET', 'RANGE', 'TOLERANCE'];
const SPEC_TYPE_OPTIONS = SPEC_TYPES.map((s) => ({ value: s, labelKey: `rms.spec.type.${s}` }));
const SCOPE_OPTIONS = [
  { value: 'RECIPE', labelKey: 'rms.ecsv.scope.RECIPE' },
  { value: 'PRODUCT', labelKey: 'rms.ecsv.scope.PRODUCT' },
];
const ECSV_TYPE_OPTIONS = [
  { value: 'EC', label: 'EC' },
  { value: 'SV', label: 'SV' },
];

const NO_SPECS: EcSvSpec[] = [];
const NO_VALUES: EcSvValue[] = [];
const NO_RECIPES: Recipe[] = [];
const NO_DEVICES: Device[] = [];

/** 规格行的取值展示：按 specType 拼（与参数规格页同一口径）。 */
function specValueText(x: EcSvSpec): string {
  if (x.specType === 'RANGE') return `[${x.rangeMin ?? '-'}, ${x.rangeMax ?? '-'}]`;
  if (x.specType === 'TOLERANCE') {
    return `*=${x.targetValue ?? '-'} ±${x.tolerancePct ?? '-'}%/±${x.toleranceAbs ?? '-'}`;
  }
  return x.targetValue ?? '-';
}

/**
 * EC/SV 规格与校验页（FR-D2~D4，Req 43/44/45）。
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>手写「两个按钮当 tab 用」→ `CimTabs`（懒挂载：切到校验记录才去查值）；
 *       原实现两个表共用一次 `refresh()` 拉三份数据，切 tab 时明明不需要也会重查。</li>
 *   <li>两个弹窗从手写 `.modal-backdrop` 改为 `CimModal` + `FormGrid`：拿到 Esc 关闭、
 *       焦点管理与滚动锁定；规格字段按 `specType` 重建（切类型后旧值不再随 payload 发出）。</li>
 *   <li>删除用 `useConfirm` + toast（旧实现用 `window.confirm` 且删除失败时静默无反应）。</li>
 * </ul>
 */
export default function EcSvPage() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();

  const [tab, setTab] = useState<'spec' | 'value'>('spec');
  const [scopeFilter, setScopeFilter] = useState('');
  const [refFilter, setRefFilter] = useState('');
  const refDebounced = useDebounced(refFilter, 300);
  const [showSpec, setShowSpec] = useState(false);
  const [showUpload, setShowUpload] = useState(false);

  const summaryRes = useResource(() => api.ecSvSummary());
  const specsRes = useResource(
    () =>
      api.listEcSvSpecs({
        ...(scopeFilter ? { scope: scopeFilter } : {}),
        ...(refDebounced.trim() ? { refId: refDebounced.trim() } : {}),
      }),
    { deps: [scopeFilter, refDebounced], enabled: tab === 'spec', keepPreviousData: true },
  );
  const valuesRes = useResource(() => api.listEcSvValues({ limit: 100 }), {
    enabled: tab === 'value',
    keepPreviousData: true,
  });

  const summary = summaryRes.data;

  const refreshAll = () => {
    summaryRes.refresh();
    if (tab === 'spec') specsRes.refresh();
    else valuesRes.refresh();
  };

  const doDelete = async (x: EcSvSpec) => {
    const ok = await confirm({
      titleKey: 'rms.common.delete',
      contentKey: 'rms.common.confirmDelete',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteEcSvSpec(x.id);
      toast.success(t('rms.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refreshAll();
  };

  const specColumns = useMemo<Array<DataTableColumn<EcSvSpec>>>(
    () => [
      {
        key: 'scope',
        titleKey: 'rms.ecsv.scope',
        width: 110,
        render: (x) => <CimTag size="small">{t(`rms.ecsv.scope.${x.scope}`)}</CimTag>,
      },
      { key: 'refId', titleKey: 'rms.ecsv.refId', mono: true, width: 160 },
      {
        key: 'ecsvType',
        titleKey: 'rms.ecsv.ecsvType',
        width: 90,
        render: (x) => <CimTag size="small">{x.ecsvType}</CimTag>,
      },
      { key: 'paramKey', titleKey: 'rms.ecsv.paramKey', mono: true, width: 170 },
      {
        key: 'specType',
        titleKey: 'rms.spec.specType',
        width: 110,
        render: (x) => <CimTag size="small">{t(`rms.spec.type.${x.specType}`, { defaultValue: x.specType })}</CimTag>,
      },
      {
        key: 'value',
        titleKey: 'rms.spec.target',
        mono: true,
        width: 200,
        render: (x) => specValueText(x),
      },
      {
        key: 'unit',
        titleKey: 'rms.spec.unit',
        width: 80,
        render: (x) => x.unit ?? t('rms.common.dash'),
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 90,
        render: (x) => (
          <Can perm={ECSV_MANAGE}>
            <CimButton size="small" onClick={() => void doDelete(x)}>
              {t('rms.common.delete')}
            </CimButton>
          </Can>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const valueColumns = useMemo<Array<DataTableColumn<EcSvValue>>>(
    () => [
      { key: 'deviceId', titleKey: 'rms.ecsv.device', mono: true, width: 150 },
      { key: 'ecsvType', titleKey: 'rms.ecsv.ecsvType', width: 90 },
      { key: 'paramKey', titleKey: 'rms.ecsv.paramKey', mono: true, width: 170 },
      { key: 'actualValue', titleKey: 'rms.ecsv.actual', mono: true, width: 130 },
      {
        key: 'result',
        titleKey: 'rms.ecsv.result',
        width: 90,
        render: (x) => (
          <CimTag
            size="small"
            tone={x.result === 'PASS' ? 'ok' : x.result === 'FAIL' ? 'err' : 'warn'}
          >
            {x.result}
          </CimTag>
        ),
      },
      {
        key: 'detail',
        titleKey: 'rms.ecsv.detail',
        ellipsis: true,
        render: (x) => <span className="muted">{x.detail}</span>,
      },
      {
        key: 'capturedAt',
        titleKey: 'rms.ecsv.capturedAt',
        width: 170,
        render: (x) => <span className="muted">{x.capturedAt}</span>,
      },
    ],
    [t],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.ecsv.title"
        subtitleKey="rms.ecsv.sub"
        actions={
          <Can perm={ECSV_MANAGE}>
            <CimButton size="small" onClick={() => setShowUpload(true)}>
              {t('rms.ecsv.upload')}
            </CimButton>
            <CimButton type="primary" size="small" onClick={() => setShowSpec(true)}>
              + {t('rms.ecsv.addSpec')}
            </CimButton>
          </Can>
        }
      />

      {summary && (
        <div className="kpi-grid">
          <KpiCard label={t('rms.ecsv.kpi.specs')} value={summary.specs} />
          <KpiCard tone="ok" label={t('rms.ecsv.kpi.pass')} value={summary.pass} />
          <KpiCard tone="warn" label={t('rms.ecsv.kpi.fail')} value={summary.fail} />
          <KpiCard tone="info" label={t('rms.ecsv.kpi.noSpec')} value={summary.noSpec} />
        </div>
      )}

      {tab === 'spec' && (
        <Toolbar
          left={
            <>
              <CimSelect
                value={scopeFilter}
                onChange={(v) => setScopeFilter(typeof v === 'string' ? v : '')}
                options={SCOPE_OPTIONS}
                placeholderKey="rms.ecsv.scope"
                width={150}
              />
              <CimInput
                value={refFilter}
                onChange={setRefFilter}
                placeholderKey="rms.ecsv.refId"
                width={180}
                mono
              />
            </>
          }
          right={
            <CimButton size="small" onClick={refreshAll}>
              {t('rms.common.refresh')}
            </CimButton>
          }
        />
      )}

      <CimTabs
        activeKey={tab}
        onChange={(k) => setTab(k as 'spec' | 'value')}
        items={[
          {
            key: 'spec',
            labelKey: 'rms.ecsv.specTab',
            children: (
              <DataTable<EcSvSpec>
                columns={specColumns}
                rows={specsRes.data ?? NO_SPECS}
                rowKey={(x) => x.id}
                loading={specsRes.loading}
                error={specsRes.error?.message}
                onRetry={specsRes.refresh}
                onRefresh={refreshAll}
                settingsKey="rms.ecsv.spec"
                scrollX="max-content"
                emptyTitleKey="rms.ecsv.emptySpec"
              />
            ),
          },
          {
            key: 'value',
            labelKey: 'rms.ecsv.valueTab',
            children: (
              <DataTable<EcSvValue>
                columns={valueColumns}
                rows={valuesRes.data ?? NO_VALUES}
                rowKey={(x) => x.id}
                loading={valuesRes.loading}
                error={valuesRes.error?.message}
                onRetry={valuesRes.refresh}
                onRefresh={valuesRes.refresh}
                settingsKey="rms.ecsv.value"
                scrollX="max-content"
                emptyTitleKey="rms.ecsv.emptyValue"
              />
            ),
          },
        ]}
      />

      {showSpec && (
        <SpecModal
          onClose={() => setShowSpec(false)}
          onDone={() => {
            setShowSpec(false);
            toast.success(t('rms.common.created'));
            refreshAll();
          }}
        />
      )}
      {showUpload && (
        <UploadModal
          onClose={() => setShowUpload(false)}
          onDone={() => {
            setShowUpload(false);
            refreshAll();
          }}
        />
      )}
    </Page>
  );
}

interface SpecForm {
  scope: 'RECIPE' | 'PRODUCT';
  refId: string;
  ecsvType: 'EC' | 'SV';
  svId: string;
  paramKey: string;
  specType: 'TARGET' | 'RANGE' | 'TOLERANCE';
  targetValue: string;
  rangeMin: string;
  rangeMax: string;
  tolerancePct: string;
  toleranceAbs: string;
  unit: string;
}

const EMPTY_SPEC: SpecForm = {
  scope: 'RECIPE',
  refId: '',
  ecsvType: 'EC',
  svId: '',
  paramKey: '',
  specType: 'TARGET',
  targetValue: '',
  rangeMin: '',
  rangeMax: '',
  tolerancePct: '',
  toleranceAbs: '',
  unit: '',
};

/** 新增 EC/SV 规格。字段按 `specType` 重建：切到 RANGE 后原来的目标值**不随 payload 发出**。 */
function SpecModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [form, setForm] = useState<SpecForm>(EMPTY_SPEC);
  const [busy, setBusy] = useState(false);

  const recipesRes = useResource(() => api.listRecipes());
  const recipes = recipesRes.data ?? NO_RECIPES;

  const patch = (p: Partial<SpecForm>) => setForm((prev) => ({ ...prev, ...p }));

  const fields = useMemo<Array<FormField<SpecForm>>>(() => {
    const base: Array<FormField<SpecForm>> = [
      { key: 'scope', labelKey: 'rms.ecsv.scope', kind: 'select', options: SCOPE_OPTIONS, allowClear: false },
      form.scope === 'RECIPE'
        ? {
            key: 'refId',
            labelKey: 'rms.ecsv.refId',
            kind: 'select',
            required: true,
            options: recipes.map((r) => ({ value: r.id, label: r.code, hint: r.name })),
          }
        : { key: 'refId', labelKey: 'rms.ecsv.refId', kind: 'text', required: true, mono: true },
      { key: 'ecsvType', labelKey: 'rms.ecsv.ecsvType', kind: 'select', options: ECSV_TYPE_OPTIONS, allowClear: false },
      { key: 'svId', labelKey: 'rms.ecsv.svId', kind: 'text', mono: true },
      { key: 'paramKey', labelKey: 'rms.ecsv.paramKey', kind: 'text', required: true, mono: true },
      { key: 'specType', labelKey: 'rms.spec.specType', kind: 'select', options: SPEC_TYPE_OPTIONS, allowClear: false },
    ];
    if (form.specType === 'RANGE') {
      base.push(
        { key: 'rangeMin', labelKey: 'rms.spec.rangeMin', kind: 'text', mono: true },
        { key: 'rangeMax', labelKey: 'rms.spec.rangeMax', kind: 'text', mono: true },
      );
    } else if (form.specType === 'TOLERANCE') {
      base.push(
        { key: 'targetValue', labelKey: 'rms.spec.target', kind: 'text', mono: true },
        { key: 'tolerancePct', labelKey: 'rms.spec.tolPct', kind: 'text', mono: true },
        { key: 'toleranceAbs', labelKey: 'rms.spec.tolAbs', kind: 'text', mono: true },
      );
    } else {
      base.push({ key: 'targetValue', labelKey: 'rms.spec.target', kind: 'text', mono: true });
    }
    base.push({ key: 'unit', labelKey: 'rms.spec.unit', kind: 'text' });
    return base;
  }, [form.scope, form.specType, recipes]);

  const submit = async () => {
    if (!form.refId.trim() || !form.paramKey.trim()) {
      toast.warning(t('rms.ecsv.requiredHint'));
      return;
    }
    setBusy(true);
    try {
      await api.saveEcSvSpec({
        scope: form.scope,
        refId: form.refId.trim(),
        ecsvType: form.ecsvType,
        svId: form.svId || null,
        paramKey: form.paramKey.trim(),
        specType: form.specType,
        targetValue: form.targetValue || null,
        rangeMin: form.rangeMin || null,
        rangeMax: form.rangeMax || null,
        tolerancePct: form.tolerancePct ? Number(form.tolerancePct) : null,
        toleranceAbs: form.toleranceAbs ? Number(form.toleranceAbs) : null,
        unit: form.unit || null,
        enabled: true,
      });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      setBusy(false);
      return;
    }
    setBusy(false);
    onDone();
  };

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.ecsv.addSpec"
      width={720}
      onOk={submit}
      okLoading={busy}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
    >
      <FormGrid<SpecForm> fields={fields} value={form} onChange={patch} columns={2} disabled={busy} />
    </CimModal>
  );
}

interface UploadForm {
  deviceId: string;
  scope: 'RECIPE' | 'PRODUCT';
  refId: string;
  ecsvType: 'EC' | 'SV';
  values: string;
}

const EMPTY_UPLOAD: UploadForm = {
  deviceId: '',
  scope: 'RECIPE',
  refId: '',
  ecsvType: 'EC',
  values: '{\n  "CHAMBER.TEMP": "70"\n}',
};

/** 上报 EC/SV 实测值（JSON）。解析失败在本地给出明确提示，不把语法错误丢给后端。 */
function UploadModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [form, setForm] = useState<UploadForm>(EMPTY_UPLOAD);
  const [busy, setBusy] = useState(false);

  const devicesRes = useResource(() => api.listDevices());
  const recipesRes = useResource(() => api.listRecipes());
  const devices = devicesRes.data ?? NO_DEVICES;
  const recipes = recipesRes.data ?? NO_RECIPES;

  const patch = (p: Partial<UploadForm>) => setForm((prev) => ({ ...prev, ...p }));

  const fields = useMemo<Array<FormField<UploadForm>>>(() => {
    const base: Array<FormField<UploadForm>> = [
      {
        key: 'deviceId',
        labelKey: 'rms.ecsv.device',
        kind: 'select',
        required: true,
        options: devices.map((d) => ({ value: d.id, label: d.code, hint: d.name })),
      },
      { key: 'ecsvType', labelKey: 'rms.ecsv.ecsvType', kind: 'select', options: ECSV_TYPE_OPTIONS, allowClear: false },
      { key: 'scope', labelKey: 'rms.ecsv.scope', kind: 'select', options: SCOPE_OPTIONS, allowClear: false },
      form.scope === 'RECIPE'
        ? {
            key: 'refId',
            labelKey: 'rms.ecsv.refId',
            kind: 'select',
            required: true,
            options: recipes.map((r) => ({ value: r.id, label: r.code, hint: r.name })),
          }
        : { key: 'refId', labelKey: 'rms.ecsv.refId', kind: 'text', required: true, mono: true },
      { key: 'values', labelKey: 'rms.ecsv.values', kind: 'textarea', rows: 8, span: 2 },
    ];
    return base;
  }, [devices, recipes, form.scope]);

  const submit = async () => {
    if (!form.deviceId || !form.refId.trim()) {
      toast.warning(t('rms.ecsv.requiredHint'));
      return;
    }
    let parsed: Record<string, string>;
    try {
      parsed = JSON.parse(form.values);
    } catch {
      toast.error(t('rms.ecsv.badJson'));
      return;
    }
    setBusy(true);
    try {
      const r = await api.uploadEcSv({ deviceId: form.deviceId, scope: form.scope, refId: form.refId.trim(), ecsvType: form.ecsvType, values: parsed });
      toast.success(`${t('rms.common.total')} ${r.total} · ${t('rms.common.fail')} ${r.fail}`);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      setBusy(false);
      return;
    }
    setBusy(false);
    onDone();
  };

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.ecsv.upload"
      width={720}
      onOk={submit}
      okLoading={busy}
      okKey="rms.ecsv.upload"
      cancelKey="rms.common.close"
    >
      <FormGrid<UploadForm> fields={fields} value={form} onChange={patch} columns={2} disabled={busy} />
    </CimModal>
  );
}
