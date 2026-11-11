import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimModal,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  Panel,
  useToast,
  type DataTableColumn,
  type FormField,
} from '@cim/ui';
import type { StatusTone } from '@cim/theme';
import { IconSend } from '@/components/Icons';
import {
  deliverMessage,
  getOutboxStats,
  listCallbacks,
  queryOutbox,
  registerCallback,
  testCallback,
} from '@/lib/api';
import { fmtDate } from '@/lib/format';
import type { CallbackRegistrationRequest, CallbackRegistrationView, OutboxView } from '@/types';

/** 投递状态 → 语义档。 */
function deliveryTone(status: string): StatusTone {
  if (status === 'SENT') return 'ok';
  if (status === 'DEAD') return 'err';
  return 'warn';
}

interface Bundle {
  regs: CallbackRegistrationView[];
  outbox: OutboxView[];
  /** 统计是「锦上添花」，取不到就整块不显示，不该拖垮两个主表。 */
  stats: Record<string, number> | null;
}

interface RegisterForm {
  appCode: string;
  baseUrl: string;
  secret: string;
}

const EMPTY_FORM: RegisterForm = { appCode: '', baseUrl: '', secret: '' };

export default function CallbacksPage() {
  const { t } = useTranslation();
  const toast = useToast();

  const [form, setForm] = useState<RegisterForm>(EMPTY_FORM);
  const [errors, setErrors] = useState<Partial<Record<keyof RegisterForm, string>>>({});
  const [busy, setBusy] = useState(false);
  /** 逐行锁：只锁被点的那行，别把整张表变灰。 */
  const [testing, setTesting] = useState<string | null>(null);
  const [delivering, setDelivering] = useState<string | null>(null);
  const [overwrite, setOverwrite] = useState<
    { appCode: string; baseUrl: string; secret: string; token: string; existingUrl: string } | null
  >(null);

  /**
   * 三份数据合成**一个**资源。
   *
   * <p>注册 / 重投 / 测试之后都要同时刷新三块；拆三个 `useResource` 就会出现
   * 「表格已刷新、监控还没动」的中间态，且要写三次 refresh。三者也是同一屏出现的，
   * 分成三个 loading 只会让页面分三次闪。</p>
   */
  const cbRes = useResource<Bundle>(async ({ signal }) => {
    const [regs, outbox, stats] = await Promise.all([
      listCallbacks({ signal }),
      queryOutbox(undefined, undefined, { signal }),
      getOutboxStats({ signal }).catch(() => null),
    ]);
    return { regs, outbox, stats };
  });

  const regs = cbRes.data?.regs ?? [];
  const outbox = cbRes.data?.outbox ?? [];
  const stats = cbRes.data?.stats ?? null;

  const fields: Array<FormField<RegisterForm>> = [
    { key: 'appCode', labelKey: 'cb.form.appCode', kind: 'text', required: true, placeholder: 'rms-ap', mono: true },
    {
      key: 'baseUrl',
      labelKey: 'cb.form.baseUrl',
      kind: 'text',
      required: true,
      placeholder: 'http://localhost:8084/api/v1/rms/callback',
      mono: true,
    },
    { key: 'secret', labelKey: 'cb.form.secret', kind: 'text', mono: true, hintKey: 'cb.secretHint' },
  ];

  function validate(): boolean {
    const next: Partial<Record<keyof RegisterForm, string>> = {};
    if (!form.appCode.trim()) next.appCode = t('cb.form.appCode');
    if (!form.baseUrl.trim()) next.baseUrl = t('cb.form.baseUrl');
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function doRegister(req: CallbackRegistrationRequest, token: string | null) {
    setBusy(true);
    try {
      const v = await registerCallback({ ...req, confirmToken: token ?? undefined });
      // G2 二次确认：同 appCode 首调只签发令牌（confirmRequired），回填令牌后再提交才覆盖
      if (v.confirmRequired && v.confirmToken) {
        setOverwrite({
          appCode: req.appCode,
          baseUrl: req.baseUrl,
          secret: req.secret ?? '',
          token: v.confirmToken,
          existingUrl: v.baseUrl,
        });
        return;
      }
      toast.success(`${t('cb.register')} ✓ ${v.appCode} → ${v.baseUrl}`);
      setForm(EMPTY_FORM);
      setErrors({});
      setOverwrite(null);
      cbRes.refresh();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function register() {
    if (!validate()) return;
    void doRegister(
      { appCode: form.appCode.trim(), baseUrl: form.baseUrl.trim(), secret: form.secret.trim() || null },
      null,
    );
  }

  async function redeliver(messageId: string) {
    setDelivering(messageId);
    try {
      await deliverMessage(messageId);
      toast.success(t('cb.deliver') + ' ✓');
      cbRes.refresh();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setDelivering(null);
    }
  }

  async function runTest(appCode: string) {
    setTesting(appCode);
    try {
      const res = await testCallback(appCode);
      if (res.status === 'SENT') toast.success(t('cb.testOk'));
      else toast.error(`${t('cb.testFail')}：${res.lastError ?? res.status}`);
      cbRes.refresh();
    } catch (e) {
      toast.error((e as Error).message);
    } finally {
      setTesting(null);
    }
  }

  const regColumns: Array<DataTableColumn<CallbackRegistrationView>> = [
    { key: 'appCode', titleKey: 'cb.colAppCode', width: 140, mono: true },
    { key: 'baseUrl', titleKey: 'cb.colBaseUrl', mono: true, ellipsis: true },
    {
      key: 'enabled',
      titleKey: 'common.status',
      width: 100,
      render: (r) => (
        <CimTag tone={r.enabled ? 'ok' : 'err'}>{t(r.enabled ? 'status.enabled' : 'status.disabled')}</CimTag>
      ),
    },
    {
      key: 'ops',
      titleKey: 'common.actions',
      width: 100,
      render: (r) => (
        <CimButton size="small" loading={testing === r.appCode} onClick={() => runTest(r.appCode)}>
          {t('cb.test')}
        </CimButton>
      ),
    },
    { key: 'updatedAt', titleKey: 'cb.colUpdated', width: 170, mono: true, render: (r) => fmtDate(r.updatedAt) },
  ];

  const outboxColumns: Array<DataTableColumn<OutboxView>> = [
    { key: 'messageId', titleKey: 'cb.colMessageId', width: 130, mono: true },
    { key: 'appCode', titleKey: 'cb.colAppCode', width: 110, mono: true },
    { key: 'businessKey', titleKey: 'audit.colBusinessKey', width: 150, mono: true },
    {
      key: 'eventType',
      titleKey: 'cb.colEventType',
      width: 130,
      render: (o) => <CimTag>{t('cb.event.' + o.eventType)}</CimTag>,
    },
    {
      key: 'status',
      titleKey: 'cb.colStatus',
      width: 110,
      render: (o) => <CimTag tone={deliveryTone(o.status)}>{t('cb.status.' + o.status)}</CimTag>,
    },
    { key: 'attempts', titleKey: 'cb.colAttempts', width: 90, align: 'right' },
    { key: 'sentAt', titleKey: 'cb.colSentAt', width: 170, mono: true, render: (o) => fmtDate(o.sentAt) },
    {
      key: 'lastError',
      titleKey: 'cb.colLastError',
      mono: true,
      ellipsis: true,
      render: (o) => o.lastError ?? '—',
    },
    {
      key: 'ops',
      titleKey: 'common.actions',
      width: 110,
      render: (o) =>
        o.status === 'PENDING' || o.status === 'DEAD' ? (
          <CimButton
            size="small"
            icon={<IconSend width={13} height={13} />}
            loading={delivering === o.messageId}
            onClick={() => redeliver(o.messageId)}
          >
            {t('cb.deliver')}
          </CimButton>
        ) : null,
    },
  ];

  return (
    <Page>
      <Panel titleKey="cb.registerTitle">
        <FormGrid<RegisterForm>
          fields={fields}
          value={form}
          errors={errors}
          columns={3}
          onChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
        />
        <div className="row" style={{ marginTop: 10 }}>
          <CimButton type="primary" size="small" loading={busy} onClick={register}>
            {t('cb.register')}
          </CimButton>
        </div>
      </Panel>

      {stats && (
        <Panel titleKey="cb.stats.title" subKey="cb.stats.sub">
          <div className="kpi-grid">
            <div className="kpi">
              <span className="kpi-main">
                <span className="kpi-label">{t('cb.stats.sent')}</span>
                <span className="kpi-val">{stats.sent ?? '—'}</span>
              </span>
            </div>
            <div className="kpi warn">
              <span className="kpi-main">
                <span className="kpi-label">{t('cb.stats.pending')}</span>
                <span className="kpi-val">{stats.pending ?? '—'}</span>
              </span>
            </div>
            <div className={'kpi' + (stats.dead > 0 ? ' err' : '')}>
              <span className="kpi-main">
                <span className="kpi-label">{t('cb.stats.dead')}</span>
                <span className="kpi-val">{stats.dead ?? '—'}</span>
              </span>
            </div>
            <div className="kpi">
              <span className="kpi-main">
                <span className="kpi-label">{t('cb.stats.rate')}</span>
                <span className="kpi-val">
                  {stats.successRate != null ? (stats.successRate * 100).toFixed(0) + '%' : '—'}
                </span>
              </span>
            </div>
            <div className="kpi">
              <span className="kpi-main">
                <span className="kpi-label">{t('cb.stats.cost')}</span>
                <span className="kpi-val">{stats.avgCostMs > 0 ? (stats.avgCostMs / 1000).toFixed(1) + ' s' : '—'}</span>
              </span>
            </div>
          </div>
        </Panel>
      )}

      <Panel titleKey="cb.title" subKey="cb.sub" flush>
        <DataTable<CallbackRegistrationView>
          flat
          columns={regColumns}
          rows={regs}
          rowKey={(r) => r.id}
          loading={cbRes.loading}
          error={cbRes.error ? cbRes.error.message : null}
          onRetry={cbRes.refresh}
          onRefresh={cbRes.refresh}
          emptyTitleKey="cb.empty"
          scrollX="max-content"
        />
      </Panel>

      <Panel titleKey="cb.outbox" flush>
        <DataTable<OutboxView>
          flat
          columns={outboxColumns}
          rows={outbox}
          rowKey={(o) => o.id}
          loading={cbRes.loading}
          emptyTitleKey="common.empty"
          scrollX="max-content"
        />
      </Panel>

      <CimModal
        open={!!overwrite}
        onClose={() => setOverwrite(null)}
        title={t('cb.confirmOverwrite')}
        onOk={() => {
          if (!overwrite) return;
          void doRegister(
            { appCode: overwrite.appCode, baseUrl: overwrite.baseUrl, secret: overwrite.secret || null },
            overwrite.token,
          );
        }}
        okLoading={busy}
        okKey="common.confirm"
        cancelKey="common.cancel"
      >
        <p>
          {overwrite
            ? t('cb.confirmOverwriteBody', { appCode: overwrite.appCode, baseUrl: overwrite.existingUrl })
            : ''}
        </p>
      </CimModal>
    </Page>
  );
}
