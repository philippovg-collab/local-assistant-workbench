package com.example.demo.service.context;

import com.example.demo.model.ContextAssemblySnapshotDetail;
import com.example.demo.service.AuditRedactionService;
import com.example.demo.service.ChatRunTraceService;
import com.example.demo.service.audit.ChatRunLeaseToken;
import com.example.demo.service.cancellation.ChatRunLeaseLostException;
import com.example.demo.service.context.port.ContextAssemblyTraceRepository;
import org.springframework.stereotype.Service;

@Service
public class ContextAssemblyTraceService {

    private final ContextAssemblyTraceRepository repository;
    private final AuditRedactionService redactionService;

    public ContextAssemblyTraceService(
        ContextAssemblyTraceRepository repository,
        AuditRedactionService redactionService
    ) {
        this.repository = repository;
        this.redactionService = redactionService;
    }

    ContextAssemblySnapshotDetail save(ContextAssemblySnapshotDetail snapshot) {
        return save(snapshot, null);
    }

    ContextAssemblySnapshotDetail save(
        ContextAssemblySnapshotDetail snapshot,
        ChatRunTraceService.RunTraceContext traceContext
    ) {
        ChatRunLeaseToken leaseToken = traceContext == null ? null : traceContext.leaseToken();
        ContextAssemblySnapshotDetail saved = repository.save(
            redactionService.redactContextAssemblySnapshot(snapshot),
            leaseToken
        );
        if (saved == null && leaseToken != null) {
            throw new ChatRunLeaseLostException(
                "Durable chat run lease no longer owns run " + leaseToken.runId() + " while saving context assembly"
            );
        }
        return saved;
    }
}
