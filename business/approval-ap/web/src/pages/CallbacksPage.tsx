import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Panel from '@/components/Panel';
import { IconWebhook, IconPlus, IconSend, IconRefresh } from '@/components/Icons';
import { listCallbacks, registerCallback, queryOutbox, deliverMessage } from '@/lib/api';
import { toast } from '@/store/toastStore';
import { fmtDate } from '@/lib/format';
import type { CallbackRegistrationView, OutboxView } from '@/types';

function statusTag(status: string): string {
  if (status === 'SENT') return 'ok';
  if (status === 'DEAD') return 'err';
  return 'warn';
}

export default function CallbacksPage() {
  const { t } = useTranslation();
  const [regs, setRegs] = useState<CallbackRegistrationView[]>([]);
  const [outbox, setOutbox] = useState<OutboxView[]>([]);
  const [appCode, setAppCode] = useState('');
  const [baseUrl, setBaseUrl] = useState('');
  const [secret, setSecret] = useState('');
  const [busy, setBusy] = useState(false);
  const [delivering, setDelivering] = useState<string | null>(null);

  async function load() {
    try {
      const [r, o] = await Promise.all([listCallbacks(), queryOutbox()]);
      setRegs(r);
      setOutbox(o);
    } catch (e) {
      toast((e as Error).message, 'err');
    }
  }
  useEffect(() => {
    load();
  }, []);

  async function register() {
    if (!appCode.trim() || !baseUrl.trim()) {
      toast(t('common.confirm') + '：' + t('cb.form.appCode') + ' / ' + t('cb.form.baseUrl'), 'warn');
      return;
    }
    setBusy(true);
    try {
      const v = await registerCallback({ appCode: appCode.trim(), baseUrl: baseUrl.trim(), secret: secret.trim() || null });
      toast(`${t('cb.register')} ✓ ${v.appCode} → ${v.baseUrl}`, 'ok');
      setAppCode('');
      setBaseUrl('');
      setSecret('');
      await load();
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setBusy(false);
    }
  }

  async function redeliver(messageId: string) {
    setDelivering(messageId);
    try {
      await deliverMessage(messageId);
      toast(t('cb.deliver') + ' ✓', 'ok');
      await load();
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setDelivering(null);
    }
  }

  return (
    <div className="page">
      <Panel title={t('cb.registerTitle')}>
        <div className="grid-3">
          <div className="field">
            <label className="field-label">{t('cb.form.appCode')}</label>
            <input className="mono" value={appCode} onChange={(e) => setAppCode(e.target.value)} placeholder="rms-ap" />
          </div>
          <div className="field">
            <label className="field-label">{t('cb.form.baseUrl')}</label>
            <input className="mono" value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} placeholder="http://localhost:8084/api/v1/rms/callback" />
          </div>
          <div className="field">
            <label className="field-label">
              {t('cb.form.secret')} <span className="opt">HMAC</span>
            </label>
            <input className="mono" value={secret} onChange={(e) => setSecret(e.target.value)} placeholder={t('common.empty')} />
            <p className="field-hint">{t('cb.secretHint')}</p>
          </div>
        </div>
        <div className="row" style={{ marginTop: 4 }}>
          <button className="btn btn-sm" disabled={busy} onClick={register}>
            <IconPlus width={14} height={14} /> {t('cb.register')}
          </button>
        </div>
      </Panel>

      <Panel
        title={t('cb.title')}
        sub={t('cb.sub')}
        actions={
          <button className="icon-btn" onClick={load} title={t('common.refresh')} aria-label={t('common.refresh')}>
            <IconRefresh width={16} height={16} />
          </button>
        }
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('cb.colAppCode')}</th>
                <th>{t('cb.colBaseUrl')}</th>
                <th>{t('common.status')}</th>
                <th>{t('cb.colUpdated')}</th>
              </tr>
            </thead>
            <tbody>
              {regs.length === 0 && (
                <tr>
                  <td colSpan={4}>
                    <div className="empty">
                      <IconWebhook width={26} height={26} />
                      <span>{t('cb.empty')}</span>
                    </div>
                  </td>
                </tr>
              )}
              {regs.map((r) => (
                <tr key={r.id}>
                  <td className="mono">{r.appCode}</td>
                  <td className="mono">{r.baseUrl}</td>
                  <td>
                    <span className={'tag ' + (r.enabled ? 'ok' : 'err')}>
                      {r.enabled ? t('status.enabled') : t('status.disabled')}
                    </span>
                  </td>
                  <td className="mono">{fmtDate(r.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>

      <Panel title={t('cb.outbox')}>
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('cb.colMessageId')}</th>
                <th>{t('cb.colAppCode')}</th>
                <th>{t('audit.colBusinessKey')}</th>
                <th>{t('cb.colEventType')}</th>
                <th>{t('cb.colStatus')}</th>
                <th className="num">{t('cb.colAttempts')}</th>
                <th>{t('cb.colSentAt')}</th>
                <th>{t('cb.colLastError')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {outbox.length === 0 && (
                <tr>
                  <td colSpan={9}>
                    <div className="empty">
                      <span>{t('common.empty')}</span>
                    </div>
                  </td>
                </tr>
              )}
              {outbox.map((o) => (
                <tr key={o.id}>
                  <td className="mono">{o.messageId}</td>
                  <td className="mono">{o.appCode}</td>
                  <td className="mono">{o.businessKey}</td>
                  <td><span className="tag">{t('cb.event.' + o.eventType)}</span></td>
                  <td><span className={'tag ' + statusTag(o.status)}>{t('cb.status.' + o.status)}</span></td>
                  <td className="num">{o.attempts}</td>
                  <td className="mono">{fmtDate(o.sentAt)}</td>
                  <td className="mono" style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={o.lastError ?? ''}>{o.lastError ?? '—'}</td>
                  <td>
                    <div className="cell-actions">
                      {(o.status === 'PENDING' || o.status === 'DEAD') && (
                        <button className="btn btn-ghost btn-sm" disabled={delivering === o.messageId} onClick={() => redeliver(o.messageId)}>
                          <IconSend width={13} height={13} /> {delivering === o.messageId ? t('cb.delivering') : t('cb.deliver')}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>
    </div>
  );
}
