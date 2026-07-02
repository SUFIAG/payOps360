"use client";

import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { BarChart3, TrendingUp, Activity, DollarSign, AlertTriangle } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { analyticsApi } from "@/lib/api";
import ReactECharts from "echarts-for-react";
import { formatCurrency } from "@/lib/utils";

export default function AnalyticsPage() {
  const { data: paymentTrends, isLoading: trendsLoading } = useQuery({
    queryKey: ["payment-trends"],
    queryFn: () => analyticsApi.getPaymentTrends("7d"),
    refetchInterval: 30000,
  });

  const { data: providerPerformance, isLoading: performanceLoading } = useQuery({
    queryKey: ["provider-performance"],
    queryFn: () => analyticsApi.getProviderPerformance(),
    refetchInterval: 60000,
  });

  // Payment Trends Chart Configuration
  const paymentTrendsOption = {
    tooltip: {
      trigger: "axis",
      backgroundColor: "rgba(255, 255, 255, 0.95)",
      borderColor: "#e5e7eb",
      borderWidth: 1,
      textStyle: { color: "#374151" },
    },
    legend: {
      data: ["Successful", "Failed"],
      bottom: 0,
    },
    grid: {
      left: "3%",
      right: "4%",
      bottom: "12%",
      top: "10%",
      containLabel: true,
    },
    xAxis: {
      type: "category",
      boundaryGap: false,
      data: paymentTrends?.map((t) => new Date(t.timestamp).toLocaleDateString("en-US", { month: "short", day: "numeric" })) || [],
      axisLine: { lineStyle: { color: "#e5e7eb" } },
      axisLabel: { color: "#6b7280" },
    },
    yAxis: {
      type: "value",
      axisLine: { lineStyle: { color: "#e5e7eb" } },
      axisLabel: { color: "#6b7280" },
      splitLine: { lineStyle: { color: "#f3f4f6" } },
    },
    series: [
      {
        name: "Successful",
        type: "line",
        smooth: true,
        data: paymentTrends?.map((t) => t.successfulPayments) || [],
        itemStyle: { color: "#22c55e" },
        areaStyle: { color: "rgba(34, 197, 94, 0.1)" },
      },
      {
        name: "Failed",
        type: "line",
        smooth: true,
        data: paymentTrends?.map((t) => t.failedPayments) || [],
        itemStyle: { color: "#ef4444" },
        areaStyle: { color: "rgba(239, 68, 68, 0.1)" },
      },
    ],
  };

  // Provider Performance Chart Configuration
  const providerPerformanceOption = {
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      backgroundColor: "rgba(255, 255, 255, 0.95)",
      borderColor: "#e5e7eb",
      borderWidth: 1,
      textStyle: { color: "#374151" },
    },
    legend: {
      data: ["Success Rate", "Avg Latency (ms)"],
      bottom: 0,
    },
    grid: {
      left: "3%",
      right: "4%",
      bottom: "12%",
      top: "10%",
      containLabel: true,
    },
    xAxis: {
      type: "category",
      data: providerPerformance?.map((p) => p.providerName) || [],
      axisLine: { lineStyle: { color: "#e5e7eb" } },
      axisLabel: { color: "#6b7280", rotate: 45 },
    },
    yAxis: [
      {
        type: "value",
        name: "Success Rate (%)",
        min: 0,
        max: 100,
        axisLine: { lineStyle: { color: "#e5e7eb" } },
        axisLabel: { color: "#6b7280", formatter: "{value}%" },
        splitLine: { lineStyle: { color: "#f3f4f6" } },
      },
      {
        type: "value",
        name: "Latency (ms)",
        axisLine: { lineStyle: { color: "#e5e7eb" } },
        axisLabel: { color: "#6b7280" },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: "Success Rate",
        type: "bar",
        data: providerPerformance?.map((p) => p.successRate) || [],
        itemStyle: { color: "#7c3aed" },
        yAxisIndex: 0,
      },
      {
        name: "Avg Latency (ms)",
        type: "line",
        data: providerPerformance?.map((p) => p.avgLatency) || [],
        itemStyle: { color: "#f59e0b" },
        yAxisIndex: 1,
      },
    ],
  };

  // Calculate summary metrics
  const totalPayments =
    paymentTrends?.reduce((sum, t) => sum + t.successfulPayments + t.failedPayments, 0) || 0;
  const totalSuccessful = paymentTrends?.reduce((sum, t) => sum + t.successfulPayments, 0) || 0;
  const totalFailed = paymentTrends?.reduce((sum, t) => sum + t.failedPayments, 0) || 0;
  const overallSuccessRate = totalPayments > 0 ? (totalSuccessful / totalPayments) * 100 : 0;

  const avgProviderSuccessRate =
    (providerPerformance?.reduce((sum, p) => sum + p.successRate, 0) || 0) /
      (providerPerformance?.length || 1);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Analytics</h1>
        <p className="text-gray-600 mt-1">Payment trends and performance metrics</p>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
          <Card className="border-l-4 border-purple-600">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-gray-600 flex items-center gap-2">
                <Activity size={16} />
                Total Payments (7d)
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-3xl font-bold text-gray-900">{totalPayments.toLocaleString()}</p>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
        >
          <Card className="border-l-4 border-green-600">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-gray-600 flex items-center gap-2">
                <TrendingUp size={16} />
                Success Rate
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-3xl font-bold text-green-600">
                {overallSuccessRate.toFixed(1)}%
              </p>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <Card className="border-l-4 border-red-600">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-gray-600 flex items-center gap-2">
                <AlertTriangle size={16} />
                Failed Payments
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-3xl font-bold text-red-600">{totalFailed.toLocaleString()}</p>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
        >
          <Card className="border-l-4 border-yellow-500">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-gray-600 flex items-center gap-2">
                <BarChart3 size={16} />
                Avg Provider Rate
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-3xl font-bold text-yellow-600">
                {avgProviderSuccessRate.toFixed(1)}%
              </p>
            </CardContent>
          </Card>
        </motion.div>
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 gap-6">
        {/* Payment Trends Chart */}
        <Card>
          <CardHeader>
            <CardTitle>Payment Trends (Last 7 Days)</CardTitle>
          </CardHeader>
          <CardContent>
            {trendsLoading ? (
              <div className="h-80 flex items-center justify-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600"></div>
              </div>
            ) : (
              <ReactECharts option={paymentTrendsOption} style={{ height: "400px" }} />
            )}
          </CardContent>
        </Card>

        {/* Provider Performance Chart */}
        <Card>
          <CardHeader>
            <CardTitle>Provider Performance Comparison</CardTitle>
          </CardHeader>
          <CardContent>
            {performanceLoading ? (
              <div className="h-80 flex items-center justify-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600"></div>
              </div>
            ) : (
              <ReactECharts option={providerPerformanceOption} style={{ height: "400px" }} />
            )}
          </CardContent>
        </Card>
      </div>

      {/* Provider Performance Table */}
      <Card>
        <CardHeader>
          <CardTitle>Provider Performance Details</CardTitle>
        </CardHeader>
        <CardContent>
          {performanceLoading ? (
            <div className="space-y-2">
              {[...Array(5)].map((_, i) => (
                <div key={i} className="h-12 bg-gray-100 rounded animate-pulse"></div>
              ))}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-200">
                    <th className="text-left py-3 px-4 text-sm font-semibold text-gray-900">
                      Provider
                    </th>
                    <th className="text-right py-3 px-4 text-sm font-semibold text-gray-900">
                      Total Payments
                    </th>
                    <th className="text-right py-3 px-4 text-sm font-semibold text-gray-900">
                      Success Rate
                    </th>
                    <th className="text-right py-3 px-4 text-sm font-semibold text-gray-900">
                      Avg Latency
                    </th>
                    <th className="text-right py-3 px-4 text-sm font-semibold text-gray-900">
                      Total Payments
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {providerPerformance?.map((provider, index) => (
                    <motion.tr
                      key={provider.providerId}
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      transition={{ delay: index * 0.05 }}
                      className="border-b border-gray-100 hover:bg-gray-50"
                    >
                      <td className="py-3 px-4 text-sm font-medium text-gray-900">
                        {provider.providerName}
                      </td>
                      <td className="py-3 px-4 text-sm text-gray-900 text-right">
                        {provider.totalPayments.toLocaleString()}
                      </td>
                      <td className="py-3 px-4 text-sm text-right">
                        <span
                          className={`font-semibold ${
                            provider.successRate >= 95
                              ? "text-green-600"
                              : provider.successRate >= 85
                              ? "text-yellow-600"
                              : "text-red-600"
                          }`}
                        >
                          {provider.successRate.toFixed(2)}%
                        </span>
                      </td>
                      <td className="py-3 px-4 text-sm text-gray-900 text-right">
                        {provider.avgLatency.toFixed(0)} ms
                      </td>
                      <td className="py-3 px-4 text-sm text-gray-900 text-right font-medium">
                        {provider.totalPayments.toLocaleString()}
                      </td>
                    </motion.tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}



