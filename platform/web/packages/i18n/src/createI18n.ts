import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';
import { createBundleCache, createLangPreference } from './cache';
import { humanize, markMissingKey } from './humanize';
import { createMissingReporter } from './missing';
import type { CimI18n, CreateI18nOptions, LocaleInfo } from './types';

/**
 * 创建一套文案运行时（每个应用调用一次，返回的 `instance` 就是全局 i18next 单例）。
 *
 * <p>四级兜底链（顺序即优先级）：</p>
 * <ol>
 *   <li><b>目标语言</b>——远端包叠在内置包之上（逐键覆盖）；</li>
 *   <li><b>默认语言</b>——`fallbackLng`。远端场景下服务端已把中文并入目标包，这一级主要
 *       兜「内置包只有中文」的应用；</li>
 *   <li><b>内置包</b>——随前端发版的 key 表，保证没有网络时界面完整；</li>
 *   <li><b>humanize(key)</b>——四级全空时的兜底，**绝不渲染裸 key**。生产用
 *       {@link humanize}（`Toggle Theme`，对终端用户可读），开发态用
 *       {@link markMissingKey}（`⟦rms.shell.toggleTheme⟧`，一眼可识别）——
 *       humanize 的产物「看起来像译文」，`Desc` / `Callback failed` 这类能瞒过所有人。</li>
 * </ol>
 *
 * <p>缺键处理器**无条件**注册（不依赖上报通道）：发现缺口和上报工单是两件事，
 * 纯内置包的应用没有端点，但仍然要在开发态喊出来。见 {@link createMissingReporter}。</p>
 *
 * <p>启动策略：`localStorage` 里的整包**同步**灌入（首帧就是上次的语言，不闪白）→
 * 再异步比对版本。⚠️ 缓存必须存整包，见 {@link createBundleCache}。</p>
 */
