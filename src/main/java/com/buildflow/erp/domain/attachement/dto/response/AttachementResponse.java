package com.buildflow.erp.domain.attachement.dto.response;

import com.buildflow.erp.domain.attachement.entity.AttachementStatut;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AttachementResponse(
        UUID id,
        UUID chantierId,
        String chantierNom,
        String reference,
        LocalDate dateAttachement,
        BigDecimal montantHt,
        BigDecimal tva,
        BigDecimal montantTtc,
        AttachementStatut statut,
        LocalDateTime dateEncaissement,
        List<AttachementLigneResponse> lignes
) {}
