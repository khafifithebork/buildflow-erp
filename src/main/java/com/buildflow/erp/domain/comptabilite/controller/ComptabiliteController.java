package com.buildflow.erp.domain.comptabilite.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.comptabilite.dto.response.EcritureComptableResponse;
import com.buildflow.erp.domain.comptabilite.service.ComptabiliteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/comptabilite")
@RequiredArgsConstructor
public class ComptabiliteController {

    private final ComptabiliteService comptabiliteService;

    @GetMapping("/ecritures")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'DIRECTEUR')")
    public ApiResponse<List<EcritureComptableResponse>> ecritures() {
        return ApiResponse.success(comptabiliteService.listEcritures());
    }
}