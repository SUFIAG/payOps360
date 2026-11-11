// Types mirror the backend REST contract (com.payops.*.adapter.in.web).

export type Severity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type RiskLevel = Severity;
export type Permission =
  | "PAYMENT_READ" | "PAYMENT_WRITE" | "PROVIDER_READ" | "PROVIDER_MANAGE" | "ALERT_READ" | "ALERT_MANAGE"
  | "INCIDENT_READ" | "INCIDENT_MANAGE" | "ANALYTICS_READ" | "AI_USE" | "USER_MANAGE" | "AUDIT_READ"
  | "NOTIFICATION_MANAGE" | "RECON_READ" | "RECON_MANAGE";
export type Role = "ADMIN" | "OPERATIONS_MANAGER" | "ANALYST" | "SUPPORT" | "AUDITOR";

export interface ApiError { code: string; message: string; fields?: Record<string, string>; correlationId?: string }
export interface ApiEnvelope<T> { success: boolean; data: T; error?: ApiError; timestamp: string }
export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }

// ---------------------------------------------------------------- identity
export interface User {
  id: string; email: string; fullName: string; role: Role; permissions: Permission[];
  status: UserStatus; mfaEnabled: boolean; mustChangePassword: boolean; locked: boolean;
  lastLoginAt?: string; createdAt: string;
}
export interface AuthResponse {
  status: "AUTHENTICATED" | "MFA_REQUIRED";
  accessToken?: string; accessTokenExpiresAt?: string; user?: User; organizationName?: string;
  challengeToken?: string; mfaEnrollmentRequired: boolean; recoveryCodes: string[];
}
export interface MfaEnrollment { secret: string; otpauthUri: string }
export interface Session { id: string; ipAddress?: string; userAgent?: string; createdAt: string; lastUsedAt: string; expiresAt: string; current: boolean }
export interface CreatedUser { user: User; temporaryPassword: string }
export type UserStatus = "ACTIVE" | "DISABLED" | "INVITED" | "PENDING_VERIFICATION";
export interface AuthConfig { selfSignupEnabled: boolean; passwordMinLength: number }
export interface Accepted { message: string }
export interface InvitationDetails { organizationName: string; email: string; fullName: string; role: Role; expiresAt: string }
export interface SignupInput { organizationName: string; fullName: string; email: string; password: string; baseCurrency: string }
export type MailStatus = "PENDING" | "SENT" | "FAILED" | "LOGGED";
export interface MailboxMessage {
  id: string; recipient: string; subject: string; category: string; status: MailStatus; attempts: number;
  lastError?: string; textBody?: string; htmlBody?: string; createdAt: string; sentAt?: string;
}

// ---------------------------------------------------------------- payments
export type PaymentStatus =
  | "INITIATED" | "AUTHORIZED" | "CAPTURED" | "PROCESSING" | "SETTLED" | "FAILED" | "RETRY_PENDING"
  | "RETRY_IN_PROGRESS" | "RETRY_FAILED" | "REFUNDED" | "CHARGEBACK" | "CANCELLED";
export type PaymentMethod = "CARD" | "DIGITAL_WALLET" | "BANK_TRANSFER" | "REAL_TIME_PAYMENT" | "CASH_PICKUP" | "BNPL";
export interface Payment {
  reference: string; merchantReference?: string; amount: number; currency: string; providerCode: string;
  providerName: string; customerRef: string; method: PaymentMethod; cardLast4?: string; country?: string;
  status: PaymentStatus; previousStatus?: PaymentStatus; statusChangedAt: string; failureCode?: string;
  failureMessage?: string; latencyMs?: number; retryCount: number; stuck: boolean; riskScore: number;
  riskLevel: RiskLevel; riskFactors: string[]; createdAt: string; updatedAt: string;
}
export interface PaymentEvent { fromStatus?: PaymentStatus; toStatus: PaymentStatus; reason?: string; errorCode?: string; actor?: string; occurredAt: string }
export interface PaymentDetails { payment: Payment; timeline: PaymentEvent[]; allowedTransitions: PaymentStatus[]; retryable: boolean }

