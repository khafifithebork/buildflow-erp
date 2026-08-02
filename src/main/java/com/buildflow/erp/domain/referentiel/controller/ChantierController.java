package com.buildflow.erp.domain.referentiel.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.dto.response.ChantierResponse;
import com.buildflow.erp.domain.referentiel.service.ChantierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chantiers")
@RequiredArgsConstructor
public class ChantierController {

    private final ChantierService chantierService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR', 'PM', 'CHEF_CHANTIER')")
    public ResponseEntity<ApiResponse<ChantierResponse>> create(@Valid @RequestBody CreateChantierRequest request) {
        ChantierResponse response = chantierService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR', 'PM', 'CHEF_CHANTIER')")
    public ResponseEntity<ApiResponse<ChantierResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody CreateChantierRequest request) {
        return ResponseEntity.ok(ApiResponse.success(chantierService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        chantierService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/demarrer")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR', 'PM', 'CHEF_CHANTIER')")
    public ResponseEntity<ApiResponse<ChantierResponse>> demarrer(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(chantierService.demarrer(id)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChantierResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(chantierService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChantierResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(chantierService.findAll()));
    }
}