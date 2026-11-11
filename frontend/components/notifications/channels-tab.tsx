"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, KeyRound, Pencil, Plus, Radio, Send, Trash2, XCircle } from "lucide-react";
import { notificationsApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { cn, timeAgo } from "@/lib/utils";
import {
  Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, Input, Label, Skeleton,
} from "@/components/ui/primitives";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import type { ChannelInput, ChannelTestResult, ChannelType, NotificationChannel } from "@/types/api";
import { CHANNEL_META, CHANNEL_TYPES, ChannelIcon, Toggle } from "./shared";

export function ChannelsTab() {
  const queryClient = useQueryClient();
  const channels = useQuery({ queryKey: ["notifications", "channels"], queryFn: notificationsApi.channels });
  const [editing, setEditing] = useState<NotificationChannel | null>(null);
  const [creating, setCreating] = useState(false);
  const [results, setResults] = useState<Record<string, ChannelTestResult>>({});

  const test = useMutation({
    mutationFn: (id: string) => notificationsApi.testChannel(id),
    onSuccess: (result, id) => {
      setResults((r) => ({ ...r, [id]: result }));
      queryClient.invalidateQueries({ queryKey: ["notifications", "deliveries"] });
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => notificationsApi.deleteChannel(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });

  return (
    <Card>
      <CardHeader
        icon={<Radio className="h-4 w-4" />}
        title="Channels"
        subtitle="Where notifications go. URLs, keys and secrets are encrypted at rest and never shown again."
        actions={<Button size="sm" onClick={() => setCreating(true)}><Plus className="h-4 w-4" /> Add channel</Button>}
      />
      <CardBody>
        <ErrorNote message={channels.error ? errorMessage(channels.error) : remove.error ? errorMessage(remove.error) : null} />
        {channels.isLoading ? (
          <div className="space-y-3">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-16 w-full" />)}</div>
        ) : !channels.data?.length ? (
          <Empty icon={<Radio className="h-8 w-8" />} title="No channels yet" hint="Add an e-mail list, Slack or Teams webhook, a signed webhook or a PagerDuty service." />
        ) : (
          <ul className="divide-y divide-border">
            {channels.data.map((c) => {
              const result = results[c.id];
              return (
                <li key={c.id} className="flex flex-col gap-3 py-4 md:flex-row md:items-center">
                  <div className="flex min-w-0 flex-1 items-center gap-3">
                    <ChannelIcon type={c.type} />
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-medium">{c.name}</span>
                        <Badge tone="ink">{CHANNEL_META[c.type].label}</Badge>
                        <Badge tone={c.enabled ? "green" : "gray"} dot>{c.enabled ? "Active" : "Disabled"}</Badge>
                        {c.hasSigningSecret && <Badge tone="brass"><KeyRound className="h-3 w-3" /> Signed</Badge>}
                      </div>
                      <p className="mt-0.5 truncate font-mono text-xs text-muted">{c.target}</p>
                      {result && (
                        <p className={cn("mt-1 flex items-center gap-1 text-xs", result.success ? "text-jade-400" : "text-danger")}>
                          {result.success ? <CheckCircle2 className="h-3.5 w-3.5" /> : <XCircle className="h-3.5 w-3.5" />}
                          {result.detail} · {timeAgo(result.delivery.createdAt)}
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button size="sm" variant="secondary" loading={test.isPending && test.variables === c.id}
                      disabled={!c.enabled || test.isPending} onClick={() => test.mutate(c.id)}>
                      <Send className="h-3.5 w-3.5" /> Test
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setEditing(c)} aria-label={`Edit ${c.name}`}>
                      <Pencil className="h-3.5 w-3.5" /> Edit
                    </Button>
                    <Button size="sm" variant="ghost" aria-label={`Delete ${c.name}`}
                      loading={remove.isPending && remove.variables === c.id}
                      onClick={() => {
                        if (window.confirm(`Delete channel "${c.name}"? It is removed from every rule and escalation level.`)) remove.mutate(c.id);
                      }}>
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
        <ErrorNote message={test.error ? errorMessage(test.error) : null} />
      </CardBody>

      <ChannelDialog open={creating} onOpenChange={setCreating} />
      <ChannelDialog key={editing?.id ?? "none"} channel={editing ?? undefined} open={!!editing} onOpenChange={(o) => !o && setEditing(null)} />
    </Card>
  );
}

function ChannelDialog({ channel, open, onOpenChange }: {
  channel?: NotificationChannel; open: boolean; onOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const editing = !!channel;
  const [type, setType] = useState<ChannelType | null>(channel?.type ?? null);
  const [name, setName] = useState(channel?.name ?? "");
  const [enabled, setEnabled] = useState(channel?.enabled ?? true);
  const [target, setTarget] = useState(channel?.type === "EMAIL" ? channel.target : "");
  const [secret, setSecret] = useState("");
  const [testResult, setTestResult] = useState<ChannelTestResult | null>(null);

  const save = useMutation({
    mutationFn: () => {
      const input: ChannelInput = {
        name: name.trim(), type: type!, enabled,
        target: target.trim() || undefined, signingSecret: secret.trim() || undefined,
      };
      return channel ? notificationsApi.updateChannel(channel.id, input) : notificationsApi.createChannel(input);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["notifications"] });
      setOpen(false);
    },
  });
  const test = useMutation({
    mutationFn: () => notificationsApi.testChannel(channel!.id),
    onSuccess: (r) => {
      setTestResult(r);
      queryClient.invalidateQueries({ queryKey: ["notifications", "deliveries"] });
    },
  });

  function setOpen(next: boolean) {
    if (!next && !editing) {
      setType(null); setName(""); setEnabled(true); setTarget(""); setSecret(""); save.reset();
    }
    if (!next) setTestResult(null);
    onOpenChange(next);
  }

  function generateSecret() {
    const bytes = new Uint8Array(24);
    crypto.getRandomValues(bytes);
    setSecret(Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join(""));
  }

  const meta = type ? CHANNEL_META[type] : null;
  const targetRequired = !editing || type === "EMAIL";
  const secretRequired = type === "WEBHOOK" && !editing;
  const canSave = !!type && !!name.trim() && (!targetRequired || !!target.trim()) && (!secretRequired || secret.trim().length >= 16);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent
        title={channel ? `Edit ${channel.name}` : meta ? `New ${meta.label} channel` : "Add channel"}
        description={meta ? meta.hint : "Choose where notifications should be delivered."}
        className="max-w-xl"
      >
        {!type ? (
          <div className="grid gap-2 sm:grid-cols-2">
            {CHANNEL_TYPES.map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setType(t)}
                className="flex items-center gap-3 rounded-xl border border-border p-3 text-left transition-colors hover:border-jade-500/50 hover:bg-surface-2/60 cursor-pointer"
              >
                <ChannelIcon type={t} />
                <span>
                  <span className="block text-sm font-medium">{CHANNEL_META[t].label}</span>
                  <span className="block text-xs text-muted">{CHANNEL_META[t].targetLabel}</span>
                </span>
              </button>
            ))}
          </div>
        ) : (
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              if (canSave) save.mutate();
            }}
          >
            <div>
              <Label htmlFor="channel-name">Name</Label>
              <Input id="channel-name" required autoFocus maxLength={120} value={name} onChange={(e) => setName(e.target.value)}
                placeholder={type === "EMAIL" ? "Payments on-call" : `${meta!.label} – #payments-alerts`} />
            </div>
            <div>
              <Label htmlFor="channel-target">{meta!.targetLabel}</Label>
              <Input
                id="channel-target"
                type={type === "PAGERDUTY" ? "password" : "text"}
                autoComplete="off"
                value={target}
                onChange={(e) => setTarget(e.target.value)}
                placeholder={channel && type !== "EMAIL" ? `${channel.target} (leave blank to keep)` : meta!.placeholder}
              />
              {editing && type !== "EMAIL" && <p className="mt-1 text-xs text-muted">Stored value is encrypted; enter a new one only to replace it.</p>}
            </div>
            {type === "WEBHOOK" && (
              <div>
                <Label htmlFor="channel-secret">Signing secret</Label>
                <div className="flex gap-2">
                  <Input id="channel-secret" type="text" autoComplete="off" className="font-mono" value={secret}
                    onChange={(e) => setSecret(e.target.value)}
                    placeholder={editing ? "Leave blank to keep the current secret" : "At least 16 characters"} />
                  <Button type="button" variant="secondary" onClick={generateSecret}><KeyRound className="h-4 w-4" /> Generate</Button>
                </div>
                <p className="mt-1 text-xs text-muted">Copy it now — it is encrypted and cannot be displayed again.</p>
              </div>
            )}
            <Toggle id="channel-enabled" checked={enabled} onChange={setEnabled} label="Channel enabled" />

            {testResult && (
              <div className={cn("rounded-lg border px-3 py-2 text-sm", testResult.success ? "border-jade-500/30 bg-jade-500/10 text-jade-400" : "border-danger/30 bg-danger/10 text-danger")}>
                {testResult.success ? "Test sent: " : "Test failed: "}{testResult.detail}
              </div>
            )}
            <ErrorNote message={save.error ? errorMessage(save.error) : test.error ? errorMessage(test.error) : null} />
            <div className="flex flex-wrap justify-end gap-2">
              {!editing && <Button type="button" variant="ghost" className="mr-auto" onClick={() => setType(null)}>Back</Button>}
              {channel && (
                <Button type="button" variant="secondary" className="mr-auto" loading={test.isPending}
                  disabled={!channel.enabled} onClick={() => test.mutate()}>
                  <Send className="h-4 w-4" /> Test
                </Button>
              )}
              <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
              <Button type="submit" loading={save.isPending} disabled={!canSave}>{editing ? "Save changes" : "Create channel"}</Button>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
