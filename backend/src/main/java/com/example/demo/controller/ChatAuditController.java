package com.example.demo.controller;

import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.service.ChatAuditService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-runs")
public class ChatAuditController {

    private final ChatAuditService chatAuditService;

    public ChatAuditController(ChatAuditService chatAuditService) {
        this.chatAuditService = chatAuditService;
    }

    @GetMapping
    public List<ChatAuditRunSummary> listRuns() {
        return chatAuditService.listRuns();
    }

    @GetMapping("/{id}")
    public ChatAuditRunDetail getRun(@PathVariable String id) {
        return chatAuditService.getRun(id);
    }
}