// ---------------------------------------------------------------- providers
export type HealthStatus = "HEALTHY" | "DEGRADED" | "CRITICAL" | "UNKNOWN";
export type ProviderType = "CARD_ACQUIRER" | "PAYMENT_GATEWAY" | "DIGITAL_WALLET" | "BANK_TRANSFER" | "REAL_TIME_PAYMENTS" | "REMITTANCE_CORRIDOR" | "BNPL";
export interface Provider {
  id: number; code: string; name: string; type: ProviderType; region?: string; slaLatencyMs: number;
  slaSuccessRate: number; costBps: number; active: boolean;
  health: { status: HealthStatus; successRate?: number; avgLatencyMs?: number; p95LatencyMs?: number; volume: number; observedAt?: string };
}
export interface HealthSnapshot {
  providerId: number; windowStart: string; windowEnd: string; total: number; succeeded: number; failed: number;
  successRate: number; avgLatencyMs: number; p95LatencyMs: number; status: HealthStatus;
}

// ---------------------------------------------------------------- alerts & incidents
export type AlertStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED";
export interface Alert {
  reference: string; type: string; severity: Severity; status: AlertStatus; source: "RULE" | "AI"; title: string;
  description?: string; providerCode?: string; paymentReference?: string; incidentReference?: string;
  metricValue?: number; threshold?: number; detectedAt: string; lastSeenAt: string; acknowledgedAt?: string;
  acknowledgedBy?: string; resolvedAt?: string; resolvedBy?: string; resolutionNote?: string;
}
export interface AlertSummary { open: number; acknowledged: number; resolvedLast24h: number; openBySeverity: Record<Severity, number> }

export type IncidentStatus = "OPEN" | "ACKNOWLEDGED" | "INVESTIGATING" | "RESOLVED";
export interface AiAnalysis { summary: string; rootCause: string; recommendations: string[]; confidence: number; engine: string; generatedAt: string }
export interface Incident {
  reference: string; title: string; description?: string; severity: Severity; status: IncidentStatus; providerCode?: string;
  affectedPayments: number; affectedAmount: number; openedAt: string; acknowledgedAt?: string; acknowledgedBy?: string;
  resolvedAt?: string; resolvedBy?: string; resolutionNote?: string; minutesToAcknowledge?: number;
  minutesToResolve?: number; aiAnalysis?: AiAnalysis; sla?: IncidentSla; escalationLevel: number;
}
/** Remaining seconds are negative once a deadline has passed; null once acknowledged / resolved. */
export interface IncidentSla {
  ackDueAt?: string; resolveDueAt?: string; ackBreached: boolean; resolveBreached: boolean;
  ackRemainingSeconds?: number; resolveRemainingSeconds?: number;
}
export interface SlaTarget { severity: Severity; ackMinutes: number; resolveMinutes: number; customized: boolean }
export type PostmortemStatus = "DRAFT" | "PUBLISHED";
export interface PostmortemTimelineItem { at: string; text: string }
export interface PostmortemActionItem { text: string; owner?: string; dueDate?: string; done: boolean }
export interface Postmortem {
  incidentReference: string; exists: boolean; status: PostmortemStatus; summary?: string; impact?: string; rootCause?: string;
  timeline: PostmortemTimelineItem[]; actionItems: PostmortemActionItem[]; aiEngine?: string; createdBy?: string;
  updatedBy?: string; publishedBy?: string; createdAt?: string; updatedAt?: string; publishedAt?: string;
}
export interface PostmortemInput {
  summary?: string; impact?: string; rootCause?: string; timeline: PostmortemTimelineItem[]; actionItems: PostmortemActionItem[];
}
export interface TimelineEntry { kind: string; message: string; actor?: string; occurredAt: string }
export interface IncidentDetails { incident: Incident; timeline: TimelineEntry[] }
export interface IncidentSummary {
  open: number; acknowledged: number; investigating: number; resolvedLast30Days: number; meanMinutesToAcknowledge?: number;
  meanMinutesToResolve?: number; activeSlaBreaches: number; slaAttainmentPercent?: number;
}

// ---------------------------------------------------------------- notifications
export type ChannelType = "EMAIL" | "SLACK" | "MS_TEAMS" | "WEBHOOK" | "PAGERDUTY";
export type NotificationEventType =
  | "ALERT_RAISED" | "INCIDENT_OPENED" | "INCIDENT_ESCALATED" | "INCIDENT_SLA_BREACHED" | "INCIDENT_RESOLVED" | "PROVIDER_DEGRADED"
  | "RECONCILIATION_EXCEPTIONS";
