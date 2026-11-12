#!/usr/bin/env node
// 文案键静态对表：源码里用到的键是否都在目录里，以及「另一个语言」是否真的翻译了。
//
// 为什么需要它：缺键不报错。
//   - 键完全不存在 → 生产 humanize 出一句「看起来像译文」的假话（`rms.notFound.desc` → `Desc`），
//     开发态才显示 `⟦key⟧`。**缺键只在开发时被看见**，而「有没有人打开过那个页面」没法保证。
//   - 英文槽空着 → 服务端 `frontendBundle` 拿中文兜底填满，键是「命中」的，i18next 与前端
//     都不会有任何信号，表现是**英文界面显示中文**。这条前端治不了，只能在源码层比。
//
// 扫两类引用（少扫一类就会漏）：
//   1. `t('some.key')`            —— 直接取文案
//   2. `xxxKey="some.key"`        —— 注入式（`titleKey` / `hintKey` / `okKey` …），
//      平台组件（EmptyState / Panel / CimDialog…）按这个 key 自己去翻译。
//      ⚠️ 只扫 t() 会漏掉整整一类：`<NotFoundState hintKey="rms.notFound.desc">` 就是这么漏的。
//
// 除「有没有这个键」之外，还查四件事（都是不会报错的静默故障）：
//   3. 中英键集不对等        → 另一边回落成对方语言
//   4. 英文槽为空 / 仍是中文  → 英文界面显示中文（服务端兜底掩盖）
//   5. 占位符不一致          → `{{n}}` 只在中文里有，英文界面数字直接消失
//   6. 目录解析不出（见下）  → 宁可报错也不静默跳过
//
// 排除两类「名字像文案键但不是」的属性：`settingsKey`（DataTable 的列设置持久化键）、
// `storageKey`（localStorage 键）。它们的值形如 `iam.audit`，扫进来会变成假缺口。
//
// 用法：node scripts/check-i18n.mjs [--verbose]
// 退出码：有真缺口（缺定义 / 不对等 / 空英文 / 占位符不一致 / 解析失败）→ 1

import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();
const VERBOSE = process.argv.includes('--verbose');

/** 名字以 Key 结尾但不是文案键的属性 */
const NON_I18N_PROPS = new Set(['settingsKey', 'storageKey']);

/** 中日韩统一表意文字：出现在「英文」槽里就是没翻 */
const CJK = /[\u4e00-\u9fa5]/;

/** 各应用：源码根目录 + 文案目录来源 */
const TARGETS = [
  {
    app: 'rms',
    root: 'business/rms-ap/web/src',
    catalog: {
      kind: 'ts',
      file: 'business/rms-ap/web/src/lib/i18n/messages.ts',
    },
  },
  {
    app: 'approval',
    root: 'business/approval-ap/web/src',
    catalog: {
      kind: 'ts',
      file: 'business/approval-ap/web/src/lib/i18n/messages.ts',
    },
  },
  {
    app: 'iam',
    root: 'business/iam-ap/web/src',
    // IAM 的文案在后端目录（DB 驱动），前端没有 messages.ts
    catalog: {
      kind: 'java',
      file: 'business/iam-ap/server/src/main/java/com/cim/iam/server/console/IamConsoleCatalog.java',
    },
  },
  {
    app: 'mds',
    root: 'business/mds-ap/web/src',
    catalog: {
      kind: 'ts',
      file: 'business/mds-ap/web/src/lib/i18n/messages.ts',
    },
  },
];

function walk(dir, acc = []) {
  if (!existsSync(dir)) return acc;
  for (const name of readdirSync(dir)) {
    if (name === 'node_modules' || name === 'dist' || name === 'target') continue;
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, acc);
    else acc.push(p);
  }
  return acc;
}

// ------------------------------------------------------------------ 目录解析

/**
 * TS 文案包 → `{ zh: Map, en: Map|null, unparsed }`
 *
 * `messages.ts` 里中文段在前、`export const enUS` 之后是英文段。值只认单引号字面量；
 * 若某个键的值不是单引号（比如改成模板串），计入 `unparsed` 而不是当作没有。
 */
