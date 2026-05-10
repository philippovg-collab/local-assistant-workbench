# Chat Lifecycle

`POST /api/chat-runs` is the canonical execution API. New chat executions write only to the `chat_run_*` durable lifecycle tables: queue, headers, snapshots, events, LLM calls, outputs, and immutable results.

The legacy `chat_audit_runs` table is read and retained only for historical data during the migration window. It is not an execution write path.

## Ownership And Cancellation

- Workers claim `chat_run_queue` rows with a lease owner and attempt count.
- Mutable trace writes for durable runs are accepted only while the matching queue row is `IN_PROGRESS` and owned by the same lease token.
- `COMPLETED`, `FAILED`, and `CANCELLED` are terminal states. Late completion after cancellation must not overwrite `CANCELLED`.
- Cancellation writes DB terminal state immediately. Same-process provider abort is best effort and happens only when the current JVM owns the local cancellation handle.
- Running queue rows are removed by the owning worker after terminal handling. Pending rows can be removed only through pending-row cleanup.

## Compatibility Endpoint

`POST /api/chat` is compatibility-only. It submits the same durable run, waits briefly, and returns the legacy response only if the run completes within the compatibility timeout. Timeout responses do not cancel the durable run.

Every `/api/chat` request emits `chat_compat_endpoint_used`; timeout responses also emit `chat_compat_endpoint_timeout`. The endpoint can be removed after 30 consecutive days of zero production usage and static/frontend confirmation that production code no longer calls `apiClient.executeChat`.
