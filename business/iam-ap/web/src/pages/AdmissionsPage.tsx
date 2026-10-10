import { useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { AppRegistration, AssignmentDto } from '@/types';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import { ADMISSION_GRANT, ADMISSION_REVOKE } from '@/lib/permCodes';
import { IconAlert, IconCheckCircle, IconInbox, IconLink } from '@/components/Icons';

/**
 * 准入授权：以「用户」为主线，查看并编辑其可进入的 ap 与角色组。
 *
 * <p>任何授予 / 撤销都会 bump 该用户令牌版本（后端自动），其存量令牌立即失效、需重新登录 —
 * 这正是「准入变更即时生效」的落点。写操作按钮按 `iam:admission:grant|revoke` 权限显隐。</p>
 */
export default function AdmissionsPage() {
  const { t } = useTranslation();
  const [apps, setApps] = useState<AppRegistration[]>([]);
  const [userId, setUserId] = useState('admin');
  const [assignments, setAssignments] = useState<AssignmentDto[] | null>(null);
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<AppRegistration[]>('/apps').then(setApps).catch((e: any) => setErr(e?.msg || t('iam.admissions.errLoad')));
    // 默认即以 admin 查询一次，进入页面立即可见准入矩阵（符合常用使用习惯）
    void query();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function flash(t: string) {
    setMsg(t);
    setTimeout(() => setMsg(''), 3000);
  }

  async function query(e?: FormEvent) {
    e?.preventDefault();
    if (!userId.trim()) return;
    setErr('');
    setAssignments(null);
    try {
      const list = await api.get<AssignmentDto[]>(`/apps/users/${encodeURIComponent(userId.trim())}/assignments`);
      setAssignments(list);
      const map: Record<string, string> = {};
      for (const a of list) map[a.appCode] = a.roles.join(', ');
      setEdits(map);
    } catch (e: any) {
      setErr(e?.msg || t('iam.admissions.errQuery'));
    }
  }

  function roleOf(code: string): AssignmentDto | undefined {
    return assignments?.find((a) => a.appCode === code);
  }

  async function grant(appCode: string) {
    setErr('');
    setBusy(true);
    try {
      const roles = (edits[appCode] ?? '')
        .split(',')
        .map((r) => r.trim())
        .filter(Boolean);
      await api.post(`/apps/${encodeURIComponent(appCode)}/users`, { userId: userId.trim(), roles });
      flash(t('iam.admissions.msgGranted', { user: userId, app: appCode, roles: roles.join(', ') || t('iam.admissions.noRole') }));
      await query();
    } catch (e: any) {
      setErr(e?.msg || t('iam.admissions.errGrant'));
    } finally {
      setBusy(false);
    }
  }

  async function revoke(appCode: string) {
    setErr('');
    try {
      await api.del(`/apps/${encodeURIComponent(appCode)}/users/${encodeURIComponent(userId.trim())}`);
      flash(t('iam.admissions.msgRevoked', { user: userId, app: appCode }));
      await query();
    } catch (e: any) {
      setErr(e?.msg || t('iam.admissions.errRevoke'));
    }
  }

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

      <Panel title={t('iam.admissions.title')} sub={t('iam.admissions.sub')}>
        <form className="row" onSubmit={query}>
          <div className="field">
            <label className="field-label" htmlFor="au">
              {t('iam.admissions.f.userId')}
            </label>
            <input
              id="au"
              className="mono"
              placeholder="admin"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
            />
          </div>
          <button type="submit" disabled={!userId.trim()}>
            {t('iam.admissions.query')}
          </button>
        </form>

        {assignments && (
          <div className="table-wrap" style={{ marginTop: 14 }}>
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 150 }}>{t('iam.admissions.th.appCode')}</th>
                  <th style={{ width: 170 }}>{t('iam.admissions.th.appName')}</th>
                  <th style={{ width: 90 }}>{t('iam.admissions.th.admission')}</th>
                  <th>{t('iam.admissions.th.roles')}</th>
                  <th style={{ width: 170 }}>{t('iam.admissions.th.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {apps.map((a) => {
                  const cur = roleOf(a.appCode);
                  const admitted = !!cur;
                  return (
                    <tr key={a.appCode}>
                      <td className="mono">{a.appCode}</td>
                      <td>{a.appName}</td>
                      <td>
                        <span className="status">
                          <i className={'led ' + (admitted ? 'ok' : '')} />
                          <b>{admitted ? t('iam.admissions.admitted') : t('iam.admissions.notAdmitted')}</b>
                        </span>
                      </td>
                      <td>
                        <input
                          className="mono"
                          placeholder="ADMIN, OPERATOR"
                          value={edits[a.appCode] ?? ''}
                          onChange={(e) => setEdits((m) => ({ ...m, [a.appCode]: e.target.value }))}
                        />
                      </td>
                      <td>
                        <div className="cell-actions">
                          <Perms code={ADMISSION_GRANT}>
                            <button className="btn-sm" disabled={busy} onClick={() => grant(a.appCode)}>
                              <IconLink width={13} height={13} />
                              {admitted ? t('iam.admissions.update') : t('iam.admissions.grant')}
                            </button>
                          </Perms>
                          {admitted && (
                            <Perms code={ADMISSION_REVOKE}>
                              <button className="btn-danger btn-sm" onClick={() => revoke(a.appCode)}>
                                {t('iam.admissions.revoke')}
                              </button>
                            </Perms>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {!assignments && (
          <div className="empty" style={{ marginTop: 14 }}>
            <IconInbox width={22} height={22} />
            <b>{t('iam.admissions.emptyTitle')}</b>
            <span>{t('iam.admissions.emptyHint')}</span>
          </div>
        )}
      </Panel>

      <Panel title={t('iam.admissions.impactTitle')} sub={t('iam.admissions.impactSub')}>
        <ul className="note-list">
          <li>{t('iam.admissions.impact1')}</li>
          <li>{t('iam.admissions.impact2')}</li>
          <li>{t('iam.admissions.impact3')}</li>
        </ul>
      </Panel>
    </div>
  );
}
