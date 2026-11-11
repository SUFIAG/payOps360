"use client";

import { Check, X } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Client-side mirror of the backend PasswordPolicy
 * (com.payops.identity.domain.PasswordPolicy). The server remains the
 * authority and additionally rejects reuse of recent passwords.
 */
const COMMON = [
  "password", "password1", "password123", "123456789012", "qwertyuiop", "letmein", "welcome", "admin",
  "administrator", "changeme", "iloveyou", "football", "monkey", "dragon", "sunshine", "princess", "passw0rd",
  "p@ssw0rd", "qwerty123", "abc123",
];

export interface PasswordContext { email?: string; fullName?: string; minLength?: number }
interface Rule { label: string; ok: boolean }

export function passwordRules(password: string, { email, fullName, minLength = 12 }: PasswordContext): Rule[] {
  const lower = password.toLowerCase();
  const local = (email ?? "").toLowerCase().split("@")[0];
  const nameParts = (fullName ?? "").toLowerCase().split(/\s+/).filter((p) => p.length >= 3);
  return [
    { label: `At least ${minLength} characters`, ok: password.length >= minLength },
    { label: "At most 72 bytes", ok: new TextEncoder().encode(password).length <= 72 },
    { label: "An uppercase letter", ok: /\p{Lu}/u.test(password) },
    { label: "A lowercase letter", ok: /\p{Ll}/u.test(password) },
    { label: "A digit", ok: /\p{Nd}/u.test(password) },
    { label: "A special character", ok: /[^\p{L}\p{Nd}]/u.test(password) },
    { label: "No common passwords", ok: password.length > 0 && !COMMON.some((c) => lower.includes(c)) },
    { label: "Not your name or email", ok: password.length > 0 && !(local.length >= 3 && lower.includes(local)) && !nameParts.some((p) => lower.includes(p)) },
    { label: "No character repeated 4+ times in a row", ok: password.length > 0 && !/(.)\1{3}/u.test(password) },
  ];
}

export function isPasswordAcceptable(password: string, context: PasswordContext) {
  return passwordRules(password, context).every((r) => r.ok);
}

const LEVELS = [
  { label: "Too weak", bar: "bg-danger", text: "text-danger" },
  { label: "Weak", bar: "bg-danger", text: "text-danger" },
  { label: "Fair", bar: "bg-warning", text: "text-warning" },
  { label: "Good", bar: "bg-brass-400", text: "text-brass-400" },
  { label: "Strong", bar: "bg-jade-500", text: "text-jade-400" },
];

export function PasswordStrength({ password, ...context }: { password: string } & PasswordContext) {
  const rules = passwordRules(password, context);
  const met = rules.filter((r) => r.ok).length;
  const allMet = met === rules.length;
  // Policy compliance caps the score; extra length beyond the minimum earns "Strong".
  const level = !password ? 0 : allMet ? (password.length >= (context.minLength ?? 12) + 4 ? 4 : 3) : Math.min(2, Math.floor((met / rules.length) * 3));
  const tone = LEVELS[level];

  return (
    <div className="space-y-2" aria-live="polite">
      <div className="flex items-center gap-2">
        <div className="flex flex-1 gap-1" aria-hidden>
          {[1, 2, 3, 4].map((i) => (
            <span key={i} className={cn("h-1 flex-1 rounded-full transition-colors", password && i <= Math.max(level, 1) ? tone.bar : "bg-surface-2")} />
          ))}
        </div>
        <span className={cn("w-16 text-right text-[11px] font-medium", password ? tone.text : "text-muted")}>{password ? tone.label : ""}</span>
      </div>
      <ul className="grid grid-cols-1 gap-x-3 gap-y-1 text-[11px] sm:grid-cols-2">
        {rules.map((r) => (
          <li key={r.label} className={cn("flex items-center gap-1.5", r.ok ? "text-jade-400" : "text-muted")}>
            {r.ok ? <Check className="h-3 w-3 shrink-0" /> : <X className="h-3 w-3 shrink-0 opacity-60" />}
            {r.label}
          </li>
        ))}
      </ul>
    </div>
  );
}
