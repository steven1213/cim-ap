import { useEffect, useState } from 'react';
import * as api from '@/lib/api';
import type { AppRoleGroup } from '@/types';
import Panel from '@/components/Panel';
import { IconAlert, IconRefresh, IconShield } from '@/components/Icons';

/** 角色组视图（路由级 `RequirePerm`，需 `iam:role:list`）：按接入码聚合实际在用的粗角色组与人数分布。 */
export default function RolesPage() {
  const [groups, setGroups] = useState<AppRoleGroup[]>([]);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    setErr('');
    try {
      setGroups(await api.get<AppRoleGroup[]>('/admin/roles'));
    } catch (e: any) {
      setErr(e?.msg || '加载角色组失败');
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
        title="角色组分布"
        sub="按接入码聚合，展示各 app 内实际分配的粗角色组及人数"
        flush
        actions={
          <>
            <span className="tag">{groups.length} 个接入码</span>
            <span className="tag pri">{totalRoles} 个角色组</span>
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? '刷新中' : '刷新'}
            </button>
          </>
        }
      >
        {groups.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 170 }}>接入码</th>
                  <th style={{ width: 220 }}>应用名称</th>
                  <th>角色组与人数</th>
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
                            <span key={r.role} className="tag pri" title={`${r.userCount} 名用户`}>
                              {r.role}
                              <b className="tag-count">{r.userCount}</b>
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span className="dim">未被分配任何角色组</span>
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
            <b>暂无应用</b>
            <span>先在「应用注册」登记接入码</span>
          </div>
        )}
      </Panel>

      <Panel title="关于角色组" sub="IAM 只管准入，不管业务内部权限">
        <ul className="note-list">
          <li>角色组是「ap 内的粗粒度分组」，由准入授权时写入，随令牌 roles claim 下发。</li>
          <li>本视图不维护独立字典，而是聚合实际在用值，避免「定义与实际脱节」。</li>
          <li>业务系统内的菜单 / 按钮 / 数据行权限由各业务 ap 自行控制（IAM 不介入）。</li>
        </ul>
      </Panel>
    </div>
  );
}
