package com.example.demo.controller;

import com.example.demo.model.CreateKnowledgePresetRequest;
import com.example.demo.model.KnowledgePresetDetail;
import com.example.demo.model.KnowledgePresetRevisionDiff;
import com.example.demo.model.KnowledgePresetRevisionDetail;
import com.example.demo.model.KnowledgePresetSummary;
import com.example.demo.service.KnowledgePresetService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-facets")
public class KnowledgeFacetController {

    private final KnowledgePresetService knowledgePresetService;

    public KnowledgeFacetController(KnowledgePresetService knowledgePresetService) {
        this.knowledgePresetService = knowledgePresetService;
    }

    @GetMapping
    public List<KnowledgePresetSummary> listKnowledgeFacets() {
        return knowledgePresetService.listFacets();
    }

    @GetMapping("/{id}")
    public KnowledgePresetDetail getKnowledgeFacet(@PathVariable String id) {
        return knowledgePresetService.getFacet(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgePresetDetail createKnowledgeFacet(@Valid @RequestBody CreateKnowledgePresetRequest request) {
        return knowledgePresetService.createFacet(request);
    }

    @PutMapping("/{id}")
    public KnowledgePresetDetail updateKnowledgeFacet(@PathVariable String id, @Valid @RequestBody CreateKnowledgePresetRequest request) {
        return knowledgePresetService.updateFacet(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteKnowledgeFacet(@PathVariable String id) {
        knowledgePresetService.deleteFacet(id);
    }

    @GetMapping("/{id}/revisions")
    public List<KnowledgePresetRevisionDetail> listKnowledgeFacetRevisions(@PathVariable String id) {
        return knowledgePresetService.listFacetRevisions(id);
    }

    @GetMapping("/{id}/revisions/{revision}")
    public KnowledgePresetRevisionDetail getKnowledgeFacetRevision(@PathVariable String id, @PathVariable int revision) {
        return knowledgePresetService.getFacetRevision(id, revision);
    }

    @GetMapping("/{id}/diff")
    public KnowledgePresetRevisionDiff diffKnowledgeFacetRevisions(
        @PathVariable String id,
        @RequestParam int fromRevision,
        @RequestParam int toRevision
    ) {
        return knowledgePresetService.diffFacetRevisions(id, fromRevision, toRevision);
    }

    @PostMapping("/{id}/restore/{revision}")
    public KnowledgePresetDetail restoreKnowledgeFacetRevision(@PathVariable String id, @PathVariable int revision) {
        return knowledgePresetService.restoreFacetRevision(id, revision);
    }
}
