"use client";

import { useState } from "react";
import Link from "next/link";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Sparkles, X } from "lucide-react";
import { reconciliationApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission, useSession } from "@/lib/auth/session";
import { formatDateTime, formatMoney, humanize, timeAgo } from "@/lib/utils";
import { Badge, Button, ErrorNote, Input, Label, Segmented, Select, Skeleton, Textarea } from "@/components/ui/primitives";
import { EXCEPTION_TYPE_META, ExceptionStatusBadge, ExceptionTypeBadge, RESOLUTION_REASONS, ageLabel } from "./shared";
import type { ReconExceptionDetails, ResolutionReason } from "@/types/api";

type Action = "assign" | "resolve" | "match" | "writeoff";

/** Side drawer with exception evidence, workflow actions and "Explain with AI". */
export function ExceptionDrawer({ reference, onClose }: { reference: string | null; onClose: () => void }) {
  const details = useQuery({
    queryKey: ["recon", "exception", reference],
    queryFn: () => reconciliationApi.exception(reference as string),
    enabled: !!reference,
  });

  return (
    <DialogPrimitive.Root open={!!reference} onOpenChange={(o) => !o && onClose()}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-40 bg-ink-950/50 backdrop-blur-[2px] data-[state=open]:animate-in data-[state=open]:fade-in-0" />
        <DialogPrimitive.Content className="fixed inset-y-0 right-0 z-50 flex w-full max-w-lg flex-col border-l border-jade-500/20 bg-[var(--bg)] shadow-2xl data-[state=open]:animate-in data-[state=open]:slide-in-from-right">
          <div className="flex items-center justify-between border-b border-border px-5 py-3">
            <div>
              <DialogPrimitive.Title className="font-display font-semibold">Reconciliation exception</DialogPrimitive.Title>
              <DialogPrimitive.Description className="font-mono text-xs text-muted">{reference}</DialogPrimitive.Description>
            </div>
            <DialogPrimitive.Close className="cursor-pointer rounded-md p-1.5 text-muted hover:text-foreground" aria-label="Close">
              <X className="h-4 w-4" />
            </DialogPrimitive.Close>
          </div>
          <div className="flex-1 space-y-5 overflow-y-auto px-5 py-4">
            <ErrorNote message={details.isError ? errorMessage(details.error) : null} />
            {details.isPending ? (
              <div className="space-y-3">{Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-10 w-full" />)}</div>
            ) : details.data ? (
              <DrawerBody details={details.data} />
            ) : null}
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

function DrawerBody({ details }: { details: ReconExceptionDetails }) {
  const { exception: e, line, payment } = details;
  const canManage = usePermission("RECON_MANAGE");
  const canAi = usePermission("AI_USE");
  const closed = e.status === "RESOLVED" || e.status === "WRITTEN_OFF";

  return (
    <>
      <section className="space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <ExceptionTypeBadge type={e.type} />
          <ExceptionStatusBadge status={e.status} />
          <Badge tone="gray">{e.providerName}</Badge>
        </div>
        <p className="text-sm text-muted">{e.detail ?? EXCEPTION_TYPE_META[e.type].hint}</p>
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <Field label="Amount at risk" value={<span className="font-semibold text-danger">{formatMoney(e.amountAtRisk, e.currency)}</span>} />
          <Field label="≈ USD" value={formatMoney(e.amountAtRiskUsd, "USD")} />
          {e.expectedAmount !== undefined && <Field label={e.type === "FEE_OVERCHARGE" ? "Expected fee" : "Expected"} value={formatMoney(e.expectedAmount, e.currency)} />}
          {e.actualAmount !== undefined && <Field label={e.type === "FEE_OVERCHARGE" ? "Charged fee" : "Settled"} value={formatMoney(e.actualAmount, e.currency)} />}
          <Field label="Age" value={`${ageLabel(e.ageHours)} · ${timeAgo(e.createdAt)}`} />
          <Field label="Assignee" value={e.assignee ?? "Unassigned"} />
          {e.batchReference && <Field label="Batch" value={<span className="font-mono text-xs">{e.batchReference}</span>} />}
          {e.resolvedBy && <Field label="Closed by" value={`${e.resolvedBy} · ${formatDateTime(e.resolvedAt)}`} />}
        </dl>
        {e.resolutionNote && (
          <div className="rounded-lg border border-border bg-surface-2/50 px-3 py-2 text-sm">
            <p className="text-[11px] uppercase tracking-wider text-muted">{e.resolutionReason ? humanize(e.resolutionReason) : "Resolution"}</p>
            <p className="mt-0.5">{e.resolutionNote}</p>
          </div>
        )}
      </section>

      {line && (
        <section>
          <h3 className="mb-2 text-xs font-medium uppercase tracking-wider text-muted">Settlement line #{line.lineNo}</h3>
          <dl className="grid grid-cols-2 gap-3 rounded-lg border border-border p-3 text-sm">
            <Field label="Reference" value={<span className="font-mono text-xs">{line.providerReference ?? "—"}</span>} />
            <Field label="Type" value={humanize(line.type)} />
            <Field label="Amount" value={formatMoney(line.amount, line.currency)} />
            <Field label="Fee" value={line.fee !== undefined ? formatMoney(line.fee, line.currency) : "—"} />
            <Field label="Settled" value={formatDateTime(line.settledAt)} />
            <Field label="Match" value={`${humanize(line.matchStatus)}${line.matchMethod ? ` · ${humanize(line.matchMethod)}` : ""}`} />
          </dl>
        </section>
      )}

      {payment && (
        <section>
          <h3 className="mb-2 text-xs font-medium uppercase tracking-wider text-muted">Ledger payment</h3>
          <dl className="grid grid-cols-2 gap-3 rounded-lg border border-border p-3 text-sm">
            <Field label="Reference" value={
              <Link href={`/payments/${encodeURIComponent(payment.reference)}`} className="font-mono text-xs text-jade-400 hover:underline">{payment.reference}</Link>
            } />
            <Field label="Status" value={humanize(payment.status)} />
            <Field label="Amount" value={formatMoney(payment.amount, payment.currency)} />
            <Field label="Method" value={humanize(payment.method)} />
            <Field label="Created" value={formatDateTime(payment.createdAt)} />
            <Field label="Provider" value={payment.providerCode ?? "—"} />
          </dl>
        </section>
      )}

      {canAi && <ExplainPanel reference={e.reference} />}

      {canManage && !closed && <Actions details={details} />}
    </>
  );
}

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] uppercase tracking-wider text-muted">{label}</dt>
      <dd className="mt-0.5 truncate">{value}</dd>
    </div>
  );
}

