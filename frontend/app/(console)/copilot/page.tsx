"use client";

import {
  Activity, Brain, CreditCard, Eraser, EyeOff, FileClock, Lock, Route, ShieldAlert, ShieldCheck, Siren, Sparkles, TrendingUp,
} from "lucide-react";
import { CopilotChat, useConversation } from "@/components/ai/copilot-chat";
import { Button, Card, CardBody, CardHeader, Empty, PageHeader } from "@/components/ui/primitives";
import { usePermission } from "@/lib/auth/session";

const CAPABILITIES: { icon: React.ElementType; title: string; detail: string }[] = [
  { icon: Activity, title: "Performance KPIs", detail: "Volume, success rate, latency and value over any recent window." },
  { icon: Brain, title: "Failure root causes", detail: "Groups failures by code and provider and explains what they mean." },
  { icon: Route, title: "Smart routing", detail: "Ranks providers on success, p95 latency and cost to suggest routing." },
  { icon: TrendingUp, title: "Forecasting", detail: "Projects near-term volume with confidence bands." },
  { icon: ShieldAlert, title: "Risk review", detail: "Surfaces high-risk payments and the factors driving their scores." },
  { icon: Siren, title: "Incident summaries", detail: "Summarises open incidents, impact and next best actions." },
  { icon: CreditCard, title: "Payment lookup", detail: "Mention a PAY- reference to get its status, timeline and retry advice." },
];

const GOVERNANCE: { icon: React.ElementType; text: string }[] = [
  { icon: Lock, text: "Read-only tools — the copilot cannot move money or change state." },
  { icon: EyeOff, text: "Customer identifiers are masked before any data reaches the model." },
  { icon: FileClock, text: "Every question is recorded in the tamper-evident audit trail." },
];

export default function CopilotPage() {
  const allowed = usePermission("AI_USE");
  const { messages, reset } = useConversation();

  if (!allowed) {
    return (
      <Card>
        <Empty icon={<Lock className="h-8 w-8" />} title="Access restricted" hint="Your role does not include the AI_USE permission." />
      </Card>
    );
  }

  return (
    <>
      <PageHeader
        title="AI Copilot"
        subtitle="Ask questions in plain language. Answers are grounded in your live payment data."
      />
      <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
        <Card className="flex h-[calc(100vh-10rem)] min-h-[480px] flex-col overflow-hidden">
          <CopilotChat />
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader
              title="What I can do"
              subtitle="Tools the copilot can call"
              icon={<Sparkles className="h-4 w-4" />}
            />
            <CardBody>
              <ul className="space-y-3">
                {CAPABILITIES.map(({ icon: Icon, title, detail }) => (
                  <li key={title} className="flex gap-3">
                    <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-surface-2 text-jade-400">
                      <Icon className="h-3.5 w-3.5" aria-hidden />
                    </span>
                    <div>
                      <p className="text-sm font-medium">{title}</p>
                      <p className="text-xs text-muted">{detail}</p>
                    </div>
                  </li>
                ))}
              </ul>
              <Button
                variant="secondary"
                className="mt-5 w-full"
                onClick={reset}
                disabled={messages.length === 0}
              >
                <Eraser className="h-4 w-4" /> Clear conversation
              </Button>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Governance" subtitle="How AI access is controlled" icon={<ShieldCheck className="h-4 w-4" />} />
            <CardBody>
              <ul className="space-y-2.5">
                {GOVERNANCE.map(({ icon: Icon, text }) => (
                  <li key={text} className="flex items-start gap-2.5 text-xs text-muted">
                    <Icon className="mt-0.5 h-3.5 w-3.5 shrink-0 text-jade-400" aria-hidden />
                    <span>{text}</span>
                  </li>
                ))}
              </ul>
              <p className="mt-4 rounded-lg border border-border bg-surface-2 px-3 py-2 text-[11px] text-muted">
                AI output is advisory. Verify before acting on payments or incidents.
              </p>
            </CardBody>
          </Card>
        </div>
      </div>
    </>
  );
}
