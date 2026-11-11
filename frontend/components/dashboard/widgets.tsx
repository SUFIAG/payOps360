"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { AnimatePresence, animate, motion } from "framer-motion";
import { ArrowDownRight, ArrowUpRight, Brain, Lightbulb, TrendingUp } from "lucide-react";
import { Badge, Card, CardBody, CardHeader, Empty } from "@/components/ui/primitives";
import { HealthDot, PaymentStatusBadge, RiskBadge, SeverityBadge } from "@/components/ui/status";
import { EChart } from "@/components/charts/echart";
import { cn, flag, formatMoney, formatNumber, formatPercent, timeAgo } from "@/lib/utils";
import type { Anomaly, Insights, Kpi, Payment, Provider, Recommendation } from "@/types/api";

export function AnimatedNumber({ value, format }: { value: number; format: (v: number) => string }) {
  const [display, setDisplay] = useState(value);
  const previous = useRef(value);
  useEffect(() => {
    const controls = animate(previous.current, value, { duration: 0.9, ease: "easeOut", onUpdate: setDisplay });
    previous.current = value;
    return () => controls.stop();
  }, [value]);
  return <>{format(display)}</>;
}

const STATUS_COLOR: Record<Insights["healthStatus"], string> = {
  OPTIMAL: "#17996f", WATCH: "#5b8def", DEGRADED: "#e0a33b", CRITICAL: "#e5484d",
};

/** AI operations health score with a glowing radial gauge. */
export function HealthGauge({ insights }: { insights: Insights }) {
  const color = STATUS_COLOR[insights.healthStatus];
  const radius = 70;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - insights.healthScore / 100);
  return (
    <Card className="relative overflow-hidden">
      <div className="pointer-events-none absolute -right-10 -top-10 h-48 w-48 rounded-full blur-3xl" style={{ background: `${color}33` }} />
      <CardBody className="flex flex-col gap-5 sm:flex-row sm:items-center">
        <div className="relative mx-auto h-44 w-44 shrink-0">
          <svg viewBox="0 0 180 180" className="h-full w-full -rotate-90">
            <circle cx="90" cy="90" r={radius} stroke="var(--border)" strokeWidth="12" fill="none" />
            <motion.circle
              cx="90" cy="90" r={radius} stroke={color} strokeWidth="12" fill="none" strokeLinecap="round"
              strokeDasharray={circumference}
              initial={{ strokeDashoffset: circumference }}
              animate={{ strokeDashoffset: offset }}
              transition={{ duration: 1.2, ease: "easeOut" }}
              style={{ filter: `drop-shadow(0 0 8px ${color})` }}
            />
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span className="font-display text-5xl font-bold" style={{ color }}>
              <AnimatedNumber value={insights.healthScore} format={(v) => Math.round(v).toString()} />
            </span>
            <span className="text-[10px] uppercase tracking-[0.25em] text-muted">Ops health</span>
          </div>
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <Badge tone={insights.healthStatus === "OPTIMAL" ? "jade" : insights.healthStatus === "WATCH" ? "blue" : insights.healthStatus === "DEGRADED" ? "amber" : "red"} dot>
              {insights.healthStatus}
            </Badge>
            <span className="flex items-center gap-1 text-[11px] text-muted"><Brain className="h-3 w-3" /> {insights.engine}</span>
          </div>
          <p className="mt-3 text-lg leading-snug">{insights.headline}</p>
          <div className="mt-4 flex gap-6 text-xs text-muted">
            <span><b className="text-foreground">{insights.anomalies.length}</b> anomalies</span>
            <span><b className="text-foreground">{insights.recommendations.length}</b> AI actions</span>
            <span>updated {timeAgo(insights.generatedAt)}</span>
          </div>
        </div>
      </CardBody>
    </Card>
  );
}

