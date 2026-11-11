"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ScrollText } from "lucide-react";
import { notificationsApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { formatDateTime, humanize, timeAgo } from "@/lib/utils";
import {
  Badge, Card, CardBody, CardHeader, Empty, ErrorNote, Segmented, Skeleton, type Tone,
} from "@/components/ui/primitives";
import { SeverityBadge } from "@/components/ui/status";
import type { DeliveryStatus } from "@/types/api";
import { CHANNEL_META, ChannelIcon } from "./shared";

type Filter = DeliveryStatus | "ALL";
const statusTone: Record<DeliveryStatus, Tone> = { PENDING: "amber", SENT: "green", FAILED: "red" };

export function DeliveriesTab() {
  const [filter, setFilter] = useState<Filter>("ALL");
  const deliveries = useQuery({
    queryKey: ["notifications", "deliveries", filter],
    queryFn: () => notificationsApi.deliveries(filter === "ALL" ? undefined : filter, 200),
    refetchInterval: 10_000,
    placeholderData: (prev) => prev,
  });

  return (
    <Card>
      <CardHeader
        icon={<ScrollText className="h-4 w-4" />}
        title="Delivery log"
        subtitle="Every notification attempt. Failed deliveries are retried with exponential back-off, up to 5 attempts."
        actions={
          <Segmented value={filter} onChange={setFilter} options={[
            { value: "ALL", label: "All" }, { value: "PENDING", label: "Pending" },
            { value: "SENT", label: "Sent" }, { value: "FAILED", label: "Failed" },
          ]} />
        }
      />
      <CardBody className="p-0">
        <div className="px-5"><ErrorNote message={deliveries.error ? errorMessage(deliveries.error) : null} /></div>
        {deliveries.isLoading ? (
          <div className="space-y-3 p-5">{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-12 w-full" />)}</div>
        ) : !deliveries.data?.length ? (
          <Empty icon={<ScrollText className="h-8 w-8" />} title="No deliveries" hint="Notifications appear here once a rule, escalation or test sends one." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-[11px] uppercase tracking-wider text-muted">
                  <th className="px-5 py-2.5 font-medium">When</th>
                  <th className="px-3 py-2.5 font-medium">Channel</th>
                  <th className="px-3 py-2.5 font-medium">Event</th>
                  <th className="px-3 py-2.5 font-medium">Message</th>
                  <th className="px-3 py-2.5 font-medium">Status</th>
                  <th className="px-5 py-2.5 font-medium">Attempts</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {deliveries.data.map((d) => (
                  <tr key={d.id} className="align-top">
                    <td className="whitespace-nowrap px-5 py-3 text-xs text-muted" title={formatDateTime(d.createdAt)}>{timeAgo(d.createdAt)}</td>
                    <td className="px-3 py-3">
                      <div className="flex items-center gap-2">
                        <ChannelIcon type={d.channelType} className="h-7 w-7 rounded-lg" />
                        <div>
                          <p className="font-medium">{d.channelName}</p>
                          <p className="text-[11px] text-muted">{CHANNEL_META[d.channelType].label}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-3 py-3">
                      <div className="flex flex-col items-start gap-1">
                        <Badge tone={d.source === "ESCALATION" ? "amber" : d.source === "TEST" ? "blue" : "ink"}>{humanize(d.source)}</Badge>
                        <span className="text-xs text-muted">{humanize(d.eventType)}</span>
                      </div>
                    </td>
                    <td className="max-w-md px-3 py-3">
                      <div className="flex items-center gap-2">
                        {d.severity && <SeverityBadge severity={d.severity} />}
                        {d.subjectType === "INCIDENT" && d.subjectReference ? (
                          <Link href={`/incidents/${encodeURIComponent(d.subjectReference)}`} className="font-mono text-[11px] text-jade-400 hover:underline">{d.subjectReference}</Link>
                        ) : d.subjectReference && <span className="font-mono text-[11px] text-muted">{d.subjectReference}</span>}
                      </div>
                      <p className="mt-1 truncate">{d.title}</p>
                      {d.lastError && <p className="mt-1 break-words text-xs text-danger">{d.lastError}</p>}
                    </td>
                    <td className="px-3 py-3">
                      <Badge tone={statusTone[d.status]} dot>{humanize(d.status)}</Badge>
                      {d.status === "PENDING" && d.nextAttemptAt && <p className="mt-1 text-[11px] text-muted">next {timeAgo(d.nextAttemptAt)}</p>}
                      {d.sentAt && <p className="mt-1 text-[11px] text-muted">{timeAgo(d.sentAt)}</p>}
                    </td>
                    <td className="px-5 py-3 font-mono text-xs">{d.attempts}/5</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardBody>
    </Card>
  );
}
