package com.buildflow.erp.domain.referentiel.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.common.dto.PageResponse;
import com.buildflow.erp.domain.referentiel.dto.request.CreateCategorieArticleRequest;
import com.buildflow.erp.domain.referentiel.dto.response.CategorieArticleResponse;
import com.buildflow.erp.domain.referentiel.service.CategorieArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories-articles")
@RequiredArgsConstructor
public class CategorieArticleController {

    private final CategorieArticleService categorieArticleService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACHAT')")
    public ResponseEntity<ApiResponse<CategorieArticleResponse>> create(
            @Valid @RequestBody CreateCategorieArticleRequest request) {
        CategorieArticleResponse response = categorieArticleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategorieArticleResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(categorieArticleService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CategorieArticleResponse>>> findAll(
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(categorieArticleService.findAll(pageable)));
    }
}