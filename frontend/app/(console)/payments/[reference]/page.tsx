"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft, Bot, CircleDot, CreditCard, Lightbulb, MessageSquare, RefreshCw, Route, ShieldAlert, Workflow,
} from "lucide-react";
import { aiApi, paymentsApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission } from "@/lib/auth/session";
import { cn, flag, formatDateTime, formatMoney, humanize, timeAgo } from "@/lib/utils";
import {
  Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, Label, Select, Skeleton, Textarea,
} from "@/components/ui/primitives";
import { Dialog, DialogClose, DialogContent } from "@/components/ui/dialog";
import { PaymentStatusBadge, RiskBadge } from "@/components/ui/status";
import type { Payment, PaymentDetails, PaymentEvent, PaymentStatus } from "@/types/api";

export default function PaymentDetailPage() {
  const params = useParams<{ reference: string }>();
  const reference = safeDecode(params.reference);
  const details = useQuery({ queryKey: ["payments", "detail", reference], queryFn: () => paymentsApi.get(reference) });

  return (
    <div className="space-y-5">
      <Link href="/payments" className="inline-flex items-center gap-1.5 text-sm text-muted hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> All payments
      </Link>

      {details.isPending && <DetailSkeleton />}
      {details.isError && <ErrorNote message={errorMessage(details.error)} />}
      {details.data && <PaymentView details={details.data} />}
    </div>
  );
}

function safeDecode(value: string) {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function DetailSkeleton() {
  return (
    <div className="space-y-5">
      <Skeleton className="h-36 w-full" />
      <div className="grid gap-5 lg:grid-cols-3">
        <Skeleton className="h-80 lg:col-span-2" />
        <Skeleton className="h-80" />
      </div>
    </div>
  );
}

function PaymentView({ details }: { details: PaymentDetails }) {
  const { payment, timeline } = details;
  return (
    <>
      <SummaryHeader details={details} />
      <div className="grid gap-5 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-2">
          <AiExplanation reference={payment.reference} />
          <TimelineCard events={timeline} />
        </div>
        <RiskCard payment={payment} />
      </div>
    </>
  );
}

// ------------------------------------------------------------------ header
function SummaryHeader({ details }: { details: PaymentDetails }) {
  const { payment: p } = details;
  const canWrite = usePermission("PAYMENT_WRITE");
  return (
    <Card>
      <CardBody className="space-y-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="font-mono text-xs text-muted">{p.reference}{p.merchantReference && ` · ${p.merchantReference}`}</p>
            <div className="mt-1 flex flex-wrap items-center gap-3">
              <h1 className="font-display text-3xl font-semibold tracking-tight tabular-nums">{formatMoney(p.amount, p.currency)}</h1>
              <PaymentStatusBadge status={p.status} />
              {p.stuck && <Badge tone="amber" dot>Stuck</Badge>}
            </div>
            <p className="mt-1 text-xs text-muted">Created {formatDateTime(p.createdAt)} · status changed {timeAgo(p.statusChangedAt)}</p>
          </div>
          {canWrite && <PaymentActions details={details} />}
        </div>

        {p.failureMessage && (
          <ErrorNote message={`${p.failureCode ? `${p.failureCode}: ` : ""}${p.failureMessage}`} />
        )}

        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
          <Fact label="Provider" value={<>{p.providerName} <span className="font-mono text-xs text-muted">{p.providerCode}</span></>} />
          <Fact label="Customer" value={<span className="font-mono text-xs">{p.customerRef}</span>} />
          <Fact
            label="Method"
            value={<span className="inline-flex items-center gap-1.5"><CreditCard className="h-3.5 w-3.5 text-muted" />{humanize(p.method)}{p.cardLast4 && ` •••• ${p.cardLast4}`}</span>}
          />
          <Fact label="Country" value={<>{flag(p.country)} {p.country ?? "—"}</>} />
          <Fact label="Risk" value={<RiskBadge level={p.riskLevel} score={p.riskScore} />} />
          <Fact label="Latency · Retries" value={`${p.latencyMs !== undefined ? `${p.latencyMs} ms` : "—"} · ${p.retryCount}`} />
        </dl>
      </CardBody>
    </Card>
  );
}

function Fact({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-[11px] uppercase tracking-wider text-muted">{label}</dt>
      <dd className="mt-1 text-sm">{value}</dd>
    </div>
  );
}

// ------------------------------------------------------------------ actions
function useInvalidatePayment(reference: string) {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ["payments"] });
    qc.invalidateQueries({ queryKey: ["ai", "explain", reference] });
  };
}

