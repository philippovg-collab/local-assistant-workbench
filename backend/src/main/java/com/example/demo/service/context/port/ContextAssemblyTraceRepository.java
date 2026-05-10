package com.example.demo.service.context.port;

import com.example.demo.model.ContextAssemblySnapshotDetail;
import com.example.demo.service.audit.ChatRunLeaseToken;
import java.util.Optional;

public interface ContextAssemblyTraceRepository {

    ContextAssemblySnapshotDetail save(ContextAssemblySnapshotDetail snapshot);

    default ContextAssemblySnapshotDetail save(
        ContextAssemblySnapshotDetail snapshot,
        ChatRunLeaseToken leaseToken
    ) {
        return save(snapshot);
    }

    Optional<ContextAssemblySnapshotDetail> findByRunId(String runId);
}
