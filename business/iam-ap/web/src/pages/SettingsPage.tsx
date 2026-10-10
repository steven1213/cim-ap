import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { SettingsDto, SyncResult } from '@/types';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import { SYNC_RUN } from '@/lib/permCodes';
import { IconAlert, IconSliders, IconSync } from '@/components/Icons';

/** 系统设置：只读展示当前生效的运行时策略参数（目录同步按钮按 `iam:sync:run` 显隐）。 */
export default function SettingsPage() {
  const { t } = useTranslation();
  const [s, setS] = useState<SettingsDto | null>(null);
  const [err, setErr] = useState('');
  const [syncBusy, setSyncBusy] = useState(false);
  const [syncMsg, setSyncMsg] = useState('');

  useEffect(() => {
    api.get<SettingsDto>('/admin/settings').then(setS).catch((e: any) => setErr(e?.msg || t('iam.settings.errLoad')));
  }, []);

  async function onSync() {
    setErr('');
    setSyncBusy(true);
    try {
      const r = await api.post<SyncResult>('/admin/sync/ad');
      if (r.skipped) {
        setSyncMsg(t('iam.settings.msgSkipped', { reason: r.reason }));
      } else if (r.reason) {
        setSyncMsg(r.reason);
      } else {
        setSyncMsg(t('iam.settings.msgDone', { orgs: r.orgCount, users: r.userCount, deactivated: r.deactivated }));
      }
    } catch (e: any) {
      setErr(e?.msg || t('iam.settings.errSync'));
    } finally {
      setSyncBusy(false);
    }
  }

  if (err) {
    return (
      <div className="page">
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      </div>
    );
  }

  if (!s) {
    return (
      <Panel title={t('iam.settings.title')}>
        <div className="empty">
          <IconSliders width={22} height={22} />
          <b>{t('iam.settings.loading')}</b>
        </div>
      </Panel>
    );
  }

  return (
    <div className="page">
      <div className="grid-2">
        <Panel title={t('iam.settings.authTitle')} sub={t('iam.settings.authSub')}>
          <dl className="meta">
            <dt className="meta-k">{t('iam.settings.mk.authSrc')}</dt>
            <dd className="meta-v mono">{s.authSource}</dd>
            <dt className="meta-k">{t('iam.settings.mk.pbkdf2Rounds')}</dt>
            <dd className="meta-v mono">{s.passwordRounds.toLocaleString()}</dd>
            <dt className="meta-k">{t('iam.settings.mk.pepper')}</dt>
            <dd className="meta-v">
              {s.pepperConfigured ? (
                <span className="tag ok">{t('iam.settings.configured')}</span>
              ) : (
                <span className="tag err">{t('iam.settings.pepperMissing')}</span>
              )}
            </dd>
            <dt className="meta-k">{t('iam.settings.mk.bootstrap')}</dt>
            <dd className="meta-v">
              {s.bootstrapEnabled ? <span className="tag ok">{t('iam.settings.enabled')}</span> : <span className="tag">{t('iam.settings.disabled')}</span>}
              <span className="mono"> · {s.bootstrapAdminUsername}</span>
            </dd>
          </dl>
        </Panel>

        <Panel title={t('iam.settings.tokenTitle')} sub={t('iam.settings.tokenSub')}>
          <dl className="meta">
            <dt className="meta-k">{t('iam.settings.mk.accessTtl')}</dt>
            <dd className="meta-v mono">{t('iam.settings.v.minutes', { n: s.accessTokenTtlMinutes })}</dd>
            <dt className="meta-k">{t('iam.settings.mk.refreshTtl')}</dt>
            <dd className="meta-v mono">{t('iam.settings.v.days', { n: Math.round(s.refreshTokenTtlMinutes / 1440) })}</dd>
            <dt className="meta-k">issuer / kid</dt>
            <dd className="meta-v mono">
              {s.jwtIssuer} / {s.jwtKid}
            </dd>
            <dt className="meta-k">{t('iam.settings.mk.rsaSrc')}</dt>
            <dd className="meta-v">
              {s.rsaKeyInjected ? <span className="tag ok">{t('iam.settings.kms')}</span> : <span className="tag warn">{t('iam.settings.rsaTemp')}</span>}
            </dd>
            <dt className="meta-k">{t('iam.settings.mk.jwks')}</dt>
            <dd className="meta-v mono">{s.jwksPath}</dd>
          </dl>
        </Panel>
      </div>

      <div className="grid-2">
        <Panel title={t('iam.settings.lockTitle')} sub={t('iam.settings.lockSub')}>
          <dl className="meta">
            <dt className="meta-k">{t('iam.settings.mk.threshold')}</dt>
            <dd className="meta-v mono">{t('iam.settings.v.times', { n: s.lockoutMaxAttempts })}</dd>
            <dt className="meta-k">{t('iam.settings.mk.lockMin')}</dt>
            <dd className="meta-v mono">{t('iam.settings.v.minutes', { n: s.lockoutLockMinutes })}</dd>
            <dt className="meta-k">{t('iam.settings.mk.window')}</dt>
            <dd className="meta-v mono">{t('iam.settings.v.minutes', { n: s.lockoutWindowMinutes })}</dd>
          </dl>
        </Panel>

        <Panel title={t('iam.settings.corsTitle')} sub={t('iam.settings.corsSub')}>
          {s.webAllowedOrigins?.length ? (
            <div className="tag-list">
              {s.webAllowedOrigins.map((o) => (
                <span key={o} className="tag mono">
                  {o}
                </span>
              ))}
            </div>
          ) : (
            <span className="dim">{t('iam.settings.notConfigured')}</span>
          )}
        </Panel>
      </div>

      <div className="grid-2">
        <Panel title={t('iam.settings.dirApiTitle')} sub={t('iam.settings.dirApiSub')}>
          <dl className="meta">
            <dt className="meta-k">{t('iam.settings.mk.dirApi')}</dt>
            <dd className="meta-v">
              {s.directoryApiEnabled ? (
                <span className="tag ok">{t('iam.settings.dirEnabled')}</span>
              ) : (
                <span className="tag err">{t('iam.settings.dirDisabled')}</span>
              )}
            </dd>
            <dt className="meta-k">{t('iam.settings.mk.authMethod')}</dt>
            <dd className="meta-v mono">{t('iam.settings.v.dirKey')}</dd>
            <dt className="meta-k">{t('iam.settings.mk.dirEp')}</dt>
            <dd className="meta-v mono">/api/v1/directory/*</dd>
            <dt className="meta-k">{t('iam.settings.mk.watermark')}</dt>
            <dd className="meta-v">{t('iam.settings.v.watermark')}</dd>
          </dl>
        </Panel>

        <Panel title={t('iam.settings.adSyncTitle')} sub={t('iam.settings.adSyncSub')}>
          <dl className="meta">
            <dt className="meta-k">{t('iam.settings.mk.syncSwitch')}</dt>
            <dd className="meta-v">
              {s.adSyncEnabled ? <span className="tag ok">{t('iam.settings.on')}</span> : <span className="tag">{t('iam.common.close')}</span>}
            </dd>
            <dt className="meta-k">{t('iam.settings.mk.connCfg')}</dt>
            <dd className="meta-v">
              {s.adSyncConfigured ? (
                <span className="tag ok">{t('iam.settings.ready')}</span>
              ) : (
                <span className="tag warn">{t('iam.settings.syncNotCfg')}</span>
              )}
            </dd>
            <dt className="meta-k">{t('iam.settings.mk.baseDn')}</dt>
            <dd className="meta-v mono">{s.adBaseDn || '—'}</dd>
            <dt className="meta-k">{t('iam.settings.mk.interval')}</dt>
            <dd className="meta-v mono">{t('iam.settings.v.seconds', { n: Math.round(s.adSyncIntervalMs / 1000) })}</dd>
            <dt className="meta-k">{t('iam.settings.mk.crossSrc')}</dt>
            <dd className="meta-v">{t('iam.settings.v.crossSrc')}</dd>
          </dl>
          <div className="row" style={{ marginTop: 12 }}>
            <Perms code={SYNC_RUN}>
              <button type="button" className="btn-sm" onClick={onSync} disabled={syncBusy}>
                <IconSync width={13} height={13} />
                {syncBusy ? t('iam.settings.syncing') : t('iam.settings.sync')}
              </button>
            </Perms>
            {syncMsg && <span className="hint" style={{ margin: 0 }}>{syncMsg}</span>}
          </div>
        </Panel>
      </div>

      <Panel title={t('iam.settings.noteTitle')} sub={t('iam.settings.noteSub')}>
        <ul className="note-list">
          <li><b>{t('iam.settings.note1')}</b></li>
          <li>{t('iam.settings.note2')}</li>
          <li>{t('iam.settings.note3')}</li>
        </ul>
      </Panel>
    </div>
  );
}
