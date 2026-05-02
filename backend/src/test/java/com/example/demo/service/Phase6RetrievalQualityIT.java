package com.example.demo.service;

import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.DocumentBlock;
import com.example.demo.service.material.DocumentBlockConfidence;
import com.example.demo.service.material.DocumentBlockType;
import com.example.demo.service.material.DocumentParseResult;
import com.example.demo.service.material.DocumentParserProfile;
import com.example.demo.service.material.MaterialMetadataHints;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.StoredMaterialSegment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Phase6RetrievalQualityIT {

    @Test
    void writesPhase6QualityReportAndEnforcesControlledRolloutGate() throws Exception {
        RetrievalPipeline fixedHybrid = createPipeline(ChunkProfile.FIXED_V1, RelevanceProfile.HYBRID_V1);
        RetrievalPipeline structuredHybridForChunking = createPipeline(ChunkProfile.STRUCTURED_V1, RelevanceProfile.HYBRID_V1);
        QualityMatrix chunkingMatrix = runQualityMatrix(
            "chunking-quality",
            "fixed-v1 + hybrid-v1",
            "structured-v1 + hybrid-v1",
            fixedHybrid,
            structuredHybridForChunking,
            seedChunkingQualityCorpus(fixedHybrid, structuredHybridForChunking)
        );

        RetrievalPipeline structuredHybrid = createPipeline(ChunkProfile.STRUCTURED_V1, RelevanceProfile.HYBRID_V1);
        RetrievalPipeline structuredRerank = createPipeline(ChunkProfile.STRUCTURED_V1, RelevanceProfile.HYBRID_RERANK_V1);
        QualityMatrix rerankMatrix = runQualityMatrix(
            "reranker-quality",
            "structured-v1 + hybrid-v1",
            "structured-v1 + hybrid-rerank-v1",
            structuredHybrid,
            structuredRerank,
            seedRerankQualityCorpus(structuredHybrid, structuredRerank)
        );

        List<QualityMatrix> matrices = List.of(chunkingMatrix, rerankMatrix);
        GateEvaluation gate = evaluateQualityGate(matrices);
        writeQualityReport(matrices, gate);

        assertTrue(gate.passed(), "Phase 6 quality gate failed: " + String.join("; ", gate.failures()));
    }

    private List<QualityScenario> seedChunkingQualityCorpus(RetrievalPipeline baseline, RetrievalPipeline candidate) {
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
                "chunking-ru-lineage"
            ),
            candidate.seedTextMaterial(
                "RU exact",
                "Ремонт подстанции запланирован на май 2026 года.",
                "chunking-ru-lineage"
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
                "chunking-kz-lineage"
            ),
            candidate.seedTextMaterial(
                "KZ exact",
                "Желі жүктемесі жоспарлы режимде жұмыс істейді.",
                "chunking-kz-lineage"
            )
        ));

        scenarios.add(scenario(
            "table-row-focus",
            "chunking",
            "What is the Delta tariff limit and price?",
            List.of("Delta", "18000"),
            false,
            true,
            false,
            baseline.seedParsedMaterial(
                "Tariff matrix",
                List.of(
                    block(0, DocumentBlockType.TITLE, "Tariff matrix", 1),
                    block(
                        1,
                        DocumentBlockType.TABLE,
                        "Plan | Limit | Price\n"
                            + "Alpha | 40 MW | 9000 tenge\n"
                            + "Bravo | 55 MW | 12000 tenge\n"
                            + "Delta | 75 MW | 18000 tenge\n"
                            + "Echo | 95 MW | 24000 tenge",
                        1
                    )
                ),
                "chunking-tariff-lineage",
                MaterialMetadataSnapshot.empty()
            ),
            candidate.seedParsedMaterial(
                "Tariff matrix",
                List.of(
                    block(0, DocumentBlockType.TITLE, "Tariff matrix", 1),
                    block(
                        1,
                        DocumentBlockType.TABLE,
                        "Plan | Limit | Price\n"
                            + "Alpha | 40 MW | 9000 tenge\n"
                            + "Bravo | 55 MW | 12000 tenge\n"
                            + "Delta | 75 MW | 18000 tenge\n"
                            + "Echo | 95 MW | 24000 tenge",
                        1
                    )
                ),
                "chunking-tariff-lineage",
                MaterialMetadataSnapshot.empty()
            )
        ));

        scenarios.add(scenario(
            "slide-owner-focus",
            "chunking",
            "Who owns the reserve bridge escalation?",
            List.of("Reserve bridge escalation", "National dispatch center"),
            false,
            true,
            false,
            baseline.seedParsedMaterial(
                "Escalation slide",
                List.of(
                    block(0, DocumentBlockType.TITLE, "Escalation model", 2),
                    block(
                        1,
                        DocumentBlockType.SLIDE,
                        "Primary owner: regional dispatcher\n"
                            + "Reserve bridge escalation: National dispatch center\n"
                            + "Fallback liaison: field supervisor",
                        2
                    )
                ),
                "chunking-slide-lineage",
                MaterialMetadataSnapshot.empty()
            ),
            candidate.seedParsedMaterial(
                "Escalation slide",
                List.of(
                    block(0, DocumentBlockType.TITLE, "Escalation model", 2),
                    block(
                        1,
                        DocumentBlockType.SLIDE,
                        "Primary owner: regional dispatcher\n"
                            + "Reserve bridge escalation: National dispatch center\n"
                            + "Fallback liaison: field supervisor",
                        2
                    )
                ),
                "chunking-slide-lineage",
                MaterialMetadataSnapshot.empty()
            )
        ));

        return List.copyOf(scenarios);
    }

    private List<QualityScenario> seedRerankQualityCorpus(RetrievalPipeline baseline, RetrievalPipeline candidate) {
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

    private QualityMatrix runQualityMatrix(
        String name,
        String baselineProfile,
        String candidateProfile,
        RetrievalPipeline baseline,
        RetrievalPipeline candidate,
        List<QualityScenario> scenarios
    ) {
        List<QualityResult> results = new ArrayList<>();
        int baselineNoContext = 0;
        int candidateNoContext = 0;

        for (QualityScenario scenario : scenarios) {
            MaterialRetrievalResult baselineResult = baseline.retrievalService().retrieveContext(scenario.query());
            MaterialRetrievalResult candidateResult = candidate.retrievalService().retrieveContext(scenario.query());

            if (baselineResult.sources().isEmpty()) {
                baselineNoContext += 1;
            }
            if (candidateResult.sources().isEmpty()) {
                candidateNoContext += 1;
            }

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

            results.add(new QualityResult(
                scenario.name(),
                scenario.category(),
                scenario.query(),
                scenario.exactMatchGuard(),
                scenario.chunkingSensitive(),
                scenario.rerankSensitive(),
                verdictOf(
                    scenario,
                    baselineCoverage,
                    candidateCoverage,
                    baselineFocusLength,
                    candidateFocusLength,
                    baselineHitAt1,
                    candidateHitAt1
                ),
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

        return new QualityMatrix(
            name,
            baselineProfile,
            candidateProfile,
            baselineNoContext,
            candidateNoContext,
            results
        );
    }

    private String verdictOf(
        QualityScenario scenario,
        int baselineCoverage,
        int candidateCoverage,
        int baselineFocusLength,
        int candidateFocusLength,
        boolean baselineHitAt1,
        boolean candidateHitAt1
    ) {
        if (candidateCoverage > baselineCoverage) {
            return "CANDIDATE_WIN";
        }
        if (candidateCoverage < baselineCoverage) {
            return "BASELINE_WIN";
        }
        if (scenario.rerankSensitive() && candidateHitAt1 && !baselineHitAt1) {
            return "CANDIDATE_WIN";
        }
        if (scenario.rerankSensitive() && baselineHitAt1 && !candidateHitAt1) {
            return "BASELINE_WIN";
        }
        if (scenario.chunkingSensitive() && candidateCoverage > 0 && candidateFocusLength < baselineFocusLength) {
            return "CANDIDATE_WIN";
        }
        if (scenario.chunkingSensitive() && baselineCoverage > 0 && baselineFocusLength < candidateFocusLength) {
            return "BASELINE_WIN";
        }
        return "TIE";
    }

    private GateEvaluation evaluateQualityGate(List<QualityMatrix> matrices) {
        List<String> failures = new ArrayList<>();
        for (QualityMatrix matrix : matrices) {
            if (matrix.candidateNoContext() > matrix.baselineNoContext()) {
                failures.add(matrix.name() + ": candidate increased no-context count");
            }
            for (QualityResult result : matrix.results()) {
                if (result.exactMatchGuard() && (!result.baselineHitAt3() || !result.candidateHitAt3())) {
                    failures.add(matrix.name() + "/" + result.name() + ": exact-match guard lost hit@3");
                }
                if (result.exactMatchGuard() && "BASELINE_WIN".equals(result.verdict())) {
                    failures.add(matrix.name() + "/" + result.name() + ": exact-match guard produced BASELINE_WIN");
                }
            }
        }

        matrices.stream()
            .filter(matrix -> "chunking-quality".equals(matrix.name()))
            .findFirst()
            .ifPresent(matrix -> {
                boolean hasChunkingWin = matrix.results().stream()
                    .anyMatch(result -> result.chunkingSensitive() && "CANDIDATE_WIN".equals(result.verdict()));
                if (!hasChunkingWin) {
                    failures.add(matrix.name() + ": no CANDIDATE_WIN on chunking-sensitive scenarios");
                }
            });

        matrices.stream()
            .filter(matrix -> "reranker-quality".equals(matrix.name()))
            .findFirst()
            .ifPresent(matrix -> {
                boolean hasRerankWin = matrix.results().stream()
                    .anyMatch(result -> result.rerankSensitive() && "CANDIDATE_WIN".equals(result.verdict()));
                if (!hasRerankWin) {
                    failures.add(matrix.name() + ": no CANDIDATE_WIN on rerank-sensitive scenarios");
                }
            });

        return new GateEvaluation(failures.isEmpty() ? "PASS" : "FAIL", failures);
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

    private void writeQualityReport(List<QualityMatrix> matrices, GateEvaluation gate) throws Exception {
        Path reportDirectory = Path.of("target", "search-quality");
        Files.createDirectories(reportDirectory);
        Path reportPath = reportDirectory.resolve("phase6-quality-report.md");
        Path jsonReportPath = reportDirectory.resolve("phase6-quality-report.json");
        Path legacyReportPath = reportDirectory.resolve("phase6-rerank-report.md");

        StringBuilder report = new StringBuilder()
            .append("# Phase 6 Quality Rollout Report\n\n")
            .append("- Gate status: ").append(gate.gateStatus()).append("\n")
            .append("- Generated at: ").append(Instant.now()).append("\n")
            .append("- Matrices: ").append(matrices.size()).append("\n\n");

        if (!gate.failures().isEmpty()) {
            report.append("## Gate Failures\n\n");
            gate.failures().forEach(failure -> report.append("- ").append(failure).append("\n"));
            report.append("\n");
        }

        for (QualityMatrix matrix : matrices) {
            report.append("## ")
                .append(matrix.name())
                .append("\n\n")
                .append("- Baseline: ").append(matrix.baselineProfile()).append("\n")
                .append("- Candidate: ").append(matrix.candidateProfile()).append("\n")
                .append("- Baseline no-context count: ").append(matrix.baselineNoContext()).append("\n")
                .append("- Candidate no-context count: ").append(matrix.candidateNoContext()).append("\n")
                .append("- Candidate wins: ").append(matrix.candidateWins()).append("\n")
                .append("- Baseline wins: ").append(matrix.baselineWins()).append("\n")
                .append("- Ties: ").append(matrix.ties()).append("\n\n")
                .append("| Category | Scenario | Exact guard | Chunking-sensitive | Rerank-sensitive | Verdict | Baseline top-hit coverage | Candidate top-hit coverage | Baseline focus chars | Candidate focus chars | Baseline hit@1 | Baseline hit@3 | Candidate hit@1 | Candidate hit@3 | Baseline top hit | Candidate top hit |\n")
                .append("|---|---|---|---|---|---|---:|---:|---:|---:|---|---|---|---|---|---|\n");

            for (QualityResult result : matrix.results()) {
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
            report.append("\n");
        }

        Files.writeString(reportPath, report.toString());
        Files.writeString(legacyReportPath, report.toString());

        ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.writeValue(jsonReportPath.toFile(), jsonPayload(matrices, gate));
    }

    private Map<String, Object> jsonPayload(List<QualityMatrix> matrices, GateEvaluation gate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", Instant.now().toString());
        payload.put("matrices", matrices.stream().map(this::matrixJson).toList());
        payload.put("summary", summaryJson(matrices, gate));
        payload.put("scenarios", matrices.stream()
            .flatMap(matrix -> matrix.results().stream().map(result -> scenarioJson(matrix, result)))
            .toList());
        payload.put("gateStatus", gate.gateStatus());
        return payload;
    }

    private Map<String, Object> summaryJson(List<QualityMatrix> matrices, GateEvaluation gate) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("matrixCount", matrices.size());
        summary.put("scenarioCount", matrices.stream().mapToInt(matrix -> matrix.results().size()).sum());
        summary.put("candidateWins", matrices.stream().mapToLong(QualityMatrix::candidateWins).sum());
        summary.put("baselineWins", matrices.stream().mapToLong(QualityMatrix::baselineWins).sum());
        summary.put("ties", matrices.stream().mapToLong(QualityMatrix::ties).sum());
        summary.put("failures", gate.failures());
        return summary;
    }

    private Map<String, Object> matrixJson(QualityMatrix matrix) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", matrix.name());
        node.put("baselineProfile", matrix.baselineProfile());
        node.put("candidateProfile", matrix.candidateProfile());
        node.put("baselineNoContext", matrix.baselineNoContext());
        node.put("candidateNoContext", matrix.candidateNoContext());
        node.put("candidateWins", matrix.candidateWins());
        node.put("baselineWins", matrix.baselineWins());
        node.put("ties", matrix.ties());
        return node;
    }

    private Map<String, Object> scenarioJson(QualityMatrix matrix, QualityResult result) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("matrix", matrix.name());
        node.put("name", result.name());
        node.put("category", result.category());
        node.put("query", result.query());
        node.put("exactMatchGuard", result.exactMatchGuard());
        node.put("chunkingSensitive", result.chunkingSensitive());
        node.put("rerankSensitive", result.rerankSensitive());
        node.put("verdict", result.verdict());
        node.put("baselineCoverage", result.baselineCoverage());
        node.put("candidateCoverage", result.candidateCoverage());
        node.put("baselineFocusLength", result.baselineFocusLength() == Integer.MAX_VALUE ? null : result.baselineFocusLength());
        node.put("candidateFocusLength", result.candidateFocusLength() == Integer.MAX_VALUE ? null : result.candidateFocusLength());
        node.put("baselineHitAt1", result.baselineHitAt1());
        node.put("baselineHitAt3", result.baselineHitAt3());
        node.put("candidateHitAt1", result.candidateHitAt1());
        node.put("candidateHitAt3", result.candidateHitAt3());
        node.put("baselineTopHit", topHitJson(result.baselineSources()));
        node.put("candidateTopHit", topHitJson(result.candidateSources()));
        return node;
    }

    private Map<String, Object> topHitJson(List<ChatSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return Map.of();
        }
        ChatSource source = sources.getFirst();
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("materialId", source.materialId());
        node.put("title", source.title());
        node.put("excerpt", source.excerpt());
        node.put("score", source.score());
        node.put("chunkIndex", source.chunkIndex());
        return node;
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

    private DocumentBlock block(int index, DocumentBlockType type, String text, Integer page) {
        return new DocumentBlock(
            index,
            type,
            text,
            page,
            "quality-fixture",
            false,
            DocumentBlockConfidence.HIGH,
            type == DocumentBlockType.TITLE ? index + 1 : null
        );
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

    private record QualityMatrix(
        String name,
        String baselineProfile,
        String candidateProfile,
        int baselineNoContext,
        int candidateNoContext,
        List<QualityResult> results
    ) {
        private QualityMatrix {
            results = results == null ? List.of() : List.copyOf(results);
        }

        private long candidateWins() {
            return results.stream().filter(result -> "CANDIDATE_WIN".equals(result.verdict())).count();
        }

        private long baselineWins() {
            return results.stream().filter(result -> "BASELINE_WIN".equals(result.verdict())).count();
        }

        private long ties() {
            return results.stream().filter(result -> "TIE".equals(result.verdict())).count();
        }
    }

    private record GateEvaluation(String gateStatus, List<String> failures) {
        private GateEvaluation {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        private boolean passed() {
            return "PASS".equals(gateStatus);
        }
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

        private String seedParsedMaterial(
            String title,
            List<DocumentBlock> blocks,
            String sourceKey,
            MaterialMetadataSnapshot metadata
        ) {
            Instant now = Instant.parse("2026-04-17T10:00:00Z");
            DocumentParseResult parseResult = new DocumentParseResult(
                blocks,
                MaterialMetadataHints.empty(),
                List.of(),
                blocks.stream()
                    .map(DocumentBlock::page)
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(null),
                "quality-fixture",
                blocks.stream().anyMatch(block -> Boolean.TRUE.equals(block.ocrUsed())),
                DocumentParserProfile.RICH_TEXT
            );
            String content = contentSupport.joinBlocks(parseResult);
            List<StoredMaterialSegment> segments = contentSupport.toStoredSegments(parseResult);
            List<StoredMaterialChunk> rawChunks = contentSupport.buildChunks(parseResult, chunkProfile);
            StoredMaterialRecord record = new StoredMaterialRecord(
                UUID.randomUUID().toString(),
                title,
                "file",
                title + ".txt",
                "text/plain",
                content,
                contentSupport.normalizeForHash(content),
                UUID.randomUUID().toString(),
                sourceKey,
                "quality-fixture",
                false,
                parseResult.pageCount(),
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
                    embeddings.get(index),
                    chunk.chunkType(),
                    chunk.sectionPath(),
                    chunk.headingTrail(),
                    chunk.tableId(),
                    chunk.slideId(),
                    chunk.parserConfidence()
                ));
            }
            return embedded;
        }
    }
}