const KPI_FORMAT: Record<string, (v: number) => string> = {
  USD: (v) => formatMoney(v, "USD", true),
  "%": (v) => formatPercent(v, 2),
  ms: (v) => `${Math.round(v)} ms`,
  count: (v) => formatNumber(Math.round(v)),
};
/** Metrics where a decrease is good news. */
const LOWER_IS_BETTER = new Set(["p95Latency", "revenueAtRisk", "highRisk", "stuck"]);

export function KpiTile({ kpi, trend }: { kpi: Kpi; trend?: number[] }) {
  const change = kpi.previous ? ((kpi.value - kpi.previous) * 100) / Math.abs(kpi.previous) : undefined;
  const good = change === undefined ? undefined : LOWER_IS_BETTER.has(kpi.key) ? change <= 0 : change >= 0;
  const fmt = KPI_FORMAT[kpi.unit] ?? KPI_FORMAT.count;
  return (
    <Card className="group relative overflow-hidden p-4 transition-colors hover:border-jade-500/40">
      <p className="text-[11px] font-medium uppercase tracking-wider text-muted">{kpi.label}</p>
      <p className="mt-2 font-display text-2xl font-semibold"><AnimatedNumber value={kpi.value} format={fmt} /></p>
      <div className="mt-1 flex items-center justify-between">
        {change !== undefined ? (
          <span className={cn("flex items-center text-xs font-medium", good ? "text-jade-400" : "text-danger")}>
            {change >= 0 ? <ArrowUpRight className="h-3.5 w-3.5" /> : <ArrowDownRight className="h-3.5 w-3.5" />}
            {Math.abs(change).toFixed(1)}%
          </span>
        ) : <span className="text-xs text-muted">—</span>}
        <span className="text-[10px] text-muted">vs prev.</span>
      </div>
      {trend && trend.length > 1 && (
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-10 opacity-60">
          <EChart height={40} option={{
            grid: { left: 0, right: 0, top: 0, bottom: 0 }, xAxis: { type: "category", show: false, data: trend.map((_, i) => i) },
            yAxis: { type: "value", show: false, scale: true }, tooltip: { show: false }, legend: { show: false },
            series: [{ type: "line", data: trend, smooth: true, symbol: "none", lineStyle: { width: 1.5, color: "#17996f" },
              areaStyle: { color: { type: "linear", x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: "rgba(23,153,111,0.35)" }, { offset: 1, color: "rgba(23,153,111,0)" }] } } }],
          }} />
        </div>
      )}
    </Card>
  );
}

export function LiveFeed({ payments }: { payments: Payment[] }) {
  return (
    <Card className="flex h-full flex-col">
      <CardHeader title="Live transaction stream" subtitle="Real-time via secure event stream"
        actions={<span className="flex items-center gap-1.5 text-[11px] text-jade-400"><span className="h-2 w-2 animate-pulse rounded-full bg-jade-400" /> LIVE</span>} />
      <div className="mt-3 min-h-0 flex-1 overflow-hidden px-3 pb-3">
        {payments.length === 0 && <Empty title="Waiting for payments…" hint="New payments appear here the moment they are ingested." />}
        <ul className="space-y-1.5">
          <AnimatePresence initial={false}>
            {payments.slice(0, 9).map((p) => (
              <motion.li key={p.reference} initial={{ opacity: 0, x: -12, backgroundColor: "rgba(23,153,111,0.15)" }}
                animate={{ opacity: 1, x: 0, backgroundColor: "rgba(0,0,0,0)" }} transition={{ duration: 0.6 }} layout className="rounded-lg">
                <Link href={`/payments/${p.reference}`} className="flex items-center gap-3 rounded-lg px-2 py-1.5 hover:bg-surface-2">
                  <span className="text-lg leading-none">{flag(p.country)}</span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-mono text-xs">{p.reference}</p>
                    <p className="text-[11px] text-muted">{p.providerName} · {p.method.replace("_", " ").toLowerCase()}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-medium">{formatMoney(p.amount, p.currency)}</p>
                    <div className="flex justify-end gap-1">
                      {p.riskLevel !== "LOW" && <RiskBadge level={p.riskLevel} />}
                      <PaymentStatusBadge status={p.status} />
                    </div>
                  </div>
                </Link>
              </motion.li>
            ))}
          </AnimatePresence>
        </ul>
      </div>
    </Card>
  );
}

