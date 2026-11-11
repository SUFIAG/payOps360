"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ExternalLink, Inbox, Lock, RefreshCw } from "lucide-react";
import { Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, PageHeader, Skeleton, type Tone } from "@/components/ui/primitives";
import { devApi } from "@/lib/api/endpoints";
import { ApiRequestError, errorMessage } from "@/lib/api/client";
import { usePermission } from "@/lib/auth/session";
import { cn, humanize, timeAgo } from "@/lib/utils";
import type { MailboxMessage, MailStatus } from "@/types/api";

const STATUS_TONE: Record<MailStatus, Tone> = { PENDING: "amber", SENT: "jade", LOGGED: "blue", FAILED: "red" };
const URL_PATTERN = /(https?:\/\/[^\s<>"]+)/g;

/** Renders the plain-text body with links made clickable (no HTML is injected). */
function TextBody({ text }: { text: string }) {
  return (
    <pre className="whitespace-pre-wrap break-words rounded-xl border border-border bg-surface-2 p-4 font-sans text-sm leading-relaxed">
      {text.split(URL_PATTERN).map((part, i) =>
        i % 2 === 1
          ? <a key={i} href={part} className="break-all text-jade-400 underline underline-offset-2" target="_blank" rel="noreferrer">{part}</a>
          : <span key={i}>{part}</span>,
      )}
    </pre>
  );
}

function MessageDetail({ message }: { message: MailboxMessage }) {
  const links = [...new Set(message.textBody?.match(URL_PATTERN) ?? [])];
  return (
    <div className="space-y-4">
      <div>
        <h2 className="font-display text-lg font-semibold">{message.subject}</h2>
        <p className="mt-1 text-xs text-muted">
          To <span className="font-medium text-foreground">{message.recipient}</span> · {new Date(message.createdAt).toLocaleString()} · {humanize(message.category)}
        </p>
      </div>
      {links.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {links.map((href) => (
            <Button key={href} asChild size="sm" variant="outline">
              <a href={href} target="_blank" rel="noreferrer"><ExternalLink className="h-3.5 w-3.5" /> {new URL(href).pathname}</a>
            </Button>
          ))}
        </div>
      )}
      {message.lastError && <ErrorNote message={`Last delivery error: ${message.lastError}`} />}
      {message.textBody ? <TextBody text={message.textBody} /> : <p className="text-sm text-muted">The body was redacted after delivery.</p>}
    </div>
  );
}

function Mailbox() {
  const mailbox = useQuery({ queryKey: ["dev-mailbox"], queryFn: () => devApi.mailbox(50), refetchInterval: 5000, retry: false });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const messages = mailbox.data ?? [];
  const selected = messages.find((m) => m.id === selectedId) ?? messages[0];

  if (mailbox.error instanceof ApiRequestError && mailbox.error.status === 404) {
    return <Card><Empty icon={<Inbox className="h-8 w-8" />} title="Mailbox unavailable" hint="The development mailbox only exists when the backend runs with the dev profile." /></Card>;
  }

  return (
    <>
      <PageHeader
        title="Dev mailbox"
        subtitle="Outgoing email captured by the outbox. Development profile only. Shows every tenant's mail so sign-up links can be followed."
        actions={<Button variant="secondary" onClick={() => mailbox.refetch()} loading={mailbox.isFetching}><RefreshCw className="h-4 w-4" /> Refresh</Button>}
      />
      <ErrorNote message={mailbox.isError ? errorMessage(mailbox.error) : null} />
      <div className="grid gap-6 lg:grid-cols-[minmax(280px,380px)_1fr]">
        <Card>
          <CardHeader title="Recent messages" subtitle={`${messages.length} message${messages.length === 1 ? "" : "s"}`} icon={<Inbox className="h-4 w-4" />} />
          <CardBody className="space-y-1 p-3">
            {mailbox.isLoading && Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-14" />)}
            {!mailbox.isLoading && messages.length === 0 && <Empty title="No email yet" hint="Trigger a password reset, invitation or sign-up to see it here." />}
            {messages.map((m) => (
              <button
                key={m.id}
                onClick={() => setSelectedId(m.id)}
                className={cn("w-full rounded-lg px-3 py-2.5 text-left transition-colors cursor-pointer",
                  selected?.id === m.id ? "bg-surface-2 ring-1 ring-border" : "hover:bg-surface-2/60")}
              >
                <div className="flex items-center justify-between gap-2">
                  <p className="truncate text-sm font-medium">{m.subject}</p>
                  <Badge tone={STATUS_TONE[m.status]}>{humanize(m.status)}</Badge>
                </div>
                <p className="mt-0.5 truncate text-xs text-muted">{m.recipient} · {timeAgo(m.createdAt)}</p>
              </button>
            ))}
          </CardBody>
        </Card>
        <Card>
          <CardBody>{selected ? <MessageDetail message={selected} /> : <Empty icon={<Inbox className="h-8 w-8" />} title="Select a message" />}</CardBody>
        </Card>
      </div>
    </>
  );
}

export default function MailboxPage() {
  const allowed = usePermission("USER_MANAGE");
  if (!allowed) {
    return <Card><Empty icon={<Lock className="h-8 w-8" />} title="Access restricted" hint="The development mailbox requires the USER_MANAGE permission." /></Card>;
  }
  return <Mailbox />;
}
