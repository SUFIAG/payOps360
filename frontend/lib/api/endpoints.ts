import { api, apiDownload, apiUpload, qs } from "./client";
import type {
  ExceptionExplanation, FeeRule, FeeSchedule, MatchRatePoint, ReconException, ReconExceptionDetails, ReconExceptionStatus,
  ReconExceptionType, ReconSummary, ResolutionReason, SettlementBatch,
  Accepted, AuthConfig, InvitationDetails, MailboxMessage, SignupInput,
  Alert, AlertStatus, AlertSummary, AuditEntry, AuthResponse, Breakdown, Briefing, ChainVerification, CopilotReply,
  Corridor, CreatedUser, HealthSnapshot, Incident, IncidentDetails, IncidentStatus, IncidentSummary, Insights,
  MfaEnrollment, Overview, Page, Payment, PaymentDetails, PaymentInsight, PaymentMethod, PaymentStatus, Provider,
  ProviderPerformance, ProviderType, RiskLevel, Role, SearchInterpretation, Session, Severity, TimeBucket, User,
  ChannelInput, ChannelTestResult, DeliveryStatus, EscalationPolicy, NotificationChannel, NotificationDelivery,
  NotificationRule, PolicyInput, Postmortem, PostmortemInput, RuleInput, SlaTarget,
} from "@/types/api";

export const authApi = {
  login: (email: string, password: string) => api<AuthResponse>("/auth/login", { method: "POST", body: { email, password }, auth: false }),
  verifyMfa: (challengeToken: string, code: string) => api<AuthResponse>("/auth/mfa/verify", { method: "POST", body: { challengeToken, code }, auth: false }),
  startEnrollment: (challengeToken: string) => api<MfaEnrollment>("/auth/mfa/enroll/start", { method: "POST", body: { challengeToken }, auth: false }),
  confirmEnrollment: (challengeToken: string, code: string) => api<AuthResponse>("/auth/mfa/enroll/confirm", { method: "POST", body: { challengeToken, code }, auth: false }),
  logout: () => api<void>("/auth/logout", { method: "POST", auth: false }),
  config: () => api<AuthConfig>("/auth/config", { auth: false }),
  forgotPassword: (email: string) => api<Accepted>("/auth/password/forgot", { method: "POST", body: { email }, auth: false }),
  resetPassword: (token: string, newPassword: string) => api<void>("/auth/password/reset", { method: "POST", body: { token, newPassword }, auth: false }),
  invitation: (token: string) => api<InvitationDetails>(`/auth/invitations/${encodeURIComponent(token)}`, { auth: false }),
  acceptInvitation: (token: string, password: string) => api<AuthResponse>("/auth/invitations/accept", { method: "POST", body: { token, password }, auth: false }),
  signup: (input: SignupInput) => api<Accepted>("/auth/signup", { method: "POST", body: input, auth: false }),
  verifyEmail: (token: string) => api<void>("/auth/verify-email", { method: "POST", body: { token }, auth: false }),
  resendVerification: (email: string) => api<Accepted>("/auth/verify-email/resend", { method: "POST", body: { email }, auth: false }),
};

export const accountApi = {
  me: () => api<{ user: User; organizationName: string }>("/account/me"),
  changePassword: (currentPassword: string, newPassword: string) => api<void>("/account/password", { method: "POST", body: { currentPassword, newPassword } }),
  startMfa: () => api<MfaEnrollment>("/account/mfa/enroll/start", { method: "POST" }),
  confirmMfa: (code: string) => api<{ recoveryCodes: string[] }>("/account/mfa/enroll/confirm", { method: "POST", body: { code } }),
  disableMfa: (password: string, code: string) => api<void>("/account/mfa/disable", { method: "POST", body: { password, code } }),
  regenerateRecoveryCodes: (code: string) => api<{ recoveryCodes: string[] }>("/account/mfa/recovery-codes", { method: "POST", body: { code } }),
  sessions: () => api<Session[]>("/account/sessions"),
  revokeSession: (id: string) => api<void>(`/account/sessions/${id}`, { method: "DELETE" }),
};

