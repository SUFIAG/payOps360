"use client";

import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { AlertOctagon, ChevronLeft, ChevronRight, Coins, FileSpreadsheet, Inbox, Lock, Percent, Scale, TrendingUp, X } from "lucide-react";
import { providersApi, reconciliationApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission } from "@/lib/auth/session";
import { cn, formatDateTime, formatMoney, formatNumber, formatPercent, timeAgo } from "@/lib/utils";
import { Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, Label, PageHeader, Segmented, Select, Skeleton } from "@/components/ui/primitives";
import { AnimatedNumber } from "@/components/dashboard/widgets";
import { EChart, palette } from "@/components/charts/echart";
import { UploadSettlementDialog } from "@/components/reconciliation/upload-dialog";
import { ExceptionDrawer } from "@/components/reconciliation/exception-drawer";
import { FeeScheduleTab } from "@/components/reconciliation/fee-schedule-tab";
import {
  BatchStatusBadge, EXCEPTION_STATUSES, EXCEPTION_TYPE_META, EXCEPTION_TYPES, ExceptionStatusBadge, ExceptionTypeBadge, ageLabel, isActive,
} from "@/components/reconciliation/shared";
import type { MatchRatePoint, ReconExceptionStatus, ReconExceptionType, ReconSummary, SettlementBatch } from "@/types/api";

const DAYS = 30;
type Tab = "exceptions" | "batches" | "fees";

export default function ReconciliationPage() {
  const allowed = usePermission("RECON_READ");
  if (!allowed) {
    return (
      <Card>
        <Empty icon={<Lock className="h-8 w-8" />} title="Access restricted" hint="Reconciliation requires the RECON_READ permission." />
      </Card>
    );
  }
  return <Reconciliation />;
}

function Reconciliation() {
  const canManage = usePermission("RECON_MANAGE");
  const [tab, setTab] = useState<Tab>("exceptions");
  const [batchFilter, setBatchFilter] = useState<string | undefined>();
  const [selected, setSelected] = useState<string | null>(null);
  const summary = useQuery({ queryKey: ["recon", "summary", DAYS], queryFn: () => reconciliationApi.summary(DAYS), refetchInterval: 30_000 });
  const trend = useQuery({ queryKey: ["recon", "trend", DAYS], queryFn: () => reconciliationApi.trend(DAYS) });

  return (
    <div className="space-y-5">
      <PageHeader
        title="Reconciliation"
        subtitle="Match provider settlement files against the ledger, work the exception queue and catch fee leakage."
        actions={canManage ? <UploadSettlementDialog /> : undefined}
      />

      <ErrorNote message={summary.isError ? errorMessage(summary.error) : null} />
      <KpiRow summary={summary.data} />

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Match rate over time" subtitle={`Share of settlement lines matched to the ledger, last ${DAYS} days`} icon={<TrendingUp className="h-4 w-4" />} />
          <CardBody>
            {trend.isPending ? <Skeleton className="h-[240px] w-full" /> : <MatchRateChart points={trend.data ?? []} />}
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="Open exceptions by type" icon={<AlertOctagon className="h-4 w-4" />} />
          <CardBody>
            <OpenByType summary={summary.data} />
          </CardBody>
        </Card>
      </div>

      <Segmented<Tab>
        value={tab}
        onChange={setTab}
        options={[
          { value: "exceptions", label: "Exception queue" },
          { value: "batches", label: "Settlement batches" },
          { value: "fees", label: "Fee schedules" },
        ]}
      />

      {tab === "exceptions" && (
        <ExceptionQueue batch={batchFilter} onClearBatch={() => setBatchFilter(undefined)} onSelect={setSelected} />
      )}
      {tab === "batches" && (
        <BatchesTable onOpen={(reference) => { setBatchFilter(reference); setTab("exceptions"); }} />
      )}
      {tab === "fees" && <FeeScheduleTab />}

      <ExceptionDrawer reference={selected} onClose={() => setSelected(null)} />
    </div>
  );
}

