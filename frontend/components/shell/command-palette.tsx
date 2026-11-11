"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { create } from "zustand";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { ArrowRight, Bot, CreditCard, Search } from "lucide-react";
import { useCopilot } from "./copilot-drawer";
import { useConversation } from "@/components/ai/copilot-chat";
import { cn } from "@/lib/utils";

export const useCommandPalette = create<{ open: boolean; setOpen: (o: boolean) => void }>((set) => ({
  open: false,
  setOpen: (open) => set({ open }),
}));

const PAGES = [
  ["Command Center", "/dashboard"], ["Payments", "/payments"], ["Providers", "/providers"], ["Alerts", "/alerts"],
  ["Incidents", "/incidents"], ["Reconciliation", "/reconciliation"], ["Analytics", "/analytics"], ["AI Copilot", "/copilot"], ["Users & Roles", "/admin/users"],
  ["Audit Trail", "/admin/audit"], ["Security settings", "/settings/security"],
] as const;

export function CommandPalette() {
  const { open, setOpen } = useCommandPalette();
  const router = useRouter();
  const openCopilot = useCopilot((s) => s.setOpen);
  const [query, setQuery] = useState("");
  const [index, setIndex] = useState(0);

  const onOpenChange = (next: boolean) => {
    if (!next) setQuery("");
    setOpen(next);
  };

  const actions = useMemo(() => {
    const q = query.trim();
    const list: { label: string; hint: string; icon: React.ElementType; run: () => void }[] = [];
    if (/^PAY-[A-Z0-9]{12}$/i.test(q)) {
      list.push({ label: `Open payment ${q.toUpperCase()}`, hint: "Payment", icon: CreditCard, run: () => router.push(`/payments/${q.toUpperCase()}`) });
    }
    PAGES.filter(([label]) => label.toLowerCase().includes(q.toLowerCase())).forEach(([label, href]) =>
      list.push({ label, hint: "Go to", icon: ArrowRight, run: () => router.push(href) }));
    if (q) {
      list.push({ label: `Search payments: “${q}”`, hint: "AI search", icon: Search, run: () => router.push(`/payments?ask=${encodeURIComponent(q)}`) });
      list.push({
        label: `Ask Copilot: “${q}”`, hint: "AI", icon: Bot,
        run: () => {
          openCopilot(true);
          useConversation.getState().setSuggestions([q, ...useConversation.getState().suggestions.filter((s) => s !== q)]);
        },
      });
    }
    return list;
  }, [query, router, openCopilot]);

  const run = (i: number) => {
    actions[i]?.run();
    onOpenChange(false);
  };

  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-ink-950/70 backdrop-blur-sm" />
        <DialogPrimitive.Content className="fixed left-1/2 top-[15vh] z-50 w-[92vw] max-w-xl -translate-x-1/2 overflow-hidden rounded-2xl border border-jade-500/25 bg-[var(--bg)] shadow-2xl">
          <DialogPrimitive.Title className="sr-only">Command palette</DialogPrimitive.Title>
          <DialogPrimitive.Description className="sr-only">Navigate, search payments or ask the AI copilot</DialogPrimitive.Description>
          <div className="flex items-center gap-3 border-b border-border px-4">
            <Search className="h-4 w-4 text-jade-400" />
            <input
              autoFocus
              value={query}
              onChange={(e) => { setQuery(e.target.value); setIndex(0); }}
              onKeyDown={(e) => {
                if (e.key === "ArrowDown") { e.preventDefault(); setIndex((i) => Math.min(i + 1, actions.length - 1)); }
                if (e.key === "ArrowUp") { e.preventDefault(); setIndex((i) => Math.max(i - 1, 0)); }
                if (e.key === "Enter") { e.preventDefault(); run(index); }
              }}
              placeholder="Type a page, a PAY- reference, or a question…"
              className="h-14 flex-1 bg-transparent text-sm outline-none"
            />
          </div>
          <div className="max-h-80 overflow-y-auto p-2">
            {actions.map((a, i) => {
              const Icon = a.icon;
              return (
                <button key={a.label} onMouseEnter={() => setIndex(i)} onClick={() => run(i)}
                  className={cn("flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm cursor-pointer", i === index ? "bg-jade-500/10 text-jade-400" : "text-foreground")}>
                  <Icon className="h-4 w-4" />
                  <span className="flex-1 truncate">{a.label}</span>
                  <span className="text-[10px] uppercase tracking-wider text-muted">{a.hint}</span>
                </button>
              );
            })}
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
