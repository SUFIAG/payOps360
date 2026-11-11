"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { QRCodeSVG } from "qrcode.react";
import {
  AlertTriangle, Check, CheckCircle2, Copy, Download, KeyRound, Laptop, LogOut, MonitorSmartphone, RefreshCw, ShieldCheck,
  ShieldOff, Smartphone, X,
} from "lucide-react";
import {
  Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, Input, Label, PageHeader, Skeleton,
} from "@/components/ui/primitives";
import { accountApi } from "@/lib/api/endpoints";
import { errorMessage, refreshSession } from "@/lib/api/client";
import { useSession } from "@/lib/auth/session";
import { cn, formatDateTime, timeAgo } from "@/lib/utils";
import type { MfaEnrollment, Session } from "@/types/api";

// ------------------------------------------------------------------ helpers
/** Rotates the session first so the new access token reflects the change (e.g. password-change flag cleared). */
async function refreshUser() {
  await refreshSession();
  const me = await accountApi.me();
  useSession.getState().setUser(me.user, me.organizationName);
}

function useCopy() {
  const [copied, setCopied] = useState<string | null>(null);
  const copy = async (key: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(key);
      setTimeout(() => setCopied((c) => (c === key ? null : c)), 1800);
    } catch {
      setCopied(null);
    }
  };
  return { copied, copy };
}

const sanitizeCode = (v: string) => v.replace(/\D/g, "").slice(0, 6);

function CodeInput({ id, value, onChange, label = "6-digit code" }: { id: string; value: string; onChange: (v: string) => void; label?: string }) {
  return (
    <div>
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        value={value}
        onChange={(e) => onChange(sanitizeCode(e.target.value))}
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="\d{6}"
        placeholder="123456"
        className="font-mono tracking-[0.4em]"
        required
      />
    </div>
  );
}

// ------------------------------------------------------------------ password
const RULES: { label: string; test: (pw: string, confirm: string) => boolean }[] = [
  { label: "At least 12 characters", test: (p) => p.length >= 12 },
  { label: "An uppercase letter", test: (p) => /[A-Z]/.test(p) },
  { label: "A lowercase letter", test: (p) => /[a-z]/.test(p) },
  { label: "A digit", test: (p) => /\d/.test(p) },
  { label: "A special character", test: (p) => /[^A-Za-z0-9]/.test(p) },
  { label: "Passwords match", test: (p, c) => p.length > 0 && p === c },
];

