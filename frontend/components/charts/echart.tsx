"use client";

import dynamic from "next/dynamic";
import type { EChartsOption } from "echarts";
import { useTheme } from "@/components/shell/theme";

const ReactECharts = dynamic(() => import("echarts-for-react"), { ssr: false });

// Jade, sapphire, brass, plum, amber, coral, slate, sage — distinct but muted, so charts read as one system.
export const palette = ["#17996f", "#5b8def", "#c9a35e", "#8e6bbf", "#e0a33b", "#e5484d", "#7b8794", "#8fbfa8"];

/** ECharts wrapper applying the PayOps360 brand theme for dark and light mode. */
export function EChart({ option, height = 260, className }: { option: EChartsOption; height?: number; className?: string }) {
  const { theme } = useTheme();
  const dark = theme === "dark";
  const text = dark ? "#a5a29a" : "#6b6861";
  const grid = dark ? "rgba(255,255,255,0.07)" : "rgba(26,27,31,0.07)";
  const themed: EChartsOption = {
    color: palette,
    backgroundColor: "transparent",
    textStyle: { color: text, fontFamily: "var(--font-body), sans-serif" },
    grid: { left: 8, right: 12, top: 28, bottom: 8, containLabel: true },
    animationDuration: 700,
    animationEasing: "cubicOut",
    tooltip: {
      backgroundColor: dark ? "rgba(32,34,39,0.97)" : "#fff",
      borderColor: dark ? "#34373e" : "#e5e0d6",
      padding: [8, 12],
      extraCssText: "border-radius:10px;box-shadow:0 12px 28px -10px rgba(0,0,0,0.35);",
      textStyle: { color: dark ? "#eeece7" : "#1a1b1f", fontSize: 12 },
    },
    legend: { textStyle: { color: text }, top: 0, icon: "roundRect", itemWidth: 10, itemHeight: 6 },
    ...option,
  };
  const axisDefaults = (axis: unknown) =>
    Array.isArray(axis)
      ? axis.map((a) => ({ axisLine: { lineStyle: { color: grid } }, splitLine: { lineStyle: { color: grid } }, axisLabel: { color: text }, ...a }))
      : axis && { axisLine: { lineStyle: { color: grid } }, splitLine: { lineStyle: { color: grid } }, axisLabel: { color: text }, ...(axis as object) };
  if (option.xAxis) themed.xAxis = axisDefaults(option.xAxis) as EChartsOption["xAxis"];
  if (option.yAxis) themed.yAxis = axisDefaults(option.yAxis) as EChartsOption["yAxis"];
  return <ReactECharts option={themed} style={{ height }} className={className} notMerge lazyUpdate />;
}
