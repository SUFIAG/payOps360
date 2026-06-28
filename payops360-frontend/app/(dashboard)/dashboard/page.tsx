"use client";

import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  TrendingUp,
  TrendingDown,
  DollarSign,
  Activity,
  AlertTriangle,
  Server,
  ArrowUpRight,
  ArrowDownRight,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { dashboardApi } from "@/lib/api";
import { formatCurrency } from "@/lib/utils";

export default function DashboardPage() {
  const { data: overview, isLoading } = useQuery({
    queryKey: ["dashboard-overview"],
    queryFn: () => dashboardApi.getOverview(),
    refetchInterval: 30000, // Refresh every 30 seconds
  });

  const stats = [
    {
      title: "Total Payments",
      value: overview?.totalPayments || 0,
      icon: <Activity className="text-blue-600" />,
      color: "bg-blue-50",
      change: "+12.5%",
      positive: true,
    },
    {
      title: "Success Rate",
      value: `${overview?.successRate?.toFixed(2) || 0}%`,
      icon: <TrendingUp className="text-green-600" />,
      color: "bg-green-50",
      change: "+2.3%",
      positive: true,
    },
    {
      title: "Total Amount",
      value: formatCurrency(overview?.totalAmount || 0),
      icon: <DollarSign className="text-brand-purple-600" />,
      color: "bg-brand-lavender-50",
      change: "+18.2%",
      positive: true,
    },
    {
      title: "Failed Payments",
      value: overview?.failedPayments || 0,
      icon: <AlertTriangle className="text-red-600" />,
      color: "bg-red-50",
      change: "-5.1%",
      positive: true,
    },
    {
      title: "Active Providers",
      value: overview?.activeProviders || 0,
      icon: <Server className="text-indigo-600" />,
      color: "bg-indigo-50",
      change: "+2",
      positive: true,
    },
    {
      title: "Active Alerts",
      value: overview?.activeAlerts || 0,
      icon: <AlertTriangle className="text-orange-600" />,
      color: "bg-orange-50",
      change: "-3",
      positive: true,
    },
  ];

  if (isLoading) {
    return (
      <div className="space-y-6">
        <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[...Array(6)].map((_, i) => (
            <Card key={i} className="animate-pulse">
              <CardHeader className="h-20 bg-gray-100"></CardHeader>
              <CardContent className="h-24 bg-gray-50"></CardContent>
            </Card>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-500 mt-1">Payment Operations Overview</p>
        </div>
        <div className="flex items-center gap-2 text-sm text-gray-500">
          <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
          Live Data
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {stats.map((stat, index) => (
          <motion.div
            key={stat.title}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.1 }}
          >
            <Card className="hover:shadow-lg transition-shadow border-l-4 border-brand-purple-600">
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium text-gray-600">
                  {stat.title}
                </CardTitle>
                <div className={`p-2 rounded-lg ${stat.color}`}>{stat.icon}</div>
              </CardHeader>
              <CardContent>
                <div className="flex items-end justify-between">
                  <div>
                    <div className="text-3xl font-bold text-gray-900">{stat.value}</div>
                    <div
                      className={`flex items-center gap-1 mt-2 text-sm ${
                        stat.positive ? "text-green-600" : "text-red-600"
                      }`}
                    >
                      {stat.positive ? (
                        <ArrowUpRight size={16} />
                      ) : (
                        <ArrowDownRight size={16} />
                      )}
                      <span className="font-medium">{stat.change}</span>
                      <span className="text-gray-500">vs last period</span>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        ))}
      </div>

      {/* Recent Activity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Recent Payments</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="text-sm text-gray-500 text-center py-8">
                Payment list will be displayed here
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>System Health</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-600">API Response Time</span>
                <span className="text-sm font-medium text-green-600">156ms</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-600">Database Latency</span>
                <span className="text-sm font-medium text-green-600">23ms</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-600">Provider Uptime</span>
                <span className="text-sm font-medium text-green-600">99.8%</span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

