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
@RequestMapping("/api/knowledge-presets")
public class KnowledgePresetController {

    private final KnowledgePresetService knowledgePresetService;

    public KnowledgePresetController(KnowledgePresetService knowledgePresetService) {
        this.knowledgePresetService = knowledgePresetService;
    }

    @GetMapping
    public List<KnowledgePresetSummary> listKnowledgePresets() {
        return knowledgePresetService.listPresets();
    }

    @GetMapping("/{id}")
    public KnowledgePresetDetail getKnowledgePreset(@PathVariable String id) {
        return knowledgePresetService.getPreset(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgePresetDetail createKnowledgePreset(@Valid @RequestBody CreateKnowledgePresetRequest request) {
        return knowledgePresetService.createPreset(request);
    }

    @PutMapping("/{id}")
    public KnowledgePresetDetail updateKnowledgePreset(@PathVariable String id, @Valid @RequestBody CreateKnowledgePresetRequest request) {
        return knowledgePresetService.updatePreset(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteKnowledgePreset(@PathVariable String id) {
        knowledgePresetService.deletePreset(id);
    }

    @GetMapping("/{id}/revisions")
    public List<KnowledgePresetRevisionDetail> listKnowledgePresetRevisions(@PathVariable String id) {
        return knowledgePresetService.listRevisions(id);
    }

    @GetMapping("/{id}/revisions/{revision}")
    public KnowledgePresetRevisionDetail getKnowledgePresetRevision(@PathVariable String id, @PathVariable int revision) {
        return knowledgePresetService.getRevision(id, revision);
    }

    @GetMapping("/{id}/diff")
    public KnowledgePresetRevisionDiff diffKnowledgePresetRevisions(
        @PathVariable String id,
        @RequestParam int fromRevision,
        @RequestParam int toRevision
    ) {
        return knowledgePresetService.diffRevisions(id, fromRevision, toRevision);
    }

    @PostMapping("/{id}/restore/{revision}")
    public KnowledgePresetDetail restoreKnowledgePresetRevision(@PathVariable String id, @PathVariable int revision) {
        return knowledgePresetService.restoreRevision(id, revision);
    }
}
