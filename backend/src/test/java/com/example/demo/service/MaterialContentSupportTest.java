package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialContentSupportTest {

    private final MaterialContentSupport contentSupport = new MaterialContentSupport(new MaterialProperties());

    @Test
    void keepsTextMaterialsInSameLineageWhenExplicitTitleStaysTheSame() {
        StoredMaterialRecord existing = storedRecord(
            "Pricing FAQ",
            "text",
            null,
            "Старая редакция тарифа 9000 тенге.",
            "pricing-lineage"
        );

        MaterialContentSupport.MaterialLineageIdentity candidate = contentSupport.buildLineageIdentity(
            "text",
            "Pricing FAQ",
            null,
            "Новая редакция тарифа 12000 тенге."
        );

        assertTrue(contentSupport.matchesLineage(existing, candidate));
    }

    @Test
    void doesNotMergeFallbackTextTitleForUnrelatedContent() {
        StoredMaterialRecord existing = storedRecord(
            "Text material",
            "text",
            null,
            "Регламент технического обслуживания подстанции.",
            "text-fallback-lineage"
        );

        MaterialContentSupport.MaterialLineageIdentity candidate = contentSupport.buildLineageIdentity(
            "text",
            null,
            null,
            "Политика закупки оборудования и бюджетирования."
        );

        assertFalse(contentSupport.matchesLineage(existing, candidate));
    }

    @Test
    void doesNotMergeFilesWithSameNameWhenContentAnchorDiffers() {
        StoredMaterialRecord existing = storedRecord(
            "brief.txt",
            "file",
            "brief.txt",
            "График ремонта подстанции на май и июнь.",
            "brief-lineage"
        );

        MaterialContentSupport.MaterialLineageIdentity candidate = contentSupport.buildLineageIdentity(
            "file",
            null,
            "brief.txt",
            "Порядок закупки трансформаторов и кабеля."
        );

        assertFalse(contentSupport.matchesLineage(existing, candidate));
    }

    @Test
    void doesNotMergeFileLineageWhenExplicitTitlesDifferEvenIfFilenameAndAnchorOverlap() {
        StoredMaterialRecord existing = storedRecord(
            "Ремонтный план 2026",
            "file",
            "brief.txt",
            "план ремонта подстанции север май июнь июль август сентябрь октябрь ноябрь декабрь версия один",
            "repair-plan-lineage"
        );

        MaterialContentSupport.MaterialLineageIdentity candidate = contentSupport.buildLineageIdentity(
            "file",
            "Операционный план 2026",
            "brief.txt",
            "план ремонта подстанции север май июнь июль август сентябрь октябрь ноябрь декабрь версия два"
        );

        assertFalse(contentSupport.matchesLineage(existing, candidate));
    }

    private StoredMaterialRecord storedRecord(
        String title,
        String sourceType,
        String originalFileName,
        String content,
        String sourceKey
    ) {
        Instant timestamp = Instant.parse("2026-04-17T10:00:00Z");
        return new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            title,
            sourceType,
            originalFileName,
            "text/plain",
            content,
            contentSupport.normalizeForHash(content),
            contentSupport.sha256(contentSupport.normalizeForHash(content)),
            sourceKey,
            "direct-text",
            false,
            null,
            List.of(),
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            null,
            null,
            timestamp,
            timestamp
        );
    }
}
