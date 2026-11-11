// useDebounced：把「输入 → 防抖 → 再取数」的重担从每个页面移走。
//
// 改造前的写法是把防抖塞进取数函数：
//     useEffect(() => { const id = setTimeout(refresh, 200); return () => clearTimeout(id) }, [keyword]);
// 两个问题：① `refresh` 一变就重新计时，语义含糊；② 防抖后的值没有被记住，
// 页面拿到的仍是即时值，于是「后端按 debounced 值过滤、前端按即时值再滤一遍」——
// 两套值不一致时会闪一行「无数据」。
//
// 这里只做一件事：返回**滞后**的值；取数与渲染都用同一个滞后值。

import { useEffect, useState } from 'react';

export function useDebounced<T>(value: T, delay = 200): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    if (delay <= 0) {
      setDebounced(value);
      return;
    }
    const id = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(id);
  }, [value, delay]);

  return debounced;
}
