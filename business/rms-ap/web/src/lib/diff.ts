/** 差异项字段/类别标签（param:xxx 走原样，其余映射 i18n）。 */
export function fieldLabel(t: (k: string, o?: any) => string, field: string): string {
  if (field.startsWith('param:')) {
    return field.slice(6);
  }
  return t(`rms.compare.f.${field}`, { defaultValue: field });
}

/** 差异类别标签（BODY/PARAM/STRUCTURE）。 */
export function categoryLabel(t: (k: string) => string, c: string | null | undefined): string {
  return c ? t(`rms.compare.cat.${c}`) : '';
}
