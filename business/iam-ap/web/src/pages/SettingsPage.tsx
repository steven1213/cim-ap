import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import { Can, CimSpin, CimTag, Page, Panel, useToast } from '@cim/ui';
import * as api from '@/lib/api';
import type { SettingsDto, SyncResult } from '@/types';
import { SYNC_RUN } from '@/lib/permCodes';
import { IconAlert, IconSync } from '@/components/Icons';

/**
 * 系统设置：只读展示当前生效的运行时策略参数（目录同步按钮按 `iam:sync:run` 显隐）。
 *
 * <p>页面是纯读 + 一个动作，故只把取数换成 `useResource`（加载 / 失败 / 重试三态由它承载），
 * 键值栅格保留 `dl.meta` —— 平台没有对应组件，`CimDescriptions` 的行高会让 6 组参数
 * 从「一屏读完」变成要滚动，属于为统一而牺牲可读性。</p>
 */
export default function SettingsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const [syncBusy, setSyncBusy] = useState(false);

  const sRes = useResource<SettingsDto>(({ signal }) => api.get<SettingsDto>('/admin/settings', undefined, { signal }));
  const s = sRes.data;

  async function onSync() {
    setSyncBusy(true);
    try {
      const r = await api.post<SyncResult>('/admin/sync/ad');
      if (r.skipped) {
        toast.warning(t('iam.settings.msgSkipped', { reason: r.reason }));
      } else if (r.reason) {
        toast.info(r.reason);
      } else {
        toast.success(
          t('iam.settings.msgDone', { orgs: r.orgCount, users: r.userCount, deactivated: r.deactivated }),
        );
      }
    } catch (e: any) {
      toast.error(e?.msg || t('iam.settings.errSync'));
    } finally {
      setSyncBusy(false);
    }
  }

  if (sRes.error) {
    return (
      <Page>
        <Panel title={t('iam.settings.title')}>
          <div className="alert err">
            <IconAlert width={15} height={15} />
            <span>{sRes.error.message || t('iam.settings.errLoad')}</span>
          </div>
          <button className="btn-sm" style={{ marginTop: 12 }} onClick={sRes.refresh}>
            {t('iam.common.refresh')}
          </button>
        </Panel>
      </Page>
    );
  }

  if (!s) {
    return (
      <Page>
        <Panel title={t('iam.settings.title')}>
          <CimSpin block height={240} />
        </Panel>
      </Page>
    );
  }

  return (
    <Page>
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
                <CimTag size="small" tone="ok">{t('iam.settings.configured')}</CimTag>
              ) : (
                <CimTag size="small" tone="err">{t('iam.settings.pepperMissing')}</CimTag>
              )}
            </dd>
            <dt className="meta-k">{t('iam.settings.mk.bootstrap')}</dt>
            <dd className="meta-v">
              {s.bootstrapEnabled ? (
                <CimTag size="small" tone="ok">{t('iam.settings.enabled')}</CimTag>
              ) : (
                <CimTag size="small">{t('iam.settings.disabled')}</CimTag>
              )}
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
              {s.rsaKeyInjected ? (
                <CimTag size="small" tone="ok">{t('iam.settings.kms')}</CimTag>
              ) : (
                <CimTag size="small" tone="warn">{t('iam.settings.rsaTemp')}</CimTag>
              )}
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
                <CimTag key={o} size="small">
                  <span className="mono">{o}</span>
                </CimTag>
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
                <CimTag size="small" tone="ok">{t('iam.settings.dirEnabled')}</CimTag>
              ) : (
                <CimTag size="small" tone="err">{t('iam.settings.dirDisabled')}</CimTag>
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
              {s.adSyncEnabled ? (
                <CimTag size="small" tone="ok">{t('iam.settings.on')}</CimTag>
              ) : (
                <CimTag size="small">{t('iam.common.close')}</CimTag>
              )}
            </dd>
            <dt className="meta-k">{t('iam.settings.mk.connCfg')}</dt>
            <dd className="meta-v">
              {s.adSyncConfigured ? (
                <CimTag size="small" tone="ok">{t('iam.settings.ready')}</CimTag>
              ) : (
                <CimTag size="small" tone="warn">{t('iam.settings.syncNotCfg')}</CimTag>
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
            <Can perm={SYNC_RUN}>
              <button type="button" className="btn-sm" onClick={() => void onSync()} disabled={syncBusy}>
                <IconSync width={13} height={13} />
                {syncBusy ? t('iam.settings.syncing') : t('iam.settings.sync')}
              </button>
            </Can>
          </div>
        </Panel>
      </div>

      <Panel title={t('iam.settings.noteTitle')} sub={t('iam.settings.noteSub')}>
        <ul className="note-list">
          <li>
            <b>{t('iam.settings.note1')}</b>
          </li>
          <li>{t('iam.settings.note2')}</li>
          <li>{t('iam.settings.note3')}</li>
        </ul>
      </Panel>
    </Page>
  );
}
