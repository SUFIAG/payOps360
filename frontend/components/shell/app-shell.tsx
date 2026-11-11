"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import * as Dropdown from "@radix-ui/react-dropdown-menu";
import {
  Activity, AlertTriangle, BarChart3, Bell, BellRing, Bot, CreditCard, FileClock, Gauge, Inbox, LogOut, Moon, Scale, Search, Server,
  ShieldCheck, Siren, Sparkles, Sun, Users,
} from "lucide-react";
import { Logo } from "./logo";
import { useTheme } from "./theme";
import { CopilotDrawer, useCopilot } from "./copilot-drawer";
import { CommandPalette, useCommandPalette } from "./command-palette";
import { useSession } from "@/lib/auth/session";
import { refreshSession } from "@/lib/api/client";
import { useQuery } from "@tanstack/react-query";
import { accountApi, authApi, devApi } from "@/lib/api/endpoints";
import { describeNotification, useLive, useLiveStream } from "@/lib/live";
import { cn, timeAgo } from "@/lib/utils";
import type { Permission } from "@/types/api";

const NAV: { href: string; label: string; icon: React.ElementType; permission?: Permission; section: string }[] = [
  { href: "/dashboard", label: "Command Center", icon: Gauge, section: "Operate" },
  { href: "/payments", label: "Payments", icon: CreditCard, permission: "PAYMENT_READ", section: "Operate" },
  { href: "/providers", label: "Providers", icon: Server, permission: "PROVIDER_READ", section: "Operate" },
  { href: "/alerts", label: "Alerts", icon: AlertTriangle, permission: "ALERT_READ", section: "Operate" },
  { href: "/incidents", label: "Incidents", icon: Siren, permission: "INCIDENT_READ", section: "Operate" },
  { href: "/reconciliation", label: "Reconciliation", icon: Scale, permission: "RECON_READ", section: "Operate" },
  { href: "/analytics", label: "Analytics", icon: BarChart3, permission: "ANALYTICS_READ", section: "Intelligence" },
  { href: "/copilot", label: "AI Copilot", icon: Bot, permission: "AI_USE", section: "Intelligence" },
  { href: "/admin/users", label: "Users & Roles", icon: Users, permission: "USER_MANAGE", section: "Govern" },
  { href: "/admin/audit", label: "Audit Trail", icon: FileClock, permission: "AUDIT_READ", section: "Govern" },
  { href: "/settings/notifications", label: "Notifications", icon: BellRing, permission: "NOTIFICATION_MANAGE", section: "Govern" },
  { href: "/settings/security", label: "Security", icon: ShieldCheck, section: "Govern" },
];
const DEV_MAILBOX = { href: "/admin/mailbox", label: "Dev Mailbox", icon: Inbox, permission: "USER_MANAGE" as Permission, section: "Govern" };

