package com.buildflow.erp.domain.soustraitance.dto.response;

import com.buildflow.erp.domain.soustraitance.entity.ContratStatut;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContratSousTraitantResponse(
        UUID id,
        String reference,
        UUID sousTraitantId,
        String sousTraitantRaisonSociale,
        UUID chantierId,
        String chantierNom,
        String objet,
        BigDecimal montantHt,
        BigDecimal tva,
        BigDecimal montantTtc,
        BigDecimal montantPaye,
        BigDecimal resteAPayer,
        LocalDate dateDebut,
        LocalDate dateFin,
        ContratStatut statut
) {}
