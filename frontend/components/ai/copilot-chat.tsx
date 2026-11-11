"use client";

import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { create } from "zustand";
import { Bot, Send, Sparkles, User as UserIcon, Wrench } from "lucide-react";
import { aiApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { cn } from "@/lib/utils";

interface Message { role: "user" | "assistant"; content: string; tools?: string[]; engine?: string }

/** Conversation kept in memory for the session so drawer and full page share it. */
export const useConversation = create<{ messages: Message[]; suggestions: string[]; add: (m: Message) => void; setSuggestions: (s: string[]) => void; reset: () => void }>((set) => ({
  messages: [],
  suggestions: [
    "How are we performing in the last 24 hours?",
    "Why are payments failing right now?",
    "Which provider should we route to?",
    "Forecast volume for the next 6 hours",
  ],
  add: (m) => set((s) => ({ messages: [...s.messages, m] })),
  setSuggestions: (suggestions) => set({ suggestions }),
  reset: () => set({ messages: [] }),
}));

export function CopilotChat({ compact = false }: { compact?: boolean }) {
  const { messages, suggestions, add, setSuggestions } = useConversation();
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);

  // Block body: newer Chromium returns a Promise from scrollIntoView, which React would treat as a cleanup.
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, busy]);

  const ask = async (question: string) => {
    const q = question.trim();
    if (!q || busy) return;
    setInput("");
    const history = messages.slice(-10).map(({ role, content }) => ({ role, content }));
    add({ role: "user", content: q });
    setBusy(true);
    try {
      const reply = await aiApi.copilot(q, history);
      add({ role: "assistant", content: reply.answer, tools: reply.toolsUsed, engine: reply.engine });
      if (reply.suggestions?.length) setSuggestions(reply.suggestions);
    } catch (e) {
      add({ role: "assistant", content: `**Sorry, I couldn't answer that.** ${errorMessage(e)}` });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex h-full flex-col">
      <div className={cn("flex-1 space-y-4 overflow-y-auto", compact ? "p-4" : "p-6")}>
        {messages.length === 0 && (
          <div className="flex flex-col items-center gap-3 py-10 text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brass-400/15 text-brass-400 glow-brand">
              <Sparkles className="h-7 w-7" />
            </div>
            <h3 className="font-display text-lg font-semibold">PayOps Copilot</h3>
            <p className="max-w-sm text-sm text-muted">
              Grounded in your live payment data. Ask about performance, failures, providers, incidents, risk or forecasts.
            </p>
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={cn("flex gap-3", m.role === "user" && "flex-row-reverse")}>
            <div className={cn("flex h-8 w-8 shrink-0 items-center justify-center rounded-full",
              m.role === "user" ? "bg-ink-600 text-white" : "bg-brass-400/15 text-brass-400")}>
              {m.role === "user" ? <UserIcon className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
            </div>
            <div className={cn("max-w-[85%] rounded-2xl px-4 py-3",
              m.role === "user" ? "bg-ink-600 text-white" : "border border-border bg-surface-2")}>
              {m.role === "assistant" ? (
                <div className="prose-ai"><ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content}</ReactMarkdown></div>
              ) : (
                <p className="text-sm">{m.content}</p>
              )}
              {m.role === "assistant" && (m.tools?.length || m.engine) ? (
                <div className="mt-2 flex flex-wrap items-center gap-1.5 border-t border-border pt-2 text-[10px] text-muted">
                  {m.tools?.length ? <><Wrench className="h-3 w-3" />{m.tools.join(" · ")}</> : null}
                  {m.engine && <span className="ml-auto rounded bg-jade-500/10 px-1.5 py-0.5 text-jade-400">{m.engine}</span>}
                </div>
              ) : null}
            </div>
          </div>
        ))}
        {busy && (
          <div className="flex items-center gap-3 text-sm text-muted">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-jade-500/15 text-jade-400"><Bot className="h-4 w-4" /></div>
            <span className="flex gap-1">
              <span className="h-2 w-2 animate-bounce rounded-full bg-jade-400" />
              <span className="h-2 w-2 animate-bounce rounded-full bg-jade-400 [animation-delay:120ms]" />
              <span className="h-2 w-2 animate-bounce rounded-full bg-jade-400 [animation-delay:240ms]" />
            </span>
            Analysing live data…
          </div>
        )}
        <div ref={endRef} />
      </div>
      <div className="border-t border-border p-3">
        <div className="mb-2 flex flex-wrap gap-1.5">
          {suggestions.slice(0, compact ? 3 : 4).map((s) => (
            <button key={s} onClick={() => ask(s)} disabled={busy}
              className="rounded-full border border-border px-2.5 py-1 text-[11px] text-muted hover:border-jade-500/50 hover:text-jade-400 cursor-pointer">
              {s}
            </button>
          ))}
        </div>
        <form onSubmit={(e) => { e.preventDefault(); ask(input); }} className="flex gap-2">
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            maxLength={2000}
            placeholder="Ask about payments, providers, incidents…"
            className="h-11 flex-1 rounded-xl border border-border bg-surface-2 px-4 text-sm focus:border-jade-500/60 focus:outline-none"
          />
          <button type="submit" disabled={busy || !input.trim()} aria-label="Send"
            className="flex h-11 w-11 items-center justify-center rounded-xl bg-jade-600 text-white disabled:opacity-40 cursor-pointer">
            <Send className="h-4 w-4" />
          </button>
        </form>
        <p className="mt-1.5 text-center text-[10px] text-muted">AI output is advisory. Customer identifiers are masked before analysis.</p>
      </div>
    </div>
  );
}
