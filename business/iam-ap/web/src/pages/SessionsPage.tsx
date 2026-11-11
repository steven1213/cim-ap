import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimInput,
  CimTag,
  DataTable,
  Page,
  Panel,
  useConfirm,
  useToast,
  type DataTableColumn,} from '@cim/ui';
import * as api from '@/lib/api';
import type { KickResult, SessionRow, TokenVersionBump } from '@/types';
import { fmtDateTime } from '@/lib/format';
import { SESSION_KICK } from '@/lib/permCodes';

/** 在线会话：活跃会话列表 + 强制下线（bump 令牌版本）。下线按钮按 `iam:session:kick` 显隐。 */
export default function SessionsPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [kicking, setKicking] = useState<string | null>(null);
  const [uid, setUid] = useState('');
  const [bump, setBump] = useState<TokenVersionBump | null>(null);
  const [busy, setBusy] = useState(false);

  const listRes = useResource<SessionRow[]>(({ signal }) => api.get<SessionRow[]>('/admin/sessions', undefined, { signal }));
  const rows = listRes.data ?? [];

  async function kick(userId: string, username: string) {
    const ok = await confirm({
      title: t('iam.sessions.kick'),
      content: t('iam.sessions.confirm', { username }),
      danger: true,
      okKey: 'iam.sessions.kick',
      cancelKey: 'iam.common.cancel',
    });
    if (!ok) return;
    setKicking(userId);
    try {
      const r = await api.del<KickResult>(`/admin/sessions/${encodeURIComponent(userId)}`);
      toast.success(t('iam.sessions.msgKicked', { username, version: r.version }));
      listRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.sessions.errKick'));
    } finally {
      setKicking(null);
    }
  }

  async function onManualBump() {
    if (!uid.trim()) return;
    setBump(null);
    setBusy(true);
    try {
      const r = await api.post<TokenVersionBump>(`/internal/token-version/bump?uid=${encodeURIComponent(uid.trim())}`);
      setBump(r);
      listRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || e?.message || t('iam.sessions.errOp'));
    } finally {
      setBusy(false);
    }
  }

  const columns = useMemo<Array<DataTableColumn<SessionRow>>>(
    () => [
      { key: 'username', titleKey: 'iam.sessions.th.user', mono: true, render: (r) => r.username },
      {
        key: 'userId',
        titleKey: 'iam.sessions.th.userId',
        width: 180,
        mono: true,
        ellipsis: true,
        render: (r) => <span className="dim">{r.userId}</span>,
      },
      {
        key: 'accessTokenJti',
        titleKey: 'iam.sessions.th.jti',
        width: 260,
        mono: true,
        ellipsis: true,
        render: (r) => <span className="dim">{r.accessTokenJti ?? '—'}</span>,
      },
      {
        key: 'expiresAt',
        titleKey: 'iam.sessions.th.expire',
        width: 190,
        mono: true,
        render: (r) => fmtDateTime(r.expiresAt),
      },
      {
        key: 'actions',
        titleKey: 'iam.sessions.th.actions',
        width: 130,
        render: (r) => (
          <Can perm={SESSION_KICK}>
            <button
              className="btn-danger btn-sm"
              onClick={() => void kick(r.userId, r.username)}
              disabled={kicking === r.userId}
            >
              {t('iam.sessions.kick')}
            </button>
          </Can>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t, kicking],
  );

  return (
    <Page>
      <Panel
        title={t('iam.sessions.title')}
        sub={t('iam.sessions.sub')}
        actions={<CimTag tone="run">{t('iam.sessions.count', { n: rows.length })}</CimTag>}
      >
        <DataTable<SessionRow>
          flat
          columns={columns}
          rows={rows}
          rowKey={(r) => `${r.userId}-${r.accessTokenJti ?? ''}`}
          loading={listRes.loading}
          error={listRes.error?.message}
          onRetry={listRes.refresh}
          onRefresh={listRes.refresh}
          settingsKey="iam.sessions"
          emptyTitleKey="iam.sessions.emptyTitle"
          emptyHintKey="iam.sessions.emptyHint"
        />
      </Panel>

      <Can perm={SESSION_KICK}>
        <Panel
          title={t('iam.sessions.kickTitle')}
          sub={t('iam.sessions.kickSub')}
          actions={
            <CimTag tone="warn" size="small">
              {t('iam.sessions.highRisk')}
            </CimTag>
          }
        >
          <div className="row">
            <div className="field">
              <label className="field-label" htmlFor="kick-uid">
                {t('iam.sessions.f.userId')}
              </label>
              <CimInput
                id="kick-uid"
                mono
                value={uid}
                onChange={setUid}
                placeholderKey="iam.sessions.userIdPH"
                onEnter={() => void onManualBump()}
                width={280}
              />
            </div>
            <button className="btn-danger" type="button" onClick={() => void onManualBump()} disabled={busy || !uid.trim()}>
              {busy ? t('iam.sessions.processing') : t('iam.sessions.process')}
            </button>
          </div>

          {bump && (
            <div className="alert ok" style={{ marginTop: 12 }}>
              <span>{t('iam.sessions.bumpDone', { uid: bump.uid, version: bump.version })}</span>
            </div>
          )}

          <ul className="note-list" style={{ marginTop: 12 }}>
            <li>{t('iam.sessions.note1')}</li>
            <li>{t('iam.sessions.note2')}</li>
            <li>{t('iam.sessions.note3')}</li>
          </ul>
        </Panel>
      </Can>
    </Page>
  );
}
