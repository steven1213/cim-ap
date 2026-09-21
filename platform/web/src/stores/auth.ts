import { create } from 'zustand';

interface AuthState {
  /** accessToken 仅存内存（平台前端文档 §3/§10）；refreshToken 走 httpOnly Cookie */
  token: string | null;
  setToken: (token: string | null) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  setToken: (token) => set({ token }),
}));
