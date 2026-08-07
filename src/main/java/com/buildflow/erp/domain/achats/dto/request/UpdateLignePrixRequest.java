package com.buildflow.erp.domain.achats.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;


/** New unit price for one line of a purchase order. */
public record UpdateLignePrixRequest(
        @NotNull @PositiveOrZero Double prixUnitaire
) {}
