package com.buildflow.erp.domain.achats.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record LigneAchatResponse(
        UUID id,
        String articleCode,
        String designation,
        double quantite,
        String unite,
        double prixUnitaire,
        BigDecimal total,
        /** Le taux figé à la commande, en fraction (0.10 = 10 %). */
        BigDecimal tvaRate,
        String bpuLigneRef
) {}