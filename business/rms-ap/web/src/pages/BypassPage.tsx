import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimModal,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  PageHead,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import { BYPASS_MANAGE } from '@/lib/permCodes';
import type { Bypass, Device, Recipe } from '@/types';

/**
 * Bypass 豁免页（FR-B1~B3，Req 22/23）。
 *
 * 状态用 `CimTag` 的语义档表达（ok/err/warn），不再手写 `className="tag ok"`：
 * 语义色只有平台那一份 tokens，切明暗主题时自动跟随。
 */
export default function BypassPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const [show, setShow] = useState(false);

  const { data, loading, error, refresh } = useResource(() => api.listBypasses());
  const rows = data ?? [];

  const doToggle = async (x: Bypass) => {
    try {
      await api.setBypassActive(x.id, !x.active);
      toast.success(t(x.active ? 'rms.common.disabled' : 'rms.common.enabled'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const doDelete = async (x: Bypass) => {
    const ok = await confirm({
      title: t('rms.common.delete'),
      contentKey: 'rms.common.confirmDelete',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteBypass(x.id);
      toast.success(t('rms.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const statusTone = (s: Bypass['status']) =>
    s === 'ACTIVE' ? 'ok' : s === 'EXPIRED' || s === 'REVOKED' ? 'err' : 'warn';

  const columns = useMemo<Array<DataTableColumn<Bypass>>>(
    () => [
      {
        key: 'scope',
        titleKey: 'rms.bypass.scope',
        width: 110,
        render: (r) => t(`rms.bypass.scope.${r.scope}`),
      },
      {
        key: 'target',
        titleKey: 'rms.bypass.target',
        width: 200,
        render: (r) => <span className="cim-mono">{r.targetLabel ?? r.targetId}</span>,
      },
      {
        key: 'bypassType',
        titleKey: 'rms.bypass.bypassType',
        width: 130,
        render: (r) => t(`rms.bypass.type.${r.bypassType}`),
      },
      {
        key: 'reason',
        titleKey: 'rms.bypass.reason',
        render: (r) => <span className="muted">{r.reason}</span>,
      },
      {
        key: 'status',
        titleKey: 'rms.bypass.status',
        width: 100,
        render: (r) => (
          <CimTag tone={statusTone(r.status)}>{t(`rms.bypass.status.${r.status}`)}</CimTag>
        ),
      },
      {
        key: 'window',
        titleKey: 'rms.bypass.effectiveFrom',
        width: 260,
        render: (r) => (
          <span className="muted">
            {r.effectiveFrom ?? '—'} ~ {r.effectiveTo ?? '∞'}
          </span>
        ),
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 160,
        render: (r) => (
          <span className="cell-actions">
            <Perms code={BYPASS_MANAGE}>
              <button className="btn btn-ghost btn-sm" onClick={() => void doToggle(r)}>
                {r.active ? t('rms.common.disable') : t('rms.common.enable')}
              </button>
              <button className="btn btn-ghost btn-sm danger" onClick={() => void doDelete(r)}>
                {t('rms.common.delete')}
              </button>
            </Perms>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.bypass.title"
        subtitleKey="rms.bypass.sub"
        actions={
          <Perms code={BYPASS_MANAGE}>
            <button className="btn btn-primary btn-sm" onClick={() => setShow(true)}>
              + {t('rms.bypass.create')}
            </button>
          </Perms>
        }
      />

      <DataTable<Bypass>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={loading}
        error={error?.message}
        onRetry={refresh}
        onRefresh={refresh}
        settingsKey="rms.bypass"
        emptyTitleKey="rms.bypass.empty"
      />

      {show && (
        <BypassModal
          onClose={() => setShow(false)}
          onDone={() => {
            setShow(false);
            toast.success(t('rms.common.created'));
            refresh();
          }}
        />
      )}
    </Page>
  );
}

interface BypassForm {
  scope: string;
  targetId: string;
  bypassType: string;
  reason: string;
  effectiveFrom: string;
  effectiveTo: string;
}

function BypassModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [form, setForm] = useState<BypassForm>({
    scope: 'DEVICE',
    targetId: '',
    bypassType: 'BOTH',
    reason: '',
    effectiveFrom: '',
    effectiveTo: '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof BypassForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  // 两个下拉的数据源各自取一次；取数失败不阻塞弹窗（选不到就是空列表）
  const { data: devices } = useResource(() => api.listDevices());
  const { data: recipes } = useResource(() => api.listRecipes());

  const targetOptions = useMemo(() => {
    const source: Array<Device | Recipe> = form.scope === 'DEVICE' ? devices ?? [] : recipes ?? [];
    return source.map((x) => ({ value: x.id, label: x.code }));
  }, [form.scope, devices, recipes]);

  const fields = useMemo<Array<FormField<BypassForm>>>(
    () => [
      {
        kind: 'select',
        key: 'scope',
        labelKey: 'rms.bypass.scope',
        required: true,
        allowClear: false,
        options: [
          { value: 'DEVICE', labelKey: 'rms.bypass.scope.DEVICE' },
          { value: 'RECIPE', labelKey: 'rms.bypass.scope.RECIPE' },
        ],
      },
      {
        kind: 'select',
        key: 'targetId',
        labelKey: 'rms.bypass.target',
        required: true,
        options: targetOptions,
        placeholderKey: 'rms.common.select',
      },
      {
        kind: 'select',
        key: 'bypassType',
        labelKey: 'rms.bypass.bypassType',
        allowClear: false,
        options: [
          { value: 'COMPARE', labelKey: 'rms.bypass.type.COMPARE' },
          { value: 'PARAM_CHECK', labelKey: 'rms.bypass.type.PARAM_CHECK' },
          { value: 'BOTH', labelKey: 'rms.bypass.type.BOTH' },
        ],
      },
      {
        kind: 'datetime',
        key: 'effectiveFrom',
        labelKey: 'rms.bypass.effectiveFrom',
        // 后端 `Instant.parse` / `LocalDateTime.parse` 只认 ISO 的 T 分隔；显示仍走空格
        valueFormat: 'YYYY-MM-DDTHH:mm',
        displayFormat: 'YYYY-MM-DD HH:mm',
      },
      {
        kind: 'datetime',
        key: 'effectiveTo',
        labelKey: 'rms.bypass.effectiveTo',
        valueFormat: 'YYYY-MM-DDTHH:mm',
        displayFormat: 'YYYY-MM-DD HH:mm',
      },
      { kind: 'text', key: 'reason', labelKey: 'rms.bypass.reason', required: true, span: 2 },
    ],
    [targetOptions],
  );

  async function submit() {
    const next: Partial<Record<keyof BypassForm, string>> = {};
    if (!form.targetId) next.targetId = t('rms.common.required');
    if (!form.reason) next.reason = t('rms.common.required');
    setFieldErrors(next);
    if (Object.keys(next).length) return;

    setBusy(true);
    setErr('');
    try {
      await api.createBypass({
        scope: form.scope as 'DEVICE' | 'RECIPE',
        targetId: form.targetId,
        bypassType: form.bypassType as 'COMPARE' | 'PARAM_CHECK' | 'BOTH',
        reason: form.reason,
        effectiveFrom: form.effectiveFrom || null,
        effectiveTo: form.effectiveTo || null,
      });
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
      titleKey="rms.bypass.create"
      onOk={() => void submit()}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      okLoading={busy}
      width={640}
    >
      <FormGrid<BypassForm>
        fields={fields}
        value={form}
        // 切 scope 必须清空 targetId：不清会带着上一种目标类型的 id 提交，后端按另一张表查不到
        onChange={(patch) =>
          setForm((prev) => ({ ...prev, ...patch, ...(patch.scope ? { targetId: '' } : {}) }))
        }
        errors={fieldErrors}
        columns={2}
      />
      {err ? (
        <p className="cim-field-error" style={{ marginTop: 10 }}>
          {err}
        </p>
      ) : null}
    </CimModal>
  );
}
