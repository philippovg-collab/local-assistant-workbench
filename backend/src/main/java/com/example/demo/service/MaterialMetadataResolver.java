package com.example.demo.service;

import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.service.material.MaterialMetadataHints;
import com.example.demo.service.reference.port.ReferenceDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MaterialMetadataResolver {

    private final MaterialMetadataInference inference;
    private final MaterialMetadataMergePolicy mergePolicy;

    @Autowired
    public MaterialMetadataResolver(ReferenceDataRepository referenceDataRepository) {
        this.inference = new MaterialMetadataInference();
        this.mergePolicy = new MaterialMetadataMergePolicy(referenceDataRepository);
    }

    public MaterialMetadataSnapshot resolve(
        MaterialMetadataInput manualInput,
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String contentHeader
    ) {
        return resolve(
            manualInput,
            title,
            sourceType,
            originalFileName,
            mediaType,
            contentHeader,
            MaterialMetadataHints.empty()
        );
    }

    public MaterialMetadataSnapshot resolve(
        MaterialMetadataInput manualInput,
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String contentHeader,
        MaterialMetadataHints parserHints
    ) {
        MaterialMetadataHints regexHints = inference.inferHints(title, sourceType, originalFileName, mediaType, contentHeader);
        return mergePolicy.merge(manualInput, mergePolicy.mergeHints(parserHints, regexHints));
    }
}
