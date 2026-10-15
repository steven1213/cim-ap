import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useIdentityStore, PRESET_USERS } from '@/store/identityStore';
import { IconChevron } from '@/components/Icons';

// dev 态身份切换器：以 userId / tenantId 标识当前操作人。
export default function IdentitySwitcher() {
  const { t } = useTranslation();
  const { userId, tenantId, setIdentity } = useIdentityStore();
  const [open, setOpen] = useState(false);
  const [nextUser, setNextUser] = useState(userId);
  const [nextTenant, setNextTenant] = useState(tenantId);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setNextUser(userId);
    setNextTenant(tenantId);
  }, [userId, tenantId]);

  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  function apply() {
    setIdentity(nextUser.trim() || 'alice', nextTenant.trim() || 'default');
    setOpen(false);
  }

  const initial = (userId || '?').charAt(0).toUpperCase();

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button className="user-chip" onClick={() => setOpen((v) => !v)} title={t('identity.change')}>
        <span className="avatar">{initial}</span>
        <span className="user-meta">
          <b>{userId}</b>
          <small>tenant:{tenantId}</small>
        </span>
        <IconChevron width={14} height={14} />
      </button>

      {open && (
        <div
          className="tab-ctx"
          style={{ top: 40, right: 0, minWidth: 240 }}
          onClick={(e) => e.stopPropagation()}
        >
          <div style={{ padding: '10px 10px 4px', fontSize: 11, color: 'var(--muted)', letterSpacing: '0.06em', textTransform: 'uppercase' }}>
            {t('identity.change')}
          </div>
          <div className="field" style={{ padding: '0 10px' }}>
            <label className="field-label">{t('identity.userId')}</label>
            <select value={nextUser} onChange={(e) => setNextUser(e.target.value)}>
              {PRESET_USERS.map((u) => (
                <option key={u} value={u}>{u}</option>
              ))}
              {!PRESET_USERS.includes(nextUser as (typeof PRESET_USERS)[number]) && (
                <option value={nextUser}>{nextUser}</option>
              )}
            </select>
          </div>
          <div className="field" style={{ padding: '8px 10px' }}>
            <label className="field-label">{t('identity.tenantId')}</label>
            <input value={nextTenant} onChange={(e) => setNextTenant(e.target.value)} placeholder="default" />
          </div>
          <div style={{ display: 'flex', gap: 6, padding: 10 }}>
            <button className="btn btn-block btn-sm" onClick={apply}>
              {t('identity.apply')}
            </button>
          </div>
          <div style={{ padding: '0 10px 10px', fontSize: 10.5, color: 'var(--muted)', lineHeight: 1.5 }}>
            {t('identity.hint')}
          </div>
        </div>
      )}
    </div>
  );
}
