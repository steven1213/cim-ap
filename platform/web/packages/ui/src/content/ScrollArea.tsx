// 滚动区：固定高度的内容容器。
//
// 参考项目的 GoatContent 用 `height - 2` 计算滚动高度（那 2px 是卡片边框），
// 并且要靠 watch + onActivated 手动 update —— 这类高度补偿散落在
// utils/height.js 里共有 50 多个函数。这里交给 flex 布局：父容器定高，
// 本组件 flex:1 + minHeight:0 + overflow:auto，不再做像素运算。
//
// 另一个增量：记住滚动位置。详情页返回列表再进来时，用户不希望从顶部重新找。

import { useEffect, useRef, useState, type ReactNode } from 'react';

export interface CimScrollAreaProps {
  children: ReactNode;
  /** 固定高度（px 或任意 CSS 长度）。不传则撑满父容器。 */
  height?: number | string;
  /** 切换此值时重置滚动位置（如切换了另一个配方）。 */
  resetKey?: string | number;
  /** 是否记住滚动位置（配合 resetKey 使用）。 */
  rememberScroll?: boolean;
  className?: string;
  /** 滚动到底部时触发（无限加载）。 */
  onReachBottom?: () => void;
  /** 距底部多少 px 触发 onReachBottom，默认 40。 */
  reachBottomOffset?: number;
}

export function CimScrollArea({
  children,
  height,
  resetKey,
  rememberScroll = false,
  className,
  onReachBottom,
  reachBottomOffset = 40,
}: CimScrollAreaProps) {
  const ref = useRef<HTMLDivElement>(null);
  // 每个 resetKey 一个滚动位置
  const store = useRef<Map<string | number | undefined, number>>(new Map());
  const [atBottom, setAtBottom] = useState(false);

  // 切换数据源：恢复该 key 上次的位置（开了记忆才有）
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (rememberScroll) {
      const saved = store.current.get(resetKey) ?? 0;
      el.scrollTop = saved;
    } else {
      el.scrollTop = 0;
    }
  }, [resetKey, rememberScroll]);

  return (
    <div
      ref={ref}
      className={className ? `cim-scroll-area ${className}` : 'cim-scroll-area'}
      style={{
        height: height ?? '100%',
        flex: height === undefined ? '1 1 auto' : undefined,
        minHeight: 0,
        overflow: 'auto',
      }}
      onScroll={(e) => {
        const el = e.currentTarget;
        if (rememberScroll) store.current.set(resetKey, el.scrollTop);
        const near =
          el.scrollHeight - el.scrollTop - el.clientHeight <= reachBottomOffset;
        if (near && !atBottom) {
          setAtBottom(true);
          onReachBottom?.();
        } else if (!near && atBottom) {
          setAtBottom(false);
        }
      }}
    >
      {children}
    </div>
  );
}
