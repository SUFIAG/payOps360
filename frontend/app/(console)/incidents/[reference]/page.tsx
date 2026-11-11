"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft, BellRing, Bot, Brain, Check, CircleCheck, CircleDot, FileText, Flame, Link2, Loader2, MessageSquare, RefreshCw, Search, Server,
  ShieldAlert, Sparkles, Timer, TimerOff, TrendingUp,
} from "lucide-react";
import { aiApi, incidentsApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission } from "@/lib/auth/session";
import { cn, formatDateTime, formatMoney, formatNumber, humanize, timeAgo } from "@/lib/utils";
import {
  Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, Label, Segmented, Select, Skeleton, Textarea,
} from "@/components/ui/primitives";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { GenericStatusBadge, SeverityBadge } from "@/components/ui/status";
import { EscalationBadge, SlaChip } from "@/components/incidents/sla";
import { PostmortemSection } from "@/components/incidents/postmortem-editor";
import type { AiAnalysis, Incident, Severity, TimelineEntry } from "@/types/api";

const SEVERITY_ORDER: Severity[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

export default function IncidentDetailPage() {
  const params = useParams<{ reference: string }>();
  const reference = decodeURIComponent(params.reference);

  const [tab, setTab] = useState<"response" | "postmortem">("response");
  const details = useQuery({
    queryKey: ["incidents", "detail", reference],
    queryFn: () => incidentsApi.get(reference),
    refetchInterval: 15_000,
  });

  if (details.isLoading) return <DetailSkeleton />;
  if (details.error || !details.data) {
    return (
      <div className="space-y-4">
        <BackLink />
        <ErrorNote message={details.error ? errorMessage(details.error) : "Incident not found"} />
      </div>
    );
  }

  const { incident, timeline } = details.data;
  const resolved = incident.status === "RESOLVED";
  const showPostmortem = resolved && tab === "postmortem";
  return (
    <div className="space-y-6">
      <BackLink />
      <IncidentHeader incident={incident} />
      {resolved && (
        <Segmented
          value={tab}
          onChange={setTab}
          options={[{ value: "response", label: "Response & analysis" }, { value: "postmortem", label: "Postmortem" }]}
        />
      )}
      {showPostmortem ? (
        <PostmortemSection reference={incident.reference} />
      ) : (
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
          <div className="space-y-6">
            <AnalysisPanel incident={incident} />
            <TimelineCard reference={incident.reference} timeline={timeline} resolved={resolved} />
          </div>
          <ActionsPanel incident={incident} />
        </div>
      )}
    </div>
  );
}

function BackLink() {
  return (
    <Link href="/incidents" className="inline-flex items-center gap-1.5 text-xs text-muted hover:text-jade-400">
      <ArrowLeft className="h-3.5 w-3.5" /> All incidents
    </Link>
  );
}

function DetailSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-4 w-24" />
      <Skeleton className="h-36 w-full rounded-2xl" />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
        <Skeleton className="h-80 w-full rounded-2xl" />
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
    </div>
  );
}

/** Invalidate everything incident-related after a mutation. */
function useInvalidateIncidents() {
  const queryClient = useQueryClient();
  return () => Promise.all([
    queryClient.invalidateQueries({ queryKey: ["incidents"] }),
    queryClient.invalidateQueries({ queryKey: ["alerts"] }),
  ]);
}

// ---------------------------------------------------------------- header

