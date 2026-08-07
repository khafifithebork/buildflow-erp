package com.buildflow.erp.domain.achats.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record LigneAchatResponse(
        UUID id,
        String articleCode,
        String designation,
        BigDecimal quantite,
        String unite,
        double prixUnitaire,
        BigDecimal total,
        String bpuLigneRef
) {}