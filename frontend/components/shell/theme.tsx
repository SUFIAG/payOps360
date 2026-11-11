"use client";

import { create } from "zustand";

type Theme = "dark" | "light";

/** Theme preference (non-sensitive, persisted in localStorage). Dark is the default. */
export const useTheme = create<{ theme: Theme; toggle: () => void; init: () => void }>((set, get) => ({
  theme: "dark",
  init: () => {
    const stored = typeof window !== "undefined" ? (localStorage.getItem("payops-theme") as Theme | null) : null;
    const theme = stored ?? "dark";
    document.documentElement.classList.toggle("dark", theme === "dark");
    set({ theme });
  },
  toggle: () => {
    const theme = get().theme === "dark" ? "light" : "dark";
    localStorage.setItem("payops-theme", theme);
    document.documentElement.classList.toggle("dark", theme === "dark");
    set({ theme });
  },
}));
