"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Dropdown from "@radix-ui/react-dropdown-menu";
import {
  AlertTriangle, Check, ChevronLeft, ChevronRight, Copy, KeyRound, Lock, LockOpen, Mail, MailX, MoreHorizontal, Send,
  ShieldCheck, ShieldOff, UserCheck, UserPlus, Users, UserX,
} from "lucide-react";
import {
  Badge, Button, Card, CardBody, CardHeader, Empty, ErrorNote, Input, Label, PageHeader, Segmented, Select, Skeleton,
  type Tone,
} from "@/components/ui/primitives";
import { Dialog, DialogClose, DialogContent } from "@/components/ui/dialog";
import { usersApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission, useSession } from "@/lib/auth/session";
import { cn, humanize, timeAgo } from "@/lib/utils";
import type { CreatedUser, Permission, Role, User, UserStatus } from "@/types/api";

const ROLES: Role[] = ["ADMIN", "OPERATIONS_MANAGER", "ANALYST", "SUPPORT", "AUDITOR"];

/** Mirrors the backend Role catalogue (com.payops.identity.domain.Role). */
const ROLE_PERMISSIONS: Record<Role, Permission[]> = {
  ADMIN: [
    "PAYMENT_READ", "PAYMENT_WRITE", "PROVIDER_READ", "PROVIDER_MANAGE", "ALERT_READ", "ALERT_MANAGE", "INCIDENT_READ",
    "INCIDENT_MANAGE", "ANALYTICS_READ", "AI_USE", "USER_MANAGE", "AUDIT_READ",
  ],
  OPERATIONS_MANAGER: [
    "PAYMENT_READ", "PAYMENT_WRITE", "PROVIDER_READ", "PROVIDER_MANAGE", "ALERT_READ", "ALERT_MANAGE", "INCIDENT_READ",
    "INCIDENT_MANAGE", "ANALYTICS_READ", "AI_USE", "AUDIT_READ",
  ],
  ANALYST: ["PAYMENT_READ", "PROVIDER_READ", "ALERT_READ", "INCIDENT_READ", "ANALYTICS_READ", "AI_USE"],
  SUPPORT: ["PAYMENT_READ", "PROVIDER_READ", "ALERT_READ", "ALERT_MANAGE", "INCIDENT_READ", "AI_USE"],
  AUDITOR: ["PAYMENT_READ", "PROVIDER_READ", "ALERT_READ", "INCIDENT_READ", "ANALYTICS_READ", "AUDIT_READ"],
};
const ALL_PERMISSIONS = ROLE_PERMISSIONS.ADMIN;
const ROLE_SHORT: Record<Role, string> = { ADMIN: "Admin", OPERATIONS_MANAGER: "Ops Mgr", ANALYST: "Analyst", SUPPORT: "Support", AUDITOR: "Auditor" };

const PAGE_SIZE = 25;
type UserAction = "disable" | "enable" | "unlock" | "reset-mfa";
interface Confirmation { user: User; action: UserAction | "reset-password" | "resend-invite" | "revoke-invite"; title: string; description: string; danger: boolean }

const STATUS_TONE: Record<UserStatus, Tone> = { ACTIVE: "green", DISABLED: "gray", INVITED: "brass", PENDING_VERIFICATION: "amber" };
interface Credentials { title: string; created: CreatedUser }

