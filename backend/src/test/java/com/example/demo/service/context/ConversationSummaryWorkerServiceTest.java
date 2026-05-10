package com.example.demo.service.context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.ContextProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import com.example.demo.service.context.ConversationSummaryPromptBuilder.SummaryPromptInput;
import com.example.demo.service.context.port.ContextAssemblyTraceRepository;
import com.example.demo.service.context.port.ConversationSummaryRepository;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConversationSummaryWorkerServiceTest {

    @Test
    void fallsBackToActiveChatProviderModelWhenPriorRunModelIsUnavailable() {
        WorkerFixture fixture = fixture(null);

        fixture.workerService.requestProcessing();

        verify(fixture.summaryService).summarize(any(SummaryPromptInput.class), eq("active-chat-model"));
        verify(fixture.summaryRepository).completeRefresh(
            eq(fixture.job),
            eq(fixture.payload),
            eq("run-1"),
            any(Instant.class)
        );
    }

    @Test
    void keepsPriorRunModelAheadOfActiveProviderDefault() {
        WorkerFixture fixture = fixture("prior-run-model");

        fixture.workerService.requestProcessing();

        verify(fixture.summaryService).summarize(any(SummaryPromptInput.class), eq("prior-run-model"));
        verify(fixture.activeProviderResolver, never()).defaultChatModel();
    }

    private WorkerFixture fixture(String priorRunModel) {
        ContextProperties contextProperties = new ContextProperties();
        contextProperties.setEnabled(true);
        contextProperties.setConversationsEnabled(true);
        contextProperties.setHistoryEnabled(true);
        contextProperties.setSummaryEnabled(true);
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setModel("stale-env-model");

        ConversationSummaryRefreshJob job = new ConversationSummaryRefreshJob(
            "conversation-1",
            1,
            "PENDING",
            0,
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-05-10T10:00:00Z"),
            Instant.parse("2026-05-10T10:00:00Z")
        );
        ConversationSummaryPayload payload = new ConversationSummaryPayload(
            "summary",
            List.of(),
            List.of(),
            List.of(),
            List.of(1)
        );
        StoredConversationRun run = new StoredConversationRun(
            "conversation-1",
            "run-1",
            1,
            null,
            null,
            "hash",
            "Original prompt",
            null,
            null,
            Instant.parse("2026-05-10T10:00:01Z"),
            "COMPLETED",
            Instant.parse("2026-05-10T10:00:02Z"),
            null,
            null,
            null
        );
        ChatExecutionResponse response = new ChatExecutionResponse(
            ChatMode.DIRECT,
            priorRunModel,
            "Original prompt",
            "Assistant answer",
            "READY",
            "2026-05-10T10:00:02Z",
            null,
            null,
            null,
            List.of(),
            List.of()
        );

        ConversationSummaryRepository summaryRepository = mock(ConversationSummaryRepository.class);
        when(summaryRepository.claimNextRefreshJob(anyString(), any(Instant.class), any(Duration.class)))
            .thenReturn(Optional.of(job), Optional.empty());
        when(summaryRepository.findByConversationId("conversation-1")).thenReturn(Optional.empty());
        when(summaryRepository.hasPendingRefreshJobs()).thenReturn(false);

        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        when(conversationRepository.listRuns("conversation-1")).thenReturn(List.of(run));

        ChatRunTraceRepository traceRepository = mock(ChatRunTraceRepository.class);
        when(traceRepository.findResult("run-1")).thenReturn(Optional.of(response));

        ContextAssemblyTraceRepository contextAssemblyTraceRepository = mock(ContextAssemblyTraceRepository.class);
        when(contextAssemblyTraceRepository.findByRunId("run-1")).thenReturn(Optional.empty());

        ConversationSummaryService summaryService = mock(ConversationSummaryService.class);
        when(summaryService.summarize(any(SummaryPromptInput.class), anyString())).thenReturn(payload);

        ActiveLlmProviderResolver activeProviderResolver = mock(ActiveLlmProviderResolver.class);
        if (priorRunModel == null) {
            when(activeProviderResolver.defaultChatModel()).thenReturn("active-chat-model");
        }

        ConversationSummaryWorkerService workerService = new ConversationSummaryWorkerService(
            contextProperties,
            llmProperties,
            activeProviderResolver,
            summaryRepository,
            conversationRepository,
            traceRepository,
            contextAssemblyTraceRepository,
            summaryService,
            Runnable::run
        );

        return new WorkerFixture(
            workerService,
            summaryRepository,
            summaryService,
            activeProviderResolver,
            job,
            payload
        );
    }

    private record WorkerFixture(
        ConversationSummaryWorkerService workerService,
        ConversationSummaryRepository summaryRepository,
        ConversationSummaryService summaryService,
        ActiveLlmProviderResolver activeProviderResolver,
        ConversationSummaryRefreshJob job,
        ConversationSummaryPayload payload
    ) {
    }
}
