package com.example.demo.service;

import com.example.demo.config.ChatAuditProperties;
import com.example.demo.service.audit.port.ChatAuditRepository;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ChatAuditRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(ChatAuditRetentionService.class);

    private final ChatAuditProperties properties;
    private final ChatRunTraceRepository traceRepository;
    private final ChatAuditRepository legacyAuditRepository;

    public ChatAuditRetentionService(
        ChatAuditProperties properties,
        ChatRunTraceRepository traceRepository,
        ChatAuditRepository legacyAuditRepository
    ) {
        this.properties = properties;
        this.traceRepository = traceRepository;
        this.legacyAuditRepository = legacyAuditRepository;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void deleteExpiredRuns() {
        int retentionDays = properties.getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deletedTraces = traceRepository.deleteRunsOlderThan(cutoff);
        int deletedLegacyAuditRuns = legacyAuditRepository.deleteRunsOlderThan(cutoff);
        if (deletedTraces > 0 || deletedLegacyAuditRuns > 0) {
            logger.info(
                "Deleted expired chat audit data cutoff={} traceRuns={} legacyRuns={}",
                cutoff,
                deletedTraces,
                deletedLegacyAuditRuns
            );
        }
    }
}