// ------------------------------------------------------------------ dialogs
function CredentialsDialog({ value, onClose }: { value: Credentials | null; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    if (!value) return;
    try {
      await navigator.clipboard.writeText(value.created.temporaryPassword);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  };
  return (
    <Dialog open={!!value} onOpenChange={(o) => { if (!o) { setCopied(false); onClose(); } }}>
      {value && (
        <DialogContent title={value.title} description={`Temporary credentials for ${value.created.user.fullName} (${value.created.user.email})`}>
          <div className="space-y-4">
            <div className="flex items-start gap-2 rounded-lg border border-warning/30 bg-warning/10 px-3 py-2 text-sm text-warning">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>This password is shown only once. Share it through a secure channel — the user must change it at first sign-in.</span>
            </div>
            <div>
              <Label>Temporary password</Label>
              <div className="flex items-center gap-2">
                <code className="flex-1 break-all rounded-lg border border-border bg-surface-2 px-3 py-2 font-mono text-sm">{value.created.temporaryPassword}</code>
                <Button variant="secondary" size="icon" onClick={copy} aria-label="Copy temporary password">
                  {copied ? <Check className="h-4 w-4 text-jade-400" /> : <Copy className="h-4 w-4" />}
                </Button>
              </div>
            </div>
            <div className="flex justify-end">
              <DialogClose asChild><Button>I have shared it securely</Button></DialogClose>
            </div>
          </div>
        </DialogContent>
      )}
    </Dialog>
  );
}

type InviteMode = "email" | "password";

function InviteDialog({ open, onOpenChange, onCreated, onInvited }: {
  open: boolean; onOpenChange: (o: boolean) => void; onCreated: (c: CreatedUser) => void; onInvited: (u: User) => void;
}) {
  const [mode, setMode] = useState<InviteMode>("email");
  const [email, setEmail] = useState("");
  const [fullName, setFullName] = useState("");
  const [role, setRole] = useState<Role>("ANALYST");
  const reset = () => {
    setEmail("");
    setFullName("");
    setRole("ANALYST");
  };
  const create = useMutation({
    mutationFn: async (): Promise<{ created?: CreatedUser; invited?: User }> =>
      mode === "email"
        ? { invited: await usersApi.invite(email.trim(), fullName.trim(), role) }
        : { created: await usersApi.create(email.trim(), fullName.trim(), role) },
    onSuccess: ({ created, invited }) => {
      reset();
      if (created) onCreated(created);
      if (invited) onInvited(invited);
    },
  });
  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) create.reset(); onOpenChange(o); }}>
      <DialogContent
        title="Invite user"
        description={mode === "email"
          ? "We email a secure link (valid 72 hours) so the user can choose their own password."
          : "Create the account now with a one-time temporary password that you share securely."}
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            create.mutate();
          }}
        >
          <Segmented<InviteMode>
            value={mode}
            onChange={(m) => { setMode(m); create.reset(); }}
            options={[{ value: "email", label: "Email invitation" }, { value: "password", label: "Temporary password" }]}
          />
          <div>
            <Label htmlFor="invite-email">Work email</Label>
            <Input id="invite-email" type="email" required autoComplete="off" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@company.com" />
          </div>
          <div>
            <Label htmlFor="invite-name">Full name</Label>
            <Input id="invite-name" required value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={120} />
          </div>
          <div>
            <Label htmlFor="invite-role">Role</Label>
            <Select id="invite-role" className="w-full" value={role} onChange={(e) => setRole(e.target.value as Role)}>
              {ROLES.map((r) => <option key={r} value={r}>{humanize(r)}</option>)}
            </Select>
            <p className="mt-1.5 text-xs text-muted">{ROLE_PERMISSIONS[role].length} of {ALL_PERMISSIONS.length} permissions. Grant the least privilege needed.</p>
          </div>
          <ErrorNote message={create.isError ? errorMessage(create.error) : null} />
          <div className="flex justify-end gap-2">
            <DialogClose asChild><Button type="button" variant="ghost">Cancel</Button></DialogClose>
            <Button type="submit" loading={create.isPending} disabled={!email.trim() || !fullName.trim()}>
              {mode === "email" ? <><Mail className="h-4 w-4" /> Send invitation</> : <><UserPlus className="h-4 w-4" /> Create user</>}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ------------------------------------------------------------------ table pieces
