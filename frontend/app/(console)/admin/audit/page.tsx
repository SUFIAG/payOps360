"use client";

import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, FileClock, Link2, Lock, ShieldAlert, ShieldCheck } from "lucide-react";
import {
  Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, Label, PageHeader, Select, Skeleton, type Tone,
} from "@/components/ui/primitives";
import { auditApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission } from "@/lib/auth/session";
import { formatDateTime, formatNumber, humanize, timeAgo } from "@/lib/utils";
import type { AuditEntry } from "@/types/api";

const ACTIONS = [
  "LOGIN_SUCCESS", "LOGIN_FAILED", "ACCOUNT_LOCKED", "MFA_ENABLED", "PASSWORD_CHANGED", "USER_CREATED", "USER_ROLE_CHANGED",
  "PAYMENT_STATUS_CHANGED", "INCIDENT_STATUS_CHANGED", "AI_INVESTIGATION", "AI_COPILOT_QUERY", "TOKEN_REUSE_DETECTED",
] as const;
const PAGE_SIZE = 50;

function actionTone(action: string): Tone {
  if (/FAILED|LOCKED|REUSE|DENIED|DISABLED|REVOKED/.test(action)) return "red";
  if (action.startsWith("AI_")) return "blue";
  if (/MFA|PASSWORD|ROLE|USER_/.test(action)) return "amber";
  if (action.includes("SUCCESS")) return "green";
  return "ink";
}

function outcomeTone(outcome: string): Tone {
  const o = outcome.toUpperCase();
  if (o === "SUCCESS" || o === "ALLOWED" || o === "OK") return "green";
  if (o === "FAILURE" || o === "FAILED" || o === "DENIED" || o === "ERROR") return "red";
  return "gray";
}

function Details({ details }: { details: Record<string, string> }) {
  const entries = Object.entries(details ?? {});
  if (entries.length === 0) return <span className="text-muted/50">—</span>;
  return (
    <div className="flex max-w-xs flex-wrap gap-1">
      {entries.map(([k, v]) => (
        <span key={k} className="max-w-full truncate rounded bg-surface-2 px-1.5 py-0.5 font-mono text-[10px]" title={`${k}=${v}`}>
          <span className="text-muted">{k}=</span>{v}
        </span>
      ))}
    </div>
  );
}

