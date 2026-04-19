package com.example.demo.controller;

import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.service.ChatAuditService;
import com.example.demo.service.ChatRunExecutionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-runs")
public class ChatAuditController {

    private final ChatAuditService chatAuditService;
    private final ChatRunExecutionService chatRunExecutionService;

    public ChatAuditController(
        ChatAuditService chatAuditService,
        ChatRunExecutionService chatRunExecutionService
    ) {
        this.chatAuditService = chatAuditService;
        this.chatRunExecutionService = chatRunExecutionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ChatRunSubmissionResponse submitRun(@RequestBody ChatExecutionRequest request) {
        return chatRunExecutionService.submit(request);
    }

    @GetMapping
    public List<ChatAuditRunSummary> listRuns() {
        return chatAuditService.listRuns();
    }

    @GetMapping("/{id}")
    public ChatAuditRunDetail getRun(@PathVariable String id) {
        return chatAuditService.getRun(id);
    }

    @GetMapping("/{id}/trace")
    public ChatRunTraceDetail getTrace(@PathVariable String id) {
        return chatAuditService.getTrace(id);
    }

    @GetMapping("/{id}/result")
    public ChatExecutionResponse getResult(@PathVariable String id) {
        return chatRunExecutionService.getResult(id);
    }

    @PostMapping("/{id}/cancel")
    public ChatRunTraceDetail cancel(@PathVariable String id) {
        return chatRunExecutionService.cancel(id);
    }
}
