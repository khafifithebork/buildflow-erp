package com.buildflow.erp.domain.attachement.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.attachement.dto.request.CreateAttachementRequest;
import com.buildflow.erp.domain.attachement.dto.response.AttachementResponse;
import com.buildflow.erp.domain.attachement.service.AttachementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AttachementController {

    private final AttachementService attachementService;

    @PostMapping("/api/v1/chantiers/{chantierId}/attachements")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    public ResponseEntity<ApiResponse<AttachementResponse>> create(
            @PathVariable UUID chantierId,
            @Valid @RequestBody CreateAttachementRequest request) {
        AttachementResponse response = attachementService.create(chantierId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/chantiers/{chantierId}/attachements")
    public ResponseEntity<ApiResponse<List<AttachementResponse>>> findByChantier(@PathVariable UUID chantierId) {
        return ResponseEntity.ok(ApiResponse.success(attachementService.findByChantier(chantierId)));
    }

    @PatchMapping("/api/v1/attachements/{id}/encaisser")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'DIRECTEUR')")
    public ResponseEntity<ApiResponse<AttachementResponse>> encaisser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(attachementService.encaisser(id)));
    }
}
