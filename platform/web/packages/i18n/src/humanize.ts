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

/**
 * 开发态缺键占位：把 key 原样夹进方括号，`⟦rms.shell.toggleTheme⟧`。
 *
 * <p>为什么两个环境用不同策略：{@link humanize} 的产物**看起来像译文**——
 * `rms.notFound.desc` 会显示成一个孤零零的 `Desc`，`cb.event.callback_failed` 变成
 * `Callback failed`，谁看都像「设计如此」。生产环境要这个「可读」，开发环境要「可识别」，
 * 两者不可兼得，所以按环境分开，而不是选一个折中。</p>
 *
 * <p>顺带说明：dev 下 `⟦…⟧` 也提醒了「这个 key 拼得对不对」——humanize 会吃掉拼写错误。</p>
 */
export function markMissingKey(key: string): string {
  return key ? `⟦${key}⟧` : '';
}
