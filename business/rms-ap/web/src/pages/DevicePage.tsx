import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import type { Device, DeviceType, DeviceArea, DeviceStatus } from '@/types';

const STATUS_TONE: Record<DeviceStatus, string> = {
  ENABLED: 'ok',
  DISABLED: 'warn',
};

export default function DevicePage() {
  const { t } = useTranslation();
  const [list, setList] = useState<Device[]>([]);
  const [types, setTypes] = useState<DeviceType[]>([]);
  const [areas, setAreas] = useState<DeviceArea[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');

  const [editing, setEditing] = useState<Device | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  async function refresh() {
    setLoading(true);
    try {
      const data = await api.listDevices({ keyword: keyword || undefined });
      setList(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let alive = true;
    Promise.all([api.listDeviceTypes(), api.listDeviceAreas()]).then(([ty, ar]) => {
      if (!alive) return;
      setTypes(ty);
      setAreas(ar);
    });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const id = setTimeout(refresh, 200);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword]);

  async function doDelete(x: Device) {
    if (!confirm(t('rms.common.confirmDelete'))) return;
    await api.deleteDevice(x.id);
    await refresh();
  }

  const typeName = useMemo(() => Object.fromEntries(types.map((x) => [x.id, x.name])), [types]);
  const areaName = useMemo(() => Object.fromEntries(areas.map((x) => [x.id, x.name])), [areas]);

  const filtered = useMemo(() => {
    if (!keyword) return list;
    const k = keyword.toLowerCase();
    return list.filter(
      (x) => x.code.toLowerCase().includes(k) || x.name.toLowerCase().includes(k) ||
        (x.ip ?? '').toLowerCase().includes(k),
    );
  }, [list, keyword]);

  return (
    <div className="page">
      <div className="page-head">
        <h1>{t('rms.device.title')}</h1>
        <Perms code="rms:device:create">
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
            + {t('rms.device.createNew')}
          </button>
        </Perms>
      </div>

      <div className="toolbar">
        <input
          className="search-input"
          placeholder={t('rms.common.search')}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        {loading && <span className="muted">{t('rms.common.loading')}</span>}
      </div>

      <div className="panel">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.device.code')}</th>
              <th>{t('rms.device.name')}</th>
              <th>{t('rms.device.type')}</th>
              <th>{t('rms.device.area')}</th>
              <th>{t('rms.device.ip')}</th>
              <th>{t('rms.common.status')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.code}</td>
                <td>{x.name}</td>
                <td>{x.deviceTypeName ?? (x.deviceTypeId ? typeName[x.deviceTypeId] ?? '?' : t('rms.common.dash'))}</td>
                <td>{x.areaName ?? (x.areaId ? areaName[x.areaId] ?? '?' : t('rms.common.dash'))}</td>
                <td className="mono">{x.ip ?? t('rms.common.dash')}</td>
                <td>
                  <span className={'tag ' + (STATUS_TONE[x.status] ?? 'info')}>
                    {t('rms.device.status.' + x.status)}
                  </span>
                </td>
                <td className="cell-actions">
                  <Perms code="rms:device:update">
                    <button className="btn btn-ghost btn-sm" onClick={() => setEditing(x)}>
                      {t('rms.common.edit')}
                    </button>
                  </Perms>
                  <Perms code="rms:device:delete">
                    <button className="btn btn-ghost btn-sm danger" onClick={() => doDelete(x)}>
                      {t('rms.common.delete')}
                    </button>
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && filtered.length === 0 && (
              <tr>
                <td colSpan={7} className="empty-cell">
                  {t('rms.common.noData')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {(showCreate || editing) && (
        <DeviceModal
          types={types}
          areas={areas}
          initial={editing}
          onClose={() => {
            setShowCreate(false);
            setEditing(null);
          }}
          onDone={async () => {
            setShowCreate(false);
            setEditing(null);
            await refresh();
          }}
        />
      )}
    </div>
  );
}

function DeviceModal({
  types,
  areas,
  initial,
  onClose,
  onDone,
}: {
  types: DeviceType[];
  areas: DeviceArea[];
  initial: Device | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [code, setCode] = useState(initial?.code ?? '');
  const [name, setName] = useState(initial?.name ?? '');
  const [deviceTypeId, setDeviceTypeId] = useState(initial?.deviceTypeId ?? '');
  const [areaId, setAreaId] = useState(initial?.areaId ?? '');
  const [ip, setIp] = useState(initial?.ip ?? '');
  const [status, setStatus] = useState<DeviceStatus>(initial?.status ?? 'ENABLED');
  const [remark, setRemark] = useState(initial?.remark ?? '');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit() {
    if (!code || !name || !deviceTypeId) {
      setErr(t('rms.common.code') + ' / ' + t('rms.common.name') + ' / ' + t('rms.device.type') + ' *');
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const payload = {
        code,
        name,
        deviceTypeId,
        areaId: areaId || null,
        ip: ip || null,
        status,
        remark: remark || null,
      };
      if (initial) {
        await api.updateDevice(initial.id, payload);
      } else {
        await api.createDevice(payload);
      }
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
        <div className="modal-head">
          <h3>{initial ? t('rms.common.edit') : t('rms.device.createNew')}</h3>
          <button className="icon-btn" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.device.code')} *</label>
              <input className="text-input" value={code} disabled={!!initial} onChange={(e) => setCode(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.device.name')} *</label>
              <input className="text-input" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.device.type')} *</label>
              <select className="text-input" value={deviceTypeId} onChange={(e) => setDeviceTypeId(e.target.value)}>
                <option value="">—</option>
                {types.map((x) => (
                  <option key={x.id} value={x.id}>
                    {x.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.device.area')}</label>
              <select className="text-input" value={areaId} onChange={(e) => setAreaId(e.target.value)}>
                <option value="">—</option>
                {areas.map((x) => (
                  <option key={x.id} value={x.id}>
                    {x.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field-label">{t('rms.device.ip')}</label>
              <input className="text-input" value={ip} placeholder="192.168.x.x" onChange={(e) => setIp(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.common.status')}</label>
              <select className="text-input" value={status} onChange={(e) => setStatus(e.target.value as DeviceStatus)}>
                <option value="ENABLED">{t('rms.device.status.ENABLED')}</option>
                <option value="DISABLED">{t('rms.device.status.DISABLED')}</option>
              </select>
            </div>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.common.remark')}</label>
            <input className="text-input" value={remark} onChange={(e) => setRemark(e.target.value)} />
          </div>
          {err && <div className="form-error">{err}</div>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-ghost" onClick={onClose}>
            {t('rms.common.cancel')}
          </button>
          <button className="btn btn-primary" disabled={busy} onClick={submit}>
            {t('rms.common.save')}
          </button>
        </div>
      </div>
    </div>
  );
}