export function ProviderMatrix({ providers }: { providers: Provider[] }) {
  return (
    <Card>
      <CardHeader title="Provider health matrix" subtitle="15-minute rolling window vs contracted SLA"
        actions={<Link href="/providers" className="text-xs text-jade-400 hover:underline">All providers →</Link>} />
      <CardBody className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
        {providers.map((p) => {
          const sr = p.health.successRate ?? 0;
          const belowSla = p.health.status !== "UNKNOWN" && sr < p.slaSuccessRate;
          return (
            <div key={p.code} className={cn("rounded-xl border p-3 transition-colors",
              p.health.status === "CRITICAL" ? "border-danger/40 bg-danger/5" : p.health.status === "DEGRADED" ? "border-warning/40 bg-warning/5" : "border-border bg-surface-2")}>
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-2 text-sm font-medium"><HealthDot status={p.health.status} /> {p.name}</span>
                <span className="text-[10px] uppercase text-muted">{p.health.status}</span>
              </div>
              <div className="mt-2 grid grid-cols-3 gap-2 text-xs">
                <div><p className="text-muted">Success</p><p className={cn("font-semibold", belowSla && "text-danger")}>{p.health.status === "UNKNOWN" ? "—" : formatPercent(sr)}</p></div>
                <div><p className="text-muted">p95</p><p className="font-semibold">{p.health.p95LatencyMs ? `${p.health.p95LatencyMs}ms` : "—"}</p></div>
                <div><p className="text-muted">Volume</p><p className="font-semibold">{p.health.volume}</p></div>
              </div>
              <div className="mt-2 h-1 overflow-hidden rounded-full bg-[var(--bg)]">
                <div className={cn("h-full rounded-full", belowSla ? "bg-danger" : "bg-jade-500")} style={{ width: `${Math.min(100, sr)}%` }} />
              </div>
            </div>
          );
        })}
      </CardBody>
    </Card>
  );
}

export function AiInsightsPanel({ anomalies, recommendations }: { anomalies: Anomaly[]; recommendations: Recommendation[] }) {
  return (
    <Card className="relative overflow-hidden">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-jade-400 to-transparent" />
      <CardHeader title="AI insights & next best actions" subtitle="Anomaly detection + prescriptive recommendations" icon={<Lightbulb className="h-4 w-4" />} />
      <CardBody className="space-y-3">
        {anomalies.length === 0 && recommendations.length === 0 && (
          <Empty icon={<TrendingUp className="h-6 w-6" />} title="No anomalies detected" hint="The AI is continuously scanning provider failure rates, latency and volume." />
        )}
        {anomalies.slice(0, 3).map((a, i) => (
          <motion.div key={`${a.type}-${a.providerCode}`} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.08 }}
            className="rounded-xl border border-danger/30 bg-danger/5 p-3">
            <div className="flex items-center gap-2">
              <SeverityBadge severity={a.severity} />
              <span className="text-xs font-medium">{a.type.replace(/_/g, " ")} · {a.providerName}</span>
            </div>
            <p className="mt-1.5 text-sm text-muted">{a.explanation}</p>
          </motion.div>
        ))}
        {recommendations.map((r, i) => (
          <motion.div key={r.title} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 + i * 0.06 }}
            className="rounded-xl border border-border bg-surface-2 p-3">
            <div className="flex items-center gap-2">
              <Badge tone="jade">P{r.priority}</Badge>
              <Badge tone="ink">{r.category}</Badge>
              <span className="truncate text-sm font-medium">{r.title}</span>
            </div>
            <p className="mt-1.5 text-xs text-muted">{r.detail}</p>
            <p className="mt-1.5 text-xs font-medium text-jade-400">→ {r.action}</p>
          </motion.div>
        ))}
      </CardBody>
    </Card>
  );
}
