import { Badge } from "@/components/ui/badge";
import type { RetrievalDebug } from "@/types";

type RetrievalDebugPanelProps = {
  retrievalDebug: RetrievalDebug | null;
  effectiveFilterLines: string[];
  queryHintLines: string[];
};

export function RetrievalDebugPanel({
  retrievalDebug,
  effectiveFilterLines,
  queryHintLines,
}: RetrievalDebugPanelProps) {
  if (!retrievalDebug) {
    return null;
  }

  return (
    <div className="space-y-3 rounded-[22px] border border-border bg-surface-subtle/70 px-4 py-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-foreground">Retrieval debug</p>
        <Badge variant="outline">{retrievalDebug.relevanceProfile}</Badge>
      </div>
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-[18px] border border-field-border bg-field px-3 py-3 text-xs text-foreground">
          semantic: {retrievalDebug.semanticCandidateCount}
        </div>
        <div className="rounded-[18px] border border-field-border bg-field px-3 py-3 text-xs text-foreground">
          lexical: {retrievalDebug.lexicalCandidateCount}
        </div>
        <div className="rounded-[18px] border border-field-border bg-field px-3 py-3 text-xs text-foreground">
          rerank pool: {retrievalDebug.rerankCandidateCount}
        </div>
        <div className="rounded-[18px] border border-field-border bg-field px-3 py-3 text-xs text-foreground">
          final chunks: {retrievalDebug.finalChunkCount}
        </div>
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-[18px] border border-field-border bg-field px-4 py-3">
          <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Effective filters</p>
          <p className="mt-2 text-sm leading-6 text-foreground">
            {effectiveFilterLines.length > 0 ? effectiveFilterLines.join(" · ") : "No effective filters"}
          </p>
        </div>
        <div className="rounded-[18px] border border-field-border bg-field px-4 py-3">
          <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Query hints</p>
          <p className="mt-2 text-sm leading-6 text-foreground">
            {queryHintLines.length > 0 ? queryHintLines.join(" · ") : "No extracted hints"}
          </p>
        </div>
        <div className="rounded-[18px] border border-field-border bg-field px-4 py-3">
          <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Applied capabilities</p>
          <p className="mt-2 text-sm leading-6 text-foreground">
            {retrievalDebug.appliedCapabilities.length > 0
              ? retrievalDebug.appliedCapabilities.join(" · ")
              : "No rollout capabilities applied"}
          </p>
        </div>
        <div className="rounded-[18px] border border-field-border bg-field px-4 py-3">
          <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Active rollout flags</p>
          <p className="mt-2 text-sm leading-6 text-foreground">
            {[
              `metadata=${retrievalDebug.activeRolloutFlags.metadataV1 ? "on" : "off"}`,
              `structured=${retrievalDebug.activeRolloutFlags.structuredV1 ? "on" : "off"}`,
              `filters=${retrievalDebug.activeRolloutFlags.metadataFiltersV1 ? "on" : "off"}`,
              `search=${retrievalDebug.activeRolloutFlags.searchApiV1 ? "on" : "off"}`,
              `reranker=${retrievalDebug.activeRolloutFlags.rerankerV1 ? "on" : "off"}`,
              `hints=${retrievalDebug.activeRolloutFlags.queryHintsV1 ? "on" : "off"}`,
            ].join(" · ")}
          </p>
        </div>
      </div>
    </div>
  );
}
