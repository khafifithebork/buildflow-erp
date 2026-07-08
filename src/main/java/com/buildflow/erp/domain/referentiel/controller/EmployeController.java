package com.buildflow.erp.domain.referentiel.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.referentiel.dto.request.CreateEmployeRequest;
import com.buildflow.erp.domain.referentiel.dto.response.EmployeResponse;
import com.buildflow.erp.domain.referentiel.service.EmployeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employes")
@RequiredArgsConstructor
public class EmployeController {

    private final EmployeService employeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<EmployeResponse>> create(
            @Valid @RequestBody CreateEmployeRequest request) {
        EmployeResponse response = employeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'FINANCE', 'PM', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<EmployeResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(employeService.findById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RH', 'FINANCE', 'PM', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<List<EmployeResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(employeService.findAll()));
    }
}