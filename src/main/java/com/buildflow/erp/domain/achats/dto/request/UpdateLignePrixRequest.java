package com.buildflow.erp.domain.achats.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** New unit price for one line of a purchase order. */
public record UpdateLignePrixRequest(
        @NotNull @DecimalMin("0.0") BigDecimal prixUnitaire
) {}
