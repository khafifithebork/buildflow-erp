package com.buildflow.erp.domain.stock.dto.request;

import com.buildflow.erp.domain.stock.entity.TypeMouvement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateMouvementStockRequest(
        @NotNull UUID articleId,

        /**
         * Where the movement applies. Null means the central dépôt, so an
         * ENTREE with no chantier receives goods into the warehouse.
         */
        UUID chantierId,

        @NotNull TypeMouvement typeMouvement,
        @NotNull @DecimalMin("0.001") BigDecimal quantite,
        String documentRef,

        /**
         * Destination, for {@code TRANSFERT} only. Null means the central
         * dépôt, so a transfer can move stock either way between the warehouse
         * and a site. Must differ from {@code chantierId}.
         */
        UUID chantierDestinationId
) {}