function readTsCatalog(file) {
  const src = readFileSync(join(ROOT, file), 'utf8');
  const splitAt = src.indexOf('export const enUS');
  const zhPart = splitAt >= 0 ? src.slice(0, splitAt) : src;
  const enPart = splitAt >= 0 ? src.slice(splitAt) : '';

  const parse = (text) => {
    const map = new Map();
    let entries = 0;
    for (const m of text.matchAll(/^\s*'([\w.\-]+)'\s*:\s*'((?:[^'\\]|\\.)*)'/gm)) {
      map.set(m[1], m[2]);
      entries++;
    }
    // 键总数（含值不是单引号字面量的）——两者不等说明有值解析不出来
    const keys = [...text.matchAll(/^\s*'([\w.\-]+)'\s*:/gm)].length;
    return { map, unparsed: Math.max(0, keys - entries) };
  };

  const zh = parse(zhPart);
  const en = splitAt >= 0 ? parse(enPart) : null;
  return {
    zh: zh.map,
    en: en ? en.map : null,
    unparsed: zh.unparsed + (en ? en.unparsed : 0),
    note: en
      ? `${zh.map.size} / en ${en.map.size}`
      : `${zh.map.size}`,
  };
}

/**
 * Java 目录 → `{ zh: Map, en: Map, unparsed }`
 *
 * 逐字符扫描 `new TextDef(code, zh, en)`，尊重字符串字面量与括号深度。
 *
 * ⚠️ **不能按行解析**：长文案会被拆成多行、还用 `+` 拼接（`iam.login.hero.desc` 就是），
 * 按行匹配会静默跳过。这个坑在本仓真实发生过——按行扫「只少一条」，而少的那条恰好是
 * 最长的文案，最可能没翻。所以这里解析不出的条目一律计入 `unparsed` 并报错。
 */
function readJavaCatalog(file) {
  const src = readFileSync(join(ROOT, file), 'utf8');

  const zh = new Map();
  const en = new Map();
  let unparsed = 0;
  let cursor = 0;

  while (true) {
    const start = src.indexOf('new TextDef(', cursor);
    if (start < 0) break;

    let i = start + 'new TextDef('.length;
    let depth = 1;
    let inStr = false;
    const args = [''];
    while (i < src.length && depth > 0) {
      const ch = src[i];
      if (inStr) {
        args[args.length - 1] += ch;
        if (ch === '\\') {
          args[args.length - 1] += src[i + 1] ?? '';
          i += 2;
          continue;
        }
        if (ch === '"') inStr = false;
        i++;
        continue;
      }
      if (ch === '"') {
        inStr = true;
        args[args.length - 1] += ch;
        i++;
        continue;
      }
      if (ch === '(') {
        depth++;
        args[args.length - 1] += ch;
        i++;
        continue;
      }
      if (ch === ')') {
        depth--;
        if (depth === 0) {
          i++;
          break;
        }
        args[args.length - 1] += ch;
        i++;
        continue;
      }
      if (ch === ',' && depth === 1) {
        args.push('');
        i++;
        continue;
      }
      args[args.length - 1] += ch;
      i++;
    }
    cursor = i;

    if (args.length !== 3) {
      unparsed++;
      continue;
    }
    const literal = (raw) => {
      if (raw.trim() === 'null') return null;
      const parts = [...raw.matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((m) => m[1]);
      // 把字符串字面量整体剥掉后，剩下的只能是空白与 `+`，才是「一串字面量拼接」；
      // 否则可能是引用常量（静态核不了）。
      // ⚠️ 不能反过来「从原文里删掉 + 再比」——文案本身就可能含 `+`（「点右上角「+」新建…」）。
      const stripped = raw.replace(/"(?:[^"\\]|\\.)*"/g, '');
      if (parts.length === 0 || stripped.replace(/[\s+]/g, '') !== '') return undefined;
      return parts.join('');
    };

    const code = literal(args[0]);
    const zhText = literal(args[1]);
    const enText = literal(args[2]);
    if (!code || zhText === undefined || enText === undefined) {
      unparsed++;
      continue;
    }
    zh.set(code, zhText);
    // null 与空串同义：英文槽没内容
    en.set(code, enText === null ? '' : enText);
  }

  return { zh, en, unparsed, note: `${zh.size}` };
}

