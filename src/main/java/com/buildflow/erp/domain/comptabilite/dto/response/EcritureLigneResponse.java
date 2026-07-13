package com.buildflow.erp.domain.comptabilite.dto.response;

import java.math.BigDecimal;

public record EcritureLigneResponse(
        String id,
        String compteNum,
        String compteLibelle,
        BigDecimal debit,
        BigDecimal credit
) {}