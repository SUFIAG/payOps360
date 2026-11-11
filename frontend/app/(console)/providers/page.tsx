"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { EChartsOption } from "echarts";
import { Activity, Network, Plus, Power, Route, Server, Trophy } from "lucide-react";
import { aiApi, providersApi, type ProviderInput } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission } from "@/lib/auth/session";
import { cn, formatNumber, formatPercent, humanize, timeAgo } from "@/lib/utils";
import {
  Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, Input, Label, PageHeader, Select, Skeleton,
} from "@/components/ui/primitives";
import { Dialog, DialogClose, DialogContent } from "@/components/ui/dialog";
import { HealthDot } from "@/components/ui/status";
import { EChart, palette } from "@/components/charts/echart";
import type { HealthStatus, Provider, ProviderType, RoutingRanking } from "@/types/api";

const PROVIDER_TYPES: ProviderType[] = [
  "CARD_ACQUIRER", "PAYMENT_GATEWAY", "DIGITAL_WALLET", "BANK_TRANSFER", "REAL_TIME_PAYMENTS", "REMITTANCE_CORRIDOR", "BNPL",
];

const healthTone: Record<HealthStatus, string> = {
  HEALTHY: "text-jade-400", DEGRADED: "text-warning", CRITICAL: "text-danger", UNKNOWN: "text-muted",
};

