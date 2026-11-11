"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookCheck, FileText, ListChecks, Plus, Save, Sparkles, Trash2 } from "lucide-react";
import { postmortemApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission } from "@/lib/auth/session";
import { cn, formatDateTime, timeAgo } from "@/lib/utils";
import {
  Badge, Button, Card, CardBody, CardHeader, ErrorNote, Input, Label, Skeleton, Textarea,
} from "@/components/ui/primitives";
import type { Postmortem, PostmortemActionItem, PostmortemInput, PostmortemTimelineItem } from "@/types/api";

/** ISO instant -> value for <input type="datetime-local"> in the browser's zone. */
function toLocalInput(iso: string) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fromLocalInput(value: string) {
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? new Date().toISOString() : d.toISOString();
}

function toInput(p: Postmortem): PostmortemInput {
  return {
    summary: p.summary ?? "",
    impact: p.impact ?? "",
    rootCause: p.rootCause ?? "",
    timeline: p.timeline.map((t) => ({ ...t })),
    actionItems: p.actionItems.map((a) => ({ ...a })),
  };
}

function clean(input: PostmortemInput): PostmortemInput {
  return {
    summary: input.summary?.trim() || undefined,
    impact: input.impact?.trim() || undefined,
    rootCause: input.rootCause?.trim() || undefined,
    timeline: input.timeline.filter((t) => t.text.trim()).map((t) => ({ at: t.at, text: t.text.trim() })),
    actionItems: input.actionItems.filter((a) => a.text.trim()).map((a) => ({
      text: a.text.trim(), owner: a.owner?.trim() || undefined, dueDate: a.dueDate || undefined, done: a.done,
    })),
  };
}

export function PostmortemSection({ reference }: { reference: string }) {
  const query = useQuery({ queryKey: ["incidents", "postmortem", reference], queryFn: () => postmortemApi.get(reference) });

  if (query.isLoading) {
    return (
      <Card>
        <CardBody className="space-y-3">
          <Skeleton className="h-6 w-48" />
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-28 w-full" />
        </CardBody>
      </Card>
    );
  }
  if (query.error || !query.data) {
    return <ErrorNote message={query.error ? errorMessage(query.error) : "Postmortem unavailable"} />;
  }
  // Re-mount the form whenever the server copy changes so local edits start from it.
  return <PostmortemForm key={`${query.data.updatedAt ?? "new"}-${query.data.status}`} reference={reference} postmortem={query.data} />;
}

