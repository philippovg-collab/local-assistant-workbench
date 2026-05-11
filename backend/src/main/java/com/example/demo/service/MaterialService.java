package com.example.demo.service;

import com.example.demo.service.material.StoredMaterialRecord;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialLineageOverrideInput;
import com.example.demo.model.MaterialListResponse;
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

    public List<MaterialSummary> listSummaries(Integer offset, Integer limit) {
        return queryService.listSummaries(offset, limit);
    }

    public MaterialListResponse listSummariesPage(Integer offset, Integer limit) {
        return queryService.listSummariesPage(offset, limit);
    }

    public MaterialListResponse listSummariesPage(Integer offset, Integer limit, String workspaceKey) {
        return queryService.listSummariesPage(offset, limit, workspaceKey);
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

    public MaterialSummary saveText(
        String title,
        String content,
        MaterialMetadataInput metadata,
        MaterialLineageOverrideInput lineageOverride
    ) {
        return ingestionService.saveText(title, content, metadata, lineageOverride);
    }

    public MaterialSummary saveUpload(String title, MultipartFile file) {
        return saveUpload(title, file, null);
    }

    public MaterialSummary saveUpload(String title, MultipartFile file, MaterialMetadataInput metadata) {
        return ingestionService.saveUpload(title, file, metadata);
    }

    public MaterialSummary saveUpload(
        String title,
        MultipartFile file,
        MaterialMetadataInput metadata,
        MaterialLineageOverrideInput lineageOverride
    ) {
        return ingestionService.saveUpload(title, file, metadata, lineageOverride);
    }

    public MaterialSummary saveUploadVersion(
        String materialId,
        String title,
        MultipartFile file,
        MaterialMetadataInput metadata
    ) {
        return ingestionService.saveUploadVersion(materialId, title, file, metadata);
    }

    public MaterialSummary saveUploadVersion(
        String materialId,
        String title,
        MultipartFile file,
        MaterialMetadataInput metadata,
        String workspaceKey
    ) {
        queryService.requireMaterialInWorkspace(materialId, workspaceKey);
        return ingestionService.saveUploadVersion(materialId, title, file, metadata);
    }

    public MaterialSummary editMaterial(
        String materialId,
        String title,
        String content,
        MaterialMetadataInput metadata
    ) {
        return ingestionService.editMaterial(materialId, title, content, metadata);
    }

    public MaterialSummary editMaterial(
        String materialId,
        String title,
        String content,
        MaterialMetadataInput metadata,
        String workspaceKey
    ) {
        queryService.requireMaterialInWorkspace(materialId, workspaceKey);
        return ingestionService.editMaterial(materialId, title, content, metadata);
    }

    public void delete(String id) {
        queryService.delete(id);
    }

    public void delete(String id, String workspaceKey) {
        queryService.requireMaterialInWorkspace(id, workspaceKey);
        queryService.delete(id);
    }

    public MaterialSummary reindex(String id) {
        return queryService.reindex(id);
    }

    public MaterialSummary reindex(String id, String workspaceKey) {
        queryService.requireMaterialInWorkspace(id, workspaceKey);
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

    public MaterialLineageResponse getLineage(String id, String workspaceKey) {
        queryService.requireMaterialInWorkspace(id, workspaceKey);
        return queryService.getLineage(id);
    }

    public MaterialDetail getDetail(String id) {
        return queryService.getDetail(id);
    }

    public MaterialDetail getDetail(String id, String workspaceKey) {
        queryService.requireMaterialInWorkspace(id, workspaceKey);
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

    public MaterialRetrievalResult retrieveContext(
        String prompt,
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        List<String> dismissedRetrievalHintKeys
    ) {
        return retrievalService.retrieveContext(prompt, knowledgeScope, retrievalFilters, dismissedRetrievalHintKeys);
    }

    public MaterialSearchResponse search(MaterialSearchRequest request) {
        return retrievalService.search(request);
    }

    public boolean importLegacyRecord(StoredMaterialRecord legacyRecord) {
        return ingestionService.importLegacyRecord(legacyRecord);
    }
}
