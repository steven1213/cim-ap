import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  CimModal,
  FormGrid,
  Page,
  Panel,
  useToast,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import { useHasPermission } from '@/lib/usePermission';
import type { ShiftAssignmentView, ShiftUserOption, ShiftView } from '@/types';
import { SHIFT_COLORS } from './ShiftDefsPage';

/**
 * 排班安排（SchedulePage，V17）——周视图：7 列（周一至周日）× 人员行。
 *
 * <p><b>动态表头特例</b>：列头是「随周次变化的日期」，DataTable 列头须 i18n 键、
 * 进不去动态文案——与状态矩阵同款，用原生 table（{@code .schedule-grid}）渲染。
 * 交互：点色块弹班次选择（有 SHIFT_UPDATE 权限）；「按班制生成」「复制上周」
 * 一步落库，均跳过 MANUAL 人工格（人工安排不被模板覆盖）。</p>
 */

const NO_SHIFTS: ShiftView[] = [];
const NO_ROWS: ShiftAssignmentView[] = [];
const NO_USERS: UserRow[] = [];

function mondayOf(d: Date): Date {
  const day = d.getDay(); // 0=周日
  const diff = day === 0 ? -6 : 1 - day;
  const m = new Date(d);
  m.setDate(d.getDate() + diff);
  m.setHours(0, 0, 0, 0);
  return m;
}

function isoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

type UserRow = ShiftUserOption;

