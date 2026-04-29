package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.demo.model.DocumentType;
import com.example.demo.model.RetrievalQueryHints;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalQueryHintExtractorTest {

    private final RetrievalQueryHintExtractor extractor = new RetrievalQueryHintExtractor();

    @Test
    void extractsDocumentNumbersDateRangesVersionLanguageAndBusinessFacets() {
        RetrievalQueryHints hints = extractor.extract(
            "Покажи по проекту North Upgrade, договор № KZ-2026-0415-ENERGY с 2026-04-01 по 2026-04-30 "
                + "версия 2 на русском, контрагент GridBuild LLP, статус APPROVED, подразделение Grid operations"
        );

        assertEquals("KZ-2026-0415-ENERGY", hints.documentNumber());
        assertEquals("2026-04-01", hints.documentDateFrom().toString());
        assertEquals("2026-04-30", hints.documentDateTo().toString());
        assertEquals("версия 2", hints.versionLabel());
        assertEquals("ru", hints.language());
        assertEquals("North Upgrade", hints.project());
        assertEquals(List.of(), hints.projectKeys());
        assertEquals(List.of(DocumentType.CONTRACT), hints.documentTypes());
        assertEquals("GridBuild LLP", hints.counterparty());
        assertEquals("APPROVED", hints.businessStatus());
        assertEquals("Grid operations", hints.department());
    }

    @Test
    void extractsStandaloneDatesAndEnglishLanguageMarkers() {
        RetrievalQueryHints hints = extractor.extract(
            "Find revision 3 for project Alpha, on 2026-05-11 in english"
        );

        assertEquals("2026-05-11", hints.documentDateFrom().toString());
        assertEquals("2026-05-11", hints.documentDateTo().toString());
        assertEquals("revision 3", hints.versionLabel());
        assertEquals("en", hints.language());
        assertEquals("Alpha", hints.project());
        assertEquals(List.of(), hints.projectKeys());
    }

    @Test
    void doesNotInferUnsupportedSourceTrust() {
        RetrievalQueryHints hints = extractor.extract(
            "Найди самый надежный документ высокой достоверности по проекту North Upgrade"
        );

        assertNull(hints.documentNumber());
        assertNull(hints.versionLabel());
        assertNull(hints.counterparty());
    }
}
