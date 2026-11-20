import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import { useConfirm, useToast } from '@cim/ui';
import * as api from '@/lib/mdsApi';
import type { LocationType } from '@/lib/mdsApi';

/** 右详情四态中的三态（简单主数据无复制动作，copy 仅工序库页面自持）。 */
export type DetailMode = 'view' | 'edit' | 'create';

export interface MasterDetailOptions<T extends { id: string }> {
  /** 拉取列表（keyword 由页面侧 filter 客户端过滤，或后端自行处理）。 */
  fetchList: (signal?: AbortSignal) => Promise<T[]>;
  /** 单条删除（批量删除循环复用）。 */
  remove: (id: string) => Promise<void>;
  /** 编辑锁类型；无锁域不传。编辑抢锁、取消/切换/卸载显式释放，保存由后端同事务清锁。 */
  lockType?: LocationType;
  /** 客户端关键字过滤（列表 API 不支持 keyword 的域）。 */
  filter?: (row: T, keyword: string) => boolean;
  /** 页面侧脏检查（有未保存修改时切行需确认放弃）。 */
  isDirty?: () => boolean;
}

/**
 * 简单主数据「左列表 + 右详情」动线 Hook——工序库（OperationsPage）改版同款模式的统一实现。
 *
 * <p>高频动线（搜索 → 点选 → 就地编辑 → 保存）全程零弹窗：编辑在右侧详情面板完成，
 * 不丢视线焦点；中频动作（新建/批量删除）在列表工具栏；对象级动作（编辑/删除）贴详情头。</p>
 *
 * <p>编辑锁「人 vs 人」互斥：进入编辑先抢锁（抢不到提示谁正在编辑，不进表单）；保存成功由
 * 后端同事务清锁，取消/切行/卸载由本 Hook 显式释放。批量删除逐条执行并汇报成败。</p>
 */
export function useMasterDetail<T extends { id: string }>(opts: MasterDetailOptions<T>) {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [keyword, setKeyword] = useState('');
  const res = useResource(({ signal }) => opts.fetchList(signal));
  const list = useMemo(() => res.data ?? [], [res.data]);

  // 左列表：选中 + 勾选（批量删除）
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [checkedKeys, setCheckedKeys] = useState<string[]>([]);

  // 右详情：view | edit | create
  const [mode, setMode] = useState<DetailMode>('view');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  /** 编辑中持有锁的行 id（保存清锁在后端事务内，前端只在取消/切换/卸载时显式释放）。 */
  const lockedIdRef = useRef<string | null>(null);
  const dirtyFnRef = useRef<() => boolean>(() => false);
  dirtyFnRef.current = opts.isDirty ?? (() => false);
  const optsRef = useRef(opts);
  optsRef.current = opts;

  const releaseLock = (id: string) => {
    const lt = optsRef.current.lockType;
    if (lt) void api.releaseLock(lt, id).catch(() => {});
  };

  // 卸载兜底：编辑中直接离开页面，释放持有的编辑锁
  useEffect(
    () => () => {
      if (lockedIdRef.current) releaseLock(lockedIdRef.current);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  const rows = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    const filter = optsRef.current.filter;
    if (!kw || !filter) return list;
    return list.filter((r) => filter(r, kw));
  }, [list, keyword]);

  const selected = useMemo(() => list.find((r) => r.id === selectedId) ?? null, [list, selectedId]);

  const enterCreate = () => {
    setErr('');
    setMode('create');
  };

  /** 进入编辑：先抢锁，抢不到提示「谁正在编辑」；返回是否成功进入。 */
  const enterEdit = async (row: T): Promise<boolean> => {
    const lt = optsRef.current.lockType;
    if (lt) {
      try {
        await api.claimLock(lt, row.id);
      } catch (e) {
        toast.error(e instanceof Error ? e.message : String(e));
        return false;
      }
    }
    lockedIdRef.current = row.id;
    setErr('');
    setMode('edit');
    return true;
  };

  /** 退出编辑回查看：显式释放编辑锁。 */
  const backToView = () => {
    if (lockedIdRef.current) {
      releaseLock(lockedIdRef.current);
      lockedIdRef.current = null;
    }
    setErr('');
    setMode('view');
  };

  const onRowClick = async (row: T) => {
    if (row.id === selectedId && mode === 'view') return;
    if (dirtyFnRef.current()) {
      const ok = await confirm({
        titleKey: 'mds.common.discardTitle',
        contentKey: 'mds.common.discardText',
        okKey: 'mds.common.ok',
      });
      if (!ok) return;
    }
    if (lockedIdRef.current) {
      releaseLock(lockedIdRef.current);
      lockedIdRef.current = null;
    }
    setSelectedId(row.id);
    setMode('view');
    setErr('');
  };

  /** 保存成功后的统一收尾：刷新列表、选中刚保存的行、回查看态（锁已由后端同事务清掉）。 */
  const saved = (id: string) => {
    lockedIdRef.current = null;
    res.refresh();
    setSelectedId(id);
    setMode('view');
    setErr('');
  };

  const doDelete = async (row: T) => {
    const ok = await confirm({
      titleKey: 'mds.common.delete',
      contentKey: 'mds.common.confirmDelete',
      danger: true,
      okKey: 'mds.common.delete',
    });
    if (!ok) return;
    try {
      await optsRef.current.remove(row.id);
      toast.success(t('mds.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    if (row.id === selectedId) setSelectedId(null);
    setCheckedKeys((ks) => ks.filter((k) => k !== row.id));
    res.refresh();
  };

  const doBatchDelete = async () => {
    if (!checkedKeys.length) return;
    const ok = await confirm({
      titleKey: 'mds.common.batchDeleteConfirm',
      content: t('mds.common.batchDeleteConfirmText', { n: checkedKeys.length }),
      okKey: 'mds.common.delete',
      danger: true,
    });
    if (!ok) return;
    let okCount = 0;
    let failCount = 0;
    for (const id of checkedKeys) {
      try {
        await optsRef.current.remove(id);
        okCount += 1;
      } catch {
        failCount += 1;
      }
    }
    if (failCount === 0) toast.success(t('mds.common.deleted'));
    else toast.error(t('mds.common.batchResult', { ok: okCount, fail: failCount }));
    if (selectedId && checkedKeys.includes(selectedId)) setSelectedId(null);
    setCheckedKeys([]);
    res.refresh();
  };

  return {
    keyword,
    setKeyword,
    res,
    rows,
    selected,
    selectedId,
    checkedKeys,
    setCheckedKeys,
    mode,
    busy,
    setBusy,
    err,
    setErr,
    enterCreate,
    enterEdit,
    backToView,
    onRowClick,
    saved,
    doDelete,
    doBatchDelete,
  };
}
