package com.example.demo.service;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.infrastructure.material.DocumentTextExtractor;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialFormatRegistry;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialLineageResponse;
import com.example.demo.model.MaterialDetail;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialSearchRequest;
import com.example.demo.model.MaterialSearchResponse;
import com.example.demo.model.RechunkActiveMaterialsBatchRequest;
import com.example.demo.model.RechunkActiveMaterialsBatchResponse;
import com.example.demo.model.RechunkActiveMaterialsResponse;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialUploadPolicyResponse;
import com.example.demo.model.RetrievalFilters;
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
        return saveText(title, content, null);
    }

    public MaterialSummary saveText(String title, String content, MaterialMetadataInput metadata) {
        return ingestionService.saveText(title, content, metadata);
    }

    public MaterialSummary saveUpload(String title, MultipartFile file) {
        return saveUpload(title, file, null);
    }

    public MaterialSummary saveUpload(String title, MultipartFile file, MaterialMetadataInput metadata) {
        return ingestionService.saveUpload(title, file, metadata);
    }

    public void delete(String id) {
        queryService.delete(id);
    }

    public MaterialSummary reindex(String id) {
        return queryService.reindex(id);
    }

    public RechunkActiveMaterialsResponse rechunkActiveMaterials() {
        return queryService.rechunkActiveMaterials();
    }

    public RechunkActiveMaterialsBatchResponse rechunkActiveMaterialsBatch(RechunkActiveMaterialsBatchRequest request) {
        return queryService.rechunkActiveMaterialsBatch(request);
    }

    public MaterialLineageResponse getLineage(String id) {
        return queryService.getLineage(id);
    }

    public MaterialDetail getDetail(String id) {
        return queryService.getDetail(id);
    }

    public MaterialRetrievalResult retrieveContext(String prompt) {
        return retrievalService.retrieveContext(prompt);
    }

    public MaterialRetrievalResult retrieveContext(String prompt, KnowledgeScope knowledgeScope) {
        return retrievalService.retrieveContext(prompt, knowledgeScope);
    }

    public MaterialRetrievalResult retrieveContext(
        String prompt,
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters
    ) {
        return retrievalService.retrieveContext(prompt, knowledgeScope, retrievalFilters);
    }

    public MaterialSearchResponse search(MaterialSearchRequest request) {
        return retrievalService.search(request);
    }

    public boolean importLegacyRecord(StoredMaterialRecord legacyRecord) {
        return ingestionService.importLegacyRecord(legacyRecord);
    }
}
