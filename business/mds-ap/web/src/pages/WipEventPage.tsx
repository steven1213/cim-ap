import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimButton,
  DataTable,
  FormGrid,
  Page,
  Panel,
  StatusTag,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';
import type { ReasonCodeView, ReasonCodeTypeView, WipReasonEventView } from '@/types';

/**
 * WIP 原因事件（WipEventPage）——「快照，不联表」的治理演示页（V16 示例域）。
 *
 * <p>上半区模拟 WIP 事务：选事务类型 + 启用原因码（type 匹配 + enabled 下拉口径）
 * 创建事件，快照五件套同事务落库。下半区是事件履历：展示列全部取自快照字段——
 * 主数据后续改名/挪部门/挪树位/停用都不会改变这里的历史。</p>
 */

const NO_TYPES: ReasonCodeTypeView[] = [];
const NO_EVENTS: WipReasonEventView[] = [];
const NO_REASONS: ReasonCodeView[] = [];

export default function WipEventPage() {
  const { t } = useTranslation();
  const toast = useToast();

  const typesRes = useResource(({ signal }) => api.listReasonCodeTypes(signal));
  const types = typesRes.data ?? NO_TYPES;
  const eventsRes = useResource(({ signal }) => api.listWipReasonEvents(undefined, signal));
  const events = eventsRes.data ?? NO_EVENTS;

  const [lotKeyword, setLotKeyword] = useState('');
  const rows = useMemo(() => {
    const kw = lotKeyword.trim().toLowerCase();
    if (!kw) return events;
    return events.filter(
      (e) =>
        e.lotId.toLowerCase().includes(kw) ||
        e.reasonCodeSnapshot.toLowerCase().includes(kw) ||
        (e.operator ?? '').toLowerCase().includes(kw),
    );
  }, [events, lotKeyword]);

  // ── 事件创建表单 ──
  const [form, setForm] = useState({
    lotId: '',
    waferId: '',
    operationCode: '',
    equipmentCode: '',
    eventType: '',
    reasonId: '',
    operator: '',
  });
  const [busy, setBusy] = useState(false);

  /** 事务类型候选：登记中的语义域（种子 8 类 + 页面自建）。 */
  const typeOptions = useMemo(
    () => types.map((x) => ({ value: x.typeKey, label: `${x.typeKey} · ${x.typeName}` })),
    [types],
  );

  /** 原因码候选：随事务类型联动取「type 匹配 + 启用」（WIP 下拉口径）。 */
  const reasonsRes = useResource(
    ({ signal }) =>
      form.eventType
        ? api.listEnabledReasonCodes(form.eventType, signal)
        : Promise.resolve(NO_REASONS),
    { deps: [form.eventType], enabled: !!form.eventType },
  );
  const reasonOptions = useMemo(
    () =>
      (reasonsRes.data ?? NO_REASONS).map((c) => ({
        value: c.id,
        label: `${c.reasonCode} · ${c.shortName ?? ''}`,
      })),
    [reasonsRes.data],
  );

  const createFields = useMemo<Array<FormField<typeof form>>>(
    () => [
      { kind: 'text' as const, key: 'lotId' as const, labelKey: 'mds.wipEvent.field.lotId', required: true },
      { kind: 'text' as const, key: 'waferId' as const, labelKey: 'mds.wipEvent.field.waferId' },
      { kind: 'select' as const, key: 'eventType' as const, labelKey: 'mds.wipEvent.field.eventType', options: typeOptions },
      { kind: 'select' as const, key: 'reasonId' as const, labelKey: 'mds.wipEvent.field.reason', options: reasonOptions, allowClear: true, placeholderKey: 'mds.wipEvent.reasonPlaceholder' },
      { kind: 'text' as const, key: 'operationCode' as const, labelKey: 'mds.wipEvent.field.operation' },
      { kind: 'text' as const, key: 'equipmentCode' as const, labelKey: 'mds.wipEvent.field.equipment' },
      { kind: 'text' as const, key: 'operator' as const, labelKey: 'mds.wipEvent.field.operator' },
    ],
    [typeOptions, reasonOptions],
  );

  const submit = async () => {
    if (!form.lotId.trim() || !form.eventType || !form.reasonId) {
      toast.error(t('mds.common.required'));
      return;
    }
    setBusy(true);
    try {
      await api.createWipReasonEvent({
        lotId: form.lotId.trim(),
        waferId: form.waferId || null,
        operationCode: form.operationCode || null,
        equipmentCode: form.equipmentCode || null,
        eventType: form.eventType,
        reasonId: form.reasonId,
        operator: form.operator || null,
      });
      toast.success(t('mds.wipEvent.created'));
      setForm((prev) => ({ ...prev, lotId: '', waferId: '', reasonId: '' }));
      eventsRes.refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const columns = useMemo<Array<DataTableColumn<WipReasonEventView>>>(
    () => [
      { key: 'occurredAt', titleKey: 'mds.wipEvent.field.occurredAt', width: 170, render: (r) => (r.occurredAt ?? '').replace('T', ' ').slice(0, 19) },
      { key: 'lotId', titleKey: 'mds.wipEvent.field.lotId', width: 130, mono: true },
      {
        key: 'eventType',
        titleKey: 'mds.wipEvent.field.eventType',
        width: 110,
        render: (r) => (
          <StatusTag
            dict="wipEventType"
            value={r.eventType}
            label={t(`mds.wipEvent.type.${r.eventType}`)}
          />
        ),
      },
      { key: 'reasonCodeSnapshot', titleKey: 'mds.wipEvent.snap.code', width: 150, mono: true },
      { key: 'reasonNameSnapshot', titleKey: 'mds.wipEvent.snap.name', width: 140, render: (r) => r.reasonNameSnapshot ?? '—' },
      { key: 'reasonPathSnapshot', titleKey: 'mds.wipEvent.snap.path', ellipsis: true, mono: true },
      { key: 'owningDeptSnapshot', titleKey: 'mds.wipEvent.snap.dept', width: 100, render: (r) => r.owningDeptSnapshot ?? '—' },
      { key: 'operator', titleKey: 'mds.wipEvent.field.operator', width: 90, render: (r) => r.operator ?? '—' },
      { key: 'waferId', titleKey: 'mds.wipEvent.field.waferId', width: 100, render: (r) => r.waferId ?? '—' },
      { key: 'operationCode', titleKey: 'mds.wipEvent.field.operation', width: 100, render: (r) => r.operationCode ?? '—' },
      { key: 'equipmentCode', titleKey: 'mds.wipEvent.field.equipment', width: 110, render: (r) => r.equipmentCode ?? '—' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <Panel titleKey="mds.wipEvent.createTitle" sub={t('mds.wipEvent.createHint')}>
        <FormGrid
          fields={createFields}
          value={form}
          onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
          columns={4}
        />
        <div className="reason-event-actions">
          <Can perm={MDS_PERMS.reason.update}>
            <CimButton
              type="primary"
              loading={busy}
              disabled={!form.lotId.trim() || !form.eventType || !form.reasonId}
              onClick={() => void submit()}
            >
              {t('mds.wipEvent.create')}
            </CimButton>
          </Can>
        </div>
      </Panel>

      <Panel titleKey="mds.wipEvent.historyTitle" sub={t('mds.wipEvent.historyHint')}>
        <DataTable<WipReasonEventView>
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id}
          loading={eventsRes.loading}
          error={eventsRes.error?.message ?? null}
          onRetry={eventsRes.refresh}
          search={{
            value: lotKeyword,
            onChange: setLotKeyword,
            placeholderKey: 'mds.wipEvent.searchPlaceholder',
          }}
        />
      </Panel>
    </Page>
  );
}
