import { Badge, type Tone } from "./primitives";
import { cn, humanize } from "@/lib/utils";
import type { HealthStatus, PaymentStatus, Severity } from "@/types/api";

const paymentTone: Record<PaymentStatus, Tone> = {
  INITIATED: "gray", AUTHORIZED: "blue", CAPTURED: "blue", PROCESSING: "blue", SETTLED: "green",
  FAILED: "red", RETRY_PENDING: "amber", RETRY_IN_PROGRESS: "amber", RETRY_FAILED: "red",
  REFUNDED: "ink", CHARGEBACK: "red", CANCELLED: "gray",
};
export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  return <Badge tone={paymentTone[status]} dot>{humanize(status)}</Badge>;
}

const severityTone: Record<Severity, Tone> = { LOW: "gray", MEDIUM: "amber", HIGH: "red", CRITICAL: "red" };
export function SeverityBadge({ severity }: { severity: Severity }) {
  return (
    <Badge tone={severityTone[severity]} className={cn(severity === "CRITICAL" && "animate-pulse")}>{severity}</Badge>
  );
}

export function RiskBadge({ level, score }: { level: Severity; score?: number }) {
  const tone: Tone = level === "LOW" ? "green" : level === "MEDIUM" ? "amber" : "red";
  return <Badge tone={tone}>{score !== undefined ? `${score} · ` : ""}{level}</Badge>;
}

const healthColor: Record<HealthStatus, string> = {
  HEALTHY: "text-jade-400 bg-jade-400",
  DEGRADED: "text-warning bg-warning",
  CRITICAL: "text-danger bg-danger",
  UNKNOWN: "text-muted bg-muted",
};
export function HealthDot({ status, className }: { status: HealthStatus; className?: string }) {
  return <span className={cn("inline-block h-2.5 w-2.5 rounded-full", healthColor[status], status !== "UNKNOWN" && "animate-pulse-ring", className)} />;
}

export function GenericStatusBadge({ status }: { status: string }) {
  const tone: Tone = status === "RESOLVED" ? "green" : status === "OPEN" ? "red" : status === "ACKNOWLEDGED" ? "amber" : "blue";
  return <Badge tone={tone} dot>{humanize(status)}</Badge>;
}
