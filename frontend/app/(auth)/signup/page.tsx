"use client";

import { useState } from "react";
import Link from "next/link";
import { Building2, MailCheck } from "lucide-react";
import { Button, ErrorNote, Input, Label, Select, Skeleton } from "@/components/ui/primitives";
import { authApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import type { SignupInput } from "@/types/api";
import { AuthHeading, AuthLayout, useAuthConfig } from "../_components/auth-layout";
import { PasswordStrength, isPasswordAcceptable } from "../_components/password-strength";

const CURRENCIES = ["USD", "EUR", "GBP", "SAR", "AED", "INR", "PKR", "EGP", "NGN", "KES", "SGD", "AUD", "CAD", "JPY"];
const EMPTY: SignupInput = { organizationName: "", fullName: "", email: "", password: "", baseCurrency: "USD" };

export default function SignupPage() {
  const config = useAuthConfig();
  const [form, setForm] = useState<SignupInput>(EMPTY);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const set = (key: keyof SignupInput) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  const minLength = config.data?.passwordMinLength;
  const valid = form.organizationName.trim().length >= 2 && form.fullName.trim().length >= 2 && !!form.email.trim()
    && isPasswordAcceptable(form.password, { email: form.email, fullName: form.fullName, minLength });

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await authApi.signup({ ...form, organizationName: form.organizationName.trim(), fullName: form.fullName.trim(), email: form.email.trim() });
      setSentTo(form.email.trim());
      setForm(EMPTY);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  if (config.isLoading) {
    return <AuthLayout><Skeleton className="h-64" /></AuthLayout>;
  }

  if (!config.data?.selfSignupEnabled) {
    return (
      <AuthLayout stepKey="disabled">
        <div className="space-y-5">
          <AuthHeading title="Sign-up is by invitation" description="Self-service organisation sign-up is not available on this deployment. Ask your PayOps360 administrator for an invitation." />
          <Button asChild variant="secondary" className="w-full"><Link href="/login">Back to sign in</Link></Button>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout stepKey={sentTo ? "sent" : "form"}>
      {sentTo ? (
        <div className="space-y-5">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-jade-500/10 text-jade-400"><MailCheck className="h-5 w-5" /></div>
          <AuthHeading
            title="Verify your email"
            description={<>We sent a verification link to <span className="font-medium text-foreground">{sentTo}</span>. Open it within 24 hours to activate your workspace.</>}
          />
          <div className="flex gap-2">
            <Button asChild variant="secondary" className="flex-1"><Link href={`/verify-email?email=${encodeURIComponent(sentTo)}`}>Resend link</Link></Button>
            <Button asChild className="flex-1"><Link href="/login">Go to sign in</Link></Button>
          </div>
        </div>
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); void submit(); }} className="space-y-4">
          <AuthHeading title="Create your organization" description="Set up a PayOps360 workspace. You'll be its first administrator." />
          <div>
            <Label htmlFor="organizationName">Organization name</Label>
            <Input id="organizationName" required autoFocus autoComplete="organization" maxLength={200} value={form.organizationName} onChange={set("organizationName")} />
          </div>
          <div className="grid grid-cols-[1fr_auto] gap-3">
            <div>
              <Label htmlFor="fullName">Your full name</Label>
              <Input id="fullName" required autoComplete="name" maxLength={200} value={form.fullName} onChange={set("fullName")} />
            </div>
            <div>
              <Label htmlFor="baseCurrency">Currency</Label>
              <Select id="baseCurrency" value={form.baseCurrency} onChange={set("baseCurrency")}>
                {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
              </Select>
            </div>
          </div>
          <div>
            <Label htmlFor="email">Work email</Label>
            <Input id="email" type="email" required autoComplete="username" value={form.email} onChange={set("email")} />
          </div>
          <div>
            <Label htmlFor="password">Password</Label>
            <Input id="password" type="password" required autoComplete="new-password" value={form.password} onChange={set("password")} />
          </div>
          <PasswordStrength password={form.password} email={form.email} fullName={form.fullName} minLength={minLength} />
          <ErrorNote message={error} />
          <Button type="submit" className="w-full" size="lg" loading={busy} disabled={!valid}><Building2 className="h-4 w-4" /> Create organization</Button>
          <p className="text-center text-xs text-muted">Already have an account? <Link href="/login" className="text-jade-400 hover:underline">Sign in</Link></p>
        </form>
      )}
    </AuthLayout>
  );
}