// ------------------------------------------------------------------ KPIs
function KpiRow({ summary }: { summary?: ReconSummary }) {
  if (!summary) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-[104px] w-full" />)}
      </div>
    );
  }
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <Kpi label="Match rate" icon={<Percent className="h-4 w-4" />} value={summary.matchRate ?? 0}
        format={(v) => (summary.matchRate === undefined || summary.matchRate === null ? "—" : formatPercent(v, 1))}
        hint={`${formatNumber(summary.matchedLines)} of ${formatNumber(summary.reconciledLines)} lines · ${summary.days}d`}
        tone={summary.matchRate === undefined || summary.matchRate === null ? undefined : summary.matchRate >= 98 ? "good" : summary.matchRate >= 90 ? "warn" : "bad"} />
      <Kpi label="Amount at risk" icon={<Scale className="h-4 w-4" />} value={summary.amountAtRiskUsd}
        format={(v) => formatMoney(v, "USD", true)} hint="Open & investigating, in USD" tone={summary.amountAtRiskUsd > 0 ? "bad" : "good"} />
      <Kpi label="Open exceptions" icon={<Inbox className="h-4 w-4" />} value={summary.openExceptions}
        format={(v) => formatNumber(Math.round(v))} hint={`${formatNumber(summary.batches)} batch(es) imported · ${summary.days}d`}
        tone={summary.openExceptions > 0 ? "warn" : "good"} />
      <Kpi label="Fee leakage" icon={<Coins className="h-4 w-4" />} value={summary.feeLeakageUsd}
        format={(v) => formatMoney(v, "USD", true)} hint={`Overcharged fees · ${summary.days}d`} tone={summary.feeLeakageUsd > 0 ? "bad" : "good"} />
    </div>
  );
}

function Kpi({ label, icon, value, format, hint, tone }: {
  label: string; icon: React.ReactNode; value: number; format: (v: number) => string; hint: string; tone?: "good" | "warn" | "bad";
}) {
  return (
    <Card className="p-4 transition-colors hover:border-jade-500/40">
      <div className="flex items-center justify-between">
        <p className="text-[11px] font-medium uppercase tracking-wider text-muted">{label}</p>
        <span className={cn("text-muted", tone === "good" && "text-jade-400", tone === "warn" && "text-warning", tone === "bad" && "text-danger")}>{icon}</span>
      </div>
      <p className="mt-2 font-display text-2xl font-semibold tabular-nums"><AnimatedNumber value={value} format={format} /></p>
      <p className="mt-1 text-xs text-muted">{hint}</p>
    </Card>
  );
}

// ------------------------------------------------------------------ charts
function MatchRateChart({ points }: { points: MatchRatePoint[] }) {
  if (!points.length) {
    return <Empty icon={<TrendingUp className="h-6 w-6" />} title="No settlements yet" hint="Import a settlement file to start tracking the match rate." />;
  }
  return (
    <EChart
      height={240}
      option={{
        tooltip: { trigger: "axis" },
        legend: { data: ["Match rate", "Lines"] },
        xAxis: { type: "category", data: points.map((p) => p.day.slice(5)), axisTick: { show: false } },
        yAxis: [
          { type: "value", min: (v: { min: number }) => Math.max(0, Math.floor(v.min / 10) * 10), max: 100, axisLabel: { formatter: "{value}%" } },
          { type: "value", splitLine: { show: false } },
        ],
        series: [
          { name: "Lines", type: "bar", yAxisIndex: 1, data: points.map((p) => p.lines), itemStyle: { color: palette[6], opacity: 0.35, borderRadius: [3, 3, 0, 0] }, barMaxWidth: 18 },
          {
            name: "Match rate", type: "line", smooth: true, symbolSize: 6, data: points.map((p) => p.matchRate ?? null),
            lineStyle: { width: 2, color: palette[0] }, itemStyle: { color: palette[0] },
            markLine: { silent: true, symbol: "none", lineStyle: { color: palette[2], type: "dashed" }, data: [{ yAxis: 98, name: "Target" }], label: { formatter: "98% target", color: palette[2] } },
          },
        ],
      }}
    />
  );
}

