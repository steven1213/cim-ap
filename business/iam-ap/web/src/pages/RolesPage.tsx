import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import type { AppRoleGroup } from '@/types';
import Panel from '@/components/Panel';
import { IconAlert, IconRefresh, IconShield } from '@/components/Icons';

/** 角色组视图（路由级 `RequirePerm`，需 `iam:role:list`）：按接入码聚合实际在用的粗角色组与人数分布。 */
export default function RolesPage() {
  const { t } = useTranslation();
  const [groups, setGroups] = useState<AppRoleGroup[]>([]);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    setErr('');
    try {
      setGroups(await api.get<AppRoleGroup[]>('/admin/roles'));
    } catch (e: any) {
      setErr(e?.msg || t('iam.rolesView.errLoad'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const totalRoles = groups.reduce((s, g) => s + g.roles.length, 0);

  return (
    <div className="page">
      {err && (
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      )}

      <Panel
        title={t('iam.rolesView.title')}
        sub={t('iam.rolesView.sub')}
        flush
        actions={
          <>
            <span className="tag">{t('iam.rolesView.countApps', { n: groups.length })}</span>
            <span className="tag pri">{t('iam.rolesView.countRoles', { n: totalRoles })}</span>
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? t('iam.rolesView.refreshing') : t('iam.rolesView.refresh')}
            </button>
          </>
        }
      >
        {groups.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 170 }}>{t('iam.rolesView.th.appCode')}</th>
                  <th style={{ width: 220 }}>{t('iam.rolesView.th.appName')}</th>
                  <th>{t('iam.rolesView.th.roles')}</th>
                </tr>
              </thead>
              <tbody>
                {groups.map((g) => (
                  <tr key={g.appCode}>
                    <td className="mono">{g.appCode}</td>
                    <td>{g.appName}</td>
                    <td>
                      {g.roles.length ? (
                        <div className="tag-list">
                          {g.roles.map((r) => (
                            <span key={r.role} className="tag pri" title={t('iam.rolesView.userCount', { n: r.userCount })}>
                              {r.role}
                              <b className="tag-count">{r.userCount}</b>
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span className="dim">{t('iam.rolesView.noRole')}</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconShield width={22} height={22} />
            <b>{t('iam.rolesView.emptyTitle')}</b>
            <span>{t('iam.rolesView.emptyHint')}</span>
          </div>
        )}
      </Panel>

      <Panel title={t('iam.rolesView.aboutTitle')} sub={t('iam.rolesView.aboutSub')}>
        <ul className="note-list">
          <li>{t('iam.rolesView.about1')}</li>
          <li>{t('iam.rolesView.about2')}</li>
          <li>{t('iam.rolesView.about3')}</li>
        </ul>
      </Panel>
    </div>
  );
}