export const usersApi = {
  list: (page = 0, size = 50) => api<Page<User>>(`/users${qs({ page, size })}`),
  create: (email: string, fullName: string, role: Role) => api<CreatedUser>("/users", { method: "POST", body: { email, fullName, role } }),
  changeRole: (id: string, role: Role) => api<User>(`/users/${id}/role`, { method: "PUT", body: { role } }),
  action: (id: string, action: "disable" | "enable" | "unlock" | "reset-mfa") => api<User>(`/users/${id}/${action}`, { method: "POST" }),
  resetPassword: (id: string) => api<CreatedUser>(`/users/${id}/reset-password`, { method: "POST" }),
  invite: (email: string, fullName: string, role: Role) => api<User>("/users/invitations", { method: "POST", body: { email, fullName, role } }),
  resendInvitation: (id: string) => api<User>(`/users/${id}/invitations/resend`, { method: "POST" }),
  revokeInvitation: (id: string) => api<void>(`/users/${id}/invitations`, { method: "DELETE" }),
};

/** Development-only outbox viewer; answers 404 outside the backend's dev profile. */
export const devApi = {
  mailbox: (limit = 50) => api<MailboxMessage[]>(`/dev/mailbox${qs({ limit })}`),
};

export interface PaymentFilters {
  status?: PaymentStatus[]; provider?: string; method?: PaymentMethod; risk?: RiskLevel; currency?: string;
  minAmount?: number; maxAmount?: number; customer?: string; q?: string; country?: string; stuck?: boolean;
  from?: string; to?: string; page?: number; size?: number;
}

export const paymentsApi = {
  search: (f: PaymentFilters) => api<Page<Payment>>(`/payments${qs({ ...f })}`),
  get: (reference: string) => api<PaymentDetails>(`/payments/${encodeURIComponent(reference)}`),
  changeStatus: (reference: string, status: PaymentStatus, reason?: string) =>
    api<Payment>(`/payments/${encodeURIComponent(reference)}/status`, { method: "POST", body: { status, reason } }),
  retry: (reference: string) => api<Payment>(`/payments/${encodeURIComponent(reference)}/retry`, { method: "POST" }),
};

export interface ProviderInput { code: string; name: string; type: ProviderType; region?: string; slaLatencyMs: number; slaSuccessRate: number; costBps: number }

export const providersApi = {
  list: () => api<Provider[]>("/providers"),
  health: (code: string, hours = 24) => api<HealthSnapshot[]>(`/providers/${code}/health${qs({ hours })}`),
  create: (input: ProviderInput) => api<Provider>("/providers", { method: "POST", body: input }),
  update: (code: string, input: ProviderInput) => api<Provider>(`/providers/${code}`, { method: "PUT", body: input }),
  setActive: (code: string, active: boolean) => api<Provider>(`/providers/${code}/${active ? "activate" : "deactivate"}`, { method: "POST" }),
};

export const alertsApi = {
  list: (status?: AlertStatus, severity?: Severity, page = 0, size = 25) => api<Page<Alert>>(`/alerts${qs({ status, severity, page, size })}`),
  summary: () => api<AlertSummary>("/alerts/summary"),
  acknowledge: (reference: string) => api<Alert>(`/alerts/${reference}/acknowledge`, { method: "POST" }),
  resolve: (reference: string, note: string) => api<Alert>(`/alerts/${reference}/resolve`, { method: "POST", body: { note } }),
};

