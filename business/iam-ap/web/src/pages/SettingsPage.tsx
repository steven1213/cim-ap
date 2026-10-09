import { useEffect, useState } from 'react';
import * as api from '@/lib/api';
import type { SettingsDto } from '@/types';
import Panel from '@/components/Panel';
import { IconAlert, IconSliders } from '@/components/Icons';

/** 系统设置（需 iam-ap:ADMIN）：只读展示当前生效的运行时策略参数。 */
export default function SettingsPage() {
  const [s, setS] = useState<SettingsDto | null>(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.get<SettingsDto>('/admin/settings').then(setS).catch((e: any) => setErr(e?.msg || '加载设置失败'));
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

  if (!s) {
    return (
      <Panel title="系统设置">
        <div className="empty">
          <IconSliders width={22} height={22} />
          <b>正在加载生效配置…</b>
        </div>
      </Panel>
    );
  }

  return (
    <div className="page">
      <div className="grid-2">
        <Panel title="认证与口令" sub="登录认证源与两层派生参数">
          <dl className="meta">
            <dt className="meta-k">认证源</dt>
            <dd className="meta-v mono">{s.authSource}</dd>
            <dt className="meta-k">PBKDF2 轮数</dt>
            <dd className="meta-v mono">{s.passwordRounds.toLocaleString()}</dd>
            <dt className="meta-k">服务端 pepper</dt>
            <dd className="meta-v">
              {s.pepperConfigured ? (
                <span className="tag ok">已配置</span>
              ) : (
                <span className="tag err">未配置（不安全）</span>
              )}
            </dd>
            <dt className="meta-k">首管理员引导</dt>
            <dd className="meta-v">
              {s.bootstrapEnabled ? <span className="tag ok">启用</span> : <span className="tag">停用</span>}
              <span className="mono"> · {s.bootstrapAdminUsername}</span>
            </dd>
          </dl>
        </Panel>

        <Panel title="令牌与签名" sub="JWT 签发参数与密钥来源">
          <dl className="meta">
            <dt className="meta-k">访问令牌 TTL</dt>
            <dd className="meta-v mono">{s.accessTokenTtlMinutes} 分钟</dd>
            <dt className="meta-k">刷新令牌 TTL</dt>
            <dd className="meta-v mono">{Math.round(s.refreshTokenTtlMinutes / 1440)} 天</dd>
            <dt className="meta-k">issuer / kid</dt>
            <dd className="meta-v mono">
              {s.jwtIssuer} / {s.jwtKid}
            </dd>
            <dt className="meta-k">RSA 私钥来源</dt>
            <dd className="meta-v">
              {s.rsaKeyInjected ? <span className="tag ok">KMS 注入</span> : <span className="tag warn">启动临时生成</span>}
            </dd>
            <dt className="meta-k">JWKS 端点</dt>
            <dd className="meta-v mono">{s.jwksPath}</dd>
          </dl>
        </Panel>
      </div>

      <div className="grid-2">
        <Panel title="登录锁定策略" sub="暴力破解防护（滑动窗口）">
          <dl className="meta">
            <dt className="meta-k">失败阈值</dt>
            <dd className="meta-v mono">{s.lockoutMaxAttempts} 次</dd>
            <dt className="meta-k">锁定时长</dt>
            <dd className="meta-v mono">{s.lockoutLockMinutes} 分钟</dd>
            <dt className="meta-k">计数窗口</dt>
            <dd className="meta-v mono">{s.lockoutWindowMinutes} 分钟</dd>
          </dl>
        </Panel>

        <Panel title="跨域白名单" sub="允许访问本服务的前端源">
          {s.webAllowedOrigins?.length ? (
            <div className="tag-list">
              {s.webAllowedOrigins.map((o) => (
                <span key={o} className="tag mono">
                  {o}
                </span>
              ))}
            </div>
          ) : (
            <span className="dim">未配置</span>
          )}
        </Panel>
      </div>

      <Panel title="说明" sub="为何是只读">
        <ul className="note-list">
          <li>这些参数影响<b>签发与验证的一致性</b>，在线修改会与已签发令牌/业务验证端产生漂移。</li>
          <li>如需调整，请通过配置中心 / 环境变量发布，并重启生效。</li>
          <li>本页用于让管理员核对当前生效值，便于排障与合规审计。</li>
        </ul>
      </Panel>
    </div>
  );
}
