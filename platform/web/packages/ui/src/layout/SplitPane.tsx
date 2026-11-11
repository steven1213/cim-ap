// 主从双栏：左「清单/树」定宽、右「详情/编辑」自适应。
//
// <p><b>与 `CimSplitter` 的分工（容易混，先看这段）</b>：
// `CimSplitter` 是**可拖拽**分栏（antd `Splitter`，用户能改宽度、尺寸可上报）；
// 本组件是**固定布局**（左列 280–380px 之间自适应、窗口窄于 1000px 时自动堆叠成单列）。
// 管理页的主从形态 99% 用后者——用户不需要也不应该去调「树有多宽」，
// 而且定宽列能在窄屏优雅落成上下布局，可拖拽分栏做不到。
// 只有在「两栏都可能是主工作区」（如配方对比、IDE 式布局）时才用 `CimSplitter`。</p>
//
// <p><b>为什么值得封一层</b>：`.split` 不只是「两列 grid」，它对**直接子元素**有要求——
// `.split > .panel { min-height: 0 }`、`.split .panel-body` 要弹性填充并内部滚动、
// 左列还要 `.panel.scroll-y` 吸顶。这些约束写在 CSS 里看不见，页面手写 div 时
// 少一层 Panel 或包一层 div，就会变成「表格把面板撑破、整页出现双滚动条」。
// 收进组件后，约束随组件走。</p>
//
// <p><b>为什么用 children 而不是 `left`/`right`</b>：两侧都是一个上百行的 `<Panel>`
// （带 actions、错误分支、表格），塞进 props 会让缩进翻倍、diff 难读。
// 代价是「必须正好两个子元素」靠运行期提醒而不是类型保证——`panel` 是直接子元素
// 这件事本身也是 CSS 的要求，类型系统同样保证不了。</p>

import { useEffect, type CSSProperties, type ReactNode } from 'react';

export interface CimSplitPaneProps {
  /** 正好两个：左（清单/树，通常 `<Panel className="scroll-y">`）、右（详情/编辑）。 */
  children: ReactNode;
  /**
   * 左列宽度覆盖（默认 `minmax(280px, 380px)`）。
   *
   * <p>只在左列内容明显更宽/更窄时用（如权限树层级深、语言列表项短）。
   * 传数字按 px 处理。</p>
   */
  leftWidth?: number | string;
  className?: string;
}

export function CimSplitPane({ children, leftWidth, className }: CimSplitPaneProps) {
  const count = Array.isArray(children) ? children.filter(Boolean).length : 1;

  // 只在开发期提醒：多一层包裹或漏一层是「双滚动条 / 面板被撑破」的常见来源，
  // 但报错会拦住一个能正常渲染的页面，代价大于收益。
  useEffect(() => {
    // 不用 `import.meta.env.DEV` 直读：`@cim/ui` 是被应用 tsconfig 以源码方式编译的，
    // 里面不一定带 `vite/client` 的类型；这里只做「有就读、没有就当非 DEV」。
    const dev = (import.meta as unknown as { env?: { DEV?: boolean } }).env?.DEV === true;
    if (dev && count !== 2) {
      console.warn(
        `[CimSplitPane] 期望 2 个直接子元素（左清单 + 右详情），实际 ${count} 个。` +
          '多余包裹层会破坏 `.split > .panel` 与 `.split .panel-body` 的滚动约束。',
      );
    }
  }, [count]);

  const style: CSSProperties | undefined = leftWidth
    ? {
        gridTemplateColumns: `minmax(0, ${
          typeof leftWidth === 'number' ? `${leftWidth}px` : leftWidth
        }) minmax(0, 1fr)`,
      }
    : undefined;

  return (
    <div className={className ? `split ${className}` : 'split'} style={style}>
      {children}
    </div>
  );
}