export default function ProvidersPage() {
  const canManage = usePermission("PROVIDER_MANAGE");
  const providers = useQuery({ queryKey: ["providers"], queryFn: providersApi.list, refetchInterval: 30_000 });
  const [selectedCode, setSelectedCode] = useState<string | null>(null);

  const list = useMemo(() => providers.data ?? [], [providers.data]);
  const selected = list.find((p) => p.code === selectedCode) ?? null;
  const counts = useMemo(() => {
    const c: Record<HealthStatus, number> = { HEALTHY: 0, DEGRADED: 0, CRITICAL: 0, UNKNOWN: 0 };
    list.forEach((p) => (c[p.health.status] += 1));
    return c;
  }, [list]);

  return (
    <div className="space-y-5">
      <PageHeader
        title="Provider network"
        subtitle="Live health of every acquirer, gateway, wallet and corridor against its SLA."
        actions={
          <>
            {providers.data && (
              <div className="flex items-center gap-3 text-xs text-muted">
                {(Object.keys(counts) as HealthStatus[]).filter((s) => counts[s] > 0).map((s) => (
                  <span key={s} className="inline-flex items-center gap-1.5"><HealthDot status={s} />{counts[s]} {humanize(s)}</span>
                ))}
              </div>
            )}
            {canManage && <AddProviderDialog />}
          </>
        }
      />

      <ErrorNote message={providers.isError ? errorMessage(providers.error) : null} />

      <div className="grid gap-5 xl:grid-cols-3">
        <div className="xl:col-span-2">
          {providers.isPending ? (
            <div className="grid gap-4 sm:grid-cols-2">
              {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-56" />)}
            </div>
          ) : list.length === 0 ? (
            <Card><Empty icon={<Network className="h-6 w-6" />} title="No providers configured" hint={canManage ? "Add your first provider to start routing payments." : undefined} /></Card>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              {list.map((p) => (
                <ProviderCard
                  key={p.code}
                  provider={p}
                  selected={p.code === selectedCode}
                  canManage={canManage}
                  onSelect={() => setSelectedCode(p.code === selectedCode ? null : p.code)}
                />
              ))}
            </div>
          )}
        </div>

        <div className="space-y-5 xl:sticky xl:top-4 xl:self-start">
          {selected ? (
            <HealthPanel provider={selected} onClose={() => setSelectedCode(null)} />
          ) : (
            <Card>
              <Empty icon={<Activity className="h-6 w-6" />} title="Select a provider" hint="Click any provider card to see its 24-hour health history." />
            </Card>
          )}
          <RoutingLeaderboard />
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ provider card
function ProviderCard({ provider: p, selected, canManage, onSelect }: {
  provider: Provider; selected: boolean; canManage: boolean; onSelect: () => void;
}) {
  const { health } = p;
  return (
    <Card
      onClick={onSelect}
      className={cn(
        "cursor-pointer transition-all hover:border-jade-500/40",
        selected && "ring-2 ring-jade-500/60",
        !p.active && "opacity-60",
      )}
    >
      <CardBody className="space-y-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onSelect();
              }}
              aria-pressed={selected}
              aria-label={`Show health history for ${p.name}`}
              className="flex cursor-pointer items-center gap-2 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-jade-400/60 rounded"
            >
              <HealthDot status={health.status} />
              <span className="truncate font-display font-semibold">{p.name}</span>
            </button>
            <p className="mt-0.5 text-xs text-muted">
              <span className="font-mono">{p.code}</span> · {humanize(p.type)}{p.region && ` · ${p.region}`}
            </p>
          </div>
          <div className="flex shrink-0 flex-col items-end gap-1">
            <Badge tone={p.active ? "jade" : "gray"} dot>{p.active ? "Active" : "Inactive"}</Badge>
            <span className={cn("text-[11px] font-medium", healthTone[health.status])}>{humanize(health.status)}</span>
          </div>
        </div>

        <SlaBar
          label="Success rate"
          value={health.successRate}
          display={formatPercent(health.successRate, 2)}
          target={p.slaSuccessRate}
          targetDisplay={`SLA ${formatPercent(p.slaSuccessRate, 1)}`}
          domain={[Math.min(80, (health.successRate ?? 100) - 2), 100]}
          ok={health.successRate === undefined || health.successRate >= p.slaSuccessRate}
        />
        <SlaBar
          label="p95 latency"
          value={health.p95LatencyMs}
          display={health.p95LatencyMs !== undefined ? `${health.p95LatencyMs} ms` : "—"}
          target={p.slaLatencyMs}
          targetDisplay={`SLA ${p.slaLatencyMs} ms`}
          domain={[0, Math.max(p.slaLatencyMs * 2, health.p95LatencyMs ?? 0)]}
          ok={health.p95LatencyMs === undefined || health.p95LatencyMs <= p.slaLatencyMs}
        />

        <div className="flex items-center justify-between gap-3 border-t border-border pt-3 text-xs text-muted">
          <span><span className="text-foreground tabular-nums">{formatNumber(health.volume, true)}</span> payments</span>
          <span><span className="text-foreground tabular-nums">{p.costBps}</span> bps</span>
          {health.observedAt && <span title={health.observedAt}>{timeAgo(health.observedAt)}</span>}
          {canManage && <ActiveToggle provider={p} />}
        </div>
      </CardBody>
    </Card>
  );
}

/** Horizontal bar with an SLA target marker. */
function SlaBar({ label, value, display, target, targetDisplay, domain, ok }: {
  label: string; value?: number; display: string; target: number; targetDisplay: string; domain: [number, number]; ok: boolean;
}) {
  const [lo, hi] = domain;
  const pos = (v: number) => Math.max(0, Math.min(100, ((v - lo) / Math.max(hi - lo, 1)) * 100));
  return (
    <div>
      <div className="mb-1 flex items-baseline justify-between text-xs">
        <span className="text-muted">{label}</span>
        <span>
          <span className={cn("font-medium tabular-nums", value === undefined ? "text-muted" : ok ? "text-jade-400" : "text-danger")}>{display}</span>
          <span className="ml-1.5 text-muted">{targetDisplay}</span>
        </span>
      </div>
      <div className="relative h-1.5 rounded-full bg-surface-2" role="img" aria-label={`${label} ${display}, ${targetDisplay}`}>
        {value !== undefined && (
          <div className={cn("h-1.5 rounded-full", ok ? "bg-jade-500" : "bg-danger")} style={{ width: `${pos(value)}%` }} />
        )}
        <span className="absolute -top-0.5 h-2.5 w-0.5 rounded bg-foreground/70" style={{ left: `${pos(target)}%` }} />
      </div>
    </div>
  );
}

function ActiveToggle({ provider }: { provider: Provider }) {
  const qc = useQueryClient();
  const toggle = useMutation({
    mutationFn: () => providersApi.setActive(provider.code, !provider.active),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["providers"] });
      qc.invalidateQueries({ queryKey: ["ai", "insights"] });
    },
  });
  return (
    <Button
      variant={provider.active ? "ghost" : "outline"}
      size="sm"
      loading={toggle.isPending}
      onClick={(e) => {
        e.stopPropagation();
        toggle.mutate();
      }}
      title={toggle.isError ? errorMessage(toggle.error) : undefined}
      aria-label={`${provider.active ? "Deactivate" : "Activate"} ${provider.name}`}
      className={cn(toggle.isError && "text-danger")}
    >
      <Power className="h-3.5 w-3.5" /> {provider.active ? "Deactivate" : "Activate"}
    </Button>
  );
}

