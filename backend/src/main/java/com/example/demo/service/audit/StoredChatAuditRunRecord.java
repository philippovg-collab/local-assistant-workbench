package com.example.demo.service.audit;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatMode;
import java.time.Instant;

public record StoredChatAuditRunRecord(
    String id,
    ChatMode mode,
    String model,
    String prompt,
    String answer,
    String contextStatus,
    AnswerMode answerMode,
    String auditJson,
    Instant createdAt
) {
}