function PaymentActions({ details }: { details: PaymentDetails }) {
  const { payment, allowedTransitions, retryable } = details;
  const invalidate = useInvalidatePayment(payment.reference);
  const retry = useMutation({ mutationFn: () => paymentsApi.retry(payment.reference), onSuccess: invalidate });

  return (
    <div className="flex flex-col items-end gap-2">
      <div className="flex flex-wrap gap-2">
        {retryable && (
          <Button variant="outline" onClick={() => retry.mutate()} loading={retry.isPending}>
            <RefreshCw className="h-4 w-4" /> Retry
          </Button>
        )}
        {allowedTransitions.length > 0 && <ChangeStatusDialog reference={payment.reference} options={allowedTransitions} />}
      </div>
      <ErrorNote message={retry.isError ? errorMessage(retry.error) : null} />
    </div>
  );
}

function ChangeStatusDialog({ reference, options }: { reference: string; options: PaymentStatus[] }) {
  const [open, setOpen] = useState(false);
  const [status, setStatus] = useState<PaymentStatus>(options[0]);
  const [reason, setReason] = useState("");
  const invalidate = useInvalidatePayment(reference);
  const change = useMutation({
    mutationFn: () => paymentsApi.changeStatus(reference, status, reason.trim() || undefined),
    onSuccess: () => {
      invalidate();
      setOpen(false);
      setReason("");
    },
  });

  const show = () => {
    setStatus(options[0]);
    change.reset();
    setOpen(true);
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <Button variant="secondary" onClick={show}>
        <Workflow className="h-4 w-4" /> Change status
      </Button>
      <DialogContent title="Change payment status" description={`Manually transition ${reference}. This is recorded in the audit log.`}>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            change.mutate();
          }}
        >
          <div>
            <Label htmlFor="new-status">New status</Label>
            <Select id="new-status" className="w-full" value={status} onChange={(e) => setStatus(e.target.value as PaymentStatus)}>
              {options.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
            </Select>
          </div>
          <div>
            <Label htmlFor="reason">Reason</Label>
            <Textarea id="reason" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Why is this change needed?" />
          </div>
          <ErrorNote message={change.isError ? errorMessage(change.error) : null} />
          <div className="flex justify-end gap-2">
            <DialogClose asChild>
              <Button type="button" variant="ghost">Cancel</Button>
            </DialogClose>
            <Button type="submit" loading={change.isPending}>Confirm change</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ------------------------------------------------------------------ AI explanation
function AiExplanation({ reference }: { reference: string }) {
  const insight = useQuery({
    queryKey: ["ai", "explain", reference],
    queryFn: () => aiApi.explainPayment(reference),
    staleTime: 5 * 60_000,
  });
  const d = insight.data;

  return (
    <Card className="border-jade-500/20">
      <CardHeader
        title="AI Explanation"
        subtitle="What happened, why, and what to do next"
        icon={<Bot className="h-4 w-4" />}
        actions={d && <Badge tone="jade">{d.engine}</Badge>}
      />
      <CardBody className="space-y-4">
        {insight.isPending && (
          <div className="space-y-2" aria-busy="true" aria-label="Generating explanation">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-11/12" />
            <Skeleton className="h-4 w-3/4" />
            <div className="grid gap-3 pt-2 sm:grid-cols-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          </div>
        )}
        {insight.isError && <ErrorNote message={errorMessage(insight.error)} />}
        {d && (
          <>
            <p className="text-sm leading-relaxed">{d.narrative}</p>
            {d.failureCategory && (
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <Badge tone="red">{humanize(d.failureCategory)}</Badge>
                {d.failureMeaning && <span className="text-muted">{d.failureMeaning}</span>}
              </div>
            )}
            <div className="grid gap-3 sm:grid-cols-2">
              <Advice
                icon={<RefreshCw className="h-4 w-4" />}
                title={d.retryRecommended ? "Retry recommended" : "Do not retry"}
                tone={d.retryRecommended ? "good" : "warn"}
              >
                {d.retryAdvice}
              </Advice>
              {d.suggestedProvider && (
                <Advice icon={<Route className="h-4 w-4" />} title="Suggested provider">
                  <span className="font-mono">{d.suggestedProvider}</span>
                </Advice>
              )}
              {d.operatorAction && (
                <Advice icon={<Lightbulb className="h-4 w-4" />} title="Operator action">{d.operatorAction}</Advice>
              )}
              {d.customerMessage && (
                <Advice icon={<MessageSquare className="h-4 w-4" />} title="Customer message">“{d.customerMessage}”</Advice>
              )}
            </div>
            <p className="text-[11px] text-muted">Generated {timeAgo(d.generatedAt)}</p>
          </>
        )}
      </CardBody>
    </Card>
  );
}

function Advice({ icon, title, tone, children }: { icon: React.ReactNode; title: string; tone?: "good" | "warn"; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-border bg-surface-2/50 p-3">
      <p className={cn("mb-1 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider", tone === "warn" ? "text-warning" : "text-jade-400")}>
        {icon}{title}
      </p>
      <p className="text-sm">{children}</p>
    </div>
  );
}

// ------------------------------------------------------------------ timeline
function TimelineCard({ events }: { events: PaymentEvent[] }) {
  return (
    <Card>
      <CardHeader title="Lifecycle" subtitle={`${events.length} event${events.length === 1 ? "" : "s"}`} icon={<CircleDot className="h-4 w-4" />} />
      <CardBody>
        {events.length === 0 ? (
          <Empty title="No lifecycle events recorded" />
        ) : (
          <ol className="relative ml-2 border-l border-border">
            {events.map((e, i) => {
              const failed = e.toStatus === "FAILED" || e.toStatus === "RETRY_FAILED" || e.toStatus === "CHARGEBACK";
              const latest = i === events.length - 1;
              return (
                <li key={`${e.occurredAt}-${i}`} className="relative pb-5 pl-6 last:pb-0">
                  <span
                    className={cn(
                      "absolute -left-[7px] top-1 h-3.5 w-3.5 rounded-full border-2 border-[var(--bg)]",
                      failed ? "bg-danger" : latest ? "bg-jade-500 ring-4 ring-jade-500/20" : "bg-ink-300",
                    )}
                  />
                  <div className="flex flex-wrap items-center gap-2">
                    {e.fromStatus && <><PaymentStatusBadge status={e.fromStatus} /><span className="text-muted">→</span></>}
                    <PaymentStatusBadge status={e.toStatus} />
                    {e.errorCode && <Badge tone="red" className="font-mono">{e.errorCode}</Badge>}
                  </div>
                  {e.reason && <p className="mt-1 text-sm">{e.reason}</p>}
                  <p className="mt-0.5 text-xs text-muted">
                    {formatDateTime(e.occurredAt)}{e.actor && ` · by ${e.actor}`}
                  </p>
                </li>
              );
            })}
          </ol>
        )}
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ risk
function RiskCard({ payment }: { payment: Payment }) {
  const pct = Math.max(0, Math.min(100, payment.riskScore));
  const color = payment.riskLevel === "LOW" ? "bg-jade-500" : payment.riskLevel === "MEDIUM" ? "bg-warning" : "bg-danger";
  return (
    <Card className="h-fit">
      <CardHeader title="Risk assessment" icon={<ShieldAlert className="h-4 w-4" />} actions={<RiskBadge level={payment.riskLevel} />} />
      <CardBody className="space-y-4">
        <div>
          <div className="mb-1 flex justify-between text-xs text-muted">
            <span>Risk score</span>
            <span className="tabular-nums text-foreground">{payment.riskScore} / 100</span>
          </div>
          <div className="h-2 rounded-full bg-surface-2" role="meter" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label="Risk score">
            <div className={cn("h-2 rounded-full", color)} style={{ width: `${pct}%` }} />
          </div>
        </div>
        {payment.riskFactors.length === 0 ? (
          <p className="text-sm text-muted">No risk factors flagged.</p>
        ) : (
          <ul className="space-y-2">
            {payment.riskFactors.map((f) => (
              <li key={f} className="flex items-start gap-2 text-sm">
                <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0 text-warning" />
                <span>{f}</span>
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </Card>
  );
}
