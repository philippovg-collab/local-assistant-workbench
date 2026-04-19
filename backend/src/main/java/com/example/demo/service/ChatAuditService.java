package com.example.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.example.demo.api.ApiException;
import com.example.demo.infrastructure.audit.PostgresChatAuditRepository;
import com.example.demo.infrastructure.audit.StoredChatAuditRunRecord;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatAuditService {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final int DEFAULT_LIST_LIMIT = 20;

    private final PostgresChatAuditRepository repository;

    public ChatAuditService(PostgresChatAuditRepository repository) {
        this.repository = repository;
    }

    public String record(ChatExecutionResponse response) {
        if (response == null) {
            return null;
        }

        ChatAuditRunDetail detail = new ChatAuditRunDetail(
            UUID.randomUUID().toString(),
            response.mode(),
            response.model(),
            response.prompt(),
            response.answer(),
            response.contextStatus(),
            response.answerModeApplied(),
            Instant.parse(response.createdAt()),
            response.instructionTrace(),
            response.knowledgeScopeResolved(),
            response.retrievalTrace(),
            response.sources()
        );
        repository.save(new StoredChatAuditRunRecord(
            detail.id(),
            detail.mode(),
            detail.model(),
            detail.prompt(),
            detail.answer(),
            detail.contextStatus(),
            detail.answerMode(),
            writeJson(detail),
            detail.createdAt()
        ));
        return detail.id();
    }

    public List<ChatAuditRunSummary> listRuns() {
        return repository.findAll(DEFAULT_LIST_LIMIT).stream()
            .map(record -> new ChatAuditRunSummary(
                record.id(),
                record.mode(),
                record.model(),
                record.answerMode(),
                clip(record.prompt(), 120),
                clip(record.answer(), 160),
                record.createdAt()
            ))
            .toList();
    }

    public ChatAuditRunDetail getRun(String id) {
        return repository.findById(requireValidId(id))
            .map(record -> readJson(record.auditJson()))
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "chat_audit.not_found",
                "Chat audit run '" + id + "' does not exist"
            ));
    }

    private String requireValidId(String id) {
        try {
            return UUID.fromString(id).toString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "chat_audit.invalid_id",
                "Chat audit id must be a valid UUID",
                exception
            );
        }
    }

    private String writeJson(ChatAuditRunDetail detail) {
        try {
            return JSON_MAPPER.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_audit.storage_encode_failed",
                "Unable to encode chat audit JSON",
                exception
            );
        }
    }

    private ChatAuditRunDetail readJson(String rawJson) {
        try {
            return JSON_MAPPER.readValue(rawJson, ChatAuditRunDetail.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_audit.storage_decode_failed",
                "Unable to decode chat audit JSON",
                exception
            );
        }
    }

    private String clip(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }
}
