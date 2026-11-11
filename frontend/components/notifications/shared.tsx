"use client";

import { Mail, MessageSquare, PhoneCall, Users, Webhook } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ChannelType, NotificationChannel, NotificationEventType, Severity } from "@/types/api";

export const SEVERITIES: Severity[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

export const CHANNEL_META: Record<ChannelType, {
  label: string; icon: React.ElementType; targetLabel: string; placeholder: string; hint: string; className: string;
}> = {
  EMAIL: {
    label: "E-mail", icon: Mail, targetLabel: "Recipients", placeholder: "oncall@acme.com, payments-ops@acme.com",
    hint: "Up to 20 addresses separated by commas. Delivered through the platform e-mail outbox.",
    className: "bg-info/12 text-info",
  },
  SLACK: {
    label: "Slack", icon: MessageSquare, targetLabel: "Incoming webhook URL", placeholder: "https://hooks.slack.com/services/…",
    hint: "Create an incoming webhook for the target channel in your Slack workspace.",
    className: "bg-brass-400/12 text-brass-400",
  },
  MS_TEAMS: {
    label: "Microsoft Teams", icon: Users, targetLabel: "Webhook URL", placeholder: "https://….webhook.office.com/…",
    hint: "Use an incoming webhook connector or a Workflows \"post to a channel when a webhook request is received\" URL.",
    className: "bg-ink-400/15 text-muted",
  },
  WEBHOOK: {
    label: "Webhook", icon: Webhook, targetLabel: "Endpoint URL", placeholder: "https://ops.acme.com/hooks/payops",
    hint: "JSON POST signed with HMAC-SHA256: X-PayOps-Signature: t=<unix>,v1=hex(hmac(secret, \"<t>.<body>\")).",
    className: "bg-jade-500/12 text-jade-400",
  },
  PAGERDUTY: {
    label: "PagerDuty", icon: PhoneCall, targetLabel: "Integration (routing) key", placeholder: "32-character Events API v2 key",
    hint: "Events API v2 integration key of a PagerDuty service. Incidents resolve automatically on INCIDENT_RESOLVED.",
    className: "bg-danger/12 text-danger",
  },
};

export const CHANNEL_TYPES = Object.keys(CHANNEL_META) as ChannelType[];

export const EVENT_META: Record<NotificationEventType, { label: string; hint: string }> = {
  ALERT_RAISED: { label: "Alert raised", hint: "A new alert is detected" },
  INCIDENT_OPENED: { label: "Incident opened", hint: "Declared or auto-correlated" },
  INCIDENT_ESCALATED: { label: "Incident escalated", hint: "Severity was raised" },
  INCIDENT_SLA_BREACHED: { label: "Incident SLA breached", hint: "Acknowledge or resolve target missed" },
  INCIDENT_RESOLVED: { label: "Incident resolved", hint: "Incident closed" },
  PROVIDER_DEGRADED: { label: "Provider degraded", hint: "Health turns degraded or critical" },
  RECONCILIATION_EXCEPTIONS: { label: "Reconciliation exceptions", hint: "A settlement batch produced exceptions over the threshold" },
};

export const EVENT_TYPES = Object.keys(EVENT_META) as NotificationEventType[];

export function ChannelIcon({ type, className }: { type: ChannelType; className?: string }) {
  const meta = CHANNEL_META[type];
  const Icon = meta.icon;
  return (
    <span className={cn("flex h-9 w-9 shrink-0 items-center justify-center rounded-xl", meta.className, className)} aria-hidden>
      <Icon className="h-4 w-4" />
    </span>
  );
}

/** Checkbox list for picking channels. */
export function ChannelPicker({ channels, value, onChange, idPrefix }: {
  channels: NotificationChannel[]; value: string[]; onChange: (ids: string[]) => void; idPrefix: string;
}) {
  if (channels.length === 0) {
    return <p className="text-sm text-muted">Create a channel first (Channels tab).</p>;
  }
  return (
    <div className="grid max-h-52 gap-1.5 overflow-y-auto pr-1 sm:grid-cols-2">
      {channels.map((c) => {
        const checked = value.includes(c.id);
        const Icon = CHANNEL_META[c.type].icon;
        return (
          <label
            key={c.id}
            htmlFor={`${idPrefix}-${c.id}`}
            className={cn(
              "flex cursor-pointer items-center gap-2 rounded-lg border px-2.5 py-2 text-sm transition-colors",
              checked ? "border-jade-500/50 bg-jade-500/10" : "border-border hover:border-jade-500/30",
              !c.enabled && "opacity-60",
            )}
          >
            <input
              id={`${idPrefix}-${c.id}`}
              type="checkbox"
              className="h-4 w-4 accent-[#0e7c5b]"
              checked={checked}
              onChange={(e) => onChange(e.target.checked ? [...value, c.id] : value.filter((id) => id !== c.id))}
            />
            <Icon className="h-3.5 w-3.5 text-muted" />
            <span className="truncate">{c.name}</span>
            {!c.enabled && <span className="ml-auto text-[10px] uppercase text-muted">off</span>}
          </label>
        );
      })}
    </div>
  );
}

export function Toggle({ checked, onChange, label, id }: { checked: boolean; onChange: (v: boolean) => void; label: string; id: string }) {
  return (
    <label htmlFor={id} className="inline-flex cursor-pointer items-center gap-2 text-sm">
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={cn(
          "relative h-5 w-9 rounded-full transition-colors cursor-pointer",
          checked ? "bg-jade-600" : "bg-surface-2 border border-border",
        )}
      >
        <span className={cn("absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-all", checked ? "left-[18px]" : "left-0.5")} />
      </button>
      {label}
    </label>
  );
}

export function channelNames(ids: string[], channels: NotificationChannel[]) {
  const names = ids.map((id) => channels.find((c) => c.id === id)?.name).filter(Boolean) as string[];
  return names.length ? names.join(", ") : "—";
}
