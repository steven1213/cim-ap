import { create } from 'zustand';

export type ToastKind = 'ok' | 'err' | 'warn' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  msg: string;
}

interface ToastState {
  toasts: Toast[];
  push: (msg: string, kind?: ToastKind) => void;
  remove: (id: number) => void;
}

let seq = 0;

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  push: (msg, kind = 'info') => {
    const id = ++seq;
    set((s) => ({ toasts: [...s.toasts, { id, kind, msg }] }));
    setTimeout(() => get().remove(id), 4200);
  },
  remove: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

export function toast(msg: string, kind: ToastKind = 'info'): void {
  useToastStore.getState().push(msg, kind);
}
