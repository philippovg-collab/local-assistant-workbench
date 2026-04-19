package com.example.demo.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ChatRunOutputTrace(
    String rawModelAnswer,
    String finalUserAnswer,
    List<ChatSource> sources,
    JsonNode postprocess,
    Boolean abstained,
    Boolean strictSourcesBlockedAnswer
) {
    public ChatRunOutputTrace {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
