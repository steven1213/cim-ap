// 数据字典注入（枚举值 → 显示文案 / 语义档）。
//
// 为什么字典不走 i18n（设计稿 §5 已论证）：
//   recipeState=DRAFT 是**业务枚举**，不是界面文案。塞进 i18n 会导致
//   新增一个枚举值就要发一次翻译包、字典的启用/停用无法表达、
//   后端枚举与前端键无法校验。后端已有 sys_dict / sys_dict_item
//   （GET /sys/dicts/code/{code}/items，字段 itemKey / itemValue）。
//
// 但 @cim/ui 不得 import 数据层，所以这里仍然是注入：
//   应用把「按 code 加载字典项」的实现传进来，组件负责缓存与渲染。
//
// 未注入时的行为：`useDict` 返回空列表，`EnumTag` 回落为显示原始枚举值。
// 也就是「没有字典也能跑，只是没翻译」，不会白屏。

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { toneOf, type StatusTone } from '@cim/theme';
import { useUiLabel } from './locale';
import { ToneTag } from './data/StatusTag';

export interface DictItem {
  /** 落库值，如 DRAFT。 */
  value: string;
  /** 显示文案，如「草稿」。 */
  label: string;
  /** 语义档；不填则由 @cim/theme/status 的映射表推断。 */
  tone?: StatusTone;
  disabled?: boolean;
}

export interface UiDictValue {
  /** 已加载的字典项（同步读，未加载返回空数组）。 */
  get: (code: string) => DictItem[];
  /** 触发加载（幂等，重复调用只发一次请求）。 */
  load: (code: string) => void;
  /** 是否正在加载。 */
  isLoading: (code: string) => boolean;
  /** 加载失败信息。 */
  getError: (code: string) => string | null;
}

const EMPTY: DictItem[] = [];

const FALLBACK: UiDictValue = {
  get: () => EMPTY,
  load: () => undefined,
  isLoading: () => false,
  getError: () => null,
};

const UiDictContext = createContext<UiDictValue>(FALLBACK);

/**
 * 字典提供者。`loader` 应返回该 code 下的全部字典项，实现方自行决定缓存策略
 * （建议在 loader 内部再包一层 Promise 去重，这里只保证不重复调用同一 key 的未完成请求）。
 */
export function UiDictProvider({
  loader,
  children,
}: {
  loader: (code: string) => Promise<DictItem[]>;
  children: ReactNode;
}) {
  const [state, setState] = useState<Record<string, DictItem[]>>({});
  const [loading, setLoading] = useState<Record<string, boolean>>({});
  const [errors, setErrors] = useState<Record<string, string | null>>({});
  // 在途/已完成标记放 ref：避免 setState 异步导致同一 code 并发重复请求
  const inflight = useRef<Set<string>>(new Set());

  const load = useCallback(
    (code: string) => {
      if (!code || inflight.current.has(code)) return;
      inflight.current.add(code);
      setLoading((s) => ({ ...s, [code]: true }));
      loader(code)
        .then((items) => {
          setState((s) => ({ ...s, [code]: items }));
          setErrors((s) => ({ ...s, [code]: null }));
        })
        .catch((e: unknown) => {
          // 失败后允许重试：把标记移除
          inflight.current.delete(code);
          setErrors((s) => ({ ...s, [code]: e instanceof Error ? e.message : String(e) }));
        })
        .finally(() => setLoading((s) => ({ ...s, [code]: false })));
    },
    [loader],
  );

  const value = useMemo<UiDictValue>(
    () => ({
      get: (code) => state[code] ?? EMPTY,
      load,
      isLoading: (code) => !!loading[code],
      getError: (code) => errors[code] ?? null,
    }),
    [state, loading, errors, load],
  );

  return <UiDictContext.Provider value={value}>{children}</UiDictContext.Provider>;
}

/** 取字典上下文（一般用 useDict 就够）。 */
export function useDictContext(): UiDictValue {
  return useContext(UiDictContext);
}

/**
 * 订阅一个字典。
 *
 * ```tsx
 * const { items, labelOf, loading } = useDict('recipeState');
 * ```
 *
 * 首次调用会自动触发加载；同一 code 被多处使用时只发一次请求。
 */
export function useDict(code: string | undefined): {
  items: DictItem[];
  loading: boolean;
  error: string | null;
  /** 值 → 文案；未命中时回落为原始值（便于发现后端多回了一个未登记的枚举）。 */
  labelOf: (value: string | null | undefined) => string;
  /** 值 → 语义档；字典项未配 tone 时用映射表推断。 */
  toneOf: (value: string | null | undefined) => StatusTone;
} {
  const ctx = useContext(UiDictContext);
  const items = code ? ctx.get(code) : EMPTY;
  const loading = code ? ctx.isLoading(code) : false;
  const error = code ? ctx.getError(code) : null;
  // load 是 useCallback([loader])，引用稳定；不放进依赖会漏掉 loader 变化的场景
  const { load } = ctx;

  useEffect(() => {
    if (code) load(code);
  }, [code, load]);

  return useMemo(() => {
    const byValue = new Map(items.map((i) => [i.value, i]));
    return {
      items,
      loading,
      error,
      labelOf: (v) => {
        if (v === null || v === undefined || v === '') return '—';
        return byValue.get(v)?.label ?? v;
      },
      toneOf: (v) => {
        if (v === null || v === undefined) return 'idle' as StatusTone;
        return byValue.get(v)?.tone ?? toneOf(code ?? '', v);
      },
    };
  }, [items, loading, error, code]);
}

/**
 * 枚举标签：字典 + i18n 双兜底。
 *
 * 取值顺序：字典 label → `enum.{dict}.{value}` 译文 → 原始枚举值。
 * 这样在三类环境都能显示正确：已配字典、只配了译文、什么都没配但要看得到值。
 */
export function EnumTag({
  dict,
  value,
  label,
  tone,
  children,
}: {
  dict: string;
  value: string | null | undefined;
  /** 覆盖文案。 */
  label?: string;
  /** 覆盖语义档。 */
  tone?: StatusTone;
  /** 完全自定义内容（优先级最高）。 */
  children?: ReactNode;
}) {
  const uiLabel = useUiLabel();
  const d = useDict(dict);
  const raw = value ?? '—';
  const text = label ?? (d.items.length ? d.labelOf(raw) : uiLabel(`enum.${dict}.${raw}`, raw));
  // 用统一的 ToneTag 渲染：语义色只有一处实现（@cim/theme 的 TONE_VAR）
  return <ToneTag tone={tone ?? d.toneOf(raw)}>{children ?? text}</ToneTag>;
}
