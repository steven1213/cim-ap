import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '@/lib/api';
import { changeLanguage, currentLang } from '@/lib/i18n';
import type { SysI18nDto, SysLocaleDto } from '@/types';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import { useHasPermission } from '@/lib/usePermission';
import { I18N_LIST, I18N_CREATE, I18N_DELETE, I18N_LOCALE } from '@/lib/permCodes';
import {
  IconAlert,
  IconCheckCircle,
  IconInbox,
  IconPlus,
  IconRefresh,
  IconSave,
  IconTrash,
} from '@/components/Icons';

/**
 * 多语言（`/i18n`）：语言目录与译文维护。
 *
 * <p><b>数据面在 IAM 自己</b>（不像菜单/权限/角色那样复用平台端点）：管理端点
 * `GET/POST/DELETE /api/v1/admin/i18n/**` 由 `I18nAdminController` 提供，权限码是
 * 控制台自己的 `iam:i18n:*` —— 与平台 `sys:*` 无关，因此本页不需要双闸门。</p>
 *
 * <p><b>两级 scope</b>：`SYSTEM` 是程序内置种子（启动写入、不可删），`USER` 是业务/UI 文案。
 * 两者都按 `(localeCode, code)` upsert——在此覆盖 SYSTEM 行即可把内置文案改掉，
 * 且重启不会被种子冲回（种子口径是「已存在即跳过」）。</p>
 *
 * <p><b>保存后即时生效</b>：写库即 `cache.invalidate()`，前端下次拉取或刷新页面即拿到新译文。
 * 若正在编辑当前语言，可用「应用」按钮立即重拉。</p>
 */
