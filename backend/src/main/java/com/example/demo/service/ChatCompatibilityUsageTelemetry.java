package com.example.demo.service;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatCompatibilityUsageTelemetry {

    private static final Logger logger = LoggerFactory.getLogger(ChatCompatibilityUsageTelemetry.class);

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong timeoutResponses = new AtomicLong(0);

    public long recordRequest() {
        long count = totalRequests.incrementAndGet();
        logger.info(
            "chat_compat_endpoint_used totalRequests={} timeoutResponses={}",
            count,
            timeoutResponses.get()
        );
        return count;
    }

    public long recordTimeoutResponse() {
        long count = timeoutResponses.incrementAndGet();
        logger.info(
            "chat_compat_endpoint_timeout totalRequests={} timeoutResponses={}",
            totalRequests.get(),
            count
        );
        return count;
    }

    public long totalRequests() {
        return totalRequests.get();
    }

    public long timeoutResponses() {
        return timeoutResponses.get();
    }
}
