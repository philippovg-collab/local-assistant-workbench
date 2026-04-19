package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.model.ChatAuditRunDetail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.http.HttpStatus;

final class ChatAuditJson {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private ChatAuditJson() {
    }

    static String write(ChatAuditRunDetail detail) {
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

    static ChatAuditRunDetail read(String rawJson) {
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
}
