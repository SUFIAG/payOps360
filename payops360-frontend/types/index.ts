// Auth Types
export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  organizationName: string;
  role: UserRole;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface User {
  id: number;
  email: string;
  fullName: string;
  role: UserRole;
  organizationId: number;
  organizationName: string;
  createdAt: string;
}

export type UserRole = "ADMIN" | "OPERATIONS_MANAGER" | "ANALYST" | "SUPPORT";

// Payment Types
export interface Payment {
  id: number;
  externalId: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  providerId: number;
  providerName: string;
  merchantReference: string;
  customerEmail?: string;
  customerName?: string;
  paymentMethod?: string;
  errorCode?: string;
  errorMessage?: string;
  retryCount: number;
  metadata?: Record<string, any>;
  createdAt: string;
  updatedAt: string;
}

export type PaymentStatus =
  | "INITIATED"
  | "AUTHORIZED"
  | "CAPTURED"
  | "PROCESSING"
  | "SETTLED"
  | "FAILED"
  | "RETRY_PENDING"
  | "RETRY_IN_PROGRESS"
  | "RETRY_FAILED"
  | "REFUNDED"
  | "CHARGEBACK"
  | "CANCELLED";

export interface PaymentEvent {
  id: number;
  paymentId: number;
  eventType: string;
  fromStatus?: string;
  toStatus?: string;
  description?: string;
  message?: string;
  timestamp: string;
}

// Provider Types
export interface Provider {
  id: number;
  code: string;
  name: string;
  type: string;
  status: ProviderStatus;
  configuration: Record<string, any>;
  createdAt: string;
}

export type ProviderStatus = "ACTIVE" | "INACTIVE" | "MAINTENANCE";

export interface ProviderHealthMetrics {
  providerId: number;
  providerName: string;
  successRate: number;
  failureRate: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  uptimePercentage: number;
  lastUpdated: string;
}

// Alert Types
export interface Alert {
  id: number;
  type: AlertType;
  severity: AlertSeverity;
  title: string;
  description: string;
  entityType: string;
  entityId: number;
  status: AlertStatus;
  threshold?: number;
  actualValue?: number;
  createdAt: string;
  acknowledgedAt?: string;
  resolvedAt?: string;
}

export type AlertType =
  | "HIGH_FAILURE_RATE"
  | "LOW_SUCCESS_RATE"
  | "HIGH_LATENCY"
  | "PROVIDER_DOWN"
  | "PAYMENT_STUCK"
  | "ANOMALY_DETECTED"
  | "THRESHOLD_BREACH"
  | "PATTERN_DETECTED"
  | "SLA_BREACH"
  | "CAPACITY_WARNING"
  | "SECURITY_ALERT"
  | "SYSTEM_ERROR";

export type AlertSeverity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO";
export type AlertStatus = "NEW" | "ACKNOWLEDGED" | "INVESTIGATING" | "RESOLVED" | "FALSE_POSITIVE";

// Incident Types
export interface Incident {
  id: string;
  category: IncidentCategory;
  severity: AlertSeverity;
  title: string;
  description: string;
  status: IncidentStatus;
  correlatedAlertIds: string[];
  affectedPaymentCount: number;
  impactScore: number;
  detectedAt: string;
  acknowledgedAt?: string;
  resolvedAt?: string;
  resolutionNotes?: string;
  mttr?: number; // Mean Time To Resolve in minutes
  alertCount?: number; // Backwards compatibility
  affectedEntities?: string[]; // Backwards compatibility
  estimatedImpact?: string; // Backwards compatibility
  createdAt?: string; // Backwards compatibility
}

export type IncidentCategory =
  | "PROVIDER_OUTAGE"
  | "PAYMENT_PROCESSING_ISSUE"
  | "NETWORK_FAILURE"
  | "SYSTEM_ERROR"
  | "SECURITY_BREACH"
  | "PERFORMANCE_DEGRADATION"
  | "OTHER";

export type IncidentStatus = "OPEN" | "ACKNOWLEDGED" | "INVESTIGATING" | "RESOLVED" | "ESCALATED";

// Dashboard Types
export interface DashboardOverview {
  totalPayments: number;
  successfulPayments: number;
  failedPayments: number;
  successRate: number;
  totalAmount: number;
  activeProviders: number;
  activeAlerts: number;
  criticalIncidents: number;
}

// Analytics Types
export interface PaymentTrend {
  timestamp: string;
  totalPayments: number;
  successfulPayments: number;
  failedPayments: number;
  successRate: number;
  totalAmount: number;
}

export interface ProviderPerformance {
  providerId: number;
  providerName: string;
  successRate: number;
  avgLatency: number;
  totalPayments: number;
}

// API Response Types
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  timestamp: string;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface ApiError {
  success: false;
  error: string;
  message: string;
  timestamp: string;
  path?: string;
}