function ExplainPanel({ reference }: { reference: string }) {
  const explain = useMutation({ mutationFn: () => reconciliationApi.explain(reference) });
  return (
    <section className="rounded-xl border border-jade-500/20 bg-jade-500/5 p-4">
      <div className="flex items-center justify-between gap-3">
        <h3 className="flex items-center gap-2 text-sm font-medium"><Sparkles className="h-4 w-4 text-brass-400" /> AI analysis</h3>
        <Button variant="outline" size="sm" onClick={() => explain.mutate()} loading={explain.isPending}>
          {explain.data ? "Explain again" : "Explain with AI"}
        </Button>
      </div>
      <ErrorNote message={explain.isError ? errorMessage(explain.error) : null} />
      {explain.data && (
        <div className="mt-3 space-y-3 text-sm">
          <div>
            <p className="text-[11px] uppercase tracking-wider text-muted">Likely cause</p>
            <p className="mt-0.5">{explain.data.likelyCause}</p>
          </div>
          <div>
            <p className="text-[11px] uppercase tracking-wider text-muted">Suggested action</p>
            <p className="mt-0.5 font-medium">{explain.data.suggestedAction}</p>
          </div>
          {explain.data.checks.length > 0 && (
            <ul className="list-disc space-y-0.5 pl-5 text-muted">{explain.data.checks.map((c) => <li key={c}>{c}</li>)}</ul>
          )}
          <div className="flex gap-2">
            <Badge tone="brass">{explain.data.engine}</Badge>
            <Badge tone="gray">Confidence: {humanize(explain.data.confidence)}</Badge>
          </div>
        </div>
      )}
    </section>
  );
}

