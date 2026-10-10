import { useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { AppRegistration, AppStatus } from '@/types';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import { APP_CREATE, APP_UPDATE } from '@/lib/permCodes';
import {
  IconAlert,
  IconApps,
  IconCheckCircle,
  IconPower,
  IconRefresh,
} from '@/components/Icons';

/** 应用注册：登记业务接入码、维护状态（按钮按 `iam:app:*` 权限显隐）。准入分配见「准入授权」。 */
export default function AppMgmtPage() {
  const { t } = useTranslation();
  const [apps, setApps] = useState<AppRegistration[]>([]);
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);

  const [appCode, setAppCode] = useState('');
  const [appName, setAppName] = useState('');
  const [sortNo, setSortNo] = useState(0);

  async function loadApps() {
    setLoading(true);
    setErr('');
    try {
      setApps(await api.get<AppRegistration[]>('/apps'));
    } catch (e: any) {
      setErr(e?.msg || t('iam.appMgmt.errLoad'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadApps();
  }, []);

  function flash(t: string) {
    setMsg(t);
    setTimeout(() => setMsg(''), 3000);
  }

  async function onRegister(e: FormEvent) {
    e.preventDefault();
    setErr('');
    setBusy(true);
    try {
      await api.post<AppRegistration>('/apps', { appCode: appCode.trim(), appName, sortNo: Number(sortNo) });
      flash(t('iam.appMgmt.msgRegistered', { code: appCode }));
      setAppCode('');
      setAppName('');
      setSortNo(0);
      await loadApps();
    } catch (e: any) {
      setErr(e?.msg || t('iam.appMgmt.errRegister'));
    } finally {
      setBusy(false);
    }
  }

  async function toggleStatus(a: AppRegistration) {
    setErr('');
    const next: AppStatus = a.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    if (next === 'DISABLED' && !window.confirm(t('iam.appMgmt.confirmDisable', { code: a.appCode }))) {
      return;
    }
    try {
      await api.put(`/apps/${encodeURIComponent(a.appCode)}`, { appName: a.appName, status: next });
      flash(t('iam.appMgmt.msgToggled', { code: a.appCode, state: next === 'ENABLED' ? t('iam.appMgmt.stateEnabled') : t('iam.appMgmt.stateDisabled') }));
      await loadApps();
    } catch (e: any) {
      setErr(e?.msg || t('iam.appMgmt.errUpdate'));
    }
  }

  const enabled = apps.filter((a) => a.status === 'ENABLED').length;

  return (
    <div className="page">
      {err && (
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      )}
      {msg && (
        <div className="alert ok">
          <IconCheckCircle width={15} height={15} />
          <span>{msg}</span>
        </div>
      )}

      <Panel
        title={t('iam.appMgmt.title')}
        sub={t('iam.appMgmt.sub')}
        flush
        actions={
          <>
            <span className="tag">{t('iam.appMgmt.count', { n: apps.length })}</span>
            <span className="tag ok">{t('iam.appMgmt.enabled', { n: enabled })}</span>
            <button className="btn-ghost btn-sm" onClick={loadApps} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? t('iam.appMgmt.refreshing') : t('iam.appMgmt.refresh')}
            </button>
          </>
        }
      >
        {apps.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>{t('iam.appMgmt.th.appCode')}</th>
                  <th>{t('iam.appMgmt.th.name')}</th>
                  <th style={{ width: 100 }}>{t('iam.appMgmt.th.status')}</th>
                  <th className="num" style={{ width: 80 }}>
                    {t('iam.appMgmt.th.sort')}
                  </th>
                  <th style={{ width: 120 }}>{t('iam.appMgmt.th.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {apps.map((a) => (
                  <tr key={a.id}>
                    <td className="mono">{a.appCode}</td>
                    <td>{a.appName}</td>
                    <td>
                      <span className="status">
                        <i className={'led ' + (a.status === 'ENABLED' ? 'ok' : 'err')} />
                        <b>{a.status === 'ENABLED' ? t('iam.appMgmt.enabled2') : t('iam.appMgmt.disabled2')}</b>
                      </span>
                    </td>
                    <td className="num">{a.sortNo}</td>
                    <td>
                      <Perms code={APP_UPDATE}>
                        <button className="btn-ghost btn-sm" onClick={() => toggleStatus(a)}>
                          <IconPower width={13} height={13} />
                          {a.status === 'ENABLED' ? t('iam.appMgmt.disable') : t('iam.appMgmt.enable')}
                        </button>
                      </Perms>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconApps width={22} height={22} />
            <b>{t('iam.appMgmt.emptyTitle')}</b>
            <span>{t('iam.appMgmt.emptyHint')}</span>
          </div>
        )}
      </Panel>

      <Perms code={APP_CREATE}>
        <Panel title={t('iam.appMgmt.registerTitle')} sub={t('iam.appMgmt.registerSub')}>
          <form onSubmit={onRegister}>
          <div className="form-grid">
            <div className="field">
              <label className="field-label" htmlFor="reg-code">
                {t('iam.appMgmt.f.appCode')}
              </label>
              <input
                id="reg-code"
                className="mono"
                placeholder="mds-ap"
                value={appCode}
                onChange={(e) => setAppCode(e.target.value)}
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="reg-name">
                {t('iam.appMgmt.f.name')}
              </label>
              <input
                id="reg-name"
                placeholder={t('iam.appMgmt.namePH')}
                value={appName}
                onChange={(e) => setAppName(e.target.value)}
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="reg-sort">
                {t('iam.appMgmt.f.sort')}
              </label>
              <input
                id="reg-sort"
                type="number"
                placeholder="0"
                value={sortNo}
                onChange={(e) => setSortNo(Number(e.target.value))}
              />
            </div>
          </div>
          <p className="hint">{t('iam.appMgmt.hint')}</p>
          <button type="submit" className="btn-block" disabled={busy || !appCode.trim() || !appName} style={{ marginTop: 12 }}>
            {t('iam.appMgmt.register')}
          </button>
          </form>
        </Panel>
      </Perms>

      <Panel title={t('iam.appMgmt.guideTitle')} sub={t('iam.appMgmt.guideSub')}>
        <ul className="note-list">
          <li>{t('iam.appMgmt.guide1')}</li>
          <li>{t('iam.appMgmt.guide2')}</li>
          <li>{t('iam.appMgmt.guide3')}</li>
        </ul>
      </Panel>
    </div>
  );
}