/** Restores the session from the HttpOnly refresh cookie and loads the profile. */
function useAuthGate() {
  const router = useRouter();
  const pathname = usePathname();
  const { status, user, accessToken } = useSession();

  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (!accessToken && !(await refreshSession())) {
        if (!cancelled) router.replace(`/login?next=${encodeURIComponent(pathname)}`);
        return;
      }
      if (!user) {
        try {
          const me = await accountApi.me();
          if (!cancelled) useSession.getState().setUser(me.user, me.organizationName);
        } catch {
          if (!cancelled) router.replace("/login");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [accessToken, user, router, pathname]);

  useEffect(() => {
    if (status === "anonymous") router.replace("/login");
  }, [status, router]);

  useEffect(() => {
    if (user?.mustChangePassword && pathname !== "/settings/security") router.replace("/settings/security?required=1");
  }, [user, pathname, router]);

  return !!user && !!accessToken;
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const ready = useAuthGate();
  useLiveStream();
  const pathname = usePathname();
  const router = useRouter();
  const { user, organizationName } = useSession();
  const { theme, toggle } = useTheme();
  const openCopilot = useCopilot((s) => s.setOpen);
  const openPalette = useCommandPalette((s) => s.setOpen);
  const { connected, notifications, unread, markRead } = useLive();
  const [signingOut, setSigningOut] = useState(false);
  // The dev mailbox only exists in the backend's dev profile: show it when the endpoint answers, hide on 404.
  const canManageUsers = !!user?.permissions.includes("USER_MANAGE");
  const mailboxProbe = useQuery({
    queryKey: ["dev-mailbox-probe"],
    queryFn: () => devApi.mailbox(1),
    enabled: ready && canManageUsers,
    retry: false,
    staleTime: Infinity,
  });
  const mailboxAvailable = mailboxProbe.isSuccess;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        openPalette(true);
      }
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "j") {
        e.preventDefault();
        openCopilot(true);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [openCopilot, openPalette]);

  const signOut = async () => {
    setSigningOut(true);
    try {
      await authApi.logout();
    } finally {
      useSession.getState().expire();
      router.replace("/login");
    }
  };

  if (!ready) {
    return (
      <div className="console-backdrop flex min-h-screen items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <Logo />
          <div className="h-1 w-40 overflow-hidden rounded-full bg-surface-2">
            <div className="h-full w-1/2 animate-[pulse_1.2s_ease-in-out_infinite] rounded-full bg-jade-500" />
          </div>
        </div>
      </div>
    );
  }

  const items = [...NAV, ...(mailboxAvailable ? [DEV_MAILBOX] : [])]
    .filter((n) => !n.permission || user?.permissions.includes(n.permission));
  const sections = [...new Set(items.map((i) => i.section))];

  return (
    <div className="console-backdrop min-h-screen">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-border bg-[var(--surface)]/60 backdrop-blur-xl lg:flex">
        <div className="px-5 py-5"><Logo /></div>
        <nav className="flex-1 space-y-5 overflow-y-auto px-3 py-2">
          {sections.map((section) => (
            <div key={section}>
              <p className="px-3 pb-1.5 text-[10px] font-semibold uppercase tracking-[0.2em] text-muted/70">{section}</p>
              {items.filter((i) => i.section === section).map((item) => {
                const active = pathname === item.href || pathname.startsWith(item.href + "/");
                const Icon = item.icon;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={cn(
                      "group flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-all",
                      active ? "bg-surface text-foreground font-medium shadow-[var(--shadow-card)] ring-1 ring-border [&>svg]:text-jade-400" : "text-muted hover:bg-surface-2 hover:text-foreground",
                    )}
                  >
                    <Icon className="h-4 w-4" />
                    {item.label}
                  </Link>
                );
              })}
            </div>
          ))}
        </nav>
        <div className="m-3 rounded-xl border border-brass-400/25 bg-brass-400/[0.06] p-3">
          <div className="flex items-center gap-2 text-xs font-medium text-brass-400"><Sparkles className="h-3.5 w-3.5" /> AI Copilot</div>
          <p className="mt-1 text-[11px] text-muted">Ask anything about your payments. <kbd className="rounded bg-surface-2 px-1">Ctrl J</kbd></p>
          <button onClick={() => openCopilot(true)} className="mt-2 w-full rounded-md bg-ink-950 py-1.5 text-xs font-semibold text-[#eeece7] ring-1 ring-brass-400/40 transition-colors hover:bg-ink-800 dark:bg-brass-400 dark:text-ink-950 dark:ring-0 dark:hover:bg-brass-300 cursor-pointer">
            Ask Copilot
          </button>
        </div>
      </aside>

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-border bg-[var(--bg)]/70 px-4 backdrop-blur-xl lg:px-8">
          <div className="lg:hidden"><Logo withText={false} /></div>
          <button
            onClick={() => openPalette(true)}
            className="flex h-10 max-w-md flex-1 items-center gap-2 rounded-lg border border-border bg-surface-2 px-3 text-left text-sm text-muted hover:border-jade-500/40 cursor-pointer"
          >
            <Search className="h-4 w-4" />
            <span className="flex-1 truncate">Search payments, jump to pages, ask AI…</span>
            <kbd className="hidden rounded bg-[var(--bg)] px-1.5 py-0.5 text-[10px] sm:block">Ctrl K</kbd>
          </button>
          <div className="ml-auto flex items-center gap-2">
            <span className={cn("hidden items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] sm:flex",
              connected ? "border-jade-500/30 text-jade-400" : "border-border text-muted")}>
              <Activity className="h-3 w-3" /> {connected ? "Live" : "Connecting"}
            </span>
            <button onClick={() => openCopilot(true)} className="flex h-9 items-center gap-1.5 rounded-lg border border-brass-400/40 px-3 text-xs font-medium text-brass-400 transition-colors hover:bg-brass-400/10 cursor-pointer">
              <Bot className="h-4 w-4" /> <span className="hidden sm:inline">Copilot</span>
            </button>
            <Dropdown.Root onOpenChange={(o) => o && markRead()}>
              <Dropdown.Trigger className="relative flex h-9 w-9 items-center justify-center rounded-lg text-muted hover:bg-surface-2 hover:text-foreground cursor-pointer" aria-label="Notifications">
                <Bell className="h-4 w-4" />
                {unread > 0 && <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[9px] font-bold text-white">{unread}</span>}
              </Dropdown.Trigger>
              <Dropdown.Portal>
                <Dropdown.Content align="end" sideOffset={8} className="z-50 w-80 rounded-xl border border-border bg-[var(--bg)] p-2 shadow-2xl">
                  <p className="px-2 py-1.5 text-xs font-semibold uppercase tracking-wider text-muted">Live notifications</p>
                  {notifications.length === 0 && <p className="px-2 py-4 text-center text-xs text-muted">No new alerts or incidents.</p>}
                  {notifications.slice(0, 8).map((n) => {
                    const d = describeNotification(n);
                    return (
                      <Dropdown.Item key={n.id} onSelect={() => router.push(d.href)} className="cursor-pointer rounded-lg px-2 py-2 outline-none hover:bg-surface-2">
                        <p className="flex items-center gap-2 text-sm font-medium">
                          <span className={cn("h-2 w-2 rounded-full", d.severity === "CRITICAL" || d.severity === "HIGH" ? "bg-danger" : "bg-warning")} />
                          <span className="truncate">{d.title}</span>
                        </p>
                        <p className="line-clamp-2 pl-4 text-xs text-muted">{d.detail}</p>
                        <p className="pl-4 text-[10px] text-muted/70">{timeAgo(new Date(n.at).toISOString())}</p>
                      </Dropdown.Item>
                    );
                  })}
                </Dropdown.Content>
              </Dropdown.Portal>
            </Dropdown.Root>
            <button onClick={toggle} className="flex h-9 w-9 items-center justify-center rounded-lg text-muted hover:bg-surface-2 hover:text-foreground cursor-pointer" aria-label="Toggle theme">
              {theme === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </button>
            <Dropdown.Root>
              <Dropdown.Trigger className="flex items-center gap-2 rounded-lg px-2 py-1 hover:bg-surface-2 cursor-pointer">
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-ink-700 to-ink-950 text-xs font-semibold text-[#e6cc96] ring-1 ring-brass-400/40">
                  {user?.fullName.split(" ").map((p) => p[0]).slice(0, 2).join("")}
                </div>
                <div className="hidden text-left leading-tight md:block">
                  <div className="text-xs font-medium">{user?.fullName}</div>
                  <div className="text-[10px] text-muted">{organizationName}</div>
                </div>
              </Dropdown.Trigger>
              <Dropdown.Portal>
                <Dropdown.Content align="end" sideOffset={8} className="z-50 w-60 rounded-xl border border-border bg-[var(--bg)] p-2 shadow-2xl">
                  <div className="px-2 py-2">
                    <p className="text-sm font-medium">{user?.email}</p>
                    <p className="text-xs text-muted">{user?.role.replace("_", " ")} · MFA {user?.mfaEnabled ? "on" : "off"}</p>
                  </div>
                  <Dropdown.Item onSelect={() => router.push("/settings/security")} className="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-2 text-sm outline-none hover:bg-surface-2">
                    <ShieldCheck className="h-4 w-4" /> Security settings
                  </Dropdown.Item>
                  <Dropdown.Item disabled={signingOut} onSelect={signOut} className="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-2 text-sm text-danger outline-none hover:bg-danger/10">
                    <LogOut className="h-4 w-4" /> Sign out
                  </Dropdown.Item>
                </Dropdown.Content>
              </Dropdown.Portal>
            </Dropdown.Root>
          </div>
        </header>
        <nav className="flex gap-1 overflow-x-auto border-b border-border px-4 py-2 lg:hidden">
          {items.map((item) => (
            <Link key={item.href} href={item.href} className={cn("whitespace-nowrap rounded-md px-3 py-1.5 text-xs",
              pathname.startsWith(item.href) ? "bg-jade-500/15 text-jade-400" : "text-muted")}>{item.label}</Link>
          ))}
        </nav>
        <main key={pathname} className="mx-auto max-w-[1600px] animate-rise px-4 py-6 lg:px-8">{children}</main>
      </div>
      <CopilotDrawer />
      <CommandPalette />
    </div>
  );
}
