import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Panel from '@/components/Panel';
import DetailModal from '@/components/DetailModal';
import { IconCheck, IconRefresh, IconSearch } from '@/components/Icons';
import { listDonePaged, type PageResult } from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { fmtDate } from '@/lib/format';
import type { TaskView } from '@/types';

export default function DonePage() {
  const { t } = useTranslation();
  const { userId } = useIdentityStore();
  const PAGE_SIZE = 10;
  const [paged, setPaged] = useState<PageResult<TaskView> | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [detailId, setDetailId] = useState<string | null>(null);
  /** 关键字过滤（标题/业务单号/节点名） */
  const [q, setQ] = useState('');

  const tasks = paged?.records ?? [];
  const totalPages = paged ? Math.max(1, Math.ceil(paged.total / PAGE_SIZE)) : 1;
  const filtered = tasks.filter((tk) => {
    if (!q.trim()) return true;
    const kw = q.trim().toLowerCase();
    return [tk.title, tk.businessKey, tk.name]
      .some((v) => v && v.toLowerCase().includes(kw));
  });

  async function load(p = 0) {
    setLoading(true);
    try {
      setPaged(await listDonePaged(p + 1, PAGE_SIZE));
    } catch (e) {
      // 保留旧列表
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    load(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  function goto(p: number) {
    setPage(p);
    load(p);
  }

  return (
    <div className="page">
      <Panel
        title={t('done.title')}
        sub={t('done.sub')}
        actions={
          <>
            <span className="search-inline">
              <IconSearch width={13} height={13} />
              <input
                className="text-input"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder={t('common.searchPlaceholder')}
              />
            </span>
            <button className="icon-btn" onClick={() => load(page)} title={t('common.refresh')} aria-label={t('common.refresh')}>
              <IconRefresh width={16} height={16} />
            </button>
          </>
        }
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('done.colName')}</th>
                <th>{t('common.colTitle')}</th>
                <th>{t('done.colBusinessKey')}</th>
                <th>{t('done.colAssignee')}</th>
                <th>{t('done.colEnded')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={6} className="dim" style={{ textAlign: 'center' }}>{t('common.loading')}</td>
                </tr>
              )}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td colSpan={6}>
                    <div className="empty">
                      <IconCheck width={26} height={26} />
                      <span>{t('common.empty')}</span>
                    </div>
                  </td>
                </tr>
              )}
              {filtered.map((tk) => (
                <tr key={tk.taskId}>
                  <td>{tk.name}</td>
                  <td>{tk.title || '—'}</td>
                  <td className="mono">{tk.businessKey}</td>
                  <td>{tk.assignee ?? '—'}</td>
                  <td className="mono">{fmtDate(tk.dueDate ?? tk.createdAt)}</td>
                  <td>
                    <button className="btn btn-ghost btn-sm" onClick={() => setDetailId(tk.processInstanceId)}>
                      <IconSearch width={13} height={13} /> {t('common.detail')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {paged && paged.total > PAGE_SIZE && (
          <div className="pager">
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => goto(page - 1)}>
              {t('page.prev')}
            </button>
            <span className="dim">{t('page.info').replace('{page}', String(page + 1)).replace('{total}', String(totalPages))}</span>
            <button className="btn btn-ghost btn-sm" disabled={page >= totalPages - 1} onClick={() => goto(page + 1)}>
              {t('page.next')}
            </button>
          </div>
        )}
      </Panel>

      {detailId && <DetailModal processInstanceId={detailId} onClose={() => setDetailId(null)} onChanged={load} />}
    </div>
  );
}
