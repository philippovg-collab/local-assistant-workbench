import { Braces, MessageSquareCode, Sparkles, TerminalSquare } from "lucide-react";
import { apiClient } from "@/api/client";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { StudioScaffold } from "@/components/app/StudioScaffold";
import { AppliedInstructionList } from "@/components/AppliedInstructionList";
import { ChatAuditPanel } from "@/components/ChatAuditPanel";
import { ChatForm } from "@/components/ChatForm";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import type {
  AnswerMode,
  ChatAuditRunDetail,
  ChatAuditRunSummary,
  ChatExecutionRequest,
  ChatExecutionResponse,
  InstructionSummary,
  ModelInfo,
} from "@/types";
import { formatDate } from "@/utils/format";
import { answerModeLabels } from "@/utils/workbenchPresentation";

type DirectChatPanelProps = {
  helperText: string;
  isBlocked: boolean;
  models: ModelInfo[];
  modelsError: string | null;
  selectedModel: string;
  onModelChange: (value: string) => void;
  answerMode: AnswerMode;
  onAnswerModeChange: (value: AnswerMode) => void;
  prompt: string;
  onPromptChange: (value: string) => void;
  temporaryInstruction: string;
  onTemporaryInstructionChange: (value: string) => void;
  instructions: InstructionSummary[];
  selectedInstructionIds: string[];
  onToggleInstruction: (instructionId: string) => void;
  isSubmitting: boolean;
  currentRunStatus?: string | null;
  error: string | null;
  response: ChatExecutionResponse | null;
  requestPreview: ChatExecutionRequest;
  chatRuns: ChatAuditRunSummary[];
  selectedChatRun: ChatAuditRunDetail | null;
  chatRunsError: string | null;
  onLoadChatRun: (runId: string) => Promise<ChatAuditRunDetail | null>;
  onSubmit: () => Promise<unknown>;
};

const codeBlockClassName =
  "overflow-x-auto rounded-[22px] border border-border/70 bg-[#0c2238] px-4 py-4 font-mono text-xs leading-6 text-[#dceeff]";

export function DirectChatPanel({
  helperText,
  isBlocked,
  models,
  modelsError,
  selectedModel,
  onModelChange,
  answerMode,
  onAnswerModeChange,
  prompt,
  onPromptChange,
  temporaryInstruction,
  onTemporaryInstructionChange,
  instructions,
  selectedInstructionIds,
  onToggleInstruction,
  isSubmitting,
  currentRunStatus,
  error,
  response,
  requestPreview,
  chatRuns,
  selectedChatRun,
  chatRunsError,
  onLoadChatRun,
  onSubmit,
}: DirectChatPanelProps) {
  return (
    <StudioScaffold
      badge="POST /api/chat-runs mode=direct"
      controls={
        <ChatForm
          answerMode={answerMode}
          error={error}
          helperText={isSubmitting && currentRunStatus ? `${helperText} Статус запуска: ${currentRunStatus}.` : helperText}
          instructionEmptyStateMessage="Сначала создай chat/scenario инструкцию во вкладке библиотеки."
          instructions={instructions}
          isSubmitDisabled={isSubmitting || !prompt.trim() || isBlocked}
          isSubmitting={isSubmitting}
          models={models}
          modelsError={modelsError}
          onAnswerModeChange={onAnswerModeChange}
          onModelChange={onModelChange}
          onPromptChange={onPromptChange}
          onSubmit={onSubmit}
          onTemporaryInstructionChange={onTemporaryInstructionChange}
          onToggleInstruction={onToggleInstruction}
          prompt={prompt}
          promptLabel="User prompt"
          promptRows={7}
          selectedInstructionIds={selectedInstructionIds}
          selectedModel={selectedModel}
          submitBusyLabel="Отправляем..."
          submitIdleLabel="Отправить напрямую"
          temporaryInstruction={temporaryInstruction}
          temporaryInstructionLabel="Временная инструкция на этот запрос"
          temporaryInstructionPlaceholder="Например: структурируй ответ в 3 пункта."
          temporaryInstructionRows={4}
        />
      }
      description="Direct-режим использует тот же execution contract, но без retrieval. Здесь можно проверить instruction stack, answer mode и audit trail без влияния локального контекста."
      eyebrow="Direct Studio"
      layout="stacked"
      results={
        <div className="space-y-5">
          <SectionIntro
            badge="Live contract"
            badgeVariant="secondary"
            description="Здесь виден transport-слой: пример запроса, raw JSON и итоговый ответ модели вместе с применённым instruction trace."
            eyebrow="Direct Response"
            title="Контракт запроса и ответа"
          />

          <div className="space-y-4">
            <article className="surface-subtle space-y-3 rounded-[24px] p-5">
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <TerminalSquare className="h-4 w-4 text-primary" />
                  curl
                </div>
                <Badge variant="secondary">unified chat contract</Badge>
              </div>
              <pre className={codeBlockClassName}>{apiClient.buildCurlExample(requestPreview)}</pre>
            </article>

            {response ? (
              <>
                <article className="surface-subtle space-y-4 rounded-[24px] p-5">
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                      <Braces className="h-4 w-4 text-primary" />
                      JSON
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant="default">{response.model}</Badge>
                      <Badge variant="secondary">{answerModeLabels[response.answerModeApplied ?? answerMode]}</Badge>
                    </div>
                  </div>
                  <pre className={codeBlockClassName}>{JSON.stringify(response, null, 2)}</pre>
                </article>

                <article className="surface-subtle space-y-4 rounded-[24px] p-5">
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                      <MessageSquareCode className="h-4 w-4 text-primary" />
                      Ответ модели
                    </div>
                    <Badge variant="secondary">{formatDate(response.createdAt)}</Badge>
                  </div>
                  <Separator />
                  <p className="text-sm leading-7 text-foreground">{response.answer}</p>
                </article>

                <AppliedInstructionList
                  appliedInstructions={response.appliedInstructions}
                  instructionTrace={response.instructionTrace}
                />

                <ChatAuditPanel
                  currentAuditRunId={response.auditRunId}
                  currentInstructionTrace={response.instructionTrace}
                  currentKnowledgeScopeResolved={response.knowledgeScopeResolved}
                  currentRetrievalTrace={response.retrievalTrace}
                  error={chatRunsError}
                  runs={chatRuns}
                  selectedRun={selectedChatRun}
                  onLoadRun={onLoadChatRun}
                />
              </>
            ) : (
              <EmptyState
                description="После запроса здесь появятся JSON-ответ, instruction trace и аудит запуска."
                icon={Sparkles}
                title="Прямой ответ пока пустой"
              />
            )}
          </div>
        </div>
      }
      title="Прямой запрос к модели"
    />
  );
}