function AuditTrail() {
  const [action, setAction] = useState("");
  const [page, setPage] = useState(0);

  const audit = useQuery({
    queryKey: ["audit", action, page],
    queryFn: () => auditApi.list(action || undefined, page, PAGE_SIZE),
    placeholderData: (prev) => prev,
  });
  const verify = useMutation({ mutationFn: auditApi.verify });
  const data = audit.data;
  const result = verify.data;

  return (
    <>
      <PageHeader
        title="Audit trail"
        subtitle="Tamper-evident, append-only record of security and operational events."
        actions={
          <Button variant="outline" onClick={() => verify.mutate()} loading={verify.isPending}>
            <ShieldCheck className="h-4 w-4" /> Verify integrity
          </Button>
        }
      />

      <div className="space-y-6">
        {(result || verify.isError) && (
          <div aria-live="polite">
            {verify.isError ? (
              <ErrorNote message={errorMessage(verify.error)} />
            ) : result?.intact ? (
              <div className="flex items-start gap-3 rounded-2xl border border-jade-500/40 bg-jade-500/10 p-4">
                <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-jade-400" />
                <div>
                  <p className="font-display font-semibold text-jade-400">Hash chain intact</p>
                  <p className="text-sm text-muted">
                    {formatNumber(result.entriesChecked)} entries verified. Each entry&apos;s SHA-256 hash links to its predecessor — no record has been altered, inserted or removed.
                  </p>
                </div>
              </div>
            ) : result ? (
              <div role="alert" className="flex items-start gap-3 rounded-2xl border border-danger/40 bg-danger/10 p-4">
                <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-danger" />
                <div>
                  <p className="font-display font-semibold text-danger">Integrity check failed — chain broken</p>
                  <p className="text-sm text-muted">
                    {formatNumber(result.entriesChecked)} entries checked.
                    {result.firstBrokenEntryId !== undefined && <> First mismatch at entry <span className="font-mono text-foreground">#{result.firstBrokenEntryId}</span>.</>}
                    {" "}Escalate to security: records at or after this point may have been tampered with.
                  </p>
                </div>
              </div>
            ) : null}
          </div>
        )}

        <Card>
          <CardHeader
            title="Events"
            subtitle={data ? `${formatNumber(data.totalItems)} entr${data.totalItems === 1 ? "y" : "ies"}` : "Loading…"}
            icon={<FileClock className="h-4 w-4" />}
            actions={
              <div className="flex items-center gap-2">
                <Label htmlFor="audit-action" className="mb-0 hidden sm:block">Action</Label>
                <Select
                  id="audit-action"
                  className="h-9 text-xs"
                  value={action}
                  onChange={(e) => { setAction(e.target.value); setPage(0); }}
                >
                  <option value="">All actions</option>
                  {ACTIONS.map((a) => <option key={a} value={a}>{humanize(a)}</option>)}
                </Select>
              </div>
            }
          />
          <CardBody className="space-y-3">
            <ErrorNote message={audit.isError ? errorMessage(audit.error) : null} />
            <div className="overflow-x-auto">
              <table className="w-full min-w-[1000px] text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-muted">
                    <th scope="col" className="py-2 pr-3 font-medium">Time</th>
                    <th scope="col" className="px-3 py-2 font-medium">Actor</th>
                    <th scope="col" className="px-3 py-2 font-medium">Action</th>
                    <th scope="col" className="px-3 py-2 font-medium">Resource</th>
                    <th scope="col" className="px-3 py-2 font-medium">Outcome</th>
                    <th scope="col" className="px-3 py-2 font-medium">IP</th>
                    <th scope="col" className="px-3 py-2 font-medium">Details</th>
                    <th scope="col" className="py-2 pl-3 font-medium">Hash</th>
                  </tr>
                </thead>
                <tbody>
                  {audit.isLoading && Array.from({ length: 8 }).map((_, i) => (
                    <tr key={i} className="border-b border-border/60"><td colSpan={8} className="py-2.5"><Skeleton className="h-7" /></td></tr>
                  ))}
                  {data?.items.map((e: AuditEntry) => (
                    <tr key={e.id} className="border-b border-border/60 align-top hover:bg-surface-2/40">
                      <td className="whitespace-nowrap py-2.5 pr-3">
                        <p className="text-xs">{formatDateTime(e.occurredAt)}</p>
                        <p className="text-[10px] text-muted">{timeAgo(e.occurredAt)}</p>
                      </td>
                      <td className="max-w-[180px] truncate px-3 py-2.5 text-xs" title={e.actor}>{e.actor ?? <span className="text-muted">system</span>}</td>
                      <td className="px-3 py-2.5"><Badge tone={actionTone(e.action)}>{e.action}</Badge></td>
                      <td className="px-3 py-2.5 text-xs">
                        {e.resourceType ? (
                          <>
                            <span className="text-muted">{humanize(e.resourceType)}</span>
                            {e.resourceId && <span className="block max-w-[160px] truncate font-mono text-[11px]" title={e.resourceId}>{e.resourceId}</span>}
                          </>
                        ) : <span className="text-muted/50">—</span>}
                      </td>
                      <td className="px-3 py-2.5"><Badge tone={outcomeTone(e.outcome)} dot>{humanize(e.outcome)}</Badge></td>
                      <td className="whitespace-nowrap px-3 py-2.5 font-mono text-[11px] text-muted">{e.ipAddress ?? "—"}</td>
                      <td className="px-3 py-2.5"><Details details={e.details} /></td>
                      <td className="py-2.5 pl-3">
                        <span className="inline-flex items-center gap-1 font-mono text-[11px] text-muted" title={e.hash}>
                          <Link2 className="h-3 w-3" aria-hidden />{e.hash.slice(0, 10)}…
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {data && data.items.length === 0 && (
              <Empty icon={<FileClock className="h-8 w-8" />} title="No audit events" hint={action ? `No ${humanize(action).toLowerCase()} events recorded.` : "Events will appear as users act in the system."} />
            )}
            {data && data.totalPages > 1 && (
              <div className="flex items-center justify-between pt-2 text-xs text-muted">
                <span>Page {data.page + 1} of {formatNumber(data.totalPages)}</span>
                <div className="flex gap-2">
                  <Button variant="secondary" size="sm" disabled={page === 0 || audit.isFetching} onClick={() => setPage((p) => p - 1)} aria-label="Previous page"><ChevronLeft className="h-4 w-4" /></Button>
                  <Button variant="secondary" size="sm" disabled={page + 1 >= data.totalPages || audit.isFetching} onClick={() => setPage((p) => p + 1)} aria-label="Next page"><ChevronRight className="h-4 w-4" /></Button>
                </div>
              </div>
            )}
          </CardBody>
        </Card>
      </div>
    </>
  );
}

export default function AuditPage() {
  const allowed = usePermission("AUDIT_READ");
  if (!allowed) {
    return (
      <Card>
        <Empty icon={<Lock className="h-8 w-8" />} title="Access restricted" hint="Viewing the audit trail requires the AUDIT_READ permission." />
      </Card>
    );
  }
  return <AuditTrail />;
}
