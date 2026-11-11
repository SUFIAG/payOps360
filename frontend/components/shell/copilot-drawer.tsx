"use client";

import { create } from "zustand";
import { AnimatePresence, motion } from "framer-motion";
import { Bot, Maximize2, X } from "lucide-react";
import Link from "next/link";
import { CopilotChat } from "@/components/ai/copilot-chat";

export const useCopilot = create<{ open: boolean; setOpen: (o: boolean) => void }>((set) => ({
  open: false,
  setOpen: (open) => set({ open }),
}));

export function CopilotDrawer() {
  const { open, setOpen } = useCopilot();
  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div className="fixed inset-0 z-40 bg-ink-950/50 backdrop-blur-[2px]" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setOpen(false)} />
          <motion.aside
            className="fixed inset-y-0 right-0 z-50 flex w-full max-w-md flex-col border-l border-jade-500/20 bg-[var(--bg)] shadow-2xl"
            initial={{ x: "100%" }} animate={{ x: 0 }} exit={{ x: "100%" }} transition={{ type: "spring", damping: 30, stiffness: 300 }}
          >
            <div className="flex items-center justify-between border-b border-border px-4 py-3">
              <div className="flex items-center gap-2 font-display font-semibold"><Bot className="h-5 w-5 text-brass-400" /> PayOps Copilot</div>
              <div className="flex gap-1">
                <Link href="/copilot" onClick={() => setOpen(false)} className="rounded-md p-1.5 text-muted hover:text-foreground" aria-label="Open full screen">
                  <Maximize2 className="h-4 w-4" />
                </Link>
                <button onClick={() => setOpen(false)} className="rounded-md p-1.5 text-muted hover:text-foreground cursor-pointer" aria-label="Close"><X className="h-4 w-4" /></button>
              </div>
            </div>
            <div className="min-h-0 flex-1"><CopilotChat compact /></div>
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}