export type DeliveryStatus = "PENDING" | "SENT" | "FAILED";
export interface NotificationChannel {
  id: string; name: string; type: ChannelType; enabled: boolean; target: string; hasSigningSecret: boolean;
  createdBy?: string; createdAt: string; updatedAt: string;
}
export interface ChannelInput { name: string; type: ChannelType; enabled: boolean; target?: string; signingSecret?: string }
export interface NotificationRule {
  id: string; name: string; eventType: NotificationEventType; minSeverity: Severity; providerCode?: string;
  channelIds: string[]; enabled: boolean; createdAt: string; updatedAt: string;
}
export interface RuleInput { name: string; eventType: NotificationEventType; minSeverity: Severity; providerCode?: string; channelIds: string[]; enabled: boolean }
export interface EscalationLevel { level: number; delayMinutes: number; channelIds: string[] }
export interface EscalationPolicy {
  id: string; name: string; minSeverity: Severity; enabled: boolean; levels: EscalationLevel[]; createdAt: string; updatedAt: string;
}
export interface PolicyInput { name: string; minSeverity: Severity; enabled: boolean; levels: { delayMinutes: number; channelIds: string[] }[] }
export interface NotificationDelivery {
  id: string; channelId?: string; channelName: string; channelType: ChannelType; source: "RULE" | "ESCALATION" | "TEST";
  eventType: string; severity?: Severity; subjectType?: string; subjectReference?: string; title: string;
  status: DeliveryStatus; attempts: number; lastError?: string; nextAttemptAt?: string; createdAt: string; sentAt?: string;
}
export interface ChannelTestResult { success: boolean; detail: string; delivery: NotificationDelivery }

// ---------------------------------------------------------------- analytics
export interface Kpi { key: string; label: string; value: number; previous?: number; unit: string; changePercent?: number }
export interface WindowStats {
  total: number; succeeded: number; failed: number; inFlight: number; stuck: number; highRisk: number; volumeUsd: number;
  failedVolumeUsd: number; avgLatencyMs: number; p95LatencyMs: number; uniqueCustomers: number;
}
export interface Overview { from: string; to: string; kpis: Kpi[]; current: WindowStats; previous: WindowStats }
export interface TimeBucket { bucket: string; total: number; succeeded: number; failed: number; volumeUsd: number; avgLatencyMs: number }
export interface ProviderPerformance {
  providerId: number; code: string; name: string; total: number; succeeded: number; failed: number; successRate: number;
  avgLatencyMs: number; p95LatencyMs: number; volumeUsd: number; slaSuccessRate: number; slaLatencyMs: number; costBps: number;
}
export interface Breakdown { label: string; count: number; volumeUsd: number }
export interface Corridor { country: string; count: number; volumeUsd: number; successRate: number }

// ---------------------------------------------------------------- AI
export interface Anomaly { type: string; providerCode: string; providerName: string; metric: string; observed: number; baseline: number; deviationPercent: number; severity: Severity; explanation: string }
export interface Recommendation { priority: number; category: string; title: string; detail: string; action: string }
export interface ForecastPoint { at: string; expected: number; lower: number; upper: number }
export interface RoutingRanking { code: string; name: string; score: number; successRate: number; p95LatencyMs: number; costBps: number; rationale: string }
export interface Insights {
  healthScore: number; healthStatus: "OPTIMAL" | "WATCH" | "DEGRADED" | "CRITICAL"; headline: string; anomalies: Anomaly[];
  recommendations: Recommendation[]; forecast: ForecastPoint[]; routing: RoutingRanking[]; engine: string; generatedAt: string;
}
export interface CopilotReply { answer: string; toolsUsed: string[]; suggestions: string[]; engine: string; generatedAt: string }
export interface PaymentInsight {
  reference: string; status: string; failureCode?: string; failureCategory?: string; failureMeaning?: string;
  retryRecommended: boolean; retryAdvice: string; operatorAction?: string; customerMessage?: string; suggestedProvider?: string;
  riskSummary: string; riskFactors: string[]; narrative: string; engine: string; generatedAt: string;
}
export interface SearchInterpretation {
  statuses: PaymentStatus[]; provider?: string; method?: PaymentMethod; risk?: RiskLevel; currency?: string; minAmount?: number;
  maxAmount?: number; country?: string; lastHours?: number; stuck?: boolean; text?: string; explanation: string; engine: string;
}
export interface Briefing { markdown: string; engine: string; generatedAt: string }

