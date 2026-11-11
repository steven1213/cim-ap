import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimModal,
  CimSwitch,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  PageHead,
  Panel,
  Toolbar,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  CimButton,
  InlineAlert,
  KpiCard,

  KpiGrid,
  CimField,
  CimInput,
  StatusTag,
} from '@cim/ui';
import type { StatusTone } from '@cim/theme';
import { BatchDownloadModal, BatchHoldModal } from '@/components/BatchOpsModal';
import * as api from '@/lib/rmsApi';
import { INTEGRATION_OPERATE } from '@/lib/permCodes';
import type {
  AlarmView,
  Device,
  DeviceType,
  DownloadJob,
  DownloadReceipt,
  EquipmentEvent,
  HoldView,
  InventoryReport,
  InventoryRow,
  InventorySummary,
  OnlineCompareResult,
  Recipe,
  RunCheckResult,
  ToolRecipeMap,
} from '@/types';

const NO_HOLDS: HoldView[] = [];
const NO_ALARMS: AlarmView[] = [];
const NO_DEVICES: Device[] = [];
const NO_TYPES: DeviceType[] = [];
const NO_RECIPES: Recipe[] = [];
const NO_PPID: ToolRecipeMap[] = [];
const NO_JOBS: DownloadJob[] = [];
const NO_EVENTS: EquipmentEvent[] = [];
const NO_INV: InventoryRow[] = [];

const MODE_OPTIONS = ['FULL_BODY', 'PARAMETER', 'STRUCTURE'].map((m) => ({
  value: m,
  labelKey: `rms.compare.mode.${m}`,
}));

const BASELINE_OPTIONS = [
  { value: 'ACTIVE', labelKey: 'rms.integration.sim.baselineActive' },
  { value: 'GOLDEN', labelKey: 'rms.integration.sim.baselineGolden' },
];

const DL_MODE_OPTIONS = ['IMMEDIATE', 'ON_IDLE', 'BATCH'].map((m) => ({ value: m, label: m }));

const EVT_TYPE_OPTIONS = [
  { value: 'PP_CHANGE', label: 'PP_CHANGE' },
  { value: 'PP_VERIFICATION_FAILED', label: 'PP_VERIFICATION_FAILED' },
];

const CHANGE_TYPE_OPTIONS = ['CREATED', 'EDITED', 'DELETED'].map((v) => ({ value: v, label: v }));

/** 阶段机的合法下一步（与后端 DownloadStage.canMoveTo 对齐）。 */
function nextStage(stage: string): string | null {
  switch (stage) {
    case 'QUEUED':
      return 'GRANTED';
    case 'GRANTED':
      return 'SENT_TO_TOOL';
    case 'SENT_TO_TOOL':
      return 'TOOL_ACKED';
    case 'TOOL_ACKED':
      return 'VERIFIED';
    case 'FAILED':
      return 'QUEUED'; // 失败重试入队
    default:
      return null; // VERIFIED 为终态
  }
}

/** 核对结论 → 语义档。 */
function invTone(r: string): StatusTone {
  if (r === 'ORPHAN') return 'warn';
  if (r === 'DRIFT') return 'err';
  return 'ok';
}

/** HASH 缩略展示（前后各 8 位）。 */
function shortHash(h: string | null | undefined): string {
  if (!h) return '—';
  return h.length <= 20 ? h : `${h.slice(0, 8)}…${h.slice(-8)}`;
}

/**
 * 集成监控页（IF-E1~M1/A1）：Hold 存量 + 报警 + 接口联调 + 批量操作（W9）。
 *
 * <p>迁移要点：</p>
 * <ul>
 *   <li>基础字典（设备 / 机型 / 配方）在页面顶层取一次，各面板共享。
 *       旧实现每个面板各自 `listDevices()` + `listRecipes()`，进页面要打 10 次同样的请求。</li>
 *   <li>`window.prompt` 收「放行原因 / 失败原因」→ 带 textarea 的弹窗
 *       （prompt 阻塞主线程、不能多行、取消与空串同义）。</li>
 *   <li>六张表全部换成 `DataTable flat`（嵌在 `Panel` 里不画第二层框），
 *       过滤开关从裸 checkbox 换成 `CimSwitch`。</li>
 * </ul>
 */
