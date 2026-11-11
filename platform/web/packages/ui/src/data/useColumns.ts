// 列偏好（显示/隐藏 + 顺序）：参考 industry.mes 的「列设置」能力重写为 React hook。
//
// 与原实现的差别（有意为之）：
// 1. 原实现把列状态塞在组件的 reactive options 里，与表格渲染耦合（GoatTable 1057 行）；
//    这里抽成纯 hook，DataTable 只负责渲染；
// 2. 原实现没有持久化，刷新即回默认；这里按 `settingsKey` 落 localStorage（按应用/页面隔离）；
// 3. 原实现允许把所有列都隐藏（表格变成空壳）；这里禁止隐藏最后一列。

import { useCallback, useMemo, useState } from 'react';

export interface ColumnMeta {
  key: string;
  /** 出厂默认是否隐藏（用户可在列设置里打开）。 */
  hiddenByDefault?: boolean;
}

export interface ColumnState {
  key: string;
  hidden: boolean;
}

const PREFIX = 'cim.columns.';

function readSaved(storageKey: string): ColumnState[] | null {
  try {
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return null;
    return parsed
      .filter((x): x is ColumnState => !!x && typeof x === 'object' && typeof (x as ColumnState).key === 'string')
      .map((x) => ({ key: x.key, hidden: !!x.hidden }));
  } catch {
    // 存储被禁用 / JSON 损坏：静默回落默认列，不阻断页面
    return null;
  }
}

function writeSaved(storageKey: string | null, state: ColumnState[]): void {
  if (!storageKey) return;
  try {
    if (state.length === 0) window.localStorage.removeItem(storageKey);
    else window.localStorage.setItem(storageKey, JSON.stringify(state));
  } catch {
    /* 忽略：偏好丢失不影响功能 */
  }
}

/**
 * @param columns     声明的列（顺序即默认顺序）
 * @param settingsKey 持久化键；不传 = 不持久化（仍可在当前会话调整）
 */
export function useColumns(columns: ColumnMeta[], settingsKey?: string) {
  const storageKey = settingsKey ? PREFIX + settingsKey : null;
  const [saved, setSaved] = useState<ColumnState[]>(() => (storageKey ? readSaved(storageKey) ?? [] : []));

  // 合并：已保存顺序优先（过滤掉已删除的列），新增列追加到末尾
  const order = useMemo<ColumnState[]>(() => {
    const declared = new Map(columns.map((c) => [c.key, c]));
    const out: ColumnState[] = [];
    for (const s of saved) {
      if (declared.has(s.key)) out.push({ key: s.key, hidden: !!s.hidden });
    }
    for (const c of columns) {
      if (!out.some((o) => o.key === c.key)) out.push({ key: c.key, hidden: !!c.hiddenByDefault });
    }
    return out;
  }, [columns, saved]);

  const commit = useCallback(
    (next: ColumnState[]) => {
      setSaved(next);
      writeSaved(storageKey, next);
    },
    [storageKey],
  );

  const visibleCount = order.filter((o) => !o.hidden).length;

  /** 切换某列显隐；不允许隐藏最后一列（返回 false 表示被拒绝）。 */
  const setHidden = useCallback(
    (key: string, hidden: boolean): boolean => {
      const target = order.find((o) => o.key === key);
      if (!target) return false;
      if (hidden && visibleCount <= 1 && !target.hidden) return false;
      commit(order.map((o) => (o.key === key ? { ...o, hidden } : o)));
      return true;
    },
    [order, visibleCount, commit],
  );

  /** 顺序上移/下移（dir = -1 / 1）。 */
  const move = useCallback(
    (key: string, dir: -1 | 1): void => {
      const i = order.findIndex((o) => o.key === key);
      const j = i + dir;
      if (i < 0 || j < 0 || j >= order.length) return;
      const next = order.slice();
      const tmp = next[i]!;
      next[i] = next[j]!;
      next[j] = tmp;
      commit(next);
    },
    [order, commit],
  );

  const reset = useCallback(() => {
    commit([]);
  }, [commit]);

  const isCustomized = saved.length > 0;

  return { order, visibleCount, setHidden, move, reset, isCustomized };
}
