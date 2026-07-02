import { create } from "zustand";
import { User } from "@/types";
import { authApi } from "@/lib/api/auth";

interface AuthState {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  fetchUser: () => Promise<void>;
  setUser: (user: User | null) => void;
  setToken: (token: string | null) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null,
  isLoading: false,
  isAuthenticated: false,

  login: async (email: string, password: string) => {
    set({ isLoading: true });
    try {
      await authApi.login({ email, password });
      const user = await authApi.getCurrentUser();
      const token = localStorage.getItem('accessToken');
      set({ user, token, isAuthenticated: true, isLoading: false });
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      set({ user: null, token: null, isAuthenticated: false });
    }
  },

  fetchUser: async () => {
    set({ isLoading: true });
    try {
      const user = await authApi.getCurrentUser();
      const token = localStorage.getItem('accessToken');
      set({ user, token, isAuthenticated: true, isLoading: false });
    } catch (error) {
      set({ user: null, token: null, isAuthenticated: false, isLoading: false });
    }
  },

  setUser: (user: User | null) => {
    set({ user, isAuthenticated: !!user });
  },

  setToken: (token: string | null) => {
    set({ token });
  },
}));

