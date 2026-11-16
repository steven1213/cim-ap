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
import type { BankTransferRule, Equipment, StockerBank } from '@/types';

/**
 * 存储区 Bank（StockerBank）——Stocker 内逻辑存储分区 + Bank 间转运规则。
 *
 * <p>行业口径（SEMI E88 zone）：Bank=标准 zone 的本地命名，层级
 * {@code Stocker → Bank → Shelf(不建模)}；bankType 挂业务用途字典
 * （原料/WIP/成品/不良/控片/回收），剩余容量归 MCS 运行时。转运规则是
 * 「允许从哪个 Bank 到哪个 Bank」的 Host 侧 allowlist（E88 6.1.1 目的地由
 * Host 指定），from→to 唯一、from≠to、跨 Stocker 允许（经 OHT/OHS 换巷道）。</p>
 *
 * <p>主从布局：上 Bank 列表（按所属设备过滤、行点击选中），下选中 Bank 的
 * 出/入转运规则。bankCode 设备内唯一建后不可改；被规则引用的 Bank 不可删。</p>
 */

const NO_BANKS: StockerBank[] = [];
const NO_RULES: BankTransferRule[] = [];
const NO_EQP: Equipment[] = [];

export default function BankPage() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const toast = useToast();
  const bankTypeDict = useDict('mds.stocker_bank_type');

  const [keyword, setKeyword] = useState('');
  const [equipmentFilter, setEquipmentFilter] = useState('');
  const [selectedBankId, setSelectedBankId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<StockerBank | null>(null);
  const [ruleCreating, setRuleCreating] = useState(false);
  const [ruleEditing, setRuleEditing] = useState<BankTransferRule | null>(null);

  // 所属设备下拉 = MATERIAL 大类设备（Stocker/OHB/NTB 等存储设备）
  const eqpRes = useResource(({ signal }) => api.listEquipments({ view: 'amhs' }, signal));
  const bankRes = useResource(({ signal }) => api.listStockerBanks(undefined, signal));
  const ruleRes = useResource(({ signal }) => api.listBankTransferRules(undefined, signal));

  const equipmentLabel = useMemo(() => {
    const map = new Map<string, string>();
    for (const e of eqpRes.data ?? NO_EQP) {
      map.set(e.id, `${e.equipmentCode} ${e.equipmentName}`);
    }
    return map;
  }, [eqpRes.data]);

  const equipmentOptions = useMemo(
    () => [
      { value: '', label: t('mds.bank.allEquipments') },
      ...(eqpRes.data ?? NO_EQP).map((e) => ({
        value: e.id,
        label: `${e.equipmentCode} ${e.equipmentName}`,
      })),
    ],
    [eqpRes.data, t],
  );

  const rows = useMemo(() => {
    let list = bankRes.data ?? NO_BANKS;
    if (equipmentFilter) list = list.filter((b) => b.equipmentId === equipmentFilter);
    const kw = keyword.trim().toLowerCase();
    if (kw) {
      list = list.filter(
        (b) =>
          b.bankCode.toLowerCase().includes(kw) ||
          b.bankName.toLowerCase().includes(kw) ||
          (b.equipmentCode ?? '').toLowerCase().includes(kw),
      );
    }
    return list;
  }, [bankRes.data, equipmentFilter, keyword]);

  const selectedBank = useMemo(
    () => (bankRes.data ?? NO_BANKS).find((b) => b.id === selectedBankId) ?? null,
    [bankRes.data, selectedBankId],
  );

  const bankLabel = useMemo(() => {
    const map = new Map<string, string>();
    for (const b of bankRes.data ?? NO_BANKS) {
      map.set(b.id, `${b.equipmentCode ?? ''}/${b.bankCode} ${b.bankName}`);
    }
    return map;
  }, [bankRes.data]);

  const relatedRules = useMemo(() => {
    if (!selectedBankId) return NO_RULES;
    return (ruleRes.data ?? NO_RULES).filter(
      (r) => r.fromBankId === selectedBankId || r.toBankId === selectedBankId,
    );
  }, [ruleRes.data, selectedBankId]);

  const openEdit = async (row: StockerBank) => {
    try {
      await api.claimLock('stockerBank', row.id);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    setEditing(row);
  };

  const closeEdit = () => {
    if (editing) {
      void api.releaseLock('stockerBank', editing.id).catch(() => {});
    }
    setEditing(null);
  };

  const doDelete = async (row: StockerBank) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      contentKey: 'mds.common.confirmDelete',
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteStockerBank(row.id);
      toast.success(t('mds.common.deleted'));
      if (selectedBankId === row.id) setSelectedBankId(null);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    bankRes.refresh();
    ruleRes.refresh();
  };

  const columns = useMemo<Array<DataTableColumn<StockerBank>>>(
    () => [
      { key: 'bankCode', titleKey: 'mds.bank.code', width: 130, mono: true },
      { key: 'bankName', titleKey: 'mds.bank.name', width: 190 },
      {
        key: 'bankType',
        titleKey: 'mds.bank.type',
        width: 140,
        render: (r) => <span className="mono">{bankTypeDict.labelOf(r.bankType)}</span>,
      },
      {
        key: 'capacity',
        titleKey: 'mds.bank.capacity',
        width: 90,
        render: (r) => (r.capacity != null ? r.capacity : '—'),
      },
      {
        key: 'equipment',
        titleKey: 'mds.bank.equipment',
        width: 210,
        render: (r) => equipmentLabel.get(r.equipmentId) ?? r.equipmentId,
      },
      {
        key: 'enabled',
        titleKey: 'mds.topo.status',
        width: 80,
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
            <Can perm={MDS_PERMS.bank.update}>
              <CimButton size="small" onClick={() => void openEdit(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.bank.delete}>
              <CimButton size="small" danger onClick={() => void doDelete(r)}>
                {t('mds.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, bankTypeDict, equipmentLabel],
  );

  const ruleColumns = useMemo<Array<DataTableColumn<BankTransferRule>>>(
    () => [
      {
        key: 'direction',
        titleKey: 'mds.bank.direction',
        width: 90,
        render: (r) =>
          r.fromBankId === selectedBankId ? (
            <span className="mono">{t('mds.bank.outbound')}</span>
          ) : (
            <span className="mono">{t('mds.bank.inbound')}</span>
          ),
      },
      {
        key: 'peer',
        titleKey: 'mds.bank.peer',
        width: 280,
        render: (r) => {
          const peerId = r.fromBankId === selectedBankId ? r.toBankId : r.fromBankId;
          return bankLabel.get(peerId) ?? peerId;
        },
      },
      {
        key: 'priority',
        titleKey: 'mds.bank.priority',
        width: 90,
        render: (r) => (r.priority != null ? <span className="mono">{r.priority}</span> : '—'),
      },
      {
        key: 'enabled',
        titleKey: 'mds.topo.status',
        width: 80,
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
            <Can perm={MDS_PERMS.bank.update}>
              <CimButton size="small" onClick={() => setRuleEditing(r)}>
                {t('mds.common.edit')}
              </CimButton>
            </Can>
            <Can perm={MDS_PERMS.bank.delete}>
              <CimButton size="small" danger onClick={() => void doDeleteRule(r)}>
                {t('mds.common.delete')}
              </CimButton>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, selectedBankId, bankLabel],
  );

  const doDeleteRule = async (rule: BankTransferRule) => {
    const ok = await confirm({
      title: t('mds.common.delete'),
      contentKey: 'mds.common.confirmDelete',
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteBankTransferRule(rule.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    ruleRes.refresh();
  };

  return (
    <Page>
      <div className="bank-toolbar">
        <select
          className="topo-group-filter"
          value={equipmentFilter}
          onChange={(e) => setEquipmentFilter(e.target.value)}
        >
          {equipmentOptions.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>
      <DataTable<StockerBank>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        rowClassName={(r) => (r.id === selectedBankId ? 'row-selected' : '')}
        onRowClick={(r) => setSelectedBankId(r.id === selectedBankId ? null : r.id)}
        loading={bankRes.loading}
        error={bankRes.error?.message ?? null}
        onRetry={bankRes.refresh}
        headActions={
          <Can perm={MDS_PERMS.bank.create}>
            <CimButton type="primary" size="small" onClick={() => setCreating(true)}>
              {t('mds.bank.createNew')}
            </CimButton>
          </Can>
        }
        search={{
          value: keyword,
          onChange: setKeyword,
          placeholderKey: 'mds.bank.searchPlaceholder',
        }}
        onRefresh={bankRes.refresh}
      />

      <div className="bank-rules-head">
        <h3>
          {selectedBank
            ? t('mds.bank.rulesTitle', { name: `${selectedBank.bankCode} ${selectedBank.bankName}` })
            : t('mds.bank.rulesTitle', { name: t('mds.bank.noSelection') })}
        </h3>
        <Can perm={MDS_PERMS.bank.create}>
          <CimButton
            type="primary"
            size="small"
            disabled={!selectedBank}
            onClick={() => setRuleCreating(true)}
          >
            {t('mds.bank.ruleCreate')}
          </CimButton>
        </Can>
      </div>
      {selectedBank ? (
        <DataTable<BankTransferRule>
          columns={ruleColumns}
          rows={relatedRules}
          rowKey={(r) => r.id}
          loading={ruleRes.loading}
          error={ruleRes.error?.message ?? null}
          onRetry={ruleRes.refresh}
        />
      ) : (
        <p className="dim">{t('mds.bank.selectHint')}</p>
      )}

      {creating && (
        <BankFormModal
          initial={null}
          onClose={() => setCreating(false)}
          onDone={() => {
            setCreating(false);
            toast.success(t('mds.common.created'));
            bankRes.refresh();
          }}
        />
      )}
      {editing && (
        <BankFormModal
          initial={editing}
          onClose={closeEdit}
          onDone={() => {
            setEditing(null);
            toast.success(t('mds.common.updated'));
            bankRes.refresh();
          }}
        />
      )}
      {ruleCreating && selectedBank && (
        <RuleFormModal
          bank={selectedBank}
          banks={bankRes.data ?? NO_BANKS}
          initial={null}
          onClose={() => setRuleCreating(false)}
          onDone={() => {
            setRuleCreating(false);
            toast.success(t('mds.common.created'));
            ruleRes.refresh();
          }}
        />
      )}
      {ruleEditing && selectedBank && (
        <RuleFormModal
          bank={selectedBank}
          banks={bankRes.data ?? NO_BANKS}
          initial={ruleEditing}
          onClose={() => setRuleEditing(null)}
          onDone={() => {
            setRuleEditing(null);
            toast.success(t('mds.common.updated'));
            ruleRes.refresh();
          }}
        />
      )}
    </Page>
  );
}

const BANK_TITLE = {
  editKey: 'mds.bank.editTitle',
  createKey: 'mds.bank.createTitle',
} as const;

const RULE_TITLE = {
  editKey: 'mds.bank.ruleEditTitle',
  createKey: 'mds.bank.ruleCreateTitle',
} as const;

/** Bank 新建/编辑弹窗（编辑锁由页面层抢/放；bankCode 建后不可改；类型走业务用途字典）。 */
function BankFormModal({
  initial,
  onClose,
  onDone,
}: {
  initial: StockerBank | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const bankTypeDict = useDict('mds.stocker_bank_type');
  // 所属设备下拉 = MATERIAL 大类设备（与页面过滤同源）
  const eqpRes = useResource(({ signal }) => api.listEquipments({ view: 'amhs' }, signal));
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [form, setForm] = useState({
    equipmentId: initial?.equipmentId ?? '',
    code: initial?.bankCode ?? '',
    name: initial?.bankName ?? '',
    bankType: initial?.bankType ?? '',
    capacity: initial?.capacity != null ? String(initial.capacity) : '',
    enabled: initial?.enabled ?? true,
  });

  const equipmentOptions = useMemo(
    () =>
      (eqpRes.data ?? NO_EQP).map((e) => ({
        value: e.id,
        label: `${e.equipmentCode} ${e.equipmentName}`,
      })),
    [eqpRes.data],
  );

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? [
            {
              kind: 'select' as const,
              key: 'equipmentId' as const,
              labelKey: 'mds.bank.equipment',
              required: true,
              options: equipmentOptions,
            },
            {
              kind: 'text' as const,
              key: 'code' as const,
              labelKey: 'mds.bank.code',
              required: true,
              mono: true,
            },
          ]
        : []),
      { kind: 'text' as const, key: 'name' as const, labelKey: 'mds.bank.name', required: true },
      {
        kind: 'select' as const,
        key: 'bankType' as const,
        labelKey: 'mds.bank.type',
        allowClear: true,
        options: bankTypeDict.items
          .filter((i) => !i.disabled)
          .map((i) => ({ value: i.value, label: i.label })),
      },
      {
        kind: 'number' as const,
        key: 'capacity' as const,
        labelKey: 'mds.bank.capacity',
      },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [initial, equipmentOptions, bankTypeDict.items],
  );

  async function submit() {
    if (!initial && (!form.equipmentId || !form.code.trim())) {
      setErr(t('mds.common.required'));
      return;
    }
    if (!form.name.trim()) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    const capacity = form.capacity === '' ? null : Number(form.capacity);
    try {
      if (initial) {
        await api.updateStockerBank(initial.id, {
          bankName: form.name.trim(),
          bankType: form.bankType || null,
          capacity,
          enabled: form.enabled,
        });
      } else {
        await api.createStockerBank({
          equipmentId: form.equipmentId,
          bankCode: form.code.trim(),
          bankName: form.name.trim(),
          bankType: form.bankType || null,
          capacity,
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
      titleKey={initial ? BANK_TITLE.editKey : BANK_TITLE.createKey}
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

/** 转运规则新建/编辑弹窗（from/to 建后不可改，只改优先级/启用；新建选方向+对端）。 */
function RuleFormModal({
  bank,
  banks,
  initial,
  onClose,
  onDone,
}: {
  bank: StockerBank;
  banks: StockerBank[];
  initial: BankTransferRule | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  // 新建：direction=outbound 表示 本Bank→对端；inbound 表示 对端→本Bank
  const [form, setForm] = useState({
    direction: 'outbound' as 'outbound' | 'inbound',
    peerBankId: '',
    priority: initial?.priority != null ? String(initial.priority) : '',
    enabled: initial?.enabled ?? true,
  });

  const peerOptions = useMemo(
    () =>
      banks
        .filter((b) => b.id !== bank.id)
        .map((b) => ({
          value: b.id,
          label: `${b.equipmentCode ?? ''}/${b.bankCode} ${b.bankName}`,
        })),
    [banks, bank.id],
  );

  const fields = useMemo<Array<FormField<typeof form>>>(
    () => [
      ...(!initial
        ? ([
            {
              kind: 'select' as const,
              key: 'direction' as const,
              labelKey: 'mds.bank.direction',
              required: true,
              options: [
                { value: 'outbound', label: t('mds.bank.outbound') },
                { value: 'inbound', label: t('mds.bank.inbound') },
              ],
            },
            {
              kind: 'select' as const,
              key: 'peerBankId' as const,
              labelKey: 'mds.bank.peer',
              required: true,
              options: peerOptions,
            },
          ] as Array<FormField<typeof form>>)
        : []),
      {
        kind: 'number' as const,
        key: 'priority' as const,
        labelKey: 'mds.bank.priority',
      },
      { kind: 'switch' as const, key: 'enabled' as const, labelKey: 'mds.topo.status' },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [initial, peerOptions],
  );

  async function submit() {
    if (!initial && !form.peerBankId) {
      setErr(t('mds.common.required'));
      return;
    }
    setBusy(true);
    setErr('');
    const priority = form.priority === '' ? null : Number(form.priority);
    try {
      if (initial) {
        await api.updateBankTransferRule(initial.id, { priority, enabled: form.enabled });
      } else {
        await api.createBankTransferRule({
          fromBankId: form.direction === 'outbound' ? bank.id : form.peerBankId,
          toBankId: form.direction === 'outbound' ? form.peerBankId : bank.id,
          priority,
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
      titleKey={initial ? RULE_TITLE.editKey : RULE_TITLE.createKey}
      onOk={() => void submit()}
      okKey="mds.common.save"
      cancelKey="mds.common.cancel"
      okLoading={busy}
      width={480}
    >
      <p className="dim bank-rule-context">{t('mds.bank.ruleContext', { name: `${bank.bankCode} ${bank.bankName}` })}</p>
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