export default function SchedulePage() {
  const { t } = useTranslation();
  const toast = useToast();
  const canEdit = useHasPermission(MDS_PERMS.shift.update);

  const [weekStart, setWeekStart] = useState<Date>(() => mondayOf(new Date()));
  const weekStartIso = isoDate(weekStart);

  const usersRes = useResource(({ signal }) => api.listShiftUsers(signal));
  const users = usersRes.data ?? NO_USERS;

  const shiftsRes = useResource(({ signal }) => api.listEnabledShifts(signal));
  const shifts = shiftsRes.data ?? NO_SHIFTS;
  const shiftOf = (code: string) => shifts.find((s) => s.shiftCode === code) ?? null;

  const assignmentsRes = useResource(({ signal }) => api.listWeekAssignments(weekStartIso, signal), {
    deps: [weekStartIso],
  });
  const assignments = assignmentsRes.data ?? NO_ROWS;

  // 格子索引：(date|userId) → assignment
  const cellMap = useMemo(() => {
    const m = new Map<string, ShiftAssignmentView>();
    for (const a of assignments) m.set(`${a.workDate}|${a.userId}`, a);
    return m;
  }, [assignments]);

  const days = useMemo(
    () => Array.from({ length: 7 }, (_, i) => new Date(weekStart.getFullYear(), weekStart.getMonth(), weekStart.getDate() + i)),
    [weekStart],
  );

  const visibleUsers = useMemo(() => users.filter((u) => u.enabled), [users]);

  function shiftWeek(delta: number) {
    setWeekStart((prev) => {
      const d = new Date(prev);
      d.setDate(d.getDate() + delta * 7);
      return d;
    });
  }

  // ── 单格弹窗 ──
  const [cellForm, setCellForm] = useState<{ date: string; user: UserRow } | null>(null);

  // ── 班制铺排弹窗 ──
  const [generateOpen, setGenerateOpen] = useState(false);
  const [copyBusy, setCopyBusy] = useState(false);
  const doCopyLastWeek = async () => {
    setCopyBusy(true);
    try {
      const from = new Date(weekStart);
      from.setDate(from.getDate() - 7);
      const n = await api.copyLastWeek({ fromWeekStart: isoDate(from), toWeekStart: weekStartIso });
      toast.success(t('mds.schedule.copied', { n }));
      assignmentsRes.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setCopyBusy(false);
    }
  };

  return (
    <Page>
      <Panel
        title={t('mds.schedule.panelTitle')}
        sub={t('mds.schedule.weekRange', {
          from: weekStartIso,
          to: isoDate(days[6]),
        })}
        flush
        actions={
          <>
            <CimButton size="small" onClick={() => shiftWeek(-1)}>
              ‹
            </CimButton>
            <CimButton size="small" onClick={() => setWeekStart(mondayOf(new Date()))}>
              {t('mds.schedule.thisWeek')}
            </CimButton>
            <CimButton size="small" onClick={() => shiftWeek(1)}>
              ›
            </CimButton>
            <Can perm={MDS_PERMS.shift.update}>
              <CimButton size="small" onClick={() => setGenerateOpen(true)}>
                {t('mds.schedule.generate')}
              </CimButton>
              <CimButton size="small" onClick={() => void doCopyLastWeek()} disabled={copyBusy}>
                {t('mds.schedule.copyLastWeek')}
              </CimButton>
            </Can>
          </>
        }
      >
        <div className="schedule-wrap">
          <table className="schedule-grid">
            <thead>
              <tr>
                <th className="schedule-corner">{t('mds.schedule.userCol')}</th>
                {days.map((d) => {
                  const iso = isoDate(d);
                  const weekend = d.getDay() === 0 || d.getDay() === 6;
                  return (
                    <th key={iso} className={weekend ? 'schedule-weekend' : ''}>
                      <div>{t('mds.schedule.weekday.' + d.getDay())}</div>
                      <div className="mono dim schedule-day">{iso.slice(5)}</div>
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {visibleUsers.map((u) => (
                <tr key={u.userId}>
                  <td className="schedule-user">{u.username}</td>
                  {days.map((d) => {
                    const iso = isoDate(d);
                    const cell = cellMap.get(`${iso}|${u.userId}`);
                    const shift = cell ? shiftOf(cell.shiftCode) : null;
                    return (
                      <td key={iso}>
                        {cell && shift ? (
                          <button
                            type="button"
                            className="schedule-cell"
                            style={{
                              background: SHIFT_COLORS[shift.color ?? ''] ?? '#64748b',
                            }}
                            title={`${shift.shiftName} ${shift.startTime}-${shift.endTime}（${cell.source}）`}
                            onClick={() => {
                              if (canEdit) setCellForm({ date: iso, user: u });
                            }}
                          >
                            {shift.shiftName}
                            {cell.source !== 'MANUAL' ? <span className="schedule-src">·{cell.source}</span> : null}
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="schedule-cell schedule-cell-empty"
                            onClick={() => {
                              if (canEdit) setCellForm({ date: iso, user: u });
                            }}
                          >
                            {canEdit ? '+' : ''}
                          </button>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
              {visibleUsers.length === 0 && (
                <tr>
                  <td colSpan={8} className="schedule-empty dim">
                    {usersRes.loading ? t('mds.common.loading') : t('mds.schedule.noUsers')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <p className="dim schedule-hint">{t('mds.schedule.legendHint')}</p>
      </Panel>

      {cellForm && (
        <CellModal
          state={cellForm}
          shifts={shifts}
          onClose={() => setCellForm(null)}
          onDone={() => {
            setCellForm(null);
            assignmentsRes.refresh();
          }}
        />
      )}
      {generateOpen && (
        <GenerateModal
          weekStartIso={weekStartIso}
          users={visibleUsers}
          onClose={() => setGenerateOpen(false)}
          onDone={(n) => {
            setGenerateOpen(false);
            toast.success(t('mds.schedule.generated', { n }));
            assignmentsRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

/** 单格班次选择弹窗（选班次=保存，清空=软删该格）。 */
function CellModal({
  state,
  shifts,
  onClose,
  onDone,
}: {
  state: { date: string; user: UserRow };
  shifts: ShiftView[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  async function pick(shiftCode: string | null) {
    setBusy(true);
    try {
      await api.saveShiftCell({
        workDate: state.date,
        userId: state.user.userId,
        username: state.user.username,
        shiftCode,
      });
      onDone();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="mds.schedule.cellTitle"
      onOk={onClose}
      okKey="mds.common.cancel"
      cancelKey="mds.common.cancel"
      okLoading={false}
      width={420}
    >
      <p className="dim">
        {t('mds.schedule.cellSub', { user: state.user.username, date: state.date })}
      </p>
      <div className="schedule-pick-list">
        {shifts.map((s) => (
          <button
            key={s.shiftCode}
            type="button"
            className="schedule-pick"
            disabled={busy}
            onClick={() => void pick(s.shiftCode)}
          >
            <span className="shift-color-dot" style={{ background: SHIFT_COLORS[s.color ?? ''] ?? '#64748b' }} />
            <span>{s.shiftName}</span>
            <span className="mono dim">
              {s.startTime}-{s.endTime}
              {s.crossDay ? ' +1' : ''}
            </span>
          </button>
        ))}
        <button
          type="button"
          className="schedule-pick schedule-pick-clear"
          disabled={busy}
          onClick={() => void pick(null)}
        >
          {t('mds.schedule.clearCell')}
        </button>
      </div>
    </CimModal>
  );
}

/** 班制铺排弹窗（选班制 + 勾人员，从本周一一步落库）。 */
function GenerateModal({
  weekStartIso,
  users,
  onClose,
  onDone,
}: {
  weekStartIso: string;
  users: UserRow[];
  onClose: () => void;
  onDone: (n: number) => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const patternsRes = useResource(({ signal }) => api.listEnabledShiftPatterns(signal));
  const patterns = patternsRes.data ?? [];
  const [form, setForm] = useState({
    patternCode: '',
    startDate: weekStartIso,
    userIds: [] as string[],
  });

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      {
        kind: 'select',
        key: 'patternCode',
        labelKey: 'mds.schedule.pattern',
        options: patterns.map((p) => ({
          value: p.patternCode,
          label: `${p.patternName}（${p.cycleDays}${t('mds.shiftPattern.cycleDays')}）`,
        })),
        required: true,
      },
      {
        kind: 'date',
        key: 'startDate',
        labelKey: 'mds.schedule.startDate',
        valueFormat: 'yyyy-MM-dd',
        required: true,
      },
    ],
    [patterns, t],
  );

  function toggleUser(userId: string) {
    setForm((prev) => ({
      ...prev,
      userIds: prev.userIds.includes(userId)
        ? prev.userIds.filter((x) => x !== userId)
        : [...prev.userIds, userId],
    }));
  }

  async function submit() {
    if (!form.patternCode) {
      setErr(t('mds.common.required'));
      return;
    }
    if (!form.userIds.length) {
      setErr(t('mds.schedule.noUsersPicked'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const usernames = Object.fromEntries(users.map((u) => [u.userId, u.username]));
      const n = await api.generateFromPattern({ ...form, usernames });
      onDone(n);
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="mds.schedule.generateTitle"
      onOk={() => void submit()}
      okKey="mds.schedule.generateOk"
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
      <div className="pattern-detail-editor">
        <p className="dim">{t('mds.schedule.pickUsers', { total: users.length })}</p>
        <div className="schedule-user-picker">
          {users.map((u) => (
            <label key={u.userId} className={'schedule-user-chip' + (form.userIds.includes(u.userId) ? ' on' : '')}>
              <input
                type="checkbox"
                checked={form.userIds.includes(u.userId)}
                onChange={() => toggleUser(u.userId)}
              />
              {u.username}
            </label>
          ))}
        </div>
      </div>
      {err ? <p className="cim-field-error form-err">{err}</p> : null}
    </CimModal>
  );
}
