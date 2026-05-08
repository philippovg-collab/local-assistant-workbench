import { BookOpenText, ExternalLink } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { ChatSource } from "@/types";

type RagSourcesListProps = {
  sources: ChatSource[];
  onOpenSource: (materialId: string, openSourceUrl?: string | null) => void;
};

export function RagSourcesList({ sources, onOpenSource }: RagSourcesListProps) {
  if (sources.length === 0) {
    return (
      <EmptyState
        description="Уточни вопрос, сузь или расширь корпус и попробуй другой режим ответа."
        icon={BookOpenText}
        title="Контекст не найден"
      />
    );
  }

  return (
    <div className="space-y-3">
      {sources.map((source) => (
        <article
          className="space-y-3 rounded-[22px] border border-field-border bg-field p-4"
          key={`${source.materialId}-${source.chunkId}-${source.chunkIndex ?? "na"}-${source.score}`}
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <strong className="text-sm font-semibold text-foreground">{source.title}</strong>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="default">score {source.score}</Badge>
              <Badge variant="secondary">confidence {source.confidence}</Badge>
              {source.ocrUsed ? <Badge variant="warning">OCR</Badge> : null}
            </div>
          </div>
          <p className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
            chunk {source.chunkIndex ?? "n/a"}
            {source.page ? ` · page ${source.page}` : ""}
            {source.extractor ? ` · ${source.extractor}` : ""}
            {source.semanticDistance !== undefined && source.semanticDistance !== null
              ? ` · semantic ${source.semanticDistance}`
              : ""}
            {source.lexicalScore !== undefined && source.lexicalScore !== null
              ? ` · lexical ${source.lexicalScore}`
              : ""}
          </p>
          <p className="text-sm leading-6 text-foreground">{source.excerpt}</p>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm leading-6 text-muted-foreground">
              matched: {source.matchedTerms.length > 0 ? source.matchedTerms.join(", ") : "нет явных терминов"}
            </p>
            <Button
              size="sm"
              type="button"
              variant="outline"
              onClick={() => onOpenSource(source.materialId, source.openSourceUrl)}
            >
              <ExternalLink className="h-4 w-4" />
              Открыть источник
            </Button>
          </div>
          {source.scoreBreakdown ? (
            <details className="rounded-[18px] border border-border bg-background/70 px-4 py-3">
              <summary className="cursor-pointer text-sm font-medium text-foreground">
                Score breakdown
              </summary>
              <div className="mt-3 grid gap-2 text-xs text-muted-foreground md:grid-cols-2">
                <div>chunkType: {source.chunkType ?? "NARRATIVE"}</div>
                <div>baseRrf: {source.scoreBreakdown.baseRrf}</div>
                <div>semantic bonus: {source.scoreBreakdown.semanticRankBonus}</div>
                <div>lexical bonus: {source.scoreBreakdown.lexicalRankBonus}</div>
                <div>identifier bonus: {source.scoreBreakdown.identifierBonus}</div>
                <div>heading bonus: {source.scoreBreakdown.headingBonus}</div>
                <div>metadata bonus: {source.scoreBreakdown.metadataBonus}</div>
                <div>appendix penalty: {source.scoreBreakdown.appendixPenalty}</div>
                <div>boilerplate penalty: {source.scoreBreakdown.boilerplatePenalty}</div>
                <div>low-confidence penalty: {source.scoreBreakdown.lowConfidencePenalty}</div>
                <div>final score: {source.scoreBreakdown.finalScore}</div>
              </div>
            </details>
          ) : null}
        </article>
      ))}
    </div>
  );
}
