import { Badge, type Tone } from "@/components/ui/primitives";
import type { BatchStatus, ReconExceptionStatus, ReconExceptionType, ResolutionReason } from "@/types/api";

export const EXCEPTION_TYPE_META: Record<ReconExceptionType, { label: string; tone: Tone; hint: string }> = {
  AMOUNT_MISMATCH: { label: "Amount mismatch", tone: "amber", hint: "Settled amount differs from the ledger" },
  MISSING_IN_SETTLEMENT: { label: "Missing in settlement", tone: "red", hint: "Payment succeeded but was never settled" },
  MISSING_IN_LEDGER: { label: "Missing in ledger", tone: "blue", hint: "Settlement line with no matching payment" },
  DUPLICATE: { label: "Duplicate", tone: "red", hint: "Payment settled more than once" },
  FEE_OVERCHARGE: { label: "Fee overcharge", tone: "brass", hint: "Fee above the expected schedule" },
};
export const EXCEPTION_TYPES = Object.keys(EXCEPTION_TYPE_META) as ReconExceptionType[];

export const EXCEPTION_STATUSES: ReconExceptionStatus[] = ["OPEN", "INVESTIGATING", "RESOLVED", "WRITTEN_OFF"];
const STATUS_TONE: Record<ReconExceptionStatus, Tone> = { OPEN: "red", INVESTIGATING: "amber", RESOLVED: "jade", WRITTEN_OFF: "ink" };
const BATCH_TONE: Record<BatchStatus, Tone> = { RECEIVED: "blue", MATCHING: "amber", COMPLETED: "jade", FAILED: "red" };

export const RESOLUTION_REASONS: { value: ResolutionReason; label: string }[] = [
  { value: "PROVIDER_CORRECTED", label: "Provider corrected" },
  { value: "LEDGER_CORRECTED", label: "Ledger corrected" },
  { value: "TIMING_DIFFERENCE", label: "Timing difference" },
  { value: "SETTLED_LATER", label: "Settled later" },
  { value: "REFUND_RECOVERED", label: "Refund / amount recovered" },
  { value: "FALSE_POSITIVE", label: "False positive" },
  { value: "OTHER", label: "Other" },
];

export function ExceptionTypeBadge({ type }: { type: ReconExceptionType }) {
  const meta = EXCEPTION_TYPE_META[type];
  return <Badge tone={meta.tone}>{meta.label}</Badge>;
}

export function ExceptionStatusBadge({ status }: { status: ReconExceptionStatus }) {
  return <Badge tone={STATUS_TONE[status]} dot>{status === "WRITTEN_OFF" ? "Written off" : status.charAt(0) + status.slice(1).toLowerCase()}</Badge>;
}

export function BatchStatusBadge({ status }: { status: BatchStatus }) {
  return <Badge tone={BATCH_TONE[status]} dot>{status.charAt(0) + status.slice(1).toLowerCase()}</Badge>;
}

export function isActive(status: BatchStatus) {
  return status === "RECEIVED" || status === "MATCHING";
}

export function ageLabel(hours: number) {
  if (hours < 1) return "< 1h";
  if (hours < 48) return `${hours}h`;
  return `${Math.floor(hours / 24)}d`;
}