export default function I18nAdminPage() {
  const { t } = useTranslation();
  const canRead = useHasPermission(I18N_LIST);
  const canRemove = useHasPermission(I18N_DELETE);

  const [locales, setLocales] = useState<SysLocaleDto[]>([]);
  const [messages, setMessages] = useState<SysI18nDto[]>([]);
  const [missing, setMissing] = useState<Record<string, string>>({});

  const [localeDraft, setLocaleDraft] = useState<LocaleDraft | null>(null);
  const [msgDraft, setMsgDraft] = useState<MsgDraft | null>(null);

  const [filterLocale, setFilterLocale] = useState('');
  const [filterModule, setFilterModule] = useState('');
  const [keyword, setKeyword] = useState('');

  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');

  async function loadAll() {
    if (!canRead) return;
    setLoading(true);
    setErr('');
    try {
      const [ls, ms, miss] = await Promise.all([
        api.get<SysLocaleDto[]>('/admin/i18n/locales'),
        api.get<SysI18nDto[]>('/admin/i18n/messages', {
          localeCode: filterLocale || undefined,
          module: filterModule || undefined,
          keyword: keyword || undefined,
        }),
        api.get<Record<string, string>>('/admin/i18n/missing'),
      ]);
      setLocales(ls ?? []);
      setMessages(ms ?? []);
      setMissing(miss ?? {});
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canRead, filterLocale, filterModule]);

  function flash(text: string) {
    setMsg(text);
    setTimeout(() => setMsg(''), 3000);
  }

  const modules = useMemo(() => {
    const set = new Set<string>();
    for (const m of messages) if (m.module) set.add(m.module);
    return Array.from(set).sort();
  }, [messages]);

  const missingKeys = useMemo(() => Object.keys(missing).sort(), [missing]);

  async function onSaveLocale(e: FormEvent) {
    e.preventDefault();
    if (!localeDraft) return;
    setErr('');
    setBusy(true);
    try {
      await api.post<SysLocaleDto>('/admin/i18n/locales', {
        id: localeDraft.id || undefined,
        code: localeDraft.code.trim(),
        name: localeDraft.name.trim() || null,
        isDefault: localeDraft.isDefault,
        sortNo: Number(localeDraft.sortNo) || 0,
        status: localeDraft.status,
      });
      flash(t('iam.common.saved'));
      await loadAll();
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDeleteLocale(id: string, code: string) {
    if (!window.confirm(`${t('iam.common.confirmDelete')}\n${code}`)) return;
    setErr('');
    setBusy(true);
    try {
      await api.del<void>(`/admin/i18n/locales/${encodeURIComponent(id)}`);
      flash(t('iam.common.deleted'));
      await loadAll();
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onSaveMessage(e: FormEvent) {
    e.preventDefault();
    if (!msgDraft) return;
    setErr('');
    if (!msgDraft.code.trim() || !msgDraft.localeCode.trim()) {
      setErr(t('iam.common.required'));
      return;
    }
    setBusy(true);
    try {
      await api.post<SysI18nDto>('/admin/i18n/messages', {
        id: msgDraft.id || undefined,
        localeCode: msgDraft.localeCode.trim(),
        code: msgDraft.code.trim(),
        content: msgDraft.content,
        module: msgDraft.module.trim() || undefined,
        scope: msgDraft.scope,
      });
      flash(t('iam.common.saved'));
      setMsgDraft(null);
      await loadAll();
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onDeleteMessage(m: SysI18nDto) {
    if (!window.confirm(`${t('iam.common.confirmDelete')}\n${m.localeCode} · ${m.code}`)) return;
    setErr('');
    setBusy(true);
    try {
      await api.del<void>(`/admin/i18n/messages/${encodeURIComponent(m.id)}`);
      flash(t('iam.common.deleted'));
      await loadAll();
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function onClearMissing() {
    setErr('');
    setBusy(true);
    try {
      await api.del<void>('/admin/i18n/missing');
      flash(t('iam.common.deleted'));
      await loadAll();
    } catch (e: any) {
      setErr(e?.msg || t('iam.common.loadFailed'));
    } finally {
      setBusy(false);
    }
  }

  if (!canRead) {
    return (
      <div className="page">
        <Panel>
          <div className="alert warn">
            <IconAlert width={15} height={15} />
            <span>{t('iam.common.forbidden.desc')}</span>
          </div>
        </Panel>
      </div>
    );
  }

  return (
    <div className="page">
      {err && (
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      )}
      {msg && (
        <div className="alert ok">
          <IconCheckCircle width={15} height={15} />
          <span>{msg}</span>
        </div>
      )}

      <Panel
        title={t('iam.admin.i18n.locales')}
        sub={`${locales.length}`}
        flush
        actions={
          <>
            <Perms code={I18N_LOCALE}>
              <button
                className="icon-btn"
                title={t('iam.admin.i18n.newLocale')}
                onClick={() => {
                  setErr('');
                  setLocaleDraft({
                    id: '',
                    code: '',
                    name: '',
                    isDefault: false,
                    sortNo: 20,
                    status: 'ENABLED',
                  });
                }}
              >
                <IconPlus width={14} height={14} />
              </button>
            </Perms>
            <button className="btn-ghost btn-sm" onClick={loadAll} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? t('iam.common.loading') : t('iam.common.refresh')}
            </button>
          </>
        }
      >
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>{t('iam.field.code')}</th>
                <th>{t('iam.field.name')}</th>
                <th style={{ width: 110 }}>{t('iam.i18n.default')}</th>
                <th className="num" style={{ width: 80 }}>
                  {t('iam.field.sortNo')}
                </th>
                <th style={{ width: 100 }}>{t('iam.common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {locales.map((l) => (
                <tr key={l.id}>
                  <td className="mono">{l.code}</td>
                  <td>{l.name ?? '—'}</td>
                  <td>
                    {l.isDefault ? (
                      <span className="tag pri">{t('iam.i18n.default')}</span>
                    ) : (
                      <span className="dim">—</span>
                    )}
                  </td>
                  <td className="num">{l.sortNo}</td>
                  <td>
                    <div className="cell-actions">
                      <Perms code={I18N_LOCALE}>
                        <button
                          className="btn-ghost btn-sm"
                          onClick={() =>
                            setLocaleDraft({
                              id: l.id,
                              code: l.code,
                              name: l.name ?? '',
                              isDefault: l.isDefault,
                              sortNo: l.sortNo,
                              status: l.status,
                            })
                          }
                        >
                          {t('iam.common.edit')}
                        </button>
                      </Perms>
                      {!l.isDefault && (
                        <Perms code={I18N_LOCALE}>
                          <button
                            className="icon-btn danger"
                            title={t('iam.common.delete')}
                            onClick={() => onDeleteLocale(l.id, l.code)}
                          >
                            <IconTrash width={13} height={13} />
                          </button>
                        </Perms>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {localeDraft && (
          <div style={{ padding: 12 }}>
            <form onSubmit={onSaveLocale}>
              <div className="form-grid">
                <div className="field">
                  <label className="field-label" htmlFor="l-code">
                    {t('iam.field.code')}
                    <span className="opt">{t('iam.common.required')}</span>
                  </label>
                  <input
                    id="l-code"
                    className="mono"
                    placeholder="zh-TW"
                    value={localeDraft.code}
                    onChange={(e) => setLocaleDraft({ ...localeDraft, code: e.target.value })}
                  />
                </div>
                <div className="field">
                  <label className="field-label" htmlFor="l-name">
                    {t('iam.field.name')}
                  </label>
                  <input
                    id="l-name"
                    placeholder="繁體中文"
                    value={localeDraft.name}
                    onChange={(e) => setLocaleDraft({ ...localeDraft, name: e.target.value })}
                  />
                </div>
                <div className="field">
                  <label className="field-label" htmlFor="l-sort">
                    {t('iam.field.sortNo')}
                  </label>
                  <input
                    id="l-sort"
                    type="number"
                    value={localeDraft.sortNo}
                    onChange={(e) => setLocaleDraft({ ...localeDraft, sortNo: Number(e.target.value) })}
                  />
                </div>
                <div className="field">
                  <label className="field-label" htmlFor="l-default">
                    {t('iam.i18n.default')}
                  </label>
                  <select
                    id="l-default"
                    value={localeDraft.isDefault ? '1' : '0'}
                    onChange={(e) => setLocaleDraft({ ...localeDraft, isDefault: e.target.value === '1' })}
                  >
                    <option value="0">{t('iam.common.none')}</option>
                    <option value="1">{t('iam.i18n.default')}</option>
                  </select>
                </div>
              </div>
              <p className="hint">{t('iam.admin.i18n.localeCodeHint')}</p>
              <p className="hint">{t('iam.admin.i18n.defaultLocked')}</p>
              <div className="sub-head" style={{ marginTop: 10 }}>
                <button type="submit" className="btn" disabled={busy}>
                  {busy ? t('iam.common.saving') : t('iam.common.save')}
                </button>
                <button type="button" className="btn-ghost btn-sm" onClick={() => setLocaleDraft(null)}>
                  {t('iam.common.close')}
                </button>
              </div>
            </form>
          </div>
        )}
      </Panel>

      <Panel
        title={t('iam.admin.i18n.messages')}
        sub={`${messages.length}`}
        flush
        actions={
          <>
            <Perms code={I18N_CREATE}>
              <button
                className="icon-btn"
                title={t('iam.admin.i18n.newMessage')}
                onClick={() => {
                  setErr('');
                  setMsgDraft({
                    id: '',
                    localeCode: filterLocale || currentLang(),
                    code: '',
                    content: '',
                    module: filterModule,
                    scope: 'USER',
                  });
                }}
              >
                <IconPlus width={14} height={14} />
              </button>
            </Perms>
            {currentLang() && (
              <button
                className="btn-ghost btn-sm"
                title={t('iam.admin.i18n.reloadHint')}
                onClick={() => changeLanguage(currentLang(), true)}
              >
                <IconSave width={13} height={13} />
                {t('iam.common.refresh')}
              </button>
            )}
          </>
        }
      >
        <div style={{ padding: '10px 12px 0' }}>
          <div className="grid-3">
            <div className="field">
              <label className="field-label" htmlFor="f-locale">
                {t('iam.admin.i18n.filterLocale')}
              </label>
              <select
                id="f-locale"
                value={filterLocale}
                onChange={(e) => setFilterLocale(e.target.value)}
              >
                <option value="">{t('iam.common.all')}</option>
                {locales.map((l) => (
                  <option key={l.id} value={l.code}>
                    {l.code}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field-label" htmlFor="f-module">
                {t('iam.admin.i18n.filterModule')}
              </label>
              <select
                id="f-module"
                value={filterModule}
                onChange={(e) => setFilterModule(e.target.value)}
              >
                <option value="">{t('iam.common.all')}</option>
                {modules.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field-label" htmlFor="f-kw">
                {t('iam.admin.i18n.keyword')}
              </label>
              <input
                id="f-kw"
                className="mono"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') loadAll();
                }}
              />
            </div>
          </div>
        </div>

        {messages.length ? (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th style={{ width: 90 }}>{t('iam.field.locale')}</th>
                  <th>{t('iam.field.code')}</th>
                  <th>{t('iam.field.content')}</th>
                  <th style={{ width: 90 }}>{t('iam.field.module')}</th>
                  <th style={{ width: 90 }}>{t('iam.admin.i18n.scope', { defaultValue: 'Scope' })}</th>
                  <th style={{ width: 120 }}>{t('iam.common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {messages.map((m) => (
                  <tr key={m.id}>
                    <td className="mono">{m.localeCode}</td>
                    <td className="mono">{m.code}</td>
                    <td>{m.content ?? <span className="tag err">{t('iam.common.none')}</span>}</td>
                    <td className="mono dim">{m.module ?? '—'}</td>
                    <td>
                      <span className={'tag' + (m.scope === 'SYSTEM' ? ' info' : '')}>{m.scope}</span>
                    </td>
                    <td>
                      <div className="cell-actions">
                        <Perms code={I18N_CREATE}>
                          <button
                            className="btn-ghost btn-sm"
                            onClick={() =>
                              setMsgDraft({
                                id: m.id,
                                localeCode: m.localeCode,
                                code: m.code,
                                content: m.content ?? '',
                                module: m.module ?? '',
                                scope: m.scope,
                              })
                            }
                          >
                            {t('iam.common.edit')}
                          </button>
                        </Perms>
                        <Perms code={I18N_DELETE}>
                          <button
                            className="icon-btn danger"
                            title={t('iam.common.delete')}
                            onClick={() => onDeleteMessage(m)}
                          >
                            <IconTrash width={13} height={13} />
                          </button>
                        </Perms>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty">
            <IconInbox width={22} height={22} />
            <b>{t('iam.common.none')}</b>
          </div>
        )}

        {msgDraft && (
          <div style={{ padding: 12 }}>
            <form onSubmit={onSaveMessage}>
              <div className="form-grid">
                <div className="field">
                  <label className="field-label" htmlFor="t-locale">
                    {t('iam.field.locale')}
                    <span className="opt">{t('iam.common.required')}</span>
                  </label>
                  <select
                    id="t-locale"
                    value={msgDraft.localeCode}
                    onChange={(e) => setMsgDraft({ ...msgDraft, localeCode: e.target.value })}
                  >
                    {locales.map((l) => (
                      <option key={l.id} value={l.code}>
                        {l.code}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label className="field-label" htmlFor="t-code">
                    {t('iam.field.code')}
                    <span className="opt">{t('iam.common.required')}</span>
                  </label>
                  <input
                    id="t-code"
                    className="mono"
                    placeholder="iam.menu.menus"
                    value={msgDraft.code}
                    onChange={(e) => setMsgDraft({ ...msgDraft, code: e.target.value })}
                  />
                </div>
                <div className="field">
                  <label className="field-label" htmlFor="t-module">
                    {t('iam.field.module')}
                  </label>
                  <input
                    id="t-module"
                    className="mono"
                    value={msgDraft.module}
                    onChange={(e) => setMsgDraft({ ...msgDraft, module: e.target.value })}
                  />
                </div>
                <div className="field">
                  <label className="field-label" htmlFor="t-scope">
                    {t('iam.admin.i18n.scope', { defaultValue: 'Scope' })}
                  </label>
                  <select
                    id="t-scope"
                    value={msgDraft.scope}
                    onChange={(e) =>
                      setMsgDraft({ ...msgDraft, scope: e.target.value as 'SYSTEM' | 'USER' })
                    }
                  >
                    <option value="USER">USER</option>
                    <option value="SYSTEM">SYSTEM</option>
                  </select>
                </div>
              </div>
              <div className="field" style={{ marginTop: 11 }}>
                <label className="field-label" htmlFor="t-content">
                  {t('iam.field.content')}
                </label>
                <textarea
                  id="t-content"
                  rows={2}
                  value={msgDraft.content}
                  onChange={(e) => setMsgDraft({ ...msgDraft, content: e.target.value })}
                />
              </div>
              <p className="hint">{t('iam.admin.i18n.systemScope')}</p>
              <p className="hint">{t('iam.admin.i18n.reloadHint')}</p>
              <div className="sub-head" style={{ marginTop: 10 }}>
                <Perms code={I18N_CREATE}>
                  <button type="submit" className="btn" disabled={busy}>
                    {busy ? t('iam.common.saving') : t('iam.common.save')}
                  </button>
                </Perms>
                <button type="button" className="btn-ghost btn-sm" onClick={() => setMsgDraft(null)}>
                  {t('iam.common.close')}
                </button>
              </div>
            </form>
          </div>
        )}
      </Panel>

      <Panel
        title={t('iam.i18n.missing')}
        sub={t('iam.admin.i18n.missingHint')}
        actions={
          <>
            <span className={'tag' + (missingKeys.length ? ' warn' : '')}>
              {t('iam.admin.i18n.missingCount', { n: missingKeys.length })}
            </span>
            {canRemove && missingKeys.length > 0 && (
              <button className="btn-ghost btn-sm" onClick={onClearMissing} disabled={busy}>
                <IconTrash width={13} height={13} />
                {t('iam.admin.i18n.clearMissing')}
              </button>
            )}
          </>
        }
      >
        {missingKeys.length ? (
          <div className="tag-list">
            {missingKeys.map((k) => (
              <span className="tag warn mono" key={k} title={missing[k]}>
                {k}
                <button
                  className="chip-x"
                  title={t('iam.common.add')}
                  onClick={() => {
                    setMsgDraft({
                      id: '',
                      localeCode: filterLocale || currentLang(),
                      code: k,
                      content: '',
                      module: '',
                      scope: 'USER',
                    });
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                  }}
                >
                  +
                </button>
              </span>
            ))}
          </div>
        ) : (
          <div className="empty">
            <IconCheckCircle width={22} height={22} />
            <b>{t('iam.common.none')}</b>
          </div>
        )}
      </Panel>
    </div>
  );
}

interface LocaleDraft {
  id: string;
  code: string;
  name: string;
  isDefault: boolean;
  sortNo: number;
  status: 'ENABLED' | 'DISABLED';
}

interface MsgDraft {
  id: string;
  localeCode: string;
  code: string;
  content: string;
  module: string;
  scope: 'SYSTEM' | 'USER';
}
