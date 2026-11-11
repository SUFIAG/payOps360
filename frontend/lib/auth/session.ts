import { create } from "zustand";
import type { AuthResponse, Permission, User } from "@/types/api";

/**
 * In-memory session. Nothing sensitive is written to localStorage, so an XSS
 * cannot exfiltrate a long-lived credential; reloads restore the session via
 * the HttpOnly refresh cookie.
 */
interface SessionState {
  accessToken: string | null;
  user: User | null;
  organizationName: string;
  status: "unknown" | "authenticated" | "anonymous";
  signIn: (auth: AuthResponse) => void;
  setUser: (user: User, organizationName?: string) => void;
  expire: () => void;
  can: (permission: Permission) => boolean;
}

export const useSession = create<SessionState>((set, get) => ({
  accessToken: null,
  user: null,
  organizationName: "",
  status: "unknown",
  signIn: (auth) =>
    set({
      accessToken: auth.accessToken ?? null,
      user: auth.user ?? get().user,
      organizationName: auth.organizationName ?? get().organizationName,
      status: "authenticated",
    }),
  setUser: (user, organizationName) => set({ user, organizationName: organizationName ?? get().organizationName }),
  expire: () => set({ accessToken: null, user: null, status: "anonymous" }),
  can: (permission) => !!get().user?.permissions.includes(permission),
}));

export function usePermission(permission: Permission): boolean {
  return useSession((s) => !!s.user?.permissions.includes(permission));
}
