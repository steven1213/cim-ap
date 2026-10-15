import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import Panel from '@/components/Panel';
import { IconShield, IconSearch, IconCheck } from '@/components/Icons';
import { listAudit } from '@/lib/api';
import { toast } from '@/store/toastStore';
import { fmtDate, shortHash } from '@/lib/format';
import type { AuditView } from '@/types';

function actionTag(action: string): string {
  if (action.startsWith('COMPLETE_APPROVE') || action === 'START') return 'ok';
  if (action.startsWith('COMPLETE_REJECT') || action.startsWith('CALLBACK_FAILED')) return 'err';
  if (action === 'RETURN' || action === 'REASSIGN') return 'warn';
  return 'info';
}

export default function AuditPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const [key, setKey] = useState(params.get('businessKey') ?? '');
  const [rows, setRows] = useState<AuditView[]>([]);
  const [loading, setLoading] = useState(false);
  const [chainOk, setChainOk] = useState<boolean | null>(null);

  async function search(k: string) {
    if (!k.trim()) {
      toast(t('audit.businessKey') + ' ?', 'warn');
      return;
    }
    setLoading(true);
    setParams({ businessKey: k.trim() });
    try {
      const data = await listAudit(k.trim());
      setRows(data);
      // 校验哈希链：每条 prevHash 应等于上一条的 hash
      let ok = data.length > 0;
      for (let i = 1; i < data.length; i++) {
        if (data[i].prevHash !== data[i - 1].hash) {
          ok = false;
          break;
        }
      }
      setChainOk(ok);
    } catch (e) {
      toast((e as Error).message, 'err');
    } finally {
      setLoading(false);
    }
  }

  // 通过导航（我发起 → 审计追溯）带参进入时自动查询
  useEffect(() => {
    const k = params.get('businessKey');
    if (k) {
      setKey(k);
      void search(k);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="page">
      <Panel title={t('audit.title')} sub={t('audit.sub')}>
        <div className="row">
          <div className="field" style={{ flex: '1 1 280px' }}>
            <label className="field-label">{t('audit.businessKey')}</label>
            <input
              className="mono"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && search(key)}
              placeholder="BK-2026-001"
            />
          </div>
          <button className="btn btn-sm" onClick={() => search(key)} disabled={loading}>
            <IconSearch width={14} height={14} /> {t('audit.search')}
          </button>
        </div>

        {chainOk === true && (
          <div className="alert ok" style={{ marginTop: 12 }}>
            <IconCheck width={15} height={15} />
            {t('audit.chainOk')}
          </div>
        )}
        {chainOk === false && (
          <div className="alert err" style={{ marginTop: 12 }}>
            {t('audit.chainBroken')}
          </div>
        )}

        {rows.length > 0 && (
          <div className="table-wrap" style={{ marginTop: 12 }}>
            <table className="data">
              <thead>
                <tr>
                  <th className="num">#</th>
                  <th>{t('audit.colAction')}</th>
                  <th>{t('audit.colActor')}</th>
                  <th>{t('audit.colBefore')}</th>
                  <th>{t('audit.colAfter')}</th>
                  <th>{t('audit.colDetail')}</th>
                  <th>{t('audit.colTime')}</th>
                  <th>{t('audit.colHash')}</th>
                  <th>{t('audit.colPrevHash')}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((a) => (
                  <tr key={a.id}>
                    <td className="num">{a.seq}</td>
                    <td><span className={'tag ' + actionTag(a.action)}>{t('audit.action.' + a.action)}</span></td>
                    <td>{a.actor}</td>
                    <td className="mono">{a.beforeState}</td>
                    <td className="mono">{a.afterState}</td>
                    <td style={{ maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={a.detail}>{a.detail}</td>
                    <td className="mono">{fmtDate(a.createdAt)}</td>
                    <td className="mono" title={a.hash}>{shortHash(a.hash, 12)}</td>
                    <td className="mono" title={a.prevHash}>{shortHash(a.prevHash, 12)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {!loading && rows.length === 0 && chainOk === null && (
          <div className="empty" style={{ marginTop: 16 }}>
            <IconShield width={26} height={26} />
            <span>{t('audit.empty')}</span>
          </div>
        )}
      </Panel>
    </div>
  );
}
