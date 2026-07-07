package com.buildflow.erp.domain.referentiel.dto.response;
import com.buildflow.erp.domain.referentiel.entity.ChantierStatut;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ChantierResponse(
        UUID id,
        String code,
        String nom,
        String client,
        String adresse,
        String ville,
        ChantierStatut statut,
        LocalDate dateDebut,
        LocalDate dateFin,
        BigDecimal budgetHt,
        BigDecimal depensesHt,
        int avancement,
        String chefProjetNom,
        int nombreOuvriers,
        List<String> soustraitantsActifs,
        List<JalonResponse> jalons
) {}