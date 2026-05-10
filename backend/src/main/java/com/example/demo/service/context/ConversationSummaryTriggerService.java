package com.example.demo.service.context;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.ContextAssemblyDroppedItem;
import com.example.demo.model.ConversationSummaryMemory;
import com.example.demo.service.AfterCommitExecutor;
import com.example.demo.service.ChatRunTraceService;
import com.example.demo.service.context.port.ConversationSummaryRepository;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConversationSummaryTriggerService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationSummaryTriggerService.class);
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final ContextProperties contextProperties;
    private final ConversationSummaryRepository summaryRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationSummaryWorkerService workerService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final ChatRunTraceService chatRunTraceService;

    public ConversationSummaryTriggerService(
        ContextProperties contextProperties,
        ConversationSummaryRepository summaryRepository,
        ConversationRepository conversationRepository,
        ConversationSummaryWorkerService workerService,
        AfterCommitExecutor afterCommitExecutor,
        ChatRunTraceService chatRunTraceService
    ) {
        this.contextProperties = contextProperties;
        this.summaryRepository = summaryRepository;
        this.conversationRepository = conversationRepository;
        this.workerService = workerService;
        this.afterCommitExecutor = afterCommitExecutor;
        this.chatRunTraceService = chatRunTraceService;
    }

    public void afterCompleted(
        PreparedContextAssembly contextAssembly,
        ChatRunTraceService.RunTraceContext traceContext
    ) {
        if (!contextProperties.isSummaryEnabled()
            || contextAssembly == null
            || !contextAssembly.active()
            || !StringUtils.hasText(contextAssembly.conversationId())
            || contextAssembly.turnNo() == null) {
            return;
        }
        afterCommitExecutor.afterCommit(() -> triggerBestEffort(contextAssembly, traceContext));
    }

    private void triggerBestEffort(
        PreparedContextAssembly contextAssembly,
        ChatRunTraceService.RunTraceContext traceContext
    ) {
        try {
            ConversationSummaryMemory summary = summaryRepository
                .findByConversationId(contextAssembly.conversationId())
                .orElse(null);
            int summaryThroughTurnNo = summary == null || summary.summaryThroughTurnNo() == null
                ? 0
                : summary.summaryThroughTurnNo();
            boolean refreshDue = completedTurnsSince(contextAssembly.conversationId(), summaryThroughTurnNo)
                >= contextProperties.getSummaryRefreshTurns();
            boolean budgetDroppedHistory = contextAssembly.droppedItems().stream()
                .map(ContextAssemblyDroppedItem::reason)
                .anyMatch(reason -> "token_budget".equals(reason) || "max_turns".equals(reason));
            boolean budgetOverflow = contextAssembly.tokenBudget() != null
                && contextAssembly.tokenBudget().droppedHistoryTokens() != null
                && contextAssembly.tokenBudget().droppedHistoryTokens() > 0;
            if (!refreshDue && !budgetDroppedHistory && !budgetOverflow) {
                return;
            }
            summaryRepository.requestRefresh(
                contextAssembly.conversationId(),
                contextAssembly.turnNo(),
                Instant.now()
            );
            recordTriggered(traceContext, contextAssembly, refreshDue, budgetDroppedHistory || budgetOverflow);
            workerService.requestProcessing();
        } catch (RuntimeException exception) {
            recordFailed(traceContext, exception);
            logger.warn("Conversation summary trigger failed; completed run is kept", exception);
        }
    }

    private long completedTurnsSince(String conversationId, int summaryThroughTurnNo) {
        return conversationRepository.listRuns(conversationId).stream()
            .filter(run -> STATUS_COMPLETED.equals(run.status()))
            .mapToInt(StoredConversationRun::turnNo)
            .filter(turnNo -> turnNo > summaryThroughTurnNo)
            .count();
    }

    private void recordTriggered(
        ChatRunTraceService.RunTraceContext traceContext,
        PreparedContextAssembly contextAssembly,
        boolean refreshDue,
        boolean budgetOverflow
    ) {
        try {
            chatRunTraceService.insertEvent(traceContext, "CONVERSATION_SUMMARY_REFRESH_REQUESTED", Map.of(
                "conversationId",
                contextAssembly.conversationId(),
                "requestedThroughTurnNo",
                contextAssembly.turnNo(),
                "refreshDue",
                refreshDue,
                "budgetOverflow",
                budgetOverflow
            ));
        } catch (RuntimeException exception) {
            logger.warn("Unable to record conversation summary trigger event", exception);
        }
    }

    private void recordFailed(ChatRunTraceService.RunTraceContext traceContext, RuntimeException exception) {
        try {
            chatRunTraceService.insertEvent(traceContext, "CONVERSATION_SUMMARY_TRIGGER_DEGRADED", Map.of(
                "reason",
                exception.getClass().getSimpleName()
            ));
        } catch (RuntimeException eventException) {
            logger.warn("Unable to record conversation summary trigger degraded event", eventException);
        }
    }
}
