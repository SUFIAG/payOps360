"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { UserCheck } from "lucide-react";
import { Badge, Button, ErrorNote, Input, Label, Skeleton } from "@/components/ui/primitives";
import { authApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { humanize } from "@/lib/utils";
import type { AuthResponse } from "@/types/api";
import { AuthHeading, AuthLayout, useAuthConfig } from "../_components/auth-layout";
import { PasswordStrength, isPasswordAcceptable } from "../_components/password-strength";
import { SignInContinuation } from "../_components/sign-in-continuation";

function AcceptInvite() {
  const token = useSearchParams().get("token") ?? "";
  const minLength = useAuthConfig().data?.passwordMinLength;
  const invitation = useQuery({ queryKey: ["invitation", token], queryFn: () => authApi.invitation(token), enabled: !!token, retry: false });
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [outcome, setOutcome] = useState<AuthResponse | null>(null);
  const [step, setStep] = useState("form");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const invalid = !token || invitation.isError;
  const details = invitation.data;

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      setOutcome(await authApi.acceptInvitation(token, password));
      setPassword("");
      setConfirm("");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const mismatch = confirm.length > 0 && confirm !== password;
  const valid = !!details && isPasswordAcceptable(password, { email: details.email, fullName: details.fullName, minLength }) && password === confirm;

  return (
    <AuthLayout stepKey={outcome ? step : invalid ? "invalid" : "form"}>
      {outcome ? (
        // Once accepted the account is active; "back" simply returns to the normal sign-in page.
        <SignInContinuation auth={outcome} next="/dashboard" onStepChange={setStep} onRestart={() => window.location.assign("/login")} />
      ) : invalid ? (
        <div className="space-y-5">
          <AuthHeading
            title="Invitation unavailable"
            description={invitation.isError ? errorMessage(invitation.error) : "This page needs the link from your invitation email."}
          />
          <p className="text-sm text-muted">Invitations expire after 72 hours and work once. Ask your administrator to resend it.</p>
          <Button asChild variant="secondary" className="w-full"><Link href="/login">Go to sign in</Link></Button>
        </div>
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); void submit(); }} className="space-y-5">
          <AuthHeading
            title={details ? `Join ${details.organizationName}` : "Accept your invitation"}
            description="Choose a password to activate your PayOps360 account."
          />
          {invitation.isLoading || !details ? (
            <Skeleton className="h-20" />
          ) : (
            <dl className="space-y-2 rounded-xl border border-border bg-surface-2 p-4 text-sm">
              <div className="flex justify-between gap-3"><dt className="text-muted">Name</dt><dd className="truncate font-medium">{details.fullName}</dd></div>
              <div className="flex justify-between gap-3"><dt className="text-muted">Email</dt><dd className="truncate font-medium">{details.email}</dd></div>
              <div className="flex justify-between gap-3"><dt className="text-muted">Role</dt><dd><Badge tone="jade">{humanize(details.role)}</Badge></dd></div>
            </dl>
          )}
          {/* Lets password managers associate the new credential with the account. */}
          <input type="email" autoComplete="username" value={details?.email ?? ""} readOnly hidden />
          <div>
            <Label htmlFor="password">Password</Label>
            <Input id="password" type="password" autoComplete="new-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          <PasswordStrength password={password} email={details?.email} fullName={details?.fullName} minLength={minLength} />
          <div>
            <Label htmlFor="confirm">Confirm password</Label>
            <Input id="confirm" type="password" autoComplete="new-password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} aria-invalid={mismatch} />
            {mismatch && <p className="mt-1 text-xs text-danger">Passwords do not match.</p>}
          </div>
          <ErrorNote message={error} />
          <Button type="submit" className="w-full" size="lg" loading={busy} disabled={!valid}><UserCheck className="h-4 w-4" /> Activate account</Button>
        </form>
      )}
    </AuthLayout>
  );
}

export default function AcceptInvitePage() {
  return (
    <Suspense>
      <AcceptInvite />
    </Suspense>
  );
}
