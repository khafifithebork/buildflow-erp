package com.buildflow.erp.domain.auth.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.auth.dto.response.PendingUserResponse;
import com.buildflow.erp.domain.auth.service.UserAdminService;
import com.buildflow.erp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR', 'RH', 'PM')")
    public ApiResponse<List<PendingUserResponse>> pending(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(userAdminService.listApprovableFor(principal.getUser().getRole()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR', 'RH', 'PM')")
    public ApiResponse<Void> approve(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        userAdminService.approve(id, principal.getUser().getRole());
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTEUR', 'RH', 'PM')")
    public ApiResponse<Void> reject(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        userAdminService.reject(id, principal.getUser().getRole());
        return ApiResponse.success(null);
    }
}