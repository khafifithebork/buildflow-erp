package com.buildflow.erp.domain.stock.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record StockArticleResponse(
        UUID id,
        /** Needed by the client to act on this line (affectation, mouvements). */
        UUID articleId,
        String articleCode,
        String designation,
        String unite,
        /** Null when the line sits in the central dépôt. */
        UUID chantierId,
        /** "Dépôt central" when there is no chantier. */
        String chantierNom,
        /** DEPOT or CHANTIER — where this quantity is held. */
        String emplacement,
        /** Still available at this location — "Stock Dispo". */
        BigDecimal quantiteTheorique,
        /** Incorporated into the works — "Stock Travaux (Posé)". */
        BigDecimal quantiteTravaux,
        BigDecimal seuilAlerte,
        boolean enAlerte,
        /** What the material on this line cost, and what it is valued at. */
        double coutUnitaire,
        /** Its value: everything held here, at that cost. */
        BigDecimal valeurHt
) {}