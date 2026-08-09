package com.buildflow.erp.domain.stock.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record StockArticleResponse(
        UUID id,
        String articleCode,
        String designation,
        String unite,
        /** Null when the line sits in the central dépôt. */
        UUID chantierId,
        /** "Dépôt central" when there is no chantier. */
        String chantierNom,
        /** DEPOT or CHANTIER — where this quantity is held. */
        String emplacement,
        BigDecimal quantiteTheorique,
        BigDecimal seuilAlerte,
        boolean enAlerte
) {}