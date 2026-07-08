package com.buildflow.erp.domain.tresorerie.controller;

import com.buildflow.erp.common.dto.ApiResponse;
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

    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<List<CaisseTransactionResponse>>> getTransactions(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(tresorerieService.getTransactions(id)));
    }
}
