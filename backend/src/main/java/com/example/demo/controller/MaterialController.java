package com.example.demo.controller;

import com.example.demo.api.ApiException;
import com.example.demo.model.CreateTextMaterialRequest;
import com.example.demo.model.MaterialLineageResponse;
import com.example.demo.model.MaterialUploadPolicyResponse;
import com.example.demo.model.MaterialSummary;
import com.example.demo.service.MaterialService;
import java.util.UUID;
import java.util.List;
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
    public List<MaterialSummary> listMaterials() {
        return materialService.listSummaries();
    }

    @GetMapping("/policy")
    public MaterialUploadPolicyResponse getUploadPolicy() {
        return materialService.getUploadPolicy();
    }

    @GetMapping("/{id}/lineage")
    public MaterialLineageResponse getLineage(@PathVariable String id) {
        return materialService.getLineage(requireValidMaterialId(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public MaterialSummary createTextMaterial(@RequestBody CreateTextMaterialRequest request) {
        if (request == null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "request.invalid_payload",
                "Request payload is required"
            );
        }
        return materialService.saveText(request.title(), request.content());
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialSummary uploadMaterial(
        @RequestPart("file") MultipartFile file,
        @RequestParam(required = false) String title
    ) {
        return materialService.saveUpload(title, file);
    }

    @DeleteMapping("/{id}")
    public void deleteMaterial(@PathVariable String id) {
        materialService.delete(requireValidMaterialId(id));
    }

    @PostMapping("/{id}/reindex")
    public MaterialSummary reindexMaterial(@PathVariable String id) {
        return materialService.reindex(requireValidMaterialId(id));
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
