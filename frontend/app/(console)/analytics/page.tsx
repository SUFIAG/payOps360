"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { EChartsOption } from "echarts";
import {
  Activity, BarChart3, Bot, FileText, Globe, PieChart, ShieldAlert, Sparkles, TrendingDown, TrendingUp, TriangleAlert, Zap,
} from "lucide-react";
import { aiApi, analyticsApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission } from "@/lib/auth/session";
import {
  change, cn, flag, formatDateTime, formatMoney, formatNumber, formatPercent, humanize, successRate,
} from "@/lib/utils";
import {
  Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, PageHeader, Segmented, Skeleton,
} from "@/components/ui/primitives";
import { EChart, palette } from "@/components/charts/echart";
import type { Breakdown, Kpi, ProviderPerformance, TimeBucket } from "@/types/api";

type Window = "PT1H" | "PT24H" | "P7D" | "P30D";

const WINDOWS: { value: Window; label: string; bucket: string }[] = [
  { value: "PT1H", label: "1h", bucket: "PT5M" },
  { value: "PT24H", label: "24h", bucket: "PT1H" },
  { value: "P7D", label: "7d", bucket: "PT6H" },
  { value: "P30D", label: "30d", bucket: "P1D" },
];

/** KPIs where an increase is bad news. */
const INVERSE_KPIS = new Set(["p95Latency", "revenueAtRisk", "highRisk", "stuck"]);

const RISK_COLORS: Record<string, string> = { LOW: "#17996f", MEDIUM: "#e0a33b", HIGH: "#e7773d", CRITICAL: "#e5484d" };

function bucketLabel(iso: string, range: Window) {
  const d = new Date(iso);
  if (range === "PT1H" || range === "PT24H") return d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
  if (range === "P7D") return d.toLocaleString("en-GB", { weekday: "short", hour: "2-digit", minute: "2-digit" });
  return d.toLocaleDateString("en-GB", { day: "2-digit", month: "short" });
}

function formatKpi(kpi: Kpi) {
  switch (kpi.unit) {
    case "USD": return formatMoney(kpi.value, "USD", true);
    case "%": return formatPercent(kpi.value, 2);
    case "ms": return `${formatNumber(kpi.value)} ms`;
    default: return formatNumber(kpi.value, kpi.value >= 100_000);
  }
}

