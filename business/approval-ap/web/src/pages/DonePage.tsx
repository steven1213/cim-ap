import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import { CimButton, DataTable, Page, type DataTableColumn } from '@cim/ui';
import DetailModal from '@/components/DetailModal';
import { IconSearch } from '@/components/Icons';
import { listDone } from '@/lib/api';
import { useIdentityStore } from '@/store/identityStore';
import { fmtDate } from '@/lib/format';
import type { TaskView } from '@/types';

const PAGE_SIZE = 20;

export default function DonePage() {
  const { t } = useTranslation();
  const { userId } = useIdentityStore();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(PAGE_SIZE);
  const [q, setQ] = useState('');
  const [detailId, setDetailId] = useState<string | null>(null);

  /**
   * 取**全量**再在本地分页，不用 `/tasks/done/page`。
   *
   * <p>改造前是服务端分 10 条 + 客户端按关键字过滤当前页：搜「BK-2026-012」时
   * 如果它落在第 3 页就永远搜不到，而界面看起来「搜过了、就是没有」——比报错更误导。
   * 要么把关键字下推给后端（后端没有该参数），要么本地分页；选后者，并把每页放到 20 条。</p>
   */
  const doneRes = useResource<TaskView[]>(
    ({ signal }) => listDone(undefined, undefined, { signal }),
    { deps: [userId] },
  );

  const all = doneRes.data ?? [];
  const filtered = useMemo(() => {
    const kw = q.trim().toLowerCase();
    if (!kw) return all;
    return all.filter((tk) =>
      [tk.title, tk.businessKey, tk.name, tk.assignee].some((v) => v && v.toLowerCase().includes(kw)),
    );
  }, [all, q]);

  const pageRows = filtered.slice((page - 1) * size, page * size);

  const columns: Array<DataTableColumn<TaskView>> = [
    { key: 'name', titleKey: 'done.colName', width: 150 },
    { key: 'title', titleKey: 'common.colTitle', ellipsis: true, render: (tk) => tk.title || '—' },
    { key: 'businessKey', titleKey: 'done.colBusinessKey', width: 170, mono: true },
    { key: 'assignee', titleKey: 'done.colAssignee', width: 120, render: (tk) => tk.assignee ?? '—' },
    {
      key: 'endedAt',
      titleKey: 'done.colEnded',
      width: 160,
      mono: true,
      render: (tk) => fmtDate(tk.dueDate ?? tk.createdAt),
    },
    {
      key: 'ops',
      titleKey: 'common.actions',
      width: 96,
      render: (tk) => (
        <CimButton size="small" icon={<IconSearch width={13} height={13} />} onClick={() => setDetailId(tk.processInstanceId)}>
          {t('common.detail')}
        </CimButton>
      ),
    },
  ];

  return (
    <Page>
        <DataTable<TaskView>
          titleKey="done.title"
          columns={columns}
          rows={pageRows}
          rowKey={(tk) => tk.taskId}
          loading={doneRes.loading}
          error={doneRes.error ? doneRes.error.message : null}
          onRetry={doneRes.refresh}
          onRefresh={doneRes.refresh}
          search={{
            value: q,
            onChange: (v) => {
              setQ(v);
              // 过滤条件变了还停在第 3 页会看到「空页 + 明明有数据」，回第一页
              setPage(1);
            },
            placeholderKey: 'common.searchPlaceholder',
            width: 240,
          }}
          pagination={{
            page,
            size,
            total: filtered.length,
            onChange: (p, s) => {
              setPage(s === size ? p : 1);
              setSize(s);
            },
            sizeOptions: [10, 20, 50, 100],
          }}
          emptyTitleKey="common.empty"
          scrollX="max-content"
        />

      {detailId && <DetailModal processInstanceId={detailId} onClose={() => setDetailId(null)} onChanged={doneRes.refresh} />}
    </Page>
  );
}
