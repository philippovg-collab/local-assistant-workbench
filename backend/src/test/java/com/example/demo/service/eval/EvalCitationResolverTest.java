package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.model.ChatSource;
import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.eval.AnswerCitation;
import com.example.demo.model.eval.AnswerClaim;
import com.example.demo.model.eval.EvalStructuredAnswer;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvalCitationResolverTest {

    private final EvalCitationResolver resolver = new EvalCitationResolver();

    @Test
    void resolvesSourceIdToStableLocator() {
        var source = new ChatSource("mat-1", "Title", "Excerpt", 100, 7, "test", false);

        EvalStructuredAnswer resolved = resolver.resolve(new EvalStructuredAnswer(
            "answer",
            "answered",
            List.of(new AnswerClaim("c1", "claim", List.of(new AnswerCitation(1, null))))
        ), List.of(source));

        EvidenceLocator locator = resolved.claims().getFirst().citations().getFirst().evidenceLocator();
        assertEquals("mat-1", locator.materialId());
        assertEquals(7, locator.page());
    }

    @Test
    void rejectsInvalidSourceId() {
        assertThrows(EvalCitationResolutionException.class, () -> resolver.resolve(new EvalStructuredAnswer(
            "answer",
            "answered",
            List.of(new AnswerClaim("c1", "claim", List.of(new AnswerCitation(2, null))))
        ), List.of(new ChatSource("mat-1", "Title", "Excerpt", 100, 1, "test", false))));
    }
}
