"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowDown, Layers, Pencil, Plus, Save, Timer, Trash2 } from "lucide-react";
import { notificationsApi, slaApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { humanize } from "@/lib/utils";
import {
  Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, Input, Label, Select, Skeleton,
} from "@/components/ui/primitives";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { SeverityBadge } from "@/components/ui/status";
import type { EscalationPolicy, NotificationChannel, Severity, SlaTarget } from "@/types/api";
import { ChannelPicker, SEVERITIES, Toggle, channelNames } from "./shared";

export function EscalationTab() {
  return (
    <div className="space-y-6">
      <PoliciesCard />
      <SlaTargetsCard />
    </div>
  );
}

function PoliciesCard() {
  const queryClient = useQueryClient();
  const policies = useQuery({ queryKey: ["notifications", "policies"], queryFn: notificationsApi.policies });
  const channels = useQuery({ queryKey: ["notifications", "channels"], queryFn: notificationsApi.channels });
  const [editing, setEditing] = useState<EscalationPolicy | null>(null);
  const [creating, setCreating] = useState(false);
  const remove = useMutation({
    mutationFn: (id: string) => notificationsApi.deletePolicy(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications", "policies"] }),
  });
  const allChannels = channels.data ?? [];

  return (
    <Card>
      <CardHeader
        icon={<Layers className="h-4 w-4" />}
        title="Escalation policies"
        subtitle="While an incident (or critical alert) stays unacknowledged, each level is notified after its delay. Checked every minute."
        actions={<Button size="sm" onClick={() => setCreating(true)} disabled={!allChannels.length}><Plus className="h-4 w-4" /> Add policy</Button>}
      />
      <CardBody>
        <ErrorNote message={policies.error ? errorMessage(policies.error) : remove.error ? errorMessage(remove.error) : null} />
        {policies.isLoading ? (
          <Skeleton className="h-32 w-full" />
        ) : !policies.data?.length ? (
          <Empty icon={<Layers className="h-8 w-8" />} title="No escalation policies"
            hint="Without a policy, unacknowledged incidents are only surfaced in the console and through routing rules." />
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {policies.data.map((p) => (
              <div key={p.id} className={`rounded-xl border border-border bg-surface-2/40 p-4 ${p.enabled ? "" : "opacity-60"}`}>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-medium">{p.name}</p>
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted">
                      Incidents <SeverityBadge severity={p.minSeverity} /> and above · critical alerts
                      {!p.enabled && <Badge tone="gray">Disabled</Badge>}
                    </div>
                  </div>
                  <div className="flex">
                    <Button size="sm" variant="ghost" onClick={() => setEditing(p)} aria-label={`Edit ${p.name}`}><Pencil className="h-3.5 w-3.5" /></Button>
                    <Button size="sm" variant="ghost" aria-label={`Delete ${p.name}`} loading={remove.isPending && remove.variables === p.id}
                      onClick={() => window.confirm(`Delete escalation policy "${p.name}"?`) && remove.mutate(p.id)}>
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
                <ol className="mt-3 space-y-1.5">
                  {p.levels.map((l, i) => (
                    <li key={l.level}>
                      {i > 0 && <ArrowDown className="mx-auto my-0.5 h-3 w-3 text-muted" />}
                      <div className="flex items-center gap-3 rounded-lg border border-border bg-[var(--bg)] px-3 py-2 text-sm">
                        <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-jade-500/15 font-mono text-[11px] text-jade-400">L{l.level}</span>
                        <span className="text-xs text-muted">after {l.delayMinutes} min{i > 0 ? " more" : ""}</span>
                        <span className="truncate">{channelNames(l.channelIds, allChannels)}</span>
                      </div>
                    </li>
                  ))}
                </ol>
              </div>
            ))}
          </div>
        )}
      </CardBody>
      <PolicyDialog channels={allChannels} open={creating} onOpenChange={setCreating} />
      <PolicyDialog key={editing?.id ?? "none"} channels={allChannels} policy={editing ?? undefined} open={!!editing}
        onOpenChange={(o) => !o && setEditing(null)} />
    </Card>
  );
}

interface LevelDraft { delayMinutes: number; channelIds: string[] }

function PolicyDialog({ policy, channels, open, onOpenChange }: {
  policy?: EscalationPolicy; channels: NotificationChannel[]; open: boolean; onOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const initialLevels: LevelDraft[] = policy?.levels.map((l) => ({ delayMinutes: l.delayMinutes, channelIds: l.channelIds }))
    ?? [{ delayMinutes: 5, channelIds: [] }, { delayMinutes: 15, channelIds: [] }];
  const [name, setName] = useState(policy?.name ?? "");
  const [minSeverity, setMinSeverity] = useState<Severity>(policy?.minSeverity ?? "HIGH");
  const [enabled, setEnabled] = useState(policy?.enabled ?? true);
  const [levels, setLevels] = useState<LevelDraft[]>(initialLevels);

  const save = useMutation({
    mutationFn: () => {
      const input = { name: name.trim(), minSeverity, enabled, levels };
      return policy ? notificationsApi.updatePolicy(policy.id, input) : notificationsApi.createPolicy(input);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["notifications", "policies"] });
      setOpen(false);
    },
  });

  function setOpen(next: boolean) {
    if (!next && !policy) {
      setName(""); setMinSeverity("HIGH"); setEnabled(true); setLevels(initialLevels); save.reset();
    }
    onOpenChange(next);
  }

  const setLevel = (i: number, patch: Partial<LevelDraft>) => setLevels((ls) => ls.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));
  const canSave = !!name.trim() && levels.length > 0
    && levels.every((l) => l.channelIds.length > 0 && l.delayMinutes >= 0 && l.delayMinutes <= 1440);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent title={policy ? "Edit escalation policy" : "New escalation policy"}
        description="Level 1 fires after its delay from opening; each next level after its delay from the previous one."
        className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); if (canSave) save.mutate(); }}>
          <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_200px]">
            <div>
              <Label htmlFor="policy-name">Name</Label>
              <Input id="policy-name" required autoFocus maxLength={120} value={name} onChange={(e) => setName(e.target.value)} placeholder="Payments on-call" />
            </div>
            <div>
              <Label htmlFor="policy-severity">Incidents from</Label>
              <Select id="policy-severity" className="w-full" value={minSeverity} onChange={(e) => setMinSeverity(e.target.value as Severity)}>
                {SEVERITIES.map((s) => <option key={s} value={s}>{humanize(s)} and above</option>)}
              </Select>
            </div>
          </div>
          <div className="space-y-3">
            {levels.map((l, i) => (
              <div key={i} className="rounded-xl border border-border p-3">
                <div className="mb-2 flex flex-wrap items-center gap-3">
                  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-jade-500/15 font-mono text-[11px] text-jade-400">L{i + 1}</span>
                  <Label htmlFor={`level-delay-${i}`} className="mb-0">Notify after</Label>
                  <Input id={`level-delay-${i}`} type="number" min={0} max={1440} className="h-8 w-24" value={l.delayMinutes}
                    onChange={(e) => setLevel(i, { delayMinutes: Number(e.target.value) })} />
                  <span className="text-xs text-muted">min {i === 0 ? "unacknowledged" : "after L" + i}</span>
                  {levels.length > 1 && (
                    <Button type="button" size="sm" variant="ghost" className="ml-auto" aria-label={`Remove level ${i + 1}`}
                      onClick={() => setLevels((ls) => ls.filter((_, idx) => idx !== i))}>
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  )}
                </div>
                <ChannelPicker idPrefix={`level-${i}`} channels={channels} value={l.channelIds} onChange={(ids) => setLevel(i, { channelIds: ids })} />
              </div>
            ))}
            {levels.length < 10 && (
              <Button type="button" size="sm" variant="secondary" onClick={() => setLevels((ls) => [...ls, { delayMinutes: 15, channelIds: [] }])}>
                <Plus className="h-4 w-4" /> Add level
              </Button>
            )}
          </div>
          <Toggle id="policy-enabled" checked={enabled} onChange={setEnabled} label="Policy enabled" />
          <ErrorNote message={save.error ? errorMessage(save.error) : null} />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button type="submit" loading={save.isPending} disabled={!canSave}>{policy ? "Save changes" : "Create policy"}</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------- SLA targets

