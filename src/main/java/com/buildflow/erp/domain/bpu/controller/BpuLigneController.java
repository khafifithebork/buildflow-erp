package com.buildflow.erp.domain.bpu.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.bpu.dto.request.CreateBpuLigneRequest;
import com.buildflow.erp.domain.bpu.dto.request.ImportBpuLignesRequest;
import com.buildflow.erp.domain.bpu.dto.response.BpuLigneResponse;
import com.buildflow.erp.domain.bpu.service.BpuLigneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chantiers/{chantierId}/bpu-lignes")
@RequiredArgsConstructor
public class BpuLigneController {

    private final BpuLigneService bpuLigneService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    public ResponseEntity<ApiResponse<BpuLigneResponse>> create(
            @PathVariable UUID chantierId,
            @Valid @RequestBody CreateBpuLigneRequest request) {
        BpuLigneResponse response = bpuLigneService.create(chantierId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    public ResponseEntity<ApiResponse<BpuLigneResponse>> update(
            @PathVariable UUID chantierId,
            @PathVariable UUID id,
            @Valid @RequestBody CreateBpuLigneRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bpuLigneService.update(chantierId, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    public ResponseEntity<Void> delete(@PathVariable UUID chantierId, @PathVariable UUID id) {
        bpuLigneService.delete(chantierId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    public ResponseEntity<ApiResponse<List<BpuLigneResponse>>> batchReplace(
            @PathVariable UUID chantierId,
            @Valid @RequestBody ImportBpuLignesRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bpuLigneService.batchReplace(chantierId, request)));
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    public ResponseEntity<ApiResponse<List<BpuLigneResponse>>> importExcel(
            @PathVariable UUID chantierId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(bpuLigneService.importExcel(chantierId, file)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BpuLigneResponse>>> findByChantier(@PathVariable UUID chantierId) {
        return ResponseEntity.ok(ApiResponse.success(bpuLigneService.findByChantier(chantierId)));
    }
}
