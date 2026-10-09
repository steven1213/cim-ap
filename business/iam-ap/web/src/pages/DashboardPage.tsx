import { useAuthStore } from '@/store/authStore';

/** 概览：展示当前登录用户身份、可进入应用与角色。 */
export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  if (!user) return <div className="card">正在加载用户信息…</div>;
  return (
    <div className="card">
      <h2>概览</h2>
      <dl className="kv">
        <dt>用户ID</dt>
        <dd>{user.userId}</dd>
        <dt>用户名</dt>
        <dd>{user.username}</dd>
        <dt>租户</dt>
        <dd>{user.tenantId ?? '-'}</dd>
        <dt>可进入应用</dt>
        <dd>{user.apps.length ? user.apps.join(', ') : '无'}</dd>
        <dt>角色</dt>
        <dd>
          {Object.keys(user.roles).length ? (
            Object.entries(user.roles).map(([app, roles]) => (
              <div key={app}>
                {app}：{roles.join(', ')}
              </div>
            ))
          ) : (
            '无'
          )}
        </dd>
      </dl>
    </div>
  );
}
