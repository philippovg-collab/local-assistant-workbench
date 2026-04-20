package com.example.demo.controller;

import com.example.demo.api.ApiException;
import com.example.demo.api.InputLimits;
import com.example.demo.model.MaterialSearchRequest;
import com.example.demo.model.MaterialSearchResponse;
import com.example.demo.service.MaterialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final MaterialService materialService;

    public SearchController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @PostMapping("/search")
    public MaterialSearchResponse search(@Valid @RequestBody MaterialSearchRequest request) {
        if (request == null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "request.invalid_payload",
                "Request payload is required"
            );
        }
        InputLimits.validateMaterialSearchRequest(request);
        return materialService.search(request);
    }
}
