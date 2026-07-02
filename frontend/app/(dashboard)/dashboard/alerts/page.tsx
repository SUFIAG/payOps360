"use client";

import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { AlertTriangle } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { alertApi } from "@/lib/api";
import { formatDate, getStatusColor } from "@/lib/utils";

export default function AlertsPage() {
  const { data: alertsData, isLoading } = useQuery({
    queryKey: ["alerts"],
    queryFn: () => alertApi.getAlerts({ page: 0, size: 50 }),
    refetchInterval: 10000,
  });

  const alerts = alertsData?.content || [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Alerts</h1>
        <p className="text-gray-600 mt-1">Monitor and manage system alerts</p>
      </div>

      <Card>
        <CardContent className="p-6">
          {isLoading ? (
            <div className="space-y-4">
              {[...Array(5)].map((_, i) => (
                <div key={i} className="h-20 bg-gray-100 rounded animate-pulse"></div>
              ))}
            </div>
          ) : alerts.length === 0 ? (
            <div className="text-center py-12">
              <AlertTriangle className="mx-auto text-gray-400 mb-4" size={48} />
              <p className="text-gray-600">No active alerts</p>
            </div>
          ) : (
            <div className="space-y-4">
              {alerts.map((alert, index) => (
                <motion.div
                  key={alert.id}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: index * 0.05 }}
                  className="flex items-start gap-4 p-4 border border-gray-200 rounded-lg hover:border-purple-600 transition-colors bg-white"
                >
                  <div
                    className={`p-2 rounded-lg ${
                      alert.severity === "CRITICAL"
                        ? "bg-red-100"
                        : alert.severity === "HIGH"
                        ? "bg-yellow-100"
                        : "bg-blue-100"
                    }`}
                  >
                    <AlertTriangle
                      className={
                        alert.severity === "CRITICAL"
                          ? "text-red-600"
                          : alert.severity === "HIGH"
                          ? "text-yellow-600"
                          : "text-blue-600"
                      }
                      size={24}
                    />
                  </div>
                  <div className="flex-1">
                    <div className="flex items-start justify-between">
                      <div>
                        <h3 className="font-semibold text-gray-900">{alert.title}</h3>
                        <p className="text-sm text-gray-600 mt-1">{alert.description}</p>
                        <p className="text-xs text-gray-500 mt-2">
                          {formatDate(alert.createdAt, "relative")}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge className={getStatusColor(alert.severity)}>
                          {alert.severity}
                        </Badge>
                        <Badge variant="outline">{alert.status}</Badge>
                      </div>
                    </div>
                    {alert.status === "NEW" && (
                      <div className="mt-3 flex gap-2">
                        <Button size="sm">Acknowledge</Button>
                        <Button size="sm" variant="outline">
                          Resolve
                        </Button>
                      </div>
                    )}
                  </div>
                </motion.div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