export function createI18n(options: CreateI18nOptions): CimI18n {
  const {
    app,
    builtin = {},
    defaultLng = 'zh-CN',
    remote,
    onLanguageChange,
    missingReport,
    missingDebounceMs,
    missingRetryLimit,
    dev = false,
  } = options;

  const cache = createBundleCache(app);
  const langPref = createLangPreference(app, defaultLng);
  const canReport = Boolean(remote?.reportMissing) && missingReport !== false;
  const loaded = new Set<string>();
  let initialized = false;

  const reportMissing = createMissingReporter({
    // 没有上报端点（纯内置包应用）时只做 dev 提示，不攒批、不重试
    send: canReport ? (keys, lang) => remote!.reportMissing!(keys, lang) : undefined,
    currentLang: () => i18next.language || defaultLng,
    debounceMs: missingDebounceMs,
    retryLimit: missingRetryLimit,
    dev,
  });

  function ensureInit(): void {
    if (initialized) return;
    // 一个应用只应创建一次。重复创建时保留先到者的资源，避免 i18next 打「重复 init」警告。
    if (i18next.isInitialized) {
      initialized = true;
      return;
    }
    const resources: Record<string, { translation: Record<string, string> }> = {};
    for (const [lang, messages] of Object.entries(builtin)) {
      resources[lang] = { translation: messages };
      loaded.add(lang);
    }
    i18next.use(initReactI18next).init({
      // 以「上次选择的语言」起步，而不是默认语言：否则刷新瞬间会先渲染中文再跳变
      lng: langPref.read(),
      fallbackLng: defaultLng,
      defaultNS: 'translation',
      ns: ['translation'],
      resources,
      // 资源已在本地、无异步加载：同步初始化，保证首帧 `isInitialized` 已为 true
      initImmediate: false,
      react: {
        // 不用 Suspense：文案是「先渲染缓存、后台刷新」，挂起会导致白屏/闪烁
        useSuspense: false,
        bindI18n: 'languageChanged loaded',
        // 远端包晚到时要触发重渲染，否则界面停在缓存版本
        bindI18nStore: 'added removed',
      },
      // 缺键处理器已无条件提供，故 saveMissing 直接开：否则 i18next 根本不回调它。
      // 有 handler 时 i18next 不会退到 backendConnector（不会去找不存在的后端）。
      saveMissing: true,
      parseMissingKeyHandler: dev ? markMissingKey : humanize,
      missingKeyHandler: (_lngs: readonly string[], _ns: string, key: string) => {
        reportMissing(key);
      },
      interpolation: { escapeValue: false },
      returnNull: false,
    });
    initialized = true;
  }

  /** 写入某语言的译文；空包视为「无事发生」并返回 false（版本未变的正常情况）。 */
  function applyBundle(lang: string, messages: Record<string, string> | undefined): boolean {
    if (!messages) return false;
    if (Object.keys(messages).length === 0) return false;
    // deep + overwrite：与内置包逐键合并，而非整包替换
    i18next.addResourceBundle(lang, 'translation', messages, true, true);
    loaded.add(lang);
    // ⚠️ 缓存要写**合并后的整包**，不能只写刚落地的这段。
    // 只写增量会把内置包与上一版缓存里的键从磁盘上抹掉——下次启动就从缓存里取不到，
    // 表现为「升级后一批文案突然变回 humanize」。
    const merged = i18next.getResourceBundle(lang, 'translation') as
      | Record<string, string>
      | undefined;
    cache.write(lang, merged ?? messages);
    return true;
  }

  /** 拉远端包并落地；网络异常向上抛，由调用方决定退回策略。 */
  async function pullRemote(lang: string, force: boolean): Promise<void> {
    if (!remote) return;
    // ⚠️ 只有「该语言的整包已在本地」时才带版本号。
    // 服务端的 version 是**全局**的（整表快照算一个号），若切换到从没加载过的语言也带上，
    // 服务端会判定「版本一致」直接回空包 → 该语言一个键都没有 → 界面全退化成 humanize。
    const cached = force ? null : cache.read(lang);
    const payload = await remote.loadBundle({
      lang,
      version: cached ? cache.readVersion() : undefined,
    });
    if (!payload) return;
    if (payload.version) cache.writeVersion(payload.version);
    // 版本未变：服务端只回版本号、消息体为空 —— 此时保留刚灌入的缓存，勿清空
    const applied = applyBundle(payload.lang || lang, payload.messages);
    if (!applied && cached) {
      // ⚠️ 「该语言的整包只在 localStorage 里、内存里没有」这一格必须补。
      // 服务端按 `v` 判版本一致时回空包，若直接 `i18next.changeLanguage(lang)`，
      // 切过去的语言在 i18next 里一条资源都没有 → 逐键回落 `fallbackLng`（默认语言），
      // 表现为「下拉已显示 English，正文还是中文」，且不报错、不触发缺键上报——
      // 键是命中的，只是命中的是兜底语言的值。本应用首次切某语言能生效、
      // 刷新后再切就失效，正是这个原因（缓存还在，版本没变）。
      applyBundle(lang, cached);
    }
  }

  async function changeLanguage(lang: string, opts: { force?: boolean } = {}): Promise<void> {
    ensureInit();
    onLanguageChange?.(lang);
    try {
      await pullRemote(lang, opts.force === true);
    } catch {
      // 译文拉取失败：退回本地缓存（至少不显示裸 key）；切换本身不因此失败
      const cached = cache.read(lang);
      if (cached) applyBundle(lang, cached);
    }
    if (i18next.language !== lang) {
      await i18next.changeLanguage(lang);
    } else {
      // 同一语言但资源刚更新：手动触发重渲染
      i18next.emit('languageChanged', lang);
    }
    // 开发态兜底信号：切过去的语言在内存里一条资源都没有 → 界面整体回落默认语言，
    // 用户看到的是「切了没反应」。这个形态既不报错也不触发缺键上报（键是命中的，
    // 命中的是兜底语言的值），只能在这里喊一声。
    if (dev && lang !== defaultLng && !i18next.hasResourceBundle(lang, 'translation')) {
      console.warn(
        `[i18n:${app}] 切到 ${lang} 后没有任何译文资源，界面将整体回落 ${defaultLng}（看起来像「切换不生效」）`,
      );
    }
    langPref.write(lang);
  }

  async function bootstrap(): Promise<string> {
    ensureInit();
    const lang = langPref.read();
    onLanguageChange?.(lang);
    // 同步灌入上次的整包：第一帧就是正确语言，不闪白
    const cached = cache.read(lang);
    if (cached) applyBundle(lang, cached);
    if (i18next.language !== lang) await i18next.changeLanguage(lang);
    // 后台比对版本（不阻塞首屏）
    void changeLanguage(lang).catch(() => {});
    return lang;
  }

  async function loadLocales(): Promise<LocaleInfo[]> {
    if (remote?.loadLocales) {
      try {
        const list = await remote.loadLocales();
        const enabled = (list ?? []).filter((l) => l.status !== 'DISABLED');
        if (enabled.length > 0) return enabled;
      } catch {
        // 目录拉取失败：回退内置目录，语言开关不能因为后端挂了而消失
      }
    }
    return options.locales ?? Object.keys(builtin).map((code) => ({ code, name: code }));
  }

  return {
    instance: i18next,
    bootstrap,
    changeLanguage,
    currentLang: () => langPref.read(),
    loadLocales,
    hasKey: (key: string) => i18next.exists(key),
    loadedLanguages: () => Array.from(loaded),
  };
}
