package com.example.demo.controller;

import com.example.demo.model.ChatMode;
import com.example.demo.model.ConversationCreateRequest;
import com.example.demo.model.ConversationDetail;
import com.example.demo.model.ConversationPatchRequest;
import com.example.demo.model.ConversationRunDetail;
import com.example.demo.model.ConversationSummary;
import com.example.demo.service.ConversationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDetail createConversation(@Valid @RequestBody ConversationCreateRequest request) {
        return conversationService.createConversation(request);
    }

    @GetMapping
    public List<ConversationSummary> listConversations(
        @RequestParam(required = false) String workspaceKey,
        @RequestParam(required = false) ChatMode mode
    ) {
        return conversationService.listConversations(workspaceKey, mode);
    }

    @GetMapping("/{id}")
    public ConversationDetail getConversation(@PathVariable String id) {
        return conversationService.getConversation(id);
    }

    @PatchMapping("/{id}")
    public ConversationDetail patchConversation(
        @PathVariable String id,
        @Valid @RequestBody ConversationPatchRequest request
    ) {
        return conversationService.patchConversation(id, request);
    }

    @GetMapping("/{id}/runs")
    public List<ConversationRunDetail> listRuns(@PathVariable String id) {
        return conversationService.listRuns(id);
    }
}
