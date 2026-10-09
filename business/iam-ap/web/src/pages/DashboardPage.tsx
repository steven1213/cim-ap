import { useAuthStore } from '@/store/authStore';
import Panel from '@/components/Panel';
import { IconApps, IconGauge, IconInbox, IconKey, IconShield, IconUser } from '@/components/Icons';

/** 概览：当前登录身份、准入矩阵与会话安全属性。 */
export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);

  if (!user) {
    return (
      <Panel title="概览">
        <div className="empty">
          <IconGauge width={24} height={24} />
          <b>正在加载用户信息…</b>
        </div>
      </Panel>
    );
  }

  const apps = user.apps ?? [];
  const roleEntries = Object.entries(user.roles ?? {});
  const roleCount = roleEntries.reduce((sum, [, roles]) => sum + roles.length, 0);

  return (
    <div className="page">
      <div className="kpi-grid">
        <div className="kpi">
          <div className="kpi-ico">
            <IconUser width={16} height={16} />
          </div>
          <div className="kpi-main">
            <div className="kpi-label">用户 ID</div>
            <div className="kpi-val">{user.userId}</div>
          </div>
        </div>
        <div className="kpi ok">
          <div className="kpi-ico">
            <IconApps width={16} height={16} />
          </div>
          <div className="kpi-main">
            <div className="kpi-label">可进入应用</div>
            <div className="kpi-val">{apps.length}</div>
          </div>
        </div>
        <div className="kpi info">
          <div className="kpi-ico">
            <IconShield width={16} height={16} />
          </div>
          <div className="kpi-main">
            <div className="kpi-label">角色组</div>
            <div className="kpi-val">{roleCount}</div>
          </div>
        </div>
        <div className="kpi warn">
          <div className="kpi-ico">
            <IconKey width={16} height={16} />
          </div>
          <div className="kpi-main">
            <div className="kpi-label">租户</div>
            <div className="kpi-val">{user.tenantId ?? '-'}</div>
          </div>
        </div>
      </div>

      <div className="grid-2">
        <Panel title="身份信息" sub="来自当前会话与 IAM 令牌 claim">
          <dl className="meta">
            <dt className="meta-k">用户 ID</dt>
            <dd className="meta-v mono">{user.userId}</dd>
            <dt className="meta-k">用户名</dt>
            <dd className="meta-v">{user.username}</dd>
            <dt className="meta-k">租户</dt>
            <dd className="meta-v mono">{user.tenantId ?? '—'}</dd>
            <dt className="meta-k">准入应用</dt>
            <dd className="meta-v mono">{apps.length ? apps.join(' , ') : '—'}</dd>
          </dl>
        </Panel>

        <Panel title="会话与安全" sub="令牌签发与验证方式">
          <dl className="meta">
            <dt className="meta-k">认证源</dt>
            <dd className="meta-v">本地凭证（两层 PBKDF2）</dd>
            <dt className="meta-k">签名算法</dt>
            <dd className="meta-v mono">RS256 / JWT</dd>
            <dt className="meta-k">验签方式</dt>
            <dd className="meta-v">JWKS 公钥，业务侧本地验签</dd>
            <dt className="meta-k">准入判定</dt>
            <dd className="meta-v mono">apps claim</dd>
            <dt className="meta-k">失效机制</dt>
            <dd className="meta-v">版本号校验 + 黑名单（jti）</dd>
          </dl>
        </Panel>
      </div>

      <Panel
        title="准入与角色矩阵"
        sub="按接入码分组的准入状态与角色组"
        flush
        actions={<span className="tag">{apps.length} 个接入码</span>}
      >
        {roleEntries.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>接入码</th>
                  <th>准入</th>
                  <th>角色组</th>
                </tr>
              </thead>
              <tbody>
                {roleEntries.map(([app, roles]) => (
                  <tr key={app}>
                    <td className="mono">{app}</td>
                    <td>
                      <span className="status">
                        <i className="led ok" />
                        <b>已准入</b>
                      </span>
                    </td>
                    <td>
                      {roles.length ? (
                        <div className="tag-list">
                          {roles.map((r) => (
                            <span key={r} className="tag pri">
                              {r}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span className="tag">无角色</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconInbox width={22} height={22} />
            <b>暂无准入应用</b>
            <span>请联系管理员在「应用与准入」中分配</span>
          </div>
        )}
      </Panel>
    </div>
  );
}