// ---------------------------------------------------------------- audit
export interface AuditEntry { id: number; occurredAt: string; actor?: string; action: string; resourceType?: string; resourceId?: string; outcome: string; ipAddress?: string; details: Record<string, string>; hash: string }
export interface ChainVerification { intact: boolean; entriesChecked: number; firstBrokenEntryId?: number }

// ---------------------------------------------------------------- reconciliation
export type BatchStatus = "RECEIVED" | "MATCHING" | "COMPLETED" | "FAILED";
export type ReconExceptionType = "AMOUNT_MISMATCH" | "MISSING_IN_SETTLEMENT" | "MISSING_IN_LEDGER" | "DUPLICATE" | "FEE_OVERCHARGE";
export type ReconExceptionStatus = "OPEN" | "INVESTIGATING" | "RESOLVED" | "WRITTEN_OFF";
export type ResolutionReason =
  | "PROVIDER_CORRECTED" | "LEDGER_CORRECTED" | "TIMING_DIFFERENCE" | "REFUND_RECOVERED" | "FALSE_POSITIVE"
  | "MANUAL_MATCH" | "SETTLED_LATER" | "OTHER";
export type SettlementLineType = "SALE" | "REFUND" | "CHARGEBACK" | "FEE";
export type LineMatchStatus = "PENDING" | "MATCHED" | "AMOUNT_MISMATCH" | "MISSING_IN_LEDGER" | "DUPLICATE" | "FEE_ONLY";

export interface SettlementBatch {
  reference: string; providerCode: string; providerName: string; fileName: string; status: BatchStatus;
  totalLines: number; rejectedLines: number; processedLines: number; progressPercent: number; matched: number;
  amountMismatches: number; missingInLedger: number; missingInSettlement: number; duplicates: number; feeOvercharges: number;
  exceptions: number; matchRate?: number; grossAmountUsd: number; feeAmountUsd: number; periodStart?: string; periodEnd?: string;
  parseErrors: string[]; errorMessage?: string; uploadedBy?: string; createdAt: string; startedAt?: string; completedAt?: string;
}
export interface ReconException {
  reference: string; type: ReconExceptionType; status: ReconExceptionStatus; providerCode: string; providerName: string;
  batchReference?: string; paymentReference?: string; providerReference?: string; amountAtRisk: number; currency: string;
  amountAtRiskUsd: number; expectedAmount?: number; actualAmount?: number; detail?: string; assignee?: string;
  resolutionReason?: ResolutionReason; resolutionNote?: string; resolvedBy?: string; resolvedAt?: string;
  createdAt: string; updatedAt: string; ageHours: number;
}
export interface ReconExceptionDetails {
  exception: ReconException;
  line?: {
    lineNo: number; type: SettlementLineType; providerReference?: string; amount: number; currency: string; fee?: number;
    expectedFee?: number; fxRate?: number; settledAt: string; matchStatus: LineMatchStatus; matchMethod?: "EXACT" | "FUZZY" | "MANUAL";
  };
  payment?: { reference: string; merchantReference?: string; amount: number; currency: string; method: string; status: string; providerCode?: string; createdAt: string };
}
export interface ReconSummary {
  days: number; matchRate?: number; reconciledLines: number; matchedLines: number; batches: number; openExceptions: number;
  amountAtRiskUsd: number; feeLeakageUsd: number; openByType: Partial<Record<ReconExceptionType, number>>;
}
export interface MatchRatePoint { day: string; lines: number; matched: number; matchRate?: number }
export interface FeeRule { method?: string; currency?: string; percentage: number; fixedFee: number }
export interface FeeSchedule { providerCode: string; providerName: string; fallbackPercentage?: number; rules: FeeRule[]; updatedBy?: string; updatedAt?: string }
export interface ExceptionExplanation { reference: string; likelyCause: string; suggestedAction: string; checks: string[]; confidence: string; engine: string; generatedAt: string }
