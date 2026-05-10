package com.example.demo.controller;

import com.example.demo.model.ClientEventRequest;
import com.example.demo.service.ClientEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client-events")
public class ClientEventController {

    private final ClientEventService clientEventService;

    public ClientEventController(ClientEventService clientEventService) {
        this.clientEventService = clientEventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void recordClientEvent(
        @Valid @RequestBody ClientEventRequest event,
        HttpServletRequest request
    ) {
        clientEventService.record(event, request);
    }
}
