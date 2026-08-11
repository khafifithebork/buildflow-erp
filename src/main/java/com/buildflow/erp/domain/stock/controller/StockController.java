package com.buildflow.erp.domain.stock.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.common.dto.PageResponse;
import com.buildflow.erp.domain.stock.dto.request.CreateMouvementStockRequest;
import com.buildflow.erp.domain.stock.dto.response.StockArticleResponse;
import com.buildflow.erp.domain.stock.service.StockService;
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
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/chantiers/{chantierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MAGASINIER', 'PM', 'CHEF_CHANTIER')")
    public ResponseEntity<ApiResponse<PageResponse<StockArticleResponse>>> getStockByChantier(
            @PathVariable UUID chantierId,
            @PageableDefault(size = 20, sort = "article.designation") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(stockService.getStockByChantier(chantierId, pageable)));
    }

    /** Stock held in the central dépôt, i.e. not yet allocated to any chantier. */
    @GetMapping("/depot")
    @PreAuthorize("hasAnyRole('ADMIN', 'MAGASINIER', 'PM', 'CHEF_CHANTIER')")
    public ResponseEntity<ApiResponse<PageResponse<StockArticleResponse>>> getStockDepot(
            @PageableDefault(size = 20, sort = "article.designation") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(stockService.getStockDepot(pageable)));
    }

    /** Incorporates material into the works at one location. */
    @PostMapping("/affectation-travaux")
    @PreAuthorize("hasAnyRole('ADMIN', 'MAGASINIER', 'PM', 'CHEF_CHANTIER')")
    public ResponseEntity<ApiResponse<StockArticleResponse>> affecterAuxTravaux(
            @Valid @RequestBody com.buildflow.erp.domain.stock.dto.request.AffecterTravauxRequest request) {
        return ResponseEntity.ok(ApiResponse.success(stockService.affecterAuxTravaux(request)));
    }

    @PostMapping("/mouvements")
    @PreAuthorize("hasAnyRole('ADMIN', 'MAGASINIER', 'PM')")
    public ResponseEntity<ApiResponse<StockArticleResponse>> createMouvement(
            @Valid @RequestBody CreateMouvementStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(stockService.createMouvement(request)));
    }
}