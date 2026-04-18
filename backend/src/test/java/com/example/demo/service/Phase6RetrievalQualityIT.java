package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.infrastructure.material.ChunkProfile;
import com.example.demo.infrastructure.material.StoredEmbeddedMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.infrastructure.material.StoredMaterialSegment;
import com.example.demo.model.ChatSource;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryMaterialRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Phase6RetrievalQualityIT {

    @Test
    void writesPhase6HybridReportWithoutExactMatchRegressionAndWithChunkingWin() throws Exception {
        RetrievalPipeline baseline = createPipeline(ChunkProfile.FIXED_V1, RelevanceProfile.LEGACY);
        RetrievalPipeline candidate = createPipeline(ChunkProfile.SENTENCE_V1, RelevanceProfile.HYBRID_V1);

        List<QualityScenario> scenarios = seedQualityCorpus(baseline, candidate);
        List<QualityResult> results = new ArrayList<>();
        int baselineNoContext = 0;
        int candidateNoContext = 0;
        int chunkingWins = 0;

        for (QualityScenario scenario : scenarios) {
            MaterialRetrievalResult baselineResult = baseline.retrievalService().retrieveContext(scenario.query());
            MaterialRetrievalResult candidateResult = candidate.retrievalService().retrieveContext(scenario.query());

            if (baselineResult.sources().isEmpty()) {
                baselineNoContext += 1;
            }
            if (candidateResult.sources().isEmpty()) {
                candidateNoContext += 1;
            }

            boolean baselineHit = hitsExpectedMaterial(baselineResult, scenario.baselineMaterialId());
            boolean candidateHit = hitsExpectedMaterial(candidateResult, scenario.candidateMaterialId());
            boolean baselineHitAt1 = hitsExpectedMaterialWithinTopK(baselineResult, scenario.baselineMaterialId(), 1);
            boolean baselineHitAt3 = hitsExpectedMaterialWithinTopK(baselineResult, scenario.baselineMaterialId(), 3);
            boolean candidateHitAt1 = hitsExpectedMaterialWithinTopK(candidateResult, scenario.candidateMaterialId(), 1);
            boolean candidateHitAt3 = hitsExpectedMaterialWithinTopK(candidateResult, scenario.candidateMaterialId(), 3);
            int baselineCoverage = coverageScore(baselineResult.sources(), scenario.requiredNeedles());
            int candidateCoverage = coverageScore(candidateResult.sources(), scenario.requiredNeedles());
            String verdict;
            if (candidateCoverage > baselineCoverage) {
                verdict = "CANDIDATE_WIN";
            } else if (candidateCoverage < baselineCoverage) {
                verdict = "BASELINE_WIN";
            } else {
                verdict = "TIE";
            }

            if (scenario.chunkingSensitive() && candidateCoverage > baselineCoverage) {
                chunkingWins += 1;
            }
            if (scenario.exactMatchGuard()) {
                assertTrue(baselineHit, "Baseline should keep hitting exact-match scenario " + scenario.name());
                assertTrue(candidateHit, "Candidate should not regress on exact-match scenario " + scenario.name());
            }

            results.add(new QualityResult(
                scenario.name(),
                scenario.category(),
                scenario.query(),
                scenario.exactMatchGuard(),
                scenario.chunkingSensitive(),
                verdict,
                baselineCoverage,
                candidateCoverage,
                baselineHitAt1,
                baselineHitAt3,
                candidateHitAt1,
                candidateHitAt3,
                baselineResult.sources(),
                candidateResult.sources()
            ));
        }

        writeQualityReport(results, baselineNoContext, candidateNoContext);

        assertFalse(chunkingWins == 0, "Expected at least one chunking-sensitive win for sentence-v1 + hybrid-v1.");
        assertTrue(
            candidateNoContext <= baselineNoContext,
            "Sentence-v1 + hybrid-v1 increased no-context answers on the baseline corpus."
        );
    }

    private List<QualityScenario> seedQualityCorpus(RetrievalPipeline baseline, RetrievalPipeline candidate) {
        List<QualityScenario> scenarios = new ArrayList<>();

        scenarios.add(scenario(
            "ru-exact",
            "exact",
            "Когда запланирован ремонт подстанции?",
            List.of("май 2026"),
            true,
            false,
            baseline.seedTextMaterial(
                "RU exact",
                "Ремонт подстанции запланирован на май 2026 года.",
                "ru-lineage"
            ),
            candidate.seedTextMaterial(
                "RU exact",
                "Ремонт подстанции запланирован на май 2026 года.",
                "ru-lineage"
            )
        ));

        scenarios.add(scenario(
            "kz-exact",
            "exact",
            "Желі жүктемесі қалай жұмыс істейді?",
            List.of("жоспарлы режимде"),
            true,
            false,
            baseline.seedTextMaterial(
                "KZ exact",
                "Желі жүктемесі жоспарлы режимде жұмыс істейді.",
                "kz-lineage"
            ),
            candidate.seedTextMaterial(
                "KZ exact",
                "Желі жүктемесі жоспарлы режимде жұмыс істейді.",
                "kz-lineage"
            )
        ));

        scenarios.add(scenario(
            "en-exact",
            "exact",
            "What does premium tariff include?",
            List.of("backup dispatch channel"),
            true,
            false,
            baseline.seedTextMaterial(
                "EN exact",
                "Premium tariff includes backup dispatch channel.",
                "en-lineage"
            ),
            candidate.seedTextMaterial(
                "EN exact",
                "Premium tariff includes backup dispatch channel.",
                "en-lineage"
            )
        ));

        scenarios.add(scenario(
            "synonym",
            "multilingual",
            "электроэнергия по резервной линии",
            List.of("резервной линии"),
            false,
            false,
            baseline.seedTextMaterial(
                "Synonym",
                "Электричество подается стабильно по резервной линии.",
                "synonym-lineage"
            ),
            candidate.seedTextMaterial(
                "Synonym",
                "Электричество подается стабильно по резервной линии.",
                "synonym-lineage"
            )
        ));

        scenarios.add(scenario(
            "ocr-noisy",
            "ocr",
            "подстанция 110 кВ после ремонта",
            List.of("работает стабильно"),
            false,
            false,
            baseline.seedTextMaterial(
                "OCR noisy",
                "Подстаиция 110 кВ работает стабильно после ремонта.",
                "ocr-lineage"
            ),
            candidate.seedTextMaterial(
                "OCR noisy",
                "Подстаиция 110 кВ работает стабильно после ремонта.",
                "ocr-lineage"
            )
        ));

        String crossSentenceContent = String.join(
            " ",
            "Tariff Alpha is recommended for industrial clients.",
            "Backup dispatch support remains enabled.",
            "Monthly price is 12000 tenge after April 2026."
        );
        scenarios.add(scenario(
            "cross-sentence-fact",
            "structure",
            "How much does backup dispatch support cost?",
            List.of("dispatch support", "12000"),
            false,
            true,
            baseline.seedTextMaterial("Cross sentence", crossSentenceContent, "cross-sentence-lineage"),
            candidate.seedTextMaterial("Cross sentence", crossSentenceContent, "cross-sentence-lineage")
        ));

        String longParagraph = String.join(
            " ",
            "Regional dispatcher approves the outage window for the repair team.",
            "The approved outage window lasts 48 hours when transformer cooling remains stable.",
            "Field crews report back before energization resumes."
        );
        scenarios.add(scenario(
            "long-paragraph-answer",
            "structure",
            "Who approves the 48 hour outage window?",
            List.of("regional dispatcher", "48 hours"),
            false,
            true,
            baseline.seedTextMaterial("Long paragraph", longParagraph, "long-paragraph-lineage"),
            candidate.seedTextMaterial("Long paragraph", longParagraph, "long-paragraph-lineage")
        ));

        List<StoredMaterialChunk> legacyFileChunks = List.of(
            new StoredMaterialChunk(0, "Сканированный Premium тариф. Приоритетная поддержка доступна.", List.of(), 1, "ocr", true),
            new StoredMaterialChunk(1, "Стоимость составляет 15000 тенге после апреля 2026 года.", List.of(), 2, "ocr", true)
        );
        scenarios.add(scenario(
            "legacy-best-effort-file",
            "ocr",
            "Сколько стоит Premium тариф со сканированного файла?",
            List.of("15000"),
            false,
            true,
            baseline.seedLegacyFileMaterial("legacy-scan.pdf", legacyFileChunks, "legacy-file-lineage"),
            candidate.seedLegacyFileMaterial("legacy-scan.pdf", legacyFileChunks, "legacy-file-lineage")
        ));

        scenarios.add(scenario(
            "document-number",
            "identifier",
            "Что указано в договоре KZ-2026-0415-ENERGY?",
            List.of("KZ-2026-0415-ENERGY", "KazEnergy Service"),
            true,
            false,
            baseline.seedTextMaterial(
                "Contract register",
                "Договор KZ-2026-0415-ENERGY заключён с KazEnergy Service на обслуживание резервной линии.",
                "contract-number-lineage"
            ),
            candidate.seedTextMaterial(
                "Contract register",
                "Договор KZ-2026-0415-ENERGY заключён с KazEnergy Service на обслуживание резервной линии.",
                "contract-number-lineage"
            )
        ));

        scenarios.add(scenario(
            "date-range",
            "filter-shape",
            "Какой период действия регламента на весну 2026?",
            List.of("1 марта 2026", "31 мая 2026"),
            false,
            false,
            baseline.seedTextMaterial(
                "Spring regulation",
                "Период действия регламента: с 1 марта 2026 по 31 мая 2026. В это окно действует ускоренное согласование ремонтов.",
                "date-range-lineage"
            ),
            candidate.seedTextMaterial(
                "Spring regulation",
                "Период действия регламента: с 1 марта 2026 по 31 мая 2026. В это окно действует ускоренное согласование ремонтов.",
                "date-range-lineage"
            )
        ));

        scenarios.add(scenario(
            "department",
            "filter-shape",
            "Кто отвечает за первичную верификацию актов в подразделении релейной защиты?",
            List.of("релейной защиты", "первичную верификацию"),
            false,
            false,
            baseline.seedTextMaterial(
                "Department memo",
                "Подразделение релейной защиты отвечает за первичную верификацию актов перед передачей в диспетчерский контур.",
                "department-lineage"
            ),
            candidate.seedTextMaterial(
                "Department memo",
                "Подразделение релейной защиты отвечает за первичную верификацию актов перед передачей в диспетчерский контур.",
                "department-lineage"
            )
        ));

        scenarios.add(scenario(
            "project",
            "filter-shape",
            "Что предусматривает проект Северный Ветер?",
            List.of("резервный канал", "dispatch"),
            false,
            false,
            baseline.seedTextMaterial(
                "Project charter",
                "Проект Северный Ветер предусматривает резервный канал dispatch для изолированных подстанций северного кластера.",
                "project-lineage"
            ),
            candidate.seedTextMaterial(
                "Project charter",
                "Проект Северный Ветер предусматривает резервный канал dispatch для изолированных подстанций северного кластера.",
                "project-lineage"
            )
        ));

        scenarios.add(scenario(
            "counterparty",
            "filter-shape",
            "Что поставляет контрагент GridBuild LLP?",
            List.of("GridBuild LLP", "кабельные муфты"),
            false,
            false,
            baseline.seedTextMaterial(
                "Counterparty note",
                "Контрагент GridBuild LLP поставляет кабельные муфты и комплект крепежа для аварийного запаса.",
                "counterparty-lineage"
            ),
            candidate.seedTextMaterial(
                "Counterparty note",
                "Контрагент GridBuild LLP поставляет кабельные муфты и комплект крепежа для аварийного запаса.",
                "counterparty-lineage"
            )
        ));

        scenarios.add(scenario(
            "business-status",
            "filter-shape",
            "Какой статус у версии 3.2 регламента подключения?",
            List.of("согласовано", "3.2"),
            false,
            false,
            baseline.seedTextMaterial(
                "Status bulletin",
                "Регламент подключения, версия 3.2. Статус документа: согласовано и готово к публикации.",
                "business-status-lineage"
            ),
            candidate.seedTextMaterial(
                "Status bulletin",
                "Регламент подключения, версия 3.2. Статус документа: согласовано и готово к публикации.",
                "business-status-lineage"
            )
        ));

        String tableContent = String.join(
            "\n",
            "Таблица тарифов 2026",
            "Тариф | Лимит | Цена",
            "Alpha | 50 МВт | 12000 тенге",
            "Bravo | 75 МВт | 18000 тенге",
            "Charlie | 100 МВт | 25000 тенге"
        );
        scenarios.add(scenario(
            "table-layout",
            "structure",
            "Какой лимит у тарифа Bravo?",
            List.of("Bravo", "75 МВт"),
            false,
            true,
            baseline.seedTextMaterial("Tariff table", tableContent, "table-lineage"),
            candidate.seedTextMaterial("Tariff table", tableContent, "table-lineage")
        ));

        String presentationContent = String.join(
            "\n",
            "Slide 1. Crisis response",
            "- Trigger: frequency drop below threshold",
            "- Owner: National dispatch center",
            "- Escalation channel: reserve bridge"
        );
        scenarios.add(scenario(
            "presentation-bullets",
            "structure",
            "Who owns the frequency drop response in the presentation?",
            List.of("National dispatch center"),
            false,
            true,
            baseline.seedTextMaterial("Ops deck", presentationContent, "presentation-lineage"),
            candidate.seedTextMaterial("Ops deck", presentationContent, "presentation-lineage")
        ));

        String appendixContent = String.join(
            "\n",
            "Основной раздел. Действующая ставка по ускоренному обслуживанию составляет 12000 тенге.",
            "Приложение А. Архивная ставка до 2024 года составляла 9000 тенге и больше не применяется."
        );
        scenarios.add(scenario(
            "appendix-noise",
            "structure",
            "Какая действующая ставка по ускоренному обслуживанию?",
            List.of("12000"),
            false,
            true,
            baseline.seedTextMaterial("Appendix note", appendixContent, "appendix-lineage"),
            candidate.seedTextMaterial("Appendix note", appendixContent, "appendix-lineage")
        ));

        scenarios.add(scenario(
            "multilingual-abbreviation",
            "multilingual",
            "Кто владелец SCADA gateway на ПС-17?",
            List.of("SCADA gateway", "Regional telemetry team"),
            false,
            false,
            baseline.seedTextMaterial(
                "Telemetry memo",
                "SCADA gateway на ПС-17 обслуживает Regional telemetry team и дежурный инженер связи.",
                "multilingual-lineage"
            ),
            candidate.seedTextMaterial(
                "Telemetry memo",
                "SCADA gateway на ПС-17 обслуживает Regional telemetry team и дежурный инженер связи.",
                "multilingual-lineage"
            )
        ));

        return List.copyOf(scenarios);
    }

    private RetrievalPipeline createPipeline(ChunkProfile chunkProfile, RelevanceProfile relevanceProfile) {
        MaterialProperties materialProperties = new MaterialProperties();
        materialProperties.setChunkProfile(chunkProfile.propertyValue());
        materialProperties.setChunkSize(88);
        materialProperties.setChunkOverlap(45);
        materialProperties.setMaxChunks(12);
        MaterialContentSupport contentSupport = new MaterialContentSupport(materialProperties);

        RagProperties ragProperties = new RagProperties();
        ragProperties.setRelevanceProfile(relevanceProfile.propertyValue());

        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
        MaterialRetrievalService retrievalService = new MaterialRetrievalService(
            repository,
            repository,
            new LexicalSearchStrategy(List.of(repository), ragProperties),
            embeddingClient,
            ragProperties,
            new HybridChunkRanker(),
            contentSupport
        );

        return new RetrievalPipeline(repository, contentSupport, embeddingClient, retrievalService, chunkProfile);
    }

    private boolean hitsExpectedMaterial(MaterialRetrievalResult result, String materialId) {
        return result.sources().stream().anyMatch(source -> materialId.equals(source.materialId()));
    }

    private boolean hitsExpectedMaterialWithinTopK(MaterialRetrievalResult result, String materialId, int limit) {
        if (result == null || limit <= 0) {
            return false;
        }
        return result.sources().stream()
            .limit(limit)
            .anyMatch(source -> materialId.equals(source.materialId()));
    }

    private int coverageScore(List<ChatSource> sources, List<String> requiredNeedles) {
        if (sources == null || sources.isEmpty()) {
            return 0;
        }
        ChatSource source = sources.getFirst();
        return (int) requiredNeedles.stream()
            .filter(needle -> containsIgnoreCase(source.excerpt(), needle))
            .count();
    }

    private boolean containsIgnoreCase(String text, String fragment) {
        return text != null
            && fragment != null
            && text.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private void writeQualityReport(
        List<QualityResult> results,
        int baselineNoContext,
        int candidateNoContext
    ) throws Exception {
        Path reportDirectory = Path.of("target", "search-quality");
        Files.createDirectories(reportDirectory);
        Path baselineReportPath = reportDirectory.resolve("phase0-baseline-report.md");
        Path legacyReportPath = reportDirectory.resolve("phase6-hybrid-report.md");

        StringBuilder report = new StringBuilder()
            .append("# Phase 0 RAG Quality Baseline Report\n\n")
            .append("| Category | Scenario | Exact guard | Chunking-sensitive | Verdict | Baseline top-hit coverage | Candidate top-hit coverage | Baseline hit@1 | Baseline hit@3 | Candidate hit@1 | Candidate hit@3 | Baseline top hit | Candidate top hit |\n")
            .append("|---|---|---|---|---|---:|---:|---|---|---|---|---|---|\n");

        for (QualityResult result : results) {
            report.append("| ")
                .append(result.category())
                .append(" | ")
                .append(result.name())
                .append(" | ")
                .append(result.exactMatchGuard() ? "yes" : "no")
                .append(" | ")
                .append(result.chunkingSensitive() ? "yes" : "no")
                .append(" | ")
                .append(result.verdict())
                .append(" | ")
                .append(result.baselineCoverage())
                .append(" | ")
                .append(result.candidateCoverage())
                .append(" | ")
                .append(result.baselineHitAt1() ? "yes" : "no")
                .append(" | ")
                .append(result.baselineHitAt3() ? "yes" : "no")
                .append(" | ")
                .append(result.candidateHitAt1() ? "yes" : "no")
                .append(" | ")
                .append(result.candidateHitAt3() ? "yes" : "no")
                .append(" | ")
                .append(renderTopHit(result.baselineSources()))
                .append(" | ")
                .append(renderTopHit(result.candidateSources()))
                .append(" |\n");
        }

        report.append("\n")
            .append("## Summary\n\n")
            .append("- Total scenarios: ").append(results.size()).append("\n")
            .append("- Baseline no-context count: ").append(baselineNoContext).append("\n")
            .append("- Candidate no-context count: ").append(candidateNoContext).append("\n")
            .append("- Candidate wins: ").append(results.stream().filter(result -> "CANDIDATE_WIN".equals(result.verdict())).count()).append("\n")
            .append("- Baseline wins: ").append(results.stream().filter(result -> "BASELINE_WIN".equals(result.verdict())).count()).append("\n")
            .append("- Ties: ").append(results.stream().filter(result -> "TIE".equals(result.verdict())).count()).append("\n")
            .append("\n")
            .append("## Coverage Groups\n\n");

        results.stream()
            .map(QualityResult::category)
            .distinct()
            .forEach(category -> report.append("- ")
                .append(category)
                .append(": ")
                .append(results.stream().filter(result -> category.equals(result.category())).count())
                .append(" scenarios\n"));

        report.append("\n")
            .append("- Baseline no-context count: ").append(baselineNoContext).append("\n")
            .append("- Candidate no-context count: ").append(candidateNoContext).append("\n");

        Files.writeString(baselineReportPath, report.toString());
        Files.writeString(legacyReportPath, report.toString());
    }

    private String renderTopHit(List<ChatSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return "none";
        }
        ChatSource source = sources.getFirst();
        return source.title() + ": " + source.excerpt().replace("|", "\\|");
    }

    private QualityScenario scenario(
        String name,
        String category,
        String query,
        List<String> requiredNeedles,
        boolean exactMatchGuard,
        boolean chunkingSensitive,
        String baselineMaterialId,
        String candidateMaterialId
    ) {
        return new QualityScenario(
            name,
            category,
            query,
            requiredNeedles,
            exactMatchGuard,
            chunkingSensitive,
            baselineMaterialId,
            candidateMaterialId
        );
    }

    private record QualityScenario(
        String name,
        String category,
        String query,
        List<String> requiredNeedles,
        boolean exactMatchGuard,
        boolean chunkingSensitive,
        String baselineMaterialId,
        String candidateMaterialId
    ) {
    }

    private record QualityResult(
        String name,
        String category,
        String query,
        boolean exactMatchGuard,
        boolean chunkingSensitive,
        String verdict,
        int baselineCoverage,
        int candidateCoverage,
        boolean baselineHitAt1,
        boolean baselineHitAt3,
        boolean candidateHitAt1,
        boolean candidateHitAt3,
        List<ChatSource> baselineSources,
        List<ChatSource> candidateSources
    ) {
    }

    private record RetrievalPipeline(
        InMemoryMaterialRepository repository,
        MaterialContentSupport contentSupport,
        DeterministicEmbeddingClient embeddingClient,
        MaterialRetrievalService retrievalService,
        ChunkProfile chunkProfile
    ) {
        private String seedTextMaterial(String title, String content, String sourceKey) {
            Instant now = Instant.parse("2026-04-17T10:00:00Z");
            List<StoredMaterialSegment> segments = List.of(
                new StoredMaterialSegment(0, content, 1, "direct-text", false)
            );
            List<StoredMaterialChunk> rawChunks = contentSupport.buildChunks(segments, chunkProfile);
            StoredMaterialRecord record = new StoredMaterialRecord(
                UUID.randomUUID().toString(),
                title,
                "text",
                null,
                "text/plain",
                content,
                contentSupport.normalizeForHash(content),
                UUID.randomUUID().toString(),
                sourceKey,
                "direct-text",
                false,
                1,
                rawChunks,
                MaterialIndexingStatus.READY,
                MaterialVersionState.ACTIVE,
                null,
                null,
                now,
                now
            );
            repository.save(record, chunkProfile.propertyValue(), rawChunks, segments);
            repository.markIndexingReady(
                record.id(),
                embed(rawChunks),
                MaterialIndexingStatus.READY,
                null,
                null,
                now
            );
            return record.id();
        }

        private String seedLegacyFileMaterial(
            String originalFileName,
            List<StoredMaterialChunk> legacyChunks,
            String sourceKey
        ) {
            Instant now = Instant.parse("2026-04-17T10:00:00Z");
            String content = legacyChunks.stream().map(StoredMaterialChunk::text).reduce((left, right) -> left + "\n\n" + right).orElse("");
            List<StoredMaterialSegment> segments = chunkProfile == ChunkProfile.SENTENCE_V1
                ? contentSupport.pseudoSegmentsFromChunks(legacyChunks, "ocr", true)
                : List.of();
            List<StoredMaterialChunk> rawChunks = chunkProfile == ChunkProfile.SENTENCE_V1
                ? contentSupport.buildChunks(segments, chunkProfile)
                : legacyChunks;
            StoredMaterialRecord record = new StoredMaterialRecord(
                UUID.randomUUID().toString(),
                originalFileName,
                "file",
                originalFileName,
                "application/pdf",
                content,
                contentSupport.normalizeForHash(content),
                UUID.randomUUID().toString(),
                sourceKey,
                "ocr",
                true,
                2,
                rawChunks,
                MaterialIndexingStatus.READY,
                MaterialVersionState.ACTIVE,
                null,
                null,
                now,
                now
            );
            repository.save(record, chunkProfile.propertyValue(), rawChunks, segments);
            repository.markIndexingReady(
                record.id(),
                embed(rawChunks),
                MaterialIndexingStatus.READY,
                null,
                null,
                now
            );
            return record.id();
        }

        private List<StoredEmbeddedMaterialChunk> embed(List<StoredMaterialChunk> rawChunks) {
            List<float[]> embeddings = embeddingClient.embedAll(rawChunks.stream().map(StoredMaterialChunk::text).toList());
            List<StoredEmbeddedMaterialChunk> embedded = new ArrayList<>();
            for (int index = 0; index < rawChunks.size(); index++) {
                StoredMaterialChunk chunk = rawChunks.get(index);
                embedded.add(new StoredEmbeddedMaterialChunk(
                    chunk.index(),
                    chunk.text(),
                    chunk.page(),
                    chunk.extractor(),
                    Boolean.TRUE.equals(chunk.ocrUsed()),
                    embeddings.get(index)
                ));
            }
            return embedded;
        }
    }
}
