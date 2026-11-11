"use client";

import { useState } from "react";
import Link from "next/link";
import { MailCheck, Send } from "lucide-react";
import { Button, ErrorNote, Input, Label } from "@/components/ui/primitives";
import { authApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { AuthHeading, AuthLayout } from "../_components/auth-layout";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await authApi.forgotPassword(email.trim());
      setSentTo(email.trim());
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout stepKey={sentTo ? "sent" : "form"}>
      {sentTo ? (
        <div className="space-y-5">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-jade-500/10 text-jade-400"><MailCheck className="h-5 w-5" /></div>
          <AuthHeading
            title="Check your inbox"
            description={<>If an account exists for <span className="font-medium text-foreground">{sentTo}</span>, we&apos;ve sent a link to reset its password. The link works once and expires in 30 minutes.</>}
          />
          <p className="text-xs text-muted">Didn&apos;t receive it? Check spam, or try again in a minute.</p>
          <div className="flex gap-2">
            <Button variant="secondary" className="flex-1" onClick={() => setSentTo(null)}>Try again</Button>
            <Button asChild className="flex-1"><Link href="/login">Back to sign in</Link></Button>
          </div>
        </div>
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); void submit(); }} className="space-y-5">
          <AuthHeading title="Forgot your password?" description="Enter your work email and we'll send you a secure link to choose a new one." />
          <div>
            <Label htmlFor="email">Work email</Label>
            <Input id="email" type="email" autoComplete="username" required autoFocus value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <ErrorNote message={error} />
          <Button type="submit" className="w-full" size="lg" loading={busy} disabled={!email.trim()}><Send className="h-4 w-4" /> Send reset link</Button>
          <p className="text-center text-xs text-muted"><Link href="/login" className="text-jade-400 hover:underline">← Back to sign in</Link></p>
        </form>
      )}
    </AuthLayout>
  );
}
