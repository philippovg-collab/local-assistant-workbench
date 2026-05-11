import { AlertTriangle, ArrowRight, CheckCircle2, GitCompareArrows, XCircle } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { useEvalCompare } from "@/hooks/useEvalCompare";
import type { EvalCompatibilityReason, EvalRun } from "@/types";
import { Field, JsonBlock } from "./EvalShared";
import {
  badgeForMetricVerdict,
  badgeForOverallVerdict,
  formatMetricValue,
  metricDeltaRows,
  NONE_VALUE,
  shortId,
} from "./evalPresentation";

type EvalCompareViewProps = {
  compareHook: ReturnType<typeof useEvalCompare>;
  runs: EvalRun[];
};

export function EvalCompareView({ compareHook, runs }: EvalCompareViewProps) {
  const metricRows = metricDeltaRows(compareHook.metricSummary);
  const hasMetricRows = metricRows.length > 0;
  const reasonTone = compareHook.isIncompatible ? "destructive" : "warning";

  return (
    <section className="surface-subtle space-y-4 rounded-[24px] p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <GitCompareArrows className="h-4 w-4 text-primary" />
          Compare
        </div>
        <Button
          disabled={!compareHook.baselineRunId || !compareHook.candidateRunId || compareHook.isComparing}
          size="sm"
          type="button"
          onClick={() => void compareHook.createCompare()}
        >
          <ArrowRight className="h-4 w-4" />
          Compare
        </Button>
      </div>

      {compareHook.error ? <p className="text-sm leading-6 text-destructive">{compareHook.error}</p> : null}

      <div className="grid gap-3 md:grid-cols-2">
        <RunSelect label="Base run" runs={runs} value={compareHook.baselineRunId} onValueChange={compareHook.setBaselineRunId} />
        <RunSelect label="Compare run" runs={runs} value={compareHook.candidateRunId} onValueChange={compareHook.setCandidateRunId} />
      </div>

      {compareHook.compare ? (
        <div className="space-y-4">
          <div className="flex flex-wrap gap-2">
            <Badge variant={compareHook.isIncompatible ? "destructive" : "success"}>
              {compareHook.compare.compatibilityStatus ?? compareHook.compare.status ?? "UNKNOWN"}
            </Badge>
            <Badge variant={badgeForOverallVerdict(compareHook.overallVerdict)}>
              {compareHook.overallVerdict ?? "NO_VERDICT"}
            </Badge>
            <Badge variant="secondary">base {shortId(compareHook.compare.baselineRunId)}</Badge>
            <Badge variant="secondary">compare {shortId(compareHook.compare.candidateRunId)}</Badge>
          </div>

          {compareHook.compatibilityReasons.length > 0 ? (
            <CompatibilityReasons
              isIncompatible={compareHook.isIncompatible}
              reasonTone={reasonTone}
              reasons={compareHook.compatibilityReasons}
            />
          ) : null}

          {compareHook.isIncompatible ? (
            <div className="flex items-center gap-2 text-sm font-semibold text-destructive">
              <XCircle className="h-4 w-4" />
              Release proof is blocked
            </div>
          ) : (
            <div className="space-y-4">
              <div className="flex items-center gap-2 text-sm font-semibold text-success">
                <CheckCircle2 className="h-4 w-4" />
                Comparable release proof
              </div>
              {hasMetricRows
                ? <MetricDeltaTable rows={metricRows} />
                : <NoMetricDelta isError={Boolean(compareHook.metricSummaryError)} summary={compareHook.summary} />}
            </div>
          )}
        </div>
      ) : (
        <EmptyState description="Выбери два backend eval runs, чтобы получить compatibility и summary." icon={GitCompareArrows} title="Compare не создан" />
      )}
    </section>
  );
}

