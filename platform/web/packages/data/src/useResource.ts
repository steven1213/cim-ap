// useResource：页面取数的唯一姿势。
//
// 由来：改造前 20 多个页面各自写
//     const [list, setList] = useState([]); const [loading, setLoading] = useState(true);
//     async function refresh() { setLoading(true); try { setList(await api.x()) } finally { setLoading(false) } }
//     useEffect(() => { const id = setTimeout(refresh, 200); return () => clearTimeout(id) }, [keyword]);
// 这套写法缺的不是「少几行」，而是四个会真出问题的语义（见下）。这里一次做对。
//
// ── 内建的语义 ───────────────────────────────────────────────
// 1. 竞态：请求序号递增，只接受最新一次回包。
//    快速切筛选/切页时，先发的慢请求后到会覆盖新数据 —— 这是产线上「表格显示的是上一次
//    筛选结果」的根因，肉眼极难发现（数据看着都合法）。
// 2. 取消：deps 变化或卸载时 abort 旧请求，并忽略取消错误。
//    不取消则每次切筛选都留一个在途请求，页签多了会打满浏览器的 6 并发连接。
// 3. 去重：同一签名**同时最多一个在途请求**（新请求替换而不是叠加）。
//    连点「刷新」不再打出一串重复请求。
// 4. 轮询：`document.hidden` 时暂停，恢复可见立即补一次。
//    产线大屏常年开着且常被切到后台，不暂停就是全天候空转。
// 5. 不吞错：`error` 交给页面决定是整页空态还是行内提示，**不在 hook 里弹 Toast**。
//    hook 不知道这个列表是主内容还是侧栏附带信息，弹不弹是页面的判断。
//
// ── 刻意不做 ────────────────────────────────────────────────
// - 不做请求缓存（SWR 式）：hook 不认识 URL，同一资源被两个组件各取一次是正常的，
//   真要共享缓存属于各 ap 的数据层设计，塞进通用 hook 会变成半个状态管理库。
// - 不做重试：重试策略与幂等性相关（POST 不能盲重试），必须在 API 层按端点决定。
// - 不接管分页：分页是 `DataTable` 的受控属性，页面把 page/size 放进 `deps` 即可。

import { useCallback, useEffect, useRef, useState } from 'react';
import { isAbortError, toApiError, type ApiError } from './types';

export interface ResourceOptions {
  /**
   * 依赖变化即重取。可以直接传内联数组（`[keyword, page]`）——
   * 内部按**内容签名**比较，不依赖数组引用，否则每次 render 都会重取一次。
   */
  deps?: unknown[];
  /** 轮询间隔（毫秒）。页面不可见时暂停，恢复可见立即补一次。 */
  refetchInterval?: number;
  /**
   * 依赖变化时保留旧数据（只置 loading），翻页/切筛选时表格不闪空。
   * 注意：这不等于缓存——旧数据只用于占位，回包到了就被替换。
   */
  keepPreviousData?: boolean;
  /** 条件取数（如抽屉没打开就不取）。false 时不发请求，也不改动已有 data。 */
  enabled?: boolean;
}

export interface ResourceResult<T> {
  data: T | undefined;
  loading: boolean;
  error: ApiError | undefined;
  /** 至少完成过一次请求（无论成败）。用于「首次加载 vs 刷新」的骨架屏判断。 */
  loaded: boolean;
  /** 最近一次成功回包的时间戳（毫秒）。 */
  lastUpdated: number | undefined;
  /** 手动重取。与 deps 变化等价，但会打断在途请求后立即重发。 */
  refresh: () => void;
}

/**
 * 依赖数组 → 内容签名。
 *
 * <p>`deps` 通常是内联数组，引用每次都变；直接进 effect 依赖会导致无限重取。这里序列化
 * 成字符串再比较。序列化失败（含循环引用）退回 `map(String)`，绝不抛给调用方。</p>
 */
function signatureOf(deps?: unknown[]): string {
  if (!deps || deps.length === 0) return '';
  try {
    return JSON.stringify(deps) ?? '';
  } catch {
    return deps.map((d) => String(d)).join('\u0001');
  }
}

export function useResource<T>(
  fetcher: (ctx: { signal: AbortSignal }) => Promise<T>,
  opts: ResourceOptions = {},
): ResourceResult<T> {
  const { deps, refetchInterval, keepPreviousData = false, enabled = true } = opts;

  const [data, setData] = useState<T | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | undefined>(undefined);
  const [loaded, setLoaded] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<number | undefined>(undefined);
  // 手动刷新的触发令牌：自增即触发下面那个 effect 重跑，不必把 run 抽成 useCallback
  const [tick, setTick] = useState(0);

  // fetcher 放 ref：页面几乎总是传内联箭头函数，若进依赖数组就会每次 render 都重取。
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  // 序号用于竞态判定；abortRef 保留当前在途请求以便替换
  const seqRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);

  const sig = signatureOf(deps);

  useEffect(() => {
    if (!enabled) return;

    const seq = ++seqRef.current;
    // 替换而非叠加：无论上次是自动取数还是手动刷新，都先取消它
    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;

    setLoading(true);
    // 保留旧数据时不清 error（错误条闪一下再回来很难看）；不保留时清掉旧的错误
    if (!keepPreviousData) setError(undefined);

    void (async () => {
      try {
        const next = await fetcherRef.current({ signal: ac.signal });
        // 竞态：拿到回包不代表它是最新的，序号变了就丢弃
        if (seq !== seqRef.current) return;
        setData(next);
        setError(undefined);
        setLastUpdated(Date.now());
      } catch (e) {
        // 取消不是错误：切筛选/卸载都会走到这里
        if (isAbortError(e)) return;
        if (seq !== seqRef.current) return;
        setError(toApiError(e));
      } finally {
        // 只让最新一次决定 loading / loaded，否则旧请求收尾会把新请求的 loading 关掉
        if (seq === seqRef.current) {
          setLoading(false);
          setLoaded(true);
        }
      }
    })();

    return () => {
      ac.abort();
    };
  }, [sig, tick, enabled, keepPreviousData]);

  // 轮询：隐藏时跳过（不发请求），恢复可见立即补一次
  useEffect(() => {
    if (!enabled || !refetchInterval || refetchInterval <= 0) return;

    const timer = window.setInterval(() => {
      if (!document.hidden) setTick((n) => n + 1);
    }, refetchInterval);

    const onVisible = () => {
      if (!document.hidden) setTick((n) => n + 1);
    };
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [enabled, refetchInterval]);

  const refresh = useCallback(() => setTick((n) => n + 1), []);

  return { data, loading, error, loaded, lastUpdated, refresh };
}
