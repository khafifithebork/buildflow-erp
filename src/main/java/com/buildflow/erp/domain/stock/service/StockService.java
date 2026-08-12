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

    /**
     * Incorporates material into the works: moves quantity from available to
     * posé at one location, without changing where it is.
     */
    StockArticleResponse affecterAuxTravaux(
            com.buildflow.erp.domain.stock.dto.request.AffecterTravauxRequest request);

    /**
     * Re-values material already received, when the order it came in on is
     * re-priced. Without this the stock keeps the old price and the marge nette
     * carries the difference as a loss that never happened.
     *
     * @param quantite  what was received on that line
     * @param deltaPrix the change in unit price
     * @return false when nothing is held any more, so the correction had
     *         nowhere to land — the caller reports that to the user
     */
    boolean revaloriser(java.util.UUID articleId, java.util.UUID chantierId,
                        java.math.BigDecimal quantite, double deltaPrix);
}