function PasswordCard({ onChanged }: { onChanged: () => void }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [done, setDone] = useState(false);
  const results = RULES.map((r) => ({ label: r.label, ok: r.test(next, confirm) }));
  const valid = results.every((r) => r.ok) && current.length > 0;

  const mutation = useMutation({
    mutationFn: async () => {
      await accountApi.changePassword(current, next);
      await refreshUser();
    },
    onSuccess: () => {
      setCurrent("");
      setNext("");
      setConfirm("");
      setDone(true);
      onChanged();
    },
  });

  return (
    <Card>
      <CardHeader title="Change password" subtitle="Use a long, unique passphrase" icon={<KeyRound className="h-4 w-4" />} />
      <CardBody>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            setDone(false);
            if (valid) mutation.mutate();
          }}
        >
          <div>
            <Label htmlFor="pw-current">Current password</Label>
            <Input id="pw-current" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <Label htmlFor="pw-new">New password</Label>
              <Input id="pw-new" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} required />
            </div>
            <div>
              <Label htmlFor="pw-confirm">Confirm new password</Label>
              <Input id="pw-confirm" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
            </div>
          </div>
          <ul className="grid gap-1.5 sm:grid-cols-2" aria-label="Password requirements">
            {results.map((r) => (
              <li key={r.label} className={cn("flex items-center gap-2 text-xs", r.ok ? "text-jade-400" : "text-muted")}>
                {r.ok ? <Check className="h-3.5 w-3.5" aria-hidden /> : <X className="h-3.5 w-3.5" aria-hidden />}
                <span>{r.label}</span>
                <span className="sr-only">{r.ok ? "(met)" : "(not met)"}</span>
              </li>
            ))}
          </ul>
          <ErrorNote message={mutation.isError ? errorMessage(mutation.error) : null} />
          {done && (
            <p role="status" className="flex items-center gap-2 rounded-lg border border-jade-500/30 bg-jade-500/10 px-3 py-2 text-sm text-jade-400">
              <CheckCircle2 className="h-4 w-4" /> Password updated.
            </p>
          )}
          <Button type="submit" disabled={!valid} loading={mutation.isPending}>Update password</Button>
        </form>
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ recovery codes
function RecoveryCodes({ codes, onAcknowledge }: { codes: string[]; onAcknowledge: () => void }) {
  const { copied, copy } = useCopy();
  const [saved, setSaved] = useState(false);
  const text = codes.join("\n");

  const download = () => {
    const blob = new Blob([`PayOps360 recovery codes\nGenerated ${new Date().toISOString()}\n\n${text}\n`], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "payops360-recovery-codes.txt";
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-start gap-2 rounded-lg border border-warning/30 bg-warning/10 px-3 py-2 text-sm text-warning">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
        <span>Save these recovery codes now. Each works once and they will not be shown again.</span>
      </div>
      <ol className="grid grid-cols-2 gap-2 rounded-xl border border-border bg-surface-2 p-4 font-mono text-sm">
        {codes.map((c) => <li key={c} className="tracking-wider">{c}</li>)}
      </ol>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={() => copy("codes", text)}>
          {copied === "codes" ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
          {copied === "codes" ? "Copied" : "Copy"}
        </Button>
        <Button type="button" variant="secondary" size="sm" onClick={download}>
          <Download className="h-3.5 w-3.5" /> Download .txt
        </Button>
      </div>
      <label className="flex cursor-pointer items-center gap-2 text-sm">
        <input type="checkbox" checked={saved} onChange={(e) => setSaved(e.target.checked)} className="h-4 w-4 accent-jade-500" />
        I have saved these recovery codes somewhere safe
      </label>
      <Button type="button" disabled={!saved} onClick={onAcknowledge}>Done</Button>
    </div>
  );
}

// ------------------------------------------------------------------ MFA
function Enrollment({ enrollment, onCancel, onConfirmed }: { enrollment: MfaEnrollment; onCancel: () => void; onConfirmed: (codes: string[]) => void }) {
  const [code, setCode] = useState("");
  const { copied, copy } = useCopy();
  const grouped = enrollment.secret.replace(/\s/g, "").match(/.{1,4}/g)?.join(" ") ?? enrollment.secret;
  const confirm = useMutation({ mutationFn: () => accountApi.confirmMfa(code), onSuccess: (r) => onConfirmed(r.recoveryCodes) });

  return (
    <div className="space-y-5">
      <ol className="list-decimal space-y-1 pl-5 text-sm text-muted">
        <li>Scan the QR code with an authenticator app (Google Authenticator, 1Password, Authy…).</li>
        <li>Or enter the setup key manually.</li>
        <li>Enter the 6-digit code the app shows to confirm.</li>
      </ol>
      <div className="flex flex-col gap-5 sm:flex-row sm:items-center">
        <div className="w-fit rounded-xl bg-white p-3">
          <QRCodeSVG value={enrollment.otpauthUri} size={168} level="M" title="Authenticator QR code" />
        </div>
        <div className="min-w-0 flex-1 space-y-2">
          <Label>Setup key</Label>
          <div className="flex items-center gap-2">
            <code className="min-w-0 flex-1 break-all rounded-lg border border-border bg-surface-2 px-3 py-2 font-mono text-sm tracking-wider">{grouped}</code>
            <Button type="button" variant="secondary" size="icon" aria-label="Copy setup key" onClick={() => copy("secret", enrollment.secret)}>
              {copied === "secret" ? <Check className="h-4 w-4 text-jade-400" /> : <Copy className="h-4 w-4" />}
            </Button>
          </div>
        </div>
      </div>
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (code.length === 6) confirm.mutate();
        }}
      >
        <div className="max-w-xs"><CodeInput id="mfa-confirm" value={code} onChange={setCode} /></div>
        <ErrorNote message={confirm.isError ? errorMessage(confirm.error) : null} />
        <div className="flex gap-2">
          <Button type="submit" disabled={code.length !== 6} loading={confirm.isPending}>Verify &amp; enable</Button>
          <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
        </div>
      </form>
    </div>
  );
}

type MfaView = { kind: "idle" } | { kind: "enrolling"; enrollment: MfaEnrollment } | { kind: "codes"; codes: string[] };

