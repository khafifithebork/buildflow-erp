package com.buildflow.erp.domain.salaires.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.salaires.dto.request.CreateDemandePaieRequest;
import com.buildflow.erp.domain.salaires.dto.request.PayerDemandePaieRequest;
import com.buildflow.erp.domain.salaires.dto.response.DemandePaieResponse;
import com.buildflow.erp.domain.salaires.service.DemandePaieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demandes-paie")
@RequiredArgsConstructor
public class DemandePaieController {

    private final DemandePaieService demandePaieService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<ApiResponse<DemandePaieResponse>> create(
            @Valid @RequestBody CreateDemandePaieRequest request) {
        DemandePaieResponse response = demandePaieService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'FINANCE', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<DemandePaieResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(demandePaieService.findById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'FINANCE', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<List<DemandePaieResponse>>> findAll(
            @RequestParam(required = false) String periode) {
        List<DemandePaieResponse> result = (periode != null)
                ? demandePaieService.findByPeriode(periode)
                : demandePaieService.findAll();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<ApiResponse<DemandePaieResponse>> valider(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(demandePaieService.valider(id)));
    }

    @PatchMapping("/{id}/payer")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<DemandePaieResponse>> payer(
            @PathVariable UUID id, @Valid @RequestBody PayerDemandePaieRequest request) {
        return ResponseEntity.ok(ApiResponse.success(demandePaieService.payer(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        demandePaieService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
