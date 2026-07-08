package com.buildflow.erp.domain.referentiel.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.referentiel.dto.request.CreateSousTraitantRequest;
import com.buildflow.erp.domain.referentiel.dto.response.SousTraitantResponse;
import com.buildflow.erp.domain.referentiel.service.SousTraitantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sous-traitants")
@RequiredArgsConstructor
public class SousTraitantController {

    private final SousTraitantService sousTraitantService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'ACHAT')")
    public ResponseEntity<ApiResponse<SousTraitantResponse>> create(
            @Valid @RequestBody CreateSousTraitantRequest request) {
        SousTraitantResponse response = sousTraitantService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SousTraitantResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(sousTraitantService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SousTraitantResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(sousTraitantService.findAll()));
    }
}