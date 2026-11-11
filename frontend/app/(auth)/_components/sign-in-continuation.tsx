"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { QRCodeSVG } from "qrcode.react";
import { Copy, KeyRound } from "lucide-react";
import { Button, ErrorNote, Input, Label } from "@/components/ui/primitives";
import { authApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { useSession } from "@/lib/auth/session";
import type { AuthResponse, MfaEnrollment } from "@/types/api";
import { AuthHeading } from "./auth-layout";

type Step = "pending" | "mfa" | "enroll" | "recovery";

export function safeNext(next: string | null) {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/dashboard";
}

/**
 * Everything after a successful password step: TOTP verification, MFA
 * enrolment (when the organisation requires it), one-time display of recovery
 * codes, then the redirect into the console. Used by login and invitation acceptance.
 */
export function SignInContinuation({ auth, next, onRestart, onStepChange }: {
  auth: AuthResponse; next: string; onRestart: () => void; onStepChange?: (step: string) => void;
}) {
  const router = useRouter();
  const [step, setStepState] = useState<Step>("pending");
  const [code, setCode] = useState("");
  const [useRecovery, setUseRecovery] = useState(false);
  const [enrollment, setEnrollment] = useState<MfaEnrollment | null>(null);
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const started = useRef(false);

  const setStep = (s: Step) => {
    setStepState(s);
    onStepChange?.(s);
  };

  const finish = (res: AuthResponse) => {
    useSession.getState().signIn(res);
    if (res.recoveryCodes?.length) {
      setRecoveryCodes(res.recoveryCodes);
      setStep("recovery");
      return;
    }
    router.replace(res.user?.mustChangePassword ? "/settings/security?required=1" : next);
  };

  // Runs once per outcome; the ref guards against React Strict Mode's double effect.
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    (async () => {
      if (auth.status === "AUTHENTICATED") return finish(auth);
      if (auth.mfaEnrollmentRequired) {
        try {
          setEnrollment(await authApi.startEnrollment(auth.challengeToken ?? ""));
        } catch (e) {
          setError(errorMessage(e));
        }
        setStep("enroll");
      } else {
        setStep("mfa");
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth]);

  const submitCode = async () => {
    setBusy(true);
    setError(null);
    try {
      const challenge = auth.challengeToken ?? "";
      const res = step === "enroll" ? await authApi.confirmEnrollment(challenge, code) : await authApi.verifyMfa(challenge, code);
      setCode("");
      finish(res);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  if (step === "pending") {
    return <p className="text-sm text-muted">Signing you in…</p>;
  }

  if (step === "recovery") {
    return (
      <div className="space-y-5">
        <AuthHeading title="Save your recovery codes" description="Each code works once if you lose your device. They will not be shown again." />
        <div className="grid grid-cols-2 gap-2 rounded-xl border border-border bg-surface-2 p-4 font-mono text-sm">
          {recoveryCodes.map((c) => <span key={c}>{c}</span>)}
        </div>
        <Button variant="secondary" className="w-full" onClick={() => navigator.clipboard.writeText(recoveryCodes.join("\n"))}>
          <Copy className="h-4 w-4" /> Copy codes
        </Button>
        <Button className="w-full" onClick={() => router.replace(next)}>I have stored them safely</Button>
      </div>
    );
  }

  return (
    <form onSubmit={(e) => { e.preventDefault(); void submitCode(); }} className="space-y-5">
      <AuthHeading
        title={step === "enroll" ? "Set up two-factor authentication" : "Two-factor verification"}
        description={step === "enroll"
          ? "Your organisation requires MFA. Scan the code with Google Authenticator, Microsoft Authenticator or 1Password."
          : useRecovery ? "Enter one of your single-use recovery codes." : "Enter the 6-digit code from your authenticator app."}
      />
      {step === "enroll" && enrollment && (
        <div className="flex flex-col items-center gap-3">
          <div className="rounded-xl bg-white p-3"><QRCodeSVG value={enrollment.otpauthUri} size={168} /></div>
          <code className="break-all text-center font-mono text-xs text-muted">{enrollment.secret.match(/.{1,4}/g)?.join(" ")}</code>
        </div>
      )}
      <div>
        <Label htmlFor="code">{useRecovery ? "Recovery code" : "Verification code"}</Label>
        <Input
          id="code"
          autoFocus
          autoComplete="one-time-code"
          inputMode={useRecovery ? "text" : "numeric"}
          placeholder={useRecovery ? "xxxxx-xxxxx" : "123456"}
          maxLength={useRecovery ? 11 : 6}
          value={code}
          onChange={(e) => setCode(useRecovery ? e.target.value : e.target.value.replace(/\D/g, ""))}
          className="text-center font-mono text-lg tracking-[0.4em]"
        />
      </div>
      <ErrorNote message={error} />
      <Button type="submit" className="w-full" size="lg" loading={busy} disabled={code.length < 6}><KeyRound className="h-4 w-4" /> Verify</Button>
      <div className="flex justify-between text-xs">
        <button type="button" onClick={onRestart} className="text-muted hover:text-foreground cursor-pointer">← Back</button>
        {step === "mfa" && (
          <button type="button" onClick={() => { setUseRecovery(!useRecovery); setCode(""); }} className="text-jade-400 hover:underline cursor-pointer">
            {useRecovery ? "Use authenticator code" : "Use a recovery code"}
          </button>
        )}
      </div>
    </form>
  );
}
