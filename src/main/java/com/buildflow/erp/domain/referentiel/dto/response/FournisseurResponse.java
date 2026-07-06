package com.buildflow.erp.domain.referentiel.dto.response;

import com.buildflow.erp.domain.referentiel.entity.FournisseurStatut;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FournisseurResponse(
        UUID id,
        String code,
        String raisonSociale,
        String ice,
        String contact,
        String telephone,
        String email,
        String ville,
        String adresse,
        String rib,
        String banque,
        FournisseurStatut statut,
        List<String> categorieArticles,
        BigDecimal totalAchatsAnnee,
        BigDecimal soldeImpaye
) {}