function OpenByType({ summary }: { summary?: ReconSummary }) {
  if (!summary) return <Skeleton className="h-[200px] w-full" />;
  const max = Math.max(1, ...EXCEPTION_TYPES.map((t) => summary.openByType[t] ?? 0));
  if (summary.openExceptions === 0) {
    return <Empty icon={<Inbox className="h-6 w-6" />} title="Queue is clear" hint="No open reconciliation exceptions." />;
  }
  return (
    <ul className="space-y-3">
      {EXCEPTION_TYPES.map((t) => {
        const n = summary.openByType[t] ?? 0;
        return (
          <li key={t}>
            <div className="mb-1 flex justify-between text-sm">
              <span>{EXCEPTION_TYPE_META[t].label}</span>
              <span className="tabular-nums text-muted">{formatNumber(n)}</span>
            </div>
            <div className="h-1.5 overflow-hidden rounded-full bg-surface-2">
              <div className="h-full rounded-full bg-jade-500/70" style={{ width: `${(n * 100) / max}%` }} />
            </div>
          </li>
        );
      })}
    </ul>
  );
}

// ------------------------------------------------------------------ exception queue
type StatusFilter = "ACTIVE" | "ALL" | ReconExceptionStatus;

function ExceptionQueue({ batch, onClearBatch, onSelect }: { batch?: string; onClearBatch: () => void; onSelect: (reference: string) => void }) {
  const providers = useQuery({ queryKey: ["providers"], queryFn: providersApi.list });
  const [type, setType] = useState<ReconExceptionType | "">("");
  const [status, setStatus] = useState<StatusFilter>("ACTIVE");
  const [provider, setProvider] = useState("");
  const [page, setPage] = useState(0);
  const statuses: ReconExceptionStatus[] | undefined =
    status === "ACTIVE" ? ["OPEN", "INVESTIGATING"] : status === "ALL" ? undefined : [status];

  const list = useQuery({
    queryKey: ["recon", "exceptions", { type, status, provider, batch, page }],
    queryFn: () => reconciliationApi.exceptions({ type: type ? [type] : undefined, status: statuses, provider: provider || undefined, batch, page, size: 25 }),
    placeholderData: keepPreviousData,
  });
  const change = (fn: () => void) => { fn(); setPage(0); };

  return (
    <Card className="overflow-hidden">
      <CardBody className="grid grid-cols-1 gap-3 border-b border-border sm:grid-cols-3 lg:grid-cols-4">
        <div>
          <Label htmlFor="rex-type">Type</Label>
          <Select id="rex-type" className="w-full" value={type} onChange={(e) => change(() => setType(e.target.value as ReconExceptionType | ""))}>
            <option value="">All types</option>
            {EXCEPTION_TYPES.map((t) => <option key={t} value={t}>{EXCEPTION_TYPE_META[t].label}</option>)}
          </Select>
        </div>
        <div>
          <Label htmlFor="rex-status">Status</Label>
          <Select id="rex-status" className="w-full" value={status} onChange={(e) => change(() => setStatus(e.target.value as StatusFilter))}>
            <option value="ACTIVE">Open & investigating</option>
            <option value="ALL">All statuses</option>
            {EXCEPTION_STATUSES.map((s) => <option key={s} value={s}>{s === "WRITTEN_OFF" ? "Written off" : s.charAt(0) + s.slice(1).toLowerCase()}</option>)}
          </Select>
        </div>
        <div>
          <Label htmlFor="rex-provider">Provider</Label>
          <Select id="rex-provider" className="w-full" value={provider} onChange={(e) => change(() => setProvider(e.target.value))}>
            <option value="">All providers</option>
            {providers.data?.map((p) => <option key={p.code} value={p.code}>{p.name}</option>)}
          </Select>
        </div>
        {batch && (
          <div className="flex items-end">
            <span className="inline-flex items-center gap-1 rounded-full border border-jade-500/30 bg-jade-500/10 py-0.5 pl-2.5 pr-1 text-xs text-jade-400">
              Batch {batch}
              <button onClick={() => { onClearBatch(); setPage(0); }} aria-label="Clear batch filter" className="cursor-pointer rounded-full p-0.5 hover:bg-jade-500/20">
                <X className="h-3 w-3" />
              </button>
            </span>
          </div>
        )}
      </CardBody>
      <ErrorNote message={list.isError ? errorMessage(list.error) : null} />
      {list.isPending ? (
        <div className="space-y-2 p-5">{Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-10 w-full" />)}</div>
      ) : !list.data?.items.length ? (
        <Empty icon={<Inbox className="h-6 w-6" />} title="No exceptions match" hint="Import a settlement file or widen the filters." />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[960px] text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-muted">
                <th className="px-4 py-3 font-medium">Exception</th>
                <th className="px-4 py-3 font-medium">Type</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Provider</th>
                <th className="px-4 py-3 font-medium">Payment / settlement ref</th>
                <th className="px-4 py-3 text-right font-medium">At risk</th>
                <th className="px-4 py-3 font-medium">Assignee</th>
                <th className="px-4 py-3 text-right font-medium">Age</th>
              </tr>
            </thead>
            <tbody>
              {list.data.items.map((e) => (
                <tr
                  key={e.reference}
                  onClick={() => onSelect(e.reference)}
                  onKeyDown={(ev) => { if (ev.key === "Enter" || ev.key === " ") { ev.preventDefault(); onSelect(e.reference); } }}
                  tabIndex={0}
                  className="cursor-pointer border-b border-border/60 transition-colors hover:bg-surface-2/60 focus:bg-surface-2/60 focus:outline-none"
                >
                  <td className="px-4 py-2.5 font-mono text-xs text-jade-400">{e.reference}</td>
                  <td className="px-4 py-2.5"><ExceptionTypeBadge type={e.type} /></td>
                  <td className="px-4 py-2.5"><ExceptionStatusBadge status={e.status} /></td>
                  <td className="px-4 py-2.5">{e.providerName}</td>
                  <td className="px-4 py-2.5 font-mono text-xs text-muted">{e.paymentReference ?? e.providerReference ?? "—"}</td>
                  <td className="px-4 py-2.5 text-right font-medium tabular-nums">{formatMoney(e.amountAtRisk, e.currency)}</td>
                  <td className="px-4 py-2.5 text-muted">{e.assignee ?? "—"}</td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-muted" title={formatDateTime(e.createdAt)}>{ageLabel(e.ageHours)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {list.data && list.data.totalItems > 0 && (
        <Pager page={list.data.page} totalPages={list.data.totalPages} totalItems={list.data.totalItems} noun="exceptions" onPage={setPage} />
      )}
    </Card>
  );
}

// ------------------------------------------------------------------ batches
function BatchesTable({ onOpen }: { onOpen: (reference: string) => void }) {
  const [page, setPage] = useState(0);
  const batches = useQuery({
    queryKey: ["recon", "batches", page],
    queryFn: () => reconciliationApi.batches(undefined, page, 10),
    placeholderData: keepPreviousData,
    refetchInterval: (q) => (q.state.data?.items.some((b) => isActive(b.status)) ? 2_000 : 30_000),
  });

  return (
    <Card className="overflow-hidden">
      <ErrorNote message={batches.isError ? errorMessage(batches.error) : null} />
      {batches.isPending ? (
        <div className="space-y-2 p-5">{Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-10 w-full" />)}</div>
      ) : !batches.data?.items.length ? (
        <Empty icon={<FileSpreadsheet className="h-6 w-6" />} title="No settlement files imported" hint="Use “Upload settlement” — you can download a sample CSV built from recent payments." />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[980px] text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-muted">
                <th className="px-4 py-3 font-medium">Batch</th>
                <th className="px-4 py-3 font-medium">Provider</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 text-right font-medium">Lines</th>
                <th className="px-4 py-3 text-right font-medium">Match rate</th>
                <th className="px-4 py-3 text-right font-medium">Exceptions</th>
                <th className="px-4 py-3 text-right font-medium">Gross (USD)</th>
                <th className="px-4 py-3 font-medium">Imported</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {batches.data.items.map((b) => <BatchRow key={b.reference} batch={b} onOpen={onOpen} />)}
            </tbody>
          </table>
        </div>
      )}
      {batches.data && batches.data.totalItems > 0 && (
        <Pager page={batches.data.page} totalPages={batches.data.totalPages} totalItems={batches.data.totalItems} noun="batches" onPage={setPage} />
      )}
    </Card>
  );
}

function BatchRow({ batch: b, onOpen }: { batch: SettlementBatch; onOpen: (reference: string) => void }) {
  const active = isActive(b.status);
  return (
    <tr className="border-b border-border/60 transition-colors hover:bg-surface-2/60">
      <td className="px-4 py-2.5">
        <p className="font-mono text-xs text-jade-400">{b.reference}</p>
        <p className="max-w-[220px] truncate text-xs text-muted" title={b.fileName}>{b.fileName}</p>
      </td>
      <td className="px-4 py-2.5">{b.providerName}</td>
      <td className="px-4 py-2.5">
        <div className="flex items-center gap-2">
          <BatchStatusBadge status={b.status} />
          {active && (
            <div className="h-1.5 w-20 overflow-hidden rounded-full bg-surface-2" role="progressbar" aria-valuenow={b.progressPercent} aria-valuemin={0} aria-valuemax={100}>
              <div className="h-full rounded-full bg-jade-500 transition-all duration-500" style={{ width: `${b.progressPercent}%` }} />
            </div>
          )}
        </div>
        {b.status === "FAILED" && b.errorMessage && <p className="mt-1 text-xs text-danger">{b.errorMessage}</p>}
      </td>
      <td className="px-4 py-2.5 text-right tabular-nums">
        {formatNumber(b.totalLines)}
        {b.rejectedLines > 0 && <Badge tone="amber" className="ml-2" >{b.rejectedLines} rejected</Badge>}
      </td>
      <td className="px-4 py-2.5 text-right tabular-nums">{formatPercent(b.matchRate)}</td>
      <td className="px-4 py-2.5 text-right tabular-nums">{active ? "…" : formatNumber(b.exceptions)}</td>
      <td className="px-4 py-2.5 text-right tabular-nums">{formatMoney(b.grossAmountUsd, "USD", true)}</td>
      <td className="px-4 py-2.5 whitespace-nowrap text-muted" title={formatDateTime(b.createdAt)}>
        {timeAgo(b.createdAt)}
        {b.uploadedBy && <span className="block text-xs">{b.uploadedBy}</span>}
      </td>
      <td className="px-4 py-2.5 text-right">
        <Button variant="ghost" size="sm" onClick={() => onOpen(b.reference)} disabled={active || b.exceptions === 0}>
          View exceptions
        </Button>
      </td>
    </tr>
  );
}

function Pager({ page, totalPages, totalItems, noun, onPage }: { page: number; totalPages: number; totalItems: number; noun: string; onPage: (p: number) => void }) {
  return (
    <div className="flex items-center justify-between gap-3 border-t border-border px-4 py-3 text-xs text-muted">
      <span>{formatNumber(totalItems)} {noun}</span>
      <div className="flex items-center gap-2">
        <Button variant="secondary" size="sm" onClick={() => onPage(page - 1)} disabled={page <= 0} aria-label="Previous page"><ChevronLeft className="h-4 w-4" /></Button>
        <span>Page {page + 1} of {Math.max(totalPages, 1)}</span>
        <Button variant="secondary" size="sm" onClick={() => onPage(page + 1)} disabled={page + 1 >= totalPages} aria-label="Next page"><ChevronRight className="h-4 w-4" /></Button>
      </div>
    </div>
  );
}
