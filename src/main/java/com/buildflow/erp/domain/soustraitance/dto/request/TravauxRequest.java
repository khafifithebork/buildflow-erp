package com.buildflow.erp.domain.soustraitance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TravauxRequest(
        @NotNull @DecimalMin("0.00") BigDecimal montantRealiseHt
) {}
