"use client";

import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { BrainCircuit, Radar, ShieldCheck, Sparkles, Zap } from "lucide-react";
import { Logo } from "@/components/shell/logo";
import { authApi } from "@/lib/api/endpoints";

/**
 * Two-column frame shared by every sign-in and onboarding page: the ink
 * story panel on the left, the form column on the right. `stepKey` re-runs
 * the entry animation when a multi-step form changes step.
 */
export function AuthLayout({ children, stepKey }: { children: React.ReactNode; stepKey?: string }) {
  return (
    <div className="grid h-dvh overflow-hidden lg:grid-cols-[1.05fr_1fr]">
      <section className="ink-panel relative hidden overflow-hidden px-14 py-10 lg:flex lg:flex-col lg:justify-between">
        <div className="relative z-10"><Logo tone="light" /></div>

        <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }} className="relative z-10 max-w-xl">
          <span className="inline-flex items-center gap-2 rounded-full border border-[#d6b574]/30 bg-[#d6b574]/10 px-3 py-1 text-[11px] font-medium uppercase tracking-[0.16em] text-[#e6cc96]">
            <Sparkles className="h-3.5 w-3.5" /> AI-native payment operations
          </span>
          <h1 className="mt-5 font-display text-[2.5rem] font-semibold leading-[1.12] tracking-[-0.025em] text-[#f4f2ed] xl:text-[2.85rem]">
            See every payment.<br /><em className="text-gradient-brand not-italic">Resolve issues before customers notice.</em>
          </h1>
          <p className="mt-5 max-w-lg text-sm leading-relaxed text-[#a5a29a] xl:text-[15px]">
            One calm command center for providers, corridors and incidents — with an AI copilot, anomaly detection and
            automated root-cause analysis watching every settlement, around the clock.
          </p>

          <LiveLedgerCard />

          <ul className="mt-8 grid grid-cols-2 gap-x-8 gap-y-4 border-t border-white/10 pt-6">
            {[
              [Radar, "Real-time anomaly detection"],
              [BrainCircuit, "AI root-cause analysis"],
              [Zap, "Smart routing advice"],
              [ShieldCheck, "Bank-grade security & audit"],
            ].map(([Icon, title]) => {
              const I = Icon as React.ElementType;
              return (
                <li key={title as string} className="flex items-center gap-3 text-[13px] text-[#d9d5cc]">
                  <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-white/[0.06] ring-1 ring-white/10"><I className="h-3.5 w-3.5 text-[#6fd3ae]" /></span>
                  {title as string}
                </li>
              );
            })}
          </ul>
        </motion.div>

        <p className="relative z-10 text-[11px] text-[#8e897f]">© {new Date().getFullYear()} PayOps360 · ISO 27001-aligned controls · Encrypted at rest and in transit</p>
      </section>

      <section className="console-backdrop flex items-center justify-center overflow-y-auto p-6">
        <motion.div key={stepKey} initial={{ opacity: 0, x: 12 }} animate={{ opacity: 1, x: 0 }} className="w-full max-w-sm py-6">
          <div className="mb-8 lg:hidden"><Logo /></div>
          {children}
        </motion.div>
      </section>
    </div>
  );
}

/** Heading block used at the top of each auth form. */
export function AuthHeading({ title, description }: { title: string; description?: React.ReactNode }) {
  return (
    <div>
      <h2 className="font-display text-[1.75rem] font-semibold tracking-[-0.02em]">{title}</h2>
      {description && <p className="mt-1.5 text-sm text-muted">{description}</p>}
    </div>
  );
}

/** Public onboarding configuration (self-signup flag, password minimum length). */
export function useAuthConfig() {
  return useQuery({ queryKey: ["auth-config"], queryFn: authApi.config, staleTime: 5 * 60_000, retry: false });
}

/** Decorative preview: a success-rate line that draws itself, framed like a ledger statement. */
function LiveLedgerCard() {
  const line = "M0 38 C 20 34, 34 36, 52 30 S 86 22, 104 26 S 140 14, 160 16 S 196 8, 220 10 S 262 6, 280 4";
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.35, duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
      className="mt-8 rounded-2xl border [@media(max-height:820px)]:hidden border-white/10 bg-white/[0.04] p-5 backdrop-blur-sm"
      aria-hidden
    >
      <div className="flex items-start justify-between">
        <div>
          <p className="text-[11px] uppercase tracking-[0.16em] text-[#8e897f]">Settlement success · last 24h</p>
          <p className="mt-1 font-display text-3xl font-semibold tracking-tight tabular-nums text-[#f4f2ed]">99.42<span className="text-xl text-[#a5a29a]">%</span></p>
        </div>
        <span className="flex items-center gap-1.5 rounded-full bg-[#3fbf92]/12 px-2.5 py-1 text-[11px] text-[#6fd3ae]">
          <span className="relative flex h-1.5 w-1.5">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[#3fbf92] opacity-60" />
            <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-[#3fbf92]" />
          </span>
          Live
        </span>
      </div>
      <svg viewBox="0 0 280 44" className="mt-3 h-12 w-full" preserveAspectRatio="none">
        <defs>
          <linearGradient id="ledger-fill" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="#3fbf92" stopOpacity="0.28" />
            <stop offset="100%" stopColor="#3fbf92" stopOpacity="0" />
          </linearGradient>
        </defs>
        <motion.path d={`${line} L280 44 L0 44 Z`} fill="url(#ledger-fill)" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 1.4, duration: 0.8 }} />
        <motion.path d={line} fill="none" stroke="#3fbf92" strokeWidth="2" strokeLinecap="round"
          initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ delay: 0.5, duration: 1.6, ease: "easeInOut" }} />
      </svg>
      <div className="mt-3 grid grid-cols-3 gap-4 border-t border-white/10 pt-3 text-[11px] text-[#8e897f]">
        <div><p>Volume</p><p className="mt-0.5 font-mono text-[13px] text-[#eeece7]">$4.82M</p></div>
        <div><p>p95 latency</p><p className="mt-0.5 font-mono text-[13px] text-[#eeece7]">412 ms</p></div>
        <div><p>Open incidents</p><p className="mt-0.5 font-mono text-[13px] text-[#e6cc96]">1</p></div>
      </div>
    </motion.div>
  );
}