export const incidentsApi = {
  list: (status?: IncidentStatus, page = 0, size = 25) => api<Page<Incident>>(`/incidents${qs({ status, page, size })}`),
  summary: () => api<IncidentSummary>("/incidents/summary"),
  get: (reference: string) => api<IncidentDetails>(`/incidents/${reference}`),
  declare: (title: string, severity: Severity, description?: string, providerCode?: string) =>
    api<Incident>("/incidents", { method: "POST", body: { title, severity, description, providerCode } }),
  transition: (reference: string, action: "acknowledge" | "investigate") => api<Incident>(`/incidents/${reference}/${action}`, { method: "POST" }),
  escalate: (reference: string, severity: Severity) => api<Incident>(`/incidents/${reference}/escalate`, { method: "POST", body: { severity } }),
  resolve: (reference: string, note: string) => api<Incident>(`/incidents/${reference}/resolve`, { method: "POST", body: { note } }),
  addNote: (reference: string, note: string) => api<Incident>(`/incidents/${reference}/notes`, { method: "POST", body: { note } }),
};

export const analyticsApi = {
  overview: (window = "PT24H") => api<Overview>(`/analytics/overview${qs({ window })}`),
  timeseries: (window = "PT24H", bucket = "PT1H") => api<TimeBucket[]>(`/analytics/timeseries${qs({ window, bucket })}`),
  providers: (window = "PT24H") => api<ProviderPerformance[]>(`/analytics/providers${qs({ window })}`),
  failureReasons: (window = "PT24H") => api<Breakdown[]>(`/analytics/failure-reasons${qs({ window })}`),
  methods: (window = "PT24H") => api<Breakdown[]>(`/analytics/methods${qs({ window })}`),
  risk: (window = "PT24H") => api<Breakdown[]>(`/analytics/risk${qs({ window })}`),
  corridors: (window = "PT24H") => api<Corridor[]>(`/analytics/corridors${qs({ window })}`),
};

export const aiApi = {
  insights: () => api<Insights>("/ai/insights"),
  copilot: (question: string, history: { role: string; content: string }[]) =>
    api<CopilotReply>("/ai/copilot", { method: "POST", body: { question, history } }),
  investigate: (reference: string) => api<Incident>(`/ai/incidents/${reference}/investigate`, { method: "POST" }),
  explainPayment: (reference: string) => api<PaymentInsight>(`/ai/payments/${encodeURIComponent(reference)}/explain`),
  interpret: (query: string) => api<SearchInterpretation>("/ai/search/interpret", { method: "POST", body: { query } }),
  briefing: () => api<Briefing>("/ai/briefing"),
};

export const auditApi = {
  list: (action?: string, page = 0, size = 50) => api<Page<AuditEntry>>(`/audit${qs({ action, page, size })}`),
  verify: () => api<ChainVerification>("/audit/verify"),
};

export const slaApi = {
  targets: () => api<SlaTarget[]>("/incidents/sla-targets"),
  update: (targets: { severity: Severity; ackMinutes: number; resolveMinutes: number }[]) =>
    api<SlaTarget[]>("/incidents/sla-targets", { method: "PUT", body: { targets } }),
};

export const postmortemApi = {
  get: (reference: string) => api<Postmortem>(`/incidents/${encodeURIComponent(reference)}/postmortem`),
  save: (reference: string, input: PostmortemInput) =>
    api<Postmortem>(`/incidents/${encodeURIComponent(reference)}/postmortem`, { method: "PUT", body: input }),
  publish: (reference: string) => api<Postmortem>(`/incidents/${encodeURIComponent(reference)}/postmortem/publish`, { method: "POST" }),
  draftWithAi: (reference: string) => api<Postmortem>(`/ai/incidents/${encodeURIComponent(reference)}/postmortem-draft`, { method: "POST" }),
};

