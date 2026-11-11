"use client";

import { useState } from "react";
import { Lock } from "lucide-react";
import { usePermission } from "@/lib/auth/session";
import { Card, Empty, PageHeader, Segmented } from "@/components/ui/primitives";
import { ChannelsTab } from "@/components/notifications/channels-tab";
import { RulesTab } from "@/components/notifications/rules-tab";
import { EscalationTab } from "@/components/notifications/escalation-tab";
import { DeliveriesTab } from "@/components/notifications/deliveries-tab";

type Tab = "channels" | "rules" | "escalation" | "deliveries";

const TABS: { value: Tab; label: string }[] = [
  { value: "channels", label: "Channels" },
  { value: "rules", label: "Rules" },
  { value: "escalation", label: "Escalation" },
  { value: "deliveries", label: "Delivery log" },
];

export default function NotificationsSettingsPage() {
  const canManage = usePermission("NOTIFICATION_MANAGE");
  const [tab, setTab] = useState<Tab>("channels");

  if (!canManage) {
    return (
      <div>
        <PageHeader title="Notifications" />
        <Card>
          <Empty icon={<Lock className="h-8 w-8" />} title="Access restricted"
            hint="Managing notification channels, routing and escalation requires the NOTIFICATION_MANAGE permission (Admin or Operations Manager)." />
        </Card>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Notifications"
        subtitle="Route alerts, incidents and provider health to e-mail, Slack, Teams, webhooks and PagerDuty — with escalation and SLA targets."
        actions={<Segmented value={tab} options={TABS} onChange={setTab} />}
      />
      {tab === "channels" && <ChannelsTab />}
      {tab === "rules" && <RulesTab />}
      {tab === "escalation" && <EscalationTab />}
      {tab === "deliveries" && <DeliveriesTab />}
    </div>
  );
}
