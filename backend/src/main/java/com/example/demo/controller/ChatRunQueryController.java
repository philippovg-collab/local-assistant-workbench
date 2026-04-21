package com.example.demo.controller;

import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatRunStatusResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.service.ChatRunQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-runs")
public class ChatRunQueryController {

    private final ChatRunQueryService chatRunQueryService;

    public ChatRunQueryController(ChatRunQueryService chatRunQueryService) {
        this.chatRunQueryService = chatRunQueryService;
    }

    @GetMapping
    public List<ChatAuditRunSummary> listRuns(
        @RequestParam(required = false) String workspaceKey
    ) {
        return chatRunQueryService.listRuns(workspaceKey);
    }

    @GetMapping("/{id}")
    public ChatAuditRunDetail getRun(@PathVariable String id) {
        return chatRunQueryService.getRun(id);
    }

    @GetMapping("/{id}/trace")
    public ChatRunTraceDetail getTrace(@PathVariable String id) {
        return chatRunQueryService.getTrace(id);
    }

    @GetMapping("/{id}/status")
    public ChatRunStatusResponse getStatus(@PathVariable String id) {
        return chatRunQueryService.getStatus(id);
    }

    @GetMapping("/{id}/result")
    public ChatExecutionResponse getResult(@PathVariable String id) {
        return chatRunQueryService.getResult(id);
    }
}
