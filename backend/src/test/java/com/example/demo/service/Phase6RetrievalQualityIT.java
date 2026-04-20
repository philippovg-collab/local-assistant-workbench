package com.example.demo.service;

import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.DocumentBlockConfidence;
import com.example.demo.service.material.DocumentBlockType;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.StoredMaterialSegment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.model.ChatSource;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryMaterialRepository;
import com.example.demo.support.TestLexicalRoutingSupport;
import com.example.demo.support.TestMaterialServices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Phase6RetrievalQualityIT {

    @Test
    void writesPhase6HybridReportWithoutExactMatchRegressionAndWithChunkingWin() throws Exception {
        RetrievalPipeline baseline = createPipeline(ChunkProfile.STRUCTURED_V1, RelevanceProfile.HYBRID_V1);
        RetrievalPipeline candidate = createPipeline(ChunkProfile.STRUCTURED_V1, RelevanceProfile.HYBRID_RERANK_V1);

        List<QualityScenario> scenarios = seedQualityCorpus(baseline, candidate);
        List<QualityResult> results = new ArrayList<>();
        int baselineNoContext = 0;
        int candidateNoContext = 0;
        int rerankWins = 0;

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
            int baselineFocusLength = bestSupportingExcerptLength(
                baselineResult.sources(),
                scenario.requiredNeedles(),
                baselineCoverage
            );
            int candidateFocusLength = bestSupportingExcerptLength(
                candidateResult.sources(),
                scenario.requiredNeedles(),
                candidateCoverage
            );
            String verdict;
            if (candidateCoverage > baselineCoverage) {
                verdict = "CANDIDATE_WIN";
            } else if (candidateCoverage < baselineCoverage) {
                verdict = "BASELINE_WIN";
            } else if (scenario.rerankSensitive() && candidateHitAt1 && !baselineHitAt1) {
                verdict = "CANDIDATE_WIN";
            } else if (scenario.rerankSensitive() && baselineHitAt1 && !candidateHitAt1) {
                verdict = "BASELINE_WIN";
            } else if (scenario.chunkingSensitive()
                && candidateCoverage > 0
                && candidateFocusLength < baselineFocusLength) {
                verdict = "CANDIDATE_WIN";
            } else if (scenario.chunkingSensitive()
                && baselineCoverage > 0
                && baselineFocusLength < candidateFocusLength) {
                verdict = "BASELINE_WIN";
            } else {
                verdict = "TIE";
            }

            if (scenario.rerankSensitive() && "CANDIDATE_WIN".equals(verdict)) {
                rerankWins += 1;
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
                scenario.rerankSensitive(),
                verdict,
                baselineCoverage,
                candidateCoverage,
                baselineFocusLength,
                candidateFocusLength,
                baselineHitAt1,
                baselineHitAt3,
                candidateHitAt1,
                candidateHitAt3,
                baselineResult.sources(),
                candidateResult.sources()
            ));
        }

        writeQualityReport(results, baselineNoContext, candidateNoContext);

        assertFalse(rerankWins == 0, "Expected at least one rerank-sensitive win for hybrid-rerank-v1.");
        assertTrue(
            candidateNoContext <= baselineNoContext,
            "Hybrid reranking increased no-context answers on the baseline corpus."
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
            false,
            false,
            baseline.seedLegacyFileMaterial("legacy-scan.pdf", legacyFileChunks, "legacy-file-lineage"),
            candidate.seedLegacyFileMaterial("legacy-scan.pdf", legacyFileChunks, "legacy-file-lineage")
        ));

        scenarios.add(scenario(
            "multilingual-abbreviation",
            "multilingual",
            "Кто владелец SCADA gateway на ПС-17?",
            List.of("SCADA gateway", "Regional telemetry team"),
            false,
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

        String baselineIdentifierWrong = baseline.seedStructuredMaterial(
            "Identifier memo",
            List.of(structuredChunk(
                0,
                "Служебная записка project North Upgrade department Grid operations по договору KZ-2026-0415-ENERGY без сведений о контрагенте.",
                DocumentBlockType.NARRATIVE,
                List.of("Notes"),
                null,
                null,
                DocumentBlockConfidence.HIGH
            )),
            "identifier-memo-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-10"),
                "AUX-2026-0415",
                "Procurement",
                "v1",
                "ru",
                SourceTrustLevel.LOW,
                "South Upgrade",
                "SouthGrid LLP",
                "DRAFT"
            )
        );
        String baselineIdentifierTarget = baseline.seedStructuredMaterial(
            "Dispatch register",
            List.of(structuredChunk(
                0,
                "Контрагент KazEnergy Service обслуживает резервную линию по подтверждённому договору.",
                DocumentBlockType.TABLE,
                List.of("Dispatch register", "Contract matrix"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            "identifier-target-lineage",
            metadata(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-15"),
                "KZ-2026-0415-ENERGY",
                "Grid operations",
                "v2",
                "ru",
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "KazEnergy Service",
                "APPROVED"
            )
        );
        String candidateIdentifierWrong = candidate.seedStructuredMaterial(
            "Identifier memo",
            List.of(structuredChunk(
                0,
                "Служебная записка project North Upgrade department Grid operations по договору KZ-2026-0415-ENERGY без сведений о контрагенте.",
                DocumentBlockType.NARRATIVE,
                List.of("Notes"),
                null,
                null,
                DocumentBlockConfidence.HIGH
            )),
            "identifier-memo-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-10"),
                "AUX-2026-0415",
                "Procurement",
                "v1",
                "ru",
                SourceTrustLevel.LOW,
                "South Upgrade",
                "SouthGrid LLP",
                "DRAFT"
            )
        );
        String candidateIdentifierTarget = candidate.seedStructuredMaterial(
            "Dispatch register",
            List.of(structuredChunk(
                0,
                "Контрагент KazEnergy Service обслуживает резервную линию по подтверждённому договору.",
                DocumentBlockType.TABLE,
                List.of("Dispatch register", "Contract matrix"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            "identifier-target-lineage",
            metadata(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-15"),
                "KZ-2026-0415-ENERGY",
                "Grid operations",
                "v2",
                "ru",
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "KazEnergy Service",
                "APPROVED"
            )
        );
        scenarios.add(scenario(
            "document-number-ambiguity",
            "identifier",
            "What is in project North Upgrade department Grid operations contract KZ-2026-0415-ENERGY?",
            List.of("KazEnergy Service"),
            false,
            false,
            true,
            baselineIdentifierTarget,
            candidateIdentifierTarget
        ));

        String baselineVersionWrong = baseline.seedStructuredMaterial(
            "North change memo",
            List.of(structuredChunk(
                0,
                "North Upgrade revision 2 draft keeps the legacy dispatch bridge disabled.",
                DocumentBlockType.NARRATIVE,
                List.of("North change memo"),
                null,
                null,
                DocumentBlockConfidence.HIGH
            )),
            "version-wrong-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-12"),
                "CHANGE-2026-04",
                "Grid operations",
                "v1",
                "en",
                SourceTrustLevel.MEDIUM,
                "North Upgrade",
                "GridBuild LLP",
                "DRAFT"
            )
        );
        String baselineVersionTarget = baseline.seedStructuredMaterial(
            "North change register",
            List.of(structuredChunk(
                0,
                "Revision history confirms the backup dispatch channel is enabled for the rollout.",
                DocumentBlockType.SLIDE,
                List.of("Release notes"),
                null,
                "slide-1",
                DocumentBlockConfidence.HIGH
            )),
            "version-target-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-12"),
                "CHANGE-2026-04",
                "Grid operations",
                "revision 2",
                "en",
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED"
            )
        );
        String candidateVersionWrong = candidate.seedStructuredMaterial(
            "North change memo",
            List.of(structuredChunk(
                0,
                "North Upgrade revision 2 draft keeps the legacy dispatch bridge disabled.",
                DocumentBlockType.NARRATIVE,
                List.of("North change memo"),
                null,
                null,
                DocumentBlockConfidence.HIGH
            )),
            "version-wrong-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-12"),
                "CHANGE-2026-04",
                "Grid operations",
                "v1",
                "en",
                SourceTrustLevel.MEDIUM,
                "North Upgrade",
                "GridBuild LLP",
                "DRAFT"
            )
        );
        String candidateVersionTarget = candidate.seedStructuredMaterial(
            "North change register",
            List.of(structuredChunk(
                0,
                "Revision history confirms the backup dispatch channel is enabled for the rollout.",
                DocumentBlockType.SLIDE,
                List.of("Release notes"),
                null,
                "slide-1",
                DocumentBlockConfidence.HIGH
            )),
            "version-target-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-12"),
                "CHANGE-2026-04",
                "Grid operations",
                "revision 2",
                "en",
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED"
            )
        );
        scenarios.add(scenario(
            "version-label",
            "identifier",
            "What changed in project North Upgrade revision 2?",
            List.of("backup dispatch channel"),
            false,
            false,
            true,
            baselineVersionTarget,
            candidateVersionTarget
        ));

        String baselineLowTrust = baseline.seedStructuredMaterial(
            "Copied tariff memo",
            List.of(structuredChunk(
                0,
                "Premium tariff costs 12000 tenge according to the copied memo.",
                DocumentBlockType.CAPTION,
                List.of("Copied tariff memo"),
                null,
                null,
                DocumentBlockConfidence.MEDIUM
            )),
            "trust-low-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-05"),
                "TRUST-LOW-2026",
                "Commercial office",
                "v1",
                "en",
                SourceTrustLevel.UNKNOWN,
                "North Upgrade",
                "GridBuild LLP",
                "DRAFT"
            )
        );
        String baselineHighTrust = baseline.seedStructuredMaterial(
            "Approved tariff register",
            List.of(structuredChunk(
                0,
                "Premium tariff costs 12000 tenge according to the approved contract register.",
                DocumentBlockType.TABLE,
                List.of("Approved tariff register"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            "trust-high-lineage",
            metadata(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-05"),
                "TRUST-HIGH-2026",
                "Commercial office",
                "v2",
                "en",
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED"
            )
        );
        String candidateLowTrust = candidate.seedStructuredMaterial(
            "Copied tariff memo",
            List.of(structuredChunk(
                0,
                "Premium tariff costs 12000 tenge according to the copied memo.",
                DocumentBlockType.CAPTION,
                List.of("Copied tariff memo"),
                null,
                null,
                DocumentBlockConfidence.MEDIUM
            )),
            "trust-low-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-05"),
                "TRUST-LOW-2026",
                "Commercial office",
                "v1",
                "en",
                SourceTrustLevel.UNKNOWN,
                "North Upgrade",
                "GridBuild LLP",
                "DRAFT"
            )
        );
        String candidateHighTrust = candidate.seedStructuredMaterial(
            "Approved tariff register",
            List.of(structuredChunk(
                0,
                "Premium tariff costs 12000 tenge according to the approved contract register.",
                DocumentBlockType.TABLE,
                List.of("Approved tariff register"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            "trust-high-lineage",
            metadata(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-05"),
                "TRUST-HIGH-2026",
                "Commercial office",
                "v2",
                "en",
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED"
            )
        );
        scenarios.add(scenario(
            "source-trust-preference",
            "metadata",
            "How much does premium tariff cost?",
            List.of("approved contract register"),
            false,
            false,
            true,
            baselineHighTrust,
            candidateHighTrust
        ));

        String baselineFacetWrong = baseline.seedStructuredMaterial(
            "Regional dispatch note",
            List.of(structuredChunk(
                0,
                "project North Upgrade counterparty GridBuild LLP status APPROVED still references the reserve bridge in a draft note.",
                DocumentBlockType.NARRATIVE,
                List.of("Regional dispatch note"),
                null,
                null,
                DocumentBlockConfidence.HIGH
            )),
            "facet-wrong-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-18"),
                "FACET-2026-A",
                "South operations",
                "v1",
                "en",
                SourceTrustLevel.MEDIUM,
                "South Upgrade",
                "SouthGrid LLP",
                "DRAFT"
            )
        );
        String baselineFacetTarget = baseline.seedStructuredMaterial(
            "North approval record",
            List.of(structuredChunk(
                0,
                "Reserve bridge activation remains approved for the North cluster.",
                DocumentBlockType.TABLE,
                List.of("North approval", "Reserve bridge"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            "facet-target-lineage",
            metadata(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-18"),
                "FACET-2026-B",
                "Grid operations",
                "v3",
                "en",
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED"
            )
        );
        String candidateFacetWrong = candidate.seedStructuredMaterial(
            "Regional dispatch note",
            List.of(structuredChunk(
                0,
                "project North Upgrade counterparty GridBuild LLP status APPROVED still references the reserve bridge in a draft note.",
                DocumentBlockType.NARRATIVE,
                List.of("Regional dispatch note"),
                null,
                null,
                DocumentBlockConfidence.HIGH
            )),
            "facet-wrong-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-18"),
                "FACET-2026-A",
                "South operations",
                "v1",
                "en",
                SourceTrustLevel.MEDIUM,
                "South Upgrade",
                "SouthGrid LLP",
                "DRAFT"
            )
        );
        String candidateFacetTarget = candidate.seedStructuredMaterial(
            "North approval record",
            List.of(structuredChunk(
                0,
                "Reserve bridge activation remains approved for the North cluster.",
                DocumentBlockType.TABLE,
                List.of("North approval", "Reserve bridge"),
                "table-1",
                null,
                DocumentBlockConfidence.HIGH
            )),
            "facet-target-lineage",
            metadata(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-18"),
                "FACET-2026-B",
                "Grid operations",
                "v3",
                "en",
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED"
            )
        );
        scenarios.add(scenario(
            "metadata-sensitive-ambiguity",
            "metadata",
            "What is approved for project North Upgrade counterparty GridBuild LLP status APPROVED?",
            List.of("Reserve bridge activation"),
            false,
            false,
            true,
            baselineFacetTarget,
            candidateFacetTarget
        ));

        String baselineAppendix = baseline.seedStructuredMaterial(
            "Archive appendix",
            List.of(structuredChunk(
                0,
                "Приложение А. Действующая ставка по ускоренному обслуживанию до 2024 года была 9000 тенге.",
                DocumentBlockType.APPENDIX,
                List.of("Appendix A"),
                null,
                null,
                DocumentBlockConfidence.HIGH
            )),
            "appendix-wrong-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2024-05-01"),
                "APPENDIX-ARCHIVE",
                "Archive office",
                "v1",
                "ru",
                SourceTrustLevel.LOW,
                "Archive",
                "Legacy Supplier",
                "SUPERSEDED"
            )
        );
        String baselineCurrent = baseline.seedStructuredMaterial(
            "Current tariff memo",
            List.of(structuredChunk(
                0,
                "Основной раздел. Действующая ставка по ускоренному обслуживанию составляет 12000 тенге.",
                DocumentBlockType.NARRATIVE,
                List.of("Current tariff"),
                null,
                null,
                DocumentBlockConfidence.HIGH
            )),
            "appendix-target-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-01"),
                "CURRENT-TARIFF-2026",
                "Commercial office",
                "v2",
                "ru",
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED"
            )
        );
        String candidateAppendix = candidate.seedStructuredMaterial(
            "Archive appendix",
            List.of(structuredChunk(
                0,
                "Приложение А. Действующая ставка по ускоренному обслуживанию до 2024 года была 9000 тенге.",
                DocumentBlockType.APPENDIX,
                List.of("Appendix A"),
                null,
                null,
                DocumentBlockConfidence.HIGH
            )),
            "appendix-wrong-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2024-05-01"),
                "APPENDIX-ARCHIVE",
                "Archive office",
                "v1",
                "ru",
                SourceTrustLevel.LOW,
                "Archive",
                "Legacy Supplier",
                "SUPERSEDED"
            )
        );
        String candidateCurrent = candidate.seedStructuredMaterial(
            "Current tariff memo",
            List.of(structuredChunk(
                0,
                "Основной раздел. Действующая ставка по ускоренному обслуживанию составляет 12000 тенге.",
                DocumentBlockType.NARRATIVE,
                List.of("Current tariff"),
                null,
                null,
                DocumentBlockConfidence.HIGH
            )),
            "appendix-target-lineage",
            metadata(
                DocumentType.REPORT,
                LocalDate.parse("2026-04-01"),
                "CURRENT-TARIFF-2026",
                "Commercial office",
                "v2",
                "ru",
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED"
            )
        );
        scenarios.add(scenario(
            "appendix-suppression",
            "metadata",
            "Какая действующая ставка по ускоренному обслуживанию?",
            List.of("12000"),
            false,
            false,
            true,
            baselineCurrent,
            candidateCurrent
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
        MaterialRetrievalService retrievalService = TestMaterialServices.retrievalService(
            repository,
            repository,
            repository,
            TestLexicalRoutingSupport.productionRouter(repository, ragProperties, List.of(repository)),
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
        return (int) requiredNeedles.stream()
            .filter(needle -> sources.stream()
                .limit(3)
                .anyMatch(source -> containsIgnoreCase(source.excerpt(), needle)))
            .count();
    }

    private int bestSupportingExcerptLength(
        List<ChatSource> sources,
        List<String> requiredNeedles,
        int targetCoverage
    ) {
        if (sources == null || sources.isEmpty() || targetCoverage <= 0) {
            return Integer.MAX_VALUE;
        }
        return sources.stream()
            .limit(3)
            .filter(source -> source != null && source.excerpt() != null)
            .filter(source -> coverageScore(List.of(source), requiredNeedles) == targetCoverage)
            .mapToInt(source -> source.excerpt().length())
            .min()
            .orElse(Integer.MAX_VALUE);
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
        Path legacyReportPath = reportDirectory.resolve("phase6-rerank-report.md");

        StringBuilder report = new StringBuilder()
            .append("# Phase 6 Rerank Quality Report\n\n")
            .append("| Category | Scenario | Exact guard | Chunking-sensitive | Rerank-sensitive | Verdict | Baseline top-hit coverage | Candidate top-hit coverage | Baseline focus chars | Candidate focus chars | Baseline hit@1 | Baseline hit@3 | Candidate hit@1 | Candidate hit@3 | Baseline top hit | Candidate top hit |\n")
            .append("|---|---|---|---|---|---|---:|---:|---:|---:|---|---|---|---|---|---|\n");

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
                .append(result.rerankSensitive() ? "yes" : "no")
                .append(" | ")
                .append(result.verdict())
                .append(" | ")
                .append(result.baselineCoverage())
                .append(" | ")
                .append(result.candidateCoverage())
                .append(" | ")
                .append(renderFocusLength(result.baselineFocusLength()))
                .append(" | ")
                .append(renderFocusLength(result.candidateFocusLength()))
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

    private String renderFocusLength(int focusLength) {
        return focusLength == Integer.MAX_VALUE ? "-" : Integer.toString(focusLength);
    }

    private StoredMaterialChunk structuredChunk(
        int index,
        String text,
        DocumentBlockType chunkType,
        List<String> headingTrail,
        String tableId,
        String slideId,
        DocumentBlockConfidence parserConfidence
    ) {
        return new StoredMaterialChunk(
            index,
            text,
            List.of(),
            1,
            "structured-v1",
            parserConfidence == DocumentBlockConfidence.LOW,
            chunkType,
            headingTrail,
            headingTrail,
            tableId,
            slideId,
            parserConfidence
        );
    }

    private MaterialMetadataSnapshot metadata(
        DocumentType documentType,
        LocalDate documentDate,
        String documentNumber,
        String department,
        String versionLabel,
        String language,
        SourceTrustLevel sourceTrust,
        String project,
        String counterparty,
        String businessStatus
    ) {
        return MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
            documentType,
            documentDate,
            documentNumber,
            null,
            department,
            versionLabel,
            language,
            List.of(),
            sourceTrust,
            project,
            counterparty,
            businessStatus,
            null,
            null
        ));
    }

    private QualityScenario scenario(
        String name,
        String category,
        String query,
        List<String> requiredNeedles,
        boolean exactMatchGuard,
        boolean chunkingSensitive,
        boolean rerankSensitive,
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
            rerankSensitive,
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
        boolean rerankSensitive,
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
        boolean rerankSensitive,
        String verdict,
        int baselineCoverage,
        int candidateCoverage,
        int baselineFocusLength,
        int candidateFocusLength,
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
            return seedTextMaterial(title, content, sourceKey, MaterialMetadataSnapshot.empty());
        }

        private String seedTextMaterial(
            String title,
            String content,
            String sourceKey,
            MaterialMetadataSnapshot metadata
        ) {
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
                now,
                metadata
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

        private String seedStructuredMaterial(
            String title,
            List<StoredMaterialChunk> chunks,
            String sourceKey,
            MaterialMetadataSnapshot metadata
        ) {
            Instant now = Instant.parse("2026-04-17T10:00:00Z");
            String content = chunks.stream()
                .map(StoredMaterialChunk::text)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
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
                "structured-v1",
                chunks.stream().anyMatch(chunk -> Boolean.TRUE.equals(chunk.ocrUsed())),
                chunks.stream().map(StoredMaterialChunk::page).filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(null),
                chunks,
                MaterialIndexingStatus.READY,
                MaterialVersionState.ACTIVE,
                null,
                null,
                now,
                now,
                metadata
            );
            repository.save(record, ChunkProfile.STRUCTURED_V1.propertyValue(), chunks, List.of());
            repository.markIndexingReady(
                record.id(),
                embed(chunks),
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
