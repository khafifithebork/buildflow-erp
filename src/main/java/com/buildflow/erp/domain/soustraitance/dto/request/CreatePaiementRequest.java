package com.buildflow.erp.domain.soustraitance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaiementRequest(
        @NotBlank String reference,
        @NotNull @DecimalMin(value = "0.01", message = "Montant must be positive") BigDecimal montant,
        @NotBlank String motif
) {}
