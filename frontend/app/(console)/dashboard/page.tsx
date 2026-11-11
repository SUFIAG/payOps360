"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Bot, Route, Sparkles } from "lucide-react";
import { Button, Card, CardBody, CardHeader, PageHeader, Segmented, Skeleton } from "@/components/ui/primitives";
import { EChart } from "@/components/charts/echart";
import { AiInsightsPanel, HealthGauge, KpiTile, LiveFeed, ProviderMatrix } from "@/components/dashboard/widgets";
import { useCopilot } from "@/components/shell/copilot-drawer";
import { aiApi, analyticsApi, paymentsApi, providersApi } from "@/lib/api/endpoints";
import { useLive } from "@/lib/live";
import { useSession } from "@/lib/auth/session";
import { flag, formatMoney, formatPercent, successRate } from "@/lib/utils";

const WINDOWS = { PT1H: "PT5M", PT24H: "PT1H", P7D: "PT6H" } as const;
type Window = keyof typeof WINDOWS;

export default function CommandCenter() {
  const [window, setWindow] = useState<Window>("PT24H");
  const user = useSession((s) => s.user);
  const openCopilot = useCopilot((s) => s.setOpen);
  const liveFeed = useLive((s) => s.feed);

  const insights = useQuery({ queryKey: ["insights"], queryFn: aiApi.insights, refetchInterval: 20_000 });
  const overview = useQuery({ queryKey: ["overview", window], queryFn: () => analyticsApi.overview(window), refetchInterval: 20_000 });
  const series = useQuery({ queryKey: ["series", window], queryFn: () => analyticsApi.timeseries(window, WINDOWS[window]), refetchInterval: 30_000 });
  const providers = useQuery({ queryKey: ["providers"], queryFn: providersApi.list, refetchInterval: 30_000 });
  const failures = useQuery({ queryKey: ["failures", window], queryFn: () => analyticsApi.failureReasons(window) });
  const corridors = useQuery({ queryKey: ["corridors", window], queryFn: () => analyticsApi.corridors(window) });
  const recent = useQuery({ queryKey: ["recent-payments"], queryFn: () => paymentsApi.search({ size: 9 }) });

  const feed = useMemo(() => {
    const seen = new Set<string>();
    return [...liveFeed, ...(recent.data?.items ?? [])].filter((p) => !seen.has(p.reference) && seen.add(p.reference));
  }, [liveFeed, recent.data]);

  const trend = (key: string) => {
    const s = series.data ?? [];
    if (key === "volume") return s.map((b) => b.volumeUsd);
    if (key === "transactions") return s.map((b) => b.total);
    if (key === "successRate") return s.map((b) => successRate(b));
    if (key === "p95Latency") return s.map((b) => b.avgLatencyMs);
    return undefined;
  };

  const forecastOption = useMemo(() => {
    const s = series.data ?? [];
    const f = window === "PT24H" ? insights.data?.forecast ?? [] : [];
    const label = (iso: string) => new Date(iso).toLocaleString("en-GB", window === "P7D" ? { day: "2-digit", hour: "2-digit" } : { hour: "2-digit", minute: "2-digit" });
    const x = [...s.map((b) => label(b.bucket)), ...f.map((p) => label(p.at))];
    const pad = (n: number) => Array(n).fill(null);
    return {
      tooltip: { trigger: "axis" as const },
      legend: { data: ["Payments", "Success rate", "AI forecast"] },
      xAxis: [{ type: "category" as const, data: x, boundaryGap: false }],
      yAxis: [{ type: "value" as const, name: "payments" }, { type: "value" as const, name: "%", min: 80, max: 100, splitLine: { show: false } }],
      series: [
        { name: "Payments", type: "line" as const, smooth: true, symbol: "none", data: [...s.map((b) => b.total), ...pad(f.length)],
          lineStyle: { width: 2.5, color: "#17996f" },
          areaStyle: { color: { type: "linear" as const, x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: "rgba(23,153,111,0.35)" }, { offset: 1, color: "rgba(23,153,111,0)" }] } } },
        { name: "Success rate", type: "line" as const, yAxisIndex: 1, smooth: true, symbol: "none", lineStyle: { width: 1.5, color: "#5b8def" },
          data: [...s.map((b) => (b.succeeded + b.failed ? Number(successRate(b).toFixed(2)) : null)), ...pad(f.length)] },
        { name: "Band low", type: "line" as const, stack: "band", symbol: "none", lineStyle: { opacity: 0 }, data: [...pad(s.length), ...f.map((p) => p.lower)], tooltip: { show: false } },
        { name: "Band", type: "line" as const, stack: "band", symbol: "none", lineStyle: { opacity: 0 }, areaStyle: { color: "rgba(201,163,94,0.12)" },
          data: [...pad(s.length), ...f.map((p) => p.upper - p.lower)], tooltip: { show: false } },
        { name: "AI forecast", type: "line" as const, smooth: true, symbol: "circle", symbolSize: 4, lineStyle: { type: "dashed" as const, width: 2, color: "#c9a35e" },
          data: [...pad(Math.max(0, s.length - 1)), ...(s.length ? [s[s.length - 1].total] : []), ...f.map((p) => p.expected)] },
      ],
    };
  }, [series.data, insights.data, window]);

  const failureOption = useMemo(() => ({
    tooltip: { trigger: "item" as const },
    legend: { show: false },
    series: [{ type: "pie" as const, radius: ["55%", "80%"], itemStyle: { borderColor: "transparent", borderRadius: 4 },
      label: { color: "inherit", fontSize: 10, formatter: "{b}" },
      data: (failures.data ?? []).slice(0, 6).map((f) => ({ name: f.label.replace(/_/g, " "), value: f.count })) }],
  }), [failures.data]);

  const kpis = overview.data?.kpis ?? [];
  const primary = ["volume", "transactions", "successRate", "p95Latency"];
  const secondary = ["revenueAtRisk", "highRisk", "stuck", "customers"];

  return (
    <>
      <PageHeader
        title={`Good ${new Date().getHours() < 12 ? "morning" : new Date().getHours() < 18 ? "afternoon" : "evening"}, ${user?.fullName.split(" ")[0] ?? ""}`}
        subtitle="Your payment operations at a glance — monitored continuously by PayOps AI."
        actions={<>
          <Segmented value={window} onChange={setWindow} options={[{ value: "PT1H", label: "1H" }, { value: "PT24H", label: "24H" }, { value: "P7D", label: "7D" }]} />
          <Button variant="outline" size="sm" onClick={() => openCopilot(true)}><Sparkles className="h-4 w-4" /> Ask AI</Button>
        </>}
      />

      <div className="grid gap-4 xl:grid-cols-[1.25fr_1fr]">
        {insights.data ? <HealthGauge insights={insights.data} /> : <Skeleton className="h-52" />}
        <div className="grid grid-cols-2 gap-3">
          {kpis.length === 0 && Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-28" />)}
          {kpis.filter((k) => primary.includes(k.key)).map((k) => <KpiTile key={k.key} kpi={k} trend={trend(k.key)} />)}
        </div>
      </div>

      <div className="mt-3 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {kpis.filter((k) => secondary.includes(k.key)).map((k) => <KpiTile key={k.key} kpi={k} />)}
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-[2fr_1fr]">
        <Card>
          <CardHeader title="Traffic, success rate & AI forecast" subtitle={window === "PT24H" ? "Next 12 hours forecast with 80% confidence band" : "Volume and authorisation success"} />
          <CardBody>{series.data ? <EChart option={forecastOption} height={300} /> : <Skeleton className="h-72" />}</CardBody>
        </Card>
        <div className="h-[380px]"><LiveFeed payments={feed} /></div>
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-[1.4fr_1fr]">
        {providers.data ? <ProviderMatrix providers={providers.data.filter((p) => p.active)} /> : <Skeleton className="h-80" />}
        {insights.data ? <AiInsightsPanel anomalies={insights.data.anomalies} recommendations={insights.data.recommendations} /> : <Skeleton className="h-80" />}
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader title="Smart routing leaderboard" subtitle="AI ranking: success · latency · cost" icon={<Route className="h-4 w-4" />} />
          <CardBody className="space-y-2.5">
            {(insights.data?.routing ?? []).slice(0, 6).map((r, i) => (
              <div key={r.code}>
                <div className="flex items-center justify-between text-xs">
                  <span><span className="mr-2 font-mono text-muted">#{i + 1}</span>{r.name}</span>
                  <span className="font-semibold text-jade-400">{r.score.toFixed(0)}</span>
                </div>
                <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-surface-2">
                  <div className="h-full rounded-full bg-gradient-to-r from-jade-600 to-jade-400" style={{ width: `${r.score}%` }} />
                </div>
              </div>
            ))}
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="Failure drivers" subtitle="Top decline and error codes" />
          <CardBody>{failures.data?.length ? <EChart option={failureOption} height={220} /> : <Skeleton className="h-52" />}</CardBody>
        </Card>
        <Card>
          <CardHeader title="Top corridors" subtitle="Destination volume (USD, indicative FX)" />
          <CardBody className="space-y-2">
            {(corridors.data ?? []).slice(0, 7).map((c) => (
              <div key={c.country} className="flex items-center gap-3 text-sm">
                <span className="text-lg">{flag(c.country)}</span>
                <span className="w-8 font-mono text-xs text-muted">{c.country}</span>
                <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-2">
                  <div className="h-full rounded-full bg-info" style={{ width: `${(c.volumeUsd / (corridors.data?.[0]?.volumeUsd || 1)) * 100}%` }} />
                </div>
                <span className="w-20 text-right text-xs">{formatMoney(c.volumeUsd, "USD", true)}</span>
                <span className="w-12 text-right text-xs text-muted">{formatPercent(c.successRate)}</span>
              </div>
            ))}
          </CardBody>
        </Card>
      </div>

      <button onClick={() => openCopilot(true)}
        className="fixed bottom-6 right-6 z-30 flex h-14 w-14 items-center justify-center rounded-full bg-jade-600 text-white shadow-[var(--shadow-card-hover)] transition-transform hover:scale-105 cursor-pointer lg:hidden"
        aria-label="Open AI copilot">
        <Bot className="h-6 w-6" />
      </button>
    </>
  );
}
