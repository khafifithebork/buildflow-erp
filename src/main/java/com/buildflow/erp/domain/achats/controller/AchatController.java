package com.buildflow.erp.domain.achats.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.common.dto.UpdateIndicateursRequest;
import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import com.buildflow.erp.domain.achats.service.AchatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/achats")
@RequiredArgsConstructor
public class AchatController {

    private final AchatService achatService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACHAT')")
    public ResponseEntity<ApiResponse<AchatResponse>> create(@Valid @RequestBody CreateAchatRequest request) {
        AchatResponse response = achatService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AchatResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(achatService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AchatResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(achatService.findAll()));
    }

    @PatchMapping("/{id}/validate-bl")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACHAT', 'PM')")
    public ResponseEntity<ApiResponse<AchatResponse>> validateBL(
            @PathVariable UUID id,
            @RequestParam String bonLivraisonRef) {
        return ResponseEntity.ok(ApiResponse.success(achatService.validateBL(id, bonLivraisonRef)));
    }

    @PatchMapping("/{id}/validate-facture")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<AchatResponse>> validateFacture(
            @PathVariable UUID id,
            @RequestParam String factureRef) {
        return ResponseEntity.ok(ApiResponse.success(achatService.validateFacture(id, factureRef)));
    }

    /**
     * Toggles the two operational billing indicators on an existing achat.
     * Kept separate from the (non-existent) full "update achat" endpoint so the
     * Achat view can flip a checkbox without resubmitting lines and totals.
     */
    @PatchMapping("/{id}/indicateurs")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACHAT', 'FINANCE')")
    public ResponseEntity<ApiResponse<AchatResponse>> updateIndicateurs(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIndicateursRequest request) {
        return ResponseEntity.ok(ApiResponse.success(achatService.updateIndicateurs(id, request)));
    }

    @PatchMapping("/{id}/validate-paiement")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<AchatResponse>> validatePaiement(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(achatService.validatePaiement(id)));
    }
}