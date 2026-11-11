// 下拉选择。三种数据来源共用一套渲染：
//   1. `options`  —— 调用方给静态选项；
//   2. `dict`     —— 传字典码，自动从 UiDictProvider 加载（recipeState / downloadStage …）；
//   3. 两者都不传 —— 空列表，仍可搜索（远程搜索模式，配 `onSearch`）。
//
// 对照参考项目 GoatSelect（206 行）：它把「值左对齐 + 文案右对齐」「翻转」
// 「回车创建标签」这些细节都做进组件，并且 `items` 里 `value` 是显示文本、
// `text` 是落库值 —— 命名与直觉相反，接线时极易错。这里保持 antd 的
// `{ value, label }` 语义：`value` 落库，`label` 显示。

import { Select } from 'antd';
import { useMemo } from 'react';
import { useUiLabel } from '../locale';
import { useDict } from '../dict';

export interface CimOption {
  /** 落库值。 */
  value: string;
  /** 显示文案（已翻译）。 */
  label?: string;
  /** 显示文案的 i18n 键（优先级高于 label）。 */
  labelKey?: string;
  disabled?: boolean;
  /** 右侧补充说明（如编码后面跟名称）。 */
  hint?: string;
}

export interface CimSelectProps {
  value?: string | string[] | null;
  onChange?: (value: string | string[] | null) => void;
  options?: CimOption[];
  /** 字典码：与 options 二选一（同时给则合并，dict 在前）。 */
  dict?: string;
  /** 多选。 */
  multiple?: boolean;
  allowClear?: boolean;
  /** 默认开启搜索（选项超过 8 个时几乎总是需要）。 */
  showSearch?: boolean;
  /** 远程搜索回调（配合 options 由外部更新）。 */
  onSearch?: (keyword: string) => void;
  placeholderKey?: string;
  placeholder?: string;
  disabled?: boolean;
  loading?: boolean;
  status?: 'error' | 'warning';
  /** 多选时折叠标签数量。 */
  maxTagCount?: number | 'responsive';
  width?: number | string;
  id?: string;
  onBlur?: () => void;
  /** 无匹配时的自定义内容（默认什么都不显示）。 */
  notFoundKey?: string;
}

export function CimSelect({
  value,
  onChange,
  options,
  dict,
  multiple = false,
  allowClear = true,
  showSearch = true,
  onSearch,
  placeholderKey,
  placeholder,
  disabled = false,
  loading = false,
  status,
  maxTagCount = 'responsive',
  width,
  id,
  onBlur,
  notFoundKey,
}: CimSelectProps) {
  const label = useUiLabel();
  const d = useDict(dict);

  const merged = useMemo<CimOption[]>(() => {
    const fromDict: CimOption[] = dict
      ? d.items.map((i) => ({ value: i.value, label: i.label, disabled: i.disabled }))
      : [];
    return [...fromDict, ...(options ?? [])];
  }, [dict, d.items, options]);

  const antdOptions = useMemo(
    () =>
      merged.map((o) => ({
        value: o.value,
        disabled: o.disabled,
        label: (
          <span className="cim-select-opt">
            <span>{o.labelKey ? label(o.labelKey, o.labelKey) : (o.label ?? o.value)}</span>
            {o.hint ? <span className="cim-select-opt-hint">{o.hint}</span> : null}
          </span>
        ),
        // 搜索时也要能按原始值与 hint 命中
        title: `${o.labelKey ? label(o.labelKey, o.labelKey) : (o.label ?? o.value)} ${o.hint ?? ''}`.trim(),
      })),
    [merged, label],
  );

  const ph = placeholderKey ? label(placeholderKey, placeholder ?? '') : placeholder;

  return (
    <Select
      id={id}
      mode={multiple ? 'multiple' : undefined}
      value={value ?? (multiple ? [] : undefined)}
      onChange={(v) => onChange?.(v ?? null)}
      options={antdOptions}
      allowClear={allowClear}
      showSearch={showSearch}
      onSearch={onSearch}
      // 本地选项过滤：label 是 ReactNode，必须自己给 filterOption
      filterOption={
        onSearch
          ? false
          : (input, option) =>
              String(option?.title ?? '')
                .toLowerCase()
                .includes(input.toLowerCase())
      }
      placeholder={ph}
      disabled={disabled}
      loading={loading || d.loading}
      status={status}
      maxTagCount={multiple ? maxTagCount : undefined}
      style={{ width: width ?? '100%' }}
      onBlur={onBlur}
      notFoundContent={notFoundKey ? label(notFoundKey, '—') : undefined}
      optionFilterProp="title"
    />
  );
}
