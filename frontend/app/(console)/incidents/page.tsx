"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowUpRight, ChevronLeft, ChevronRight, CircleCheck, Eye, Gauge, Plus, Search, Server, ShieldAlert, Siren, Sparkles, Timer,
  TimerOff,
} from "lucide-react";
import { incidentsApi, providersApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission } from "@/lib/auth/session";
import { formatMoney, formatNumber, humanize, timeAgo } from "@/lib/utils";
import {
  Badge, Button, Card, CardBody, Empty, ErrorNote, Input, Label, PageHeader, Segmented, Select, Skeleton, Textarea,
} from "@/components/ui/primitives";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { GenericStatusBadge, SeverityBadge } from "@/components/ui/status";
import { EscalationBadge, SlaBadge } from "@/components/incidents/sla";
import type { IncidentStatus, Severity } from "@/types/api";

type StatusFilter = IncidentStatus | "ALL";

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "OPEN", label: "Open" },
  { value: "ACKNOWLEDGED", label: "Acknowledged" },
  { value: "INVESTIGATING", label: "Investigating" },
  { value: "RESOLVED", label: "Resolved" },
];
const SEVERITIES: Severity[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];
const PAGE_SIZE = 20;

function minutes(value?: number) {
  return value === undefined || value === null ? "—" : `${formatNumber(value)}m`;
}

