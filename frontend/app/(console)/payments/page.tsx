"use client";

import { Suspense, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import {
  AlertTriangle, ChevronLeft, ChevronRight, Download, Radio, RotateCcw, Search, Sparkles, X,
} from "lucide-react";
import { aiApi, paymentsApi, providersApi, type PaymentFilters } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { useLive } from "@/lib/live";
import { cn, flag, formatDateTime, formatMoney, humanize, timeAgo } from "@/lib/utils";
import {
  Badge, Button, Card, CardBody, Empty, ErrorNote, Input, Label, PageHeader, Select, Skeleton,
} from "@/components/ui/primitives";
import { PaymentStatusBadge, RiskBadge } from "@/components/ui/status";
import type { Payment, PaymentMethod, PaymentStatus, RiskLevel, SearchInterpretation } from "@/types/api";

const PAGE_SIZE = 25;

const STATUSES: PaymentStatus[] = [
  "INITIATED", "AUTHORIZED", "CAPTURED", "PROCESSING", "SETTLED", "FAILED", "RETRY_PENDING",
  "RETRY_IN_PROGRESS", "RETRY_FAILED", "REFUNDED", "CHARGEBACK", "CANCELLED",
];
const METHODS: PaymentMethod[] = ["CARD", "DIGITAL_WALLET", "BANK_TRANSFER", "REAL_TIME_PAYMENT", "CASH_PICKUP", "BNPL"];
const RISK_LEVELS: RiskLevel[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

type Filters = Omit<PaymentFilters, "page" | "size">;

/** Maps the AI's structured interpretation onto the REST filter contract. */
function toFilters(i: SearchInterpretation): Filters {
  return {
    status: i.statuses.length ? i.statuses : undefined,
    provider: i.provider,
    method: i.method,
    risk: i.risk,
    currency: i.currency,
    minAmount: i.minAmount,
    maxAmount: i.maxAmount,
    country: i.country,
    stuck: i.stuck || undefined,
    from: i.lastHours ? new Date(Date.now() - i.lastHours * 3_600_000).toISOString() : undefined,
    q: i.text,
  };
}

interface Chip { key: string; label: string; remove: (f: Filters) => Filters }

function filterChips(f: Filters): Chip[] {
  const chips: Chip[] = [];
  const drop = (key: keyof Filters) => (cur: Filters) => ({ ...cur, [key]: undefined });
  f.status?.forEach((s) =>
    chips.push({
      key: `status-${s}`,
      label: humanize(s),
      remove: (cur) => {
        const next = (cur.status ?? []).filter((x) => x !== s);
        return { ...cur, status: next.length ? next : undefined };
      },
    }),
  );
  if (f.provider) chips.push({ key: "provider", label: `Provider: ${f.provider}`, remove: drop("provider") });
  if (f.method) chips.push({ key: "method", label: `Method: ${humanize(f.method)}`, remove: drop("method") });
  if (f.risk) chips.push({ key: "risk", label: `Risk: ${f.risk}`, remove: drop("risk") });
  if (f.currency) chips.push({ key: "currency", label: `Currency: ${f.currency}`, remove: drop("currency") });
  if (f.minAmount !== undefined) chips.push({ key: "min", label: `≥ ${f.minAmount}`, remove: drop("minAmount") });
  if (f.maxAmount !== undefined) chips.push({ key: "max", label: `≤ ${f.maxAmount}`, remove: drop("maxAmount") });
  if (f.country) chips.push({ key: "country", label: `${flag(f.country)} ${f.country}`, remove: drop("country") });
  if (f.stuck) chips.push({ key: "stuck", label: "Stuck only", remove: drop("stuck") });
  if (f.from) chips.push({ key: "from", label: `Since ${timeAgo(f.from)}`, remove: drop("from") });
  if (f.q) chips.push({ key: "q", label: `“${f.q}”`, remove: drop("q") });
  return chips;
}

// ------------------------------------------------------------------ CSV export
function csvCell(value: string | number | boolean | undefined) {
  const s = value === undefined ? "" : String(value);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

function exportCsv(rows: Payment[]) {
  const header = ["reference", "createdAt", "amount", "currency", "provider", "method", "country", "status", "riskScore", "riskLevel", "latencyMs", "stuck"];
  const lines = rows.map((p) =>
    [p.reference, p.createdAt, p.amount, p.currency, p.providerCode, p.method, p.country, p.status, p.riskScore, p.riskLevel, p.latencyMs, p.stuck]
      .map(csvCell)
      .join(","),
  );
  const blob = new Blob([[header.join(","), ...lines].join("\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `payments-${new Date().toISOString().slice(0, 19).replace(/:/g, "")}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

// ------------------------------------------------------------------ page
export default function PaymentsPage() {
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(0);
  const [live, setLive] = useState(false);
  const [interpretation, setInterpretation] = useState<SearchInterpretation | null>(null);

  const applyFilters = (next: Filters) => {
    setFilters(next);
    setPage(0);
  };

  const interpret = useMutation({
    mutationFn: (query: string) => aiApi.interpret(query),
    onSuccess: (result) => {
      setInterpretation(result);
      applyFilters(toFilters(result));
    },
  });

  const payments = useQuery({
    queryKey: ["payments", "search", filters, page],
    queryFn: () => paymentsApi.search({ ...filters, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
    refetchInterval: live ? 10_000 : false,
  });

  const reset = () => {
    setInterpretation(null);
    applyFilters({});
  };

  return (
    <div className="space-y-5">
      <PageHeader
        title="Payments"
        subtitle="Search, filter and inspect every transaction across the provider network."
        actions={
          <>
            <LiveToggle live={live} onToggle={() => setLive((v) => !v)} since={payments.dataUpdatedAt} />
            <Button
              variant="secondary"
              size="sm"
              onClick={() => payments.data && exportCsv(payments.data.items)}
              disabled={!payments.data?.items.length}
            >
              <Download className="h-4 w-4" /> Export CSV
            </Button>
          </>
        }
      />

      <Suspense fallback={null}>
        <AskFromUrl onAsk={(q) => interpret.mutate(q)} />
      </Suspense>

      <SmartSearch
        loading={interpret.isPending}
        error={interpret.isError ? errorMessage(interpret.error) : null}
        interpretation={interpretation}
        onAsk={(q) => interpret.mutate(q)}
        onDismiss={() => setInterpretation(null)}
      />

      <FilterBar filters={filters} onChange={applyFilters} />

      <ActiveChips filters={filters} onChange={applyFilters} onReset={reset} />

      <ErrorNote message={payments.isError ? errorMessage(payments.error) : null} />

      <Card className="overflow-hidden">
        <PaymentsTable rows={payments.data?.items} loading={payments.isPending} />
        {payments.data && (
          <Pagination
            page={payments.data.page}
            totalPages={payments.data.totalPages}
            totalItems={payments.data.totalItems}
            size={payments.data.size}
            fetching={payments.isFetching}
            onPage={setPage}
          />
        )}
      </Card>
    </div>
  );
}

/** Runs a smart search once when the page is opened with `?ask=`. */
function AskFromUrl({ onAsk }: { onAsk: (query: string) => void }) {
  const ask = useSearchParams().get("ask");
  const handled = useRef<string | null>(null);
  useEffect(() => {
    if (ask && handled.current !== ask) {
      handled.current = ask;
      onAsk(ask);
    }
  }, [ask, onAsk]);
  return null;
}

// ------------------------------------------------------------------ smart search
function SmartSearch({ loading, error, interpretation, onAsk, onDismiss }: {
  loading: boolean;
  error: string | null;
  interpretation: SearchInterpretation | null;
  onAsk: (query: string) => void;
  onDismiss: () => void;
}) {
  const [query, setQuery] = useState("");
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim()) onAsk(query.trim());
  };

  return (
    <Card className="border-jade-500/20">
      <CardBody className="space-y-3">
        <form onSubmit={submit} className="flex flex-col gap-2 sm:flex-row">
          <div className="relative flex-1">
            <Sparkles className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-jade-400" />
            <Input
              aria-label="Ask in plain English"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Ask in plain English, e.g. failed card payments over 500 GBP to Pakistan today"
              className="pl-9"
            />
          </div>
          <Button type="submit" loading={loading} disabled={!query.trim()}>
            <Search className="h-4 w-4" /> Ask AI
          </Button>
        </form>
        <ErrorNote message={error} />
        {interpretation && (
          <div className="flex items-start justify-between gap-3 rounded-lg border border-jade-500/20 bg-jade-500/5 px-3 py-2 text-sm">
            <div className="flex items-start gap-2">
              <Sparkles className="mt-0.5 h-4 w-4 shrink-0 text-brass-400" />
              <p>
                {interpretation.explanation}{" "}
                <Badge tone="brass" className="ml-1 align-middle">{interpretation.engine}</Badge>
              </p>
            </div>
            <button onClick={onDismiss} aria-label="Dismiss explanation" className="cursor-pointer text-muted hover:text-foreground">
              <X className="h-4 w-4" />
            </button>
          </div>
        )}
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ manual filters
function FilterBar({ filters, onChange }: { filters: Filters; onChange: (f: Filters) => void }) {
  const providers = useQuery({ queryKey: ["providers"], queryFn: providersApi.list });
  const [text, setText] = useState(filters.q ?? "");
  const [prevQ, setPrevQ] = useState(filters.q);
  if (prevQ !== filters.q) {
    // keep the text box in sync when the AI or a chip changes the query
    setPrevQ(filters.q);
    setText(filters.q ?? "");
  }

  const set = <K extends keyof Filters>(key: K, value: Filters[K]) => onChange({ ...filters, [key]: value });

  return (
    <Card>
      <CardBody className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-6">
        <form
          className="lg:col-span-2"
          onSubmit={(e) => {
            e.preventDefault();
            set("q", text.trim() || undefined);
          }}
        >
          <Label htmlFor="pay-q">Search</Label>
          <Input id="pay-q" value={text} onChange={(e) => setText(e.target.value)} placeholder="Reference, customer, card…" />
        </form>
        <div>
          <Label htmlFor="pay-status">Status</Label>
          <Select
            id="pay-status"
            className="w-full"
            value=""
            onChange={(e) => {
              const s = e.target.value as PaymentStatus;
              if (s && !filters.status?.includes(s)) set("status", [...(filters.status ?? []), s]);
            }}
          >
            <option value="">{filters.status?.length ? `${filters.status.length} selected — add…` : "Any status"}</option>
            {STATUSES.filter((s) => !filters.status?.includes(s)).map((s) => (
              <option key={s} value={s}>{humanize(s)}</option>
            ))}
          </Select>
        </div>
        <div>
          <Label htmlFor="pay-provider">Provider</Label>
          <Select
            id="pay-provider"
            className="w-full"
            value={filters.provider ?? ""}
            onChange={(e) => set("provider", e.target.value || undefined)}
          >
            <option value="">All providers</option>
            {providers.data?.map((p) => <option key={p.code} value={p.code}>{p.name}</option>)}
          </Select>
        </div>
        <div>
          <Label htmlFor="pay-method">Method</Label>
          <Select
            id="pay-method"
            className="w-full"
            value={filters.method ?? ""}
            onChange={(e) => set("method", (e.target.value || undefined) as PaymentMethod | undefined)}
          >
            <option value="">All methods</option>
            {METHODS.map((m) => <option key={m} value={m}>{humanize(m)}</option>)}
          </Select>
        </div>
        <div className="flex items-end gap-3">
          <div className="flex-1">
            <Label htmlFor="pay-risk">Risk</Label>
            <Select
              id="pay-risk"
              className="w-full"
              value={filters.risk ?? ""}
              onChange={(e) => set("risk", (e.target.value || undefined) as RiskLevel | undefined)}
            >
              <option value="">Any</option>
              {RISK_LEVELS.map((r) => <option key={r} value={r}>{humanize(r)}</option>)}
            </Select>
          </div>
          <Button
            type="button"
            variant={filters.stuck ? "outline" : "secondary"}
            aria-pressed={!!filters.stuck}
            onClick={() => set("stuck", filters.stuck ? undefined : true)}
            title="Show only stuck payments"
          >
            <AlertTriangle className="h-4 w-4" /> Stuck
          </Button>
        </div>
      </CardBody>
    </Card>
  );
}

function ActiveChips({ filters, onChange, onReset }: { filters: Filters; onChange: (f: Filters) => void; onReset: () => void }) {
  const chips = filterChips(filters);
  if (!chips.length) return null;
  return (
    <div className="flex flex-wrap items-center gap-2" aria-label="Active filters">
      {chips.map((c) => (
        <span key={c.key} className="inline-flex items-center gap-1 rounded-full border border-jade-500/30 bg-jade-500/10 py-0.5 pl-2.5 pr-1 text-xs text-jade-400">
          {c.label}
          <button
            onClick={() => onChange(c.remove(filters))}
            aria-label={`Remove filter ${c.label}`}
            className="cursor-pointer rounded-full p-0.5 hover:bg-jade-500/20"
          >
            <X className="h-3 w-3" />
          </button>
        </span>
      ))}
      <Button variant="ghost" size="sm" onClick={onReset}>
        <RotateCcw className="h-3.5 w-3.5" /> Clear all
      </Button>
    </div>
  );
}

// ------------------------------------------------------------------ live toggle
function LiveToggle({ live, onToggle, since }: { live: boolean; onToggle: () => void; since: number }) {
  const feed = useLive((s) => s.feed);
  const fresh = useMemo(() => feed.filter((p) => Date.parse(p.createdAt) > since).length, [feed, since]);
  return (
    <div className="flex items-center gap-2">
      {live && fresh > 0 && <Badge tone="jade" dot>{fresh} new payment{fresh === 1 ? "" : "s"}</Badge>}
      <Button variant={live ? "outline" : "secondary"} size="sm" onClick={onToggle} aria-pressed={live}>
        <Radio className={cn("h-4 w-4", live && "animate-pulse")} /> {live ? "Live" : "Go live"}
      </Button>
    </div>
  );
}

// ------------------------------------------------------------------ table
function PaymentsTable({ rows, loading }: { rows?: Payment[]; loading: boolean }) {
  if (loading) {
    return (
      <div className="space-y-2 p-5">
        {Array.from({ length: 8 }, (_, i) => <Skeleton key={i} className="h-10 w-full" />)}
      </div>
    );
  }
  if (!rows?.length) {
    return <Empty icon={<Search className="h-6 w-6" />} title="No payments match" hint="Try widening the filters or asking the AI a different question." />;
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[960px] text-sm">
        <thead>
          <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-muted">
            <th className="px-4 py-3 font-medium">Reference</th>
            <th className="px-4 py-3 font-medium">Created</th>
            <th className="px-4 py-3 text-right font-medium">Amount</th>
            <th className="px-4 py-3 font-medium">Provider</th>
            <th className="px-4 py-3 font-medium">Method</th>
            <th className="px-4 py-3 font-medium">Country</th>
            <th className="px-4 py-3 font-medium">Status</th>
            <th className="px-4 py-3 font-medium">Risk</th>
            <th className="px-4 py-3 text-right font-medium">Latency</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((p) => (
            <tr
              key={p.reference}
              className={cn(
                "border-b border-border/60 transition-colors hover:bg-surface-2/60",
                p.stuck && "bg-warning/5 shadow-[inset_3px_0_0_var(--color-warning)]",
              )}
            >
              <td className="px-4 py-2.5">
                <Link href={`/payments/${encodeURIComponent(p.reference)}`} className="font-mono text-xs text-jade-400 hover:underline">
                  {p.reference}
                </Link>
                {p.stuck && <Badge tone="amber" className="ml-2">Stuck</Badge>}
              </td>
              <td className="px-4 py-2.5 whitespace-nowrap text-muted" title={formatDateTime(p.createdAt)}>{timeAgo(p.createdAt)}</td>
              <td className="px-4 py-2.5 text-right font-medium tabular-nums">{formatMoney(p.amount, p.currency)}</td>
              <td className="px-4 py-2.5">{p.providerName}</td>
              <td className="px-4 py-2.5 text-muted">{humanize(p.method)}{p.cardLast4 ? ` ·${p.cardLast4}` : ""}</td>
              <td className="px-4 py-2.5" title={p.country}>{flag(p.country)} <span className="text-xs text-muted">{p.country}</span></td>
              <td className="px-4 py-2.5"><PaymentStatusBadge status={p.status} /></td>
              <td className="px-4 py-2.5"><RiskBadge level={p.riskLevel} score={p.riskScore} /></td>
              <td className="px-4 py-2.5 text-right tabular-nums text-muted">{p.latencyMs !== undefined ? `${p.latencyMs} ms` : "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Pagination({ page, totalPages, totalItems, size, fetching, onPage }: {
  page: number; totalPages: number; totalItems: number; size: number; fetching: boolean; onPage: (p: number) => void;
}) {
  const first = totalItems === 0 ? 0 : page * size + 1;
  const last = Math.min((page + 1) * size, totalItems);
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-3 text-xs text-muted">
      <span>
        {first}–{last} of {totalItems.toLocaleString()} payments
        {fetching && <span className="ml-2 animate-pulse text-jade-400">refreshing…</span>}
      </span>
      <div className="flex items-center gap-2">
        <Button variant="secondary" size="sm" onClick={() => onPage(page - 1)} disabled={page <= 0} aria-label="Previous page">
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <span>Page {page + 1} of {Math.max(totalPages, 1)}</span>
        <Button variant="secondary" size="sm" onClick={() => onPage(page + 1)} disabled={page + 1 >= totalPages} aria-label="Next page">
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
