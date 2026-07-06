package com.buildflow.erp.domain.referentiel.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.referentiel.dto.request.CreateArticleRequest;
import com.buildflow.erp.domain.referentiel.dto.response.ArticleResponse;
import com.buildflow.erp.domain.referentiel.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACHAT')")
    public ResponseEntity<ApiResponse<ArticleResponse>> create(@Valid @RequestBody CreateArticleRequest request) {
        ArticleResponse response = articleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArticleResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(articleService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ArticleResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(articleService.findAll()));
    }
}