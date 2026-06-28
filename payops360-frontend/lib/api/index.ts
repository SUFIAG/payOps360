import { apiClient } from "./client";
import {
  Payment,
  PaymentEvent,
  Provider,
  ProviderHealthMetrics,
  Alert,
  Incident,
  DashboardOverview,
  PagedResponse,
  PaymentTrend,
  ProviderPerformance,
} from "@/types";

export const paymentApi = {
  getPayments: async (params?: {
    status?: string;
    providerId?: number;
    page?: number;
    size?: number;
  }): Promise<PagedResponse<Payment>> => {
    const response = await apiClient.get<PagedResponse<Payment>>("/payments", params);
    return response.data;
  },

  getPayment: async (id: number): Promise<Payment> => {
    const response = await apiClient.get<Payment>(`/payments/${id}`);
    return response.data;
  },

  getPaymentTimeline: async (id: number): Promise<PaymentEvent[]> => {
    const response = await apiClient.get<PaymentEvent[]>(`/payments/${id}/timeline`);
    return response.data;
  },

  updatePaymentStatus: async (id: number, status: string): Promise<Payment> => {
    const response = await apiClient.patch<Payment>(`/payments/${id}/status`, { status });
    return response.data;
  },
};

export const providerApi = {
  getProviders: async (params?: { status?: string; page?: number; size?: number }): Promise<PagedResponse<Provider>> => {
    const response = await apiClient.get<PagedResponse<Provider>>("/providers", params);
    return response.data;
  },

  getProvider: async (id: number): Promise<Provider> => {
    const response = await apiClient.get<Provider>(`/providers/${id}`);
    return response.data;
  },

  getProviderHealth: async (id: number): Promise<ProviderHealthMetrics> => {
    const response = await apiClient.get<ProviderHealthMetrics>(`/providers/${id}/health`);
    return response.data;
  },
};

export const alertApi = {
  getAlerts: async (params?: {
    severity?: string;
    status?: string;
    page?: number;
    size?: number;
  }): Promise<PagedResponse<Alert>> => {
    const response = await apiClient.get<PagedResponse<Alert>>("/alerts", params);
    return response.data;
  },

  getAlert: async (id: number): Promise<Alert> => {
    const response = await apiClient.get<Alert>(`/alerts/${id}`);
    return response.data;
  },

  acknowledgeAlert: async (id: number): Promise<Alert> => {
    const response = await apiClient.patch<Alert>(`/alerts/${id}/acknowledge`);
    return response.data;
  },

  resolveAlert: async (id: number, resolution: string): Promise<Alert> => {
    const response = await apiClient.patch<Alert>(`/alerts/${id}/resolve`, { resolution });
    return response.data;
  },
};

export const incidentApi = {
  getIncidents: async (params?: {
    severity?: string;
    status?: string;
    page?: number;
    size?: number;
  }): Promise<PagedResponse<Incident>> => {
    const response = await apiClient.get<PagedResponse<Incident>>("/incidents", params);
    return response.data;
  },

  getIncident: async (id: number): Promise<Incident> => {
    const response = await apiClient.get<Incident>(`/incidents/${id}`);
    return response.data;
  },

  acknowledgeIncident: async (id: number): Promise<Incident> => {
    const response = await apiClient.patch<Incident>(`/incidents/${id}/acknowledge`);
    return response.data;
  },

  resolveIncident: async (id: number, resolution: string): Promise<Incident> => {
    const response = await apiClient.patch<Incident>(`/incidents/${id}/resolve`, { resolution });
    return response.data;
  },
};

export const dashboardApi = {
  getOverview: async (): Promise<DashboardOverview> => {
    const response = await apiClient.get<DashboardOverview>("/dashboard/overview");
    return response.data;
  },
};

export const analyticsApi = {
  getPaymentTrends: async (timeWindow?: string): Promise<PaymentTrend[]> => {
    const response = await apiClient.get<PaymentTrend[]>("/analytics/payment-trends", {
      timeWindow,
    });
    return response.data;
  },

  getProviderPerformance: async (): Promise<ProviderPerformance[]> => {
    const response = await apiClient.get<ProviderPerformance[]>("/analytics/provider-performance");
    return response.data;
  },
};

