package com.example.demo.controller;

import com.example.demo.llmprovider.LlmProviderMapper;
import com.example.demo.llmprovider.LlmProviderProbeService;
import com.example.demo.llmprovider.LlmProviderService;
import com.example.demo.model.LlmProviderActivateRequest;
import com.example.demo.model.LlmProviderConfigResponse;
import com.example.demo.model.LlmProviderInput;
import com.example.demo.model.LlmProviderModelInfo;
import com.example.demo.model.LlmProviderProbeResult;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm-providers")
public class LlmProviderController {

    private final LlmProviderService providerService;
    private final LlmProviderProbeService probeService;

    public LlmProviderController(
        LlmProviderService providerService,
        LlmProviderProbeService probeService
    ) {
        this.providerService = providerService;
        this.probeService = probeService;
    }

    @GetMapping
    public List<LlmProviderConfigResponse> listProviders() {
        return providerService.listProviders();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LlmProviderConfigResponse createProvider(@Valid @RequestBody LlmProviderInput request) {
        return providerService.createProvider(request);
    }

    @PutMapping("/{id}")
    public LlmProviderConfigResponse updateProvider(
        @PathVariable UUID id,
        @Valid @RequestBody LlmProviderInput request
    ) {
        return providerService.updateProvider(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProvider(@PathVariable UUID id) {
        providerService.deleteProvider(id);
    }

    @PostMapping("/{id}/probe")
    public LlmProviderProbeResult probe(@PathVariable UUID id) {
        return probeService.probe(id);
    }

    @GetMapping("/{id}/models")
    public List<LlmProviderModelInfo> listProviderModels(@PathVariable UUID id) {
        return probeService.listModels(id);
    }

    @PostMapping("/{id}/activate")
    public LlmProviderConfigResponse activateProvider(
        @PathVariable UUID id,
        @Valid @RequestBody LlmProviderActivateRequest request
    ) {
        return LlmProviderMapper.toResponse(providerService.activateProvider(id, request));
    }

    @PostMapping("/fallback/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activateFallback(@Valid @RequestBody LlmProviderActivateRequest request) {
        providerService.activateFallback(request);
    }
}
