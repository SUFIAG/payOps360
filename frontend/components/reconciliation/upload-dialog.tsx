"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Download, FileUp, Loader2, Upload, XCircle } from "lucide-react";
import { providersApi, reconciliationApi } from "@/lib/api/endpoints";
import { errorMessage } from "@/lib/api/client";
import { formatNumber, formatPercent } from "@/lib/utils";
import { Button, ErrorNote, Input, Label, Select } from "@/components/ui/primitives";
import { Dialog, DialogClose, DialogContent } from "@/components/ui/dialog";
import { BatchStatusBadge, isActive } from "./shared";
import type { SettlementBatch } from "@/types/api";

const MAX_BYTES = 10 * 1024 * 1024;

/** Upload a provider settlement CSV, then follow the asynchronous matching run. */
export function UploadSettlementDialog() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [provider, setProvider] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [invalid, setInvalid] = useState<string | null>(null);
  const [batchRef, setBatchRef] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const providers = useQuery({ queryKey: ["providers"], queryFn: providersApi.list, enabled: open });
  const selected = provider || providers.data?.[0]?.code || "";

  const upload = useMutation({
    mutationFn: () => reconciliationApi.upload(selected, file as File),
    onSuccess: (batch) => {
      setBatchRef(batch.reference);
      qc.invalidateQueries({ queryKey: ["recon", "batches"] });
    },
  });
  const sample = useMutation({ mutationFn: () => reconciliationApi.sampleCsv(selected) });

  const progress = useQuery({
    queryKey: ["recon", "batch", batchRef],
    queryFn: () => reconciliationApi.batch(batchRef as string),
    enabled: !!batchRef,
    refetchInterval: (q) => {
      const data = q.state.data;
      if (data && !isActive(data.status)) {
        return false;
      }
      return 1500;
    },
  });
  const batch = progress.data ?? upload.data;
  const done = !!batch && !isActive(batch.status);

  // Refresh KPIs, batches and the exception queue once matching finishes.
  const finished = done && batch ? batch.reference + batch.status : null;
  useEffect(() => {
    if (finished) {
      qc.invalidateQueries({ queryKey: ["recon", "summary"] });
      qc.invalidateQueries({ queryKey: ["recon", "trend"] });
      qc.invalidateQueries({ queryKey: ["recon", "batches"] });
      qc.invalidateQueries({ queryKey: ["recon", "exceptions"] });
    }
  }, [finished, qc]);

  const reset = () => {
    setFile(null);
    setInvalid(null);
    setBatchRef(null);
    upload.reset();
    sample.reset();
    if (fileInput.current) fileInput.current.value = "";
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selected) return setInvalid("Choose a provider.");
    if (!file) return setInvalid("Choose a CSV file to upload.");
    if (!/\.(csv|txt)$/i.test(file.name)) return setInvalid("Only .csv settlement files are supported.");
    if (file.size > MAX_BYTES) return setInvalid("The file exceeds the 10 MB limit.");
    setInvalid(null);
    upload.mutate();
  };

  return (
    <Dialog open={open} onOpenChange={(o) => { setOpen(o); if (!o) reset(); }}>
      <Button onClick={() => setOpen(true)}><Upload className="h-4 w-4" /> Upload settlement</Button>
      <DialogContent title="Import settlement file" description="Upload a provider settlement CSV (max 10 MB). Lines are matched against the ledger in the background.">
        {batch ? (
          <BatchProgress batch={batch} onAnother={reset} />
        ) : (
          <form onSubmit={submit} className="space-y-4">
            <div>
              <Label htmlFor="recon-provider">Provider</Label>
              <Select id="recon-provider" className="w-full" value={selected} onChange={(e) => setProvider(e.target.value)}>
                {providers.data?.map((p) => <option key={p.code} value={p.code}>{p.name}</option>)}
              </Select>
            </div>
            <div>
              <Label htmlFor="recon-file">Settlement CSV</Label>
              <Input
                id="recon-file"
                ref={fileInput}
                type="file"
                accept=".csv,text/csv"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="file:mr-3 file:rounded-md file:border-0 file:bg-surface-2 file:px-2 file:py-1 file:text-xs file:text-foreground"
              />
              <p className="mt-1.5 text-xs text-muted">
                Default columns: <span className="font-mono">provider_reference, amount, currency, fee, fx_rate, settled_at, type</span>
              </p>
            </div>
            <div className="flex items-center justify-between gap-3 rounded-lg border border-dashed border-border px-3 py-2">
              <p className="text-xs text-muted">No real file? Generate one from recent payments, with deliberate mismatches.</p>
              <Button type="button" variant="secondary" size="sm" onClick={() => sample.mutate()} loading={sample.isPending} disabled={!selected}>
                <Download className="h-4 w-4" /> Download sample CSV
              </Button>
            </div>
            <ErrorNote message={invalid ?? (upload.isError ? errorMessage(upload.error) : sample.isError ? errorMessage(sample.error) : null)} />
            <div className="flex justify-end gap-2">
              <DialogClose asChild>
                <Button type="button" variant="ghost">Cancel</Button>
              </DialogClose>
              <Button type="submit" loading={upload.isPending}><FileUp className="h-4 w-4" /> Import</Button>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}

function BatchProgress({ batch, onAnother }: { batch: SettlementBatch; onAnother: () => void }) {
  const active = isActive(batch.status);
  return (
    <div className="space-y-4" aria-live="polite">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="font-mono text-xs text-muted">{batch.reference}</p>
          <p className="text-sm font-medium">{batch.fileName}</p>
        </div>
        <BatchStatusBadge status={batch.status} />
      </div>
      <div>
        <div className="mb-1 flex justify-between text-xs text-muted">
          <span className="flex items-center gap-1.5">
            {active ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : batch.status === "FAILED" ? <XCircle className="h-3.5 w-3.5 text-danger" /> : <CheckCircle2 className="h-3.5 w-3.5 text-jade-400" />}
            {active ? "Matching against the ledger…" : batch.status === "FAILED" ? batch.errorMessage ?? "Matching failed" : "Reconciliation complete"}
          </span>
          <span className="tabular-nums">{batch.progressPercent}%</span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-surface-2" role="progressbar" aria-valuenow={batch.progressPercent} aria-valuemin={0} aria-valuemax={100}>
          <div className="h-full rounded-full bg-jade-500 transition-all duration-500" style={{ width: `${batch.progressPercent}%` }} />
        </div>
      </div>
      <dl className="grid grid-cols-3 gap-3 text-center">
        <Stat label="Lines" value={formatNumber(batch.totalLines)} />
        <Stat label="Match rate" value={formatPercent(batch.matchRate)} />
        <Stat label="Exceptions" value={active ? "…" : formatNumber(batch.exceptions)} />
      </dl>
      {batch.rejectedLines > 0 && (
        <div className="rounded-lg border border-warning/30 bg-warning/10 px-3 py-2 text-xs text-warning">
          <p className="font-medium">{batch.rejectedLines} line(s) could not be parsed and were skipped.</p>
          <ul className="mt-1 list-disc pl-4">{batch.parseErrors.slice(0, 5).map((e) => <li key={e}>{e}</li>)}</ul>
        </div>
      )}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onAnother} disabled={active}>Import another</Button>
        <DialogClose asChild>
          <Button type="button">{active ? "Continue in background" : "Done"}</Button>
        </DialogClose>
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border bg-surface-2/50 px-2 py-2">
      <dt className="text-[11px] uppercase tracking-wider text-muted">{label}</dt>
      <dd className="mt-0.5 font-display text-lg font-semibold tabular-nums">{value}</dd>
    </div>
  );
}