function PostmortemForm({ reference, postmortem }: { reference: string; postmortem: Postmortem }) {
  const canEdit = usePermission("INCIDENT_MANAGE");
  const canUseAi = usePermission("AI_USE");
  const queryClient = useQueryClient();
  const [form, setForm] = useState<PostmortemInput>(() => toInput(postmortem));
  const [dirty, setDirty] = useState(!postmortem.exists);
  const published = postmortem.status === "PUBLISHED";

  const onSaved = (p: Postmortem) => {
    queryClient.setQueryData(["incidents", "postmortem", reference], p);
    return queryClient.invalidateQueries({ queryKey: ["incidents", "detail", reference] });
  };
  const save = useMutation({ mutationFn: () => postmortemApi.save(reference, clean(form)), onSuccess: onSaved });
  const publish = useMutation({
    mutationFn: async () => {
      if (dirty || !postmortem.exists) await postmortemApi.save(reference, clean(form));
      return postmortemApi.publish(reference);
    },
    onSuccess: onSaved,
  });
  const draft = useMutation({ mutationFn: () => postmortemApi.draftWithAi(reference), onSuccess: onSaved });

  const update = (patch: Partial<PostmortemInput>) => {
    setForm((f) => ({ ...f, ...patch }));
    setDirty(true);
  };
  const setTimelineItem = (i: number, patch: Partial<PostmortemTimelineItem>) =>
    update({ timeline: form.timeline.map((t, idx) => (idx === i ? { ...t, ...patch } : t)) });
  const setActionItem = (i: number, patch: Partial<PostmortemActionItem>) =>
    update({ actionItems: form.actionItems.map((a, idx) => (idx === i ? { ...a, ...patch } : a)) });

  const error = save.error ?? publish.error ?? draft.error;
  const readOnly = !canEdit;
  const doneCount = form.actionItems.filter((a) => a.done).length;

  return (
    <Card>
      <CardHeader
        icon={<FileText className="h-4 w-4" />}
        title="Postmortem"
        subtitle={published
          ? `Published by ${postmortem.publishedBy ?? "—"} · ${formatDateTime(postmortem.publishedAt)}`
          : postmortem.exists
            ? `Draft · last edited by ${postmortem.updatedBy ?? "—"} ${timeAgo(postmortem.updatedAt)}`
            : "Not started · prefilled from the incident record"}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={published ? "green" : "amber"} dot>{published ? "Published" : "Draft"}</Badge>
            {postmortem.aiEngine && <Badge tone="brass"><Sparkles className="h-3 w-3" />{postmortem.aiEngine}</Badge>}
          </div>
        }
      />
      <CardBody className="space-y-5">
        {canEdit && !published && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-jade-500/30 bg-jade-500/5 px-4 py-3">
            <p className="text-sm text-muted">
              Generate a blameless draft from the timeline, impact and root-cause analysis. It replaces the summary, impact and root cause; action items are merged.
            </p>
            {canUseAi && (
              <Button size="sm" variant="outline" loading={draft.isPending} onClick={() => draft.mutate()}>
                {!draft.isPending && <Sparkles className="h-4 w-4" />} Draft with AI
              </Button>
            )}
          </div>
        )}

        <Field label="Summary" htmlFor="pm-summary">
          <Textarea id="pm-summary" readOnly={readOnly} className="min-h-24" value={form.summary ?? ""}
            onChange={(e) => update({ summary: e.target.value })} placeholder="What happened and how it was resolved." />
        </Field>
        <Field label="Impact" htmlFor="pm-impact">
          <Textarea id="pm-impact" readOnly={readOnly} value={form.impact ?? ""}
            onChange={(e) => update({ impact: e.target.value })} placeholder="Customers, payments and amount affected; duration; SLA." />
        </Field>
        <Field label="Root cause" htmlFor="pm-root-cause">
          <Textarea id="pm-root-cause" readOnly={readOnly} className="min-h-24" value={form.rootCause ?? ""}
            onChange={(e) => update({ rootCause: e.target.value })} placeholder="The underlying cause and contributing factors (blameless)." />
        </Field>

        <section>
          <div className="mb-2 flex items-center justify-between">
            <h4 className="text-xs font-medium uppercase tracking-wider text-muted">Timeline · {form.timeline.length} entries</h4>
            {canEdit && (
              <Button size="sm" variant="ghost" onClick={() => update({ timeline: [...form.timeline, { at: new Date().toISOString(), text: "" }] })}>
                <Plus className="h-4 w-4" /> Add entry
              </Button>
            )}
          </div>
          <ol className="max-h-96 space-y-2 overflow-y-auto pr-1">
            {form.timeline.map((t, i) => (
              <li key={i} className="flex flex-col gap-2 rounded-lg border border-border bg-surface-2/40 p-2 sm:flex-row sm:items-center">
                <Input type="datetime-local" aria-label="Time" readOnly={readOnly} className="h-9 sm:w-52"
                  value={toLocalInput(t.at)} onChange={(e) => setTimelineItem(i, { at: fromLocalInput(e.target.value) })} />
                <Input aria-label="Event" readOnly={readOnly} className="h-9 flex-1" value={t.text}
                  onChange={(e) => setTimelineItem(i, { text: e.target.value })} />
                {canEdit && (
                  <Button size="icon" variant="ghost" aria-label="Remove entry"
                    onClick={() => update({ timeline: form.timeline.filter((_, idx) => idx !== i) })}>
                    <Trash2 className="h-4 w-4" />
                  </Button>
                )}
              </li>
            ))}
          </ol>
        </section>

        <section>
          <div className="mb-2 flex items-center justify-between">
            <h4 className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-muted">
              <ListChecks className="h-3.5 w-3.5" /> Action items · {doneCount}/{form.actionItems.length} done
            </h4>
            {canEdit && (
              <Button size="sm" variant="ghost" onClick={() => update({ actionItems: [...form.actionItems, { text: "", owner: "", done: false }] })}>
                <Plus className="h-4 w-4" /> Add item
              </Button>
            )}
          </div>
          {form.actionItems.length === 0 ? (
            <p className="text-sm text-muted">No follow-up actions yet.</p>
          ) : (
            <ul className="space-y-2">
              {form.actionItems.map((a, i) => (
                <li key={i} className="grid gap-2 rounded-lg border border-border bg-surface-2/40 p-2 md:grid-cols-[auto_minmax(0,1fr)_160px_150px_auto] md:items-center">
                  <input type="checkbox" aria-label="Done" disabled={readOnly} checked={a.done}
                    onChange={(e) => setActionItem(i, { done: e.target.checked })}
                    className="h-4 w-4 cursor-pointer accent-[var(--color-jade-600,#0e7c5b)]" />
                  <Input aria-label="Action" readOnly={readOnly} className={cn("h-9", a.done && "text-muted line-through")}
                    value={a.text} placeholder="What will be done" onChange={(e) => setActionItem(i, { text: e.target.value })} />
                  <Input aria-label="Owner" readOnly={readOnly} className="h-9" value={a.owner ?? ""} placeholder="Owner"
                    onChange={(e) => setActionItem(i, { owner: e.target.value })} />
                  <Input aria-label="Due date" type="date" readOnly={readOnly} className="h-9" value={a.dueDate ?? ""}
                    onChange={(e) => setActionItem(i, { dueDate: e.target.value || undefined })} />
                  {canEdit ? (
                    <Button size="icon" variant="ghost" aria-label="Remove item"
                      onClick={() => update({ actionItems: form.actionItems.filter((_, idx) => idx !== i) })}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  ) : <span />}
                </li>
              ))}
            </ul>
          )}
        </section>

        <ErrorNote message={error ? errorMessage(error) : null} />

        {canEdit && (
          <div className="flex flex-wrap items-center justify-end gap-2 border-t border-border pt-4">
            {dirty && <span className="mr-auto text-xs text-warning">Unsaved changes</span>}
            <Button variant="secondary" loading={save.isPending} disabled={!dirty || publish.isPending} onClick={() => save.mutate()}>
              {!save.isPending && <Save className="h-4 w-4" />} {published ? "Save changes" : "Save draft"}
            </Button>
            {!published && (
              <Button loading={publish.isPending} disabled={save.isPending || !form.summary?.trim() || !form.rootCause?.trim()}
                onClick={() => publish.mutate()}>
                {!publish.isPending && <BookCheck className="h-4 w-4" />} Publish
              </Button>
            )}
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function Field({ label, htmlFor, children }: { label: string; htmlFor: string; children: React.ReactNode }) {
  return (
    <div>
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
    </div>
  );
}
