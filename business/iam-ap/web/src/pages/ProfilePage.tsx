import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { deriveClientHash, randomClientSalt } from '@/lib/crypto';
import * as api from '@/lib/api';
import type { ChangePasswordRequest, SaltResponse } from '@/types';
import { orgTypeLabel, originLabel } from '@/lib/format';
import Panel from '@/components/Panel';
import { IconAlert, IconTree } from '@/components/Icons';

/**
 * 自助改密：与登录一致，浏览器内先对旧/新口令做第一层 PBKDF2 派生，仅传 clientHash
 * （明文口令不出浏览器）。旧口令派生需先取服务端盐，新口令由客户端生成随机盐后派生。
 *
 * 提交成功后，服务端已 bump 该用户的令牌版本，当前会话令牌随即失效（下次请求将被验证端判 401）。
 * 因此此处主动清空本地会话并跳转登录页，强制以新口令重新登录，避免停留在已失效的会话里。
 */
export default function ProfilePage() {
  const [oldPassword, setOld] = useState('');
  const [newPassword, setNew] = useState('');
  const [confirmPassword, setConfirm] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const username = useAuthStore((s) => s.user?.username);
  const userId = useAuthStore((s) => s.user?.userId);
  const tenantId = useAuthStore((s) => s.user?.tenantId);
  const displayName = useAuthStore((s) => s.user?.displayName);
  const employeeNo = useAuthStore((s) => s.user?.employeeNo);
  const jobTitle = useAuthStore((s) => s.user?.jobTitle);
  const source = useAuthStore((s) => s.user?.source);
  const orgs = useAuthStore((s) => s.user?.orgs) ?? [];
  const appCount = useAuthStore((s) => s.user?.apps?.length ?? 0);
  const clearSession = useAuthStore((s) => s.clear);
  const navigate = useNavigate();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setErr('');
    if (!username) {
      setErr('当前会话缺失用户名，请重新登录');
      return;
    }
    if (newPassword !== confirmPassword) {
      setErr('两次输入的新口令不一致');
      return;
    }
    setBusy(true);
    try {
      // 1) 取服务端盐（旧口令第一层派生所需）
      const salt = await api.get<SaltResponse>('/login/salt', { username });
      const oldClientSalt = salt.clientSalt || randomClientSalt();
      const oldCredential = await deriveClientHash(oldPassword, oldClientSalt);
      // 2) 新口令：客户端生成随机盐后做第一层派生
      const newClientSalt = randomClientSalt();
      const newCredential = await deriveClientHash(newPassword, newClientSalt);
      // 3) 提交（仅传 clientHash，明文口令不出浏览器）
      const body: ChangePasswordRequest = { oldCredential, newCredential, newClientSalt };
      await api.post<void>('/me/password', body);
      // 服务端已 bump 令牌版本 → 当前令牌失效，主动清态并重登
      clearSession();
      navigate('/login', { state: { notice: '口令已更新，请重新登录' } });
    } catch (e: any) {
      setErr(e?.msg || e?.message || '改密失败');
    } finally {
      setBusy(false);
    }
  }

  const mismatch = !!confirmPassword && newPassword !== confirmPassword;
  const canSubmit = !!oldPassword && !!newPassword && !!confirmPassword && !mismatch;

  return (
    <div className="page">
      <div className="grid-2">
        <Panel title="账户信息" sub="当前登录身份与档案">
          <dl className="meta">
            <dt className="meta-k">用户 ID</dt>
            <dd className="meta-v mono">{userId ?? '—'}</dd>
            <dt className="meta-k">用户名</dt>
            <dd className="meta-v">{username ?? '—'}</dd>
            <dt className="meta-k">姓名</dt>
            <dd className="meta-v">{displayName ?? '—'}</dd>
            <dt className="meta-k">工号</dt>
            <dd className="meta-v mono">{employeeNo ?? '—'}</dd>
            <dt className="meta-k">岗位</dt>
            <dd className="meta-v">{jobTitle ?? '—'}</dd>
            <dt className="meta-k">档案来源</dt>
            <dd className="meta-v">{originLabel(source)}</dd>
            <dt className="meta-k">租户</dt>
            <dd className="meta-v mono">{tenantId ?? '—'}</dd>
            <dt className="meta-k">准入应用</dt>
            <dd className="meta-v mono">{appCount}</dd>
          </dl>

          <div className="sub-block">
            <div className="sub-title">我的组织归属</div>
            {orgs.length ? (
              <div className="tag-list">
                {orgs.map((o) => (
                  <span key={o.orgId} className={'tag' + (o.primary ? ' pri' : '')} title={o.code}>
                    {o.name}
                    <span className="dim"> · {orgTypeLabel(o.nodeType)}</span>
                  </span>
                ))}
              </div>
            ) : (
              <p className="hint" style={{ marginTop: 4 }}>
                <IconTree width={13} height={13} /> 尚未归属任何组织——归属由管理员在「组织架构」中维护。
              </p>
            )}
          </div>

          <div className="sub-block">
            <div className="sub-title">安全提示</div>
            <ul className="note-list">
              <li>改密后全部已签发令牌立即失效，需重新登录。</li>
              <li>AD / LDAP 账号的口令变更请在目录侧完成。</li>
            </ul>
          </div>
        </Panel>

        <Panel title="修改口令" sub="修改成功后需以新口令重新登录">
          <form onSubmit={onSubmit}>
            <div className="field">
              <label className="field-label" htmlFor="pw-old">
                原口令
              </label>
              <input
                id="pw-old"
                type="password"
                value={oldPassword}
                onChange={(e) => setOld(e.target.value)}
                autoComplete="current-password"
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="pw-new">
                新口令
              </label>
              <input
                id="pw-new"
                type="password"
                value={newPassword}
                onChange={(e) => setNew(e.target.value)}
                autoComplete="new-password"
              />
            </div>
            <div className="field">
              <label className="field-label" htmlFor="pw-confirm">
                确认新口令
              </label>
              <input
                id="pw-confirm"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirm(e.target.value)}
                autoComplete="new-password"
              />
            </div>

            {err && (
              <div className="alert err" style={{ marginTop: 12 }}>
                <IconAlert width={15} height={15} />
                <span>{err}</span>
              </div>
            )}

            <button type="submit" className="btn-block" disabled={busy || !canSubmit} style={{ marginTop: 14 }}>
              {busy ? '提交中…' : '提交修改'}
            </button>
          </form>
          <p className="hint">
            口令在浏览器内完成第一层 PBKDF2 派生（与登录一致），仅密文派生值上传；明文口令不离开浏览器。
          </p>
        </Panel>
      </div>
    </div>
  );
}
