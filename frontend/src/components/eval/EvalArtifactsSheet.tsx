import { useMemo } from "react";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import type { EvalCase, EvalRunItem, EvalRunItemArtifact } from "@/types";
import { JsonBlock, MetricCard } from "./EvalShared";
import { asArray, asRecord, expectedModeLabels, shortId } from "./evalPresentation";

type EvalArtifactsSheetProps = {
  artifacts: EvalRunItemArtifact[];
  caseDetail: EvalCase | null;
  isLoading: boolean;
  item: EvalRunItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

export function EvalArtifactsSheet({
  artifacts,
  caseDetail,
  isLoading,
  item,
  open,
  onOpenChange,
}: EvalArtifactsSheetProps) {
  const artifactByType = useMemo(() => {
    const map = new Map<string, EvalRunItemArtifact>();
    artifacts.forEach((artifact) => {
      if (artifact.artifactType) {
        map.set(artifact.artifactType, artifact);
      }
    });
    return map;
  }, [artifacts]);

  const resultPayload = asRecord(artifactByType.get("CHAT_RUN_RESULT")?.payload);
  const structuredPayload = asRecord(artifactByType.get("STRUCTURED_OUTPUT")?.payload);
  const claimsPayload = asRecord(artifactByType.get("ANSWER_CLAIMS")?.payload);
  const contextPayload = asRecord(artifactByType.get("CONTEXT_ASSEMBLY")?.payload);
  const promptPayload = asRecord(artifactByType.get("PROMPT_SNAPSHOT")?.payload);
  const retrievalPayload = asRecord(artifactByType.get("RETRIEVAL_SUMMARY")?.payload);
  const itemArtifact = asRecord(item?.artifact);
  const claims = asArray(claimsPayload.claims);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-[92vw] max-w-3xl overflow-y-auto" side="right">
        <SheetHeader>
          <SheetTitle>Элемент оценки</SheetTitle>
          <SheetDescription>{item?.id ? shortId(item.id) : "Элемент не выбран"}</SheetDescription>
        </SheetHeader>
        {item ? (
          <div className="mt-6 space-y-4 text-sm leading-6 text-sidebar-foreground">
            <div className="grid gap-3 md:grid-cols-2">
              <MetricCard label="статус" value={item.status ?? "н/д"} />
              <MetricCard label="ошибка" value={item.failureCode ?? item.failureMessage ?? "н/д"} />
              <MetricCard label="chat run" value={shortId(item.chatRunId)} />
              <MetricCard label="ревизия кейса" value={item.caseRevision ?? "н/д"} />
              <MetricCard label="версия набора" value={String(itemArtifact.datasetVersion ?? "н/д")} />
              <MetricCard label="хеш содержимого кейса" value={shortId(String(itemArtifact.caseContentHash ?? ""))} />
            </div>

            <div className="rounded-[22px] border border-white/10 bg-white/6 px-4 py-4">
              <strong className="block text-sm font-semibold">Вопрос</strong>
              <p className="mt-2">{caseDetail?.question ?? "н/д"}</p>
              <p className="mt-2 text-sidebar-foreground/70">
                ожидаемый режим: {caseDetail?.expectedMode ? expectedModeLabels[caseDetail.expectedMode] : "н/д"}
              </p>
            </div>

            <div className="rounded-[22px] border border-white/10 bg-white/6 px-4 py-4">
              <strong className="block text-sm font-semibold">Ответ</strong>
              <p className="mt-2">
                {String(resultPayload.answer ?? resultPayload.finalUserAnswer ?? structuredPayload.answer ?? "н/д")}
              </p>
            </div>

            <div className="rounded-[22px] border border-white/10 bg-white/6 px-4 py-4">
              <strong className="block text-sm font-semibold">Локаторы доказательств</strong>
              {claims.length > 0 ? (
                <div className="mt-3 space-y-3">
                  {claims.map((claim, index) => {
                    const record = asRecord(claim);
                    const citations = asArray(record.citations);
                    return (
                      <div className="rounded-[18px] border border-white/10 bg-[#0b243a]/30 px-3 py-3" key={`${record.claimId ?? index}`}>
                        <p>{String(record.text ?? record.claimId ?? `утверждение-${index + 1}`)}</p>
                        <div className="mt-2 space-y-2">
                          {citations.map((citation, citationIndex) => (
                            <div className="text-xs text-sidebar-foreground/75" key={citationIndex}>
                              {JSON.stringify(asRecord(citation).evidenceLocator ?? citation)}
                            </div>
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <p className="mt-2 text-sidebar-foreground/70">н/д</p>
              )}
            </div>

            <div className="space-y-3">
              <ArtifactSummary title="Сборка контекста" value={contextPayload} />
              <ArtifactSummary title="Снимок prompt" value={promptPayload} />
              <ArtifactSummary title="Сводка retrieval" value={retrievalPayload} />
              <ArtifactSummary title="Все артефакты" value={artifacts} />
            </div>
            {isLoading ? <p className="text-sm text-sidebar-foreground/70">Загружаем артефакты...</p> : null}
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}

function ArtifactSummary({ title, value }: { title: string; value: unknown }) {
  return (
    <details className="rounded-[18px] border border-white/10 bg-white/6 px-4 py-3">
      <summary className="cursor-pointer text-sm font-semibold text-sidebar-foreground">{title}</summary>
      <div className="mt-3 text-foreground"><JsonBlock value={value} /></div>
    </details>
  );
}
