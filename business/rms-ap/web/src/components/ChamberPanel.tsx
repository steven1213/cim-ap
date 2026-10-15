import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Perms from '@/components/Perms';
import * as api from '@/lib/rmsApi';
import type { DeviceChamber } from '@/types';

/**
 * 机台腔体面板（W7，受控组件）。
 *
 * <p>多腔体机台的配方是按腔体装载的，故腔体是「机台视角」的必要维度。
 * 挂在机台详情抽屉内：指定 {@link deviceId} 即维护该机台的腔体列表——
 * 在哪台机上点开，就维护哪台机的腔体，不再需要单独选机台（一处搞定）。</p>
 */
export default function ChamberSection({
  deviceId,
  onChanged,
}: {
  deviceId: string;
  onChanged?: () => void;
}) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<DeviceChamber[]>([]);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [sortNo, setSortNo] = useState('0');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function load(id: string) {
    if (!id) {
      setRows([]);
      return;
    }
    try {
      setRows(await api.listChambers(id));
    } catch (e: any) {
      setErr(e?.message || 'error');
    }
  }

  useEffect(() => {
    setErr('');
    load(deviceId);
  }, [deviceId]);

  async function add() {
    if (!code.trim() || !name.trim()) {
      setErr(t('rms.chamber.required'));
      return;
    }
    setBusy(true);
    setErr('');
    try {
      await api.createChamber({
        deviceId,
        code: code.trim(),
        name: name.trim(),
        sortNo: Number(sortNo) || 0,
      });
      setCode('');
      setName('');
      setSortNo('0');
      await load(deviceId);
      onChanged?.();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  async function remove(x: DeviceChamber) {
    if (!window.confirm(t('rms.chamber.deleteConfirm'))) return;
    setBusy(true);
    setErr('');
    try {
      await api.deleteChamber(x.id);
      await load(deviceId);
      onChanged?.();
    } catch (e: any) {
      setErr(e?.message || 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>{t('rms.chamber.title')}</h3>
        <span className="muted">{t('rms.chamber.sub')}</span>
      </div>
      {err && (
        <div className="alert err">
          <span>{err}</span>
        </div>
      )}

      <div className="form-grid">
        <div className="field">
          <label className="field-label">{t('rms.chamber.code')}</label>
          <input
            className="text-input"
            value={code}
            placeholder="CH-A"
            onChange={(e) => setCode(e.target.value)}
          />
        </div>
        <div className="field">
          <label className="field-label">{t('rms.chamber.name')}</label>
          <input
            className="text-input"
            value={name}
            placeholder={t('rms.chamber.namePh')}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="field">
          <label className="field-label">{t('rms.chamber.sortNo')}</label>
          <input
            className="text-input"
            value={sortNo}
            onChange={(e) => setSortNo(e.target.value)}
          />
        </div>
        <div className="field">
          <label className="field-label">&nbsp;</label>
          <Perms code="rms:device:create">
            <button className="btn btn-primary" disabled={busy} onClick={add}>
              {t('rms.chamber.add')}
            </button>
          </Perms>
        </div>
      </div>

      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              <th>{t('rms.chamber.code')}</th>
              <th>{t('rms.chamber.name')}</th>
              <th>{t('rms.chamber.sortNo')}</th>
              <th>{t('rms.common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((x) => (
              <tr key={x.id}>
                <td className="mono">{x.code}</td>
                <td>{x.name}</td>
                <td className="mono">{x.sortNo}</td>
                <td className="cell-actions">
                  <Perms code="rms:device:delete">
                    <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => remove(x)}>
                      {t('rms.common.delete')}
                    </button>
                  </Perms>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={4} className="empty-cell">
                  {t('rms.chamber.empty')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
