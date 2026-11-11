"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { MailCheck, Send, ShieldCheck } from "lucide-react";
import { Button, ErrorNote, Input, Label } from "@/components/ui/primitives";
import { authApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { AuthHeading, AuthLayout } from "../_components/auth-layout";

/**
 * Verification needs an explicit click: mail scanners that prefetch links
 * must not be able to consume the single-use token.
 */
function VerifyEmail() {
  const router = useRouter();
  const params = useSearchParams();
  const token = params.get("token") ?? "";
  const [email, setEmail] = useState(params.get("email") ?? "");
  const [resent, setResent] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  if (token) {
    return (
      <div className="space-y-5">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-jade-500/10 text-jade-400"><ShieldCheck className="h-5 w-5" /></div>
        <AuthHeading title="Confirm your email address" description="Activate your administrator account to start using your PayOps360 workspace." />
        <ErrorNote message={error} />
        <Button className="w-full" size="lg" loading={busy} onClick={() => run(async () => {
          await authApi.verifyEmail(token);
          router.replace("/login?verified=1");
        })}>
          <MailCheck className="h-4 w-4" /> Verify email
        </Button>
        {error && <p className="text-center text-xs text-muted"><Link href="/verify-email" className="text-jade-400 hover:underline">Send a new verification link</Link></p>}
      </div>
    );
  }

  return resent ? (
    <div className="space-y-5">
      <AuthHeading title="Check your inbox" description={<>If <span className="font-medium text-foreground">{resent}</span> is awaiting verification, a new link is on its way. It expires in 24 hours.</>} />
      <Button asChild variant="secondary" className="w-full"><Link href="/login">Back to sign in</Link></Button>
    </div>
  ) : (
    <form onSubmit={(e) => { e.preventDefault(); void run(async () => { await authApi.resendVerification(email.trim()); setResent(email.trim()); }); }} className="space-y-5">
      <AuthHeading title="Verify your email" description="Enter the address you signed up with and we'll send a fresh verification link." />
      <div>
        <Label htmlFor="email">Work email</Label>
        <Input id="email" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <ErrorNote message={error} />
      <Button type="submit" className="w-full" size="lg" loading={busy} disabled={!email.trim()}><Send className="h-4 w-4" /> Send verification link</Button>
      <p className="text-center text-xs text-muted"><Link href="/login" className="text-jade-400 hover:underline">← Back to sign in</Link></p>
    </form>
  );
}

export default function VerifyEmailPage() {
  return (
    <AuthLayout>
      <Suspense>
        <VerifyEmail />
      </Suspense>
    </AuthLayout>
  );
}
