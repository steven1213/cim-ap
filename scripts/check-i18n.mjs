#!/usr/bin/env node
// 文案键静态对表：源码里用到的键 是否都在目录里。
//
// 为什么需要它：i18next 缺键不报错——生产环境 humanize 出一句「看起来像译文」的假话
// （`rms.notFound.desc` → `Desc`），开发环境才显示 `⟦key⟧`。也就是说**缺键只会在开发时
// 被看见**，而「有没有人真的打开过那个页面」是没法保证的。这个脚本把这件事变成一次静态比对。
//
// 扫两类引用（少扫一类就会漏）：
//   1. `t('some.key')`            —— 直接取文案
//   2. `xxxKey="some.key"`        —— 注入式（`titleKey` / `hintKey` / `okKey` …），
//      平台组件（EmptyState / Panel / CimDialog…）按这个 key 自己去翻译。
//      ⚠️ 只扫 t() 会漏掉整整一类：`<NotFoundState hintKey="rms.notFound.desc">` 就是这么漏的。
//
// 排除两类「名字像文案键但不是」的属性：`settingsKey`（DataTable 的列设置持久化键）、
// `storageKey`（localStorage 键）。它们的值形如 `iam.audit`，扫进来会变成假缺口。
//
// 用法：node scripts/check-i18n.mjs [--verbose]
// 退出码：有真缺口（引用了但目录里没有）或中英键集不对等 → 1

import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();
const VERBOSE = process.argv.includes('--verbose');

/** 名字以 Key 结尾但不是文案键的属性 */
const NON_I18N_PROPS = new Set(['settingsKey', 'storageKey']);

/** 各应用：源码根目录 + 文案目录来源 */
const TARGETS = [
  {
    app: 'rms',
    root: 'business/rms-ap/web/src',
    // 内置包：中英两份都在同一个文件里，故顺带能做键集对等检查
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

/** 从 TS 文案包取 { zh, en } 两个键集；没有 enUS 段时 en 为 null。 */
function readTsCatalog(file) {
  const src = readFileSync(join(ROOT, file), 'utf8');
  const splitAt = src.indexOf('export const enUS');
  const zhPart = splitAt >= 0 ? src.slice(0, splitAt) : src;
  const enPart = splitAt >= 0 ? src.slice(splitAt) : '';
  const keysOf = (text) =>
    new Set([...text.matchAll(/^\s*'([\w.\-]+)'\s*:/gm)].map((m) => m[1]));
  return {
    zh: keysOf(zhPart),
    en: splitAt >= 0 ? keysOf(enPart) : null,
    note: splitAt >= 0 ? `${keysOf(zhPart).size} / en ${keysOf(enPart).size}` : `${keysOf(zhPart).size}`,
  };
}

/** 从 Java 目录取键集（`new TextDef("key", "中文", "English")`，中英同行，天然对等）。 */
function readJavaCatalog(file) {
  const src = readFileSync(join(ROOT, file), 'utf8');
  const keys = new Set([...src.matchAll(/new TextDef\(\s*"([\w.\-]+)"/g)].map((m) => m[1]));
  return { zh: keys, en: null, note: `${keys.size}` };
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
  }
  return out;
}

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

  const parity =
    catalog.en === null
      ? []
      : [...catalog.zh].filter((k) => !catalog.en.has(k)).concat([...catalog.en].filter((k) => !catalog.zh.has(k)));

  const unused = [...catalog.zh].filter((k) => !literals.has(k));
  // 前缀家族：至少要有成员，否则整个枚举都没翻
  const emptyFamilies = [...prefixes].filter(
    (p) => ![...catalog.zh].some((k) => k.startsWith(p)),
  );

  const byVia = (via) => usage.filter((u) => u.via === via).length;
  const propRefs = usage.filter((u) => u.via !== 't()').length;

  console.log(
    `[i18n] ${target.app.padEnd(8)} 引用 ${String(literals.size).padStart(4)} 个键` +
      `（t() ${byVia('t()')} + 注入属性 ${propRefs}）` +
      ` · 目录 ${catalog.note}` +
      ` · 缺定义 ${missing.length}` +
      (catalog.en === null ? '' : ` · 中英不对等 ${parity.length}`) +
      ` · 无人引用 ${unused.length}`,
  );

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
