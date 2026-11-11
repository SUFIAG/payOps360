"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  BellRing, Check, ChevronLeft, ChevronRight, CircleCheck, Clock, CreditCard, Eye, Gauge, Server, Siren, Sparkles,
} from "lucide-react";
import { alertsApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission } from "@/lib/auth/session";
import { cn, formatDateTime, formatNumber, humanize, timeAgo } from "@/lib/utils";
import {
  Badge, Button, Card, CardBody, Empty, ErrorNote, Label, PageHeader, Segmented, Select, Skeleton, Textarea,
} from "@/components/ui/primitives";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { GenericStatusBadge, SeverityBadge } from "@/components/ui/status";
import type { Alert, AlertStatus, Severity } from "@/types/api";

type StatusFilter = AlertStatus | "ALL";
type SeverityFilter = Severity | "ALL";

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "OPEN", label: "Open" },
  { value: "ACKNOWLEDGED", label: "Acknowledged" },
  { value: "RESOLVED", label: "Resolved" },
];
const SEVERITIES: Severity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];
const PAGE_SIZE = 20;

const severityBar: Record<Severity, string> = {
  CRITICAL: "bg-danger", HIGH: "bg-danger/70", MEDIUM: "bg-warning", LOW: "bg-ink-400",
};

export default function AlertsPage() {
  const [status, setStatus] = useState<StatusFilter>("OPEN");
  const [severity, setSeverity] = useState<SeverityFilter>("ALL");
  const [page, setPage] = useState(0);
  const [resolving, setResolving] = useState<Alert | null>(null);

  const summary = useQuery({ queryKey: ["alerts", "summary"], queryFn: alertsApi.summary, refetchInterval: 15_000 });
  const list = useQuery({
    queryKey: ["alerts", "list", status, severity, page],
    queryFn: () => alertsApi.list(status === "ALL" ? undefined : status, severity === "ALL" ? undefined : severity, page, PAGE_SIZE),
    refetchInterval: 15_000,
    placeholderData: (prev) => prev,
  });

  const resetPage = () => setPage(0);

  return (
    <div>
      <PageHeader title="Alerts" subtitle="Rule-based and AI-detected signals across providers and payments." />

      <SummaryTiles
        loading={summary.isLoading}
        open={summary.data?.open}
        acknowledged={summary.data?.acknowledged}
        resolved={summary.data?.resolvedLast24h}
        bySeverity={summary.data?.openBySeverity}
      />

      <Card className="mt-6">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4">
          <Segmented value={status} options={STATUS_OPTIONS} onChange={(v) => { setStatus(v); resetPage(); }} />
          <div className="flex items-center gap-2">
            <Label htmlFor="severity" className="mb-0">Severity</Label>
            <Select
              id="severity"
              value={severity}
              onChange={(e) => { setSeverity(e.target.value as SeverityFilter); resetPage(); }}
              className="h-9"
            >
              <option value="ALL">All severities</option>
              {SEVERITIES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
            </Select>
          </div>
        </div>

        <CardBody className="space-y-3">
          {list.error && <ErrorNote message={errorMessage(list.error)} />}
          {list.isLoading ? (
            Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-28 w-full" />)
          ) : list.data && list.data.items.length > 0 ? (
            list.data.items.map((alert) => <AlertRow key={alert.reference} alert={alert} onResolve={() => setResolving(alert)} />)
          ) : (
            !list.error && (
              <Empty
                icon={<CircleCheck className="h-8 w-8" />}
                title="No alerts match these filters"
                hint="All quiet. New signals will appear here automatically."
              />
            )
          )}
        </CardBody>

        {list.data && list.data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-border px-5 py-3 text-xs text-muted">
            <span>
              Page {list.data.page + 1} of {list.data.totalPages} · {formatNumber(list.data.totalItems)} alerts
            </span>
            <div className="flex gap-2">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                <ChevronLeft className="h-4 w-4" /> Prev
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={page + 1 >= list.data.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </Card>

      <ResolveDialog alert={resolving} onClose={() => setResolving(null)} />
    </div>
  );
}

// ---------------------------------------------------------------- summary

function SummaryTiles({ loading, open, acknowledged, resolved, bySeverity }: {
  loading: boolean; open?: number; acknowledged?: number; resolved?: number; bySeverity?: Record<Severity, number>;
}) {
  const tiles = [
    { label: "Open", value: open, icon: <Siren className="h-4 w-4" />, accent: "text-danger" },
    { label: "Acknowledged", value: acknowledged, icon: <Eye className="h-4 w-4" />, accent: "text-warning" },
    { label: "Resolved (24h)", value: resolved, icon: <CircleCheck className="h-4 w-4" />, accent: "text-jade-400" },
  ];
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {tiles.map((t) => (
        <Card key={t.label} className="p-5">
          <div className="flex items-center justify-between text-xs uppercase tracking-wider text-muted">
            {t.label}
            <span className={t.accent}>{t.icon}</span>
          </div>
          {loading ? (
            <Skeleton className="mt-3 h-8 w-16" />
          ) : (
            <p className="mt-2 font-display text-3xl font-semibold">{formatNumber(t.value ?? 0)}</p>
          )}
        </Card>
      ))}
      <Card className="p-5">
        <div className="text-xs uppercase tracking-wider text-muted">Open by severity</div>
        {loading ? (
          <Skeleton className="mt-3 h-8 w-full" />
        ) : (
          <div className="mt-3 grid grid-cols-4 gap-2">
            {SEVERITIES.map((s) => (
              <div key={s} className="text-center">
                <div className={cn("mx-auto mb-1 h-1 w-full rounded-full", severityBar[s])} />
                <p className="font-display text-lg font-semibold">{bySeverity?.[s] ?? 0}</p>
                <p className="text-[10px] uppercase tracking-wider text-muted">{s}</p>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}

// ---------------------------------------------------------------- row

function AlertRow({ alert, onResolve }: { alert: Alert; onResolve: () => void }) {
  const canManage = usePermission("ALERT_MANAGE");
  const queryClient = useQueryClient();
  const acknowledge = useMutation({
    mutationFn: () => alertsApi.acknowledge(alert.reference),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["alerts"] }),
  });

  return (
    <div className="relative overflow-hidden rounded-xl border border-border bg-surface/60 p-4 pl-5 transition-colors hover:border-jade-500/30">
      <span className={cn("absolute inset-y-0 left-0 w-1", severityBar[alert.severity])} aria-hidden />
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <SeverityBadge severity={alert.severity} />
            <GenericStatusBadge status={alert.status} />
            {alert.source === "AI" ? (
              <Badge tone="brass"><Sparkles className="h-3 w-3" /> AI detected</Badge>
            ) : (
              <Badge tone="ink">Rule</Badge>
            )}
            <span className="font-mono text-[11px] text-muted">{alert.reference}</span>
            <span className="text-[11px] text-muted">· {humanize(alert.type)}</span>
          </div>
          <h3 className="mt-2 font-medium">{alert.title}</h3>
          {alert.description && <p className="mt-1 text-sm text-muted">{alert.description}</p>}

          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-1.5 text-xs text-muted">
            {alert.providerCode && (
              <span className="inline-flex items-center gap-1.5"><Server className="h-3.5 w-3.5" />{alert.providerCode}</span>
            )}
            {alert.paymentReference && (
              <Link
                href={`/payments/${encodeURIComponent(alert.paymentReference)}`}
                className="inline-flex items-center gap-1.5 text-jade-400 hover:underline"
              >
                <CreditCard className="h-3.5 w-3.5" />{alert.paymentReference}
              </Link>
            )}
            {alert.incidentReference && (
              <Link
                href={`/incidents/${encodeURIComponent(alert.incidentReference)}`}
                className="inline-flex items-center gap-1.5 text-jade-400 hover:underline"
              >
                <BellRing className="h-3.5 w-3.5" />{alert.incidentReference}
              </Link>
            )}
            {alert.metricValue !== undefined && (
              <span className="inline-flex items-center gap-1.5">
                <Gauge className="h-3.5 w-3.5" />
                <span className="font-mono text-foreground">{formatNumber(alert.metricValue)}</span>
                {alert.threshold !== undefined && <>vs threshold <span className="font-mono">{formatNumber(alert.threshold)}</span></>}
              </span>
            )}
            <span className="inline-flex items-center gap-1.5" title={formatDateTime(alert.detectedAt)}>
              <Clock className="h-3.5 w-3.5" />Detected {timeAgo(alert.detectedAt)}
            </span>
            <span title={formatDateTime(alert.lastSeenAt)}>Last seen {timeAgo(alert.lastSeenAt)}</span>
          </div>

          {alert.status === "RESOLVED" && (
            <p className="mt-2 text-xs text-muted">
              Resolved {timeAgo(alert.resolvedAt)}{alert.resolvedBy && ` by ${alert.resolvedBy}`}
              {alert.resolutionNote && <> — <span className="text-foreground">{alert.resolutionNote}</span></>}
            </p>
          )}
          {alert.status === "ACKNOWLEDGED" && alert.acknowledgedBy && (
            <p className="mt-2 text-xs text-muted">Acknowledged by {alert.acknowledgedBy} {timeAgo(alert.acknowledgedAt)}</p>
          )}
          {acknowledge.error && <div className="mt-2"><ErrorNote message={errorMessage(acknowledge.error)} /></div>}
        </div>

        {canManage && alert.status !== "RESOLVED" && (
          <div className="flex shrink-0 gap-2">
            {alert.status === "OPEN" && (
              <Button variant="secondary" size="sm" loading={acknowledge.isPending} onClick={() => acknowledge.mutate()}>
                <Check className="h-4 w-4" /> Acknowledge
              </Button>
            )}
            <Button variant="outline" size="sm" onClick={onResolve}>
              <CircleCheck className="h-4 w-4" /> Resolve
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- resolve dialog

function ResolveDialog({ alert, onClose }: { alert: Alert | null; onClose: () => void }) {
  const [note, setNote] = useState("");
  const queryClient = useQueryClient();
  const resolve = useMutation({
    mutationFn: ({ reference, note }: { reference: string; note: string }) => alertsApi.resolve(reference, note),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["alerts"] });
      close();
    },
  });

  function close() {
    setNote("");
    resolve.reset();
    onClose();
  }

  return (
    <Dialog open={!!alert} onOpenChange={(open) => !open && close()}>
      {alert && (
        <DialogContent title="Resolve alert" description={`${alert.reference} · ${alert.title}`}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (note.trim()) resolve.mutate({ reference: alert.reference, note: note.trim() });
            }}
            className="space-y-4"
          >
            <div>
              <Label htmlFor="resolution-note">Resolution note</Label>
              <Textarea
                id="resolution-note"
                required
                autoFocus
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="What was the cause and how was it addressed?"
              />
            </div>
            <ErrorNote message={resolve.error ? errorMessage(resolve.error) : null} />
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" onClick={close}>Cancel</Button>
              <Button type="submit" loading={resolve.isPending} disabled={!note.trim()}>Resolve alert</Button>
            </div>
          </form>
        </DialogContent>
      )}
    </Dialog>
  );
}
