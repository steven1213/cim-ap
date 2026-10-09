import { useState, type FormEvent } from 'react';
import * as api from '@/lib/api';
import type { TokenVersionBump } from '@/types';

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
    <div className="card">
      <h2>令牌踢人（强制下线）</h2>
      <p className="hint">
        bump 该用户的令牌版本号，使其所有存量令牌在验证端缓存到期后即时失效，需重新登录。本地账号的用户ID即用户名。
      </p>
      <form className="row" onSubmit={onKick}>
        <input
          placeholder="用户ID（本地账号即用户名）"
          value={uid}
          onChange={(e) => setUid(e.target.value)}
        />
        <button disabled={busy || !uid}>踢下线</button>
      </form>
      {err && <div className="err">{err}</div>}
      {res && (
        <div className="ok">
          已 bump 用户 {res.uid}，当前版本号：{res.version}
        </div>
      )}
    </div>
  );
}
