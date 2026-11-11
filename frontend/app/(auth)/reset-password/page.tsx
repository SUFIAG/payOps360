"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { KeyRound } from "lucide-react";
import { Button, ErrorNote, Input, Label } from "@/components/ui/primitives";
import { authApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { AuthHeading, AuthLayout, useAuthConfig } from "../_components/auth-layout";
import { PasswordStrength, isPasswordAcceptable } from "../_components/password-strength";

function ResetPasswordForm() {
  const router = useRouter();
  const token = useSearchParams().get("token") ?? "";
  const minLength = useAuthConfig().data?.passwordMinLength;
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!token) {
    return (
      <div className="space-y-5">
        <AuthHeading title="Reset link missing" description="Open the link from your email again, or request a new one." />
        <Button asChild className="w-full"><Link href="/forgot-password">Request a new link</Link></Button>
      </div>
    );
  }

  const mismatch = confirm.length > 0 && confirm !== password;
  const valid = isPasswordAcceptable(password, { minLength }) && password === confirm;

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await authApi.resetPassword(token, password);
      setPassword("");
      setConfirm("");
      router.replace("/login?reset=1");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={(e) => { e.preventDefault(); void submit(); }} className="space-y-5">
      <AuthHeading title="Choose a new password" description="For your security, every signed-in device will be signed out once the password changes." />
      <div>
        <Label htmlFor="password">New password</Label>
        <Input id="password" type="password" autoComplete="new-password" required autoFocus value={password} onChange={(e) => setPassword(e.target.value)} />
      </div>
      <PasswordStrength password={password} minLength={minLength} />
      <div>
        <Label htmlFor="confirm">Confirm password</Label>
        <Input id="confirm" type="password" autoComplete="new-password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} aria-invalid={mismatch} />
        {mismatch && <p className="mt-1 text-xs text-danger">Passwords do not match.</p>}
      </div>
      <ErrorNote message={error} />
      <Button type="submit" className="w-full" size="lg" loading={busy} disabled={!valid}><KeyRound className="h-4 w-4" /> Update password</Button>
      <p className="text-center text-xs text-muted">Link expired? <Link href="/forgot-password" className="text-jade-400 hover:underline">Request a new one</Link></p>
    </form>
  );
}

export default function ResetPasswordPage() {
  return (
    <AuthLayout>
      <Suspense>
        <ResetPasswordForm />
      </Suspense>
    </AuthLayout>
  );
}