function Actions({ details }: { details: ReconExceptionDetails }) {
  const e = details.exception;
  const qc = useQueryClient();
  const me = useSession((s) => s.user?.email);
  const canMatch = e.type !== "MISSING_IN_SETTLEMENT" && !!details.line;
  const options: { value: Action; label: string }[] = [
    { value: "assign", label: "Assign" },
    { value: "resolve", label: "Resolve" },
    ...(canMatch ? [{ value: "match" as Action, label: "Manual match" }] : []),
    { value: "writeoff", label: "Write off" },
  ];
  const [action, setAction] = useState<Action>("assign");
  const [assignee, setAssignee] = useState(e.assignee ?? "");
  const [reason, setReason] = useState<ResolutionReason>("PROVIDER_CORRECTED");
  const [note, setNote] = useState("");
  const [paymentRef, setPaymentRef] = useState(details.payment?.reference ?? "");

  const run = useMutation({
    mutationFn: () => {
      switch (action) {
        case "assign": return reconciliationApi.assign(e.reference, assignee.trim() || undefined);
        case "resolve": return reconciliationApi.resolve(e.reference, reason, note.trim());
        case "match": return reconciliationApi.match(e.reference, paymentRef.trim(), note.trim() || undefined);
        case "writeoff": return reconciliationApi.writeOff(e.reference, note.trim());
      }
    },
    onSuccess: () => {
      setNote("");
      qc.invalidateQueries({ queryKey: ["recon"] });
    },
  });

  const needsNote = action === "resolve" || action === "writeoff";
  const disabled = (needsNote && !note.trim()) || (action === "match" && !paymentRef.trim());

  return (
    <section className="space-y-3 border-t border-border pt-4">
      <h3 className="text-xs font-medium uppercase tracking-wider text-muted">Actions</h3>
      <Segmented<Action> value={action} options={options} onChange={(a) => { setAction(a); run.reset(); }} />
      <form
        className="space-y-3"
        onSubmit={(ev) => {
          ev.preventDefault();
          if (!disabled) run.mutate();
        }}
      >
        {action === "assign" && (
          <div>
            <Label htmlFor="rex-assignee">Assignee e-mail</Label>
            <div className="flex gap-2">
              <Input id="rex-assignee" type="email" value={assignee} onChange={(ev) => setAssignee(ev.target.value)} placeholder="ops@company.com" maxLength={320} />
              {me && <Button type="button" variant="secondary" onClick={() => setAssignee(me)}>Me</Button>}
            </div>
            <p className="mt-1 text-xs text-muted">Assigning an open exception moves it to Investigating.</p>
          </div>
        )}
        {action === "resolve" && (
          <div>
            <Label htmlFor="rex-reason">Reason</Label>
            <Select id="rex-reason" className="w-full" value={reason} onChange={(ev) => setReason(ev.target.value as ResolutionReason)}>
              {RESOLUTION_REASONS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
            </Select>
          </div>
        )}
        {action === "match" && (
          <div>
            <Label htmlFor="rex-payment">Payment reference</Label>
            <Input id="rex-payment" value={paymentRef} onChange={(ev) => setPaymentRef(ev.target.value)} placeholder="PAY-…" className="font-mono" maxLength={100} />
            <p className="mt-1 text-xs text-muted">Links the settlement line to this payment and resolves the exception.</p>
          </div>
        )}
        {action !== "assign" && (
          <div>
            <Label htmlFor="rex-note">{needsNote ? "Note (required)" : "Note"}</Label>
            <Textarea id="rex-note" rows={3} value={note} onChange={(ev) => setNote(ev.target.value)} maxLength={1000}
              placeholder={action === "writeoff" ? "Why is this amount being written off?" : "What was found and done?"} />
          </div>
        )}
        <ErrorNote message={run.isError ? errorMessage(run.error) : null} />
        <div className="flex justify-end">
          <Button type="submit" variant={action === "writeoff" ? "danger" : "primary"} loading={run.isPending} disabled={disabled}>
            {options.find((o) => o.value === action)?.label}
          </Button>
        </div>
      </form>
    </section>
  );
}
