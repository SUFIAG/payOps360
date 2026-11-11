"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Lock } from "lucide-react";
import { Button, ErrorNote, Input, Label } from "@/components/ui/primitives";
import { authApi } from "@/lib/api/endpoints";
import { ApiRequestError, errorMessage } from "@/lib/api/client";
import type { AuthResponse } from "@/types/api";
import { AuthHeading, AuthLayout, useAuthConfig } from "../_components/auth-layout";
import { SignInContinuation, safeNext } from "../_components/sign-in-continuation";

function LoginFlow() {
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const notice = params.get("reset") ? "Your password was changed. Sign in with your new password."
    : params.get("verified") ? "Your email address is verified. Sign in to finish setting up your workspace." : null;
  const config = useAuthConfig();
  const [email, setEmail] = useState(params.get("email") ?? "");
  const [password, setPassword] = useState("");
  const [outcome, setOutcome] = useState<AuthResponse | null>(null);
  const [step, setStep] = useState("credentials");
  const [error, setError] = useState<string | null>(null);
  const [unverified, setUnverified] = useState(false);
  const [busy, setBusy] = useState(false);

  const submitCredentials = async () => {
    setBusy(true);
    setError(null);
    setUnverified(false);
    try {
      const res = await authApi.login(email, password);
      setPassword("");
      setOutcome(res);
    } catch (e) {
      setUnverified(e instanceof ApiRequestError && e.error.code === "EMAIL_NOT_VERIFIED");
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout stepKey={outcome ? step : "credentials"}>
      {outcome ? (
        <SignInContinuation
          auth={outcome}
          next={next}
          onStepChange={setStep}
          onRestart={() => { setOutcome(null); setStep("credentials"); setError(null); }}
        />
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); void submitCredentials(); }} className="space-y-5">
          <AuthHeading title="Welcome back" description="Sign in to your operations workspace. Access is restricted to authorised operators." />
          {notice && <p role="status" className="rounded-lg border border-jade-500/30 bg-jade-500/10 px-3 py-2 text-sm text-jade-400">{notice}</p>}
          <div>
            <Label htmlFor="email">Work email</Label>
            <Input id="email" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div>
            <div className="flex items-baseline justify-between">
              <Label htmlFor="password">Password</Label>
              <Link href="/forgot-password" className="mb-1.5 text-xs text-jade-400 hover:underline">Forgot password?</Link>
            </div>
            <Input id="password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          <ErrorNote message={error} />
          {unverified && (
            <p className="text-xs text-muted">
              Didn&apos;t get the email? <Link href={`/verify-email?email=${encodeURIComponent(email)}`} className="text-jade-400 hover:underline">Send a new verification link</Link>
            </p>
          )}
          <Button type="submit" className="w-full" size="lg" loading={busy}><Lock className="h-4 w-4" /> Continue securely</Button>
          <p className="text-center text-xs text-muted">Accounts are provisioned by your administrator. Repeated failures temporarily lock the account.</p>
          {config.data?.selfSignupEnabled && (
            <p className="border-t border-border pt-4 text-center text-sm text-muted">
              New to PayOps360? <Link href="/signup" className="font-medium text-jade-400 hover:underline">Create an organization</Link>
            </p>
          )}
        </form>
      )}
    </AuthLayout>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginFlow />
    </Suspense>
  );
}
