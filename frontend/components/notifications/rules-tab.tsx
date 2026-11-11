"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Route, Server, Trash2 } from "lucide-react";
import { notificationsApi, providersApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { humanize } from "@/lib/utils";
import {
  Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, Input, Label, Select, Skeleton,
} from "@/components/ui/primitives";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { SeverityBadge } from "@/components/ui/status";
import type { NotificationChannel, NotificationEventType, NotificationRule, Severity } from "@/types/api";
import { ChannelPicker, EVENT_META, EVENT_TYPES, SEVERITIES, Toggle, channelNames } from "./shared";

export function RulesTab() {
  const queryClient = useQueryClient();
  const rules = useQuery({ queryKey: ["notifications", "rules"], queryFn: notificationsApi.rules });
  const channels = useQuery({ queryKey: ["notifications", "channels"], queryFn: notificationsApi.channels });
  const [editing, setEditing] = useState<NotificationRule | null>(null);
  const [creating, setCreating] = useState(false);

  const remove = useMutation({
    mutationFn: (id: string) => notificationsApi.deleteRule(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications", "rules"] }),
  });
  const allChannels = channels.data ?? [];

  return (
    <Card>
      <CardHeader
        icon={<Route className="h-4 w-4" />}
        title="Routing rules"
        subtitle="Event type + minimum severity (+ provider) → channels. The same event goes out at most once per rule every 10 minutes."
        actions={<Button size="sm" onClick={() => setCreating(true)} disabled={!allChannels.length}><Plus className="h-4 w-4" /> Add rule</Button>}
      />
      <CardBody className="p-0">
        <div className="px-5"><ErrorNote message={rules.error ? errorMessage(rules.error) : remove.error ? errorMessage(remove.error) : null} /></div>
        {rules.isLoading ? (
          <div className="space-y-3 p-5">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-12 w-full" />)}</div>
        ) : !rules.data?.length ? (
          <Empty icon={<Route className="h-8 w-8" />} title="No routing rules"
            hint={allChannels.length ? "Add a rule to start notifying channels about alerts, incidents and provider health." : "Create a channel first, then route events to it."} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-[11px] uppercase tracking-wider text-muted">
                  <th className="px-5 py-2.5 font-medium">Rule</th>
                  <th className="px-3 py-2.5 font-medium">Event</th>
                  <th className="px-3 py-2.5 font-medium">Min severity</th>
                  <th className="px-3 py-2.5 font-medium">Provider</th>
                  <th className="px-3 py-2.5 font-medium">Channels</th>
                  <th className="px-5 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {rules.data.map((r) => (
                  <tr key={r.id} className={r.enabled ? "" : "opacity-60"}>
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{r.name}</span>
                        {!r.enabled && <Badge tone="gray">Disabled</Badge>}
                      </div>
                    </td>
                    <td className="px-3 py-3">{EVENT_META[r.eventType]?.label ?? humanize(r.eventType)}</td>
                    <td className="px-3 py-3"><SeverityBadge severity={r.minSeverity} /></td>
                    <td className="px-3 py-3 text-muted">{r.providerCode ? <span className="inline-flex items-center gap-1"><Server className="h-3 w-3" />{r.providerCode}</span> : "Any"}</td>
                    <td className="max-w-64 truncate px-3 py-3 text-muted">{channelNames(r.channelIds, allChannels)}</td>
                    <td className="whitespace-nowrap px-5 py-3 text-right">
                      <Button size="sm" variant="ghost" onClick={() => setEditing(r)} aria-label={`Edit ${r.name}`}><Pencil className="h-3.5 w-3.5" /></Button>
                      <Button size="sm" variant="ghost" aria-label={`Delete ${r.name}`}
                        loading={remove.isPending && remove.variables === r.id}
                        onClick={() => window.confirm(`Delete rule "${r.name}"?`) && remove.mutate(r.id)}>
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardBody>

      <RuleDialog channels={allChannels} open={creating} onOpenChange={setCreating} />
      <RuleDialog key={editing?.id ?? "none"} channels={allChannels} rule={editing ?? undefined} open={!!editing}
        onOpenChange={(o) => !o && setEditing(null)} />
    </Card>
  );
}

function RuleDialog({ rule, channels, open, onOpenChange }: {
  rule?: NotificationRule; channels: NotificationChannel[]; open: boolean; onOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(rule?.name ?? "");
  const [eventType, setEventType] = useState<NotificationEventType>(rule?.eventType ?? "INCIDENT_OPENED");
  const [minSeverity, setMinSeverity] = useState<Severity>(rule?.minSeverity ?? "HIGH");
  const [providerCode, setProviderCode] = useState(rule?.providerCode ?? "");
  const [channelIds, setChannelIds] = useState<string[]>(rule?.channelIds ?? []);
  const [enabled, setEnabled] = useState(rule?.enabled ?? true);
  const providers = useQuery({ queryKey: ["providers"], queryFn: providersApi.list, enabled: open, staleTime: 60_000 });

  const save = useMutation({
    mutationFn: () => {
      const input = { name: name.trim(), eventType, minSeverity, providerCode: providerCode || undefined, channelIds, enabled };
      return rule ? notificationsApi.updateRule(rule.id, input) : notificationsApi.createRule(input);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["notifications", "rules"] });
      setOpen(false);
    },
  });

  function setOpen(next: boolean) {
    if (!next && !rule) {
      setName(""); setEventType("INCIDENT_OPENED"); setMinSeverity("HIGH"); setProviderCode(""); setChannelIds([]); setEnabled(true);
      save.reset();
    }
    onOpenChange(next);
  }

  const canSave = !!name.trim() && channelIds.length > 0;

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent title={rule ? `Edit rule` : "New routing rule"} description="Notify channels when a matching event occurs." className="max-w-xl">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); if (canSave) save.mutate(); }}>
          <div>
            <Label htmlFor="rule-name">Name</Label>
            <Input id="rule-name" required autoFocus maxLength={120} value={name} onChange={(e) => setName(e.target.value)} placeholder="Critical incidents to on-call" />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <Label htmlFor="rule-event">Event</Label>
              <Select id="rule-event" className="w-full" value={eventType} onChange={(e) => setEventType(e.target.value as NotificationEventType)}>
                {EVENT_TYPES.map((t) => <option key={t} value={t}>{EVENT_META[t].label}</option>)}
              </Select>
              <p className="mt-1 text-xs text-muted">{EVENT_META[eventType].hint}</p>
            </div>
            <div>
              <Label htmlFor="rule-severity">Minimum severity</Label>
              <Select id="rule-severity" className="w-full" value={minSeverity} onChange={(e) => setMinSeverity(e.target.value as Severity)}>
                {SEVERITIES.map((s) => <option key={s} value={s}>{humanize(s)} and above</option>)}
              </Select>
            </div>
          </div>
          <div>
            <Label htmlFor="rule-provider">Provider filter</Label>
            <Select id="rule-provider" className="w-full" value={providerCode} onChange={(e) => setProviderCode(e.target.value)}>
              <option value="">Any provider</option>
              {providers.data?.map((p) => <option key={p.code} value={p.code}>{p.name} ({p.code})</option>)}
              {providerCode && !providers.data?.some((p) => p.code === providerCode) && <option value={providerCode}>{providerCode}</option>}
            </Select>
          </div>
          <div>
            <Label>Channels</Label>
            <ChannelPicker idPrefix="rule-channel" channels={channels} value={channelIds} onChange={setChannelIds} />
          </div>
          <Toggle id="rule-enabled" checked={enabled} onChange={setEnabled} label="Rule enabled" />
          <ErrorNote message={save.error ? errorMessage(save.error) : null} />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button type="submit" loading={save.isPending} disabled={!canSave}>{rule ? "Save changes" : "Create rule"}</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