function IncidentHeader({ incident }: { incident: Incident }) {
  const stats = [
    { label: "Affected payments", value: formatNumber(incident.affectedPayments), icon: <Flame className="h-4 w-4" /> },
    { label: "Amount at risk", value: formatMoney(incident.affectedAmount, "USD"), icon: <TrendingUp className="h-4 w-4" /> },
    { label: "Time to acknowledge", value: incident.minutesToAcknowledge != null ? `${formatNumber(incident.minutesToAcknowledge)} min` : "—", icon: <Timer className="h-4 w-4" /> },
    { label: "Time to resolve", value: incident.minutesToResolve != null ? `${formatNumber(incident.minutesToResolve)} min` : "—", icon: <CircleCheck className="h-4 w-4" /> },
  ];

  return (
    <Card className={cn("relative overflow-hidden", incident.severity === "CRITICAL" && incident.status !== "RESOLVED" && "border-danger/40")}>
      <CardBody>
        <div className="flex flex-wrap items-center gap-2">
          <SeverityBadge severity={incident.severity} />
          <GenericStatusBadge status={incident.status} />
          {incident.providerCode && <Badge tone="ink"><Server className="h-3 w-3" />{incident.providerCode}</Badge>}
          <SlaChip incident={incident} kind="ack" />
          <SlaChip incident={incident} kind="resolve" />
          <EscalationBadge level={incident.escalationLevel} />
          <span className="font-mono text-xs text-muted">{incident.reference}</span>
        </div>
        <h1 className="mt-3 font-display text-2xl font-semibold tracking-tight">{incident.title}</h1>
        {incident.description && <p className="mt-2 max-w-3xl text-sm text-muted">{incident.description}</p>}
        <p className="mt-2 text-xs text-muted">
          Opened {timeAgo(incident.openedAt)} · {formatDateTime(incident.openedAt)}
          {incident.acknowledgedBy && <> · acknowledged by {incident.acknowledgedBy}</>}
          {incident.resolvedBy && <> · resolved by {incident.resolvedBy} {timeAgo(incident.resolvedAt)}</>}
        </p>
        {incident.resolutionNote && (
          <div className="mt-3 rounded-lg border border-jade-500/30 bg-jade-500/5 px-3 py-2 text-sm">
            <span className="text-xs uppercase tracking-wider text-jade-400">Resolution</span>
            <p className="mt-0.5">{incident.resolutionNote}</p>
          </div>
        )}

        <div className="mt-5 grid grid-cols-2 gap-3 lg:grid-cols-4">
          {stats.map((s) => (
            <div key={s.label} className="rounded-xl border border-border bg-surface-2/50 p-3">
              <div className="flex items-center gap-1.5 text-[11px] uppercase tracking-wider text-muted">
                <span className="text-jade-400">{s.icon}</span>{s.label}
              </div>
              <p className="mt-1 font-display text-lg font-semibold">{s.value}</p>
            </div>
          ))}
        </div>
      </CardBody>
    </Card>
  );
}

// ---------------------------------------------------------------- AI analysis

const ANALYSIS_STEPS = [
  "Collecting incident evidence",
  "Correlating alerts & provider health",
  "Analysing failure patterns",
  "Evaluating routing alternatives",
  "Drafting root-cause hypothesis",
];

function AnalysisPanel({ incident }: { incident: Incident }) {
  const canUseAi = usePermission("AI_USE");
  const invalidate = useInvalidateIncidents();
  const investigate = useMutation({
    mutationFn: () => aiApi.investigate(incident.reference),
    onSuccess: () => invalidate(),
  });
  const analysis = incident.aiAnalysis;

  return (
    <Card className="relative overflow-hidden">
      <div className="pointer-events-none absolute -right-24 -top-24 h-56 w-56 rounded-full bg-jade-500/10 blur-3xl" aria-hidden />
      <CardHeader
        icon={<Brain className="h-4 w-4" />}
        title="AI Root-Cause Analysis"
        subtitle={analysis ? `${analysis.engine} · generated ${timeAgo(analysis.generatedAt)}` : "Correlates evidence across payments, providers and alerts"}
        actions={canUseAi && (
          <Button
            size="sm"
            variant={analysis ? "outline" : "primary"}
            loading={investigate.isPending}
            onClick={() => investigate.mutate()}
          >
            {!investigate.isPending && (analysis ? <RefreshCw className="h-4 w-4" /> : <Sparkles className="h-4 w-4" />)}
            {analysis ? "Re-run analysis" : "Investigate with AI"}
          </Button>
        )}
      />
      <CardBody>
        <ErrorNote message={investigate.error ? errorMessage(investigate.error) : null} />
        {investigate.isPending ? (
          <AnalysingSteps />
        ) : analysis ? (
          <AnalysisView analysis={analysis} />
        ) : (
          <Empty
            icon={<Bot className="h-8 w-8" />}
            title="No analysis yet"
            hint={canUseAi ? "Run an AI investigation to get a root-cause hypothesis and remediation plan." : "An operator with AI access can run an investigation."}
          />
        )}
      </CardBody>
    </Card>
  );
}