// ------------------------------------------------------------------ health history
function HealthPanel({ provider, onClose }: { provider: Provider; onClose: () => void }) {
  const history = useQuery({
    queryKey: ["providers", provider.code, "health"],
    queryFn: () => providersApi.health(provider.code, 24),
  });

  const option = useMemo<EChartsOption | null>(() => {
    if (!history.data?.length) return null;
    const points = [...history.data].sort((a, b) => a.windowEnd.localeCompare(b.windowEnd));
    return {
      tooltip: { trigger: "axis" },
      legend: { data: ["Success rate", "p95 latency"] },
      grid: { left: 8, right: 8, top: 32, bottom: 8, containLabel: true },
      xAxis: { type: "time", splitLine: { show: false } },
      yAxis: [
        { type: "value", name: "%", min: (v) => Math.floor(Math.min(v.min, provider.slaSuccessRate) - 1), max: 100 },
        { type: "value", name: "ms", splitLine: { show: false } },
      ],
      series: [
        {
          name: "Success rate",
          type: "line",
          smooth: true,
          showSymbol: false,
          data: points.map((h) => [h.windowEnd, h.successRate]),
          lineStyle: { width: 2 },
          areaStyle: { opacity: 0.08 },
          markLine: {
            silent: true,
            symbol: "none",
            label: { formatter: "SLA", color: palette[0] },
            lineStyle: { type: "dashed", color: palette[0], opacity: 0.6 },
            data: [{ yAxis: provider.slaSuccessRate }],
          },
        },
        {
          name: "p95 latency",
          type: "line",
          yAxisIndex: 1,
          smooth: true,
          showSymbol: false,
          data: points.map((h) => [h.windowEnd, h.p95LatencyMs]),
          lineStyle: { width: 2 },
          markLine: {
            silent: true,
            symbol: "none",
            label: { formatter: "SLA", color: palette[1] },
            lineStyle: { type: "dashed", color: palette[1], opacity: 0.6 },
            data: [{ yAxis: provider.slaLatencyMs }],
          },
        },
      ],
    };
  }, [history.data, provider.slaSuccessRate, provider.slaLatencyMs]);

  return (
    <Card>
      <CardHeader
        title={provider.name}
        subtitle="Health history · last 24h"
        icon={<Server className="h-4 w-4" />}
        actions={<Button variant="ghost" size="sm" onClick={onClose}>Close</Button>}
      />
      <CardBody>
        {history.isPending && <Skeleton className="h-[260px] w-full" />}
        {history.isError && <ErrorNote message={errorMessage(history.error)} />}
        {history.data && !option && <Empty title="No health snapshots yet" hint="Snapshots appear once the provider processes traffic." />}
        {option && <EChart option={option} height={260} />}
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ routing leaderboard
function RoutingLeaderboard() {
  const insights = useQuery({ queryKey: ["ai", "insights"], queryFn: aiApi.insights, refetchInterval: 60_000 });
  const routing: RoutingRanking[] = insights.data?.routing ?? [];
  const top = Math.max(...routing.map((r) => r.score), 1);

  return (
    <Card>
      <CardHeader
        title="Smart-routing leaderboard"
        subtitle="AI-ranked by success, latency and cost"
        icon={<Route className="h-4 w-4" />}
        actions={insights.data && <Badge tone="jade">{insights.data.engine}</Badge>}
      />
      <CardBody>
        {insights.isPending && (
          <div className="space-y-3">{Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-12" />)}</div>
        )}
        {insights.isError && <ErrorNote message={errorMessage(insights.error)} />}
        {insights.data && routing.length === 0 && <Empty title="No rankings yet" hint="Rankings appear once active providers carry traffic." />}
        {routing.length > 0 && (
          <ol className="space-y-3">
            {routing.map((r, i) => (
              <li key={r.code} className="space-y-1">
                <div className="flex items-center justify-between gap-2 text-sm">
                  <span className="flex min-w-0 items-center gap-2">
                    <span
                      className={cn(
                        "flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-xs font-semibold",
                        i === 0 ? "bg-jade-600 text-white" : "bg-surface-2 text-muted",
                      )}
                    >
                      {i === 0 ? <Trophy className="h-3.5 w-3.5" /> : i + 1}
                    </span>
                    <span className="truncate font-medium">{r.name}</span>
                  </span>
                  <span className="font-display font-semibold tabular-nums text-jade-400">{r.score.toFixed(1)}</span>
                </div>
                <div className="h-1.5 rounded-full bg-surface-2">
                  <div className="h-1.5 rounded-full bg-gradient-to-r from-jade-600 to-jade-400" style={{ width: `${(r.score / top) * 100}%` }} />
                </div>
                <p className="text-xs text-muted">
                  {formatPercent(r.successRate, 1)} · p95 {r.p95LatencyMs} ms · {r.costBps} bps — {r.rationale}
                </p>
              </li>
            ))}
          </ol>
        )}
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ add provider
interface ProviderForm { code: string; name: string; type: ProviderType; region: string; slaLatencyMs: string; slaSuccessRate: string; costBps: string }

const EMPTY_FORM: ProviderForm = {
  code: "", name: "", type: "CARD_ACQUIRER", region: "", slaLatencyMs: "1500", slaSuccessRate: "97", costBps: "25",
};

function toInput(f: ProviderForm): ProviderInput | string {
  const slaLatencyMs = Number(f.slaLatencyMs);
  const slaSuccessRate = Number(f.slaSuccessRate);
  const costBps = Number(f.costBps);
  if (!/^[A-Z0-9_-]{2,40}$/.test(f.code.trim().toUpperCase())) return "Code must be 2–40 letters, digits, dashes or underscores.";
  if (!f.name.trim()) return "Name is required.";
  if (!Number.isInteger(slaLatencyMs) || slaLatencyMs <= 0) return "SLA latency must be a positive whole number of milliseconds.";
  if (!(slaSuccessRate > 0 && slaSuccessRate <= 100)) return "SLA success rate must be between 0 and 100.";
  if (!(costBps >= 0)) return "Cost must be zero or more basis points.";
  return {
    code: f.code.trim().toUpperCase(), name: f.name.trim(), type: f.type, region: f.region.trim() || undefined,
    slaLatencyMs, slaSuccessRate, costBps,
  };
}

function AddProviderDialog() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<ProviderForm>(EMPTY_FORM);
  const [invalid, setInvalid] = useState<string | null>(null);
  const create = useMutation({
    mutationFn: (input: ProviderInput) => providersApi.create(input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["providers"] });
      qc.invalidateQueries({ queryKey: ["ai", "insights"] });
      setOpen(false);
    },
  });

  const show = () => {
    setForm(EMPTY_FORM);
    setInvalid(null);
    create.reset();
    setOpen(true);
  };

  const field = (key: keyof ProviderForm) => ({
    id: `provider-${key}`,
    value: form[key],
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm((f) => ({ ...f, [key]: e.target.value })),
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const input = toInput(form);
    if (typeof input === "string") {
      setInvalid(input);
      return;
    }
    setInvalid(null);
    create.mutate(input);
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <Button onClick={show}><Plus className="h-4 w-4" /> Add provider</Button>
      <DialogContent title="Add provider" description="Register a new acquirer, gateway or corridor and its SLA targets.">
        <form onSubmit={submit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <Label htmlFor="provider-code">Code</Label>
              <Input {...field("code")} placeholder="ADYEN_EU" className="font-mono uppercase" required />
            </div>
            <div>
              <Label htmlFor="provider-name">Name</Label>
              <Input {...field("name")} placeholder="Adyen Europe" required />
            </div>
            <div>
              <Label htmlFor="provider-type">Type</Label>
              <Select {...field("type")} className="w-full">
                {PROVIDER_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
              </Select>
            </div>
            <div>
              <Label htmlFor="provider-region">Region</Label>
              <Input {...field("region")} placeholder="EU" />
            </div>
            <div>
              <Label htmlFor="provider-slaLatencyMs">SLA latency (ms)</Label>
              <Input {...field("slaLatencyMs")} type="number" min={1} step={1} inputMode="numeric" required />
            </div>
            <div>
              <Label htmlFor="provider-slaSuccessRate">SLA success rate (%)</Label>
              <Input {...field("slaSuccessRate")} type="number" min={0} max={100} step={0.01} inputMode="decimal" required />
            </div>
            <div>
              <Label htmlFor="provider-costBps">Cost (bps)</Label>
              <Input {...field("costBps")} type="number" min={0} step={0.01} inputMode="decimal" required />
            </div>
          </div>
          <ErrorNote message={invalid ?? (create.isError ? errorMessage(create.error) : null)} />
          <div className="flex justify-end gap-2">
            <DialogClose asChild>
              <Button type="button" variant="ghost">Cancel</Button>
            </DialogClose>
            <Button type="submit" loading={create.isPending}>Create provider</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
