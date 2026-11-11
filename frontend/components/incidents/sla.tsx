"use client";

import { useEffect, useState } from "react";
import { AlarmClock, BellRing, CircleCheck, TimerOff } from "lucide-react";
import { Badge, type Tone } from "@/components/ui/primitives";
import { cn } from "@/lib/utils";
import type { Incident } from "@/types/api";

/** Re-renders every `intervalMs` so countdowns tick between refetches. */
export function useNow(intervalMs = 1000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

export function formatDuration(totalSeconds: number) {
  const s = Math.abs(Math.round(totalSeconds));
  const d = Math.floor(s / 86_400);
  const h = Math.floor((s % 86_400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${sec.toString().padStart(2, "0")}s`;
  return `${sec}s`;
}

type SlaKind = "ack" | "resolve";

interface SlaState { tone: Tone; text: string; breached: boolean; pending: boolean }

/** Derives the live state of one SLA timer of an incident. */
export function slaState(incident: Incident, kind: SlaKind, now: number): SlaState | null {
  const sla = incident.sla;
  if (!sla) return null;
  const due = kind === "ack" ? sla.ackDueAt : sla.resolveDueAt;
  if (!due) return null;
  const pending = kind === "ack"
    ? sla.ackRemainingSeconds !== undefined && sla.ackRemainingSeconds !== null
    : sla.resolveRemainingSeconds !== undefined && sla.resolveRemainingSeconds !== null;
  const flagged = kind === "ack" ? sla.ackBreached : sla.resolveBreached;
  if (!pending) {
    return flagged ? { tone: "red", text: "breached", breached: true, pending: false } : { tone: "green", text: "met", breached: false, pending: false };
  }
  const remaining = (new Date(due).getTime() - now) / 1000;
  if (remaining <= 0 || flagged) {
    return { tone: "red", text: `overdue ${formatDuration(Math.min(remaining, 0))}`, breached: true, pending: true };
  }
  return { tone: remaining < 300 ? "amber" : "blue", text: `${formatDuration(remaining)} left`, breached: false, pending: true };
}

/** Countdown chip for the acknowledge or resolve SLA of an incident. */
export function SlaChip({ incident, kind, className }: { incident: Incident; kind: SlaKind; className?: string }) {
  const now = useNow();
  const state = slaState(incident, kind, now);
  if (!state) return null;
  const label = kind === "ack" ? "Ack" : "Resolve";
  const Icon = state.breached ? TimerOff : state.pending ? AlarmClock : CircleCheck;
  const due = kind === "ack" ? incident.sla?.ackDueAt : incident.sla?.resolveDueAt;
  return (
    <Badge
      tone={state.tone}
      className={cn("font-mono tabular-nums", state.breached && state.pending && "animate-pulse", className)}
    >
      <span title={due ? `${label} due ${new Date(due).toLocaleString()}` : undefined} className="inline-flex items-center gap-1">
        <Icon className="h-3 w-3" />
        <span className="font-sans">{label}</span> {state.text}
      </span>
    </Badge>
  );
}

/** Compact SLA badge for lists: the most urgent pending timer, or the overall outcome. */
export function SlaBadge({ incident }: { incident: Incident }) {
  const now = useNow(5000);
  const ack = slaState(incident, "ack", now);
  const resolve = slaState(incident, "resolve", now);
  if (!ack && !resolve) return null;
  const pending = [ack, resolve].find((s) => s?.pending);
  if (pending) {
    const kind = ack?.pending ? "Ack" : "Resolve";
    return (
      <Badge tone={pending.tone} className="font-mono tabular-nums">
        {pending.breached ? <TimerOff className="h-3 w-3" /> : <AlarmClock className="h-3 w-3" />}
        <span className="font-sans">{kind}</span> {pending.text}
      </Badge>
    );
  }
  const breached = !!(ack?.breached || resolve?.breached);
  return (
    <Badge tone={breached ? "red" : "green"}>
      {breached ? <TimerOff className="h-3 w-3" /> : <CircleCheck className="h-3 w-3" />}
      SLA {breached ? "breached" : "met"}
    </Badge>
  );
}

export function EscalationBadge({ level }: { level: number }) {
  if (!level) return null;
  return (
    <span title={`Escalation policy level ${level} has been notified`}>
      <Badge tone={level >= 2 ? "red" : "amber"}>
        <BellRing className="h-3 w-3" /> Escalation L{level}
      </Badge>
    </span>
  );
}
