package com.buildflow.erp.domain.referentiel.dto.response;

import com.buildflow.erp.domain.referentiel.entity.SousTraitantStatut;
import java.math.BigDecimal;
import java.util.UUID;

public record SousTraitantResponse(
        UUID id,
        String code,
        String raisonSociale,
        String ice,
        String specialite,
        String contact,
        String telephone,
        String email,
        String ville,
        String adresse,
        SousTraitantStatut statut,
        int nombreContratsActifs,
        BigDecimal montantTotalPaye
) {}