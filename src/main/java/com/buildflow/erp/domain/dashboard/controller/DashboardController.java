package com.buildflow.erp.domain.dashboard.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.dashboard.dto.response.DashboardKpisResponse;
import com.buildflow.erp.domain.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/v1/dashboard/kpis")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'DIRECTEUR')")
    public ApiResponse<DashboardKpisResponse> kpis(@RequestParam(required = false) String month) {
        return ApiResponse.success(dashboardService.getKpis(month));
    }
}
