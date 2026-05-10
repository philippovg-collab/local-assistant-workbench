package com.example.demo.service;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.RechunkActiveMaterialsBatchRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.util.StringUtils;

final class MaterialRechunkBatchSupport {

    static final int MAX_RECHUNK_BATCH_LIMIT = 500;

    private static final String INVALID_LIMIT = "material.rechunk_batch_invalid_limit";
    private static final String INVALID_CURSOR = "material.rechunk_batch_invalid_cursor";
    private static final int DEFAULT_RECHUNK_BATCH_LIMIT = 100;
    private static final String CURSOR_VERSION = "v1";

    NormalizedBatchRequest normalizeBatchRequest(RechunkActiveMaterialsBatchRequest request) {
        int limit = request == null || request.limit() == null ? DEFAULT_RECHUNK_BATCH_LIMIT : request.limit();
        if (limit < 1 || limit > MAX_RECHUNK_BATCH_LIMIT) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                INVALID_LIMIT,
                "Batch limit must be between 1 and " + MAX_RECHUNK_BATCH_LIMIT
            );
        }

        return new NormalizedBatchRequest(
            limit,
            decodeCursor(request == null ? null : request.cursor()),
            request != null && Boolean.TRUE.equals(request.dryRun())
        );
    }

    String encodeCursor(RechunkBatchCursor cursor) {
        if (cursor == null) {
            return null;
        }

        String payload = CURSOR_VERSION + "|" + cursor.createdAt() + "|" + cursor.id();
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private RechunkBatchCursor decodeCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 3 || !CURSOR_VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported cursor format");
            }

            Instant createdAt = Instant.parse(parts[1]);
            String id = UUID.fromString(parts[2]).toString();
            return new RechunkBatchCursor(createdAt, id);
        } catch (RuntimeException exception) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                INVALID_CURSOR,
                "Batch cursor is invalid or unsupported",
                exception
            );
        }
    }

    record NormalizedBatchRequest(
        int limit,
        RechunkBatchCursor cursor,
        boolean dryRun
    ) {
    }

    record RechunkBatchCursor(
        Instant createdAt,
        String id
    ) {
    }
}
