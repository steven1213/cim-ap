/**
 * 缺键兜底文案：`rms.recipe.code` → `Code`。
 *
 * <p>这是四级兜底的最后一环，唯一目的是**绝不把裸 key 渲染到界面上**。
 * 文案不翻译、不查表，只做「取末段 + 驼峰/下划线拆词 + 首字母大写」——
 * 因为到了这一步，任何猜测都比 `rms.recipe.code` 好看。</p>
 */
export function humanize(key: string): string {
  if (!key) return '';
  const tail = key.includes('.') ? key.slice(key.lastIndexOf('.') + 1) : key;
  const spaced = tail
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .trim();
  if (!spaced) return key;
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}