function RowActions({ user, self, onConfirm }: { user: User; self: boolean; onConfirm: (c: Confirmation) => void }) {
  const item = "flex cursor-pointer items-center gap-2 rounded-lg px-2 py-2 text-sm outline-none data-[highlighted]:bg-surface-2 data-[disabled]:pointer-events-none data-[disabled]:opacity-40";
  if (user.status === "INVITED") {
    return (
      <Dropdown.Root>
        <Dropdown.Trigger asChild>
          <Button variant="ghost" size="icon" aria-label={`Invitation actions for ${user.fullName}`}><MoreHorizontal className="h-4 w-4" /></Button>
        </Dropdown.Trigger>
        <Dropdown.Portal>
          <Dropdown.Content align="end" sideOffset={6} className="z-50 w-56 rounded-xl border border-border bg-[var(--bg)] p-1.5 shadow-2xl">
            <Dropdown.Item className={item} onSelect={() => onConfirm({ user, action: "resend-invite", title: "Resend invitation", description: `Send a new invitation link to ${user.email}? The previous link stops working.`, danger: false })}>
              <Send className="h-4 w-4" /> Resend invitation
            </Dropdown.Item>
            <Dropdown.Item className={cn(item, "text-danger")} onSelect={() => onConfirm({ user, action: "revoke-invite", title: "Revoke invitation", description: `Withdraw the invitation for ${user.email}? The link stops working and the pending account is removed.`, danger: true })}>
              <MailX className="h-4 w-4" /> Revoke invitation
            </Dropdown.Item>
          </Dropdown.Content>
        </Dropdown.Portal>
      </Dropdown.Root>
    );
  }
  return (
    <Dropdown.Root>
      <Dropdown.Trigger asChild>
        <Button variant="ghost" size="icon" aria-label={`Actions for ${user.fullName}`}><MoreHorizontal className="h-4 w-4" /></Button>
      </Dropdown.Trigger>
      <Dropdown.Portal>
        <Dropdown.Content align="end" sideOffset={6} className="z-50 w-56 rounded-xl border border-border bg-[var(--bg)] p-1.5 shadow-2xl">
          {user.locked && (
            <Dropdown.Item className={item} onSelect={() => onConfirm({ user, action: "unlock", title: "Unlock account", description: `Clear the lockout on ${user.email}? Failed-attempt counters are reset.`, danger: false })}>
              <LockOpen className="h-4 w-4" /> Unlock
            </Dropdown.Item>
          )}
          <Dropdown.Item className={item} onSelect={() => onConfirm({ user, action: "reset-password", title: "Reset password", description: `Issue a new temporary password for ${user.email}? Their sessions are revoked and they must change it at next sign-in.`, danger: true })}>
            <KeyRound className="h-4 w-4" /> Reset password
          </Dropdown.Item>
          <Dropdown.Item disabled={!user.mfaEnabled} className={item} onSelect={() => onConfirm({ user, action: "reset-mfa", title: "Reset MFA", description: `Remove the authenticator from ${user.email}? They will need to enrol again at next sign-in.`, danger: true })}>
            <ShieldOff className="h-4 w-4" /> Reset MFA
          </Dropdown.Item>
          <Dropdown.Separator className="my-1 h-px bg-border" />
          {user.status === "ACTIVE" ? (
            <Dropdown.Item disabled={self} className={cn(item, "text-danger")} onSelect={() => onConfirm({ user, action: "disable", title: "Disable user", description: `Disable ${user.email}? All of their sessions are revoked immediately.`, danger: true })}>
              <UserX className="h-4 w-4" /> Disable
            </Dropdown.Item>
          ) : (
            <Dropdown.Item className={item} onSelect={() => onConfirm({ user, action: "enable", title: "Enable user", description: `Re-enable access for ${user.email}?`, danger: false })}>
              <UserCheck className="h-4 w-4" /> Enable
            </Dropdown.Item>
          )}
        </Dropdown.Content>
      </Dropdown.Portal>
    </Dropdown.Root>
  );
}

