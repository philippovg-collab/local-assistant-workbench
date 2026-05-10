package com.example.demo.service.audit.port;

import com.example.demo.service.audit.OperatorAuditEvent;
import java.time.Instant;

public interface OperatorAuditRepository {

    void save(OperatorAuditEvent event);

    int deleteEventsOlderThan(Instant cutoff);
}
