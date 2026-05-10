package com.example.demo.service;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.ChatAuditRunDetail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

final class ChatAuditJson {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private ChatAuditJson() {
    }

    static String write(ChatAuditRunDetail detail) {
        try {
            return JSON_MAPPER.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new ApplicationException(
                ErrorType.INTERNAL,
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
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "chat_audit.storage_decode_failed",
                "Unable to decode chat audit JSON",
                exception
            );
        }
    }
}
