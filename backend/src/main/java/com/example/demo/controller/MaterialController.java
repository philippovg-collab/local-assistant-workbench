package com.example.demo.controller;

import com.example.demo.api.ApiException;
import com.example.demo.model.CreateTextMaterialRequest;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialListResponse;
import com.example.demo.model.MaterialLineageResponse;
import com.example.demo.model.MaterialDetail;
import com.example.demo.model.RechunkActiveMaterialsBatchRequest;
import com.example.demo.model.RechunkActiveMaterialsBatchResponse;
import com.example.demo.model.MaterialUploadPolicyResponse;
import com.example.demo.model.RechunkActiveMaterialsResponse;
import com.example.demo.model.MaterialSummary;
import com.example.demo.service.MaterialService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping
    public MaterialListResponse listMaterials(
        @RequestParam(required = false) Integer offset,
        @RequestParam(required = false) Integer limit
    ) {
        return materialService.listSummariesPage(offset, limit);
    }

    @GetMapping("/policy")
    public MaterialUploadPolicyResponse getUploadPolicy() {
        return materialService.getUploadPolicy();
    }

    @GetMapping("/{id}/lineage")
    public MaterialLineageResponse getLineage(@PathVariable String id) {
        return materialService.getLineage(requireValidMaterialId(id));
    }

    @GetMapping("/{id}")
    public MaterialDetail getMaterial(@PathVariable String id) {
        return materialService.getDetail(requireValidMaterialId(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public MaterialSummary createTextMaterial(@Valid @RequestBody CreateTextMaterialRequest request) {
        if (request == null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "request.invalid_payload",
                "Request payload is required"
            );
        }
        return materialService.saveText(request.title(), request.content(), request.metadata());
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialSummary uploadMaterial(
        @RequestPart("file") MultipartFile file,
        @RequestParam(required = false) String title,
        @Valid @RequestPart(value = "metadata", required = false) MaterialMetadataInput metadata
    ) {
        return materialService.saveUpload(title, file, metadata);
    }

    @PostMapping(path = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialSummary uploadMaterialVersion(
        @PathVariable String id,
        @RequestPart("file") MultipartFile file,
        @RequestParam(required = false) String title,
        @Valid @RequestPart(value = "metadata", required = false) MaterialMetadataInput metadata
    ) {
        return materialService.saveUploadVersion(requireValidMaterialId(id), title, file, metadata);
    }

    @DeleteMapping("/{id}")
    public void deleteMaterial(@PathVariable String id) {
        materialService.delete(requireValidMaterialId(id));
    }

    @PostMapping("/{id}/reindex")
    public MaterialSummary reindexMaterial(@PathVariable String id) {
        return materialService.reindex(requireValidMaterialId(id));
    }

    @PostMapping("/rechunk-active")
    public RechunkActiveMaterialsResponse rechunkActiveMaterials() {
        return materialService.rechunkActiveMaterials();
    }

    @PostMapping(path = "/rechunk-active/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RechunkActiveMaterialsBatchResponse rechunkActiveMaterialsBatch(
        @Valid @RequestBody(required = false) RechunkActiveMaterialsBatchRequest request
    ) {
        return materialService.rechunkActiveMaterialsBatch(request);
    }

    private String requireValidMaterialId(String id) {
        if (!StringUtils.hasText(id)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.invalid_id",
                "Material id must not be blank"
            );
        }

        String normalizedId = id.trim();
        try {
            UUID.fromString(normalizedId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.invalid_id",
                "Material id must be a valid UUID",
                exception
            );
        }

        return normalizedId;
    }
}