function AnalysingSteps() {
  const [step, setStep] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setStep((s) => Math.min(s + 1, ANALYSIS_STEPS.length - 1)), 1400);
    return () => clearInterval(id);
  }, []);

  return (
    <div className="space-y-3 py-2" aria-live="polite">
      <p className="flex items-center gap-2 text-sm text-jade-400">
        <Sparkles className="h-4 w-4 animate-pulse" /> Analysing evidence…
      </p>
      <ol className="space-y-2">
        {ANALYSIS_STEPS.map((label, i) => (
          <li
            key={label}
            className={cn("flex items-center gap-2.5 text-sm transition-opacity", i > step ? "opacity-35" : "opacity-100")}
          >
            {i < step ? (
              <Check className="h-4 w-4 text-jade-400" />
            ) : i === step ? (
              <Loader2 className="h-4 w-4 animate-spin text-jade-400" />
            ) : (
              <CircleDot className="h-4 w-4 text-muted" />
            )}
            {label}
          </li>
        ))}
      </ol>
      <div className="h-1 overflow-hidden rounded-full bg-surface-2">
        <div
          className="h-full rounded-full bg-jade-500 transition-all duration-700"
          style={{ width: `${((step + 1) / ANALYSIS_STEPS.length) * 90}%` }}
        />
      </div>
    </div>
  );
}

