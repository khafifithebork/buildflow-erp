package com.buildflow.erp.domain.stock.service;

import com.buildflow.erp.common.dto.PageResponse;
import com.buildflow.erp.domain.achats.entity.Achat;
import com.buildflow.erp.domain.stock.dto.request.CreateMouvementStockRequest;
import com.buildflow.erp.domain.stock.dto.response.StockArticleResponse;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface StockService {
    void approvisionnerDepuisAchat(Achat achat);
    PageResponse<StockArticleResponse> getStockByChantier(UUID chantierId, Pageable pageable);

    /** Everything held in the central dépôt (stock with no chantier). */
    PageResponse<StockArticleResponse> getStockDepot(Pageable pageable);
    StockArticleResponse createMouvement(CreateMouvementStockRequest request);
}