export default function IncidentsPage() {
  const canManage = usePermission("INCIDENT_MANAGE");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [page, setPage] = useState(0);
  const [declaring, setDeclaring] = useState(false);

  const summary = useQuery({ queryKey: ["incidents", "summary"], queryFn: incidentsApi.summary, refetchInterval: 15_000 });
  const list = useQuery({
    queryKey: ["incidents", "list", status, page],
    queryFn: () => incidentsApi.list(status === "ALL" ? undefined : status, page, PAGE_SIZE),
    refetchInterval: 15_000,
    placeholderData: (prev) => prev,
  });

  const s = summary.data;
  const tiles = [
    { label: "Open", value: s ? formatNumber(s.open) : undefined, icon: <Siren className="h-4 w-4 text-danger" /> },
    { label: "Acknowledged", value: s ? formatNumber(s.acknowledged) : undefined, icon: <Eye className="h-4 w-4 text-warning" /> },
    { label: "Investigating", value: s ? formatNumber(s.investigating) : undefined, icon: <Search className="h-4 w-4 text-info" /> },
    { label: "Resolved (30d)", value: s ? formatNumber(s.resolvedLast30Days) : undefined, icon: <CircleCheck className="h-4 w-4 text-jade-400" /> },
    { label: "MTTA", value: s ? minutes(s.meanMinutesToAcknowledge) : undefined, icon: <Timer className="h-4 w-4 text-jade-400" /> },
    { label: "MTTR", value: s ? minutes(s.meanMinutesToResolve) : undefined, icon: <Timer className="h-4 w-4 text-jade-400" /> },
    { label: "SLA breached", value: s ? formatNumber(s.activeSlaBreaches ?? 0) : undefined, icon: <TimerOff className={s?.activeSlaBreaches ? "h-4 w-4 text-danger" : "h-4 w-4 text-muted"} /> },
    { label: "SLA met (30d)", value: s ? (s.slaAttainmentPercent == null ? "—" : `${Math.round(s.slaAttainmentPercent)}%`) : undefined, icon: <Gauge className="h-4 w-4 text-jade-400" /> },
  ];

  return (
    <div>
      <PageHeader
        title="Incidents"
        subtitle="Declared and auto-correlated incidents with AI-assisted root-cause analysis."
        actions={canManage && (
          <Button onClick={() => setDeclaring(true)}><Plus className="h-4 w-4" /> Declare incident</Button>
        )}
      />

      <div className="grid grid-cols-2 gap-4 md:grid-cols-4 xl:grid-cols-8">
        {tiles.map((t) => (
          <Card key={t.label} className="p-4">
            <div className="flex items-center justify-between text-[11px] uppercase tracking-wider text-muted">
              {t.label}{t.icon}
            </div>
            {summary.isLoading ? (
              <Skeleton className="mt-3 h-7 w-14" />
            ) : (
              <p className="mt-2 font-display text-2xl font-semibold">{t.value ?? "—"}</p>
            )}
          </Card>
        ))}
      </div>
      {summary.error && <div className="mt-3"><ErrorNote message={errorMessage(summary.error)} /></div>}

      <Card className="mt-6">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4">
          <Segmented value={status} options={STATUS_OPTIONS} onChange={(v) => { setStatus(v); setPage(0); }} />
          {list.data && <span className="text-xs text-muted">{formatNumber(list.data.totalItems)} incidents</span>}
        </div>

        <CardBody className="p-0">
          {list.error && <div className="p-5"><ErrorNote message={errorMessage(list.error)} /></div>}
          {list.isLoading ? (
            <div className="space-y-3 p-5">
              {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-16 w-full" />)}
            </div>
          ) : list.data && list.data.items.length > 0 ? (
            <ul className="divide-y divide-border">
              {list.data.items.map((incident) => (
                <li key={incident.reference}>
                  <Link
                    href={`/incidents/${encodeURIComponent(incident.reference)}`}
                    className="group flex flex-col gap-3 px-5 py-4 transition-colors hover:bg-surface-2/50 md:flex-row md:items-center"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <SeverityBadge severity={incident.severity} />
                        <GenericStatusBadge status={incident.status} />
                        <EscalationBadge level={incident.escalationLevel} />
                        {incident.aiAnalysis && <Badge tone="brass"><Sparkles className="h-3 w-3" /> AI analysed</Badge>}
                        <span className="font-mono text-[11px] text-muted">{incident.reference}</span>
                      </div>
                      <p className="mt-1.5 truncate font-medium group-hover:text-jade-400">{incident.title}</p>
                      <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
                        {incident.providerCode && (
                          <span className="inline-flex items-center gap-1"><Server className="h-3 w-3" />{incident.providerCode}</span>
                        )}
                        <span>Opened {timeAgo(incident.openedAt)}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-6 text-right">
                      <div className="min-w-32">
                        <SlaBadge incident={incident} />
                        <p className="mt-0.5 text-[10px] uppercase tracking-wider text-muted">SLA</p>
                      </div>
                      <div>
                        <p className="font-display text-sm font-semibold">{formatNumber(incident.affectedPayments)}</p>
                        <p className="text-[10px] uppercase tracking-wider text-muted">Payments</p>
                      </div>
                      <div>
                        <p className="font-display text-sm font-semibold">{formatMoney(incident.affectedAmount, "USD", true)}</p>
                        <p className="text-[10px] uppercase tracking-wider text-muted">At risk</p>
                      </div>
                      <ArrowUpRight className="h-4 w-4 text-muted group-hover:text-jade-400" />
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            !list.error && (
              <Empty
                icon={<ShieldAlert className="h-8 w-8" />}
                title="No incidents"
                hint={status === "ALL" ? "Nothing has been declared yet." : `No ${humanize(status).toLowerCase()} incidents.`}
              />
            )
          )}
        </CardBody>

        {list.data && list.data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-border px-5 py-3 text-xs text-muted">
            <span>Page {list.data.page + 1} of {list.data.totalPages}</span>
            <div className="flex gap-2">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                <ChevronLeft className="h-4 w-4" /> Prev
              </Button>
              <Button variant="secondary" size="sm" disabled={page + 1 >= list.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Next <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </Card>

      {canManage && <DeclareDialog open={declaring} onOpenChange={setDeclaring} />}
    </div>
  );
}

// ---------------------------------------------------------------- declare dialog

function DeclareDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [title, setTitle] = useState("");
  const [severity, setSeverity] = useState<Severity>("HIGH");
  const [description, setDescription] = useState("");
  const [providerCode, setProviderCode] = useState("");

  const providers = useQuery({ queryKey: ["providers"], queryFn: providersApi.list, enabled: open, staleTime: 60_000 });
  const declare = useMutation({
    mutationFn: () => incidentsApi.declare(title.trim(), severity, description.trim() || undefined, providerCode || undefined),
    onSuccess: async (incident) => {
      await queryClient.invalidateQueries({ queryKey: ["incidents"] });
      setOpen(false);
      router.push(`/incidents/${encodeURIComponent(incident.reference)}`);
    },
  });

  function setOpen(next: boolean) {
    if (!next) {
      setTitle(""); setSeverity("HIGH"); setDescription(""); setProviderCode(""); declare.reset();
    }
    onOpenChange(next);
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent title="Declare incident" description="Open a new incident and notify the on-call operations team.">
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (title.trim()) declare.mutate();
          }}
        >
          <div>
            <Label htmlFor="incident-title">Title</Label>
            <Input
              id="incident-title"
              required
              autoFocus
              maxLength={200}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Elevated card declines on Stripe EU"
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <Label htmlFor="incident-severity">Severity</Label>
              <Select id="incident-severity" className="w-full" value={severity} onChange={(e) => setSeverity(e.target.value as Severity)}>
                {SEVERITIES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
              </Select>
            </div>
            <div>
              <Label htmlFor="incident-provider">Provider</Label>
              <Select
                id="incident-provider"
                className="w-full"
                value={providerCode}
                onChange={(e) => setProviderCode(e.target.value)}
                disabled={providers.isLoading}
              >
                <option value="">{providers.isLoading ? "Loading…" : "Not provider-specific"}</option>
                {providers.data?.map((p) => <option key={p.code} value={p.code}>{p.name} ({p.code})</option>)}
              </Select>
            </div>
          </div>
          <div>
            <Label htmlFor="incident-description">Description</Label>
            <Textarea
              id="incident-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Observed symptoms, customer impact, first signals…"
            />
          </div>
          <ErrorNote message={declare.error ? errorMessage(declare.error) : providers.error ? errorMessage(providers.error) : null} />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button type="submit" loading={declare.isPending} disabled={!title.trim()}>
              <Siren className="h-4 w-4" /> Declare
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
