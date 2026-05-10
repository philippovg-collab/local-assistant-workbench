package com.example.demo.service;

import com.example.demo.config.OperatorAuditProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OperatorAuditRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(OperatorAuditRetentionService.class);

    private final OperatorAuditProperties properties;
    private final OperatorAuditService operatorAuditService;

    public OperatorAuditRetentionService(
        OperatorAuditProperties properties,
        OperatorAuditService operatorAuditService
    ) {
        this.properties = properties;
        this.operatorAuditService = operatorAuditService;
    }

    @Scheduled(cron = "0 45 3 * * *")
    public void deleteExpiredEvents() {
        int retentionDays = properties.getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = operatorAuditService.deleteEventsOlderThan(cutoff);
        if (deleted > 0) {
            logger.info("Deleted expired operator audit events cutoff={} events={}", cutoff, deleted);
        }
    }
}
