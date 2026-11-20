import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimCheckTag,
  CimModal,
  DataTable,
  FormGrid,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useHasPermission } from '@/lib/usePermission';
import type { ControlSwitchView, ShiftPatternView, ShiftView } from '@/types';

/**
 * 班次与班制（ShiftDefsPage，V17/V18）——排班域的定义类主数据维护。
 *
 * <p>左栏班次（一天内的值守时间段，晚班演示跨天口径），右栏班制（周期轮转序列，
 * 是排班页「按班制生成」的模板）。守门规则：code 建后不可改；被引用的班次禁删
 * （2004 只能停用）；班制 detail 项数必须等于周期天数且引用启用班次。</p>
 *
 * <p>V18 卡控开关：页面头部就地切换（无独立管理页，动线不割裂）——开关数据源是
 * `cim_control_switch`（mshift 域），MES 等消费方按 TTL 缓存拉取；开 = 消费方执行
 * 卡控判定，关 = 卡控逻辑常驻但直接放行。启停权限沿用 {@code mds:shift:update}。</p>
 */

const NO_SHIFTS: ShiftView[] = [];
const NO_PATTERNS: ShiftPatternView[] = [];

const SHIFT_TITLE = {
  editKey: 'mds.shiftDef.editTitle',
  createKey: 'mds.shiftDef.createTitle',
} as const;
const PATTERN_TITLE = {
  editKey: 'mds.shiftPattern.editTitle',
  createKey: 'mds.shiftPattern.createTitle',
} as const;

/** 日历色板键 → CSS 色（与后端种子 color 对齐；纯展示不进字典）。 */
export const SHIFT_COLORS: Record<string, string> = {
  blue: '#2563eb',
  teal: '#0d9488',
  purple: '#7c3aed',
  green: '#16a34a',
  orange: '#ea580c',
  red: '#dc2626',
  gray: '#64748b',
  cyan: '#0891b2',
};

