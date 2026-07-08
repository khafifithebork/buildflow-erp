package com.buildflow.erp.domain.stock.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record StockArticleResponse(
        UUID id,
        String articleCode,
        String designation,
        String unite,
        UUID chantierId,
        String chantierNom,
        BigDecimal quantiteTheorique,
        BigDecimal seuilAlerte,
        boolean enAlerte
) {}