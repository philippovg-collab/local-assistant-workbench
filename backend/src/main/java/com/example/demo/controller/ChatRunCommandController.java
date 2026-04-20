package com.example.demo.controller;

import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.service.ChatRunExecutionService;
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

    private final ChatRunExecutionService chatRunExecutionService;

    public ChatRunCommandController(ChatRunExecutionService chatRunExecutionService) {
        this.chatRunExecutionService = chatRunExecutionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ChatRunSubmissionResponse submitRun(@RequestBody ChatExecutionRequest request) {
        return chatRunExecutionService.submit(request);
    }

    @PostMapping("/{id}/cancel")
    public ChatRunTraceDetail cancel(@PathVariable String id) {
        return chatRunExecutionService.cancel(id);
    }
}
