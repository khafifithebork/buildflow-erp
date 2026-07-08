package com.buildflow.erp.domain.tresorerie.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CaisseResponse(
        UUID id,
        String code,
        String libelle,
        UUID chantierId,
        String chantierNom,
        BigDecimal solde,
        BigDecimal seuilMinimum,
        boolean enAlerte,
        List<CaisseTransactionResponse> dernieresTransactions
) {}
