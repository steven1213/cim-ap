import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useIdentityStore, PRESET_USERS, SHARED_DOMAIN } from '@/store/identityStore';
import { IconChevron } from '@/components/Icons';

import { CimSelect, CimInput, CimField, CimButton } from '@cim/ui';
// dev 态身份切换器：以 userId / tenantId（业务域）标识当前操作人。
// 统一登录态不渲染本组件（见 Layout.tsx：身份与域由 JWT 决定，界面只读）。
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
    setIdentity(nextUser.trim() || 'alice', nextTenant.trim() || SHARED_DOMAIN);
    setOpen(false);
  }

  const initial = (userId || '?').charAt(0).toUpperCase();

  // 用户下拉的选项：预置账号 + 当前账号（若不在预置里，说明是手工输入的，也保留）
  const userOptions = [
    ...PRESET_USERS.map((u) => ({ value: u, label: u })),
    ...(PRESET_USERS.includes(nextUser as (typeof PRESET_USERS)[number])
      ? []
      : [{ value: nextUser, label: nextUser }]),
  ];

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <CimButton bare className="user-chip" onClick={() => setOpen((v) => !v)} title={t('identity.change')}>
        <span className="avatar">{initial}</span>
        <span className="user-meta">
          <b>{userId}</b>
          <small>domain:{tenantId}</small>
        </span>
        <IconChevron width={14} height={14} />
      </CimButton>

      {open && (
        <div
          className="tab-ctx"
          style={{ top: 40, right: 0, minWidth: 240 }}
          onClick={(e) => e.stopPropagation()}
        >
          <div style={{ padding: '10px 10px 4px', fontSize: 11, color: 'var(--muted)', letterSpacing: '0.06em', textTransform: 'uppercase' }}>
            {t('identity.change')}
          </div>
          <CimField label={t('identity.userId')} style={{ padding: '0 10px' }}>
            <CimSelect
              value={nextUser}
              onChange={(v) => setNextUser(typeof v === 'string' ? v : '')}
              options={userOptions}
            />
          </CimField>
          <CimField label={t('identity.tenantId')} style={{ padding: '8px 10px' }}>
            <CimInput value={nextTenant} onChange={setNextTenant} placeholder="default" />
          </CimField>
          <div style={{ display: 'flex', gap: 6, padding: 10 }}>
            <CimButton type="primary" size="small" block onClick={apply}>
              {t('identity.apply')}
            </CimButton>
          </div>
          <div style={{ padding: '0 10px 10px', fontSize: 10.5, color: 'var(--muted)', lineHeight: 1.5 }}>
            {t('identity.hint')}
          </div>
        </div>
      )}
    </div>
  );
}