function SlaTargetsCard() {
  const targets = useQuery({ queryKey: ["incidents", "sla-targets"], queryFn: slaApi.targets });
  return (
    <Card>
      <CardHeader icon={<Timer className="h-4 w-4" />} title="Incident SLA targets"
        subtitle="Time to acknowledge and to resolve, per severity. Breaches are flagged every minute and emit INCIDENT_SLA_BREACHED." />
      <CardBody>
        <ErrorNote message={targets.error ? errorMessage(targets.error) : null} />
        {targets.isLoading || !targets.data ? <Skeleton className="h-40 w-full" /> : <SlaForm key={targets.dataUpdatedAt} targets={targets.data} />}
      </CardBody>
    </Card>
  );
}

function SlaForm({ targets }: { targets: SlaTarget[] }) {
  const queryClient = useQueryClient();
  const [values, setValues] = useState(() => targets.map((t) => ({ ...t })));
  const save = useMutation({
    mutationFn: () => slaApi.update(values.map(({ severity, ackMinutes, resolveMinutes }) => ({ severity, ackMinutes, resolveMinutes }))),
    onSuccess: (data) => queryClient.setQueryData(["incidents", "sla-targets"], data),
  });
  const set = (severity: Severity, patch: Partial<SlaTarget>) =>
    setValues((vs) => vs.map((v) => (v.severity === severity ? { ...v, ...patch } : v)));
  const valid = values.every((v) => v.ackMinutes >= 1 && v.resolveMinutes >= v.ackMinutes);
  const dirty = values.some((v, i) => v.ackMinutes !== targets[i]?.ackMinutes || v.resolveMinutes !== targets[i]?.resolveMinutes);

  return (
    <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); if (valid) save.mutate(); }}>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-[11px] uppercase tracking-wider text-muted">
              <th className="py-2 pr-3 font-medium">Severity</th>
              <th className="px-3 py-2 font-medium">Acknowledge within (min)</th>
              <th className="px-3 py-2 font-medium">Resolve within (min)</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {values.map((v) => (
              <tr key={v.severity}>
                <td className="py-2.5 pr-3"><SeverityBadge severity={v.severity} /></td>
                <td className="px-3 py-2.5">
                  <Input type="number" min={1} max={10080} className="h-9 w-32" aria-label={`${v.severity} acknowledge minutes`}
                    value={v.ackMinutes} onChange={(e) => set(v.severity, { ackMinutes: Number(e.target.value) })} />
                </td>
                <td className="px-3 py-2.5">
                  <Input type="number" min={1} max={43200} className="h-9 w-32" aria-label={`${v.severity} resolve minutes`}
                    value={v.resolveMinutes} onChange={(e) => set(v.severity, { resolveMinutes: Number(e.target.value) })} />
                </td>
                <td className="px-3 py-2.5 text-xs text-muted">{v.customized ? "Custom" : "Default"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!valid && <p className="text-xs text-danger">Targets must be at least 1 minute and resolve must not be shorter than acknowledge.</p>}
      <p className="text-xs text-muted">New targets apply to incidents opened from now on (and tighten existing ones when severity is escalated).</p>
      <ErrorNote message={save.error ? errorMessage(save.error) : null} />
      <div className="flex justify-end">
        <Button type="submit" loading={save.isPending} disabled={!valid || !dirty}><Save className="h-4 w-4" /> Save targets</Button>
      </div>
    </form>
  );
}
