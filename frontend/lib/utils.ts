import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import { formatDistanceToNowStrict } from "date-fns";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatMoney(value: number, currency = "USD", compact = false) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    notation: compact ? "compact" : "standard",
    maximumFractionDigits: compact ? 1 : 2,
  }).format(value);
}

export function formatNumber(value: number, compact = false) {
  return new Intl.NumberFormat("en-US", { notation: compact ? "compact" : "standard", maximumFractionDigits: 1 }).format(value);
}

export function formatPercent(value: number | undefined | null, digits = 1) {
  return value === undefined || value === null ? "—" : `${value.toFixed(digits)}%`;
}

export function formatDateTime(value?: string) {
  if (!value) return "—";
  return new Date(value).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" });
}

export function timeAgo(value?: string) {
  return value ? `${formatDistanceToNowStrict(new Date(value))} ago` : "—";
}

/** Regional-indicator flag emoji for an ISO-3166 alpha-2 code. */
export function flag(country?: string) {
  if (!country || !/^[A-Z]{2}$/.test(country)) return "🌐";
  return String.fromCodePoint(...[...country].map((c) => 0x1f1a5 + c.charCodeAt(0)));
}

export function humanize(value?: string) {
  return value ? value.toLowerCase().replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase()) : "";
}

/** Success rate of completed payments (backend sends raw counts). */
export function successRate(s: { succeeded: number; failed: number }) {
  const done = s.succeeded + s.failed;
  return done === 0 ? 0 : (s.succeeded * 100) / done;
}

export function change(value: number, previous?: number | null) {
  return previous === undefined || previous === null || previous === 0 ? undefined : ((value - previous) * 100) / Math.abs(previous);
}
