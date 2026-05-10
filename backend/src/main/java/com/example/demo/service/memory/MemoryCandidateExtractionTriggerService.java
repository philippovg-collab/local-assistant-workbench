package com.example.demo.service.memory;

import com.example.demo.config.ContextProperties;
import com.example.demo.service.AfterCommitExecutor;
import com.example.demo.service.ChatRunTraceService;
import com.example.demo.service.context.PreparedContextAssembly;
import com.example.demo.service.memory.port.MemoryRepository;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MemoryCandidateExtractionTriggerService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryCandidateExtractionTriggerService.class);

    private final ContextProperties properties;
    private final MemoryRepository repository;
    private final MemoryCandidateExtractionWorkerService workerService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final ChatRunTraceService chatRunTraceService;

    public MemoryCandidateExtractionTriggerService(
        ContextProperties properties,
        MemoryRepository repository,
        MemoryCandidateExtractionWorkerService workerService,
        AfterCommitExecutor afterCommitExecutor,
        ChatRunTraceService chatRunTraceService
    ) {
        this.properties = properties;
        this.repository = repository;
        this.workerService = workerService;
        this.afterCommitExecutor = afterCommitExecutor;
        this.chatRunTraceService = chatRunTraceService;
    }

    public void afterCompleted(
        PreparedContextAssembly contextAssembly,
        ChatRunTraceService.RunTraceContext traceContext
    ) {
        if (!properties.isLongTermMemoryEnabled()
            || contextAssembly == null
            || !contextAssembly.active()
            || !StringUtils.hasText(contextAssembly.conversationId())
            || !StringUtils.hasText(contextAssembly.runId())
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
            repository.enqueueExtractionJob(
                contextAssembly.conversationId(),
                contextAssembly.runId(),
                contextAssembly.turnNo(),
                Instant.now()
            );
            recordTriggered(traceContext, contextAssembly);
            workerService.requestProcessing();
        } catch (RuntimeException exception) {
            recordFailed(traceContext, exception);
            logger.warn("Memory extraction trigger failed; completed run is kept", exception);
        }
    }

    private void recordTriggered(
        ChatRunTraceService.RunTraceContext traceContext,
        PreparedContextAssembly contextAssembly
    ) {
        try {
            chatRunTraceService.insertEvent(traceContext, "MEMORY_EXTRACTION_REQUESTED", Map.of(
                "conversationId",
                contextAssembly.conversationId(),
                "turnNo",
                contextAssembly.turnNo()
            ));
        } catch (RuntimeException exception) {
            logger.warn("Unable to record memory extraction trigger event", exception);
        }
    }

    private void recordFailed(ChatRunTraceService.RunTraceContext traceContext, RuntimeException exception) {
        try {
            chatRunTraceService.insertEvent(traceContext, "MEMORY_EXTRACTION_TRIGGER_DEGRADED", Map.of(
                "reason",
                exception.getClass().getSimpleName()
            ));
        } catch (RuntimeException eventException) {
            logger.warn("Unable to record memory extraction trigger degraded event", eventException);
        }
    }
}
