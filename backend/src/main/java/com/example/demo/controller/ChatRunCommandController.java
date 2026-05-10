package com.example.demo.controller;

import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.service.ChatRunExecutionService;
import com.example.demo.service.ChatRunSubmissionCoordinator;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-runs")
public class ChatRunCommandController {

    private final ChatRunSubmissionCoordinator chatRunSubmissionCoordinator;
    private final ChatRunExecutionService chatRunExecutionService;

    @Autowired
    public ChatRunCommandController(
        ChatRunSubmissionCoordinator chatRunSubmissionCoordinator,
        ChatRunExecutionService chatRunExecutionService
    ) {
        this.chatRunSubmissionCoordinator = chatRunSubmissionCoordinator;
        this.chatRunExecutionService = chatRunExecutionService;
    }

    ChatRunCommandController(ChatRunExecutionService chatRunExecutionService) {
        this.chatRunSubmissionCoordinator = null;
        this.chatRunExecutionService = chatRunExecutionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ChatRunSubmissionResponse submitRun(@Valid @RequestBody ChatExecutionRequest request) {
        if (chatRunSubmissionCoordinator == null) {
            return chatRunExecutionService.submit(request);
        }
        return chatRunSubmissionCoordinator.submit(request);
    }

    @PostMapping("/{id}/cancel")
    public ChatRunTraceDetail cancel(@PathVariable String id) {
        return chatRunExecutionService.cancel(id);
    }
}
