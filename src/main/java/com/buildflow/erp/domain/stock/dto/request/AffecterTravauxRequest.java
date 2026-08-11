package com.buildflow.erp.domain.stock.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Incorporates material into the works — "poser" it.
 *
 * <p>Moves quantity from available to posé at one location. The location is not
 * changed, and neither is the total value of stock.
 */
public record AffecterTravauxRequest(
        @NotNull UUID articleId,

        /** Where the material is. Null means the central dépôt. */
        UUID chantierId,

        @NotNull @Positive BigDecimal quantite,

        /** Optional note — a BL, a pointage, whatever justifies the posé. */
        String documentRef
) {}
