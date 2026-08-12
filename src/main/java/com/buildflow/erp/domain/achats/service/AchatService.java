package com.buildflow.erp.domain.achats.service;

import com.buildflow.erp.common.dto.UpdateIndicateursRequest;
import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.UpdateLignePrixRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import java.util.List;
import java.util.UUID;

public interface AchatService {
    AchatResponse create(CreateAchatRequest request);
    AchatResponse findById(UUID id);
    List<AchatResponse> findAll();
    AchatResponse validateBL(UUID id, String bonLivraisonRef);
    AchatResponse validateFacture(UUID id, String factureRef);
    AchatResponse validatePaiement(UUID id, ModePaiement modePaiement);
    AchatResponse updateIndicateurs(UUID id, UpdateIndicateursRequest request);

    /**
     * How far a re-pricing reached the stock the order had delivered. Material
     * consumed before the correction left at the old price, and that cost
     * cannot be put back into stock — so the correction is not always whole,
     * and the difference is worth telling the user about rather than leaving
     * them to find it in the marge.
     */
    enum Revalorisation {
        /** Nothing had been received, or every unit delivered was corrected. */
        COMPLETE,
        /** Part of the delivery has been consumed or moved on. */
        PARTIELLE,
        /** None of it is in stock any more. */
        IMPOSSIBLE
    }

    /**
     * A re-priced order, together with anything the change left out of step —
     * an invoice that no longer matches, stock that could not be re-valued.
     * Null warning means nothing needs saying.
     */
    record RepricingResult(AchatResponse achat, String warning) {}

    /** Re-prices one order line and rolls the change up into the order totals. */
    RepricingResult updateLignePrix(UUID achatId, UUID ligneId, UpdateLignePrixRequest request);

    /**
     * Re-prices a whole order so it comes to {@code montantHt}, keeping its
     * lines in the proportions they already have.
     */
    RepricingResult updateMontantHt(UUID achatId, java.math.BigDecimal montantHt);
}