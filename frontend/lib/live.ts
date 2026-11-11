"use client";

import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { create } from "zustand";
import { useSession } from "@/lib/auth/session";
import { refreshSession } from "@/lib/api/client";
import type { Alert, Incident, Payment } from "@/types/api";

/** Real-time event store fed by the Server-Sent Events stream. */
export interface LiveEvent { id: string; type: string; at: number; payload: unknown }

interface LiveState {
  connected: boolean;
  feed: Payment[];
  notifications: LiveEvent[];
  unread: number;
  push: (event: LiveEvent) => void;
  setConnected: (c: boolean) => void;
  markRead: () => void;
}

export const useLive = create<LiveState>((set) => ({
  connected: false,
  feed: [],
  notifications: [],
  unread: 0,
  setConnected: (connected) => set({ connected }),
  markRead: () => set({ unread: 0 }),
  push: (event) =>
    set((s) => {
      if (event.type === "payment.created") {
        return { feed: [event.payload as Payment, ...s.feed].slice(0, 40) };
      }
      if (event.type === "alert.raised" || event.type === "incident.opened") {
        return { notifications: [event, ...s.notifications].slice(0, 50), unread: s.unread + 1 };
      }
      return {};
    }),
}));

export function describeNotification(event: LiveEvent): { title: string; detail: string; href: string; severity?: string } {
  if (event.type === "incident.opened") {
    const i = event.payload as Incident;
    return { title: `Incident ${i.reference}`, detail: i.title, href: `/incidents/${i.reference}`, severity: i.severity };
  }
  const a = event.payload as Alert;
  return { title: a.title, detail: a.description ?? "", href: "/alerts", severity: a.severity };
}

/**
 * Opens the authenticated SSE stream (fetch + ReadableStream so the bearer
 * token travels in a header, never in a URL) and reconnects with back-off.
 */
export function useLiveStream() {
  const token = useSession((s) => s.accessToken);
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!token) return;
    const controller = new AbortController();
    let retry = 1000;
    let stopped = false;

    const connect = async () => {
      while (!stopped) {
        try {
          const current = useSession.getState().accessToken;
          const res = await fetch("/api/v1/stream", {
            headers: { Authorization: `Bearer ${current}`, Accept: "text/event-stream" },
            signal: controller.signal,
          });
          if (res.status === 401) {
            if (!(await refreshSession())) return;
            continue;
          }
          if (!res.ok || !res.body) throw new Error("stream unavailable");
          useLive.getState().setConnected(true);
          retry = 1000;
          const reader = res.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";
          for (;;) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const frames = buffer.split(/\r?\n\r?\n/);
            buffer = frames.pop() ?? "";
            for (const frame of frames) handleFrame(frame);
          }
        } catch {
          if (stopped) return;
        }
        useLive.getState().setConnected(false);
        await new Promise((r) => setTimeout(r, retry));
        retry = Math.min(retry * 2, 30000);
      }
    };

    const handleFrame = (frame: string) => {
      let type = "message";
      const data: string[] = [];
      for (const line of frame.split(/\r?\n/)) {
        if (line.startsWith("event:")) type = line.slice(6).trim();
        else if (line.startsWith("data:")) data.push(line.slice(5).trim());
      }
      if (!data.length || type === "heartbeat" || type === "connected") return;
      try {
        const payload = JSON.parse(data.join("\n"));
        useLive.getState().push({ id: crypto.randomUUID(), type, at: Date.now(), payload });
        if (type.startsWith("alert.")) queryClient.invalidateQueries({ queryKey: ["alerts"] });
        if (type.startsWith("incident.")) queryClient.invalidateQueries({ queryKey: ["incidents"] });
        if (type === "provider.health") queryClient.invalidateQueries({ queryKey: ["providers"] });
      } catch {
        /* ignore malformed frame */
      }
    };

    connect();
    return () => {
      stopped = true;
      controller.abort();
      useLive.getState().setConnected(false);
    };
  }, [token, queryClient]);
}