function AnalysisView({ analysis }: { analysis: AiAnalysis }) {
  // Backend reports confidence as 0..1; tolerate a 0..100 scale too.
  const confidence = Math.round(analysis.confidence <= 1 ? analysis.confidence * 100 : analysis.confidence);
  const tone = confidence >= 75 ? "bg-jade-500" : confidence >= 50 ? "bg-warning" : "bg-danger";

  return (
    <div className="space-y-5">
      <div>
        <h4 className="text-xs uppercase tracking-wider text-muted">Summary</h4>
        <p className="mt-1 text-sm leading-relaxed">{analysis.summary}</p>
      </div>
      <div className="rounded-xl border border-jade-500/30 bg-jade-500/5 p-4">
        <h4 className="flex items-center gap-1.5 text-xs uppercase tracking-wider text-jade-400">
          <ShieldAlert className="h-3.5 w-3.5" /> Probable root cause
        </h4>
        <p className="mt-1 text-sm leading-relaxed">{analysis.rootCause}</p>
      </div>
      {analysis.recommendations.length > 0 && (
        <div>
          <h4 className="text-xs uppercase tracking-wider text-muted">Recommended actions</h4>
          <ol className="mt-2 space-y-2">
            {analysis.recommendations.map((r, i) => (
              <li key={i} className="flex gap-3 text-sm">
                <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-jade-500/15 font-mono text-[11px] text-jade-400">
                  {i + 1}
                </span>
                <span className="leading-relaxed">{r}</span>
              </li>
            ))}
          </ol>
        </div>
      )}
      <div className="flex flex-wrap items-center gap-4 border-t border-border pt-4">
        <div className="min-w-48 flex-1">
          <div className="flex items-center justify-between text-xs">
            <span className="uppercase tracking-wider text-muted">Confidence</span>
            <span className="font-mono">{confidence}%</span>
          </div>
          <div
            className="mt-1.5 h-2 overflow-hidden rounded-full bg-surface-2"
            role="meter"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={confidence}
            aria-label="Analysis confidence"
          >
            <div className={cn("h-full rounded-full transition-all", tone)} style={{ width: `${confidence}%` }} />
          </div>
        </div>
        <div className="text-xs text-muted">
          <Badge tone="brass"><Bot className="h-3 w-3" />{analysis.engine}</Badge>
          <span className="ml-2">{formatDateTime(analysis.generatedAt)}</span>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- timeline

const kindIcon: Record<string, { icon: React.ReactNode; className: string }> = {
  OPENED: { icon: <Flame className="h-3.5 w-3.5" />, className: "bg-danger/15 text-danger" },
  ALERT_CORRELATED: { icon: <Link2 className="h-3.5 w-3.5" />, className: "bg-info/15 text-info" },
  ACKNOWLEDGED: { icon: <Check className="h-3.5 w-3.5" />, className: "bg-warning/15 text-warning" },
  INVESTIGATING: { icon: <Search className="h-3.5 w-3.5" />, className: "bg-info/15 text-info" },
  ESCALATED: { icon: <TrendingUp className="h-3.5 w-3.5" />, className: "bg-danger/15 text-danger" },
  RESOLVED: { icon: <CircleCheck className="h-3.5 w-3.5" />, className: "bg-jade-500/15 text-jade-400" },
  NOTE: { icon: <MessageSquare className="h-3.5 w-3.5" />, className: "bg-ink-400/15 text-muted" },
  AI_ANALYSIS: { icon: <Sparkles className="h-3.5 w-3.5" />, className: "bg-jade-500/15 text-jade-400" },
  SLA_BREACHED: { icon: <TimerOff className="h-3.5 w-3.5" />, className: "bg-danger/15 text-danger" },
  ESCALATION_NOTIFIED: { icon: <BellRing className="h-3.5 w-3.5" />, className: "bg-warning/15 text-warning" },
  POSTMORTEM_PUBLISHED: { icon: <FileText className="h-3.5 w-3.5" />, className: "bg-jade-500/15 text-jade-400" },
};
const fallbackKind = { icon: <CircleDot className="h-3.5 w-3.5" />, className: "bg-surface-2 text-muted" };

function TimelineCard({ reference, timeline, resolved }: { reference: string; timeline: TimelineEntry[]; resolved: boolean }) {
  const canManage = usePermission("INCIDENT_MANAGE");
  const invalidate = useInvalidateIncidents();
  const [note, setNote] = useState("");
  const addNote = useMutation({
    mutationFn: (text: string) => incidentsApi.addNote(reference, text),
    onSuccess: async () => {
      setNote("");
      await invalidate();
    },
  });

  const entries = useMemo(
    () => [...timeline].sort((a, b) => new Date(a.occurredAt).getTime() - new Date(b.occurredAt).getTime()),
    [timeline],
  );

  return (
    <Card>
      <CardHeader icon={<Timer className="h-4 w-4" />} title="Timeline" subtitle={`${entries.length} events`} />
      <CardBody>
        {entries.length === 0 ? (
          <Empty title="No events recorded" />
        ) : (
          <ol className="relative space-y-5 before:absolute before:bottom-2 before:left-[13px] before:top-2 before:w-px before:bg-border">
            {entries.map((e, i) => {
              const k = kindIcon[e.kind] ?? fallbackKind;
              return (
                <li key={`${e.occurredAt}-${i}`} className="relative flex gap-3">
                  <span className={cn("relative z-10 flex h-7 w-7 shrink-0 items-center justify-center rounded-full ring-4 ring-[var(--bg)]", k.className)}>
                    {k.icon}
                  </span>
                  <div className="min-w-0 flex-1 pt-0.5">
                    <div className="flex flex-wrap items-baseline justify-between gap-x-3">
                      <span className="text-xs font-medium uppercase tracking-wider text-muted">{humanize(e.kind)}</span>
                      <time className="text-[11px] text-muted" dateTime={e.occurredAt} title={formatDateTime(e.occurredAt)}>
                        {timeAgo(e.occurredAt)}
                      </time>
                    </div>
                    <p className="mt-0.5 whitespace-pre-wrap break-words text-sm">{e.message}</p>
                    {e.actor && <p className="mt-0.5 text-[11px] text-muted">by {e.actor}</p>}
                  </div>
                </li>
              );
            })}
          </ol>
        )}

        {canManage && (
          <form
            className="mt-6 space-y-2 border-t border-border pt-4"
            onSubmit={(e) => {
              e.preventDefault();
              if (note.trim()) addNote.mutate(note.trim());
            }}
          >
            <Label htmlFor="incident-note">Add note{resolved && " (post-incident)"}</Label>
            <Textarea
              id="incident-note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Share findings, mitigation steps or customer comms…"
            />
            <ErrorNote message={addNote.error ? errorMessage(addNote.error) : null} />
            <div className="flex justify-end">
              <Button type="submit" size="sm" variant="secondary" loading={addNote.isPending} disabled={!note.trim()}>
                <MessageSquare className="h-4 w-4" /> Add note
              </Button>
            </div>
          </form>
        )}
      </CardBody>
    </Card>
  );
}

// ---------------------------------------------------------------- actions

function ActionsPanel({ incident }: { incident: Incident }) {
  const canManage = usePermission("INCIDENT_MANAGE");
  const invalidate = useInvalidateIncidents();
  const higher = SEVERITY_ORDER.slice(SEVERITY_ORDER.indexOf(incident.severity) + 1);
  const [escalateTo, setEscalateTo] = useState<Severity | "">("");
  const [resolveOpen, setResolveOpen] = useState(false);

  const transition = useMutation({
    mutationFn: (action: "acknowledge" | "investigate") => incidentsApi.transition(incident.reference, action),
    onSuccess: () => invalidate(),
  });
  const escalate = useMutation({
    mutationFn: (severity: Severity) => incidentsApi.escalate(incident.reference, severity),
    onSuccess: async () => {
      setEscalateTo("");
      await invalidate();
    },
  });

  const resolved = incident.status === "RESOLVED";
  const error = transition.error ?? escalate.error;

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader icon={<ShieldAlert className="h-4 w-4" />} title="Response actions" subtitle={`Status: ${humanize(incident.status)}`} />
        <CardBody className="space-y-4">
          {!canManage ? (
            <p className="text-sm text-muted">You have read-only access to this incident.</p>
          ) : resolved ? (
            <Empty icon={<CircleCheck className="h-7 w-7 text-jade-400" />} title="Incident resolved" hint="Notes can still be added for the post-incident review." className="py-4" />
          ) : (
            <>
              <div className="grid gap-2">
                {incident.status === "OPEN" && (
                  <Button
                    variant="secondary"
                    className="justify-start"
                    loading={transition.isPending && transition.variables === "acknowledge"}
                    disabled={transition.isPending}
                    onClick={() => transition.mutate("acknowledge")}
                  >
                    <Check className="h-4 w-4" /> Acknowledge
                  </Button>
                )}
                {(incident.status === "OPEN" || incident.status === "ACKNOWLEDGED") && (
                  <Button
                    variant="secondary"
                    className="justify-start"
                    loading={transition.isPending && transition.variables === "investigate"}
                    disabled={transition.isPending}
                    onClick={() => transition.mutate("investigate")}
                  >
                    <Search className="h-4 w-4" /> Start investigation
                  </Button>
                )}
                <Button className="justify-start" onClick={() => setResolveOpen(true)}>
                  <CircleCheck className="h-4 w-4" /> Resolve incident
                </Button>
              </div>

              <div className="border-t border-border pt-4">
                <Label htmlFor="escalate">Escalate severity</Label>
                {higher.length === 0 ? (
                  <p className="text-xs text-muted">Already at the highest severity.</p>
                ) : (
                  <div className="flex gap-2">
                    <Select
                      id="escalate"
                      className="flex-1"
                      value={escalateTo}
                      onChange={(e) => setEscalateTo(e.target.value as Severity | "")}
                    >
                      <option value="">Select severity…</option>
                      {higher.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
                    </Select>
                    <Button
                      variant="danger"
                      disabled={!escalateTo}
                      loading={escalate.isPending}
                      onClick={() => escalateTo && escalate.mutate(escalateTo)}
                    >
                      <TrendingUp className="h-4 w-4" /> Escalate
                    </Button>
                  </div>
                )}
              </div>
              <ErrorNote message={error ? errorMessage(error) : null} />
            </>
          )}
        </CardBody>
      </Card>

      <ResolveDialog incident={incident} open={resolveOpen} onOpenChange={setResolveOpen} onResolved={invalidate} />
    </div>
  );
}

function ResolveDialog({ incident, open, onOpenChange, onResolved }: {
  incident: Incident; open: boolean; onOpenChange: (open: boolean) => void; onResolved: () => Promise<unknown>;
}) {
  const [note, setNote] = useState("");
  const resolve = useMutation({
    mutationFn: (text: string) => incidentsApi.resolve(incident.reference, text),
    onSuccess: async () => {
      await onResolved();
      setOpen(false);
    },
  });

  function setOpen(next: boolean) {
    if (!next) {
      setNote("");
      resolve.reset();
    }
    onOpenChange(next);
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent title="Resolve incident" description={`${incident.reference} · ${incident.title}`}>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (note.trim()) resolve.mutate(note.trim());
          }}
        >
          <div>
            <Label htmlFor="incident-resolution">Resolution note</Label>
            <Textarea
              id="incident-resolution"
              required
              autoFocus
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Root cause, fix applied, and any follow-up actions."
            />
          </div>
          <ErrorNote message={resolve.error ? errorMessage(resolve.error) : null} />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button type="submit" loading={resolve.isPending} disabled={!note.trim()}>Resolve incident</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
