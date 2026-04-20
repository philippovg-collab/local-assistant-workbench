package com.example.demo.controller;

import com.example.demo.api.ApiException;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.service.ChatExecutionService;
import com.example.demo.service.ModelCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatExecutionService chatExecutionService;
    private final ModelCatalogService modelCatalogService;

    public ChatController(
        ChatExecutionService chatExecutionService,
        ModelCatalogService modelCatalogService
    ) {
        this.chatExecutionService = chatExecutionService;
        this.modelCatalogService = modelCatalogService;
    }

    @GetMapping("/models")
    public List<OllamaModelInfo> listModels() {
        return modelCatalogService.listModels();
    }

    @PostMapping("/chat")
    public ChatExecutionResponse chat(@Valid @RequestBody ChatExecutionRequest request) {
        if (request == null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "request.invalid_payload",
                "Request payload is required"
            );
        }
        return chatExecutionService.execute(request);
    }
}
