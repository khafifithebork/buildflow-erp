package com.buildflow.erp.domain.referentiel.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.referentiel.dto.request.CreateFournisseurRequest;
import com.buildflow.erp.domain.referentiel.dto.response.FournisseurResponse;
import com.buildflow.erp.domain.referentiel.service.FournisseurService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fournisseurs")
@RequiredArgsConstructor
public class FournisseurController {

    private final FournisseurService fournisseurService;

    // Managing suppliers is owned by ADMIN/ACHAT (matches the /dashboard/fournisseurs
    // page). Reads are also needed by PM, whose purchase-creation form on
    // /dashboard/achats populates a supplier dropdown from these endpoints.
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACHAT')")
    public ResponseEntity<ApiResponse<FournisseurResponse>> create(
            @Valid @RequestBody CreateFournisseurRequest request) {
        FournisseurResponse response = fournisseurService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACHAT', 'PM')")
    public ResponseEntity<ApiResponse<FournisseurResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(fournisseurService.findById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACHAT', 'PM')")
    public ResponseEntity<ApiResponse<List<FournisseurResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(fournisseurService.findAll()));
    }
}