export const notificationsApi = {
  channels: () => api<NotificationChannel[]>("/notifications/channels"),
  createChannel: (input: ChannelInput) => api<NotificationChannel>("/notifications/channels", { method: "POST", body: input }),
  updateChannel: (id: string, input: ChannelInput) => api<NotificationChannel>(`/notifications/channels/${id}`, { method: "PUT", body: input }),
  deleteChannel: (id: string) => api<void>(`/notifications/channels/${id}`, { method: "DELETE" }),
  testChannel: (id: string) => api<ChannelTestResult>(`/notifications/channels/${id}/test`, { method: "POST" }),
  rules: () => api<NotificationRule[]>("/notifications/rules"),
  createRule: (input: RuleInput) => api<NotificationRule>("/notifications/rules", { method: "POST", body: input }),
  updateRule: (id: string, input: RuleInput) => api<NotificationRule>(`/notifications/rules/${id}`, { method: "PUT", body: input }),
  deleteRule: (id: string) => api<void>(`/notifications/rules/${id}`, { method: "DELETE" }),
  policies: () => api<EscalationPolicy[]>("/notifications/escalation-policies"),
  createPolicy: (input: PolicyInput) => api<EscalationPolicy>("/notifications/escalation-policies", { method: "POST", body: input }),
  updatePolicy: (id: string, input: PolicyInput) => api<EscalationPolicy>(`/notifications/escalation-policies/${id}`, { method: "PUT", body: input }),
  deletePolicy: (id: string) => api<void>(`/notifications/escalation-policies/${id}`, { method: "DELETE" }),
  deliveries: (status?: DeliveryStatus, limit = 100) => api<NotificationDelivery[]>(`/notifications/deliveries${qs({ status, limit })}`),
};

export interface ExceptionFilters {
  type?: ReconExceptionType[]; status?: ReconExceptionStatus[]; provider?: string; batch?: string; assignee?: string; page?: number; size?: number;
}

const ref = (r: string) => encodeURIComponent(r);

export const reconciliationApi = {
  summary: (days = 30) => api<ReconSummary>(`/reconciliation/summary${qs({ days })}`),
  trend: (days = 30) => api<MatchRatePoint[]>(`/reconciliation/trend${qs({ days })}`),
  batches: (provider?: string, page = 0, size = 10) => api<Page<SettlementBatch>>(`/reconciliation/batches${qs({ provider, page, size })}`),
  batch: (reference: string) => api<SettlementBatch>(`/reconciliation/batches/${ref(reference)}`),
  upload: (provider: string, file: File) => {
    const form = new FormData();
    form.append("provider", provider);
    form.append("file", file);
    return apiUpload<SettlementBatch>("/reconciliation/batches", form);
  },
  sampleCsv: (provider: string) => apiDownload(`/reconciliation/providers/${ref(provider)}/sample-csv`),
  feeSchedule: (provider: string) => api<FeeSchedule>(`/reconciliation/providers/${ref(provider)}/fee-schedule`),
  saveFeeSchedule: (provider: string, rules: FeeRule[]) =>
    api<FeeSchedule>(`/reconciliation/providers/${ref(provider)}/fee-schedule`, { method: "PUT", body: { rules } }),
  exceptions: (f: ExceptionFilters) => api<Page<ReconException>>(`/reconciliation/exceptions${qs({ ...f })}`),
  exception: (reference: string) => api<ReconExceptionDetails>(`/reconciliation/exceptions/${ref(reference)}`),
  assign: (reference: string, assignee?: string) =>
    api<ReconException>(`/reconciliation/exceptions/${ref(reference)}/assign`, { method: "POST", body: { assignee } }),
  resolve: (reference: string, reason: ResolutionReason, note: string) =>
    api<ReconException>(`/reconciliation/exceptions/${ref(reference)}/resolve`, { method: "POST", body: { reason, note } }),
  match: (reference: string, paymentReference: string, note?: string) =>
    api<ReconException>(`/reconciliation/exceptions/${ref(reference)}/match`, { method: "POST", body: { paymentReference, note } }),
  writeOff: (reference: string, note: string) =>
    api<ReconException>(`/reconciliation/exceptions/${ref(reference)}/write-off`, { method: "POST", body: { note } }),
  explain: (reference: string) => api<ExceptionExplanation>(`/ai/reconciliation/exceptions/${ref(reference)}/explain`, { method: "POST" }),
};