/** 收集源码里的键引用：`{ key, file, line, via }` */
function collectUsage(root) {
  const out = [];
  const files = walk(join(ROOT, root)).filter((f) => /\.(tsx?|jsx?)$/.test(f));
  for (const abs of files) {
    // 文案包本身只是定义，不算引用
    if (/\/lib\/i18n\/messages\.ts$/.test(abs.split(join(ROOT)).join('/'))) continue;
    const src = readFileSync(abs, 'utf8');
    const rel = relative(ROOT, abs).split('\\').join('/');

    const push = (key, index, via) => {
      const line = src.slice(0, index).split('\n').length;
      out.push({ key, file: rel, line, via });
    };

    // 1) t('key') / t("key") / exists('key')
    for (const m of src.matchAll(/\b(?:t|exists)\(\s*'([\w.\-]+)'/g)) push(m[1], m.index, 't()');
    for (const m of src.matchAll(/\b(?:t|exists)\(\s*"([\w.\-]+)"/g)) push(m[1], m.index, 't()');

    // 2) xxxKey="key"（注入式文案）
    for (const m of src.matchAll(/\b([A-Za-z]+Key)\s*=\s*"([\w.\-]+)"/g)) {
      if (!NON_I18N_PROPS.has(m[1])) push(m[2], m.index, `${m[1]}=`);
    }
    for (const m of src.matchAll(/\b([A-Za-z]+Key)\s*=\s*'([\w.\-]+)'/g)) {
      if (!NON_I18N_PROPS.has(m[1])) push(m[2], m.index, `${m[1]}=`);
    }

    // 3) `titleKey: 'key'`（对象属性形式）——列定义 / 表单字段把文案键写成属性，
    //    JSX 属性那种扫法看不见。全仓这类写法比 JSX 属性还多，漏掉它等于漏掉大半。
    for (const m of src.matchAll(/\b([A-Za-z]+Key)\s*:\s*'([\w.\-]+)'/g)) {
      if (!NON_I18N_PROPS.has(m[1])) push(m[2], m.index, `${m[1]}:`);
    }
    for (const m of src.matchAll(/\b([A-Za-z]+Key)\s*:\s*"([\w.\-]+)"/g)) {
      if (!NON_I18N_PROPS.has(m[1])) push(m[2], m.index, `${m[1]}:`);
    }

    // 4) `i18n: 'key'` / `i18nCode: 'key'` —— 导航配置等载体属性，属性名不带 Key 后缀。
    //    漏掉它会把 navConfig 的活键判成「无人引用」，顺着死键清理就会删掉菜单文案。
    for (const m of src.matchAll(/\b(?:i18n|i18nCode)\s*[:=]\s*'([\w.\-]+)'/g)) {
      push(m[1], m.index, 'i18n=');
    }
    for (const m of src.matchAll(/\b(?:i18n|i18nCode)\s*[:=]\s*"([\w.\-]+)"/g)) {
      push(m[1], m.index, 'i18n=');
    }
  }
  return out;
}

// ------------------------------------------------------------------ 比对

/** `{{name}}` 占位符集合 */
const placeholders = (text) => new Set([...text.matchAll(/\{\{(\w+)\}\}/g)].map((m) => m[1]));

let failed = false;

for (const target of TARGETS) {
  const catalog =
    target.catalog.kind === 'ts'
      ? readTsCatalog(target.catalog.file)
      : readJavaCatalog(target.catalog.file);

  const usage = collectUsage(target.root);

  /** 拼接式 key（`t('a.b.' + x)`）会被截成前缀，单独归拢 */
  const prefixes = new Set();
  const literals = new Map(); // key -> 首次出现位置
  for (const u of usage) {
    if (u.key.endsWith('.')) {
      prefixes.add(u.key);
      continue;
    }
    if (!literals.has(u.key)) literals.set(u.key, u);
  }

  const missing = [...literals.values()].filter((u) => !catalog.zh.has(u.key));

  // 键集对等（Java 的中英同行，天然对等；TS 要单独比）
  const parity = catalog.en
    ? [...catalog.zh.keys()].filter((k) => !catalog.en.has(k))
        .concat([...catalog.en.keys()].filter((k) => !catalog.zh.has(k)))
    : [];

  // 英文槽为空 / 仍是中文：属于「键命中但内容不对」，i18next 永远不会报
  const blankEn = catalog.en
    ? [...catalog.zh.keys()].filter((k) => catalog.en.has(k) && !catalog.en.get(k).trim())
    : [];
  const cjkEn = catalog.en
    ? [...catalog.zh.keys()].filter((k) => catalog.en.has(k) && CJK.test(catalog.en.get(k)))
    : [];

  // 占位符：中文里有 {{n}}、英文里没有 → 英文界面那一处的数字直接消失
  const phMismatch = catalog.en
    ? [...catalog.zh.keys()]
        .filter((k) => catalog.en.has(k) && catalog.en.get(k).trim())
        .map((k) => {
          const a = [...placeholders(catalog.zh.get(k))].sort();
          const b = [...placeholders(catalog.en.get(k))].sort();
          return a.join(',') === b.join(',') ? null : { key: k, zh: a.join(','), en: b.join(',') };
        })
        .filter(Boolean)
    : [];

  const unused = [...catalog.zh.keys()].filter((k) => !literals.has(k));
  // 前缀家族：至少要有成员，否则整个枚举都没翻
  const emptyFamilies = [...prefixes].filter(
    (p) => ![...catalog.zh.keys()].some((k) => k.startsWith(p)),
  );

  const byVia = (via) => usage.filter((u) => u.via === via).length;
  const propRefs = usage.filter((u) => u.via !== 't()').length;

  console.log(
    `[i18n] ${target.app.padEnd(8)} 引用 ${String(literals.size).padStart(4)} 个键` +
      `（t() ${byVia('t()')} + 注入属性 ${propRefs}）` +
      ` · 目录 ${catalog.note}` +
      ` · 缺定义 ${missing.length}` +
      (catalog.en ? ` · 不对等 ${parity.length} · 空英文 ${blankEn.length} · 英文含中文 ${cjkEn.length} · 占位符 ${phMismatch.length}` : '') +
      ` · 无人引用 ${unused.length}` +
      (catalog.unparsed ? ` · 解析不出 ${catalog.unparsed}` : ''),
  );

  if (catalog.unparsed > 0) {
    failed = true;
    console.log(`  ✗ 目录里有 ${catalog.unparsed} 条解析不出（无法核对，不允许静默跳过）：`);
    console.log(`    ${target.catalog.file}`);
  }

  if (missing.length > 0) {
    failed = true;
    console.log(`  ✗ 引用了但目录里没有：`);
    for (const u of missing) console.log(`    ${u.key}   ${u.file}:${u.line}  (${u.via})`);
  }

  if (parity.length > 0) {
    failed = true;
    console.log(`  ✗ 中英键集不对等（另一边会回落成对方语言，且不触发缺键）：`);
    for (const k of parity.slice(0, 20)) console.log(`    ${k}`);
    if (parity.length > 20) console.log(`    …共 ${parity.length} 个`);
  }

  if (blankEn.length > 0) {
    failed = true;
    console.log(`  ✗ 英文槽是空的（英文界面会靠中文兜底，看着像「没翻译」或整行空白）：`);
    for (const k of blankEn.slice(0, 20)) console.log(`    ${k}`);
    if (blankEn.length > 20) console.log(`    …共 ${blankEn.length} 个`);
  }

  if (cjkEn.length > 0) {
    failed = true;
    console.log(`  ✗ 英文槽里还是中文（复制时漏改，英文界面会显示中文）：`);
    for (const k of cjkEn.slice(0, 20)) console.log(`    ${k}   ${catalog.en.get(k)}`);
    if (cjkEn.length > 20) console.log(`    …共 ${cjkEn.length} 个`);
  }

  if (phMismatch.length > 0) {
    failed = true;
    console.log(`  ✗ 中英占位符不一致（缺的那个值在界面上直接消失，不会报错）：`);
    for (const p of phMismatch.slice(0, 20)) console.log(`    ${p.key}   zh{${p.zh}} en{${p.en}}`);
    if (phMismatch.length > 20) console.log(`    …共 ${phMismatch.length} 个`);
  }

  if (emptyFamilies.length > 0) {
    console.log(`  ! 拼接式前缀在目录里没有任何成员（整个枚举可能都没翻译）：`);
    for (const p of emptyFamilies) console.log(`    ${p}*`);
  }

  if (VERBOSE && unused.length > 0) {
    console.log(`  · 目录里无人引用的键（可能是动态 key 或已废弃）：`);
    for (const k of unused) console.log(`    ${k}`);
  }
}

if (failed) {
  console.log('\n[i18n] 未通过：先补目录，再跑一次。');
  process.exit(1);
}
console.log('\n[i18n] 通过');
