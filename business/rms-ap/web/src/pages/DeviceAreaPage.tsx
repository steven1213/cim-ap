import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import type { DeviceArea } from '@/types';

export default function DeviceAreaPage() {
  const { t } = useTranslation();
  const [list, setList] = useState<DeviceArea[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');

  const [editing, setEditing] = useState<DeviceArea | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  async function refresh() {
    setLoading(true);
    try {
      const data = await api.listDeviceAreas({ keyword: keyword || undefined });
      setList(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const id = setTimeout(refresh, 200);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword]);

  async function doDelete(x: DeviceArea) {
    if (!confirm(t('rms.common.confirmDelete'))) return;
    await api.deleteDeviceArea(x.id);
    await refresh();
  }

  const filtered = useMemo(() => {
    if (!keyword) return list;
    const k = keyword.toLowerCase();
    return list.filter(
      (x) => x.code.toLowerCase().includes(k) || x.name.toLowerCase().includes(k),
    );
  }, [list, keyword]);

  return (
    <div className="page">
      <div className="page-head">
        <h1>{t('rms.deviceArea.title')}</h1>
        <Perms code="rms:device-area:create">
          <button className="btn btn-primary btn-sm" onClick={() => setShowCreate(true)}>
            + {t('rms.deviceArea.createNew')}
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
              <th>{t('rms.deviceArea.code')}</th>
              <th>{t('rms.deviceArea.name')}</th>
              <th>{t('rms.deviceArea.parent')}</th>
              <th>{t('rms.common.remark')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.code}</td>
                <td>{x.name}</td>
                <td>{x.parentName ?? t('rms.common.dash')}</td>
                <td className="muted">{x.remark ?? t('rms.common.dash')}</td>
                <td className="cell-actions">
                  <Perms code="rms:device-area:update">
                    <button className="btn btn-ghost btn-sm" onClick={() => setEditing(x)}>
                      {t('rms.common.edit')}
                    </button>
                  </Perms>
                  <Perms code="rms:device-area:delete">
                    <button className="btn btn-ghost btn-sm danger" onClick={() => doDelete(x)}>
                      {t('rms.common.delete')}
                    </button>
                  </Perms>
                </td>
              </tr>
            ))}
            {!loading && filtered.length === 0 && (
              <tr>
                <td colSpan={5} className="empty-cell">
                  {t('rms.common.noData')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {(showCreate || editing) && (
        <AreaModal
          areas={list}
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

function AreaModal({
  areas,
  initial,
  onClose,
  onDone,
}: {
  areas: DeviceArea[];
  initial: DeviceArea | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [code, setCode] = useState(initial?.code ?? '');
  const [name, setName] = useState(initial?.name ?? '');
  const [parentId, setParentId] = useState(initial?.parentId ?? '');
  const [remark, setRemark] = useState(initial?.remark ?? '');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  // 编辑时不能选自己作为父级（避免成环）
  const parentOptions = areas.filter((a) => !initial || a.id !== initial.id);

  async function submit() {
    if (!code || !name) {
      setErr(t('rms.common.code') + ' / ' + t('rms.common.name') + ' *');
      return;
    }
    setBusy(true);
    setErr('');
    try {
      const payload = {
        code,
        name,
        parentId: parentId || null,
        remark: remark || null,
      };
      if (initial) {
        await api.updateDeviceArea(initial.id, payload);
      } else {
        await api.createDeviceArea(payload);
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
          <h3>{initial ? t('rms.common.edit') : t('rms.deviceArea.createNew')}</h3>
          <button className="icon-btn" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="modal-body">
          <div className="form-grid">
            <div className="field">
              <label className="field-label">{t('rms.deviceArea.code')} *</label>
              <input className="text-input" value={code} disabled={!!initial} onChange={(e) => setCode(e.target.value)} />
            </div>
            <div className="field">
              <label className="field-label">{t('rms.deviceArea.name')} *</label>
              <input className="text-input" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
          </div>
          <div className="field">
            <label className="field-label">{t('rms.deviceArea.parent')}</label>
            <select className="text-input" value={parentId} onChange={(e) => setParentId(e.target.value)}>
              <option value="">—</option>
              {parentOptions.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
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
