"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  Activity,
  AlertTriangle,
  CheckCircle,
  Clock,
  TrendingUp,
  Users,
  ArrowUpCircle,
  Search,
  Filter,
  Download,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { incidentApi } from "@/lib/api";
import { formatDate, cn } from "@/lib/utils";
import { toast } from "sonner";
import type { Incident } from "@/types";

export default function IncidentsPage() {
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const queryClient = useQueryClient();

  const { data: incidentsData, isLoading } = useQuery({
    queryKey: ["incidents", statusFilter],
    queryFn: () => incidentApi.getIncidents({
      page: 0,
      size: 50,
      status: statusFilter !== "ALL" ? statusFilter : undefined
    }),
    refetchInterval: 15000, // Refresh every 15 seconds
  });

  const acknowledgeMutation = useMutation({
    mutationFn: (id: string) => incidentApi.acknowledgeIncident(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incidents"] });
      toast.success("Incident acknowledged");
    },
  });

  const resolveMutation = useMutation({
    mutationFn: ({ id, resolution }: { id: string; resolution: string }) =>
      incidentApi.resolveIncident(id, { resolutionNotes: resolution }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incidents"] });
      toast.success("Incident resolved");
    },
  });

  const incidents = incidentsData?.content || [];
  const filteredIncidents = incidents.filter((incident) =>
    incident.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    incident.description.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Calculate stats
  const stats = {
    total: incidents.length,
    open: incidents.filter((i) => i.status === "OPEN").length,
    critical: incidents.filter((i) => i.severity === "CRITICAL").length,
    avgMttr: incidents.filter((i) => i.mttr).reduce((acc, i) => acc + (i.mttr || 0), 0) /
             (incidents.filter((i) => i.mttr).length || 1),
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "OPEN":
        return "bg-red-100 text-red-700 border-red-200";
      case "ACKNOWLEDGED":
        return "bg-yellow-100 text-yellow-700 border-yellow-200";
      case "INVESTIGATING":
        return "bg-purple-100 text-purple-700 border-purple-200";
      case "RESOLVED":
        return "bg-green-100 text-green-700 border-green-200";
      case "ESCALATED":
        return "bg-orange-100 text-orange-700 border-orange-200";
      default:
        return "bg-gray-100 text-gray-700";
    }
  };

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case "CRITICAL":
        return "bg-red-600 text-white";
      case "HIGH":
        return "bg-yellow-600 text-white";
      case "MEDIUM":
        return "bg-yellow-500 text-gray-900";
      case "LOW":
        return "bg-gray-400 text-white";
      default:
        return "bg-gray-200 text-gray-900";
    }
  };

  const getCategoryIcon = (category: string) => {
    switch (category) {
      case "PROVIDER_OUTAGE":
        return "🔴";
      case "PERFORMANCE_DEGRADATION":
        return "⚠️";
      case "HIGH_FAILURE_RATE":
        return "📉";
      case "SECURITY_CONCERN":
        return "🔒";
      case "DATA_ANOMALY":
        return "📊";
      default:
        return "📋";
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <h1 className="text-3xl font-bold text-gray-900">Incidents</h1>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          {[...Array(4)].map((_, i) => (
            <Card key={i} className="animate-pulse">
              <CardHeader className="h-20 bg-gray-100"></CardHeader>
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
          <h1 className="text-3xl font-bold text-gray-900">Incident Management</h1>
          <p className="text-gray-600 mt-1">
            Track and resolve correlated payment incidents
          </p>
        </div>
        <Button variant="outline" size="sm">
          <Download size={16} />
          Export Report
        </Button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
        >
          <Card className="border-l-4 border-purple-600">
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-gray-600">
                Total Incidents
              </CardTitle>
              <Activity className="text-purple-600" size={20} />
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-gray-900">{stats.total}</div>
              <p className="text-xs text-gray-600 mt-1">Active tracking</p>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <Card className="border-l-4 border-red-600">
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-gray-600">
                Open Incidents
              </CardTitle>
              <AlertTriangle className="text-red-600" size={20} />
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-gray-900">{stats.open}</div>
              <p className="text-xs text-gray-600 mt-1">Require attention</p>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
        >
          <Card className="border-l-4 border-yellow-500">
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-gray-600">
                Critical Severity
              </CardTitle>
              <ArrowUpCircle className="text-yellow-600" size={20} />
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-gray-900">{stats.critical}</div>
              <p className="text-xs text-gray-600 mt-1">High priority</p>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
        >
          <Card className="border-l-4 border-green-600">
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-gray-600">
                Avg MTTR
              </CardTitle>
              <Clock className="text-green-600" size={20} />
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-gray-900">
                {stats.avgMttr.toFixed(0)}m
              </div>
              <p className="text-xs text-gray-600 mt-1">Resolution time</p>
            </CardContent>
          </Card>
        </motion.div>
      </div>

      {/* Filters and Search */}
      <Card>
        <CardContent className="p-6">
          <div className="flex flex-col md:flex-row gap-4">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-muted-foreground" size={18} />
              <Input
                placeholder="Search incidents by title or description..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-10"
              />
            </div>
            <div className="flex gap-2">
              {["ALL", "OPEN", "ACKNOWLEDGED", "INVESTIGATING", "RESOLVED"].map((status) => (
                <Button
                  key={status}
                  variant={statusFilter === status ? "default" : "outline"}
                  size="sm"
                  onClick={() => setStatusFilter(status)}
                >
                  {status}
                </Button>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Incidents List */}
      <Card>
        <CardContent className="p-6">
          {filteredIncidents.length === 0 ? (
            <div className="text-center py-12">
              <CheckCircle className="mx-auto text-success mb-4" size={64} />
              <p className="text-muted-foreground font-medium">
                {searchQuery ? "No incidents match your search" : "No incidents found"}
              </p>
              <p className="text-sm text-muted-foreground mt-2">
                {searchQuery ? "Try a different search term" : "System is running smoothly"}
              </p>
            </div>
          ) : (
            <div className="space-y-4">
              {filteredIncidents.map((incident, index) => (
                <motion.div
                  key={incident.id}
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: index * 0.05 }}
                  className="border border-border rounded-lg p-5 hover:shadow-md transition-all bg-card"
                >
                  <div className="flex items-start gap-4">
                    {/* Category Icon */}
                    <div className="text-3xl mt-1">
                      {getCategoryIcon(incident.category)}
                    </div>

                    {/* Content */}
                    <div className="flex-1 space-y-3">
                      {/* Header */}
                      <div className="flex items-start justify-between">
                        <div>
                          <h3 className="font-semibold text-lg text-foreground">
                            {incident.title}
                          </h3>
                          <p className="text-sm text-muted-foreground mt-1">
                            {incident.description}
                          </p>
                        </div>
                        <div className="flex items-center gap-2">
                          <Badge className={getSeverityColor(incident.severity)}>
                            {incident.severity}
                          </Badge>
                          <Badge variant="outline" className={getStatusColor(incident.status)}>
                            {incident.status}
                          </Badge>
                        </div>
                      </div>

                      {/* Stats */}
                      <div className="flex items-center gap-6 text-sm">
                        <div className="flex items-center gap-2 text-muted-foreground">
                          <AlertTriangle size={16} />
                          <span>{incident.correlatedAlertIds.length} correlated alerts</span>
                        </div>
                        <div className="flex items-center gap-2 text-muted-foreground">
                          <Users size={16} />
                          <span>{incident.affectedPaymentCount} payments affected</span>
                        </div>
                        <div className="flex items-center gap-2 text-muted-foreground">
                          <TrendingUp size={16} />
                          <span>Impact: {incident.impactScore.toFixed(1)}</span>
                        </div>
                        <div className="flex items-center gap-2 text-muted-foreground">
                          <Clock size={16} />
                          <span>{formatDate(incident.detectedAt, "relative")}</span>
                        </div>
                      </div>

                      {/* Actions */}
                      {incident.status !== "RESOLVED" && (
                        <div className="flex gap-2 pt-2">
                          {incident.status === "OPEN" && (
                            <Button
                              size="sm"
                              onClick={() => acknowledgeMutation.mutate(incident.id)}
                              disabled={acknowledgeMutation.isPending}
                            >
                              Acknowledge
                            </Button>
                          )}
                          {(incident.status === "ACKNOWLEDGED" || incident.status === "INVESTIGATING") && (
                            <Button
                              size="sm"
                              variant="default"
                              onClick={() =>
                                resolveMutation.mutate({
                                  id: incident.id,
                                  resolution: "Incident resolved successfully",
                                })
                              }
                              disabled={resolveMutation.isPending}
                            >
                              Mark Resolved
                            </Button>
                          )}
                          <Button size="sm" variant="outline">
                            View Details
                          </Button>
                          <Button size="sm" variant="outline">
                            Escalate
                          </Button>
                        </div>
                      )}

                      {/* Resolution Info */}
                      {incident.status === "RESOLVED" && incident.mttr && (
                        <div className="bg-success/10 border border-success/20 rounded-lg p-3">
                          <div className="flex items-center gap-2 text-sm">
                            <CheckCircle className="text-success" size={16} />
                            <span className="text-success font-medium">
                              Resolved in {incident.mttr} minutes
                            </span>
                            {incident.resolutionNotes && (
                              <span className="text-muted-foreground">
                                • {incident.resolutionNotes}
                              </span>
                            )}
                          </div>
                        </div>
                      )}
                    </div>
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

