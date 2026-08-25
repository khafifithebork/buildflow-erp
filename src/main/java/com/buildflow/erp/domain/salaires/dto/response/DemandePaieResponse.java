package com.buildflow.erp.domain.salaires.dto.response;

import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.domain.salaires.entity.DemandePaieStatut;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DemandePaieResponse(
        UUID id,
        String reference,
        String libelle,
        String periode,
        UUID chantierId,
        String chantierNom,
        UUID bpuLigneId,
        String bpuLigneRef,
        BigDecimal montantNet,
        DemandePaieStatut statut,
        ModePaiement modePaiement,
        LocalDateTime createdAt
) {}
