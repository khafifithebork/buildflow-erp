package com.buildflow.erp.domain.soustraitance.dto.request;

import com.buildflow.erp.domain.soustraitance.entity.DossierStatut;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RetenueRequest(
        @NotNull @DecimalMin("0.00") BigDecimal retenueGarantieHt,
        @NotNull DossierStatut dossierStatut
) {}
