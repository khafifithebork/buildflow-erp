package com.buildflow.erp.domain.soustraitance.dto.response;

import com.buildflow.erp.domain.soustraitance.entity.PaiementStatut;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaiementSousTraitantResponse(
        UUID id,
        String reference,
        BigDecimal montant,
        String motif,
        PaiementStatut statut,
        LocalDate datePaiement
) {}
