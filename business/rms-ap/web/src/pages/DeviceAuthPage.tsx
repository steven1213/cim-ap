import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import { DEVICE_AUTH_MANAGE } from '@/lib/permCodes';
import type { DeviceAuth, Device, DeviceType, DeviceArea } from '@/types';

/** 设备授权页（FR-P4，Req 11/12）。 */
export default function DeviceAuthPage() {
  const { t } = useTranslation();
  const [list, setList] = useState<DeviceAuth[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [types, setTypes] = useState<DeviceType[]>([]);
  const [areas, setAreas] = useState<DeviceArea[]>([]);
  const [loading, setLoading] = useState(true);
  const [show, setShow] = useState(false);
  const [subject, setSubject] = useState('');

  async function refresh() {
    setLoading(true);
    try {
      setList(await api.listDeviceAuth({ subject: subject || undefined }));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    api.listDevices().then(setDevices);
    api.listDeviceTypes().then(setTypes);
    api.listDeviceAreas().then(setAreas);
  }, []);

  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, [subject]);

  async function revoke(x: DeviceAuth) {
    if (!confirm(t('rms.common.confirmDelete'))) return;
    await api.revokeDeviceAuth(x.id);
    await refresh();
  }

  const label = (x: DeviceAuth) => {
    if (x.deviceId) return `${t('rms.deviceAuth.device')}: ${x.deviceLabel ?? x.deviceId}`;
    if (x.deviceTypeId) return `${t('rms.deviceAuth.deviceType')}: ${types.find((z) => z.id === x.deviceTypeId)?.name ?? x.deviceTypeId}`;
    if (x.areaId) return `${t('rms.deviceAuth.area')}: ${areas.find((z) => z.id === x.areaId)?.name ?? x.areaId}`;
    return t('rms.common.dash');
  };

  return (
    <div className="page">
      <div className="page-head">
        <div><h1>{t('rms.deviceAuth.title')}</h1><p className="muted">{t('rms.deviceAuth.sub')}</p></div>
        <Perms code={DEVICE_AUTH_MANAGE}>
          <button className="btn btn-primary btn-sm" onClick={() => setShow(true)}>+ {t('rms.deviceAuth.grant')}</button>
        </Perms>
      </div>

      <div className="toolbar">
        <div className="search-inline">
          <input className="text-input" placeholder={t('rms.deviceAuth.subject')} value={subject}
            onChange={(e) => setSubject(e.target.value)} />
        </div>
        {loading && <span className="muted">{t('rms.common.loading')}</span>}
      </div>

      <div className="panel">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.deviceAuth.subject')}</th>
              <th>{t('rms.deviceAuth.target')}</th>
              <th>{t('rms.deviceAuth.canOperate')}</th>
              <th>{t('rms.common.createTime')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {list.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.subject}</td>
                <td>{label(x)}</td>
                <td>{x.canOperate ? t('rms.common.yes') : t('rms.common.no')}</td>
                <td className="muted">{x.createTime ?? t('rms.common.dash')}</td>
                <td className="cell-actions">
                  <Perms code={DEVICE_AUTH_MANAGE}>
                    <button className="btn btn-ghost btn-sm danger" onClick={() => revoke(x)}>{t('rms.common.revoke')}</button>
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && list.length === 0 && <tr><td colSpan={5} className="empty-cell">{t('rms.deviceAuth.empty')}</td></tr>}
          </tbody>
        </table>
      </div>

      {show && (
        <AuthModal devices={devices} types={types} areas={areas}
          onClose={() => setShow(false)}
          onDone={async () => { setShow(false); await refresh(); }} />
      )}
    </div>
  );
}

function AuthModal({ devices, types, areas, onClose, onDone }: {
  devices: Device[]; types: DeviceType[]; areas: DeviceArea[];
  onClose: () => void; onDone: () => void;
}) {
  const { t } = useTranslation();
  const [subject, setSubject] = useState('');
  const [deviceId, setDeviceId] = useState('');
  const [deviceTypeId, setDeviceTypeId] = useState('');
  const [areaId, setAreaId] = useState('');
  const [canOperate, setCanOperate] = useState(true);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit() {
    if (!subject) { setErr(t('rms.deviceAuth.subject') + ' *'); return; }
    if (!deviceId && !deviceTypeId && !areaId) { setErr(t('rms.deviceAuth.targetRequired')); return; }
    setBusy(true); setErr('');
    try {
      await api.grantDeviceAuth({
        subject, subjectType: 'USER',
        deviceId: deviceId || null, deviceTypeId: deviceTypeId || null, areaId: areaId || null, canOperate,
      });
      onDone();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head"><h3>{t('rms.deviceAuth.grant')}</h3><button className="icon-btn" onClick={onClose}>✕</button></div>
        <div className="modal-body">
          <div className="field">
            <label className="field-label">{t('rms.deviceAuth.subject')} *</label>
            <input className="text-input" value={subject} placeholder="username" onChange={(e) => setSubject(e.target.value)} />
          </div>
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.deviceAuth.device')}</label>
              <select className="text-input" value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
                <option value="">{t('rms.common.select')}</option>
                {devices.map((d) => <option key={d.id} value={d.id}>{d.code}</option>)}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.deviceAuth.deviceType')}</label>
              <select className="text-input" value={deviceTypeId} onChange={(e) => setDeviceTypeId(e.target.value)}>
                <option value="">{t('rms.common.select')}</option>
                {types.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.deviceAuth.area')}</label>
              <select className="text-input" value={areaId} onChange={(e) => setAreaId(e.target.value)}>
                <option value="">{t('rms.common.select')}</option>
                {areas.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.deviceAuth.canOperate')}</label>
              <input type="checkbox" checked={canOperate} onChange={(e) => setCanOperate(e.target.checked)} />
            </div>
          </div>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>{t('rms.common.cancel')}</button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>{t('rms.common.save')}</button>
        </div>
      </div>
    </div>
  );
}
