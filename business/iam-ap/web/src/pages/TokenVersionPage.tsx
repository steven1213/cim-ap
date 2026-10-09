import { useState, type FormEvent } from 'react';
import * as api from '@/lib/api';
import type { TokenVersionBump } from '@/types';
import Panel from '@/components/Panel';
import { IconAlert, IconCheckCircle, IconKick } from '@/components/Icons';

/** 令牌踢人：bump 用户令牌版本号，使其所有存量令牌即时失效、需重新登录。 */
export default function TokenVersionPage() {
  const [uid, setUid] = useState('');
  const [res, setRes] = useState<TokenVersionBump | null>(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function onKick(e: FormEvent) {
    e.preventDefault();
    setRes(null);
    setErr('');
    setBusy(true);
    try {
      // 该端点返回裸对象（非 Result 包装），用 postRaw 透传
      const r = await api.postRaw<TokenVersionBump>(
        `/internal/token-version/bump?uid=${encodeURIComponent(uid)}`,
      );
      setRes(r);
    } catch (e: any) {
      setErr(e?.msg || e?.message || '踢人失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <Panel
        title="令牌踢人（强制下线）"
        sub="bump 令牌版本号，验证端进程内即时校验，无缓存滞后"
        actions={
          <span className="tag warn">
            <IconKick width={12} height={12} />
            高危操作
          </span>
        }
      >
        <form className="row" onSubmit={onKick}>
          <div className="field">
            <label className="field-label" htmlFor="kick-uid">
              用户 ID
            </label>
            <input
              id="kick-uid"
              className="mono"
              placeholder="admin（本地账号即用户名）"
              value={uid}
              onChange={(e) => setUid(e.target.value)}
            />
          </div>
          <button className="btn-danger" type="submit" disabled={busy || !uid}>
            {busy ? '处理中…' : '踢下线'}
          </button>
        </form>

        {err && (
          <div className="alert err" style={{ marginTop: 12 }}>
            <IconAlert width={15} height={15} />
            <span>{err}</span>
          </div>
        )}
        {res && (
          <div className="alert ok" style={{ marginTop: 12 }}>
            <IconCheckCircle width={15} height={15} />
            <span>
              已 bump 用户 <b>{res.uid}</b>，当前令牌版本号 <b>{res.version}</b>
            </span>
          </div>
        )}

        <div className="sub-block">
          <div className="sub-title">执行说明</div>
          <ul className="note-list">
            <li>作用对象：该用户已签发的全部访问令牌与刷新令牌。</li>
            <li>生效方式：IAM 在验证端比对令牌 <code>ver</code> claim 与库内版本号，不一致即返回 401。</li>
            <li>后续影响：用户需使用有效凭证重新登录；未到期令牌不会被直接删除，但会被拒绝。</li>
            <li>回滚：再次登录不会恢复旧令牌，令牌版本号单调递增。</li>
          </ul>
        </div>
      </Panel>
    </div>
  );
}
