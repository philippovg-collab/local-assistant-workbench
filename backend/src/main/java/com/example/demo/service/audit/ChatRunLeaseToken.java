package com.example.demo.service.audit;

public record ChatRunLeaseToken(
    String runId,
    String leaseOwner,
    int attemptCount
) {
    public static ChatRunLeaseToken from(ChatRunQueueLease lease) {
        if (lease == null) {
            return null;
        }
        return new ChatRunLeaseToken(lease.runId(), lease.leaseOwner(), lease.attemptCount());
    }
}
