package com.buildflow.erp.domain.achats.dto.request;

import com.buildflow.erp.common.paiement.ModePaiement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Un reglement porte sur une commande : combien, et par quel moyen. */
public record ReglerAchatRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal montant,
        @NotNull ModePaiement modePaiement
) {}
