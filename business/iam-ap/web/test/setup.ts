// 渲染契约测试的运行环境垫片。
//
// ⚠️ 本模块必须是测试入口的**第一个** import：ESM 会先求值依赖，
// 页面模块（经 zustand persist / lib/i18n）在**求值时或首屏渲染时**就要用浏览器 API，
// 这里必须先补齐，否则会在 import 阶段就抛 ReferenceError。
//
// 只补「渲染路径真的会碰到」的那几个：`localStorage`（zustand persist 落盘 + 语言读取）。
// 刻意**不**伪装 `window`：凡是需要 `window` 的代码都在事件回调里（如 `window.confirm`），
// 首屏不会执行，缺了它反而能暴露「把副作用写进了渲染路径」这类问题。

type StorageLike = {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
  clear(): void;
  key(index: number): string | null;
  readonly length: number;
};

const memory = new Map<string, string>();

const storage: StorageLike = {
  getItem: (key) => (memory.has(key) ? (memory.get(key) as string) : null),
  setItem: (key, value) => {
    memory.set(key, String(value));
  },
  removeItem: (key) => {
    memory.delete(key);
  },
  clear: () => {
    memory.clear();
  },
  key: (index) => Array.from(memory.keys())[index] ?? null,
  get length() {
    return memory.size;
  },
};

(globalThis as unknown as { localStorage: StorageLike }).localStorage = storage;

export {};
