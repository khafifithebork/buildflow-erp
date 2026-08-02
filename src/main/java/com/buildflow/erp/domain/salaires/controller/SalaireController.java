package com.buildflow.erp.domain.salaires.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.salaires.dto.request.CreateFichePaieRequest;
import com.buildflow.erp.domain.salaires.dto.request.PayerFichePaieRequest;
import com.buildflow.erp.domain.salaires.dto.response.FichePaieResponse;
import com.buildflow.erp.domain.salaires.service.SalaireService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/salaires")
@RequiredArgsConstructor
public class SalaireController {

    private final SalaireService salaireService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<ApiResponse<FichePaieResponse>> create(
            @Valid @RequestBody CreateFichePaieRequest request) {
        FichePaieResponse response = salaireService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'FINANCE', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<FichePaieResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(salaireService.findById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'FINANCE', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<List<FichePaieResponse>>> findAll(
            @RequestParam(required = false) String periode) {
        List<FichePaieResponse> result = (periode != null)
                ? salaireService.findByPeriode(periode)
                : salaireService.findAll();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH')")
    public ResponseEntity<ApiResponse<FichePaieResponse>> valider(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(salaireService.valider(id)));
    }

    @PatchMapping("/{id}/payer")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<FichePaieResponse>> payer(
            @PathVariable UUID id, @Valid @RequestBody PayerFichePaieRequest request) {
        return ResponseEntity.ok(ApiResponse.success(salaireService.payer(id, request)));
    }
}