function MfaCard() {
  const user = useSession((s) => s.user);
  const [view, setView] = useState<MfaView>({ kind: "idle" });
  const [regenCode, setRegenCode] = useState("");
  const [disablePw, setDisablePw] = useState("");
  const [disableCode, setDisableCode] = useState("");

  const start = useMutation({ mutationFn: accountApi.startMfa, onSuccess: (enrollment) => setView({ kind: "enrolling", enrollment }) });
  const regenerate = useMutation({
    mutationFn: () => accountApi.regenerateRecoveryCodes(regenCode),
    onSuccess: (r) => {
      setRegenCode("");
      setView({ kind: "codes", codes: r.recoveryCodes });
    },
  });
  const disable = useMutation({
    mutationFn: async () => {
      await accountApi.disableMfa(disablePw, disableCode);
      await refreshUser();
    },
    onSuccess: () => {
      setDisablePw("");
      setDisableCode("");
    },
  });

  const acknowledge = () => {
    setView({ kind: "idle" });
    void refreshUser();
  };

  const enabled = !!user?.mfaEnabled;

  return (
    <Card>
      <CardHeader
        title="Two-factor authentication"
        subtitle="Time-based one-time passwords (TOTP)"
        icon={<Smartphone className="h-4 w-4" />}
        actions={<Badge tone={enabled ? "green" : "amber"} dot>{enabled ? "Enabled" : "Disabled"}</Badge>}
      />
      <CardBody>
        {view.kind === "codes" ? (
          <RecoveryCodes codes={view.codes} onAcknowledge={acknowledge} />
        ) : view.kind === "enrolling" ? (
          <Enrollment
            enrollment={view.enrollment}
            onCancel={() => setView({ kind: "idle" })}
            onConfirmed={(codes) => setView({ kind: "codes", codes })}
          />
        ) : !enabled ? (
          <div className="space-y-4">
            <p className="text-sm text-muted">
              Protect your account with a second factor. Even if your password leaks, attackers cannot sign in without your device.
            </p>
            <ErrorNote message={start.isError ? errorMessage(start.error) : null} />
            <Button onClick={() => start.mutate()} loading={start.isPending}>
              <ShieldCheck className="h-4 w-4" /> Enable authenticator
            </Button>
          </div>
        ) : (
          <div className="grid gap-6 lg:grid-cols-2">
            <form
              className="space-y-3"
              onSubmit={(e) => {
                e.preventDefault();
                if (regenCode.length === 6) regenerate.mutate();
              }}
            >
              <div>
                <h4 className="flex items-center gap-2 text-sm font-medium"><RefreshCw className="h-4 w-4 text-jade-400" /> Regenerate recovery codes</h4>
                <p className="mt-1 text-xs text-muted">Invalidates all previous recovery codes.</p>
              </div>
              <CodeInput id="mfa-regen" value={regenCode} onChange={setRegenCode} label="Authenticator code" />
              <ErrorNote message={regenerate.isError ? errorMessage(regenerate.error) : null} />
              <Button type="submit" variant="secondary" disabled={regenCode.length !== 6} loading={regenerate.isPending}>Regenerate</Button>
            </form>
            <form
              className="space-y-3 lg:border-l lg:border-border lg:pl-6"
              onSubmit={(e) => {
                e.preventDefault();
                if (disablePw && disableCode.length === 6) disable.mutate();
              }}
            >
              <div>
                <h4 className="flex items-center gap-2 text-sm font-medium text-danger"><ShieldOff className="h-4 w-4" /> Disable MFA</h4>
                <p className="mt-1 text-xs text-muted">Not recommended. Your organisation may require MFA.</p>
              </div>
              <div>
                <Label htmlFor="mfa-disable-pw">Password</Label>
                <Input id="mfa-disable-pw" type="password" autoComplete="current-password" value={disablePw} onChange={(e) => setDisablePw(e.target.value)} required />
              </div>
              <CodeInput id="mfa-disable-code" value={disableCode} onChange={setDisableCode} label="Authenticator code" />
              <ErrorNote message={disable.isError ? errorMessage(disable.error) : null} />
              <Button type="submit" variant="danger" disabled={!disablePw || disableCode.length !== 6} loading={disable.isPending}>Disable MFA</Button>
            </form>
          </div>
        )}
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ sessions
function describeAgent(ua?: string): { label: string; mobile: boolean } {
  if (!ua) return { label: "Unknown device", mobile: false };
  const browser =
    /Edg\//.test(ua) ? "Edge" : /OPR\/|Opera/.test(ua) ? "Opera" : /Firefox\//.test(ua) ? "Firefox"
      : /Chrome\//.test(ua) ? "Chrome" : /Safari\//.test(ua) ? "Safari" : /curl|PostmanRuntime|okhttp|python/i.test(ua) ? "API client" : "Browser";
  const os =
    /Windows/.test(ua) ? "Windows" : /iPhone|iPad|iOS/.test(ua) ? "iOS" : /Android/.test(ua) ? "Android"
      : /Mac OS X|Macintosh/.test(ua) ? "macOS" : /Linux/.test(ua) ? "Linux" : "";
  return { label: os ? `${browser} on ${os}` : browser, mobile: /Mobile|iPhone|Android/.test(ua) };
}

function SessionsCard() {
  const qc = useQueryClient();
  const sessions = useQuery({ queryKey: ["account", "sessions"], queryFn: accountApi.sessions });
  const revoke = useMutation({
    mutationFn: (id: string) => accountApi.revokeSession(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["account", "sessions"] }),
  });

  const sorted: Session[] = [...(sessions.data ?? [])].sort((a, b) => Number(b.current) - Number(a.current) || b.lastUsedAt.localeCompare(a.lastUsedAt));

  return (
    <Card>
      <CardHeader title="Active sessions" subtitle="Devices currently signed in to your account" icon={<MonitorSmartphone className="h-4 w-4" />} />
      <CardBody className="space-y-3">
        <ErrorNote message={sessions.isError ? errorMessage(sessions.error) : revoke.isError ? errorMessage(revoke.error) : null} />
        {sessions.isLoading && [0, 1, 2].map((i) => <Skeleton key={i} className="h-16" />)}
        {sessions.isSuccess && sorted.length === 0 && <Empty title="No active sessions" />}
        <ul className="space-y-2">
          {sorted.map((s) => {
            const agent = describeAgent(s.userAgent);
            const Icon = agent.mobile ? Smartphone : Laptop;
            return (
              <li key={s.id} className={cn("flex flex-wrap items-center gap-3 rounded-xl border p-3", s.current ? "border-jade-500/40 bg-jade-500/5" : "border-border bg-surface-2/50")}>
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-surface-2 text-muted"><Icon className="h-4 w-4" aria-hidden /></span>
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
                    <span title={s.userAgent}>{agent.label}</span>
                    {s.current && <Badge tone="jade">This device</Badge>}
                  </p>
                  <p className="text-xs text-muted">
                    <span className="font-mono">{s.ipAddress ?? "unknown IP"}</span>
                    {" · "}Signed in {formatDateTime(s.createdAt)}
                    {" · "}Last active {timeAgo(s.lastUsedAt)}
                  </p>
                </div>
                {!s.current && (
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => revoke.mutate(s.id)}
                    loading={revoke.isPending && revoke.variables === s.id}
                    aria-label={`Revoke session ${agent.label}`}
                  >
                    <LogOut className="h-3.5 w-3.5" /> Revoke
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ page
function SecurityContent() {
  const params = useSearchParams();
  const router = useRouter();
  const user = useSession((s) => s.user);
  const [changed, setChanged] = useState(false);
  const required = !!user?.mustChangePassword || (params.get("required") === "1" && !changed);

  return (
    <>
      <PageHeader title="Account security" subtitle={user ? `Signed in as ${user.email}` : "Manage your password, MFA and sessions"} />
      {required && (
        <div role="alert" className="mb-6 flex items-start gap-3 rounded-2xl border border-warning/40 bg-warning/10 p-4">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning" />
          <div>
            <p className="font-display font-semibold text-warning">Password change required</p>
            <p className="text-sm text-muted">
              Your password was issued by an administrator or has expired. Set a new password to continue using PayOps360.
            </p>
          </div>
        </div>
      )}
      <div className="grid gap-6 xl:grid-cols-2">
        <div className="space-y-6">
          <PasswordCard
            onChanged={() => {
              setChanged(true);
              if (params.get("required")) router.replace("/settings/security");
            }}
          />
          <SessionsCard />
        </div>
        <MfaCard />
      </div>
    </>
  );
}

export default function SecurityPage() {
  return (
    <Suspense fallback={<div className="space-y-4"><Skeleton className="h-10 w-64" /><Skeleton className="h-80" /></div>}>
      <SecurityContent />
    </Suspense>
  );
}
