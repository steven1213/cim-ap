// 步骤条：配方签核、工单流转、下发进度的可视化。
//
// 与参考项目 GoatSteps 的差别：
//   它的 `current` 是**下标**，步骤增删一个就全体错位（而流程步骤恰恰是最常改的）。
//   这里用 `current` = 步骤 key，内部算下标；同时自动推导「已完成的步骤」，
//   调用方只关心「现在在哪一步」，不必自己给每一步标 status。

import { Steps } from 'antd';
import { useMemo } from 'react';
import { useUiLabel } from '../locale';

export interface StepItem {
  /** 步骤唯一键，通常就用状态枚举值（DRAFT / PENDING_SIGN / ACTIVE）。 */
  key: string;
  labelKey?: string;
  label?: string;
  descriptionKey?: string;
  description?: string;
  /** 该步失败（如 VERIFIED 之前出现 FAILED）。 */
  failed?: boolean;
}

export interface CimStepsProps {
  items: StepItem[];
  /** 当前步骤的 key。不在 items 里时取 -1（全部未开始）。 */
  current: string;
  direction?: 'horizontal' | 'vertical';
  size?: 'default' | 'small';
  /** 点击某步切换（只在允许跳步的流程里用）。 */
  onChange?: (key: string) => void;
  className?: string;
}

export function CimSteps({
  items,
  current,
  direction = 'horizontal',
  size = 'small',
  onChange,
  className,
}: CimStepsProps) {
  const label = useUiLabel();

  const antdItems = useMemo(() => {
    const idx = items.findIndex((i) => i.key === current);
    return items.map((item, i) => {
      const isCurrent = i === idx;
      const status = item.failed
        ? ('error' as const)
        : isCurrent
          ? ('process' as const)
          : idx >= 0 && i < idx
            ? ('finish' as const)
            : ('wait' as const);
      return {
        key: item.key,
        title: item.labelKey ? label(item.labelKey, item.labelKey) : item.label,
        description: item.descriptionKey
          ? label(item.descriptionKey, item.descriptionKey)
          : item.description,
        status,
        disabled: !onChange,
      };
    });
  }, [items, current, label, onChange]);

  const currentIndex = items.findIndex((i) => i.key === current);

  return (
    <Steps
      className={className}
      items={antdItems}
      current={currentIndex < 0 ? 0 : currentIndex}
      direction={direction}
      size={size}
      onChange={
        onChange
          ? (i) => {
              const item = items[i];
              if (item) onChange(item.key);
            }
          : undefined
      }
    />
  );
}
