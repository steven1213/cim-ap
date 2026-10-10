import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
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
      setErr(t('iam.profile.errSession'));
      return;
    }
    if (newPassword !== confirmPassword) {
      setErr(t('iam.profile.errMismatch'));
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
      navigate('/login', { state: { notice: t('iam.profile.pwdUpdatedNotice') } });
    } catch (e: any) {
      setErr(e?.msg || e?.message || t('iam.profile.errChange'));
    } finally {
      setBusy(false);
    }
  }

  const mismatch = !!confirmPassword && newPassword !== confirmPassword;
  const canSubmit = !!oldPassword && !!newPassword && !!confirmPassword && !mismatch;

  return (
    <div className="page">
      <div className="grid-2">
        <Panel title={t('iam.profile.infoTitle')} sub={t('iam.profile.infoSub')}>
          <dl className="meta">
            <dt className="meta-k">{t('iam.profile.mk.userId')}</dt>
            <dd className="meta-v mono">{userId ?? '—'}</dd>
            <dt className="meta-k">{t('iam.profile.mk.username')}</dt>
            <dd className="meta-v">{username ?? '—'}</dd>
            <dt className="meta-k">{t('iam.profile.mk.name')}</dt>
            <dd className="meta-v">{displayName ?? '—'}</dd>
            <dt className="meta-k">{t('iam.profile.mk.empNo')}</dt>
            <dd className="meta-v mono">{employeeNo ?? '—'}</dd>
            <dt className="meta-k">{t('iam.profile.mk.job')}</dt>
            <dd className="meta-v">{jobTitle ?? '—'}</dd>
            <dt className="meta-k">{t('iam.profile.mk.source')}</dt>
            <dd className="meta-v">{originLabel(source)}</dd>
            <dt className="meta-k">{t('iam.profile.mk.tenant')}</dt>
            <dd className="meta-v mono">{tenantId ?? '—'}</dd>
            <dt className="meta-k">{t('iam.profile.mk.apps')}</dt>
            <dd className="meta-v mono">{appCount}</dd>
          </dl>

          <div className="sub-block">
            <div className="sub-title">{t('iam.profile.subTitleOrgs')}</div>
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
                <IconTree width={13} height={13} /> {t('iam.profile.noOrgs')}
              </p>
            )}
          </div>

          <div className="sub-block">
            <div className="sub-title">{t('iam.profile.secTitle')}</div>
            <ul className="note-list">
              <li>{t('iam.profile.sec1')}</li>
              <li>{t('iam.profile.sec2')}</li>
            </ul>
          </div>
        </Panel>

        <Panel title={t('iam.profile.pwdTitle')} sub={t('iam.profile.pwdSub')}>
          <form onSubmit={onSubmit}>
            <div className="field">
              <label className="field-label" htmlFor="pw-old">
                {t('iam.profile.f.old')}
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
                {t('iam.profile.f.new')}
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
                {t('iam.profile.f.confirm')}
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
              {busy ? t('iam.profile.submitting') : t('iam.profile.submit')}
            </button>
          </form>
          <p className="hint">
            {t('iam.profile.hint')}
          </p>
        </Panel>
      </div>
    </div>
  );
}
