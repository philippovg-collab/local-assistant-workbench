package com.example.demo.service;

import com.example.demo.config.SearchSyncProperties;
import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "app.search-sync", name = "enabled", havingValue = "true")
public class SearchSyncOperatorRunner implements ApplicationRunner {

    private final SearchSyncProperties searchSyncProperties;
    private final SearchSyncRecoveryService recoveryService;
    private final ConfigurableApplicationContext applicationContext;

    public SearchSyncOperatorRunner(
        SearchSyncProperties searchSyncProperties,
        SearchSyncRecoveryService recoveryService,
        ConfigurableApplicationContext applicationContext
    ) {
        this.searchSyncProperties = searchSyncProperties;
        this.recoveryService = recoveryService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(searchSyncProperties.getOperator().getCommand())) {
            return;
        }
        if (!searchSyncProperties.getOperator().isEnabled()) {
            throw new IllegalStateException(
                "Search sync operator command is configured, but app.search-sync.operator.enabled is false."
            );
        }

        String command = normalizedCommand();
        Duration timeout = Duration.ofSeconds(searchSyncProperties.getOperator().getWaitTimeoutSeconds());
        Duration pollInterval = Duration.ofMillis(searchSyncProperties.getOperator().getPollIntervalMillis());

        try {
            if ("requeue-failed".equals(command)) {
                SearchSyncRecoveryService.RequeueRunSummary summary =
                    recoveryService.recoverFailedEventsAndWait(timeout, pollInterval);
                printRequeueSummary(summary);
                return;
            }

            if ("rebuild-write-index".equals(command)) {
                SearchSyncRecoveryService.RebuildRunSummary summary =
                    recoveryService.rebuildCurrentWriteIndexAndWait(timeout, pollInterval);
                printRebuildSummary(summary);
                return;
            }

            throw new IllegalArgumentException(
                "Unsupported search sync operator command '" + command + "'. Expected requeue-failed or rebuild-write-index."
            );
        } finally {
            applicationContext.close();
        }
    }

    private String normalizedCommand() {
        String command = searchSyncProperties.getOperator().getCommand();
        if (!StringUtils.hasText(command)) {
            throw new IllegalArgumentException("Search sync operator command must not be blank");
        }
        return command.trim().toLowerCase(Locale.ROOT);
    }

    private void printRequeueSummary(SearchSyncRecoveryService.RequeueRunSummary summary) {
        System.out.println(
            "search-sync operator completed: command=requeue-failed"
                + " requeuedFailed="
                + summary.requeuedFailedCount()
                + " waitedMs="
                + summary.waitSummary().waitedMillis()
                + " lastSuccessfulSyncAt="
                + summary.waitSummary().lastSuccessfulSyncAt()
        );
    }

    private void printRebuildSummary(SearchSyncRecoveryService.RebuildRunSummary summary) {
        System.out.println(
            "search-sync operator completed: command=rebuild-write-index"
                + " writeIndex="
                + summary.writeIndexName()
                + " clearedDocuments="
                + summary.clearedDocumentCount()
                + " requeuedFailed="
                + summary.requeuedFailedCount()
                + " replayEnqueued="
                + summary.replayEnqueuedCount()
                + " waitedMs="
                + summary.waitSummary().waitedMillis()
                + " lastSuccessfulSyncAt="
                + summary.waitSummary().lastSuccessfulSyncAt()
        );
    }
}