function CompatibilityReasons({
  isIncompatible,
  reasonTone,
  reasons,
}: {
  isIncompatible: boolean;
  reasonTone: "destructive" | "warning";
  reasons: EvalCompatibilityReason[];
}) {
  return (
    <div className={`space-y-3 rounded-[22px] border px-4 py-4 ${
      isIncompatible ? "border-destructive/30 bg-destructive/10" : "border-warning/30 bg-warning/10"
    }`}>
      <div className={`flex items-center gap-2 text-sm font-semibold ${isIncompatible ? "text-destructive" : "text-warning"}`}>
        {isIncompatible ? <XCircle className="h-4 w-4" /> : <AlertTriangle className="h-4 w-4" />}
        {isIncompatible ? "Blocked" : "Compatibility warnings"}
      </div>
      {reasons.map((reason) => (
        <div
          className={`rounded-[18px] border bg-background px-4 py-3 ${
            isIncompatible ? "border-destructive/20" : "border-warning/20"
          }`}
          key={reason.code ?? reason.message}
        >
          <div className="flex flex-wrap items-center gap-2">
            <strong className="block text-sm text-foreground">{reason.code ?? "reason"}</strong>
            <Badge variant={reasonTone}>{reason.status ?? "UNKNOWN"}</Badge>
          </div>
          <p className="mt-1 text-sm leading-6 text-muted-foreground">{reason.message}</p>
          {reason.details ? <div className="mt-2"><JsonBlock value={reason.details} /></div> : null}
        </div>
      ))}
    </div>
  );
}

function MetricDeltaTable({ rows }: { rows: ReturnType<typeof metricDeltaRows> }) {
  return (
    <div className="overflow-hidden rounded-[22px] border border-border bg-background">
      <table className="min-w-full divide-y divide-border text-sm">
        <thead className="bg-surface-subtle-strong text-xs uppercase tracking-[0.12em] text-muted-foreground">
          <tr>
            <th className="px-4 py-3 text-left font-semibold">Metric</th>
            <th className="px-4 py-3 text-right font-semibold">Base</th>
            <th className="px-4 py-3 text-right font-semibold">Candidate</th>
            <th className="px-4 py-3 text-right font-semibold">Delta</th>
            <th className="px-4 py-3 text-left font-semibold">Verdict</th>
            <th className="px-4 py-3 text-right font-semibold">Scored</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {rows.map((row) => (
            <tr key={row.metric}>
              <td className="px-4 py-3 font-medium text-foreground">
                <div>{row.metric}</div>
                <div className="mt-1 text-xs uppercase tracking-[0.12em] text-muted-foreground">{row.direction}</div>
              </td>
              <td className="px-4 py-3 text-right tabular-nums text-muted-foreground">{formatMetricValue(row.baselineValue)}</td>
              <td className="px-4 py-3 text-right tabular-nums text-muted-foreground">{formatMetricValue(row.candidateValue)}</td>
              <td className="px-4 py-3 text-right tabular-nums text-muted-foreground">{formatMetricValue(row.delta)}</td>
              <td className="px-4 py-3"><Badge variant={badgeForMetricVerdict(row.verdict)}>{row.verdict}</Badge></td>
              <td className="px-4 py-3 text-right tabular-nums text-muted-foreground">
                {formatMetricValue(row.sampleSize)}
                {Number(row.nonScorableCount ?? 0) > 0 ? ` / ${formatMetricValue(row.nonScorableCount)} n/a` : ""}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function NoMetricDelta({
  isError,
  summary,
}: {
  isError: boolean;
  summary: Record<string, unknown>;
}) {
  return (
    <div className={`rounded-[22px] border px-4 py-4 ${
      isError ? "border-destructive/30 bg-destructive/10" : "border-field-border bg-field"
    }`}>
      <div className={`flex items-center gap-2 text-sm font-semibold ${isError ? "text-destructive" : "text-foreground"}`}>
        <AlertTriangle className={`h-4 w-4 ${isError ? "text-destructive" : "text-warning"}`} />
        {isError ? "Compatible compare is missing metricSummary" : "Backend returned no metric deltas"}
      </div>
      <div className="mt-3"><JsonBlock value={summary} /></div>
    </div>
  );
}

function RunSelect({
  label,
  onValueChange,
  runs,
  value,
}: {
  label: string;
  onValueChange: (runId: string | null) => void;
  runs: EvalRun[];
  value: string | null;
}) {
  return (
    <Field label={label}>
      <Select value={value ?? NONE_VALUE} onValueChange={(nextValue) => onValueChange(nextValue === NONE_VALUE ? null : nextValue)}>
        <SelectTrigger><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem value={NONE_VALUE}>No run</SelectItem>
          {runs.map((run) => (
            <SelectItem key={run.id} value={run.id ?? NONE_VALUE}>
              {shortId(run.id)} · {run.runKind ?? "n/a"} · {run.status ?? "n/a"}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </Field>
  );
}
