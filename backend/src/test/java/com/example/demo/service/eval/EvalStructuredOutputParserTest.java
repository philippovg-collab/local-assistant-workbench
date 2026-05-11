package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class EvalStructuredOutputParserTest {

    private final EvalStructuredOutputParser parser = new EvalStructuredOutputParser(JsonMapper.builder().build());

    @Test
    void acceptsRawJsonOnly() {
        var raw = parser.parse("""
            {"answer":"10","finalMode":"answered","claims":[{"claimId":"c1","text":"Limit is 10","citations":[{"sourceId":1}]}]}
            """);
        assertEquals("10", raw.answer());
        assertEquals(1, raw.claims().getFirst().citations().getFirst().sourceId());
    }

    @Test
    void rejectsProseAndInvalidSchema() {
        assertThrows(EvalOutputFormatException.class, () -> parser.parse("Here is the answer: {\"answer\":\"10\"}"));
        assertThrows(EvalOutputFormatException.class, () -> parser.parse("{\"answer\":\"10\",\"finalMode\":\"answered\"}"));
        assertThrows(EvalOutputFormatException.class, () -> parser.parse(
            "{\"answer\":\"10\",\"finalMode\":\"answered\",\"claims\":[{\"claimId\":\"c1\",\"text\":\"x\",\"citations\":[{\"sourceId\":0}]}]}"
        ));
        assertThrows(EvalOutputFormatException.class, () -> parser.parse("""
            ```json
            {"answer":"none","finalMode":"abstained","claims":[]}
            ```
            """));
    }
}