export default function ShiftDefsPage() {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const shiftsRes = useResource(({ signal }) => api.listShifts(signal));
  const patternsRes = useResource(({ signal }) => api.listShiftPatterns(signal));
  const shifts = shiftsRes.data ?? NO_SHIFTS;
  const patterns = patternsRes.data ?? NO_PATTERNS;

  // ── 卡控开关（V18）——就地启停；标签文案优先 i18n（en），缺翻译回退库名 ──
  const canControl = useHasPermission(MDS_PERMS.shift.update);
  const switchesRes = useResource(({ signal }) => api.listControlSwitches('mshift', signal));
  const switches = switchesRes.data ?? [];
  const [toggling, setToggling] = useState<string | null>(null);
  const controlLabelOf = (sw: ControlSwitchView) => {
    const key = `mds.shift.control.${sw.controlCode}`;
    return i18n.exists(key) ? t(key) : sw.name;
  };
  const doToggleSwitch = async (sw: ControlSwitchView, next: boolean) => {
    setToggling(sw.controlCode);
    try {
      await api.setControlSwitch(sw.controlCode, next);
      toast.success(t('mds.common.saved'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setToggling(null);
      switchesRes.refresh();
    }
  };

  // ── 班次弹窗 ──
  const [shiftForm, setShiftForm] = useState<
    { mode: 'create' } | { mode: 'edit'; shift: ShiftView } | null
  >(null);
  const doDeleteShift = async (row: ShiftView) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      content: t('mds.shiftDef.deleteConfirm', { code: row.shiftCode }),
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteShift(row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      // 2004（被引用禁删）直接把后端文案吐给用户
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    shiftsRes.refresh();
  };

  // ── 班制弹窗 ──
  const [patternForm, setPatternForm] = useState<
    { mode: 'create' } | { mode: 'edit'; pattern: ShiftPatternView } | null
  >(null);
  const doDeletePattern = async (row: ShiftPatternView) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      content: t('mds.shiftPattern.deleteConfirm', { code: row.patternCode }),
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteShiftPattern(row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    patternsRes.refresh();
  };

  // ── 班次表 ──
  const shiftColumns = useMemo<Array<DataTableColumn<ShiftView>>>(
    () => [
      { key: 'shiftCode', titleKey: 'mds.shiftDef.code', width: 110, mono: true },
      {
        key: 'shiftName',
        titleKey: 'mds.shiftDef.name',
        render: (r) => (
          <span>
            <span
              className="shift-color-dot"
              style={{ background: SHIFT_COLORS[r.color ?? ''] ?? '#64748b' }}
            />
            {r.shiftName}
          </span>
        ),
      },
      {
        key: 'range',
        titleKey: 'mds.shiftDef.range',
        width: 150,
        mono: true,
        render: (r) => `${r.startTime} - ${r.endTime}${r.crossDay ? ' +1' : ''}`,
      },
      {
        key: 'enabled',
        titleKey: 'mds.shiftDef.enabled',
        width: 90,
        render: (r) => (
          <span className={r.enabled ? '' : 'dim'}>
            {r.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 140,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.shift.update}>
              <CimButton size="small" onClick={() => setShiftForm({ mode: 'edit', shift: r })}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.shift.delete}>
              <CimButton size="small" danger onClick={() => void doDeleteShift(r)}>
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

  // ── 班制表 ──
  const shiftNameOf = (code: string) => shifts.find((s) => s.shiftCode === code)?.shiftName ?? code;
  const patternColumns = useMemo<Array<DataTableColumn<ShiftPatternView>>>(
    () => [
      { key: 'patternCode', titleKey: 'mds.shiftPattern.code', width: 120, mono: true },
      { key: 'patternName', titleKey: 'mds.shiftPattern.name' },
      {
        key: 'detail',
        titleKey: 'mds.shiftPattern.detail',
        render: (r) => (
          <span className="mono dim">
            {r.detail.map((c, i) => `${i + 1}.${shiftNameOf(c)}`).join(' → ')}
          </span>
        ),
      },
      {
        key: 'enabled',
        titleKey: 'mds.shiftDef.enabled',
        width: 90,
        render: (r) => (
          <span className={r.enabled ? '' : 'dim'}>
            {r.enabled ? t('mds.topo.enabled') : t('mds.topo.disabled')}
          </span>
        ),
      },
      {
        key: 'actions',
        titleKey: 'mds.common.actions',
        width: 140,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={MDS_PERMS.shift.update}>
              <CimButton size="small" onClick={() => setPatternForm({ mode: 'edit', pattern: r })}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.shift.delete}>
              <CimButton size="small" danger onClick={() => void doDeletePattern(r)}>
                {t('mds.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, shifts],
  );

  return (
    <Page>
      <div className="shift-control-bar">
        <span className="shift-control-title">{t('mds.shift.control.title')}</span>
        {switches.map((sw) => (
          <CimCheckTag
            key={sw.controlCode}
            checked={sw.enabled}
            disabled={!canControl || toggling === sw.controlCode}
            onChange={(next) => void doToggleSwitch(sw, next)}
          >
            {controlLabelOf(sw)}
          </CimCheckTag>
        ))}
        {switchesRes.loading ? <span className="dim">{t('mds.common.loading')}</span> : null}
        <span className="dim shift-control-hint">{t('mds.shift.control.hint')}</span>
      </div>

      <div className="shift-defs-layout">
        <Panel
          title={t('mds.shiftDef.panelTitle')}
          sub={t('mds.shiftDef.panelSub')}
          flush
          actions={
            <Can perm={MDS_PERMS.shift.create}>
              <CimButton type="primary" size="small" onClick={() => setShiftForm({ mode: 'create' })}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
        >
          <DataTable<ShiftView>
            columns={shiftColumns}
            rows={shifts}
            rowKey={(r) => r.shiftCode}
            loading={shiftsRes.loading}
            error={shiftsRes.error?.message ?? null}
            onRetry={shiftsRes.refresh}
          />
        </Panel>

        <Panel
          title={t('mds.shiftPattern.panelTitle')}
          sub={t('mds.shiftPattern.panelSub')}
          flush
          actions={
            <Can perm={MDS_PERMS.shift.create}>
              <CimButton type="primary" size="small" onClick={() => setPatternForm({ mode: 'create' })}>
                {t('mds.common.create')}
              </CimButton>
            </Can>
          }
        >
          <DataTable<ShiftPatternView>
            columns={patternColumns}
            rows={patterns}
            rowKey={(r) => r.patternCode}
            loading={patternsRes.loading}
            error={patternsRes.error?.message ?? null}
            onRetry={patternsRes.refresh}
          />
        </Panel>
      </div>

      {shiftForm && (
        <ShiftFormModal
          state={shiftForm}
          onClose={() => setShiftForm(null)}
          onDone={() => {
            setShiftForm(null);
            shiftsRes.refresh();
          }}
        />
      )}
      {patternForm && (
        <PatternFormModal
          state={patternForm}
          onClose={() => setPatternForm(null)}
          onDone={() => {
            setPatternForm(null);
            patternsRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

/** 班次新建/编辑弹窗（shiftCode 建后不可改；跨天时允许 start > end）。 */
function ShiftFormModal({
  state,
  onClose,
  onDone,
}: {
  state: { mode: 'create' } | { mode: 'edit'; shift: ShiftView };
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const initial = state.mode === 'edit' ? state.shift : null;
  const [form, setForm] = useState({
    shiftCode: initial?.shiftCode ?? '',
    shiftName: initial?.shiftName ?? '',
    startTime: initial?.startTime ?? '08:00',
    endTime: initial?.endTime ?? '16:00',
    crossDay: initial?.crossDay ?? false,
    color: initial?.color ?? 'blue',
    enabled: initial?.enabled ?? true,
    sortNo: initial?.sortNo ?? 100,
    description: initial?.description ?? '',
  });

  const colorOptions = Object.keys(SHIFT_COLORS).map((k) => ({ value: k, label: k }));
  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [{ kind: 'text' as const, key: 'shiftCode' as const, labelKey: 'mds.shiftDef.code', required: true, mono: true }]
        : []),
      { kind: 'text' as const, key: 'shiftName' as const, labelKey: 'mds.shiftDef.name', required: true },
      { kind: 'time' as const, key: 'startTime' as const, labelKey: 'mds.shiftDef.startTime' },
      { kind: 'time' as const, key: 'endTime' as const, labelKey: 'mds.shiftDef.endTime' },
      { kind: 'select' as const, key: 'color' as const, labelKey: 'mds.shiftDef.color', options: colorOptions, allowClear: true, placeholderKey: 'mds.shiftDef.colorPlaceholder' },
      { kind: 'switch' as const, key: 'crossDay' as const, labelKey: 'mds.shiftDef.crossDay' },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.shiftDef.enabled' },
      { kind: 'number' as const, key: 'sortNo' as const, labelKey: 'mds.common.sortNo', precision: 0 },
      { kind: 'textarea' as const, key: 'description' as const, labelKey: 'mds.common.remark', span: 2, rows: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [initial],
  );

  async function submit() {
    if (!form.shiftCode.trim() || !form.shiftName.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const payload = { ...form };
      if (initial) {
        await api.updateShift(initial.id, payload);
      } else {
        await api.createShift(payload);
      }
      toast.success(t('mds.common.saved'));
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
      titleKey={initial ? SHIFT_TITLE.editKey : SHIFT_TITLE.createKey}
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

/** 班制新建/编辑弹窗（周期 N 行下拉；行数随 cycleDays 联动）。 */
function PatternFormModal({
  state,
  onClose,
  onDone,
}: {
  state: { mode: 'create' } | { mode: 'edit'; pattern: ShiftPatternView };
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const initial = state.mode === 'edit' ? state.pattern : null;
  const [form, setForm] = useState({
    patternCode: initial?.patternCode ?? '',
    patternName: initial?.patternName ?? '',
    cycleDays: initial?.cycleDays ?? 4,
    detail: initial?.detail ?? ([] as string[]),
    enabled: initial?.enabled ?? true,
    sortNo: initial?.sortNo ?? 100,
    description: initial?.description ?? '',
  });

  const shiftsRes = useResource(({ signal }) => api.listShifts(signal));
  const shifts = shiftsRes.data ?? NO_SHIFTS;
  const shiftOptions = shifts.map((s) => ({ value: s.shiftCode, label: s.shiftName }));

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [{ kind: 'text' as const, key: 'patternCode' as const, labelKey: 'mds.shiftPattern.code', required: true, mono: true }]
        : []),
      { kind: 'text' as const, key: 'patternName' as const, labelKey: 'mds.shiftPattern.name', required: true },
      { kind: 'number' as const, key: 'cycleDays' as const, labelKey: 'mds.shiftPattern.cycleDays', precision: 0, min: 1, max: 31 },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.shiftDef.enabled' },
      { kind: 'number' as const, key: 'sortNo' as const, labelKey: 'mds.common.sortNo', precision: 0 },
      { kind: 'textarea' as const, key: 'description' as const, labelKey: 'mds.common.remark', span: 2, rows: 2 },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [initial],
  );

  async function submit() {
    if (!form.patternCode.trim() || !form.patternName.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    if (form.detail.length !== form.cycleDays || form.detail.some((c) => !c)) {
      setErr(t('mds.shiftPattern.detailInvalid'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const payload = { ...form };
      if (initial) {
        await api.updateShiftPattern(initial.id, payload);
      } else {
        await api.createShiftPattern(payload);
      }
      toast.success(t('mds.common.saved'));
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
      titleKey={initial ? PATTERN_TITLE.editKey : PATTERN_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={560}
    >
      <FormGrid
        fields={fields}
        value={form}
        onChange={(patch) =>
          setForm((prev) => {
            const next = { ...prev, ...patch };
            // 周期天数联动轮转序列行数：增行补第一个班次，减行截断
            if ('cycleDays' in patch && typeof patch.cycleDays === 'number') {
              const n = Math.max(1, Math.min(31, Math.round(patch.cycleDays)));
              const detail = [...next.detail];
              while (detail.length < n) detail.push(shifts[0]?.shiftCode ?? '');
              next.detail = detail.slice(0, n);
            }
            return next;
          })
        }
        columns={2}
      />
      <div className="pattern-detail-editor">
        <p className="dim">{t('mds.shiftPattern.detailHint')}</p>
        {Array.from({ length: form.cycleDays }, (_, i) => (
          <div key={i} className="pattern-detail-row">
            <span className="mono">{t('mds.shiftPattern.dayN', { n: i + 1 })}</span>
            <select
              className="cim-select"
              value={form.detail[i] ?? ''}
              onChange={(e) => {
                const v = e.target.value;
                setForm((prev) => {
                  const detail = [...prev.detail];
                  detail[i] = v;
                  return { ...prev, detail };
                });
              }}
            >
              {shiftOptions.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
        ))}
      </div>
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
