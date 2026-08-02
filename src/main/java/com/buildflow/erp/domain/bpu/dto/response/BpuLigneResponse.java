package com.buildflow.erp.domain.bpu.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record BpuLigneResponse(
        UUID id,
        String ref,
        String designation,
        String unite,
        BigDecimal qtePrevue,
        BigDecimal puHt,
        BigDecimal budgetPrevuHt,
        BigDecimal montantEngageHt,
        BigDecimal tauxConsommation,
        boolean alerteDepassement
) {}
