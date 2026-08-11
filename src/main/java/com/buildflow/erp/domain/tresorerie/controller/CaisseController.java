package com.buildflow.erp.domain.tresorerie.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.common.dto.UpdateIndicateursRequest;
import com.buildflow.erp.domain.tresorerie.dto.request.CreateCaisseRequest;
import com.buildflow.erp.domain.tresorerie.dto.request.CreateTransactionRequest;
import com.buildflow.erp.domain.tresorerie.dto.response.CaisseResponse;
import com.buildflow.erp.domain.tresorerie.dto.response.CaisseTransactionResponse;
import com.buildflow.erp.domain.tresorerie.service.TresorerieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/caisses")
@RequiredArgsConstructor
public class CaisseController {

    private final TresorerieService tresorerieService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<CaisseResponse>> create(
            @Valid @RequestBody CreateCaisseRequest request) {
        CaisseResponse response = tresorerieService.createCaisse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'DIRECTEUR', 'CHEF_CHANTIER')")
    public ResponseEntity<ApiResponse<CaisseResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(tresorerieService.findById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<List<CaisseResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(tresorerieService.findAll()));
    }

    @PostMapping("/{id}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<CaisseTransactionResponse>> enregistrerTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTransactionRequest request) {
        CaisseTransactionResponse response = tresorerieService.enregistrerTransaction(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    public record AnnulerTransactionRequest(String motif) {}

    /**
     * Cancels a cash movement that should not have happened. The balance is put
     * back by a reversing entry; the original row is kept and marked cancelled.
     */
    @PatchMapping("/{id}/transactions/{transactionId}/annuler")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<CaisseTransactionResponse>> annulerTransaction(
            @PathVariable UUID id,
            @PathVariable UUID transactionId,
            @RequestBody(required = false) AnnulerTransactionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tresorerieService.annulerTransaction(
                id, transactionId, request == null ? null : request.motif())));
    }

    /** Toggles the two operational billing indicators on an existing cash operation. */
    @PatchMapping("/{id}/transactions/{transactionId}/indicateurs")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<CaisseTransactionResponse>> updateTransactionIndicateurs(
            @PathVariable UUID id,
            @PathVariable UUID transactionId,
            @Valid @RequestBody UpdateIndicateursRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                tresorerieService.updateTransactionIndicateurs(id, transactionId, request)));
    }

    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<List<CaisseTransactionResponse>>> getTransactions(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(tresorerieService.getTransactions(id)));
    }
}