export default function IntegrationPage() {
  const { t } = useTranslation();
  const toast = useToast();

  const [activeOnly, setActiveOnly] = useState(true);
  const [unackOnly, setUnackOnly] = useState(false);
  const [showBatchHold, setShowBatchHold] = useState(false);
  const [showBatchDownload, setShowBatchDownload] = useState(false);
  const [releaseRow, setReleaseRow] = useState<HoldView | null>(null);

  const dictRes = useResource(async () => {
    const [devices, types, recipes] = await Promise.all([
      api.listDevices(),
      api.listDeviceTypes(),
      api.listRecipes(),
    ]);
    return { devices, types, recipes };
  });
  const devices = dictRes.data?.devices ?? NO_DEVICES;
  const types = dictRes.data?.types ?? NO_TYPES;
  const recipes = dictRes.data?.recipes ?? NO_RECIPES;

  const holdsRes = useResource(() => api.listHolds({ activeOnly }), {
    deps: [activeOnly],
    keepPreviousData: true,
  });
  const alarmsRes = useResource(() => api.listAlarms({ unackOnly }), {
    deps: [unackOnly],
    keepPreviousData: true,
  });

  const refreshAll = () => {
    holdsRes.refresh();
    alarmsRes.refresh();
  };

  const ack = async (a: AlarmView) => {
    try {
      await api.ackAlarm(a.id);
      toast.success(t('rms.integration.acked'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    alarmsRes.refresh();
  };

  const holds = holdsRes.data ?? NO_HOLDS;
  const alarms = alarmsRes.data ?? NO_ALARMS;

  const holdColumns = useMemo<Array<DataTableColumn<HoldView>>>(
    () => [
      {
        key: 'deviceCode',
        titleKey: 'rms.integration.device',
        mono: true,
        width: 160,
        render: (x) => x.deviceCode ?? x.deviceId,
      },
      {
        key: 'recipeCode',
        titleKey: 'rms.integration.recipe',
        mono: true,
        width: 170,
        render: (x) => x.recipeCode ?? t('rms.common.dash'),
      },
      { key: 'source', titleKey: 'rms.integration.source', width: 130 },
      {
        key: 'reason',
        titleKey: 'rms.integration.reason',
        ellipsis: true,
        render: (x) => <span className="muted">{x.reason}</span>,
      },
      {
        key: 'active',
        titleKey: 'rms.integration.active',
        width: 100,
        render: (x) => (
          <CimTag size="small" tone={x.active ? 'err' : 'ok'}>
            {x.active ? 'HOLD' : 'OK'}
          </CimTag>
        ),
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 110,
        render: (x) =>
          x.active ? (
            <Can perm={INTEGRATION_OPERATE}>
              <CimButton size="small" onClick={() => setReleaseRow(x)}>
                {t('rms.integration.release')}
              </CimButton>
            </Can>
          ) : null,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  const alarmColumns = useMemo<Array<DataTableColumn<AlarmView>>>(
    () => [
      { key: 'alarmCode', titleKey: 'rms.integration.alarmCode', mono: true, width: 150 },
      {
        key: 'severity',
        titleKey: 'rms.integration.severity',
        width: 110,
        render: (x) => (
          <CimTag
            size="small"
            tone={x.severity === 'CRITICAL' ? 'err' : x.severity === 'MAJOR' ? 'warn' : 'idle'}
          >
            {x.severity}
          </CimTag>
        ),
      },
      {
        key: 'deviceId',
        titleKey: 'rms.integration.device',
        mono: true,
        width: 140,
        render: (x) => x.deviceId ?? '—',
      },
      {
        key: 'lotId',
        titleKey: 'rms.integration.lotId',
        mono: true,
        width: 130,
        render: (x) => x.lotId ?? '—',
      },
      {
        key: 'message',
        titleKey: 'rms.integration.message',
        ellipsis: true,
        render: (x) => <span className="muted">{x.message}</span>,
      },
      {
        key: 'occurredAt',
        titleKey: 'rms.integration.occurredAt',
        width: 170,
        render: (x) => <span className="muted">{x.occurredAt}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 110,
        render: (x) =>
          x.acknowledged ? (
            <CimTag size="small" tone="ok">
              {t('rms.integration.acknowledged')}
            </CimTag>
          ) : (
            <Can perm={INTEGRATION_OPERATE}>
              <CimButton size="small" onClick={() => void ack(x)}>
                {t('rms.integration.ack')}
              </CimButton>
            </Can>
          ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.integration.title"
        subtitleKey="rms.integration.sub"
        actions={
          <>
            <Can perm={INTEGRATION_OPERATE}>
              <CimButton size="small" onClick={() => setShowBatchHold(true)}>
                {t('rms.batch.holdBtn')}
              </CimButton>
              <CimButton size="small" onClick={() => setShowBatchDownload(true)}>
                {t('rms.batch.downloadBtn')}
              </CimButton>
            </Can>
            <CimButton size="small" onClick={refreshAll}>
              {t('rms.common.refresh')}
            </CimButton>
          </>
        }
      />

      {showBatchHold ? (
        <BatchHoldModal
          devices={devices.map((d) => ({ id: d.id, code: d.code, name: d.name }))}
          types={types}
          recipes={recipes.map((r) => ({ id: r.id, code: r.code, name: r.name }))}
          onClose={() => setShowBatchHold(false)}
          onDone={refreshAll}
        />
      ) : null}
      {showBatchDownload ? (
        <BatchDownloadModal
          devices={devices.map((d) => ({ id: d.id, code: d.code, name: d.name }))}
          recipes={recipes.map((r) => ({ id: r.id, code: r.code, name: r.name }))}
          onClose={() => setShowBatchDownload(false)}
          onDone={refreshAll}
        />
      ) : null}

      <Panel
        titleKey="rms.integration.holds"
        flush
        actions={
          <label className="check">
            <CimSwitch size="small" checked={activeOnly} onChange={setActiveOnly} />
            {t('rms.integration.onlyActive')}
          </label>
        }
      >
        <DataTable<HoldView>
          flat
          columns={holdColumns}
          rows={holds}
          rowKey={(x) => x.id}
          loading={holdsRes.loading}
          error={holdsRes.error?.message}
          onRetry={holdsRes.refresh}
          scrollX="max-content"
          emptyTitleKey="rms.integration.emptyHolds"
        />
      </Panel>

      <Panel
        titleKey="rms.integration.alarms"
        flush
        actions={
          <label className="check">
            <CimSwitch size="small" checked={unackOnly} onChange={setUnackOnly} />
            {t('rms.integration.onlyUnack')}
          </label>
        }
      >
        <DataTable<AlarmView>
          flat
          columns={alarmColumns}
          rows={alarms}
          rowKey={(x) => x.id}
          loading={alarmsRes.loading}
          error={alarmsRes.error?.message}
          onRetry={alarmsRes.refresh}
          scrollX="max-content"
          emptyTitleKey="rms.integration.emptyAlarms"
        />
      </Panel>

      <PpidPanel devices={devices} types={types} recipes={recipes} />
      <DownloadPanel devices={devices} recipes={recipes} onChanged={refreshAll} />
      <GemEventPanel devices={devices} />
      <InventoryPanel devices={devices} />
      <SimPanel devices={devices} recipes={recipes} onDone={refreshAll} />

      {releaseRow ? (
        <ReasonModal
          titleKey="rms.integration.release"
          labelKey="rms.integration.releaseReason"
          okKey="rms.integration.release"
          onClose={() => setReleaseRow(null)}
          onSubmit={async (reason) => {
            await api.releaseHold(releaseRow.id, reason);
            toast.success(t('rms.integration.released'));
            holdsRes.refresh();
          }}
        />
      ) : null}

    </Page>
  );
}

/** 机台 PPID ↔ 配方映射（D7 / IF-E2）：EAP 侧只认 PPID，映射由 RMS 维护。 */
interface PpidForm {
  scope: 'DEVICE' | 'TYPE';
  deviceId: string;
  typeId: string;
  ppid: string;
  recipeId: string;
}

function PpidPanel({
  devices,
  types,
  recipes,
}: {
  devices: Device[];
  types: DeviceType[];
  recipes: Recipe[];
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const [form, setForm] = useState<PpidForm>({
    scope: 'DEVICE',
    deviceId: '',
    typeId: '',
    ppid: '',
    recipeId: '',
  });
  const [busy, setBusy] = useState(false);

  const mapsRes = useResource(() => api.listPpidMaps());
  const rows = mapsRes.data ?? NO_PPID;

  // 首次字典回来后补默认值（保持旧实现的「默认选第一条」手感）
  const deviceId = form.deviceId || devices[0]?.id || '';
  const typeId = form.typeId || types[0]?.id || '';
  const recipeId = form.recipeId || recipes[0]?.id || '';

  const fields = useMemo<Array<FormField<PpidForm>>>(
    () => [
      {
        key: 'scope',
        labelKey: 'rms.integration.ppid.scope',
        kind: 'select',
        allowClear: false,
        options: [
          { value: 'DEVICE', labelKey: 'rms.integration.ppid.scopeDevice' },
          { value: 'TYPE', labelKey: 'rms.integration.ppid.scopeType' },
        ],
      },
      form.scope === 'DEVICE'
        ? {
            key: 'deviceId',
            labelKey: 'rms.integration.device',
            kind: 'select',
            options: devices.map((d) => ({ value: d.id, label: d.code, hint: d.name })),
          }
        : {
            key: 'typeId',
            labelKey: 'rms.integration.ppid.deviceType',
            kind: 'select',
            options: types.map((d) => ({ value: d.id, label: d.code, hint: d.name })),
          },
      {
        key: 'ppid',
        labelKey: 'rms.integration.ppid.ppidOnTool',
        kind: 'text',
        mono: true,
        required: true,
        placeholderKey: 'rms.integration.ppid.ppidPh',
      },
      {
        key: 'recipeId',
        labelKey: 'rms.integration.recipe',
        kind: 'select',
        options: recipes.map((r) => ({ value: r.id, label: r.code, hint: r.name })),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [devices, types, recipes, form.scope],
  );

  const add = async () => {
    if (!form.ppid.trim() || !recipeId) {
      toast.warning(t('rms.integration.ppid.required'));
      return;
    }
    setBusy(true);
    try {
      await api.registerPpidMap({
        deviceId: form.scope === 'DEVICE' ? deviceId : null,
        deviceTypeId: form.scope === 'TYPE' ? typeId : null,
        ppidOnTool: form.ppid.trim(),
        recipeId,
      });
      setForm((prev) => ({ ...prev, ppid: '' }));
      toast.success(t('rms.common.created'));
      mapsRes.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id: string) => {
    const ok = await confirm({
      titleKey: 'rms.common.delete',
      contentKey: 'rms.common.confirmDelete',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deletePpidMap(id);
      toast.success(t('rms.common.deleted'));
      mapsRes.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const columns = useMemo<Array<DataTableColumn<ToolRecipeMap>>>(
    () => [
      {
        key: 'scope',
        titleKey: 'rms.integration.ppid.scope',
        width: 170,
        render: (m) => (
          <>
            <CimTag size="small">
              {m.deviceId
                ? t('rms.integration.ppid.scopeDevice')
                : t('rms.integration.ppid.scopeType')}
            </CimTag>
            <span className="cim-mono" style={{ marginLeft: 8 }}>
              {m.deviceCode ?? m.deviceTypeName ?? '—'}
            </span>
          </>
        ),
      },
      { key: 'ppidOnTool', titleKey: 'rms.integration.ppid.ppidOnTool', mono: true, width: 160 },
      {
        key: 'recipeCode',
        titleKey: 'rms.integration.recipe',
        mono: true,
        width: 170,
        render: (m) => m.recipeCode ?? m.recipeId,
      },
      {
        key: 'createTime',
        titleKey: 'rms.common.createTime',
        width: 170,
        render: (m) => <span className="muted">{m.createTime ?? '—'}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 90,
        render: (m) => (
          <Can perm={INTEGRATION_OPERATE}>
            <CimButton size="small" onClick={() => void remove(m.id)}>
              {t('rms.common.delete')}
            </CimButton>
          </Can>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Panel titleKey="rms.integration.ppid.title" subKey="rms.integration.ppid.sub">
      <FormGrid<PpidForm>
        fields={fields}
        value={{ ...form, deviceId, typeId, recipeId }}
        onChange={(p) => setForm((prev) => ({ ...prev, ...p }))}
        columns={4}
        disabled={busy}
      />
      <Toolbar
        left={
          <Can perm={INTEGRATION_OPERATE}>
            <CimButton type="primary" size="small" disabled={busy} onClick={() => void add()}>
              {t('rms.integration.ppid.register')}
            </CimButton>
          </Can>
        }
      />
      <DataTable<ToolRecipeMap>
        flat
        columns={columns}
        rows={rows}
        rowKey={(m) => m.id}
        loading={mapsRes.loading}
        error={mapsRes.error?.message}
        onRetry={mapsRes.refresh}
        onRefresh={mapsRes.refresh}
        scrollX="max-content"
        emptyTitleKey="rms.integration.ppid.empty"
      />
    </Panel>
  );
}

/** 集成调试台：以 EAP/MES 视角发起 IF-E3 在线比对与 IF-M1 Run 校验，支持三种比对方式。 */
interface SimForm {
  toolId: string;
  recipeUid: string;
  cmpMode: string;
  baselineType: string;
  paramSnapshot: string;
  verifyRequired: boolean;
}

function SimPanel({
  devices,
  recipes,
  onDone,
}: {
  devices: Device[];
  recipes: Recipe[];
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [form, setForm] = useState<SimForm>({
    toolId: '',
    recipeUid: '',
    cmpMode: 'FULL_BODY',
    baselineType: 'ACTIVE',
    paramSnapshot: '',
    verifyRequired: true,
  });
  const [cmp, setCmp] = useState<OnlineCompareResult | null>(null);
  const [chk, setChk] = useState<RunCheckResult | null>(null);
  const [busy, setBusy] = useState(false);

  const toolId = form.toolId || devices[0]?.id || '';
  const recipeUid = form.recipeUid || recipes[0]?.id || '';

  const fields: Array<FormField<SimForm>> = [
    {
      key: 'toolId',
      labelKey: 'rms.integration.device',
      kind: 'select',
      options: devices.map((d) => ({ value: d.id, label: d.code, hint: d.name })),
    },
    {
      key: 'recipeUid',
      labelKey: 'rms.integration.recipe',
      kind: 'select',
      options: recipes.map((r) => ({ value: r.id, label: r.code, hint: r.name })),
    },
    { key: 'cmpMode', labelKey: 'rms.compare.mode', kind: 'select', options: MODE_OPTIONS, allowClear: false },
    {
      key: 'baselineType',
      labelKey: 'rms.integration.sim.baseline',
      kind: 'select',
      options: BASELINE_OPTIONS,
      allowClear: false,
    },
    {
      key: 'paramSnapshot',
      labelKey: 'rms.integration.sim.snapshot',
      kind: 'textarea',
      rows: 3,
      span: 2,
      placeholderKey: 'rms.integration.sim.snapshotPh',
    },
    { key: 'verifyRequired', labelKey: 'rms.integration.sim.verifyRequired', kind: 'switch' },
  ];

  const doCheck = async () => {
    setBusy(true);
    setChk(null);
    try {
      setChk(
        await api.runCheck({
          toolId,
          recipeUid,
          versionPolicy: 'LATEST_ACTIVE',
          verifyRequired: form.verifyRequired,
        }),
      );
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const doCompare = async () => {
    setBusy(true);
    setCmp(null);
    try {
      const r = await api.onlineCompare({
        toolId,
        recipeUid,
        cmpMode: form.cmpMode,
        baselineType: form.baselineType,
        paramSnapshot: form.paramSnapshot.trim() || null,
        correlationId: `sim-${Date.now()}`,
      });
      setCmp(r);
      onDone();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const diffColumns = useMemo<
    Array<DataTableColumn<NonNullable<OnlineCompareResult['diffSummary']>[number]>>
  >(
    () => [
      { key: 'paramKey', titleKey: 'rms.spec.paramKey', mono: true, width: 200 },
      {
        key: 'expected',
        titleKey: 'rms.compare.expected',
        mono: true,
        render: (c) => c.expected ?? '—',
      },
      { key: 'actual', titleKey: 'rms.compare.actual', mono: true, render: (c) => c.actual ?? '—' },
      {
        key: 'diffType',
        titleKey: 'rms.compare.diffType',
        width: 150,
        render: (c) => c.diffType ?? '—',
      },
    ],
    [t],
  );

  return (
    <Panel titleKey="rms.integration.sim.title" subKey="rms.integration.sim.sub">
      <FormGrid<SimForm>
        fields={fields}
        value={{ ...form, toolId, recipeUid }}
        onChange={(p) => setForm((prev) => ({ ...prev, ...p }))}
        columns={2}
        disabled={busy}
      />

      <Toolbar
        left={
          <>
            <CimButton size="small" disabled={busy} onClick={() => void doCheck()}>
              {t('rms.integration.sim.runCheck')}
            </CimButton>
            <CimButton type="primary" size="small" disabled={busy} onClick={() => void doCompare()}>
              {busy ? t('rms.common.loading') : t('rms.integration.sim.onlineCompare')}
            </CimButton>
          </>
        }
      />

      {chk ? (
        <div className="cim-form-grid" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
          <CimField label={t('rms.integration.sim.checkResult')}>
            <div>
              <CimTag size="small" tone={chk.checkResult === 'QUALIFIED' ? 'ok' : 'err'}>
                {chk.checkResult}
              </CimTag>
            </div>
          </CimField>
          <CimField label={t('rms.integration.sim.activeVersion')}>
            <div className="cim-mono">{chk.activeVersion ?? '—'}</div>
          </CimField>
          <CimField label={t('rms.integration.reason')}>
            <div className="muted">{chk.reason ?? '—'}</div>
          </CimField>
        </div>
      ) : null}

      {cmp ? (
        <div style={{ marginTop: 10 }}>
          <div>
            <CimTag
              size="small"
              tone={cmp.cmpResult === 'PASS' ? 'ok' : cmp.cmpResult === 'BYPASSED' ? 'warn' : 'err'}
            >
              {cmp.cmpResult}
            </CimTag>
            <span className="muted">
              {' · '}
              {t('rms.integration.sim.baselineVersion')} v{cmp.baselineVersion ?? '—'}
              {' · '}
              {cmp.durationMs} ms
            </span>
            {cmp.holdApplied ? (
              <span style={{ marginLeft: 8 }}>
                <CimTag size="small" tone="err">
                  {t('rms.integration.sim.holdApplied')}
                </CimTag>
              </span>
            ) : null}
            {cmp.alarmRaised ? (
              <span style={{ marginLeft: 8 }}>
                <CimTag size="small" tone="warn">
                  {t('rms.integration.sim.alarmRaised')}
                </CimTag>
              </span>
            ) : null}
          </div>
          {cmp.detail ? (
            <InlineAlert tone="warn" style={{ marginTop: 8 }}>{cmp.detail}</InlineAlert>
          ) : null}
          {cmp.diffSummary.length > 0 ? (
            <div style={{ marginTop: 8 }}>
              <DataTable
                flat
                columns={diffColumns}
                rows={cmp.diffSummary}
                rowKey={(c) => c.paramKey}
                rowClassName={() => 'cim-row-warn'}
                scrollX="max-content"
                showCount={false}
              />
            </div>
          ) : null}
        </div>
      ) : null}
    </Panel>
  );
}

interface DownloadForm {
  toolId: string;
  recipeUid: string;
  ppidOnTool: string;
  downloadMode: string;
}

/**
 * IF-E2 下发任务看板（Req §5.3 RecipeDownloadProgress）。
 *
 * <p>下发是异步长流程：受理后落一条任务，EAP 逐步回执 GRANTED→SENT_TO_TOOL→TOOL_ACKED，
 * 最终由回读 HASH 比对判定 VERIFIED（不一致则 FAILED 并报 RMS_DRIFT）。</p>
 */
function DownloadPanel({
  devices,
  recipes,
  onChanged,
}: {
  devices: Device[];
  recipes: Recipe[];
  onChanged: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [form, setForm] = useState<DownloadForm>({
    toolId: '',
    recipeUid: '',
    ppidOnTool: '',
    downloadMode: 'IMMEDIATE',
  });
  const [pendingOnly, setPendingOnly] = useState(false);
  const [receipt, setReceipt] = useState<DownloadReceipt | null>(null);
  const [failJob, setFailJob] = useState<DownloadJob | null>(null);
  const [busy, setBusy] = useState(false);

  const jobsRes = useResource(() => api.listDownloads({ pendingOnly }), {
    deps: [pendingOnly],
    keepPreviousData: true,
  });
  const rows = jobsRes.data ?? NO_JOBS;

  const toolId = form.toolId || devices[0]?.id || '';
  const recipeUid = form.recipeUid || recipes[0]?.id || '';

  const fields: Array<FormField<DownloadForm>> = [
    {
      key: 'toolId',
      labelKey: 'rms.integration.device',
      kind: 'select',
      options: devices.map((d) => ({ value: d.id, label: d.code, hint: d.name })),
    },
    {
      key: 'recipeUid',
      labelKey: 'rms.integration.recipe',
      kind: 'select',
      options: recipes.map((r) => ({ value: r.id, label: r.code, hint: r.name })),
    },
    {
      key: 'ppidOnTool',
      labelKey: 'rms.integration.dl.ppidOnTool',
      kind: 'text',
      mono: true,
      placeholderKey: 'rms.integration.ppid.ppidPh',
    },
    {
      key: 'downloadMode',
      labelKey: 'rms.integration.dl.mode',
      kind: 'select',
      options: DL_MODE_OPTIONS,
      allowClear: false,
    },
  ];

  const issue = async () => {
    setBusy(true);
    setReceipt(null);
    try {
      const r = await api.downloadRecipe({
        toolId,
        recipeUid,
        ppidOnTool: form.ppidOnTool.trim() || null,
        downloadMode: form.downloadMode as DownloadJob['downloadMode'],
        requestor: 'console',
        correlationId: `dl-${Date.now()}`,
      });
      setReceipt(r);
      jobsRes.refresh();
      onChanged();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const advance = async (job: DownloadJob, mismatch = false) => {
    const next = nextStage(job.stage);
    if (!next) return;
    setBusy(true);
    try {
      await api.ackDownload(job.correlationId, {
        stage: next,
        toolAck: next === 'SENT_TO_TOOL' || next === 'TOOL_ACKED' ? 'ACK' : null,
        // 回读校验：正常用下发时的 bodyHash；mismatch 传错值以验证漂移判定
        readbackHash: next === 'VERIFIED' ? (mismatch ? 'deadbeef' : job.bodyHash) : null,
      });
      jobsRes.refresh();
      onChanged();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<DownloadJob>>>(
    () => [
      { key: 'correlationId', titleKey: 'rms.integration.dl.correlationId', mono: true, width: 190 },
      { key: 'deviceId', titleKey: 'rms.integration.device', mono: true, width: 140 },
      {
        key: 'ppidOnTool',
        titleKey: 'rms.integration.dl.ppidOnTool',
        mono: true,
        width: 140,
        render: (x) => x.ppidOnTool ?? '—',
      },
      { key: 'downloadMode', titleKey: 'rms.integration.dl.mode', mono: true, width: 120 },
      {
        key: 'stage',
        titleKey: 'rms.integration.dl.stage',
        width: 140,
        render: (x) => (
          <StatusTag size="small" dict="downloadStage" value={x.stage} />
        ),
      },
      {
        key: 'toolAck',
        titleKey: 'rms.integration.dl.toolAck',
        mono: true,
        width: 110,
        render: (x) => x.toolAck ?? '—',
      },
      {
        key: 'errorDesc',
        titleKey: 'rms.integration.dl.error',
        ellipsis: true,
        render: (x) => <span className="muted">{x.errorDesc ?? '—'}</span>,
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 280,
        render: (x) => {
          const next = nextStage(x.stage);
          return (
            <Can perm={INTEGRATION_OPERATE}>
              <span className="cell-actions">
                {next ? (
                  <CimButton size="small" disabled={busy} onClick={() => void advance(x)}>
                    {x.stage === 'FAILED' ? t('rms.integration.dl.retry') : `→ ${next}`}
                  </CimButton>
                ) : null}
                {x.stage === 'TOOL_ACKED' ? (
                  <CimButton
                    size="small"
                    disabled={busy}
                    onClick={() => void advance(x, true)}
                  >
                    {t('rms.integration.dl.readbackMismatch')}
                  </CimButton>
                ) : null}
                {!x.terminal ? (
                  <CimButton size="small" disabled={busy} onClick={() => setFailJob(x)}>
                    {t('rms.integration.dl.fail')}
                  </CimButton>
                ) : null}
              </span>
            </Can>
          );
        },
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, busy],
  );

  return (
    <Panel titleKey="rms.integration.dl.title" subKey="rms.integration.dl.sub">
      {receipt ? (
        <InlineAlert tone={(receipt.result === 'ACCEPTED' ? 'ok' : 'err')} style={{ marginBottom: 10 }}>{receipt.result} · {t('rms.integration.dl.stage')} {receipt.stage}
            {receipt.errorDesc ? ` · ${receipt.errorDesc}` : ''}</InlineAlert>
      ) : null}

      <FormGrid<DownloadForm>
        fields={fields}
        value={{ ...form, toolId, recipeUid }}
        onChange={(p) => setForm((prev) => ({ ...prev, ...p }))}
        columns={4}
        disabled={busy}
      />

      <Toolbar
        left={
          <Can perm={INTEGRATION_OPERATE}>
            <CimButton
              type="primary"
              size="small"
              disabled={busy || !toolId || !recipeUid}
              onClick={() => void issue()}
            >
              {t('rms.integration.dl.issue')}
            </CimButton>
          </Can>
        }
        right={
          <label className="check">
            <CimSwitch size="small" checked={pendingOnly} onChange={setPendingOnly} />
            {t('rms.integration.dl.pendingOnly')}
          </label>
        }
      />

      <DataTable<DownloadJob>
        flat
        columns={columns}
        rows={rows}
        rowKey={(x) => x.id}
        loading={jobsRes.loading}
        error={jobsRes.error?.message}
        onRetry={jobsRes.refresh}
        onRefresh={jobsRes.refresh}
        scrollX="max-content"
        emptyTitleKey="rms.integration.dl.empty"
      />

      {failJob ? (
        <ReasonModal
          titleKey="rms.integration.dl.fail"
          labelKey="rms.integration.dl.reasonPrompt"
          okKey="rms.integration.dl.fail"
          danger
          onClose={() => setFailJob(null)}
          onSubmit={async (reason) => {
            await api.ackDownload(failJob.correlationId, { stage: 'FAILED', errorDesc: reason });
            jobsRes.refresh();
            onChanged();
          }}
        />
      ) : null}
    </Panel>
  );
}

interface GemForm {
  toolId: string;
  eventType: 'PP_CHANGE' | 'PP_VERIFICATION_FAILED';
  ppid: string;
  changeType: 'CREATED' | 'EDITED' | 'DELETED';
  errorDesc: string;
  holdOnDrift: boolean;
}

/**
 * GEM 设备事件面板（SEMI E30）：机台主动上报的配方变更（PPChange）与机台校验失败（PPVerificationFailed）。
 *
 * <p>事件驱动与「巡检」互补——工程师在机台改完配方，主机几秒内即知，不必等下一轮巡检；
 * 校验失败还会联动把在途下发任务判 FAILED。</p>
 */
function GemEventPanel({ devices }: { devices: Device[] }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [form, setForm] = useState<GemForm>({
    toolId: '',
    eventType: 'PP_CHANGE',
    ppid: '',
    changeType: 'EDITED',
    errorDesc: '',
    holdOnDrift: true,
  });
  const [last, setLast] = useState<EquipmentEvent | null>(null);
  const [busy, setBusy] = useState(false);

  const eventsRes = useResource(() => api.listEquipmentEvents());
  const rows = eventsRes.data ?? NO_EVENTS;
  const toolId = form.toolId || devices[0]?.id || '';

  const fields: Array<FormField<GemForm>> = [
    {
      key: 'toolId',
      labelKey: 'rms.integration.device',
      kind: 'select',
      options: devices.map((d) => ({ value: d.id, label: d.code, hint: d.name })),
    },
    {
      key: 'eventType',
      labelKey: 'rms.integration.evt.eventType',
      kind: 'select',
      options: EVT_TYPE_OPTIONS,
      allowClear: false,
    },
    {
      key: 'ppid',
      labelKey: 'rms.integration.evt.ppid',
      kind: 'text',
      mono: true,
      required: true,
      placeholder: 'ETCH_STD_01',
    },
    form.eventType === 'PP_CHANGE'
      ? {
          key: 'changeType',
          labelKey: 'rms.integration.evt.changeType',
          kind: 'select',
          options: CHANGE_TYPE_OPTIONS,
          allowClear: false,
        }
      : {
          key: 'errorDesc',
          labelKey: 'rms.integration.evt.ppError',
          kind: 'text',
          placeholder: 'STEP3 out of range',
        },
    { key: 'holdOnDrift', labelKey: 'rms.integration.evt.holdOnDrift', kind: 'switch' },
  ];

  const post = async () => {
    if (!toolId || !form.ppid.trim()) {
      toast.warning(t('rms.integration.evt.required'));
      return;
    }
    setBusy(true);
    setLast(null);
    try {
      const r = await api.postEquipmentEvent({
        eventId: `evt-${Date.now()}`,
        toolId,
        eventType: form.eventType,
        ppid: form.ppid.trim(),
        changeType: form.eventType === 'PP_CHANGE' ? form.changeType : null,
        errorDesc:
          form.eventType === 'PP_VERIFICATION_FAILED' ? form.errorDesc.trim() || null : null,
        holdOnDrift: form.holdOnDrift,
        correlationId: `corr-evt-${Date.now()}`,
      });
      setLast(r);
      eventsRes.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<EquipmentEvent>>>(
    () => [
      {
        key: 'deviceCode',
        titleKey: 'rms.integration.device',
        mono: true,
        width: 150,
        render: (x) => x.deviceCode ?? x.toolId,
      },
      { key: 'eventType', titleKey: 'rms.integration.evt.eventType', mono: true, width: 190 },
      {
        key: 'ppid',
        titleKey: 'rms.integration.evt.ppid',
        mono: true,
        width: 150,
        render: (x) => x.ppid ?? t('rms.common.dash'),
      },
      {
        key: 'changeType',
        titleKey: 'rms.integration.evt.changeType',
        mono: true,
        width: 120,
        render: (x) => x.changeType ?? t('rms.common.dash'),
      },
      {
        key: 'flags',
        titleKey: 'rms.integration.evt.flags',
        width: 150,
        render: (x) => (
          <>
            <CimTag size="small" tone={x.driftDetected ? 'err' : x.alarmRaised ? 'warn' : 'ok'}>
              {x.driftDetected ? 'DRIFT' : x.alarmRaised ? 'ALARM' : 'OK'}
            </CimTag>
            {x.holdApplied ? (
              <CimTag size="small" tone="warn">
                HOLD
              </CimTag>
            ) : null}
          </>
        ),
      },
      {
        key: 'handlingResult',
        titleKey: 'rms.integration.evt.handlingResult',
        ellipsis: true,
        render: (x) => <span className="muted">{x.handlingResult ?? t('rms.common.dash')}</span>,
      },
      {
        key: 'occurredAt',
        titleKey: 'rms.integration.evt.occurredAt',
        width: 170,
        render: (x) => <span className="muted">{x.occurredAt ?? t('rms.common.dash')}</span>,
      },
    ],
    [t],
  );

  return (
    <Panel titleKey="rms.integration.evt.title" subKey="rms.integration.evt.sub">
      {last ? (
        <InlineAlert tone={(last.alarmRaised ? 'err' : 'ok')} style={{ marginBottom: 10 }}>{last.eventType} · {last.ppid ?? t('rms.common.dash')} · {last.handlingResult}
            {last.driftDetected ? ` · ${t('rms.integration.evt.drift')}` : ''}
            {last.holdApplied ? ` · ${t('rms.integration.evt.held')}` : ''}</InlineAlert>
      ) : null}

      <FormGrid<GemForm>
        fields={fields}
        value={{ ...form, toolId }}
        onChange={(p) => setForm((prev) => ({ ...prev, ...p }))}
        columns={3}
        disabled={busy}
      />

      <Toolbar
        left={
          <Can perm={INTEGRATION_OPERATE}>
            <CimButton type="primary" size="small" disabled={busy} onClick={() => void post()}>
              {t('rms.integration.evt.report')}
            </CimButton>
          </Can>
        }
      />

      <DataTable<EquipmentEvent>
        flat
        columns={columns}
        rows={rows}
        rowKey={(x) => x.id}
        loading={eventsRes.loading}
        error={eventsRes.error?.message}
        onRetry={eventsRes.refresh}
        onRefresh={eventsRes.refresh}
        scrollX="max-content"
        emptyTitleKey="rms.integration.evt.empty"
      />
    </Panel>
  );
}

interface InvForm {
  toolId: string;
  items: string;
}

/**
 * 机台配方清单面板（S7F19 目录核对）：回答「机台上到底装了哪些配方，和 RMS 对得上吗」。
 *
 * <p>识别三类风险：ORPHAN（机台有、RMS 无映射 → 未纳管）、DRIFT（机台 HASH 与生效版不一致）、
 * 以及 MISSING（RMS 有映射、机台未上报）。</p>
 */
function InventoryPanel({ devices }: { devices: Device[] }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [form, setForm] = useState<InvForm>({ toolId: '', items: '' });
  const [report, setReport] = useState<InventoryReport | null>(null);
  const [busy, setBusy] = useState(false);

  const toolId = form.toolId || devices[0]?.id || '';

  const rowsRes = useResource(() => api.listToolInventory(toolId ? { deviceId: toolId } : {}), {
    deps: [toolId],
    enabled: !!toolId,
    keepPreviousData: true,
  });
  const summaryRes = useResource(() => api.toolInventorySummary(toolId || undefined), {
    deps: [toolId],
    enabled: !!toolId,
    keepPreviousData: true,
  });

  const rows = rowsRes.data ?? NO_INV;
  const summary: InventorySummary | undefined = summaryRes.data;

  const submit = async () => {
    if (!toolId) {
      toast.warning(t('rms.integration.inv.required'));
      return;
    }
    setBusy(true);
    setReport(null);
    try {
      const items = form.items
        .split('\n')
        .map((l) => l.trim())
        .filter(Boolean)
        .map((line) => {
          const eq = line.indexOf('=');
          return eq < 0
            ? { ppid: line, bodyHash: null as string | null }
            : { ppid: line.slice(0, eq).trim(), bodyHash: line.slice(eq + 1).trim() };
        });
      setReport(
        await api.reportToolInventory({ toolId, correlationId: `inv-${Date.now()}`, items }),
      );
      rowsRes.refresh();
      summaryRes.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<InventoryRow>>>(
    () => [
      { key: 'ppidOnTool', titleKey: 'rms.integration.inv.ppid', mono: true, width: 170 },
      {
        key: 'recipeCode',
        titleKey: 'rms.integration.recipe',
        mono: true,
        width: 170,
        render: (x) => x.recipeCode ?? t('rms.common.dash'),
      },
      {
        key: 'toolBodyHash',
        titleKey: 'rms.integration.inv.toolHash',
        mono: true,
        width: 170,
        render: (x) => shortHash(x.toolBodyHash),
      },
      {
        key: 'expectedHash',
        titleKey: 'rms.integration.inv.expectedHash',
        mono: true,
        width: 170,
        render: (x) => shortHash(x.expectedHash),
      },
      {
        key: 'reconcileResult',
        titleKey: 'rms.integration.inv.result',
        width: 120,
        render: (x) => (
          <CimTag size="small" tone={invTone(x.reconcileResult)}>
            {x.reconcileResult}
          </CimTag>
        ),
      },
      {
        key: 'lastSeenAt',
        titleKey: 'rms.integration.inv.lastSeen',
        width: 170,
        render: (x) => <span className="muted">{x.lastSeenAt ?? t('rms.common.dash')}</span>,
      },
    ],
    [t],
  );

  const fields: Array<FormField<InvForm>> = [
    {
      key: 'toolId',
      labelKey: 'rms.integration.device',
      kind: 'select',
      options: devices.map((d) => ({ value: d.id, label: d.code, hint: d.name })),
    },
    {
      key: 'items',
      labelKey: 'rms.integration.inv.items',
      kind: 'textarea',
      rows: 3,
      span: 2,
      placeholderKey: 'rms.integration.inv.itemsPh',
    },
  ];

  return (
    <Panel titleKey="rms.integration.inv.title" subKey="rms.integration.inv.sub">
      {report ? (
        <InlineAlert
          tone={report.orphan > 0 || report.drift > 0 ? 'err' : 'ok'}
          style={{ marginBottom: 10 }}
        >
          <>
            {t('rms.integration.inv.report')}: {t('rms.integration.inv.total')} {report.total}
            {' · OK '}
            {report.ok}
            {' · DRIFT '}
            {report.drift}
            {' · ORPHAN '}
            {report.orphan}
            {report.missing.length > 0
              ? ` · ${t('rms.integration.inv.missing')}: ${report.missing.join(', ')}`
              : ''}
          </>
        </InlineAlert>
      ) : null}

      {summary ? (
        <KpiGrid style={{ marginBottom: 10 }}>
          <KpiCard label={t('rms.integration.inv.total')} value={summary.total} />
          <KpiCard tone="ok" label={'OK'} value={summary.ok} />
          <KpiCard tone="warn" label={'DRIFT'} value={summary.drift} />
          <KpiCard tone="warn" label={'ORPHAN'} value={summary.orphan} />
        </KpiGrid>
      ) : null}

      <FormGrid<InvForm>
        fields={fields}
        value={{ ...form, toolId }}
        onChange={(p) => setForm((prev) => ({ ...prev, ...p }))}
        columns={4}
        disabled={busy}
      />

      <Toolbar
        left={
          <Can perm={INTEGRATION_OPERATE}>
            <CimButton type="primary" size="small" disabled={busy} onClick={() => void submit()}>
              {t('rms.integration.inv.reportAction')}
            </CimButton>
          </Can>
        }
      />

      <DataTable<InventoryRow>
        flat
        columns={columns}
        rows={rows}
        rowKey={(x) => x.id}
        loading={rowsRes.loading}
        error={rowsRes.error?.message}
        onRetry={rowsRes.refresh}
        onRefresh={() => {
          rowsRes.refresh();
          summaryRes.refresh();
        }}
        scrollX="max-content"
        emptyTitleKey="rms.integration.inv.empty"
      />
    </Panel>
  );
}

/** 收一段文本原因的小弹窗（替代 `window.prompt`）。 */
function ReasonModal({
  titleKey,
  labelKey,
  okKey,
  danger = false,
  onClose,
  onSubmit,
}: {
  titleKey: string;
  labelKey: string;
  okKey: string;
  danger?: boolean;
  onClose: () => void;
  onSubmit: (reason: string) => Promise<void>;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (!reason.trim()) {
      toast.warning(t(labelKey));
      return;
    }
    setBusy(true);
    try {
      await onSubmit(reason.trim());
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      setBusy(false);
      return;
    }
    setBusy(false);
    onClose();
  };

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey={titleKey}
      width={560}
      onOk={submit}
      okLoading={busy}
      okDanger={danger}
      okKey={okKey}
      cancelKey="rms.common.cancel"
      maskClosable={false}
    >
      <CimField label={t(labelKey)} required>
        <CimInput rows={3} value={reason} onChange={setReason} />
      </CimField>
    </CimModal>
  );
}
