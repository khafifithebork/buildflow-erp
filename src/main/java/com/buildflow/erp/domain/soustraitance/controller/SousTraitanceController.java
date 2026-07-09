package com.buildflow.erp.domain.soustraitance.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.soustraitance.dto.request.CreateContratRequest;
import com.buildflow.erp.domain.soustraitance.dto.request.CreatePaiementRequest;
import com.buildflow.erp.domain.soustraitance.dto.response.ContratSousTraitantResponse;
import com.buildflow.erp.domain.soustraitance.dto.response.PaiementSousTraitantResponse;
import com.buildflow.erp.domain.soustraitance.service.SousTraitanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contrats-sous-traitant")
@RequiredArgsConstructor
public class SousTraitanceController {

    private final SousTraitanceService sousTraitanceService;

    // ── Contrats ───────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<ContratSousTraitantResponse>> createContrat(
            @Valid @RequestBody CreateContratRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(sousTraitanceService.createContrat(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR', 'FINANCE', 'CHEF_CHANTIER')")
    public ResponseEntity<ApiResponse<ContratSousTraitantResponse>> findContratById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(sousTraitanceService.findContratById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR', 'FINANCE')")
    public ResponseEntity<ApiResponse<List<ContratSousTraitantResponse>>> findAllContrats(
            @RequestParam(required = false) UUID chantierId) {
        List<ContratSousTraitantResponse> result = (chantierId != null)
                ? sousTraitanceService.findContratsByChantier(chantierId)
                : sousTraitanceService.findAllContrats();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/{id}/terminer")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<ContratSousTraitantResponse>> terminerContrat(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(sousTraitanceService.terminerContrat(id)));
    }

    // ── Paiements (nested under contrat) ───────────────────────────

    @PostMapping("/{contratId}/paiements")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<PaiementSousTraitantResponse>> createPaiement(
            @PathVariable UUID contratId,
            @Valid @RequestBody CreatePaiementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(sousTraitanceService.createPaiement(contratId, request)));
    }

    @GetMapping("/{contratId}/paiements")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR', 'FINANCE')")
    public ResponseEntity<ApiResponse<List<PaiementSousTraitantResponse>>> getPaiements(
            @PathVariable UUID contratId) {
        return ResponseEntity.ok(ApiResponse.success(sousTraitanceService.getPaiements(contratId)));
    }

    @PatchMapping("/paiements/{paiementId}/valider")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<PaiementSousTraitantResponse>> validerPaiement(
            @PathVariable UUID paiementId) {
        return ResponseEntity.ok(ApiResponse.success(sousTraitanceService.validerPaiement(paiementId)));
    }

    @PatchMapping("/paiements/{paiementId}/payer")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<PaiementSousTraitantResponse>> payerPaiement(
            @PathVariable UUID paiementId) {
        return ResponseEntity.ok(ApiResponse.success(sousTraitanceService.payerPaiement(paiementId)));
    }
}
