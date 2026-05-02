import {
  Activity,
  BookCopy,
  BrainCircuit,
  DatabaseZap,
  SearchCheck,
  ShieldCheck,
} from "lucide-react";
import { Badge, type BadgeProps } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import type { HealthResponse, ModelInfo } from "@/types";
import type { RagReadinessPresentation } from "@/utils/readiness";
import { formatDate } from "@/utils/format";
import { buildSearchPresentation } from "@/utils/searchPresentation";

type StatusSummaryProps = {
  health: HealthResponse | null;
  healthError: string | null;
  models?: ModelInfo[];
  modelsError: string | null;
  ragPresentation: RagReadinessPresentation;
  instructionsCount?: number | null;
};

const badgeVariantByStatus = (
  status: "online" | "idle" | "warn",
): BadgeProps["variant"] => {
  if (status === "online") {
    return "success";
  }
  if (status === "warn") {
    return "warning";
  }
  return "secondary";
};

export function StatusSummary({
  health,
  healthError,
  models,
  modelsError,
  ragPresentation,
  instructionsCount = null,
}: StatusSummaryProps) {
  const modelCount = models?.length ?? 0;
  const isBackendHealthy = health?.status === "UP";
  const searchMode = health?.searchMode;
  const searchProvider = health?.searchProvider;
  const searchPresentation = buildSearchPresentation(health);
  const backendMessage = health
    ? isBackendHealthy
      ? health.application
      : health.directReasonMessage ??
        health.llmReasonMessage ??
        health.embeddingReasonMessage ??
        health.knowledgeReasonMessage ??
        health.ocrReasonMessage ??
        health.vectorReasonMessage ??
        health.application
    : healthError ?? "Проверяем сервис";
  const llmMessage = health?.directStatus === "DOWN"
    ? health.directReasonMessage ?? "Direct chat path сейчас недоступен."
    : health?.llmStatus === "DOWN"
      ? health.llmReasonMessage ?? "Каталог моделей сейчас недоступен."
    : modelsError ?? (models ? "Единый LLM client обслуживает direct и RAG режимы." : "Каталог моделей загрузится при открытии Studio.");
  const queueSummary = health
    ? `${health.indexingPendingCount ?? 0} pending / ${health.indexingInProgressCount ?? 0} in progress / ${health.indexingFailedCount ?? 0} failed`
    : "Ожидаем snapshot очереди индексации.";
  const searchBacklogSummary = health?.searchSyncBacklog
    ? `${health.searchSyncBacklog.pendingCount ?? 0} pending / ${health.searchSyncBacklog.inProgressCount ?? 0} in progress / ${health.searchSyncBacklog.failedCount ?? 0} failed`
    : "Ожидаем snapshot search sync backlog.";
  const qualityFlagsSummary = health?.qualityLayer
    ? [
        `metadata=${health.qualityLayer.flags.metadataV1 ? "on" : "off"}`,
        `structured=${health.qualityLayer.flags.structuredV1 ? "on" : "off"}`,
        `filters=${health.qualityLayer.flags.metadataFiltersV1 ? "on" : "off"}`,
        `search=${health.qualityLayer.flags.searchApiV1 ? "on" : "off"}`,
        `reranker=${health.qualityLayer.flags.rerankerV1 ? "on" : "off"}`,
        `hints=${health.qualityLayer.flags.queryHintsV1 ? "on" : "off"}`,
      ].join(" · ")
    : "Quality-layer flags пока не опубликованы backend-контрактом.";
  const qualityMetadataSummary = health?.qualityLayer
    ? `${health.qualityLayer.metadataCoverage.activeWithEffectiveMetadata}/${health.qualityLayer.metadataCoverage.activeTotal} active материалов с canonical metadata`
    : "Ждём metadata coverage.";
  const qualityBackfillSummary = health?.qualityLayer
    ? `${health.qualityLayer.activeBackfillCoverage.structuredProfileActive}/${health.qualityLayer.activeBackfillCoverage.activeTotal} active на structured-v1 · pending=${health.qualityLayer.activeBackfillCoverage.pendingBackfill} · partial-ready=${health.qualityLayer.activeBackfillCoverage.partialReadyActive}`
    : "Ждём ACTIVE backfill coverage.";
  const qualityRetrievalWindowSummary = health?.qualityLayer
    ? `sample=${health.qualityLayer.retrievalWindow.sampleSize} · no-context=${Math.round(health.qualityLayer.retrievalWindow.noContextRate * 100)}%`
    : "Ждём retrieval window.";
  const summaryCards = [
    {
      icon: Activity,
      label: "Backend",
      headline: health ? health.status : "Недоступен",
      message: backendMessage,
      tone: isBackendHealthy ? "online" : "warn",
    },
    {
      icon: BrainCircuit,
      label: "LLM",
      headline:
        health?.directStatus === "DOWN"
          ? "Direct недоступен"
          : health?.llmStatus === "DOWN"
            ? "Каталог моделей недоступен"
          : models
            ? modelCount > 0
              ? `${modelCount} моделей`
              : "Нет списка моделей"
            : "По запросу",
      message: llmMessage,
      tone: health?.directStatus === "DOWN" || health?.llmStatus === "DOWN" ? "warn" : modelCount > 0 ? "online" : "idle",
    },
    {
      icon: SearchCheck,
      label: "RAG",
      headline: ragPresentation.headline,
      message: ragPresentation.overviewMessage,
      tone: ragPresentation.statusDotClass,
    },
    {
      icon: DatabaseZap,
      label: "Search",
      headline: searchPresentation.label,
      message: searchPresentation.detail,
      tone: searchPresentation.tone,
    },
    {
      icon: BookCopy,
      label: "Инструкции",
      headline: instructionsCount === null ? "По запросу" : `${instructionsCount}`,
      message: instructionsCount === null
        ? "Instruction snippets загрузятся при открытии вкладок Instructions или Studio."
        : "Instruction snippets теперь подключаются явно и реально влияют на prompt policy.",
      tone: instructionsCount && instructionsCount > 0 ? "online" : "idle",
    },
  ] as const;

  const runtimeRows = [
    {
      label: "Последний успешный direct probe",
      value:
        health?.directLastSuccessfulProbeAt
          ? formatDate(health.directLastSuccessfulProbeAt)
          : "Пока нет",
    },
    {
      label: "Последний успешный LLM probe",
      value: health?.llmLastSuccessfulProbeAt ? formatDate(health.llmLastSuccessfulProbeAt) : "Пока нет",
    },
    {
      label: "Последний успешный embedding probe",
      value:
        health?.embeddingLastSuccessfulProbeAt
          ? formatDate(health.embeddingLastSuccessfulProbeAt)
          : "Пока нет",
    },
    {
      label: "Knowledge status",
      value: health?.knowledgeStatus ?? "Неизвестно",
    },
    {
      label: "Knowledge readiness reason",
      value: health?.knowledgeReasonMessage ?? "Knowledge base готова для RAG.",
    },
    {
      label: "Ready active материалы",
      value: health
        ? `${health.readyMaterialCount ?? 0} ready / ${health.activeMaterialCount ?? 0} active / ${health.materialCount ?? 0} total`
        : "Ожидаем snapshot knowledge base.",
    },
    {
      label: "Причина RAG degraded",
      value: health?.ragDegradedReasonMessage ?? "RAG readiness сейчас не деградирован.",
    },
    {
      label: "Причина Direct degraded",
      value: health?.directReasonMessage ?? "Direct chat path сейчас не деградирован.",
    },
    {
      label: "Причина model catalog degraded",
      value: health?.llmReasonMessage ?? "Model catalog сейчас не деградирован.",
    },
    {
      label: "Очередь индексации",
      value: queueSummary,
    },
    {
      label: "Следующий retry",
      value: health?.indexingNextRetryAt ? formatDate(health.indexingNextRetryAt) : "Не запланирован",
    },
    {
      label: "Quality-layer flags",
      value: qualityFlagsSummary,
    },
    {
      label: "Metadata coverage",
      value: qualityMetadataSummary,
    },
    {
      label: "ACTIVE backfill coverage",
      value: qualityBackfillSummary,
    },
    {
      label: "Retrieval window",
      value: qualityRetrievalWindowSummary,
    },
  ];

  const searchRows = [
    {
      label: "Configured mode",
      value: searchMode ?? "Неизвестно",
    },
    {
      label: "Effective provider",
      value: searchProvider ?? "Неизвестно",
    },
    {
      label: "Search status",
      value: searchPresentation.statusLabel,
    },
    {
      label: "Elasticsearch sync",
      value: searchPresentation.syncLabel,
    },
    {
      label: "Search пояснение",
      value: searchPresentation.detail,
    },
    {
      label: "Search sync backlog",
      value: searchBacklogSummary,
    },
    {
      label: "Самый старый outstanding",
      value:
        health?.searchSyncBacklog?.oldestOutstandingAt
          ? formatDate(health.searchSyncBacklog.oldestOutstandingAt)
          : "Нет backlog",
    },
    {
      label: "Последний успешный sync",
      value:
        health?.searchSyncBacklog?.lastSuccessfulSyncAt
          ? formatDate(health.searchSyncBacklog.lastSuccessfulSyncAt)
          : "Пока нет",
    },
  ];

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        {summaryCards.map((card) => {
          const Icon = card.icon;

          return (
            <Card className="h-full p-0" key={card.label}>
              <CardContent className="mt-0 flex h-full flex-col gap-4 p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                    <Icon className="h-5 w-5" />
                  </div>
                  <Badge variant={badgeVariantByStatus(card.tone)}>{card.label}</Badge>
                </div>
                <div className="space-y-2">
                  <strong className="block text-xl font-semibold tracking-normal text-foreground">
                    {card.headline}
                  </strong>
                  <p className="text-sm leading-6 text-muted-foreground">{card.message}</p>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader className="gap-4">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-primary" />
                <CardTitle>Runtime Signals</CardTitle>
              </div>
              <Badge variant="secondary">/api/health</Badge>
            </div>
          </CardHeader>
          <CardContent className="mt-0 space-y-3">
            {runtimeRows.map((row, index) => (
              <div key={row.label}>
                {index > 0 ? <Separator className="mb-3" /> : null}
                <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between sm:gap-6">
                  <strong className="text-sm font-medium text-foreground">{row.label}</strong>
                  <span className="text-sm leading-6 text-muted-foreground sm:max-w-[52%] sm:text-right">
                    {row.value}
                  </span>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="gap-4">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <SearchCheck className="h-4 w-4 text-primary" />
                <CardTitle>Search Plane</CardTitle>
              </div>
              <Badge variant="secondary">production lexical</Badge>
            </div>
          </CardHeader>
          <CardContent className="mt-0 space-y-3">
            {searchRows.map((row, index) => (
              <div key={row.label}>
                {index > 0 ? <Separator className="mb-3" /> : null}
                <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between sm:gap-6">
                  <strong className="text-sm font-medium text-foreground">{row.label}</strong>
                  <span className="text-sm leading-6 text-muted-foreground sm:max-w-[52%] sm:text-right">
                    {row.value}
                  </span>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
