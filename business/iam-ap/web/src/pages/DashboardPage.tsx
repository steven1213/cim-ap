import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '@/lib/api';
import type { OverviewDto } from '@/types';
import { useAuthStore } from '@/store/authStore';
import { isIamAdmin } from '@/lib/permissions';
import { auditLabel, auditTone, fmtRelative } from '@/lib/format';
import Panel from '@/components/Panel';
import {
  IconAlert,
  IconApps,
  IconGauge,
  IconInbox,
  IconKey,
  IconList,
  IconLock,
  IconMonitor,
  IconShield,
  IconUser,
  IconUsers,
} from '@/components/Icons';

/**
 * 概览：
 * - 管理员 → 平台态势（用户/接入/会话/锁定 + 最近审计事件）；
 * - 普通用户 → 个人身份、准入矩阵与会话安全属性。
 */
export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  const admin = isIamAdmin(user);

  if (admin) return <AdminOverview />;
  return <PersonalOverview />;
}

function AdminOverview() {
  const [ov, setOv] = useState<OverviewDto | null>(null);
  const [err, setErr] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    api
      .get<OverviewDto>('/admin/overview')
      .then(setOv)
      .catch((e: any) => setErr(e?.msg || '加载概览失败'));
  }, []);

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

  if (!ov) {
    return (
      <Panel title="平台概览">
        <div className="empty">
          <IconGauge width={24} height={24} />
          <b>正在加载平台态势…</b>
        </div>
      </Panel>
    );
  }

  const failTone = ov.loginFailureCount > 0 ? 'warn' : 'ok';

  return (
    <div className="page">
      <div className="kpi-grid">
        <Kpi icon={<IconUsers width={16} height={16} />} label="用户账号" value={`${ov.enabledUserCount} / ${ov.userCount}`} sub={`${ov.disabledUserCount} 已禁用`} />
        <Kpi icon={<IconApps width={16} height={16} />} tone="ok" label="接入应用" value={`${ov.enabledAppCount} / ${ov.appCount}`} sub="启用 / 总数" />
        <Kpi icon={<IconMonitor width={16} height={16} />} tone="info" label="在线会话" value={ov.activeSessionCount} sub="活跃刷新令牌" />
        <Kpi icon={<IconLock width={16} height={16} />} tone={ov.lockedCount > 0 ? 'warn' : 'ok'} label="锁定账户" value={ov.lockedCount} sub="暴力破解防护" />
        <Kpi icon={<IconShield width={16} height={16} />} tone="ok" label="登录成功" value={ov.loginSuccessCount} sub="累计" />
        <Kpi icon={<IconAlert width={16} height={16} />} tone={failTone} label="登录失败" value={ov.loginFailureCount} sub="累计，需关注" />
      </div>

      <Panel
        title="最近审计事件"
        sub="登录、授权、改密等关键动作流水"
        flush
        actions={
          <button className="btn-ghost btn-sm" onClick={() => navigate('/audit')}>
            <IconList width={13} height={13} />
            查看全部
          </button>
        }
      >
        {ov.recentEvents.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 150 }}>时间</th>
                  <th style={{ width: 120 }}>类型</th>
                  <th style={{ width: 120 }}>操作人</th>
                  <th style={{ width: 130 }}>对象</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                {ov.recentEvents.map((e) => (
                  <tr key={e.id}>
                    <td className="mono dim">{fmtRelative(e.createTime)}</td>
                    <td>
                      <span className={'tag ' + auditTone(e.type, e.result)}>{auditLabel(e.type)}</span>
                    </td>
                    <td className="mono">{e.actor ?? '—'}</td>
                    <td className="mono">{e.subject ?? '—'}</td>
                    <td className="dim">{e.detail ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconInbox width={22} height={22} />
            <b>暂无审计事件</b>
          </div>
        )}
      </Panel>
    </div>
  );
}

function PersonalOverview() {
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
        <Kpi icon={<IconUser width={16} height={16} />} label="用户 ID" value={user.userId} />
        <Kpi icon={<IconApps width={16} height={16} />} tone="ok" label="可进入应用" value={apps.length} />
        <Kpi icon={<IconShield width={16} height={16} />} tone="info" label="角色组" value={roleCount} />
        <Kpi icon={<IconKey width={16} height={16} />} tone="warn" label="租户" value={user.tenantId ?? '-'} />
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
            <span>请联系管理员在「准入授权」中分配</span>
          </div>
        )}
      </Panel>
    </div>
  );
}

function Kpi({
  icon,
  label,
  value,
  sub,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
  sub?: string;
  tone?: 'ok' | 'info' | 'warn';
}) {
  return (
    <div className={'kpi' + (tone ? ' ' + tone : '')}>
      <div className="kpi-ico">{icon}</div>
      <div className="kpi-main">
        <div className="kpi-label">{label}</div>
        <div className="kpi-val">{value}</div>
        {sub && <div className="kpi-sub">{sub}</div>}
      </div>
    </div>
  );
}