function RoleMatrix() {
  return (
    <Card>
      <CardHeader
        title="Role → permission matrix"
        subtitle="Least privilege & segregation of duties: only Admin manages users; Auditor is read-only plus the audit trail."
        icon={<ShieldCheck className="h-4 w-4" />}
      />
      <CardBody className="overflow-x-auto">
        <table className="w-full min-w-[560px] text-xs">
          <thead>
            <tr className="text-left text-muted">
              <th scope="col" className="py-2 pr-3 font-medium">Permission</th>
              {ROLES.map((r) => <th key={r} scope="col" className="px-2 py-2 text-center font-medium">{ROLE_SHORT[r]}</th>)}
            </tr>
          </thead>
          <tbody>
            {ALL_PERMISSIONS.map((p) => (
              <tr key={p} className="border-t border-border/60">
                <th scope="row" className="py-1.5 pr-3 text-left font-mono font-normal text-muted">{p}</th>
                {ROLES.map((r) => {
                  const has = ROLE_PERMISSIONS[r].includes(p);
                  return (
                    <td key={r} className="px-2 py-1.5 text-center">
                      {has ? <Check className="mx-auto h-3.5 w-3.5 text-jade-400" aria-label="granted" /> : <span className="text-muted/40" aria-label="not granted">—</span>}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ page
function UsersAdmin() {
  const qc = useQueryClient();
  const me = useSession((s) => s.user);
  const [page, setPage] = useState(0);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [confirm, setConfirm] = useState<Confirmation | null>(null);
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [rowError, setRowError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const users = useQuery({ queryKey: ["users", page], queryFn: () => usersApi.list(page, PAGE_SIZE), placeholderData: (prev) => prev });
  const invalidate = () => qc.invalidateQueries({ queryKey: ["users"] });

  const changeRole = useMutation({
    mutationFn: ({ id, role }: { id: string; role: Role }) => usersApi.changeRole(id, role),
    onSuccess: () => { setRowError(null); void invalidate(); },
    onError: (e) => setRowError(errorMessage(e)),
  });

  const runAction = useMutation({
    mutationFn: async (c: Confirmation): Promise<CreatedUser | null> => {
      if (c.action === "reset-password") return usersApi.resetPassword(c.user.id);
      if (c.action === "resend-invite") await usersApi.resendInvitation(c.user.id);
      else if (c.action === "revoke-invite") await usersApi.revokeInvitation(c.user.id);
      else await usersApi.action(c.user.id, c.action);
      return null;
    },
    onSuccess: (created, c) => {
      setConfirm(null);
      setRowError(null);
      if (created) setCredentials({ title: "Password reset", created });
      if (c.action === "resend-invite") setNotice(`A new invitation link was sent to ${c.user.email}.`);
      if (c.action === "revoke-invite") setNotice(`The invitation for ${c.user.email} was revoked.`);
      void invalidate();
    },
  });

  const data = users.data;

  return (
    <>
      <PageHeader
        title="Users & roles"
        subtitle="Manage who can access PayOps360 and what they can do."
        actions={<Button onClick={() => setInviteOpen(true)}><UserPlus className="h-4 w-4" /> Invite user</Button>}
      />

      <div className="space-y-6">
        <Card>
          <CardHeader
            title="Team members"
            subtitle={data ? `${data.totalItems} user${data.totalItems === 1 ? "" : "s"}` : "Loading…"}
            icon={<Users className="h-4 w-4" />}
          />
          <CardBody className="space-y-3">
            <ErrorNote message={users.isError ? errorMessage(users.error) : rowError} />
            {notice && (
              <div role="status" className="flex items-center justify-between gap-3 rounded-lg border border-jade-500/30 bg-jade-500/10 px-3 py-2 text-sm text-jade-400">
                <span className="flex items-center gap-2"><Mail className="h-4 w-4 shrink-0" /> {notice}</span>
                <button onClick={() => setNotice(null)} className="text-xs text-muted hover:text-foreground cursor-pointer">Dismiss</button>
              </div>
            )}
            <div className="overflow-x-auto">
              <table className="w-full min-w-[860px] text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-muted">
                    <th scope="col" className="py-2 pr-3 font-medium">User</th>
                    <th scope="col" className="px-3 py-2 font-medium">Role</th>
                    <th scope="col" className="px-3 py-2 font-medium">Status</th>
                    <th scope="col" className="px-3 py-2 font-medium">MFA</th>
                    <th scope="col" className="px-3 py-2 font-medium">Last login</th>
                    <th scope="col" className="py-2 pl-3 text-right font-medium"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {users.isLoading && Array.from({ length: 5 }).map((_, i) => (
                    <tr key={i} className="border-b border-border/60"><td colSpan={6} className="py-3"><Skeleton className="h-8" /></td></tr>
                  ))}
                  {data?.items.map((u) => {
                    const self = u.id === me?.id;
                    return (
                      <tr key={u.id} className={cn("border-b border-border/60 hover:bg-surface-2/40", u.status === "DISABLED" && "opacity-60")}>
                        <td className="py-3 pr-3">
                          <div className="flex items-center gap-3">
                            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-ink-500 to-ink-700 text-[11px] font-bold text-white">
                              {u.fullName.split(" ").map((p) => p[0]).slice(0, 2).join("")}
                            </div>
                            <div className="min-w-0">
                              <p className="truncate font-medium">{u.fullName}{self && <span className="ml-1.5 text-xs text-muted">(you)</span>}</p>
                              <p className="truncate text-xs text-muted">{u.email}</p>
                            </div>
                          </div>
                        </td>
                        <td className="px-3 py-3">
                          <Select
                            aria-label={`Role for ${u.fullName}`}
                            className="h-8 text-xs"
                            value={u.role}
                            disabled={self || u.status === "DISABLED" || (changeRole.isPending && changeRole.variables?.id === u.id)}
                            title={self ? "You cannot change your own role" : undefined}
                            onChange={(e) => changeRole.mutate({ id: u.id, role: e.target.value as Role })}
                          >
                            {ROLES.map((r) => <option key={r} value={r}>{humanize(r)}</option>)}
                          </Select>
                        </td>
                        <td className="px-3 py-3">
                          <div className="flex flex-wrap gap-1.5">
                            <Badge tone={STATUS_TONE[u.status]} dot>{u.status === "INVITED" ? "Invited" : humanize(u.status)}</Badge>
                            {u.locked && <Badge tone="red"><Lock className="h-3 w-3" /> Locked</Badge>}
                            {u.mustChangePassword && <Badge tone="amber">Pending password</Badge>}
                          </div>
                        </td>
                        <td className="px-3 py-3">
                          {u.status === "INVITED" ? <span className="text-xs text-muted">—</span> : u.mfaEnabled ? <Badge tone="jade"><ShieldCheck className="h-3 w-3" /> On</Badge> : <Badge tone="amber"><ShieldOff className="h-3 w-3" /> Off</Badge>}
                        </td>
                        <td className="px-3 py-3 text-xs text-muted" title={u.lastLoginAt}>{u.lastLoginAt ? timeAgo(u.lastLoginAt) : "Never"}</td>
                        <td className="py-3 pl-3 text-right">
                          <RowActions user={u} self={self} onConfirm={(c) => { runAction.reset(); setConfirm(c); }} />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {data && data.items.length === 0 && <Empty icon={<Users className="h-8 w-8" />} title="No users yet" hint="Invite your first teammate." />}
            {data && data.totalPages > 1 && (
              <div className="flex items-center justify-between pt-2 text-xs text-muted">
                <span>Page {data.page + 1} of {data.totalPages}</span>
                <div className="flex gap-2">
                  <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)} aria-label="Previous page"><ChevronLeft className="h-4 w-4" /></Button>
                  <Button variant="secondary" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} aria-label="Next page"><ChevronRight className="h-4 w-4" /></Button>
                </div>
              </div>
            )}
          </CardBody>
        </Card>

        <RoleMatrix />
      </div>

      <InviteDialog
        open={inviteOpen}
        onOpenChange={setInviteOpen}
        onCreated={(created) => {
          setInviteOpen(false);
          setCredentials({ title: "User created", created });
          void invalidate();
        }}
        onInvited={(invited) => {
          setInviteOpen(false);
          setNotice(`Invitation sent to ${invited.email}. The link is valid for 72 hours.`);
          void invalidate();
        }}
      />

      <Dialog open={!!confirm} onOpenChange={(o) => { if (!o) setConfirm(null); }}>
        {confirm && (
          <DialogContent title={confirm.title} description={confirm.description}>
            <ErrorNote message={runAction.isError ? errorMessage(runAction.error) : null} />
            <div className="mt-4 flex justify-end gap-2">
              <DialogClose asChild><Button variant="ghost">Cancel</Button></DialogClose>
              <Button variant={confirm.danger ? "danger" : "primary"} loading={runAction.isPending} onClick={() => runAction.mutate(confirm)}>
                Confirm
              </Button>
            </div>
          </DialogContent>
        )}
      </Dialog>

      <CredentialsDialog value={credentials} onClose={() => setCredentials(null)} />
    </>
  );
}

export default function UsersPage() {
  const allowed = usePermission("USER_MANAGE");
  if (!allowed) {
    return (
      <Card>
        <Empty icon={<Lock className="h-8 w-8" />} title="Access restricted" hint="Managing users requires the USER_MANAGE permission." />
      </Card>
    );
  }
  return <UsersAdmin />;
}
