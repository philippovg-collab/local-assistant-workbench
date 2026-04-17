package com.example.demo.service;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.infrastructure.material.DocumentTextExtractor;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialFormatRegistry;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.MaterialLineageResponse;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialUploadPolicyResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaterialService {

    private final MaterialQueryService queryService;
    private final MaterialIngestionService ingestionService;
    private final MaterialRetrievalService retrievalService;

    public MaterialService(
        MaterialQueryService queryService,
        MaterialIngestionService ingestionService,
        MaterialRetrievalService retrievalService
    ) {
        this.queryService = queryService;
        this.ingestionService = ingestionService;
        this.retrievalService = retrievalService;
    }

    public List<MaterialSummary> listSummaries() {
        return queryService.listSummaries();
    }

    public MaterialUploadPolicyResponse getUploadPolicy() {
        return queryService.getUploadPolicy();
    }

    public MaterialSummary saveText(String title, String content) {
        return ingestionService.saveText(title, content);
    }

    public MaterialSummary saveUpload(String title, MultipartFile file) {
        return ingestionService.saveUpload(title, file);
    }

    public void delete(String id) {
        queryService.delete(id);
    }

    public MaterialSummary reindex(String id) {
        return queryService.reindex(id);
    }

    public MaterialLineageResponse getLineage(String id) {
        return queryService.getLineage(id);
    }

    public MaterialRetrievalResult retrieveContext(String prompt) {
        return retrievalService.retrieveContext(prompt);
    }

    public boolean importLegacyRecord(StoredMaterialRecord legacyRecord) {
        return ingestionService.importLegacyRecord(legacyRecord);
    }
}