export default function AnalyticsPage() {
  const [range, setRange] = useState<Window>("PT24H");
  const bucket = WINDOWS.find((w) => w.value === range)?.bucket ?? "PT1H";

  const overview = useQuery({ queryKey: ["analytics", "overview", range], queryFn: () => analyticsApi.overview(range) });
  const series = useQuery({ queryKey: ["analytics", "timeseries", range, bucket], queryFn: () => analyticsApi.timeseries(range, bucket) });
  const providers = useQuery({ queryKey: ["analytics", "providers", range], queryFn: () => analyticsApi.providers(range) });
  const methods = useQuery({ queryKey: ["analytics", "methods", range], queryFn: () => analyticsApi.methods(range) });
  const risk = useQuery({ queryKey: ["analytics", "risk", range], queryFn: () => analyticsApi.risk(range) });
  const failures = useQuery({ queryKey: ["analytics", "failure-reasons", range], queryFn: () => analyticsApi.failureReasons(range) });
  const corridors = useQuery({ queryKey: ["analytics", "corridors", range], queryFn: () => analyticsApi.corridors(range) });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Analytics"
        subtitle="Volume, conversion and provider performance across your payment estate."
        actions={<Segmented value={range} options={WINDOWS.map(({ value, label }) => ({ value, label }))} onChange={setRange} />}
      />

      <KpiStrip loading={overview.isLoading} kpis={overview.data?.kpis} error={overview.error} />

      <BriefingCard />

      <div className="grid gap-6 xl:grid-cols-3">
        <ChartCard
          className="xl:col-span-2"
          icon={<BarChart3 className="h-4 w-4" />}
          title="Volume & transactions"
          subtitle="USD processed and transaction count per interval"
          loading={series.isLoading}
          error={series.error}
          empty={!series.data?.length}
        >
          {series.data && <VolumeChart data={series.data} range={range} />}
        </ChartCard>
        <ChartCard
          icon={<Activity className="h-4 w-4" />}
          title="Success rate"
          subtitle="Succeeded ÷ completed, per interval"
          loading={series.isLoading}
          error={series.error}
          empty={!series.data?.length}
        >
          {series.data && <SuccessRateChart data={series.data} range={range} />}
        </ChartCard>
      </div>

      <ChartCard
        icon={<Zap className="h-4 w-4" />}
        title="Provider comparison"
        subtitle="Success rate vs p95 latency"
        loading={providers.isLoading}
        error={providers.error}
        empty={!providers.data?.length}
      >
        {providers.data && <ProviderChart data={providers.data} />}
      </ChartCard>

      <div className="grid gap-6 lg:grid-cols-2 xl:grid-cols-3">
        <ChartCard
          icon={<PieChart className="h-4 w-4" />}
          title="Payment method mix"
          subtitle="Share of transactions"
          loading={methods.isLoading}
          error={methods.error}
          empty={!methods.data?.length}
        >
          {methods.data && <DonutChart data={methods.data} />}
        </ChartCard>
        <ChartCard
          icon={<ShieldAlert className="h-4 w-4" />}
          title="Risk distribution"
          subtitle="Transactions by risk level"
          loading={risk.isLoading}
          error={risk.error}
          empty={!risk.data?.length}
        >
          {risk.data && <DonutChart data={risk.data} colors={RISK_COLORS} />}
        </ChartCard>
        <ChartCard
          className="lg:col-span-2 xl:col-span-1"
          icon={<TriangleAlert className="h-4 w-4" />}
          title="Top failure reasons"
          subtitle="Failed transactions by reason"
          loading={failures.isLoading}
          error={failures.error}
          empty={!failures.data?.length}
        >
          {failures.data && <FailureChart data={failures.data} />}
        </ChartCard>
      </div>

      <Card>
        <CardHeader icon={<Globe className="h-4 w-4" />} title="Corridors" subtitle="Performance by destination country" />
        <CardBody>
          {corridors.error ? (
            <ErrorNote message={errorMessage(corridors.error)} />
          ) : corridors.isLoading ? (
            <div className="space-y-2">{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-9 w-full" />)}</div>
          ) : !corridors.data?.length ? (
            <Empty title="No corridor data in this range" />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[520px] text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-[11px] uppercase tracking-wider text-muted">
                    <th className="py-2 pr-3 font-medium">Country</th>
                    <th className="py-2 pr-3 text-right font-medium">Payments</th>
                    <th className="py-2 pr-3 text-right font-medium">Volume (USD)</th>
                    <th className="py-2 font-medium">Success rate</th>
                  </tr>
                </thead>
                <tbody>
                  {corridors.data.map((c) => (
                    <tr key={c.country} className="border-b border-border/50 last:border-0">
                      <td className="py-2.5 pr-3">
                        <span className="mr-2 text-base">{flag(c.country)}</span>
                        <span className="font-mono">{c.country}</span>
                      </td>
                      <td className="py-2.5 pr-3 text-right font-mono">{formatNumber(c.count)}</td>
                      <td className="py-2.5 pr-3 text-right font-mono">{formatMoney(c.volumeUsd)}</td>
                      <td className="py-2.5">
                        <div className="flex items-center gap-2">
                          <div className="h-1.5 w-24 overflow-hidden rounded-full bg-surface-2">
                            <div
                              className={cn("h-full rounded-full", c.successRate >= 95 ? "bg-jade-500" : c.successRate >= 85 ? "bg-warning" : "bg-danger")}
                              style={{ width: `${Math.min(100, Math.max(0, c.successRate))}%` }}
                            />
                          </div>
                          <span className="font-mono text-xs">{formatPercent(c.successRate)}</span>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
}

// ---------------------------------------------------------------- KPI strip

function KpiStrip({ loading, kpis, error }: { loading: boolean; kpis?: Kpi[]; error: unknown }) {
  if (error) return <ErrorNote message={errorMessage(error)} />;
  if (loading || !kpis) {
    return (
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        {Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-2xl" />)}
      </div>
    );
  }
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
      {kpis.map((kpi) => {
        const delta = change(kpi.value, kpi.previous);
        const good = delta === undefined ? undefined : INVERSE_KPIS.has(kpi.key) ? delta <= 0 : delta >= 0;
        return (
          <Card key={kpi.key} className="p-4">
            <p className="text-[11px] uppercase tracking-wider text-muted">{kpi.label}</p>
            <p className="mt-1.5 font-display text-2xl font-semibold">{formatKpi(kpi)}</p>
            {delta === undefined ? (
              <p className="mt-1 text-xs text-muted">No prior period</p>
            ) : (
              <p className={cn("mt-1 inline-flex items-center gap-1 text-xs", good ? "text-jade-400" : "text-danger")}>
                {delta >= 0 ? <TrendingUp className="h-3.5 w-3.5" /> : <TrendingDown className="h-3.5 w-3.5" />}
                {delta >= 0 ? "+" : ""}{delta.toFixed(1)}%
                <span className="text-muted">vs prev.</span>
              </p>
            )}
          </Card>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------- AI briefing

function BriefingCard() {
  const canUseAi = usePermission("AI_USE");
  const briefing = useMutation({ mutationFn: aiApi.briefing });

  if (!canUseAi) return null;
  return (
    <Card className="relative overflow-hidden">
      <div className="pointer-events-none absolute -left-20 -top-20 h-48 w-48 rounded-full bg-jade-500/10 blur-3xl" aria-hidden />
      <CardHeader
        icon={<Sparkles className="h-4 w-4" />}
        title="AI Executive Briefing"
        subtitle={briefing.data ? `Generated ${formatDateTime(briefing.data.generatedAt)}` : "A concise narrative of performance, risks and actions"}
        actions={
          <Button size="sm" variant={briefing.data ? "outline" : "primary"} loading={briefing.isPending} onClick={() => briefing.mutate()}>
            {!briefing.isPending && <FileText className="h-4 w-4" />}
            {briefing.data ? "Regenerate" : "Generate briefing"}
          </Button>
        }
      />
      <CardBody>
        <ErrorNote message={briefing.error ? errorMessage(briefing.error) : null} />
        {briefing.isPending ? (
          <div className="space-y-2">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-3 w-11/12" />
            <Skeleton className="h-3 w-4/5" />
          </div>
        ) : briefing.data ? (
          <>
            <div className="prose-ai">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{briefing.data.markdown}</ReactMarkdown>
            </div>
            <div className="mt-4 border-t border-border pt-3">
              <Badge tone="brass"><Bot className="h-3 w-3" />{briefing.data.engine}</Badge>
            </div>
          </>
        ) : (
          !briefing.error && (
            <p className="text-sm text-muted">
              Generate an executive summary of the current operating picture, written by the PayOps360 insight engine.
            </p>
          )
        )}
      </CardBody>
    </Card>
  );
}

// ---------------------------------------------------------------- chart shell

function ChartCard({ icon, title, subtitle, loading, error, empty, className, children }: {
  icon: React.ReactNode; title: string; subtitle?: string; loading: boolean; error: unknown; empty: boolean;
  className?: string; children: React.ReactNode;
}) {
  return (
    <Card className={className}>
      <CardHeader icon={icon} title={title} subtitle={subtitle} />
      <CardBody>
        {error ? (
          <ErrorNote message={errorMessage(error)} />
        ) : loading ? (
          <Skeleton className="h-[260px] w-full" />
        ) : empty ? (
          <Empty title="No data in this range" className="h-[260px]" />
        ) : (
          children
        )}
      </CardBody>
    </Card>
  );
}

// ---------------------------------------------------------------- charts

function VolumeChart({ data, range }: { data: TimeBucket[]; range: Window }) {
  const option = useMemo<EChartsOption>(() => ({
    tooltip: { trigger: "axis" },
    legend: { data: ["Volume (USD)", "Transactions"] },
    xAxis: { type: "category", boundaryGap: true, data: data.map((b) => bucketLabel(b.bucket, range)), splitLine: { show: false } },
    yAxis: [
      { type: "value", name: "USD", axisLabel: { formatter: (v: number) => formatMoney(v, "USD", true) } },
      { type: "value", name: "Txns", splitLine: { show: false }, axisLabel: { formatter: (v: number) => formatNumber(v, true) } },
    ],
    series: [
      {
        name: "Volume (USD)",
        type: "line",
        smooth: true,
        showSymbol: false,
        data: data.map((b) => Math.round(b.volumeUsd)),
        lineStyle: { width: 2, color: palette[0] },
        itemStyle: { color: palette[0] },
        areaStyle: {
          color: {
            type: "linear", x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: "rgba(23,153,111,0.35)" }, { offset: 1, color: "rgba(23,153,111,0)" }],
          },
        },
        z: 3,
      },
      {
        name: "Transactions",
        type: "bar",
        yAxisIndex: 1,
        data: data.map((b) => b.total),
        barMaxWidth: 14,
        itemStyle: { color: "rgba(91,141,239,0.45)", borderRadius: [3, 3, 0, 0] },
      },
    ],
  }), [data, range]);
  return <EChart option={option} height={280} />;
}

function SuccessRateChart({ data, range }: { data: TimeBucket[]; range: Window }) {
  const option = useMemo<EChartsOption>(() => {
    const rates = data.map((b) => (b.succeeded + b.failed === 0 ? null : Number(successRate(b).toFixed(2))));
    const present = rates.filter((r): r is number => r !== null);
    const min = present.length ? Math.max(0, Math.floor(Math.min(...present) - 2)) : 0;
    return {
      tooltip: { trigger: "axis", valueFormatter: (v) => (typeof v === "number" ? `${v.toFixed(2)}%` : "—") },
      xAxis: { type: "category", data: data.map((b) => bucketLabel(b.bucket, range)), splitLine: { show: false } },
      yAxis: { type: "value", min, max: 100, axisLabel: { formatter: "{value}%" } },
      series: [{
        name: "Success rate",
        type: "line",
        smooth: true,
        showSymbol: false,
        connectNulls: true,
        data: rates,
        lineStyle: { width: 2, color: palette[0] },
        itemStyle: { color: palette[0] },
        markLine: {
          silent: true,
          symbol: "none",
          lineStyle: { type: "dashed", color: "#e0a33b" },
          label: { formatter: "95%", color: "#e0a33b" },
          data: [{ yAxis: 95 }],
        },
      }],
    };
  }, [data, range]);
  return <EChart option={option} height={280} />;
}

function ProviderChart({ data }: { data: ProviderPerformance[] }) {
  const option = useMemo<EChartsOption>(() => ({
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    legend: { data: ["Success rate %", "p95 latency (ms)"] },
    xAxis: { type: "category", data: data.map((p) => p.code), axisLabel: { interval: 0, rotate: data.length > 8 ? 30 : 0 } },
    yAxis: [
      { type: "value", name: "%", max: 100, axisLabel: { formatter: "{value}%" } },
      { type: "value", name: "ms", splitLine: { show: false } },
    ],
    series: [
      {
        name: "Success rate %",
        type: "bar",
        barMaxWidth: 22,
        data: data.map((p) => Number(p.successRate.toFixed(2))),
        itemStyle: { color: palette[0], borderRadius: [4, 4, 0, 0] },
      },
      {
        name: "p95 latency (ms)",
        type: "bar",
        yAxisIndex: 1,
        barMaxWidth: 22,
        data: data.map((p) => p.p95LatencyMs),
        itemStyle: { color: palette[3], borderRadius: [4, 4, 0, 0] },
      },
    ],
  }), [data]);
  return <EChart option={option} height={300} />;
}

function DonutChart({ data, colors }: { data: Breakdown[]; colors?: Record<string, string> }) {
  const option = useMemo<EChartsOption>(() => ({
    tooltip: { trigger: "item", formatter: "{b}: {c} ({d}%)" },
    legend: { type: "scroll", bottom: 0, top: "auto" },
    series: [{
      type: "pie",
      radius: ["52%", "76%"],
      center: ["50%", "45%"],
      avoidLabelOverlap: true,
      label: { show: false },
      itemStyle: { borderColor: "rgba(20,21,24,0.9)", borderWidth: 2, borderRadius: 4 },
      data: data.map((d, i) => ({
        name: humanize(d.label),
        value: d.count,
        itemStyle: { color: colors?.[d.label] ?? palette[i % palette.length] },
      })),
    }],
  }), [data, colors]);
  return <EChart option={option} height={260} />;
}

function FailureChart({ data }: { data: Breakdown[] }) {
  const option = useMemo<EChartsOption>(() => {
    const top = [...data].sort((a, b) => b.count - a.count).slice(0, 8).reverse();
    return {
      tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
      grid: { left: 8, right: 24, top: 8, bottom: 8, containLabel: true },
      xAxis: { type: "value" },
      yAxis: {
        type: "category",
        data: top.map((d) => humanize(d.label)),
        splitLine: { show: false },
        axisLabel: { width: 120, overflow: "truncate" },
      },
      series: [{
        name: "Failures",
        type: "bar",
        barMaxWidth: 14,
        data: top.map((d) => d.count),
        itemStyle: { color: "#e5484d", borderRadius: [0, 4, 4, 0] },
      }],
    };
  }, [data]);
  return <EChart option={option} height={260} />;
}
