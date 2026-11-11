"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Receipt, Trash2 } from "lucide-react";
import { providersApi, reconciliationApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { usePermission } from "@/lib/auth/session";
import { formatDateTime, humanize } from "@/lib/utils";
import { Button, Card, CardBody, CardHeader, Empty, ErrorNote, Input, Select, Skeleton } from "@/components/ui/primitives";
import type { FeeRule, FeeSchedule, PaymentMethod } from "@/types/api";

const METHODS: PaymentMethod[] = ["CARD", "DIGITAL_WALLET", "BANK_TRANSFER", "REAL_TIME_PAYMENT", "CASH_PICKUP", "BNPL"];

interface RuleRow { key: number; method: string; currency: string; percentage: string; fixedFee: string }

let nextKey = 1;
const toRow = (r: FeeRule): RuleRow => ({
  key: nextKey++, method: r.method ?? "", currency: r.currency ?? "", percentage: String(r.percentage), fixedFee: String(r.fixedFee ?? 0),
});

/** Per-provider expected fee schedule (percentage + fixed, optionally per method / currency). */
export function FeeScheduleTab() {
  const providers = useQuery({ queryKey: ["providers"], queryFn: providersApi.list });
  const [provider, setProvider] = useState("");
  const selected = provider || providers.data?.[0]?.code || "";
  const schedule = useQuery({
    queryKey: ["recon", "fee-schedule", selected],
    queryFn: () => reconciliationApi.feeSchedule(selected),
    enabled: !!selected,
  });

  return (
    <Card>
      <CardHeader
        title="Expected fee schedules"
        subtitle="Settlement fees above these rates are flagged as fee overcharges and counted as leakage."
        icon={<Receipt className="h-4 w-4" />}
        actions={
          <Select aria-label="Provider" value={selected} onChange={(e) => setProvider(e.target.value)}>
            {providers.data?.map((p) => <option key={p.code} value={p.code}>{p.name}</option>)}
          </Select>
        }
      />
      <CardBody>
        <ErrorNote message={schedule.isError ? errorMessage(schedule.error) : null} />
        {schedule.isPending ? (
          <div className="space-y-2">{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-10 w-full" />)}</div>
        ) : schedule.data ? (
          <ScheduleEditor key={schedule.data.providerCode} schedule={schedule.data} />
        ) : null}
      </CardBody>
    </Card>
  );
}

function ScheduleEditor({ schedule }: { schedule: FeeSchedule }) {
  const qc = useQueryClient();
  const canManage = usePermission("RECON_MANAGE");
  const [rows, setRows] = useState<RuleRow[]>(() => schedule.rules.map(toRow));
  const [invalid, setInvalid] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: (rules: FeeRule[]) => reconciliationApi.saveFeeSchedule(schedule.providerCode, rules),
    onSuccess: (data) => qc.setQueryData(["recon", "fee-schedule", schedule.providerCode], data),
  });

  const update = (key: number, patch: Partial<RuleRow>) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const rules: FeeRule[] = [];
    for (const r of rows) {
      const percentage = Number(r.percentage);
      const fixedFee = Number(r.fixedFee || 0);
      if (!Number.isFinite(percentage) || percentage < 0 || percentage > 100) return setInvalid("Percentage must be between 0 and 100.");
      if (!Number.isFinite(fixedFee) || fixedFee < 0) return setInvalid("Fixed fee must be zero or more.");
      if (r.currency && !/^[A-Za-z]{3}$/.test(r.currency)) return setInvalid("Currency must be a 3-letter code or empty.");
      rules.push({ method: r.method || undefined, currency: r.currency ? r.currency.toUpperCase() : undefined, percentage, fixedFee });
    }
    const keys = rules.map((r) => `${r.method ?? ""}|${r.currency ?? ""}`);
    if (new Set(keys).size !== keys.length) return setInvalid("Each method / currency combination may appear only once.");
    setInvalid(null);
    save.mutate(rules);
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <p className="text-xs text-muted">
        Fee = amount × % + fixed (in the settlement currency). The most specific rule wins.
        {schedule.fallbackPercentage !== undefined && (
          <> Without a matching rule, the provider&apos;s catalogue cost of <span className="font-medium text-foreground">{schedule.fallbackPercentage}%</span> applies.</>
        )}
      </p>
      {rows.length === 0 ? (
        <Empty icon={<Receipt className="h-6 w-6" />} title="No fee rules yet" hint="Settlement fees are compared against the provider's catalogue cost until rules are added." />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px] text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-muted">
                <th className="py-2 pr-3 font-medium">Method</th>
                <th className="py-2 pr-3 font-medium">Currency</th>
                <th className="py-2 pr-3 font-medium">Percentage</th>
                <th className="py-2 pr-3 font-medium">Fixed fee</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.key} className="border-b border-border/60">
                  <td className="py-2 pr-3">
                    <Select aria-label="Method" className="w-full" value={r.method} disabled={!canManage} onChange={(e) => update(r.key, { method: e.target.value })}>
                      <option value="">Any method</option>
                      {METHODS.map((m) => <option key={m} value={m}>{humanize(m)}</option>)}
                    </Select>
                  </td>
                  <td className="py-2 pr-3">
                    <Input aria-label="Currency" value={r.currency} disabled={!canManage} maxLength={3} placeholder="Any" className="w-24 uppercase"
                      onChange={(e) => update(r.key, { currency: e.target.value })} />
                  </td>
                  <td className="py-2 pr-3">
                    <Input aria-label="Percentage" type="number" min={0} max={100} step={0.0001} value={r.percentage} disabled={!canManage} className="w-28"
                      onChange={(e) => update(r.key, { percentage: e.target.value })} />
                  </td>
                  <td className="py-2 pr-3">
                    <Input aria-label="Fixed fee" type="number" min={0} step={0.01} value={r.fixedFee} disabled={!canManage} className="w-28"
                      onChange={(e) => update(r.key, { fixedFee: e.target.value })} />
                  </td>
                  <td className="py-2 text-right">
                    {canManage && (
                      <Button type="button" variant="ghost" size="icon" aria-label="Remove rule" onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <ErrorNote message={invalid ?? (save.isError ? errorMessage(save.error) : null)} />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-xs text-muted">
          {schedule.updatedAt ? <>Last changed {formatDateTime(schedule.updatedAt)}{schedule.updatedBy ? ` by ${schedule.updatedBy}` : ""}</> : "Never changed"}
          {save.isSuccess && <span className="ml-2 text-jade-400">Saved</span>}
        </span>
        {canManage && (
          <div className="flex gap-2">
            <Button type="button" variant="secondary" onClick={() => setRows((rs) => [...rs, toRow({ percentage: 0, fixedFee: 0 })])}
              disabled={rows.length >= 50}>
              <Plus className="h-4 w-4" /> Add rule
            </Button>
            <Button type="submit" loading={save.isPending}>Save schedule</Button>
          </div>
        )}
      </div>
      {!canManage && <p className="text-xs text-muted">Read-only: editing requires the RECON_MANAGE permission.</p>}
    </form>
  );
}
